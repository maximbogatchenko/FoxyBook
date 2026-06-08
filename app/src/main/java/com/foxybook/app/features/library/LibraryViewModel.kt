package com.foxybook.app.features.library

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxybook.app.core.datastore.DataStoreManager
import com.foxybook.app.core.models.BookCollection
import com.foxybook.app.core.models.BookFormat
import com.foxybook.app.core.models.LibraryBook
import com.foxybook.app.core.models.LibraryTab
import com.foxybook.app.core.reader.BookParser
import com.foxybook.app.core.utils.FileHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LibraryState(
    val allBooks: List<LibraryBook> = emptyList(),
    val collections: List<BookCollection> = emptyList(),
    val currentTab: LibraryTab = LibraryTab.ALL,
    val selectedCollectionId: String? = null,
    val isCreateCollectionDialogOpen: Boolean = false,
    val isRenameCollectionDialogOpen: Boolean = false,
    val renameCollectionId: String? = null,
    val isMoveBookDialogOpen: Boolean = false,
    val moveBookId: Int? = null,
    val moveBookFormat: String? = null,
    val isDeleteBookDialogOpen: Boolean = false,
    val deleteBookId: Int? = null,
    val deleteBookFormat: String? = null
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
    data class MoveBookClicked(val bookId: Int, val format: String) : LibraryEvent
    data class MoveBookToCollection(val bookId: Int, val format: String, val collectionId: String) : LibraryEvent
    data class RemoveBookFromCollection(val bookId: Int, val format: String, val collectionId: String) : LibraryEvent
    data object DismissDialogs : LibraryEvent
}

class LibraryViewModel(
    private val dataStoreManager: DataStoreManager,
    private val bookParser: BookParser
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                dataStoreManager.libraryBooks,
                dataStoreManager.collections
            ) { books: List<LibraryBook>, collections: List<BookCollection> ->
                _state.value.copy(allBooks = books, collections = collections)
            }.collect { _state.value = it }
        }
    }

    fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.TabSelected -> _state.value = _state.value.copy(currentTab = event.tab, selectedCollectionId = if (event.tab != LibraryTab.COLLECTIONS) null else _state.value.selectedCollectionId)
            is LibraryEvent.CollectionSelected -> _state.value = _state.value.copy(selectedCollectionId = event.collectionId)
            is LibraryEvent.ToggleFavorite -> viewModelScope.launch { dataStoreManager.toggleFavorite(event.bookId, event.format) }
            is LibraryEvent.DeleteBook -> _state.value = _state.value.copy(isDeleteBookDialogOpen = true, deleteBookId = event.bookId, deleteBookFormat = event.format)
            is LibraryEvent.CreateCollectionClicked -> _state.value = _state.value.copy(isCreateCollectionDialogOpen = true)
            is LibraryEvent.CreateCollection -> viewModelScope.launch { dataStoreManager.createCollection(event.name); _state.value = _state.value.copy(isCreateCollectionDialogOpen = false) }
            is LibraryEvent.RenameCollectionClicked -> _state.value = _state.value.copy(isRenameCollectionDialogOpen = true, renameCollectionId = event.collectionId)
            is LibraryEvent.RenameCollection -> viewModelScope.launch { dataStoreManager.renameCollection(event.collectionId, event.newName); _state.value = _state.value.copy(isRenameCollectionDialogOpen = false, renameCollectionId = null) }
            is LibraryEvent.DeleteCollection -> viewModelScope.launch { dataStoreManager.deleteCollection(event.collectionId); if (_state.value.selectedCollectionId == event.collectionId) _state.value = _state.value.copy(selectedCollectionId = null) }
            is LibraryEvent.MoveBookClicked -> _state.value = _state.value.copy(isMoveBookDialogOpen = true, moveBookId = event.bookId, moveBookFormat = event.format)
            is LibraryEvent.MoveBookToCollection -> viewModelScope.launch { dataStoreManager.addBookToCollection(event.bookId, event.format, event.collectionId); _state.value = _state.value.copy(isMoveBookDialogOpen = false) }
            is LibraryEvent.RemoveBookFromCollection -> viewModelScope.launch { dataStoreManager.removeBookFromCollection(event.bookId, event.format, event.collectionId); _state.value = _state.value.copy(isMoveBookDialogOpen = false) }
            is LibraryEvent.DismissDialogs -> _state.value = _state.value.copy(isCreateCollectionDialogOpen = false, isRenameCollectionDialogOpen = false, isMoveBookDialogOpen = false, isDeleteBookDialogOpen = false, renameCollectionId = null, moveBookId = null, moveBookFormat = null, deleteBookId = null, deleteBookFormat = null)
        }
    }

    fun confirmDeleteBook() {
        val bookId = _state.value.deleteBookId ?: return
        val format = _state.value.deleteBookFormat ?: return
        viewModelScope.launch {
            dataStoreManager.removeLibraryBook(bookId, format)
            _state.value = _state.value.copy(isDeleteBookDialogOpen = false, deleteBookId = null, deleteBookFormat = null)
        }
    }

    fun importBook(uri: Uri, context: Context) {
        viewModelScope.launch {
            try {
                val path = uri.toString()
                Log.d("LibraryVM", "Importing book from URI: $path")
                val parsed = bookParser.parse(path)
                if (parsed != null) {
                    val book = LibraryBook(
                        id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
                        title = parsed.title,
                        author = parsed.author,
                        format = parsed.format,
                        filePath = path,
                        downloadDate = System.currentTimeMillis()
                    )
                    dataStoreManager.addLibraryBook(book)
                    Log.d("LibraryVM", "Book imported successfully: ${parsed.title}")
                } else {
                    Log.e("LibraryVM", "Failed to parse imported book")
                }
            } catch (e: Exception) {
                Log.e("LibraryVM", "Error importing book", e)
            }
        }
    }
}
