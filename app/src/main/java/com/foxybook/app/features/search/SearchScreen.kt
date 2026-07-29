package com.foxybook.app.features.search

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import android.content.res.Configuration
import com.foxybook.app.R
import com.foxybook.app.core.models.Author
import com.foxybook.app.core.models.Book
import com.foxybook.app.core.models.BookSource
import com.foxybook.app.core.models.SearchTab
import com.foxybook.app.core.models.Series
import com.foxybook.app.ui.components.BookCover
import com.foxybook.app.ui.components.PulsingBookLoader
import kotlinx.coroutines.delay

// ─── Constants ───

private val CARD_ELEVATION = 2.dp
private val SEARCH_BAR_SHAPE = RoundedCornerShape(28.dp)

// ─── Tab helpers ───

private data class TabInfo(val tab: SearchTab, val icon: ImageVector, val labelRes: Int)

private val allTabs = listOf(
    TabInfo(SearchTab.ALL, Icons.Default.Search, R.string.search_tab_all),
    TabInfo(SearchTab.BOOKS, Icons.AutoMirrored.Filled.MenuBook, R.string.search_tab_books),
    TabInfo(SearchTab.AUTHORS, Icons.Default.PersonSearch, R.string.search_tab_authors),
    TabInfo(SearchTab.SERIES, Icons.Default.AutoStories, R.string.search_tab_series),
    TabInfo(SearchTab.GENRES, Icons.Default.Style, R.string.search_tab_genres),
)

@Composable
private fun bookCountText(count: Int): String =
    "$count ${stringResource(R.string.books_plural_many)}"

// ═══════════════════════════════════════════════════════════════
//  SearchScreen
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBookClick: (Book) -> Unit,
    onSeriesClick: (String, String, String) -> Unit,
    onAuthorClick: (String, String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // ── Scroll state for auto-hide chips+tabs ──
    val listState = rememberLazyListState()
    val wasAtTop = remember { mutableStateOf(true) }
    val showHeader by remember {
        derivedStateOf {
            val atTop = listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
            if (atTop) true else wasAtTop.value
        }
    }
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        val atTop = listState.firstVisibleItemIndex == 0 &&
                listState.firstVisibleItemScrollOffset == 0
        wasAtTop.value = atTop
    }

    LaunchedEffect(Unit) {
        val pending = com.foxybook.app.core.models.PendingSearchQuery.query
        if (pending != null) {
            com.foxybook.app.core.models.PendingSearchQuery.query = null
            viewModel.onEvent(SearchEvent.QueryChanged(pending))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // ── Search bar ──
        SearchBar(
            query = state.query,
            onQueryChanged = { viewModel.onEvent(SearchEvent.QueryChanged(it)) },
            onSearch = { viewModel.onEvent(SearchEvent.SearchRequested) },
            isLandscape = isLandscape
        )

        // ── Source chips + Tabs (auto-hide in landscape) ──
        if (state.hasQuery) {
            AnimatedVisibility(
                visible = showHeader || !isLandscape,
                enter = expandVertically(tween(200)),
                exit = shrinkVertically(tween(200))
            ) {
                Column(Modifier.fillMaxWidth()) {
                    SourceSelector(
                        selectedSource = state.bookSource,
                        onSourceChanged = { viewModel.onEvent(SearchEvent.SourceChanged(it)) },
                        isLandscape = isLandscape
                    )
                    SearchTabRow(
                        selectedTab = state.selectedTab,
                        onTabSelected = { viewModel.onEvent(SearchEvent.TabSelected(it)) }
                    )
                }
            }
        }

        // ── Content ──
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.onEvent(SearchEvent.SearchRequested) },
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            when {
                !state.hasQuery -> IdleContent()
                state.isSearching && state.books.isEmpty() && state.authors.isEmpty() &&
                    state.series.isEmpty() && state.genreBooks.isEmpty() -> LoadingContent()
                state.error != null -> ErrorContent(state.error!!) { viewModel.onEvent(SearchEvent.SearchRequested) }
                state.isEmpty -> EmptyContent()
                else -> SearchContent(
                    state = state,
                    onBookClick = onBookClick,
                    onAuthorClick = onAuthorClick,
                    onSeriesClick = onSeriesClick,
                    onTabClick = { viewModel.onEvent(SearchEvent.TabSelected(it)) },
                    onLoadMore = { viewModel.onEvent(SearchEvent.LoadMore(it)) },
                    canLoadMore = { viewModel.canLoadMore(it) },
                    listState = listState
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Search Bar
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    isLandscape: Boolean = false
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    TextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (isLandscape) 4.dp else 10.dp),
        placeholder = {
            Text(
                stringResource(R.string.search_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChanged("") }) {
                    Icon(
                        Icons.Default.Clear,
                        stringResource(R.string.search_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        singleLine = true,
        shape = SEARCH_BAR_SHAPE,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MaterialTheme.colorScheme.primary
        ),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = androidx.compose.ui.text.input.ImeAction.Search
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onSearch = {
                keyboardController?.hide()
                onSearch()
            }
        )
    )
}

// ═══════════════════════════════════════════════════════════════
//  Source Selector
// ═══════════════════════════════════════════════════════════════

private val sourceEntries = listOf(
    BookSource.FLIBUSTA to "Flibusta",
    BookSource.COOLLIB to "CoolLib",
    BookSource.FANTASY_WORLDS to "Fantasy"
)

@Composable
private fun SourceSelector(
    selectedSource: BookSource,
    onSourceChanged: (BookSource) -> Unit,
    isLandscape: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = if (isLandscape) 2.dp else 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        sourceEntries.forEach { (source, label) ->
            val selected = selectedSource == source
            val chipColor = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant

            FilterChip(
                selected = selected,
                onClick = { onSourceChanged(source) },
                label = {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp
                    )
                },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = chipColor,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = chipColor.copy(alpha = 0.5f),
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = if (selected) Color.Transparent
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    selectedBorderColor = Color.Transparent,
                    borderWidth = 0.5.dp,
                    selectedBorderWidth = 0.dp,
                    enabled = true,
                    selected = selected
                ),
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Tab Row
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SearchTabRow(
    selectedTab: SearchTab,
    onTabSelected: (SearchTab) -> Unit
) {
    val tabWidth = remember { with(allTabs) { (1f / size).coerceAtMost(0.25f) } }

    PrimaryScrollableTabRow(
        selectedTabIndex = selectedTab.ordinal,
        modifier = Modifier.fillMaxWidth(),
        edgePadding = 8.dp,
        divider = {},
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        allTabs.forEach { tabInfo ->
            val selected = selectedTab == tabInfo.tab
            Tab(
                selected = selected,
                onClick = { onTabSelected(tabInfo.tab) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            tabInfo.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            stringResource(tabInfo.labelRes),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}

// ═══════════════════════════════════════════════════════════════
//  States
// ═══════════════════════════════════════════════════════════════

@Composable
private fun IdleContent() {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(100); visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(500)) + scaleIn(initialScale = 0.9f, animationSpec = tween(500))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 64.dp, horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Decorative circle with icon
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                "FoxyBook",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(8.dp))

            Text(
                stringResource(R.string.search_idle_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // Hint chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Книги", "Авторы", "Серии").forEach { hint ->
                    SuggestionChip(
                        onClick = { },
                        label = {
                            Text(hint, style = MaterialTheme.typography.labelSmall)
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    val infiniteTransition = rememberInfiniteTransition(label = "search_loading")

    // Вращение кольца
    val searchRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "search_rotation"
    )

    // Пульсация масштаба
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse_scale"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Анимированная лупа с кольцом
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .rotate(searchRotation)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                                )
                            )
                        )
                )
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp).scale(pulseScale),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Текст с анимированными точками
            Text(
                text = stringResource(R.string.search_ing),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(0, 150, 300).forEach { delay ->
                    val a by infiniteTransition.animateFloat(
                        initialValue = 0.3f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(600, delay, FastOutSlowInEasing), RepeatMode.Reverse),
                        label = "sdot_$delay"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .alpha(a)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 64.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            stringResource(R.string.search_no_results),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(4.dp))

        Text(
            stringResource(R.string.search_try_another),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 64.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            stringResource(R.string.search_error),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(Modifier.height(4.dp))

        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            maxLines = 3,
        )

        Spacer(Modifier.height(24.dp))

        FilledTonalButton(
            onClick = onRetry,
            shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)
        ) {
            Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.retry))
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Search Content
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SearchContent(
    state: SearchState,
    onBookClick: (Book) -> Unit,
    onAuthorClick: (String, String) -> Unit,
    onSeriesClick: (String, String, String) -> Unit,
    onTabClick: (SearchTab) -> Unit,
    onLoadMore: (SearchTab) -> Unit,
    canLoadMore: (SearchTab) -> Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState()
) {

    when (state.selectedTab) {
        SearchTab.ALL -> AllTab(
            state = state,
            listState = listState,
            onBookClick = onBookClick,
            onAuthorClick = onAuthorClick,
            onSeriesClick = onSeriesClick,
            onTabClick = onTabClick
        )
        SearchTab.BOOKS -> BooksTab(
            books = state.books,
            listState = listState,
            isLoading = state.isSearching,
            isLoadingMore = state.isLoadingMore,
            onBookClick = onBookClick,
            onLoadMore = { onLoadMore(SearchTab.BOOKS) },
            canLoadMore = { canLoadMore(SearchTab.BOOKS) }
        )
        SearchTab.AUTHORS -> AuthorsTab(
            authors = state.authors,
            listState = listState,
            isLoading = state.isSearchingAuthors,
            isLoadingMore = state.isLoadingMore,
            onAuthorClick = onAuthorClick,
            onLoadMore = { onLoadMore(SearchTab.AUTHORS) },
            canLoadMore = { canLoadMore(SearchTab.AUTHORS) }
        )
        SearchTab.SERIES -> SeriesTab(
            series = state.series,
            listState = listState,
            isLoading = state.isSearchingSeries,
            isLoadingMore = state.isLoadingMore,
            onSeriesClick = onSeriesClick,
            onLoadMore = { onLoadMore(SearchTab.SERIES) },
            canLoadMore = { canLoadMore(SearchTab.SERIES) }
        )
        SearchTab.GENRES -> {
            GenresTab(
                books = state.genreBooks,
                listState = listState,
                isLoading = state.isSearchingGenres,
                isLoadingMore = state.isLoadingMore,
                onBookClick = onBookClick,
                onLoadMore = { onLoadMore(SearchTab.GENRES) },
                canLoadMore = { canLoadMore(SearchTab.GENRES) }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  All Tab
// ═══════════════════════════════════════════════════════════════

@Composable
private fun AllTab(
    state: SearchState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onBookClick: (Book) -> Unit,
    onAuthorClick: (String, String) -> Unit,
    onSeriesClick: (String, String, String) -> Unit,
    onTabClick: (SearchTab) -> Unit
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Books section
        if (state.books.isNotEmpty()) {
            item(key = "books_header") {
                SectionHeader(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    title = stringResource(R.string.search_section_books),
                    count = state.books.size,
                    onViewAll = { onTabClick(SearchTab.BOOKS) }
                )
            }
            state.books.take(5).forEach { book ->
                item(key = "ab_${book.id}") {
                    BookCard(book = book, onClick = { onBookClick(book) })
                }
            }
            if (state.books.size > 5) {
                item(key = "books_all") {
                    ViewAllButton(stringResource(R.string.search_all_books)) { onTabClick(SearchTab.BOOKS) }
                }
            }
        }

        // Authors section
        if (state.authors.isNotEmpty()) {
            item(key = "authors_header") {
                SectionHeader(
                    icon = Icons.Default.PersonSearch,
                    title = stringResource(R.string.search_section_authors),
                    count = state.authors.size,
                    onViewAll = { onTabClick(SearchTab.AUTHORS) }
                )
            }
            state.authors.take(5).forEach { author ->
                item(key = "aa_${author.authorId}") {
                    AuthorCard(author = author, onClick = { onAuthorClick(author.authorId, author.name) })
                }
            }
            if (state.authors.size > 5) {
                item(key = "authors_all") {
                    ViewAllButton(stringResource(R.string.search_all_authors)) { onTabClick(SearchTab.AUTHORS) }
                }
            }
        } else if (state.isSearchingAuthors) {
            item(key = "authors_loading") {
                LoadingIndicator(text = stringResource(R.string.search_authors))
            }
        }

        // Series section
        if (state.series.isNotEmpty()) {
            item(key = "series_header") {
                SectionHeader(
                    icon = Icons.Default.AutoStories,
                    title = stringResource(R.string.search_section_series),
                    count = state.series.size,
                    onViewAll = { onTabClick(SearchTab.SERIES) }
                )
            }
            state.series.take(5).forEach { seriesItem ->
                item(key = "as_${seriesItem.seriesId}") {
                    SeriesCard(
                        series = seriesItem,
                        onClick = { onSeriesClick(seriesItem.seriesId, seriesItem.seriesTitle, seriesItem.authorId) }
                    )
                }
            }
            if (state.series.size > 5) {
                item(key = "series_all") {
                    ViewAllButton(stringResource(R.string.search_all_series)) { onTabClick(SearchTab.SERIES) }
                }
            }
        } else if (state.isSearchingSeries) {
            item(key = "series_loading") {
                LoadingIndicator(text = stringResource(R.string.search_series))
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    count: Int,
    onViewAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ViewAllButton(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LoadingIndicator(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Books Tab
// ═══════════════════════════════════════════════════════════════

@Composable
private fun BooksTab(
    books: List<Book>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    onBookClick: (Book) -> Unit,
    onLoadMore: () -> Unit,
    canLoadMore: () -> Boolean
) {
    if (isLoading && books.isEmpty()) {
        LoadingContent()
    } else if (books.isEmpty()) {
        EmptyTabContent(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            message = stringResource(R.string.search_no_books)
        )
    } else {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(books, key = { "b_${it.id}" }) { book ->
                BookCard(book = book, onClick = { onBookClick(book) })
            }
            item { LoadingFooter(isLoading = isLoadingMore, canLoadMore = canLoadMore, onLoadMore = onLoadMore) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Authors Tab
// ═══════════════════════════════════════════════════════════════

@Composable
private fun AuthorsTab(
    authors: List<Author>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    onAuthorClick: (String, String) -> Unit,
    onLoadMore: () -> Unit,
    canLoadMore: () -> Boolean
) {
    if (isLoading && authors.isEmpty()) {
        LoadingContent()
    } else if (authors.isEmpty()) {
        EmptyTabContent(
            icon = Icons.Default.PersonSearch,
            message = stringResource(R.string.search_no_authors)
        )
    } else {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(authors, key = { "a_${it.authorId}" }) { author ->
                AuthorCard(author = author, onClick = { onAuthorClick(author.authorId, author.name) })
            }
            item { LoadingFooter(isLoading = isLoadingMore, canLoadMore = canLoadMore, onLoadMore = onLoadMore) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Series Tab
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SeriesTab(
    series: List<Series>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    onSeriesClick: (String, String, String) -> Unit,
    onLoadMore: () -> Unit,
    canLoadMore: () -> Boolean
) {
    if (isLoading && series.isEmpty()) {
        LoadingContent()
    } else if (series.isEmpty()) {
        EmptyTabContent(
            icon = Icons.Default.AutoStories,
            message = stringResource(R.string.search_no_series)
        )
    } else {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(series, key = { "s_${it.seriesId}" }) { s ->
                SeriesCard(
                    series = s,
                    onClick = { onSeriesClick(s.seriesId, s.seriesTitle, s.authorId) }
                )
            }
            item { LoadingFooter(isLoading = isLoadingMore, canLoadMore = canLoadMore, onLoadMore = onLoadMore) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Genres Tab
// ═══════════════════════════════════════════════════════════════

@Composable
private fun GenresTab(
    books: List<Book>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    onBookClick: (Book) -> Unit,
    onLoadMore: () -> Unit,
    canLoadMore: () -> Boolean
) {
    if (isLoading && books.isEmpty()) {
        LoadingContent()
    } else if (books.isEmpty()) {
        EmptyTabContent(
            icon = Icons.Default.Style,
            message = stringResource(R.string.search_no_genres)
        )
    } else {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(books, key = { "g_${it.id}" }) { book ->
                BookCard(book = book, onClick = { onBookClick(book) })
            }
            item { LoadingFooter(isLoading = isLoadingMore, canLoadMore = canLoadMore, onLoadMore = onLoadMore) }
        }
    }
}

@Composable
private fun EmptyTabContent(icon: ImageVector, message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  Book Card
// ═══════════════════════════════════════════════════════════════

@Composable
private fun BookCard(book: Book, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            BookCover(
                coverUrl = book.coverUrl,
                title = book.title,
                author = book.author,
                contentDescription = book.title,
                width = 72.dp,
                height = 100.dp
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(book.author, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                if (book.genres.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(book.genres.take(2).joinToString(" · ") { if (it.length > 20) it.take(18) + "…" else it },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.primary, fontSize = 12.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (book.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(book.description.take(120),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 12.sp, lineHeight = 16.sp),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Author Card
// ═══════════════════════════════════════════════════════════════

@Composable
private fun AuthorCard(author: Author, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = CARD_ELEVATION),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar placeholder
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PersonSearch,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    author.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (author.bookCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                bookCountText(author.bookCount),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Series Card
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SeriesCard(series: Series, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = CARD_ELEVATION),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.AutoStories,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    series.seriesTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (series.bookCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                bookCountText(series.bookCount),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(
                            "· ${stringResource(R.string.series_details_books_in_series)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Load More / Loading Footer
// ═══════════════════════════════════════════════════════════════

@Composable
private fun LoadingFooter(
    isLoading: Boolean,
    canLoadMore: () -> Boolean,
    onLoadMore: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            LoadingSpinner()
        } else if (canLoadMore()) {
            FilledTonalButton(
                onClick = onLoadMore,
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.search_load_more),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun LoadingSpinner() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(40.dp),
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.search_loading_more),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium
        )
    }
}
