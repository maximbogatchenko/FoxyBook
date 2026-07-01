package com.foxybook.app.features.newbooks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foxybook.app.ui.components.BookCover
import com.foxybook.app.ui.components.PulsingBookLoader
import com.foxybook.app.core.models.Book
import com.foxybook.app.core.models.BookSource
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun NewBooksScreen(
    viewModel: NewBooksViewModel,
    onBookClick: (Book) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val bookSource by viewModel.bookSource.collectAsState()
    val isGridMode by viewModel.isGridMode.collectAsState()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // ── Header ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 20.dp, end = 4.dp, top = 16.dp, bottom = 12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Новинки", style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Свежие поступления", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { viewModel.toggleViewMode() }) {
                    Icon(
                        if (isGridMode) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = if (isGridMode) "Список" else "Сетка"
                    )
                }
            }
        }

        // ── Source Selector ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf(
                BookSource.FLIBUSTA to "Flibusta",
                BookSource.COOLLIB to "CoolLib",
                BookSource.FANTASY_WORLDS to "Fantasy"
            ).forEach { (source, label) ->
                val selected = bookSource == source
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.switchSource(source) },
                    label = {
                        Text(label, style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
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

        // ── Content (PullToRefreshBox оборачивает всё, включая Loading/Error) ──
        val isRefreshing by viewModel.isRefreshing.collectAsState()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            when (val s = state) {
                is NewBooksUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val infiniteTransition = rememberInfiniteTransition(label = "newbooks_loading")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 0.85f, targetValue = 1.15f,
                            animationSpec = infiniteRepeatable(tween(1200), repeatMode = RepeatMode.Reverse),
                            label = "scale"
                        )
                        val dots by infiniteTransition.animateFloat(
                            initialValue = 0f, targetValue = 4f,
                            animationSpec = infiniteRepeatable(tween(1400), repeatMode = RepeatMode.Restart),
                            label = "dots"
                        )

                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Whatshot,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp).scale(scale),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Spacer(Modifier.height(24.dp))
                            Text(
                                text = "Загружаем новинки" + ".".repeat(dots.toInt().coerceIn(0, 4)),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                is NewBooksUiState.Empty -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.NewReleases, null, modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Нет новинок", style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                is NewBooksUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Error, null, modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Ошибка", style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(s.message, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(onClick = { viewModel.refresh() }) { Text("Повторить") }
                        }
                    }
                }

                is NewBooksUiState.Success -> {
                    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
                    if (isGridMode) {
                        NewBooksGrid(
                            books = s.books,
                            onBookClick = onBookClick,
                            loadMore = { viewModel.loadMore() },
                            isLoadingMore = isLoadingMore,
                            canLoadMore = { viewModel.canLoadMore() }
                        )
                    } else {
                        NewBooksList(
                            books = s.books,
                            onBookClick = onBookClick,
                            loadMore = { viewModel.loadMore() },
                            isLoadingMore = isLoadingMore,
                            canLoadMore = { viewModel.canLoadMore() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingFooter(isLoadingMore: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isLoadingMore,
            enter = fadeIn(animationSpec = tween(400)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "loading_more")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "rotation"
                )

                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Внешний круг
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(48.dp)
                            .alpha(0.3f),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Внутренний вращающийся индикатор
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Загрузка книг...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun NewBooksList(
    books: List<Book>,
    onBookClick: (Book) -> Unit,
    loadMore: () -> Unit,
    isLoadingMore: Boolean,
    canLoadMore: () -> Boolean
) {
    val listState = rememberLazyListState()

    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            last.index >= listState.layoutInfo.totalItemsCount - 6
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { nearEnd }
            .distinctUntilChanged()
            .filter { it && canLoadMore() }
            .collect { loadMore() }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(books, key = { "nb_${it.id}" }) { book ->
            NewBookCard(book = book, onClick = { onBookClick(book) })
        }
        item(key = "loading_footer") {
            LoadingFooter(isLoadingMore)
        }
        item(key = "bottom_spacer") { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun NewBooksGrid(
    books: List<Book>,
    onBookClick: (Book) -> Unit,
    loadMore: () -> Unit,
    isLoadingMore: Boolean,
    canLoadMore: () -> Boolean
) {
    val gridState = rememberLazyGridState()

    val nearEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            last.index >= gridState.layoutInfo.totalItemsCount - 9
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { nearEnd }
            .distinctUntilChanged()
            .filter { it && canLoadMore() }
            .collect { loadMore() }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        gridItems(books, key = { "ng_${it.id}" }) { book ->
            NewBookGridCard(book = book, onClick = { onBookClick(book) })
        }
        item(key = "loading_footer") {
            LoadingFooter(isLoadingMore)
        }
        item(key = "bottom_spacer") { Spacer(Modifier.height(8.dp)) }
    }
}

// ═══════════════════════════════════════════════════════════════
//  List Card — оптимизирован для плавного скролла
// ═══════════════════════════════════════════════════════════════

@Composable
private fun NewBookCard(book: Book, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            // Cover — фиксированный размер, без лишних обёрток
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
//  Grid Card
// ═══════════════════════════════════════════════════════════════

@Composable
private fun NewBookGridCard(book: Book, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth()) {
            BookCover(
                coverUrl = book.coverUrl,
                title = book.title,
                author = book.author,
                contentDescription = book.title,
                width = 140.dp,
                height = 180.dp
            )
            Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp)) {
                Text(book.title, style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(book.author, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                if (book.genres.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(book.genres.first(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
