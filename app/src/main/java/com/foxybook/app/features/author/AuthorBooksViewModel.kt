package com.foxybook.app.features.author

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxybook.app.core.models.Book
import com.foxybook.app.domain.usecases.GetAuthorBooksUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AuthorBooksUiState {
    data object Loading : AuthorBooksUiState
    data class Success(val books: List<Book>) : AuthorBooksUiState
    data class Error(val message: String) : AuthorBooksUiState
}

class AuthorBooksViewModel(
    private val getAuthorBooksUseCase: GetAuthorBooksUseCase
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
                }
            } catch (e: Exception) {
                _state.value = AuthorBooksUiState.Error(e.message ?: "Ошибка загрузки")
            }
        }
    }

    }
