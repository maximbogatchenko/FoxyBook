package com.foxybook.app.features.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import com.foxybook.app.core.models.LibraryBook

/**
 * Dispatches to the correct view mode (LIST / COMPACT / GRID).
 */
@Composable
fun BookListContent(
    books: List<LibraryBook>, viewMode: LibraryViewMode, emptyTitle: String, emptySubtitle: String,
    selectedBookIds: Set<String> = emptySet(), isSelectionMode: Boolean = false,
    onBookClick: (String, Int, String) -> Unit,
    onToggleFavorite: (Int, String) -> Unit, onMoveBook: (Int, String) -> Unit, onDeleteBook: (Int, String) -> Unit,
    onCoverClick: (String) -> Unit, onBookDetails: (LibraryBook) -> Unit = {},
    onLongPressBook: (Int, String) -> Unit = { _, _ -> }, onToggleSelection: (Int, String) -> Unit = { _, _ -> }
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (books.isEmpty()) {
        EmptyLibraryView(emptyTitle, emptySubtitle)
    } else when (viewMode) {
        LibraryViewMode.LIST -> BookListView(books, selectedBookIds, isSelectionMode, onBookClick, onToggleFavorite, onMoveBook, onDeleteBook, onCoverClick, onBookDetails, onLongPressBook, onToggleSelection)
        LibraryViewMode.COMPACT -> CompactBookListView(books, selectedBookIds, isSelectionMode, onBookClick, onToggleFavorite, onMoveBook, onDeleteBook, onCoverClick, onBookDetails, onLongPressBook, onToggleSelection)
        LibraryViewMode.GRID -> GridBookListView(books, selectedBookIds, isSelectionMode, onBookClick, onToggleFavorite, onMoveBook, onDeleteBook, onCoverClick, onBookDetails, onLongPressBook, onToggleSelection, isLandscape = isLandscape)
    }
}

@Composable
fun EmptyLibraryView(emptyTitle: String, emptySubtitle: String) {
    Column(modifier = Modifier.fillMaxSize().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.AutoStories, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(16.dp))
        Text(emptyTitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Text(emptySubtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
    }
}

// ─── Book List View ──────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookListView(
    books: List<LibraryBook>, selectedBookIds: Set<String>, isSelectionMode: Boolean,
    onBookClick: (String, Int, String) -> Unit, onToggleFavorite: (Int, String) -> Unit,
    onMoveBook: (Int, String) -> Unit, onDeleteBook: (Int, String) -> Unit, onCoverClick: (String) -> Unit,
    onBookDetails: (LibraryBook) -> Unit = {}, onLongPressBook: (Int, String) -> Unit, onToggleSelection: (Int, String) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(books, key = { "${it.id}-${it.format}" }) { book ->
            val key = "${book.id}-${book.format}"
            LibraryBookCard(book = book, isSelected = key in selectedBookIds,
                onClick = { if (isSelectionMode) onToggleSelection(book.id, book.format) else onBookClick(book.filePath, book.id, book.format) },
                onLongClick = { onLongPressBook(book.id, book.format) },
                onToggleFavorite = { onToggleFavorite(book.id, book.format) },
                onMove = { onMoveBook(book.id, book.format) },
                onDelete = { onDeleteBook(book.id, book.format) },
                onCoverClick = { onBookDetails(book) })
        }
    }
}

// ─── Compact Book List View ──────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactBookListView(
    books: List<LibraryBook>, selectedBookIds: Set<String>, isSelectionMode: Boolean,
    onBookClick: (String, Int, String) -> Unit, onToggleFavorite: (Int, String) -> Unit,
    onMoveBook: (Int, String) -> Unit, onDeleteBook: (Int, String) -> Unit, onCoverClick: (String) -> Unit,
    onBookDetails: (LibraryBook) -> Unit = {}, onLongPressBook: (Int, String) -> Unit, onToggleSelection: (Int, String) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(books, key = { "${it.id}-${it.format}" }) { book ->
            val key = "${book.id}-${book.format}"
            CompactBookCard(book = book, isSelected = key in selectedBookIds,
                onClick = { if (isSelectionMode) onToggleSelection(book.id, book.format) else onBookClick(book.filePath, book.id, book.format) },
                onLongClick = { onLongPressBook(book.id, book.format) },
                onToggleFavorite = { onToggleFavorite(book.id, book.format) },
                onMoveToCollection = { onMoveBook(book.id, book.format) },
                onDeleteBook = { onDeleteBook(book.id, book.format) },
                onCoverClick = { onBookDetails(book) })
        }
    }
}

// ─── Grid Book List View ─────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridBookListView(
    books: List<LibraryBook>, selectedBookIds: Set<String>, isSelectionMode: Boolean,
    onBookClick: (String, Int, String) -> Unit, onToggleFavorite: (Int, String) -> Unit,
    onMoveBook: (Int, String) -> Unit, onDeleteBook: (Int, String) -> Unit, onCoverClick: (String) -> Unit,
    onBookDetails: (LibraryBook) -> Unit = {}, onLongPressBook: (Int, String) -> Unit, onToggleSelection: (Int, String) -> Unit,
    isLandscape: Boolean = false
) {
    val columns = if (isLandscape) GridCells.Fixed(4) else GridCells.Fixed(2)

    LazyVerticalGrid(columns = columns, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        gridItems(books, key = { "${it.id}-${it.format}" }) { book ->
            val key = "${book.id}-${book.format}"
            GridBookCard(book = book, isSelected = key in selectedBookIds, isSelectionMode = isSelectionMode,
                onCoverClick = { if (isSelectionMode) onToggleSelection(book.id, book.format) else onBookDetails(book) },
                onReadClick = { if (isSelectionMode) onToggleSelection(book.id, book.format) else onBookClick(book.filePath, book.id, book.format) },
                onLongClick = { onLongPressBook(book.id, book.format) },
                onToggleFavorite = { onToggleFavorite(book.id, book.format) },
                onMoveToCollection = { onMoveBook(book.id, book.format) },
                onDeleteBook = { onDeleteBook(book.id, book.format) },
                isLandscape = isLandscape)
        }
    }
}
