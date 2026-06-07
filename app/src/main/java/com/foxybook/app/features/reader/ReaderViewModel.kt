package com.foxybook.app.features.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxybook.app.core.datastore.DataStoreManager
import com.foxybook.app.core.models.ParsedBook
import com.foxybook.app.core.models.ReaderMode
import com.foxybook.app.core.models.ReaderSettings
import com.foxybook.app.core.models.ReaderTheme
import com.foxybook.app.core.models.ReadingPosition
import com.foxybook.app.core.reader.BookParser
import com.foxybook.app.core.reader.ContentBlock
import com.foxybook.app.core.reader.HtmlBlockParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ReaderState(
    val book: ParsedBook? = null,
    val currentChapter: Int = 0,

    // Chapter block cache (current + prev + next)
    val chapterBlocks: Map<Int, List<ContentBlock>> = emptyMap(),

    // Scroll mode
    val scrollY: Int = 0,
    val scrollPercentage: Int = 0,

    // Page mode
    val pageCurrent: Int = 0,
    val pageTotal: Int = 1,

    // Common
    val readingPercentage: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
    val settings: ReaderSettings = ReaderSettings(),
    val showSettings: Boolean = false,
    val showChapters: Boolean = false,
    val isImmersive: Boolean = false,
    val positionRestored: Boolean = false
)

sealed interface ReaderEvent {
    data class LoadBook(val filePath: String, val bookId: Int, val format: String) : ReaderEvent
    data class ChapterChanged(val index: Int) : ReaderEvent
    data class ScrollProgress(val percentage: Int, val scrollY: Int) : ReaderEvent
    data class PageInfo(val current: Int, val total: Int) : ReaderEvent
    data object NextChapter : ReaderEvent
    data object PreviousChapter : ReaderEvent
    data class FontSizeChanged(val size: Int) : ReaderEvent
    data class LineHeightChanged(val height: Float) : ReaderEvent
    data class MarginsChanged(val margins: Int) : ReaderEvent
    data class ReaderModeChanged(val mode: ReaderMode) : ReaderEvent
    data class ReaderThemeChanged(val theme: ReaderTheme) : ReaderEvent
    data object ToggleSettings : ReaderEvent
    data object ToggleChapters : ReaderEvent
    data object ToggleImmersive : ReaderEvent
}

class ReaderViewModel(
    private val bookParser: BookParser,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private var bookId: Int = -1
    private var format: String = ""
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            dataStoreManager.readerSettings.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
    }

    fun onEvent(event: ReaderEvent) {
        when (event) {
            is ReaderEvent.LoadBook -> loadBook(event.filePath, event.bookId, event.format)
            is ReaderEvent.ChapterChanged -> goToChapter(event.index)
            is ReaderEvent.ScrollProgress -> handleScrollProgress(event.percentage, event.scrollY)
            is ReaderEvent.PageInfo -> handlePageInfo(event.current, event.total)
            is ReaderEvent.NextChapter -> nextChapter()
            is ReaderEvent.PreviousChapter -> previousChapter()
            is ReaderEvent.FontSizeChanged -> updateSettings { it.copy(fontSize = event.size) }
            is ReaderEvent.LineHeightChanged -> updateSettings { it.copy(lineHeight = event.height) }
            is ReaderEvent.MarginsChanged -> updateSettings { it.copy(margins = event.margins) }
            is ReaderEvent.ReaderModeChanged -> updateSettings { it.copy(readerMode = event.mode.name) }
            is ReaderEvent.ReaderThemeChanged -> updateSettings { it.copy(readerTheme = event.theme.name) }
            is ReaderEvent.ToggleSettings -> _state.update {
                it.copy(showSettings = !it.showSettings, showChapters = false)
            }
            is ReaderEvent.ToggleChapters -> _state.update {
                it.copy(showChapters = !it.showChapters, showSettings = false)
            }
            is ReaderEvent.ToggleImmersive -> _state.update {
                it.copy(isImmersive = !it.isImmersive)
            }
        }
    }

    // ─── Block access ───

    fun getBlocks(chapterIndex: Int): List<ContentBlock> {
        return _state.value.chapterBlocks[chapterIndex] ?: parseChapterBlocks(chapterIndex)
    }

    private fun parseChapterBlocks(chapterIndex: Int): List<ContentBlock> {
        val book = _state.value.book ?: return emptyList()
        val chapter = book.chapters.getOrNull(chapterIndex) ?: return emptyList()
        val blocks = HtmlBlockParser.parse(chapter.htmlContent)
        _state.update { it.copy(chapterBlocks = it.chapterBlocks + (chapterIndex to blocks)) }
        return blocks
    }

    // ─── Private ───

    private fun loadBook(filePath: String, bId: Int, fmt: String) {
        bookId = bId
        format = fmt
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    _state.update { it.copy(isLoading = false, error = "Файл не найден"); return@launch }
                }
                val book = bookParser.parse(file)
                if (book == null || book.chapters.isEmpty()) {
                    _state.update { it.copy(isLoading = false, error = "Не удалось открыть книгу"); return@launch }
                }
                _state.update { it.copy(book = book, isLoading = false) }

                // Preload current + adjacent chapter blocks
                preloadChapterBlocks(0)
                preloadChapterBlocks(1)
                preloadChapterBlocks(-1)

                // Restore reading position
                try {
                    val pos = dataStoreManager.readingPositionForBook(bId, fmt).first()
                    val currentBook = _state.value.book
                    if (pos != null && currentBook != null && pos.chapterIndex < currentBook.chapters.size) {
                        _state.update {
                            it.copy(
                                currentChapter = pos.chapterIndex,
                                scrollY = pos.scrollPosition,
                                positionRestored = true
                            )
                        }
                        preloadChapterBlocks(pos.chapterIndex - 1)
                        preloadChapterBlocks(pos.chapterIndex + 1)
                    }
                } catch (_: Exception) {}

                updateReadingPercentage()
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun goToChapter(index: Int) {
        val book = _state.value.book ?: return
        if (index < 0 || index >= book.chapters.size) return
        _state.update { it.copy(currentChapter = index, scrollY = 0, positionRestored = false) }
        preloadChapterBlocks(index - 1)
        preloadChapterBlocks(index + 1)
        updateReadingPercentage()
        debounceSave(0)
    }

    private fun nextChapter() {
        val next = _state.value.currentChapter + 1
        val book = _state.value.book ?: return
        if (next < book.chapters.size) goToChapter(next)
    }

    private fun previousChapter() {
        val prev = _state.value.currentChapter - 1
        if (prev >= 0) goToChapter(prev)
    }

    private fun preloadChapterBlocks(chapterIndex: Int) {
        val book = _state.value.book ?: return
        if (chapterIndex < 0 || chapterIndex >= book.chapters.size) return
        if (_state.value.chapterBlocks.containsKey(chapterIndex)) return
        viewModelScope.launch(Dispatchers.IO) {
            parseChapterBlocks(chapterIndex)
        }
    }

    private fun handleScrollProgress(percentage: Int, scrollY: Int) {
        _state.update { it.copy(scrollPercentage = percentage, scrollY = scrollY) }
        updateReadingPercentage()
        debounceSave(scrollY)
        // Preload next chapter when near end
        if (percentage > 75) {
            preloadChapterBlocks(_state.value.currentChapter + 1)
        }
    }

    private fun handlePageInfo(current: Int, total: Int) {
        _state.update { it.copy(pageCurrent = current, pageTotal = total) }
        updateReadingPercentage()
        debounceSave(current)
        // Preload next chapter when on last 3 pages
        if (total > 0 && current >= total - 3) {
            preloadChapterBlocks(_state.value.currentChapter + 1)
        }
        // Preload prev chapter when on first 2 pages
        if (current <= 1) {
            preloadChapterBlocks(_state.value.currentChapter - 1)
        }
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
        val pct = ((s.currentChapter + chapterPct / 100f) / book.chapters.size * 100).toInt().coerceIn(0, 100)
        _state.update { it.copy(readingPercentage = pct) }
    }

    private fun debounceSave(scrollOrPage: Int) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(800)
            if (bookId >= 0) {
                dataStoreManager.saveReadingPosition(
                    ReadingPosition(
                        bookId = bookId,
                        format = format,
                        chapterIndex = _state.value.currentChapter,
                        scrollPosition = scrollOrPage,
                        lastUpdated = System.currentTimeMillis()
                    )
                )
                dataStoreManager.updateLastReadDate(bookId, format)
            }
        }
    }

    private fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        val new = transform(_state.value.settings)
        _state.update { it.copy(settings = new) }
        viewModelScope.launch { dataStoreManager.saveReaderSettings(new) }
    }

    override fun onCleared() {
        super.onCleared()
        saveJob?.cancel()
    }
}
