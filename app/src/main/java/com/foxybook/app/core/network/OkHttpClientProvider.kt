package com.foxybook.app.core.network

import android.content.Context
import android.util.Log
import com.foxybook.app.core.models.BookSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Отменяемый HTTP-вызов: отмена корутины (withTimeout, смена запроса, уход
 * с экрана) реально прерывает сетевой вызов, а не только забывает его результат.
 */
suspend fun OkHttpClient.executeCancellable(request: Request): Response {
    return suspendCancellableCoroutine { continuation ->
        val call = newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (continuation.isCancelled) {
                    response.close()
                } else {
                    continuation.resume(response)
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) {
                    continuation.resumeWithException(e)
                }
            }
        })
    }
}

/**
 * Каталоги отдают ETag/Last-Modified, но Cache-Control есть не у всех, а у
 * флибусты он равен 10 минутам. Без явного max-age дисковый кеш OkHttp почти
 * не работает, и каждый запуск приложения качает фиды заново.
 *
 * Заголовок добавляется сетевым интерсептором — только он влияет на то, что
 * попадёт в кеш, — и только если сервер не задал собственную политику.
 * Собственную политику сервера уважаем: если он запретил кеширование,
 * добавлять max-age нельзя.
 */
/**
 * Сколько секунд можно переиспользовать ответ по этому URL.
 *
 * Новинки меняются часто, жанры и индексы серий — практически никогда, поиск
 * устаревает быстро. Всё остальное не кешируем: HTML-страницы книг и так
 * приходят с собственными заголовками.
 */
internal fun feedTtlSecondsFor(url: String): Int {
    val path = url.substringBefore('?')
    return when {
        path.contains("/opds/genres") -> 6 * 3600
        path.contains("/opds/sequencesindex") || path.contains("/opds/authorsindex") -> 6 * 3600
        path.contains("/opds/sequences") -> 6 * 3600
        path.contains("/opds/search") || path.contains("/opdssearch") -> 120
        path.endsWith("/opds/new") || path.contains("/opds/new/") || path.contains("/opds/last") -> 600
        path.contains("/opds") -> 600
        else -> 0
    }
}

internal fun feedCacheInterceptorFor(ttlFor: (String) -> Int): Interceptor = Interceptor { chain ->
    val response = chain.proceed(chain.request())
    if (!response.isSuccessful) return@Interceptor response
    val cacheControl = response.header("Cache-Control")
    if (cacheControl != null || response.header("Expires") != null) return@Interceptor response
    val ttl = ttlFor(chain.request().url.encodedPath + "?" + chain.request().url.encodedQuery)
    if (ttl <= 0) return@Interceptor response
    response.newBuilder()
        .header("Cache-Control", "public, max-age=$ttl")
        .build()
}

class OkHttpClientProvider(context: Context) {

    /** Ответ сервера без смысла повторять на том же зеркале. */
    class HttpStatusException(val code: Int, message: String) : IOException(message)

    private val TAG = "FOXYBOOK_NETWORK"

    val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

    /**
     * Порядок зеркал — это порядок предпочтения: первым идёт проверенное
     * отвечающее зеркало. У flibusta домен flibusta.is регулярно не отвечает
     * (проверено: connect висит 20+ с), поэтому он идёт вторым и почти всегда
     * отсекается по [unhealthyUntil].
     */
    private val flibustaMirrors = listOf(
<<<<<<< HEAD
        "https://flibusta.is", "https://flibusta.site"
=======
        "https://flibusta.site", "https://flibusta.is"
>>>>>>> d24a20e (FoxyBook v1.9)
    )

    private val coollibMirrors = listOf(
        "https://coollib.net", "https://coollib.cc"
    )

    private val fantasyWorldsMirrors = listOf(
        "https://fantasy-worlds.org"
    )

    /** Зеркала, признанные недоступными: метка времени, до которой зеркало не берём. */
    private val unhealthyUntil = ConcurrentHashMap<String, Long>()

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

    /**
     * Ключ зеркала в карте здоровья. Все публичные методы и вызывающий код
     * оперируют полными base URL, а сетевой слой знает только хост — если
     * писать и читать по разным ключам, отметка «зеркало мёртвое» никогда не
     * сработает. Поэтому обе стороны приводятся к каноническому base URL.
     */
    private fun mirrorKey(url: String): String {
        val host = hostOf(url).orEmpty()
        val known = getMirrors().firstOrNull { hostOf(it) == host }
        return (known ?: url.substringBefore('/')).trimEnd('/')
    }

    fun isMirrorHealthy(mirror: String): Boolean {
        val until = unhealthyUntil[mirror.trimEnd('/')] ?: return true
        if (System.currentTimeMillis() >= until) {
            unhealthyUntil.remove(mirror.trimEnd('/'))
            return true
        }
        return false
    }

    fun markMirrorUnhealthy(mirror: String) {
        val key = mirror.trimEnd('/')
        if (key.isBlank()) return
        unhealthyUntil[key] = System.currentTimeMillis() + MIRROR_COOLDOWN_MS
        Log.w(TAG, "Mirror marked unhealthy for ${MIRROR_COOLDOWN_MS / 1000}s: $key")
    }

    fun markMirrorHealthy(mirror: String) {
        if (mirror.isNotBlank()) unhealthyUntil.remove(mirror.trimEnd('/'))
    }

    /**
     * Базовый URL источника: предпочитаем выбранное зеркало, но если оно
     * только что показало себя мёртвым — берём первое живое, чтобы не ждать
     * таймаут на каждом запросе.
     */
    fun getBaseUrl(): String {
        val preferred = activeMirror
        if (isMirrorHealthy(preferred)) return preferred
        val fallback = getMirrors().firstOrNull { it != preferred && isMirrorHealthy(it) }
        return fallback ?: preferred
    }

    fun switchToNextMirror(): String {
        val mirrors = getMirrors()
        val currentIndex = mirrors.indexOf(activeMirror)
        if (currentIndex < 0) return activeMirror
        val nextIndex = (currentIndex + 1) % mirrors.size
        val next = mirrors[nextIndex]
        if (next != activeMirror) {
            activeMirror = next
            Log.d(TAG, "Mirror rotated: $activeMirror")
        }
        return activeMirror
    }

    /**
     * Следующее пригодное зеркало после [currentHost], либо null, если
     * чередовать нечем (тогда повторяем тот же хост — дешевле, чем висеть
     * на заведомо мёртвом зеркале).
     */
<<<<<<< HEAD
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
=======
    private fun nextUsableMirror(currentKey: String): String? {
        val mirrors = getMirrors()
        if (mirrors.size < 2) return null
        return mirrors.firstOrNull { mirrorKey(it) != currentKey && isMirrorHealthy(it) }
>>>>>>> d24a20e (FoxyBook v1.9)
    }

    // ═══════════════════════════════════════════════════════════════
    //  HTTP
    // ═══════════════════════════════════════════════════════════════

    /**
     * GET с автоматическим переключением зеркала. Отменяемый: отмена корутины
     * прерывает запрос, а не оставляет его висеть до readTimeout.
     *
     * Зеркало переключается только на серверные ошибки (5xx), таймауты и 429 —
     * 403/404 означают «здесь такого нет», повторять бессмысленно.
     */
    suspend fun fetchWithMirrorRetry(
        url: String,
        client: OkHttpClient = this.client,
        maxRetries: Int = MAX_MIRROR_RETRIES
    ): String {
        val response = fetchResponse(url, client, maxRetries)
        response.use { return it.body?.string() ?: "" }
    }

    suspend fun fetchResponse(
        url: String,
        client: OkHttpClient = this.client,
        maxRetries: Int = MAX_MIRROR_RETRIES
    ): Response {
        var currentUrl = url
        var lastError = "Неизвестная ошибка"
        val attempts = maxOf(1, maxRetries)
        var attempt = 0

        while (attempt < attempts) {
            attempt++
            val key = mirrorKey(currentUrl)
            try {
                val request = Request.Builder().url(currentUrl).build()
                val response = client.executeCancellable(request)
                if (response.isSuccessful) {
                    markMirrorHealthy(key)
                    return response
                }

                val code = response.code
                response.close()
                lastError = "HTTP $code"
                val worthRotating = code == 408 || code == 429 || code in 500..599
                if (worthRotating) {
                    markMirrorUnhealthy(key)
                    val next = if (attempt < attempts) nextUsableMirror(key) else null
                    if (next != null) {
                        Log.w(TAG, "HTTP $code on $key, rotating to $next (attempt $attempt)")
                        currentUrl = replaceHost(currentUrl, next)
                        continue
                    }
                }
                // 403/404 и прочие «конечные» ответы зеркала не исправляются
                // повтором: отдаём ошибку вызывающему, чтобы он откатился
                // на другой источник данных.
                throw HttpStatusException(code, "$lastError на ${getBaseUrl()}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpStatusException) {
                throw e
            } catch (e: IOException) {
                lastError = e.message ?: "Ошибка сети"
                markMirrorUnhealthy(key)
                val next = if (attempt < attempts) nextUsableMirror(key) else null
                if (next != null) {
                    Log.w(TAG, "Request to $key failed: ${e.message}, rotating to $next")
                    currentUrl = replaceHost(currentUrl, next)
                    continue
                }
                if (attempt < attempts) continue
                throw IOException("$lastError (все зеркала исчерпаны)", e)
            }
        }
        throw IOException(lastError)
    }

    fun hostOf(url: String): String? = url.substringAfter("://", "").substringBefore('/')

    private fun replaceHost(url: String, newBase: String): String {
        val host = hostOf(url) ?: return url
        return url.replaceFirst(host, hostOf(newBase).orEmpty())
    }

    // ═══════════════════════════════════════════════════════════════
    //  Clients
    // ═══════════════════════════════════════════════════════════════

    /** HTTP cache: 20 MB */
    private val httpCache = Cache(
        directory = File(context.cacheDir, "http_cache"),
        maxSize = HTTP_CACHE_SIZE
    )

    /** Shared connection pool — all clients reuse the same TCP connections */
    private val connectionPool = ConnectionPool(
        maxIdleConnections = MAX_IDLE_CONNECTIONS,
        keepAliveDuration = KEEP_ALIVE_DURATION,
        timeUnit = TimeUnit.MINUTES
    )

    private fun baseInterceptor(extraHeaders: Map<String, String> = emptyMap()) = Interceptor { chain ->
        val builder = chain.request().newBuilder()
            .header("User-Agent", USER_AGENT)
            .header("Connection", "keep-alive")
        extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        chain.proceed(builder.build())
    }

    /**
     * Каталоги отдают ETag/Last-Modified, но Cache-Control есть не у всех, а у
     * флибусты он равен 10 минутам. Без явного max-age дисковый кеш OkHttp
     * почти не работает. Заголовок добавляется сетевым интерсептором (только
     * он влияет на то, что попадёт в кеш) и только если сервер не задал
     * собственную политику кеширования.
     */
    private fun feedCacheInterceptor() = feedCacheInterceptorFor(::feedTtlSeconds)

    fun feedTtlSeconds(url: String): Int = feedTtlSecondsFor(url)

    fun createClient(
        connectSeconds: Long = DEFAULT_CONNECT_TIMEOUT,
        readSeconds: Long = DEFAULT_READ_TIMEOUT,
        writeSeconds: Long = DEFAULT_WRITE_TIMEOUT,
        withCache: Boolean = false,
        extraHeaders: Map<String, String> = emptyMap(),
        cacheFeeds: Boolean = false,
        callSeconds: Long = DEFAULT_CALL_TIMEOUT
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .apply { if (withCache) cache(httpCache) }
            .connectionPool(connectionPool)
            .connectTimeout(connectSeconds, TimeUnit.SECONDS)
            .readTimeout(readSeconds, TimeUnit.SECONDS)
            .writeTimeout(writeSeconds, TimeUnit.SECONDS)
            .callTimeout(callSeconds, TimeUnit.SECONDS)
            .addInterceptor(baseInterceptor(extraHeaders))
            .apply { if (withCache && cacheFeeds) addNetworkInterceptor(feedCacheInterceptor()) }
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    val client: OkHttpClient by lazy {
        createClient(
            withCache = true,
            cacheFeeds = true,
            extraHeaders = mapOf(
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Accept-Language" to "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7"
            )
        )
    }

    private val downloadClientLazy: OkHttpClient by lazy { createDownloadClient() }

    /** Один клиент на скачивания вместо клиента на каждый API-класс. */
    val downloadClient: OkHttpClient get() = downloadClientLazy

    fun createDownloadClient(): OkHttpClient {
<<<<<<< HEAD
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
=======
        return createClient(
            connectSeconds = DOWNLOAD_CONNECT_TIMEOUT,
            readSeconds = DOWNLOAD_READ_TIMEOUT,
            writeSeconds = DOWNLOAD_WRITE_TIMEOUT,
            callSeconds = 0
        )
>>>>>>> d24a20e (FoxyBook v1.9)
    }

    companion object {
        /** Размер HTTP-кеша */
        const val HTTP_CACHE_SIZE = 20L * 1024L * 1024L
        /** Таймаут подключения по умолчанию (с) */
        const val DEFAULT_CONNECT_TIMEOUT = 10L
        /** Таймаут чтения по умолчанию (с) */
        const val DEFAULT_READ_TIMEOUT = 30L
        /** Таймаут записи по умолчанию (с) */
        const val DEFAULT_WRITE_TIMEOUT = 30L
        /** Общий таймаут запроса (с), 0 — без ограничения */
        const val DEFAULT_CALL_TIMEOUT = 60L
        /** Таймаут подключения для скачивания (с) */
        const val DOWNLOAD_CONNECT_TIMEOUT = 30L
        /** Таймаут чтения для скачивания (с) */
        const val DOWNLOAD_READ_TIMEOUT = 180L
        /** Таймаут записи для скачивания (с) */
        const val DOWNLOAD_WRITE_TIMEOUT = 60L
        /** Максимальное количество попыток при ошибке зеркала */
        const val MAX_MIRROR_RETRIES = 3
        /** Максимальное количество idle соединений */
        const val MAX_IDLE_CONNECTIONS = 10
        /** Время жизни idle соединения (мин) */
        const val KEEP_ALIVE_DURATION = 5L
        /** Сколько зеркало считается мёртвым после ошибки (мс) */
        const val MIRROR_COOLDOWN_MS = 5L * 60L * 1000L
    }
}