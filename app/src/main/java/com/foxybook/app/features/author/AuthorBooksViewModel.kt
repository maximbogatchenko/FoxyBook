package com.foxybook.app.features.author

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxybook.app.core.models.Book
import com.foxybook.app.domain.usecases.GetAuthorBooksUseCase
import com.foxybook.app.domain.usecases.GetBookInfoUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

sealed interface AuthorBooksUiState {
    data object Loading : AuthorBooksUiState
    data class Success(val books: List<Book>) : AuthorBooksUiState
    data class Error(val message: String) : AuthorBooksUiState
}

class AuthorBooksViewModel(
    private val getAuthorBooksUseCase: GetAuthorBooksUseCase,
    private val getBookInfoUseCase: GetBookInfoUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<AuthorBooksUiState>(AuthorBooksUiState.Loading)
    val state: StateFlow<AuthorBooksUiState> = _state.asStateFlow()

    fun loadAuthorBooks(authorId: String, authorName: String) {
        viewModelScope.launch {
            _state.value = AuthorBooksUiState.Loading

            try {
                val books = getAuthorBooksUseCase(authorId)

                if (books.isEmpty()) {
                    _state.value = AuthorBooksUiState.Error("Не удалось загрузить книги автора")
                } else {
                    _state.value = AuthorBooksUiState.Success(books)
                    fetchCovers(books)
                }
            } catch (e: Exception) {
                _state.value = AuthorBooksUiState.Error(e.message ?: "Ошибка загрузки")
            }
        }
    }

    private suspend fun fetchCovers(books: List<Book>) {
        val booksNeedingCovers = books.filter { it.coverUrl.isBlank() || it.coverUrl.endsWith("/cover") }
        if (booksNeedingCovers.isEmpty()) return

        val coverUrls = supervisorScope {
            booksNeedingCovers.map { book ->
                async(Dispatchers.IO) {
                    try {
                        val info = getBookInfoUseCase(book.id)
                        if (info != null && info.coverUrl.isNotBlank()) {
                            book.id to info.coverUrl
                        } else null
                    } catch (_: Exception) { null }
                }
            }.awaitAll().filterNotNull().toMap()
        }

        if (coverUrls.isEmpty()) return

        _state.update { state ->
            val success = state as? AuthorBooksUiState.Success ?: return@update state
            val updatedBooks = success.books.map { book ->
                coverUrls[book.id]?.let { book.copy(coverUrl = it) } ?: book
            }
            AuthorBooksUiState.Success(updatedBooks)
        }
    }
}
