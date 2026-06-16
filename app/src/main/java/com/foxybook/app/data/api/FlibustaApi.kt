package com.foxybook.app.data.api

import com.foxybook.app.core.models.Book
import com.foxybook.app.core.models.BookFormat
import com.foxybook.app.core.models.BookInfo
import com.foxybook.app.core.models.Series

interface FlibustaApi {

    suspend fun searchBooks(
        query: String,
        limit: Int = 50
    ): List<Book>

    suspend fun searchByAuthor(
        author: String,
        limit: Int = 20
    ): List<Book>

    suspend fun searchBySeries(
        series: String,
        limit: Int = 20
    ): List<Series>

    suspend fun getSeriesBooks(
        seriesId: String,
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
