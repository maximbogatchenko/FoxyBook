package com.foxybook.app.core.utils

import android.content.Context
import android.util.Log
import com.foxybook.app.core.models.BookFormat
import java.io.File
import java.security.MessageDigest

object BookImageCache {

    private const val TAG = "BOOK_IMAGES"
    private const val CACHE_DIR = "book_cache"

    fun getCacheDir(context: Context, bookId: Int): File {
        val dir = File(context.getExternalFilesDir(null), "$CACHE_DIR/$bookId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun isCached(context: Context, bookId: Int): Boolean {
        val dir = getCacheDir(context, bookId)
        return dir.exists() && dir.listFiles()?.isNotEmpty() == true
    }

    fun clearCache(context: Context, bookId: Int) {
        val dir = getCacheDir(context, bookId)
        if (dir.exists()) dir.deleteRecursively()
    }

    fun saveImage(context: Context, bookId: Int, name: String, data: ByteArray): File {
        val safeName = sanitizeFileName(name)
        val file = File(getCacheDir(context, bookId), safeName)
        file.writeBytes(data)
        Log.d(TAG, "Saved image: $safeName (${data.size} bytes) for book $bookId")
        return file
    }

    fun getImageFile(context: Context, bookId: Int, name: String): File {
        return File(getCacheDir(context, bookId), sanitizeFileName(name))
    }

    fun getBaseFileUrl(context: Context, bookId: Int): String {
        return getCacheDir(context, bookId).toURI().toString()
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace("/", "_")
            .replace("\\", "_")
            .replace(":", "_")
            .replace("?", "_")
            .replace("*", "_")
            .replace("<", "_")
            .replace(">", "_")
            .replace("|", "_")
            .replace("\"", "_")
    }

    fun getMimeType(name: String): String = when {
        name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) -> "image/jpeg"
        name.endsWith(".png", true) -> "image/png"
        name.endsWith(".gif", true) -> "image/gif"
        name.endsWith(".webp", true) -> "image/webp"
        name.endsWith(".svg", true) -> "image/svg+xml"
        else -> "image/jpeg"
    }

    fun isImageName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
               lower.endsWith(".png") || lower.endsWith(".gif") ||
               lower.endsWith(".webp") || lower.endsWith(".svg")
    }
}
