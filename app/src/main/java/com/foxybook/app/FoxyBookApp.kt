package com.foxybook.app

import android.app.Application
import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import com.foxybook.app.di.appModule
import java.util.concurrent.TimeUnit

class FoxyBookApp : Application(), SingletonImageLoader.Factory {

    private val TAG = "FOXYBOOK_APP"

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
    }

    override fun newImageLoader(context: Context): ImageLoader {
        Log.d(TAG, "newImageLoader | creating Coil ImageLoader with custom OkHttp")

        val coverClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val request = originalRequest.newBuilder()
                    .header("User-Agent",
                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "image/webp,image/apng,image/*,*/*;q=0.8")
                    .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Connection", "keep-alive")
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
            .build()
    }
}
