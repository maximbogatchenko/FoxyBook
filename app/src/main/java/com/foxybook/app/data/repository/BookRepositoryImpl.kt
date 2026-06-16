package com.foxybook.app.data.repository

import android.util.Log
import com.foxybook.app.core.database.BookDataRepository
import com.foxybook.app.core.datastore.DataStoreManager
import com.foxybook.app.core.models.Book
import com.foxybook.app.core.models.BookFormat
import com.foxybook.app.core.models.BookInfo
import com.foxybook.app.core.models.LibraryBook
import com.foxybook.app.core.models.Series
import com.foxybook.app.data.api.FlibustaApi
import com.foxybook.app.data.storage.FileDownloader
import com.foxybook.app.domain.repository.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class BookRepositoryImpl(
    private val api: FlibustaApi,
    private val dataStoreManager: DataStoreManager,
    private val bookDataRepository: BookDataRepository,
    private val fileDownloader: FileDownloader
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
    ): Result<String> = withContext(Dispatchers.IO) {
        Log.d("BookRepository", "downloadBook: id=$id, format=$format")
        try {
            val responseBody = api.downloadBook(id, format, onProgress)
                ?: return@withContext Result.failure(Exception("Failed to download"))

            val fileName = "$id.${format.extension}"
            val customDirUri = dataStoreManager.downloadDirectory.first()
            val resultPath = fileDownloader.download(responseBody, fileName, format, customDirUri, onProgress)

            Log.d("BookRepository", "downloadBook: SUCCESS, path=$resultPath")
            Result.success(resultPath)
        } catch (e: Exception) {
            Log.e("BookRepository", "downloadBook: ERROR", e)
            Result.failure(e)
        }
    }

    override fun getLibraryBooks(): Flow<List<LibraryBook>> {
        return bookDataRepository.getAllBooks()
    }

    override suspend fun addLibraryBook(book: LibraryBook) {
        bookDataRepository.addLibraryBook(book)
    }

    override suspend fun removeLibraryBook(bookId: Int, format: String) {
        val book = getLibraryBooks().first().find { it.id == bookId && it.format == format }
        Log.d("BookRepository", "removeLibraryBook: id=$bookId, format=$format, found=${book != null}")

        bookDataRepository.removeLibraryBook(bookId, format)

        if (book != null) {
            Log.d("BookRepository", "removeLibraryBook: deleting file at ${book.filePath}")
            fileDownloader.deleteFile(book.filePath)
        }
    }

    override suspend fun isBookDownloaded(id: String, format: BookFormat): Boolean {
        val book = getLibraryBooks().first().find { it.id.toString() == id && it.format == format.extension }
            ?: return false
        return fileDownloader.fileExists(book.filePath)
    }
}
