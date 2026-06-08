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
}
