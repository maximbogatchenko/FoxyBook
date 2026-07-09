package com.foxybook.app.features.reader

import android.app.Activity
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.SubcomposeAsyncImage
import com.foxybook.app.R
import com.foxybook.app.core.models.ReaderMode
import com.foxybook.app.core.models.ReaderTheme
import com.foxybook.app.core.reader.ContentBlock
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Reader Colors ───

data class ReaderColors(
    val background: Color,
    val text: Color,
    val textSecondary: Color,
    val quoteBackground: Color,
    val quoteBorder: Color,
    val selectionHighlight: Color
)

fun readerColors(darkTheme: Boolean): ReaderColors = if (darkTheme) {
    ReaderColors(
        background = Color(0xFF1A1A1A),
        text = Color(0xFFE0E0E0),
        textSecondary = Color(0xFFB0B0B0),
        quoteBackground = Color(0xFF2A2A2A),
        quoteBorder = Color(0xFF555555),
        selectionHighlight = Color(0x40FF8A65)
    )
} else {
    ReaderColors(
        background = Color(0xFFFFFBF5),
        text = Color(0xFF1A1A1A),
        textSecondary = Color(0xFF5A5A5A),
        quoteBackground = Color(0xFFF5F0EB),
        quoteBorder = Color(0xFFCCCCCC),
        selectionHighlight = Color(0x40FFB74D)
    )
}

fun amoledReaderColors(): ReaderColors = ReaderColors(
    background = Color(0xFF000000),
    text = Color(0xFFEEEEEE),
    textSecondary = Color(0xFFAAAAAA),
    quoteBackground = Color(0xFF111111),
    quoteBorder = Color(0xFF333333),
    selectionHighlight = Color(0x40FF8A65)
)

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

    val resolvedReaderTheme = ReaderTheme.valueOf(settings.readerTheme)
    val isAmoledTheme = resolvedReaderTheme == ReaderTheme.AMOLED
    val darkTheme = when (resolvedReaderTheme) {
        ReaderTheme.LIGHT -> false
        ReaderTheme.AMOLED -> true
        ReaderTheme.DARK -> true
        ReaderTheme.SYSTEM -> isSystemInDarkTheme()
    }

    // Animated reader colors for smooth theme transitions
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

    // Capture the nav bar height once at first composition (non-immersive).
    // This MUST be constant to prevent re-pagination on immersive toggle.
    val rawNavBarHeight = with(density) { WindowInsets.navigationBars.getBottom(this) }
    val rawStatusBarHeight = with(density) { WindowInsets.statusBars.getTop(this) }

    // Top padding: base 16dp + status bar + extra 12dp for notch
    val topPadPx = with(density) { 16.dp.roundToPx() } + rawStatusBarHeight + with(density) { 12.dp.roundToPx() }
    val topPadDp = with(density) { topPadPx.toDp() }

    val view = LocalView.current
    val context = LocalContext.current

    // Sync status bar appearance with the reader theme (save & restore on exit)
    DisposableEffect(darkTheme) {
        val w = (context as? Activity)?.window
        if (w != null) {
            val controller = WindowCompat.getInsetsController(w, view)
            val prevAppearance = controller.isAppearanceLightStatusBars
            controller.isAppearanceLightStatusBars = !darkTheme
            onDispose {
                controller.isAppearanceLightStatusBars = prevAppearance
            }
        } else {
            onDispose {}
        }
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

        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Apply custom brightness to the reading window
    LaunchedEffect(settings.brightness) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val lp = window.attributes
        lp.screenBrightness = if (settings.brightness < 0f) -1f else settings.brightness
        window.attributes = lp
    }

    // Save reading position when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            viewModel.savePositionNow()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeightPx = constraints.maxHeight
        val screenWidthPx = constraints.maxWidth

        // Fixed bottom reserve prevents text from being hidden behind the bottom toolbar.
        // Not dependent on isImmersive — no re-pagination on immersive toggle.
        val pageBottomReservePx = with(density) { 32.dp.roundToPx() }
        val availableHeightPx = remember { screenHeightPx - topPadPx - rawNavBarHeight - pageBottomReservePx }
        val availableWidthPx = screenWidthPx - with(density) { settings.margins.dp.roundToPx() * 2 }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
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
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    state.error != null -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(state.error ?: stringResource(R.string.reader_error), style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                    state.book != null -> {
                        val mode = ReaderMode.valueOf(settings.readerMode)
                        if (mode == ReaderMode.HORIZONTAL) {
                            PageModeContent(
                                viewModel = viewModel,
                                state = state,
                                settings = settings,
                                colors = colors,
                                contentWidthPx = availableWidthPx,
                                contentHeightPx = availableHeightPx,
                                textMeasurer = textMeasurer,
                                density = density,
                                topPadDp = topPadDp,
                                onToggleImmersive = { viewModel.onEvent(ReaderEvent.ToggleImmersive) }
                            )
                        } else {
                            ScrollModeContent(
                                viewModel = viewModel,
                                state = state,
                                settings = settings,
                                colors = colors,
                                onToggleImmersive = { viewModel.onEvent(ReaderEvent.ToggleImmersive) }
                            )
                        }
                    }
                }
            }

            if (!state.isImmersive && !state.isSelectingTtsStartPosition) {
                Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
                    TopAppBar(
                        title = {
                            Text(
                                text = state.book?.title ?: stringResource(R.string.reader_title),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.text
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.cd_back),
                                    tint = colors.text
                                )
                            }
                        },
                        actions = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val topText = if (settings.showProgressAsPercentage) {
                                    "${state.readingPercentage}%"
                                } else if (ReaderMode.valueOf(settings.readerMode) == ReaderMode.HORIZONTAL) {
                                    "${state.pageCurrent + 1}/${state.pageTotal}"
                                } else {
                                    val blocks = state.chapterBlocks[state.currentChapter]
                                    val blockProgress = if (blocks != null && blocks.size > 1) {
                                        (state.scrollY + 1).coerceIn(1, blocks.size)
                                    } else 1
                                    "$blockProgress/${blocks?.size ?: 1}"
                                }
                                Text(
                                    topText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.text.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(end = 4.dp)
                                )

                                val bookmarkRemovedStr = stringResource(R.string.reader_bookmark_removed)
                                val bookmarkAddedStr = stringResource(R.string.reader_bookmark_added)
                                val isBookmarked = if (ReaderMode.valueOf(settings.readerMode) == ReaderMode.HORIZONTAL) {
                                    state.bookmarks.any {
                                        it.chapterIndex == state.currentChapter &&
                                        it.pageIndex == state.pageCurrent
                                    }
                                } else {
                                    state.bookmarks.any {
                                        it.chapterIndex == state.currentChapter &&
                                        it.scrollPosition == state.scrollY
                                    }
                                }

                                IconButton(onClick = {
                                    if (isBookmarked) {
                                        val b = if (ReaderMode.valueOf(settings.readerMode) == ReaderMode.HORIZONTAL) {
                                            state.bookmarks.find {
                                                it.chapterIndex == state.currentChapter &&
                                                it.pageIndex == state.pageCurrent
                                            }
                                        } else {
                                            state.bookmarks.find {
                                                it.chapterIndex == state.currentChapter &&
                                                it.scrollPosition == state.scrollY
                                            }
                                        }
                                        if (b != null) {
                                            viewModel.onEvent(ReaderEvent.RemoveBookmark(b))
                                            scope.launch { snackbarHostState.showSnackbar(bookmarkRemovedStr) }
                                        }
                                    } else {
                                        val preview = if (ReaderMode.valueOf(settings.readerMode) == ReaderMode.HORIZONTAL) {
                                            state.chapterPages[state.currentChapter]?.getOrNull(state.pageCurrent)?.blocks?.firstOrNull { it.getTextContent().isNotBlank() }?.getTextContent() ?: ""
                                        } else {
                                            state.chapterBlocks[state.currentChapter]?.getOrNull(state.scrollY)?.getTextContent() ?: ""
                                        }
                                        viewModel.onEvent(ReaderEvent.AddBookmark(preview))
                                        scope.launch { snackbarHostState.showSnackbar(bookmarkAddedStr) }
                                    }
                                }) {
                                    Icon(
                                        if (isBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = stringResource(R.string.cd_bookmark),
                                        tint = if (isBookmarked) MaterialTheme.colorScheme.primary else colors.text
                                    )
                                }

                                IconButton(onClick = { viewModel.onEvent(ReaderEvent.ToggleChapters) }) {
                                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = stringResource(R.string.cd_book), tint = colors.text)
                                }

                                IconButton(onClick = { viewModel.onEvent(ReaderEvent.ToggleSettings) }) {
                                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_settings), tint = colors.text)
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = colors.background.copy(alpha = 0.95f),
                            titleContentColor = colors.text,
                            navigationIconContentColor = colors.text,
                            actionIconContentColor = colors.text
                        ),
                        windowInsets = TopAppBarDefaults.windowInsets
                    )
                }

                if (state.book != null) {
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(colors.background.copy(alpha = 0.95f))
                        .navigationBarsPadding()
                    ) {
                        Column {
                            LinearProgressIndicator(
                                progress = { state.readingPercentage / 100f },
                                modifier = Modifier.fillMaxWidth().height(2.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                trackColor = Color.Transparent,
                            )
                            ReaderBottomBar(state = state, viewModel = viewModel, colors = colors)
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (state.isImmersive || state.isSelectingTtsStartPosition) 0.dp else 100.dp)
                    .navigationBarsPadding(),
                contentAlignment = Alignment.BottomCenter
            ) {
                SnackbarHost(snackbarHostState)
            }

            if (state.isSelectingTtsStartPosition) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.reader_tts_select_paragraph),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                            color = colors.text
                        )
                        TextButton(
                            onClick = { viewModel.onEvent(ReaderEvent.CancelTtsSelection) },
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text(stringResource(R.string.cd_cancel), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (state.showSettings) {
        SettingsSheet(settings, viewModel)
    }
    if (state.showChapters && state.book != null) {
        BookSheet(
            book = state.book!!,
            bookmarks = state.bookmarks,
            currentChapter = state.currentChapter,
            initialTab = if (state.showBookmarks) 1 else 0,
            chapterTitlesExtracted = state.chapterTitlesExtracted,
            viewModel = viewModel,
            snackbarHostState = snackbarHostState
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookSheet(
    book: com.foxybook.app.core.models.ParsedBook,
    bookmarks: List<com.foxybook.app.core.models.Bookmark>,
    currentChapter: Int,
    initialTab: Int = 0,
    chapterTitlesExtracted: Map<Int, String> = emptyMap(),
    viewModel: ReaderViewModel,
    snackbarHostState: SnackbarHostState
) {
    val sheetState = rememberModalBottomSheetState()
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    val tabs = listOf(stringResource(R.string.reader_chapters), stringResource(R.string.reader_bookmarks))

    ModalBottomSheet(
        onDismissRequest = { viewModel.onEvent(ReaderEvent.ToggleChapters) },
        sheetState = sheetState,
        dragHandle = null
    ) {
        Column(modifier = Modifier.fillMaxWidth().height(500.dp)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (selectedTab == 0) {
                    ChaptersList(
                        originalTitles = book.chapters.map { it.title },
                        extractedTitles = chapterTitlesExtracted,
                        currentChapter = currentChapter
                    ) { index ->
                        viewModel.onEvent(ReaderEvent.ChapterChanged(index, resetPosition = true))
                        viewModel.onEvent(ReaderEvent.ToggleChapters)
                    }
                } else {
                    BookmarksList(bookmarks, book.chapters.map { it.title }, chapterTitlesExtracted, viewModel, snackbarHostState)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ChaptersList(
    originalTitles: List<String>,
    extractedTitles: Map<Int, String> = emptyMap(),
    currentChapter: Int,
    onSelect: (Int) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)) {
        items(count = originalTitles.size, key = { it }) { index ->
            val displayTitle = extractedTitles[index]
                ?.ifBlank { null }
                ?: originalTitles.getOrNull(index)
                ?: stringResource(R.string.reader_chapter, index + 1)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(index) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (index == currentChapter) FontWeight.Bold else FontWeight.Normal,
                    color = if (index == currentChapter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BookmarksList(
    bookmarks: List<com.foxybook.app.core.models.Bookmark>,
    chapterTitles: List<String>,
    extractedTitles: Map<Int, String> = emptyMap(),
    viewModel: ReaderViewModel,
    snackbarHostState: SnackbarHostState
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    val scope = rememberCoroutineScope()

    if (bookmarks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.reader_no_bookmarks), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(count = bookmarks.size, key = { bookmarks[it].id }) { index ->
                val bookmark = bookmarks[index]
                val displayChapterTitle = bookmark.chapterTitle.ifBlank {
                    extractedTitles[bookmark.chapterIndex]?.ifBlank { null }
                        ?: chapterTitles.getOrNull(bookmark.chapterIndex)
                        ?: stringResource(R.string.reader_chapter, bookmark.chapterIndex + 1)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.onEvent(ReaderEvent.GoToBookmark(bookmark)) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = displayChapterTitle,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = dateFormat.format(Date(bookmark.createdAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = bookmark.shortTextPreview,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.reader_bookmark_position, bookmark.chapterIndex + 1, bookmark.textOffset),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    val bmRemovedText = stringResource(R.string.reader_bookmark_removed)
                    IconButton(onClick = {
                        viewModel.onEvent(ReaderEvent.RemoveBookmark(bookmark))
                        scope.launch { snackbarHostState.showSnackbar(bmRemovedText) }
                    }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.cd_delete), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

@Composable
fun BlockComposable(
    block: ContentBlock,
    fontSize: Int,
    lineHeight: Float,
    colors: ReaderColors,
    isSelectionMode: Boolean = false,
    isCurrentTtsBlock: Boolean = false,
    onTtsClick: () -> Unit = {},
    onToggleImmersive: () -> Unit = {}
) {
    val bottomPadding = if (block.isSplitAtBottom) 0.dp else (fontSize * 0.4).dp

    val highlightColor = if (isCurrentTtsBlock) colors.selectionHighlight else Color.Transparent

    Box(modifier = Modifier.background(highlightColor)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(isSelectionMode) {
                    detectTapGestures(
                        onTap = {
                            if (isSelectionMode) onTtsClick()
                            else onToggleImmersive()
                        }
                    )
                }
        ) {
            when (block) {
                is ContentBlock.Heading -> {
                    val sizes = mapOf(1 to 28, 2 to 24, 3 to 21, 4 to 19, 5 to 17, 6 to 15)
                    val size = sizes[block.level] ?: 18
                    Text(
                        text = block.text,
                        fontSize = size.sp,
                        lineHeight = (size * lineHeight * 0.85).sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }
                is ContentBlock.Paragraph -> {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            text = block.text,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * lineHeight).sp,
                            color = colors.text,
                            textAlign = TextAlign.Justify,
                            modifier = Modifier.padding(bottom = bottomPadding)
                        )
                    }
                }
                is ContentBlock.Quote -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.quoteBackground, RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                            .padding(vertical = 6.dp)
                            .drawWithContent {
                                drawContent()
                                drawRect(color = colors.quoteBorder, topLeft = Offset(0f, 0f), size = Size(3f, size.height))
                            }
                            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = if (block.isSplitAtBottom) 0.dp else 8.dp)
                    ) {
                        Column {
                            Text(
                                text = block.text,
                                fontSize = (fontSize - 1).sp,
                                lineHeight = (fontSize * lineHeight).sp,
                                color = colors.text.copy(alpha = 0.85f),
                                fontStyle = FontStyle.Italic,
                                textAlign = TextAlign.Justify
                            )
                            if (block.author != null) {
                                Text(
                                    text = block.author,
                                    fontSize = (fontSize - 2).sp,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(top = 4.dp).align(Alignment.End)
                                )
                            }
                        }
                    }
                }
                is ContentBlock.Poem -> {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).padding(start = 24.dp)) {
                        if (block.title != null) {
                            Text(text = block.title, fontSize = (fontSize - 1).sp, fontWeight = FontWeight.SemiBold,
                                color = colors.text, modifier = Modifier.padding(bottom = 6.dp))
                        }
                        block.lines.forEach { line ->
                            Text(text = line, fontSize = (fontSize - 1).sp,
                                lineHeight = (fontSize * lineHeight * 0.9).sp, color = colors.text)
                        }
                        if (block.author != null) {
                            Text(text = block.author, fontSize = (fontSize - 2).sp, color = colors.textSecondary,
                                modifier = Modifier.padding(top = 6.dp).align(Alignment.End))
                        }
                    }
                }
                is ContentBlock.EmptyLine -> Spacer(modifier = Modifier.height(block.height.dp))
                is ContentBlock.Image -> {
                    val imgModel: Any? = remember(block.src) {
                        when {
                            block.src.isBlank() -> null
                            block.src.startsWith("file://") -> java.io.File(block.src.removePrefix("file://"))
                            else -> block.src
                        }
                    }
                    SubcomposeAsyncImage(
                    model = imgModel,
                    contentDescription = block.alt,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                    loading = {
                        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    },
                    error = {
                        Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(48.dp),
                                tint = colors.text.copy(alpha = 0.2f))
                        }
                    }
                )
            }
        }
    }
    }
}

@Composable
fun ReaderBottomBar(
    state: ReaderState,
    viewModel: ReaderViewModel,
    colors: ReaderColors,
    modifier: Modifier = Modifier
) {
    val book = state.book ?: return
    val chapter = book.chapters.getOrNull(state.currentChapter)
    val mode = ReaderMode.valueOf(state.settings.readerMode)

    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { viewModel.onEvent(ReaderEvent.PreviousChapter) }, enabled = state.currentChapter > 0, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.PlayArrow, stringResource(R.string.cd_previous), modifier = Modifier.size(24.dp).rotate(180f),
                tint = if (state.currentChapter > 0) MaterialTheme.colorScheme.primary else colors.text.copy(alpha = 0.2f))
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = chapter?.title ?: "", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.text)
            Text(text = if (state.settings.showProgressAsPercentage) {
                    "${state.readingPercentage}%"
                } else if (mode == ReaderMode.HORIZONTAL) {
                    stringResource(R.string.reader_chapter_page, state.pageCurrent + 1, state.pageTotal)
                } else {
                    val blocks = state.chapterBlocks[state.currentChapter]
                    val blockProgress = if (blocks != null && blocks.size > 1) {
                        (state.scrollY + 1).coerceIn(1, blocks.size)
                    } else 1
                    "$blockProgress/${blocks?.size ?: 1}"
                },
                style = MaterialTheme.typography.labelSmall, color = colors.text.copy(alpha = 0.6f))
        }
        IconButton(onClick = { viewModel.onEvent(ReaderEvent.NextChapter) }, enabled = state.currentChapter < book.chapters.size - 1, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.PlayArrow, stringResource(R.string.cd_next), modifier = Modifier.size(24.dp),
                tint = if (state.currentChapter < book.chapters.size - 1) MaterialTheme.colorScheme.primary else colors.text.copy(alpha = 0.2f))
        }
    }
}
