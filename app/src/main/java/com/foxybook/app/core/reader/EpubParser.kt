package com.foxybook.app.core.reader

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.foxybook.app.core.models.EpubBook
import com.foxybook.app.core.models.EpubChapter
import com.foxybook.app.core.utils.BookImageCache
import com.foxybook.app.core.utils.UriUtils
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipFile

class EpubParser(private val context: Context? = null) {

    companion object {
        private const val TAG = "EPUB_PARSER"
    }

    private fun getFileFromUri(context: Context, uri: Uri): File? {
        Log.d(TAG, "getFileFromUri: uri=$uri, scheme=${uri.scheme}")
        if (uri.scheme == "file") return uri.path?.let { File(it) }
        val temp = UriUtils.copyUriToTempFile(context, uri, "temp_epub_${System.currentTimeMillis()}.epub")
        if (temp == null) {
            Log.e(TAG, "getFileFromUri: Failed to copy URI to temp file")
        } else {
            Log.d(TAG, "getFileFromUri: Successfully copied to ${temp.absolutePath}, size=${temp.length()}")
        }
        return temp
    }

    /**
     * Quickly parses metadata and chapter list without reading full content.
     */
    fun parse(context: Context, uri: Uri, bookId: Int = 0): EpubBook? {
        val file = getFileFromUri(context, uri) ?: return null
        var zip: ZipFile? = null
        return try {
            zip = ZipFile(file)
            val opfPath = findOpfPath(zip) ?: return null
            val opfDir = opfPath.substringBeforeLast("/", "")
            val opfXml = zip.readEntry(opfPath) ?: return null
            val doc = Jsoup.parse(opfXml, "", Parser.xmlParser())

            val title = doc.selectFirst("dc\\:title, title")?.text()?.trim() ?: "Без названия"
            val author = doc.selectFirst("dc\\:creator, creator")?.text()?.trim() ?: "Неизвестный автор"

            val manifest = mutableMapOf<String, String>()
            for (item in doc.select("manifest > item")) {
                val id = item.attr("id") ?: continue
                val href = item.attr("href") ?: continue
                manifest[id] = href
            }

            val spineIds = doc.select("spine > itemref").mapNotNull { it.attr("idref").ifBlank { null } }
            
            val chapters = spineIds.mapIndexed { idx, idref ->
                val href = manifest[idref] ?: ""
                val entryPath = resolveHref(opfDir, href)
                EpubChapter(title = "Глава ${idx + 1}", htmlContent = "", href = entryPath)
            }

            EpubBook(title = title, author = author, chapters = chapters)
        } catch (e: Exception) {
            Log.e(TAG, "EPUB: Quick parse error", e)
            null
        } finally {
            try { zip?.close() } catch (_: Exception) {}
            if (uri.scheme != "file") file.delete()
        }
    }

    /**
     * Loads full content of a single chapter.
     */
    fun loadChapterContent(context: Context, uri: Uri, href: String, bookId: Int = 0): String {
        val file = getFileFromUri(context, uri) ?: return ""
        var zip: ZipFile? = null
        return try {
            zip = ZipFile(file)
            val html = zip.readEntry(href) ?: return ""
            
            val chapterDir = href.substringBeforeLast("/", "")
            val imageMap = preloadImagesForBook(zip, bookId)
            
            val processedHtml = replaceImageSrc(html, imageMap, chapterDir)
            val cleanHtml = cleanChapterHtml(processedHtml)
            
            cleanHtml
        } catch (e: Exception) {
            Log.e(TAG, "EPUB: Failed to load chapter $href", e)
            ""
        } finally {
            try { zip?.close() } catch (_: Exception) {}
            if (uri.scheme != "file") file.delete()
        }
    }

    private fun preloadImagesForBook(zip: ZipFile, bookId: Int): Map<String, String> {
        val imageMap = mutableMapOf<String, String>()
        val allEntries = zip.entries().toList()
        for (entry in allEntries) {
            if (!entry.isDirectory && BookImageCache.isImageName(entry.name)) {
                val fileName = entry.name.substringAfterLast("/")
                if (context != null && bookId > 0) {
                    val cachedFile = BookImageCache.getImageFile(context, bookId, fileName)
                    if (cachedFile.exists()) {
                        imageMap[entry.name] = "file://${cachedFile.absolutePath}"
                        imageMap[fileName] = "file://${cachedFile.absolutePath}"
                        continue
                    }
                    // If not in cache, extract it
                    try {
                        val bytes = zip.getInputStream(entry).readBytes()
                        val savedFile = BookImageCache.saveImage(context, bookId, fileName, bytes)
                        imageMap[entry.name] = "file://${savedFile.absolutePath}"
                        imageMap[fileName] = "file://${savedFile.absolutePath}"
                    } catch (_: Exception) {}
                } else {
                    // Fallback to data URI if context missing (though it shouldn't be for cached books)
                    try {
                        val bytes = zip.getInputStream(entry).readBytes()
                        val mime = BookImageCache.getMimeType(entry.name)
                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        imageMap[entry.name] = "data:$mime;base64,$b64"
                    } catch (_: Exception) {}
                }
            }
        }
        return imageMap
    }

    private fun findOpfPath(zip: ZipFile): String? {
        val containerEntry = zip.getEntry("META-INF/container.xml")
        if (containerEntry != null) {
            try {
                val xml = zip.getInputStream(containerEntry).bufferedReader(Charsets.UTF_8).readText()
                val doc = Jsoup.parse(xml, "", Parser.xmlParser())
                val path = doc.selectFirst("rootfile")?.attr("full-path")
                if (!path.isNullOrBlank()) return path.trim()
            } catch (_: Exception) {}
        }
        return zip.entries().toList().find { it.name.endsWith(".opf", ignoreCase = true) }?.name
    }

    private fun replaceImageSrc(html: String, imageMap: Map<String, String>, chapterDir: String): String {
        val doc = Jsoup.parse(html, "", Parser.xmlParser())
        for (img in doc.select("img, image")) {
            val src = if (img.tagName() == "img") img.attr("src") else (img.attr("xlink:href").ifBlank { img.attr("href") })
            if (src.isBlank() || src.startsWith("data:")) continue

            val resolved = resolveImagePath(src, chapterDir)
            val target = imageMap[resolved] ?: imageMap[src] ?: imageMap[src.substringAfterLast("/")]
            if (target != null) {
                if (img.tagName() == "img") img.attr("src", target)
                else {
                    img.attr("xlink:href", target)
                    img.attr("href", target)
                }
            }
        }
        return doc.outerHtml()
    }

    private fun resolveImagePath(src: String, chapterDir: String): String {
        val decoded = try { URLDecoder.decode(src, "UTF-8") } catch (_: Exception) { src }
        if (chapterDir.isBlank()) return normalizePath(decoded)
        return normalizePath("$chapterDir/$decoded")
    }

    private fun normalizePath(path: String): String {
        val result = mutableListOf<String>()
        for (part in path.split("/")) {
            when {
                part.isEmpty() || part == "." -> {}
                part == ".." -> if (result.isNotEmpty()) result.removeLast()
                else -> result.add(part)
            }
        }
        return result.joinToString("/")
    }

    private fun cleanChapterHtml(html: String): String {
        return try {
            val d = Jsoup.parse(html)
            d.select("script, style, link[rel=stylesheet]").remove()
            d.selectFirst("body")?.html() ?: d.html()
        } catch (_: Exception) { html }
    }

    private fun resolveHref(baseDir: String, href: String): String {
        val decoded = try { URLDecoder.decode(href, "UTF-8") } catch (_: Exception) { href }
        if (baseDir.isBlank()) return normalizePath(decoded)
        return normalizePath("$baseDir/$decoded")
    }

    private fun ZipFile.readEntry(name: String): String? {
        return try {
            val entry = this.getEntry(name) ?: return null
            this.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText()
        } catch (_: Exception) { null }
    }
}
