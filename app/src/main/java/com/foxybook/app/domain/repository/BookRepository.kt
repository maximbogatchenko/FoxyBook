package com.foxybook.app.domain.repository

import com.foxybook.app.core.models.Book
import com.foxybook.app.core.models.BookFormat
import com.foxybook.app.core.models.BookInfo
import com.foxybook.app.core.models.LibraryBook
import com.foxybook.app.core.models.Series
import kotlinx.coroutines.flow.Flow

interface BookRepository {

    suspend fun searchBooks(query: String, limit: Int = 20): List<Book>

    suspend fun searchByAuthor(author: String, limit: Int = 20): List<Book>

    suspend fun searchBySeries(series: String, limit: Int = 20): List<Series>

    suspend fun getSeriesBooks(seriesId: String, limit: Int = 50): List<Book>

    suspend fun getBookInfo(id: Int): BookInfo?

    fun getDownloadUrl(id: String, format: BookFormat): String

    suspend fun downloadBook(
        id: String,
        format: BookFormat,
        onProgress: (Float) -> Unit = {}
    ): Result<String>

    fun getLibraryBooks(): Flow<List<LibraryBook>>

    suspend fun addLibraryBook(book: LibraryBook)

    suspend fun removeLibraryBook(bookId: Int, format: String)

    suspend fun isBookDownloaded(id: String, format: BookFormat): Boolean
}
