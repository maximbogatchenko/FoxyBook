package com.foxybook.app.features.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxybook.app.core.datastore.DataStoreManager
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookDetailsState(
    val uiState: BookDetailsUiState = BookDetailsUiState.Loading,
    val downloads: Map<BookFormat, DownloadProgress> = BookFormat.entries.associateWith { DownloadProgress() },
    val bookmarks: List<Bookmark> = emptyList(),
    val showFolderErrorDialog: Boolean = false
)

sealed interface BookDetailsEvent {
    data class LoadBook(val id: Int) : BookDetailsEvent
    data class Download(val format: BookFormat) : BookDetailsEvent
    data class RemoveBookmark(val bookmark: Bookmark) : BookDetailsEvent
    data class JumpToBookmark(val bookmark: Bookmark, val format: String) : BookDetailsEvent
    data object DismissFolderError : BookDetailsEvent
}

class BookDetailsViewModel(
    private val getBookInfoUseCase: GetBookInfoUseCase,
    private val downloadBookUseCase: DownloadBookUseCase,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val _state = MutableStateFlow(BookDetailsState())
    val state: StateFlow<BookDetailsState> = _state.asStateFlow()

    fun onEvent(event: BookDetailsEvent) {
        when (event) {
            is BookDetailsEvent.LoadBook -> loadBookInfo(event.id)
            is BookDetailsEvent.Download -> downloadBook(event.format)
            is BookDetailsEvent.RemoveBookmark -> removeBookmark(event.bookmark)
            is BookDetailsEvent.JumpToBookmark -> jumpToBookmark(event.bookmark, event.format)
            is BookDetailsEvent.DismissFolderError -> _state.update { it.copy(showFolderErrorDialog = false) }
        }
    }

    private fun loadBookInfo(id: Int) {
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
            _state.update { it.copy(uiState = BookDetailsUiState.Loading) }
            try {
                val info = getBookInfoUseCase(id)
                if (info != null) {
                    _state.update { it.copy(uiState = BookDetailsUiState.Success(info)) }
                } else {
                    _state.update { it.copy(uiState = BookDetailsUiState.Error("Книга не найдена")) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(uiState = BookDetailsUiState.Error(e.message ?: "Ошибка")) }
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
                        percent = (progress * 100).toInt().coerceIn(0, 100)
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
}
