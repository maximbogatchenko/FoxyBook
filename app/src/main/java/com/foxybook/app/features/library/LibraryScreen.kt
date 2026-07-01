package com.foxybook.app.features.library

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foxybook.app.core.models.BookCollection
import com.foxybook.app.core.models.LibraryBook
import com.foxybook.app.core.models.LibraryTab
import com.foxybook.app.ui.components.CoverViewer
import com.foxybook.app.ui.components.CoverWithAuthor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onBookClick: (String, Int, String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    var coverViewerUrl by remember { mutableStateOf<String?>(null) }
    var selectedBookForDetails by remember { mutableStateOf<LibraryBook?>(null) }
    var showViewModeMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            Log.d("LibraryScreen", "Importing ${uris.size} books: $uris")
            uris.forEach { uri ->
                viewModel.importBook(uri, context)
            }
        }
    }

    // Show error in snackbar
    LaunchedEffect(state.importError) {
        state.importError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissImportError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Loading overlay
        if (state.isImporting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .clickable(enabled = false, onClick = {}),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Импорт книги…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            if (state.isSelectionMode) {
                TopAppBar(
                    title = { Text("Выбрано: ${state.selectedBookIds.size}", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.onEvent(LibraryEvent.ClearSelection) }) {
                            Icon(Icons.Default.Close, contentDescription = "Отменить")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.onEvent(LibraryEvent.SelectAll) }) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Выбрать всё")
                        }
                        IconButton(onClick = { viewModel.onEvent(LibraryEvent.BatchToggleFavorite) }) {
                            Icon(Icons.Default.Favorite, contentDescription = "В избранное", tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = { viewModel.onEvent(LibraryEvent.BatchShowCollectionDialog) }) {
                            Icon(Icons.Default.Folder, contentDescription = "В коллекцию")
                        }
                        IconButton(onClick = { viewModel.onEvent(LibraryEvent.BatchDeleteSelected) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    windowInsets = WindowInsets(0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            } else {
                TopAppBar(
                    title = { Text("Библиотека", fontWeight = FontWeight.Bold) },
                    actions = {
                        Box {
                            IconButton(onClick = { showViewModeMenu = true }) {
                                Icon(
                                    when (state.viewMode) {
                                        LibraryViewMode.LIST -> Icons.Default.ViewHeadline
                                        LibraryViewMode.COMPACT -> Icons.Default.ViewAgenda
                                        LibraryViewMode.GRID -> Icons.Default.GridView
                                    },
                                    contentDescription = "Вид"
                                )
                            }
                            DropdownMenu(
                                expanded = showViewModeMenu,
                                onDismissRequest = { showViewModeMenu = false }
                            ) {
                                LibraryViewMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                mode.label,
                                                fontWeight = if (state.viewMode == mode) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            showViewModeMenu = false
                                            viewModel.onEvent(LibraryEvent.ChangeViewMode(mode))
                                        },
                                        leadingIcon = {
                                            Icon(
                                                when (mode) {
                                                    LibraryViewMode.LIST -> Icons.Default.ViewHeadline
                                                    LibraryViewMode.COMPACT -> Icons.Default.ViewAgenda
                                                    LibraryViewMode.GRID -> Icons.Default.GridView
                                                },
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = {
                            importLauncher.launch(arrayOf("application/epub+zip", "application/x-fictionbook+xml", "application/x-mobipocket-ebook", "application/octet-stream", "text/plain"))
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Импорт книги")
                        }
                        if (state.currentTab == LibraryTab.COLLECTIONS) {
                            IconButton(onClick = { viewModel.onEvent(LibraryEvent.CreateCollectionClicked) }) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = "Новая коллекция")
                            }
                        }
                    },
                    windowInsets = WindowInsets(0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }

            // Tabs
            androidx.compose.material3.TabRow(
                selectedTabIndex = state.currentTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                LibraryTab.entries.forEach { tab ->
                    androidx.compose.material3.Tab(
                        selected = state.currentTab == tab,
                        onClick = { viewModel.onEvent(LibraryEvent.TabSelected(tab)) },
                        text = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        icon = {
                            Icon(
                                when (tab) {
                                    LibraryTab.ALL -> Icons.Default.MenuBook
                                    LibraryTab.FAVORITES -> Icons.Default.Favorite
                                    LibraryTab.HISTORY -> Icons.Default.History
                                    LibraryTab.COLLECTIONS -> Icons.Default.Folder
                                },
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Content with swipe gestures
            var swipeOffset by remember { mutableStateOf(0f) }
            val swipeThreshold = with(LocalDensity.current) { 80.dp.toPx() }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (swipeOffset < -swipeThreshold) {
                                    val next = (state.currentTab.ordinal + 1).coerceAtMost(LibraryTab.entries.size - 1)
                                    viewModel.onEvent(LibraryEvent.TabSelected(LibraryTab.entries[next]))
                                } else if (swipeOffset > swipeThreshold) {
                                    val prev = (state.currentTab.ordinal - 1).coerceAtLeast(0)
                                    viewModel.onEvent(LibraryEvent.TabSelected(LibraryTab.entries[prev]))
                                }
                                swipeOffset = 0f
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                swipeOffset += dragAmount
                            }
                        )
                    }
            ) {
                AnimatedContent(
                    targetState = state.currentTab to state.selectedCollectionId,
                    transitionSpec = {
                        val dir = if (targetState.first.ordinal > initialState.first.ordinal) 1 else -1
                        (slideInHorizontally(tween(250)) { it * dir } + fadeIn(tween(250)))
                            .togetherWith(slideOutHorizontally(tween(250)) { -it * dir } + fadeOut(tween(200)))
                    },
                    label = "library_content"
                ) { (tab, _) ->
                    when (tab) {
                        LibraryTab.ALL -> BookListContent(
                            books = state.allBooks,
                            viewMode = state.viewMode,
                            emptyTitle = "Библиотека пуста",
                            emptySubtitle = "Скачайте книги для чтения",
                            selectedBookIds = state.selectedBookIds,
                            isSelectionMode = state.isSelectionMode,
                            onBookClick = onBookClick,
                            onToggleFavorite = { id, fmt -> viewModel.onEvent(LibraryEvent.ToggleFavorite(id, fmt)) },
                            onMoveBook = { id, fmt -> viewModel.onEvent(LibraryEvent.MoveBookClicked(id, fmt)) },
                            onDeleteBook = { id, fmt -> viewModel.onEvent(LibraryEvent.DeleteBook(id, fmt)) },
                            onCoverClick = { url -> coverViewerUrl = url },
                            onBookDetails = { book -> selectedBookForDetails = book },
                            onLongPressBook = { id, fmt -> viewModel.onEvent(LibraryEvent.LongPressBook(id, fmt)) },
                            onToggleSelection = { id, fmt -> viewModel.onEvent(LibraryEvent.ToggleBookSelection(id, fmt)) }
                        )
                        LibraryTab.FAVORITES -> BookListContent(
                            books = state.favoriteBooks,
                            viewMode = state.viewMode,
                            emptyTitle = "Нет избранных",
                            emptySubtitle = "Нажмите ♡ чтобы добавить",
                            selectedBookIds = state.selectedBookIds,
                            isSelectionMode = state.isSelectionMode,
                            onBookClick = onBookClick,
                            onToggleFavorite = { id, fmt -> viewModel.onEvent(LibraryEvent.ToggleFavorite(id, fmt)) },
                            onMoveBook = { id, fmt -> viewModel.onEvent(LibraryEvent.MoveBookClicked(id, fmt)) },
                            onDeleteBook = { id, fmt -> viewModel.onEvent(LibraryEvent.DeleteBook(id, fmt)) },
                            onCoverClick = { url -> coverViewerUrl = url },
                            onBookDetails = { book -> selectedBookForDetails = book },
                            onLongPressBook = { id, fmt -> viewModel.onEvent(LibraryEvent.LongPressBook(id, fmt)) },
                            onToggleSelection = { id, fmt -> viewModel.onEvent(LibraryEvent.ToggleBookSelection(id, fmt)) }
                        )
                        LibraryTab.HISTORY -> BookListContent(
                            books = state.historyBooks,
                            viewMode = state.viewMode,
                            emptyTitle = "История пуста",
                            emptySubtitle = "Начните читать",
                            selectedBookIds = state.selectedBookIds,
                            isSelectionMode = state.isSelectionMode,
                            onBookClick = onBookClick,
                            onToggleFavorite = { id, fmt -> viewModel.onEvent(LibraryEvent.ToggleFavorite(id, fmt)) },
                            onMoveBook = { id, fmt -> viewModel.onEvent(LibraryEvent.MoveBookClicked(id, fmt)) },
                            onDeleteBook = { id, fmt -> viewModel.onEvent(LibraryEvent.DeleteBook(id, fmt)) },
                            onCoverClick = { url -> coverViewerUrl = url },
                            onBookDetails = { book -> selectedBookForDetails = book },
                            onLongPressBook = { id, fmt -> viewModel.onEvent(LibraryEvent.LongPressBook(id, fmt)) },
                            onToggleSelection = { id, fmt -> viewModel.onEvent(LibraryEvent.ToggleBookSelection(id, fmt)) }
                        )
                        LibraryTab.COLLECTIONS -> CollectionsContent(
                            collections = state.collections,
                            selectedCollectionId = state.selectedCollectionId,
                        books = state.allBooks,
                        viewMode = state.viewMode,
                        selectedBookIds = state.selectedBookIds,
                        isSelectionMode = state.isSelectionMode,
                        onCollectionClick = { viewModel.onEvent(LibraryEvent.CollectionSelected(it)) },
                        onRenameCollection = { viewModel.onEvent(LibraryEvent.RenameCollectionClicked(it)) },
                        onDeleteCollection = { viewModel.onEvent(LibraryEvent.DeleteCollection(it)) },
                        onBookClick = onBookClick,
                        onToggleFavorite = { id, fmt -> viewModel.onEvent(LibraryEvent.ToggleFavorite(id, fmt)) },
                        onMoveBook = { id, fmt -> viewModel.onEvent(LibraryEvent.MoveBookClicked(id, fmt)) },
                        onDeleteBook = { id, fmt -> viewModel.onEvent(LibraryEvent.DeleteBook(id, fmt)) },
                        onCoverClick = { url -> coverViewerUrl = url },
                        onBookDetails = { book -> selectedBookForDetails = book },
                        onCreateCollection = { viewModel.onEvent(LibraryEvent.CreateCollectionClicked) },
                        onLongPressBook = { id, fmt -> viewModel.onEvent(LibraryEvent.LongPressBook(id, fmt)) },
                        onToggleSelection = { id, fmt -> viewModel.onEvent(LibraryEvent.ToggleBookSelection(id, fmt)) }
                    )
                }
            }
        }
    }

        // Book details dialog (for grid mode)
        selectedBookForDetails?.let { book ->
            BookDetailsDialog(
                book = book,
                onDismiss = { selectedBookForDetails = null },
                onOpenCover = { url -> coverViewerUrl = url; selectedBookForDetails = null },
                onRead = { onBookClick(book.filePath, book.id, book.format) }
            )
        }

        // Dialogs
        if (state.isCreateCollectionDialogOpen) {
            CreateCollectionDialog(
                onDismiss = { viewModel.onEvent(LibraryEvent.DismissDialogs) },
                onConfirm = { name -> viewModel.onEvent(LibraryEvent.CreateCollection(name)) }
            )
        }
        if (state.isRenameCollectionDialogOpen && state.renameCollectionId != null) {
            val currentName = state.collections.find { it.id == state.renameCollectionId }?.name ?: ""
            RenameCollectionDialog(
                currentName = currentName,
                onDismiss = { viewModel.onEvent(LibraryEvent.DismissDialogs) },
                onConfirm = { newName ->
                    viewModel.onEvent(
                        LibraryEvent.RenameCollection(
                            state.renameCollectionId!!,
                            newName
                        )
                    )
                }
            )
        }
        if (state.isDeleteBookDialogOpen) {
            AlertDialog(
                onDismissRequest = { viewModel.onEvent(LibraryEvent.DismissDialogs) },
                title = { Text("Удалить книгу?") },
                text = {
                    Column {
                        Text(
                            if (state.deleteFromDevice) "Книга будет удалена из библиотеки и с устройства."
                            else "Книга будет удалена только из библиотеки."
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setDeleteFromDevice(!state.deleteFromDevice) }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = state.deleteFromDevice,
                                onCheckedChange = { viewModel.setDeleteFromDevice(it) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Удалить файлы с устройства",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmDeleteBook() }) {
                        Text(
                            "Удалить",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.onEvent(LibraryEvent.DismissDialogs) }) {
                        Text(
                            "Отмена"
                        )
                    }
                }
            )
        }
        if (state.isMoveBookDialogOpen && state.moveBookId != null && state.moveBookFormat != null) {
            MoveBookDialog(
                collections = state.collections,
                bookId = state.moveBookId!!,
                bookFormat = state.moveBookFormat!!,
                currentCollections = state.allBooks.find { it.id == state.moveBookId && it.format == state.moveBookFormat }?.collectionIds
                    ?: emptyList(),
                onDismiss = { viewModel.onEvent(LibraryEvent.DismissDialogs) },
                onMoveToCollection = { colId ->
                    viewModel.onEvent(
                        LibraryEvent.MoveBookToCollection(
                            state.moveBookId!!,
                            state.moveBookFormat!!,
                            colId
                        )
                    )
                },
                onRemoveFromCollection = { colId ->
                    viewModel.onEvent(
                        LibraryEvent.RemoveBookFromCollection(
                            state.moveBookId!!,
                            state.moveBookFormat!!,
                            colId
                        )
                    )
                },
                onCreateCollection = {
                    viewModel.onEvent(LibraryEvent.SetPendingBookForCollection(state.moveBookId!!, state.moveBookFormat!!))
                    viewModel.onEvent(LibraryEvent.CreateCollectionClicked)
                }
            )
        }

        // Batch delete dialog
        if (state.showBatchDeleteDialog && state.isSelectionMode) {
            AlertDialog(
                onDismissRequest = { viewModel.onEvent(LibraryEvent.DismissBatchDeleteDialog) },
                title = { Text("Удалить ${state.selectedBookIds.size} ${pluralBook(state.selectedBookIds.size)}?") },
                text = {
                    Column {
                        Text("Выбранные книги будут удалены из библиотеки.")
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setBatchDeleteFromDevice(!state.batchDeleteFromDevice) }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = state.batchDeleteFromDevice,
                                onCheckedChange = { viewModel.setBatchDeleteFromDevice(it) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Удалить файлы с устройства", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.onEvent(LibraryEvent.BatchDeleteConfirm) }) {
                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.onEvent(LibraryEvent.DismissBatchDeleteDialog) }) {
                        Text("Отмена")
                    }
                }
            )
        }

        // Batch collection dialog
        if (state.showBatchCollectionDialog && state.isSelectionMode) {
            BatchCollectionDialog(
                collections = state.collections,
                onDismiss = { viewModel.onEvent(LibraryEvent.DismissBatchCollectionDialog) },
                onSelectCollection = { colId ->
                    viewModel.onEvent(LibraryEvent.BatchAddToCollection(colId))
                },
                onCreateCollection = {
                    viewModel.onEvent(LibraryEvent.DismissBatchCollectionDialog)
                    viewModel.onEvent(LibraryEvent.CreateCollectionClicked)
                }
            )
        }

        // Full-screen cover viewer overlay
        if (coverViewerUrl != null) {
            CoverViewer(
                coverUrl = coverViewerUrl!!,
                onDismiss = { coverViewerUrl = null }
            )
        }

        // Error snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookListContent(
    books: List<LibraryBook>, viewMode: LibraryViewMode, emptyTitle: String, emptySubtitle: String,
    selectedBookIds: Set<String> = emptySet(),
    isSelectionMode: Boolean = false,
    onBookClick: (String, Int, String) -> Unit,
    onToggleFavorite: (Int, String) -> Unit,
    onMoveBook: (Int, String) -> Unit,
    onDeleteBook: (Int, String) -> Unit,
    onCoverClick: (String) -> Unit,
    onBookDetails: (LibraryBook) -> Unit = {},
    onLongPressBook: (Int, String) -> Unit = { _, _ -> },
    onToggleSelection: (Int, String) -> Unit = { _, _ -> }
) {
    if (books.isEmpty()) {
        EmptyLibraryView(emptyTitle, emptySubtitle)
    } else {
        when (viewMode) {
            LibraryViewMode.LIST -> BookListView(
                books, selectedBookIds, isSelectionMode, onBookClick, onToggleFavorite, onMoveBook, onDeleteBook, onCoverClick, onBookDetails, onLongPressBook, onToggleSelection
            )
            LibraryViewMode.COMPACT -> CompactBookListView(
                books, selectedBookIds, isSelectionMode, onBookClick, onToggleFavorite, onMoveBook, onDeleteBook, onCoverClick, onBookDetails, onLongPressBook, onToggleSelection
            )
            LibraryViewMode.GRID -> GridBookListView(
                books, selectedBookIds, isSelectionMode, onBookClick, onToggleFavorite, onMoveBook, onDeleteBook, onCoverClick, onBookDetails, onLongPressBook, onToggleSelection
            )
        }
    }
}

@Composable
private fun EmptyLibraryView(emptyTitle: String, emptySubtitle: String) {
    Column(modifier = Modifier.fillMaxSize().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(16.dp))
        Text(emptyTitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Text(emptySubtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookListView(
    books: List<LibraryBook>,
    selectedBookIds: Set<String>,
    isSelectionMode: Boolean,
    onBookClick: (String, Int, String) -> Unit,
    onToggleFavorite: (Int, String) -> Unit,
    onMoveBook: (Int, String) -> Unit,
    onDeleteBook: (Int, String) -> Unit,
    onCoverClick: (String) -> Unit,
    onBookDetails: (LibraryBook) -> Unit = {},
    onLongPressBook: (Int, String) -> Unit,
    onToggleSelection: (Int, String) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(books, key = { "${it.id}-${it.format}" }) { book ->
            val key = "${book.id}-${book.format}"
            val isSelected = key in selectedBookIds
            LibraryBookCard(book = book,
                isSelected = isSelected,
                onClick = {
                    if (isSelectionMode) onToggleSelection(book.id, book.format)
                    else onBookClick(book.filePath, book.id, book.format)
                },
                onLongClick = { onLongPressBook(book.id, book.format) },
                onToggleFavorite = { onToggleFavorite(book.id, book.format) },
                onMove = { onMoveBook(book.id, book.format) },
                onDelete = { onDeleteBook(book.id, book.format) },
                onCoverClick = { onBookDetails(book) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactBookListView(
    books: List<LibraryBook>,
    selectedBookIds: Set<String>,
    isSelectionMode: Boolean,
    onBookClick: (String, Int, String) -> Unit,
    onToggleFavorite: (Int, String) -> Unit,
    onMoveBook: (Int, String) -> Unit,
    onDeleteBook: (Int, String) -> Unit,
    onCoverClick: (String) -> Unit,
    onBookDetails: (LibraryBook) -> Unit = {},
    onLongPressBook: (Int, String) -> Unit,
    onToggleSelection: (Int, String) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(books, key = { "${it.id}-${it.format}" }) { book ->
            val key = "${book.id}-${book.format}"
            val isSelected = key in selectedBookIds
            CompactBookCard(book = book,
                isSelected = isSelected,
                onClick = {
                    if (isSelectionMode) onToggleSelection(book.id, book.format)
                    else onBookClick(book.filePath, book.id, book.format)
                },
                onLongClick = { onLongPressBook(book.id, book.format) },
                onToggleFavorite = { onToggleFavorite(book.id, book.format) },
                onMoveToCollection = { onMoveBook(book.id, book.format) },
                onDeleteBook = { onDeleteBook(book.id, book.format) },
                onCoverClick = { onBookDetails(book) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridBookListView(
    books: List<LibraryBook>,
    selectedBookIds: Set<String>,
    isSelectionMode: Boolean,
    onBookClick: (String, Int, String) -> Unit,
    onToggleFavorite: (Int, String) -> Unit,
    onMoveBook: (Int, String) -> Unit,
    onDeleteBook: (Int, String) -> Unit,
    onCoverClick: (String) -> Unit,
    onBookDetails: (LibraryBook) -> Unit = {},
    onLongPressBook: (Int, String) -> Unit,
    onToggleSelection: (Int, String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        gridItems(books, key = { "${it.id}-${it.format}" }) { book ->
            val key = "${book.id}-${book.format}"
            val isSelected = key in selectedBookIds
            GridBookCard(book = book,
                isSelected = isSelected,
                isSelectionMode = isSelectionMode,
                onCoverClick = {
                    if (isSelectionMode) onToggleSelection(book.id, book.format)
                    else onBookDetails(book)
                },
                onReadClick = {
                    if (isSelectionMode) onToggleSelection(book.id, book.format)
                    else onBookClick(book.filePath, book.id, book.format)
                },
                onLongClick = { onLongPressBook(book.id, book.format) },
                onToggleFavorite = { onToggleFavorite(book.id, book.format) },
                onMoveToCollection = { onMoveBook(book.id, book.format) },
                onDeleteBook = { onDeleteBook(book.id, book.format) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryBookCard(
    book: LibraryBook, isSelected: Boolean = false, onClick: () -> Unit, onLongClick: () -> Unit = {}, onToggleFavorite: () -> Unit, onMove: () -> Unit, onDelete: () -> Unit, onCoverClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val shape = remember { RoundedCornerShape(16.dp) }
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 1.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Selection checkmark
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Выбрано",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            CoverWithAuthor(
                coverUrl = book.coverUrl,
                author = book.author,
                contentDescription = book.title,
                width = 56.dp,
                height = 78.dp,
                onCoverClick = { onCoverClick() }
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(2.dp))
                Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(book.format.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val date = if (book.lastReadDate > 0) Date(book.lastReadDate) else Date(book.downloadDate)
                    Text(dateFormat.format(date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                Icon(if (book.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Избранное",
                    tint = if (book.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp))
            }
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Ещё", modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("В коллекцию") }, onClick = { showMenu = false; onMove() },
                        leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp)) })
                    DropdownMenuItem(text = { Text("Удалить", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDelete() },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactBookCard(
    book: LibraryBook, isSelected: Boolean = false, onClick: () -> Unit, onLongClick: () -> Unit = {},
    onToggleFavorite: () -> Unit, onMoveToCollection: () -> Unit, onDeleteBook: () -> Unit,
    onCoverClick: () -> Unit
) {
    val shape = remember { RoundedCornerShape(12.dp) }
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 1.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Selection checkmark
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Выбрано",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            CoverWithAuthor(
                coverUrl = book.coverUrl,
                author = book.author,
                contentDescription = book.title,
                width = 40.dp,
                height = 56.dp,
                onCoverClick = { onCoverClick() }
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(1.dp))
                Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                Icon(if (book.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Избранное",
                    tint = if (book.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp))
            }
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Ещё", modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("В коллекцию") }, onClick = { showMenu = false; onMoveToCollection() },
                        leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp)) })
                    DropdownMenuItem(text = { Text("Удалить", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDeleteBook() },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridBookCard(
    book: LibraryBook, isSelected: Boolean = false, isSelectionMode: Boolean = false,
    onCoverClick: () -> Unit, onReadClick: () -> Unit, onLongClick: () -> Unit = {},
    onToggleFavorite: () -> Unit, onMoveToCollection: () -> Unit, onDeleteBook: () -> Unit
) {
    val shape = remember { RoundedCornerShape(12.dp) }
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
            .combinedClickable(onClick = onReadClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 2.dp),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Cover area with overlays
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCoverClick),
                contentAlignment = Alignment.Center
            ) {
                Box {
                    CoverWithAuthor(
                        coverUrl = book.coverUrl,
                        author = book.author,
                        contentDescription = book.title,
                        width = 140.dp,
                        height = 200.dp,
                        showFullName = false
                    )

                    // Selection overlay
                    if (isSelected) {
                        Box(
                            modifier = Modifier.matchParentSize()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        )
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Выбрано",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(48.dp)
                        )
                    }

                    // Favorite overlay — top-left of cover
                    Box(
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(32.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                    ) {
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.fillMaxSize().padding(2.dp)
                        ) {
                            Icon(
                                if (book.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Избранное",
                                tint = if (book.isFavorite) MaterialTheme.colorScheme.error
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Menu overlay — top-right of cover
                    Box(
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(32.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                    ) {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Ещё",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("В коллекцию") }, onClick = { showMenu = false; onMoveToCollection() },
                                leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp)) })
                            DropdownMenuItem(text = { Text("Удалить", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDeleteBook() },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) })
                        }
                    }
                }
            }

            // Title
            Text(
                book.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp)
            )
            // Author
            Text(
                book.author,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
            )
            // Read / Select button
            OutlinedButton(
                onClick = onReadClick,
                modifier = Modifier.padding(bottom = 10.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    if (isSelectionMode) "Выбрать" else "Читать",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun CollectionsContent(
    collections: List<BookCollection>, selectedCollectionId: String?, books: List<LibraryBook>,
    viewMode: LibraryViewMode,
    selectedBookIds: Set<String> = emptySet(),
    isSelectionMode: Boolean = false,
    onCollectionClick: (String?) -> Unit, onRenameCollection: (String) -> Unit, onDeleteCollection: (String) -> Unit,
    onBookClick: (String, Int, String) -> Unit,
    onToggleFavorite: (Int, String) -> Unit, onMoveBook: (Int, String) -> Unit, onDeleteBook: (Int, String) -> Unit,
    onCoverClick: (String) -> Unit,
    onBookDetails: (LibraryBook) -> Unit = {},
    onCreateCollection: () -> Unit = {},
    onLongPressBook: (Int, String) -> Unit = { _, _ -> },
    onToggleSelection: (Int, String) -> Unit = { _, _ -> }
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Collection chips row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // All books chip
            FilterChip(
                selected = selectedCollectionId == null,
                onClick = { onCollectionClick(null) },
                label = { Text("Все папки") },
                leadingIcon = {
                    Icon(Icons.Default.MenuBook, null, modifier = Modifier.size(18.dp))
                }
            )
            // Collection chips
            for (collection in collections) {
                val count = books.count { collection.id in it.collectionIds }
                FilterChip(
                    selected = selectedCollectionId == collection.id,
                    onClick = { onCollectionClick(collection.id) },
                    label = { Text("${collection.name} · $count") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
            // Add button
            IconButton(
                onClick = onCreateCollection,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Создать коллекцию",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedCollectionId != null) {
            BookListContent(
                books = books.filter { selectedCollectionId in it.collectionIds },
                viewMode = viewMode,
                emptyTitle = "Коллекция пуста",
                emptySubtitle = "Добавьте книги через меню",
                selectedBookIds = selectedBookIds,
                isSelectionMode = isSelectionMode,
                onBookClick = onBookClick,
                onToggleFavorite = onToggleFavorite,
                onMoveBook = onMoveBook,
                onDeleteBook = onDeleteBook,
                onCoverClick = onCoverClick,
                onBookDetails = onBookDetails,
                onLongPressBook = onLongPressBook,
                onToggleSelection = onToggleSelection
            )
        } else {
            if (collections.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Нет коллекций",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Создайте коллекцию, чтобы упорядочить книги",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                // Show collections as cards when none selected
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(collections) { collection ->
                        val count = books.count { collection.id in it.collectionIds }
                        var showMenu by remember { mutableStateOf(false) }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCollectionClick(collection.id) },
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        collection.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "Книг: $count",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Ещё", modifier = Modifier.size(20.dp))
                                }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Переименовать") },
                                        onClick = { showMenu = false; onRenameCollection(collection.id) },
                                        leadingIcon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp)) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                                        onClick = { showMenu = false; onDeleteCollection(collection.id) },
                                        leadingIcon = { Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookDetailsDialog(
    book: LibraryBook,
    onDismiss: () -> Unit,
    onOpenCover: (String) -> Unit,
    onRead: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            OutlinedButton(onClick = onRead) {
                Icon(Icons.Default.MenuBook, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Читать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
        title = {
            Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        text = {
            Column {
                // Cover — клик открывает на весь экран
                Box(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenCover(book.coverUrl) },
                    contentAlignment = Alignment.Center
                ) {
                    CoverWithAuthor(
                        coverUrl = book.coverUrl,
                        author = book.author,
                        width = 120.dp,
                        height = 170.dp,
                        showFullName = false
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(book.author, style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                // Формат
                Text("Формат: ${book.format.uppercase()}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                // Дата добавления
                Text("Добавлена: ${dateFormat.format(Date(book.downloadDate))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Дата последнего чтения
                // Прогресс чтения
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Прогресс", style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface)
                            val displayProgress = if (book.lastReadDate > 0) book.readingProgress else 0
                            Text("${displayProgress}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (book.readingProgress.coerceIn(0, 100) / 100f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer,
                        )
                        if (book.lastReadDate > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Последнее чтение: ${dateFormat.format(Date(book.lastReadDate))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun CreateCollectionDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Новая коллекция") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text("Создать") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun RenameCollectionDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Переименовать") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun MoveBookDialog(
    collections: List<BookCollection>, bookId: Int, bookFormat: String, currentCollections: List<String>,
    onDismiss: () -> Unit, onMoveToCollection: (String) -> Unit, onRemoveFromCollection: (String) -> Unit,
    onCreateCollection: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Коллекции", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                if (collections.isEmpty()) {
                    Text("Нет коллекций", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    collections.forEach { collection ->
                        val isIn = collection.id in currentCollections
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { if (isIn) onRemoveFromCollection(collection.id) else onMoveToCollection(collection.id) }
                                .padding(horizontal = 4.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (isIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                collection.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isIn) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (isIn) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (collection != collections.last()) {
                            HorizontalDivider(modifier = Modifier.padding(start = 32.dp))
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
                TextButton(
                    onClick = onCreateCollection,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Создать коллекцию")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Готово") }
        }
    )
}

private fun pluralBook(count: Int): String = when {
    count % 10 == 1 && count % 100 != 11 -> "книгу"
    count % 10 in 2..4 && (count % 100 !in 12..14) -> "книги"
    else -> "книг"
}

@Composable
private fun BatchCollectionDialog(
    collections: List<BookCollection>,
    onDismiss: () -> Unit,
    onSelectCollection: (String) -> Unit,
    onCreateCollection: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить в коллекцию", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                if (collections.isEmpty()) {
                    Text("Нет коллекций", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    collections.forEach { collection ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelectCollection(collection.id) }
                                .padding(horizontal = 4.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                collection.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (collection != collections.last()) {
                            HorizontalDivider(modifier = Modifier.padding(start = 32.dp))
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
                TextButton(
                    onClick = onCreateCollection,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Создать коллекцию")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Готово") }
        }
    )
}
