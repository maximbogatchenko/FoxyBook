package com.foxybook.app.core.reader

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.foxybook.app.core.models.Fb2Book
import com.foxybook.app.core.models.Fb2Chapter
import com.foxybook.app.core.utils.BookImageCache
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

class Fb2Parser(private val context: Context? = null) {

    private var cachedDoc: Document? = null
    private var cachedUri: Uri? = null
    private var cachedImageMap: Map<String, String>? = null

    companion object {
        private const val TAG = "FB2_PARSER"
    }

    /**
     * Quickly parses the structure.
     */
    fun parse(context: Context, uri: Uri, bookId: Int = 0): Fb2Book? {
        return try {
            val doc = getDocument(context, uri) ?: return null
            val title = doc.selectFirst("title-info book-title")?.text()?.trim() ?: "Unknown"
            val author = buildAuthor(doc)
            val description = doc.selectFirst("title-info annotation")?.wholeText()?.trim() ?: ""

            val chapters = mutableListOf<Fb2Chapter>()
            val sections = doc.select("body section")
            
            if (sections.isEmpty()) {
                chapters.add(Fb2Chapter(title = title, htmlContent = "", sectionId = 0))
            } else {
                sections.forEachIndexed { idx, section ->
                    val sectionTitle = section.selectFirst("> title > p")?.text()?.trim() ?: "Глава ${idx + 1}"
                    chapters.add(Fb2Chapter(title = sectionTitle, htmlContent = "", sectionId = idx))
                }
            }

            Fb2Book(title = title, author = author, description = description, chapters = chapters)
        } catch (e: Exception) {
            Log.e(TAG, "FB2: Quick parse error", e)
            null
        }
    }

    fun loadChapterContent(context: Context, uri: Uri, sectionId: Int, bookId: Int = 0): String {
        return try {
            val doc = getDocument(context, uri) ?: return ""
            val imageMap = getImageMap(doc, bookId)
            
            val sections = doc.select("body section")
            if (sectionId == -1 || sections.isEmpty()) {
                val body = doc.selectFirst("body") ?: return ""
                return convertSectionToHtml(body, "", imageMap)
            }
            
            val section = sections.getOrNull(sectionId) ?: return ""
            val title = section.selectFirst("> title > p")?.text()?.trim() ?: ""
            convertSectionToHtml(section, title, imageMap)
        } catch (e: Exception) {
            Log.e(TAG, "FB2: Failed to load section $sectionId", e)
            ""
        }
    }

    private fun getDocument(context: Context, uri: Uri): Document? {
        if (cachedUri == uri && cachedDoc != null) return cachedDoc
        val xml = readFb2Xml(context, uri) ?: return null
        cachedDoc = Jsoup.parse(xml, "", Parser.xmlParser())
        cachedUri = uri
        cachedImageMap = null
        return cachedDoc
    }

    private fun getImageMap(doc: Document, bookId: Int): Map<String, String> {
        cachedImageMap?.let { return it }
        val map = extractBinaryImages(doc, bookId)
        cachedImageMap = map
        return map
    }

    private fun extractBinaryImages(doc: Document, bookId: Int): Map<String, String> {
        val imageMap = mutableMapOf<String, String>()
        val binaries = doc.select("binary")
        for (binary in binaries) {
            val id = binary.attr("id").ifBlank { continue }
            val contentType = binary.attr("content-type") ?: "image/jpeg"
            try {
                val base64Text = binary.text().trim().replace("\\s".toRegex(), "")
                val bytes = Base64.decode(base64Text, Base64.DEFAULT)
                val extension = extensionFromContentType(contentType, id)
                val fileName = if (id.contains(".")) id else "$id.$extension"

                if (context != null && bookId > 0) {
                    val cachedImg = BookImageCache.saveImage(context, bookId, fileName, bytes)
                    imageMap["#$id"] = "file://${cachedImg.absolutePath}"
                    imageMap[id] = "file://${cachedImg.absolutePath}"
                } else {
                    val mime = BookImageCache.getMimeType(fileName)
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    imageMap["#$id"] = "data:$mime;base64,$b64"
                    imageMap[id] = "data:$mime;base64,$b64"
                }
            } catch (_: Exception) {}
        }
        return imageMap
    }

    private fun extensionFromContentType(contentType: String, id: String): String = when {
        contentType.contains("png", true) -> "png"
        contentType.contains("gif", true) -> "gif"
        contentType.contains("webp", true) -> "webp"
        contentType.contains("svg", true) -> "svg"
        id.endsWith(".png", true) -> "png"
        else -> "jpg"
    }

    private fun buildAuthor(doc: Document): String {
        val authorEl = doc.selectFirst("title-info author") ?: return "Неизвестный автор"
        val first = authorEl.selectFirst("first-name")?.text() ?: ""
        val last = authorEl.selectFirst("last-name")?.text() ?: ""
        return "$first $last".trim().ifBlank { "Неизвестный автор" }
    }

    private fun convertSectionToHtml(section: Element, sectionTitle: String, imageMap: Map<String, String>): String {
        val sb = StringBuilder()
        if (sectionTitle.isNotBlank()) sb.append("<h2>").append(escapeHtml(sectionTitle)).append("</h2>")

        for (child in section.children()) {
            when (child.tagName()) {
                "title" -> {}
                "p" -> sb.append("<p>").append(processInlineElements(child, imageMap)).append("</p>")
                "empty-line" -> sb.append("<br/>")
                "image" -> {
                    val href = child.attr("l:href").ifBlank { child.attr("href") }
                    val resolved = imageMap[href] ?: imageMap["#$href"] ?: href
                    sb.append("<div class=\"image\"><img src=\"").append(escapeHtml(resolved)).append("\" alt=\"\"/></div>")
                }
                "subtitle" -> sb.append("<h3>").append(processInlineElements(child, imageMap)).append("</h3>")
                "poem" -> {
                    sb.append("<div class=\"poem\">")
                    child.select("stanza v").forEach { v ->
                        sb.append("<p class=\"verse-line\">").append(escapeHtml(v.text())).append("</p>")
                    }
                    sb.append("</div>")
                }
                "cite", "blockquote" -> {
                    sb.append("<blockquote>")
                    child.select("p").forEach { p -> sb.append("<p>").append(escapeHtml(p.text())).append("</p>") }
                    sb.append("</blockquote>")
                }
            }
        }
        return sb.toString()
    }

    private fun processInlineElements(element: Element, imageMap: Map<String, String>): String {
        val sb = StringBuilder()
        for (node in element.childNodes()) {
            if (node is TextNode) sb.append(escapeHtml(node.text()))
            else if (node is Element) {
                when (node.tagName()) {
                    "strong", "b" -> sb.append("<strong>").append(processInlineElements(node, imageMap)).append("</strong>")
                    "emphasis", "em", "i" -> sb.append("<em>").append(processInlineElements(node, imageMap)).append("</em>")
                    "image" -> {
                        val href = node.attr("l:href").ifBlank { node.attr("href") }
                        val resolved = imageMap[href] ?: imageMap["#$href"] ?: href
                        sb.append("<img src=\"").append(escapeHtml(resolved)).append("\" alt=\"\"/>")
                    }
                    else -> sb.append(processInlineElements(node, imageMap))
                }
            }
        }
        return sb.toString()
    }

    private fun escapeHtml(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun readFb2Xml(context: Context, uri: Uri): String? {
        Log.d(TAG, "readFb2Xml: uri=$uri")
        return try {
            val stream = if (uri.scheme == "file") {
                File(uri.path!!).inputStream()
            } else {
                context.contentResolver.openInputStream(uri)
            } ?: run {
                Log.e(TAG, "readFb2Xml: Failed to open InputStream for $uri")
                return null
            }

            val bytes = stream.use { it.readBytes() }
            Log.d(TAG, "readFb2Xml: Read ${bytes.size} bytes")

            val isZip = bytes.size >= 4 &&
                bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

            if (isZip) {
                val zis = java.util.zip.ZipInputStream(bytes.inputStream())
                zis.use { z ->
                    var entry = z.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith(".fb2")) {
                            Log.d(TAG, "readFb2Xml: Found .fb2 entry in zip: ${entry.name}")
                            return z.bufferedReader().readText()
                        }
                        entry = z.nextEntry
                    }
                }
                Log.w(TAG, "readFb2Xml: No .fb2 entry found in zip")
                return null
            }

            val text = bytes.toString(Charsets.UTF_8)
            Log.d(TAG, "readFb2Xml: Read ${text.length} chars from XML")
            text
        } catch (e: Exception) {
            Log.e(TAG, "readFb2Xml: Exception reading $uri", e)
            null
        }
    }
}
