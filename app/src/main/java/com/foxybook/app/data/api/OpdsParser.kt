package com.foxybook.app.data.api

import com.foxybook.app.core.models.Author
import com.foxybook.app.core.models.Book
<<<<<<< HEAD
import com.foxybook.app.core.models.BookGenre
import com.foxybook.app.core.models.BookInfo
=======
>>>>>>> d24a20e (FoxyBook v1.9)
import com.foxybook.app.core.models.Series
import com.foxybook.app.core.utils.XmlUtils
import org.xmlpull.v1.XmlPullParser

/**
<<<<<<< HEAD
 * Shared OPDS parser extracted from FlibustaApiOpdsImpl and FantasyWorldsApiOpdsImpl.
 *
 * Contains all pure OPDS/XML parsing logic that was duplicated ~700 lines across the two files.
 * Each API implementation creates an instance with its own [baseUrlProvider] and [fetchFn].
 *
 * Thread-safe: all methods are stateless — they parse input and return results without mutation.
 */
class OpdsParser(
    private val baseUrlProvider: () -> String,
    private val fetchFn: (String) -> String
) {
    private val resolvedBaseUrl: String get() = baseUrlProvider()
    fun getBaseUrl(): String = resolvedBaseUrl

    // ═══════════════════════════════════════════════════════════════
    //  Genre navigation entries (OPDS catalog)
    // ═══════════════════════════════════════════════════════════════

    data class GenreNavEntry(
        val name: String,
        val url: String
    )

    /**
     * Парсит любой навигационный OPDS-фид: достаёт title + href из каждого entry.
     * Подходит для /opds/genres (список жанров) и /opds/genres/Фантастика (поджанры).
     */
    fun parseNavEntries(xml: String): List<GenreNavEntry> {
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
                                    linkHref = if (href.startsWith("http")) href else "$resolvedBaseUrl$href"
=======
 * Общий разбор OPDS/Atom для всех каталогов (Flibusta, CoolLib, Fantasy-worlds).
 *
 * Каждая страница разбирается ровно один раз: [parseFeedPage] за один проход
 * отдаёт и элементы, и ссылку на следующую страницу. Раньше страница сначала
 * целиком разбиралась на книги, а потом ещё раз целиком — ради одного
 * `<link rel="next">`.
 *
 * Thread-safe: методы не хранят состояние, только разбирают вход.
 */
class OpdsParser(
    private val baseUrlProvider: () -> String,
    private val fetchFn: suspend (String) -> String
) {

    private companion object {
        /**
         * Паттерны живут в companion, а не создаются в циле: `Regex("...")`
         * компилирует новый Pattern на каждый вызов, и в исходном коде это
         * давало сотни компиляций на одну страницу фида.
         */
        val RE_BOOK_PATH = Regex("""/b/(\d+)""")
        val RE_LIB_PATH = Regex("""/lib/id(\d+)""")
        val RE_BOOKS_TAG = Regex("""books:(\d+)$""")
        val RE_TAG_BOOK = Regex("""tag:book:(\d+)$""")
        val RE_AUTHOR_PATH = Regex("""/author/(\d+)""")
        val RE_FW_AUTHOR_PATH = Regex("""/opds/author/id(\d+)""")
        val RE_AUTHOR_URI = Regex("""/a/(\d+)""")
        val RE_AUTHOR_URI_ALT = Regex("""/author/id(\d+)""")
        val RE_TAG_AUTHOR = Regex("""tag:author:(\d+)""")
        val RE_TAG_SEQUENCE = Regex("""tag:sequence:(\d+)""")
        val RE_TAG_AUTHOR_SEQUENCE = Regex("""tag:author:(\d+):sequence:(\d+)""")
        val RE_SEQUENCE_PATH = Regex("""/sequence/(\d+)""")
        val RE_SEQUENCE_BOOKS = Regex("""/opds/sequencebooks/(\d+)""")
        val RE_FW_SEQUENCE = Regex("""/opds/series/id(\d+)""")
        val RE_ALL_DIGITS = Regex("""(\d+)""")
        val RE_HTML_TAG = Regex("""<[^>]+>""")
        val RE_WHITESPACE = Regex("""\s+""")
        val RE_PUNCTUATION = Regex("""[.,!?;:'\"«»()\[\]{}–—]""")
        val RE_YEAR_PREFIX = Regex("""^[Гг]од издани[яе].*$""")
        val RE_BOOK_SLASH = Regex("""/book/(\d+)""")
        val RE_TRAILING_ID = Regex("""[:/](\d{3,})$""")

        val KNOWN_FORMATS = listOf("epub", "fb2", "mobi", "txt", "pdf")
    }

    private val resolvedBaseUrl: String get() = baseUrlProvider().trimEnd('/')
    fun getBaseUrl(): String = resolvedBaseUrl

    /** Каталоги отдают относительные href — приводим их к абсолютным. */
    fun absolute(href: String): String =
        if (href.startsWith("http")) href else "$resolvedBaseUrl$href"

    /** Страница фида: элементы + ссылка на следующую страницу. */
    data class FeedPage<T>(val items: List<T>, val nextUrl: String?)

    /** Навигационная запись (жанр, буква алфавитного индекса). */
    data class GenreNavEntry(val name: String, val url: String)

    private data class SequenceIds(val id: String, val authorId: String?)

    // ═══════════════════════════════════════════════════════════════
    //  Один общий проход по фиду
    // ═══════════════════════════════════════════════════════════════

    /**
     * Разбирает страницу Atom/OPDS за один проход.
     *
     * Порядок элементов внутри `<entry>` у каталогов произвольный, поэтому всё
     * собирается в «сырые» поля и материализуется на закрытии `</entry>`.
     *
     * id книги берётся из ссылок (`/b/N`, `/lib/idN`) раньше, чем из `<id>`:
     * у флибусты `<id>` — это md5-хеш, и вытаскивание из него «цифр» давало
     * неверный номер книги.
     */
    fun parseFeedPage(
        xml: String,
        limit: Int = Int.MAX_VALUE,
        books: MutableList<Book> = mutableListOf(),
        authorIds: MutableList<String> = mutableListOf(),
        authors: MutableList<Author> = mutableListOf(),
        series: MutableList<Series> = mutableListOf(),
        navEntries: MutableList<GenreNavEntry> = mutableListOf(),
        bookAuthors: MutableList<Pair<String, String>> = mutableListOf()
    ): String? {
        val parser = XmlUtils.createParser(xml)
        var eventType = parser.eventType
        var nextUrl: String? = null

        var inEntry = false
        var title: String? = null
        var entryId: String? = null
        var idFromEntryTag: Int? = null
        var idFromLink: Int? = null
        var hasBookId = false
        var description = ""
        var coverUrl: String? = null
        var itemCount = 0
        var authorIdValue: String? = null
        val authorNames = mutableListOf<String>()
        val genres = mutableListOf<String>()
        val formats = mutableListOf<String>()
        val bookSeries = linkedMapOf<String, Series>()
        var navHref: String? = null
        val entryAuthorPairs = mutableListOf<Pair<String, String>>()
        var pendingAuthorName: String? = null

        fun resetEntry() {
            inEntry = true
            title = null
            entryId = null
            idFromEntryTag = null
            idFromLink = null
            hasBookId = false
            description = ""
            coverUrl = null
            itemCount = 0
            authorIdValue = null
            authorNames.clear()
            genres.clear()
            formats.clear()
            bookSeries.clear()
            navHref = null
            entryAuthorPairs.clear()
            pendingAuthorName = null
        }

        fun addFormat(raw: String) {
            val ext = raw.trim().trimEnd('/').substringAfterLast('/').lowercase()
            if (ext in KNOWN_FORMATS && ext !in formats) formats.add(ext)
        }

        fun addBookSeries(seriesId: String, href: String, linkTitle: String?) {
            if (bookSeries.containsKey(seriesId)) return
            val rawTitle = (linkTitle ?: "").ifBlank { "" }
            val cleanTitle = rawTitle
                .replace(Regex("""^Все книги серии[ :]+"""), "")
                .trim(' ', '"', '«', '»', '“', '”')
            bookSeries[seriesId] = Series(
                seriesId = seriesId,
                seriesTitle = cleanTitle,
                seriesUrl = absolute(href),
            )
        }

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            if (eventType == XmlPullParser.START_TAG) {
                when (name) {
                    "link" -> {
                        val rel = parser.getAttributeValue(null, "rel").orEmpty()
                        val href = parser.getAttributeValue(null, "href")
                        if (href != null) {
                            when {
                                rel == "next" -> {
                                    if (nextUrl == null) nextUrl = absolute(href)
                                }

                                !inEntry -> Unit

                                rel == "http://opds-spec.org/image" ||
                                    rel == "http://opds-spec.org/image/thumbnail" -> {
                                    if (coverUrl == null) coverUrl = absolute(href)
                                }

                                rel == "http://opds-spec.org/acquisition/open-access" -> addFormat(href)

                                rel == "related" -> {
                                    RE_SEQUENCE_BOOKS.find(href)?.let {
                                        addBookSeries(it.groupValues[1], href, parser.getAttributeValue(null, "title"))
                                    }
                                    if (bookSeries.isEmpty()) {
                                        RE_FW_SEQUENCE.find(href)?.let {
                                            addBookSeries(it.groupValues[1], href, parser.getAttributeValue(null, "title"))
                                        }
                                    }
                                    if (authorIdValue == null) {
                                        authorIdValue = RE_FW_AUTHOR_PATH.find(href)?.groupValues?.get(1)
                                            ?: RE_AUTHOR_PATH.find(href)?.groupValues?.get(1)
                                    }
                                }

                                else -> {
                                    // id книги надёжнее брать из ссылок, а не из <id>
                                    val bookId = RE_LIB_PATH.find(href)?.groupValues?.get(1)
                                        ?: RE_BOOK_PATH.find(href)?.groupValues?.get(1)
                                    if (bookId != null) {
                                        hasBookId = true
                                        if (idFromLink == null) idFromLink = bookId.toIntOrNull()
                                    }
                                    if (authorIdValue == null) {
                                        RE_AUTHOR_PATH.find(href)?.let { authorIdValue = it.groupValues[1] }
                                    }
                                    if (navHref == null && (rel.isEmpty() || rel == "alternate")) {
                                        navHref = href
                                    }
>>>>>>> d24a20e (FoxyBook v1.9)
                                }
                            }
                        }
                    }
<<<<<<< HEAD
                }
                XmlPullParser.END_TAG -> {
                    if (name == "entry") {
                        inEntry = false
                        if (title != null && linkHref != null) {
                            entries.add(GenreNavEntry(title!!, linkHref!!))
                        }
                    }
=======

                    "entry" -> resetEntry()

                    "title" -> if (inEntry) title = parser.nextText().trim()

                    "id" -> if (inEntry) {
                        val text = parser.nextText().trim()
                        entryId = text
                        // Только anchored-паттерны: tag:book:1628fc6dcc… не должен
                        // превращаться в id 1628.
                        val parsed = RE_BOOKS_TAG.find(text)?.groupValues?.get(1)?.toIntOrNull()
                            ?: RE_TAG_BOOK.find(text)?.groupValues?.get(1)?.toIntOrNull()
                            ?: RE_LIB_PATH.find(text)?.groupValues?.get(1)?.toIntOrNull()
                            ?: RE_BOOK_PATH.find(text)?.groupValues?.get(1)?.toIntOrNull()
                        if (parsed != null) {
                            idFromEntryTag = parsed
                            hasBookId = true
                        }
                    }

                    "name" -> if (inEntry) {
                        val nameText = parser.nextText().trim()
                        if (nameText.isNotBlank()) {
                            authorNames.add(nameText)
                            pendingAuthorName = nameText
                        }
                    }

                    "uri" -> if (inEntry) {
                        val uri = parser.nextText().trim()
                        val id = RE_AUTHOR_URI.find(uri)?.groupValues?.get(1)
                            ?: RE_AUTHOR_URI_ALT.find(uri)?.groupValues?.get(1)
                        if (id != null) {
                            if (authorIdValue == null) authorIdValue = id
                            // <name> идёт непосредственно перед своим <uri>,
                            // поэтому имя/id связываются попарно
                            pendingAuthorName?.let { name -> entryAuthorPairs.add(name to id) }
                            pendingAuthorName = null
                        }
                    }

                    "category" -> if (inEntry) {
                        val label = parser.getAttributeValue(null, "label")
                            ?: parser.getAttributeValue(null, "term")
                        if (!label.isNullOrBlank()) genres.add(label)
                    }

                    // dc:format приходит в разборе с включёнными namespace как "format"
                    "format" -> if (inEntry) {
                        when (parser.nextText().trim().lowercase()) {
                            "fb2+zip", "fb2.zip", "fb2" -> addFormat("fb2")
                            "epub" -> addFormat("epub")
                            "mobi", "azw3" -> addFormat("mobi")
                            "txt", "text" -> addFormat("txt")
                            "pdf" -> addFormat("pdf")
                        }
                    }

                    "content", "summary" -> if (inEntry) {
                        val raw = parser.nextText()
                        itemCount = RE_ALL_DIGITS.find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                        description = stripHtmlAndTruncate(raw)
                    }
                }
            } else if (eventType == XmlPullParser.END_TAG && name == "entry") {
                inEntry = false
                val entryTitle = title
                val entryIdText = entryId.orEmpty()
                val bookId = idFromLink ?: idFromEntryTag

                if (bookId != null && hasBookId && !entryTitle.isNullOrBlank() && books.size < limit) {
                    books.add(
                        Book(
                            id = bookId,
                            title = entryTitle,
                            author = authorNames.joinToString(", ").ifBlank { "Unknown Author" },
                            link = "/b/$bookId",
                            sendLink = "/send/$bookId",
                            coverUrl = coverUrl.orEmpty(),
                            genres = genres.toList(),
                            description = description,
                            formats = formats.toList(),
                        )
                    )
                    for ((name, authorId) in entryAuthorPairs) {
                        if (bookAuthors.size >= limit) break
                        val pair = name to authorId
                        if (!bookAuthors.contains(pair)) bookAuthors.add(pair)
                    }
                }

                // Каталоги, где в <author> нет <uri> (Fantasy-worlds в ленте
                // новинок): id автора известен из rel="related", подставляем его
                // ко всем именам записи.
                val entryAuthorId = authorIdValue
                if (bookId != null && entryAuthorPairs.isEmpty() && entryAuthorId != null) {
                    for (name in authorNames) {
                        if (bookAuthors.size >= limit) break
                        val pair = name to entryAuthorId
                        if (!bookAuthors.contains(pair)) bookAuthors.add(pair)
                    }
                }

                val resolvedAuthorId = authorIdValue
                    ?: RE_TAG_AUTHOR.find(entryIdText)?.groupValues?.get(1)
                    ?: RE_AUTHOR_PATH.find(entryIdText)?.groupValues?.get(1)
                if (resolvedAuthorId != null && authorIds.size < limit && authorIds.none { it == resolvedAuthorId }) {
                    authorIds.add(resolvedAuthorId)
                }

                // Авторская запись: у неё нет id книги, только id автора
                if (bookId == null && resolvedAuthorId != null &&
                    !entryTitle.isNullOrBlank() && authors.size < limit &&
                    authors.none { it.authorId == resolvedAuthorId }
                ) {
                    authors.add(
                        Author(
                            authorId = resolvedAuthorId,
                            name = entryTitle,
                            bookCount = itemCount,
                            portraitUrl = coverUrl.orEmpty(),
                        )
                    )
                }

                // Серия: tag:sequence:ID (глобальный каталог) либо
                // tag:author:A:sequence:S (серия автора)
                val seq = RE_TAG_AUTHOR_SEQUENCE.find(entryIdText)?.let {
                    SequenceIds(it.groupValues[2], it.groupValues[1])
                } ?: RE_TAG_SEQUENCE.find(entryIdText)?.let {
                    SequenceIds(it.groupValues[1], null)
                } ?: RE_SEQUENCE_PATH.find(entryIdText)?.let {
                    SequenceIds(it.groupValues[1], null)
                }
                if (seq != null && !entryTitle.isNullOrBlank() &&
                    series.size < limit && series.none { it.seriesId == seq.id }
                ) {
                    series.add(
                        Series(
                            seriesId = seq.id,
                            seriesTitle = entryTitle.trim(),
                            seriesUrl = "$resolvedBaseUrl/opds/sequencebooks/${seq.id}",
                            bookCount = itemCount,
                            authorId = seq.authorId.orEmpty(),
                        )
                    )
                }

                // Серии из ссылок rel="related" книжной записи
                for ((seriesId, seriesItem) in bookSeries) {
                    if (series.size >= limit) break
                    if (series.none { it.seriesId == seriesId }) series.add(seriesItem)
                }

                // Навигационная запись: ни книги, ни автора, ни серии
                val href = navHref
                if (href != null && bookId == null && resolvedAuthorId == null &&
                    seq == null && !entryTitle.isNullOrBlank() && navEntries.size < limit
                ) {
                    val absoluteHref = absolute(href)
                    if (navEntries.none { it.url == absoluteHref }) {
                        navEntries.add(GenreNavEntry(entryTitle, absoluteHref))
                    }
>>>>>>> d24a20e (FoxyBook v1.9)
                }
            }
            eventType = parser.next()
        }
<<<<<<< HEAD
=======
        return nextUrl
    }

    // ═══════════════════════════════════════════════════════════════
    //  Genre / index navigation entries
    // ═══════════════════════════════════════════════════════════════

    /** Навигационный фид: title + href каждой записи (жанры, буквенный индекс). */
    fun parseNavEntries(xml: String): List<GenreNavEntry> {
        val entries = mutableListOf<GenreNavEntry>()
        parseFeedPage(xml, limit = Int.MAX_VALUE, navEntries = entries)
>>>>>>> d24a20e (FoxyBook v1.9)
        return entries
    }

    // ═══════════════════════════════════════════════════════════════
<<<<<<< HEAD
    //  Pagination — follow <link rel="next">
    // ═══════════════════════════════════════════════════════════════

    fun fetchAllPages(url: String, maxPages: Int = 5): List<String> {
        val pages = mutableListOf<String>()
        var currentUrl = url
        val seenUrls = mutableSetOf<String>()
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

    fun parseNextLink(xml: String): String {
=======
    //  Страницы фидов: сеть + разбор одним проходом
    // ═══════════════════════════════════════════════════════════════

    suspend fun fetchBookPage(url: String, limit: Int): FeedPage<Book> {
        val books = mutableListOf<Book>()
        val next = parseFeedPage(fetchXml(url), limit = limit, books = books)
        return FeedPage(books.distinctBy { it.id }, next)
    }

    /**
     * Страница книг вместе с навигационной пагинацией.
     *
     * Fantasy-worlds не отдаёт `rel="next"`, а кладёт следующую страницу
     * отдельной записью: `<id>tag:root:nextpage</id>` с заголовком
     * «Следующая страница: N» и ссылкой на `/opds/last/N/`. Без такой
     * записи пагинация там строилась бы вслепую по смещению.
     */
    suspend fun fetchBookPageWithNav(url: String, limit: Int): FeedPage<Book> {
        val books = mutableListOf<Book>()
        val nav = mutableListOf<GenreNavEntry>()
        val next = parseFeedPage(fetchXml(url), limit = limit, books = books, navEntries = nav)
        val navNext = nav.firstOrNull {
            it.url.contains("/opds/last/") || it.name.startsWith("Следующая страница", ignoreCase = true)
        }?.url
        return FeedPage(books.distinctBy { it.id }, next ?: navNext)
    }

    suspend fun fetchAuthorPage(url: String, limit: Int): FeedPage<Author> {
        val authors = mutableListOf<Author>()
        val next = parseFeedPage(fetchXml(url), limit = limit, authors = authors)
        return FeedPage(authors.distinctBy { it.authorId }, next)
    }

    suspend fun fetchSeriesPage(url: String, limit: Int): FeedPage<Series> {
        val series = mutableListOf<Series>()
        val next = parseFeedPage(fetchXml(url), limit = limit, series = series)
        return FeedPage(series.distinctBy { it.seriesId }, next)
    }

    /**
     * Книги и серии одной страницы за один проход. Нужен поиску по сериям: у
     * флибусты серия лежит в `rel="related"` той же книжной записи, поэтому
     * второй проход по фиду не требуется.
     */
    suspend fun fetchBooksAndSeries(url: String, limit: Int): BooksAndSeries {
        val books = mutableListOf<Book>()
        val series = mutableListOf<Series>()
        val next = parseFeedPage(fetchXml(url), limit = limit, books = books, series = series)
        return BooksAndSeries(
            books = books.distinctBy { it.id },
            series = series.distinctBy { it.seriesId },
            nextUrl = next,
        )
    }

    data class BooksAndSeries(
        val books: List<Book>,
        val series: List<Series>,
        val nextUrl: String?
    )

    /**
     * Авторы книжной выдачи. У каталогов, где поиск по авторам отдаёт книги
     * (Fantasy-worlds), это единственный способ получить настоящие id авторов:
     * id лежит в `<uri>/author/id{N}</uri>` рядом с именем, а не в `<id>` записи.
     */
    data class AuthorCount(val authorId: String, val name: String, val bookCount: Int)

    suspend fun fetchAuthorsFromBooks(url: String, limit: Int): List<AuthorCount> {
        val pairs = mutableListOf<Pair<String, String>>()
        parseFeedPage(fetchXml(url), limit = limit, bookAuthors = pairs)
        val counts = LinkedHashMap<String, AuthorCount>()
        for ((name, authorId) in pairs) {
            val existing = counts[authorId]
            if (existing == null) {
                counts[authorId] = AuthorCount(authorId, name, 1)
            } else {
                counts[authorId] = existing.copy(bookCount = existing.bookCount + 1)
            }
        }
        return counts.values.take(limit)
    }

    suspend fun fetchAuthorIdsPage(url: String, limit: Int): FeedPage<String> {
        val ids = mutableListOf<String>()
        val next = parseFeedPage(fetchXml(url), limit = limit, authorIds = ids)
        return FeedPage(ids, next)
    }

    /** Навигационная страница каталога: жанры, буквенный индекс серий. */
    suspend fun fetchNavPage(url: String): List<GenreNavEntry> = parseNavEntries(fetchXml(url))

    fun parseNextLink(xml: String): String? {
>>>>>>> d24a20e (FoxyBook v1.9)
        val parser = XmlUtils.createParser(xml)
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "link") {
<<<<<<< HEAD
                val rel = parser.getAttributeValue(null, "rel")
                val href = parser.getAttributeValue(null, "href")
                if (rel == "next" && href != null) {
                    return if (href.startsWith("http")) href else "$resolvedBaseUrl$href"
=======
                if (parser.getAttributeValue(null, "rel") == "next") {
                    val href = parser.getAttributeValue(null, "href")
                    if (href != null) return absolute(href)
>>>>>>> d24a20e (FoxyBook v1.9)
                }
            }
            eventType = parser.next()
        }
<<<<<<< HEAD
        return ""
    }

    // ═══════════════════════════════════════════════════════════════
    //  Fast OPDS parsing (regex-based, for new-books feeds)
    // ═══════════════════════════════════════════════════════════════

    fun parseNewBooksFast(xml: String, limit: Int): List<Book> {
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
                    if (href.startsWith("http")) href else "$resolvedBaseUrl$href"
                } ?: ""

            val genres = Regex("""<category\s+[^>]*label="([^"]*)""")
                .findAll(entryText).map { decodeXml(it.groupValues[1]) }.toList()

            val description = Regex("<content[^>]*>(.*?)</content>", RegexOption.DOT_MATCHES_ALL)
                .find(entryText)?.groupValues?.get(1)?.let { stripHtmlAndTruncate(it) }
                ?: ""

            val formats = Regex(
                """<link\s+href="[^"]*/([a-z0-9]+)"\s+rel="http://opds-spec.org/acquisition/open-access""",
                RegexOption.IGNORE_CASE
            )
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
    //  Parse OPDS entries — books (XmlPullParser-based)
    // ═══════════════════════════════════════════════════════════════

    fun parseOpdsBooks(xml: String, limit: Int): List<Book> {
        val books = mutableListOf<Book>()
        val parser = XmlUtils.createParser(xml)
        var eventType = parser.eventType

        var title: String? = null
        var id: Int? = null
        val authors = mutableListOf<String>()
        var coverUrl: String? = null
        val genres = mutableListOf<String>()
        val formats = mutableListOf<String>()
        var seqNumber = 0
        var inEntry = false
        var hasBookLink = false
        var description = ""

        val knownFormats = listOf("epub", "fb2", "mobi", "txt", "pdf")

        while (eventType != XmlPullParser.END_DOCUMENT && books.size < limit) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "entry" -> {
                            title = null; id = null; authors.clear(); coverUrl = null
                            genres.clear(); formats.clear(); seqNumber = 0; inEntry = true
                            hasBookLink = false; description = ""
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
                                    coverUrl = if (href.startsWith("http")) href else "$resolvedBaseUrl$href"
                                }
                                // Извлекаем формат из acquisition-ссылок (например, /b/523168/fb2 → "fb2")
                                if (rel == "http://opds-spec.org/acquisition/open-access" && href != null) {
                                    val ext = href.substringAfterLast("/").lowercase()
                                    if (ext in knownFormats && ext !in formats) {
                                        formats.add(ext)
                                    }
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
                        "content", "summary" -> {
                            if (inEntry) {
                                val raw = parser.nextText()
                                description = raw
                                    .let { decodeXml(it) }
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
                                    formats = formats.toList(),
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

    fun parseOpdsAuthorIds(xml: String, limit: Int): List<String> {
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

    fun parseOpdsAuthorIdsFromBooks(xml: String, limit: Int): List<String> {
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
                    // Fallback for Fantasy-worlds: author ID in <link rel="related" href="/opds/author/id{NUM}">
                    if (name == "link" && inEntry && currentId == null) {
                        val rel = parser.getAttributeValue(null, "rel")
                        val href = parser.getAttributeValue(null, "href")
                        if (rel == "related" && href != null) {
                            val fantasyMatch = Regex("""/opds/author/id(\d+)""").find(href)
                            if (fantasyMatch != null) {
                                currentId = fantasyMatch.groupValues[1]
                            } else {
                                // Flibusta-style: /author/{NUM} in href
                                val linkMatch = Regex("""/author/(\d+)""").find(href)
                                if (linkMatch != null) currentId = linkMatch.groupValues[1]
                            }
                        }
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

    fun parseOpdsAuthors(xml: String, limit: Int): List<Author> {
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
                                    portraitUrl = if (href.startsWith("http")) href else "$resolvedBaseUrl$href"
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
    //  Parse OPDS entries — series (from author sequences)
    // ═══════════════════════════════════════════════════════════════

    fun parseOpdsSeries(xml: String, limit: Int): List<Series> {
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
                                    seriesUrl = "$resolvedBaseUrl/sequence/$currentId",
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

    fun parseOpdsSequences(xml: String, limit: Int): List<Series> {
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
                                    seriesUrl = "$resolvedBaseUrl/sequencebooks/$currentId",
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

    fun parseOpdsBookInfo(xml: String, bookId: Int): BookInfo? {
        val parser = XmlUtils.createParser(xml)
        var eventType = parser.eventType

        var title: String? = null
        val authorList = mutableListOf<String>()
        var description = ""
        val genres = mutableListOf<BookGenre>()
        var coverUrl: String? = null
        var inEntry = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "entry" -> { inEntry = true }
                        "title" -> if (inEntry) title = parser.nextText()
                        "name" -> {
                            if (inEntry) {
                                val authorName = parser.nextText().trim()
                                if (authorName.isNotBlank()) authorList.add(authorName)
                            }
                        }
                        "content", "summary" -> {
                            if (inEntry) {
                                description = parser.nextText().trim()
                                    .let { decodeXml(it) }
                                    .replace(Regex("<[^>]+>"), " ")
                                    .replace(Regex("\\s+"), " ")
                                    .trim()
                            }
                        }
                        "category" -> {
                            if (inEntry) {
                                val term = parser.getAttributeValue(null, "term") ?: ""
                                val label = parser.getAttributeValue(null, "label") ?: term
                                if (label.isNotBlank()) {
                                    genres.add(BookGenre(id = term, title = label))
                                }
                            }
                        }
                        "link" -> {
                            if (inEntry) {
                                val rel = parser.getAttributeValue(null, "rel")
                                val href = parser.getAttributeValue(null, "href")
                                if (rel == "http://opds-spec.org/image" && href != null) {
                                    coverUrl = if (href.startsWith("http")) href else "$resolvedBaseUrl$href"
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (name == "entry" && inEntry) {
                        val finalTitle = title
                        if (finalTitle != null) {
                            return BookInfo(
                                id = bookId,
                                title = finalTitle,
                                author = authorList.joinToString(", ").ifBlank { "Unknown Author" },
                                description = description,
                                genres = genres,
                                coverUrl = coverUrl ?: ""
                            )
                        }
                    }
                }
            }
            eventType = parser.next()
        }

=======
>>>>>>> d24a20e (FoxyBook v1.9)
        return null
    }

    // ═══════════════════════════════════════════════════════════════
<<<<<<< HEAD
    //  Parse series entries from book OPDS feed
    //  (looks for <link> with /opds/sequencebooks/ and rel="related")
    // ═══════════════════════════════════════════════════════════════

    fun parseOpdsSeriesFromBooks(xml: String, limit: Int): List<Series> {
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
                        // Flibusta: /opds/sequencebooks/{NUM}  |  Fantasy-worlds: /opds/series/id{NUM}
                        val isSeriesLink = linkRel == "related" &&
                            (href.contains("/opds/sequencebooks/") || href.contains("/opds/series/"))
                        if (isSeriesLink) {
                            val flibustaMatch = Regex("""/opds/sequencebooks/(\d+)""").find(href)
                            val fantasyMatch = Regex("""/opds/series/id(\d+)""").find(href)
                            val idMatch = flibustaMatch ?: fantasyMatch
                            if (idMatch != null) {
                                val seriesId = idMatch.groupValues[1]
                                if (seriesId !in seenIds) {
                                    seenIds.add(seriesId)
                                    val rawTitle = parser.getAttributeValue(null, "title") ?: ""
                                    val title = rawTitle
                                        .replace(Regex("""^Все книги серии[ :]+"""), "")
                                        .trim(' ', '"', '«', '»', '“', '”')
                                    seriesList.add(
                                        Series(
                                            seriesId = seriesId,
                                            seriesTitle = title,
                                            seriesUrl = if (href.startsWith("http")) href else "$resolvedBaseUrl$href",
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

    // ═══════════════════════════════════════════════════════════════
    //  Network helpers
    // ═══════════════════════════════════════════════════════════════

    fun fetchHtml(url: String): String = fetchFn(url)

    fun fetchXml(url: String): String = fetchFn(url)
=======
    //  Network helpers
    // ═══════════════════════════════════════════════════════════════

    suspend fun fetchHtml(url: String): String = fetchFn(url)

    suspend fun fetchXml(url: String): String = fetchFn(url)
>>>>>>> d24a20e (FoxyBook v1.9)

    // ═══════════════════════════════════════════════════════════════
    //  Text helpers
    // ═══════════════════════════════════════════════════════════════

    fun stripHtmlAndTruncate(html: String): String {
<<<<<<< HEAD
        val clean = html
            .let { decodeXml(it) }
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .replace(Regex("^[Гг]од издани[яе].*$"), "")
=======
        if (html.isBlank()) return ""
        var clean = decodeXml(html)
            .replace(RE_HTML_TAG, " ")
            .replace(RE_WHITESPACE, " ")
            .trim()
            .replace(RE_YEAR_PREFIX, "")
>>>>>>> d24a20e (FoxyBook v1.9)
            .trim()
        if (clean.isBlank()) return ""
        return if (clean.length > 200) clean.take(200) + "…" else clean
    }

    fun decodeXml(text: String): String {
<<<<<<< HEAD
=======
        if (text.indexOf('&') < 0) return text
>>>>>>> d24a20e (FoxyBook v1.9)
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
    }

<<<<<<< HEAD
    fun parseBookId(idText: String): Int? {
        Regex("""/b/(\d+)""").find(idText)?.let { return it.groupValues[1].toIntOrNull() }
        Regex("""/book/(\d+)""").find(idText)?.let { return it.groupValues[1].toIntOrNull() }
        Regex("""[:/](\d{3,})$""").find(idText)?.let { return it.groupValues[1].toIntOrNull() }
=======
    /** Нормализация названия: пунктуация и регистр не влияют на совпадение. */
    fun normalizeTitle(text: String): String =
        text.trim().lowercase()
            .replace(RE_PUNCTUATION, "")
            .replace(RE_WHITESPACE, " ")

    fun parseBookId(idText: String): Int? {
        RE_BOOK_PATH.find(idText)?.let { return it.groupValues[1].toIntOrNull() }
        RE_LIB_PATH.find(idText)?.let { return it.groupValues[1].toIntOrNull() }
        RE_BOOK_SLASH.find(idText)?.let { return it.groupValues[1].toIntOrNull() }
        RE_BOOKS_TAG.find(idText)?.let { return it.groupValues[1].toIntOrNull() }
        RE_TAG_BOOK.find(idText)?.let { return it.groupValues[1].toIntOrNull() }
        RE_TRAILING_ID.find(idText)?.let { return it.groupValues[1].toIntOrNull() }
>>>>>>> d24a20e (FoxyBook v1.9)
        return null
    }
}