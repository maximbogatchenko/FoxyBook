package com.foxybook.app.features.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.foxybook.app.core.models.BookDetailsUiState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.foxybook.app.R
import com.foxybook.app.core.models.BookFormat
import com.foxybook.app.core.models.Bookmark
import com.foxybook.app.core.models.DownloadProgress
import com.foxybook.app.core.models.DownloadStatus
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import com.foxybook.app.navigation.Routes
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import com.foxybook.app.ui.components.CoverViewer
import com.foxybook.app.ui.components.CoverWithAuthor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookDetailsScreen(
    bookId: Int,
    viewModel: BookDetailsViewModel,
    onBackClick: () -> Unit,
    onReadBook: (String, String) -> Unit,
    onGoToSettings: () -> Unit = {},
    onGenreSearch: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var coverViewerUrl by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = (state.uiState as? BookDetailsUiState.Success)?.bookInfo?.title
                        ?: stringResource(R.string.book_details_title)
                    Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val uiState = state.uiState) {
                is BookDetailsUiState.Loading -> {
                    // Красивая анимация загрузки
                    BookLoadingAnimation()
                }
                is BookDetailsUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(R.string.book_details_loading_error), style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(uiState.message, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = {
                            viewModel.onEvent(BookDetailsEvent.LoadBook(bookId, null))
                        }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
                is BookDetailsUiState.Success -> {
                    val info = uiState.bookInfo
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        // Header: Cover + Title/Author
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CoverWithAuthor(
                                    coverUrl = info.coverUrl,
                                    author = info.author,
                                    contentDescription = info.title,
                                    width = 100.dp,
                                    height = 140.dp,
                                    onCoverClick = { url -> coverViewerUrl = url }
                                )
                                Spacer(modifier = Modifier.width(20.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(info.title, style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(info.author, style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    // Форматы в стиле Flibusta: (fb2) - (epub) - (mobi)
                                    val formatsText = state.availableFormats
                                        .joinToString(" - ") { "(${it.extension})" }
                                    if (formatsText.isNotBlank()) {
                                        Text(
                                            text = formatsText,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Description
                        if (info.description.isNotBlank()) {
                            SectionTitle(stringResource(R.string.book_details_description))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(info.description, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        // Genres
                        if (info.genres.isNotEmpty()) {
                            SectionTitle(stringResource(R.string.book_details_genres))
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                info.genres.forEach { genre ->
                                    AssistChip(
                                        onClick = { onGenreSearch(genre.title) },
                                        label = { Text(genre.title) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }


                        // Download section
                        SectionTitle(stringResource(R.string.book_details_download))
                        Spacer(modifier = Modifier.height(12.dp))

                        SingleDownloadButton(
                            state = state,
                            viewModel = viewModel,
                            onReadBook = onReadBook
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Bookmarks section
                        if (state.bookmarks.isNotEmpty()) {
                            SectionTitle(stringResource(R.string.book_details_bookmarks))
                            Spacer(modifier = Modifier.height(12.dp))
                            state.bookmarks.forEach { bookmark ->
                                BookmarkItem(
                                    bookmark = bookmark,
                                    onDelete = { viewModel.onEvent(BookDetailsEvent.RemoveBookmark(bookmark)) },
                                    onClick = {
                                        val downloadedFormat = state.downloads.entries.find { it.value.status == DownloadStatus.DOWNLOADED }
                                        if (downloadedFormat != null) {
                                            viewModel.onEvent(BookDetailsEvent.JumpToBookmark(bookmark, downloadedFormat.key.extension))
                                            onReadBook(downloadedFormat.value.filePath, downloadedFormat.key.extension)
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    if (state.showFolderErrorDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(BookDetailsEvent.DismissFolderError) },
            title = { Text(stringResource(R.string.book_details_folder_error_title)) },
            text = { Text(stringResource(R.string.book_details_folder_error_text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onEvent(BookDetailsEvent.DismissFolderError)
                    onGoToSettings()
                }) {
                    Text(stringResource(R.string.book_details_go_to_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(BookDetailsEvent.DismissFolderError) }) {
                    Text(stringResource(R.string.cancel))
                }
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

@Composable
private fun SectionTitle(title: String) {
    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun BookmarkItem(
    bookmark: Bookmark,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.bookmark_chapter, bookmark.chapterIndex + 1),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = dateFormat.format(Date(bookmark.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.cd_delete),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = bookmark.shortTextPreview,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DownloadButton(
    format: BookFormat,
    progress: DownloadProgress,
    onDownload: () -> Unit,
    onRead: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (progress.status) {
                            DownloadStatus.DOWNLOADED -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        }
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = format.name,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = when (progress.status) {
                            DownloadStatus.DOWNLOADED -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (progress.status) {
                            DownloadStatus.IDLE -> stringResource(R.string.book_details_ready)
                            DownloadStatus.DOWNLOADING -> {
                                if (progress.percent < 0) stringResource(R.string.book_details_downloading) else stringResource(R.string.book_details_downloading_progress, progress.percent)
                            }
                            DownloadStatus.DOWNLOADED -> stringResource(R.string.book_details_downloaded)
                            DownloadStatus.ERROR -> progress.error ?: stringResource(R.string.book_details_error)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (progress.status == DownloadStatus.DOWNLOADED) FontWeight.SemiBold else FontWeight.Normal,
                        color = when (progress.status) {
                            DownloadStatus.DOWNLOADED -> MaterialTheme.colorScheme.primary
                            DownloadStatus.ERROR -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                when (progress.status) {
                    DownloadStatus.IDLE, DownloadStatus.ERROR -> {
                        OutlinedButton(
                            onClick = onDownload,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.book_details_download))
                        }
                    }
                    DownloadStatus.DOWNLOADING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    DownloadStatus.DOWNLOADED -> {
                        OutlinedButton(
                            onClick = onRead,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp)
                        ) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.book_details_read_btn))
                        }
                    }
                }
            }

            if (progress.status == DownloadStatus.DOWNLOADING) {
                Spacer(modifier = Modifier.height(10.dp))
                if (progress.percent >= 0) {
                    LinearProgressIndicator(
                        progress = { progress.percent / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SingleDownloadButton(
    state: BookDetailsState,
    viewModel: BookDetailsViewModel,
    onReadBook: (String, String) -> Unit
) {
    val context = LocalContext.current
    val selectedFormat = state.selectedFormat
    val progress = state.downloads[selectedFormat] ?: DownloadProgress()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // ── Заголовок ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.book_details_format),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = selectedFormat.name.uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Выпадающий список форматов ──
            var expandedFormats by remember { mutableStateOf(false) }

            Box {
                // Триггер — красивая карточка с выбранным форматом
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { expandedFormats = true }
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Иконка формата
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = selectedFormat.name.take(2).uppercase(),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedFormat.name.uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(formatDescriptionRes(selectedFormat)),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val isDownloaded = state.downloads[selectedFormat]?.status == DownloadStatus.DOWNLOADED
                        if (isDownloaded) {
                            Text(
                                text = "✓",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }

                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Выпадающее меню
                DropdownMenu(
                    expanded = expandedFormats,
                    onDismissRequest = { expandedFormats = false },
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(16.dp)
                        )
                        .shadow(8.dp, RoundedCornerShape(16.dp))
                ) {
                    state.availableFormats.forEachIndexed { index, format ->
                        val isDownloaded = state.downloads[format]?.status == DownloadStatus.DOWNLOADED
                        val isSelected = format == selectedFormat

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.onEvent(BookDetailsEvent.SelectFormat(format))
                                    expandedFormats = false
                                }
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else Color.Transparent
                                )
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Бейдж формата
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = format.name.take(2).uppercase(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = format.name.uppercase(),
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(formatDescriptionRes(format)),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isDownloaded) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.book_details_downloaded_label),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }

                            if (isSelected) {
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                )
                            }
                        }

                        if (index < state.availableFormats.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Кнопка действия ──
            when (progress.status) {
                DownloadStatus.IDLE, DownloadStatus.ERROR -> {
                    androidx.compose.material3.Button(
                        onClick = { viewModel.onEvent(BookDetailsEvent.DownloadPrimary) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.book_details_download_btn), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                    }
                    if (progress.status == DownloadStatus.ERROR) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = progress.error ?: stringResource(R.string.settings_download_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                DownloadStatus.DOWNLOADING -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = if (progress.percent < 0) stringResource(R.string.book_details_downloading) else stringResource(R.string.book_details_downloading_progress, progress.percent),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { if (progress.percent >= 0) progress.percent / 100f else 0f },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer,
                        )
                    }
                }
                DownloadStatus.DOWNLOADED -> {
                    androidx.compose.material3.Button(
                        onClick = {
                            if (selectedFormat.isNativelySupported()) {
                                onReadBook(progress.filePath, selectedFormat.extension)
                            } else {
                                openBookExternally(context, progress.filePath, selectedFormat.mimeType)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.book_details_read), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
fun openBookExternally(context: Context, filePath: String, mimeType: String) {
    try {
        val uri = if (filePath.startsWith("content://")) {
            // MediaStore URI — копируем в cache, чтобы внешнее приложение точно получило доступ
            val inputStream = context.contentResolver.openInputStream(Uri.parse(filePath))
            if (inputStream != null) {
                val ext = mimeToExtension(mimeType)
                val tempFile = File(context.cacheDir, "share/${System.currentTimeMillis()}.$ext")
                tempFile.parentFile?.mkdirs()
                inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
            } else {
                Uri.parse(filePath)
            }
        } else if (filePath.startsWith("file://")) {
            Uri.parse(filePath)
        } else {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(filePath)
            )
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // createChooser обходит ограничение Android 14+ на неявные интенты
        context.startActivity(Intent.createChooser(intent, null))
    } catch (_: Exception) {
        android.widget.Toast.makeText(context, context.getString(R.string.book_details_external_open_error), android.widget.Toast.LENGTH_SHORT).show()
    }
}

/** Определяет расширение файла по MIME-типу для копирования в кэш */
private fun mimeToExtension(mimeType: String): String {
    return when {
        mimeType.contains("epub") -> "epub"
        mimeType.contains("mobipocket") || mimeType.contains("mobi") -> "mobi"
        mimeType.contains("fb2") || mimeType.contains("fictionbook") -> "fb2"
        mimeType.contains("pdf") -> "pdf"
        mimeType.contains("plain") || mimeType.contains("text") -> "txt"
        else -> "pdf"
    }
}

// ═══════════════════════════════════════════════════════════════
//  Shimmer Loading Components
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ShimmerLoadingCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerTranslate = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover placeholder
            Box(
                modifier = Modifier
                    .size(width = 100.dp, height = 140.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            start = Offset(shimmerTranslate.value - 200f, shimmerTranslate.value - 200f),
                            end = Offset(shimmerTranslate.value, shimmerTranslate.value)
                        )
                    )
            )
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                // Title placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                start = Offset(shimmerTranslate.value - 200f, shimmerTranslate.value - 200f),
                                end = Offset(shimmerTranslate.value, shimmerTranslate.value)
                            )
                        )
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Author placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                start = Offset(shimmerTranslate.value - 200f, shimmerTranslate.value - 200f),
                                end = Offset(shimmerTranslate.value, shimmerTranslate.value)
                            )
                        )
                )
                Spacer(modifier = Modifier.height(12.dp))
                // Format placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                start = Offset(shimmerTranslate.value - 200f, shimmerTranslate.value - 200f),
                                end = Offset(shimmerTranslate.value, shimmerTranslate.value)
                            )
                        )
                )
            }
        }
    }
}

@Composable
fun BookLoadingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")

    // Пульсация иконки
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    // Вращение градиента
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Анимированные точки
    val dotsCount by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dots"
    )

    val dots = ".".repeat(dotsCount.toInt().coerceIn(0, 4))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        MaterialTheme.colorScheme.surface
                    ),
                    radius = 800f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Анимированная иконка книги с градиентным кольцом
            Box(contentAlignment = Alignment.Center) {
                // Вращающееся кольцо
                CircularProgressIndicator(
                    progress = { 0f },
                    modifier = Modifier.size(120.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    strokeWidth = 2.dp,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                )

                Icon(
                    Icons.Default.AutoStories,
                    contentDescription = stringResource(R.string.loading),
                    modifier = Modifier
                        .size(64.dp)
                        .scale(scale),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Текст с точками
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.book_details_loading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = dots,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.book_details_loading_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

private fun formatDescriptionRes(format: BookFormat): Int = when (format) {
    BookFormat.EPUB -> R.string.format_description_epub
    BookFormat.FB2 -> R.string.format_description_fb2
    BookFormat.MOBI -> R.string.format_description_mobi
    BookFormat.TXT -> R.string.format_description_txt
    BookFormat.PDF -> R.string.format_description_pdf
}
