package com.foxybook.app.domain.usecases

import com.foxybook.app.core.models.Book
import com.foxybook.app.domain.repository.BookRepository

class GetAuthorBooksUseCase(
    private val repository: BookRepository
) {
    suspend operator fun invoke(authorId: String, limit: Int = 50): List<Book> {
        return repository.getAuthorBooks(authorId, limit)
    }
}
