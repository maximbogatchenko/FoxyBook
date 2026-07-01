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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
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
    val importError: String? = null,

    // Batch selection
    val selectedBookIds: Set<String> = emptySet(), // "id-format" keys
    val showBatchDeleteDialog: Boolean = false,
    val showBatchCollectionDialog: Boolean = false,
    val batchDeleteFromDevice: Boolean = true
) {
    val isSelectionMode: Boolean get() = selectedBookIds.isNotEmpty()
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

    // Batch selection
    data class LongPressBook(val bookId: Int, val format: String) : LibraryEvent
    data class ToggleBookSelection(val bookId: Int, val format: String) : LibraryEvent
    data object ClearSelection : LibraryEvent
    data object SelectAll : LibraryEvent
    data object BatchDeleteSelected : LibraryEvent
    data object BatchDeleteConfirm : LibraryEvent
    data object BatchToggleFavorite : LibraryEvent
    data class BatchAddToCollection(val collectionId: String) : LibraryEvent
    data object DismissBatchDeleteDialog : LibraryEvent
    data object DismissBatchCollectionDialog : LibraryEvent
    data object BatchShowCollectionDialog : LibraryEvent
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
                _state.update { it.copy(allBooks = books, collections = collections) }
            }.collect { }
        }
        viewModelScope.launch {
            val saved = dataStoreManager.libraryViewMode.first()
            _state.update {
                it.copy(
                    viewMode = try { LibraryViewMode.valueOf(saved) } catch (_: Exception) { LibraryViewMode.LIST }
                )
            }
        }
    }

    fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.TabSelected -> _state.update { it.copy(currentTab = event.tab, selectedCollectionId = if (event.tab != LibraryTab.COLLECTIONS) null else it.selectedCollectionId, selectedBookIds = emptySet()) }
            is LibraryEvent.CollectionSelected -> _state.update { it.copy(selectedCollectionId = event.collectionId, selectedBookIds = emptySet()) }
            is LibraryEvent.ToggleFavorite -> viewModelScope.launch { bookDataRepository.toggleFavorite(event.bookId, event.format) }
            is LibraryEvent.DeleteBook -> _state.update { it.copy(isDeleteBookDialogOpen = true, deleteBookId = event.bookId, deleteBookFormat = event.format, deleteFromDevice = true) }
            is LibraryEvent.CreateCollectionClicked -> _state.update { it.copy(isCreateCollectionDialogOpen = true) }
            is LibraryEvent.SetPendingBookForCollection -> _state.update { it.copy(pendingCollectionBookId = event.bookId, pendingCollectionBookFormat = event.format) }
            is LibraryEvent.CreateCollection -> viewModelScope.launch {
                val collectionId = bookDataRepository.createCollection(event.name)
                _state.update { s ->
                    // Auto-add the pending book if set (from move-book dialog)
                    if (s.pendingCollectionBookId != null && s.pendingCollectionBookFormat != null) {
                        bookDataRepository.addBookToCollection(s.pendingCollectionBookId, s.pendingCollectionBookFormat, collectionId)
                    }
                    s.copy(isCreateCollectionDialogOpen = false, pendingCollectionBookId = null, pendingCollectionBookFormat = null)
                }
            }
            is LibraryEvent.RenameCollectionClicked -> _state.update { it.copy(isRenameCollectionDialogOpen = true, renameCollectionId = event.collectionId) }
            is LibraryEvent.RenameCollection -> viewModelScope.launch {
                bookDataRepository.renameCollection(event.collectionId, event.newName)
                _state.update { it.copy(isRenameCollectionDialogOpen = false, renameCollectionId = null) }
            }
            is LibraryEvent.DeleteCollection -> viewModelScope.launch {
                bookDataRepository.deleteCollection(event.collectionId)
                _state.update { if (it.selectedCollectionId == event.collectionId) it.copy(selectedCollectionId = null) else it }
            }
            is LibraryEvent.MoveBookClicked -> _state.update { it.copy(isMoveBookDialogOpen = true, moveBookId = event.bookId, moveBookFormat = event.format) }
            is LibraryEvent.MoveBookToCollection -> viewModelScope.launch {
                bookDataRepository.addBookToCollection(event.bookId, event.format, event.collectionId)
                _state.update { it.copy(isMoveBookDialogOpen = false) }
            }
            is LibraryEvent.RemoveBookFromCollection -> viewModelScope.launch {
                bookDataRepository.removeBookFromCollection(event.bookId, event.format, event.collectionId)
                _state.update { it.copy(isMoveBookDialogOpen = false) }
            }
            is LibraryEvent.DismissDialogs -> _state.update { it.copy(isCreateCollectionDialogOpen = false, isRenameCollectionDialogOpen = false, isMoveBookDialogOpen = false, isDeleteBookDialogOpen = false, renameCollectionId = null, moveBookId = null, moveBookFormat = null, deleteBookId = null, deleteBookFormat = null, deleteFromDevice = true) }
            // Batch selection
            is LibraryEvent.LongPressBook -> {
                val key = "${event.bookId}-${event.format}"
                _state.update { it.copy(selectedBookIds = it.selectedBookIds + key) }
            }
            is LibraryEvent.ToggleBookSelection -> {
                val key = "${event.bookId}-${event.format}"
                _state.update { it.copy(selectedBookIds = if (key in it.selectedBookIds) it.selectedBookIds - key else it.selectedBookIds + key) }
            }
            is LibraryEvent.ClearSelection -> _state.update { it.copy(selectedBookIds = emptySet(), showBatchDeleteDialog = false, showBatchCollectionDialog = false) }
            is LibraryEvent.SelectAll -> {
                val keys = _state.value.displayedBooks.map { "${it.id}-${it.format}" }.toSet()
                _state.update { it.copy(selectedBookIds = keys) }
            }
            is LibraryEvent.BatchDeleteSelected -> _state.update { it.copy(showBatchDeleteDialog = true) }
            is LibraryEvent.BatchDeleteConfirm -> viewModelScope.launch {
                val ids = _state.value.selectedBookIds.toList()
                val deleteFromDevice = _state.value.batchDeleteFromDevice
                // Fetch all books once, not per iteration
                val allBooks = if (deleteFromDevice) bookDataRepository.getAllBooks().first() else emptyList()
                for (key in ids) {
                    val parts = key.split("-", limit = 2)
                    if (parts.size != 2) continue
                    val bookId = parts[0].toIntOrNull() ?: continue
                    val format = parts[1]
                    if (deleteFromDevice) {
                        val book = allBooks.find { it.id == bookId && it.format == format }
                        if (book != null) fileDownloader.deleteFile(book.filePath)
                    }
                    bookDataRepository.removeLibraryBook(bookId, format)
                }
                _state.update { it.copy(selectedBookIds = emptySet(), showBatchDeleteDialog = false) }
            }
            is LibraryEvent.BatchToggleFavorite -> {
                val ids = _state.value.selectedBookIds.toList()
                viewModelScope.launch {
                    for (key in ids) {
                        val parts = key.split("-", limit = 2)
                        if (parts.size != 2) continue
                        val bookId = parts[0].toIntOrNull() ?: continue
                        val format = parts[1]
                        bookDataRepository.toggleFavorite(bookId, format)
                    }
                }
            }
            is LibraryEvent.BatchAddToCollection -> {
                val ids = _state.value.selectedBookIds.toList()
                viewModelScope.launch {
                    for (key in ids) {
                        val parts = key.split("-", limit = 2)
                        if (parts.size != 2) continue
                        val bookId = parts[0].toIntOrNull() ?: continue
                        val format = parts[1]
                        bookDataRepository.addBookToCollection(bookId, format, event.collectionId)
                    }
                    _state.update { it.copy(selectedBookIds = emptySet(), showBatchCollectionDialog = false) }
                }
            }
            is LibraryEvent.DismissBatchDeleteDialog -> _state.update { it.copy(showBatchDeleteDialog = false) }
            is LibraryEvent.DismissBatchCollectionDialog -> _state.update { it.copy(showBatchCollectionDialog = false) }
            is LibraryEvent.BatchShowCollectionDialog -> _state.update { it.copy(showBatchCollectionDialog = true) }
            is LibraryEvent.ChangeViewMode -> {
                _state.update { it.copy(viewMode = event.mode) }
                viewModelScope.launch { dataStoreManager.setLibraryViewMode(event.mode.name) }
            }
        }
    }

    fun confirmDeleteBook() {
        val bookId = _state.value.deleteBookId ?: return
        val format = _state.value.deleteBookFormat ?: return
        viewModelScope.launch {
            var bookPath: String? = null
            if (_state.value.deleteFromDevice) {
                val allBooks = bookDataRepository.getAllBooks().first()
                bookPath = allBooks.find { it.id == bookId && it.format == format }?.filePath
            }

            bookDataRepository.removeLibraryBook(bookId, format)

            if (bookPath != null) {
                fileDownloader.deleteFile(bookPath)
            }

            _state.update { it.copy(isDeleteBookDialogOpen = false, deleteBookId = null, deleteBookFormat = null) }
        }
    }

    fun setDeleteFromDevice(value: Boolean) {
        _state.update { it.copy(deleteFromDevice = value) }
    }

    fun setBatchDeleteFromDevice(value: Boolean) {
        _state.update { it.copy(batchDeleteFromDevice = value) }
    }

    fun importBook(uri: Uri, context: Context) {
        _state.update { it.copy(isImporting = true, importError = null) }
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
                    _state.update { it.copy(isImporting = false, importError = "Неподдерживаемый формат файла") }
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

                // Генерируем bookId заранее, чтобы передать в парсер
                val bookId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
                val parsed = bookParser.parse(internalPath, extension, bookId)
                if (parsed != null) {
                    val coverPath = bookParser.extractCover(internalPath, extension)
                    val book = LibraryBook(
                        id = bookId,
                        title = parsed.title,
                        author = parsed.author,
                        format = parsed.format,
                        filePath = internalPath,
                        coverUrl = coverPath?.let { "file://$it" } ?: "",
                        downloadDate = System.currentTimeMillis()
                    )
                    bookDataRepository.addLibraryBook(book)
                    Log.d("LibraryVM", "Book imported successfully: ${parsed.title}")
                    _state.update { it.copy(isImporting = false) }
                } else {
                    Log.e("LibraryVM", "Failed to parse imported book from $internalPath")
                    if (destFile.exists()) destFile.delete()
                    _state.update { it.copy(isImporting = false, importError = "Не удалось распознать формат книги") }
                }
            } catch (e: Exception) {
                Log.e("LibraryVM", "Error importing book", e)
                _state.update { it.copy(isImporting = false, importError = e.message ?: "Ошибка импорта") }
            }
        }
    }

    fun dismissImportError() {
        _state.update { it.copy(importError = null) }
    }
}
