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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Отменяемый HTTP-запрос: если coroutine отменена (например, по withTimeout),
 * OkHttp-вызов тоже отменяется, и IO-поток освобождается.
 */
private suspend fun OkHttpClient.executeCancellable(request: Request): Response {
    return suspendCancellableCoroutine { continuation ->
        val call = newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) {
                    continuation.resumeWithException(e)
                }
            }
        })
    }
}

class FlibustaApiOpdsImpl(
    private val networkClient: OkHttpClientProvider
) : FlibustaApi {

    private companion object {
        const val TAG = "FLIBUSTA_OPDS"
        const val MAX_RETRIES = 3
        const val ALL_RESULTS = Int.MAX_VALUE
        const val MAX_AUTHORS_TO_CRAWL = 3
        const val MAX_SEQ_PAGES = 15

        // Кеш всех серий из /opds/sequences — заполняется при первом поиске,
        // потом поиск серий работает мгновенно без пагинации по сотням страниц
        @Volatile
        private var seriesCache: Map<String, List<Series>>? = null // normalizedTitle -> series list
        @Volatile
        private var isCacheBuilding = false
        private val cacheLock = Any()
    }

    private val client = networkClient.client
    private val downloadClient = networkClient.createDownloadClient()

    private val parser = OpdsParser(
        baseUrlProvider = { networkClient.getBaseUrl() },
        fetchFn = { url -> networkClient.fetchWithMirrorRetry(url, client) }
    )

    // Кэш реальных URL скачивания из HTML-страницы книги (bookId -> format -> url)
    private val downloadUrlCache = mutableMapOf<Int, Map<String, String>>()

    /**
     * Если у книги нет обложки из OPDS (или обложка — заглушка) — конструируем по паттерну Флибусты:
     * https://flibusta.is/i/{id % 100}/{id}/cover.jpg
     * Это работает для ~80% книг и устраняет необходимость в отдельных
     * HTTP-запросах getBookInfo для получения обложки.
     */
    private fun fillCoverUrl(book: Book): Book {
        val cv = book.coverUrl
        if (cv.isNotBlank() && !cv.endsWith("/cover") && cv != "/cover") return book
        val folder = book.id % 100
        val constructedUrl = "${parser.getBaseUrl()}/i/$folder/${book.id}/cover.jpg"
        return book.copy(coverUrl = constructedUrl)
    }

    override suspend fun getNewBooks(limit: Int): List<Book> = withContext(Dispatchers.IO) {
        val url = "${parser.getBaseUrl()}/opds/new/0/new"
        Log.d(TAG, "getNewBooks | $url")
        val xml = parser.fetchXml(url)
        val books = parser.parseNewBooksFast(xml, limit.coerceAtMost(50)).map { fillCoverUrl(it) }
        Log.d(TAG, "getNewBooks | found=${books.size}")
        books.distinctBy { it.id }
    }

    override suspend fun getNewBooksFirstPage(): NewBooksPage = withContext(Dispatchers.IO) {
        val url = "${parser.getBaseUrl()}/opds/new/0/new"
        Log.d(TAG, "getNewBooksFirstPage | $url")
        val xml = parser.fetchXml(url)
        val books = parser.parseNewBooksFast(xml, 50).map { fillCoverUrl(it) }
        val nextUrl = if (books.isNotEmpty()) {
            val fromLink = parser.parseNextLink(xml).let {
                if (it.isNotBlank()) (if (it.startsWith("http")) it else "${parser.getBaseUrl()}$it") else null
            }
            fromLink ?: "${parser.getBaseUrl()}/opds/new/${books.size}/new"
        } else null
        NewBooksPage(books.distinctBy { it.id }, nextUrl)
    }

    override suspend fun getNewBooksNextPage(url: String): NewBooksPage = withContext(Dispatchers.IO) {
        Log.d(TAG, "getNewBooksNextPage | $url")
        val xml = parser.fetchXml(url)
        val books = parser.parseNewBooksFast(xml, 50).map { fillCoverUrl(it) }
        val nextUrl = if (books.isNotEmpty()) {
            val fromLink = parser.parseNextLink(xml).let {
                if (it.isNotBlank()) (if (it.startsWith("http")) it else "${parser.getBaseUrl()}$it") else null
            }
            if (fromLink != null) fromLink else {
                val offsetMatch = Regex("""/opds/new/(\d+)/new""").find(url)
                val offset = offsetMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                "${parser.getBaseUrl()}/opds/new/${offset + books.size}/new"
            }
        } else null
        NewBooksPage(books.distinctBy { it.id }, nextUrl)
    }

    override suspend fun searchBooks(query: String, limit: Int): SearchPage<Book> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "${parser.getBaseUrl()}/opds/search?searchType=books&searchTerm=$encoded"
        Log.d(TAG, "searchBooks | $url")
        val xml = parser.fetchXml(url)
        val books = parser.parseOpdsBooks(xml, limit).map { fillCoverUrl(it) }
        val nextUrl = if (books.isNotEmpty()) parser.parseNextLink(xml).let { if (it.isNotBlank()) it else null } else null
        Log.d(TAG, "searchBooks | found=${books.size}, next=$nextUrl")
        SearchPage(books.distinctBy { it.id }, nextUrl)
    }

    override suspend fun searchBooksNextPage(url: String, limit: Int): SearchPage<Book> = withContext(Dispatchers.IO) {
        val xml = parser.fetchXml(url)
        val books = parser.parseOpdsBooks(xml, limit).map { fillCoverUrl(it) }
        val nextUrl = if (books.isNotEmpty()) parser.parseNextLink(xml).let { if (it.isNotBlank()) it else null } else null
        SearchPage(books.distinctBy { it.id }, nextUrl)
    }

    override suspend fun searchByAuthor(query: String, limit: Int): SearchPage<Author> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "${parser.getBaseUrl()}/opds/search?searchType=authors&searchTerm=$encoded"
        Log.d(TAG, "searchByAuthor | $url")
        val xml = parser.fetchXml(url)
        val authors = parser.parseOpdsAuthors(xml, limit)
        val nextUrl = if (authors.isNotEmpty()) parser.parseNextLink(xml).let { if (it.isNotBlank()) it else null } else null
        SearchPage(authors.distinctBy { it.authorId }, nextUrl)
    }

    override suspend fun searchByAuthorNextPage(url: String, limit: Int): SearchPage<Author> = withContext(Dispatchers.IO) {
        val xml = parser.fetchXml(url)
        val authors = parser.parseOpdsAuthors(xml, limit)
        val nextUrl = if (authors.isNotEmpty()) parser.parseNextLink(xml).let { if (it.isNotBlank()) it else null } else null
        SearchPage(authors.distinctBy { it.authorId }, nextUrl)
    }

    override suspend fun getAuthorBooks(authorId: String, limit: Int): List<Book> = withContext(Dispatchers.IO) {
        val url = "${parser.getBaseUrl()}/opds/author/$authorId/alphabet"
        Log.d(TAG, "getAuthorBooks | $url")
        val pages = parser.fetchAllPages(url)
        val books = mutableListOf<Book>()
        for (xml in pages) {
            if (books.size >= limit) break
            books.addAll(parser.parseOpdsBooks(xml, limit).map { fillCoverUrl(it) })
        }
        books.sortedBy { it.sequenceNumber }.distinctBy { it.id }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Series cache — загружаем все серии один раз в память
    // ═══════════════════════════════════════════════════════════════

    /**
     * Собирает кеш всех серий из /opds/sequences (с пагинацией по rel="next").
     * Вызывается один раз при первом поиске серий. Результат хранится в памяти.
     */
    private suspend fun ensureSeriesCache() {
        if (seriesCache != null || isCacheBuilding) return
        synchronized(cacheLock) {
            if (seriesCache != null || isCacheBuilding) return
            isCacheBuilding = true
        }

        Log.d(TAG, "ensureSeriesCache | building series cache...")
        val startTime = System.currentTimeMillis()
        val cache = mutableMapOf<String, MutableList<Series>>()
        var currentUrl = "${parser.getBaseUrl()}/opds/sequences"
        var pageCount = 0
        val seenUrls = mutableSetOf<String>()

        try {
            while (currentUrl.isNotBlank() && pageCount < 200) {
                if (currentUrl in seenUrls) break
                seenUrls.add(currentUrl)

                val xml = try {
                    parser.fetchXml(currentUrl)
                } catch (e: Exception) {
                    Log.w(TAG, "ensureSeriesCache | page $pageCount failed: ${e.message}")
                    break
                }

                val series = parser.parseOpdsSequences(xml, ALL_RESULTS)
                for (s in series) {
                    val key = s.seriesTitle.trim().lowercase()
                        .replace(Regex("""[.,!?;:'\"«»()\[\]{}–—]"""), "")
                    cache.getOrPut(key) { mutableListOf() }.add(s)
                }

                pageCount++
                currentUrl = parser.parseNextLink(xml)
            }
        } catch (e: Exception) {
            Log.e(TAG, "ensureSeriesCache | error: ${e.message}")
        }

        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "ensureSeriesCache | done: ${cache.size} unique titles, ${pageCount} pages, ${elapsed}ms")

        synchronized(cacheLock) {
            seriesCache = cache
            isCacheBuilding = false
        }
    }

    override suspend fun searchBySeries(query: String, limit: Int): SearchPage<Series> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().lowercase()
            .replace(Regex("""[.,!?;:'\"«»()\[\]{}]"""), "")  // убираем пунктуацию для поиска
        val allSeries = mutableListOf<Series>()
        val encoded = URLEncoder.encode(query, "UTF-8")

        // 0. Сначала пробуем кеш всех серий (быстро!)
        ensureSeriesCache()
        val cache = seriesCache
        if (cache != null) {
            val cached = cache.filterKeys { it.contains(cleanQuery) }.values.flatten()
            if (cached.isNotEmpty()) {
                val result = cached.distinctBy { it.seriesId }.take(limit)
                Log.d(TAG, "searchBySeries | cache hit: ${result.size} series")
                return@withContext SearchPage(result, null)
            }
        }

        Log.d(TAG, "searchBySeries | cache miss, trying API search...")

        // 1. Пагинация поиска книг — чем больше книг, тем больше серий
        val bookUrl = "${parser.getBaseUrl()}/opds/search?searchType=books&searchTerm=$encoded"
        Log.d(TAG, "searchBySeries | books -> $bookUrl")
        try {
            val bookPages = parser.fetchAllPages(bookUrl)
            for (xml in bookPages) {
                allSeries.addAll(parser.parseOpdsSeriesFromBooks(xml, limit))
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchBySeries | books failed", e)
        }

        // 2. Поиск серий через авторские последовательности (как в Fantasy Worlds)
        try {
            val authorIds = mutableListOf<String>()
            // Собираем ID авторов из книг с пагинацией
            for (xml in parser.fetchAllPages(bookUrl)) {
                authorIds.addAll(parser.parseOpdsAuthorIdsFromBooks(xml, Int.MAX_VALUE))
            }
            authorIds.distinct().take(5).chunked(2).forEach { batch ->
                coroutineScope {
                    batch.map { id ->
                        async {
                            try {
                                parser.fetchAllPages("${parser.getBaseUrl()}/opds/authorsequences/$id")
                                    .flatMap { parser.parseOpdsSeries(it, limit) }
                            } catch (e: Exception) { emptyList() }
                        }
                    }.forEach { allSeries.addAll(it.await()) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchBySeries | author sequences failed", e)
        }

        // 3. Поиск в общих сериях с пагинацией (follow rel="next")
        try {
            val sequencePages = parser.fetchAllPages("${parser.getBaseUrl()}/opds/sequences", maxPages = 15)
            for (xml in sequencePages) {
                allSeries.addAll(parser.parseOpdsSequences(xml, limit))
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchBySeries | sequences failed", e)
        }

        val filtered = allSeries.filter {
            it.seriesTitle.trim().lowercase()
                .replace(Regex("""[.,!?;:'\"«»()\[\]{}]"""), "")
                .contains(cleanQuery)
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

    /**
     * Состояние пагинации поиска по жанрам.
     */
    @Volatile
    private var genreState: GenreSearchState? = null

    private data class GenreSearchState(
        val matchingGenres: List<OpdsParser.GenreNavEntry>,
        val currentGenreIndex: Int,
        val subGenres: List<OpdsParser.GenreNavEntry>,
        val currentSubGenreIndex: Int,
        val subGenreNextUrl: String?
    )

    override suspend fun searchByGenre(query: String, limit: Int): SearchPage<Book> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().lowercase()
            .replace(Regex("""[.,!?;:'\"«»()\[\]{}]"""), "")
        Log.d(TAG, "searchByGenre | query=$cleanQuery, limit=$limit")

        // 1. Загружаем /opds/genres — список всех жанров
        val genresXml = try {
            parser.fetchXml("${parser.getBaseUrl()}/opds/genres")
        } catch (e: Exception) {
            Log.e(TAG, "searchByGenre | /opds/genres failed, falling back to search", e)
            return@withContext searchByGenreFallback(query, limit, cleanQuery)
        }
        val allGenres = parser.parseNavEntries(genresXml)
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
            parser.fetchXml(matching[0].url)
        } catch (e: Exception) {
            Log.e(TAG, "searchByGenre | sub-genre feed failed for '${matching[0].name}', falling back", e)
            return@withContext searchByGenreFallback(query, limit, cleanQuery)
        }
        val subGenres = parser.parseNavEntries(subGenresXml)
        Log.d(TAG, "searchByGenre | '${matching[0].name}' has ${subGenres.size} sub-genres")

        // 4. Перебираем поджанры, пока не найдём книги
        val books = mutableListOf<Book>()
        var foundSubGenreIndex = -1
        var nextSubUrl: String? = null

        for (i in subGenres.indices) {
            if (books.size >= limit) break
            val sub = subGenres[i]
            try {
                val subXml = parser.fetchXml(sub.url)
                val subBooks = parser.parseOpdsBooks(subXml, limit - books.size)
                if (subBooks.isNotEmpty()) {
                    books.addAll(subBooks)
                    foundSubGenreIndex = i
                    nextSubUrl = parser.parseNextLink(subXml).let { if (it.isNotBlank()) it else null }
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
        SearchPage(books.distinctBy { it.id }.map { fillCoverUrl(it) }, if (hasMore) "genre_next://0" else null)
    }

    /**
     * Fallback: поиск через /opds/search + клиентская фильтрация по жанру.
     * Используется когда OPDS-каталог жанров недоступен.
     */
    private suspend fun searchByGenreFallback(query: String, limit: Int, cleanQuery: String): SearchPage<Book> {
        Log.d(TAG, "searchByGenreFallback | query=$query")
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "${parser.getBaseUrl()}/opds/search?searchType=books&searchTerm=$encoded"
        val xml = try {
            parser.fetchXml(url)
        } catch (e: Exception) {
            Log.e(TAG, "searchByGenreFallback | search failed", e)
            return SearchPage(emptyList())
        }
        val allBooks = parser.parseOpdsBooks(xml, limit).map { fillCoverUrl(it) }
        val filtered = allBooks.filter { book ->
            book.genres.any { it.lowercase().contains(cleanQuery) }
        }
        val nextUrl = if (filtered.isNotEmpty()) "$url&pageNumber=1" else null
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
                parser.fetchXml(currentState.subGenreNextUrl)
            } catch (e: Exception) {
                Log.e(TAG, "searchByGenreNextPage | fetch next failed", e)
                null
            }
            if (xml != null) {
                    val books = parser.parseOpdsBooks(xml, limit).map { fillCoverUrl(it) }
                    val nextNext = parser.parseNextLink(xml).let { if (it.isNotBlank()) it else null }
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
                    val subXml = parser.fetchXml(sub.url)
                    val subBooks = parser.parseOpdsBooks(subXml, limit).map { fillCoverUrl(it) }
                    if (subBooks.isNotEmpty()) {
                        val nextInSub = parser.parseNextLink(subXml).let { if (it.isNotBlank()) it else null }
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
                val subGenresXml = parser.fetchXml(nextGenre.url)
                val subGenres = parser.parseNavEntries(subGenresXml)
                for (i in subGenres.indices) {
                    val sub = subGenres[i]
                    try {
                        val subXml = parser.fetchXml(sub.url)
                        val subBooks = parser.parseOpdsBooks(subXml, limit).map { fillCoverUrl(it) }
                        if (subBooks.isNotEmpty()) {
                            val nextInSub = parser.parseNextLink(subXml).let { if (it.isNotBlank()) it else null }
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
            parser.fetchXml(url)
        } catch (e: Exception) {
            Log.e(TAG, "searchByGenreNextPageFallback | fetch failed", e)
            return SearchPage(emptyList())
        }
        val allBooks = parser.parseOpdsBooks(xml, limit).map { fillCoverUrl(it) }
        // Извлекаем searchTerm из URL для клиентской фильтрации
        val searchTerm = Regex("""searchTerm=([^&]+)""").find(url)?.groupValues?.get(1) ?: ""
        val cleanQuery = java.net.URLDecoder.decode(searchTerm, "UTF-8").lowercase()
            .replace(Regex("""[.,!?;:'\"«»()\[\]{}]"""), "")
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
            "${parser.getBaseUrl()}/opds/authorsequence/$authorId/$seriesId"
        } else {
            "${parser.getBaseUrl()}/opds/sequencebooks/$seriesId"
        }
        Log.d(TAG, "getSeriesBooks | $url")
        val pages = parser.fetchAllPages(url)
        val books = mutableListOf<Book>()
        for (xml in pages) {
            if (books.size >= limit) break
            books.addAll(parser.parseOpdsBooks(xml, limit).map { fillCoverUrl(it) })
        }
        books.sortedBy { it.sequenceNumber }.distinctBy { it.id }
    }

    override suspend fun getBookInfo(id: Int): BookInfo? = withContext(Dispatchers.IO) {
        // Пробуем все зеркала: книга может быть доступна на одном зеркале и удалена на другом.
        // Используем downloadClient напрямую (не fetchWithMirrorRetry), чтобы
        // контролировать переключение зеркал самостоятельно.
        val mirrors = networkClient.getMirrors()
        var lastError: String? = null

        for (mirror in mirrors) {
            val baseUrl = mirror.trimEnd('/')
            val htmlUrl = "$baseUrl/b/$id"
            try {
                Log.d(TAG, "getBookInfo | HTML $htmlUrl")
                val request = Request.Builder().url(htmlUrl).build()
                val response = client.executeCancellable(request)
                if (!response.isSuccessful) {
                    val code = response.code
                    response.close()
                    lastError = "HTTP $code на $mirror"
                    Log.d(TAG, "getBookInfo | $lastError, trying next mirror")
                    continue
                }
                val html = response.body?.string() ?: ""
                response.close()

                // Если страница — заглушка (каталог/поиск вместо книги) → книги нет на этом зеркале
                if (html.contains("<h1 class=\"title\">Книги</h1>") ||
                    html.contains("Результат поиска") ||
                    html.contains("Книга удалена из библиотеки", ignoreCase = true)
                ) {
                    lastError = "Book $id not found on $mirror"
                    Log.d(TAG, "getBookInfo | $lastError, trying next mirror")
                    continue
                }

                val info = parseHtmlBookInfo(html, id)

                // Если книга удалена/заменена — пробуем найти замену
                if (info == null) {
                    val replacementMatch = Regex("""заменена на\s+<a\s+href="/b/(\d+)"">""")
                        .find(html)
                    if (replacementMatch != null) {
                        val newId = replacementMatch.groupValues[1].toIntOrNull()
                        if (newId != null && newId != id) {
                            Log.d(TAG, "getBookInfo | book $id → replaced by $newId, redirecting")
                            return@withContext getBookInfo(newId)
                        }
                    }
                    continue
                }

                // Если нашли книгу на неактивном зеркале — переключаемся на него
                if (mirror != networkClient.getBaseUrl()) {
                    networkClient.switchMirror(mirror)
                }

                return@withContext info
            } catch (e: Exception) {
                lastError = "${e.message}"
                Log.w(TAG, "getBookInfo | error on $mirror: ${e.message}, trying next mirror")
            }
        }
        Log.e(TAG, "getBookInfo | all mirrors failed: $lastError")
        null
    }

    override fun getDownloadUrl(id: String, format: BookFormat): String {
        val bookId = id.toIntOrNull()
        if (bookId != null) {
            val urls = downloadUrlCache[bookId]
            if (urls != null) {
                val cached = urls[format.extension]
                if (cached != null) {
                    Log.d(TAG, "getDownloadUrl | from cache: $cached")
                    return if (cached.startsWith("http")) cached else "${parser.getBaseUrl()}$cached"
                }
            }
        }
        return "${parser.getBaseUrl()}/b/$id/${format.extension}"
    }

    override suspend fun downloadBook(id: String, format: BookFormat, onProgress: (Float) -> Unit): ResponseBody? = withContext(Dispatchers.IO) {
        // Собираем URL для попыток: сначала из кэша (реальный URL с HTML-страницы),
        // затем несколько шаблонов URL для разных источников
        val baseUrl = parser.getBaseUrl()
        val ext = format.extension
        val urlsToTry = mutableListOf<String>()
        val cachedUrl = getDownloadUrl(id, format)
        urlsToTry.add(cachedUrl)

        // Flibusta: /b/12345/epub
        // CoolLib: /b/12345/epub
        // Fantasy-worlds: свой формат в отдельной реализации
        val altPatterns = listOf(
            "$baseUrl/b/$id/$ext",
        )
        for (pattern in altPatterns) {
            if (pattern !in urlsToTry) urlsToTry.add(pattern)
        }

        val mirrors = networkClient.getMirrors()
        var lastException: Exception? = null

        for (url in urlsToTry) {
            for (mirror in mirrors) {
                val mirrorUrl = url.replace(Regex("https?://[^/]+"), mirror)
                for (attempt in 1..MAX_RETRIES) {
                    try {
                        Log.d(TAG, "downloadBook | attempt $attempt: $mirrorUrl")
                        val request = Request.Builder().url(mirrorUrl).build()
                        val response = downloadClient.newCall(request).execute()
                        if (!response.isSuccessful) {
                            val code = response.code
                            response.close()
                            lastException = Exception("HTTP $code")
                            Log.w(TAG, "downloadBook | HTTP $code for $mirrorUrl")
                            // 5xx ошибки — серверные, короткая пауза перед ретраем
                            if (code in 500..599 && attempt < MAX_RETRIES) {
                                delay(1000L * attempt)
                            }
                            // Для 4xx ошибок (кроме 429/403) нет смысла ретраить то же зеркало,
                            // но следующее зеркало в цикле может сработать
                            if (code in 400..499 && code !in listOf(429, 403)) {
                                break // переходим к следующему зеркалу
                            }
                            continue
                        }
                        // Проверяем Content-Type: если сервер вернул HTML вместо запрошенного
                        // формата (например, PDF недоступен), сохранять такой файл бессмысленно.
                        // Дёргать другие зеркала тоже бесполезно — у той же книги на другом
                        // зеркале тот же набор форматов.
                        val contentType = response.header("Content-Type", "")
                        if (contentType != null && contentType.startsWith("text/html")) {
                            response.close()
                            val msg = "Формат ${format.extension} недоступен для этой книги"
                            Log.w(TAG, "downloadBook | $msg")
                            throw Exception(msg)
                        }
                        Log.d(TAG, "downloadBook | success for $mirrorUrl")
                        return@withContext response.body
                    } catch (e: Exception) {
                        lastException = e
                        Log.w(TAG, "downloadBook | error", e)
                    }
                }
            }
        }
        throw lastException ?: Exception("Download failed")
    }

    // ═══════════════════════════════════════════════════════════════
    //  HTML book info parsing (Flibusta-specific)
    //  Uses downloadUrlCache to store real download URLs found on the page.
    // ═══════════════════════════════════════════════════════════════

    private fun parseHtmlBookInfo(html: String, bookId: Int): BookInfo? {
        val doc = Jsoup.parse(html, parser.getBaseUrl())

        // ── Title ──
        val rawTitle = doc.selectFirst("h1")?.text()?.trim()
            ?: doc.selectFirst("h2")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: return null

        // ── Check if book is deleted/replaced on Flibusta ──
        // Example: <h4>Книга <a href="/b/9">9</a> заменена на <a href="/b/114062">исправленную</a></h4>
        val bodyText = doc.body()?.text() ?: ""
        if (bodyText.contains("книга удалена из библиотеки", ignoreCase = true) ||
            bodyText.contains("заменена на", ignoreCase = true)
        ) {
            Log.d(TAG, "parseHtmlBookInfo | book $bookId is deleted or replaced on Flibusta")
            return null
        }

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

        // Clean title: strip "Author — " prefix
        val detectedAuthor = authorFromLink ?: authorFromTitle
        val title = if (detectedAuthor != null && rawTitle.startsWith(detectedAuthor)) {
            rawTitle.removePrefix(detectedAuthor).trimStart(' ', '—', '—', '–', '-').trim()
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
            if (src.startsWith("http")) src else "${parser.getBaseUrl()}$src"
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
                    if (current is org.jsoup.nodes.Element) {
                        val text = current.text().trim()
                        if (text.isNotBlank() && !text.startsWith("Оценки") && !text.startsWith("Рекомендации")) {
                            parts.add(text)
                        }
                    } else {
                        val text = current.toString().trim()
                        if (text.isNotBlank()) parts.add(text)
                    }
                    current = current.nextSibling()
                }
                val raw = parts.joinToString(" ")
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
            // Fallback: first meaningful paragraph in main content area
            if (isEmpty()) {
                for (p in doc.select("#main p, .content p, article p")) {
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
                val lastSegment = href.substringAfterLast("/")
                val fullPath = href.substringAfter("://").substringAfter("/")
                (lastSegment == ext || lastSegment == "$bookId.$ext" || fullPath.contains("$bookId/$ext")) &&
                (href.contains("/b/$bookId/") || href.contains("/dl/") || href.contains("/download/") ||
                 href.contains("/lib/id${bookId}/download/") || href.contains("$bookId.$ext"))
            } ?: formatExtensions.firstOrNull { ext ->
                // Третья попытка: любой href, оканчивающийся на /ext или .ext,
                // если в href есть bookId или текст ссылки содержит название формата
                val lastSegment = href.substringAfterLast("/").lowercase()
                (lastSegment == ext || lastSegment.endsWith(".$ext")) &&
                (href.contains("/$bookId/") || href.contains("$bookId.") || text.contains(ext))
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
}
