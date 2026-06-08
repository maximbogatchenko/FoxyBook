package com.foxybook.app.core.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

object UriUtils {
    fun getFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        
        var name: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) name = cursor.getString(nameIndex)
                }
            }
        } catch (_: Exception) {}
        return name ?: uri.lastPathSegment
    }

    fun copyUriToTempFile(context: Context, uri: Uri, tempFileName: String): File? {
        return try {
            // Check if we can open the stream first
            context.contentResolver.openInputStream(uri)?.use { input ->
                val tempFile = File(context.cacheDir, tempFileName)
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
                tempFile
            }
        } catch (e: Exception) {
            android.util.Log.e("UriUtils", "Failed to copy URI to temp file: $uri", e)
            // Log details about persistable permissions
            val persistedPermissions = context.contentResolver.persistedUriPermissions
            android.util.Log.d("UriUtils", "Currently held persisted permissions: ${persistedPermissions.size}")
            persistedPermissions.forEach { 
                android.util.Log.d("UriUtils", "Held: ${it.uri}, Read: ${it.isReadPermission}, Write: ${it.isWritePermission}")
            }
            null
        }
    }
}
