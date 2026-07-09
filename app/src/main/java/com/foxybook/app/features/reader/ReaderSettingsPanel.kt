package com.foxybook.app.features.reader

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foxybook.app.R
import com.foxybook.app.core.models.ReaderMode
import com.foxybook.app.core.models.ReaderTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(settings: com.foxybook.app.core.models.ReaderSettings, viewModel: ReaderViewModel) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = { viewModel.onEvent(ReaderEvent.ToggleSettings) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 16.dp)
            ) {
                if (!state.showTtsControls) {
                    Text(stringResource(R.string.reader_settings_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(stringResource(R.string.reader_settings_theme_book), fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            ThemeChip(
                                selected = ReaderTheme.valueOf(settings.readerTheme) == ReaderTheme.LIGHT,
                                onClick = { viewModel.onEvent(ReaderEvent.ReaderThemeChanged(ReaderTheme.LIGHT)) },
                                label = stringResource(R.string.reader_settings_theme_light), icon = Icons.Default.LightMode,
                                modifier = Modifier.weight(1f)
                            )
                            ThemeChip(
                                selected = ReaderTheme.valueOf(settings.readerTheme) == ReaderTheme.DARK,
                                onClick = { viewModel.onEvent(ReaderEvent.ReaderThemeChanged(ReaderTheme.DARK)) },
                                label = stringResource(R.string.reader_settings_theme_dark), icon = Icons.Default.DarkMode,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            ThemeChip(
                                selected = ReaderTheme.valueOf(settings.readerTheme) == ReaderTheme.SYSTEM,
                                onClick = { viewModel.onEvent(ReaderEvent.ReaderThemeChanged(ReaderTheme.SYSTEM)) },
                                label = stringResource(R.string.reader_settings_theme_system), icon = Icons.Default.Smartphone,
                                modifier = Modifier.weight(1f)
                            )
                            ThemeChip(
                                selected = ReaderTheme.valueOf(settings.readerTheme) == ReaderTheme.AMOLED,
                                onClick = { viewModel.onEvent(ReaderEvent.ReaderThemeChanged(ReaderTheme.AMOLED)) },
                                label = "AMOLED", icon = Icons.Default.DarkMode,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.onEvent(ReaderEvent.ToggleTtsControls) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VolumeUp, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.reader_settings_tts), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (state.isSpeaking || state.isPaused) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (state.isSpeaking) {
                                IconButton(onClick = { viewModel.onEvent(ReaderEvent.PauseTts) }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Default.Pause, stringResource(R.string.cd_pause), modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            } else {
                                IconButton(onClick = { viewModel.onEvent(ReaderEvent.ResumeTts) }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Default.PlayArrow, stringResource(R.string.cd_play), modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            IconButton(onClick = { viewModel.onEvent(ReaderEvent.StopTts) }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Stop, stringResource(R.string.cd_stop), modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.reader_settings_brightness), modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.onEvent(ReaderEvent.ResetBrightness) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.cd_reset), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val brightnessSliderValue = if (settings.brightness < 0f) 0.5f else settings.brightness
                    Slider(
                        value = brightnessSliderValue,
                        onValueChange = { viewModel.onEvent(ReaderEvent.SetBrightness(it)) },
                        valueRange = 0.05f..1.0f,
                        steps = 18
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.reader_settings_font), modifier = Modifier.weight(1f))
                        Text("${settings.fontSize}sp", color = MaterialTheme.colorScheme.primary)
                        IconButton(onClick = { viewModel.onEvent(ReaderEvent.FontSizeChanged(18)) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.cd_reset), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Slider(value = settings.fontSize.toFloat(), onValueChange = { viewModel.onEvent(ReaderEvent.FontSizeChanged(it.toInt())) }, valueRange = 12f..32f, steps = 10)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.reader_settings_line_height), modifier = Modifier.weight(1f))
                        Text("%.1f".format(settings.lineHeight), color = MaterialTheme.colorScheme.primary)
                        IconButton(onClick = { viewModel.onEvent(ReaderEvent.LineHeightChanged(1.8f)) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.cd_reset), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Slider(value = settings.lineHeight, onValueChange = { viewModel.onEvent(ReaderEvent.LineHeightChanged(it)) }, valueRange = 1.0f..3.0f, steps = 8)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.reader_settings_margins), modifier = Modifier.weight(1f))
                        Text("${settings.margins}dp", color = MaterialTheme.colorScheme.primary)
                        IconButton(onClick = { viewModel.onEvent(ReaderEvent.MarginsChanged(16)) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.cd_reset), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Slider(value = settings.margins.toFloat(), onValueChange = { viewModel.onEvent(ReaderEvent.MarginsChanged(it.toInt())) }, valueRange = 0f..40f, steps = 8)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.reader_settings_mode), fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = ReaderMode.valueOf(settings.readerMode) == ReaderMode.VERTICAL, onClick = { viewModel.onEvent(ReaderEvent.ReaderModeChanged(ReaderMode.VERTICAL)) }, label = { Text(stringResource(R.string.reader_settings_mode_scroll)) }, leadingIcon = { Icon(Icons.Default.ViewAgenda, null, modifier = Modifier.size(18.dp)) })
                        FilterChip(selected = ReaderMode.valueOf(settings.readerMode) == ReaderMode.HORIZONTAL, onClick = { viewModel.onEvent(ReaderEvent.ReaderModeChanged(ReaderMode.HORIZONTAL)) }, label = { Text(stringResource(R.string.reader_settings_mode_pages)) }, leadingIcon = { Icon(Icons.Default.Swipe, null, modifier = Modifier.size(18.dp)) })
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.reader_settings_progress), fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.showProgressAsPercentage,
                            onClick = { if (!settings.showProgressAsPercentage) viewModel.onEvent(ReaderEvent.ToggleProgressDisplay) },
                            label = { Text(stringResource(R.string.reader_settings_progress_percent)) }
                        )
                        FilterChip(
                            selected = !settings.showProgressAsPercentage,
                            onClick = { if (settings.showProgressAsPercentage) viewModel.onEvent(ReaderEvent.ToggleProgressDisplay) },
                            label = { Text(stringResource(R.string.reader_settings_progress_counter)) }
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                } else {
                    TtsControlsUI(state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun ThemeChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val bgColor: androidx.compose.ui.graphics.Color by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(300), label = "themeChipBg"
    )
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = contentColor)
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}
