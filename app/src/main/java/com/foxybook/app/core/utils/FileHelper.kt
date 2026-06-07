package com.foxybook.app.core.utils

import android.content.Context
import com.foxybook.app.core.models.BookFormat
import java.io.File

object FileHelper {

    fun getBooksDir(context: Context): File {
        return File(context.getExternalFilesDir(null), "books")
    }

    fun getFormatDir(context: Context, format: BookFormat): File {
        val dir = File(getBooksDir(context), format.extension)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getBookFile(context: Context, id: String, format: BookFormat): File {
        return File(getFormatDir(context, format), "${id}.${format.extension}")
    }

    fun isBookDownloaded(context: Context, id: String, format: BookFormat): Boolean {
        return getBookFile(context, id, format).exists()
    }

    fun deleteBookFile(context: Context, id: String, format: BookFormat): Boolean {
        val file = getBookFile(context, id, format)
        return if (file.exists()) file.delete() else true
    }

    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(
            "%.1f %s",
            size / Math.pow(1024.0, digitGroups.toDouble()),
            units[digitGroups]
        )
    }
}
