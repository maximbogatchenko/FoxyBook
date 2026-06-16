package com.foxybook.app.core.updater

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

@Serializable
data class GitHubReleaseResponse(
    @SerialName("tag_name") val tagName: String,
    val assets: List<GitHubAsset>,
    val body: String = ""
)

@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long
)

data class UpdateInfo(
    val version: String,
    val downloadUrl: String,
    val size: Long,
    val releaseNotes: String = ""
)

class UpdateChecker(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "FoxyBook-Android")
                .build()
            chain.proceed(request)
        }
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Suppress("DEPRECATION")
    fun getCurrentVersion(): String {
        val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return pkgInfo.versionName?.substringBefore("-") ?: "1.0"
    }

    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? {
        val request = Request.Builder()
            .url(GITHUB_API)
            .get()
            .build()

        val response = withContext(Dispatchers.IO) {
            client.newCall(request).execute()
        }

        if (!response.isSuccessful) {
            throw Exception("Ошибка сервера (${response.code})")
        }

        val body = response.body?.string() ?: throw Exception("Пустой ответ сервера")
        val release = json.decodeFromString<GitHubReleaseResponse>(body)

        val remoteVersion = release.tagName.removePrefix("v").removePrefix("V")
        val cleanCurrent = currentVersion.substringBefore("-") // remove debug suffix

        if (!isNewer(remoteVersion, cleanCurrent)) return null

        val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
            ?: throw Exception("APK не найден в релизе")

        return UpdateInfo(
            version = release.tagName,
            downloadUrl = apkAsset.browserDownloadUrl,
            size = apkAsset.size,
            releaseNotes = release.body
        )
    }

    suspend fun downloadApk(
        url: String,
        onProgress: (Float) -> Unit
    ): Uri {
        val file = File(context.cacheDir, "update.apk")
        if (file.exists()) file.delete()

        val request = Request.Builder().url(url).get().build()

        withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("Ошибка скачивания (${response.code})")

            val body = response.body ?: throw Exception("Нет тела ответа")
            val contentLength = body.contentLength()
            var downloaded = 0L

            body.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (contentLength > 0) {
                            onProgress(downloaded.toFloat() / contentLength)
                        }
                    }
                    output.flush()
                }
            }
            onProgress(1f)
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    private fun isNewer(remote: String, current: String): Boolean {
        val rParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val cParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(rParts.size, cParts.size)) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }

    companion object {
        private const val GITHUB_API =
            "https://api.github.com/repos/maximbogatchenko/FoxyBook/releases/latest"
    }
}
