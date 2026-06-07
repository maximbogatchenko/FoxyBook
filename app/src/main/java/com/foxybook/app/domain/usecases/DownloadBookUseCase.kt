package com.foxybook.app.domain.usecases

import com.foxybook.app.core.models.BookFormat
import com.foxybook.app.core.models.LibraryBook
import com.foxybook.app.domain.repository.BookRepository

class DownloadBookUseCase(
    private val repository: BookRepository
) {
    suspend operator fun invoke(
        id: Int,
        title: String,
        author: String,
        format: BookFormat,
        coverUrl: String = "",
        onProgress: (Float) -> Unit = {}
    ): Result<String> {
        val result = repository.downloadBook(id.toString(), format, onProgress)
        if (result.isSuccess) {
            val filePath = result.getOrNull() ?: return Result.failure(Exception("No file path"))
            repository.addLibraryBook(
                LibraryBook(
                    id = id,
                    title = title,
                    author = author,
                    format = format.extension,
                    filePath = filePath,
                    coverUrl = coverUrl,
                    downloadDate = System.currentTimeMillis()
                )
            )
        }
        return result
    }
}
