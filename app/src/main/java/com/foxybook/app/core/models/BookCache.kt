package com.foxybook.app.core.models

object BookCache {
    private val cache = linkedMapOf<Int, Book>()

    @Synchronized
    fun put(book: Book) {
        cache[book.id] = book
        if (cache.size > 200) {
            cache.remove(cache.keys.first())
        }
    }

    @Synchronized
    fun get(id: Int): Book? = cache[id]

    @Synchronized
    fun remove(id: Int) {
        cache.remove(id)
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }
}
