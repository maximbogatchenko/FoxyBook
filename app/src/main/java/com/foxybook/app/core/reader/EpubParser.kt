package com.foxybook.app.core.reader

import android.content.Context
import android.util.Base64
import android.util.Log
import com.foxybook.app.core.models.EpubBook
import com.foxybook.app.core.models.EpubChapter
import com.foxybook.app.core.utils.BookImageCache
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.util.zip.ZipFile

class EpubParser(private val context: Context? = null) {

    companion object {
        private const val TAG = "BOOK_IMAGES"
    }

    fun parse(file: File, bookId: Int = 0): EpubBook? {
        if (!file.exists() || file.length() == 0L) return null
        var zip: ZipFile? = null
        return try {
            zip = ZipFile(file)

            val opfPath = findOpfPath(zip) ?: return null
            val opfDir = opfPath.substringBeforeLast("/", "")

            val opfXml = zip.readEntry(opfPath) ?: return null
            val doc = Jsoup.parse(opfXml, "", Parser.xmlParser())

            val title = doc.selectFirst("dc\\:title, title")?.text()?.trim()?.ifBlank { null } ?: "Без названия"
            val author = doc.selectFirst("dc\\:creator, creator")?.text()?.trim()?.ifBlank { null } ?: "Неизвестный автор"

            // Build manifest: id -> href
            val manifest = mutableMapOf<String, String>()
            for (item in doc.select("manifest > item")) {
                val id = item.attr("id") ?: continue
                val href = item.attr("href") ?: continue
                if (href.isNotBlank()) manifest[id] = href
            }

            // ─── Extract all images to cache ───
            val imageMap = mutableMapOf<String, String>() // various keys -> file:// URL
            var imagesFound = 0
            var imagesSaved = 0

            val allEntries = zip.entries().toList()
            for (entry in allEntries) {
                if (!entry.isDirectory && BookImageCache.isImageName(entry.name)) {
                    imagesFound++
                    try {
                        val bytes = zip.getInputStream(entry).readBytes()
                        val fileName = entry.name.substringAfterLast("/")

                        if (context != null && bookId > 0) {
                            val cachedFile = BookImageCache.saveImage(context, bookId, fileName, bytes)
                            val fileUrl = "file://${cachedFile.absolutePath}"

                            // Map by full path inside ZIP
                            imageMap[entry.name] = fileUrl
                            // Map by filename only
                            imageMap[fileName] = fileUrl
                            // Map by OPF-relative path
                            if (opfDir.isNotBlank()) {
                                imageMap["$opfDir/$fileName"] = fileUrl
                                val opfRelative = "$opfDir/${entry.name.substringAfterLast("/")}"
                                if (opfRelative != fileName) imageMap[opfRelative] = fileUrl
                            }
                            imagesSaved++
                        } else {
                            // No context — embed as base64 data URI
                            val mime = BookImageCache.getMimeType(entry.name)
                            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            val dataUri = "data:$mime;base64,$b64"
                            imageMap[entry.name] = dataUri
                            imageMap[fileName] = dataUri
                            imagesSaved++
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "EPUB: Failed to extract image ${entry.name}", e)
                    }
                }
            }
            Log.d(TAG, "EPUB: Found $imagesFound images, saved $imagesSaved for book $bookId")

            // ─── Build spine ───
            val spineIds = mutableListOf<String>()
            for (ref in doc.select("spine > itemref")) {
                val idref = ref.attr("idref") ?: continue
                if (idref.isNotBlank()) spineIds.add(idref)
            }

            // ─── Build chapters ───
            val chapters = mutableListOf<EpubChapter>()
            for ((idx, idref) in spineIds.withIndex()) {
                val href = manifest[idref] ?: continue
                val entryPath = resolveHref(opfDir, href)
                val html = zip.readEntry(entryPath) ?: continue

                val chapterDir = entryPath.substringBeforeLast("/", "")
                val processedHtml = replaceImageSrc(html, imageMap, chapterDir)
                val cleanHtml = cleanChapterHtml(processedHtml)
                val chapterTitle = extractChapterTitle(cleanHtml, idx)

                if (cleanHtml.isNotBlank()) {
                    chapters.add(EpubChapter(title = chapterTitle, htmlContent = cleanHtml))
                }
            }

            if (chapters.isEmpty()) {
                Log.w(TAG, "EPUB: No chapters found for book $bookId")
                return null
            }

            Log.d(TAG, "EPUB: Parsed ${chapters.size} chapters for book $bookId")
            EpubBook(title = title, author = author, chapters = chapters)
        } catch (e: Exception) {
            Log.e(TAG, "EPUB: Parse error for book $bookId", e)
            null
        } finally {
            try { zip?.close() } catch (_: Exception) {}
        }
    }

    private fun findOpfPath(zip: ZipFile): String? {
        // Try container.xml first
        val containerEntry = zip.getEntry("META-INF/container.xml")
        if (containerEntry != null) {
            try {
                val xml = zip.getInputStream(containerEntry).bufferedReader(Charsets.UTF_8).readText()
                val doc = Jsoup.parse(xml, "", Parser.xmlParser())
                val rootfile = doc.selectFirst("rootfile")
                val path = rootfile?.attr("full-path")
                if (!path.isNullOrBlank()) return path.trim()
            } catch (_: Exception) {}
        }
        // Fallback: find any .opf
        for (entry in zip.entries().toList()) {
            if (entry.name.endsWith(".opf", ignoreCase = true)) return entry.name
        }
        return null
    }

    private fun replaceImageSrc(html: String, imageMap: Map<String, String>, chapterDir: String): String {
        if (imageMap.isEmpty()) return html

        val doc = Jsoup.parse(html, "", Parser.xmlParser())
        var replaced = 0

        for (img in doc.select("img")) {
            val src = img.attr("src") ?: continue
            if (src.isBlank() || src.startsWith("data:")) continue

            val resolved = resolveImagePath(src, chapterDir)
            val target = imageMap[resolved] ?: imageMap[src] ?: imageMap[src.substringAfterLast("/")]

            if (target != null) {
                img.attr("src", target)
                replaced++
            } else {
                Log.w(TAG, "EPUB: Image not found in map: src='$src' resolved='$resolved'")
            }
        }

        // Also handle <image> tags (SVG/XHTML)
        for (img in doc.select("image")) {
            val href = img.attr("xlink:href") ?: img.attr("href") ?: continue
            if (href.isBlank() || href.startsWith("data:")) continue

            val resolved = resolveImagePath(href, chapterDir)
            val target = imageMap[resolved] ?: imageMap[href] ?: imageMap[href.substringAfterLast("/")]

            if (target != null) {
                img.attr("xlink:href", target)
                if (img.hasAttr("href")) img.attr("href", target)
                replaced++
            }
        }

        if (replaced > 0) {
            Log.d(TAG, "EPUB: Replaced $replaced image references in chapter")
        }
        return doc.outerHtml()
    }

    private fun resolveImagePath(src: String, chapterDir: String): String {
        if (src.startsWith("data:") || src.startsWith("file:") || src.startsWith("http")) return src

        val decoded = try { URLDecoder.decode(src, "UTF-8") } catch (_: Exception) { src }

        // If already absolute path within ZIP
        if (!decoded.startsWith(".") && !decoded.startsWith("/")) {
            // Try as-is first
            if (chapterDir.isNotBlank()) {
                val combined = "$chapterDir/$decoded"
                val normalized = normalizePath(combined)
                return normalized
            }
            return decoded
        }

        // Handle relative paths (../  ./)
        if (chapterDir.isNotBlank()) {
            val combined = "$chapterDir/$decoded"
            return normalizePath(combined)
        }

        return normalizePath(decoded)
    }

    private fun normalizePath(path: String): String {
        val parts = path.split("/").toMutableList()
        val result = mutableListOf<String>()
        for (part in parts) {
            when {
                part.isEmpty() || part == "." -> { /* skip */ }
                part == ".." -> { if (result.isNotEmpty()) result.removeLast() }
                else -> result.add(part)
            }
        }
        return result.joinToString("/")
    }

    private fun isImageEntry(name: String): Boolean = BookImageCache.isImageName(name)

    private fun extractChapterTitle(html: String, fallbackIndex: Int): String {
        return try {
            val d = Jsoup.parse(html)
            d.selectFirst("h1, h2, h3, title")?.text()?.trim()?.ifBlank { null }
                ?: "Глава ${fallbackIndex + 1}"
        } catch (_: Exception) { "Глава ${fallbackIndex + 1}" }
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
