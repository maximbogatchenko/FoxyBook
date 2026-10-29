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

    /** Очищает временные кэш-директории приложения. Файлы книг не затрагиваются. */
    fun clearTempCaches(context: Context): String {
        var deletedCount = 0
        var deletedSize = 0L

        // ВАЖНО: filesDir/imported_books здесь быть не должно — это единственная
        // копия книг, импортированных пользователем через SAF.
        val dirs = listOf(
            File(context.cacheDir, "covers"),
            File(context.cacheDir, "book_images"),
            File(context.cacheDir, "http_cache")
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

        // Каталог импортированных книг чистится выборочно — см. SettingsViewModel.clearCaches(),
        // который передаёт реальные пути из библиотеки.
        val imported = cleanupOrphanedImportedFiles(context, knownPaths = emptySet())
        deletedCount += imported.first
        deletedSize += imported.second

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

    /**
     * Удаляет из filesDir/imported_books только те файлы, на которые больше нет
     * записи в библиотеке. Возвращает (количество файлов, суммарный размер).
     *
     * @param knownPaths пути файлов из библиотеки; если пусто — ничего не удаляется,
     *                   чтобы случайный вызов не привёл к потере книг.
     */
    fun cleanupOrphanedImportedFiles(
        context: Context,
        knownPaths: Set<String>
    ): Pair<Int, Long> {
        val dir = File(context.filesDir, "imported_books")
        if (!dir.exists() || knownPaths.isEmpty()) return 0 to 0L

        var count = 0
        var size = 0L
        val referenced = knownPaths.map { normalizePath(it) }.toSet()

        dir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            if (normalizePath(file.absolutePath) in referenced) return@forEach
            size += file.length()
            if (file.delete()) count++
        }
        return count to size
    }

    private fun normalizePath(path: String): String =
        runCatching { File(path).canonicalPath }.getOrDefault(path)
}
