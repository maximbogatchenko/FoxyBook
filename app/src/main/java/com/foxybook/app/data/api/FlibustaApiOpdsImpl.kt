package com.foxybook.app.data.api

import android.util.Log
import com.foxybook.app.core.models.Author
import com.foxybook.app.core.models.Book
import com.foxybook.app.core.models.BookFormat
import com.foxybook.app.core.models.BookGenre
import com.foxybook.app.core.models.BookInfo
import com.foxybook.app.core.models.NewBooksPage
import com.foxybook.app.core.models.SearchPage
import com.foxybook.app.core.models.Series
import com.foxybook.app.core.network.OkHttpClientProvider
import com.foxybook.app.core.utils.XmlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.ResponseBody
import org.xmlpull.v1.XmlPullParser
import org.jsoup.Jsoup
import java.net.URLEncoder
import kotlin.time.Duration.Companion.seconds

class FlibustaApiOpdsImpl(
    private val networkClient: OkHttpClientProvider
) : FlibustaApi {

    private companion object {
        const val TAG = "FLIBUSTA_OPDS"
        const val MAX_RETRIES = 3
        const val ALL_RESULTS = Int.MAX_VALUE
        const val MAX_AUTHORS_TO_CRAWL = 3
        const val MAX_SEQ_PAGES = 15
    }

    private val client = networkClient.client
    private val downloadClient = networkClient.createDownloadClient()
    private val baseUrl: String get() = networkClient.getBaseUrl()

    // Кэш реальных URL скачивания из HTML-страницы книги (bookId -> format -> url)
    private val downloadUrlCache = mutableMapOf<Int, Map<String, String>>()
override suspend fun getNewBooks(limit: Int): List<Book> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/opds/new/0/new"
        Log.d(TAG, "getNewBooks | $url")
        val xml = fetchXml(url)
        val books = parseNewBooksFast(xml, limit.coerceAtMost(50))
        Log.d(TAG, "getNewBooks | found=${books.size}")
        books.distinctBy { it.id }
    }

    override suspend fun getNewBooksFirstPage(): NewBooksPage = withContext(Dispatchers.IO) {
        val url = "$baseUrl/opds/new/0/new"
        Log.d(TAG, "getNewBooksFirstPage | $url")
        val xml = fetchXml(url)
        val books = parseNewBooksFast(xml, 50)
        val nextUrl = if (books.isNotEmpty()) {
            val fromLink = parseNextLink(xml).let { if (it.isNotBlank()) (if (it.startsWith("http")) it else "$baseUrl$it") else null }
            fromLink ?: "$baseUrl/opds/new/${books.size}/new"
        } else null
        NewBooksPage(books.distinctBy { it.id }, nextUrl)
    }

    override suspend fun getNewBooksNextPage(url: String): NewBooksPage = withContext(Dispatchers.IO) {
        Log.d(TAG, "getNewBooksNextPage | $url")
        val xml = fetchXml(url)
        val books = parseNewBooksFast(xml, 50)
        val nextUrl = if (books.isNotEmpty()) {
            val fromLink = parseNextLink(xml).let { if (it.isNotBlank()) (if (it.startsWith("http")) it else "$baseUrl$it") else null }
            if (fromLink != null) fromLink else {
                val offsetMatch = Regex("""/opds/new/(\d+)/new""").find(url)
                val offset = offsetMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                "$baseUrl/opds/new/${offset + books.size}/new"
            }
        } else null
        NewBooksPage(books.distinctBy { it.id }, nextUrl)
    }

    override suspend fun searchBooks(query: String, limit: Int): SearchPage<Book> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/opds/search?searchType=books&searchTerm=$encoded"
        Log.d(TAG, "searchBooks | $url")
        val xml = fetchXml(url)
        val books = parseOpdsBooks(xml, limit)
        val nextUrl = if (books.isNotEmpty()) parseNextLink(xml).let { if (it.isNotBlank()) it else null } else null
        Log.d(TAG, "searchBooks | found=${books.size}, next=$nextUrl")
        SearchPage(books.distinctBy { it.id }, nextUrl)
    }

    override suspend fun searchBooksNextPage(url: String, limit: Int): SearchPage<Book> = withContext(Dispatchers.IO) {
        val xml = fetchXml(url)
        val books = parseOpdsBooks(xml, limit)
        val nextUrl = if (books.isNotEmpty()) parseNextLink(xml).let { if (it.isNotBlank()) it else null } else null
        SearchPage(books.distinctBy { it.id }, nextUrl)
    }

    override suspend fun searchByAuthor(query: String, limit: Int): SearchPage<Author> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/opds/search?searchType=authors&searchTerm=$encoded"
        Log.d(TAG, "searchByAuthor | $url")
        val xml = fetchXml(url)
        val authors = parseOpdsAuthors(xml, limit)
        val nextUrl = if (authors.isNotEmpty()) parseNextLink(xml).let { if (it.isNotBlank()) it else null } else null
        SearchPage(authors.distinctBy { it.authorId }, nextUrl)
    }

    override suspend fun searchByAuthorNextPage(url: String, limit: Int): SearchPage<Author> = withContext(Dispatchers.IO) {
        val xml = fetchXml(url)
        val authors = parseOpdsAuthors(xml, limit)
        val nextUrl = if (authors.isNotEmpty()) parseNextLink(xml).let { if (it.isNotBlank()) it else null } else null
        SearchPage(authors.distinctBy { it.authorId }, nextUrl)
    }

    override suspend fun getAuthorBooks(authorId: String, limit: Int): List<Book> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/opds/author/$authorId/alphabet"
        Log.d(TAG, "getAuthorBooks | $url")
        val pages = fetchAllPages(url)
        val books = mutableListOf<Book>()
        for (xml in pages) {
            if (books.size >= limit) break
            books.addAll(parseOpdsBooks(xml, limit))
        }
        books.sortedBy { it.sequenceNumber }.distinctBy { it.id }
    }

    override suspend fun searchBySeries(query: String, limit: Int): SearchPage<Series> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().lowercase()
        val allSeries = mutableListOf<Series>()
        val encoded = URLEncoder.encode(query, "UTF-8")

        // 1. Поиск серий в найденных книгах (быстро - 1 запрос)
        val bookUrl = "$baseUrl/opds/search?searchType=books&searchTerm=$encoded"
        Log.d(TAG, "searchBySeries | books → $bookUrl")
        try {
            val bookXml = fetchXml(bookUrl)
            if (bookXml != null) {
                allSeries.addAll(parseOpdsSeriesFromBooks(bookXml, limit))
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchBySeries | books failed", e)
        }

        // 2. Поиск в общих сериях только на первой странице (быстро - 1 запрос)
        try {
            val page0 = fetchXml("$baseUrl/opds/sequences")
            if (page0 != null) {
                allSeries.addAll(parseOpdsSequences(page0, limit))
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchBySeries | sequences failed", e)
        }

        val filtered = allSeries.filter {
            it.seriesTitle.trim().lowercase().contains(cleanQuery)
        }.distinctBy { it.seriesId }.take(limit)

        Log.d(TAG, "searchBySeries | found=${filtered.size}")
        SearchPage(filtered, null)
    }

    override suspend fun searchBySeriesNextPage(url: String, limit: Int): SearchPage<Series> = withContext(Dispatchers.IO) {
        SearchPage(emptyList())
    }

    // ═══════════════════════════════════════════════════════════════
    //  Genre catalog navigation — correct OPDS genre browsing
    // ═══════════════════════════════════════════════════════════════
    //
    //  Flibusta OPDS genre structure (3 levels):
    //    /opds/genres              → 24 top-level genres (flat list)
    //    /opds/genres/ИМЯ_ЖАНРА   → sub-genres with book counts
    //    /opds/genres/ИМЯ_ЖАНРА/ID → actual books with <link rel="next">
    // ═══════════════════════════════════════════════════════════════

    /**
     * Парсит OPDS-фид, содержащий навигационные entry (жанры или поджанры).
     * Каждый entry имеет <title> и <link href="..."> (rel может отсутствовать).
     * Пример: /opds/genres или /opds/genres/Фантастика
     */
    private data class GenreNavEntry(
        val name: String,
        val url: String
    )

    /**
     * Парсит любой навигационный OPDS-фид: достаёт title + href из каждого entry.
     * Подходит для /opds/genres (список жанров) и /opds/genres/Фантастика (поджанры).
     */
    private fun parseNavEntries(xml: String): List<GenreNavEntry> {
        val entries = mutableListOf<GenreNavEntry>()
        val parser = XmlUtils.createParser(xml)
        var eventType = parser.eventType
        var inEntry = false
        var title: String? = null
        var linkHref: String? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "entry" -> { title = null; linkHref = null; inEntry = true }
                        "title" -> if (inEntry) title = parser.nextText()
                        "link" -> {
                            if (inEntry && linkHref == null) {
                                val href = parser.getAttributeValue(null, "href")
                                // Берём первую ссылку (alternate по умолчанию),
                                // пропускаем rel="search", rel="start", rel="up", rel="next"
                                val rel = parser.getAttributeValue(null, "rel") ?: ""
                                if (href != null && rel !in listOf("search", "start", "up", "next", "self")) {
                                    linkHref = if (href.startsWith("http")) href else "$baseUrl$href"
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name == "entry") {
                        inEntry = false
                        if (title != null && linkHref != null) {
                            entries.add(GenreNavEntry(title!!, linkHref!!))
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return entries
    }

    /**
     * Состояние пагинации поиска по жанрам.
     */
    @Volatile
    private var genreState: GenreSearchState? = null

    private data class GenreSearchState(
        val matchingGenres: List<GenreNavEntry>,
        val currentGenreIndex: Int,
        val subGenres: List<GenreNavEntry>,
        val currentSubGenreIndex: Int,
        val subGenreNextUrl: String?   // rel="next" из текущего фида поджанра
    )

    override suspend fun searchByGenre(query: String, limit: Int): SearchPage<Book> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().lowercase()
        Log.d(TAG, "searchByGenre | query=$cleanQuery, limit=$limit")

        // 1. Загружаем /opds/genres — список всех жанров
        val genresXml = try {
            fetchXml("$baseUrl/opds/genres")
        } catch (e: Exception) {
            Log.e(TAG, "searchByGenre | /opds/genres failed, falling back to search", e)
            return@withContext searchByGenreFallback(query, limit, cleanQuery)
        }
        val allGenres = parseNavEntries(genresXml)
        Log.d(TAG, "searchByGenre | /opds/genres has ${allGenres.size} genres")

        // 2. Фильтруем по запросу пользователя
        val matching = allGenres.filter { it.name.lowercase().contains(cleanQuery) }
        if (matching.isEmpty()) {
            Log.d(TAG, "searchByGenre | no matching genres, falling back to search")
            return@withContext searchByGenreFallback(query, limit, cleanQuery)
        }
        Log.d(TAG, "searchByGenre | matching: ${matching.map { it.name }}")

        // 3. Загружаем поджанры первого подходящего жанра
        val subGenresXml = try {
            fetchXml(matching[0].url)
        } catch (e: Exception) {
            Log.e(TAG, "searchByGenre | sub-genre feed failed for '${matching[0].name}', falling back", e)
            return@withContext searchByGenreFallback(query, limit, cleanQuery)
        }
        val subGenres = parseNavEntries(subGenresXml)
        Log.d(TAG, "searchByGenre | '${matching[0].name}' has ${subGenres.size} sub-genres")

        // 4. Перебираем поджанры, пока не найдём книги
        val books = mutableListOf<Book>()
        var foundSubGenreIndex = -1
        var nextSubUrl: String? = null

        for (i in subGenres.indices) {
            if (books.size >= limit) break
            val sub = subGenres[i]
            try {
                val subXml = fetchXml(sub.url)
                val subBooks = parseOpdsBooks(subXml, limit - books.size)
                if (subBooks.isNotEmpty()) {
                    books.addAll(subBooks)
                    foundSubGenreIndex = i
                    nextSubUrl = parseNextLink(subXml).let { if (it.isNotBlank()) it else null }
                    break
                }
            } catch (e: Exception) {
                Log.w(TAG, "searchByGenre | sub-genre '${sub.name}' error", e)
            }
        }

        if (books.isEmpty()) {
            Log.d(TAG, "searchByGenre | no books in OPDS genre feeds, falling back to search")
            return@withContext searchByGenreFallback(query, limit, cleanQuery)
        }

        // 5. Сохраняем состояние пагинации
        genreState = GenreSearchState(
            matchingGenres = matching,
            currentGenreIndex = 0,
            subGenres = subGenres,
            currentSubGenreIndex = foundSubGenreIndex,
            subGenreNextUrl = nextSubUrl
        )

        val hasMore = nextSubUrl != null || foundSubGenreIndex + 1 < subGenres.size || matching.size > 1
        Log.d(TAG, "searchByGenre | found ${books.size} books via OPDS nav, hasMore=$hasMore")
        SearchPage(books.distinctBy { it.id }, if (hasMore) "genre_next://0" else null)
    }

    /**
     * Fallback: поиск через /opds/search + клиентская фильтрация по жанру.
     * Используется когда OPDS-каталог жанров недоступен.
     */
    private suspend fun searchByGenreFallback(query: String, limit: Int, cleanQuery: String): SearchPage<Book> {
        Log.d(TAG, "searchByGenreFallback | query=$query")
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/opds/search?searchType=books&searchTerm=$encoded"
        val xml = try {
            fetchXml(url)
        } catch (e: Exception) {
            Log.e(TAG, "searchByGenreFallback | search failed", e)
            return SearchPage(emptyList())
        }
        val allBooks = parseOpdsBooks(xml, limit)
        val filtered = allBooks.filter { book ->
            book.genres.any { it.lowercase().contains(cleanQuery) }
        }
        val nextUrl = if (filtered.isNotEmpty()) "$baseUrl/opds/search?searchType=books&searchTerm=$encoded&pageNumber=1" else null
        Log.d(TAG, "searchByGenreFallback | found=${filtered.size} from ${allBooks.size}, next=$nextUrl")
        return SearchPage(filtered.distinctBy { it.id }, nextUrl)
    }

    override suspend fun searchByGenreNextPage(url: String, limit: Int): SearchPage<Book> = withContext(Dispatchers.IO) {
        // Fallback: если URL не genre_next:// — это обычная пагинация через /opds/search
        if (!url.startsWith("genre_next://")) {
            Log.d(TAG, "searchByGenreNextPage | fallback pagination for $url")
            return@withContext searchByGenreNextPageFallback(url, limit)
        }

        val state = genreState
        if (state == null) {
            Log.d(TAG, "searchByGenreNextPage | no state")
            return@withContext SearchPage(emptyList())
        }

        // 1. Пробуем следующую страницу текущего поджанра (rel="next")
        var currentState = state
        if (currentState.subGenreNextUrl != null) {
            val xml = try {
                fetchXml(currentState.subGenreNextUrl)
            } catch (e: Exception) {
                Log.e(TAG, "searchByGenreNextPage | fetch next failed", e)
                null
            }
            if (xml != null) {
                val books = parseOpdsBooks(xml, limit)
                val nextNext = parseNextLink(xml).let { if (it.isNotBlank()) it else null }
                if (books.isNotEmpty()) {
                    genreState = currentState.copy(subGenreNextUrl = nextNext)
                    val hasMore = nextNext != null ||
                        currentState.currentSubGenreIndex + 1 < currentState.subGenres.size ||
                        currentState.currentGenreIndex + 1 < currentState.matchingGenres.size
                    return@withContext SearchPage(
                        books.distinctBy { it.id },
                        if (hasMore) "genre_next://${currentState.currentGenreIndex}" else null
                    )
                }
                // Страница пуста — сбрасываем nextUrl и пробуем следующий поджанр
                currentState = currentState.copy(subGenreNextUrl = nextNext)
            }
        }

        // 2. Пробуем следующий поджанр
        if (currentState.currentSubGenreIndex + 1 < currentState.subGenres.size) {
            for (i in (currentState.currentSubGenreIndex + 1)..<currentState.subGenres.size) {
                val sub = currentState.subGenres[i]
                try {
                    val subXml = fetchXml(sub.url)
                    val subBooks = parseOpdsBooks(subXml, limit)
                    if (subBooks.isNotEmpty()) {
                        val nextInSub = parseNextLink(subXml).let { if (it.isNotBlank()) it else null }
                        genreState = currentState.copy(
                            currentSubGenreIndex = i,
                            subGenreNextUrl = nextInSub
                        )
                        val hasMore = nextInSub != null ||
                            i + 1 < currentState.subGenres.size ||
                            currentState.currentGenreIndex + 1 < currentState.matchingGenres.size
                        return@withContext SearchPage(
                            subBooks.distinctBy { it.id },
                            if (hasMore) "genre_next://${currentState.currentGenreIndex}" else null
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "searchByGenreNextPage | sub-genre '${sub.name}' error", e)
                }
            }
        }

        // 3. Пробуем следующий жанр
        if (currentState.currentGenreIndex + 1 < currentState.matchingGenres.size) {
            val nextGenreIdx = currentState.currentGenreIndex + 1
            val nextGenre = currentState.matchingGenres[nextGenreIdx]
            try {
                val subGenresXml = fetchXml(nextGenre.url)
                val subGenres = parseNavEntries(subGenresXml)
                for (i in subGenres.indices) {
                    val sub = subGenres[i]
                    try {
                        val subXml = fetchXml(sub.url)
                        val subBooks = parseOpdsBooks(subXml, limit)
                        if (subBooks.isNotEmpty()) {
                            val nextInSub = parseNextLink(subXml).let { if (it.isNotBlank()) it else null }
                            genreState = GenreSearchState(
                                matchingGenres = currentState.matchingGenres,
                                currentGenreIndex = nextGenreIdx,
                                subGenres = subGenres,
                                currentSubGenreIndex = i,
                                subGenreNextUrl = nextInSub
                            )
                            val hasMore = nextInSub != null ||
                                i + 1 < subGenres.size ||
                                nextGenreIdx + 1 < currentState.matchingGenres.size
                            return@withContext SearchPage(
                                subBooks.distinctBy { it.id },
                                if (hasMore) "genre_next://$nextGenreIdx" else null
                            )
                        }
                    } catch (e: Exception) { /* skip */ }
                }
            } catch (e: Exception) {
                Log.w(TAG, "searchByGenreNextPage | genre '${nextGenre.name}' error", e)
            }
        }

        // 4. Всё исчерпано
        Log.d(TAG, "searchByGenreNextPage | no more results")
        genreState = null
        SearchPage(emptyList())
    }

    /**
     * Fallback-пагинация через /opds/search (когда OPDS-каталог жанров недоступен).
     */
    private suspend fun searchByGenreNextPageFallback(url: String, limit: Int): SearchPage<Book> {
        val xml = try {
            fetchXml(url)
        } catch (e: Exception) {
            Log.e(TAG, "searchByGenreNextPageFallback | fetch failed", e)
            return SearchPage(emptyList())
        }
        val allBooks = parseOpdsBooks(xml, limit)
        // Извлекаем searchTerm из URL для клиентской фильтрации
        val searchTerm = Regex("""searchTerm=([^&]+)""").find(url)?.groupValues?.get(1) ?: ""
        val cleanQuery = java.net.URLDecoder.decode(searchTerm, "UTF-8").lowercase()
        val filtered = allBooks.filter { book ->
            book.genres.any { it.lowercase().contains(cleanQuery) }
        }
        val nextPageNumber = Regex("""pageNumber=(\d+)""").find(url)?.groupValues?.get(1)?.toIntOrNull()
        val nextUrl = if (nextPageNumber != null) {
            url.replace(Regex("""pageNumber=\d+"""), "pageNumber=${nextPageNumber + 1}")
        } else {
            null
        }
        Log.d(TAG, "searchByGenreNextPageFallback | found=${filtered.size} from ${allBooks.size}, nextPage=$nextPageNumber")
        return SearchPage(filtered.distinctBy { it.id }, nextUrl)
    }

    override suspend fun getSeriesBooks(seriesId: String, authorId: String?, limit: Int): List<Book> = withContext(Dispatchers.IO) {
        val url = if (!authorId.isNullOrBlank()) {
            "$baseUrl/opds/authorsequence/$authorId/$seriesId"
        } else {
            "$baseUrl/opds/sequencebooks/$seriesId"
        }
        Log.d(TAG, "getSeriesBooks | $url")
        val pages = fetchAllPages(url)
        val books = mutableListOf<Book>()
        for (xml in pages) {
            if (books.size >= limit) break
            books.addAll(parseOpdsBooks(xml, limit))
        }
        books.sortedBy { it.sequenceNumber }.distinctBy { it.id }
    }

    override suspend fun getBookInfo(id: Int): BookInfo? = withContext(Dispatchers.IO) {
        try {
            val htmlUrl = "$baseUrl/b/$id"
            Log.d(TAG, "getBookInfo | HTML $htmlUrl")
            val html = fetchHtml(htmlUrl)
            parseHtmlBookInfo(html, id)
        } catch (e: Exception) {
            Log.e(TAG, "getBookInfo error", e)
            null
        }
    }

    override fun getDownloadUrl(id: String, format: BookFormat): String {
        val bookId = id.toIntOrNull()
        if (bookId != null) {
            val urls = downloadUrlCache[bookId]
            if (urls != null) {
                val cached = urls[format.extension]
                if (cached != null) {
                    Log.d(TAG, "getDownloadUrl | from cache: $cached")
                    return if (cached.startsWith("http")) cached else "$baseUrl$cached"
                }
            }
        }
        return "$baseUrl/b/$id/${format.extension}"
    }

    override suspend fun downloadBook(id: String, format: BookFormat, onProgress: (Float) -> Unit): ResponseBody? = withContext(Dispatchers.IO) {
        // Собираем URL для попыток: сначала из кэша (реальный URL с HTML-страницы),
        // затем шаблонный URL как fallback
        val urlsToTry = mutableListOf<String>()
        val cachedUrl = getDownloadUrl(id, format)
        urlsToTry.add(cachedUrl)
        val fallbackUrl = "$baseUrl/b/$id/${format.extension}"
        if (fallbackUrl != cachedUrl) {
            urlsToTry.add(fallbackUrl)
        }

        var lastException: Exception? = null

        for (url in urlsToTry) {
            for (attempt in 1..MAX_RETRIES) {
                try {
                    Log.d(TAG, "downloadBook | attempt $attempt: $url")
                    val request = Request.Builder().url(url).build()
                    val response = downloadClient.newCall(request).execute()
                    if (!response.isSuccessful) {
                        val code = response.code
                        response.close()
                        lastException = Exception("HTTP $code")
                        Log.w(TAG, "downloadBook | HTTP $code for $url")
                        // 5xx ошибки — серверные, короткая пауза перед ретраем
                        if (code in 500..599 && attempt < MAX_RETRIES) {
                            delay(1000L * attempt)
                        }
                        continue
                    }
                    // Проверяем Content-Type: если сервер вернул HTML вместо запрошенного
                    // формата (например, PDF недоступен), сохранять такой файл бессмысленно
                    val contentType = response.header("Content-Type", "")
                    if (contentType != null && contentType.startsWith("text/html")) {
                        response.close()
                        lastException = Exception("Сервер вернул HTML вместо ${format.extension}")
                        Log.w(TAG, "downloadBook | HTML instead of ${format.extension} for $url")
                        continue
                    }
                    Log.d(TAG, "downloadBook | success for $url")
                    return@withContext response.body
                } catch (e: Exception) {
                    lastException = e
                    Log.w(TAG, "downloadBook | error", e)
                }
            }
        }
        throw lastException ?: Exception("Download failed")
    }
    // ═══════════════════════════════════════════════════════════════
    //  Pagination — follow <link rel="next">
    // ═══════════════════════════════════════════════════════════════

    private fun fetchAllPages(url: String, maxPages: Int = 5): List<String> {
        val pages = mutableListOf<String>()
        var currentUrl = url
        var seenUrls = mutableSetOf<String>()
        var pageCount = 0
        while (currentUrl.isNotBlank() && pageCount < maxPages) {
            if (currentUrl in seenUrls) break
            seenUrls.add(currentUrl)
            val xml = fetchXml(currentUrl)
            pages.add(xml)
            pageCount++
            currentUrl = parseNextLink(xml)
        }
        return pages
    }

    private fun parseNextLink(xml: String): String {
        val parser = XmlUtils.createParser(xml)
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "link") {
                val rel = parser.getAttributeValue(null, "rel")
                val href = parser.getAttributeValue(null, "href")
                if (rel == "next" && href != null) {
                    return if (href.startsWith("http")) href else "$baseUrl$href"
                }
            }
            eventType = parser.next()
        }
        return ""
    }

    private fun parseNewBooksFast(xml: String, limit: Int): List<Book> {
        val books = mutableListOf<Book>()
        val entryRegex = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
        val entries = entryRegex.findAll(xml).map { it.groupValues[1] }.toList()

        for (entryText in entries) {
            if (books.size >= limit) break

            val id = Regex("""/b/(\d+)""").find(entryText)
                ?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("""tag:book:(\d+)""").find(entryText)
                    ?.groupValues?.get(1)?.toIntOrNull() ?: continue

            val title = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
                .find(entryText)?.groupValues?.get(1)?.trim()?.let { decodeXml(it) }
                ?: continue

            val author = Regex("<author>.*?<name>(.*?)</name>.*?</author>", RegexOption.DOT_MATCHES_ALL)
                .find(entryText)?.groupValues?.get(1)?.trim()?.let { decodeXml(it) }
                ?: "Unknown Author"

            val coverUrl = Regex("""<link\s+href="([^"]*)"\s+rel="http://opds-spec.org/image""")
                .find(entryText)?.groupValues?.get(1)?.let { href ->
                    if (href.startsWith("http")) href else "$baseUrl$href"
                } ?: ""

            val genres = Regex("""<category\s+[^>]*label="([^"]*)""")
                .findAll(entryText).map { decodeXml(it.groupValues[1]) }.toList()

            val description = Regex("<content[^>]*>(.*?)</content>", RegexOption.DOT_MATCHES_ALL)
                .find(entryText)?.groupValues?.get(1)?.let { stripHtmlAndTruncate(it) }
                ?: ""

            val formats = Regex("""<link\s+href="[^"]*/([a-z0-9]+)"\s+rel="http://opds-spec.org/acquisition/open-access""", RegexOption.IGNORE_CASE)
                .findAll(entryText)
                .map { it.groupValues[1] }
                .filter { it in listOf("epub", "fb2", "mobi", "txt", "pdf") }
                .toList()

            books.add(Book(
                id = id,
                title = title,
                author = author,
                link = "/b/$id",
                sendLink = "/send/$id",
                coverUrl = coverUrl,
                genres = genres,
                description = description,
                formats = formats,
            ))
        }
        return books
    }

    // ═══════════════════════════════════════════════════════════════
    //  Parse OPDS entries — books
    // ═══════════════════════════════════════════════════════════════

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
        var inEntry = false
        var hasBookLink = false
        var description = ""

        while (eventType != XmlPullParser.END_DOCUMENT && books.size < limit) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "entry" -> {
                            title = null; id = null; authors.clear(); coverUrl = null; genres.clear(); seqNumber = 0; inEntry = true; hasBookLink = false; description = ""
                        }
                        "title" -> if (inEntry) title = parser.nextText()
                        "id" -> {
                            if (inEntry) {
                                val idText = parser.nextText()
                                // Fantasy-worlds: books:39665  |  CoolLib: tag:book:39665  |  Flibusta: /b/39665
                                val booksMatch = Regex("""books:(\d+)""").find(idText)
                                    ?: Regex("""tag:book:(\d+)""").find(idText)
                                    ?: Regex("""/b/(\d+)""").find(idText)
                                if (booksMatch != null) {
                                    id = booksMatch.groupValues[1].toIntOrNull()
                                    hasBookLink = true
                                }
                            }
                        }
                        "name" -> {
                            if (inEntry) {
                                val authorName = parser.nextText().trim()
                                if (authorName.isNotBlank()) authors.add(authorName)
                            }
                        }
                        "link" -> {
                            if (inEntry) {
                                val rel = parser.getAttributeValue(null, "rel")
                                val href = parser.getAttributeValue(null, "href")
                                if ((rel == "http://opds-spec.org/image" || rel == "http://opds-spec.org/image/thumbnail") && href != null) {
                                    coverUrl = if (href.startsWith("http")) href else "$baseUrl$href"
                                }
                                // Check for book link in Fantasy-worlds format: /lib/id39665/
                                if (href != null) {
                                    val libMatch = Regex("""/lib/id(\d+)""").find(href)
                                    if (libMatch != null) {
                                        hasBookLink = true
                                        if (rel == "alternate" || rel == "http://opds-spec.org/acquisition/open-access") {
                                            id = libMatch.groupValues[1].toIntOrNull()
                                        }
                                    } else {
                                        // Fallback to /b/ format
                                        if (Regex("""/b/(\d+)""").containsMatchIn(href)) {
                                            hasBookLink = true
                                            if (rel == "alternate" || rel == "http://opds-spec.org/acquisition/open-access") {
                                                val matched = Regex("""/b/(\d+)""").find(href)
                                                if (matched != null) {
                                                    id = matched.groupValues[1].toIntOrNull()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "category" -> {
                            if (inEntry) {
                                val label = parser.getAttributeValue(null, "label")
                                if (label != null) genres.add(label)
                            }
                        }
                        "content" -> {
                            if (inEntry) {
                                description = parser.nextText()
                                    .replace(Regex("<[^>]+>"), " ")
                                    .replace(Regex("\\s+"), " ")
                                    .trim()
                                    .take(200)
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name == "entry") {
                        inEntry = false
                        val currentId = id
                        val currentTitle = title
                        if (currentId != null && currentTitle != null && hasBookLink) {
                            val formatsList = mutableListOf<String>()
                            // acquisition links уже обработаны как hasBookLink, извлекаем форматы
                            val formatRegex = Regex("""<link\s+href="[^"]*/([a-z0-9]+)"\s+rel="http://opds-spec.org/acquisition/open-access""")
                            // Форматы извлекать неоткуда в этом парсере, используем стандартный набор
                            books.add(
                                Book(
                                    id = currentId,
                                    title = currentTitle,
                                    author = authors.joinToString(", ").ifBlank { "Unknown Author" },
                                    link = "/b/$currentId",
                                    sendLink = "/send/$currentId",
                                    coverUrl = coverUrl ?: "",
                                    genres = genres.toList(),
                                    sequenceNumber = seqNumber,
                                    description = description,
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

    // ═══════════════════════════════════════════════════════════════
    //  Parse OPDS entries — author IDs
    // ═══════════════════════════════════════════════════════════════

    private fun parseOpdsAuthorIds(xml: String, limit: Int): List<String> {
        val ids = mutableListOf<String>()
        val parser = XmlUtils.createParser(xml)
        var eventType = parser.eventType
        var inEntry = false
        var currentId: String? = null

        while (eventType != XmlPullParser.END_DOCUMENT && ids.size < limit) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name == "entry") { inEntry = true; currentId = null }
                    if (name == "id" && inEntry) {
                        val idText = parser.nextText()
                        val matched = Regex("""/author/(\d+)""").find(idText)
                        if (matched != null) {
                            currentId = matched.groupValues[1]
                        } else {
                            val tagMatch = Regex("""tag:author:(\d+)""").find(idText)
                            if (tagMatch != null) {
                                currentId = tagMatch.groupValues[1]
                            }
                        }
                    }
                    // Fallback: extract author ID from link href
                    if (name == "link" && inEntry && currentId == null) {
                        val href = parser.getAttributeValue(null, "href")
                        if (href != null) {
                            val linkMatch = Regex("""/author/(\d+)""").find(href)
                            if (linkMatch != null) {
                                currentId = linkMatch.groupValues[1]
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name == "entry") {
                        inEntry = false
                        if (currentId != null) {
                            ids.add(currentId)
                            currentId = null
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return ids
    }

    // ═══════════════════════════════════════════════════════════════
    //  Parse author IDs from book entries (search <uri> inside <author>)
    // ═══════════════════════════════════════════════════════════════

    private fun parseOpdsAuthorIdsFromBooks(xml: String, limit: Int): List<String> {
        val ids = mutableListOf<String>()
        val parser = XmlUtils.createParser(xml)
        var eventType = parser.eventType
        var inEntry = false
        var inAuthor = false
        var currentId: String? = null

        while (eventType != XmlPullParser.END_DOCUMENT && ids.size < limit) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name == "entry") { inEntry = true; currentId = null; inAuthor = false }
                    if (name == "author" && inEntry) inAuthor = true
                    if (name == "uri" && inAuthor) {
                        val uri = parser.nextText()
                        val match = Regex("""/a/(\d+)""").find(uri)
                        if (match != null) currentId = match.groupValues[1]
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name == "author") inAuthor = false
                    if (name == "entry") {
                        inEntry = false
                        if (currentId != null) {
                            ids.add(currentId)
                            currentId = null
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return ids
    }

    // ═══════════════════════════════════════════════════════════════
    //  Parse OPDS entries — full author objects
    // ═══════════════════════════════════════════════════════════════

    private fun parseOpdsAuthors(xml: String, limit: Int): List<Author> {
        val authors = mutableListOf<Author>()
        val parser = XmlUtils.createParser(xml)
        var eventType = parser.eventType

        var name: String? = null
        var id: String? = null
        var count = 0
        var portraitUrl: String? = null
        var inEntry = false

        while (eventType != XmlPullParser.END_DOCUMENT && authors.size < limit) {
            val tag = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (tag) {
                        "entry" -> { name = null; id = null; count = 0; portraitUrl = null; inEntry = true }
                        "title" -> if (inEntry) name = parser.nextText()
                        "id" -> {
                            if (inEntry) {
                                val idText = parser.nextText()
                                val matched = Regex("""/author/(\d+)""").find(idText)
                                id = if (matched != null) matched.groupValues[1]
                                else Regex("""tag:author:(\d+)""").find(idText)?.groupValues?.get(1)
                            }
                        }
                        "content" -> {
                            if (inEntry) {
                                val content = parser.nextText()
                                val match = Regex("(\\d+)").find(content)
                                count = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
                            }
                        }
                        "link" -> {
                            if (inEntry) {
                                val rel = parser.getAttributeValue(null, "rel")
                                val href = parser.getAttributeValue(null, "href")
                                if (href != null && id == null) {
                                    val linkMatch = Regex("""/author/(\d+)""").find(href)
                                    if (linkMatch != null) id = linkMatch.groupValues[1]
                                }
                                if ((rel == "http://opds-spec.org/image" || rel == "http://opds-spec.org/image/thumbnail") && href != null) {
                                    portraitUrl = if (href.startsWith("http")) href else "$baseUrl$href"
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (tag == "entry") {
                        inEntry = false
                        val currentId = id
                        val currentName = name
                        if (currentId != null && currentName != null) {
                            authors.add(
                                Author(
                                    authorId = currentId,
                                    name = currentName,
                                    bookCount = count,
                                    portraitUrl = portraitUrl ?: "",
                                )
                            )
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return authors
    }

    // ═══════════════════════════════════════════════════════════════
    //  Parse OPDS entries — series
    // ═══════════════════════════════════════════════════════════════

    private fun parseOpdsSeries(xml: String, limit: Int): List<Series> {
        val seriesList = mutableListOf<Series>()
        val parser = XmlUtils.createParser(xml)
        var eventType = parser.eventType

        var title: String? = null
        var id: String? = null
        var authorId: String? = null
        var count = 0
        var inEntry = false

        while (eventType != XmlPullParser.END_DOCUMENT && seriesList.size < limit) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "entry" -> { title = null; id = null; authorId = null; count = 0; inEntry = true }
                        "title" -> if (inEntry) title = parser.nextText()
                        "id" -> {
                            if (inEntry) {
                                val idText = parser.nextText()
                                // Format: tag:author:{authorId}:sequence:{sequenceId}
                                val authorSeqMatch = Regex("""tag:author:(\d+):sequence:(\d+)""").find(idText)
                                if (authorSeqMatch != null) {
                                    authorId = authorSeqMatch.groupValues[1]
                                    id = authorSeqMatch.groupValues[2]
                                } else {
                                    val seqMatch = Regex("""/sequence/(\d+)""").find(idText)
                                    if (seqMatch != null) id = seqMatch.groupValues[1]
                                }
                            }
                        }
                        "content" -> {
                            if (inEntry) {
                                val content = parser.nextText()
                                val match = Regex("(\\d+)").find(content)
                                count = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name == "entry") {
                        inEntry = false
                        val currentId = id
                        val currentTitle = title
                        if (currentId != null && currentTitle != null) {
                            seriesList.add(
                                Series(
                                    seriesId = currentId,
                                    seriesTitle = currentTitle,
                                    seriesUrl = "$baseUrl/sequence/$currentId",
                                    bookCount = count,
                                    authorId = authorId ?: "",
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

    // ═══════════════════════════════════════════════════════════════
    //  Parse global sequences catalog (/opds/sequences)
    //  Entries have IDs like tag:sequence:96783
    // ═══════════════════════════════════════════════════════════════

    private fun parseOpdsSequences(xml: String, limit: Int): List<Series> {
        val seriesList = mutableListOf<Series>()
        val parser = XmlUtils.createParser(xml)
        var eventType = parser.eventType

        var title: String? = null
        var id: String? = null
        var count = 0
        var inEntry = false

        while (eventType != XmlPullParser.END_DOCUMENT && seriesList.size < limit) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "entry" -> { title = null; id = null; count = 0; inEntry = true }
                        "title" -> if (inEntry) title = parser.nextText()
                        "id" -> {
                            if (inEntry) {
                                val idText = parser.nextText()
                                val seqMatch = Regex("""tag:sequence:(\d+)""").find(idText)
                                if (seqMatch != null) id = seqMatch.groupValues[1]
                            }
                        }
                        "content" -> {
                            if (inEntry) {
                                val content = parser.nextText()
                                val match = Regex("(\\d+)").find(content)
                                count = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name == "entry") {
                        inEntry = false
                        val currentId = id
                        val currentTitle = title
                        if (currentId != null && currentTitle != null) {
                            seriesList.add(
                                Series(
                                    seriesId = currentId,
                                    seriesTitle = currentTitle.trim(),
                                    seriesUrl = "$baseUrl/sequencebooks/$currentId",
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

    // ═══════════════════════════════════════════════════════════════
    //  Parse OPDS — single book info
    // ═══════════════════════════════════════════════════════════════

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
                            if (rel == "http://opds-spec.org/image" && href != null) {
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
                coverUrl = coverUrl ?: ""
            )
        } else null
    }

    // ═══════════════════════════════════════════════════════════════
    //  HTML book info parsing (fallback when OPDS fails)
    // ═══════════════════════════════════════════════════════════════

    private fun parseHtmlBookInfo(html: String, bookId: Int): BookInfo? {
        val doc = Jsoup.parse(html, baseUrl)

        // ── Title ──
        val rawTitle = doc.selectFirst("h1")?.text()?.trim()
            ?: doc.selectFirst("h2")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: return null

        // ── Author: prefer <article>, then /a/\d+, then /author/id, then h1 prefix ──
        val article = doc.selectFirst("article")
        val authorScope = article ?: doc
        val authorFromLink = authorScope.select("a[href]").firstOrNull { a ->
            val href = a.attr("href")
            val text = a.text().trim().lowercase()
            Regex("""/a/(\d+)""").containsMatchIn(href) &&
                text != "все" &&
                text != "флибуста" &&
                text != "coollib" &&
                text != "fantasy-worlds"
        }?.text()?.trim()
            ?: doc.select("a[href*=/author/id]").firstOrNull { a ->
                Regex("""/author/id(\d+)""").find(a.attr("href")) != null
            }?.text()?.trim()

        val authorFromTitle = if (authorFromLink == null) {
            val dash = rawTitle.indexOf(" — ")
            if (dash > 0) rawTitle.substring(0, dash).trim()
            else rawTitle.indexOf(" - ").let { if (it > 0) rawTitle.substring(0, it).trim() else null }
        } else null

        val author = authorFromLink ?: authorFromTitle ?: "Unknown Author"

        // Clean title: strip "Author — " prefix. Делаем всегда, не только когда authorFromTitle.
        val detectedAuthor = authorFromLink ?: authorFromTitle
        val title = if (detectedAuthor != null && rawTitle.startsWith(detectedAuthor)) {
            rawTitle.removePrefix(detectedAuthor).trimStart(' ', '—', '—', '\u2013', '-').trim()
        } else {
            rawTitle
                .let { t -> val d = t.indexOf(" — "); if (d > 0) t.substring(d + 3).trim() else t }
                .let { t -> val d = t.indexOf(" - "); if (d > 0) t.substring(d + 3).trim() else t }
        }

        // ── Cover: prefer /ib/ or /i/{num}/{num}/, exclude icons, then og:image ──
        val coverFromImg = doc.select("img[src]").firstOrNull { img ->
            val src = img.attr("src")
            src.contains("/ib/") ||
                (Regex("""/i/\d+/\d+""").containsMatchIn(src) && !src.endsWith(".gif")) ||
                src.contains("/img/preview/")
        }?.attr("src")

        val coverFromMeta = doc.selectFirst("meta[property=og:image]")?.attr("content")

        val coverUrl = (coverFromImg ?: coverFromMeta)?.let { src ->
            if (src.startsWith("http")) src else "$baseUrl$src"
        } ?: ""

        // ── Description ──
        val description = buildString {
            // Try <h2>Аннотация</h2> (Flibusta style)
            val annotationH2 = doc.selectFirst("h2:contains(Аннотация)")
            if (annotationH2 != null) {
                val node = annotationH2.nextSibling()
                val parts = mutableListOf<String>()
                var current = node
                while (current != null) {
                    if (current is org.jsoup.nodes.Element && current.tagName() == "h2") break
                    val text = current.toString().trim()
                    if (text.isNotBlank()) parts.add(text)
                    current = current.nextSibling()
                }
                val raw = parts.joinToString(" ")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                if (raw.isNotBlank() && !raw.startsWith("отсутствует")) {
                    append(raw.take(500))
                }
            }
            // Fallback: <meta property="og:description"> (FantasyWorlds)
            if (isEmpty()) {
                doc.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { append(it.take(500)) }
            }
            // Fallback: <meta name="description">
            if (isEmpty()) {
                doc.selectFirst("meta[name=description]")?.attr("content")?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { append(it.take(500)) }
            }
            // Fallback: first meaningful paragraph
            if (isEmpty()) {
                for (p in doc.select("p")) {
                    val text = p.text().trim()
                    if (text.length > 30 && text.length < 2000 &&
                        !text.contains("Оценки") && !text.contains("Рекомендации")) {
                        append(text)
                        break
                    }
                }
            }
        }

        // ── Genres ──
        val genres = mutableListOf<BookGenre>()
        val genreLinks = doc.select("a[href^=/g/]")
        for (genreLink in genreLinks) {
            val genreId = genreLink.attr("href").removePrefix("/g/")
            val genreTitle = genreLink.text().trim()
            if (genreTitle.isNotBlank()) {
                genres.add(BookGenre(id = genreId, title = genreTitle))
            }
        }

        // ── Available formats: ищем ссылки скачивания на странице книги ──
        val formatExtensions = listOf("epub", "fb2", "mobi", "txt", "pdf")
        val formatSet = mutableSetOf<String>()
        val urlMap = mutableMapOf<String, String>()

        for (link in doc.select("a[href]")) {
            val href = link.attr("href")
            val text = link.text().lowercase()

            // Определяем формат по тексту или href
            val fmt = formatExtensions.firstOrNull { ext ->
                (text.contains(ext) && (text.contains("скачать") || text.contains("download")))
            } ?: formatExtensions.firstOrNull { ext ->
                (href.endsWith("/$ext") || href.endsWith(".$ext")) &&
                (href.contains("/b/$bookId/") || href.contains("/lib/id${bookId}/download/") || href.contains("$bookId.$ext"))
            }

            if (fmt != null) {
                formatSet.add(fmt)
                // Сохраняем реальный URL скачивания (первый найденный для каждого формата)
                if (fmt !in urlMap) {
                    urlMap[fmt] = href
                }
            }
        }

        val availableFormats = formatSet.toList()

        // Сохраняем URL скачивания в кэш для последующего использования в getDownloadUrl
        if (urlMap.isNotEmpty()) {
            downloadUrlCache[bookId] = urlMap
        }

        return BookInfo(
            id = bookId,
            title = title,
            author = author,
            description = description,
            genres = genres,
            coverUrl = coverUrl,
            availableFormats = availableFormats
        )
    }

    // ═══════════════════════════════════════════════════════════════
    //  Network helpers
    // ═══════════════════════════════════════════════════════════════


    private fun stripHtmlAndTruncate(html: String): String {
        val clean = html
            .let { decodeXml(it) }
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .replace(Regex("^[Гг]од издани[яе].*$"), "")
            .trim()
        if (clean.isBlank()) return ""
        return if (clean.length > 200) clean.take(200) + "\u2026" else clean
    }

    private fun decodeXml(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
    }

    private fun parseBookId(idText: String): Int? {
        Regex("""/b/(\d+)""").find(idText)?.let { return it.groupValues[1].toIntOrNull() }
        Regex("""/book/(\d+)""").find(idText)?.let { return it.groupValues[1].toIntOrNull() }
        Regex("""[:/](\d{3,})$""").find(idText)?.let { return it.groupValues[1].toIntOrNull() }
        return null
    }

    private fun parseOpdsSeriesFromBooks(xml: String, limit: Int): List<Series> {
        val seriesList = mutableListOf<Series>()
        val parser = XmlUtils.createParser(xml)
        var eventType = parser.eventType
        var inEntry = false
        val seenIds = mutableSetOf<String>()

        while (eventType != XmlPullParser.END_DOCUMENT && seriesList.size < limit) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (name == "entry") inEntry = true
                    if (name == "link" && inEntry) {
                        val href = parser.getAttributeValue(null, "href") ?: ""
                        val linkRel = parser.getAttributeValue(null, "rel") ?: ""
                        if (href.contains("/opds/sequencebooks/") && linkRel == "related") {
                            val idMatch = Regex("""/opds/sequencebooks/(\d+)""").find(href)
                            if (idMatch != null) {
                                val seriesId = idMatch.groupValues[1]
                                if (seriesId !in seenIds) {
                                    seenIds.add(seriesId)
                                    var rawTitle = parser.getAttributeValue(null, "title") ?: ""
                                    val title = rawTitle
                                        .replace(Regex("""^Все книги серии[ :]+"""), "")
                                        .trim(' ', '"', '\u00ab', '\u00bb', '\u201c', '\u201d')
                                    seriesList.add(
                                        Series(
                                            seriesId = seriesId,
                                            seriesTitle = title,
                                            seriesUrl = "$baseUrl/opds/sequencebooks/$seriesId",
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name == "entry") inEntry = false
                }
            }
            eventType = parser.next()
        }
        return seriesList
    }

    private fun fetchHtml(url: String): String {
        return networkClient.fetchWithMirrorRetry(url, client)
    }

    private fun fetchXml(url: String): String {
        return networkClient.fetchWithMirrorRetry(url, client)
    }
}
