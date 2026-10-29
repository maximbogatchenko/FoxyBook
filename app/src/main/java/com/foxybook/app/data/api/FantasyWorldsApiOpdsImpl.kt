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
import com.foxybook.app.core.network.executeCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import java.io.IOException
import java.net.URLEncoder
import com.foxybook.app.core.utils.safeLogUrl

/**
 * Fantasy-worlds. Отдельный каталог с другой схемой адресов:
 *  - книги `/opds/last`, `/opds/last/{offset}` — без `rel="next"`;
 *  - поиск только `/opdssearch?q=` (на `/opds/search` каталог отдаёт 404);
 *  - авторы `/opds/author/id{N}`, id приходит строкой вида `id9544`;
 *  - серии `/opds/series/id{N}` — `/opds/sequences` на этом каталоге не существует.
 *
 * Разбор фидов общий с Flibusta/CoolLib (OpdsParser), здесь только адреса и
 * правила пагинации. Раньше класс держал свои копии парсеров (~700 строк),
 * из-за чего разъезжалось поведение с остальными каталогами.
 */
class FantasyWorldsApiOpdsImpl(
    private val networkClient: OkHttpClientProvider
) : FlibustaApi {

    private companion object {
        const val TAG = "FANTASY_WORLDS_OPDS"
        const val MAX_RETRIES = 3
<<<<<<< HEAD
        const val ALL_RESULTS = Int.MAX_VALUE
        const val MAX_AUTHORS_TO_CRAWL = 3
        const val MAX_SEQ_PAGES = 15

        @Volatile
        private var seriesCache: Map<String, List<Series>>? = null
        @Volatile
        private var isCacheBuilding = false
        private val cacheLock = Any()
=======
        const val PAGE_LIMIT = 50
        const val MAX_RESULT_PAGES = 3
>>>>>>> d24a20e (FoxyBook v1.9)
    }

    private val client = networkClient.client
    private val downloadClient = networkClient.downloadClient
    private val baseUrl: String get() = networkClient.getBaseUrl()
<<<<<<< HEAD
    private val searchBookCache = mutableMapOf<String, List<Book>>()
override suspend fun getNewBooks(limit: Int): List<Book> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/opds/last"
        Log.d(TAG, "getNewBooks | $url")
        val xml = fetchXml(url)
        val books = parseOpdsBooks(xml, limit.coerceAtMost(50))
        Log.d(TAG, "getNewBooks | found=${books.size}")
        books.distinctBy { it.id }
=======

    private val parser = OpdsParser(
        baseUrlProvider = { networkClient.getBaseUrl() },
        fetchFn = { url -> networkClient.fetchWithMirrorRetry(url, client) }
    )

    // ═══════════════════════════════════════════════════════════════
    //  Новинки: смещение вместо rel="next"
    // ═══════════════════════════════════════════════════════════════

    override suspend fun getNewBooks(limit: Int): List<Book> = withContext(Dispatchers.IO) {
        val page = parser.fetchBookPageWithNav("$baseUrl/opds/last", limit.coerceAtMost(PAGE_LIMIT))
        page.items.distinctBy { it.id }
>>>>>>> d24a20e (FoxyBook v1.9)
    }

    override suspend fun getNewBooksFirstPage(): NewBooksPage = withContext(Dispatchers.IO) {
        val url = "$baseUrl/opds/last"
        Log.d(TAG, "getNewBooksFirstPage | ${safeLogUrl(url)}")
        val page = parser.fetchBookPageWithNav(url, PAGE_LIMIT)
        val books = page.items.distinctBy { it.id }
        NewBooksPage(books, if (books.isEmpty()) null else saneNextPageUrl(url, page.nextUrl))
    }

    override suspend fun getNewBooksNextPage(url: String): NewBooksPage = withContext(Dispatchers.IO) {
        Log.d(TAG, "getNewBooksNextPage | ${safeLogUrl(url)}")
        val page = parser.fetchBookPageWithNav(url, PAGE_LIMIT)
        val books = page.items.distinctBy { it.id }
        NewBooksPage(books, if (books.isEmpty()) null else saneNextPageUrl(url, page.nextUrl))
    }

<<<<<<< HEAD
    override suspend fun searchBooks(query: String, limit: Int): SearchPage<Book> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val effectiveLimit = limit.coerceAtLeast(50)

        // Сначала пробуем стандартный OPDS search endpoint (с <link rel="next">)
        val url = "$baseUrl/opds/search?searchType=books&searchTerm=$encoded"
        Log.d(TAG, "searchBooks | $url")

        var xml: String? = null
        var allBooks: List<Book> = emptyList()
        var books: List<Book> = emptyList()

        try {
            xml = fetchXml(url)
            allBooks = parseOpdsBooks(xml, effectiveLimit)
            books = allBooks.distinctBy { it.id }
        } catch (e: Exception) {
            Log.w(TAG, "searchBooks | primary endpoint failed: ${e.message}")
        }

        // Если не нашло (ошибка или пустой результат) — пробуем альтернативный endpoint
        if (books.isEmpty()) {
            val fallbackUrl = "$baseUrl/opdssearch?searchType=books&q=$encoded"
            Log.d(TAG, "searchBooks | fallback → $fallbackUrl")
            try {
                xml = fetchXml(fallbackUrl)
                allBooks = parseOpdsBooks(xml, effectiveLimit)
                books = allBooks.distinctBy { it.id }
            } catch (e: Exception) {
                Log.w(TAG, "searchBooks | fallback also failed: ${e.message}")
            }
        }

        val nextUrl = if (books.isNotEmpty() && xml != null) parseNextLink(xml!!).let { if (it.isNotBlank()) it else null } else null
        Log.d(TAG, "searchBooks | found=${books.size}, next=$nextUrl")
        SearchPage(books, nextUrl)
    }

    override suspend fun searchBooksNextPage(url: String, limit: Int): SearchPage<Book> = withContext(Dispatchers.IO) {
        Log.d(TAG, "searchBooksNextPage | $url")
        val xml = fetchXml(url)
        val allBooks = parseOpdsBooks(xml, limit.coerceAtLeast(50))
        val books = allBooks.distinctBy { it.id }
        val nextUrl = if (books.isNotEmpty()) parseNextLink(xml).let { if (it.isNotBlank()) it else null } else null
        Log.d(TAG, "searchBooksNextPage | found=${books.size}, next=$nextUrl")
        SearchPage(books, nextUrl)
    }
=======
    /**
     * Номер страницы из адреса. У этого каталога `/opds/last/N` — это именно
     * страница, а не смещение: на живой выдаче `/opds/last` и `/opds/last/1`
     * отдают одно и то же.
     */
    private fun pageNumberOf(url: String): Int =
        Regex("""/last/(-?\d+)""").find(url)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    /**
     * Каталог отдаёт следующую страницу записью `<id>tag:root:nextpage</id>`, но
     * эти ссылки непоследовательны: `/opds/last/0` предлагает `/opds/last/-1/`,
     * а `/opds/last/2` — `/opds/last/1/`, то есть назад. Берём предложенную
     * ссылку, только если она ведёт вперёд, иначе увеличиваем номер сами.
     */
    private fun saneNextPageUrl(currentUrl: String, suggested: String?): String {
        val current = pageNumberOf(currentUrl)
        val suggestedPage = suggested?.let { pageNumberOf(it) }
        val next = if (suggestedPage != null && suggestedPage > current) suggestedPage else current + 1
        return "$baseUrl/opds/last/$next"
    }

    // ═══════════════════════════════════════════════════════════════
    //  Поиск
    // ═══════════════════════════════════════════════════════════════
>>>>>>> d24a20e (FoxyBook v1.9)

    private fun encode(query: String) = URLEncoder.encode(query, "UTF-8")

    override suspend fun searchBooks(query: String, limit: Int): SearchPage<Book> =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/opdssearch?q=${encode(query)}"
            Log.d(TAG, "searchBooks | ${safeLogUrl(url)}")
            val page = parser.fetchBookPage(url, limit.coerceAtLeast(PAGE_LIMIT))
            // Каталог игнорирует параметр пагинации, поэтому «следующая
            // страница» вернула бы те же книги — не предлагаем её.
            SearchPage(page.items.distinctBy { it.id }, null)
        }

    override suspend fun searchBooksNextPage(url: String, limit: Int): SearchPage<Book> =
        withContext(Dispatchers.IO) {
            val page = parser.fetchBookPage(url, limit.coerceAtLeast(PAGE_LIMIT))
            SearchPage(page.items.distinctBy { it.id }, null)
        }

    /**
     * `/opdssearch?searchType=authors` на этом каталоге отдаёт книги, а не
     * авторов, поэтому авторы собираются из тех же книжных записей: id автора
     * лежит в `<uri>/author/id{N}</uri>` рядом с именем.
     */
    override suspend fun searchByAuthor(query: String, limit: Int): SearchPage<Author> =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/opdssearch?searchType=authors&q=${encode(query)}"
            Log.d(TAG, "searchByAuthor | ${safeLogUrl(url)}")
            val authors = try {
                parser.fetchAuthorsFromBooks(url, limit.coerceAtLeast(PAGE_LIMIT)).map {
                    Author(
                        authorId = it.authorId,
                        name = it.name,
                        bookCount = it.bookCount,
                        portraitUrl = "",
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "searchByAuthor | failed: ${e.message}")
                emptyList()
            }
            SearchPage(authors, null)
        }

    override suspend fun searchByAuthorNextPage(url: String, limit: Int): SearchPage<Author> =
        withContext(Dispatchers.IO) {
            val page = parser.fetchAuthorPage(url, limit.coerceAtLeast(PAGE_LIMIT))
            SearchPage(page.items, null)
        }

    // ═══════════════════════════════════════════════════════════════
    //  Книги автора
    // ═══════════════════════════════════════════════════════════════

    override suspend fun getAuthorBooks(authorId: String, limit: Int): List<Book> =
        withContext(Dispatchers.IO) {
            val numeric = authorId.trim().removePrefix("id")
            if (numeric.isBlank()) return@withContext emptyList()
            val primary = "$baseUrl/opds/author/id$numeric/alphabet"
            Log.d(TAG, "getAuthorBooks | ${safeLogUrl(primary)}")
            val books = collectBookPages(primary, limit)
            if (books.isNotEmpty()) return@withContext books
            // Каталог отдаёт 500 на /alphabet — откатываемся на корень автора
            collectBookPages("$baseUrl/opds/author/id$numeric", limit)
        }

    private suspend fun collectBookPages(url: String, limit: Int): List<Book> {
        val books = mutableListOf<Book>()
        val seen = mutableSetOf<Int>()
        var next: String? = url
        var page = 0
        while (!next.isNullOrBlank() && page < MAX_RESULT_PAGES && books.size < limit) {
            val result = try {
                parser.fetchBookPage(next, limit - books.size)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "collectBookPages | page $page failed: ${e.message}")
                null
            } ?: break
            for (book in result.items) {
                if (seen.add(book.id)) books.add(book)
            }
            next = result.nextUrl
            page++
        }
        return books.take(limit)
    }

    // ═══════════════════════════════════════════════════════════════
<<<<<<< HEAD
    //  Series cache — загружаем все серии один раз в память
    // ═══════════════════════════════════════════════════════════════

    private suspend fun ensureSeriesCache() {
        if (seriesCache != null || isCacheBuilding) return
        synchronized(cacheLock) {
            if (seriesCache != null || isCacheBuilding) return
            isCacheBuilding = true
        }

        Log.d(TAG, "ensureSeriesCache | building series cache...")
        val startTime = System.currentTimeMillis()
        val cache = mutableMapOf<String, MutableList<Series>>()
        var currentUrl = "$baseUrl/opds/sequences"
        var pageCount = 0
        val seenUrls = mutableSetOf<String>()

        try {
            while (currentUrl.isNotBlank() && pageCount < 200) {
                if (currentUrl in seenUrls) break
                seenUrls.add(currentUrl)

                val xml = try {
                    fetchXml(currentUrl)
                } catch (e: Exception) {
                    Log.w(TAG, "ensureSeriesCache | page $pageCount: ${e.message}")
                    break
                }

                val series = parseOpdsSequences(xml, ALL_RESULTS)
                for (s in series) {
                    val key = s.seriesTitle.trim().lowercase()
                        .replace(Regex("""[.,!?;:'\"«»()\[\]{}–—]"""), "")
                    cache.getOrPut(key) { mutableListOf() }.add(s)
                }

                pageCount++
                currentUrl = parseNextLink(xml)
            }
        } catch (e: Exception) {
            Log.e(TAG, "ensureSeriesCache | error: ${e.message}")
        }

        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "ensureSeriesCache | done: ${cache.size} titles, ${pageCount} pages, ${elapsed}ms")

        synchronized(cacheLock) {
            seriesCache = cache
            isCacheBuilding = false
        }
    }

    override suspend fun searchBySeries(query: String, limit: Int): SearchPage<Series> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().lowercase()
            .replace(Regex("""[.,!?;:'\"«»()\[\]{}]"""), "")
        val allSeries = mutableListOf<Series>()
        val encoded = URLEncoder.encode(query, "UTF-8")

        // 0. Сначала пробуем кеш всех серий
        ensureSeriesCache()
        val cache = seriesCache
        if (cache != null) {
            val cached = cache.filterKeys { it.contains(cleanQuery) }.values.flatten()
            if (cached.isNotEmpty()) {
                val result = cached.distinctBy { it.seriesId }
                Log.d(TAG, "searchBySeries | cache hit: ${result.size} series")
                return@withContext SearchPage(result, null)
            }
        }

        Log.d(TAG, "searchBySeries | cache miss, trying API search...")

        val bookUrl = "$baseUrl/opds/search?searchType=books&searchTerm=$encoded"
        Log.d(TAG, "searchBySeries | books → $bookUrl")
        val bookPages = fetchAllPages(bookUrl)
        for (xml in bookPages) {
            allSeries.addAll(parseOpdsSeriesFromBooks(xml, limit))
        }

        val authorIds = mutableListOf<String>()
        for (xml in bookPages) {
            authorIds.addAll(parseOpdsAuthorIdsFromBooks(xml, Int.MAX_VALUE))
        }
        authorIds.distinct().take(5).chunked(2).forEach { batch ->
            coroutineScope {
                batch.map { id ->
                    async {
                        try {
                            fetchAllPages("$baseUrl/opds/authorsequences/$id").flatMap { parseOpdsSeries(it, limit) }
                        } catch (e: Exception) { emptyList() }
                    }
                }.forEach { allSeries.addAll(it.await()) }
=======
    //  Серии
    // ═══════════════════════════════════════════════════════════════

    /**
     * Отдельного поиска по сериям каталог не даёт: `/opds/sequences` на нём 404,
     * буквенный индекс отсутствует. Но в выдаче поиска у книг из серии есть
     * `rel="related"` на `/opds/series/id{N}` с названием в атрибуте title —
     * это и есть источник серий.
     */
    override suspend fun searchBySeries(query: String, limit: Int): SearchPage<Series> =
        withContext(Dispatchers.IO) {
            val normalized = parser.normalizeTitle(query)
            if (normalized.isBlank()) return@withContext SearchPage(emptyList())
            val url = "$baseUrl/opdssearch?q=${encode(query)}"
            Log.d(TAG, "searchBySeries | ${safeLogUrl(url)}")
            val page = try {
                parser.fetchBooksAndSeries(url, PAGE_LIMIT)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "searchBySeries | failed: ${e.message}")
                return@withContext SearchPage(emptyList())
>>>>>>> d24a20e (FoxyBook v1.9)
            }
            val matches = page.series.filter {
                parser.normalizeTitle(it.seriesTitle).contains(normalized)
            }
            Log.d(TAG, "searchBySeries | found=${matches.size} of ${page.series.size}")
            SearchPage(matches.distinctBy { it.seriesId }.take(limit), null)
        }

<<<<<<< HEAD
        val filtered = allSeries.filter {
            it.seriesTitle.trim().lowercase()
                .replace(Regex("""[.,!?;:'\"«»()\[\]{}]"""), "")
                .contains(cleanQuery)
        }.distinctBy { it.seriesId }

        Log.d(TAG, "searchBySeries | found=${filtered.size}")
        SearchPage(filtered, null)
    }

    override suspend fun searchBySeriesNextPage(url: String, limit: Int): SearchPage<Series> = withContext(Dispatchers.IO) {
=======
    override suspend fun searchBySeriesNextPage(url: String, limit: Int): SearchPage<Series> =
>>>>>>> d24a20e (FoxyBook v1.9)
        SearchPage(emptyList())

    /** Книги серии: у Fantasy-worlds это `/opds/series/id{N}`, а не `/opds/sequencebooks/N`. */
    override suspend fun getSeriesBooks(seriesId: String, authorId: String?, limit: Int): List<Book> =
        withContext(Dispatchers.IO) {
            val numeric = seriesId.trim().removePrefix("id")
            if (numeric.isBlank()) return@withContext emptyList()
            val url = "$baseUrl/opds/series/id$numeric"
            Log.d(TAG, "getSeriesBooks | ${safeLogUrl(url)}")
            collectBookPages(url, limit)
        }

    // ═══════════════════════════════════════════════════════════════
    //  Жанры: клиентская фильтрация по book genres
    // ═══════════════════════════════════════════════════════════════

<<<<<<< HEAD
    override suspend fun searchByGenre(query: String, limit: Int): SearchPage<Book> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val cleanQuery = query.trim().lowercase()
            .replace(Regex("""[.,!?;:'\"«»()\[\]{}]"""), "")
        val url = "$baseUrl/opdssearch?searchType=books&q=$encoded"
        Log.d(TAG, "searchByGenre | $url")
        val xml = fetchXml(url)
        val allBooks = parseOpdsBooks(xml, limit)
        val filtered = allBooks.filter { book ->
            book.genres.any { it.lowercase().contains(cleanQuery) }
=======
    override suspend fun searchByGenre(query: String, limit: Int): SearchPage<Book> =
        withContext(Dispatchers.IO) {
            val normalized = parser.normalizeTitle(query)
            if (normalized.isBlank()) return@withContext SearchPage(emptyList())
            val url = "$baseUrl/opdssearch?q=${encode(query)}"
            Log.d(TAG, "searchByGenre | ${safeLogUrl(url)}")
            val page = try {
                parser.fetchBookPage(url, limit.coerceAtLeast(PAGE_LIMIT))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "searchByGenre | failed: ${e.message}")
                return@withContext SearchPage(emptyList())
            }
            val filtered = page.items.filter { book ->
                book.genres.any { parser.normalizeTitle(it).contains(normalized) }
            }
            Log.d(TAG, "searchByGenre | found=${filtered.size} from ${page.items.size}")
            SearchPage(filtered.distinctBy { it.id }, null)
>>>>>>> d24a20e (FoxyBook v1.9)
        }

<<<<<<< HEAD
    override suspend fun searchByGenreNextPage(url: String, limit: Int): SearchPage<Book> = withContext(Dispatchers.IO) {
        val xml = fetchXml(url)
        val allBooks = parseOpdsBooks(xml, limit)
        val searchTerm = Regex("""[?&]q=([^&]+)""").find(url)?.groupValues?.get(1) ?: ""
        val cleanQuery = java.net.URLDecoder.decode(searchTerm, "UTF-8").lowercase()
            .replace(Regex("""[.,!?;:'\"«»()\[\]{}]"""), "")
        val filtered = allBooks.filter { book ->
            book.genres.any { it.lowercase().contains(cleanQuery) }
=======
    override suspend fun searchByGenreNextPage(url: String, limit: Int): SearchPage<Book> =
        withContext(Dispatchers.IO) {
            val page = try {
                parser.fetchBookPage(url, limit.coerceAtLeast(PAGE_LIMIT))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "searchByGenreNextPage | failed: ${e.message}")
                null
            } ?: return@withContext SearchPage(emptyList())
            val term = Regex("""[?&]q=([^&]+)""").find(url)?.groupValues?.get(1) ?: ""
            val normalized = parser.normalizeTitle(java.net.URLDecoder.decode(term, "UTF-8"))
            val filtered = page.items.filter { book ->
                book.genres.any { parser.normalizeTitle(it).contains(normalized) }
            }
            SearchPage(filtered.distinctBy { it.id }, null)
>>>>>>> d24a20e (FoxyBook v1.9)
        }

    // ═══════════════════════════════════════════════════════════════
    //  Страница книги и скачивание
    // ═══════════════════════════════════════════════════════════════

    override suspend fun getBookInfo(id: Int): BookInfo? = withContext(Dispatchers.IO) {
        val htmlUrl = "$baseUrl/lib/id$id/"
        Log.d(TAG, "getBookInfo | HTML $htmlUrl")
        val html = try {
            parser.fetchHtml(htmlUrl)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "getBookInfo | error", e)
            return@withContext null
        }
        parseHtmlBookInfo(html, id)
    }

    override fun getDownloadUrl(id: String, format: BookFormat): String =
        "$baseUrl/lib/id$id/download/${format.extension}"

    override suspend fun downloadBook(
        id: String,
        format: BookFormat,
        onProgress: (Float) -> Unit
    ): ResponseBody? = withContext(Dispatchers.IO) {
        val url = getDownloadUrl(id, format)
        var lastException: Exception? = null

        for (attempt in 1..MAX_RETRIES) {
            try {
                val request = Request.Builder().url(url).build()
                val response = downloadClient.executeCancellable(request)
                if (!response.isSuccessful) {
                    val code = response.code
                    response.close()
                    lastException = IOException("HTTP $code")
                    if (code in 500..599 || code == 429) networkClient.markMirrorUnhealthy(baseUrl)
                    if (code in 400..499 && code !in listOf(429, 403)) break
                    if (attempt < MAX_RETRIES) delay(1000L * attempt)
                    continue
                }
                val contentType = response.header("Content-Type").orEmpty()
                if (contentType.startsWith("text/html")) {
                    response.close()
                    lastException = IOException("Server returned HTML instead of ${format.extension}")
                    break
                }
                networkClient.markMirrorHealthy(baseUrl)
                return@withContext response.body
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                if (attempt < MAX_RETRIES) delay(1000L * attempt)
            }
        }
        throw lastException ?: IOException("Download failed")
    }

    // ═══════════════════════════════════════════════════════════════
    //  HTML book info
    // ═══════════════════════════════════════════════════════════════

    private fun parseHtmlBookInfo(html: String, bookId: Int): BookInfo? {
        val doc = Jsoup.parse(html, baseUrl)

        val rawTitle = doc.selectFirst("h1")?.text()?.trim()
            ?: doc.selectFirst("h2")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: return null

        val article = doc.selectFirst("article")
        val authorScope = article ?: doc
        val authorFromLink = authorScope.select("a[href]").firstOrNull { a ->
            val href = a.attr("href")
            val text = a.text().trim().lowercase()
            (Regex("""/a/(\d+)""").containsMatchIn(href) || Regex("""/author/id(\d+)""").containsMatchIn(href)) &&
                text != "все" && text != "флибуста" && text != "coollib" && text != "fantasy-worlds"
        }?.text()?.trim()

        val authorFromTitle = if (authorFromLink == null) {
            val dash = rawTitle.indexOf(" — ")
            if (dash > 0) rawTitle.substring(0, dash).trim()
            else rawTitle.indexOf(" - ").let { if (it > 0) rawTitle.substring(0, it).trim() else null }
        } else null

        val author = authorFromLink ?: authorFromTitle ?: "Unknown Author"

        val title = rawTitle
            .let { t -> val d = t.indexOf(" — "); if (d > 0) t.substring(d + 3).trim() else t }
            .let { t -> val d = t.indexOf(" - "); if (d > 0 && authorFromTitle != null) t.substring(d + 3).trim() else t }

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

        val description = buildString {
            val annotationH2 = doc.selectFirst("h2:contains(Аннотация)")
            if (annotationH2 != null) {
                val parts = mutableListOf<String>()
                var current = annotationH2.nextSibling()
                while (current != null) {
                    if (current is org.jsoup.nodes.Element && current.tagName() == "h2") break
                    val text = current.toString().trim()
                    if (text.isNotBlank()) parts.add(text)
                    current = current.nextSibling()
                }
<<<<<<< HEAD
                val raw = parts.joinToString(" ")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(500)
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
                    ?.takeIf { it.isNotBlank() }?.let { append(it.take(500)) }
            }
            if (isEmpty()) {
                doc.selectFirst("meta[name=description]")?.attr("content")?.trim()
                    ?.takeIf { it.isNotBlank() }?.let { append(it.take(500)) }
            }
            if (isEmpty()) {
                for (p in doc.select("p")) {
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

        val genres = mutableListOf<BookGenre>()
        for (genreLink in doc.select("a[href^=/g/]")) {
            val genreTitle = genreLink.text().trim()
            if (genreTitle.isNotBlank()) {
                genres.add(BookGenre(genreLink.attr("href").removePrefix("/g/"), genreTitle))
            }
        }

        val formatExtensions = listOf("epub", "fb2", "mobi", "txt", "pdf")
        val availableFormats = doc.select("a[href]").mapNotNull { link ->
            val href = link.attr("href")
            formatExtensions.firstOrNull { ext ->
                (href.endsWith("/$ext") || href.endsWith(".$ext")) &&
                    (href.contains("/b/$bookId/") ||
                        href.contains("/lib/id${bookId}/download/") ||
                        href.contains("$bookId.$ext"))
            }
        }.distinct()

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
