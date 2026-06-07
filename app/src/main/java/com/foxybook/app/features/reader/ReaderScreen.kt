package com.foxybook.app.features.reader

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MenuBook
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foxybook.app.core.models.ReaderMode
import com.foxybook.app.core.models.ReaderSettings
import com.foxybook.app.core.models.ReaderTheme
import com.foxybook.app.core.reader.ContentBlock
import com.foxybook.app.core.reader.TextPaginator
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════
//  Reader theme colors — isolated from app MaterialTheme
// ═══════════════════════════════════════════════════════════════

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

// ═══════════════════════════════════════════════════════════════
//  Main Reader Screen
// ═══════════════════════════════════════════════════════════════

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

    // Reader theme — independent of app theme
    val darkTheme = when (ReaderTheme.valueOf(settings.readerTheme)) {
        ReaderTheme.LIGHT -> false
        ReaderTheme.DARK -> true
        ReaderTheme.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = readerColors(darkTheme)

    LaunchedEffect(filePath) {
        viewModel.onEvent(ReaderEvent.LoadBook(filePath, bookId, bookFormat))
    }

    Scaffold(
        topBar = {
            if (!state.isImmersive) {
                TopAppBar(
                    title = {
                        Text(
                            text = state.book?.title ?: "Чтение",
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    actions = {
                        Text(
                            "${state.readingPercentage}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        IconButton(onClick = { viewModel.onEvent(ReaderEvent.ToggleChapters) }) {
                            Icon(Icons.Default.MenuBook, contentDescription = "Главы")
                        }
                        IconButton(onClick = { viewModel.onEvent(ReaderEvent.ToggleSettings) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Настройки")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        },
        bottomBar = {
            if (!state.isImmersive && state.book != null) {
                ReaderBottomBar(state, viewModel)
            }
        }
    ) { innerPadding ->
        // BoxWithConstraints gives us the EXACT available area after bars are subtracted
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
                .clickable { viewModel.onEvent(ReaderEvent.ToggleImmersive) }
        ) {
            val contentWidthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
            val contentHeightPx = with(LocalDensity.current) { maxHeight.roundToPx() }

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
                        Icon(Icons.Default.MenuBook, contentDescription = null,
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
                            contentWidthPx = contentWidthPx,
                            contentHeightPx = contentHeightPx
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

            // Progress bar
            if (!state.isImmersive) {
                LinearProgressIndicator(
                    progress = { state.readingPercentage / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    trackColor = Color.Transparent,
                )
            }
        }
    }

    if (state.showSettings) {
        SettingsSheet(settings, viewModel)
    }
    if (state.showChapters && state.book != null) {
        ChaptersSheet(state.book!!.chapters.map { it.title }, state.currentChapter) { idx ->
            viewModel.onEvent(ReaderEvent.ChapterChanged(idx))
            viewModel.onEvent(ReaderEvent.ToggleChapters)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Scroll Mode — LazyColumn of ContentBlocks
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ScrollModeContent(
    viewModel: ReaderViewModel,
    state: ReaderState,
    settings: ReaderSettings,
    colors: ReaderColors
) {
    val blocks = remember(state.currentChapter) { viewModel.getBlocks(state.currentChapter) }
    val lazyListState = rememberLazyListState()

    // Restore scroll position once
    LaunchedEffect(state.positionRestored, state.currentChapter) {
        if (state.positionRestored && state.scrollY > 0) {
            val itemIndex = state.scrollY.coerceAtMost(blocks.size - 1)
            lazyListState.scrollToItem(itemIndex, 0)
        }
    }

    val scrollPercentage by remember {
        derivedStateOf {
            val layoutInfo = lazyListState.layoutInfo
            if (layoutInfo.totalItemsCount <= 1 || layoutInfo.visibleItemsInfo.isEmpty()) 0
            else {
                val first = lazyListState.firstVisibleItemIndex
                val total = layoutInfo.totalItemsCount
                (first * 100f / total).toInt().coerceIn(0, 100)
            }
        }
    }

    LaunchedEffect(scrollPercentage) {
        viewModel.onEvent(ReaderEvent.ScrollProgress(scrollPercentage, lazyListState.firstVisibleItemIndex))
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = PaddingValues(
            horizontal = settings.margins.dp,
            vertical = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(count = blocks.size, key = { it }) { index ->
            BlockComposable(
                block = blocks[index],
                fontSize = settings.fontSize,
                lineHeight = settings.lineHeight,
                colors = colors
            )
        }

        // Chapter end indicator + bottom padding so last line is never hidden
        item {
            val book = state.book
            if (book != null && state.currentChapter < book.chapters.size - 1) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("— Конец главы —", style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Следующая: ${book.chapters[state.currentChapter + 1].title}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { viewModel.onEvent(ReaderEvent.NextChapter) })
                }
            } else {
                // Extra bottom padding so the very last line is fully visible
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Page Mode — HorizontalPager with REAL available height
// ═══════════════════════════════════════════════════════════════

@Composable
private fun PageModeContent(
    viewModel: ReaderViewModel,
    state: ReaderState,
    settings: ReaderSettings,
    colors: ReaderColors,
    contentWidthPx: Int,
    contentHeightPx: Int
) {
    val blocks = remember(state.currentChapter) { viewModel.getBlocks(state.currentChapter) }

    // Paginate using the ACTUAL available height (already minus bars)
    val pages = remember(blocks, state.currentChapter, contentWidthPx, contentHeightPx, settings) {
        TextPaginator.paginate(blocks, state.currentChapter, contentWidthPx, contentHeightPx, settings)
    }

    val book = state.book
    val hasPrevChapter = book != null && state.currentChapter > 0
    val hasNextChapter = book != null && state.currentChapter < book.chapters.size - 1
    val totalPagerPages = pages.size + (if (hasPrevChapter) 1 else 0) + (if (hasNextChapter) 1 else 0)
    val pageOffset = if (hasPrevChapter) 1 else 0

    val initialPage = if (state.positionRestored && state.scrollY > 0) {
        state.scrollY.coerceIn(0, pages.size - 1) + pageOffset
    } else pageOffset

    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { totalPagerPages })

    LaunchedEffect(pagerState.settledPage) {
        val page = pagerState.settledPage
        if (hasPrevChapter && page == 0) {
            viewModel.onEvent(ReaderEvent.PreviousChapter)
        } else if (hasNextChapter && page == totalPagerPages - 1) {
            viewModel.onEvent(ReaderEvent.NextChapter)
        } else {
            val pageIndex = (page - pageOffset).coerceIn(0, pages.size - 1)
            viewModel.onEvent(ReaderEvent.PageInfo(pageIndex, pages.size))
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        when {
            hasPrevChapter && page == 0 -> {
                ChapterTransitionPage(
                    title = book!!.chapters[state.currentChapter - 1].title,
                    label = "Предыдущая глава",
                    colors = colors
                ) { viewModel.onEvent(ReaderEvent.PreviousChapter) }
            }
            hasNextChapter && page == totalPagerPages - 1 -> {
                ChapterTransitionPage(
                    title = book!!.chapters[state.currentChapter + 1].title,
                    label = "Следующая глава",
                    colors = colors
                ) { viewModel.onEvent(ReaderEvent.NextChapter) }
            }
            else -> {
                val pageIndex = (page - pageOffset).coerceIn(0, pages.size - 1)
                val pageBlocks = pages[pageIndex].blocks

                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                horizontal = settings.margins.dp,
                                vertical = 16.dp
                            ),
                        verticalArrangement = Arrangement.Top
                    ) {
                        pageBlocks.forEach { block ->
                            BlockComposable(
                                block = block,
                                fontSize = settings.fontSize,
                                lineHeight = settings.lineHeight,
                                colors = colors
                            )
                        }
                    }

                    // Page number
                    Text(
                        text = "${pageIndex + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary.copy(alpha = 0.4f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  ContentBlock Composable — uses ReaderColors, not MaterialTheme
// ═══════════════════════════════════════════════════════════════

@Composable
private fun BlockComposable(
    block: ContentBlock,
    fontSize: Int,
    lineHeight: Float,
    colors: ReaderColors
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
                modifier = Modifier.padding(bottom = (fontSize * 0.4).dp)
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
                        drawRect(
                            color = colors.quoteBorder,
                            topLeft = Offset(0f, 0f),
                            size = Size(3f, size.height)
                        )
                    }
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .padding(start = 24.dp)
            ) {
                if (block.title != null) {
                    Text(
                        text = block.title,
                        fontSize = (fontSize - 1).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                block.lines.forEach { line ->
                    Text(
                        text = line,
                        fontSize = (fontSize - 1).sp,
                        lineHeight = (fontSize * lineHeight * 0.9).sp,
                        color = colors.text
                    )
                }
                if (block.author != null) {
                    Text(
                        text = block.author,
                        fontSize = (fontSize - 2).sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 6.dp).align(Alignment.End)
                    )
                }
            }
        }
        is ContentBlock.EmptyLine -> {
            Spacer(modifier = Modifier.height(block.height.dp))
        }
        is ContentBlock.Image -> {
            Box(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = block.alt,
                    modifier = Modifier.size(48.dp),
                    tint = colors.text.copy(alpha = 0.2f))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Chapter Transition Page
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ChapterTransitionPage(
    title: String,
    label: String,
    colors: ReaderColors,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onClick))
    }
}

// ═══════════════════════════════════════════════════════════════
//  Bottom Bar
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ReaderBottomBar(state: ReaderState, viewModel: ReaderViewModel) {
    val book = state.book ?: return
    val chapter = book.chapters.getOrNull(state.currentChapter)
    val mode = ReaderMode.valueOf(state.settings.readerMode)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { viewModel.onEvent(ReaderEvent.PreviousChapter) },
            enabled = state.currentChapter > 0,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Default.MenuBook, "Пред.", modifier = Modifier.size(18.dp),
                tint = if (state.currentChapter > 0) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
        }

        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = chapter?.title ?: "",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (mode == ReaderMode.HORIZONTAL) {
                    "Стр. ${state.pageCurrent + 1}/${state.pageTotal}"
                } else {
                    "Гл. ${state.currentChapter + 1}/${book.chapters.size}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }

        IconButton(
            onClick = { viewModel.onEvent(ReaderEvent.NextChapter) },
            enabled = state.currentChapter < book.chapters.size - 1,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Default.MenuBook, "След.", modifier = Modifier.size(18.dp),
                tint = if (state.currentChapter < book.chapters.size - 1) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Settings Sheet
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(settings: ReaderSettings, viewModel: ReaderViewModel) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = { viewModel.onEvent(ReaderEvent.ToggleSettings) },
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Настройки чтения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Шрифт", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("${settings.fontSize}sp", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
            Slider(value = settings.fontSize.toFloat(),
                onValueChange = { viewModel.onEvent(ReaderEvent.FontSizeChanged(it.toInt())) },
                valueRange = 12f..32f, steps = 10, modifier = Modifier.fillMaxWidth())

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Межстрочный", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(String.format("%.1f", settings.lineHeight), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
            Slider(value = settings.lineHeight,
                onValueChange = { viewModel.onEvent(ReaderEvent.LineHeightChanged(it)) },
                valueRange = 1.0f..3.0f, steps = 8, modifier = Modifier.fillMaxWidth())

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Поля", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("${settings.margins}dp", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
            Slider(value = settings.margins.toFloat(),
                onValueChange = { viewModel.onEvent(ReaderEvent.MarginsChanged(it.toInt())) },
                valueRange = 0f..40f, steps = 8, modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(16.dp))

            Text("Режим", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = ReaderMode.valueOf(settings.readerMode) == ReaderMode.VERTICAL,
                    onClick = { viewModel.onEvent(ReaderEvent.ReaderModeChanged(ReaderMode.VERTICAL)) },
                    label = { Text("Прокрутка") },
                    leadingIcon = { Icon(Icons.Default.ViewAgenda, null, modifier = Modifier.size(18.dp)) }
                )
                FilterChip(
                    selected = ReaderMode.valueOf(settings.readerMode) == ReaderMode.HORIZONTAL,
                    onClick = { viewModel.onEvent(ReaderEvent.ReaderModeChanged(ReaderMode.HORIZONTAL)) },
                    label = { Text("Страницы") },
                    leadingIcon = { Icon(Icons.Default.Swipe, null, modifier = Modifier.size(18.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Тема книги", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = ReaderTheme.valueOf(settings.readerTheme) == ReaderTheme.LIGHT,
                    onClick = { viewModel.onEvent(ReaderEvent.ReaderThemeChanged(ReaderTheme.LIGHT)) },
                    label = { Text("Светлая") },
                    leadingIcon = { Icon(Icons.Default.LightMode, null, modifier = Modifier.size(18.dp)) }
                )
                FilterChip(
                    selected = ReaderTheme.valueOf(settings.readerTheme) == ReaderTheme.DARK,
                    onClick = { viewModel.onEvent(ReaderEvent.ReaderThemeChanged(ReaderTheme.DARK)) },
                    label = { Text("Тёмная") },
                    leadingIcon = { Icon(Icons.Default.DarkMode, null, modifier = Modifier.size(18.dp)) }
                )
                FilterChip(
                    selected = ReaderTheme.valueOf(settings.readerTheme) == ReaderTheme.SYSTEM,
                    onClick = { viewModel.onEvent(ReaderEvent.ReaderThemeChanged(ReaderTheme.SYSTEM)) },
                    label = { Text("Системная") },
                    leadingIcon = { Icon(Icons.Default.Smartphone, null, modifier = Modifier.size(18.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Chapters Sheet
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChaptersSheet(chapterTitles: List<String>, current: Int, onSelect: (Int) -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = { onSelect(-1) }, sheetState = sheetState) {
        LazyColumn(modifier = Modifier.padding(vertical = 8.dp)) {
            items(count = chapterTitles.size, key = { it }) { index ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(index) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chapterTitles[index],
                        fontWeight = if (index == current) FontWeight.Bold else FontWeight.Normal,
                        color = if (index == current) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
