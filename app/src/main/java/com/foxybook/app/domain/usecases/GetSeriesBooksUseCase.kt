package com.foxybook.app.domain.usecases

import com.foxybook.app.core.models.Book
import com.foxybook.app.domain.repository.BookRepository

class GetSeriesBooksUseCase(
    private val repository: BookRepository
) {
    suspend operator fun invoke(seriesId: String, limit: Int = 50): List<Book> {
        return repository.getSeriesBooks(seriesId, limit)
    }
}
