package com.foxybook.app.core.network

import android.content.Context
import android.util.Log
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Centralized OkHttp client provider with:
 * - Connection pooling (10 idle connections, 5 min keep-alive)
 * - HTTP response cache (20 MB)
 * - Keep-Alive headers
 * - Mirror support
 */
class OkHttpClientProvider(context: Context) {

    private val TAG = "FOXYBOOK_NETWORK"

    val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

    val MIRRORS = listOf(
        "https://flibusta.is",
        "https://flibusta.site",
        "https://flibusta.su",
        "http://flibusta.is",
        "http://flibusta.site",
        "http://flibusta.su"
    )

    @Volatile
    var activeMirror: String = MIRRORS[0]
        private set

    fun switchMirror(newMirror: String) {
        if (newMirror != activeMirror) {
            Log.d(TAG, "Mirror: $activeMirror -> $newMirror")
            activeMirror = newMirror
        }
    }

    fun getBaseUrl(): String = activeMirror

    // HTTP cache: 20 MB
    private val httpCache = Cache(
        directory = File(context.cacheDir, "http_cache"),
        maxSize = 20L * 1024L * 1024L
    )

    // Connection pool: 10 idle connections, 5 min keep-alive
    private val connectionPool = ConnectionPool(
        maxIdleConnections = 10,
        keepAliveDuration = 5,
        timeUnit = TimeUnit.MINUTES
    )

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cache(httpCache)
            .connectionPool(connectionPool)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Connection", "keep-alive")
                    .build()
                chain.proceed(request)
            }
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun createDownloadClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS) // Longer timeout for downloads
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Connection", "keep-alive")
                    .build()
                chain.proceed(request)
            }
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
