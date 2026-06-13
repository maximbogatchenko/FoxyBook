package com.foxybook.app.features.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.foxybook.app.core.datastore.DataStoreManager
import com.foxybook.app.core.models.Book
import com.foxybook.app.core.models.BookFormat
import com.foxybook.app.core.models.BookInfo
import com.foxybook.app.core.models.BookDetailsUiState
import com.foxybook.app.core.models.ReadingPosition
import com.foxybook.app.core.models.Bookmark
import com.foxybook.app.core.models.DownloadProgress
import com.foxybook.app.core.models.DownloadStatus
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class BookDetailsState(
    val uiState: BookDetailsUiState = BookDetailsUiState.Loading,
    val downloads: Map<BookFormat, DownloadProgress> = BookFormat.entries.associateWith { DownloadProgress() },
    val bookmarks: List<Bookmark> = emptyList(),
    val showFolderErrorDialog: Boolean = false,
    val availableFormats: List<BookFormat> = emptyList(),
    val selectedFormat: BookFormat = BookFormat.EPUB,
    val showFormatSelector: Boolean = false,
    val formatAvailability: Map<BookFormat, Boolean> = BookFormat.entries.associateWith { true }
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
}

class BookDetailsViewModel(
    private val getBookInfoUseCase: GetBookInfoUseCase,
    private val downloadBookUseCase: DownloadBookUseCase,
    private val dataStoreManager: DataStoreManager,
    private val baseUrl: String = "https://flibusta.is"
) : ViewModel() {

    private val _state = MutableStateFlow(BookDetailsState())
    val state: StateFlow<BookDetailsState> = _state.asStateFlow()

    private val checkClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

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
        }
    }

    private fun loadBookInfo(id: Int, initialData: Book? = null) {
        // If we have initial data, show it immediately
        if (initialData != null) {
            _state.update { 
                it.copy(
                    uiState = BookDetailsUiState.Success(
                        BookInfo(
                            id = initialData.id,
                            title = initialData.title,
                            author = initialData.author,
                            description = "Загрузка описания...",
                            genres = emptyList(),
                            coverUrl = initialData.coverUrl
                        )
                    )
                )
            }
        } else {
            _state.update { it.copy(uiState = BookDetailsUiState.Loading) }
        }

        viewModelScope.launch {
            dataStoreManager.bookmarksForBook(id).collect { list ->
                _state.update { it.copy(bookmarks = list) }
            }
        }

        viewModelScope.launch {
            dataStoreManager.libraryBooks.collect { books ->
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
            // Only update to loading if we don't have success state from initial data
            if (_state.value.uiState !is BookDetailsUiState.Success) {
                _state.update { it.copy(uiState = BookDetailsUiState.Loading) }
            }

            try {
                val info = getBookInfoUseCase(id)
                if (info != null) {
                    _state.update { it.copy(uiState = BookDetailsUiState.Success(info)) }
                    // Check available formats
                    checkAvailableFormats(id)
                } else if (_state.value.uiState !is BookDetailsUiState.Success) {
                    _state.update { it.copy(uiState = BookDetailsUiState.Error("Книга не найдена")) }
                }
            } catch (e: Exception) {
                if (_state.value.uiState !is BookDetailsUiState.Success) {
                    _state.update { it.copy(uiState = BookDetailsUiState.Error(e.message ?: "Ошибка")) }
                }
            }
        }
    }

    private fun downloadBook(format: BookFormat) {
        val info = (_state.value.uiState as? BookDetailsUiState.Success)?.bookInfo ?: return

        viewModelScope.launch {
            setProgress(format, DownloadProgress(status = DownloadStatus.DOWNLOADING, percent = 0))
            try {
                val result = downloadBookUseCase(
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
                        status = DownloadStatus.ERROR,
                        error = error
                    ))
                }
            } catch (e: Exception) {
                setProgress(format, DownloadProgress(
                    status = DownloadStatus.ERROR, error = e.message ?: "Ошибка"
                ))
            }
        }
    }

    private fun setProgress(format: BookFormat, progress: DownloadProgress) {
        _state.update { s -> s.copy(downloads = s.downloads + (format to progress)) }
    }

    private fun removeBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            dataStoreManager.removeBookmark(bookmark)
        }
    }

    private fun jumpToBookmark(bookmark: Bookmark, format: String) {
        viewModelScope.launch {
            dataStoreManager.saveReadingPosition(
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

    private fun checkAvailableFormats(bookId: Int) {
        viewModelScope.launch {
            // Получаем формат по умолчанию из настроек
            val defaultFormatExtension = dataStoreManager.defaultFormat.first()

            // Проверяем доступность каждого формата параллельно
            val availabilityChecks = BookFormat.entries.map { format ->
                async(Dispatchers.IO) {
                    format to checkFormatAvailable(bookId, format)
                }
            }

            val availabilityMap = mutableMapOf<BookFormat, Boolean>()
            val available = availabilityChecks.map { deferred ->
                val (format, isAvailable) = deferred.await()
                availabilityMap[format] = isAvailable
                isAvailable to format
            }.filter { it.first }.map { it.second }

            Log.d("BookDetailsVM", "Available formats for book $bookId: ${available.map { it.name }}")

            if (available.isEmpty()) {
                // Если ни один формат не доступен, показываем все (возможно проблема с сетью)
                Log.w("BookDetailsVM", "No formats available, showing all")
                val allFormats = BookFormat.entries.toList()
                val preferred = selectPreferredFormat(allFormats, defaultFormatExtension)
                _state.update {
                    it.copy(
                        availableFormats = allFormats,
                        formatAvailability = availabilityMap,
                        selectedFormat = preferred
                    )
                }
            } else {
                val preferred = selectPreferredFormat(available, defaultFormatExtension)
                _state.update {
                    it.copy(
                        availableFormats = available,
                        formatAvailability = availabilityMap,
                        selectedFormat = preferred
                    )
                }
            }
        }
    }

    private fun selectPreferredFormat(available: List<BookFormat>, defaultFormatExtension: String): BookFormat {
        // Пытаемся использовать формат из настроек
        return available.firstOrNull { it.extension == defaultFormatExtension }
            // Если не найден, используем приоритет: FB2 > EPUB > MOBI > TXT
            ?: available.firstOrNull { it == BookFormat.FB2 }
            ?: available.firstOrNull { it == BookFormat.EPUB }
            ?: available.firstOrNull { it == BookFormat.MOBI }
            ?: available.firstOrNull { it == BookFormat.TXT }
            ?: BookFormat.FB2
    }

    private suspend fun checkFormatAvailable(bookId: Int, format: BookFormat): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/b/$bookId/${format.extension}"
                val request = Request.Builder()
                    .url(url)
                    .head()
                    .build()

                val response = checkClient.newCall(request).execute()
                val isAvailable = response.isSuccessful && response.code != 404
                response.close()

                Log.d("BookDetailsVM", "Format ${format.name} for book $bookId: ${if (isAvailable) "available" else "not available"} (${response.code})")
                isAvailable
            } catch (e: Exception) {
                Log.e("BookDetailsVM", "Error checking format ${format.name} for book $bookId", e)
                true // В случае ошибки считаем доступным
            }
        }
    }
}
