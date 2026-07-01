package com.foxybook.app.data.api

import com.foxybook.app.core.models.Book
import com.foxybook.app.core.models.BookFormat
import com.foxybook.app.core.models.BookInfo
import com.foxybook.app.core.models.NewBooksPage
import com.foxybook.app.core.models.Author
import com.foxybook.app.core.models.SearchPage
import com.foxybook.app.core.models.Series

interface FlibustaApi {

    suspend fun getNewBooks(limit: Int = 50): List<Book>

    suspend fun getNewBooksFirstPage(): NewBooksPage

    suspend fun getNewBooksNextPage(url: String): NewBooksPage

    suspend fun searchBooks(
        query: String,
        limit: Int = 50
    ): SearchPage<Book>

    suspend fun searchBooksNextPage(
        url: String,
        limit: Int = 50
    ): SearchPage<Book>

    suspend fun searchByAuthor(
        query: String,
        limit: Int = 20
    ): SearchPage<Author>

    suspend fun searchByAuthorNextPage(
        url: String,
        limit: Int = 20
    ): SearchPage<Author>

    suspend fun getAuthorBooks(
        authorId: String,
        limit: Int = 50
    ): List<Book>

    suspend fun searchByGenre(
        query: String,
        limit: Int = 50
    ): SearchPage<Book>

    suspend fun searchByGenreNextPage(
        url: String,
        limit: Int = 50
    ): SearchPage<Book>

    suspend fun searchBySeries(
        query: String,
        limit: Int = 20
    ): SearchPage<Series>

    suspend fun searchBySeriesNextPage(
        url: String,
        limit: Int = 20
    ): SearchPage<Series>

    suspend fun getSeriesBooks(
        seriesId: String,
        authorId: String? = null,
        limit: Int = 50
    ): List<Book>

    suspend fun getBookInfo(
        id: Int
    ): BookInfo?

    fun getDownloadUrl(
        id: String,
        format: BookFormat
    ): String

    suspend fun downloadBook(
        id: String,
        format: BookFormat,
        onProgress: (Float) -> Unit = {}
    ): okhttp3.ResponseBody?
}
