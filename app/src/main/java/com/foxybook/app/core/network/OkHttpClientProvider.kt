package com.foxybook.app.core.network

import android.content.Context
import android.util.Log
import com.foxybook.app.core.models.BookSource
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class OkHttpClientProvider(context: Context) {

    private val TAG = "FOXYBOOK_NETWORK"

    val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

    private val flibustaMirrors = listOf(
        "https://flibusta.is", "https://flibusta.site"
    )

    private val coollibMirrors = listOf(
        "https://coollib.net", "https://coollib.cc"
    )

    private val fantasyWorldsMirrors = listOf(
        "https://fantasy-worlds.org"
    )

    @Volatile
    var activeSource: BookSource = BookSource.FLIBUSTA
        private set

    @Volatile
    var activeMirror: String = flibustaMirrors[0]
        private set

    fun switchSource(source: BookSource) {
        if (source == activeSource) return
        activeSource = source
        activeMirror = when (source) {
            BookSource.FLIBUSTA -> flibustaMirrors[0]
            BookSource.COOLLIB -> coollibMirrors[0]
            BookSource.FANTASY_WORLDS -> fantasyWorldsMirrors[0]
        }
        Log.d(TAG, "Source: $activeSource, mirror: $activeMirror")
    }

    fun switchMirror(newMirror: String) {
        if (newMirror != activeMirror) {
            Log.d(TAG, "Mirror: $activeMirror -> $newMirror")
            activeMirror = newMirror
        }
    }

    fun getMirrors(): List<String> = when (activeSource) {
        BookSource.FLIBUSTA -> flibustaMirrors
        BookSource.COOLLIB -> coollibMirrors
        BookSource.FANTASY_WORLDS -> fantasyWorldsMirrors
    }

    fun getBaseUrl(): String = activeMirror

    fun switchToNextMirror(): String {
        val mirrors = getMirrors()
        val currentIndex = mirrors.indexOf(activeMirror)
        if (currentIndex < 0) return activeMirror
        val nextIndex = (currentIndex + 1) % mirrors.size
        if (nextIndex != currentIndex) {
            activeMirror = mirrors[nextIndex]
            Log.d(TAG, "Mirror rotated: $activeMirror")
        }
        return activeMirror
    }

    /**
     * Выполняет HTTP-запрос с автоматическим переключением зеркала при 502/503/504/429.
     * @throws Exception если все зеркала исчерпаны
     */
    fun fetchWithMirrorRetry(
        url: String,
        client: OkHttpClient = this.client,
        maxRetries: Int = MAX_MIRROR_RETRIES
    ): String {
        var currentUrl = url
        var lastError: String = "Неизвестная ошибка"
        for (attempt in 0 until maxRetries) {
            try {
                val request = Request.Builder().url(currentUrl).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    return response.body?.string() ?: ""
                }
                val code = response.code
                lastError = "HTTP $code"
                response.close()
                if (code in listOf(502, 503, 504, 429, 403) && attempt < maxRetries - 1) {
                    Log.w(TAG, "HTTP $code on ${getBaseUrl()}, switching mirror (attempt ${attempt + 1})")
                    currentUrl = currentUrl.replace(Regex("https?://[^/]+"), switchToNextMirror())
                    continue
                }
                throw Exception("$lastError на ${getBaseUrl()}")
            } catch (e: Exception) {
                if (e is java.util.concurrent.CancellationException) throw e
                lastError = e.message ?: "Ошибка сети"
                if (attempt < maxRetries - 1) {
                    Log.w(TAG, "Request failed: ${e.message}, switching mirror")
                    currentUrl = currentUrl.replace(Regex("https?://[^/]+"), switchToNextMirror())
                } else {
                    throw Exception("${e.message} (все зеркала исчерпаны)")
                }
            }
        }
        throw Exception(lastError)
    }


    // HTTP cache: 20 MB
    private val httpCache = Cache(
        directory = File(context.cacheDir, "http_cache"),
        maxSize = HTTP_CACHE_SIZE
    )

    // Shared connection pool — all clients reuse the same TCP connections
    private val connectionPool = ConnectionPool(
        maxIdleConnections = MAX_IDLE_CONNECTIONS,
        keepAliveDuration = KEEP_ALIVE_DURATION,
        timeUnit = TimeUnit.MINUTES
    )

    private fun baseInterceptor(extraHeaders: Map<String, String> = emptyMap()) = okhttp3.Interceptor { chain ->
        val builder = chain.request().newBuilder()
            .header("User-Agent", USER_AGENT)
            .header("Connection", "keep-alive")
        extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        chain.proceed(builder.build())
    }

    fun createClient(
        connectSeconds: Long = DEFAULT_CONNECT_TIMEOUT,
        readSeconds: Long = DEFAULT_READ_TIMEOUT,
        writeSeconds: Long = DEFAULT_WRITE_TIMEOUT,
        withCache: Boolean = false,
        extraHeaders: Map<String, String> = emptyMap()
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .apply { if (withCache) cache(httpCache) }
            .connectionPool(connectionPool)
            .connectTimeout(connectSeconds, TimeUnit.SECONDS)
            .readTimeout(readSeconds, TimeUnit.SECONDS)
            .writeTimeout(writeSeconds, TimeUnit.SECONDS)
            .addInterceptor(baseInterceptor(extraHeaders))
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    val client: OkHttpClient by lazy {
        createClient(
            withCache = true,
            extraHeaders = mapOf(
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Accept-Language" to "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7"
            )
        )
    }

    fun createDownloadClient(): OkHttpClient {
        return createClient(connectSeconds = DOWNLOAD_CONNECT_TIMEOUT, readSeconds = DOWNLOAD_READ_TIMEOUT, writeSeconds = DOWNLOAD_WRITE_TIMEOUT)
    }

    companion object {
        /** Размер HTTP-кеша */
        const val HTTP_CACHE_SIZE = 20L * 1024L * 1024L
        /** Таймаут подключения по умолчанию (с) */
        const val DEFAULT_CONNECT_TIMEOUT = 30L
        /** Таймаут чтения по умолчанию (с) */
        const val DEFAULT_READ_TIMEOUT = 30L
        /** Таймаут записи по умолчанию (с) */
        const val DEFAULT_WRITE_TIMEOUT = 30L
        /** Таймаут подключения для скачивания (с) */
        const val DOWNLOAD_CONNECT_TIMEOUT = 60L
        /** Таймаут чтения для скачивания (с) */
        const val DOWNLOAD_READ_TIMEOUT = 120L
        /** Таймаут записи для скачивания (с) */
        const val DOWNLOAD_WRITE_TIMEOUT = 60L
        /** Максимальное количество ретраев при ошибке зеркала */
        const val MAX_MIRROR_RETRIES = 3
        /** Максимальное количество idle соединений */
        const val MAX_IDLE_CONNECTIONS = 10
        /** Время жизни idle соединения (мин) */
        const val KEEP_ALIVE_DURATION = 5L
    }
}
