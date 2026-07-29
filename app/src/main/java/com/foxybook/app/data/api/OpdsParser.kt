package com.foxybook.app.data.api

import com.foxybook.app.core.models.Author
import com.foxybook.app.core.models.Book
import com.foxybook.app.core.models.BookGenre
import com.foxybook.app.core.models.BookInfo
import com.foxybook.app.core.models.Series
import com.foxybook.app.core.utils.XmlUtils
import org.xmlpull.v1.XmlPullParser

/**
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

    // ═══════════════════════════════════════════════════════════════
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
        val parser = XmlUtils.createParser(xml)
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "link") {
                val rel = parser.getAttributeValue(null, "rel")
                val href = parser.getAttributeValue(null, "href")
                if (rel == "next" && href != null) {
                    return if (href.startsWith("http")) href else "$resolvedBaseUrl$href"
                }
            }
            eventType = parser.next()
        }
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

        return null
    }

    // ═══════════════════════════════════════════════════════════════
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

    // ═══════════════════════════════════════════════════════════════
    //  Text helpers
    // ═══════════════════════════════════════════════════════════════

    fun stripHtmlAndTruncate(html: String): String {
        val clean = html
            .let { decodeXml(it) }
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .replace(Regex("^[Гг]од издани[яе].*$"), "")
            .trim()
        if (clean.isBlank()) return ""
        return if (clean.length > 200) clean.take(200) + "…" else clean
    }

    fun decodeXml(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
    }

    fun parseBookId(idText: String): Int? {
        Regex("""/b/(\d+)""").find(idText)?.let { return it.groupValues[1].toIntOrNull() }
        Regex("""/book/(\d+)""").find(idText)?.let { return it.groupValues[1].toIntOrNull() }
        Regex("""[:/](\d{3,})$""").find(idText)?.let { return it.groupValues[1].toIntOrNull() }
        return null
    }
}