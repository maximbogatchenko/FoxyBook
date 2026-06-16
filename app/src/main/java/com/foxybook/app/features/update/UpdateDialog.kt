package com.foxybook.app.features.update

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foxybook.app.core.updater.UpdateChecker
import com.foxybook.app.core.updater.UpdateInfo
import kotlinx.coroutines.launch

private sealed class UpdateDialogState {
    data object Idle : UpdateDialogState()
    data object Checking : UpdateDialogState()
    data class Available(val info: UpdateInfo) : UpdateDialogState()
    data object Downloading : UpdateDialogState()
    data class Downloaded(val uri: Uri) : UpdateDialogState()
    data class Error(val message: String) : UpdateDialogState()
}

@Composable
fun UpdateDialog(updateChecker: UpdateChecker) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<UpdateDialogState>(UpdateDialogState.Idle) }
    var downloadProgress by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        state = UpdateDialogState.Checking
        try {
            val currentVersion = updateChecker.getCurrentVersion()
            val info = updateChecker.checkForUpdate(currentVersion)
            state = if (info != null) {
                UpdateDialogState.Available(info)
            } else {
                UpdateDialogState.Idle
            }
        } catch (_: Exception) {
            state = UpdateDialogState.Idle
        }
    }

    when (val currentState = state) {
        is UpdateDialogState.Available -> {
            AlertDialog(
                onDismissRequest = { state = UpdateDialogState.Idle },
                icon = {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null)
                },
                title = { Text("Доступно обновление", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Версия ${currentState.info.version} доступна для скачивания.")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            formatSize(currentState.info.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        ChangeLogSpoiler(releaseNotes = currentState.info.releaseNotes)
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        scope.launch {
                            state = UpdateDialogState.Downloading
                            downloadProgress = 0f
                            try {
                                val uri = updateChecker.downloadApk(currentState.info.downloadUrl) { p ->
                                    downloadProgress = p
                                }
                                state = UpdateDialogState.Downloaded(uri)
                            } catch (e: Exception) {
                                state = UpdateDialogState.Error(e.message ?: "Ошибка скачивания")
                            }
                        }
                    }) { Text("Скачать") }
                },
                dismissButton = {
                    TextButton(onClick = { state = UpdateDialogState.Idle }) { Text("Позже") }
                }
            )
        }

        is UpdateDialogState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                icon = {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                },
                title = { Text("Скачивание...", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${(downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {}
            )
        }

        is UpdateDialogState.Downloaded -> {
            AlertDialog(
                onDismissRequest = { state = UpdateDialogState.Idle },
                icon = {
                    Icon(
                        Icons.Default.CheckCircle, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = { Text("Обновление загружено", fontWeight = FontWeight.Bold) },
                text = { Text("Нажмите «Установить», чтобы обновить приложение.") },
                confirmButton = {
                    Button(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(currentState.uri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        state = UpdateDialogState.Idle
                    }) { Text("Установить") }
                },
                dismissButton = {
                    TextButton(onClick = { state = UpdateDialogState.Idle }) { Text("Позже") }
                }
            )
        }

        is UpdateDialogState.Error -> {
            AlertDialog(
                onDismissRequest = { state = UpdateDialogState.Idle },
                icon = {
                    Icon(
                        Icons.Default.ErrorOutline, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = { Text("Ошибка", fontWeight = FontWeight.Bold) },
                text = { Text(currentState.message) },
                confirmButton = {
                    Button(onClick = { state = UpdateDialogState.Idle }) { Text("Закрыть") }
                },
                dismissButton = {}
            )
        }

        UpdateDialogState.Checking,
        UpdateDialogState.Idle -> { /* no dialog */ }
    }
}

@Composable
private fun ChangeLogSpoiler(releaseNotes: String) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "Что нового",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (expanded) {
            Spacer(Modifier.height(6.dp))
            HorizontalDivider()
            Spacer(Modifier.height(6.dp))
            if (releaseNotes.isBlank()) {
                Text(
                    "Автор не указал список изменений",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    val lines = releaseNotes
                        .replace("\r\n", "\n")
                        .split("\n")
                    for (line in lines) {
                        val trimmed = line.trim()
                        when {
                            trimmed.startsWith("## ") || trimmed.startsWith("### ") -> {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    trimmed.removePrefix("##").removePrefix("###").trimStart(' '),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(2.dp))
                            }
                            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                                Row(Modifier.padding(start = 8.dp)) {
                                    Text("•  ", color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        trimmed.removePrefix("- ").removePrefix("* "),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            trimmed.isNotBlank() -> {
                                Text(
                                    trimmed,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000 -> "${"%.1f".format(bytes / 1_000_000f)} MB"
        bytes >= 1_000 -> "${"%.0f".format(bytes / 1_000f)} KB"
        else -> "$bytes B"
    }
}
