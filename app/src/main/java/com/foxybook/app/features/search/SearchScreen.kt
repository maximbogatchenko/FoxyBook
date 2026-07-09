package com.foxybook.app.features.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foxybook.app.core.models.Author
import com.foxybook.app.core.models.Book
import com.foxybook.app.core.models.BookSource
import com.foxybook.app.core.models.SearchTab
import com.foxybook.app.core.models.Series
import com.foxybook.app.ui.components.CoverWithAuthor
import com.foxybook.app.ui.components.PulsingBookLoader
import androidx.compose.ui.res.stringResource
import com.foxybook.app.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBookClick: (Book) -> Unit,
    onSeriesClick: (String, String, String) -> Unit,
    onAuthorClick: (String, String) -> Unit
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding()
    ) {
        // ── Search input ──
        TextField(
            value = state.query,
            onValueChange = { viewModel.onEvent(SearchEvent.QueryChanged(it)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onEvent(SearchEvent.QueryChanged("")) }) {
                        Icon(Icons.Default.Clear, "Очистить")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = { viewModel.onEvent(SearchEvent.SearchRequested) }
            )
        )

        // ── Tabs ──
        if (state.hasQuery) {
            TabRow(
                selectedTabIndex = state.selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth()
            ) {
                SearchTab.entries.forEach { tab ->
                    Tab(
                        selected = state.selectedTab == tab,
                        onClick = { viewModel.onEvent(SearchEvent.TabSelected(tab)) },
                        text = { Text(tab.label) }
                    )
                }
            }
        }

        // ── Source Selector ──
        if (state.hasQuery) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    BookSource.FLIBUSTA to "Flibusta",
                    BookSource.COOLLIB to "CoolLib",
                    BookSource.FANTASY_WORLDS to "Fantasy"
                ).forEach { (source, label) ->
                    val selected = state.bookSource == source
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.onEvent(SearchEvent.SourceChanged(source)) },
                        label = {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                            borderWidth = 0.5.dp,
                            selectedBorderWidth = 1.dp,
                            enabled = true,
                            selected = selected
                        )
                    )
                }
            }
        }

        // ── Content ──
        PullToRefreshBox(
            isRefreshing = state.isSearching,
            onRefresh = { viewModel.onEvent(SearchEvent.SearchRequested) },
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            when {
                !state.hasQuery -> IdleContent()
                state.isSearching -> LoadingContent()
                state.error != null -> ErrorContent(state.error!!) { viewModel.onEvent(SearchEvent.SearchRequested) }
                state.isEmpty -> EmptyContent()
                else -> SearchContent(
                    state = state,
                    onBookClick = onBookClick,
                    onAuthorClick = onAuthorClick,
                    onSeriesClick = onSeriesClick,
                    onTabClick = { viewModel.onEvent(SearchEvent.TabSelected(it)) },
                    onLoadMore = { viewModel.onEvent(SearchEvent.LoadMore(it)) },
                    canLoadMore = { viewModel.canLoadMore(it) }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  States
// ═══════════════════════════════════════════════════════════════

@Composable
private fun IdleContent() {
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.MenuBook, null, modifier = Modifier.size(88.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        Spacer(Modifier.height(20.dp))
        Text("FoxyBook", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("Введите запрос для поиска", style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoadingContent() {
    Box(Modifier.fillMaxSize().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Поиск…", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyContent() {
    Column(
        Modifier.fillMaxSize().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Search, null, modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        Spacer(Modifier.height(16.dp))
        Text("Ничего не найдено", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text("Попробуйте другой запрос", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Search, null, modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
        Spacer(Modifier.height(16.dp))
        Text("Ошибка поиска", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onRetry) { Text("Повторить") }
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
    canLoadMore: (SearchTab) -> Boolean
) {
    val listState = rememberLazyListState()

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
            onBookClick = onBookClick,
            onLoadMore = { onLoadMore(SearchTab.BOOKS) },
            canLoadMore = { canLoadMore(SearchTab.BOOKS) }
        )
        SearchTab.AUTHORS -> AuthorsTab(
            authors = state.authors,
            listState = listState,
            isLoading = state.isSearchingAuthors,
            onAuthorClick = onAuthorClick,
            onLoadMore = { onLoadMore(SearchTab.AUTHORS) },
            canLoadMore = { canLoadMore(SearchTab.AUTHORS) }
        )
        SearchTab.SERIES -> SeriesTab(
            series = state.series,
            listState = listState,
            isLoading = state.isSearchingSeries,
            onSeriesClick = onSeriesClick,
            onLoadMore = { onLoadMore(SearchTab.SERIES) },
            canLoadMore = { canLoadMore(SearchTab.SERIES) }
        )
        SearchTab.GENRES -> BooksTab(
            books = state.genreBooks,
            listState = listState,
            isLoading = state.isSearchingGenres,
            onBookClick = onBookClick,
            onLoadMore = { onLoadMore(SearchTab.GENRES) },
            canLoadMore = { canLoadMore(SearchTab.GENRES) },
            emptyMessage = stringResource(R.string.search_no_genres),
            loadingMessage = stringResource(R.string.search_genres)
        )
    }

    // Infinite scroll for BOOKS/AUTHORS/SERIES tabs
    if (state.selectedTab != SearchTab.ALL) {
        val nearEnd by remember {
            derivedStateOf {
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
                last.index >= listState.layoutInfo.totalItemsCount - 3
            }
        }
        LaunchedEffect(Unit) {
            snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect {
                onLoadMore(state.selectedTab)
            }
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
    LazyColumn(state = listState, contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // Books section
        if (state.books.isNotEmpty()) {
            item {
                SectionHeader("Книги", onViewAll = { onTabClick(SearchTab.BOOKS) })
            }
            val showBooks = state.books.take(5)
            showBooks.forEach { book ->
                item(key = "ab_${book.id}") {
                    BookCard(book = book, onClick = { onBookClick(book) })
                }
            }
            if (state.books.size > 5) {
                item {
                    TextButton(stringResource(R.string.search_all_books)) { onTabClick(SearchTab.BOOKS) }
                }
            }
        }

        // Authors section
        if (state.authors.isNotEmpty()) {
            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            item {
                SectionHeader("Авторы", onViewAll = { onTabClick(SearchTab.AUTHORS) })
            }
            val showAuthors = state.authors.take(5)
            showAuthors.forEach { author ->
                item(key = "aa_${author.authorId}") {
                    AuthorCard(author = author, onClick = { onAuthorClick(author.authorId, author.name) })
                }
            }
            if (state.authors.size > 5) {
                item {
                    TextButton("Все авторы →") { onTabClick(SearchTab.AUTHORS) }
                }
            }
        } else if (state.isSearchingAuthors) {
            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            item {
                SectionHeader("Авторы", onViewAll = {})
            }
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Поиск авторов...", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Series section
        if (state.series.isNotEmpty()) {
            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            item {
                SectionHeader("Серии", onViewAll = { onTabClick(SearchTab.SERIES) })
            }
            state.series.take(5).forEach { seriesItem ->
                item(key = "as_${seriesItem.seriesId}") {
                    SeriesCard(series = seriesItem, onClick = { onSeriesClick(seriesItem.seriesId, seriesItem.seriesTitle, seriesItem.authorId) })
                }
            }
            if (state.series.size > 5) {
                item {
                    TextButton("Все серии →") { onTabClick(SearchTab.SERIES) }
                }
            }
        } else if (state.isSearchingSeries) {
            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
            item {
                SectionHeader("Серии", onViewAll = {})
            }
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Поиск серий...", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onViewAll: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun TextButton(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        ),
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp)
    )
}

// ═══════════════════════════════════════════════════════════════
//  Books Tab
// ═══════════════════════════════════════════════════════════════

@Composable
private fun BooksTab(
    books: List<Book>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    isLoading: Boolean,
    onBookClick: (Book) -> Unit,
    onLoadMore: () -> Unit,
    canLoadMore: () -> Boolean,
    emptyMessage: String = stringResource(R.string.search_no_books),
    loadingMessage: String = stringResource(R.string.search_books)
) {
    if (isLoading && books.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.search_books), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else if (books.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Search, null, modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(16.dp))
            Text("Книги не найдены", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(state = listState, contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (book in books) {
                item(key = "b_${book.id}") {
                    BookCard(book = book, onClick = { onBookClick(book) })
                }
            }
            item { LoadingFooter(canLoadMore) }
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
    onAuthorClick: (String, String) -> Unit,
    onLoadMore: () -> Unit,
    canLoadMore: () -> Boolean
) {
    if (isLoading && authors.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Поиск авторов…", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else if (authors.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.PersonSearch, null, modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(16.dp))
            Text("Авторы не найдены", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(state = listState, contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (author in authors) {
                item(key = "a_${author.authorId}") {
                    AuthorCard(author = author, onClick = { onAuthorClick(author.authorId, author.name) })
                }
            }
            item { LoadingFooter(canLoadMore) }
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
    onSeriesClick: (String, String, String) -> Unit,
    onLoadMore: () -> Unit,
    canLoadMore: () -> Boolean
) {
    if (isLoading && series.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Поиск серий…", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else if (series.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.AutoStories, null, modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(16.dp))
            Text("Серии не найдены", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(state = listState, contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (s in series) {
                item(key = "s_${s.seriesId}") {
                    SeriesCard(series = s, onClick = { onSeriesClick(s.seriesId, s.seriesTitle, s.authorId) })
                }
            }
            item { LoadingFooter(canLoadMore) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Cards
// ═══════════════════════════════════════════════════════════════

@Composable
private fun BookCard(book: Book, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
            CoverWithAuthor(coverUrl = book.coverUrl, author = book.author, contentDescription = book.title)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(book.author, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (book.genres.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(book.genres.take(2).joinToString(" · ") { if (it.length > 20) it.take(18) + "…" else it },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), fontSize = 12.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun AuthorCard(author: Author, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Card(Modifier.size(56.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                shape = RoundedCornerShape(12.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PersonSearch, null, tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(author.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (author.bookCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text("${author.bookCount} " + stringResource(R.string.books_plural_many),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium)
                }
            }
            Box(Modifier.size(28.dp).clip(CircleShape).padding(4.dp), contentAlignment = Alignment.Center) {
                Text("›", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun SeriesCard(series: Series, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Card(Modifier.size(56.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                shape = RoundedCornerShape(12.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Style, null, tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(series.seriesTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (series.bookCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text("${series.bookCount} " + stringResource(R.string.books_plural_many) + " · " + stringResource(R.string.series_details_books_in_series),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium)
                }
            }
            Box(Modifier.size(28.dp).clip(CircleShape).padding(4.dp), contentAlignment = Alignment.Center) {
                Text("›", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun LoadingFooter(canLoadMore: () -> Boolean) {
    if (canLoadMore()) {
        PulsingBookLoader()
    }
}
