package com.foxybook.app.data.api

import android.util.Xml
import com.foxybook.app.core.models.Author
import com.foxybook.app.core.models.Book
import com.foxybook.app.core.models.Series
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.util.concurrent.TimeUnit

/**
 * Проверка разбора на живых каталогах. Обычно не запускается: фикстуры в
 * resources/fixtures сняты с тех же адресов. Включается явно — нужно убедиться,
 * что каталоги не поменяли формат:
 *
 *     OPDS_LIVE_TESTS=1 ./gradlew :app:testDebugUnitTest --tests '*LiveOpdsSmokeTest*'
 *
 * Тест ничего не утверждает о конкретных книгах: содержимое выдачи меняется
 * каждый день. Проверяется только форма — то, что должно выполняться всегда.
 */
class LiveOpdsSmokeTest {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun parserFor(base: String) = OpdsParser(
        baseUrlProvider = { base },
        fetchFn = { url ->
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code} for $url")
                response.body?.string().orEmpty()
            }
        }
    )

    @Before
    fun setUp() {
        mockkStatic(Xml::class)
        every { Xml.newPullParser() } answers { KXmlParser() }
    }

    @After
    fun tearDown() {
        io.mockk.unmockkStatic(Xml::class)
    }

    private fun onlyIfEnabled() =
        assumeTrue("set OPDS_LIVE_TESTS=1 to run live catalog checks", System.getenv("OPDS_LIVE_TESTS") == "1")

    private fun assertSaneBooks(books: List<Book>, source: String) {
        assertTrue("$source: expected books", books.isNotEmpty())
        books.forEach {
            assertTrue("$source: id must be positive, got ${it.id}", it.id > 0)
            assertTrue("$source: title must not be blank", it.title.isNotBlank())
            assertTrue("$source: cover should be absolute, got ${it.coverUrl}", it.coverUrl.isEmpty() || it.coverUrl.startsWith("http"))
        }
    }

    @Test
    fun `flibusta new books and search parse`() = runBlocking {
        onlyIfEnabled()
        val parser = parserFor("https://flibusta.site")

        val newBooks = mutableListOf<Book>()
        val newNext = parser.parseFeedPage(parser.fetchXml("${parser.getBaseUrl()}/opds/new/0/new"), books = newBooks)
        assertSaneBooks(newBooks, "flibusta/new")
        assertTrue("flibusta/new: expected a next page", newNext != null)

        val searchBooks = mutableListOf<Book>()
        parser.parseFeedPage(
            parser.fetchXml("${parser.getBaseUrl()}/opds/search?searchType=books&searchTerm=${enc("квант")}"),
            books = searchBooks
        )
        assertSaneBooks(searchBooks, "flibusta/search")
    }

    @Test
    fun `flibusta genres authors and sequences parse`() = runBlocking {
        onlyIfEnabled()
        val parser = parserFor("https://flibusta.site")
        val base = parser.getBaseUrl()

        val genres = parser.parseNavEntries(parser.fetchXml("$base/opds/genres"))
        assertTrue("flibusta/genres: expected genres", genres.isNotEmpty())
        assertTrue(genres.all { it.url.startsWith("https://") })

        val authors = mutableListOf<Author>()
        parser.parseFeedPage(
            parser.fetchXml("$base/opds/search?searchType=authors&searchTerm=${enc("Стругацкий")}"),
            authors = authors
        )
        assertTrue("flibusta/authors: expected authors", authors.isNotEmpty())
        authors.forEach {
            assertTrue("flibusta/authors: id must be numeric, got ${it.authorId}", it.authorId.toIntOrNull() != null)
        }

        val series = mutableListOf<Series>()
        parser.parseFeedPage(parser.fetchXml("$base/opds/sequences"), series = series)
        assertTrue("flibusta/sequences: expected series", series.isNotEmpty())
        series.forEach {
            assertTrue("flibusta/sequences: id must be numeric, got ${it.seriesId}", it.seriesId.toIntOrNull() != null)
        }
    }

    @Test
    fun `coollib new books authors and genres parse`() = runBlocking {
        onlyIfEnabled()
        // coollib отвечает на оба домена с одного IP и легко ставит его в бан
        // при частых прогонах; зеркало .cc снимает вопрос доступности.
        val base = firstReachableBase(listOf("https://coollib.net", "https://coollib.cc"))
        // Пропуск, а не «успех»: иначе проверка молча ничего не делала.
        assumeTrue("coollib недоступен с этой машины", base != null)
        val parser = parserFor(base ?: "https://coollib.net")
        val root = parser.getBaseUrl()

        val books = mutableListOf<Book>()
        parser.parseFeedPage(parser.fetchXml("$root/opds/new/0/new"), books = books)
        assertSaneBooks(books, "coollib/new")

        val authors = mutableListOf<Author>()
        parser.parseFeedPage(
            parser.fetchXml("$root/opds/search?searchType=authors&searchTerm=${enc("Стругацкий")}"),
            authors = authors
        )
        assertTrue("coollib/authors: expected authors", authors.isNotEmpty())

        val genres = parser.parseNavEntries(parser.fetchXml("$root/opds/genres"))
        assertTrue("coollib/genres: expected genres", genres.isNotEmpty())
    }

    /** Первый доступный базовый адрес каталога или null, если оба недоступны. */
    private fun firstReachableBase(candidates: List<String>): String? {
        for (candidate in candidates) {
            val ok = runCatching {
                http.newCall(Request.Builder().url("$candidate/opds/new/0/new").build())
                    .execute().use { it.isSuccessful }
            }.getOrDefault(false)
            if (ok) return candidate
        }
        return null
    }

    @Test
    fun `fantasy-worlds new books authors and series parse`() = runBlocking {
        onlyIfEnabled()
        val parser = parserFor("https://fantasy-worlds.org")
        val base = parser.getBaseUrl()

        val books = mutableListOf<Book>()
        parser.parseFeedPage(parser.fetchXml("$base/opds/last/0"), books = books)
        assertSaneBooks(books, "worldfant/last")

        val series = mutableListOf<Series>()
        parser.parseFeedPage(
            parser.fetchXml("$base/opdssearch?searchType=authors&q=${enc("Ребелейн")}"),
            series = series
        )
        assertTrue("worldfant/search: expected series", series.isNotEmpty())
        series.forEach {
            assertTrue("worldfant/series: unexpected url ${it.seriesUrl}", it.seriesUrl.contains("/opds/series/"))
        }
    }

    @Test
    fun `fantasy-worlds next page entry is present`() = runBlocking {
        onlyIfEnabled()
        val parser = parserFor("https://fantasy-worlds.org")
        val books = mutableListOf<Book>()
        val nav = mutableListOf<OpdsParser.GenreNavEntry>()
        parser.parseFeedPage(parser.fetchXml("${parser.getBaseUrl()}/opds/last/0"), books = books, navEntries = nav)

        assertTrue("worldfant: expected books", books.isNotEmpty())
        assertTrue(
            "worldfant: expected a /opds/last/ navigation entry, got ${nav.map { it.url }}",
            nav.any { it.url.contains("/opds/last/") }
        )
    }

    @Test
    fun `flibusta search without matches yields no books instead of an error`() {
        onlyIfEnabled()
        val parser = parserFor("https://flibusta.site")
        val books = mutableListOf<Book>()
        // Термина нет в каталоге: каталог отдаёт пустую выдачу, и это не ошибка
        runBlocking {
            parser.parseFeedPage(
                parser.fetchXml("${parser.getBaseUrl()}/opds/search?searchType=books&searchTerm=${enc("zzzqqqxxwwv")}"),
                books = books
            )
        }
        assertTrue("flibusta/search: expected an empty result, not a failure", books.isEmpty())
    }

    @Test
    fun `feed cache stores coollib responses that the server leaves uncacheable`() = runBlocking {
        onlyIfEnabled()
        // coollib не отдаёт Cache-Control вообще — без интерсептора OkHttp
        // не кеширует такой ответ, и каждая загрузка качает фид заново.
        val base = firstReachableBase(listOf("https://coollib.net", "https://coollib.cc"))
        assumeTrue("coollib недоступен с этой машины", base != null)
        val cacheDir = java.nio.file.Files.createTempDirectory("foxybook-cache")
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .cache(okhttp3.Cache(cacheDir.toFile(), 5L * 1024 * 1024))
            .addNetworkInterceptor(
                com.foxybook.app.core.network.feedCacheInterceptorFor(FeedTtl)
            )
            .build()
        val url = "$base/opds/new/0/new"

        val first = client.newCall(Request.Builder().url(url).build()).execute()
        val firstFromCache = first.cacheResponse != null
        first.close()

        val second = client.newCall(Request.Builder().url(url).build()).execute()
        val secondFromCache = second.cacheResponse != null
        val secondBody = second.body?.string()
        second.close()

        assertTrue("first request must hit the network", !firstFromCache)
        assertTrue("second request must be served from cache", secondFromCache)
        assertTrue("cached body must not be empty", !secondBody.isNullOrBlank())
        cacheDir.toFile().deleteRecursively()
        Unit
    }

    @Test
    fun `feed cache respects explicit no-store from the catalog`() = runBlocking {
        onlyIfEnabled()
        // fantasy-worlds явно запрещает кеширование — уважаем это решение.
        val cacheDir = java.nio.file.Files.createTempDirectory("foxybook-cache-nostore")
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .cache(okhttp3.Cache(cacheDir.toFile(), 5L * 1024 * 1024))
            .addNetworkInterceptor(
                com.foxybook.app.core.network.feedCacheInterceptorFor(FeedTtl)
            )
            .build()
        val url = "https://fantasy-worlds.org/opds/last/0"

        client.newCall(Request.Builder().url(url).build()).execute().close()
        val second = client.newCall(Request.Builder().url(url).build()).execute()
        val servedFromCache = second.cacheResponse != null
        second.close()

        assertTrue("no-store must be honoured", !servedFromCache)
        cacheDir.toFile().deleteRecursively()
        Unit
    }

    private fun enc(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private companion object {
        /** Те же правила TTL, что и в OkHttpClientProvider.feedTtlSeconds. */
        val FeedTtl: (String) -> Int = { url ->
            val path = url.substringBefore('?')
            when {
                path.endsWith("/opds/new") || path.contains("/opds/new/") || path.contains("/opds/last") -> 600
                path.contains("/opds/genres") -> 6 * 3600
                path.contains("/opds/sequencesindex") || path.contains("/opds/authorsindex") -> 6 * 3600
                path.contains("/opds/sequences") -> 6 * 3600
                path.contains("/opds/search") || path.contains("/opdssearch") -> 120
                path.contains("/opds") -> 600
                else -> 0
            }
        }
    }
}
