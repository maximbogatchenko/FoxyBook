package com.foxybook.app.features.reader

import android.app.Activity
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.foxybook.app.R
import com.foxybook.app.core.models.ReaderMode
import com.foxybook.app.core.models.ReaderTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    filePath: String,
    bookId: Int,
    bookFormat: String,
    viewModel: ReaderViewModel,
    onBackClick: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val settings = state.settings

    val resolvedReaderTheme = try { ReaderTheme.safeValueOf(settings.readerTheme) } catch (_: Exception) { ReaderTheme.SYSTEM }
    val isAmoledTheme = resolvedReaderTheme == ReaderTheme.AMOLED
    val darkTheme = when (resolvedReaderTheme) {
        ReaderTheme.LIGHT -> false
        ReaderTheme.AMOLED -> true
        ReaderTheme.DARK -> true
        ReaderTheme.SYSTEM -> isSystemInDarkTheme()
    }

    val colors = if (isAmoledTheme) {
        amoledReaderColors()
    } else {
        val themeAnimProgress by animateFloatAsState(
            targetValue = if (darkTheme) 1f else 0f,
            animationSpec = tween(500), label = "readerTheme"
        )
        val lightCols = readerColors(false)
        val darkCols = readerColors(true)
        ReaderColors(
            background = lerp(lightCols.background, darkCols.background, themeAnimProgress),
            text = lerp(lightCols.text, darkCols.text, themeAnimProgress),
            textSecondary = lerp(lightCols.textSecondary, darkCols.textSecondary, themeAnimProgress),
            quoteBackground = lerp(lightCols.quoteBackground, darkCols.quoteBackground, themeAnimProgress),
            quoteBorder = lerp(lightCols.quoteBorder, darkCols.quoteBorder, themeAnimProgress),
            selectionHighlight = lerp(lightCols.selectionHighlight, darkCols.selectionHighlight, themeAnimProgress)
        )
    }

    LaunchedEffect(filePath) {
        Log.d("ReaderScreen", "LaunchedEffect: filePath=$filePath, bookId=$bookId")
        viewModel.onEvent(ReaderEvent.LoadBook(filePath, bookId, bookFormat))
    }

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val rawNavBarHeight = with(density) { WindowInsets.navigationBars.getBottom(this) }
    val rawStatusBarHeight = with(density) { WindowInsets.statusBars.getTop(this) }
    val topPadPx = with(density) { 16.dp.roundToPx() } + rawStatusBarHeight + with(density) { 12.dp.roundToPx() }
    val topPadDp = with(density) { topPadPx.toDp() }

    val view = LocalView.current
    val context = LocalContext.current

    DisposableEffect(darkTheme) {
        val w = (context as? Activity)?.window
        if (w != null) {
            val controller = WindowCompat.getInsetsController(w, view)
            val prevAppearance = controller.isAppearanceLightStatusBars
            controller.isAppearanceLightStatusBars = !darkTheme
            onDispose { controller.isAppearanceLightStatusBars = prevAppearance }
        } else { onDispose {} }
    }

    DisposableEffect(state.isImmersive, state.isSelectingTtsStartPosition) {
        val window = (context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        if (state.isImmersive || state.isSelectingTtsStartPosition) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }

    LaunchedEffect(settings.brightness) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val lp = window.attributes
        lp.screenBrightness = if (settings.brightness < 0f) -1f else settings.brightness
        window.attributes = lp
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.savePositionNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            viewModel.savePositionNow()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeightPx = constraints.maxHeight
        val screenWidthPx = constraints.maxWidth
        val pageBottomReservePx = with(density) { 56.dp.roundToPx() }
        val availableHeightPx = remember { screenHeightPx - topPadPx - rawNavBarHeight - pageBottomReservePx }
        val availableWidthPx = screenWidthPx - with(density) { settings.margins.dp.roundToPx() * 2 }

        Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
            Box(
                modifier = Modifier.fillMaxSize().clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) {
                    if (!state.isSelectingTtsStartPosition) {
                        viewModel.onEvent(ReaderEvent.ToggleImmersive)
                    }
                }
            ) {
                when {
                    state.isLoading -> {
                        val bookTitle = state.book?.title ?: ""
                        val bookAuthor = state.book?.author ?: ""
                        val infiniteTransition = rememberInfiniteTransition(label = "reader_loading")
                        val pulseScale by infiniteTransition.animateFloat(0.9f, 1.1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
                        val rotation by infiniteTransition.animateFloat(0f, 360f, infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Restart), label = "rotation")

                        Column(modifier = Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Box(modifier = Modifier.size(130.dp).rotate(rotation).clip(CircleShape).background(Brush.sweepGradient(colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0f), MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), MaterialTheme.colorScheme.primary.copy(alpha = 0f), MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), MaterialTheme.colorScheme.primary.copy(alpha = 0f)))))
                                Surface(modifier = Modifier.size(100.dp).scale(pulseScale), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f), tonalElevation = 4.dp) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            if (bookTitle.isNotBlank()) { Text(bookTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
                            if (bookAuthor.isNotBlank()) { Text(bookAuthor, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                                Text(if (state.isCalculatingPages) stringResource(R.string.reader_search_of, 0, 0) else stringResource(R.string.loading), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary.copy(alpha = 0.7f))
                            }
                        }
                    }
                    state.error != null -> {
                        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(state.error ?: stringResource(R.string.reader_error), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    state.book != null -> {
                        val mode = ReaderMode.safeValueOf(settings.readerMode)
                        if (mode == ReaderMode.HORIZONTAL) {
                            PageModeContent(viewModel = viewModel, state = state, settings = settings, colors = colors, contentWidthPx = availableWidthPx, contentHeightPx = availableHeightPx, textMeasurer = textMeasurer, density = density, topPadDp = topPadDp, onToggleImmersive = { viewModel.onEvent(ReaderEvent.ToggleImmersive) })
                        } else {
                            ScrollModeContent(viewModel = viewModel, state = state, settings = settings, colors = colors, onToggleImmersive = { viewModel.onEvent(ReaderEvent.ToggleImmersive) })
                        }
                    }
                }
            }

            if (!state.isImmersive && !state.isSelectingTtsStartPosition) {
                Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
                    TopAppBar(
                        title = { Text(text = state.book?.title ?: stringResource(R.string.reader_title), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = colors.text) },
                        navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = colors.text) } },
                        actions = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val topText = if (settings.showProgressAsPercentage) {
                                    "${state.readingPercentage}%"
                                } else if (ReaderMode.safeValueOf(settings.readerMode) == ReaderMode.HORIZONTAL) {
                                    if (state.isCalculatingPages || state.totalBookPages == 0) "…" else {
                                        val pagesBefore = (0 until state.currentChapter).sumOf { ch -> state.chapterPages[ch]?.size ?: 0 }
                                        val bookPage = pagesBefore + state.pageCurrent + 1
                                        val bookTotal = state.totalBookPages.coerceAtLeast(bookPage)
                                        "$bookPage/$bookTotal"
                                    }
                                } else {
                                    val blocks = state.chapterBlocks[state.currentChapter]
                                    val blockProgress = if (blocks != null && blocks.size > 1) (state.scrollY + 1).coerceIn(1, blocks.size) else 1
                                    "$blockProgress/${blocks?.size ?: 1}"
                                }
                                Text(topText, style = MaterialTheme.typography.labelMedium, color = colors.text.copy(alpha = 0.7f), modifier = Modifier.padding(end = 4.dp))

                                val bookmarkRemovedStr = stringResource(R.string.reader_bookmark_removed)
                                val bookmarkAddedStr = stringResource(R.string.reader_bookmark_added)
                                val isBookmarked = if (ReaderMode.safeValueOf(settings.readerMode) == ReaderMode.HORIZONTAL) {
                                    state.bookmarks.any { it.chapterIndex == state.currentChapter && it.pageIndex == state.pageCurrent }
                                } else {
                                    state.bookmarks.any { it.chapterIndex == state.currentChapter && it.scrollPosition == state.scrollY }
                                }

                                IconButton(onClick = {
                                    if (isBookmarked) {
                                        val b = if (ReaderMode.safeValueOf(settings.readerMode) == ReaderMode.HORIZONTAL) {
                                            state.bookmarks.find { it.chapterIndex == state.currentChapter && it.pageIndex == state.pageCurrent }
                                        } else {
                                            state.bookmarks.find { it.chapterIndex == state.currentChapter && it.scrollPosition == state.scrollY }
                                        }
                                        if (b != null) { viewModel.onEvent(ReaderEvent.RemoveBookmark(b)); scope.launch { snackbarHostState.showSnackbar(bookmarkRemovedStr) } }
                                    } else {
                                        val preview = if (ReaderMode.safeValueOf(settings.readerMode) == ReaderMode.HORIZONTAL) {
                                            state.chapterPages[state.currentChapter]?.getOrNull(state.pageCurrent)?.blocks?.firstOrNull { it.getTextContent().isNotBlank() }?.getTextContent() ?: ""
                                        } else {
                                            state.chapterBlocks[state.currentChapter]?.getOrNull(state.scrollY)?.getTextContent() ?: ""
                                        }
                                        viewModel.onEvent(ReaderEvent.AddBookmark(preview)); scope.launch { snackbarHostState.showSnackbar(bookmarkAddedStr) }
                                    }
                                }) { Icon(if (isBookmarked) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = stringResource(R.string.cd_bookmark), tint = if (isBookmarked) MaterialTheme.colorScheme.primary else colors.text) }

                                IconButton(onClick = { viewModel.onEvent(ReaderEvent.ToggleSearch) }) { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.reader_search_by_book), tint = colors.text) }
                                IconButton(onClick = { viewModel.onEvent(ReaderEvent.ToggleChapters) }) { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = stringResource(R.string.cd_book), tint = colors.text) }
                                IconButton(onClick = { viewModel.onEvent(ReaderEvent.ToggleSettings) }) { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_settings), tint = colors.text) }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background.copy(alpha = 0.95f), titleContentColor = colors.text, navigationIconContentColor = colors.text, actionIconContentColor = colors.text),
                        windowInsets = TopAppBarDefaults.windowInsets
                    )
                }

                if (state.book != null) {
                    Box(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(colors.background.copy(alpha = 0.95f)).navigationBarsPadding()) {
                        Column {
                            LinearProgressIndicator(progress = { state.readingPercentage / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), trackColor = Color.Transparent)
                            ReaderBottomBar(state = state, viewModel = viewModel, colors = colors)
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize().padding(bottom = if (state.isImmersive || state.isSelectingTtsStartPosition) 0.dp else 100.dp).navigationBarsPadding(), contentAlignment = Alignment.BottomCenter) {
                SnackbarHost(snackbarHostState)
            }

            if (state.isSelectingTtsStartPosition) {
                Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.reader_tts_select_paragraph), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), color = colors.text)
                        TextButton(onClick = { viewModel.onEvent(ReaderEvent.CancelTtsSelection) }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text(stringResource(R.string.cd_cancel), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }

    if (state.showSettings) { SettingsSheet(settings, viewModel) }
    if (state.showChapters && state.book != null) {
        BookSheet(book = state.book!!, bookmarks = state.bookmarks, currentChapter = state.currentChapter, initialTab = if (state.showBookmarks) 1 else 0, chapterTitlesExtracted = state.chapterTitlesExtracted, viewModel = viewModel, snackbarHostState = snackbarHostState)
    }
    if (state.isSearchVisible) {
        ReaderSearchSheet(query = state.searchQuery, results = state.searchResults, currentIndex = state.searchCurrentIndex, isSearching = state.searchIsSearching, viewModel = viewModel)
    }
}
