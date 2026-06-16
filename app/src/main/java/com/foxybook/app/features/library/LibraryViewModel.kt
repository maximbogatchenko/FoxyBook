package com.foxybook.app.features.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.File
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxybook.app.core.database.BookDataRepository
import com.foxybook.app.core.datastore.DataStoreManager
import com.foxybook.app.core.models.BookCollection
import com.foxybook.app.core.models.BookFormat
import com.foxybook.app.core.models.LibraryBook
import com.foxybook.app.core.models.LibraryTab
import com.foxybook.app.core.reader.BookParser
import com.foxybook.app.core.utils.FileHelper
import com.foxybook.app.data.storage.FileDownloader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class LibraryViewMode(val label: String) {
    LIST("Список"),
    COMPACT("Компактный"),
    GRID("Сетка")
}

data class LibraryState(
    val allBooks: List<LibraryBook> = emptyList(),
    val collections: List<BookCollection> = emptyList(),
    val currentTab: LibraryTab = LibraryTab.ALL,
    val selectedCollectionId: String? = null,
    val isCreateCollectionDialogOpen: Boolean = false,
    val isRenameCollectionDialogOpen: Boolean = false,
    val renameCollectionId: String? = null,
    val isMoveBookDialogOpen: Boolean = false,
    val pendingCollectionBookId: Int? = null,
    val pendingCollectionBookFormat: String? = null,
    val moveBookId: Int? = null,
    val moveBookFormat: String? = null,
    val isDeleteBookDialogOpen: Boolean = false,
    val deleteBookId: Int? = null,
    val deleteBookFormat: String? = null,
    val deleteFromDevice: Boolean = true,
    val viewMode: LibraryViewMode = LibraryViewMode.LIST,
    val isImporting: Boolean = false,
    val importError: String? = null
) {
    val favoriteBooks: List<LibraryBook> get() = allBooks.filter { it.isFavorite }
    val historyBooks: List<LibraryBook> get() = allBooks.filter { it.lastReadDate > 0 }.sortedByDescending { it.lastReadDate }
    val displayedBooks: List<LibraryBook> get() = when (currentTab) {
        LibraryTab.ALL -> allBooks
        LibraryTab.FAVORITES -> favoriteBooks
        LibraryTab.HISTORY -> historyBooks
        LibraryTab.COLLECTIONS -> selectedCollectionId?.let { cid -> allBooks.filter { cid in it.collectionIds } } ?: emptyList()
    }
}

sealed interface LibraryEvent {
    data class TabSelected(val tab: LibraryTab) : LibraryEvent
    data class CollectionSelected(val collectionId: String?) : LibraryEvent
    data class ToggleFavorite(val bookId: Int, val format: String) : LibraryEvent
    data class DeleteBook(val bookId: Int, val format: String) : LibraryEvent
    data object CreateCollectionClicked : LibraryEvent
    data class CreateCollection(val name: String) : LibraryEvent
    data class RenameCollectionClicked(val collectionId: String) : LibraryEvent
    data class RenameCollection(val collectionId: String, val newName: String) : LibraryEvent
    data class DeleteCollection(val collectionId: String) : LibraryEvent
    data class SetPendingBookForCollection(val bookId: Int, val format: String) : LibraryEvent
    data class MoveBookClicked(val bookId: Int, val format: String) : LibraryEvent
    data class MoveBookToCollection(val bookId: Int, val format: String, val collectionId: String) : LibraryEvent
    data class RemoveBookFromCollection(val bookId: Int, val format: String, val collectionId: String) : LibraryEvent
    data class ChangeViewMode(val mode: LibraryViewMode) : LibraryEvent
    data object DismissDialogs : LibraryEvent
}

class LibraryViewModel(
    private val bookDataRepository: BookDataRepository,
    private val bookParser: BookParser,
    private val fileDownloader: FileDownloader,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                bookDataRepository.getAllBooks(),
                bookDataRepository.getAllCollections()
            ) { books: List<LibraryBook>, collections: List<BookCollection> ->
                _state.value.copy(allBooks = books, collections = collections)
            }.collect { _state.value = it }
        }
        viewModelScope.launch {
            val saved = dataStoreManager.libraryViewMode.first()
            _state.value = _state.value.copy(
                viewMode = try { LibraryViewMode.valueOf(saved) } catch (_: Exception) { LibraryViewMode.LIST }
            )
        }
    }

    fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.TabSelected -> _state.value = _state.value.copy(currentTab = event.tab, selectedCollectionId = if (event.tab != LibraryTab.COLLECTIONS) null else _state.value.selectedCollectionId)
            is LibraryEvent.CollectionSelected -> _state.value = _state.value.copy(selectedCollectionId = event.collectionId)
            is LibraryEvent.ToggleFavorite -> viewModelScope.launch { bookDataRepository.toggleFavorite(event.bookId, event.format) }
            is LibraryEvent.DeleteBook -> _state.value = _state.value.copy(isDeleteBookDialogOpen = true, deleteBookId = event.bookId, deleteBookFormat = event.format, deleteFromDevice = true)
            is LibraryEvent.CreateCollectionClicked -> _state.value = _state.value.copy(isCreateCollectionDialogOpen = true)
            is LibraryEvent.SetPendingBookForCollection -> _state.value = _state.value.copy(pendingCollectionBookId = event.bookId, pendingCollectionBookFormat = event.format)
            is LibraryEvent.CreateCollection -> viewModelScope.launch {
                val s = _state.value
                val collectionId = bookDataRepository.createCollection(event.name)
                // Auto-add the pending book if set (from move-book dialog)
                if (s.pendingCollectionBookId != null && s.pendingCollectionBookFormat != null) {
                    bookDataRepository.addBookToCollection(s.pendingCollectionBookId, s.pendingCollectionBookFormat, collectionId)
                }
                _state.value = s.copy(isCreateCollectionDialogOpen = false, pendingCollectionBookId = null, pendingCollectionBookFormat = null)
            }
            is LibraryEvent.RenameCollectionClicked -> _state.value = _state.value.copy(isRenameCollectionDialogOpen = true, renameCollectionId = event.collectionId)
            is LibraryEvent.RenameCollection -> viewModelScope.launch { bookDataRepository.renameCollection(event.collectionId, event.newName); _state.value = _state.value.copy(isRenameCollectionDialogOpen = false, renameCollectionId = null) }
            is LibraryEvent.DeleteCollection -> viewModelScope.launch { bookDataRepository.deleteCollection(event.collectionId); if (_state.value.selectedCollectionId == event.collectionId) _state.value = _state.value.copy(selectedCollectionId = null) }
            is LibraryEvent.MoveBookClicked -> _state.value = _state.value.copy(isMoveBookDialogOpen = true, moveBookId = event.bookId, moveBookFormat = event.format)
            is LibraryEvent.MoveBookToCollection -> viewModelScope.launch { bookDataRepository.addBookToCollection(event.bookId, event.format, event.collectionId); _state.value = _state.value.copy(isMoveBookDialogOpen = false) }
            is LibraryEvent.RemoveBookFromCollection -> viewModelScope.launch { bookDataRepository.removeBookFromCollection(event.bookId, event.format, event.collectionId); _state.value = _state.value.copy(isMoveBookDialogOpen = false) }
            is LibraryEvent.DismissDialogs -> _state.value = _state.value.copy(isCreateCollectionDialogOpen = false, isRenameCollectionDialogOpen = false, isMoveBookDialogOpen = false, isDeleteBookDialogOpen = false, renameCollectionId = null, moveBookId = null, moveBookFormat = null, deleteBookId = null, deleteBookFormat = null, deleteFromDevice = true)
            is LibraryEvent.ChangeViewMode -> {
                _state.value = _state.value.copy(viewMode = event.mode)
                viewModelScope.launch { dataStoreManager.setLibraryViewMode(event.mode.name) }
            }
        }
    }

    fun confirmDeleteBook() {
        val bookId = _state.value.deleteBookId ?: return
        val format = _state.value.deleteBookFormat ?: return
        viewModelScope.launch {
            val book = bookDataRepository.getAllBooks().first()
                .find { it.id == bookId && it.format == format }

            bookDataRepository.removeLibraryBook(bookId, format)

            if (_state.value.deleteFromDevice && book != null) {
                fileDownloader.deleteFile(book.filePath)
            }

            _state.value = _state.value.copy(isDeleteBookDialogOpen = false, deleteBookId = null, deleteBookFormat = null)
        }
    }

    fun setDeleteFromDevice(value: Boolean) {
        _state.value = _state.value.copy(deleteFromDevice = value)
    }

    fun importBook(uri: Uri, context: Context) {
        _state.value = _state.value.copy(isImporting = true, importError = null)
        viewModelScope.launch {
            try {
                val fileName = com.foxybook.app.core.utils.UriUtils.getFileName(context, uri)
                    ?: "book_${System.currentTimeMillis()}"

                var extension = fileName.substringAfterLast(".", "").lowercase()

                // Fallback to MIME type if extension is missing or unknown
                if (extension.isEmpty() || BookFormat.fromExtension(extension) == null) {
                    val mimeType = context.contentResolver.getType(uri)
                    extension = when {
                        mimeType?.contains("epub") == true -> "epub"
                        mimeType?.contains("fictionbook") == true || mimeType?.contains("fb2") == true -> "fb2"
                        mimeType?.contains("mobipocket") == true -> "mobi"
                        mimeType?.contains("text/plain") == true -> "txt"
                        else -> extension
                    }
                }

                if (BookFormat.fromExtension(extension) == null) {
                    Log.e("LibraryVM", "Unsupported format for file: $fileName (ext: $extension)")
                    _state.value = _state.value.copy(isImporting = false, importError = "Неподдерживаемый формат файла")
                    return@launch
                }

                // Copy to internal storage to ensure permanent access
                val internalDir = File(context.filesDir, "imported_books")
                if (!internalDir.exists()) internalDir.mkdirs()

                val finalFileName = if (fileName.contains(".")) fileName else "$fileName.$extension"
                val destFile = File(internalDir, "${System.currentTimeMillis()}_$finalFileName")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val internalPath = destFile.absolutePath
                Log.d("LibraryVM", "Book copied to internal storage: $internalPath")

                val parsed = bookParser.parse(internalPath, extension)
                if (parsed != null) {
                    val coverPath = bookParser.extractCover(internalPath, extension)
                    val book = LibraryBook(
                        id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
                        title = parsed.title,
                        author = parsed.author,
                        format = parsed.format,
                        filePath = internalPath,
                        coverUrl = coverPath?.let { "file://$it" } ?: "",
                        downloadDate = System.currentTimeMillis()
                    )
                    bookDataRepository.addLibraryBook(book)
                    Log.d("LibraryVM", "Book imported successfully: ${parsed.title}")
                    _state.value = _state.value.copy(isImporting = false)
                } else {
                    Log.e("LibraryVM", "Failed to parse imported book from $internalPath")
                    if (destFile.exists()) destFile.delete()
                    _state.value = _state.value.copy(isImporting = false, importError = "Не удалось распознать формат книги")
                }
            } catch (e: Exception) {
                Log.e("LibraryVM", "Error importing book", e)
                _state.value = _state.value.copy(isImporting = false, importError = e.message ?: "Ошибка импорта")
            }
        }
    }

    fun dismissImportError() {
        _state.value = _state.value.copy(importError = null)
    }
}
