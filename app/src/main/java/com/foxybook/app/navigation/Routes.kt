package com.foxybook.app.navigation

object Routes {
    const val SPLASH = "splash"
    const val NEW_BOOKS = "new_books"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val BOOK_DETAILS = "book_details/{bookId}"
    const val AUTHOR_BOOKS = "author_books/{authorId}/{authorName}"
    const val SERIES_DETAILS = "series_details/{seriesId}/{seriesTitle}/{authorId}"
    const val READER = "reader/{bookId}/{bookFormat}/{filePath}"

    fun bookDetails(bookId: Int): String = "book_details/$bookId"
    fun seriesDetails(seriesId: String, seriesTitle: String, authorId: String = "") =
        "series_details/${java.net.URLEncoder.encode(seriesId, "UTF-8")}/${java.net.URLEncoder.encode(seriesTitle, "UTF-8")}/${java.net.URLEncoder.encode(authorId, "UTF-8")}"
    fun authorBooks(authorId: String, authorName: String) =
        "author_books/${java.net.URLEncoder.encode(authorId, "UTF-8")}/${java.net.URLEncoder.encode(authorName, "UTF-8")}"
    fun reader(bookId: Int, bookFormat: String, filePath: String) =
        "reader/$bookId/$bookFormat/${java.net.URLEncoder.encode(filePath, "UTF-8")}"
}
