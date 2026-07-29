package com.foxybook.app.features.details

import androidx.compose.ui.res.stringResource
import com.foxybook.app.R

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.foxybook.app.core.models.BookFormat
import com.foxybook.app.core.models.DownloadProgress
import com.foxybook.app.core.models.DownloadStatus
import java.io.File

// ─── Single Download Button (main download section) ──────────────

@Composable
fun SingleDownloadButton(
    state: BookDetailsState,
    viewModel: BookDetailsViewModel,
    onReadBook: (String, String) -> Unit
) {
    val context = LocalContext.current
    val selectedFormat = state.selectedFormat
    val progress = state.downloads[selectedFormat] ?: DownloadProgress()

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.book_details_format), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                Text(selectedFormat.name.uppercase(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(12.dp))

            // Format selector dropdown
            var expandedFormats by remember { mutableStateOf(false) }
            FormatSelector(state = state, expandedFormats = expandedFormats, onToggle = { expandedFormats = !expandedFormats }, onDismiss = { expandedFormats = false },
                onSelect = { viewModel.onEvent(BookDetailsEvent.SelectFormat(it)); expandedFormats = false })

            Spacer(Modifier.height(16.dp))

            // Action button
            when (progress.status) {
                DownloadStatus.IDLE, DownloadStatus.ERROR -> {
                    Button(onClick = { viewModel.onEvent(BookDetailsEvent.DownloadPrimary) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) { Icon(Icons.Default.Download, null, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.book_details_download_btn), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    if (progress.status == DownloadStatus.ERROR) { Spacer(Modifier.height(8.dp)); Text(progress.error ?: stringResource(R.string.settings_download_error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth()) }
                }
                DownloadStatus.DOWNLOADING -> {
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(if (progress.percent < 0) stringResource(R.string.book_details_downloading) else stringResource(R.string.book_details_downloading_progress, progress.percent), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(progress = { if (progress.percent >= 0) progress.percent / 100f else 0f }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primaryContainer)
                    }
                }
                DownloadStatus.DOWNLOADED -> {
                    Button(onClick = {
                        if (selectedFormat.isNativelySupported()) onReadBook(progress.filePath, selectedFormat.extension)
                        else openBookExternally(context, progress.filePath, selectedFormat.mimeType)
                    }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) { Icon(Icons.AutoMirrored.Filled.MenuBook, null, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.book_details_read), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// ─── Format Selector Dropdown ────────────────────────────────────

@Composable
private fun FormatSelector(state: BookDetailsState, expandedFormats: Boolean, onToggle: () -> Unit, onDismiss: () -> Unit, onSelect: (BookFormat) -> Unit) {
    val selectedFormat = state.selectedFormat
    Box {
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).clickable(onClick = onToggle).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Text(selectedFormat.name.take(2).uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(selectedFormat.name.uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(formatDescriptionRes(selectedFormat)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.downloads[selectedFormat]?.status == DownloadStatus.DOWNLOADED) { Text("✓", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(end = 4.dp)) }
                Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            }
        }
        DropdownMenu(expanded = expandedFormats, onDismissRequest = onDismiss, modifier = Modifier.fillMaxWidth(0.9f).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).shadow(8.dp, RoundedCornerShape(16.dp))) {
            state.availableFormats.forEachIndexed { index, format ->
                val isDownloaded = state.downloads[format]?.status == DownloadStatus.DOWNLOADED
                val isSelected = format == selectedFormat
                Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(format) }.background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        Text(format.name.take(2).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(format.name.uppercase(), fontSize = 15.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(formatDescriptionRes(format)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (isDownloaded) {
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text(stringResource(R.string.book_details_downloaded_label), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    if (isSelected) { Spacer(Modifier.width(8.dp)); Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) }
                }
                if (index < state.availableFormats.lastIndex) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        }
    }
}

// ─── Legacy Download Button (may be used in other contexts) ──────

@Composable
fun DownloadButton(format: BookFormat, progress: DownloadProgress, onDownload: () -> Unit, onRead: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)), shape = RoundedCornerShape(14.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Card(colors = CardDefaults.cardColors(containerColor = when (progress.status) { DownloadStatus.DOWNLOADED -> MaterialTheme.colorScheme.primaryContainer; else -> MaterialTheme.colorScheme.secondaryContainer }), shape = RoundedCornerShape(8.dp)) {
                    Text(format.name, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = when (progress.status) { DownloadStatus.DOWNLOADED -> MaterialTheme.colorScheme.onPrimaryContainer; else -> MaterialTheme.colorScheme.onSecondaryContainer })
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(when (progress.status) { DownloadStatus.IDLE -> stringResource(R.string.book_details_ready); DownloadStatus.DOWNLOADING -> { if (progress.percent < 0) stringResource(R.string.book_details_downloading) else stringResource(R.string.book_details_downloading_progress, progress.percent) }; DownloadStatus.DOWNLOADED -> stringResource(R.string.book_details_downloaded); DownloadStatus.ERROR -> progress.error ?: stringResource(R.string.book_details_error) }, style = MaterialTheme.typography.bodyMedium, fontWeight = if (progress.status == DownloadStatus.DOWNLOADED) FontWeight.SemiBold else FontWeight.Normal, color = when (progress.status) { DownloadStatus.DOWNLOADED -> MaterialTheme.colorScheme.primary; DownloadStatus.ERROR -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant })
                }
                when (progress.status) {
                    DownloadStatus.IDLE, DownloadStatus.ERROR -> OutlinedButton(onClick = onDownload, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 14.dp)) { Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.book_details_download)) }
                    DownloadStatus.DOWNLOADING -> CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.primary)
                    DownloadStatus.DOWNLOADED -> OutlinedButton(onClick = onRead, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 14.dp)) { Icon(Icons.AutoMirrored.Filled.MenuBook, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.book_details_read_btn)) }
                }
            }
            if (progress.status == DownloadStatus.DOWNLOADING) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { progress.percent / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}

// ─── External book opener ────────────────────────────────────────

fun openBookExternally(context: Context, filePath: String, mimeType: String) {
    try {
        val uri = if (filePath.startsWith("content://")) {
            val inputStream = context.contentResolver.openInputStream(Uri.parse(filePath))
            if (inputStream != null) {
                val ext = mimeToExtension(mimeType)
                val tempFile = File(context.cacheDir, "share/${System.currentTimeMillis()}.$ext")
                tempFile.parentFile?.mkdirs()
                inputStream.use { input -> tempFile.outputStream().use { output -> input.copyTo(output) } }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
            } else Uri.parse(filePath)
        } else if (filePath.startsWith("file://")) Uri.parse(filePath)
        else FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(filePath))
        val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, mimeType); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        context.startActivity(Intent.createChooser(intent, null))
    } catch (_: Exception) { android.widget.Toast.makeText(context, context.getString(R.string.book_details_external_open_error), android.widget.Toast.LENGTH_SHORT).show() }
}

fun mimeToExtension(mimeType: String): String = when {
    mimeType.contains("epub") -> "epub"
    mimeType.contains("mobipocket") || mimeType.contains("mobi") -> "mobi"
    mimeType.contains("fb2") || mimeType.contains("fictionbook") -> "fb2"
    mimeType.contains("pdf") -> "pdf"
    mimeType.contains("plain") || mimeType.contains("text") -> "txt"
    else -> "pdf"
}

fun formatDescriptionRes(format: BookFormat): Int = when (format) {
    BookFormat.EPUB -> R.string.format_description_epub
    BookFormat.FB2 -> R.string.format_description_fb2
    BookFormat.MOBI -> R.string.format_description_mobi
    BookFormat.TXT -> R.string.format_description_txt
    BookFormat.PDF -> R.string.format_description_pdf
}
