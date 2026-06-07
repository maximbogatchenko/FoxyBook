package com.foxybook.app.domain.usecases

import com.foxybook.app.domain.repository.BookRepository

class RemoveBookUseCase(private val repository: BookRepository) {
    suspend operator fun invoke(bookId: Int, format: String) = repository.removeLibraryBook(bookId, format)
}
