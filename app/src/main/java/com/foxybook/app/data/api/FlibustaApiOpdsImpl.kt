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
<<<<<<< HEAD
=======
import com.foxybook.app.core.network.executeCancellable
>>>>>>> d24a20e (FoxyBook v1.9)
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
<<<<<<< HEAD
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
=======
import kotlinx.coroutines.withTimeoutOrNull
>>>>>>> d24a20e (FoxyBook v1.9)
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URLEncoder
<<<<<<< HEAD
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
=======
import java.util.concurrent.ConcurrentHashMap
import com.foxybook.app.core.utils.safeLogUrl
>>>>>>> d24a20e (FoxyBook v1.9)

/**
 * Flibusta и CoolLib: оба каталога — одно и то же ПО (Flibusta.In), поэтому
 * адреса, формат `<id>` и структура фидов совпадают.
 *
 * Зеркала флибусты отвечают капризно (часть страниц отдаётся по 20 секунд),
 * поэтому здесь нет ничего, чтоDepends от количества страниц: серии ищутся
 * через поиск по книгам (одна выдача сразу содержит rel="related" на серию),
 * а не обходом каталога из сотен страниц.
 */
class FlibustaApiOpdsImpl(
    private val networkClient: OkHttpClientProvider
) : FlibustaApi {

    private companion object {
        const val TAG = "FLIBUSTA_OPDS"
        const val MAX_RETRIES = 3
<<<<<<< HEAD
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
=======
        const val PAGE_LIMIT = 50
        /** Сколько страниц выдачи максимум забирать за один вызов. */
        const val MAX_RESULT_PAGES = 3
        /** Таймаут одной страницы при обходе с откатом на следующую. */
        const val FALLBACK_PAGE_TIMEOUT_MS = 6_000L
        /** Максимум серий, которые собираем из постраничного обхода каталога. */
        const val MAX_FALLBACK_SERIES_PAGES = 3
        /** Общий бюджет времени на резервный обход каталога серий (мс). */
        const val FALLBACK_SERIES_TIMEOUT_MS = 20_000L
        /** Сколько поджанров проверяем параллельно. */
        const val SUBGENRE_PARALLELISM = 4
        /** Префикс маркера пагинации жанров (внутри ViewModel не разбирается). */
        const val GENRE_MARKER_PREFIX = "genre_next://"
    }

    private val client = networkClient.client
    private val downloadClient = networkClient.downloadClient

    private val parser = OpdsParser(
        baseUrlProvider = { networkClient.getBaseUrl() },
        fetchFn = { url -> networkClient.fetchWithMirrorRetry(url, client) }
    )

    /** Реальные URL скачивания, найденные на HTML-странице книги (bookId -> format -> url). */
    private val downloadUrlCache = ConcurrentHashMap<Int, Map<String, String>>()

    private val baseUrl: String get() = parser.getBaseUrl()

    /**
     * Если у книги нет обложки из OPDS — конструируем по паттерну Флибусты:
     * https://host/i/{id % 100}/{id}/cover.jpg
     * Это работает для большинства книг и устраняет необходимость в отдельном
     * запросе getBookInfo только ради обложки.
>>>>>>> d24a20e (FoxyBook v1.9)
     */
    private fun fillCoverUrl(book: Book): Book {
        val cv = book.coverUrl
        if (cv.isNotBlank() && !cv.endsWith("/cover") && cv != "/cover") return book
        val folder = book.id % 100
<<<<<<< HEAD
        val constructedUrl = "${parser.getBaseUrl()}/i/$folder/${book.id}/cover.jpg"
        return book.copy(coverUrl = constructedUrl)
    }

    override suspend fun getNewBooks(limit: Int): List<Book> = withContext(Dispatchers.IO) {
        val url = "${parser.getBaseUrl()}/opds/new/0/new"
        Log.d(TAG, "getNewBooks | $url")
        val xml = parser.fetchXml(url)
        val books = parser.parseNewBooksFast(xml, limit.coerceAtMost(50)).map { fillCoverUrl(it) }
=======
        return book.copy(coverUrl = "$baseUrl/i/$folder/${book.id}/cover.jpg")
    }

    // ═══════════════════════════════════════════════════════════════
    //  Новинки
    // ═══════════════════════════════════════════════════════════════

    override suspend fun getNewBooks(limit: Int): List<Book> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/opds/new/0/new"
        Log.d(TAG, "getNewBooks | ${safeLogUrl(url)}")
        val page = parser.fetchBookPage(url, limit.coerceAtMost(PAGE_LIMIT))
        val books = page.items.map { fillCoverUrl(it) }
>>>>>>> d24a20e (FoxyBook v1.9)
        Log.d(TAG, "getNewBooks | found=${books.size}")
        books
    }

    override suspend fun getNewBooksFirstPage(): NewBooksPage = withContext(Dispatchers.IO) {
<<<<<<< HEAD
        val url = "${parser.getBaseUrl()}/opds/new/0/new"
        Log.d(TAG, "getNewBooksFirstPage | $url")
        val xml = parser.fetchXml(url)
        val books = parser.parseNewBooksFast(xml, 50).map { fillCoverUrl(it) }
        val nextUrl = if (books.isNotEmpty()) {
            val fromLink = parser.parseNextLink(xml).let {
                if (it.isNotBlank()) (if (it.startsWith("http")) it else "${parser.getBaseUrl()}$it") else null
            }
            fromLink ?: "${parser.getBaseUrl()}/opds/new/${books.size}/new"
=======
        val url = "$baseUrl/opds/new/0/new"
        Log.d(TAG, "getNewBooksFirstPage | ${safeLogUrl(url)}")
        val page = parser.fetchBookPage(url, PAGE_LIMIT)
        val books = page.items.map { fillCoverUrl(it) }
        // Страница разобрана вместе с rel="next"; если каталог её не отдал,
        // продолжаем по смещению, как это делает сам Flibusta.
        val nextUrl = if (books.isNotEmpty()) {
            page.nextUrl ?: "$baseUrl/opds/new/${books.size}/new"
>>>>>>> d24a20e (FoxyBook v1.9)
        } else null
        NewBooksPage(books, nextUrl)
    }

    override suspend fun getNewBooksNextPage(url: String): NewBooksPage = withContext(Dispatchers.IO) {
<<<<<<< HEAD
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
=======
        Log.d(TAG, "getNewBooksNextPage | ${safeLogUrl(url)}")
        val page = parser.fetchBookPage(url, PAGE_LIMIT)
        val books = page.items.map { fillCoverUrl(it) }
        val nextUrl = if (books.isNotEmpty()) {
            page.nextUrl ?: nextOffsetUrl(url, books.size)
>>>>>>> d24a20e (FoxyBook v1.9)
        } else null
        NewBooksPage(books, nextUrl)
    }

    private fun nextOffsetUrl(url: String, pageSize: Int): String? {
        val offset = Regex("""/opds/new/(\d+)/new""").find(url)?.groupValues?.get(1)?.toIntOrNull()
            ?: return null
        return "$baseUrl/opds/new/${offset + pageSize}/new"
    }

    // ═══════════════════════════════════════════════════════════════
    //  Поиск книг
    // ═══════════════════════════════════════════════════════════════

    override suspend fun searchBooks(query: String, limit: Int): SearchPage<Book> =
        searchBooksPage(searchBooksUrl(query), limit)

    override suspend fun searchBooksNextPage(url: String, limit: Int): SearchPage<Book> =
        searchBooksPage(url, limit)

    private suspend fun searchBooksPage(url: String, limit: Int): SearchPage<Book> = withContext(Dispatchers.IO) {
        Log.d(TAG, "searchBooks | ${safeLogUrl(url)}")
        val page = parser.fetchBookPage(url, limit.coerceAtLeast(PAGE_LIMIT))
        val books = page.items.map { fillCoverUrl(it) }
        val nextUrl = if (books.isNotEmpty()) page.nextUrl else null
        Log.d(TAG, "searchBooks | found=${books.size}, next=${safeLogUrl(nextUrl)}")
        SearchPage(books, nextUrl)
    }

    private fun searchBooksUrl(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
<<<<<<< HEAD
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
=======
        return "$baseUrl/opds/search?searchType=books&searchTerm=$encoded"
    }

    // ═══════════════════════════════════════════════════════════════
    //  Поиск авторов
    // ═══════════════════════════════════════════════════════════════

    override suspend fun searchByAuthor(query: String, limit: Int): SearchPage<Author> =
        searchAuthorsPage(searchAuthorsUrl(query), limit)

    override suspend fun searchByAuthorNextPage(url: String, limit: Int): SearchPage<Author> =
        searchAuthorsPage(url, limit)

    private suspend fun searchAuthorsPage(url: String, limit: Int): SearchPage<Author> = withContext(Dispatchers.IO) {
        Log.d(TAG, "searchByAuthor | ${safeLogUrl(url)}")
        val page = parser.fetchAuthorPage(url, limit.coerceAtLeast(PAGE_LIMIT))
        val nextUrl = if (page.items.isNotEmpty()) page.nextUrl else null
        SearchPage(page.items, nextUrl)
>>>>>>> d24a20e (FoxyBook v1.9)
    }

    private fun searchAuthorsUrl(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
<<<<<<< HEAD
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
=======
        return "$baseUrl/opds/search?searchType=authors&searchTerm=$encoded"
    }

    // ═══════════════════════════════════════════════════════════════
    //  Книги автора / серии
    // ═══════════════════════════════════════════════════════════════

    override suspend fun getAuthorBooks(authorId: String, limit: Int): List<Book> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/opds/author/$authorId/alphabet"
        Log.d(TAG, "getAuthorBooks | ${safeLogUrl(url)}")
        collectBookPages(url, limit)
    }

    override suspend fun getSeriesBooks(seriesId: String, authorId: String?, limit: Int): List<Book> =
        withContext(Dispatchers.IO) {
            val url = if (!authorId.isNullOrBlank()) {
                "$baseUrl/opds/authorsequence/$authorId/$seriesId"
            } else {
                "$baseUrl/opds/sequencebooks/$seriesId"
            }
            Log.d(TAG, "getSeriesBooks | ${safeLogUrl(url)}")
            collectBookPages(url, limit)
        }

    /**
     * Постраничный сбор книг с остановкой по достижению лимита.
     * Раньше страницы выкачивались все maxPages штук, а лимит проверялся уже
     * после этого — на популярных авторах это лишние запросы впустую.
     */
    private suspend fun collectBookPages(url: String, limit: Int): List<Book> {
        val books = mutableListOf<Book>()
        val seen = mutableSetOf<Int>()
        var next: String? = url
        var page = 0
        while (!next.isNullOrBlank() && page < MAX_RESULT_PAGES && books.size < limit) {
            val result = parser.fetchBookPage(next, limit - books.size)
            for (book in result.items) {
                if (seen.add(book.id)) books.add(fillCoverUrl(book))
            }
            next = result.nextUrl
            page++
>>>>>>> d24a20e (FoxyBook v1.9)
        }
        return books.take(limit).sortedBy { it.sequenceNumber }
    }

    // ═══════════════════════════════════════════════════════════════
<<<<<<< HEAD
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
=======
    //  Поиск серий
    // ═══════════════════════════════════════════════════════════════

    /**
     * Каталоги не умеют искать по сериям на сервере (`searchTerm` у
     * `/opds/sequences` игнорируется), но в выдаче поиска по книгам каждая
     * книга из серии несёт `rel="related"` на свою серию. Поэтому серии
     * собираются из одной выдачи, а обход `/opds/sequences` по страницам
     * (десятки секунд на страницу у флибусты) остаётся только коротким
     * резервом.
     */
    override suspend fun searchBySeries(query: String, limit: Int): SearchPage<Series> =
        withContext(Dispatchers.IO) {
            val normalized = parser.normalizeTitle(query)
            if (normalized.isBlank()) return@withContext SearchPage(emptyList())

            val fromBooks = seriesFromBookSearch(normalized, limit)
            val matches = fromBooks.filter { parser.normalizeTitle(it.seriesTitle).contains(normalized) }

            if (matches.isNotEmpty()) {
                Log.d(TAG, "searchBySeries | found=${matches.size} from book search")
                return@withContext SearchPage(matches.distinctBy { it.seriesId }.take(limit), null)
>>>>>>> d24a20e (FoxyBook v1.9)
            }

            val fallback = withTimeoutOrNull(FALLBACK_SERIES_TIMEOUT_MS) { seriesFromCatalog(normalized, limit) }
            val result = (matches + fallback.orEmpty())
                .distinctBy { it.seriesId }
                .take(limit)
            Log.d(TAG, "searchBySeries | found=${result.size}")
            SearchPage(result, null)
        }

<<<<<<< HEAD
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
=======
    private suspend fun seriesFromBookSearch(normalized: String, limit: Int): List<Series> {
        return try {
            val page = parser.fetchBooksAndSeries(searchBooksUrl(normalized), PAGE_LIMIT)
            page.series
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
>>>>>>> d24a20e (FoxyBook v1.9)
        } catch (e: Exception) {
            Log.w(TAG, "seriesFromBookSearch | failed: ${e.message}")
            emptyList()
        }
<<<<<<< HEAD

        val filtered = allSeries.filter {
            it.seriesTitle.trim().lowercase()
                .replace(Regex("""[.,!?;:'\"«»()\[\]{}]"""), "")
                .contains(cleanQuery)
        }.distinctBy { it.seriesId }.take(limit)

        Log.d(TAG, "searchBySeries | found=${filtered.size}")
        SearchPage(filtered, null)
=======
>>>>>>> d24a20e (FoxyBook v1.9)
    }

    /**
     * Резервный путь: первые страницы каталога серий с клиентской фильтрацией.
     * Жёстко ограничен по числу страниц и по времени — обход всего каталога
     * занимал десятки минут и блокировал вкладку серий.
     */
    private suspend fun seriesFromCatalog(normalized: String, limit: Int): List<Series> {
        val result = mutableListOf<Series>()
        var next: String? = "$baseUrl/opds/sequences"
        var page = 0
        while (!next.isNullOrBlank() && page < MAX_FALLBACK_SERIES_PAGES) {
            val pageUrl = next
            val fetched = withTimeoutOrNull(FALLBACK_PAGE_TIMEOUT_MS) {
                try {
                    parser.fetchSeriesPage(pageUrl, PAGE_LIMIT)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "seriesFromCatalog | page $page failed: ${e.message}")
                    null
                }
            } ?: break
            if (fetched.items.isEmpty()) break
            result.addAll(
                fetched.items.filter { parser.normalizeTitle(it.seriesTitle).contains(normalized) }
            )
            if (result.size >= limit) break
            next = fetched.nextUrl
            page++
        }
        return result
    }

    override suspend fun searchBySeriesNextPage(url: String, limit: Int): SearchPage<Series> =
        SearchPage(emptyList())

    // ═══════════════════════════════════════════════════════════════
<<<<<<< HEAD
    //  Genre catalog navigation — correct OPDS genre browsing
    // ═══════════════════════════════════════════════════════════════

    /**
     * Состояние пагинации поиска по жанрам.
=======
    //  Жанры: навигация по каталогу жанров
    // ═══════════════════════════════════════════════════════════════

    /**
     * Состояние пагинации поиска по жанрам: какие жанры/поджанры уже пройдены.
     * Запрос входит в состояние, потому что маркер следующей страницы может
     * пережить смену запроса: без проверки вкладка «Жанры» показала бы книги
     * предыдущего поиска.
>>>>>>> d24a20e (FoxyBook v1.9)
     */
    @Volatile
    private var genreState: GenreSearchState? = null

    private data class GenreSearchState(
<<<<<<< HEAD
        val matchingGenres: List<OpdsParser.GenreNavEntry>,
        val currentGenreIndex: Int,
        val subGenres: List<OpdsParser.GenreNavEntry>,
        val currentSubGenreIndex: Int,
=======
        val query: String,
        val matchingGenres: List<OpdsParser.GenreNavEntry>,
        val genreIndex: Int,
        val subGenres: List<OpdsParser.GenreNavEntry>,
        val subGenreIndex: Int,
>>>>>>> d24a20e (FoxyBook v1.9)
        val subGenreNextUrl: String?
    )

    override suspend fun searchByGenre(query: String, limit: Int): SearchPage<Book> = withContext(Dispatchers.IO) {
<<<<<<< HEAD
        val cleanQuery = query.trim().lowercase()
            .replace(Regex("""[.,!?;:'\"«»()\[\]{}]"""), "")
        Log.d(TAG, "searchByGenre | query=$cleanQuery, limit=$limit")

        // 1. Загружаем /opds/genres — список всех жанров
        val genresXml = try {
            parser.fetchXml("${parser.getBaseUrl()}/opds/genres")
=======
        val normalized = parser.normalizeTitle(query)
        Log.d(TAG, "searchByGenre | queryLength=${normalized.length}, limit=$limit")
        if (normalized.isBlank()) return@withContext SearchPage(emptyList())

        val genres = try {
            parser.fetchNavPage("$baseUrl/opds/genres")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
>>>>>>> d24a20e (FoxyBook v1.9)
        } catch (e: Exception) {
            Log.e(TAG, "searchByGenre | /opds/genres failed, falling back to search", e)
            return@withContext searchByGenreFallback(query, limit, normalized)
        }
<<<<<<< HEAD
        val allGenres = parser.parseNavEntries(genresXml)
        Log.d(TAG, "searchByGenre | /opds/genres has ${allGenres.size} genres")
=======
        Log.d(TAG, "searchByGenre | /opds/genres has ${genres.size} genres")
>>>>>>> d24a20e (FoxyBook v1.9)

        val matching = genres.filter { parser.normalizeTitle(it.name).contains(normalized) }
        if (matching.isEmpty()) {
            Log.d(TAG, "searchByGenre | no matching genres, falling back to search")
            return@withContext searchByGenreFallback(query, limit, normalized)
        }

<<<<<<< HEAD
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
=======
        for ((genreIndex, genre) in matching.withIndex()) {
            val subGenres = try {
                parser.fetchNavPage(genre.url)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
>>>>>>> d24a20e (FoxyBook v1.9)
            } catch (e: Exception) {
                Log.w(TAG, "searchByGenre | sub-genre feed failed for '${genre.name}'", e)
                continue
            }
            Log.d(TAG, "searchByGenre | '${genre.name}' has ${subGenres.size} sub-genres")

            val found = probeSubGenres(subGenres, matching, genreIndex, limit, startIndex = 0, query = normalized)
            if (found != null) {
                genreState = found.state
                val hasMore = found.nextUrl != null ||
                    found.state.subGenreIndex + 1 < found.state.subGenres.size ||
                    genreIndex + 1 < matching.size
                Log.d(TAG, "searchByGenre | found ${found.books.size} books via OPDS nav, hasMore=$hasMore")
                return@withContext SearchPage(
                    found.books.distinctBy { it.id }.map { fillCoverUrl(it) },
                    if (hasMore) genreMarker(genreIndex, normalized) else null
                )
            }
        }

        Log.d(TAG, "searchByGenre | no books in OPDS genre feeds, falling back to search")
        searchByGenreFallback(query, limit, normalized)
    }

    /** Метка следующей страницы жанров; запрос входит в неё, чтобы маркер от чужого поиска можно было отбросить. */
    private fun genreMarker(genreIndex: Int, query: String) =
        "$GENRE_MARKER_PREFIX$genreIndex?q=${java.net.URLEncoder.encode(query, "UTF-8")}"

    /**
     * Поджанры одного жанра. Раньше они обходились строго по одному запросу
     * подряд; здесь пачками по [SUBGENRE_PARALLELISM] — как только в пачке
     * нашлись книги, дальше не идём.
     */
    private data class GenreProbeResult(
        val books: List<Book>,
        val state: GenreSearchState,
        val nextUrl: String?
    )

    private suspend fun probeSubGenres(
        subGenres: List<OpdsParser.GenreNavEntry>,
        matchingGenres: List<OpdsParser.GenreNavEntry>,
        genreIndex: Int,
        limit: Int,
        startIndex: Int,
        query: String
    ): GenreProbeResult? {
        var index = startIndex
        while (index < subGenres.size) {
            val batchIndices = (index until minOf(index + SUBGENRE_PARALLELISM, subGenres.size)).toList()
            val results = coroutineScope {
                batchIndices.map { i ->
                    async {
                        val sub = subGenres[i]
                        try {
                            val page = parser.fetchBookPage(sub.url, limit)
                            if (page.items.isEmpty()) {
                                null
                            } else {
                                GenreProbeResult(
                                    page.items,
                                    GenreSearchState(
                                        query, matchingGenres, genreIndex, subGenres, i, page.nextUrl
                                    ),
                                    page.nextUrl
                                )
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "probeSubGenres | '${sub.name}' error: ${e.message}")
                            null
                        }
                    }
                }.mapNotNull { it.await() }
            }
            if (results.isNotEmpty()) {
                // В пачке берём самый ранний поджанр с книгами: порядок
                // соответствует алфавиту и даёт предсказуемую выдачу.
                return results.minBy { it.state.subGenreIndex }
            }
            index += batchIndices.size
        }
<<<<<<< HEAD

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
=======
        return null
>>>>>>> d24a20e (FoxyBook v1.9)
    }

    /**
     * Fallback: поиск через /opds/search + клиентская фильтрация по жанру.
     * Используется, когда OPDS-каталог жанров недоступен.
     */
<<<<<<< HEAD
    private suspend fun searchByGenreFallback(query: String, limit: Int, cleanQuery: String): SearchPage<Book> {
        Log.d(TAG, "searchByGenreFallback | query=$query")
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "${parser.getBaseUrl()}/opds/search?searchType=books&searchTerm=$encoded"
        val xml = try {
            parser.fetchXml(url)
=======
    private suspend fun searchByGenreFallback(query: String, limit: Int, normalized: String): SearchPage<Book> {
        val url = searchBooksUrl(query)
        val allBooks = try {
            parser.fetchBookPage(url, limit.coerceAtLeast(PAGE_LIMIT)).items.map { fillCoverUrl(it) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
>>>>>>> d24a20e (FoxyBook v1.9)
        } catch (e: Exception) {
            Log.e(TAG, "searchByGenreFallback | search failed", e)
            return SearchPage(emptyList())
        }
<<<<<<< HEAD
        val allBooks = parser.parseOpdsBooks(xml, limit).map { fillCoverUrl(it) }
=======
>>>>>>> d24a20e (FoxyBook v1.9)
        val filtered = allBooks.filter { book ->
            book.genres.any { parser.normalizeTitle(it).contains(normalized) }
        }
<<<<<<< HEAD
        val nextUrl = if (filtered.isNotEmpty()) "$url&pageNumber=1" else null
        Log.d(TAG, "searchByGenreFallback | found=${filtered.size} from ${allBooks.size}, next=$nextUrl")
        return SearchPage(filtered.distinctBy { it.id }, nextUrl)
=======
        Log.d(TAG, "searchByGenreFallback | found=${filtered.size} from ${allBooks.size}")
        return SearchPage(filtered.distinctBy { it.id }, null)
>>>>>>> d24a20e (FoxyBook v1.9)
    }

    override suspend fun searchByGenreNextPage(url: String, limit: Int): SearchPage<Book> = withContext(Dispatchers.IO) {
        if (url.startsWith(GENRE_MARKER_PREFIX)) {
            val current = genreState
            if (current != null && markerMatchesQuery(url, current)) {
                nextGenrePage(current, limit)?.let { return@withContext it }
            } else {
                Log.d(TAG, "searchByGenreNextPage | stale genre marker, ignoring: $url")
                return@withContext SearchPage(emptyList())
            }
        }
        searchByGenreNextPageFallback(url, limit)
    }

    /** Метка от чужого запроса не должна резолвиться по текущему состоянию. */
    private fun markerMatchesQuery(marker: String, state: GenreSearchState): Boolean {
        val encoded = marker.substringAfter("?q=", "")
        if (encoded.isEmpty()) return true
        val decoded = runCatching {
            java.net.URLDecoder.decode(encoded, "UTF-8")
        }.getOrDefault(encoded)
        return parser.normalizeTitle(decoded) == state.query
    }

<<<<<<< HEAD
        // 1. Пробуем следующую страницу текущего поджанра (rel="next")
        var currentState = state
        if (currentState.subGenreNextUrl != null) {
            val xml = try {
                parser.fetchXml(currentState.subGenreNextUrl)
=======
    private suspend fun nextGenrePage(current: GenreSearchState, limit: Int): SearchPage<Book>? {
        // 1. Следующая страница текущего поджанра
        current.subGenreNextUrl?.let { next ->
            val page = try {
                parser.fetchBookPage(next, limit)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
>>>>>>> d24a20e (FoxyBook v1.9)
            } catch (e: Exception) {
                Log.w(TAG, "nextGenrePage | sub-genre page failed: ${e.message}")
                null
            }
<<<<<<< HEAD
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
=======
            if (page != null && page.items.isNotEmpty()) {
                val state = current.copy(subGenreNextUrl = page.nextUrl)
                genreState = state
                val hasMore = page.nextUrl != null ||
                    state.subGenreIndex + 1 < state.subGenres.size ||
                    state.genreIndex + 1 < state.matchingGenres.size
                return SearchPage(
                    page.items.map { fillCoverUrl(it) },
                    if (hasMore) genreMarker(state.genreIndex, state.query) else null
                )
            }
        }

        // 2. Следующий поджанр того же жанра
        val moreSub = probeSubGenres(
            subGenres = current.subGenres,
            matchingGenres = current.matchingGenres,
            genreIndex = current.genreIndex,
            limit = limit,
            startIndex = current.subGenreIndex + 1,
            query = current.query
        )
        if (moreSub != null) {
            genreState = moreSub.state
            val hasMore = moreSub.nextUrl != null ||
                moreSub.state.subGenreIndex + 1 < moreSub.state.subGenres.size ||
                moreSub.state.genreIndex + 1 < moreSub.state.matchingGenres.size
            return SearchPage(
                moreSub.books.map { fillCoverUrl(it) },
                if (hasMore) genreMarker(moreSub.state.genreIndex, moreSub.state.query) else null
            )
        }

        // 3. Следующий подходящий жанр
        val nextGenreIndex = current.genreIndex + 1
        if (nextGenreIndex < current.matchingGenres.size) {
            val subGenres = try {
                parser.fetchNavPage(current.matchingGenres[nextGenreIndex].url)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "nextGenrePage | genre feed failed: ${e.message}")
                null
            }
            if (!subGenres.isNullOrEmpty()) {
                val found = probeSubGenres(
                    subGenres = subGenres,
                    matchingGenres = current.matchingGenres,
                    genreIndex = nextGenreIndex,
                    limit = limit,
                    startIndex = 0,
                    query = current.query
                )
                if (found != null) {
                    genreState = found.state
                    val hasMore = found.nextUrl != null ||
                        found.state.subGenreIndex + 1 < found.state.subGenres.size ||
                        nextGenreIndex + 1 < current.matchingGenres.size
                    return SearchPage(
                        found.books.map { fillCoverUrl(it) },
                        if (hasMore) genreMarker(nextGenreIndex, found.state.query) else null
                    )
                }
            }
        }
        return null
>>>>>>> d24a20e (FoxyBook v1.9)
    }

    private suspend fun searchByGenreNextPageFallback(url: String, limit: Int): SearchPage<Book> {
<<<<<<< HEAD
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
=======
        if (url.startsWith(GENRE_MARKER_PREFIX)) return SearchPage(emptyList())
        val page = try {
            parser.fetchBookPage(url, limit.coerceAtLeast(PAGE_LIMIT))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
>>>>>>> d24a20e (FoxyBook v1.9)
            null
        } ?: return SearchPage(emptyList())
        val searchTerm = Regex("""searchTerm=([^&]+)""").find(url)?.groupValues?.get(1) ?: ""
        val normalized = parser.normalizeTitle(java.net.URLDecoder.decode(searchTerm, "UTF-8"))
        val filtered = page.items.filter { book ->
            book.genres.any { parser.normalizeTitle(it).contains(normalized) }
        }
        return SearchPage(filtered.map { fillCoverUrl(it) }.distinctBy { it.id }, null)
    }

<<<<<<< HEAD
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
=======
    // ═══════════════════════════════════════════════════════════════
    //  Страница книги
    // ═══════════════════════════════════════════════════════════════

    override suspend fun getBookInfo(id: Int): BookInfo? = withContext(Dispatchers.IO) {
        // Книга может быть доступна на одном зеркале и удалена на другом, поэтому
        // перебираем зеркала самостоятельно, а не через fetchWithMirrorRetry.
>>>>>>> d24a20e (FoxyBook v1.9)
        val mirrors = networkClient.getMirrors()
        var lastError: String? = null

        for (mirror in mirrors) {
<<<<<<< HEAD
            val baseUrl = mirror.trimEnd('/')
            val htmlUrl = "$baseUrl/b/$id"
=======
            val mirrorBase = mirror.trimEnd('/')
            if (!networkClient.isMirrorHealthy(mirrorBase)) {
                Log.d(TAG, "getBookInfo | skipping unhealthy mirror $mirrorBase")
                continue
            }
            val htmlUrl = "$mirrorBase/b/$id"
>>>>>>> d24a20e (FoxyBook v1.9)
            try {
                Log.d(TAG, "getBookInfo | HTML $htmlUrl")
                val request = Request.Builder().url(htmlUrl).build()
                val response = client.executeCancellable(request)
                if (!response.isSuccessful) {
                    val code = response.code
                    response.close()
                    lastError = "HTTP $code на $mirror"
<<<<<<< HEAD
                    Log.d(TAG, "getBookInfo | $lastError, trying next mirror")
                    continue
                }
                val html = response.body?.string() ?: ""
                response.close()

                // Если страница — заглушка (каталог/поиск вместо книги) → книги нет на этом зеркале
=======
                    if (code in 500..599 || code == 429) networkClient.markMirrorUnhealthy(mirrorBase)
                    Log.d(TAG, "getBookInfo | $lastError, trying next mirror")
                    continue
                }
                val html = response.body?.string().orEmpty()
                response.close()

                // Если страница — заглушка (каталог/поиск вместо книги), то
                // на этом зеркале книги нет
>>>>>>> d24a20e (FoxyBook v1.9)
                if (html.contains("<h1 class=\"title\">Книги</h1>") ||
                    html.contains("Результат поиска") ||
                    html.contains("Книга удалена из библиотеки", ignoreCase = true)
                ) {
                    lastError = "Book $id not found on $mirror"
                    Log.d(TAG, "getBookInfo | $lastError, trying next mirror")
                    continue
                }

<<<<<<< HEAD
                val info = parseHtmlBookInfo(html, id)

                // Если книга удалена/заменена — пробуем найти замену
                if (info == null) {
                    val replacementMatch = Regex("""заменена на\s+<a\s+href="/b/(\d+)"">""")
                        .find(html)
=======
                val info = parseHtmlBookInfo(html, id, mirrorBase)

                if (info == null) {
                    // Книга удалена/заменена — ищем замену
                    val replacementMatch = Regex("""заменена на\s+<a\s+href="/b/(\d+)"">""").find(html)
>>>>>>> d24a20e (FoxyBook v1.9)
                    if (replacementMatch != null) {
                        val newId = replacementMatch.groupValues[1].toIntOrNull()
                        if (newId != null && newId != id) {
                            Log.d(TAG, "getBookInfo | book $id → replaced by $newId, redirecting")
                            return@withContext getBookInfo(newId)
                        }
                    }
                    continue
                }

<<<<<<< HEAD
                // Если нашли книгу на неактивном зеркале — переключаемся на него
                if (mirror != networkClient.getBaseUrl()) {
                    networkClient.switchMirror(mirror)
                }

                return@withContext info
            } catch (e: Exception) {
                lastError = "${e.message}"
                Log.w(TAG, "getBookInfo | error on $mirror: ${e.message}, trying next mirror")
=======
                networkClient.markMirrorHealthy(mirrorBase)
                if (mirrorBase != baseUrl) networkClient.switchMirror(mirrorBase)
                return@withContext info
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Отмена (уход с экрана, withTimeout) — не признак того, что
                // зеркало сломано: иначе один таймаут на 5 минут выключал бы
                // рабочее зеркало и уводил приложение на заведомо мёртвое.
                throw e
            } catch (e: IOException) {
                lastError = e.message
                networkClient.markMirrorUnhealthy(mirrorBase)
                Log.w(TAG, "getBookInfo | network error on $mirror: ${e.message}, trying next mirror")
            } catch (e: Exception) {
                lastError = e.message
                Log.w(TAG, "getBookInfo | parse error on $mirror: ${e.message}, trying next mirror")
>>>>>>> d24a20e (FoxyBook v1.9)
            }
        }
        Log.e(TAG, "getBookInfo | all mirrors failed: $lastError")
        null
    }

    override fun getDownloadUrl(id: String, format: BookFormat): String {
        val bookId = id.toIntOrNull()
        if (bookId != null) {
<<<<<<< HEAD
            val urls = downloadUrlCache[bookId]
            if (urls != null) {
                val cached = urls[format.extension]
                if (cached != null) {
                    Log.d(TAG, "getDownloadUrl | from cache: $cached")
                    return if (cached.startsWith("http")) cached else "${parser.getBaseUrl()}$cached"
                }
=======
            downloadUrlCache[bookId]?.get(format.extension)?.let { cached ->
                Log.d(TAG, "getDownloadUrl | from cache: $cached")
                return parser.absolute(cached)
>>>>>>> d24a20e (FoxyBook v1.9)
            }
        }
        return "${parser.getBaseUrl()}/b/$id/${format.extension}"
    }

<<<<<<< HEAD
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
=======
    override suspend fun downloadBook(
        id: String,
        format: BookFormat,
        onProgress: (Float) -> Unit
    ): ResponseBody? = withContext(Dispatchers.IO) {
        val ext = format.extension
        val urlsToTry = linkedSetOf(getDownloadUrl(id, format), "$baseUrl/b/$id/$ext", "$baseUrl/$id/$ext")
        // Живые зеркала идём первыми, но запрета на остальные нет: иначе после
        // двух 5xx ни один вариант URL не проверялся бы и скачивание было бы
        // невозможно на все время остуда зеркал.
        val mirrors = networkClient.getMirrors().sortedByDescending { networkClient.isMirrorHealthy(it) }
        val triedMirrors = mutableSetOf<String>()
>>>>>>> d24a20e (FoxyBook v1.9)
        var lastException: Exception? = null

        for (url in urlsToTry) {
            for (mirror in mirrors) {
<<<<<<< HEAD
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
=======
                val key = mirror.trimEnd('/')
                // Каждое зеркало получает один раунд попыток на этот URL:
                // дальше повторять в заведомо мёртвое нет смысла.
                if (!triedMirrors.add(key)) continue
                val mirrorUrl = url.replace(Regex("""https?://[^/]+"""), mirror)
                for (attempt in 1..MAX_RETRIES) {
                    try {
                        Log.d(TAG, "downloadBook | attempt $attempt: ${safeLogUrl(mirrorUrl)}")
                        val request = Request.Builder().url(mirrorUrl).build()
                        val response = downloadClient.executeCancellable(request)
                        if (!response.isSuccessful) {
                            val code = response.code
                            response.close()
                            lastException = IOException("HTTP $code")
                            Log.w(TAG, "downloadBook | HTTP $code for ${safeLogUrl(mirrorUrl)}")
                            if (code in 500..599 || code == 429) {
                                networkClient.markMirrorUnhealthy(key)
                                if (attempt < MAX_RETRIES) delay(1000L * attempt)
                            }
                            if (code in 500..599 || code == 429) break
                            // 4xx и прочие «конечные» ответы повторять бессмысленно
                            break
                        }
                        // Сервер мог отдать HTML вместо запрошенного формата —
                        // сохранять такой файл бессмысленно
                        val contentType = response.header("Content-Type").orEmpty()
                        if (contentType.startsWith("text/html")) {
                            response.close()
                            lastException = IOException("Server returned HTML instead of $ext")
                            Log.w(TAG, "downloadBook | HTML instead of $ext for ${safeLogUrl(mirrorUrl)}")
                            break
                        }
                        Log.d(TAG, "downloadBook | success for ${safeLogUrl(mirrorUrl)}")
                        networkClient.markMirrorHealthy(key)
                        return@withContext response.body
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        lastException = e
                        networkClient.markMirrorUnhealthy(key)
                        Log.w(TAG, "downloadBook | error", e)
                        if (attempt < MAX_RETRIES) delay(1000L * attempt)
>>>>>>> d24a20e (FoxyBook v1.9)
                    }
                }
            }
        }
<<<<<<< HEAD
        throw lastException ?: Exception("Download failed")
=======
        throw lastException ?: IOException("Download failed")
>>>>>>> d24a20e (FoxyBook v1.9)
    }

    // ═══════════════════════════════════════════════════════════════
    //  HTML book info parsing (Flibusta-specific)
    //  Uses downloadUrlCache to store real download URLs found on the page.
    // ═══════════════════════════════════════════════════════════════

<<<<<<< HEAD
    private fun parseHtmlBookInfo(html: String, bookId: Int): BookInfo? {
        val doc = Jsoup.parse(html, parser.getBaseUrl())
=======
    private fun parseHtmlBookInfo(html: String, bookId: Int, pageBaseUrl: String): BookInfo? {
        val doc = Jsoup.parse(html, pageBaseUrl)
>>>>>>> d24a20e (FoxyBook v1.9)

        // ── Title ──
        val rawTitle = doc.selectFirst("h1")?.text()?.trim()
            ?: doc.selectFirst("h2")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: return null

        // ── Check if book is deleted/replaced on Flibusta ──
<<<<<<< HEAD
        // Example: <h4>Книга <a href="/b/9">9</a> заменена на <a href="/b/114062">исправленную</a></h4>
=======
>>>>>>> d24a20e (FoxyBook v1.9)
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
<<<<<<< HEAD
            rawTitle.removePrefix(detectedAuthor).trimStart(' ', '—', '—', '–', '-').trim()
=======
            rawTitle.removePrefix(detectedAuthor).trimStart(' ', '—', '–', '-').trim()
>>>>>>> d24a20e (FoxyBook v1.9)
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
<<<<<<< HEAD
            if (src.startsWith("http")) src else "${parser.getBaseUrl()}$src"
=======
            if (src.startsWith("http")) src else "$pageBaseUrl$src"
>>>>>>> d24a20e (FoxyBook v1.9)
        } ?: ""

        // ── Description ──
        val description = buildString {
            val annotationH2 = doc.selectFirst("h2:contains(Аннотация)")
            if (annotationH2 != null) {
                val parts = mutableListOf<String>()
                var current = annotationH2.nextSibling()
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
<<<<<<< HEAD
                val raw = parts.joinToString(" ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                if (raw.isNotBlank() && !raw.startsWith("отсутствует")) {
                    append(raw.take(500))
                }
=======
                val raw = parts.joinToString(" ").replace(Regex("\\s+"), " ").trim()
                if (raw.isNotBlank() && !raw.startsWith("отсутствует")) append(raw.take(500))
>>>>>>> d24a20e (FoxyBook v1.9)
            }
            if (isEmpty()) {
                doc.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { append(it.take(500)) }
            }
            if (isEmpty()) {
                doc.selectFirst("meta[name=description]")?.attr("content")?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { append(it.take(500)) }
            }
<<<<<<< HEAD
            // Fallback: first meaningful paragraph in main content area
=======
>>>>>>> d24a20e (FoxyBook v1.9)
            if (isEmpty()) {
                for (p in doc.select("#main p, .content p, article p")) {
                    val text = p.text().trim()
                    if (text.length in 30 until 2000 &&
                        !text.contains("Оценки") && !text.contains("Рекомендации")
                    ) {
                        append(text)
                        break
                    }
                }
            }
        }

        // ── Genres ──
        val genres = mutableListOf<BookGenre>()
        for (genreLink in doc.select("a[href^=/g/]")) {
            val genreTitle = genreLink.text().trim()
            if (genreTitle.isNotBlank()) {
                genres.add(
                    BookGenre(
                        id = genreLink.attr("href").removePrefix("/g/"),
                        title = genreTitle
                    )
                )
            }
        }

        // ── Available formats: ищем ссылки скачивания на странице книги ──
        val formatExtensions = listOf("epub", "fb2", "mobi", "txt", "pdf")
        val formatSet = mutableSetOf<String>()
        val urlMap = mutableMapOf<String, String>()

        for (link in doc.select("a[href]")) {
            val href = link.attr("href")
            val text = link.text().lowercase()
            val fmt = formatExtensions.firstOrNull { ext ->
                (text.contains(ext) && (text.contains("скачать") || text.contains("download")))
            } ?: formatExtensions.firstOrNull { ext ->
                val lastSegment = href.substringAfterLast("/")
                val fullPath = href.substringAfter("://").substringAfter("/")
                (lastSegment == ext || lastSegment == "$bookId.$ext" || fullPath.contains("$bookId/$ext")) &&
<<<<<<< HEAD
                (href.contains("/b/$bookId/") || href.contains("/dl/") || href.contains("/download/") ||
                 href.contains("/lib/id${bookId}/download/") || href.contains("$bookId.$ext"))
            } ?: formatExtensions.firstOrNull { ext ->
                // Третья попытка: любой href, оканчивающийся на /ext или .ext,
                // если в href есть bookId или текст ссылки содержит название формата
                val lastSegment = href.substringAfterLast("/").lowercase()
                (lastSegment == ext || lastSegment.endsWith(".$ext")) &&
                (href.contains("/$bookId/") || href.contains("$bookId.") || text.contains(ext))
=======
                    (href.contains("/b/$bookId/") || href.contains("/dl/") || href.contains("/download/") ||
                        href.contains("/lib/id${bookId}/download/") || href.contains("$bookId.$ext"))
            } ?: formatExtensions.firstOrNull { ext ->
                val lastSegment = href.substringAfterLast("/").lowercase()
                (lastSegment == ext || lastSegment.endsWith(".$ext")) &&
                    (href.contains("/$bookId/") || href.contains("$bookId.") || text.contains(ext))
>>>>>>> d24a20e (FoxyBook v1.9)
            }

            if (fmt != null) {
                formatSet.add(fmt)
                if (fmt !in urlMap) urlMap[fmt] = href
            }
        }

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
            availableFormats = formatSet.toList()
        )
    }
<<<<<<< HEAD
}
=======
}
>>>>>>> d24a20e (FoxyBook v1.9)
