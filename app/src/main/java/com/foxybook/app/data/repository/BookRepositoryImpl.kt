package com.foxybook.app.data.repository

import android.content.Context
import com.foxybook.app.core.datastore.DataStoreManager
import com.foxybook.app.core.models.Book
import com.foxybook.app.core.models.BookFormat
import com.foxybook.app.core.models.BookInfo
import com.foxybook.app.core.models.LibraryBook
import com.foxybook.app.core.models.Series
import com.foxybook.app.core.utils.FileHelper
import com.foxybook.app.data.api.FlibustaApi
import com.foxybook.app.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow

class BookRepositoryImpl(
    private val api: FlibustaApi,
    private val context: Context,
    private val dataStoreManager: DataStoreManager
) : BookRepository {

    override suspend fun searchBooks(query: String, limit: Int): List<Book> {
        return api.searchBooks(query, limit)
    }

    override suspend fun searchByAuthor(author: String, limit: Int): List<Book> {
        return api.searchByAuthor(author, limit)
    }

    override suspend fun searchBySeries(series: String, limit: Int): List<Series> {
        return api.searchBySeries(series, limit)
    }

    override suspend fun getSeriesBooks(seriesId: String, limit: Int): List<Book> {
        return api.getSeriesBooks(seriesId, limit)
    }

    override suspend fun getBookInfo(id: Int): BookInfo? {
        return api.getBookInfo(id)
    }

    override fun getDownloadUrl(id: String, format: BookFormat): String {
        return api.getDownloadUrl(id, format)
    }

    override suspend fun downloadBook(
        id: String,
        format: BookFormat,
        onProgress: (Float) -> Unit
    ): Result<String> {
        return try {
            val destDir = FileHelper.getFormatDir(context, format)
            val file = api.downloadBook(id, format, destDir, onProgress)
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getLibraryBooks(): Flow<List<LibraryBook>> {
        return dataStoreManager.libraryBooks
    }

    override suspend fun addLibraryBook(book: LibraryBook) {
        dataStoreManager.addLibraryBook(book)
    }

    override suspend fun removeLibraryBook(bookId: Int, format: String) {
        dataStoreManager.removeLibraryBook(bookId, format)
        val bookFormat = BookFormat.entries.find { it.extension == format }
        if (bookFormat != null) {
            FileHelper.deleteBookFile(context, bookId.toString(), bookFormat)
        }
    }

    override suspend fun isBookDownloaded(id: String, format: BookFormat): Boolean {
        return FileHelper.isBookDownloaded(context, id, format)
    }
}
