package com.foxybook.app.domain.usecases

import com.foxybook.app.core.models.Series
import com.foxybook.app.domain.repository.BookRepository

class SearchBySeriesUseCase(
    private val repository: BookRepository
) {
    suspend operator fun invoke(series: String, limit: Int = 20): List<Series> {
        return repository.searchBySeries(series, limit)
    }
}
