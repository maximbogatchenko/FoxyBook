package com.foxybook.app.domain.usecases

import com.foxybook.app.core.models.LibraryBook
import com.foxybook.app.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow

class GetLibraryBooksUseCase(private val repository: BookRepository) {
    operator fun invoke(): Flow<List<LibraryBook>> = repository.getLibraryBooks()
}
