package com.foxybook.app.features.library

import androidx.compose.foundation.ExperimentalFoundationApi
<<<<<<< HEAD
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
=======
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
>>>>>>> d24a20e (FoxyBook v1.9)
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
<<<<<<< HEAD
import androidx.compose.foundation.layout.fillMaxSize
=======
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
>>>>>>> d24a20e (FoxyBook v1.9)
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
<<<<<<< HEAD
=======
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MenuBook
>>>>>>> d24a20e (FoxyBook v1.9)
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
<<<<<<< HEAD
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import com.foxybook.app.core.models.LibraryBook
=======
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import com.foxybook.app.R
import com.foxybook.app.core.models.BookGroup
import com.foxybook.app.core.models.LibraryBook
import com.foxybook.app.core.models.ReadStatus
>>>>>>> d24a20e (FoxyBook v1.9)

/**
 * Dispatches to the correct view mode (LIST / COMPACT / GRID).
 */
@Composable
fun BookListContent(
<<<<<<< HEAD
    books: List<LibraryBook>, viewMode: LibraryViewMode, emptyTitle: String, emptySubtitle: String,
=======
    groups: List<BookGroup>, viewMode: LibraryViewMode, emptyTitle: String, emptySubtitle: String,
>>>>>>> d24a20e (FoxyBook v1.9)
    selectedBookIds: Set<String> = emptySet(), isSelectionMode: Boolean = false,
    onBookClick: (String, Int, String) -> Unit,
    onToggleFavorite: (Int, String) -> Unit, onMoveBook: (Int, String) -> Unit, onDeleteBook: (Int, String) -> Unit,
    onCoverClick: (String) -> Unit, onBookDetails: (LibraryBook) -> Unit = {},
<<<<<<< HEAD
=======
    onSetReadState: (Int, String, String) -> Unit = { _, _, _ -> },
>>>>>>> d24a20e (FoxyBook v1.9)
    onLongPressBook: (Int, String) -> Unit = { _, _ -> }, onToggleSelection: (Int, String) -> Unit = { _, _ -> }
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

<<<<<<< HEAD
    if (books.isEmpty()) {
        EmptyLibraryView(emptyTitle, emptySubtitle)
    } else when (viewMode) {
        LibraryViewMode.LIST -> BookListView(books, selectedBookIds, isSelectionMode, onBookClick, onToggleFavorite, onMoveBook, onDeleteBook, onCoverClick, onBookDetails, onLongPressBook, onToggleSelection)
        LibraryViewMode.COMPACT -> CompactBookListView(books, selectedBookIds, isSelectionMode, onBookClick, onToggleFavorite, onMoveBook, onDeleteBook, onCoverClick, onBookDetails, onLongPressBook, onToggleSelection)
        LibraryViewMode.GRID -> GridBookListView(books, selectedBookIds, isSelectionMode, onBookClick, onToggleFavorite, onMoveBook, onDeleteBook, onCoverClick, onBookDetails, onLongPressBook, onToggleSelection, isLandscape = isLandscape)
=======
    if (groups.isEmpty()) {
        EmptyLibraryView(emptyTitle, emptySubtitle)
    } else when (viewMode) {
        LibraryViewMode.LIST -> BookListView(groups, selectedBookIds, isSelectionMode, onBookClick, onToggleFavorite, onMoveBook, onDeleteBook, onCoverClick, onBookDetails, onSetReadState, onLongPressBook, onToggleSelection)
        LibraryViewMode.COMPACT -> CompactBookListView(groups, selectedBookIds, isSelectionMode, onBookClick, onToggleFavorite, onMoveBook, onDeleteBook, onCoverClick, onBookDetails, onSetReadState, onLongPressBook, onToggleSelection)
        LibraryViewMode.GRID -> GridBookListView(groups, selectedBookIds, isSelectionMode, onBookClick, onToggleFavorite, onMoveBook, onDeleteBook, onCoverClick, onBookDetails, onSetReadState, onLongPressBook, onToggleSelection, isLandscape = isLandscape)
    }
}

/** Заголовок полки: «Читаю · 3». */
@Composable
private fun ShelfHeader(status: ReadStatus, count: Int) {
    val title = stringResource(
        when (status) {
            ReadStatus.READING -> R.string.library_shelf_reading
            ReadStatus.UNREAD -> R.string.library_shelf_unread
            ReadStatus.FINISHED -> R.string.library_shelf_finished
        }
    )
    val icon = when (status) {
        ReadStatus.READING -> Icons.Default.MenuBook
        ReadStatus.UNREAD -> Icons.Default.BookmarkBorder
        ReadStatus.FINISHED -> Icons.Default.CheckCircle
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.size(6.dp))
        Text(
            stringResource(R.string.library_shelf_count, count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
>>>>>>> d24a20e (FoxyBook v1.9)
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
<<<<<<< HEAD
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
=======
    groups: List<BookGroup>, selectedBookIds: Set<String>, isSelectionMode: Boolean,
    onBookClick: (String, Int, String) -> Unit, onToggleFavorite: (Int, String) -> Unit,
    onMoveBook: (Int, String) -> Unit, onDeleteBook: (Int, String) -> Unit, onCoverClick: (String) -> Unit,
    onBookDetails: (LibraryBook) -> Unit = {}, onSetReadState: (Int, String, String) -> Unit,
    onLongPressBook: (Int, String) -> Unit, onToggleSelection: (Int, String) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        groups.forEach { group ->
            group.status?.let { status ->
                stickyHeader(key = "shelf-${status.name}") { ShelfHeader(status, group.books.size) }
            }
            items(group.books, key = { "${it.id}-${it.format}" }) { book ->
                val key = "${book.id}-${book.format}"
                LibraryBookCard(book = book, isSelected = key in selectedBookIds,
                    onClick = { if (isSelectionMode) onToggleSelection(book.id, book.format) else onBookClick(book.filePath, book.id, book.format) },
                    onLongClick = { onLongPressBook(book.id, book.format) },
                    onToggleFavorite = { onToggleFavorite(book.id, book.format) },
                    onMove = { onMoveBook(book.id, book.format) },
                    onDelete = { onDeleteBook(book.id, book.format) },
                    onCoverClick = { onBookDetails(book) },
                    onSetReadState = { state -> onSetReadState(book.id, book.format, state) })
            }
>>>>>>> d24a20e (FoxyBook v1.9)
        }
    }
}

// ─── Compact Book List View ──────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactBookListView(
<<<<<<< HEAD
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
=======
    groups: List<BookGroup>, selectedBookIds: Set<String>, isSelectionMode: Boolean,
    onBookClick: (String, Int, String) -> Unit, onToggleFavorite: (Int, String) -> Unit,
    onMoveBook: (Int, String) -> Unit, onDeleteBook: (Int, String) -> Unit, onCoverClick: (String) -> Unit,
    onBookDetails: (LibraryBook) -> Unit = {}, onSetReadState: (Int, String, String) -> Unit,
    onLongPressBook: (Int, String) -> Unit, onToggleSelection: (Int, String) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        groups.forEach { group ->
            group.status?.let { status ->
                stickyHeader(key = "shelf-${status.name}") { ShelfHeader(status, group.books.size) }
            }
            items(group.books, key = { "${it.id}-${it.format}" }) { book ->
                val key = "${book.id}-${book.format}"
                CompactBookCard(book = book, isSelected = key in selectedBookIds,
                    onClick = { if (isSelectionMode) onToggleSelection(book.id, book.format) else onBookClick(book.filePath, book.id, book.format) },
                    onLongClick = { onLongPressBook(book.id, book.format) },
                    onToggleFavorite = { onToggleFavorite(book.id, book.format) },
                    onMoveToCollection = { onMoveBook(book.id, book.format) },
                    onDeleteBook = { onDeleteBook(book.id, book.format) },
                    onCoverClick = { onBookDetails(book) },
                    onSetReadState = { state -> onSetReadState(book.id, book.format, state) })
            }
>>>>>>> d24a20e (FoxyBook v1.9)
        }
    }
}

// ─── Grid Book List View ─────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridBookListView(
<<<<<<< HEAD
    books: List<LibraryBook>, selectedBookIds: Set<String>, isSelectionMode: Boolean,
    onBookClick: (String, Int, String) -> Unit, onToggleFavorite: (Int, String) -> Unit,
    onMoveBook: (Int, String) -> Unit, onDeleteBook: (Int, String) -> Unit, onCoverClick: (String) -> Unit,
    onBookDetails: (LibraryBook) -> Unit = {}, onLongPressBook: (Int, String) -> Unit, onToggleSelection: (Int, String) -> Unit,
=======
    groups: List<BookGroup>, selectedBookIds: Set<String>, isSelectionMode: Boolean,
    onBookClick: (String, Int, String) -> Unit, onToggleFavorite: (Int, String) -> Unit,
    onMoveBook: (Int, String) -> Unit, onDeleteBook: (Int, String) -> Unit, onCoverClick: (String) -> Unit,
    onBookDetails: (LibraryBook) -> Unit = {}, onSetReadState: (Int, String, String) -> Unit,
    onLongPressBook: (Int, String) -> Unit, onToggleSelection: (Int, String) -> Unit,
>>>>>>> d24a20e (FoxyBook v1.9)
    isLandscape: Boolean = false
) {
    val columns = if (isLandscape) GridCells.Fixed(4) else GridCells.Fixed(2)

    LazyVerticalGrid(columns = columns, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
<<<<<<< HEAD
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
=======
        groups.forEach { group ->
            group.status?.let { status ->
                item(key = "shelf-${status.name}", span = { GridItemSpan(maxLineSpan) }) {
                    ShelfHeader(status, group.books.size)
                }
            }
            gridItems(group.books, key = { "${it.id}-${it.format}" }) { book ->
                val key = "${book.id}-${book.format}"
                GridBookCard(book = book, isSelected = key in selectedBookIds, isSelectionMode = isSelectionMode,
                    onCoverClick = { if (isSelectionMode) onToggleSelection(book.id, book.format) else onBookDetails(book) },
                    onReadClick = { if (isSelectionMode) onToggleSelection(book.id, book.format) else onBookClick(book.filePath, book.id, book.format) },
                    onLongClick = { onLongPressBook(book.id, book.format) },
                    onToggleFavorite = { onToggleFavorite(book.id, book.format) },
                    onMoveToCollection = { onMoveBook(book.id, book.format) },
                    onDeleteBook = { onDeleteBook(book.id, book.format) },
                    onSetReadState = { state -> onSetReadState(book.id, book.format, state) },
                    isLandscape = isLandscape)
            }
>>>>>>> d24a20e (FoxyBook v1.9)
        }
    }
}
