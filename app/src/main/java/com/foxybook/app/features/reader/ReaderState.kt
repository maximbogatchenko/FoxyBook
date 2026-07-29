package com.foxybook.app.features.reader

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density
import com.foxybook.app.core.models.Bookmark
import com.foxybook.app.core.models.ReaderMode
import com.foxybook.app.core.models.ReaderSettings
import com.foxybook.app.core.models.ParsedBook
import com.foxybook.app.core.reader.ContentBlock
import com.foxybook.app.core.reader.TextPaginator
import com.foxybook.app.core.tts.VoiceInfo
import com.foxybook.app.core.tts.EngineInfo

data class SearchResult(
    val text: String,
    val chapterIndex: Int,
    val blockIndex: Int,
    val matchStart: Int,
    val matchEnd: Int,
    val chapterTitle: String = ""
)

data class ReaderState(
    val book: ParsedBook? = null,
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

    // Pagination version
    val paginationVersion: Int = 0,
    val pageVersions: Map<Int, Int> = emptyMap(),

    // Chapter titles extracted from content
    val chapterTitlesExtracted: Map<Int, String> = emptyMap(),

    // Content lengths for progress calculation
    val chapterLengths: List<Int> = emptyList(),
    val totalContentLength: Long = 0L,
    val totalBookPages: Int = 0,
    val isCalculatingPages: Boolean = false,

    // TTS
    val isSpeaking: Boolean = false,
    val isPaused: Boolean = false,
    val availableVoices: List<VoiceInfo> = emptyList(),
    val availableLanguages: List<String> = emptyList(),
    val showTtsControls: Boolean = false,
    val isSelectingTtsStartPosition: Boolean = false,
    val currentTtsChapter: Int = -1,
    val currentTtsBlockIndex: Int = -1,
    val sleepTimerRemainingSeconds: Long = 0L,
    val currentEngine: String? = null,
    val availableEngines: List<EngineInfo> = emptyList(),

    // Search
    val isSearchVisible: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val searchCurrentIndex: Int = -1,
    val searchIsSearching: Boolean = false
)

sealed interface ReaderEvent {
    data class LoadBook(val filePath: String, val bookId: Int, val format: String) : ReaderEvent
    data class ChapterChanged(val index: Int, val resetPosition: Boolean = true) : ReaderEvent
    data class ScrollProgress(val percentage: Int, val blockIndex: Int, val scrollOffset: Int, val offset: Int) : ReaderEvent
    data class PageInfo(val current: Int, val total: Int, val offset: Int) : ReaderEvent
    data class UpdatePageDimensions(val width: Int, val height: Int, val textMeasurer: TextMeasurer, val density: Density) : ReaderEvent
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

    // Search
    data object ToggleSearch : ReaderEvent
    data class SearchQueryChanged(val query: String) : ReaderEvent
    data class SelectSearchResult(val index: Int) : ReaderEvent
    data object NextSearchResult : ReaderEvent
    data object PreviousSearchResult : ReaderEvent
}
