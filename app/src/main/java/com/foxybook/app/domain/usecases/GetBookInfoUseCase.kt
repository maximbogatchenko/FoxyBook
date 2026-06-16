package com.foxybook.app.domain.usecases

import com.foxybook.app.core.models.BookInfo
import com.foxybook.app.domain.repository.BookRepository

class GetBookInfoUseCase(private val repository: BookRepository) {

    private val cache = mutableMapOf<Int, BookInfo>()
    private val pendingRequests = mutableMapOf<Int, BookInfo?>()

    suspend operator fun invoke(id: Int): BookInfo? {
        synchronized(cache) {
            cache[id]?.let { return it }
        }

        // Avoid duplicate concurrent requests for the same book
        synchronized(pendingRequests) {
            pendingRequests[id]?.let { return it }
            pendingRequests[id] = null // placeholder
        }

        val info = repository.getBookInfo(id)

        synchronized(cache) {
            if (info != null) cache[id] = info
            // Keep cache bounded at 300 entries
            if (cache.size > 300) {
                val keysToRemove = cache.keys.take(100)
                keysToRemove.forEach { cache.remove(it) }
            }
        }
        synchronized(pendingRequests) {
            pendingRequests.remove(id)
        }

        return info
    }

    /** Remove cached info for a specific book (e.g., after a refresh) */
    fun invalidate(id: Int) {
        synchronized(cache) { cache.remove(id) }
    }
}
