package com.foxybook.app.core.reader

import android.content.Context
import android.net.Uri
import android.util.Log
import com.foxybook.app.core.models.ParsedBook
import com.foxybook.app.core.models.ParsedChapter

class TxtParser(private val context: Context) {
    companion object {
        private const val TAG = "TXT_PARSER"
    }

    fun parse(uri: Uri): ParsedBook? {
        Log.d(TAG, "parse: uri=$uri")
        return try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return null
            
            val fileName = com.foxybook.app.core.utils.UriUtils.getFileName(context, uri) ?: "Document.txt"
            
            ParsedBook(
                title = fileName.substringBeforeLast("."),
                author = "Unknown Author",
                chapters = listOf(ParsedChapter(title = "Начало", htmlContent = textToHtml(text))),
                format = "txt"
            )
        } catch (e: Exception) {
            Log.e(TAG, "parse: error", e)
            null
        }
    }

    private fun textToHtml(text: String): String {
        return text.split("\n").joinToString("") { line ->
            if (line.isBlank()) "<br/>" else "<p>${escapeHtml(line)}</p>"
        }
    }

    private fun escapeHtml(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
