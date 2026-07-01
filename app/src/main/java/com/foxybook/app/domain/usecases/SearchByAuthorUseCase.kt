package com.foxybook.app.domain.usecases

import com.foxybook.app.core.models.Author
import com.foxybook.app.core.models.SearchPage
import com.foxybook.app.domain.repository.BookRepository

class SearchByAuthorUseCase(
    private val repository: BookRepository
) {
    suspend operator fun invoke(query: String, limit: Int = 20): SearchPage<Author> {
        return repository.searchByAuthor(query, limit)
    }

    suspend fun nextPage(url: String, limit: Int = 20): SearchPage<Author> {
        return repository.searchByAuthorNextPage(url, limit)
    }
}
