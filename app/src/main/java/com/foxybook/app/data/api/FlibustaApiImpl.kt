package com.foxybook.app.data.api

import android.content.Context
import android.util.Log
import com.foxybook.app.core.models.Book
import com.foxybook.app.core.models.BookFormat
import com.foxybook.app.core.models.BookGenre
import com.foxybook.app.core.models.BookInfo
import com.foxybook.app.core.models.Series
import com.foxybook.app.core.network.OkHttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder

class FlibustaApiImpl(context: Context) : FlibustaApi {

    private companion object {
        const val MAX_RETRIES = 3
        const val TAG = "FLIBUSTA_API"
        val AUTHOR_BLACKLIST = setOf(
            "[Все]", "Все", "[все]", "все",
            "[All]", "All", "[all]", "all",
            "…", "...", "»", "«", "комментарии",
            "скачать", "читать",
            "fb2", "epub", "mobi", "pdf", "rtf", "djvu",
            "download"
        )
        val BOOK_LINK_REGEX = Regex("/b/(\\d+)")
        val SERIES_LINK_REGEX = Regex("/sequence/(\\d+)")
    }

    private val networkClient = OkHttpClientProvider(context)
    private val client = networkClient.client
    private val downloadClient = networkClient.createDownloadClient()
    private val baseUrl: String get() = networkClient.getBaseUrl()

    // ═══════════════════════════════════════════════════════════
    // searchBooks: GET /booksearch?ask={query}&chb=on
    // ═══════════════════════════════════════════════════════════

    override suspend fun searchBooks(query: String, limit: Int): List<Book> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/booksearch?ask=${URLEncoder.encode(query, "UTF-8")}&chb=on"
                Log.d(TAG, "searchBooks | url=$url")
                val html = fetchHtml(url) ?: return@withContext emptyList()
                val doc = Jsoup.parse(html, baseUrl)
                val main = doc.selectFirst("#main") ?: doc

                val results = mutableListOf<Book>()
                val bookLinks = main.select("a[href^=/b/]")

                for (element in bookLinks) {
                    if (results.size >= limit) break
                    val href = element.attr("href")
                    val match = BOOK_LINK_REGEX.find(href) ?: continue
                    val id = match.groupValues[1].toIntOrNull() ?: continue
                    val title = element.text().trim()
                    if (title.isBlank()) continue

                    val parent = element.parent()
                    if (parent == null) continue
                    val parentTag = parent.tagName()
                    if (parentTag == "nav" || parentTag == "header" || parentTag == "footer") continue

                    val author = extractAuthorFromSearchResult(element)
                    Log.d(TAG, "AUTHOR_NAME | bookId=$id | title=$title | author=$author")
                    results.add(
                        Book(
                            id = id,
                            title = title,
                            author = author,
                            link = "/b/$id",
                            sendLink = "/send/$id",
                            coverUrl = "$baseUrl/b/$id/cover"
                        )
                    )
                }

                Log.d(TAG, "searchBooks | found=${results.size}")
                results
            } catch (e: Exception) {
                Log.e(TAG, "searchBooks error", e)
                emptyList()
            }
        }

    // ═══════════════════════════════════════════════════════════
    // searchByAuthor: GET /booksearch?ask={author}&cha=on
    // ═══════════════════════════════════════════════════════════

    override suspend fun searchByAuthor(author: String, limit: Int): List<Book> =
        withContext(Dispatchers.IO) {
            try {
                val searchUrl = "$baseUrl/booksearch?ask=${URLEncoder.encode(author, "UTF-8")}&cha=on"
                Log.d(TAG, "searchByAuthor | url=$searchUrl")
                val searchHtml = fetchHtml(searchUrl) ?: return@withContext emptyList()
                val searchDoc = Jsoup.parse(searchHtml, baseUrl)
                val main = searchDoc.selectFirst("#main") ?: searchDoc

                val authorLinks = main.select("a[href^=/a/]")
                    .filter { isRealAuthorLink(it) }

                if (authorLinks.isEmpty()) {
                    Log.w(TAG, "searchByAuthor | no authors found for '$author'")
                    return@withContext emptyList()
                }

                val results = mutableListOf<Book>()

                for (authorLink in authorLinks) {
                    if (results.size >= limit) break
                    val authorHref = authorLink.attr("href")
                    val authorName = authorLink.text().trim()

                    val authorUrl = "$baseUrl$authorHref"
                    Log.d(TAG, "searchByAuthor | opening $authorUrl")
                    val authorHtml = fetchHtml(authorUrl) ?: continue
                    val authorDoc = Jsoup.parse(authorHtml, baseUrl)
                    val authorMain = authorDoc.selectFirst("#main") ?: authorDoc

                    val bookLinks = authorMain.select("a[href^=/b/]")
                    for (bookLink in bookLinks) {
                        if (results.size >= limit) break
                        val href = bookLink.attr("href")
                        val match = BOOK_LINK_REGEX.find(href) ?: continue
                        val id = match.groupValues[1].toIntOrNull() ?: continue
                        val title = bookLink.text().trim()
                        if (title.isBlank()) continue
                        if (results.any { it.id == id }) continue

                        results.add(
                            Book(
                                id = id,
                                title = title,
                                author = authorName,
                                link = "/b/$id",
                                sendLink = "/send/$id",
                                coverUrl = "$baseUrl/b/$id/cover"
                            )
                        )
                    }
                }

                Log.d(TAG, "searchByAuthor | found=${results.size}")
                results
            } catch (e: Exception) {
                Log.e(TAG, "searchByAuthor error", e)
                emptyList()
            }
        }

    // ═══════════════════════════════════════════════════════════
    // searchBySeries: GET /booksearch?ask={series}&chs=on
    //
    // Returns List<Series> — only series metadata.
    // Does NOT load books from series pages.
    // ═══════════════════════════════════════════════════════════

    override suspend fun searchBySeries(series: String, limit: Int): List<Series> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "searchBySeries | query='$series'")

                val searchUrl = "$baseUrl/booksearch?ask=${URLEncoder.encode(series, "UTF-8")}&chs=on"
                Log.d(TAG, "searchBySeries | url=$searchUrl")

                val searchHtml = fetchHtml(searchUrl) ?: return@withContext emptyList()
                val searchDoc = Jsoup.parse(searchHtml, baseUrl)
                val main = searchDoc.selectFirst("#main") ?: searchDoc

                // Find series links: <a href="/sequence/NNNN">Series Title</a>
                val seriesLinks = main.select("a[href^=/sequence/]")
                Log.d(TAG, "searchBySeries | raw series links=${seriesLinks.size}")

                val results = mutableListOf<Series>()
                val seenIds = mutableSetOf<String>()

                for (seriesLink in seriesLinks) {
                    if (results.size >= limit) break

                    val href = seriesLink.attr("href")
                    val match = SERIES_LINK_REGEX.find(href) ?: continue
                    val seriesId = match.groupValues[1]

                    // Deduplicate
                    if (seriesId in seenIds) continue
                    seenIds.add(seriesId)

                    val seriesTitle = seriesLink.text().trim()
                    if (seriesTitle.isBlank()) continue

                    // Try to extract book count from the text near the link
                    // Flibusta shows: "Series Title (NN)" or "Series Title — NN книг"
                    val bookCount = extractBookCount(seriesLink)

                    val seriesUrl = "$baseUrl/sequence/$seriesId"

                    results.add(
                        Series(
                            seriesId = seriesId,
                            seriesTitle = seriesTitle,
                            seriesUrl = seriesUrl,
                            bookCount = bookCount
                        )
                    )
                    Log.d(TAG, "searchBySeries | found series: '$seriesTitle' id=$seriesId count=$bookCount")
                }

                Log.d(TAG, "searchBySeries | total=${results.size}")
                results
            } catch (e: Exception) {
                Log.e(TAG, "searchBySeries error", e)
                emptyList()
            }
        }

    // ═══════════════════════════════════════════════════════════
    // getSeriesBooks: GET /sequence/{id}
    //
    // Loads the series page and extracts all books.
    // Called ONLY when user opens a series.
    // ═══════════════════════════════════════════════════════════

    override suspend fun getSeriesBooks(seriesId: String, limit: Int): List<Book> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/sequence/$seriesId"
                Log.d(TAG, "SERIES_OPEN | url=$url | seriesId=$seriesId")

                val html = fetchHtml(url)
                if (html == null) {
                    Log.e(TAG, "SERIES_OPEN | fetchHtml returned NULL for url=$url")
                    return@withContext emptyList()
                }
                Log.d(TAG, "SERIES_OPEN | html length=${html.length}")

                val doc = Jsoup.parse(html, baseUrl)
                val main = doc.selectFirst("#main") ?: doc

                // ── Strategy 1: Parse <li> items ──
                val liItems = main.select("li")
                Log.d(TAG, "SERIES_OPEN | li items found=${liItems.size}")

                val results = mutableListOf<Book>()
                val seenIds = mutableSetOf<Int>()
                var fallbackOrder = 0

                for (li in liItems) {
                    if (results.size >= limit) break

                    val bookLink = li.selectFirst("a[href^=/b/]") ?: continue
                    val href = bookLink.attr("href")
                    // Lenient match: /b/12345 or /b/12345/anything
                    val idMatch = Regex("/b/(\\d+)").find(href)
                    val id = idMatch?.groupValues?.get(1)?.toIntOrNull()
                    if (id == null) {
                        Log.w(TAG, "SERIES_OPEN | skipped li: href='$href' did not match /b/N")
                        continue
                    }

                    if (id in seenIds) continue
                    seenIds.add(id)

                    val title = bookLink.text().trim()
                    if (title.isBlank()) continue

                    val bookAuthor = extractAuthorFromSeriesPage(bookLink)

                    val sequenceNumber = extractSequenceNumber(li)
                    fallbackOrder++

                    // CRITICAL: if sequenceNumber is 0 (not found), use fallbackOrder.
                    // NEVER skip a book because it has no sequence number.
                    val effectiveNumber = if (sequenceNumber > 0) sequenceNumber else fallbackOrder

                    Log.d(
                        TAG,
                        "SERIES_BOOK_TITLE='$title' | SERIES_BOOK_NUMBER=$effectiveNumber (parsed=$sequenceNumber, fallback=$fallbackOrder) | id=$id"
                    )

                    results.add(
                        Book(
                            id = id,
                            title = title,
                            author = bookAuthor,
                            link = "/b/$id",
                            sendLink = "/send/$id",
                            coverUrl = "$baseUrl/b/$id/cover",
                            sequenceNumber = effectiveNumber
                        )
                    )
                }

                Log.d(TAG, "SERIES_BOOKS_PARSED | from li items=${results.size}")

                // ── Strategy 2: If <li> parsing found nothing, try direct <a> links ──
                if (results.isEmpty()) {
                    Log.w(TAG, "SERIES_OPEN | li parsing returned 0 books, trying direct <a> links")
                    val allBookLinks = main.select("a[href^=/b/]")
                    Log.d(TAG, "SERIES_OPEN | direct <a> links found=${allBookLinks.size}")

                    for (link in allBookLinks) {
                        if (results.size >= limit) break
                        val href = link.attr("href")
                        val idMatch = Regex("/b/(\\d+)").find(href)
                        val id = idMatch?.groupValues?.get(1)?.toIntOrNull() ?: continue
                        if (id in seenIds) continue
                        seenIds.add(id)

                        val title = link.text().trim()
                        if (title.isBlank()) continue

                        val bookAuthor = extractAuthorFromSeriesPage(link)
                        fallbackOrder++

                        Log.d(
                            TAG,
                            "SERIES_BOOK_TITLE='$title' | SERIES_BOOK_NUMBER=$fallbackOrder (fallback, no li) | id=$id"
                        )

                        results.add(
                            Book(
                                id = id,
                                title = title,
                                author = bookAuthor,
                                link = "/b/$id",
                                sendLink = "/send/$id",
                                coverUrl = "$baseUrl/b/$id/cover",
                                sequenceNumber = fallbackOrder
                            )
                        )
                    }
                    Log.d(TAG, "SERIES_BOOKS_PARSED | from direct links=${results.size}")
                }

                // ── Sort: by sequenceNumber (stable sort) ──
                // Books with real sequence numbers are sorted by number.
                // Books with fallback numbers keep their page order (stable sort).
                // NO book is ever removed or filtered out.
                val sorted = results.sortedBy { it.sequenceNumber }

                Log.d(TAG, "SERIES_BOOKS_BEFORE_SORT | count=${results.size}")
                Log.d(TAG, "SERIES_BOOKS_AFTER_SORT | count=${sorted.size}")
                sorted.forEachIndexed { idx, book ->
                    Log.d(TAG, "SERIES_BOOKS_AFTER_SORT | [$idx] SERIES_BOOK_TITLE='${book.title}' | SERIES_BOOK_NUMBER=${book.sequenceNumber}")
                }

                sorted
            } catch (e: Exception) {
                Log.e(TAG, "SERIES_OPEN | EXCEPTION", e)
                emptyList()
            }
        }

    /**
     * Extract the sequence (volume) number from a <li> element on the series page.
     *
     * Flibusta markup patterns:
     *   <li>...<a href="/b/123">Title</a>...<b>1</b></li>
     *   <li>...<a href="/b/123">Title</a>...<b>3</b> ...</li>
     *
     * The <b> tag containing only digits is the volume number.
     * If no such <b> tag exists, returns 0 (caller should use fallback order).
     */
    private fun extractSequenceNumber(li: org.jsoup.nodes.Element): Int {
        val bTags = li.select("b")
        for (b in bTags) {
            val text = b.text().trim()
            val num = text.toIntOrNull()
            if (num != null && num > 0) {
                return num
            }
        }
        return 0
    }

    // ═══════════════════════════════════════════════════════════
    // getBookInfo: GET /b/{id}
    // ═══════════════════════════════════════════════════════════

    override suspend fun getBookInfo(id: Int): BookInfo? =
        withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/b/$id"
                Log.d(TAG, "getBookInfo | url=$url")
                val html = fetchHtml(url) ?: return@withContext null
                val doc = Jsoup.parse(html, baseUrl)
                val main = doc.selectFirst("#main") ?: doc

                // Find a REAL author link — skip /a/0 ("Все") and other navigation links
                val authorEl = main.select("a[href^=/a/]").firstOrNull { isRealAuthorLink(it) }
                val authorName = authorEl?.text()?.trim() ?: "Unknown Author"
                Log.d(TAG, "AUTHOR_NAME | getBookInfo | author=$authorName | href=${authorEl?.attr("href")}")

                val title = main.selectFirst("h1")?.text()?.trim()
                    ?: main.selectFirst("h2")?.text()?.trim()
                    ?: main.selectFirst(".title")?.text()?.trim()
                    ?: "Unknown Title"

                val coverImg = main.select("img[src]").firstOrNull { img ->
                    val src = img.attr("src")
                    src.contains("/ib/") || src.contains("/i/") || src.contains("cover")
                }
                val coverUrl = coverImg?.let {
                    val src = it.attr("src")
                    if (src.startsWith("http")) src else "$baseUrl$src"
                } ?: "$baseUrl/b/$id/cover"

                val description = buildString {
                    val h2s = main.select("h2")
                    for (h2 in h2s) {
                        val next = h2.nextElementSibling()
                        if (next?.tagName() == "p") {
                            append(next.text().trim())
                            break
                        }
                    }
                    if (isEmpty()) {
                        val paragraphs = main.select("p")
                        for (p in paragraphs) {
                            val text = p.text().trim()
                            if (text.length > 50) {
                                append(text)
                                break
                            }
                        }
                    }
                }

                val genres = mutableListOf<BookGenre>()
                val genreLinks = main.select("a[href^=/g/]")
                for (genreLink in genreLinks) {
                    val genreId = genreLink.attr("href").removePrefix("/g/")
                    val genreTitle = genreLink.text().trim()
                    if (genreTitle.isNotBlank()) {
                        genres.add(BookGenre(id = genreId, title = genreTitle))
                    }
                }

                BookInfo(
                    id = id,
                    title = title,
                    author = authorName,
                    description = description,
                    genres = genres,
                    coverUrl = coverUrl
                )
            } catch (e: Exception) {
                Log.e(TAG, "getBookInfo error", e)
                null
            }
        }

    // ═══════════════════════════════════════════════════════════
    // getDownloadUrl: /b/{id}/{format}
    // ═══════════════════════════════════════════════════════════

    override fun getDownloadUrl(id: String, format: BookFormat): String {
        return "$baseUrl/b/$id/${format.extension}"
    }

    // ═══════════════════════════════════════════════════════════
    // downloadBook
    // ═══════════════════════════════════════════════════════════

    override suspend fun downloadBook(
        id: String,
        format: BookFormat,
        destDir: File,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val url = getDownloadUrl(id, format)
        val fileName = "${id}.${format.extension}"
        val destFile = File(destDir, fileName)

        if (!destDir.exists()) destDir.mkdirs()

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

                val body = response.body ?: run {
                    lastException = Exception("Empty response body")
                    continue
                }

                val contentLength = body.contentLength().toFloat()
                var bytesDownloaded = 0L

                body.byteStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            bytesDownloaded += bytesRead
                            if (contentLength > 0f) {
                                onProgress(bytesDownloaded / contentLength)
                            }
                        }
                        output.flush()
                    }
                }

                onProgress(1f)
                response.close()
                return@withContext destFile
            } catch (e: Exception) {
                lastException = e
                if (attempt < MAX_RETRIES) {
                    Thread.sleep(1000L * attempt)
                }
            }
        }

        throw lastException ?: Exception("Download failed")
    }

    // ═══════════════════════════════════════════════════════════
    // Author extraction — search results page
    // ═══════════════════════════════════════════════════════════

    private fun extractAuthorFromSearchResult(bookElement: Element): String {
        val bookTitle = bookElement.text()
        Log.d(TAG, "BOOK_TITLE | title=$bookTitle")

        // Strategy 1: Walk through siblings looking for author link or text
        var sibling = bookElement.nextSibling()
        while (sibling != null) {
            if (sibling is TextNode) {
                val text = sibling.text().trim()
                val author = stripDashPrefix(text)
                if (author.isNotBlank() && isValidAuthorName(author)) {
                    Log.d(TAG, "AUTHOR_PARSED | from=TextNode | author=$author")
                    return author
                } else if (author.isNotBlank()) {
                    Log.w(TAG, "AUTHOR_ERROR | TextNode rejected | raw='$text' stripped='$author'")
                }
            }
            if (sibling is Element) {
                val el = sibling as Element
                if (el.tagName() == "a" && el.attr("href").startsWith("/a/")) {
                    if (!isRealAuthorLink(el)) {
                        Log.w(TAG, "AUTHOR_ERROR | AuthorLink rejected (nav) | name='${el.text()}' href='${el.attr("href")}'")
                    } else {
                        val name = el.text().trim()
                        Log.d(TAG, "AUTHOR_PARSED | from=AuthorLink | author=$name")
                        return name
                    }
                }
            }
            sibling = sibling.nextSibling()
        }

        // Strategy 2: Look for author links in parent
        val parent = bookElement.parent()
        if (parent != null) {
            val authorLinks = parent.select("a[href^=/a/]")
            for (link in authorLinks) {
                if (!isRealAuthorLink(link)) {
                    Log.w(TAG, "AUTHOR_ERROR | ParentAuthorLink rejected (nav) | name='${link.text()}' href='${link.attr("href")}'")
                    continue
                }
                val name = link.text().trim()
                Log.d(TAG, "AUTHOR_PARSED | from=ParentAuthorLink | author=$name")
                return name
            }
        }

        // Strategy 3: Extract from parent text after book title
        val parentText = parent?.text() ?: ""
        val afterTitle = stripDashPrefix(parentText.substringAfter(bookTitle, "").trim())
        if (afterTitle.isNotBlank() && isValidAuthorName(afterTitle)) {
            Log.d(TAG, "AUTHOR_PARSED | from=ParentText | author=$afterTitle")
            return afterTitle
        } else if (afterTitle.isNotBlank()) {
            Log.w(TAG, "AUTHOR_ERROR | ParentText rejected | afterTitle='$afterTitle'")
        }

        Log.w(TAG, "AUTHOR_ERROR | title=$bookTitle | could not extract author")
        return "Unknown Author"
    }

    // ═══════════════════════════════════════════════════════════
    // Author extraction — series page
    // ═══════════════════════════════════════════════════════════

    private fun extractAuthorFromSeriesPage(bookElement: Element): String {
        val parent = bookElement.parent() ?: return "Unknown Author"

        val authorLinks = mutableListOf<Element>()
        var foundBookLink = false

        for (child in parent.children()) {
            if (child === bookElement) { foundBookLink = true; continue }
            if (!foundBookLink) continue
            if (child.tagName() == "a" && child.attr("href").startsWith("/a/")) {
                if (isRealAuthorLink(child)) authorLinks.add(child)
            }
        }

        val lastAuthor = authorLinks.lastOrNull()
        if (lastAuthor != null) return lastAuthor.text().trim()

        val allAuthorLinks = parent.select("a[href^=/a/]")
        for (link in allAuthorLinks.reversed()) {
            if (isRealAuthorLink(link)) return link.text().trim()
        }

        return "Unknown Author"
    }

    // ═══════════════════════════════════════════════════════════
    // Book count extraction from series link
    // ═══════════════════════════════════════════════════════════

    private fun extractBookCount(seriesLink: Element): Int {
        // Try parent text: "Series Title (15)" or "Series Title — 15 книг"
        val parentText = seriesLink.parent()?.text() ?: ""

        // Pattern: (NN)
        val parenMatch = Regex("\\((\\d+)\\)").find(parentText)
        if (parenMatch != null) {
            return parenMatch.groupValues[1].toIntOrNull() ?: 0
        }

        // Pattern: NN книг(и)
        val countMatch = Regex("(\\d+)\\s*книг").find(parentText)
        if (countMatch != null) {
            return countMatch.groupValues[1].toIntOrNull() ?: 0
        }

        return 0
    }

    // ═══════════════════════════════════════════════════════════
    // Validation
    // ═══════════════════════════════════════════════════════════

    private fun isValidAuthorName(name: String): Boolean {
        if (name.isBlank()) return false
        // Normalize: replace non-breaking spaces and invisible whitespace with regular space
        val cleaned = name
            .replace('\u00A0', ' ')  // non-breaking space
            .replace('\u200B', ' ')  // zero-width space
            .replace('\uFEFF', ' ')  // BOM
            .replace('\u200C', ' ')  // zero-width non-joiner
            .replace('\u200D', ' ')  // zero-width joiner
            .trim()
        if (cleaned.isBlank()) return false
        // Case-insensitive blacklist match
        val lowerCleaned = cleaned.lowercase()
        if (AUTHOR_BLACKLIST.any { it.lowercase() == lowerCleaned }) return false
        // Strip brackets and re-check
        val stripped = cleaned
            .removeSurrounding("[", "]")
            .removeSurrounding("(", ")")
            .trim()
        val lowerStripped = stripped.lowercase()
        if (AUTHOR_BLACKLIST.any { it.lowercase() == lowerStripped }) return false
        // Check if any token is a blacklisted word (case-insensitive)
        val tokens = stripped.split(Regex("\\s+"))
        val lowerBlacklist = AUTHOR_BLACKLIST.map { it.lowercase() }.toSet()
        if (tokens.any { it.trim().lowercase() in lowerBlacklist }) return false
        if (cleaned.length <= 1) return false
        if (cleaned.matches(Regex("^\\[.*\\]$"))) return false
        if (cleaned.matches(Regex("^[0-9]+$"))) return false
        if (cleaned.matches(Regex("^\\([a-zа-яё0-9]+\\)$", RegexOption.IGNORE_CASE))) return false
        return true
    }

    /**
     * Checks if an <a> element linking to /a/ID is a real author link
     * and NOT a navigation link like /a/0 ("Все").
     */
    private fun isRealAuthorLink(el: Element): Boolean {
        val href = el.attr("href") ?: ""
        // /a/0 is the "Все" (All) navigation link — always skip
        if (href == "/a/0" || href == "/a/0/") return false
        // Also skip if the href doesn't have a numeric ID > 0
        val idMatch = Regex("/a/(\\d+)").find(href)
        if (idMatch == null) return false
        val id = idMatch.groupValues[1].toIntOrNull() ?: return false
        if (id == 0) return false
        // Validate the text content
        return isValidAuthorName(el.text().trim())
    }

    /**
     * Strips all common dash variants (em-dash, en-dash, minus, etc.)
     * and surrounding whitespace from the beginning of text.
     */
    private fun stripDashPrefix(text: String): String {
        var result = text.trim()
        // Repeatedly strip dash+space patterns (handles "— Все", "- Все", "– Все", etc.)
        for (i in 1..3) {
            val before = result
            result = result
                .removePrefix("— ")   // em-dash + space
                .removePrefix("—")    // em-dash
                .removePrefix("– ")   // en-dash + space
                .removePrefix("–")    // en-dash
                .removePrefix("- ")   // regular dash + space
                .removePrefix("-")    // regular dash
                .removePrefix("− ")   // minus sign + space
                .removePrefix("−")    // minus sign
                .removePrefix("― ")   // horizontal bar + space
                .removePrefix("―")    // horizontal bar
                .removePrefix("─ ")   // box drawing + space
                .removePrefix("─")    // box drawing
                .trim()
            if (result == before) break  // no more dashes to strip
        }
        return result.trim()
    }

    // ═══════════════════════════════════════════════════════════
    // Network helper
    // ═══════════════════════════════════════════════════════════

    private fun fetchHtml(url: String): String? {
        return try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "fetchHtml | HTTP ${response.code} for $url")
                response.close()
                return null
            }
            val html = response.body?.string()
            response.close()
            html
        } catch (e: Exception) {
            Log.e(TAG, "fetchHtml | failed for $url", e)
            null
        }
    }
}
