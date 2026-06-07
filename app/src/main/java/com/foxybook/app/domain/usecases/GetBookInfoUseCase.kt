package com.foxybook.app.domain.usecases

import com.foxybook.app.core.models.BookInfo
import com.foxybook.app.domain.repository.BookRepository

class GetBookInfoUseCase(private val repository: BookRepository) {
    suspend operator fun invoke(id: Int): BookInfo? = repository.getBookInfo(id)
}
