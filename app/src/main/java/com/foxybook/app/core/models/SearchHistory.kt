package com.foxybook.app.core.models

/**
 * История поиска: чистая логика без Android, её покрывают юнит-тесты.
 *
 * Порядок важен — первым идёт самый свежий запрос. Повторный запрос не
 * дублируется, а переносится наверх (как в браузере).
 */
object SearchHistory {

    /** Сколько запросов храним. Больше не нужно — список быстро забивается шумом. */
    const val LIMIT = 12

    /** Убирает пустые, склеивает повторы (без учёта регистра) и обрезает по лимиту. */
    fun sanitize(queries: List<String>, limit: Int = LIMIT): List<String> =
        queries.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .take(limit)
            .toList()

    /** Добавляет запрос в начало истории. Пустой запрос игнорируется. */
    fun add(current: List<String>, query: String, limit: Int = LIMIT): List<String> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return sanitize(current, limit)
        return sanitize(listOf(trimmed) + current, limit)
    }
}
