package com.foxybook.app.features.series

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxybook.app.core.models.Book
import com.foxybook.app.core.models.Series
import com.foxybook.app.core.models.SeriesDetailsUiState
import com.foxybook.app.domain.usecases.GetBookInfoUseCase
import com.foxybook.app.domain.usecases.GetSeriesBooksUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

class SeriesDetailsViewModel(
    private val getSeriesBooksUseCase: GetSeriesBooksUseCase,
    private val getBookInfoUseCase: GetBookInfoUseCase
) : ViewModel() {

    private val TAG = "SERIES_DETAILS"

    private val _state = MutableStateFlow<SeriesDetailsUiState>(SeriesDetailsUiState.Loading)
    val state: StateFlow<SeriesDetailsUiState> = _state.asStateFlow()

    fun loadSeriesBooks(seriesId: String, seriesTitle: String, authorId: String = "") {
        viewModelScope.launch {
            _state.value = SeriesDetailsUiState.Loading

            Log.d(TAG, "SERIES_OPEN | seriesId='$seriesId' | seriesTitle='$seriesTitle' | authorId='$authorId'")

            try {
                val books = getSeriesBooksUseCase(seriesId, authorId.ifBlank { null })

                Log.d(TAG, "SERIES_BOOKS_PARSED | count=${books.size} | seriesTitle='$seriesTitle'")

                books.forEachIndexed { index, book ->
                    Log.d(
                        TAG,
                        "SERIES_BOOKS_AFTER_SORT | [$index] SERIES_BOOK_TITLE='${book.title}' | SERIES_BOOK_NUMBER=${book.sequenceNumber} | id=${book.id}"
                    )
                }

                val series = Series(
                    seriesId = seriesId,
                    seriesTitle = seriesTitle,
                    seriesUrl = "",
                    bookCount = books.size
                )

                if (books.isEmpty()) {
                    Log.w(TAG, "SERIES_OPEN | EMPTY result for seriesId='$seriesId'")
                    _state.value = SeriesDetailsUiState.Error("Не удалось загрузить книги серии")
                } else {
                    Log.d(TAG, "SERIES_OPEN | SUCCESS | ${books.size} books")
                    _state.value = SeriesDetailsUiState.Success(series, books)
                    fetchCovers(books)
                }
            } catch (e: Exception) {
                Log.e(TAG, "SERIES_OPEN | ERROR for seriesId='$seriesId'", e)
                _state.value = SeriesDetailsUiState.Error(e.message ?: "Ошибка загрузки")
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
            val success = state as? SeriesDetailsUiState.Success ?: return@update state
            val updatedBooks = success.books.map { book ->
                coverUrls[book.id]?.let { book.copy(coverUrl = it) } ?: book
            }
            SeriesDetailsUiState.Success(success.series, updatedBooks)
        }
    }
}
