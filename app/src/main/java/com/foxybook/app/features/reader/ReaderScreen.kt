package com.foxybook.app.features.reader

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.OutlinedCard
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foxybook.app.core.models.ReaderMode
import com.foxybook.app.core.models.ReaderSettings
import com.foxybook.app.core.models.ReaderTheme
import com.foxybook.app.core.reader.ContentBlock
import com.foxybook.app.core.reader.TextPaginator

// ─── Reader Colors ───

private data class ReaderColors(
    val background: Color,
    val text: Color,
    val textSecondary: Color,
    val quoteBackground: Color,
    val quoteBorder: Color,
    val selectionHighlight: Color
)

private fun readerColors(darkTheme: Boolean): ReaderColors = if (darkTheme) {
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

    val darkTheme = when (ReaderTheme.valueOf(settings.readerTheme)) {
        ReaderTheme.LIGHT -> false
        ReaderTheme.DARK -> true
        ReaderTheme.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = readerColors(darkTheme)

    LaunchedEffect(filePath) {
        Log.d("ReaderScreen", "LaunchedEffect: filePath=$filePath, bookId=$bookId")
        viewModel.onEvent(ReaderEvent.LoadBook(filePath, bookId, bookFormat))
    }

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    val view = LocalView.current
    val context = LocalContext.current

    androidx.compose.runtime.DisposableEffect(state.isImmersive, state.isSelectingTtsStartPosition) {
        val window = (context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        
        if (state.isImmersive || state.isSelectingTtsStartPosition) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            // Restore system bars when leaving the screen or changing state
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeightPx = constraints.maxHeight
        val screenWidthPx = constraints.maxWidth

        // availableHeightPx MUST be constant to prevent re-pagination.
        // We reserve enough space at the bottom to prevent text from being
        // clipped by the navigation overlay in non-immersive mode.
        // This leaves some unused space in fullscreen, but prevents jumps.
        val availableHeightPx = screenHeightPx - with(density) { 100.dp.roundToPx() }
        val availableWidthPx = screenWidthPx - with(density) { settings.margins.dp.roundToPx() * 2 }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            // 1. Content Area (Fixed size, Overlay UI will float on top)
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
                            Text(state.error ?: "Ошибка", style = MaterialTheme.typography.titleMedium,
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

            // 2. UI Overlays (TopBar, BottomBar)
            if (!state.isImmersive && !state.isSelectingTtsStartPosition) {
                // Top Overlay
                Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
                    TopAppBar(
                        title = {
                            Text(
                                text = state.book?.title ?: "Чтение",
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
                                    contentDescription = "Назад",
                                    tint = colors.text
                                )
                            }
                        },
                        actions = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${state.readingPercentage}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.text.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(end = 4.dp)
                                )

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
                                            scope.launch { snackbarHostState.showSnackbar("Закладка удалена") }
                                        }
                                    } else {
                                        val preview = if (ReaderMode.valueOf(settings.readerMode) == ReaderMode.HORIZONTAL) {
                                            state.chapterPages[state.currentChapter]?.getOrNull(state.pageCurrent)?.blocks?.firstOrNull { it.getTextContent().isNotBlank() }?.getTextContent() ?: ""
                                        } else {
                                            state.chapterBlocks[state.currentChapter]?.getOrNull(state.scrollY)?.getTextContent() ?: ""
                                        }
                                        viewModel.onEvent(ReaderEvent.AddBookmark(preview))
                                        scope.launch { snackbarHostState.showSnackbar("Закладка добавлена") }
                                    }
                                }) {
                                    Icon(
                                        if (isBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Закладка",
                                        tint = if (isBookmarked) MaterialTheme.colorScheme.primary else colors.text
                                    )
                                }

                                IconButton(onClick = { viewModel.onEvent(ReaderEvent.ToggleChapters) }) {
                                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Книга", tint = colors.text)
                                }

                                IconButton(onClick = { viewModel.onEvent(ReaderEvent.ToggleSettings) }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = colors.text)
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = colors.background.copy(alpha = 0.95f),
                            titleContentColor = colors.text,
                            navigationIconContentColor = colors.text,
                            actionIconContentColor = colors.text
                        ),
                        windowInsets = TopAppBarDefaults.windowInsets // This handles status bar padding internally
                    )
                }

                // Bottom Overlay
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

            // Snackbar Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (state.isImmersive || state.isSelectingTtsStartPosition) 0.dp else 100.dp)
                    .navigationBarsPadding(), 
                contentAlignment = Alignment.BottomCenter
            ) {
                SnackbarHost(snackbarHostState)
            }

            // Selection Mode Overlay
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
                            "Выберите абзац для начала озвучивания",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                            color = colors.text
                        )
                        androidx.compose.material3.TextButton(
                            onClick = { viewModel.onEvent(ReaderEvent.CancelTtsSelection) },
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text("Отмена", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
    viewModel: ReaderViewModel,
    snackbarHostState: SnackbarHostState
) {
    val sheetState = rememberModalBottomSheetState()
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    val tabs = listOf("Главы", "Закладки")

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
                    ChaptersList(book.chapters.map { it.title }, currentChapter) { index ->
                        viewModel.onEvent(ReaderEvent.ChapterChanged(index, resetPosition = true))
                        viewModel.onEvent(ReaderEvent.ToggleChapters)
                    }
                } else {
                    BookmarksList(bookmarks, book.chapters.map { it.title }, viewModel, snackbarHostState)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ChaptersList(
    chapterTitles: List<String>,
    current: Int,
    onSelect: (Int) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)) {
        items(count = chapterTitles.size, key = { it }) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(index) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chapterTitles[index],
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (index == current) FontWeight.Bold else FontWeight.Normal,
                    color = if (index == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
    viewModel: ReaderViewModel,
    snackbarHostState: SnackbarHostState
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    val scope = rememberCoroutineScope()

    if (bookmarks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Нет закладок", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(count = bookmarks.size, key = { bookmarks[it].id }) { index ->
                val bookmark = bookmarks[index]
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
                                text = bookmark.chapterTitle.ifBlank { chapterTitles.getOrNull(bookmark.chapterIndex) ?: "Глава ${bookmark.chapterIndex + 1}" },
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
                            text = "Позиция: ${bookmark.chapterIndex + 1} глава, ${bookmark.textOffset} символ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    IconButton(onClick = { 
                        viewModel.onEvent(ReaderEvent.RemoveBookmark(bookmark))
                        scope.launch { snackbarHostState.showSnackbar("Закладка удалена") }
                    }) {
                        Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

// ─── Scroll Mode (Seamless) ───

private data class GlobalBlock(
    val block: ContentBlock,
    val chapterIndex: Int,
    val blockIndexInChapter: Int,
    val offsetInChapter: Int
)

@Composable
private fun ScrollModeContent(
    viewModel: ReaderViewModel,
    state: ReaderState,
    settings: ReaderSettings,
    colors: ReaderColors,
    onToggleImmersive: () -> Unit
) {
    val lazyListState = rememberLazyListState()
    val book = state.book ?: return
// ... (omitted for brevity, will provide full body in actual call)

    // Simple approach: only show loaded chapters in order
    val globalBlocks = remember(state.chapterBlocks) {
        val list = mutableListOf<GlobalBlock>()

        // Get all loaded chapter indices and sort them
        val loadedChapters = state.chapterBlocks.keys.sorted()

        loadedChapters.forEach { chIdx ->
            val blocks = state.chapterBlocks[chIdx] ?: emptyList()
            var currentOffset = 0
            blocks.forEachIndexed { bIdx, block ->
                list.add(GlobalBlock(block, chIdx, bIdx, currentOffset))
                currentOffset += block.getTextContent().length
            }
        }

        Log.d("ReaderNav", "ScrollMode: Built ${list.size} blocks from chapters $loadedChapters")
        list
    }

    // Aggressive preloading on scroll
    LaunchedEffect(Unit) {
        snapshotFlow { lazyListState.firstVisibleItemIndex }
            .collect { index ->
                if (index >= 0 && index < globalBlocks.size) {
                    val currentBlock = globalBlocks[index]
                    val chapterIndex = currentBlock.chapterIndex

                    // Load next 10 chapters aggressively
                    for (i in 1..10) {
                        val nextChapter = chapterIndex + i
                        if (nextChapter < book.chapters.size && !state.chapterBlocks.containsKey(nextChapter)) {
                            Log.d("ReaderNav", "ScrollMode: Loading chapter $nextChapter")
                            viewModel.getBlocks(nextChapter)
                        }
                    }
                }
            }
    }

    // Early preloading: load next chapter when in last third of current chapter
    LaunchedEffect(lazyListState) {
        snapshotFlow {
            val idx = lazyListState.firstVisibleItemIndex
            val block = globalBlocks.getOrNull(idx)
            Pair(idx, block?.chapterIndex ?: -1)
        }.collect { (idx, chIdx) ->
            if (chIdx >= 0 && idx + 20 < globalBlocks.size) {
                // Find how many blocks are in the current chapter ahead of us
                val currentChapterBlocks = globalBlocks.filter { it.chapterIndex == chIdx }
                val currentBlockInChapter = globalBlocks.indexOfFirst { it.chapterIndex == chIdx && it.blockIndexInChapter == globalBlocks[idx].blockIndexInChapter }
                val chapterBlockCount = currentChapterBlocks.size
                if (currentBlockInChapter > chapterBlockCount * 2 / 3) {
                    // We're in the last third of the chapter, preload next chapter
                    val nextCh = chIdx + 1
                    if (nextCh < book.chapters.size && !state.chapterBlocks.containsKey(nextCh)) {
                        Log.d("ReaderNav", "ScrollMode: Early loading chapter $nextCh (near end of ch$chIdx)")
                        viewModel.getBlocks(nextCh)
                    }
                }
            }
        }
    }

    // Initial load
    LaunchedEffect(state.currentChapter) {
        // Load current and next chapters
        for (i in 0..5) {
            val chIdx = state.currentChapter + i
            if (chIdx < book.chapters.size && !state.chapterBlocks.containsKey(chIdx)) {
                viewModel.getBlocks(chIdx)
            }
        }
    }

    // Restore scroll position
    var isRestoring by remember { mutableStateOf(true) }
    var lastHandledRestoreTrigger by remember { mutableLongStateOf(-1L) }

    LaunchedEffect(state.lastPositionRestoreTrigger, globalBlocks) {
        if (globalBlocks.isEmpty() || state.lastPositionRestoreTrigger == lastHandledRestoreTrigger) {
            isRestoring = false
            return@LaunchedEffect
        }

        // Find the right block by textOffset (works across modes) or fall back to scrollY
        val targetIdx = globalBlocks.indexOfFirst {
            it.chapterIndex == state.currentChapter &&
            state.textOffset in it.offsetInChapter until (it.offsetInChapter + it.block.getTextContent().length)
        }.let { if (it == -1) globalBlocks.indexOfFirst { gb -> gb.chapterIndex == state.currentChapter && gb.blockIndexInChapter == state.scrollY } else it }

        if (targetIdx >= 0) {
            Log.d("ReaderNav", "ScrollMode: Restoring to index $targetIdx (block=${state.scrollY})")
            isRestoring = true
            lazyListState.scrollToItem(targetIdx, state.scrollOffset)
            lastHandledRestoreTrigger = state.lastPositionRestoreTrigger
            delay(100)
        }
        isRestoring = false
    }

    // Track scroll position
    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.firstVisibleItemIndex to lazyListState.isScrollInProgress
        }.collect { (index, isScrolling) ->
            if (!isScrolling && !isRestoring && index >= 0 && index < globalBlocks.size) {
                val block = globalBlocks[index]

                // Update chapter if changed
                if (block.chapterIndex != state.currentChapter) {
                    Log.d("ReaderNav", "ScrollMode: Chapter changed to ${block.chapterIndex} via scroll")
                    viewModel.onEvent(ReaderEvent.ChapterChanged(block.chapterIndex, resetPosition = false))
                }

                // Save position
                viewModel.onEvent(ReaderEvent.ScrollProgress(
                    (index * 100 / globalBlocks.size.coerceAtLeast(1)),
                    block.blockIndexInChapter,
                    lazyListState.firstVisibleItemScrollOffset,
                    block.offsetInChapter
                ))
            }
        }
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = PaddingValues(horizontal = settings.margins.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(count = globalBlocks.size, key = { index ->
            val gb = globalBlocks[index]
            "ch${gb.chapterIndex}_b${gb.blockIndexInChapter}"
        }) { index ->
            val gb = globalBlocks[index]
            BlockComposable(
                block = gb.block,
                fontSize = settings.fontSize,
                lineHeight = settings.lineHeight,
                colors = colors,
                isSelectionMode = state.isSelectingTtsStartPosition,
                isCurrentTtsBlock = state.currentTtsChapter == gb.chapterIndex && state.currentTtsBlockIndex == gb.blockIndexInChapter,
                onTtsClick = { viewModel.onEvent(ReaderEvent.StartTts(gb.chapterIndex, gb.blockIndexInChapter)) },
                onToggleImmersive = onToggleImmersive
            )
        }

        // End of book marker
        if (globalBlocks.isNotEmpty()) {
            val lastChapter = globalBlocks.last().chapterIndex
            if (lastChapter == book.chapters.size - 1) {
                item {
                    Spacer(modifier = Modifier.height(100.dp))
                    Text(
                        "Конец книги",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }
}

// ─── Page Mode (Seamless) ───

private data class GlobalPage(
    val page: TextPaginator.Page,
    val chapterIndex: Int,
    val pageIndexInChapter: Int
)

@Composable
private fun PageModeContent(
    viewModel: ReaderViewModel,
    state: ReaderState,
    settings: ReaderSettings,
    colors: ReaderColors,
    contentWidthPx: Int,
    contentHeightPx: Int,
    textMeasurer: TextMeasurer,
    density: Density,
    onToggleImmersive: () -> Unit
) {
    // Trigger pagination when dimensions change
    LaunchedEffect(contentWidthPx, contentHeightPx, settings.fontSize, settings.lineHeight, settings.margins) {
        Log.d("ReaderNav", "PageMode: Dimensions changed")
        viewModel.onEvent(ReaderEvent.UpdatePageDimensions(contentWidthPx, contentHeightPx, textMeasurer, density))
    }

    val book = state.book ?: return

    // Simple approach: only show paginated chapters in order
    val globalPages = remember(state.chapterPages) {
        val list = mutableListOf<GlobalPage>()

        // Get all paginated chapter indices and sort them
        val paginatedChapters = state.chapterPages.keys.sorted()

        paginatedChapters.forEach { chIdx ->
            val pages = state.chapterPages[chIdx] ?: emptyList()
            pages.forEachIndexed { pIdx, page ->
                list.add(GlobalPage(page, chIdx, pIdx))
            }
        }

        Log.d("ReaderNav", "PageMode: Built ${list.size} pages from chapters $paginatedChapters")
        list
    }

    // Aggressive preloading
    LaunchedEffect(Unit) {
        snapshotFlow { if (globalPages.isEmpty()) -1 else globalPages[kotlin.math.min(state.pageCurrent, globalPages.size - 1)].chapterIndex }
            .collect { visibleChapter ->
                if (visibleChapter >= 0) {
                    // Load and paginate next 10 chapters
                    for (i in 1..10) {
                        val nextChapter = visibleChapter + i
                        if (nextChapter < book.chapters.size) {
                            if (!state.chapterBlocks.containsKey(nextChapter)) {
                                Log.d("ReaderNav", "PageMode: Loading chapter $nextChapter")
                                viewModel.getBlocks(nextChapter)
                            }
                            viewModel.ensurePaginated(nextChapter)
                        }
                    }
                }
            }
    }

    // Initial load
    LaunchedEffect(state.currentChapter) {
        for (i in 0..5) {
            val chIdx = state.currentChapter + i
            if (chIdx < book.chapters.size) {
                if (!state.chapterBlocks.containsKey(chIdx)) {
                    viewModel.getBlocks(chIdx)
                }
                viewModel.ensurePaginated(chIdx)
            }
        }
    }

    // Show loading if no pages yet
    if (globalPages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Initial page calculation based on textOffset for better accuracy across font size changes
    val initialPage = remember(globalPages) {
        val idx = globalPages.indexOfFirst {
            it.chapterIndex == state.currentChapter &&
            (state.textOffset in it.page.startOffset until it.page.endOffset || 
             (it.pageIndexInChapter == 0 && state.textOffset == 0))
        }.coerceAtLeast(globalPages.indexOfFirst { it.chapterIndex == state.currentChapter })
        
        if (idx >= 0) idx else 0
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { globalPages.size.coerceAtLeast(1) }
    )

    // Restore position / Handle chapter navigation
    var isRestoring by remember { mutableStateOf(true) }
    var lastHandledRestoreTrigger by remember { mutableLongStateOf(-1L) }

    LaunchedEffect(state.lastPositionRestoreTrigger, globalPages) {
        if (globalPages.isEmpty() || state.lastPositionRestoreTrigger == lastHandledRestoreTrigger) {
            isRestoring = false
            return@LaunchedEffect
        }

        val targetIdx = globalPages.indexOfFirst {
            it.chapterIndex == state.currentChapter &&
            (state.textOffset in it.page.startOffset until it.page.endOffset ||
             (it.pageIndexInChapter == 0 && state.textOffset == 0))
        }.let { if (it == -1) globalPages.indexOfFirst { p -> p.chapterIndex == state.currentChapter } else it }

        if (targetIdx >= 0) {
            Log.d("ReaderNav", "PageMode: Restoring to page $targetIdx (ch=${state.currentChapter}, offset=${state.textOffset})")
            isRestoring = true
            pagerState.scrollToPage(targetIdx)
            lastHandledRestoreTrigger = state.lastPositionRestoreTrigger
            delay(250)
        }
        isRestoring = false
    }

    // Track current page
    LaunchedEffect(pagerState) {
        snapshotFlow {
            pagerState.currentPage to pagerState.isScrollInProgress
        }.collect { (page, isScrolling) ->
            if (!isScrolling && page >= 0 && page < globalPages.size) {
                val currentPage = globalPages[page]

                // Update chapter if changed (only when not restoring)
                if (!isRestoring && currentPage.chapterIndex != state.currentChapter) {
                    Log.d("ReaderNav", "PageMode: Chapter changed to ${currentPage.chapterIndex} via swipe")
                    viewModel.onEvent(ReaderEvent.ChapterChanged(currentPage.chapterIndex, resetPosition = false))
                }

                // Save position (always, even during restoration)
                val totalInChapter = globalPages.count { it.chapterIndex == currentPage.chapterIndex }.coerceAtLeast(1)
                viewModel.onEvent(ReaderEvent.PageInfo(
                    currentPage.pageIndexInChapter,
                    totalInChapter,
                    currentPage.page.startOffset
                ))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 2,
            key = { index ->
                val gp = globalPages.getOrNull(index)
                if (gp != null) "ch${gp.chapterIndex}_p${gp.pageIndexInChapter}" else "empty_$index"
            }
        ) { index ->
            val gp = globalPages.getOrNull(index)
            if (gp != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = settings.margins.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.Top
                    ) {
                    gp.page.blocks.forEach { block ->
                        BlockComposable(
                            block = block,
                            fontSize = settings.fontSize,
                            lineHeight = settings.lineHeight,
                            colors = colors,
                            isSelectionMode = state.isSelectingTtsStartPosition,
                            isCurrentTtsBlock = state.currentTtsChapter == gp.chapterIndex && state.currentTtsBlockIndex == block.originalIndex,
                            onTtsClick = {
                                viewModel.onEvent(ReaderEvent.StartTts(gp.chapterIndex, block.originalIndex))
                            },
                            onToggleImmersive = onToggleImmersive
                        )
                    }
                }
                val chapterPageCount = globalPages.count { it.chapterIndex == gp.chapterIndex }
                Text(
                    text = "Гл.${gp.chapterIndex + 1} Стр.${gp.pageIndexInChapter + 1} / ${chapterPageCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                )
                }
            }
        }
    }
}

@Composable
private fun BlockComposable(
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
                    SelectionContainer {
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
                is ContentBlock.Image -> Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = block.alt, modifier = Modifier.size(48.dp),
                        tint = colors.text.copy(alpha = 0.2f))
                }
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
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
            Icon(Icons.Default.PlayArrow, "Пред.", modifier = Modifier.size(24.dp).rotate(180f),
                tint = if (state.currentChapter > 0) MaterialTheme.colorScheme.primary else colors.text.copy(alpha = 0.2f))
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = chapter?.title ?: "", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.text)
            Text(text = if (mode == ReaderMode.HORIZONTAL) {
                    "Стр. ${state.pageCurrent + 1}/${state.pageTotal}"
                } else {
                    val blocks = state.chapterBlocks[state.currentChapter]
                    val progress = if (blocks != null && blocks.size > 1) {
                        (state.scrollY * 100 / blocks.size).coerceIn(0, 100)
                    } else 0
                    "Стр. ${progress}%"
                },
                style = MaterialTheme.typography.labelSmall, color = colors.text.copy(alpha = 0.6f))
        }
        IconButton(onClick = { viewModel.onEvent(ReaderEvent.NextChapter) }, enabled = state.currentChapter < book.chapters.size - 1, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.PlayArrow, "След.", modifier = Modifier.size(24.dp),
                tint = if (state.currentChapter < book.chapters.size - 1) MaterialTheme.colorScheme.primary else colors.text.copy(alpha = 0.2f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(settings: ReaderSettings, viewModel: ReaderViewModel) {
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
                Text("Настройки чтения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(16.dp))

                Text("Тема книги", fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = ReaderTheme.valueOf(settings.readerTheme) == ReaderTheme.LIGHT, onClick = { viewModel.onEvent(ReaderEvent.ReaderThemeChanged(ReaderTheme.LIGHT)) }, label = { Text("Светлая") }, leadingIcon = { Icon(Icons.Default.LightMode, null, modifier = Modifier.size(18.dp)) })
                    FilterChip(selected = ReaderTheme.valueOf(settings.readerTheme) == ReaderTheme.DARK, onClick = { viewModel.onEvent(ReaderEvent.ReaderThemeChanged(ReaderTheme.DARK)) }, label = { Text("Тёмная") }, leadingIcon = { Icon(Icons.Default.DarkMode, null, modifier = Modifier.size(18.dp)) })
                    FilterChip(selected = ReaderTheme.valueOf(settings.readerTheme) == ReaderTheme.SYSTEM, onClick = { viewModel.onEvent(ReaderEvent.ReaderThemeChanged(ReaderTheme.SYSTEM)) }, label = { Text("Системная") }, leadingIcon = { Icon(Icons.Default.Smartphone, null, modifier = Modifier.size(18.dp)) })
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.onEvent(ReaderEvent.ToggleTtsControls) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.VolumeUp, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Озвучивание книги", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(24.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Шрифт", modifier = Modifier.weight(1f))
                    Text("${settings.fontSize}sp", color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { viewModel.onEvent(ReaderEvent.FontSizeChanged(18)) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Refresh, "Сброс", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Slider(value = settings.fontSize.toFloat(), onValueChange = { viewModel.onEvent(ReaderEvent.FontSizeChanged(it.toInt())) }, valueRange = 12f..32f, steps = 10)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Межстрочный", modifier = Modifier.weight(1f))
                    Text("%.1f".format(settings.lineHeight), color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { viewModel.onEvent(ReaderEvent.LineHeightChanged(1.8f)) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Refresh, "Сброс", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Slider(value = settings.lineHeight, onValueChange = { viewModel.onEvent(ReaderEvent.LineHeightChanged(it)) }, valueRange = 1.0f..3.0f, steps = 8)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Поля", modifier = Modifier.weight(1f))
                    Text("${settings.margins}dp", color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { viewModel.onEvent(ReaderEvent.MarginsChanged(16)) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Refresh, "Сброс", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Slider(value = settings.margins.toFloat(), onValueChange = { viewModel.onEvent(ReaderEvent.MarginsChanged(it.toInt())) }, valueRange = 0f..40f, steps = 8)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Режим", fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = ReaderMode.valueOf(settings.readerMode) == ReaderMode.VERTICAL, onClick = { viewModel.onEvent(ReaderEvent.ReaderModeChanged(ReaderMode.VERTICAL)) }, label = { Text("Прокрутка") }, leadingIcon = { Icon(Icons.Default.ViewAgenda, null, modifier = Modifier.size(18.dp)) })
                    FilterChip(selected = ReaderMode.valueOf(settings.readerMode) == ReaderMode.HORIZONTAL, onClick = { viewModel.onEvent(ReaderEvent.ReaderModeChanged(ReaderMode.HORIZONTAL)) }, label = { Text("Страницы") }, leadingIcon = { Icon(Icons.Default.Swipe, null, modifier = Modifier.size(18.dp)) })
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
private fun TtsControlsUI(state: ReaderState, viewModel: ReaderViewModel) {
    val context = LocalContext.current
    var selectedLang by remember(state.settings.ttsLanguage, state.availableLanguages) {
        mutableStateOf(state.settings.ttsLanguage ?: state.availableLanguages.firstOrNull { it == "Русский" } ?: state.availableLanguages.firstOrNull() ?: "") 
    }
    
    val filteredVoices = remember(selectedLang, state.availableVoices) {
        state.availableVoices.filter { it.language == selectedLang }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.onEvent(ReaderEvent.ToggleTtsControls) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
            }
            Text("Озвучивание книги", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = {
                try {
                    val intent = Intent().apply {
                        action = "com.android.settings.TTS_SETTINGS"
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Fallback
                    val intent = Intent().apply {
                        action = android.provider.Settings.ACTION_SETTINGS
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            }) {
                Icon(Icons.Default.Settings, contentDescription = "Выбор движка TTS")
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
                    androidx.compose.material3.Button(
                        onClick = { viewModel.onEvent(ReaderEvent.StartTts()) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Читать с текущего места")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    androidx.compose.material3.OutlinedButton(
                        onClick = { viewModel.onEvent(ReaderEvent.StartTtsSelection) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
                    ) {
                        Icon(Icons.Default.TouchApp, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Выбрать место начала чтения")
                    }
                }
            } else {
                if (state.isSpeaking) {
                    IconButton(onClick = { viewModel.onEvent(ReaderEvent.PauseTts) }, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Default.Pause, "Пауза", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    IconButton(onClick = { viewModel.onEvent(ReaderEvent.ResumeTts) }, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Default.PlayArrow, "Продолжить", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                
                Spacer(modifier = Modifier.width(32.dp))
                
                IconButton(onClick = { viewModel.onEvent(ReaderEvent.StopTts) }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Default.Stop, "Остановить", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Язык", style = MaterialTheme.typography.labelLarge)
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
        
        Text("Голос", style = MaterialTheme.typography.labelLarge)
        LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filteredVoices) { voice ->
                FilterChip(
                    selected = state.settings.ttsVoice == voice.id,
                    onClick = { viewModel.onEvent(ReaderEvent.SetTtsVoice(voice.id)) },
                    label = { Text(voice.name) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Скорость: %.1fx".format(state.settings.ttsRate), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(80.dp))
            Slider(
                value = state.settings.ttsRate,
                onValueChange = { viewModel.onEvent(ReaderEvent.SetTtsRate(it)) },
                valueRange = 0.5f..2.5f,
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Высота: %.1f".format(state.settings.ttsPitch), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(80.dp))
            Slider(
                value = state.settings.ttsPitch,
                onValueChange = { viewModel.onEvent(ReaderEvent.SetTtsPitch(it)) },
                valueRange = 0.5f..2.0f,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Таймер сна", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                                "Таймер активен",
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
                                contentDescription = "Отменить",
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
                        "Чтение остановится автоматически",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Text(
                "Выберите время, через которое чтение остановится",
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
                    label = { Text("$minutes мин") },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) }
                    } else null
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
