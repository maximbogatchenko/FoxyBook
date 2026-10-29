package com.foxybook.app.core.utils

/**
 * URL для логов: без query-строки, где передаются поисковые запросы и имена
 * авторов. Логи не должны содержать пользовательский ввод.
 */
internal fun safeLogUrl(url: String?): String {
    if (url.isNullOrBlank()) return ""
    val withoutQuery = url.substringBefore('?')
    return if (withoutQuery.length > 96) withoutQuery.take(96) + "…" else withoutQuery
}