package com.foxybook.app.core.reader

import com.foxybook.app.core.models.BookFormat
import com.foxybook.app.core.models.ParsedBook
import com.foxybook.app.core.models.ParsedChapter
import java.io.File

class BookParser(
    private val epubParser: EpubParser = EpubParser(),
    private val fb2Parser: Fb2Parser = Fb2Parser(),
    private val mobiParser: MobiParser = MobiParser()
) {
    fun parse(file: File): ParsedBook? {
        if (!file.exists() || file.length() == 0L) return null

        val ext = file.extension.lowercase()
        val format = BookFormat.entries.find { it.extension == ext } ?: return null

        return when (format) {
            BookFormat.EPUB -> epubParser.parse(file)?.toParsed(format.extension)
            BookFormat.FB2 -> fb2Parser.parse(file)?.toParsed(format.extension)
            BookFormat.MOBI -> mobiParser.parse(file)?.toParsed(format.extension)
        }
    }

    private fun com.foxybook.app.core.models.EpubBook.toParsed(format: String) = ParsedBook(
        title = title, author = author,
        chapters = chapters.map { ParsedChapter(it.title, it.htmlContent) },
        format = format
    )

    private fun com.foxybook.app.core.models.Fb2Book.toParsed(format: String) = ParsedBook(
        title = title, author = author,
        chapters = chapters.map { ParsedChapter(it.title, it.htmlContent) },
        format = format
    )

    private fun com.foxybook.app.core.models.MobiBook.toParsed(format: String) = ParsedBook(
        title = title, author = author,
        chapters = chapters.map { ParsedChapter(it.title, it.htmlContent) },
        format = format
    )
}
