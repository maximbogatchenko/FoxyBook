package com.foxybook.app.features.reader

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.OutlinedCard
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.History
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
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
        // We use full screen height minus space for the page number indicator at the bottom.
        val availableHeightPx = screenHeightPx - with(density) { 32.dp.roundToPx() }
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
                    ) { viewModel.onEvent(ReaderEvent.ToggleImmersive) }
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
                                density = density
                            )
                        } else {
                            ScrollModeContent(
                                viewModel = viewModel,
                                state = state,
                                settings = settings,
                                colors = colors
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

                                val isBookmarked = remember(state.bookmarks, state.currentChapter, state.textOffset) {
                                    state.bookmarks.any {
                                        it.chapterIndex == state.currentChapter && 
                                        (it.textOffset in state.textOffset - 20..state.textOffset + 20)
                                    }
                                }

                                IconButton(onClick = {
                                    if (isBookmarked) {
                                        val b = state.bookmarks.find {
                                            it.chapterIndex == state.currentChapter &&
                                            (it.textOffset in state.textOffset - 20..state.textOffset + 20)
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
    colors: ReaderColors
) {
    val lazyListState = rememberLazyListState()
    val book = state.book ?: return
    
    val isCurrentChapterLoaded = remember(state.currentChapter, state.chapterBlocks) {
        state.chapterBlocks.containsKey(state.currentChapter)
    }

    val chaptersToRender = remember(state.currentChapter, book.chapters.size) {
        (state.currentChapter - 1..state.currentChapter + 1)
            .filter { it in book.chapters.indices }
    }

    val globalBlocks = remember(chaptersToRender, state.chapterBlocks) {
        val list = mutableListOf<GlobalBlock>()
        chaptersToRender.forEach { chIdx ->
            val blocks = state.chapterBlocks[chIdx] ?: emptyList()
            var currentOffset = 0
            blocks.forEachIndexed { bIdx, block ->
                list.add(GlobalBlock(block, chIdx, bIdx, currentOffset))
                currentOffset += block.getTextContent().length
            }
        }
        list
    }

    // Position restoration and sync
    var isRestoring by remember(state.lastPositionRestoreTrigger) { mutableStateOf(true) }

    LaunchedEffect(state.lastPositionRestoreTrigger, state.currentChapter, globalBlocks.size, isCurrentChapterLoaded) {
        if (globalBlocks.isNotEmpty() && isCurrentChapterLoaded) {
            if (state.positionRestored) {
                // Try finding by textOffset first
                var targetGlobalIndex = globalBlocks.indexOfFirst { 
                    it.chapterIndex == state.currentChapter && 
                    state.textOffset in it.offsetInChapter until (it.offsetInChapter + it.block.getTextContent().length).coerceAtLeast(it.offsetInChapter + 1)
                }
                
                // Fallback to block index (scrollY)
                if (targetGlobalIndex < 0) {
                    targetGlobalIndex = globalBlocks.indexOfFirst { 
                        it.chapterIndex == state.currentChapter && it.blockIndexInChapter == state.scrollY 
                    }
                }

                if (targetGlobalIndex >= 0) {
                    Log.d("ReaderNav", "ScrollMode: Restoring to item $targetGlobalIndex, offset ${state.scrollOffset} (ch=${state.currentChapter}, offset=${state.textOffset})")
                    lazyListState.scrollToItem(targetGlobalIndex, state.scrollOffset)
                }
            } else {
                // If chapter changed manually, scroll to its beginning
                val firstInChapter = globalBlocks.indexOfFirst { it.chapterIndex == state.currentChapter }
                if (firstInChapter >= 0 && !lazyListState.isScrollInProgress) {
                    val currentFirst = lazyListState.firstVisibleItemIndex
                    val currentFirstBlock = globalBlocks.getOrNull(currentFirst)
                    if (currentFirstBlock?.chapterIndex != state.currentChapter) {
                         Log.d("ReaderNav", "ScrollMode: Syncing to start of chapter ${state.currentChapter}")
                         lazyListState.scrollToItem(firstInChapter)
                    }
                }
            }
            isRestoring = false
        }
    }

    // Monitor scroll to update current chapter and offset
    val firstVisibleIndex by remember { derivedStateOf { lazyListState.firstVisibleItemIndex } }
    val firstVisibleOffset by remember { derivedStateOf { lazyListState.firstVisibleItemScrollOffset } }
    
    LaunchedEffect(firstVisibleIndex, firstVisibleOffset, isRestoring) {
        if (isRestoring) return@LaunchedEffect
        
        val currentGlobalBlock = globalBlocks.getOrNull(firstVisibleIndex)
        if (currentGlobalBlock != null) {
            if (currentGlobalBlock.chapterIndex != state.currentChapter) {
                Log.d("ReaderNav", "ScrollMode: Auto chapter change to ${currentGlobalBlock.chapterIndex}")
                viewModel.onEvent(ReaderEvent.ChapterChanged(currentGlobalBlock.chapterIndex, resetPosition = false))
            }
            
            val total = globalBlocks.size
            val percentage = if (total > 0) (firstVisibleIndex * 100 / total) else 0
            viewModel.onEvent(ReaderEvent.ScrollProgress(
                percentage, 
                currentGlobalBlock.blockIndexInChapter,
                firstVisibleOffset,
                currentGlobalBlock.offsetInChapter
            ))
        }
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = PaddingValues(horizontal = (if (state.isImmersive) 16 else settings.margins).dp, vertical = 16.dp),
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
                onTtsClick = { viewModel.onEvent(ReaderEvent.StartTts(gb.chapterIndex, gb.blockIndexInChapter)) }
            )
        }

        if (state.currentChapter == book.chapters.size - 1) {
            item {
                Spacer(modifier = Modifier.height(100.dp))
                Text("Конец книги", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = colors.textSecondary)
                Spacer(modifier = Modifier.height(100.dp))
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
    density: Density
) {
    LaunchedEffect(state.currentChapter, contentWidthPx, contentHeightPx, settings.fontSize, settings.lineHeight) {
        viewModel.onEvent(ReaderEvent.UpdatePageDimensions(contentWidthPx, contentHeightPx, textMeasurer, density))
    }

    val book = state.book ?: return
    
    val isCurrentChapterLoaded = remember(state.currentChapter, state.chapterPages) {
        state.chapterPages.containsKey(state.currentChapter)
    }

    val chaptersInRange = remember(state.currentChapter) {
        (state.currentChapter - 1..state.currentChapter + 1).filter { it in book.chapters.indices }
    }

    val globalPages = remember(chaptersInRange, state.chapterPages) {
        val list = mutableListOf<GlobalPage>()
        chaptersInRange.forEach { chIdx ->
            val pages = state.chapterPages[chIdx] ?: emptyList()
            pages.forEachIndexed { pIdx, page ->
                list.add(GlobalPage(page, chIdx, pIdx))
            }
        }
        list
    }

    if (globalPages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val initialGlobalIndex = remember(state.currentChapter, state.positionRestored, globalPages) {
        // Find by textOffset
        var targetIndex = globalPages.indexOfFirst { gp ->
            gp.chapterIndex == state.currentChapter && 
            state.textOffset in gp.page.startOffset until (gp.page.endOffset).coerceAtLeast(gp.page.startOffset + 1)
        }
        
        // Fallback to page index
        if (targetIndex < 0) {
            targetIndex = globalPages.indexOfFirst { it.chapterIndex == state.currentChapter && it.pageIndexInChapter == state.pageCurrent }
        }
        
        targetIndex
    }

    val pagerState = rememberPagerState(
        initialPage = if (initialGlobalIndex >= 0) initialGlobalIndex else 0,
        pageCount = { globalPages.size }
    )

    var isRestoring by remember { mutableStateOf(true) }

    LaunchedEffect(state.isImmersive) {
        val pages = state.chapterPages[state.currentChapter]
        if (state.isImmersive) {
            Log.d("ReaderNav", "FULLSCREEN ON: ch=${state.currentChapter}, page=${state.pageCurrent}, pageCount=${pages?.size}")
        } else {
            Log.d("ReaderNav", "FULLSCREEN OFF: ch=${state.currentChapter}, page=${state.pageCurrent}, pageCount=${pages?.size}")
        }
    }

    LaunchedEffect(state.lastPositionRestoreTrigger, initialGlobalIndex, isCurrentChapterLoaded) {
        if (initialGlobalIndex >= 0 && isCurrentChapterLoaded) {
            if (pagerState.currentPage != initialGlobalIndex) {
                Log.d("ReaderNav", "PageMode: Scrolling pager to $initialGlobalIndex (ch=${state.currentChapter}, offset=${state.textOffset})")
                pagerState.scrollToPage(initialGlobalIndex)
            }
            delay(100) // Small delay to let Pager settle
            isRestoring = false
        }
    }

    LaunchedEffect(pagerState.settledPage, state.currentChapter, isRestoring) {
        if (isRestoring) return@LaunchedEffect
        
        val settledGlobalPage = globalPages.getOrNull(pagerState.settledPage)
        if (settledGlobalPage != null) {
            if (settledGlobalPage.chapterIndex != state.currentChapter) {
                // Ensure the pager is really settled on a different chapter before switching state
                if (!pagerState.isScrollInProgress) {
                    Log.d("ReaderNav", "PageMode: Auto chapter change to ${settledGlobalPage.chapterIndex}")
                    viewModel.onEvent(ReaderEvent.ChapterChanged(settledGlobalPage.chapterIndex, resetPosition = false))
                }
            } else {
                val totalInChapter = state.chapterPages[settledGlobalPage.chapterIndex]?.size ?: 1
                Log.d("ReaderNav", "PageMode: PageInfo triggered: page=${settledGlobalPage.pageIndexInChapter}, offset=${settledGlobalPage.page.startOffset}")
                viewModel.onEvent(ReaderEvent.PageInfo(
                    settledGlobalPage.pageIndexInChapter, 
                    totalInChapter,
                    settledGlobalPage.page.startOffset
                ))
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 3
    ) { index ->
        val gp = globalPages.getOrNull(index)
        if (gp != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = (if (state.isImmersive) 16 else settings.margins).dp, vertical = 16.dp),
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
                            }
                        )
                    }
                }
                Text(
                    text = "${gp.pageIndexInChapter + 1} / ${state.chapterPages[gp.chapterIndex]?.size ?: "?"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                )
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
    onTtsClick: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    val bottomPadding = if (block.isSplitAtBottom) 0.dp else (fontSize * 0.4).dp
    
    val highlightColor = if (isCurrentTtsBlock) colors.selectionHighlight else Color.Transparent
    
    Box(modifier = Modifier.background(highlightColor)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isSelectionMode) {
                        Modifier.combinedClickable(
                            onClick = { showMenu = true },
                            onLongClick = { showMenu = true }
                        )
                    } else Modifier
                )
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
                    Text(
                        text = block.text,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * lineHeight).sp,
                        color = colors.text,
                        textAlign = TextAlign.Justify,
                        modifier = Modifier.padding(bottom = bottomPadding)
                    )
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

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Начать чтение отсюда") },
                onClick = {
                    showMenu = false
                    onTtsClick()
                },
                leadingIcon = { Icon(Icons.Default.PlayArrow, null) }
            )
            DropdownMenuItem(
                text = { Text("Копировать") },
                onClick = { showMenu = false },
                leadingIcon = { Icon(Icons.Default.ContentCopy, null) }
            )
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
            Icon(Icons.AutoMirrored.Filled.MenuBook, "Пред.", modifier = Modifier.size(18.dp),
                tint = if (state.currentChapter > 0) MaterialTheme.colorScheme.primary else colors.text.copy(alpha = 0.2f))
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = chapter?.title ?: "", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.text)
            Text(text = if (mode == ReaderMode.HORIZONTAL) "Стр. ${state.pageCurrent + 1}/${state.pageTotal}" else "Гл. ${state.currentChapter + 1}/${book.chapters.size}",
                style = MaterialTheme.typography.labelSmall, color = colors.text.copy(alpha = 0.6f))
        }
        IconButton(onClick = { viewModel.onEvent(ReaderEvent.NextChapter) }, enabled = state.currentChapter < book.chapters.size - 1, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, "След.", modifier = Modifier.size(18.dp),
                tint = if (state.currentChapter < book.chapters.size - 1) MaterialTheme.colorScheme.primary else colors.text.copy(alpha = 0.2f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(settings: ReaderSettings, viewModel: ReaderViewModel) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = { viewModel.onEvent(ReaderEvent.ToggleSettings) }, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
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
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Шрифт", modifier = Modifier.weight(1f))
                    Text("${settings.fontSize}sp", color = MaterialTheme.colorScheme.primary)
                }
                Slider(value = settings.fontSize.toFloat(), onValueChange = { viewModel.onEvent(ReaderEvent.FontSizeChanged(it.toInt())) }, valueRange = 12f..32f, steps = 10)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Межстрочный", modifier = Modifier.weight(1f))
                    Text("%.1f".format(settings.lineHeight), color = MaterialTheme.colorScheme.primary)
                }
                Slider(value = settings.lineHeight, onValueChange = { viewModel.onEvent(ReaderEvent.LineHeightChanged(it)) }, valueRange = 1.0f..3.0f, steps = 8)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Поля", modifier = Modifier.weight(1f))
                    Text("${settings.margins}dp", color = MaterialTheme.colorScheme.primary)
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

@Composable
private fun TtsControlsUI(state: ReaderState, viewModel: ReaderViewModel) {
    val context = LocalContext.current
    var selectedLang by remember(state.settings.ttsLanguage) { 
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
    }
}
