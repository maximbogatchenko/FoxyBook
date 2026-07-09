package com.foxybook.app.features.reader

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foxybook.app.R

@Composable
fun TtsControlsUI(state: ReaderState, viewModel: ReaderViewModel) {
    val context = LocalContext.current
    val engineLabel = state.currentEngine?.let { pkg ->
        when {
            pkg.contains("google") -> "Google TTS"
            pkg.contains("samsung") -> "Samsung TTS"
            pkg.contains("rhvoice") -> "RH Voice"
            pkg.contains("acapela") -> "Acapela TTS"
            pkg.contains("vocalizer") -> "Vocalizer TTS"
            pkg.contains("espeak") -> "eSpeak TTS"
            pkg.contains("microsoft") -> "Microsoft TTS"
            pkg.contains("amazon") -> "Amazon TTS"
            pkg.contains("ibm") -> "IBM TTS"
            else -> pkg.substringAfterLast(".")
        }
    } ?: stringResource(R.string.reader_tts_default_engine)
    var selectedLang by remember(state.settings.ttsLanguage, state.availableLanguages) {
        mutableStateOf(state.settings.ttsLanguage ?: state.availableLanguages.firstOrNull { it == "Русский" } ?: state.availableLanguages.firstOrNull() ?: "")
    }

    val filteredVoices = remember(selectedLang, state.availableVoices) {
        state.availableVoices.filter { it.language == selectedLang }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.onEvent(ReaderEvent.ToggleTtsControls) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
            }
            Text(stringResource(R.string.reader_tts_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = {
                try {
                    val intent = Intent().apply {
                        action = "com.android.settings.TTS_SETTINGS"
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent().apply {
                        action = android.provider.Settings.ACTION_SETTINGS
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            }) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_tts_select))
            }
        }

        // Выбор TTS-движка
        if (state.availableEngines.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.reader_tts_engine), style = MaterialTheme.typography.labelLarge)
            LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.availableEngines) { engine ->
                    val isSelected = state.settings.ttsEngine == engine.packageName
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onEvent(ReaderEvent.SetTtsEngine(engine.packageName)) },
                        label = { Text(engine.label) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!state.isSpeaking && !state.isPaused) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = { viewModel.onEvent(ReaderEvent.StartTts()) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.reader_tts_play_current))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.onEvent(ReaderEvent.StartTtsSelection) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
                    ) {
                        Icon(Icons.Default.TouchApp, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.reader_tts_select_position))
                    }
                }
            } else {
                if (state.isSpeaking) {
                    IconButton(onClick = { viewModel.onEvent(ReaderEvent.PauseTts) }, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Default.Pause, stringResource(R.string.cd_pause), modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    IconButton(onClick = { viewModel.onEvent(ReaderEvent.ResumeTts) }, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Default.PlayArrow, stringResource(R.string.cd_play), modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.width(32.dp))

                IconButton(onClick = { viewModel.onEvent(ReaderEvent.StopTts) }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Default.Stop, stringResource(R.string.cd_stop), modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Настройки языка и голоса показываем только для Google TTS
        // Сторонние движки используют свою систему голосов
        if (state.currentEngine?.contains("google") == true) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(stringResource(R.string.reader_tts_language), style = MaterialTheme.typography.labelLarge)
            LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.availableLanguages) { lang ->
                    FilterChip(
                        selected = selectedLang == lang,
                        onClick = {
                            selectedLang = lang
                            viewModel.onEvent(ReaderEvent.SetTtsLanguage(lang))
                        },
                        label = { Text(lang) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(stringResource(R.string.reader_tts_voice), style = MaterialTheme.typography.labelLarge)
            LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredVoices) { voice ->
                    FilterChip(
                        selected = state.settings.ttsVoice == voice.id,
                        onClick = { viewModel.onEvent(ReaderEvent.SetTtsVoice(voice.id)) },
                        label = { Text(voice.name) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reader_tts_speed_format, state.settings.ttsRate), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(80.dp))
            Slider(
                value = state.settings.ttsRate,
                onValueChange = { viewModel.onEvent(ReaderEvent.SetTtsRate(it)) },
                valueRange = 0.5f..2.5f,
                modifier = Modifier.weight(1f)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reader_tts_pitch_format, state.settings.ttsPitch), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(80.dp))
            Slider(
                value = state.settings.ttsPitch,
                onValueChange = { viewModel.onEvent(ReaderEvent.SetTtsPitch(it)) },
                valueRange = 0.5f..2.0f,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(stringResource(R.string.reader_tts_sleep_timer), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(12.dp))

        if (state.sleepTimerRemainingSeconds > 0) {
            val minutes = state.sleepTimerRemainingSeconds / 60
            val seconds = state.sleepTimerRemainingSeconds % 60
            val progress = state.sleepTimerRemainingSeconds.toFloat() / (state.sleepTimerRemainingSeconds + 1f)

            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.reader_tts_sleep_active),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                String.format("%d:%02d", minutes, seconds),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { viewModel.onEvent(ReaderEvent.CancelSleepTimer) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.cd_cancel),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.reader_tts_sleep_auto_stop),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Text(
                stringResource(R.string.reader_tts_sleep_choose),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(listOf(5, 10, 15, 20, 30, 45, 60)) { minutes ->
                val isSelected = state.sleepTimerRemainingSeconds > 0 &&
                    state.sleepTimerRemainingSeconds / 60 == minutes.toLong() &&
                    state.sleepTimerRemainingSeconds % 60 < 60
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.onEvent(ReaderEvent.SetSleepTimer(minutes)) },
                    label = { Text("$minutes ${stringResource(R.string.reader_tts_minutes)}") },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) }
                    } else null
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
