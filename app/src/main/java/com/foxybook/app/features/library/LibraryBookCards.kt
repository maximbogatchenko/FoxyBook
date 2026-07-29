package com.foxybook.app.features.library

import androidx.compose.ui.res.stringResource
import com.foxybook.app.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foxybook.app.core.models.LibraryBook
import com.foxybook.app.ui.components.CoverWithAuthor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── List Book Card ──────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryBookCard(
    book: LibraryBook, isSelected: Boolean = false, onClick: () -> Unit, onLongClick: () -> Unit = {},
    onToggleFavorite: () -> Unit, onMove: () -> Unit, onDelete: () -> Unit, onCoverClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val shape = remember { RoundedCornerShape(16.dp) }
    var showMenu by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface),
        shape = shape, elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 1.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isSelected) { Icon(Icons.Default.CheckCircle, stringResource(R.string.cd_selected), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(8.dp)) }
            CoverWithAuthor(coverUrl = book.coverUrl, author = book.author, contentDescription = book.title, width = 56.dp, height = 78.dp, onCoverClick = { onCoverClick() })
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(book.format.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val date = if (book.lastReadDate > 0) Date(book.lastReadDate) else Date(book.downloadDate)
                    Text(dateFormat.format(date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                Icon(if (book.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, stringResource(R.string.cd_favorites),
                    tint = if (book.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.MoreVert, stringResource(R.string.cd_more), modifier = Modifier.size(20.dp)) }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.cd_add_to_collection)) }, onClick = { showMenu = false; onMove() }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, modifier = Modifier.size(18.dp)) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.cd_delete), color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) })
                }
            }
        }
    }
}

// ─── Compact Book Card ───────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactBookCard(
    book: LibraryBook, isSelected: Boolean = false, onClick: () -> Unit, onLongClick: () -> Unit = {},
    onToggleFavorite: () -> Unit, onMoveToCollection: () -> Unit, onDeleteBook: () -> Unit, onCoverClick: () -> Unit
) {
    val shape = remember { RoundedCornerShape(12.dp) }
    var showMenu by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface),
        shape = shape, elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 1.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isSelected) { Icon(Icons.Default.CheckCircle, stringResource(R.string.cd_selected), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)) }
            CoverWithAuthor(coverUrl = book.coverUrl, author = book.author, contentDescription = book.title, width = 40.dp, height = 56.dp, onCoverClick = { onCoverClick() })
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) { Icon(if (book.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, stringResource(R.string.cd_favorites), tint = if (book.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, stringResource(R.string.cd_more), modifier = Modifier.size(18.dp)) }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.cd_add_to_collection)) }, onClick = { showMenu = false; onMoveToCollection() }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, modifier = Modifier.size(18.dp)) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.cd_delete), color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDeleteBook() }, leadingIcon = { Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) })
                }
            }
        }
    }
}

// ─── Grid Book Card ──────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridBookCard(
    book: LibraryBook, isSelected: Boolean = false, isSelectionMode: Boolean = false,
    onCoverClick: () -> Unit, onReadClick: () -> Unit, onLongClick: () -> Unit = {},
    onToggleFavorite: () -> Unit, onMoveToCollection: () -> Unit, onDeleteBook: () -> Unit,
    isLandscape: Boolean = false
) {
    val shape = remember { RoundedCornerShape(12.dp) }
    var showMenu by remember { mutableStateOf(false) }
    val coverWidth = if (isLandscape) 100.dp else 140.dp
    val coverHeight = if (isLandscape) 150.dp else 200.dp

    Card(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onReadClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface),
        shape = shape, elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 2.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.fillMaxWidth().clickable(onClick = onCoverClick), contentAlignment = Alignment.Center) {
                Box {
                    CoverWithAuthor(coverUrl = book.coverUrl, author = book.author, contentDescription = book.title, width = coverWidth, height = coverHeight, showFullName = false)
                    if (isSelected) { Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))); Icon(Icons.Default.CheckCircle, stringResource(R.string.cd_selected), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.Center).size(48.dp)) }
                    Box(modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(32.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)) {
                        IconButton(onClick = onToggleFavorite, modifier = Modifier.fillMaxSize().padding(2.dp)) { Icon(if (book.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, stringResource(R.string.cd_favorites), tint = if (book.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
                    }
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(32.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)) {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, stringResource(R.string.cd_more), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.cd_add_to_collection)) }, onClick = { showMenu = false; onMoveToCollection() }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, modifier = Modifier.size(18.dp)) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.cd_delete), color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDeleteBook() }, leadingIcon = { Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) })
                        }
                    }
                }
            }
            Text(book.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp))
            Text(book.author, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp))
            OutlinedButton(onClick = onReadClick, modifier = Modifier.padding(bottom = if (isLandscape) 4.dp else 10.dp), shape = RoundedCornerShape(8.dp)) {
                Text(if (isSelectionMode) stringResource(R.string.library_select) else stringResource(R.string.book_details_read_btn), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
