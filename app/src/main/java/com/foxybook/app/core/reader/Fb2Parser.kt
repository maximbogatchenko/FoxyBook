package com.foxybook.app.core.reader

import android.content.Context
import android.util.Base64
import android.util.Log
import com.foxybook.app.core.models.Fb2Book
import com.foxybook.app.core.models.Fb2Chapter
import com.foxybook.app.core.utils.BookImageCache
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

class Fb2Parser(private val context: Context? = null) {

    companion object {
        private const val TAG = "BOOK_IMAGES"
    }

    fun parse(file: File, bookId: Int = 0): Fb2Book? {
        return try {
            val xml = readFb2Xml(file) ?: return null
            if (xml.isBlank()) return null
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())

            val title = doc.selectFirst("title-info book-title")?.text()?.trim() ?: "Unknown"
            val author = buildAuthor(doc)
            val description = doc.selectFirst("title-info annotation")?.wholeText()?.trim() ?: ""

            // ─── Extract <binary> images ───
            val imageMap = extractBinaryImages(doc, bookId)

            val chapters = mutableListOf<Fb2Chapter>()
            val bodyElements = doc.select("body")

            for (body in bodyElements) {
                val bodyName = body.attr("name")
                val sections = body.children().filter { it.tagName() == "section" }

                if (sections.isEmpty()) {
                    val html = convertSectionToHtml(body, "", imageMap)
                    if (html.isNotBlank()) {
                        chapters.add(Fb2Chapter(title = bodyName.ifBlank { title }, htmlContent = html))
                    }
                    continue
                }

                for (section in sections) {
                    val sectionTitle = section.children()
                        .firstOrNull { it.tagName() == "title" }
                        ?.children()?.firstOrNull { it.tagName() == "p" }
                        ?.text()?.trim() ?: ""
                    val html = convertSectionToHtml(section, sectionTitle, imageMap)
                    if (html.isNotBlank()) {
                        chapters.add(Fb2Chapter(
                            title = sectionTitle.ifBlank { "Глава ${chapters.size + 1}" },
                            htmlContent = html
                        ))
                    }
                }
            }

            if (chapters.isEmpty()) {
                val allP = doc.select("p")
                val sb = StringBuilder()
                for (p in allP) {
                    val t = p.text().trim()
                    if (t.isNotBlank()) sb.append("<p>").append(escapeHtml(t)).append("</p>")
                }
                if (sb.isNotEmpty()) {
                    chapters.add(Fb2Chapter(title = title, htmlContent = sb.toString()))
                }
            }

            Log.d(TAG, "FB2: Parsed ${chapters.size} chapters, ${imageMap.size} images for book $bookId")
            Fb2Book(title = title, author = author, description = description, chapters = chapters)
        } catch (e: Exception) {
            Log.e(TAG, "FB2: Parse error for book $bookId", e)
            null
        }
    }

    // ─── Binary Image Extraction ───

    private fun extractBinaryImages(doc: org.jsoup.nodes.Document, bookId: Int): Map<String, String> {
        val imageMap = mutableMapOf<String, String>()
        var found = 0
        var decoded = 0

        val binaries = doc.select("binary")
        Log.d(TAG, "FB2: Found ${binaries.size} <binary> elements")

        for (binary in binaries) {
            val id = binary.attr("id") ?: continue
            if (id.isBlank()) continue
            val contentType = binary.attr("content-type") ?: "image/jpeg"

            found++
            try {
                val base64Text = binary.text().trim().replace("\\s".toRegex(), "")
                if (base64Text.isBlank()) {
                    Log.w(TAG, "FB2: Empty base64 for binary id=$id")
                    continue
                }

                val bytes = Base64.decode(base64Text, Base64.DEFAULT)
                if (bytes.isEmpty()) {
                    Log.w(TAG, "FB2: Decoded 0 bytes for binary id=$id")
                    continue
                }

                val extension = extensionFromContentType(contentType, id)
                val fileName = if (id.contains(".")) id else "$id.$extension"

                if (context != null && bookId > 0) {
                    val cachedFile = BookImageCache.saveImage(context, bookId, fileName, bytes)
                    val fileUrl = "file://${cachedFile.absolutePath}"
                    imageMap["#$id"] = fileUrl
                    imageMap[id] = fileUrl
                } else {
                    val mime = BookImageCache.getMimeType(fileName)
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    imageMap["#$id"] = "data:$mime;base64,$b64"
                    imageMap[id] = "data:$mime;base64,$b64"
                }
                decoded++
            } catch (e: Exception) {
                Log.e(TAG, "FB2: Failed to decode binary id=$id", e)
            }
        }

        Log.d(TAG, "FB2: Found $found binary images, decoded $decoded for book $bookId")
        return imageMap
    }

    private fun extensionFromContentType(contentType: String, id: String): String = when {
        contentType.contains("png", true) -> "png"
        contentType.contains("gif", true) -> "gif"
        contentType.contains("webp", true) -> "webp"
        contentType.contains("svg", true) -> "svg"
        id.endsWith(".png", true) -> "png"
        id.endsWith(".gif", true) -> "gif"
        id.endsWith(".webp", true) -> "webp"
        else -> "jpg"
    }

    private fun buildAuthor(doc: org.jsoup.nodes.Document): String {
        val authorEl = doc.selectFirst("title-info author") ?: return "Неизвестный автор"
        val first = authorEl.selectFirst("first-name")?.text()?.trim() ?: ""
        val middle = authorEl.selectFirst("middle-name")?.text()?.trim() ?: ""
        val last = authorEl.selectFirst("last-name")?.text()?.trim() ?: ""
        val parts = listOf(first, middle, last).filter { it.isNotBlank() }
        return if (parts.isNotEmpty()) parts.joinToString(" ") else authorEl.text().trim().ifBlank { "Неизвестный автор" }
    }

    private fun convertSectionToHtml(section: Element, sectionTitle: String, imageMap: Map<String, String>): String {
        val sb = StringBuilder()

        if (sectionTitle.isNotBlank()) {
            sb.append("<h2>").append(escapeHtml(sectionTitle)).append("</h2>")
        }

        for (child in section.children()) {
            when (child.tagName()) {
                "title" -> {
                    val ps = child.select("p")
                    if (ps.size > 1) {
                        for (i in 1 until ps.size) {
                            sb.append("<p class=\"subtitle\">").append(escapeHtml(ps[i].text())).append("</p>")
                        }
                    }
                }
                "epigraph" -> {
                    sb.append("<blockquote class=\"epigraph\">")
                    for (p in child.select("p")) {
                        sb.append("<p>").append(escapeHtml(p.text())).append("</p>")
                    }
                    val author = child.selectFirst("text-author")
                    if (author != null) {
                        sb.append("<p class=\"epigraph-author\">").append(escapeHtml(author.text())).append("</p>")
                    }
                    sb.append("</blockquote>")
                }
                "poem" -> {
                    sb.append("<div class=\"poem\">")
                    val poemTitle = child.selectFirst("title p")
                    if (poemTitle != null) {
                        sb.append("<h3>").append(escapeHtml(poemTitle.text())).append("</h3>")
                    }
                    for (stanza in child.select("stanza")) {
                        sb.append("<div class=\"stanza\">")
                        for (v in stanza.select("v")) {
                            sb.append("<p class=\"verse-line\">").append(escapeHtml(v.text())).append("</p>")
                        }
                        sb.append("</div>")
                    }
                    val poemAuthor = child.selectFirst("text-author")
                    if (poemAuthor != null) {
                        sb.append("<p class=\"poem-author\">").append(escapeHtml(poemAuthor.text())).append("</p>")
                    }
                    sb.append("</div>")
                }
                "p" -> {
                    sb.append("<p>").append(processInlineElements(child, imageMap)).append("</p>")
                }
                "empty-line" -> sb.append("<br/>")
                "image" -> {
                    val href = child.attr("l:href") ?: child.attr("href") ?: ""
                    val resolved = resolveFb2ImageRef(href, imageMap)
                    sb.append("<div class=\"image\"><img src=\"").append(escapeHtml(resolved)).append("\" alt=\"\"/></div>")
                }
                "subtitle" -> {
                    sb.append("<h3>").append(processInlineElements(child, imageMap)).append("</h3>")
                }
                "annotation" -> { /* skip */ }
                "section" -> {
                    val nestedTitle = child.selectFirst("> title > p")?.text()?.trim() ?: ""
                    sb.append(convertSectionToHtml(child, nestedTitle, imageMap))
                }
                else -> {
                    val text = child.text().trim()
                    if (text.isNotBlank()) {
                        sb.append("<p>").append(escapeHtml(text)).append("</p>")
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun resolveFb2ImageRef(href: String, imageMap: Map<String, String>): String {
        if (href.isBlank()) return ""
        // Try #id first (standard FB2 reference)
        if (href.startsWith("#")) {
            return imageMap[href] ?: imageMap[href.removePrefix("#")] ?: href
        }
        return imageMap[href] ?: imageMap["#$href"] ?: href
    }

    private fun processInlineElements(element: Element, imageMap: Map<String, String>): String {
        val sb = StringBuilder()
        for (node in element.childNodes()) {
            if (node is TextNode) {
                sb.append(escapeHtml(node.text()))
            } else if (node is Element) {
                when (node.tagName()) {
                    "strong", "b" -> sb.append("<strong>").append(processInlineElements(node, imageMap)).append("</strong>")
                    "emphasis", "em", "i" -> sb.append("<em>").append(processInlineElements(node, imageMap)).append("</em>")
                    "strikethrough" -> sb.append("<s>").append(processInlineElements(node, imageMap)).append("</s>")
                    "sub" -> sb.append("<sub>").append(processInlineElements(node, imageMap)).append("</sub>")
                    "sup" -> sb.append("<sup>").append(processInlineElements(node, imageMap)).append("</sup>")
                    "code" -> sb.append("<code>").append(processInlineElements(node, imageMap)).append("</code>")
                    "a" -> {
                        val href = node.attr("l:href") ?: node.attr("href") ?: "#"
                        sb.append("<a href=\"").append(escapeHtml(href)).append("\">")
                            .append(processInlineElements(node, imageMap)).append("</a>")
                    }
                    "image" -> {
                        val href = node.attr("l:href") ?: node.attr("href") ?: ""
                        val resolved = resolveFb2ImageRef(href, imageMap)
                        sb.append("<img src=\"").append(escapeHtml(resolved)).append("\" alt=\"\"/>")
                    }
                    "br" -> sb.append("<br/>")
                    else -> sb.append(processInlineElements(node, imageMap))
                }
            }
        }
        return sb.toString()
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }

    private fun readFb2Xml(file: File): String? {
        return try {
            val firstBytes = FileInputStream(file).use { fis ->
                val buf = ByteArray(4)
                val read = fis.read(buf)
                if (read < 4) return null
                buf
            }

            val isZip = firstBytes[0] == 0x50.toByte() &&
                        firstBytes[1] == 0x4B.toByte() &&
                        firstBytes[2] == 0x03.toByte() &&
                        firstBytes[3] == 0x04.toByte()

            if (isZip) {
                ZipInputStream(FileInputStream(file)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith(".fb2")) {
                            return zis.bufferedReader(Charsets.UTF_8).readText()
                        }
                        entry = zis.nextEntry
                    }
                    zis.close()
                    ZipInputStream(FileInputStream(file)).use { zis2 ->
                        var entry2 = zis2.nextEntry
                        while (entry2 != null) {
                            if (!entry2.isDirectory) {
                                return zis2.bufferedReader(Charsets.UTF_8).readText()
                            }
                            entry2 = zis2.nextEntry
                        }
                    }
                    null
                }
            } else {
                file.readText(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            Log.e(TAG, "FB2: Failed to read file", e)
            null
        }
    }
}
