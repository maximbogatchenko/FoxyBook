package com.foxybook.app.domain.usecases

import com.foxybook.app.core.models.Book
import com.foxybook.app.domain.repository.BookRepository

class SearchBooksUseCase(
    private val repository: BookRepository
) {
    suspend operator fun invoke(query: String, limit: Int = 20): List<Book> {
        return repository.searchBooks(query, limit)
    }
}
