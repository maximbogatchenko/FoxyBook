package com.foxybook.app.features.newbooks

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxybook.app.core.datastore.DataStoreManager
import com.foxybook.app.core.models.Book
import com.foxybook.app.core.models.BookSource
import com.foxybook.app.core.network.OkHttpClientProvider
import com.foxybook.app.core.utils.ConnectivityObserver
import com.foxybook.app.domain.usecases.GetNewBooksUseCase
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.io.File

private const val TAG = "NEWBOOKS_VM"

sealed interface NewBooksUiState {
    data object Loading : NewBooksUiState
    data class Success(val books: List<Book>) : NewBooksUiState
    data class Error(val message: String) : NewBooksUiState
    data object Empty : NewBooksUiState
}

class NewBooksViewModel(
    private val getNewBooksUseCase: GetNewBooksUseCase,
    private val networkProvider: OkHttpClientProvider,
    private val dataStoreManager: DataStoreManager,
    private val application: Application,
    private val connectivityObserver: ConnectivityObserver
) : ViewModel() {

    private val _state = MutableStateFlow<NewBooksUiState>(NewBooksUiState.Loading)
    val state: StateFlow<NewBooksUiState> = _state.asStateFlow()

    private val _bookSource = MutableStateFlow(BookSource.FLIBUSTA)
    val bookSource: StateFlow<BookSource> = _bookSource.asStateFlow()

    private val _isGridMode = MutableStateFlow(false)
    val isGridMode: StateFlow<Boolean> = _isGridMode.asStateFlow()

    private var nextPageUrl: String? = null

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var pageCount = 0
    private val allBooks = mutableListOf<Book>()

    /** Старт загрузки: без него первый запрос из init и первый из подписки
     *  на источник уезжают одновременно и дважды качают первую страницу. */
    @Volatile
    private var loadJob: kotlinx.coroutines.Job? = null

    @Volatile
    private var loadMoreJob: kotlinx.coroutines.Job? = null

    companion object {
        private const val CACHE_FILE = "new_books_cache.json"
        /**
         * Таймаут первой страницы. Сетевой слой теперь отменяемый, поэтому
         * таймаут реально прерывает запрос; 8 с были фиктивными — запрос
         * продолжал висеть на IO-потоке до 30 с на зеркало.
         */
        private const val FIRST_PAGE_TIMEOUT_MS = 20_000L
        private const val NEXT_PAGE_TIMEOUT_MS = 25_000L
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val cacheFile: File get() = application.filesDir.resolve(CACHE_FILE)

    init {
        // Restore view mode from DataStore
        viewModelScope.launch {
            val saved = dataStoreManager.newBooksViewMode.first()
            _isGridMode.value = saved == "GRID"
        }

        // Observe source changes from DataStore (e.g. changed in Settings)
        viewModelScope.launch {
            dataStoreManager.bookSource.collect { source ->
                if (source != _bookSource.value) {
                    _bookSource.value = source
                    networkProvider.switchSource(source)
                    loadNewBooks(force = true)
                }
            }
        }

        // Show cached data immediately, then refresh in background
        val cached = loadCachedBooks()
        if (cached != null) {
            allBooks.addAll(cached)
            _state.value = NewBooksUiState.Success(cached)
        }
        loadNewBooks()
    }

    private fun loadCachedBooks(): List<Book>? {
        return try {
            if (!cacheFile.exists()) return null
            val text = cacheFile.readText()
            json.decodeFromString<List<Book>>(text)
        } catch (e: Exception) {
            Log.w(TAG, "loadCachedBooks | error", e)
            null
        }
    }

    private fun saveCachedBooks(books: List<Book>) {
        try {
            cacheFile.writeText(json.encodeToString(books))
            Log.d(TAG, "saveCachedBooks | saved ${books.size} books")
        } catch (e: Exception) {
            Log.w(TAG, "saveCachedBooks | error", e)
        }
    }

    fun toggleViewMode() {
        val newMode = !_isGridMode.value
        _isGridMode.value = newMode
        viewModelScope.launch {
            dataStoreManager.setNewBooksViewMode(if (newMode) "GRID" else "LIST")
        }
    }

    fun switchSource(source: BookSource) {
        if (source == _bookSource.value) return
        _bookSource.value = source
        networkProvider.switchSource(source)
        loadNewBooks(force = true)
    }

    fun loadNewBooks(force: Boolean = false) {
        if (loadJob?.isActive == true && !force) return
        loadJob?.cancel()
        // Догрузка страницы относится к предыдущей выдаче: оставленная
        // без отмены она дописала бы книги старого источника в очищенный
        // список и подменила бы nextPageUrl.
        loadMoreJob?.cancel()
        _isLoadingMore.value = false
        loadJob = viewModelScope.launch {
            val isOnline = connectivityObserver.isOnline.value
            val hasCache = cacheFile.exists()

            // Если нет кэша и нет сети — сразу показываем ошибку
            if (!hasCache && !isOnline) {
                _isRefreshing.value = false
                _state.value = NewBooksUiState.Error("Нет подключения к интернету")
                return@launch
            }

            // Если нет кэша — показываем Loading, иначе обновляем в фоне
            if (!hasCache) {
                _state.value = NewBooksUiState.Loading
            }
            allBooks.clear()
            nextPageUrl = null
            pageCount = 0
            try {
                val page = withTimeout(FIRST_PAGE_TIMEOUT_MS) { getNewBooksUseCase.firstPage() }
                allBooks.addAll(page.books)
                pageCount = 1
                nextPageUrl = page.nextPageUrl
                Log.d(TAG, "loadNewBooks | ${page.books.size} books, page=$pageCount, next=$nextPageUrl")
                val deduped = allBooks.distinctBy { it.id }
                Log.d(TAG, "loadNewBooks | deduped=${deduped.size}")
                _state.value = if (deduped.isEmpty()) NewBooksUiState.Empty
                else NewBooksUiState.Success(deduped)
                saveCachedBooks(deduped)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "loadNewBooks | timeout")
                if (!hasCache) {
                    _state.value = NewBooksUiState.Error("Сервер не отвечает")
                }
                // Если есть кэш — просто не обновляем, показываем кэшированные данные
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Отменено новым вызовом или уходом с экрана: состояние
                // выставляет уже он, ошибку показывать нельзя.
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "loadNewBooks | error", e)
                if (!hasCache) {
                    _state.value = NewBooksUiState.Error(e.message ?: "Ошибка загрузки")
                }
            } finally {
                // Спиннер гасим только если нас не вытеснил более свежий запрос
                if (isActive) _isRefreshing.value = false
            }
        }
    }

    fun loadMore() {
        val url = nextPageUrl ?: return
        if (_isLoadingMore.value) return
        _isLoadingMore.value = true
        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            try {
                val page = withTimeout(NEXT_PAGE_TIMEOUT_MS) { getNewBooksUseCase.nextPage(url) }
                allBooks.addAll(page.books)
                pageCount++
                nextPageUrl = page.nextPageUrl
                val deduped = allBooks.distinctBy { it.id }
                Log.d(TAG, "loadMore | +${page.books.size} books, page=$pageCount, next=$nextPageUrl, total=${deduped.size}")
                _state.value = NewBooksUiState.Success(deduped)
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.e(TAG, "loadMore | timeout", e)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Выдачу обновили или экран закрыли — состояние выставит тот, кто отменил
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "loadMore | error", e)
                nextPageUrl = null
            } finally {
                if (isActive) _isLoadingMore.value = false
            }
        }
    }

    fun canLoadMore(): Boolean = nextPageUrl != null && !_isLoadingMore.value

    fun refresh() {
        _isRefreshing.value = true
        loadNewBooks(force = true)
    }
}
