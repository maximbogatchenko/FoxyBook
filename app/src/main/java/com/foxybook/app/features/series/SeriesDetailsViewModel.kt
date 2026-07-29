package com.foxybook.app.features.series

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxybook.app.core.models.Series
import com.foxybook.app.core.models.SeriesDetailsUiState
import com.foxybook.app.domain.usecases.GetSeriesBooksUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SeriesDetailsViewModel(
    private val getSeriesBooksUseCase: GetSeriesBooksUseCase
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
                }
            } catch (e: Exception) {
                Log.e(TAG, "SERIES_OPEN | ERROR for seriesId='$seriesId'", e)
                _state.value = SeriesDetailsUiState.Error(e.message ?: "Ошибка загрузки")
            }
        }
    }
}
