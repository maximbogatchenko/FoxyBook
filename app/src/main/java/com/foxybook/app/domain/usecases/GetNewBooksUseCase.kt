package com.foxybook.app.domain.usecases

import com.foxybook.app.core.models.Book
import com.foxybook.app.core.models.NewBooksPage
import com.foxybook.app.domain.repository.BookRepository

class GetNewBooksUseCase(
    private val repository: BookRepository
) {
    suspend operator fun invoke(limit: Int = 50): List<Book> {
        return repository.getNewBooks(limit)
    }

    suspend fun firstPage(): NewBooksPage {
        return repository.getNewBooksFirstPage()
    }

    suspend fun nextPage(url: String): NewBooksPage {
        return repository.getNewBooksNextPage(url)
    }
}
