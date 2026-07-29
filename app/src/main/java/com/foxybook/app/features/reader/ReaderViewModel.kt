package com.foxybook.app.features.reader

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxybook.app.core.database.BookDataRepository
import com.foxybook.app.core.datastore.DataStoreManager
import com.foxybook.app.core.models.Bookmark
import com.foxybook.app.core.models.ReaderMode
import com.foxybook.app.core.models.ReaderSettings
import com.foxybook.app.core.reader.BookParser
import com.foxybook.app.core.reader.ContentBlock
import com.foxybook.app.core.tts.TtsManager
import com.foxybook.app.features.reader.manager.ChapterNavigationManager
import com.foxybook.app.features.reader.manager.PaginationManager
import com.foxybook.app.features.reader.manager.ReadingPositionManager
import com.foxybook.app.features.reader.manager.SearchManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class ReaderViewModel(
    private val bookParser: BookParser,
    private val dataStoreManager: DataStoreManager,
    private val bookDataRepository: BookDataRepository,
    private val application: Application
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    val ttsManager = TtsManager(application)

    // ─── Managers ───
    val positionManager = ReadingPositionManager(bookDataRepository, dataStoreManager)
    val chapterManager = ChapterNavigationManager(bookParser, bookDataRepository)
    val paginationManager = PaginationManager()
    val searchManager = SearchManager()

    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Sync TTS state
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
        positionManager.stopSaveJob()

        // Сохраняем позицию принудительно и синхронно, без таймаута
        kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                val s = _state.value
                if (chapterManager.getBookId() >= 0 && s.book != null) {
                    positionManager.savePosition(chapterManager.getBookId(), chapterManager.getFormat(), s)
                }
            }
        }

        paginationManager.cancelAllJobs()
        saveScope.cancel()
        ttsManager.destroy()
        super.onCleared()
    }

    fun onEvent(event: ReaderEvent) {
        when (event) {
            // ─── Book Loading ───
            is ReaderEvent.LoadBook -> {
                chapterManager.setBookInfo(event.bookId, event.format)
                viewModelScope.launch(Dispatchers.IO) {
                    chapterManager.loadBook(event.filePath, event.bookId, event.format, _state.value, _state)
                    positionManager.updateReadingPercentage(_state.value)
                    // Сохраняем начальную позицию сразу после загрузки книги
                    val s = _state.value
                    if (s.positionRestored) {
                        positionManager.savePosition(event.bookId, event.format, s)
                    }
                }
            }

            // ─── Chapter Navigation ───
            is ReaderEvent.ChapterChanged -> {
                val newState = chapterManager.goToChapter(event.index, _state.value, _state)
                _state.update { newState }
                viewModelScope.launch(Dispatchers.IO) {
                    chapterManager.getBlocksSync(event.index, _state.value, _state)
                    chapterManager.preloadChapter(event.index + 1, _state.value, viewModelScope)
                    chapterManager.preloadChapter(event.index - 1, _state.value, viewModelScope)
                    if (_state.value.pageWidth > 0 && _state.value.pageHeight > 0 &&
                        ReaderMode.safeValueOf(_state.value.settings.readerMode) == ReaderMode.HORIZONTAL) {
                        val tm = _state.value.textMeasurer
                        val den = _state.value.density
                        if (tm != null && den != null) {
                            withContext(Dispatchers.Default) {
                                paginationManager.paginateChapterSync(event.index, _state.value, _state) { getBlocks(it) }
                                paginationManager.paginateChapterSync(event.index + 1, _state.value, _state) { getBlocks(it) }
                                paginationManager.paginateChapterSync(event.index - 1, _state.value, _state) { getBlocks(it) }
                            }
                        }
                    }
                }
                _state.update { positionManager.updateReadingPercentage(it) }
                positionManager.debounceSave(chapterManager.getBookId(), chapterManager.getFormat(), _state, viewModelScope)
            }
            is ReaderEvent.NextChapter -> {
                val next = chapterManager.nextChapter(_state.value)
                if (next >= 0) onEvent(ReaderEvent.ChapterChanged(next))
            }
            is ReaderEvent.PreviousChapter -> {
                val prev = chapterManager.previousChapter(_state.value)
                if (prev >= 0) onEvent(ReaderEvent.ChapterChanged(prev))
            }

            // ─── Scroll / Page ───
            is ReaderEvent.ScrollProgress -> {
                val updated = positionManager.trackScroll(event.percentage, event.blockIndex, event.scrollOffset, event.offset, _state.value)
                _state.update { updated }
                _state.update { positionManager.updateReadingPercentage(it) }
                val bid = chapterManager.getBookId()
                val fmt = chapterManager.getFormat()
                if (bid >= 0 && _state.value.positionRestored) viewModelScope.launch(Dispatchers.IO) {
                    positionManager.savePosition(bid, fmt, _state.value)
                }
            }
            is ReaderEvent.PageInfo -> {
                val updated = positionManager.trackPageInfo(event.current, event.total, event.offset, _state.value)
                _state.update { updated }
                _state.update { positionManager.updateReadingPercentage(it) }
                val bid = chapterManager.getBookId()
                val fmt = chapterManager.getFormat()
                if (bid >= 0 && _state.value.positionRestored) viewModelScope.launch(Dispatchers.IO) {
                    positionManager.savePosition(bid, fmt, _state.value)
                }
            }

            // ─── Dimensions / Pagination ───
            is ReaderEvent.UpdatePageDimensions -> {
                val updated = paginationManager.updateDimensions(event.width, event.height, event.textMeasurer, event.density, _state.value)
                _state.update { updated }
                val s = _state.value
                if (s.pageWidth > 0 && s.pageHeight > 0) {
                    paginationManager.paginateChapter(s.currentChapter, s, viewModelScope, { getBlocks(it) }, _state)
                    paginationManager.paginateChapter(s.currentChapter + 1, s, viewModelScope, { getBlocks(it) }, _state)
                    paginationManager.paginateChapter(s.currentChapter - 1, s, viewModelScope, { getBlocks(it) }, _state)
                }
                _state.update { it.copy(isCalculatingPages = true) }
                paginationManager.refreshTotalBookPages(_state.value, _state)
            }

            // ─── Settings ───
            is ReaderEvent.FontSizeChanged -> updateSettings { it.copy(fontSize = event.size) }
            is ReaderEvent.LineHeightChanged -> updateSettings { it.copy(lineHeight = event.height) }
            is ReaderEvent.MarginsChanged -> updateSettings { it.copy(margins = event.margins) }
            ReaderEvent.ToggleProgressDisplay -> updateSettings { it.copy(showProgressAsPercentage = !it.showProgressAsPercentage) }
            is ReaderEvent.ReaderModeChanged -> {
                updateSettings { it.copy(readerMode = event.mode.name) }
                _state.update { it.copy(positionRestored = true, lastPositionRestoreTrigger = System.currentTimeMillis()) }
            }
            is ReaderEvent.ReaderThemeChanged -> updateSettings { it.copy(readerTheme = event.theme.name) }

            // ─── UI Toggles ───
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
            is ReaderEvent.ToggleImmersive -> _state.update { it.copy(isImmersive = !it.isImmersive) }

            // ─── Bookmarks ───
            is ReaderEvent.AddBookmark -> addBookmark(event.preview)
            is ReaderEvent.RemoveBookmark -> removeBookmark(event.bookmark)
            is ReaderEvent.GoToBookmark -> goToBookmark(event.bookmark)

            // ─── TTS ───
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
                updateSettings { it.copy(ttsLanguage = event.language, ttsVoice = null) }
                restartTtsBlockIfActive()
            }
            is ReaderEvent.SetTtsVoice -> {
                updateSettings { it.copy(ttsVoice = event.voiceId) }
                restartTtsBlockIfActive()
            }
            is ReaderEvent.SetTtsEngine -> {
                updateSettings { it.copy(ttsEngine = event.packageName, ttsVoice = null, ttsLanguage = null) }
                ttsManager.switchEngine(event.packageName)
            }

            // ─── Brightness ───
            is ReaderEvent.SetBrightness -> updateSettings { it.copy(brightness = event.brightness) }
            ReaderEvent.ResetBrightness -> updateSettings { it.copy(brightness = -1f) }

            // ─── Sleep Timer ───
            is ReaderEvent.SetSleepTimer -> ttsManager.setSleepTimer(event.minutes)
            ReaderEvent.CancelSleepTimer -> ttsManager.cancelSleepTimer()

            // ─── Search ───
            ReaderEvent.ToggleSearch -> _state.update {
                it.copy(isSearchVisible = !it.isSearchVisible, searchResults = emptyList(), searchQuery = "", searchCurrentIndex = -1)
            }
            is ReaderEvent.SearchQueryChanged -> searchManager.performSearch(event.query, _state.value, ::getBlocks, _state)
            is ReaderEvent.SelectSearchResult -> {
                viewModelScope.launch {
                    searchManager.goToSearchResult(event.index, _state.value, _state, ::getBlocks)
                    _state.update { positionManager.updateReadingPercentage(it) }
                }
            }
            ReaderEvent.NextSearchResult -> searchManager.navigateSearchResults(1, _state.value, _state, ::getBlocks)
            ReaderEvent.PreviousSearchResult -> searchManager.navigateSearchResults(-1, _state.value, _state, ::getBlocks)
        }
    }

    // ─── Content Access (delegates to ChapterNavigationManager) ───

    fun getBlocks(chapterIndex: Int): List<ContentBlock> {
        val cached = _state.value.chapterBlocks[chapterIndex]
        if (cached != null) return cached
        chapterManager.preloadChapter(chapterIndex, _state.value, viewModelScope)
        return emptyList()
    }

    fun ensurePaginated(chapterIndex: Int) {
        paginationManager.ensurePaginated(chapterIndex, _state.value, viewModelScope, ::getBlocks, _state)
    }

    // ─── Private TTS Helpers ───

    private fun calculateCurrentBlockIndex(): Int {
        val s = _state.value
        val mode = ReaderMode.safeValueOf(s.settings.readerMode)
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

    private fun restartTtsBlockIfActive() {
        val s = _state.value
        if (!s.isSpeaking && !s.isPaused) return
        if (s.currentTtsChapter < 0 || s.currentTtsBlockIndex < 0) return
        ttsManager.stop()
        speakBlock(s.currentTtsChapter, s.currentTtsBlockIndex)
    }

    private fun speakBlock(chapterIndex: Int, blockIndex: Int) {
        viewModelScope.launch {
            val s = _state.value
            var ch = chapterIndex
            var bl = blockIndex

            val blocks = getBlocks(ch)
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
                    bl = (getBlocks(ch).size - 1).coerceAtLeast(0)
                } else {
                    bl = 0
                }
            }

            val currentBlocks = getBlocks(ch)
            val block = currentBlocks.getOrNull(bl)
            val text = block?.getTextContent() ?: ""

            if (text.isBlank()) {
                speakBlock(ch, bl + 1)
                return@launch
            }

            _state.update { it.copy(currentTtsChapter = ch, currentTtsBlockIndex = bl) }
            updateSettings { it.copy(lastTtsChapter = ch, lastTtsBlockIndex = bl) }

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

    // ─── Bookmarks ───

    private fun addBookmark(preview: String) {
        val s = _state.value
        val book = s.book ?: return
        val chapter = book.chapters.getOrNull(s.currentChapter)

        val bookmark = Bookmark(
            bookId = chapterManager.getBookId(),
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
            chapterManager.getBlocksSync(bookmark.chapterIndex, _state.value, _state)
            chapterManager.preloadChapter(bookmark.chapterIndex + 1, _state.value, viewModelScope)
            chapterManager.preloadChapter(bookmark.chapterIndex - 1, _state.value, viewModelScope)

            val s = _state.value
            if (s.pageWidth > 0 && s.pageHeight > 0 &&
                ReaderMode.safeValueOf(s.settings.readerMode) == ReaderMode.HORIZONTAL) {
                val tm = s.textMeasurer
                val den = s.density
                if (tm != null && den != null) {
                    paginationManager.paginateChapterSync(bookmark.chapterIndex, s, _state) { getBlocks(it) }
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
            _state.update { positionManager.updateReadingPercentage(it) }
            positionManager.debounceSave(chapterManager.getBookId(), chapterManager.getFormat(), _state, viewModelScope)
        }
    }

    // ─── Settings ───

    fun savePositionNow() {
        positionManager.savePositionNow(chapterManager.getBookId(), chapterManager.getFormat(), _state, saveScope)
    }

    private fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        val new = transform(_state.value.settings)
        _state.update { it.copy(settings = new) }
        viewModelScope.launch { dataStoreManager.saveReaderSettings(new) }
    }
}
