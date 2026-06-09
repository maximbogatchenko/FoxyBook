package com.foxybook.app.data.api

import android.content.Context
import android.util.Log
import com.foxybook.app.core.models.*
import com.foxybook.app.core.network.OkHttpClientProvider
import com.foxybook.app.core.utils.XmlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.ResponseBody
import org.xmlpull.v1.XmlPullParser
import java.net.URLEncoder
import kotlin.time.Duration.Companion.seconds

class FlibustaApiOpdsImpl(context: Context) : FlibustaApi {

    private companion object {
        const val TAG = "FLIBUSTA_OPDS_API"
        const val MAX_RETRIES = 3
    }

    private val networkClient = OkHttpClientProvider(context)
    private val client = networkClient.client
    private val downloadClient = networkClient.createDownloadClient()
    private val baseUrl: String get() = networkClient.getBaseUrl()

    override suspend fun searchBooks(query: String, limit: Int): List<Book> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/opds/search?searchTerm=${URLEncoder.encode(query, "UTF-8")}&searchType=books"
            Log.d(TAG, "searchBooks OPDS | url=$url")
            val xml = fetchXml(url) ?: return@withContext emptyList()
            parseOpdsBooks(xml, limit)
        } catch (e: Exception) {
            Log.e(TAG, "searchBooks OPDS error", e)
            emptyList()
        }
    }

    override suspend fun searchByAuthor(author: String, limit: Int): List<Book> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/opds/search?searchTerm=${URLEncoder.encode(author, "UTF-8")}&searchType=authors"
            Log.d(TAG, "searchByAuthor OPDS (search authors) | url=$url")
            val xml = fetchXml(url) ?: return@withContext emptyList()
            val authorIds = parseOpdsAuthorIds(xml, 1)
            
            if (authorIds.isEmpty()) return@withContext emptyList()
            
            val authorId = authorIds.first()
            val booksUrl = "$baseUrl/opds/author/$authorId"
            Log.d(TAG, "searchByAuthor OPDS (get books) | url=$booksUrl")
            val booksXml = fetchXml(booksUrl) ?: return@withContext emptyList()
            parseOpdsBooks(booksXml, limit)
        } catch (e: Exception) {
            Log.e(TAG, "searchByAuthor OPDS error", e)
            emptyList()
        }
    }

    override suspend fun searchBySeries(series: String, limit: Int): List<Series> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/opds/search?searchTerm=${URLEncoder.encode(series, "UTF-8")}&searchType=sequences"
            Log.d(TAG, "searchBySeries OPDS | url=$url")
            val xml = fetchXml(url) ?: return@withContext emptyList()
            parseOpdsSeries(xml, limit)
        } catch (e: Exception) {
            Log.e(TAG, "searchBySeries OPDS error", e)
            emptyList()
        }
    }

    override suspend fun getSeriesBooks(seriesId: String, limit: Int): List<Book> = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/opds/sequence/$seriesId"
            Log.d(TAG, "getSeriesBooks OPDS | url=$url")
            val xml = fetchXml(url) ?: return@withContext emptyList()
            parseOpdsBooks(xml, limit).sortedBy { it.sequenceNumber }
        } catch (e: Exception) {
            Log.e(TAG, "getSeriesBooks OPDS error", e)
            emptyList()
        }
    }

    override suspend fun getBookInfo(id: Int): BookInfo? = withContext(Dispatchers.IO) {
        try {
            // FoxyBook usually uses /b/ID for info, but OPDS has /opds/b/ID
            val url = "$baseUrl/opds/b/$id"
            Log.d(TAG, "getBookInfo OPDS | url=$url")
            val xml = fetchXml(url) ?: return@withContext null
            parseOpdsBookInfo(xml, id)
        } catch (e: Exception) {
            Log.e(TAG, "getBookInfo OPDS error", e)
            null
        }
    }

    override fun getDownloadUrl(id: String, format: BookFormat): String {
        return "$baseUrl/b/$id/${format.extension}"
    }

    override suspend fun downloadBook(id: String, format: BookFormat, onProgress: (Float) -> Unit): ResponseBody? = withContext(Dispatchers.IO) {
        val url = getDownloadUrl(id, format)
        var lastException: Exception? = null

        for (attempt in 1..MAX_RETRIES) {
            try {
                val request = Request.Builder().url(url).build()
                val response = downloadClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    response.close()
                    lastException = Exception("HTTP ${response.code}")
                    continue
                }

                return@withContext response.body
            } catch (e: Exception) {
                lastException = e
                if (attempt < MAX_RETRIES) {
                    kotlinx.coroutines.delay(attempt.seconds)
                }
            }
        }
        throw lastException ?: Exception("Download failed")
    }

    private fun parseOpdsBooks(xml: String, limit: Int): List<Book> {
        val books = mutableListOf<Book>()
        val parser = XmlUtils.createParser(xml)
        var eventType = parser.eventType
        
        var title: String? = null
        var id: Int? = null
        val authors = mutableListOf<String>()
        var coverUrl: String? = null
        val genres = mutableListOf<String>()
        var seqNumber = 0

        while (eventType != XmlPullParser.END_DOCUMENT && books.size < limit) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "entry" -> {
                            title = null; id = null; authors.clear(); coverUrl = null; genres.clear(); seqNumber = 0
                        }
                        "title" -> title = parser.nextText()
                        "id" -> {
                            val idText = parser.nextText()
                            if (idText.contains("/b/")) {
                                id = idText.substringAfterLast("/").toIntOrNull()
                            }
                        }
                        "author" -> {
                            // Authors can be multiple and contain nested <name>
                        }
                        "name" -> {
                            val authorName = parser.nextText().trim()
                            if (authorName.isNotBlank()) authors.add(authorName)
                        }
                        "link" -> {
                            val rel = parser.getAttributeValue(null, "rel")
                            val href = parser.getAttributeValue(null, "href")
                            if (rel == "http://opds-spec.org/image" || rel == "http://opds-spec.org/image/thumbnail") {
                                coverUrl = if (href.startsWith("http")) href else "$baseUrl$href"
                            }
                        }
                        "category" -> {
                            val label = parser.getAttributeValue(null, "label")
                            if (label != null) genres.add(label)
                        }
                        "content" -> {
                            // Could extract description here if needed for search
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name == "entry") {
                        val currentId = id
                        val currentTitle = title
                        if (currentId != null && currentTitle != null) {
                            books.add(
                                Book(
                                    id = currentId,
                                    title = currentTitle,
                                    author = authors.joinToString(", ").ifBlank { "Unknown Author" },
                                    link = "/b/$currentId",
                                    sendLink = "/send/$currentId",
                                    coverUrl = coverUrl ?: "$baseUrl/b/$currentId/cover",
                                    genres = genres.toList(),
                                    sequenceNumber = seqNumber,
                                )
                            )
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return books
    }

    private fun parseOpdsAuthorIds(xml: String, limit: Int): List<String> {
        val ids = mutableListOf<String>()
        val parser = XmlUtils.createParser(xml)
        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT && ids.size < limit) {
            val name = parser.name
            if (eventType == XmlPullParser.START_TAG && name == "id") {
                val idText = parser.nextText()
                if (idText.contains("/author/")) {
                    ids.add(idText.substringAfterLast("/"))
                }
            }
            eventType = parser.next()
        }
        return ids
    }

    private fun parseOpdsSeries(xml: String, limit: Int): List<Series> {
        val seriesList = mutableListOf<Series>()
        val parser = XmlUtils.createParser(xml)
        var eventType = parser.eventType
        
        var title: String? = null
        var id: String? = null
        var count = 0

        while (eventType != XmlPullParser.END_DOCUMENT && seriesList.size < limit) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "entry" -> { title = null; id = null; count = 0 }
                        "title" -> title = parser.nextText()
                        "id" -> {
                            val idText = parser.nextText()
                            if (idText.contains("/sequence/")) {
                                id = idText.substringAfterLast("/")
                            }
                        }
                        "content" -> {
                            val content = parser.nextText()
                            // Often contains "NN книг"
                            val match = Regex("(\\d+)").find(content)
                            count = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name == "entry") {
                        val currentId = id
                        val currentTitle = title
                        if (currentId != null && currentTitle != null) {
                            seriesList.add(
                                Series(
                                    seriesId = currentId,
                                    seriesTitle = currentTitle,
                                    seriesUrl = "$baseUrl/sequence/$currentId",
                                    bookCount = count,
                                )
                            )
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return seriesList
    }

    private fun parseOpdsBookInfo(xml: String, bookId: Int): BookInfo? {
        val parser = XmlUtils.createParser(xml)
        var eventType = parser.eventType

        var title: String? = null
        val authorList = mutableListOf<String>()
        var description = ""
        val genres = mutableListOf<BookGenre>()
        var coverUrl: String? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "title" -> title = parser.nextText()
                        "name" -> {
                            val authorName = parser.nextText().trim()
                            if (authorName.isNotBlank()) authorList.add(authorName)
                        }
                        "content" -> {
                            description = parser.nextText().trim()
                        }
                        "category" -> {
                            val term = parser.getAttributeValue(null, "term") ?: ""
                            val label = parser.getAttributeValue(null, "label") ?: term
                            if (label.isNotBlank()) {
                                genres.add(BookGenre(id = term, title = label))
                            }
                        }
                        "link" -> {
                            val rel = parser.getAttributeValue(null, "rel")
                            val href = parser.getAttributeValue(null, "href")
                            if (rel == "http://opds-spec.org/image") {
                                coverUrl = if (href.startsWith("http")) href else "$baseUrl$href"
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        val finalTitle = title
        return if (finalTitle != null) {
            BookInfo(
                id = bookId,
                title = finalTitle,
                author = authorList.joinToString(", ").ifBlank { "Unknown Author" },
                description = description,
                genres = genres,
                coverUrl = coverUrl ?: "$baseUrl/b/$bookId/cover"
            )
        } else null
    }

    private fun fetchXml(url: String): String? {
        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "fetchXml | HTTP ${response.code} for $url")
                response.close()
                return null
            }
            val xml = response.body?.string()
            response.close()
            xml
        } catch (e: Exception) {
            Log.e(TAG, "fetchXml | failed for $url", e)
            null
        }
    }
}
