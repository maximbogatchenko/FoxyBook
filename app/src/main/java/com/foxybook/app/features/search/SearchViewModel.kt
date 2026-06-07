package com.foxybook.app.features.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxybook.app.core.models.Book
import com.foxybook.app.core.models.SearchMode
import com.foxybook.app.core.models.SearchUiState
import com.foxybook.app.core.models.Series
import com.foxybook.app.domain.usecases.SearchBooksUseCase
import com.foxybook.app.domain.usecases.SearchByAuthorUseCase
import com.foxybook.app.domain.usecases.SearchBySeriesUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchState(
    val query: String = "",
    val searchMode: SearchMode = SearchMode.TITLE,
    val uiState: SearchUiState = SearchUiState.Idle
)

sealed interface SearchEvent {
    data class QueryChanged(val query: String) : SearchEvent
    data class ModeChanged(val mode: SearchMode) : SearchEvent
    data object SearchRequested : SearchEvent
}

class SearchViewModel(
    private val searchBooksUseCase: SearchBooksUseCase,
    private val searchByAuthorUseCase: SearchByAuthorUseCase,
    private val searchBySeriesUseCase: SearchBySeriesUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var debounceJob: Job? = null

    fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.QueryChanged -> onQueryChanged(event.query)
            is SearchEvent.ModeChanged -> onModeChanged(event.mode)
            is SearchEvent.SearchRequested -> performSearch()
        }
    }

    private fun onQueryChanged(query: String) {
        _state.update { it.copy(query = query) }
        debounceJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(uiState = SearchUiState.Idle) }
            return
        }
        debounceJob = viewModelScope.launch {
            delay(500)
            performSearch()
        }
    }

    private fun onModeChanged(mode: SearchMode) {
        _state.update { it.copy(searchMode = mode, uiState = SearchUiState.Idle) }
        val query = _state.value.query
        if (query.isNotBlank()) {
            debounceJob?.cancel()
            debounceJob = viewModelScope.launch {
                delay(300)
                performSearch()
            }
        }
    }

    private fun performSearch() {
        val query = _state.value.query.trim()
        if (query.isBlank()) return

        val mode = _state.value.searchMode
        viewModelScope.launch {
            _state.update { it.copy(uiState = SearchUiState.Loading) }
            try {
                when (mode) {
                    SearchMode.TITLE -> {
                        val books: List<Book> = searchBooksUseCase(query)
                        _state.update {
                            it.copy(
                                uiState = if (books.isEmpty()) SearchUiState.Empty
                                else SearchUiState.BookSuccess(books)
                            )
                        }
                    }
                    SearchMode.AUTHOR -> {
                        val books: List<Book> = searchByAuthorUseCase(query)
                        _state.update {
                            it.copy(
                                uiState = if (books.isEmpty()) SearchUiState.Empty
                                else SearchUiState.BookSuccess(books)
                            )
                        }
                    }
                    SearchMode.SERIES -> {
                        val series: List<Series> = searchBySeriesUseCase(query)
                        _state.update {
                            it.copy(
                                uiState = if (series.isEmpty()) SearchUiState.Empty
                                else SearchUiState.SeriesSuccess(series)
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(uiState = SearchUiState.Error(e.message ?: "Unknown error")) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        debounceJob?.cancel()
    }
}
