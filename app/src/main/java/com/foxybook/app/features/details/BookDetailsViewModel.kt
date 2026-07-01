package com.foxybook.app.features.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.foxybook.app.core.database.BookDataRepository
import com.foxybook.app.core.datastore.DataStoreManager
import com.foxybook.app.core.models.Book
import com.foxybook.app.core.models.BookFormat
import com.foxybook.app.core.models.BookInfo
import com.foxybook.app.core.models.BookDetailsUiState
import com.foxybook.app.core.models.BookSource
import com.foxybook.app.core.models.ReadingPosition
import com.foxybook.app.core.models.Bookmark
import com.foxybook.app.core.models.DownloadProgress
import com.foxybook.app.core.models.DownloadStatus
import com.foxybook.app.core.network.OkHttpClientProvider
import com.foxybook.app.domain.usecases.DownloadBookUseCase
import com.foxybook.app.domain.usecases.GetBookInfoUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request

data class BookDetailsState(
    val uiState: BookDetailsUiState = BookDetailsUiState.Loading,
    val downloads: Map<BookFormat, DownloadProgress> = BookFormat.entries.associateWith { DownloadProgress() },
    val bookmarks: List<Bookmark> = emptyList(),
    val showFolderErrorDialog: Boolean = false,
    val availableFormats: List<BookFormat> = emptyList(),
    val selectedFormat: BookFormat = BookFormat.EPUB,
    val showFormatSelector: Boolean = false,
    val formatAvailability: Map<BookFormat, Boolean> = BookFormat.entries.associateWith { true },
    val formatsLoading: Boolean = false
)

sealed interface BookDetailsEvent {
    data class LoadBook(val id: Int, val initialData: Book? = null) : BookDetailsEvent
    data class Download(val format: BookFormat) : BookDetailsEvent
    data class RemoveBookmark(val bookmark: Bookmark) : BookDetailsEvent
    data class JumpToBookmark(val bookmark: Bookmark, val format: String) : BookDetailsEvent
    data object DismissFolderError : BookDetailsEvent
    data object DownloadPrimary : BookDetailsEvent
    data class SelectFormat(val format: BookFormat) : BookDetailsEvent
    data object ToggleFormatSelector : BookDetailsEvent
    data object CancelDownload : BookDetailsEvent
}

class BookDetailsViewModel(
    private val getBookInfoUseCase: GetBookInfoUseCase,
    private val downloadBookUseCase: DownloadBookUseCase,
    private val dataStoreManager: DataStoreManager,
    private val bookDataRepository: BookDataRepository,
    private val networkProvider: OkHttpClientProvider
) : ViewModel() {

    private val TAG = "BOOK_DETAILS_VM"

    private val _state = MutableStateFlow(BookDetailsState())
    val state: StateFlow<BookDetailsState> = _state.asStateFlow()

    private val checkClient: OkHttpClient by lazy {
        networkProvider.createClient(connectSeconds = 5, readSeconds = 5, writeSeconds = 5)
    }

    private var downloadJob: kotlinx.coroutines.Job? = null

    fun onEvent(event: BookDetailsEvent) {
        when (event) {
            is BookDetailsEvent.LoadBook -> loadBookInfo(event.id, event.initialData)
            is BookDetailsEvent.Download -> downloadBook(event.format)
            is BookDetailsEvent.RemoveBookmark -> removeBookmark(event.bookmark)
            is BookDetailsEvent.JumpToBookmark -> jumpToBookmark(event.bookmark, event.format)
            is BookDetailsEvent.DismissFolderError -> _state.update { it.copy(showFolderErrorDialog = false) }
            is BookDetailsEvent.DownloadPrimary -> downloadBook(_state.value.selectedFormat)
            is BookDetailsEvent.SelectFormat -> {
                _state.update { it.copy(selectedFormat = event.format, showFormatSelector = false) }
            }
            is BookDetailsEvent.ToggleFormatSelector -> {
                _state.update { it.copy(showFormatSelector = !it.showFormatSelector) }
            }
            is BookDetailsEvent.CancelDownload -> {
                downloadJob?.cancel()
                downloadJob = null
            }
        }
    }

    private fun loadBookInfo(id: Int, initialData: Book? = null) {
        // Всегда начинаем с Loading — показываем анимацию загрузки
        _state.update { it.copy(uiState = BookDetailsUiState.Loading) }

        viewModelScope.launch {
            bookDataRepository.getBookmarksForBook(id).collect { list ->
                _state.update { it.copy(bookmarks = list) }
            }
        }

        viewModelScope.launch {
            bookDataRepository.getAllBooks().collect { books ->
                val bookDownloads = books.filter { it.id == id }
                _state.update { s ->
                    val newDownloads = s.downloads.toMutableMap()
                    bookDownloads.forEach { libBook ->
                        BookFormat.entries.find { it.extension == libBook.format }?.let { format ->
                            newDownloads[format] = DownloadProgress(
                                status = DownloadStatus.DOWNLOADED,
                                percent = 100,
                                filePath = libBook.filePath
                            )
                        }
                    }
                    s.copy(downloads = newDownloads)
                }
            }
        }

        viewModelScope.launch {
            try {
                val info = withTimeout(12_000) { getBookInfoUseCase(id) }

                // Даже если получили только частичную информацию — используем её с initialData
                val finalInfo = if (info != null) {
                    info.copy(
                        title = initialData?.title ?: info.title,
                        author = initialData?.author ?: info.author,
                        coverUrl = initialData?.coverUrl ?: info.coverUrl
                    )
                } else {
                    initialData?.let { d ->
                        BookInfo(
                            id = d.id, title = d.title, author = d.author,
                            description = "", genres = emptyList(),
                            coverUrl = d.coverUrl, availableFormats = d.formats
                        )
                    }
                }

                if (finalInfo != null) {
                    // Проверяем форматы и показываем всю информацию сразу
                    checkAvailableFormats(id, info, initialData)
                    _state.update {
                        it.copy(uiState = BookDetailsUiState.Success(finalInfo))
                    }
                } else {
                    _state.update { it.copy(uiState = BookDetailsUiState.Error("Книга не найдена")) }
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "loadBookInfo | timeout for book $id")
                // Показываем хотя бы то, что есть
                initialData?.let { d ->
                    val fallback = BookInfo(
                        id = d.id, title = d.title, author = d.author,
                        description = "", genres = emptyList(),
                        coverUrl = d.coverUrl, availableFormats = d.formats
                    )
                    _state.update { it.copy(uiState = BookDetailsUiState.Success(fallback)) }
                    checkAvailableFormats(id, null, initialData)
                } ?: _state.update { it.copy(uiState = BookDetailsUiState.Error("Таймаут загрузки")) }
            } catch (e: Exception) {
                Log.e(TAG, "loadBookInfo | error", e)
                initialData?.let { d ->
                    val fallback = BookInfo(
                        id = d.id, title = d.title, author = d.author,
                        description = "", genres = emptyList(),
                        coverUrl = d.coverUrl, availableFormats = d.formats
                    )
                    _state.update { it.copy(uiState = BookDetailsUiState.Success(fallback)) }
                    checkAvailableFormats(id, null, initialData)
                } ?: _state.update { it.copy(uiState = BookDetailsUiState.Error(e.message ?: "Ошибка загрузки")) }
            }
        }
    }

    private fun downloadBook(format: BookFormat) {
        val info = (_state.value.uiState as? BookDetailsUiState.Success)?.bookInfo ?: return

        viewModelScope.launch {
            setProgress(format, DownloadProgress(status = DownloadStatus.DOWNLOADING, percent = 0))
            try {
                // Добавляем таймаут 2 минуты для скачивания
                val result = withTimeout(120_000) {
                    downloadBookUseCase(
                        id = info.id,
                        title = info.title,
                        author = info.author,
                        format = format,
                        coverUrl = info.coverUrl
                    ) { progress ->
                        setProgress(format, DownloadProgress(
                            status = DownloadStatus.DOWNLOADING,
                            percent = if (progress < 0f) -1 else (progress * 100).toInt().coerceIn(0, 100)
                        ))
                    }
                }

                if (result.isSuccess) {
                    val fp = result.getOrNull() ?: ""
                    setProgress(format, DownloadProgress(
                        status = DownloadStatus.DOWNLOADED, percent = 100, filePath = fp
                    ))
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Ошибка"
                    if (error.contains("Chosen folder", ignoreCase = true)) {
                        _state.update { it.copy(showFolderErrorDialog = true) }
                    }
                    setProgress(format, DownloadProgress(
                        status = DownloadStatus.ERROR, error = error
                    ))
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "downloadBook: timeout for $format")
                setProgress(format, DownloadProgress(
                    status = DownloadStatus.ERROR, error = "Время ожидания истекло. Проверьте подключение к интернету"
                ))
            } catch (e: Exception) {
                Log.e(TAG, "downloadBook: exception", e)
                setProgress(format, DownloadProgress(
                    status = DownloadStatus.ERROR, error = e.message ?: "Ошибка скачивания"
                ))
            }
        }
    }

    private fun setProgress(format: BookFormat, progress: DownloadProgress) {
        _state.update { s -> s.copy(downloads = s.downloads + (format to progress)) }
    }

    private fun removeBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            bookDataRepository.removeBookmark(bookmark)
        }
    }

    private fun jumpToBookmark(bookmark: Bookmark, format: String) {
        viewModelScope.launch {
            bookDataRepository.saveReadingPosition(
                ReadingPosition(
                    bookId = bookmark.bookId,
                    format = format,
                    chapterIndex = bookmark.chapterIndex,
                    pageIndex = bookmark.pageIndex,
                    scrollPosition = bookmark.scrollPosition,
                    scrollOffset = bookmark.scrollOffset,
                    textOffset = bookmark.textOffset,
                    lastUpdated = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun preferredFormat(available: List<BookFormat>, defaultExt: String): BookFormat {
        val default = BookFormat.entries.firstOrNull { it.extension == defaultExt }
        if (default != null && default in available) return default
        return available.firstOrNull { it == BookFormat.EPUB }
            ?: available.firstOrNull()
            ?: BookFormat.EPUB
    }

    private suspend fun checkAvailableFormats(bookId: Int, bookInfo: BookInfo? = null, initialData: Book? = null) {
        val defaultExt = dataStoreManager.defaultFormat.first()

        // Приоритет 1: форматы из HTML страницы книги (самые точные)
        val fromHtml = bookInfo?.availableFormats
            ?.mapNotNull { ext -> BookFormat.entries.firstOrNull { it.extension == ext } }

        if (!fromHtml.isNullOrEmpty()) {
            val preferred = preferredFormat(fromHtml, defaultExt)
            _state.update {
                it.copy(
                    availableFormats = fromHtml,
                    formatAvailability = fromHtml.associateWith { true },
                    selectedFormat = preferred,
                    formatsLoading = false
                )
            }
            Log.d(TAG, "checkAvailableFormats | from HTML: ${fromHtml.map { it.extension }}, preferred: ${preferred.extension}")
            return
        }

        // Приоритет 2: форматы из OPDS feed
        val fromOpds = initialData?.formats
            ?.mapNotNull { ext -> BookFormat.entries.firstOrNull { it.extension == ext } }

        if (!fromOpds.isNullOrEmpty()) {
            val preferred = preferredFormat(fromOpds, defaultExt)
            _state.update {
                it.copy(
                    availableFormats = fromOpds,
                    formatAvailability = fromOpds.associateWith { true },
                    selectedFormat = preferred,
                    formatsLoading = false
                )
            }
            Log.d(TAG, "checkAvailableFormats | from OPDS: ${fromOpds.map { it.extension }}, preferred: ${preferred.extension}")
            return
        }

        // Fallback: пробуем скачать форматы по очереди
        // Показываем только основные форматы, которые поддерживает Flibusta
        val probableFormats = listOf(BookFormat.FB2, BookFormat.EPUB, BookFormat.MOBI)
        val preferred = preferredFormat(probableFormats, defaultExt)
        _state.update {
            it.copy(
                availableFormats = probableFormats,
                formatAvailability = probableFormats.associateWith { true },
                selectedFormat = preferred,
                formatsLoading = false
            )
        }
        Log.d(TAG, "checkAvailableFormats | fallback: probable formats, preferred: ${preferred.extension}")
    }
}
