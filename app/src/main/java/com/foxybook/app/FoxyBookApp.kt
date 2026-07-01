package com.foxybook.app

import android.app.Application
import android.content.Context
import android.os.Process
import android.util.Log
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import com.foxybook.app.core.network.OkHttpClientProvider
import com.foxybook.app.di.appModule
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FoxyBookApp : Application(), SingletonImageLoader.Factory {

    private val TAG = "FOXYBOOK_APP"
    private val CRASH_TAG = "FOXYBOOK_CRASH"

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@FoxyBookApp)
            modules(appModule)
        }
        Log.d(TAG, "Koin initialized")

        // Форсированная инициализация Coil с кастомным OkHttp для загрузки обложек
        SingletonImageLoader.get(this)
        Log.d(TAG, "Coil ImageLoader initialized")

        // Crash handler — пишет stacktrace в файл и лог
        setupCrashHandler()
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stackLines = throwable.stackTrace.joinToString("\n") { "    at $it" }
            val cause = throwable.cause?.let { "\nCaused by: ${it}\n${it.stackTrace.joinToString("\n") { "    at $it" }}" } ?: ""
            val msg = """
                |=== FOXYBOOK CRASH ===
                |Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}
                |Thread: ${thread.name} (${thread.id})
                |${throwable}::${throwable.message}
                |$stackLines
                |$cause
                |=== END ===
            """.trimMargin()

            Log.e(CRASH_TAG, msg)

            // Write to file
            try {
                val crashDir = File(cacheDir, "crashes")
                crashDir.mkdirs()
                val file = File(crashDir, "crash_${System.currentTimeMillis()}.txt")
                FileWriter(file).use { it.write(msg) }
                Log.d(CRASH_TAG, "Written to ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e(CRASH_TAG, "Failed to write crash file", e)
            }

            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    override fun newImageLoader(context: Context): ImageLoader {
        Log.d(TAG, "newImageLoader | creating Coil ImageLoader with shared OkHttp")

        val networkProvider: OkHttpClientProvider = org.koin.java.KoinJavaComponent.get(OkHttpClientProvider::class.java)
        val sharedClient = networkProvider.createClient(
            withCache = true,
            extraHeaders = mapOf(
                "Accept" to "image/webp,image/apng,image/*,*/*;q=0.8",
                "Accept-Language" to "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7"
            )
        )

        val coverClient = sharedClient.newBuilder()
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val request = originalRequest.newBuilder()
                    .header("Referer", originalRequest.url.scheme + "://" + originalRequest.url.host)
                    .build()
                val response = chain.proceed(request)
                Log.d(TAG, "Cover fetch | url=${request.url} | code=${response.code} | contentType=${response.header("Content-Type")}")
                response
            }
            .build()

        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(coverClient))
            }
            .memoryCache {
                coil3.memory.MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_cache").toOkioPath())
                    .maxSizeBytes(50L * 1024L * 1024L)
                    .build()
            }
            .build()
    }
}
