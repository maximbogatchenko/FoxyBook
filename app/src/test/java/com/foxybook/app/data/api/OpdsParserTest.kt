package com.foxybook.app.data.api

import android.util.Xml
import com.foxybook.app.core.models.Author
import com.foxybook.app.core.models.Book
import com.foxybook.app.core.models.Series
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.kxml2.io.KXmlParser

/**
 * Тесты на реальных ответах каталогов: фикстуры в resources/fixtures сняты
 * с живых flibusta.site, coollib.net и fantasy-worlds.org.
 *
 * Это единственная защита разбора XML: без неё регресс видно только на
 * устройстве, а формат фидов у каталогов разный (у флибусты относительные
 * href и md5-хеш в `<id>`, у fantasy-worlds — `books:NNN` и `/lib/idNNN/`).
 */
class OpdsParserTest {

    private fun parserFor(baseUrl: String) = OpdsParser(
        baseUrlProvider = { baseUrl },
        fetchFn = { error("no network in unit tests") }
    )

    private val flibusta = parserFor("https://flibusta.site")
    private val coollib = parserFor("https://coollib.net")
    private val worldFant = parserFor("https://fantasy-worlds.org")

    @Before
    fun setUp() {
        // android.util.Xml на JVM не реализован — подменяем на KXmlParser
        mockkStatic(Xml::class)
        every { Xml.newPullParser() } answers { KXmlParser() }
    }

    @After
    fun tearDown() {
        unmockkStatic(Xml::class)
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "fixture not found: $name"
        }.bufferedReader().use { it.readText() }

    // ─────────────── Навигация по каталогу ───────────────

    @Test
    fun `flibusta root feed exposes navigation entries`() {
        val entries = flibusta.parseNavEntries(fixture("flibusta-opds.xml"))

        assertTrue("expected some nav entries", entries.isNotEmpty())
        assertTrue(entries.any { it.name.contains("Новинки", ignoreCase = true) })
    }

    @Test
    fun `nav entries have absolute hrefs`() {
        val entries = flibusta.parseNavEntries(fixture("flibusta-opds.xml"))

        assertTrue(entries.isNotEmpty())
        entries.forEach {
            assertTrue("url should be absolute: ${it.url}", it.url.startsWith("https://flibusta.site"))
        }
    }

    @Test
    fun `genre catalog parses into nav entries`() {
        val genres = flibusta.parseNavEntries(fixture("flibusta-genres.xml"))

        assertEquals(24, genres.size)
        assertTrue(genres.all { it.url.startsWith("https://flibusta.site/opds/genres/") })
    }

    @Test
    fun `coollib genre catalog keeps absolute hrefs from the feed`() {
        val genres = coollib.parseNavEntries(fixture("coollib-genres.xml"))

        assertEquals(39, genres.size)
        assertTrue(genres.all { it.url.startsWith("https://coollib.net/") })
    }

    // ─────────────── Книги ───────────────

    @Test
    fun `flibusta new books take id from the book link, not from the md5 id`() {
        // <id> у флибусты — md5-хеш: "tag:book:1628fc6dcc…". Наивный разбор
        // цифр давал id 1628 вместо 891750.
        val books = mutableListOf<Book>()
        flibusta.parseFeedPage(fixture("flibusta-new.xml"), books = books)

        assertEquals(20, books.size)
        assertEquals(891750, books.first().id)
        assertTrue(books.none { it.id == 1628 })
    }

    @Test
    fun `flibusta new books have absolute cover urls and real author`() {
        val books = mutableListOf<Book>()
        flibusta.parseFeedPage(fixture("flibusta-new.xml"), books = books)

        val first = books.first()
        assertEquals(
            "https://flibusta.site/i/50/891750/img_oebps_dta5rl0zfhgz.jpeg",
            first.coverUrl
        )
        assertEquals("Бравая Анна", first.author)
    }

    @Test
    fun `flibusta search exposes available formats from acquisition links`() {
        val books = mutableListOf<Book>()
        flibusta.parseFeedPage(fixture("flibusta-search.xml"), books = books)

        val first = books.first()
        assertEquals(461338, first.id)
        assertTrue(
            "expected fb2/epub/txt in ${first.formats}",
            first.formats.containsAll(listOf("fb2", "epub", "txt"))
        )
    }

    @Test
    fun `flibusta search keeps absolute cover url from relative href`() {
        val books = mutableListOf<Book>()
        flibusta.parseFeedPage(fixture("flibusta-search.xml"), books = books)

        assertEquals("https://flibusta.site/i/38/461338/cover.jpg", books.first().coverUrl)
    }

    @Test
    fun `coollib new books parse id title and cover`() {
        val books = mutableListOf<Book>()
        coollib.parseFeedPage(fixture("coollib-new.xml"), books = books)

        assertEquals(20, books.size)
        val first = books.first()
        assertEquals(859853, first.id)
        assertEquals("https://coollib.net/i/53/859853/cover.jpg", first.coverUrl)
        assertTrue(first.genres.isNotEmpty())
    }

    @Test
    fun `fantasy-worlds books take id from books tag`() {
        val books = mutableListOf<Book>()
        worldFant.parseFeedPage(fixture("worldfant-last.xml"), books = books)

        // 16 записей, но одна из них — служебная «Следующая страница: 2»,
        // поэтому книг 15
        assertEquals(15, books.size)
        assertEquals(40408, books.first().id)
        assertEquals("https://fantasy-worlds.org/img/preview/404/40408.jpg", books.first().coverUrl)
    }

    @Test
    fun `fantasy-worlds navigation entry for the next page is detected`() {
        // У fantasy-worlds следующая страница — обычная запись
        // <id>tag:root:nextpage</id> со ссылкой /opds/last/2/, а не rel="next".
        val nav = mutableListOf<OpdsParser.GenreNavEntry>()
        worldFant.parseFeedPage(fixture("worldfant-last.xml"), navEntries = nav)

        val nextPage = nav.firstOrNull { it.url.contains("/opds/last/") }
        assertNotNull("expected a next-page nav entry", nextPage)
        assertEquals("https://fantasy-worlds.org/opds/last/2/", nextPage!!.url)
    }

    @Test
    fun `book limit is respected`() {
        val books = mutableListOf<Book>()
        flibusta.parseFeedPage(fixture("flibusta-new.xml"), limit = 5, books = books)

        assertEquals(5, books.size)
    }

    // ─────────────── Авторы ───────────────

    @Test
    fun `flibusta author search yields id name and book count`() {
        val authors = mutableListOf<Author>()
        flibusta.parseFeedPage(fixture("flibusta-authors.xml"), authors = authors)

        assertEquals(3, authors.size)
        val first = authors.first()
        assertEquals("19189", first.authorId)
        assertTrue(first.name.contains("Стругацкий"))
        assertTrue("expected book count from content, got ${first.bookCount}", first.bookCount > 0)
        assertEquals("https://flibusta.site/ia/41/32841/3_1.jpg", first.portraitUrl)
    }

    @Test
    fun `coollib author search yields id name and book count`() {
        val authors = mutableListOf<Author>()
        coollib.parseFeedPage(fixture("coollib-authors.xml"), authors = authors)

        assertEquals(2, authors.size)
        assertEquals("3272", authors.first().authorId)
        assertEquals(274, authors.first().bookCount)
    }

    @Test
    fun `book entries contribute their authors with ids`() {
        // fantasy-worlds отдаёт id автора только в <uri>/author/id{N}</uri>,
        // поэтому он вытаскивается оттуда, а не из <id> записи.
        val pairs = mutableListOf<Pair<String, String>>()
        worldFant.parseFeedPage(fixture("worldfant-last.xml"), bookAuthors = pairs)

        assertTrue("expected author pairs", pairs.isNotEmpty())
        assertTrue(pairs.contains("Сэм Ребелейн" to "9544"))
    }

    // ─────────────── Серии ───────────────

    @Test
    fun `flibusta sequences catalog yields series id and title`() {
        val series = mutableListOf<Series>()
        flibusta.parseFeedPage(fixture("flibusta-sequences.xml"), series = series)

        assertEquals(20, series.size)
        assertEquals("96783", series.first().seriesId)
        assertTrue(series.first().seriesTitle.isNotBlank())
        assertTrue(series.first().seriesUrl.startsWith("https://flibusta.site/opds/sequencebooks/"))
    }

    @Test
    fun `flibusta search yields series from related links`() {
        // Серия лежит в rel="related" книжной записи — второй проход по фиду не нужен.
        val series = mutableListOf<Series>()
        flibusta.parseFeedPage(fixture("flibusta-search.xml"), series = series)

        assertTrue("expected series in book search", series.isNotEmpty())
        val ids = series.map { it.seriesId }
        assertTrue("expected series 85234 from the real feed", ids.contains("85234"))
        assertTrue(series.all { it.seriesUrl.startsWith("https://flibusta.site/opds/sequencebooks/") })
    }

    @Test
    fun `fantasy-worlds search yields series from series id links`() {
        val series = mutableListOf<Series>()
        worldFant.parseFeedPage(fixture("worldfant-search.xml"), series = series)

        assertTrue("expected series in search feed", series.isNotEmpty())
        assertEquals("160", series.first().seriesId)
        assertTrue(series.first().seriesUrl.startsWith("https://fantasy-worlds.org/opds/series/"))
    }

    // ─────────────── Пагинация ───────────────

    @Test
    fun `next link is captured in the same pass as the books`() {
        val books = mutableListOf<Book>()
        val next = flibusta.parseFeedPage(fixture("coollib-search.xml"), books = books)

        assertNotNull(next)
        assertTrue("next should be absolute: $next", next!!.startsWith("https://coollib.net/opds/search"))
        assertTrue("page should have books too", books.isNotEmpty())
    }

    @Test
    fun `next link is resolved against base url`() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry><title>a</title><link href="/opds/page/2" rel="next" type="application/atom+xml"/></entry>
            </feed>
        """.trimIndent()

        val plain = parserFor("https://example.test")
        assertEquals("https://example.test/opds/page/2", plain.parseNextLink(xml))
    }

    @Test
    fun `feed without next link yields null`() {
        assertEquals(null, flibusta.parseNextLink(fixture("worldfant-opds.xml")))
    }

    // ─────────────── Контракты и регрессы ───────────────

    @Test
    fun `malformed xml surfaces an exception for the caller to handle`() {
        // Контракт: битый ответ не должен молча превратиться в «пусто».
        val failed = runCatching { flibusta.parseNavEntries("<feed><entry><title>Broken") }
        assertTrue(failed.isFailure)
    }

    @Test
    fun `xml with leading whitespace before declaration is parsed`() {
        val entries = flibusta.parseNavEntries(
            "\n  <?xml version=\"1.0\"?><feed xmlns=\"http://www.w3.org/2005/Atom\">" +
                "<entry><title>Новинки</title><link href=\"/opds/new\"/></entry></feed>"
        )
        assertEquals(1, entries.size)
        assertEquals("Новинки", entries[0].name)
    }

    @Test
    fun `empty input produces no entries`() {
        assertTrue(flibusta.parseNavEntries("").isEmpty())
    }

    @Test
    fun `parseBookId handles every real id format`() {
        assertEquals(12345, flibusta.parseBookId("tag:root:book:12345"))
        assertEquals(40408, worldFant.parseBookId("books:40408"))
        assertEquals(859853, coollib.parseBookId("tag:book:859853"))
        assertEquals(13503, worldFant.parseBookId("/lib/id13503/download/"))
    }

    @Test
    fun `md5 book id is not mistaken for a number`() {
        // Регресс: tag:book:1628fc6dcc… не должен давать id 1628
        assertEquals(null, flibusta.parseBookId("tag:book:1628fc6dcc8993245e1449651775fab1"))
    }

    @Test
    fun `normalizeTitle ignores punctuation and case`() {
        assertEquals(flibusta.normalizeTitle("Вселенная мира Ночи"), flibusta.normalizeTitle("вселенная мира ночи"))
        assertEquals(flibusta.normalizeTitle("Самоубийство в колизее"), flibusta.normalizeTitle("Самоубийство, в колизее!"))
    }

    @Test
    fun `fetching without network fails instead of returning empty`() {
        val failed = runBlocking {
            runCatching { flibusta.fetchBookPage("https://flibusta.site/opds/new/0/new", 10) }
        }
        assertTrue(failed.isFailure)
    }
}
