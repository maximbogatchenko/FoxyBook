package com.foxybook.app.features.reader

import android.app.Application
import android.util.Log
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxybook.app.core.database.BookDataRepository
import com.foxybook.app.core.datastore.DataStoreManager
import com.foxybook.app.core.models.Bookmark
import com.foxybook.app.core.models.ReaderMode
import com.foxybook.app.core.models.ReaderSettings
import com.foxybook.app.core.models.ReadingPosition
import com.foxybook.app.core.reader.BookParser
import com.foxybook.app.core.reader.ContentBlock
import com.foxybook.app.core.reader.HtmlBlockParser
import com.foxybook.app.R
import com.foxybook.app.core.reader.TextPaginator
import com.foxybook.app.core.tts.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class ReaderState(
    val book: com.foxybook.app.core.models.ParsedBook? = null,
    val currentChapter: Int = 0,

    // Cache
    val chapterBlocks: Map<Int, List<ContentBlock>> = emptyMap(),
    val chapterPages: Map<Int, List<TextPaginator.Page>> = emptyMap(),

    // Dimensions
    val pageWidth: Int = 0,
    val pageHeight: Int = 0,
    val textMeasurer: TextMeasurer? = null,
    val density: Density? = null,

    // Position
    val textOffset: Int = 0,
    val scrollY: Int = 0,
    val scrollOffset: Int = 0,
    val scrollPercentage: Int = 0,
    val pageCurrent: Int = 0,
    val pageTotal: Int = 1,

    // Common
    val readingPercentage: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
    val settings: ReaderSettings = ReaderSettings(),
    val bookmarks: List<Bookmark> = emptyList(),
    val showSettings: Boolean = false,
    val showChapters: Boolean = false,
    val showBookmarks: Boolean = false,
    val isImmersive: Boolean = false,
    val positionRestored: Boolean = false,
    val lastPositionRestoreTrigger: Long = 0L,

    // Pagination version — incremented each time dimensions change.
    // Used to detect stale page caches that need re-pagination.
    val paginationVersion: Int = 0,
    val pageVersions: Map<Int, Int> = emptyMap(), // chapterIndex → version when paginated

    // Real chapter titles extracted from content (e.g. "Пролог", "Глава 1")
    val chapterTitlesExtracted: Map<Int, String> = emptyMap(),

    // Content lengths per chapter for progress calculation (char count of text, no HTML tags)
    val chapterLengths: List<Int> = emptyList(),
    val totalContentLength: Long = 0L,

    // TTS (synced from TtsManager)
    val isSpeaking: Boolean = false,
    val isPaused: Boolean = false,
    val availableVoices: List<com.foxybook.app.core.tts.VoiceInfo> = emptyList(),
    val availableLanguages: List<String> = emptyList(),
    val showTtsControls: Boolean = false,
    val isSelectingTtsStartPosition: Boolean = false,
    val currentTtsChapter: Int = -1,
    val currentTtsBlockIndex: Int = -1,
    val sleepTimerRemainingSeconds: Long = 0L,
    val currentEngine: String? = null,
    val availableEngines: List<com.foxybook.app.core.tts.EngineInfo> = emptyList()
)

sealed interface ReaderEvent {
    data class LoadBook(val filePath: String, val bookId: Int, val format: String) : ReaderEvent
    data class ChapterChanged(val index: Int, val resetPosition: Boolean = true) : ReaderEvent
    data class ScrollProgress(val percentage: Int, val blockIndex: Int, val scrollOffset: Int, val offset: Int) : ReaderEvent
    data class PageInfo(val current: Int, val total: Int, val offset: Int) : ReaderEvent
    data class UpdatePageDimensions(
        val width: Int,
        val height: Int,
        val textMeasurer: TextMeasurer,
        val density: Density
    ) : ReaderEvent
    data object NextChapter : ReaderEvent
    data object PreviousChapter : ReaderEvent
    data class FontSizeChanged(val size: Int) : ReaderEvent
    data class LineHeightChanged(val height: Float) : ReaderEvent
    data class MarginsChanged(val margins: Int) : ReaderEvent
    data class ReaderModeChanged(val mode: ReaderMode) : ReaderEvent
    data object ToggleProgressDisplay : ReaderEvent
    data class ReaderThemeChanged(val theme: com.foxybook.app.core.models.ReaderTheme) : ReaderEvent
    data object ToggleSettings : ReaderEvent
    data object ToggleChapters : ReaderEvent
    data object ToggleBookmarks : ReaderEvent
    data object ToggleImmersive : ReaderEvent
    data class AddBookmark(val preview: String) : ReaderEvent
    data class RemoveBookmark(val bookmark: Bookmark) : ReaderEvent
    data class GoToBookmark(val bookmark: Bookmark) : ReaderEvent

    // TTS
    data object ToggleTtsControls : ReaderEvent
    data object StartTtsSelection : ReaderEvent
    data object CancelTtsSelection : ReaderEvent
    data class StartTts(val chapterIndex: Int? = null, val blockIndex: Int? = null) : ReaderEvent
    data object PauseTts : ReaderEvent
    data object ResumeTts : ReaderEvent
    data object StopTts : ReaderEvent
    data class SetTtsRate(val rate: Float) : ReaderEvent
    data class SetTtsPitch(val pitch: Float) : ReaderEvent
    data class SetTtsLanguage(val language: String) : ReaderEvent
    data class SetTtsVoice(val voiceId: String) : ReaderEvent
    data class SetTtsEngine(val packageName: String) : ReaderEvent

    // Brightness
    data class SetBrightness(val brightness: Float) : ReaderEvent
    data object ResetBrightness : ReaderEvent

    // Sleep Timer
    data class SetSleepTimer(val minutes: Int) : ReaderEvent
    data object CancelSleepTimer : ReaderEvent
}

class ReaderViewModel(
    private val bookParser: BookParser,
    private val dataStoreManager: DataStoreManager,
    private val bookDataRepository: BookDataRepository,
    private val application: Application
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    val ttsManager = TtsManager(application)

    private var bookId: Int = -1
    private var format: String = ""
    private var saveJob: Job? = null

    // Собственный scope для сохранения позиции — переживает очистку ViewModel
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val paginationJobMap = mutableMapOf<Int, Job>()

    init {
        // Sync TTS state into reader state
        viewModelScope.launch {
            ttsManager.state.collect { ttsState ->
                _state.update { it.copy(
                    isSpeaking = ttsState.isSpeaking,
                    isPaused = ttsState.isPaused,
                    availableVoices = ttsState.availableVoices,
                    availableLanguages = ttsState.availableLanguages,
                    currentEngine = ttsState.currentEngine,
                    availableEngines = ttsState.availableEngines,
                    sleepTimerRemainingSeconds = ttsState.sleepTimerRemainingSeconds
                ) }
            }
        }

        viewModelScope.launch {
            dataStoreManager.readerSettings.collect { settings ->
                val prevEngine = _state.value.settings.ttsEngine
                _state.update { it.copy(settings = settings) }
                // Восстанавливаем выбранный TTS-движок при первом запуске
                if (settings.ttsEngine != null && prevEngine == null) {
                    ttsManager.switchEngine(settings.ttsEngine)
                }
            }
        }

        // Wire up TtsManager callbacks
        ttsManager.onBlockCompleted = {
            viewModelScope.launch {
                val s = _state.value
                val nextBlock = s.currentTtsBlockIndex + 1
                speakBlock(s.currentTtsChapter, nextBlock)
            }
        }
        ttsManager.onCommand = { cmd ->
            when (cmd) {
                "RESUME" -> onEvent(ReaderEvent.ResumeTts)
                "PAUSE" -> onEvent(ReaderEvent.PauseTts)
                "STOP" -> onEvent(ReaderEvent.StopTts)
                "NEXT" -> {
                    val s = _state.value
                    speakBlock(s.currentTtsChapter, s.currentTtsBlockIndex + 1)
                }
                "PREV" -> {
                    val s = _state.value
                    speakBlock(s.currentTtsChapter, s.currentTtsBlockIndex - 1)
                }
            }
        }
    }

    override fun onCleared() {
        // Сохраняем позицию перед выходом — синхронно, чтобы гарантировать запись
        saveJob?.cancel()
        saveJob = null

        val s = _state.value
        if (bookId >= 0 && s.positionRestored && !s.isLoading && s.book != null) {
            kotlinx.coroutines.runBlocking {
                withTimeoutOrNull(3_000) {
                    withContext(Dispatchers.IO) {
                        savePositionInternal(s)
                    }
                }
            }
        }

        // Cancel all pagination jobs
        paginationJobMap.values.forEach { it.cancel() }
        paginationJobMap.clear()

        // Destroy TTS manager (unbinds service)
        ttsManager.destroy()

        super.onCleared()
    }

    /**
     * Перезапускает текущий блок TTS с новыми настройками (скорость, высота, голос).
     */
    private fun restartTtsBlockIfActive() {
        val s = _state.value
        if (!s.isSpeaking && !s.isPaused) return
        if (s.currentTtsChapter < 0 || s.currentTtsBlockIndex < 0) return

        ttsManager.stop()
        val ch = s.currentTtsChapter
        val bl = s.currentTtsBlockIndex
        speakBlock(ch, bl)
    }

    fun onEvent(event: ReaderEvent) {
        when (event) {
            is ReaderEvent.LoadBook -> loadBook(event.filePath, event.bookId, event.format)
            is ReaderEvent.ChapterChanged -> goToChapter(event.index, event.resetPosition)
            is ReaderEvent.ScrollProgress -> handleScrollProgress(event.percentage, event.blockIndex, event.scrollOffset, event.offset)
            is ReaderEvent.PageInfo -> handlePageInfo(event.current, event.total, event.offset)
            is ReaderEvent.UpdatePageDimensions -> updateDimensions(event.width, event.height, event.textMeasurer, event.density)
            is ReaderEvent.NextChapter -> nextChapter()
            is ReaderEvent.PreviousChapter -> previousChapter()
            is ReaderEvent.FontSizeChanged -> updateSettings { it.copy(fontSize = event.size) }
            is ReaderEvent.LineHeightChanged -> updateSettings { it.copy(lineHeight = event.height) }
            is ReaderEvent.MarginsChanged -> updateSettings { it.copy(margins = event.margins) }
            ReaderEvent.ToggleProgressDisplay -> updateSettings { it.copy(showProgressAsPercentage = !it.showProgressAsPercentage) }
            is ReaderEvent.ReaderModeChanged -> {
                updateSettings { it.copy(readerMode = event.mode.name) }
                // Не чистим chapterPages — новый режим использует свои данные
                // (scroll mode использует blocks, page mode использует pages).
                // Принудительная пагинация перезапишет pages когда надо.
                _state.update { it.copy(
                    positionRestored = true,
                    lastPositionRestoreTrigger = System.currentTimeMillis()
                ) }
            }
            is ReaderEvent.ReaderThemeChanged -> updateSettings { it.copy(readerTheme = event.theme.name) }
            is ReaderEvent.ToggleSettings -> _state.update {
                it.copy(showSettings = !it.showSettings, showChapters = false, showBookmarks = false, showTtsControls = false)
            }
            is ReaderEvent.ToggleChapters -> _state.update {
                it.copy(showChapters = !it.showChapters, showSettings = false, showBookmarks = false)
            }
            is ReaderEvent.ToggleBookmarks -> _state.update {
                val next = !it.showBookmarks
                it.copy(showBookmarks = next, showChapters = next, showSettings = false)
            }
            is ReaderEvent.ToggleImmersive -> {
                _state.update { it.copy(isImmersive = !it.isImmersive) }
            }
            is ReaderEvent.AddBookmark -> addBookmark(event.preview)
            is ReaderEvent.RemoveBookmark -> removeBookmark(event.bookmark)
            is ReaderEvent.GoToBookmark -> goToBookmark(event.bookmark)

            // TTS
            ReaderEvent.ToggleTtsControls -> _state.update {
                it.copy(showTtsControls = !it.showTtsControls, isSelectingTtsStartPosition = false)
            }
            ReaderEvent.StartTtsSelection -> viewModelScope.launch {
                _state.update { it.copy(showTtsControls = false, showSettings = false) }
                delay(400)
                _state.update { it.copy(isSelectingTtsStartPosition = true) }
            }
            ReaderEvent.CancelTtsSelection -> _state.update {
                it.copy(isSelectingTtsStartPosition = false, showTtsControls = true)
            }
            is ReaderEvent.StartTts -> {
                val ch = event.chapterIndex ?: _state.value.currentChapter
                val bl = event.blockIndex ?: if (ch == _state.value.currentChapter) {
                    calculateCurrentBlockIndex()
                } else 0
                speakBlock(ch, bl)
                _state.update { it.copy(isSelectingTtsStartPosition = false) }
            }
            ReaderEvent.PauseTts -> ttsManager.pause()
            ReaderEvent.ResumeTts -> {
                val s = _state.value
                speakBlock(s.currentTtsChapter, s.currentTtsBlockIndex)
            }
            ReaderEvent.StopTts -> {
                ttsManager.stop()
                _state.update { it.copy(currentTtsChapter = -1, currentTtsBlockIndex = -1) }
            }
            is ReaderEvent.SetTtsRate -> {
                updateSettings { it.copy(ttsRate = event.rate) }
                restartTtsBlockIfActive()
            }
            is ReaderEvent.SetTtsPitch -> {
                updateSettings { it.copy(ttsPitch = event.pitch) }
                restartTtsBlockIfActive()
            }
            is ReaderEvent.SetTtsLanguage -> {
                Log.d("ReaderViewModel", "SetTtsLanguage: ${event.language}")
                updateSettings { it.copy(ttsLanguage = event.language, ttsVoice = null) }
                restartTtsBlockIfActive()
            }
            is ReaderEvent.SetTtsVoice -> {
                Log.d("ReaderViewModel", "SetTtsVoice: ${event.voiceId}")
                updateSettings { it.copy(ttsVoice = event.voiceId) }
                restartTtsBlockIfActive()
            }
            is ReaderEvent.SetTtsEngine -> {
                Log.d("ReaderViewModel", "SetTtsEngine: ${event.packageName}")
                updateSettings { it.copy(ttsEngine = event.packageName, ttsVoice = null, ttsLanguage = null) }
                ttsManager.switchEngine(event.packageName)
            }

            // Brightness
            is ReaderEvent.SetBrightness -> updateSettings { it.copy(brightness = event.brightness) }
            ReaderEvent.ResetBrightness -> updateSettings { it.copy(brightness = -1f) }

            // Sleep Timer
            is ReaderEvent.SetSleepTimer -> ttsManager.setSleepTimer(event.minutes)
            ReaderEvent.CancelSleepTimer -> ttsManager.cancelSleepTimer()
        }
    }

    private fun calculateCurrentBlockIndex(): Int {
        val s = _state.value
        val mode = ReaderMode.valueOf(s.settings.readerMode)
        return if (mode == ReaderMode.HORIZONTAL) {
            val pages = s.chapterPages[s.currentChapter] ?: emptyList()
            val currentPage = pages.getOrNull(s.pageCurrent)
            val blocks = s.chapterBlocks[s.currentChapter] ?: emptyList()
            var offset = 0
            var foundIndex = 0
            for (i in blocks.indices) {
                val blockLen = blocks[i].getTextContent().length
                if (offset + blockLen > (currentPage?.startOffset ?: 0)) {
                    foundIndex = i
                    break
                }
                offset += blockLen
            }
            foundIndex
        } else {
            s.scrollY
        }
    }

    private fun speakBlock(chapterIndex: Int, blockIndex: Int) {
        viewModelScope.launch {
            val s = _state.value
            var ch = chapterIndex
            var bl = blockIndex

            val blocks = getBlocksSync(ch)
            if (bl >= blocks.size) {
                if (ch < (s.book?.chapters?.size ?: 0) - 1) {
                    ch++
                    bl = 0
                } else {
                    ttsManager.stop()
                    return@launch
                }
            } else if (bl < 0) {
                if (ch > 0) {
                    ch--
                    bl = (getBlocksSync(ch).size - 1).coerceAtLeast(0)
                } else {
                    bl = 0
                }
            }

            val currentBlocks = getBlocksSync(ch)
            val block = currentBlocks.getOrNull(bl)
            val text = block?.getTextContent() ?: ""

            if (text.isBlank()) {
                speakBlock(ch, bl + 1)
                return@launch
            }

            _state.update { it.copy(
                currentTtsChapter = ch,
                currentTtsBlockIndex = bl
            ) }

            updateSettings { it.copy(lastTtsChapter = ch, lastTtsBlockIndex = bl) }

            Log.d("ReaderViewModel", "speakBlock: Starting TTS with voice=${s.settings.ttsVoice}, rate=${s.settings.ttsRate}, pitch=${s.settings.ttsPitch}")

            // Передаём voiceName только для Google TTS — сторонние движки
            // используют свой движок и голос по умолчанию
            val isGoogleEngine = s.currentEngine?.contains("google") == true
            ttsManager.speak(
                text = text,
                bookTitle = s.book?.title ?: "Книга",
                chapterTitle = s.book?.chapters?.getOrNull(ch)?.title ?: "Глава ${ch + 1}",
                rate = s.settings.ttsRate,
                pitch = s.settings.ttsPitch,
                voiceName = if (isGoogleEngine) s.settings.ttsVoice else null
            )
        }
    }

    // ─── Content Access ───

    fun getBlocks(chapterIndex: Int): List<ContentBlock> {
        val cached = _state.value.chapterBlocks[chapterIndex]
        if (cached != null) return cached

        preloadChapter(chapterIndex)
        return emptyList()
    }

    fun ensurePaginated(chapterIndex: Int) {
        val s = _state.value
        val textMeasurer = s.textMeasurer
        val density = s.density
        val cachedVersion = s.pageVersions.getOrDefault(chapterIndex, -1)

        if (textMeasurer != null && density != null &&
            s.pageWidth > 0 && s.pageHeight > 0 &&
            !(s.chapterPages.containsKey(chapterIndex) && cachedVersion == s.paginationVersion)) {

            Log.d("ReaderNav", "ensurePaginated: Requesting pagination for chapter $chapterIndex")
            paginateChapter(chapterIndex, textMeasurer, density)
        }
    }

    private fun preloadChapter(index: Int) {
        val book = _state.value.book ?: return
        if (index < 0 || index >= book.chapters.size) return
        if (_state.value.chapterBlocks.containsKey(index)) return

        viewModelScope.launch(Dispatchers.IO) {
            getBlocksSync(index)
        }
    }

    private suspend fun getBlocksSync(chapterIndex: Int): List<ContentBlock> {
        val cached = _state.value.chapterBlocks[chapterIndex]
        if (cached != null) return cached

        val book = _state.value.book ?: return emptyList()
        val chapter = book.chapters.getOrNull(chapterIndex) ?: return emptyList()

        Log.d("ReaderNav", "START LOAD CHAPTER: $chapterIndex")
        return withContext(Dispatchers.IO) {
            val html = if (chapter.htmlContent.isBlank() && chapter.contentId.isNotBlank()) {
                bookParser.loadChapterContent(book.filePath, chapter, bookId, format)
            } else {
                chapter.htmlContent
            }

            val blocks = HtmlBlockParser.parse(html)
            Log.d("ReaderNav", "LOAD CHAPTER SUCCESS: $chapterIndex, blocks count: ${blocks.size}")

            // Extract real chapter title from content if available
            val extractedTitle = HtmlBlockParser.extractFirstTitle(blocks)

            _state.update {
                val currentChapter = it.currentChapter
                val chaptersToKeep = (currentChapter - 3..currentChapter + 3)
                val filteredBlocks = it.chapterBlocks.filterKeys { key -> key in chaptersToKeep }
                val filteredPages = it.chapterPages.filterKeys { key -> key in chaptersToKeep }

                val titles = it.chapterTitlesExtracted.toMutableMap()
                if (extractedTitle != null) {
                    titles[chapterIndex] = extractedTitle
                } else if (blocks.isEmpty()) {
                    // Empty chapter — mark title as empty so we can skip it
                    titles[chapterIndex] = ""
                }

                it.copy(
                    chapterBlocks = filteredBlocks + (chapterIndex to blocks),
                    chapterPages = filteredPages,
                    chapterTitlesExtracted = titles
                )
            }
            blocks
        }
    }

    // ─── Pagination ───

    private fun updateDimensions(width: Int, height: Int, textMeasurer: TextMeasurer, density: Density) {
        val current = _state.value

        if (current.pageWidth == width && current.pageHeight == height) {
            if (!current.chapterPages.containsKey(current.currentChapter)) {
                Log.d("ReaderNav", "updateDimensions: Dimensions same, paginating missing chapter ${current.currentChapter}")
                paginateChapter(current.currentChapter, textMeasurer, density)
            }
            return
        }

        Log.d("ReaderNav", "updateDimensions: width=$width, height=$height")

        // Не чистим chapterPages — старые страницы инвалидируются лениво
        // через paginationVersion: ensurePaginated() перепагинирует их при
        // доступе (cachedVersion != paginationVersion).
        _state.update { it.copy(
            pageWidth = width,
            pageHeight = height,
            textMeasurer = textMeasurer,
            density = density,
            paginationVersion = it.paginationVersion + 1
        ) }

        paginateChapter(current.currentChapter, textMeasurer, density)
        paginateChapter(current.currentChapter + 1, textMeasurer, density)
        paginateChapter(current.currentChapter - 1, textMeasurer, density)
    }

    private fun paginateChapter(index: Int, textMeasurer: TextMeasurer, density: Density) {
        val s = _state.value
        val book = s.book ?: return
        if (index < 0 || index >= book.chapters.size) return
        if (s.pageWidth <= 0 || s.pageHeight <= 0) return

        // Re-paginate only if no cached pages exist at the current version
        val cachedVersion = s.pageVersions.getOrDefault(index, -1)
        if (s.chapterPages.containsKey(index) && cachedVersion == s.paginationVersion) return

        Log.d("ReaderNav", "PAGINATION START: $index")
        paginationJobMap[index]?.cancel()
        paginationJobMap[index] = viewModelScope.launch(Dispatchers.Default) {
            val blocks = getBlocksSync(index)
            val pages = TextPaginator.paginate(
                blocks = blocks,
                chapterIndex = index,
                pageWidthPx = s.pageWidth,
                pageHeightPx = s.pageHeight,
                settings = s.settings,
                textMeasurer = textMeasurer,
                density = density
            )
            Log.d("ReaderNav", "PAGINATION DONE: $index (pages=${pages.size})")
            _state.update {
                val newPages = it.chapterPages + (index to pages)
                val isCurrentChapter = index == it.currentChapter
                it.copy(
                    chapterPages = newPages,
                    pageVersions = it.pageVersions + (index to it.paginationVersion),
                    pageTotal = if (isCurrentChapter && pages.isNotEmpty()) pages.size else it.pageTotal,
                    pageCurrent = if (isCurrentChapter && pages.isNotEmpty() && it.pageCurrent == 0 && pages.size == 1) 0 else it.pageCurrent
                )
            }
            if (pages.isNotEmpty()) {
                _state.update {
                    val isCurrent = index == it.currentChapter
                    it.copy(
                        positionRestored = if (isCurrent) true else it.positionRestored,
                        lastPositionRestoreTrigger = if (isCurrent) System.currentTimeMillis() else it.lastPositionRestoreTrigger
                    )
                }
            }
        }
    }

    // ─── Events ───

    private fun loadBook(filePath: String, bId: Int, fmt: String) {
        Log.d("ReaderNav", "loadBook: path=$filePath, id=$bId, format=$fmt")
        if (_state.value.book?.filePath == filePath && !_state.value.isLoading) {
            Log.d("ReaderNav", "loadBook: Book already loaded, skipping")
            return
        }

        bookId = bId
        format = fmt
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = null) }

            Log.d("ReaderDiagnostic", "--- Open Book Diagnostic ---")
            Log.d("ReaderDiagnostic", "Book ID: $bId")
            Log.d("ReaderDiagnostic", "Format: $fmt")
            Log.d("ReaderDiagnostic", "Saved FilePath: $filePath")
            Log.d("ReaderDiagnostic", "---")

            try {
                val book = bookParser.parse(filePath, fmt, bId)
                if (book == null) {
                    _state.update { it.copy(isLoading = false, error = application.getString(R.string.reader_open_error)) }
                    return@launch
                }

                // Устанавливаем книгу, но НЕ убираем isLoading — он снимется только
                // после восстановления позиции чтения, чтобы UI не показал главу 0.
                _state.update { it.copy(book = book) }

                // Precompute text content lengths per chapter for accurate progress calculation
                val chapterLengths = book.chapters.map { chapter ->
                    chapter.htmlContent.replace(Regex("<[^>]*>"), "").replace(Regex("\\s+"), " ").trim().length
                }
                val totalContentLength = chapterLengths.sumOf { it.toLong() }
                _state.update { it.copy(chapterLengths = chapterLengths, totalContentLength = totalContentLength) }

                viewModelScope.launch {
                    bookDataRepository.getBookmarksForBook(bId).collect { list ->
                        _state.update { it.copy(bookmarks = list) }
                    }
                }

                val pos = bookDataRepository.getReadingPosition(bId, fmt).first()

                if (pos != null && pos.chapterIndex < book.chapters.size) {
                    _state.update {
                        it.copy(
                            currentChapter = pos.chapterIndex,
                            textOffset = pos.textOffset,
                            scrollY = pos.scrollPosition,
                            scrollOffset = pos.scrollOffset,
                            pageCurrent = pos.pageIndex,
                            positionRestored = true,
                            lastPositionRestoreTrigger = System.currentTimeMillis(),
                            isLoading = false
                        )
                    }
                    getBlocksSync(pos.chapterIndex)
                    preloadChapter(pos.chapterIndex + 1)
                    preloadChapter(pos.chapterIndex - 1)
                } else {
                    _state.update { it.copy(
                        currentChapter = 0,
                        positionRestored = true,
                        isLoading = false
                    ) }
                    getBlocksSync(0)

                    // Auto-skip empty first chapter (cover/title pages)
                    val adjustedChapter = skipPastEmptyChapters(0)
                    preloadChapter(adjustedChapter + 1)
                }
                updateReadingPercentage()
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun goToChapter(index: Int, resetPosition: Boolean = true) {
        val book = _state.value.book ?: return
        if (index < 0 || index >= book.chapters.size) return

        if (resetPosition) {
            _state.update { it.copy(
                currentChapter = index,
                scrollY = 0,
                pageCurrent = 0,
                textOffset = 0,
                scrollOffset = 0,
                positionRestored = true,
                lastPositionRestoreTrigger = System.currentTimeMillis()
            ) }
        } else {
            _state.update { it.copy(
                currentChapter = index,
                positionRestored = true
            ) }
        }

        viewModelScope.launch(Dispatchers.IO) {
            val s = _state.value

            if (!s.chapterBlocks.containsKey(index)) {
                getBlocksSync(index)
            }

            preloadChapter(index + 1)
            preloadChapter(index - 1)

            if (s.pageWidth > 0 && s.pageHeight > 0 &&
                ReaderMode.valueOf(s.settings.readerMode) == ReaderMode.HORIZONTAL) {
                val textMeasurer = s.textMeasurer
                val density = s.density
                if (textMeasurer != null && density != null) {
                    withContext(Dispatchers.Default) {
                        paginateChapterSync(index, textMeasurer, density)
                        paginateChapterSync(index + 1, textMeasurer, density)
                        paginateChapterSync(index - 1, textMeasurer, density)
                    }
                }
            }
        }

        updateReadingPercentage()
        debounceSave()
    }

    private suspend fun paginateChapterSync(index: Int, textMeasurer: TextMeasurer, density: Density) {
        val s = _state.value
        val book = s.book ?: return
        if (index < 0 || index >= book.chapters.size) return
        if (s.pageWidth <= 0 || s.pageHeight <= 0) return
        if (s.chapterPages.containsKey(index)) return

        Log.d("ReaderNav", "PAGINATION SYNC START: $index")
        val blocks = getBlocksSync(index)
        val pages = TextPaginator.paginate(
            blocks = blocks,
            chapterIndex = index,
            pageWidthPx = s.pageWidth,
            pageHeightPx = s.pageHeight,
            settings = s.settings,
            textMeasurer = textMeasurer,
            density = density
        )
        Log.d("ReaderNav", "PAGINATION SYNC DONE: $index (pages=${pages.size})")

        _state.update {
            val currentChapter = it.currentChapter
            val chaptersToKeep = (currentChapter - 10..currentChapter + 10)
            val filteredPages = it.chapterPages.filterKeys { key -> key in chaptersToKeep }

            it.copy(chapterPages = filteredPages + (index to pages))
        }
    }

    private fun handleScrollProgress(percentage: Int, blockIndex: Int, scrollOffset: Int, offset: Int) {
        if (_state.value.isLoading) return
        _state.update { it.copy(
            scrollPercentage = percentage,
            scrollY = blockIndex,
            scrollOffset = scrollOffset,
            textOffset = offset
        ) }
        updateReadingPercentage()
        debounceSave()
    }

    private fun handlePageInfo(current: Int, total: Int, offset: Int) {
        if (_state.value.isLoading) return

        val s = _state.value
        Log.d("ReaderNav", "handlePageInfo: page $current/$total, offset $offset, currentCh=${s.currentChapter}")

        _state.update { it.copy(pageCurrent = current, pageTotal = total, textOffset = offset) }
        updateReadingPercentage()
        debounceSave()
    }

    private fun addBookmark(preview: String) {
        val s = _state.value
        val book = s.book ?: return
        val chapter = book.chapters.getOrNull(s.currentChapter)

        val bookmark = Bookmark(
            bookId = bookId,
            chapterIndex = s.currentChapter,
            chapterTitle = chapter?.title ?: "Глава ${s.currentChapter + 1}",
            pageIndex = s.pageCurrent,
            scrollPosition = s.scrollY,
            scrollOffset = s.scrollOffset,
            textOffset = s.textOffset,
            shortTextPreview = preview.take(150),
            createdAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            bookDataRepository.addBookmark(bookmark)
        }
    }

    private fun removeBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            bookDataRepository.removeBookmark(bookmark)
        }
    }

    private fun goToBookmark(bookmark: Bookmark) {
        viewModelScope.launch(Dispatchers.IO) {
            getBlocksSync(bookmark.chapterIndex)
            preloadChapter(bookmark.chapterIndex + 1)
            preloadChapter(bookmark.chapterIndex - 1)

            val s = _state.value
            if (s.pageWidth > 0 && s.pageHeight > 0 &&
                ReaderMode.valueOf(s.settings.readerMode) == ReaderMode.HORIZONTAL) {
                val tm = s.textMeasurer
                val den = s.density
                if (tm != null && den != null) {
                    paginateChapterSync(bookmark.chapterIndex, tm, den)
                }
            }

            _state.update {
                it.copy(
                    currentChapter = bookmark.chapterIndex,
                    textOffset = bookmark.textOffset,
                    scrollY = bookmark.scrollPosition,
                    scrollOffset = bookmark.scrollOffset,
                    pageCurrent = bookmark.pageIndex,
                    positionRestored = true,
                    lastPositionRestoreTrigger = System.currentTimeMillis(),
                    showBookmarks = false,
                    showChapters = false
                )
            }
            updateReadingPercentage()
            debounceSave()
        }
    }

    private fun nextChapter() {
        val s = _state.value
        val next = s.currentChapter + 1
        val book = s.book ?: return
        if (next < book.chapters.size) {
            goToChapter(next, resetPosition = true)
        }
    }

    private fun previousChapter() {
        val s = _state.value
        val prev = s.currentChapter - 1
        if (prev >= 0) {
            goToChapter(prev, resetPosition = true)
        }
    }

    /**
     * Находит первую непустую главу, начиная с [startChapter].
     * Если startChapter пустая — переключается на следующую главу с контентом.
     */
    private fun skipPastEmptyChapters(startChapter: Int): Int {
        val book = _state.value.book ?: return startChapter
        var current = startChapter
        while (current < book.chapters.size) {
            val blocks = _state.value.chapterBlocks[current]
            if (blocks != null && blocks.isNotEmpty()) {
                // Эта глава не пустая — остаёмся
                if (current != startChapter) {
                    Log.d("ReaderNav", "Skipped empty chapter $startChapter, moving to chapter $current")
                    _state.update {
                        it.copy(
                            currentChapter = current,
                            textOffset = 0,
                            scrollY = 0,
                            pageCurrent = 0,
                            scrollOffset = 0,
                            positionRestored = true,
                            lastPositionRestoreTrigger = System.currentTimeMillis()
                        )
                    }
                }
                return current
            }
            current++
        }
        // Все оставшиеся главы пустые — возвращаем startChapter
        return startChapter
    }

    private fun updateReadingPercentage() {
        val s = _state.value
        val book = s.book ?: return
        if (book.chapters.isEmpty()) return
        val mode = ReaderMode.valueOf(s.settings.readerMode)
        val chapterPct = if (mode == ReaderMode.HORIZONTAL) {
            if (s.pageTotal > 0) s.pageCurrent * 100f / s.pageTotal else 0f
        } else {
            s.scrollPercentage.toFloat()
        }

        val pct = if (s.totalContentLength > 0 && s.chapterLengths.size == book.chapters.size) {
            // Content-based progress: sum of chars in completed chapters + progress in current chapter
            val consumedBefore = s.chapterLengths.take(s.currentChapter).sumOf { it.toLong() }
            val currentChapterLen = s.chapterLengths.getOrElse(s.currentChapter) { 0 }
            val consumedInChapter = (currentChapterLen * chapterPct / 100f).toLong()
            val totalConsumed = consumedBefore + consumedInChapter
            ((totalConsumed * 100) / s.totalContentLength).toInt().coerceIn(0, 100)
        } else {
            // Fallback to chapter-based when lengths aren't available
            ((s.currentChapter + chapterPct / 100f) / book.chapters.size * 100).toInt().coerceIn(0, 100)
        }
        _state.update { it.copy(readingPercentage = pct) }
    }

    private fun debounceSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            val s = _state.value
            // Не сохраняем пока позиция не восстановлена
            if (!s.positionRestored) return@launch
            if (bookId >= 0 && !s.isLoading && s.book != null) {
                savePositionInternal(s)
            }
        }
    }

    fun savePositionNow() {
        val s = _state.value
        if (bookId < 0 || !s.positionRestored || s.isLoading || s.book == null) return
        saveJob?.cancel()
        saveJob = saveScope.launch(NonCancellable) {
            savePositionInternal(s)
        }
    }

    /**
     * Сохраняет текущую позицию чтения в БД.
     * Можно вызывать из любого потока.
     */
    private suspend fun savePositionInternal(s: ReaderState) {
        val pos = ReadingPosition(
            bookId = bookId,
            format = format,
            chapterIndex = s.currentChapter,
            pageIndex = s.pageCurrent,
            scrollPosition = s.scrollY,
            scrollOffset = s.scrollOffset,
            textOffset = s.textOffset,
            lastUpdated = System.currentTimeMillis()
        )
        bookDataRepository.saveReadingPosition(pos)
        bookDataRepository.updateLastReadDate(bookId, format)
        bookDataRepository.updateReadingProgress(bookId, format, s.readingPercentage)
    }

    private fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        val new = transform(_state.value.settings)
        _state.update { it.copy(settings = new) }
        viewModelScope.launch { dataStoreManager.saveReaderSettings(new) }
    }
}
