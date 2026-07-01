package com.foxybook.app.core.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import java.io.File

object StorageHelper {

    fun getDefaultDownloadDir(): File {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloadDir, "FoxyBook")
    }

    fun getDownloadDir(context: Context, uriString: String?): Any {
        if (uriString == null) {
            val dir = getDefaultDownloadDir()
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

        val uri = Uri.parse(uriString)
        val documentFile = DocumentFile.fromTreeUri(context, uri)

        return if (documentFile != null && documentFile.exists() && documentFile.canWrite()) {
            documentFile
        } else {
            val dir = getDefaultDownloadDir()
            if (!dir.exists()) dir.mkdirs()
            dir
        }
    }

    fun isUriValid(context: Context, uriString: String?): Boolean {
        if (uriString == null) return true
        val uri = Uri.parse(uriString)
        val documentFile = DocumentFile.fromTreeUri(context, uri)
        return documentFile != null && documentFile.exists() && documentFile.canWrite()
    }

    fun getReadablePath(uriString: String?): String {
        if (uriString == null) return "/Download/FoxyBook"
        return try {
            val uri = Uri.parse(uriString)
            val path = uri.path ?: ""
            if (path.contains(":")) {
                path.substringAfterLast(":")
            } else {
                path
            }
        } catch (_: Exception) {
            uriString ?: ""
        }
    }

    /** Очищает временные кэш-директории приложения */
    fun clearTempCaches(context: Context): String {
        var deletedCount = 0
        var deletedSize = 0L

        val dirs = listOf(
            File(context.cacheDir, "covers"),
            File(context.cacheDir, "book_images"),
            File(context.cacheDir, "http_cache"),
            File(context.filesDir, "imported_books")
        )

        for (dir in dirs) {
            if (dir.exists()) {
                val size = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                val count = dir.walkTopDown().filter { it.isFile }.count()
                dir.deleteRecursively()
                deletedCount += count
                deletedSize += size
            }
        }

        // Удаляем файл скачанного обновления, если есть
        val updateFile = File(context.cacheDir, "update.apk")
        if (updateFile.exists()) {
            deletedSize += updateFile.length()
            deletedCount++
            updateFile.delete()
        }

        val sizeStr = when {
            deletedSize >= 1_000_000 -> "${"%.1f".format(deletedSize / 1_000_000f)} MB"
            deletedSize >= 1_000 -> "${"%.0f".format(deletedSize / 1_000f)} KB"
            else -> "$deletedSize B"
        }
        return "Очищено файлов: $deletedCount ($sizeStr)"
    }
}
