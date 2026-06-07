package com.foxybook.app.core.models

import kotlinx.serialization.Serializable

// ─── Network Models ───

@Serializable
data class Book(
    val id: Int,
    val title: String,
    val author: String,
    val link: String,
    val sendLink: String,
    val coverUrl: String = "",
    val genres: List<String> = emptyList(),
    val sequenceNumber: Int = 0
)

@Serializable
data class BookGenre(
    val id: String,
    val title: String
)

@Serializable
data class BookInfo(
    val id: Int,
    val title: String,
    val author: String,
    val description: String,
    val genres: List<BookGenre>,
    val coverUrl: String = ""
)

enum class BookFormat(val extension: String) {
    EPUB("epub"),
    FB2("fb2"),
    MOBI("mobi")
}

// ─── Series Model ───

@Serializable
data class Series(
    val seriesId: String,
    val seriesTitle: String,
    val seriesUrl: String,
    val bookCount: Int = 0
)

// ─── Library Models ───

@Serializable
data class LibraryBook(
    val id: Int,
    val title: String,
    val author: String,
    val format: String,
    val filePath: String,
    val coverUrl: String = "",
    val downloadDate: Long,
    val isFavorite: Boolean = false,
    val lastReadDate: Long = 0L,
    val collectionIds: List<String> = emptyList()
)

@Serializable
data class BookCollection(
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class ReadingPosition(
    val bookId: Int,
    val format: String,
    val chapterIndex: Int = 0,
    val scrollPosition: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

// ─── Reader Models ───

enum class ReaderMode { HORIZONTAL, VERTICAL }

enum class ReaderTheme { LIGHT, DARK, SYSTEM }

@Serializable
data class ReaderSettings(
    val fontSize: Int = 18,
    val lineHeight: Float = 1.8f,
    val margins: Int = 16,
    val readerMode: String = ReaderMode.VERTICAL.name,
    val readerTheme: String = ReaderTheme.SYSTEM.name
)

@Serializable
data class EpubChapter(
    val title: String,
    val htmlContent: String
)

@Serializable
data class EpubBook(
    val title: String,
    val author: String,
    val chapters: List<EpubChapter>
)

@Serializable
data class Fb2Chapter(
    val title: String,
    val htmlContent: String
)

@Serializable
data class Fb2Book(
    val title: String,
    val author: String,
    val description: String = "",
    val chapters: List<Fb2Chapter> = emptyList()
)

@Serializable
data class MobiChapter(
    val title: String,
    val htmlContent: String
)

@Serializable
data class MobiBook(
    val title: String,
    val author: String,
    val chapters: List<MobiChapter> = emptyList()
)

// ─── Parsed Book (unified) ───

@Serializable
data class ParsedChapter(
    val title: String,
    val htmlContent: String
)

@Serializable
data class ParsedBook(
    val title: String,
    val author: String,
    val chapters: List<ParsedChapter>,
    val format: String
)

// ─── Search ───

enum class SearchMode(val label: String) {
    TITLE("По названию"),
    AUTHOR("По автору"),
    SERIES("По серии")
}

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class BookSuccess(val books: List<Book>) : SearchUiState
    data class SeriesSuccess(val series: List<Series>) : SearchUiState
    data class Error(val message: String) : SearchUiState
    data object Empty : SearchUiState
}

// ─── Book Details ───

sealed interface BookDetailsUiState {
    data object Loading : BookDetailsUiState
    data class Success(val bookInfo: BookInfo) : BookDetailsUiState
    data class Error(val message: String) : BookDetailsUiState
}

enum class DownloadStatus { IDLE, DOWNLOADING, DOWNLOADED, ERROR }

@Serializable
data class DownloadProgress(
    val status: DownloadStatus = DownloadStatus.IDLE,
    val percent: Int = 0,
    val error: String? = null,
    val filePath: String = ""
)

// ─── Library Tabs ───

enum class LibraryTab(val label: String) {
    ALL("Все книги"),
    FAVORITES("Избранное"),
    HISTORY("История"),
    COLLECTIONS("Коллекции")
}

// ─── Search History ───

@Serializable
data class SearchHistoryEntry(
    val query: String,
    val searchMode: String,
    val timestamp: Long,
    val resultCount: Int
)

// ─── Series Details ───

sealed interface SeriesDetailsUiState {
    data object Loading : SeriesDetailsUiState
    data class Success(val series: Series, val books: List<Book>) : SeriesDetailsUiState
    data class Error(val message: String) : SeriesDetailsUiState
}
