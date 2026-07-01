package com.foxybook.app.features.splash

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foxybook.app.R
import com.foxybook.app.core.updater.UpdateChecker
import com.foxybook.app.core.updater.UpdateInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─── Цветовая схема сплэша ───
private val Orange = Color(0xFFFF8C42)
private val OrangeLight = Color(0xFFFFA66B)
private val OrangeDark = Color(0xFFE67A30)
private val OrangeGlow = Color(0xFFFF8C42).copy(alpha = 0.15f)

private sealed class SplashUpdateState {
    data object Idle : SplashUpdateState()
    data object Checking : SplashUpdateState()
    data class Available(val info: UpdateInfo) : SplashUpdateState()
    data object Downloading : SplashUpdateState()
    data class Downloaded(val uri: Uri) : SplashUpdateState()
    data class Error(val message: String) : SplashUpdateState()
}

@Composable
fun SplashScreen(
    updateChecker: UpdateChecker,
    onLoadingComplete: () -> Unit
) {
    val context = LocalContext.current
    var startAnimation by remember { mutableStateOf(false) }
    var updateState by remember { mutableStateOf<SplashUpdateState>(SplashUpdateState.Idle) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var changelogExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // ─── Анимации ───

    // Вращение внешнего кольца (идёт всегда в idle/checking/available)
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation"
    )

    // Появление иконки (один раз при старте)
    val iconScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.5f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "iconScale"
    )

    // Пульсация для точек загрузки
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dotAlpha"
    )

    // Pulse для кнопки скачивания / иконки при update available
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseScale"
    )

    // Для состояния Downloaded: зелёная пульсация
    val successScale by infiniteTransition.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "successScale"
    )

    // ─── Логика загрузки и проверки ───

    LaunchedEffect(Unit) {
        startAnimation = true

        // Параллельно: анимация idle и проверка обновления
        val updateJob = async {
            try {
                val currentVersion = updateChecker.getCurrentVersion()
                updateChecker.checkForUpdate(currentVersion)
            } catch (_: Exception) { null }
        }

        delay(2500) // минимум 2.5 сек сплэша

        val updateInfo = updateJob.await()
        if (updateInfo != null) {
            updateState = SplashUpdateState.Available(updateInfo)
        } else {
            onLoadingComplete()
        }
    }

    // ─── UI: единое чёрное полотно ───

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = updateState,
            transitionSpec = { fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(300)) },
            label = "splashContent"
        ) { state ->
            when (state) {
                is SplashUpdateState.Available -> UpdateAvailableContent(
                    info = state.info,
                    changelogExpanded = changelogExpanded,
                    onToggleChangelog = { changelogExpanded = !changelogExpanded },
                    pulseScale = pulseScale,
                    onDownload = {
                        scope.launch {
                            updateState = SplashUpdateState.Downloading
                            downloadProgress = 0f
                            try {
                                val uri = updateChecker.downloadApk(state.info.downloadUrl) { p ->
                                    downloadProgress = p
                                }
                                updateState = SplashUpdateState.Downloaded(uri)
                            } catch (e: Exception) {
                                updateState = SplashUpdateState.Error(e.message ?: "Ошибка скачивания")
                            }
                        }
                    },
                    onSkip = { onLoadingComplete() }
                )

                is SplashUpdateState.Downloading -> DownloadingContent(
                    progress = downloadProgress
                )

                is SplashUpdateState.Downloaded -> DownloadedContent(
                    successScale = successScale,
                    onInstall = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(state.uri, "application/vnd.android.package-archive")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                                    setData(state.uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) { }
                        }
                        onLoadingComplete()
                    },
                    onLater = { onLoadingComplete() }
                )

                is SplashUpdateState.Error -> ErrorContent(
                    message = state.message,
                    onDismiss = { onLoadingComplete() }
                )

                else -> LoadingContent(
                    rotation = rotation,
                    iconScale = iconScale,
                    dotAlpha = dotAlpha,
                    isChecking = state is SplashUpdateState.Checking
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Loading State (начальная анимация)
// ═══════════════════════════════════════════════════════════════

@Composable
private fun LoadingContent(
    rotation: Float,
    iconScale: Float,
    dotAlpha: Float,
    isChecking: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Вращающееся кольцо + иконка книги
        AnimatedRingWithIcon(
            ringSize = 160.dp,
            rotation = rotation,
            iconScale = iconScale,
            iconRes = R.drawable.splash_icon
        )

        Spacer(modifier = Modifier.height(50.dp))

        Text(
            text = "FoxyBook",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Orange
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (isChecking) "Проверка обновлений..." else "Загрузка...",
            fontSize = 16.sp,
            color = Orange.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Три пульсирующие точки
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.padding(8.dp)) {
            repeat(3) { index ->
                val delay = index * 200
                val dot by rememberInfiniteTransition(label = "dot$index").animateFloat(
                    initialValue = 0.3f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(600, easing = FastOutSlowInEasing, delayMillis = delay),
                        RepeatMode.Reverse
                    ), label = "dotAnim$index"
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .padding(horizontal = 4.dp)
                        .background(color = Orange.copy(alpha = dot), shape = CircleShape)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Update Available (обновление найдено — анимация переходит сюда)
// ═══════════════════════════════════════════════════════════════

@Composable
private fun UpdateAvailableContent(
    info: UpdateInfo,
    changelogExpanded: Boolean,
    onToggleChangelog: () -> Unit,
    pulseScale: Float,
    onDownload: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
    ) {
        // ── Вращающееся кольцо (медленнее) + иконка обновления ──
        val slowRotation by rememberInfiniteTransition(label = "slowRot").animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
            label = "slowRotAnim"
        )
        Box(
            modifier = Modifier.size(140.dp),
            contentAlignment = Alignment.Center
        ) {
            // Кольцо с градиентом (медленно вращается)
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .rotate(slowRotation)
                    .scale(pulseScale)
                    .background(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Orange, OrangeLight,
                                Orange.copy(alpha = 0.15f), Orange
                            )
                        ),
                        shape = CircleShape
                    )
            )
            // Внутренний чёрный круг
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(Color.Black, CircleShape)
            )
            // Иконка обновления
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = "Update",
                tint = Orange,
                modifier = Modifier.size(56.dp)
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        // ── Заголовок ──
        Text(
            text = "Доступно обновление",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Версия ──
        Text(
            text = "v${info.version.removePrefix("v").removePrefix("V")}",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Orange
        )

        Spacer(modifier = Modifier.height(4.dp))

        // ── Размер ──
        Text(
            text = formatSize(info.size),
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Что нового (спойлер) ──
        if (info.releaseNotes.isNotBlank()) {
            ChangeLogSection(
                releaseNotes = info.releaseNotes,
                expanded = changelogExpanded,
                onToggle = onToggleChangelog
            )

            Spacer(modifier = Modifier.height(20.dp))
        } else {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // ── Кнопки ──
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Кнопка "Скачать" — оранжевая, с пульсацией
            Button(
                onClick = onDownload,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .scale(pulseScale),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Orange,
                    contentColor = Color.Black
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    "Скачать обновление",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Кнопка "Позже" — прозрачная
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.6f))
            ) {
                Text("Пропустить", fontSize = 15.sp)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Downloading (прогресс загрузки)
// ═══════════════════════════════════════════════════════════════

@Composable
private fun DownloadingContent(progress: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 32.dp)
    ) {
        // Кольцо-прогресс
        Box(
            modifier = Modifier.size(140.dp),
            contentAlignment = Alignment.Center
        ) {
            // Фоновое кольцо
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.size(140.dp),
                strokeWidth = 4.dp,
                color = Orange.copy(alpha = 0.15f),
                trackColor = Color.Transparent
            )
            // Активный прогресс
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(140.dp),
                strokeWidth = 4.dp,
                color = Orange,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Round
            )
            // Процент внутри
            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Скачивание...",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Пожалуйста, подождите",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.5f)
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  Downloaded (загружено — предложение установить)
// ═══════════════════════════════════════════════════════════════

@Composable
private fun DownloadedContent(
    successScale: Float,
    onInstall: () -> Unit,
    onLater: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 32.dp)
    ) {
        // Зелёное пульсирующее кольцо с галочкой
        Box(
            modifier = Modifier.size(140.dp),
            contentAlignment = Alignment.Center
        ) {
            // Кольцо
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(successScale)
                    .background(
                        color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                        shape = CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .scale(successScale)
                    .background(
                        color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                        shape = CircleShape
                    )
            )
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Success",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(64.dp)
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Обновление загружено!",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Нажмите «Установить», чтобы обновить приложение",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Кнопка установки — зелёная
        Button(
            onClick = onInstall,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50),
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Text("Установить", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onLater,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.6f))
        ) {
            Text("Позже", fontSize = 15.sp)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Error
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ErrorContent(message: String, onDismiss: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 32.dp)
    ) {
        val shakeTransition = rememberInfiniteTransition(label = "shake")
        val shakeOffset by shakeTransition.animateFloat(
            initialValue = -4f, targetValue = 4f,
            animationSpec = infiniteRepeatable(
                tween(100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shakeOffset"
        )

        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = "Error",
            tint = Color(0xFFFF5252),
            modifier = Modifier.size(72.dp).rotate(shakeOffset)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Ошибка",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF5252),
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Text("Продолжить", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Общие компоненты
// ═══════════════════════════════════════════════════════════════

@Composable
private fun AnimatedRingWithIcon(
    ringSize: androidx.compose.ui.unit.Dp,
    rotation: Float,
    iconScale: Float,
    iconRes: Int
) {
    Box(
        modifier = Modifier.size(180.dp),
        contentAlignment = Alignment.Center
    ) {
        // Внешний круг с градиентом (вращается)
        Box(
            modifier = Modifier
                .size(ringSize)
                .rotate(rotation)
                .background(
                    brush = Brush.sweepGradient(
                        colors = listOf(Orange, OrangeDark, Orange.copy(alpha = 0.2f), Orange)
                    ),
                    shape = CircleShape
                )
        )
        // Внутренний чёрный круг
        Box(
            modifier = Modifier
                .size(ringSize - 20.dp)
                .background(Color.Black, CircleShape)
        )
        // Иконка
        Icon(
            imageVector = ImageVector.vectorResource(id = iconRes),
            contentDescription = "Loading",
            tint = Color.Unspecified,
            modifier = Modifier
                .size(90.dp)
                .scale(iconScale)
        )
    }
}

@Composable
private fun ChangeLogSection(
    releaseNotes: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Orange.copy(alpha = 0.8f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Что нового",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Orange.copy(alpha = 0.9f)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                val lines = releaseNotes
                    .replace("\r\n", "\n")
                    .split("\n")
                for (line in lines) {
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("## ") || trimmed.startsWith("### ") -> {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                trimmed.removePrefix("##").removePrefix("###").trimStart(' '),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                        trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                            Row(Modifier.padding(start = 8.dp, top = 2.dp)) {
                                Text("•  ", color = Orange, fontSize = 14.sp)
                                Text(
                                    trimmed.removePrefix("- ").removePrefix("* "),
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                        trimmed.isNotBlank() -> {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                trimmed,
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
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
