package com.foxybook.app.core.network

import io.mockk.every
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Политика кеширования фидов. Проверяется без сети: важно, чтобы кеш вообще
 * появлялся (без него каждый запуск приложения качает каталоги заново) и чтобы
 * явный запрет сервера не перебивался.
 */
class FeedCacheTest {

    private val request = Request.Builder().url("https://coollib.net/opds/new/0/new").build()

    private fun chainReturning(headers: Map<String, String>, code: Int = 200): Interceptor.Chain {
        val chain = mockk<Interceptor.Chain>(relaxed = true)
        every { chain.request() } returns request
        every { chain.proceed(any()) } returns Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .body("".toResponseBody(null))
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        return chain
    }

    @Test
    fun `response without cache headers gets a max-age`() {
        val interceptor = feedCacheInterceptorFor(::feedTtlSecondsFor)
        val response = interceptor.intercept(chainReturning(emptyMap()))

        assertEquals("public, max-age=600", response.header("Cache-Control"))
    }

    @Test
    fun `explicit no-store from the catalog is not overridden`() {
        // Fantasy-worlds отдаёт no-cache, no-store: уважаем решение сервера,
        // иначе мы бы кешировали то, что он запретил кешировать.
        val interceptor = feedCacheInterceptorFor(::feedTtlSecondsFor)
        val response = interceptor.intercept(
            chainReturning(mapOf("Cache-Control" to "no-cache, no-store, must-revalidate"))
        )

        assertEquals("no-cache, no-store, must-revalidate", response.header("Cache-Control"))
    }

    @Test
    fun `existing max-age from the catalog is kept`() {
        val interceptor = feedCacheInterceptorFor(::feedTtlSecondsFor)
        val response = interceptor.intercept(
            chainReturning(mapOf("Cache-Control" to "public, max-age=600", "Vary" to "Cookie"))
        )

        assertEquals("public, max-age=600", response.header("Cache-Control"))
    }

    @Test
    fun `urls outside the catalog are not cached`() {
        val imageChain = mockk<Interceptor.Chain>(relaxed = true)
        val imageRequest = Request.Builder().url("https://flibusta.site/i/50/891750/cover.jpg").build()
        every { imageChain.request() } returns imageRequest
        every { imageChain.proceed(any()) } returns Response.Builder()
            .request(imageRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("".toResponseBody(null))
            .build()

        val response = feedCacheInterceptorFor(::feedTtlSecondsFor).intercept(imageChain)

        assertNull("covers must not be given a max-age", response.header("Cache-Control"))
    }

    @Test
    fun `error responses are passed through untouched`() {
        val interceptor = feedCacheInterceptorFor(::feedTtlSecondsFor)
        val response = interceptor.intercept(chainReturning(emptyMap(), code = 404))

        assertEquals(404, response.code)
        assertNull(response.header("Cache-Control"))
    }

    @Test
    fun `ttls follow how fast each feed changes`() {
        assertEquals(600, feedTtlSecondsFor("/opds/new/0/new"))
        assertEquals(600, feedTtlSecondsFor("/opds/last/0"))
        assertEquals(120, feedTtlSecondsFor("/opds/search?searchType=books&searchTerm=x"))
        assertEquals(120, feedTtlSecondsFor("/opdssearch?q=x"))
        assertEquals(6 * 3600, feedTtlSecondsFor("/opds/genres"))
        assertEquals(6 * 3600, feedTtlSecondsFor("/opds/sequencesindex/%D0%A1"))
        assertEquals(0, feedTtlSecondsFor("/i/50/891750/cover.jpg"))
        assertEquals(0, feedTtlSecondsFor("/b/891750"))
    }

    @Test
    fun `search feed is matched before the generic opds rule`() {
        // /opds/search содержит в себе "/opds" — порядок проверок важен,
        // иначе поиск кешировался бы на 10 минут вместо 2.
        assertEquals(120, feedTtlSecondsFor("/opds/search?searchType=books&searchTerm=abc"))
    }
}
