package com.foxybook.app.core.reader

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.foxybook.app.core.models.BookFormat
import com.foxybook.app.core.models.ParsedBook
import com.foxybook.app.core.models.ParsedChapter
import com.foxybook.app.core.utils.BookImageCache
import com.foxybook.app.core.utils.UriUtils
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

class BookParser(
    private val context: Context,
    private val epubParser: EpubParser = EpubParser(context),
    private val fb2Parser: Fb2Parser = Fb2Parser(context),
    private val mobiParser: MobiParser = MobiParser(context),
    private val txtParser: TxtParser = TxtParser(context)
) {

    companion object {
        private const val TAG = "BookParser"
    }
    /**
     * Quickly parses book structure and metadata.
     */
    fun parse(path: String, knownFormat: String? = null, bookId: Int = 0): ParsedBook? {
        Log.d("BookParser", "parse: path=$path, knownFormat=$knownFormat, bookId=$bookId")

        val uri = if (path.startsWith("content://") || path.startsWith("file://")) {
            Uri.parse(path)
        } else {
            Uri.fromFile(File(path))
        }

        Log.d("BookParser", "parse: resolved uri=$uri, scheme=${uri.scheme}")

        val extension = if (uri.scheme == "content") {
            val name = com.foxybook.app.core.utils.UriUtils.getFileName(context, uri)
            Log.d("BookParser", "parse: content name=$name")
            val extFromName = name?.substringAfterLast(".", "")?.lowercase()
            
            if (extFromName.isNullOrBlank() || BookFormat.fromExtension(extFromName) == null) {
                // Try MIME type as fallback
                val mimeType = context.contentResolver.getType(uri)
                Log.d("BookParser", "parse: fallback to mimeType=$mimeType")
                when (mimeType) {
                    "application/epub+zip" -> "epub"
                    "application/x-fictionbook+xml" -> "fb2"
                    "application/x-mobipocket-ebook" -> "mobi"
                    "text/plain" -> "txt"
                    else -> extFromName ?: ""
                }
            } else extFromName
        } else {
            val file = File(uri.path ?: path)
            file.extension.lowercase()
        }
        
        Log.d("BookParser", "parse: detected extension=$extension")

        val format = BookFormat.fromExtension(knownFormat ?: extension) ?: run {
            Log.e("BookParser", "parse: Unsupported format (ext=$extension, known=$knownFormat)")
            return null
        }

        val parsed = when (format) {
            BookFormat.EPUB -> epubParser.parse(context, uri)?.toParsed(format.extension)
            BookFormat.FB2 -> fb2Parser.parse(context, uri)?.toParsed(format.extension)
            BookFormat.MOBI -> mobiParser.parse(context, uri, bookId)?.toParsed(format.extension)
            BookFormat.TXT -> txtParser.parse(uri)
            else -> null
        }
        
        if (parsed == null) {
            Log.e("BookParser", "parse: Failed to parse book at $path")
        } else {
            Log.d("BookParser", "parse: Success! Title=${parsed.title}, Author=${parsed.author}")
        }
        
        return parsed?.copy(filePath = path)
    }

    /**
     * Loads the actual HTML content for a specific chapter.
     */
    fun loadChapterContent(path: String, chapter: ParsedChapter, bookId: Int = 0, knownFormat: String? = null): String {
        Log.d("BookParser", "loadChapterContent: path=$path, chapter=${chapter.title}, knownFormat=$knownFormat")
        val uri = if (path.startsWith("content://") || path.startsWith("file://")) {
            Uri.parse(path)
        } else {
            Uri.fromFile(File(path))
        }

        val extension = if (uri.scheme == "content") {
            val name = com.foxybook.app.core.utils.UriUtils.getFileName(context, uri)
            name?.substringAfterLast(".", "")?.lowercase() ?: ""
        } else {
            val file = File(uri.path ?: path)
            file.extension.lowercase()
        }
        
        val format = BookFormat.fromExtension(knownFormat ?: extension)

        return when (format?.extension) {
            "epub" -> epubParser.loadChapterContent(context, uri, chapter.contentId, bookId)
            "fb2" -> fb2Parser.loadChapterContent(context, uri, chapter.contentId.toIntOrNull() ?: -1, bookId)
            "mobi" -> chapter.htmlContent // MOBI контент уже загружен при парсинге
            "txt" -> chapter.htmlContent
            else -> ""
        }
    }

    private fun com.foxybook.app.core.models.EpubBook.toParsed(format: String) = ParsedBook(
        title = title, author = author,
        chapters = chapters.map { ParsedChapter(it.title, contentId = it.href) },
        format = format
    )

    private fun com.foxybook.app.core.models.Fb2Book.toParsed(format: String) = ParsedBook(
        title = title, author = author,
        chapters = chapters.map { ParsedChapter(it.title, contentId = it.sectionId.toString()) },
        format = format
    )

    private fun com.foxybook.app.core.models.MobiBook.toParsed(format: String) = ParsedBook(
        title = title, author = author,
        chapters = chapters.map { ParsedChapter(it.title, htmlContent = it.htmlContent, contentId = "") },
        format = format
    )

    /**
     * Extracts the cover image from a book file and saves it to a local cache file.
     * Returns the absolute path to the cached cover, or null if no cover found.
     */
    fun extractCover(path: String, knownFormat: String? = null): String? {
        val uri = if (path.startsWith("content://") || path.startsWith("file://")) {
            Uri.parse(path)
        } else {
            Uri.fromFile(File(path))
        }

        val extension = if (uri.scheme == "content") {
            val name = com.foxybook.app.core.utils.UriUtils.getFileName(context, uri)
            name?.substringAfterLast(".", "")?.lowercase() ?: ""
        } else {
            (uri.path?.let { File(it) })?.extension?.lowercase() ?: ""
        }

        val format = BookFormat.fromExtension(knownFormat ?: extension) ?: return null

        return when (format) {
            BookFormat.EPUB -> extractEpubCover(uri)
            BookFormat.FB2 -> extractFb2Cover(uri)
            else -> null
        }
    }

    private fun extractEpubCover(uri: Uri): String? {
        return try {
            val file = if (uri.scheme == "file") {
                File(uri.path!!)
            } else {
                // Переиспользуем кэш EpubParser (если книгу уже парсили)
                epubParser.getCachedFileForUri(uri)
                    ?: UriUtils.copyUriToTempFile(context, uri, "temp_cover_${System.currentTimeMillis()}.epub") ?: return null
            }

            val result: String?
            ZipFile(file).use { zip ->
                result = extractEpubCoverFromZip(zip)
            }
            // Удаляем temp-файл только если его не переиспользует EpubParser
            if (uri.scheme != "file" && epubParser.getCachedFileForUri(uri) == null) {
                file.delete()
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "extractEpubCover failed", e)
            null
        }
    }

    private fun extractEpubCoverFromZip(zip: ZipFile): String? {
        // Find OPF path
        val containerEntry = zip.getEntry("META-INF/container.xml") ?: return null
        val containerXml = zip.getInputStream(containerEntry).bufferedReader().readText()
        val containerDoc = Jsoup.parse(containerXml, "", Parser.xmlParser())
        val opfPath = containerDoc.selectFirst("rootfile")?.attr("full-path") ?: return null
        val opfDir = opfPath.substringBeforeLast("/", "")

        val opfEntry = zip.getEntry(opfPath) ?: return null
        val opfXml = zip.getInputStream(opfEntry).bufferedReader().readText()
        val opfDoc = Jsoup.parse(opfXml, "", Parser.xmlParser())

        // Find cover image ID via <meta name="cover"> or <item properties="cover-image">
        val coverId = opfDoc.select("meta[name=cover]").first()?.attr("content")
            ?: opfDoc.select("item[properties*=cover]").first()?.attr("id")
            ?: return null

        val coverHref = opfDoc.select("item[id=$coverId]").first()?.attr("href") ?: return null
        val resolvedPath = normalizeEpubPath(opfDir, coverHref)

        val entry = zip.getEntry(resolvedPath) ?: return null
        val bytes = zip.getInputStream(entry).readBytes()
        return saveCover(bytes, resolvedPath)
    }

    private fun extractFb2Cover(uri: Uri): String? {
        return try {
            val xml = readFb2Xml(uri) ?: return null
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())

            // Prefer binary with "cover" in its id, fallback to first binary
            val coverBinary = doc.select("binary")
                .firstOrNull { it.attr("id").lowercase().contains("cover") }
                ?: doc.selectFirst("binary") ?: return null

            val id = coverBinary.attr("id")
            val contentType = coverBinary.attr("content-type").ifBlank { "image/jpeg" }
            val base64Text = coverBinary.text().trim().replace(Regex("\\s"), "")
            val bytes = Base64.decode(base64Text, Base64.DEFAULT)

            val ext = when {
                contentType.contains("png", true) -> "png"
                contentType.contains("gif", true) -> "gif"
                contentType.contains("webp", true) -> "webp"
                id.contains(".") -> id.substringAfterLast(".")
                else -> "jpg"
            }

            val fileName = "cover.$ext"
            saveCover(bytes, fileName)
        } catch (e: Exception) {
            Log.e(TAG, "extractFb2Cover failed", e)
            null
        }
    }

    private fun saveCover(bytes: ByteArray, originalName: String): String? {
        val coverDir = File(context.cacheDir, "covers")
        if (!coverDir.exists()) coverDir.mkdirs()
        val ext = originalName.substringAfterLast(".", "jpg")
        val coverFile = File(coverDir, "cover_${System.currentTimeMillis()}.$ext")
        FileOutputStream(coverFile).use { it.write(bytes) }
        return coverFile.absolutePath
    }

    private fun normalizeEpubPath(baseDir: String, href: String): String {
        val decoded = try { java.net.URLDecoder.decode(href, "UTF-8") } catch (_: Exception) { href }
        val full = if (baseDir.isBlank()) decoded else "$baseDir/$decoded"
        val result = mutableListOf<String>()
        for (part in full.split("/")) {
            when {
                part.isEmpty() || part == "." -> {}
                part == ".." -> if (result.isNotEmpty()) result.removeLast()
                else -> result.add(part)
            }
        }
        return result.joinToString("/")
    }

    private fun readFb2Xml(uri: Uri): String? {
        return try {
            val bytes = if (uri.scheme == "file") {
                File(uri.path!!).readBytes()
            } else {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            }

            val isZip = bytes.size >= 4 &&
                bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

            if (isZip) {
                java.util.zip.ZipInputStream(bytes.inputStream()).use { z ->
                    var entry = z.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith(".fb2")) {
                            return z.bufferedReader().readText()
                        }
                        entry = z.nextEntry
                    }
                }
                return null
            }

            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "readFb2Xml failed", e)
            null
        }
    }
}
