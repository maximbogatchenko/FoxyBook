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
import com.foxybook.app.domain.usecases.SearchBooksUseCase
import com.foxybook.app.domain.usecases.SearchByAuthorUseCase
import com.foxybook.app.domain.usecases.SearchByGenreUseCase
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

data class SearchState(
    val query: String = "",
    val selectedTab: SearchTab = SearchTab.ALL,
    val books: List<Book> = emptyList(),
    val authors: List<Author> = emptyList(),
    val series: List<Series> = emptyList(),
    val genreBooks: List<Book> = emptyList(),
    val isSearching: Boolean = false,
    val isSearchingAuthors: Boolean = false,
    val isSearchingSeries: Boolean = false,
    val isSearchingGenres: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val bookSource: BookSource = BookSource.FLIBUSTA
) {
    val hasQuery: Boolean get() = query.isNotBlank()
    val isEmpty: Boolean get() = isSearching.not() && hasQuery &&
        books.isEmpty() && authors.isEmpty() && series.isEmpty() && genreBooks.isEmpty() &&
        !isSearchingAuthors && !isSearchingSeries && !isSearchingGenres
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
    private val searchByGenreUseCase: SearchByGenreUseCase,
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
        val genreBooks: List<Book> = emptyList(),
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
    private var genreNextUrl: String? = null
    private var isLoadingBooks = false
    private var isLoadingAuthors = false
    private var isLoadingSeries = false
    private var isLoadingGenres = false
    private var genreExhausted = false  // true, когда все загрузили и отфильтровали

    fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.QueryChanged -> onQueryChanged(event.query)
            is SearchEvent.SearchRequested -> {
                _state.update { it.copy(isRefreshing = true) }
                performSearch()
            }
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
                genreBooks = emptyList(),
                isSearchingAuthors = false,
                isSearchingSeries = false,
                isSearchingGenres = false
            ) }
            genreNextUrl = null
            genreExhausted = false
            networkProvider.switchSource(source)
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
            delay(SEARCH_DEBOUNCE_MS)
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
                isSearchingGenres = !hasCache,
                error = null,
                books = cached?.books ?: emptyList(),
                authors = cached?.authors ?: emptyList(),
                series = cached?.series ?: emptyList(),
                genreBooks = cached?.genreBooks ?: emptyList()
            ) }

            // Books — fast
            val booksDeferred = async {
                try {
                    val bookPage = searchBooksUseCase(query, limit = SEARCH_PAGE_LIMIT)
                    booksNextUrl = bookPage.nextPageUrl
                    _state.update { it.copy(books = bookPage.items, isSearching = false, isRefreshing = false) }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.update { it.copy(isSearching = false, isRefreshing = false) }
                }
            }

            // Authors — fast
            val authorsDeferred = async {
                try {
                    val authorPage = searchByAuthorUseCase(query, limit = SEARCH_PAGE_LIMIT)
                    authorsNextUrl = authorPage.nextPageUrl
                    _state.update { it.copy(authors = authorPage.items, isSearchingAuthors = false) }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.update { it.copy(isSearchingAuthors = false) }
                }
            }

            // Series — slow, background
            val seriesDeferred = async {
                try {
                    val seriesPage = searchBySeriesUseCase(query, limit = SEARCH_PAGE_LIMIT)
                    seriesNextUrl = seriesPage.nextPageUrl
                    _state.update { it.copy(series = seriesPage.items, isSearchingSeries = false) }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.update { it.copy(isSearchingSeries = false) }
                }
            }

            // Genres — background
            val genreDeferred = async {
                try {
                    val genrePage = searchByGenreUseCase(query, limit = SEARCH_PAGE_LIMIT)
                    genreNextUrl = genrePage.nextPageUrl
                    android.util.Log.d("SearchGenre", "first page: ${genrePage.items.size} items, nextUrl=${genrePage.nextPageUrl}")
                    _state.update { it.copy(genreBooks = genrePage.items, isSearchingGenres = false) }
                } catch (e: Exception) {
                    android.util.Log.e("SearchGenre", "first page error", e)
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.update { it.copy(isSearchingGenres = false) }
                }
            }

            // Wait for books + authors (показываем результаты сразу)
            booksDeferred.await()
            authorsDeferred.await()

            // Дожидаемся серии и жанры перед сохранением кеша,
            // чтобы кеш содержал полные результаты всех типов
            seriesDeferred.await()
            genreDeferred.await()

            // Save to cache on success
            val s = _state.value
            if (s.error == null) {
                val anyResults = s.books.isNotEmpty() || s.authors.isNotEmpty() || s.series.isNotEmpty() || s.genreBooks.isNotEmpty()
                if (anyResults) {
                    saveSearchCache(query, source, SearchCacheEntry(
                        books = s.books,
                        authors = s.authors,
                        series = s.series,
                        genreBooks = s.genreBooks
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
            SearchTab.GENRES -> genreNextUrl
            SearchTab.ALL -> return
        } ?: return

        // Guard: already loading this type
        if (tab == SearchTab.BOOKS && isLoadingBooks) return
        if (tab == SearchTab.AUTHORS && isLoadingAuthors) return
        if (tab == SearchTab.SERIES && isLoadingSeries) return
        if (tab == SearchTab.GENRES && isLoadingGenres) return

        _state.update { it.copy(isLoadingMore = true) }
        when (tab) {
            SearchTab.BOOKS -> isLoadingBooks = true
            SearchTab.AUTHORS -> isLoadingAuthors = true
            SearchTab.SERIES -> isLoadingSeries = true
            SearchTab.GENRES -> isLoadingGenres = true
            else -> {}
        }

        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            try {
                when (tab) {
                    SearchTab.BOOKS -> {
                        val page = searchBooksUseCase.nextPage(url, limit = SEARCH_PAGE_LIMIT)
                        booksNextUrl = page.nextPageUrl
                        val existingIds = _state.value.books.map { it.id }.toSet()
                        val newItems = page.items.filter { it.id !in existingIds }
                        if (newItems.isNotEmpty()) {
                            _state.update { it.copy(books = it.books + newItems) }
                        } else {
                            booksNextUrl = null
                        }
                    }
                    SearchTab.AUTHORS -> {
                        val page = searchByAuthorUseCase.nextPage(url, limit = SEARCH_PAGE_LIMIT)
                        authorsNextUrl = page.nextPageUrl
                        val existingIds = _state.value.authors.map { it.authorId }.toSet()
                        val newItems = page.items.filter { it.authorId !in existingIds }
                        _state.update { it.copy(authors = it.authors + newItems) }
                    }
                    SearchTab.SERIES -> {
                        val page = searchBySeriesUseCase.nextPage(url, limit = SEARCH_PAGE_LIMIT)
                        seriesNextUrl = page.nextPageUrl
                        val existingIds = _state.value.series.map { it.seriesId }.toSet()
                        val newItems = page.items.filter { it.seriesId !in existingIds }
                        _state.update { it.copy(series = it.series + newItems) }
                    }
                    SearchTab.GENRES -> {
                        var currentUrl: String? = url
                        var skipCount = 0
                        while (currentUrl != null && skipCount < MAX_EMPTY_GENRE_PAGES) {
                            val page = searchByGenreUseCase.nextPage(currentUrl, limit = SEARCH_PAGE_LIMIT)
                            currentUrl = page.nextPageUrl
                            if (page.items.isNotEmpty()) {
                                genreNextUrl = currentUrl
                                _state.update { it.copy(genreBooks = it.genreBooks + page.items) }
                                break
                            }
                            // На странице нет книг нужного жанра — пробуем следующую
                            skipCount++
                        }
                        if (currentUrl == null) {
                            // Все страницы исчерпаны
                            genreExhausted = true
                            genreNextUrl = null
                        } else if (skipCount >= 5) {
                            // Пропустили 5 страниц подряд без совпадений
                            genreNextUrl = currentUrl
                        }
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
                    SearchTab.GENRES -> isLoadingGenres = false
                    else -> {}
                }
            }
        }
    }

    private fun resetPagination() {
        booksNextUrl = null
        authorsNextUrl = null
        seriesNextUrl = null
        // Не сбрасываем genreNextUrl — жанровый поиск асинхронный и часто отменяется
        // при повторном debounce. Если сбросить, старый nextUrl теряется навсегда.
        isLoadingBooks = false
        isLoadingAuthors = false
        isLoadingSeries = false
        isLoadingGenres = false
        genreExhausted = false
    }

    fun canLoadMore(tab: SearchTab): Boolean = when (tab) {
        SearchTab.BOOKS -> booksNextUrl != null && !isLoadingBooks
        SearchTab.AUTHORS -> authorsNextUrl != null && !isLoadingAuthors
        SearchTab.SERIES -> seriesNextUrl != null && !isLoadingSeries
        SearchTab.GENRES -> (genreNextUrl != null || isLoadingGenres) && !genreExhausted
        SearchTab.ALL -> false
    }

    override fun onCleared() {
        super.onCleared()
        debounceJob?.cancel()
        searchJob?.cancel()
        loadMoreJob?.cancel()
    }

    companion object {
        /** Задержка debounce перед поиском (мс) */
        const val SEARCH_DEBOUNCE_MS = 250L
        /** Количество элементов на страницу поиска */
        const val SEARCH_PAGE_LIMIT = 15
        /** Сколько пустых страниц жанров пропускать перед остановкой */
        const val MAX_EMPTY_GENRE_PAGES = 5
    }
}
