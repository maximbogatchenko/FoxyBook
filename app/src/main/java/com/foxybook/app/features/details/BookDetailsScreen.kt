package com.foxybook.app.features.details

import androidx.compose.ui.res.stringResource
import com.foxybook.app.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foxybook.app.core.models.BookDetailsUiState
import com.foxybook.app.core.models.Bookmark
import com.foxybook.app.core.models.DownloadStatus
import com.foxybook.app.ui.components.CoverViewer
import com.foxybook.app.ui.components.CoverWithAuthor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookDetailsScreen(
    bookId: Int, viewModel: BookDetailsViewModel, onBackClick: () -> Unit,
    onReadBook: (String, String) -> Unit, onGoToSettings: () -> Unit = {}, onGenreSearch: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var coverViewerUrl by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        TopAppBar(title = { Text((state.uiState as? BookDetailsUiState.Success)?.bookInfo?.title ?: stringResource(R.string.book_details_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
    }) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (val uiState = state.uiState) {
                is BookDetailsUiState.Loading -> BookLoadingAnimation()
                is BookDetailsUiState.Error -> ErrorView(uiState.message) { viewModel.onEvent(BookDetailsEvent.LoadBook(bookId, null)) }
                is BookDetailsUiState.Success -> {
                    val info = uiState.bookInfo
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        // Header
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)), shape = RoundedCornerShape(20.dp)) {
                            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                CoverWithAuthor(coverUrl = info.coverUrl, author = info.author, contentDescription = info.title, width = 100.dp, height = 140.dp, onCoverClick = { url -> coverViewerUrl = url })
                                Spacer(Modifier.width(20.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(info.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(6.dp))
                                    Text(info.author, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(8.dp))
                                    val formatsText = state.availableFormats.joinToString(" - ") { "(${it.extension})" }
                                    if (formatsText.isNotBlank()) Text(formatsText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                }
                            }
                        }
                        Spacer(Modifier.height(20.dp))

                        // Description
                        if (info.description.isNotBlank()) {
                            SectionTitle(stringResource(R.string.book_details_description))
                            Spacer(Modifier.height(6.dp))
                            Text(info.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(20.dp))
                        }

                        // Genres
                        if (info.genres.isNotEmpty()) {
                            SectionTitle(stringResource(R.string.book_details_genres))
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                info.genres.forEach { genre -> AssistChip(onClick = { onGenreSearch(genre.title) }, label = { Text(genre.title) }) }
                            }
                            Spacer(Modifier.height(16.dp))
                        }

                        // Download
                        SectionTitle(stringResource(R.string.book_details_download))
                        Spacer(Modifier.height(12.dp))
                        SingleDownloadButton(state = state, viewModel = viewModel, onReadBook = onReadBook)
                        Spacer(Modifier.height(24.dp))

                        // Bookmarks
                        if (state.bookmarks.isNotEmpty()) {
                            SectionTitle(stringResource(R.string.book_details_bookmarks))
                            Spacer(Modifier.height(12.dp))
                            state.bookmarks.forEach { bookmark ->
                                BookmarkItem(bookmark = bookmark, onDelete = { viewModel.onEvent(BookDetailsEvent.RemoveBookmark(bookmark)) },
                                    onClick = {
                                        val downloadedFormat = state.downloads.entries.find { it.value.status == DownloadStatus.DOWNLOADED }
                                        if (downloadedFormat != null) {
                                            viewModel.onEvent(BookDetailsEvent.JumpToBookmark(bookmark, downloadedFormat.key.extension))
                                            onReadBook(downloadedFormat.value.filePath, downloadedFormat.key.extension)
                                        }
                                    })
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    if (state.showFolderErrorDialog) {
        AlertDialog(onDismissRequest = { viewModel.onEvent(BookDetailsEvent.DismissFolderError) },
            title = { Text(stringResource(R.string.book_details_folder_error_title)) },
            text = { Text(stringResource(R.string.book_details_folder_error_text)) },
            confirmButton = { TextButton(onClick = { viewModel.onEvent(BookDetailsEvent.DismissFolderError); onGoToSettings() }) { Text(stringResource(R.string.book_details_go_to_settings)) } },
            dismissButton = { TextButton(onClick = { viewModel.onEvent(BookDetailsEvent.DismissFolderError) }) { Text(stringResource(R.string.cancel)) } })
    }
    if (coverViewerUrl != null) CoverViewer(coverUrl = coverViewerUrl!!, onDismiss = { coverViewerUrl = null })
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Error, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.book_details_loading_error), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
        }
    }

// ─── Section Title ───────────────────────────────────────────────

@Composable
fun SectionTitle(title: String) {
    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

// ─── Bookmark Item ───────────────────────────────────────────────

@Composable
fun BookmarkItem(bookmark: Bookmark, onDelete: () -> Unit, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)), shape = RoundedCornerShape(12.dp), onClick = onClick) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bookmark, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.bookmark_chapter, bookmark.chapterIndex + 1), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                Text(dateFormat.format(Date(bookmark.createdAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Delete, stringResource(R.string.cd_delete), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)) }
            }
            Spacer(Modifier.height(4.dp))
            Text(bookmark.shortTextPreview, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
