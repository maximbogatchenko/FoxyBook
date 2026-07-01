package com.foxybook.app.domain.usecases

import com.foxybook.app.core.models.Book
import com.foxybook.app.core.models.SearchPage
import com.foxybook.app.domain.repository.BookRepository

class SearchBooksUseCase(
    private val repository: BookRepository
) {
    suspend operator fun invoke(query: String, limit: Int = 50): SearchPage<Book> {
        return repository.searchBooks(query, limit)
    }

    suspend fun nextPage(url: String, limit: Int = 50): SearchPage<Book> {
        return repository.searchBooksNextPage(url, limit)
    }
}
