package com.foxybook.app.navigation

object Routes {
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val BOOK_DETAILS = "book_details/{bookId}?title={title}&author={author}&cover={cover}"
    const val SERIES_DETAILS = "series_details/{seriesId}/{seriesTitle}"
    const val READER = "reader/{bookId}/{bookFormat}/{filePath}"

    fun bookDetails(bookId: Int, title: String? = null, author: String? = null, cover: String? = null): String {
        val base = "book_details/$bookId"
        val params = mutableListOf<String>()
        if (title != null) params.add("title=${java.net.URLEncoder.encode(title, "UTF-8")}")
        if (author != null) params.add("author=${java.net.URLEncoder.encode(author, "UTF-8")}")
        if (cover != null) params.add("cover=${java.net.URLEncoder.encode(cover, "UTF-8")}")
        return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
    }
    fun seriesDetails(seriesId: String, seriesTitle: String) =
        "series_details/${java.net.URLEncoder.encode(seriesId, "UTF-8")}/${java.net.URLEncoder.encode(seriesTitle, "UTF-8")}"
    fun reader(bookId: Int, bookFormat: String, filePath: String) =
        "reader/$bookId/$bookFormat/${java.net.URLEncoder.encode(filePath, "UTF-8")}"
}
