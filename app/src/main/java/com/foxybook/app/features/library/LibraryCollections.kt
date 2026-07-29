package com.foxybook.app.features.library

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.res.stringResource
import com.foxybook.app.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foxybook.app.core.models.BookCollection
import com.foxybook.app.core.models.LibraryBook

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background

/**
 * Collections tab content: chip row for selecting a collection, then books or collection list.
 */
@Composable
fun CollectionsContent(
    collections: List<BookCollection>, selectedCollectionId: String?, books: List<LibraryBook>,
    viewMode: LibraryViewMode, selectedBookIds: Set<String> = emptySet(), isSelectionMode: Boolean = false,
    onCollectionClick: (String?) -> Unit, onRenameCollection: (String) -> Unit, onDeleteCollection: (String) -> Unit,
    onBookClick: (String, Int, String) -> Unit, onToggleFavorite: (Int, String) -> Unit,
    onMoveBook: (Int, String) -> Unit, onDeleteBook: (Int, String) -> Unit, onCoverClick: (String) -> Unit,
    onBookDetails: (LibraryBook) -> Unit = {}, onCreateCollection: () -> Unit = {},
    onLongPressBook: (Int, String) -> Unit = { _, _ -> }, onToggleSelection: (Int, String) -> Unit = { _, _ -> }
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Collection chips row
        Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = selectedCollectionId == null, onClick = { onCollectionClick(null) }, label = { Text(stringResource(R.string.library_all_folders)) })
            for (collection in collections) {
                val count = books.count { collection.id in it.collectionIds }
                FilterChip(selected = selectedCollectionId == collection.id, onClick = { onCollectionClick(collection.id) },
                    label = { Text("${collection.name} · $count") },
                    leadingIcon = { Icon(Icons.Default.Folder, null, modifier = Modifier.size(18.dp)) })
            }
            IconButton(onClick = onCreateCollection, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Add, stringResource(R.string.collection_create), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) }
        }
        Spacer(Modifier.height(12.dp))

        if (selectedCollectionId != null) {
            BookListContent(books = books.filter { selectedCollectionId in it.collectionIds }, viewMode = viewMode,
                emptyTitle = stringResource(R.string.library_empty_collection_title), emptySubtitle = stringResource(R.string.library_empty_collection_subtitle),
                selectedBookIds = selectedBookIds, isSelectionMode = isSelectionMode, onBookClick = onBookClick,
                onToggleFavorite = onToggleFavorite, onMoveBook = onMoveBook, onDeleteBook = onDeleteBook,
                onCoverClick = onCoverClick, onBookDetails = onBookDetails, onLongPressBook = onLongPressBook,
                onToggleSelection = onToggleSelection)
        } else if (collections.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Folder, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.library_no_collections_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.library_no_collections_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(collections) { collection ->
                    val count = books.count { collection.id in it.collectionIds }
                    var showMenu by remember { mutableStateOf(false) }
                    Card(modifier = Modifier.fillMaxWidth().clickable { onCollectionClick(collection.id) },
                        shape = RoundedCornerShape(14.dp), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(collection.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(R.string.library_books_count_format, count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.MoreVert, stringResource(R.string.cd_more), modifier = Modifier.size(20.dp)) }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.collection_rename)) }, onClick = { showMenu = false; onRenameCollection(collection.id) }, leadingIcon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp)) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.cd_delete), color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDeleteCollection(collection.id) }, leadingIcon = { Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) })
                            }
                        }
                    }
                }
            }
        }
    }
}
