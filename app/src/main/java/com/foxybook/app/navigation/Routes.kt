package com.foxybook.app.navigation

object Routes {
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val BOOK_DETAILS = "book_details/{bookId}"
    const val SERIES_DETAILS = "series_details/{seriesId}/{seriesTitle}"
    const val READER = "reader/{bookId}/{bookFormat}/{filePath}"

    fun bookDetails(bookId: Int) = "book_details/$bookId"
    fun seriesDetails(seriesId: String, seriesTitle: String) =
        "series_details/${java.net.URLEncoder.encode(seriesId, "UTF-8")}/${java.net.URLEncoder.encode(seriesTitle, "UTF-8")}"
    fun reader(bookId: Int, bookFormat: String, filePath: String) =
        "reader/$bookId/$bookFormat/${java.net.URLEncoder.encode(filePath, "UTF-8")}"
}
