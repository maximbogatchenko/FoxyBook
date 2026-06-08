package com.foxybook.app.core.reader

import android.content.Context
import android.net.Uri
import android.util.Log
import com.foxybook.app.core.models.BookFormat
import com.foxybook.app.core.models.ParsedBook
import com.foxybook.app.core.models.ParsedChapter
import java.io.File

class BookParser(
    private val context: Context,
    private val epubParser: EpubParser = EpubParser(context),
    private val fb2Parser: Fb2Parser = Fb2Parser(context),
    private val mobiParser: MobiParser = MobiParser(context),
    private val txtParser: TxtParser = TxtParser(context)
) {
    /**
     * Quickly parses book structure and metadata.
     */
    fun parse(path: String, knownFormat: String? = null): ParsedBook? {
        Log.d("BookParser", "parse: path=$path, knownFormat=$knownFormat")
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
            BookFormat.MOBI -> mobiParser.parse(context, uri)?.toParsed(format.extension)
            BookFormat.TXT -> txtParser.parse(uri)
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
        chapters = chapters.map { ParsedChapter(it.title, contentId = "") },
        format = format
    )
}
