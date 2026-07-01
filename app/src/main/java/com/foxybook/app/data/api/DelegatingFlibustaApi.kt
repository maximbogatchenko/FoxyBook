package com.foxybook.app.data.api

import com.foxybook.app.core.models.Author
import com.foxybook.app.core.models.Book
import com.foxybook.app.core.models.BookFormat
import com.foxybook.app.core.models.BookInfo
import com.foxybook.app.core.models.NewBooksPage
import com.foxybook.app.core.models.SearchPage
import com.foxybook.app.core.models.Series
import com.foxybook.app.core.network.OkHttpClientProvider
import okhttp3.ResponseBody

class DelegatingFlibustaApi(
    private val networkProvider: OkHttpClientProvider
) : FlibustaApi {

    private val flibustaImpl = FlibustaApiOpdsImpl(networkProvider)
    private val fantasyWorldsImpl = FantasyWorldsApiOpdsImpl(networkProvider)

    private val delegate: FlibustaApi
        get() = when (networkProvider.activeSource) {
            com.foxybook.app.core.models.BookSource.FANTASY_WORLDS -> fantasyWorldsImpl
            else -> flibustaImpl
        }

    override suspend fun getNewBooks(limit: Int): List<Book> = delegate.getNewBooks(limit)
    override suspend fun getNewBooksFirstPage(): NewBooksPage = delegate.getNewBooksFirstPage()
    override suspend fun getNewBooksNextPage(url: String): NewBooksPage = delegate.getNewBooksNextPage(url)
    override suspend fun searchBooks(query: String, limit: Int): SearchPage<Book> = delegate.searchBooks(query, limit)
    override suspend fun searchBooksNextPage(url: String, limit: Int): SearchPage<Book> = delegate.searchBooksNextPage(url, limit)
    override suspend fun searchByAuthor(query: String, limit: Int): SearchPage<Author> = delegate.searchByAuthor(query, limit)
    override suspend fun searchByAuthorNextPage(url: String, limit: Int): SearchPage<Author> = delegate.searchByAuthorNextPage(url, limit)
    override suspend fun getAuthorBooks(authorId: String, limit: Int): List<Book> = delegate.getAuthorBooks(authorId, limit)
    override suspend fun searchByGenre(query: String, limit: Int): SearchPage<Book> = delegate.searchByGenre(query, limit)
    override suspend fun searchByGenreNextPage(url: String, limit: Int): SearchPage<Book> = delegate.searchByGenreNextPage(url, limit)
    override suspend fun searchBySeries(query: String, limit: Int): SearchPage<Series> = delegate.searchBySeries(query, limit)
    override suspend fun searchBySeriesNextPage(url: String, limit: Int): SearchPage<Series> = delegate.searchBySeriesNextPage(url, limit)
    override suspend fun getSeriesBooks(seriesId: String, authorId: String?, limit: Int): List<Book> = delegate.getSeriesBooks(seriesId, authorId, limit)
    override suspend fun getBookInfo(id: Int): BookInfo? = delegate.getBookInfo(id)
    override fun getDownloadUrl(id: String, format: BookFormat): String = delegate.getDownloadUrl(id, format)
    override suspend fun downloadBook(id: String, format: BookFormat, onProgress: (Float) -> Unit): ResponseBody? = delegate.downloadBook(id, format, onProgress)
}
