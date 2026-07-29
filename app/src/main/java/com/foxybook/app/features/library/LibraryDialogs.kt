package com.foxybook.app.features.library

import androidx.compose.ui.res.stringResource
import com.foxybook.app.R
import com.foxybook.app.core.models.BookCollection
import com.foxybook.app.ui.components.CoverWithAuthor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foxybook.app.core.models.LibraryBook

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement

/**
 * Hosts all library dialogs controlled by [LibraryState].
 */
@Composable
fun LibraryDialogsHost(state: LibraryState, viewModel: LibraryViewModel) {
    // Create collection dialog
    if (state.isCreateCollectionDialogOpen) {
        CreateCollectionDialog(
            onDismiss = { viewModel.onEvent(LibraryEvent.DismissDialogs) },
            onConfirm = { name -> viewModel.onEvent(LibraryEvent.CreateCollection(name)) }
        )
    }

    // Rename collection dialog
    if (state.isRenameCollectionDialogOpen && state.renameCollectionId != null) {
        val currentName = state.collections.find { it.id == state.renameCollectionId }?.name ?: ""
        RenameCollectionDialog(
            currentName = currentName,
            onDismiss = { viewModel.onEvent(LibraryEvent.DismissDialogs) },
            onConfirm = { newName -> viewModel.onEvent(LibraryEvent.RenameCollection(state.renameCollectionId!!, newName)) }
        )
    }

    // Delete book dialog
    if (state.isDeleteBookDialogOpen) {
        DeleteBookDialog(
            deleteFromDevice = state.deleteFromDevice,
            onToggleDeleteFromDevice = { viewModel.setDeleteFromDevice(!state.deleteFromDevice) },
            onDismiss = { viewModel.onEvent(LibraryEvent.DismissDialogs) },
            onConfirm = { viewModel.confirmDeleteBook() }
        )
    }

    // Move book dialog
    if (state.isMoveBookDialogOpen && state.moveBookId != null && state.moveBookFormat != null) {
        MoveBookDialog(
            collections = state.collections,
            bookId = state.moveBookId!!,
            bookFormat = state.moveBookFormat!!,
            currentCollections = state.allBooks.find { it.id == state.moveBookId && it.format == state.moveBookFormat }?.collectionIds ?: emptyList(),
            onDismiss = { viewModel.onEvent(LibraryEvent.DismissDialogs) },
            onMoveToCollection = { colId -> viewModel.onEvent(LibraryEvent.MoveBookToCollection(state.moveBookId!!, state.moveBookFormat!!, colId)) },
            onRemoveFromCollection = { colId -> viewModel.onEvent(LibraryEvent.RemoveBookFromCollection(state.moveBookId!!, state.moveBookFormat!!, colId)) },
            onCreateCollection = {
                viewModel.onEvent(LibraryEvent.SetPendingBookForCollection(state.moveBookId!!, state.moveBookFormat!!))
                viewModel.onEvent(LibraryEvent.CreateCollectionClicked)
            }
        )
    }

    // Batch delete dialog
    if (state.showBatchDeleteDialog && state.isSelectionMode) {
        BatchDeleteDialog(
            count = state.selectedBookIds.size,
            deleteFromDevice = state.batchDeleteFromDevice,
            onToggleDeleteFromDevice = { viewModel.setBatchDeleteFromDevice(!state.batchDeleteFromDevice) },
            onDismiss = { viewModel.onEvent(LibraryEvent.DismissBatchDeleteDialog) },
            onConfirm = { viewModel.onEvent(LibraryEvent.BatchDeleteConfirm) }
        )
    }

    // Batch collection dialog
    if (state.showBatchCollectionDialog && state.isSelectionMode) {
        BatchCollectionDialog(
            collections = state.collections,
            onDismiss = { viewModel.onEvent(LibraryEvent.DismissBatchCollectionDialog) },
            onSelectCollection = { colId -> viewModel.onEvent(LibraryEvent.BatchAddToCollection(colId)) },
            onCreateCollection = {
                viewModel.onEvent(LibraryEvent.DismissBatchCollectionDialog)
                viewModel.onEvent(LibraryEvent.CreateCollectionClicked)
            }
        )
    }
}

// ─── Book Details Dialog (grid mode) ─────────────────────────────

@Composable
fun BookDetailsDialog(book: LibraryBook, onDismiss: () -> Unit, onOpenCover: (String) -> Unit, onRead: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    AlertDialog(onDismissRequest = onDismiss,
        confirmButton = { OutlinedButton(onClick = onRead) { Icon(Icons.AutoMirrored.Filled.MenuBook, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.book_details_read_btn)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = { Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Box(modifier = Modifier.fillMaxWidth().clickable { onOpenCover(book.coverUrl) }, contentAlignment = Alignment.Center) {
                    CoverWithAuthor(coverUrl = book.coverUrl, author = book.author, width = 120.dp, height = 170.dp, showFullName = false)
                }
                Spacer(Modifier.height(12.dp))
                Text(book.author, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.library_format, book.format.uppercase()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.library_added_date, dateFormat.format(Date(book.downloadDate))), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.library_progress), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text("${if (book.lastReadDate > 0) book.readingProgress else 0}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { (book.readingProgress.coerceIn(0, 100) / 100f) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primaryContainer)
                        if (book.lastReadDate > 0) { Spacer(Modifier.height(6.dp)); Text(stringResource(R.string.library_last_read, dateFormat.format(Date(book.lastReadDate))), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    )
}

// ─── Create Collection Dialog ────────────────────────────────────

@Composable
fun CreateCollectionDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.collection_create)) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.collection_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.collection_create_btn)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

// ─── Rename Collection Dialog ────────────────────────────────────

@Composable
fun RenameCollectionDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.collection_rename)) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.collection_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.collection_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

// ─── Delete Book Dialog ──────────────────────────────────────────

@Composable
fun DeleteBookDialog(deleteFromDevice: Boolean, onToggleDeleteFromDevice: () -> Unit, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.library_delete_title)) },
        text = {
            Column {
                Text(if (deleteFromDevice) stringResource(R.string.library_delete_body_device) else stringResource(R.string.library_delete_body_library))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleDeleteFromDevice).padding(vertical = 4.dp)) {
                    Checkbox(checked = deleteFromDevice, onCheckedChange = { onToggleDeleteFromDevice() })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.library_delete_from_device), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.cd_delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

// ─── Move Book Dialog ────────────────────────────────────────────

@Composable
fun MoveBookDialog(
    collections: List<BookCollection>, bookId: Int, bookFormat: String, currentCollections: List<String>,
    onDismiss: () -> Unit, onMoveToCollection: (String) -> Unit, onRemoveFromCollection: (String) -> Unit,
    onCreateCollection: () -> Unit = {}
) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.library_tab_collections), fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                if (collections.isEmpty()) { Text(stringResource(R.string.library_no_collections_title), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(8.dp)) }
                else {
                    collections.forEach { collection ->
                        val isIn = collection.id in currentCollections
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { if (isIn) onRemoveFromCollection(collection.id) else onMoveToCollection(collection.id) }.padding(horizontal = 4.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, null, modifier = Modifier.size(20.dp), tint = if (isIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text(collection.name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isIn) FontWeight.SemiBold else FontWeight.Normal, color = if (isIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            if (isIn) Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        if (collection != collections.last()) HorizontalDivider(modifier = Modifier.padding(start = 32.dp))
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
                TextButton(onClick = onCreateCollection, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.collection_create)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cd_done)) } })
}

// ─── Batch Delete Dialog ─────────────────────────────────────────

@Composable
fun BatchDeleteDialog(count: Int, deleteFromDevice: Boolean, onToggleDeleteFromDevice: () -> Unit, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.library_delete_batch_title, count)) },
        text = {
            Column {
                Text(stringResource(R.string.library_delete_batch_body))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleDeleteFromDevice).padding(vertical = 4.dp)) {
                    Checkbox(checked = deleteFromDevice, onCheckedChange = { onToggleDeleteFromDevice() })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.library_delete_batch_from_device), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.cd_delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

// ─── Batch Collection Dialog ─────────────────────────────────────

@Composable
fun BatchCollectionDialog(collections: List<BookCollection>, onDismiss: () -> Unit, onSelectCollection: (String) -> Unit, onCreateCollection: () -> Unit = {}) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.collection_add_to), fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                if (collections.isEmpty()) { Text(stringResource(R.string.library_no_collections_title), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(8.dp)) }
                else {
                    collections.forEach { collection ->
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onSelectCollection(collection.id) }.padding(horizontal = 4.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text(collection.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        }
                        if (collection != collections.last()) HorizontalDivider(modifier = Modifier.padding(start = 32.dp))
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
                TextButton(onClick = onCreateCollection, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.collection_create)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cd_done)) } })
}
