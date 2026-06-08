package com.foxybook.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.util.Log
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.foxybook.app.core.datastore.DataStoreManager
import com.foxybook.app.core.models.Book
import com.foxybook.app.core.models.BookFormat
import com.foxybook.app.core.models.BookInfo
import com.foxybook.app.core.models.LibraryBook
import com.foxybook.app.core.models.Series
import com.foxybook.app.core.utils.FileHelper
import com.foxybook.app.data.api.FlibustaApi
import com.foxybook.app.domain.repository.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class BookRepositoryImpl(
    private val api: FlibustaApi,
    private val context: Context,
    private val dataStoreManager: DataStoreManager
) : BookRepository {

    override suspend fun searchBooks(query: String, limit: Int): List<Book> {
        return api.searchBooks(query, limit)
    }

    override suspend fun searchByAuthor(author: String, limit: Int): List<Book> {
        return api.searchByAuthor(author, limit)
    }

    override suspend fun searchBySeries(series: String, limit: Int): List<Series> {
        return api.searchBySeries(series, limit)
    }

    override suspend fun getSeriesBooks(seriesId: String, limit: Int): List<Book> {
        return api.getSeriesBooks(seriesId, limit)
    }

    override suspend fun getBookInfo(id: Int): BookInfo? {
        return api.getBookInfo(id)
    }

    override fun getDownloadUrl(id: String, format: BookFormat): String {
        return api.getDownloadUrl(id, format)
    }

    override suspend fun downloadBook(
        id: String,
        format: BookFormat,
        onProgress: (Float) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        Log.d("BookRepository", "downloadBook: id=$id, format=$format")
        try {
            val responseBody = api.downloadBook(id, format, onProgress)
                ?: return@withContext Result.failure(Exception("Failed to download"))

            val fileName = "$id.${format.extension}"

            val resultPath = saveToDefaultDownload(format, fileName, responseBody, onProgress)
            
            Log.d("BookRepository", "downloadBook: SUCCESS, path=$resultPath")
            Result.success(resultPath)
        } catch (e: Exception) {
            Log.e("BookRepository", "downloadBook: ERROR", e)
            Result.failure(e)
        }
    }

    private fun saveToDefaultDownload(
        format: BookFormat,
        fileName: String,
        body: ResponseBody,
        onProgress: (Float) -> Unit
    ): String {
        val formatDirName = format.extension
        val relativePath = "Download/FoxyBook/$formatDirName"
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            
            val contentUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val uri = context.contentResolver.insert(contentUri, contentValues)
                ?: throw Exception("Failed to create MediaStore entry")
                
            context.contentResolver.openOutputStream(uri)?.use { output ->
                writeBodyToStream(body, output, onProgress)
            } ?: throw Exception("Failed to open MediaStore output stream")
            
            uri.toString()
        } else {
            // Legacy way for Android 9 and below
            val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val foxyDir = File(downloadDir, "FoxyBook/$formatDirName")
            if (!foxyDir.exists()) foxyDir.mkdirs()
            
            val destFile = File(foxyDir, fileName)
            FileOutputStream(destFile).use { output ->
                writeBodyToStream(body, output, onProgress)
            }
            destFile.absolutePath
        }
    }

    private fun writeBodyToStream(body: ResponseBody, output: OutputStream, onProgress: (Float) -> Unit) {
        val contentLength = body.contentLength().toFloat()
        var bytesDownloaded = 0L
        val buffer = ByteArray(8192)
        
        body.byteStream().use { input ->
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                bytesDownloaded += bytesRead
                if (contentLength > 0f) {
                    onProgress(bytesDownloaded / contentLength)
                }
            }
            output.flush()
        }
        onProgress(1f)
    }

    override fun getLibraryBooks(): Flow<List<LibraryBook>> {
        return dataStoreManager.libraryBooks
    }

    override suspend fun addLibraryBook(book: LibraryBook) {
        dataStoreManager.addLibraryBook(book)
    }

    override suspend fun removeLibraryBook(bookId: Int, format: String) {
        val books = getLibraryBooks().first()
        val book = books.find { it.id == bookId && it.format == format }
        Log.d("BookRepository", "removeLibraryBook: id=$bookId, format=$format, found=${book != null}")
        
        dataStoreManager.removeLibraryBook(bookId, format)
        
        if (book != null) {
            val path = book.filePath
            Log.d("BookRepository", "removeLibraryBook: deleting file at $path")
            if (path.startsWith("content://")) {
                try {
                    val uri = Uri.parse(path)
                    if (DocumentsContract.isDocumentUri(context, uri)) {
                        DocumentsContract.deleteDocument(context.contentResolver, uri)
                        Log.d("BookRepository", "removeLibraryBook: document deleted")
                    } else {
                        context.contentResolver.delete(uri, null, null)
                        Log.d("BookRepository", "removeLibraryBook: uri deleted")
                    }
                } catch (e: Exception) {
                    Log.e("BookRepository", "removeLibraryBook: failed to delete URI $path", e)
                }
            } else {
                val file = File(path)
                if (file.exists()) {
                    val deleted = file.delete()
                    Log.d("BookRepository", "removeLibraryBook: file deleted=$deleted")
                }
            }
        }
    }

    override suspend fun isBookDownloaded(id: String, format: BookFormat): Boolean {
        val books = getLibraryBooks().first()
        val book = books.find { it.id.toString() == id && it.format == format.extension } ?: return false
        val path = book.filePath
        
        Log.d("BookRepository", "─── isBookDownloaded Diagnostic ───")
        Log.d("BookRepository", "Book ID: $id")
        Log.d("BookRepository", "Format: $format")
        Log.d("BookRepository", "Saved Path: $path")
        
        return if (path.startsWith("content://")) {
            try {
                val uri = Uri.parse(path)
                context.contentResolver.openInputStream(uri)?.use { 
                    it.close()
                    Log.d("BookRepository", "Result: URI exists and readable")
                    true 
                } ?: false
            } catch (e: Exception) {
                Log.e("BookRepository", "Result: Failed to open URI $path. Error: ${e.message}")
                false
            }
        } else {
            val exists = File(path).exists()
            Log.d("BookRepository", "Result: File exists=$exists")
            exists
        }
    }
}
