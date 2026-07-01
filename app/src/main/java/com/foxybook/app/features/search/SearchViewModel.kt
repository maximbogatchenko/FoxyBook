package com.foxybook.app.features.search

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxybook.app.core.datastore.DataStoreManager
import com.foxybook.app.core.models.Author
import com.foxybook.app.core.models.Book
import com.foxybook.app.core.models.BookSource
import com.foxybook.app.core.models.SearchPage
import com.foxybook.app.core.models.SearchTab
import com.foxybook.app.core.models.Series
import com.foxybook.app.core.network.OkHttpClientProvider
import com.foxybook.app.domain.usecases.GetBookInfoUseCase
import com.foxybook.app.domain.usecases.SearchBooksUseCase
import com.foxybook.app.domain.usecases.SearchByAuthorUseCase
import com.foxybook.app.domain.usecases.SearchBySeriesUseCase
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class SearchState(
    val query: String = "",
    val selectedTab: SearchTab = SearchTab.ALL,
    val books: List<Book> = emptyList(),
    val authors: List<Author> = emptyList(),
    val series: List<Series> = emptyList(),
    val isSearching: Boolean = false,
    val isSearchingAuthors: Boolean = false,
    val isSearchingSeries: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val bookSource: BookSource = BookSource.FLIBUSTA
) {
    val hasQuery: Boolean get() = query.isNotBlank()
    val isEmpty: Boolean get() = isSearching.not() && hasQuery &&
        books.isEmpty() && authors.isEmpty() && series.isEmpty() &&
        !isSearchingAuthors && !isSearchingSeries
}

sealed interface SearchEvent {
    data class QueryChanged(val query: String) : SearchEvent
    data object SearchRequested : SearchEvent
    data class TabSelected(val tab: SearchTab) : SearchEvent
    data class LoadMore(val tab: SearchTab) : SearchEvent
    data class SourceChanged(val source: BookSource) : SearchEvent
}

class SearchViewModel(
    private val searchBooksUseCase: SearchBooksUseCase,
    private val searchByAuthorUseCase: SearchByAuthorUseCase,
    private val searchBySeriesUseCase: SearchBySeriesUseCase,
    private val getBookInfoUseCase: GetBookInfoUseCase,
    private val dataStoreManager: DataStoreManager,
    private val networkProvider: OkHttpClientProvider,
    private val application: Application
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class SearchCacheEntry(
        val books: List<Book> = emptyList(),
        val authors: List<Author> = emptyList(),
        val series: List<Series> = emptyList(),
        val cachedAt: Long = System.currentTimeMillis()
    )

    private val cacheFile: File get() = File(application.filesDir, "cache/searches.json")

    private fun loadSearchCache(query: String, source: BookSource): SearchCacheEntry? {
        val key = "${query.lowercase().trim()}|${source.name}"
        return try {
            if (!cacheFile.exists()) return null
            @Suppress("UNCHECKED_CAST")
            val map = json.decodeFromString<Map<String, SearchCacheEntry>>(cacheFile.readText())
            map[key]
        } catch (e: Exception) {
            null
        }
    }

    private fun saveSearchCache(query: String, source: BookSource, entry: SearchCacheEntry) {
        val key = "${query.lowercase().trim()}|${source.name}"
        try {
            cacheFile.parentFile?.mkdirs()
            val map = if (cacheFile.exists()) {
                try { json.decodeFromString<MutableMap<String, SearchCacheEntry>>(cacheFile.readText()) }
                catch (_: Exception) { mutableMapOf() }
            } else mutableMapOf()
            map[key] = entry
            cacheFile.writeText(json.encodeToString(map))
        } catch (e: Exception) { /* ignore cache errors */ }
    }

    init {
        viewModelScope.launch {
            dataStoreManager.bookSource.collect { source ->
                if (source != _state.value.bookSource) {
                    _state.update { it.copy(bookSource = source) }
                    networkProvider.switchSource(source)
                    resetPagination()
                    if (_state.value.query.isNotBlank()) performSearch()
                }
            }
        }
    }

    private var debounceJob: Job? = null
    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

    // Per-type pagination state
    private var booksNextUrl: String? = null
    private var authorsNextUrl: String? = null
    private var seriesNextUrl: String? = null
    private var isLoadingBooks = false
    private var isLoadingAuthors = false
    private var isLoadingSeries = false

    fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.QueryChanged -> onQueryChanged(event.query)
            is SearchEvent.SearchRequested -> performSearch()
            is SearchEvent.TabSelected -> _state.update { it.copy(selectedTab = event.tab) }
            is SearchEvent.LoadMore -> loadMore(event.tab)
            is SearchEvent.SourceChanged -> switchSource(event.source)
        }
    }

    private fun switchSource(source: BookSource) {
        viewModelScope.launch {
            com.foxybook.app.core.models.BookCache.clear()
            _state.update { it.copy(
                bookSource = source,
                books = emptyList(),
                authors = emptyList(),
                series = emptyList(),
                isSearchingAuthors = false,
                isSearchingSeries = false
            ) }
            networkProvider.switchSource(source)
            dataStoreManager.setBookSource(source)
            resetPagination()
            val q = _state.value.query
            if (q.isNotBlank()) performSearch()
        }
    }

    private fun onQueryChanged(query: String) {
        _state.update { it.copy(query = query, error = null) }
        debounceJob?.cancel()
        if (query.isBlank()) {
            searchJob?.cancel()
            _state.update { SearchState() }
            return
        }
        debounceJob = viewModelScope.launch {
            delay(250)
            performSearch()
        }
    }

    private fun performSearch() {
        val query = _state.value.query.trim()
        if (query.isBlank()) return

        searchJob?.cancel()
        resetPagination()

        searchJob = viewModelScope.launch {
            // Try cache first
            val source = _state.value.bookSource
            val cached = loadSearchCache(query, source)
            val hasCache = cached != null

            _state.update { it.copy(
                isSearching = !hasCache,
                isSearchingAuthors = !hasCache,
                isSearchingSeries = !hasCache,
                error = null,
                books = cached?.books ?: emptyList(),
                authors = cached?.authors ?: emptyList(),
                series = cached?.series ?: emptyList()
            ) }

            // Books — fast
            val booksDeferred = async {
                try {
                    val bookPage = searchBooksUseCase(query)
                    booksNextUrl = bookPage.nextPageUrl
                    _state.update { it.copy(books = bookPage.items, isSearching = false) }
                    fetchCovers(bookPage.items)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.update { it.copy(isSearching = false) }
                }
            }

            // Authors — fast
            val authorsDeferred = async {
                try {
                    val authorPage = searchByAuthorUseCase(query)
                    authorsNextUrl = authorPage.nextPageUrl
                    _state.update { it.copy(authors = authorPage.items, isSearchingAuthors = false) }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.update { it.copy(isSearchingAuthors = false) }
                }
            }

            // Series — slow, background
            async {
                try {
                    val seriesPage = searchBySeriesUseCase(query)
                    seriesNextUrl = seriesPage.nextPageUrl
                    _state.update { it.copy(series = seriesPage.items, isSearchingSeries = false) }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.update { it.copy(isSearchingSeries = false) }
                }
            }

            // Wait for books + authors
            booksDeferred.await()
            authorsDeferred.await()

            // Save to cache on success
            val s = _state.value
            if (!s.isSearching && !s.isSearchingAuthors && !s.isSearchingSeries && s.error == null) {
                val anyResults = s.books.isNotEmpty() || s.authors.isNotEmpty() || s.series.isNotEmpty()
                if (anyResults) {
                    saveSearchCache(query, source, SearchCacheEntry(
                        books = s.books,
                        authors = s.authors,
                        series = s.series
                    ))
                }
            }
        }
    }

    private fun loadMore(tab: SearchTab) {
        if (_state.value.isLoadingMore) return
        val url = when (tab) {
            SearchTab.BOOKS -> booksNextUrl
            SearchTab.AUTHORS -> authorsNextUrl
            SearchTab.SERIES -> seriesNextUrl
            SearchTab.ALL -> return
        } ?: return

        // Guard: already loading this type
        if (tab == SearchTab.BOOKS && isLoadingBooks) return
        if (tab == SearchTab.AUTHORS && isLoadingAuthors) return
        if (tab == SearchTab.SERIES && isLoadingSeries) return

        _state.update { it.copy(isLoadingMore = true) }
        when (tab) {
            SearchTab.BOOKS -> isLoadingBooks = true
            SearchTab.AUTHORS -> isLoadingAuthors = true
            SearchTab.SERIES -> isLoadingSeries = true
            else -> {}
        }

        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            try {
                when (tab) {
                    SearchTab.BOOKS -> {
                        val page = searchBooksUseCase.nextPage(url)
                        booksNextUrl = page.nextPageUrl
                        _state.update { it.copy(books = it.books + page.items) }
                        fetchCovers(page.items)
                    }
                    SearchTab.AUTHORS -> {
                        val page = searchByAuthorUseCase.nextPage(url)
                        authorsNextUrl = page.nextPageUrl
                        _state.update { it.copy(authors = it.authors + page.items) }
                    }
                    SearchTab.SERIES -> {
                        val page = searchBySeriesUseCase.nextPage(url)
                        seriesNextUrl = page.nextPageUrl
                        _state.update { it.copy(series = it.series + page.items) }
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            } finally {
                _state.update { it.copy(isLoadingMore = false) }
                when (tab) {
                    SearchTab.BOOKS -> isLoadingBooks = false
                    SearchTab.AUTHORS -> isLoadingAuthors = false
                    SearchTab.SERIES -> isLoadingSeries = false
                    else -> {}
                }
            }
        }
    }

    private fun resetPagination() {
        booksNextUrl = null
        authorsNextUrl = null
        seriesNextUrl = null
        isLoadingBooks = false
        isLoadingAuthors = false
        isLoadingSeries = false
    }

    fun canLoadMore(tab: SearchTab): Boolean = when (tab) {
        SearchTab.BOOKS -> booksNextUrl != null && !isLoadingBooks
        SearchTab.AUTHORS -> authorsNextUrl != null && !isLoadingAuthors
        SearchTab.SERIES -> seriesNextUrl != null && !isLoadingSeries
        SearchTab.ALL -> false
    }

    private suspend fun fetchCovers(books: List<Book>) {
        val needing = books.filter { it.coverUrl.isBlank() }
        if (needing.isEmpty()) return
        // Ограничиваем конкурентные запросы до 4, чтобы не флудить сеть
        val semaphore = Semaphore(4)
        val urls = supervisorScope {
            needing.map { book ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        try {
                            val info = getBookInfoUseCase(book.id)
                            if (info != null && info.coverUrl.isNotBlank()) book.id to info.coverUrl else null
                        } catch (_: Exception) { null }
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }
        if (urls.isEmpty()) return
        _state.update { s ->
            s.copy(books = s.books.map { b -> urls[b.id]?.let { b.copy(coverUrl = it) } ?: b })
        }
    }

    override fun onCleared() {
        super.onCleared()
        debounceJob?.cancel()
        searchJob?.cancel()
        loadMoreJob?.cancel()
    }
}
