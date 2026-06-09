package com.foxybook.app.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.crossfade
import java.io.File
import java.io.FileOutputStream

object BookImageCache {

    private const val TAG = "BOOK_IMAGE_CACHE"

    fun getImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }

    fun isImageName(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") ||
                n.endsWith(".gif") || n.endsWith(".webp") || n.endsWith(".bmp")
    }

    fun getMimeType(name: String): String {
        val n = name.lowercase()
        return when {
            n.endsWith(".png") -> "image/png"
            n.endsWith(".gif") -> "image/gif"
            n.endsWith(".webp") -> "image/webp"
            n.endsWith(".bmp") -> "image/bmp"
            else -> "image/jpeg"
        }
    }

    fun getCacheDir(context: Context, bookId: Int): File {
        val dir = File(context.cacheDir, "book_images/$bookId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getImageFile(context: Context, bookId: Int, fileName: String): File {
        return File(getCacheDir(context, bookId), fileName)
    }

    fun saveImage(context: Context, bookId: Int, fileName: String, bytes: ByteArray): File {
        val file = getImageFile(context, bookId, fileName)
        try {
            FileOutputStream(file).use { it.write(bytes) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image $fileName for book $bookId", e)
        }
        return file
    }

    fun clearCache(context: Context, bookId: Int) {
        val dir = getCacheDir(context, bookId)
        if (dir.exists()) dir.deleteRecursively()
    }
}
