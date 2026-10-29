package com.foxybook.app.core.updater

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "FoxyBook-Android")
                .build()
            chain.proceed(request)
        }
        .build()

    // Отдельный клиент для APK: большие таймауты на чтение и общий таймаут вызова,
    // чтобы зависшая загрузка не могла висеть бесконечно.
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.MINUTES)
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

        response.use {
            if (!it.isSuccessful) {
                throw Exception("Ошибка сервера (${it.code})")
            }

            val body = it.body?.string() ?: throw Exception("Пустой ответ сервера")
            val release = json.decodeFromString<GitHubReleaseResponse>(body)

            val remoteVersion = release.tagName.removePrefix("v").removePrefix("V")
            val cleanCurrent = currentVersion.substringBefore("-") // remove debug suffix

            if (!isNewer(remoteVersion, cleanCurrent)) return null

            val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                ?: throw Exception("APK не найден в релизе")

            requireAllowedDownloadHost(apkAsset.browserDownloadUrl)

            return UpdateInfo(
                version = release.tagName,
                downloadUrl = apkAsset.browserDownloadUrl,
                size = apkAsset.size,
                releaseNotes = release.body
            )
        }
    }

    suspend fun downloadApk(
        url: String,
        onProgress: (Float) -> Unit
    ): Uri {
        requireAllowedDownloadHost(url)

        val file = File(context.cacheDir, "update.apk")
        if (file.exists()) file.delete()

        val request = Request.Builder().url(url).get().build()

        withContext(Dispatchers.IO) {
            val response = downloadClient.newCall(request).execute()
            response.use {
                if (!it.isSuccessful) throw Exception("Ошибка скачивания (${it.code})")

                val body = it.body ?: throw Exception("Нет тела ответа")
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
        }

        // Установка разрешена только если APK подписан тем же ключом, что и
        // установленное приложение: иначе файл не является нашим релизом.
        verifySameSigner(file)
        if (expectedSize > 0 && file.length() != expectedSize) {
            file.delete()
            throw Exception("Файл повреждён: размер не совпадает")
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /** Ожидаемый размер APK; задаётся перед [downloadApk] через [withExpectedSize]. */
    private var expectedSize: Long = 0L

    fun withExpectedSize(size: Long): UpdateChecker {
        expectedSize = size
        return this
    }

    private fun requireAllowedDownloadHost(url: String) {
        val host = url.toHttpUrlOrNull()?.host?.lowercase()
            ?: throw IllegalArgumentException("Некорректный URL обновления")
        val allowed = ALLOWED_DOWNLOAD_HOSTS.any { host == it || host.endsWith(".$it") }
        if (!allowed) {
            throw IllegalArgumentException("Загрузка обновления с непроверенного хоста: $host")
        }
    }

    private fun verifySameSigner(apkFile: File) {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw SecurityException("Не удалось прочитать подпись скачанного APK")
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)

        val archiveSigners = archive.signingInfo?.apkContentsSigners
        val installedSigners = installed.signingInfo?.apkContentsSigners
        if (archiveSigners.isNullOrEmpty() || installedSigners.isNullOrEmpty()) {
            throw SecurityException("Не удалось получить сертификаты подписи")
        }
        val archiveSet = archiveSigners.map { it.toHex() }.toSet()
        val installedSet = installedSigners.map { it.toHex() }.toSet()
        if (archiveSet != installedSet) {
            apkFile.delete()
            throw SecurityException("Подпись обновления не совпадает с подписью приложения")
        }
    }

    private fun Signature.toHex(): String =
        toByteArray().joinToString("") { b -> "%02x".format(b) }


    companion object {
        private const val GITHUB_API =
            "https://api.github.com/repos/maximbogatchenko/FoxyBook/releases/latest"

        /** APK разрешено качать только с официальных хостов GitHub. */
        private val ALLOWED_DOWNLOAD_HOSTS = listOf("github.com", "githubusercontent.com")
    }
}

/**
 * Сравнение версий покомпонентно. Строковое сравнение здесь не годится:
 * "1.10" должно быть новее "1.9", а "1.8" — не новее "1.8".
 */
internal fun isNewer(remote: String, current: String): Boolean {
    val rParts = remote.trim().removePrefix("v").removePrefix("V").split(".").map { it.toIntOrNull() ?: 0 }
    val cParts = current.trim().removePrefix("v").removePrefix("V").split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(rParts.size, cParts.size)) {
        val r = rParts.getOrElse(i) { 0 }
        val c = cParts.getOrElse(i) { 0 }
        if (r != c) return r > c
    }
    return false
}
