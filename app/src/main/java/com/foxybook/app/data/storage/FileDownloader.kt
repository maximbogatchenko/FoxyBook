package com.foxybook.app.data.storage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.foxybook.app.core.models.BookFormat
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class FileDownloader(private val context: Context) {

    companion object {
        private const val TAG = "FileDownloader"
        private const val BUFFER_SIZE = 16384
    }

    fun download(
        body: ResponseBody,
        fileName: String,
        format: BookFormat,
        customDirUri: String?,
        onProgress: (Float) -> Unit
    ): String {
        return if (customDirUri != null) {
            saveToDirectory(customDirUri, format, fileName, body, onProgress)
        } else {
            saveToDefault(format, fileName, body, onProgress)
        }
    }

    private fun saveToDirectory(
        uriString: String,
        format: BookFormat,
        fileName: String,
        body: ResponseBody,
        onProgress: (Float) -> Unit
    ): String {
        val rootUri = Uri.parse(uriString)
        val rootDir = DocumentFile.fromTreeUri(context, rootUri)
            ?: throw Exception("Failed to access custom directory")

        val foxyDir = rootDir.findFile("FoxyBook")?.takeIf { it.isDirectory }
            ?: rootDir.createDirectory("FoxyBook")
        val finalDir = foxyDir ?: throw Exception("Failed to create FoxyBook directory")
        val targetDir = finalDir.findFile(format.extension)?.takeIf { it.isDirectory }
            ?: finalDir.createDirectory(format.extension)
            ?: finalDir

        // Delete existing file before replacing
        targetDir.findFile(fileName)?.delete()

        val displayName = if (fileName.endsWith(".${format.extension}")) {
            fileName.substringBeforeLast(".")
        } else {
            fileName
        }

        val file = targetDir.createFile(format.mimeType, displayName)
            ?: throw Exception("Failed to create file in custom directory")

        try {
            context.contentResolver.takePersistableUriPermission(
                file.uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to take persistable permission: ${file.uri}", e)
        }

        context.contentResolver.openOutputStream(file.uri)?.use { output ->
            writeBodyToStream(body, output, onProgress)
        } ?: throw Exception("Failed to open output stream")

        return file.uri.toString()
    }

    private fun saveToDefault(
        format: BookFormat,
        fileName: String,
        body: ResponseBody,
        onProgress: (Float) -> Unit
    ): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(format, fileName, body, onProgress)
        } else {
            saveViaLegacy(format, fileName, body, onProgress)
        }
    }

    private fun saveViaMediaStore(
        format: BookFormat,
        fileName: String,
        body: ResponseBody,
        onProgress: (Float) -> Unit
    ): String {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/FoxyBook/${format.extension}")
        }

        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues
        ) ?: throw Exception("Failed to create MediaStore entry")

        context.contentResolver.openOutputStream(uri)?.use { output ->
            writeBodyToStream(body, output, onProgress)
        } ?: throw Exception("Failed to open MediaStore output stream")

        return uri.toString()
    }

    private fun saveViaLegacy(
        format: BookFormat,
        fileName: String,
        body: ResponseBody,
        onProgress: (Float) -> Unit
    ): String {
        val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        val destDir = File(downloadDir, "Download/FoxyBook/${format.extension}")
        if (!destDir.exists()) destDir.mkdirs()

        val destFile = File(destDir, fileName)
        FileOutputStream(destFile).use { output ->
            writeBodyToStream(body, output, onProgress)
        }
        return destFile.absolutePath
    }

    fun deleteFile(path: String) {
        if (path.isBlank()) return
        Log.d(TAG, "deleteFile: path=$path")
        if (path.startsWith("content://")) {
            try {
                val uri = Uri.parse(path)
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    val deleted = DocumentsContract.deleteDocument(context.contentResolver, uri)
                    Log.d(TAG, "deleteFile: DocumentsContract deleted=$deleted")
                } else {
                    val deleted = context.contentResolver.delete(uri, null, null)
                    Log.d(TAG, "deleteFile: ContentResolver deleted=$deleted")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete URI $path", e)
            }
        } else {
            try {
                val file = File(path)
                if (file.exists()) {
                    val deleted = file.delete()
                    Log.d(TAG, "deleteFile: File deleted=$deleted (path=$path)")
                    if (!deleted) {
                        // Fallback: попробовать deleteOnExit
                        file.deleteOnExit()
                    }
                } else {
                    Log.w(TAG, "deleteFile: File does not exist: $path")
                }
            } catch (e: Exception) {
                Log.e(TAG, "deleteFile: Error deleting file $path", e)
            }
        }
    }

    fun fileExists(path: String): Boolean {
        return if (path.startsWith("content://")) {
            try {
                val uri = Uri.parse(path)
                context.contentResolver.openInputStream(uri)?.use { it.close(); true } ?: false
            } catch (_: Exception) {
                false
            }
        } else {
            File(path).exists()
        }
    }

    private fun writeBodyToStream(
        body: ResponseBody,
        output: OutputStream,
        onProgress: (Float) -> Unit
    ) {
        val contentLength = body.contentLength().toFloat()
        var bytesDownloaded = 0L
        val buffer = ByteArray(BUFFER_SIZE)

        body.byteStream().use { input ->
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                bytesDownloaded += bytesRead
                if (contentLength > 0f) {
                    onProgress(bytesDownloaded / contentLength)
                } else if (bytesDownloaded % 65536 == 0L) {
                    onProgress(-1f)
                }
            }
            output.flush()
        }
        onProgress(1f)
    }
}
