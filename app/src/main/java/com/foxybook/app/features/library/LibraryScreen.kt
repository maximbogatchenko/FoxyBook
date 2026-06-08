package com.foxybook.app.features.library

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            Log.d("LibraryScreen", "Importing book: $it")
            viewModel.importBook(it, context)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            TopAppBar(
                title = { Text("Библиотека", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { 
                        importLauncher.launch(arrayOf("application/epub+zip", "application/x-fictionbook+xml", "application/octet-stream", "text/plain")) 
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

            AnimatedContent(
                targetState = state.currentTab to state.selectedCollectionId,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "library_content"
            ) { (tab, _) ->
                when (tab) {
                    LibraryTab.ALL -> BookList(
                        books = state.allBooks,
                        emptyTitle = "Библиотека пуста",
                        emptySubtitle = "Скачайте книги для чтения",
                        onBookClick = onBookClick,
                        onToggleFavorite = { id, fmt -> viewModel.onEvent(LibraryEvent.ToggleFavorite(id, fmt)) },
                        onMoveBook = { id, fmt -> viewModel.onEvent(LibraryEvent.MoveBookClicked(id, fmt)) },
                        onDeleteBook = { id, fmt -> viewModel.onEvent(LibraryEvent.DeleteBook(id, fmt)) },
                        onCoverClick = { url -> coverViewerUrl = url }
                    )
                    LibraryTab.FAVORITES -> BookList(
                        books = state.favoriteBooks,
                        emptyTitle = "Нет избранных",
                        emptySubtitle = "Нажмите ♡ чтобы добавить",
                        onBookClick = onBookClick,
                        onToggleFavorite = { id, fmt -> viewModel.onEvent(LibraryEvent.ToggleFavorite(id, fmt)) },
                        onMoveBook = { id, fmt -> viewModel.onEvent(LibraryEvent.MoveBookClicked(id, fmt)) },
                        onDeleteBook = { id, fmt -> viewModel.onEvent(LibraryEvent.DeleteBook(id, fmt)) },
                        onCoverClick = { url -> coverViewerUrl = url }
                    )
                    LibraryTab.HISTORY -> BookList(
                        books = state.historyBooks,
                        emptyTitle = "История пуста",
                        emptySubtitle = "Начните читать",
                        onBookClick = onBookClick,
                        onToggleFavorite = { id, fmt -> viewModel.onEvent(LibraryEvent.ToggleFavorite(id, fmt)) },
                        onMoveBook = { id, fmt -> viewModel.onEvent(LibraryEvent.MoveBookClicked(id, fmt)) },
                        onDeleteBook = { id, fmt -> viewModel.onEvent(LibraryEvent.DeleteBook(id, fmt)) },
                        onCoverClick = { url -> coverViewerUrl = url }
                    )
                    LibraryTab.COLLECTIONS -> CollectionsContent(
                        collections = state.collections,
                        selectedCollectionId = state.selectedCollectionId,
                        books = state.displayedBooks,
                        onCollectionClick = { viewModel.onEvent(LibraryEvent.CollectionSelected(it)) },
                        onRenameCollection = { viewModel.onEvent(LibraryEvent.RenameCollectionClicked(it)) },
                        onDeleteCollection = { viewModel.onEvent(LibraryEvent.DeleteCollection(it)) },
                        onBookClick = onBookClick,
                        onToggleFavorite = { id, fmt -> viewModel.onEvent(LibraryEvent.ToggleFavorite(id, fmt)) },
                        onMoveBook = { id, fmt -> viewModel.onEvent(LibraryEvent.MoveBookClicked(id, fmt)) },
                        onDeleteBook = { id, fmt -> viewModel.onEvent(LibraryEvent.DeleteBook(id, fmt)) },
                        onCoverClick = { url -> coverViewerUrl = url }
                    )
                }
            }
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
                text = { Text("Книга будет удалена из библиотеки и с устройства.") },
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
    }
}

@Composable
private fun BookList(
    books: List<LibraryBook>, emptyTitle: String, emptySubtitle: String,
    onBookClick: (String, Int, String) -> Unit,
    onToggleFavorite: (Int, String) -> Unit,
    onMoveBook: (Int, String) -> Unit,
    onDeleteBook: (Int, String) -> Unit,
    onCoverClick: (String) -> Unit
) {
    if (books.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.AutoStories, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(16.dp))
            Text(emptyTitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(emptySubtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(books, key = { "${it.id}-${it.format}" }) { book ->
                LibraryBookCard(book = book,
                    onClick = { onBookClick(book.filePath, book.id, book.format) },
                    onToggleFavorite = { onToggleFavorite(book.id, book.format) },
                    onMove = { onMoveBook(book.id, book.format) },
                    onDelete = { onDeleteBook(book.id, book.format) },
                    onCoverClick = { onCoverClick(book.coverUrl) }
                )
            }
        }
    }
}

@Composable
private fun LibraryBookCard(
    book: LibraryBook, onClick: () -> Unit, onToggleFavorite: () -> Unit, onMove: () -> Unit, onDelete: () -> Unit, onCoverClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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

@Composable
private fun CollectionsContent(
    collections: List<BookCollection>, selectedCollectionId: String?, books: List<LibraryBook>,
    onCollectionClick: (String?) -> Unit, onRenameCollection: (String) -> Unit, onDeleteCollection: (String) -> Unit,
    onBookClick: (String, Int, String) -> Unit,
    onToggleFavorite: (Int, String) -> Unit, onMoveBook: (Int, String) -> Unit, onDeleteBook: (Int, String) -> Unit,
    onCoverClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (collections.isNotEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxWidth().height(120.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(collections) { collection ->
                    var showMenu by remember { mutableStateOf(false) }
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onCollectionClick(collection.id) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = if (selectedCollectionId == collection.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(collection.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = if (selectedCollectionId == collection.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        val count = books.count { collection.id in it.collectionIds }
                        Text("$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box {
                            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.MoreVert, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text("Переименовать") }, onClick = { showMenu = false; onRenameCollection(collection.id) })
                                DropdownMenuItem(text = { Text("Удалить", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDeleteCollection(collection.id) })
                            }
                        }
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("Нет коллекций", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (selectedCollectionId != null) {
            BookList(books = books, emptyTitle = "Коллекция пуста", emptySubtitle = "Добавьте книги", onBookClick = onBookClick, onToggleFavorite = onToggleFavorite, onMoveBook = onMoveBook, onDeleteBook = onDeleteBook, onCoverClick = onCoverClick)
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Выберите коллекцию", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveBookDialog(
    collections: List<BookCollection>, bookId: Int, bookFormat: String, currentCollections: List<String>,
    onDismiss: () -> Unit, onMoveToCollection: (String) -> Unit, onRemoveFromCollection: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Перенести в коллекцию", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            if (collections.isEmpty()) {
                Text("Создайте коллекцию на вкладке «Коллекции»", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(collections) { collection ->
                        val isIn = collection.id in currentCollections
                        Card(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { if (isIn) onRemoveFromCollection(collection.id) else onMoveToCollection(collection.id) },
                            colors = CardDefaults.cardColors(containerColor = if (isIn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (isIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(collection.name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isIn) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                                if (isIn) Text("✓", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
