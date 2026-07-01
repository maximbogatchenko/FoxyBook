package com.foxybook.app.domain.usecases

import com.foxybook.app.core.models.SearchPage
import com.foxybook.app.core.models.Series
import com.foxybook.app.domain.repository.BookRepository

class SearchBySeriesUseCase(
    private val repository: BookRepository
) {
    suspend operator fun invoke(query: String, limit: Int = 20): SearchPage<Series> {
        return repository.searchBySeries(query, limit)
    }

    suspend fun nextPage(url: String, limit: Int = 20): SearchPage<Series> {
        return repository.searchBySeriesNextPage(url, limit)
    }
}
