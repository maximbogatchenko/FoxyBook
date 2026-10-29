package com.foxybook.app.features.library

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
<<<<<<< HEAD
=======
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
>>>>>>> d24a20e (FoxyBook v1.9)
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.MenuBook
<<<<<<< HEAD
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewHeadline
=======
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
>>>>>>> d24a20e (FoxyBook v1.9)
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import com.foxybook.app.core.models.LibraryTab
<<<<<<< HEAD
=======
import com.foxybook.app.core.models.LibrarySortField
import com.foxybook.app.core.models.LibrarySortSpec
>>>>>>> d24a20e (FoxyBook v1.9)
import com.foxybook.app.ui.components.CoverViewer

import androidx.compose.ui.res.stringResource
import com.foxybook.app.R
import com.foxybook.app.core.models.LibraryBook


// ─────────────────────────────────────────────────────────────────
//  Main Library Screen
// ─────────────────────────────────────────────────────────────────
<<<<<<< HEAD
=======

/**
 * Ключ содержимого библиотеки. Включает настройку сортировки, чтобы смена
 * поля/направления/группировки гарантированно перестраивала список.
 */
private data class ContentKey(
    val tab: LibraryTab,
    val collectionId: String?,
    val sortSpec: LibrarySortSpec
)

private fun ContentKey(state: LibraryState) = ContentKey(state.currentTab, state.selectedCollectionId, state.sortSpec)
>>>>>>> d24a20e (FoxyBook v1.9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onBookClick: (String, Int, String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    var coverViewerUrl by remember { mutableStateOf<String?>(null) }
    var selectedBookForDetails by remember { mutableStateOf<LibraryBook?>(null) }
    var showViewModeMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            Log.d("LibraryScreen", "Importing ${uris.size} books: $uris")
            uris.forEach { uri -> viewModel.importBook(uri, context) }
        }
    }

    LaunchedEffect(state.importError) {
        state.importError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissImportError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isImporting) ImportingOverlay()
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            LibraryTopBar(
                isSelectionMode = state.isSelectionMode,
                selectedCount = state.selectedBookIds.size,
                viewMode = state.viewMode,
<<<<<<< HEAD
=======
                sortSpec = state.sortSpec,
>>>>>>> d24a20e (FoxyBook v1.9)
                showViewModeMenu = showViewModeMenu,
                onToggleViewModeMenu = { showViewModeMenu = !showViewModeMenu },
                onDismissViewModeMenu = { showViewModeMenu = false },
                onChangeViewMode = { viewModel.onEvent(LibraryEvent.ChangeViewMode(it)) },
<<<<<<< HEAD
=======
                showSortMenu = showSortMenu,
                onToggleSortMenu = { showSortMenu = !showSortMenu },
                onDismissSortMenu = { showSortMenu = false },
                onChangeSortField = { viewModel.onEvent(LibraryEvent.ChangeSort(it)) },
                onToggleSortDirection = { viewModel.onEvent(LibraryEvent.ToggleSortDirection) },
                onToggleGroupByStatus = { viewModel.onEvent(LibraryEvent.ToggleGroupByStatus) },
>>>>>>> d24a20e (FoxyBook v1.9)
                onClearSelection = { viewModel.onEvent(LibraryEvent.ClearSelection) },
                onSelectAll = { viewModel.onEvent(LibraryEvent.SelectAll) },
                onBatchFavorite = { viewModel.onEvent(LibraryEvent.BatchToggleFavorite) },
                onBatchCollection = { viewModel.onEvent(LibraryEvent.BatchShowCollectionDialog) },
                onBatchDelete = { viewModel.onEvent(LibraryEvent.BatchDeleteSelected) },
                onImportBook = { importLauncher.launch(arrayOf("application/epub+zip", "application/x-fictionbook+xml", "application/x-mobipocket-ebook", "application/octet-stream", "text/plain")) },
                onCreateCollection = { viewModel.onEvent(LibraryEvent.CreateCollectionClicked) },
                currentTab = state.currentTab,
                isLandscape = isLandscape
            )
            LibraryTabRow(selectedTab = state.currentTab, onTabSelected = { viewModel.onEvent(LibraryEvent.TabSelected(it)) })
            Spacer(modifier = Modifier.height(if (isLandscape) 2.dp else 6.dp))

            var swipeOffset by remember { mutableStateOf(0f) }
            val swipeThreshold = with(LocalDensity.current) { 80.dp.toPx() }
            Box(modifier = Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (swipeOffset < -swipeThreshold) {
                            val next = (state.currentTab.ordinal + 1).coerceAtMost(LibraryTab.entries.size - 1)
                            viewModel.onEvent(LibraryEvent.TabSelected(LibraryTab.entries[next]))
                        } else if (swipeOffset > swipeThreshold) {
                            val prev = (state.currentTab.ordinal - 1).coerceAtLeast(0)
                            viewModel.onEvent(LibraryEvent.TabSelected(LibraryTab.entries[prev]))
                        }
                        swipeOffset = 0f
                    },
                    onHorizontalDrag = { _, dragAmount -> swipeOffset += dragAmount }
                )
            }) {
<<<<<<< HEAD
                AnimatedContent(targetState = state.currentTab to state.selectedCollectionId, transitionSpec = {
                    val dir = if (targetState.first.ordinal > initialState.first.ordinal) 1 else -1
                    (slideInHorizontally(tween(250)) { it * dir } + fadeIn(tween(250))).togetherWith(slideOutHorizontally(tween(250)) { -it * dir } + fadeOut(tween(200)))
                }, label = "library_content") { (tab, _) ->
                    when (tab) {
                        LibraryTab.ALL, LibraryTab.FAVORITES, LibraryTab.HISTORY -> BookListContent(
                            books = when (tab) { LibraryTab.FAVORITES -> state.favoriteBooks; LibraryTab.HISTORY -> state.historyBooks; else -> state.allBooks },
=======
                // Настройку сортировки обязательно включаем в targetState: AnimatedContent
                // пересобирает содержимое только при смене targetState, иначе новая
                // сортировка осталась бы незамеченной и список не перестроился бы.
                AnimatedContent(targetState = ContentKey(state), transitionSpec = {
                    if (initialState.tab == targetState.tab) {
                        // Вкладка та же — перестроили только порядок книг: без анимации.
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        val dir = if (targetState.tab.ordinal > initialState.tab.ordinal) 1 else -1
                        (slideInHorizontally(tween(250)) { it * dir } + fadeIn(tween(250))).togetherWith(slideOutHorizontally(tween(250)) { -it * dir } + fadeOut(tween(200)))
                    }
                }, label = "library_content") { key ->
                    val (tab, collectionId) = key
                    when (tab) {
                        LibraryTab.ALL, LibraryTab.FAVORITES, LibraryTab.HISTORY -> BookListContent(
                            groups = state.groupsForTab(tab),
>>>>>>> d24a20e (FoxyBook v1.9)
                            viewMode = state.viewMode,
                            emptyTitle = stringResource(when (tab) { LibraryTab.FAVORITES -> R.string.library_empty_favorites_title; LibraryTab.HISTORY -> R.string.library_empty_history_title; else -> R.string.library_empty_title }),
                            emptySubtitle = stringResource(when (tab) { LibraryTab.FAVORITES -> R.string.library_empty_favorites_subtitle; LibraryTab.HISTORY -> R.string.library_empty_history_subtitle; else -> R.string.library_empty_subtitle }),
                            selectedBookIds = state.selectedBookIds, isSelectionMode = state.isSelectionMode,
                            onBookClick = onBookClick,
                            onToggleFavorite = { id, fmt -> viewModel.onEvent(LibraryEvent.ToggleFavorite(id, fmt)) },
                            onMoveBook = { id, fmt -> viewModel.onEvent(LibraryEvent.MoveBookClicked(id, fmt)) },
                            onDeleteBook = { id, fmt -> viewModel.onEvent(LibraryEvent.DeleteBook(id, fmt)) },
                            onCoverClick = { url -> coverViewerUrl = url },
                            onBookDetails = { book -> selectedBookForDetails = book },
                            onSetReadState = { id, fmt, readState -> viewModel.onEvent(LibraryEvent.SetReadState(id, fmt, readState)) },
                            onLongPressBook = { id, fmt -> viewModel.onEvent(LibraryEvent.LongPressBook(id, fmt)) },
                            onToggleSelection = { id, fmt -> viewModel.onEvent(LibraryEvent.ToggleBookSelection(id, fmt)) }
                        )
                        LibraryTab.COLLECTIONS -> CollectionsContent(
                            collections = state.collections, selectedCollectionId = state.selectedCollectionId,
                            books = state.allBooks, viewMode = state.viewMode,
<<<<<<< HEAD
=======
                            groups = state.groupsForCollection(collectionId),
>>>>>>> d24a20e (FoxyBook v1.9)
                            selectedBookIds = state.selectedBookIds, isSelectionMode = state.isSelectionMode,
                            onCollectionClick = { viewModel.onEvent(LibraryEvent.CollectionSelected(it)) },
                            onRenameCollection = { viewModel.onEvent(LibraryEvent.RenameCollectionClicked(it)) },
                            onDeleteCollection = { viewModel.onEvent(LibraryEvent.DeleteCollection(it)) },
                            onBookClick = onBookClick,
                            onToggleFavorite = { id, fmt -> viewModel.onEvent(LibraryEvent.ToggleFavorite(id, fmt)) },
                            onMoveBook = { id, fmt -> viewModel.onEvent(LibraryEvent.MoveBookClicked(id, fmt)) },
                            onDeleteBook = { id, fmt -> viewModel.onEvent(LibraryEvent.DeleteBook(id, fmt)) },
                            onCoverClick = { url -> coverViewerUrl = url },
                            onBookDetails = { book -> selectedBookForDetails = book },
                            onCreateCollection = { viewModel.onEvent(LibraryEvent.CreateCollectionClicked) },
<<<<<<< HEAD
=======
                            onSetReadState = { id, fmt, readState -> viewModel.onEvent(LibraryEvent.SetReadState(id, fmt, readState)) },
>>>>>>> d24a20e (FoxyBook v1.9)
                            onLongPressBook = { id, fmt -> viewModel.onEvent(LibraryEvent.LongPressBook(id, fmt)) },
                            onToggleSelection = { id, fmt -> viewModel.onEvent(LibraryEvent.ToggleBookSelection(id, fmt)) }
                        )
                    }
                }
            }
        }
        selectedBookForDetails?.let { book ->
            BookDetailsDialog(book = book, onDismiss = { selectedBookForDetails = null },
                onOpenCover = { url -> coverViewerUrl = url; selectedBookForDetails = null },
                onRead = { onBookClick(book.filePath, book.id, book.format) })
        }
        LibraryDialogsHost(state = state, viewModel = viewModel)
        if (coverViewerUrl != null) CoverViewer(coverUrl = coverViewerUrl!!, onDismiss = { coverViewerUrl = null })
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

// ─────────────────────────────────────────────────────────────────
//  Importing overlay
// ─────────────────────────────────────────────────────────────────

@Composable
private fun ImportingOverlay() {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)).clickable(enabled = false, onClick = {}),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(12.dp))
            Text(stringResource(R.string.library_import_book), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  Top App Bar
// ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopBar(
    isSelectionMode: Boolean, selectedCount: Int, viewMode: LibraryViewMode,
<<<<<<< HEAD
=======
    sortSpec: LibrarySortSpec = LibrarySortSpec.DEFAULT,
>>>>>>> d24a20e (FoxyBook v1.9)
    showViewModeMenu: Boolean, onToggleViewModeMenu: () -> Unit, onDismissViewModeMenu: () -> Unit,
    onChangeViewMode: (LibraryViewMode) -> Unit, onClearSelection: () -> Unit, onSelectAll: () -> Unit,
    onBatchFavorite: () -> Unit, onBatchCollection: () -> Unit, onBatchDelete: () -> Unit,
    onImportBook: () -> Unit, onCreateCollection: () -> Unit, currentTab: LibraryTab,
<<<<<<< HEAD
=======
    showSortMenu: Boolean = false, onToggleSortMenu: () -> Unit = {}, onDismissSortMenu: () -> Unit = {},
    onChangeSortField: (LibrarySortField) -> Unit = {}, onToggleSortDirection: () -> Unit = {},
    onToggleGroupByStatus: () -> Unit = {},
>>>>>>> d24a20e (FoxyBook v1.9)
    isLandscape: Boolean = false
) {
    if (isSelectionMode) {
        TopAppBar(
            title = { Text(stringResource(R.string.library_selected_count, selectedCount), fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onClearSelection) { Icon(Icons.Default.Close, stringResource(R.string.cd_cancel)) } },
            actions = {
                IconButton(onClick = onSelectAll) { Icon(Icons.Default.CheckCircle, stringResource(R.string.library_select_all)) }
                IconButton(onClick = onBatchFavorite) { Icon(Icons.Default.Favorite, stringResource(R.string.cd_add_to_favorites), tint = MaterialTheme.colorScheme.error) }
                IconButton(onClick = onBatchCollection) { Icon(Icons.Default.Folder, stringResource(R.string.cd_add_to_collection)) }
                IconButton(onClick = onBatchDelete) { Icon(Icons.Default.Delete, stringResource(R.string.cd_delete), tint = MaterialTheme.colorScheme.error) }
            },
            windowInsets = WindowInsets(0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )
    } else {
        TopAppBar(
            title = {
                Text(
                    stringResource(R.string.library_title),
                    fontWeight = FontWeight.Bold,
                    style = if (isLandscape) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall
                )
            },
            actions = {
                Box {
<<<<<<< HEAD
=======
                    IconButton(onClick = onToggleSortMenu) {
                        Icon(
                            Icons.Default.SwapVert,
                            stringResource(R.string.library_sort),
                            tint = if (sortSpec.groupByStatus || sortSpec.field != LibrarySortSpec.DEFAULT.field)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LibrarySortMenu(
                        expanded = showSortMenu,
                        spec = sortSpec,
                        onDismiss = onDismissSortMenu,
                        onChangeField = onChangeSortField,
                        onToggleDirection = onToggleSortDirection,
                        onToggleGroupByStatus = onToggleGroupByStatus
                    )
                }
                Box {
>>>>>>> d24a20e (FoxyBook v1.9)
                    IconButton(onClick = onToggleViewModeMenu) {
                        Icon(when (viewMode) { LibraryViewMode.LIST -> Icons.Default.ViewHeadline; LibraryViewMode.COMPACT -> Icons.Default.ViewAgenda; LibraryViewMode.GRID -> Icons.Default.GridView }, stringResource(R.string.cd_view_mode))
                    }
                    DropdownMenu(expanded = showViewModeMenu, onDismissRequest = onDismissViewModeMenu) {
                        LibraryViewMode.entries.forEach { mode ->
                            DropdownMenuItem(text = { Text(when (mode) { LibraryViewMode.LIST -> stringResource(R.string.library_view_list); LibraryViewMode.COMPACT -> stringResource(R.string.library_view_compact); LibraryViewMode.GRID -> stringResource(R.string.library_view_grid) }, fontWeight = if (viewMode == mode) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { onDismissViewModeMenu(); onChangeViewMode(mode) },
                                leadingIcon = { Icon(when (mode) { LibraryViewMode.LIST -> Icons.Default.ViewHeadline; LibraryViewMode.COMPACT -> Icons.Default.ViewAgenda; LibraryViewMode.GRID -> Icons.Default.GridView }, null, modifier = Modifier.size(18.dp)) })
                        }
                    }
                }
                IconButton(onClick = onImportBook) { Icon(Icons.Default.Add, stringResource(R.string.library_import_book)) }
                if (currentTab == LibraryTab.COLLECTIONS) {
                    IconButton(onClick = onCreateCollection) { Icon(Icons.Default.CreateNewFolder, stringResource(R.string.collection_create)) }
                }
            },
            windowInsets = WindowInsets(0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )
    }
}

// ─────────────────────────────────────────────────────────────────
<<<<<<< HEAD
//  Tab Row
// ─────────────────────────────────────────────────────────────────

@Composable
=======
//  Sort menu
// ─────────────────────────────────────────────────────────────────

@Composable
private fun LibrarySortMenu(
    expanded: Boolean,
    spec: LibrarySortSpec,
    onDismiss: () -> Unit,
    onChangeField: (LibrarySortField) -> Unit,
    onToggleDirection: () -> Unit,
    onToggleGroupByStatus: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.library_sort),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LibrarySortField.entries.forEach { field ->
            val selected = spec.field == field
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(sortFieldLabel(field)),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                // Меню закрываем сразу: иначе попап перекрывает пересортированный
                // список и пользователь не видит результат выбора.
                onClick = { onDismiss(); onChangeField(field) },
                leadingIcon = if (selected) {
                    { Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) }
                } else null
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = {
                Text(
                    if (spec.descending) stringResource(R.string.library_sort_desc)
                    else stringResource(R.string.library_sort_asc)
                )
            },
            onClick = { onDismiss(); onToggleDirection() },
            leadingIcon = {
                Icon(
                    if (spec.descending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    null,
                    modifier = Modifier.size(18.dp)
                )
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.library_sort_group_status)) },
            onClick = { onDismiss(); onToggleGroupByStatus() },
            leadingIcon = {
                Icon(
                    if (spec.groupByStatus) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    null,
                    modifier = Modifier.size(18.dp)
                )
            }
        )
    }
}

private fun sortFieldLabel(field: LibrarySortField): Int = when (field) {
    LibrarySortField.DATE_ADDED -> R.string.library_sort_date_added
    LibrarySortField.LAST_READ -> R.string.library_sort_last_read
    LibrarySortField.TITLE -> R.string.library_sort_title
    LibrarySortField.AUTHOR -> R.string.library_sort_author
    LibrarySortField.PROGRESS -> R.string.library_sort_progress
}

// ─────────────────────────────────────────────────────────────────
//  Tab Row
// ─────────────────────────────────────────────────────────────────

@Composable
>>>>>>> d24a20e (FoxyBook v1.9)
private fun LibraryTabRow(selectedTab: LibraryTab, onTabSelected: (LibraryTab) -> Unit) {
    PrimaryTabRow(selectedTabIndex = selectedTab.ordinal, containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.primary) {
        LibraryTab.entries.forEach { tab ->
            Tab(selected = selectedTab == tab, onClick = { onTabSelected(tab) },
                text = { Text(libraryTabLabel(tab), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                icon = { Icon(when (tab) { LibraryTab.ALL -> Icons.AutoMirrored.Filled.MenuBook; LibraryTab.FAVORITES -> Icons.Default.Favorite; LibraryTab.HISTORY -> Icons.Default.History; LibraryTab.COLLECTIONS -> Icons.Default.Folder }, null, modifier = Modifier.size(20.dp)) })
        }
    }
}

@Composable
private fun libraryTabLabel(tab: LibraryTab): String = when (tab) {
    LibraryTab.ALL -> stringResource(R.string.library_tab_all)
    LibraryTab.FAVORITES -> stringResource(R.string.library_tab_favorites)
    LibraryTab.HISTORY -> stringResource(R.string.library_tab_history)
    LibraryTab.COLLECTIONS -> stringResource(R.string.library_tab_collections)
}
