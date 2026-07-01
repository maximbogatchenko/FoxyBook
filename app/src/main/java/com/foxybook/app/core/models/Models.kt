package com.foxybook.app.core.models

import kotlinx.serialization.Serializable

// ─── Pagination ───

data class NewBooksPage(
    val books: List<Book>,
    val nextPageUrl: String?
)

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
    val sequenceNumber: Int = 0,
    val description: String = "",
    val formats: List<String> = emptyList()
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
    val coverUrl: String = "",
    val availableFormats: List<String> = emptyList()
)

enum class BookFormat(val extension: String, val mimeType: String) {
    EPUB("epub", "application/epub+zip"),
    FB2("fb2", "application/x-fictionbook+xml"),
    MOBI("mobi", "application/x-mobipocket-ebook"),
    TXT("txt", "text/plain"),
    PDF("pdf", "application/pdf");

    fun isNativelySupported(): Boolean = this in listOf(EPUB, FB2, TXT)

    companion object {
        fun fromExtension(ext: String?): BookFormat? {
            val normalized = ext?.lowercase() ?: return null
            return entries.find { it.extension == normalized || normalized.contains(it.extension) }
        }
    }
}

// ─── Series Model ───

@Serializable
data class Series(
    val seriesId: String,
    val seriesTitle: String,
    val seriesUrl: String,
    val bookCount: Int = 0,
    val coverUrl: String = "",
    val authorId: String = ""
)

// ─── Author Model ───

@Serializable
data class Author(
    val authorId: String,
    val name: String,
    val bookCount: Int = 0,
    val portraitUrl: String = ""
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
    val collectionIds: List<String> = emptyList(),
    val readingProgress: Int = 0
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
    val pageIndex: Int = 0,
    val scrollPosition: Int = 0,
    val scrollOffset: Int = 0,
    val textOffset: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Serializable
data class Bookmark(
    val id: String = java.util.UUID.randomUUID().toString(),
    val bookId: Int,
    val chapterIndex: Int,
    val chapterTitle: String = "",
    val pageIndex: Int = 0,
    val scrollPosition: Int = 0,
    val scrollOffset: Int = 0,
    val textOffset: Int = 0,
    val shortTextPreview: String,
    val createdAt: Long = System.currentTimeMillis()
)

// ─── Reader Models ───

enum class ReaderMode { HORIZONTAL, VERTICAL }

enum class ReaderTheme { LIGHT, DARK, SYSTEM, AMOLED }

@Serializable
data class ReaderSettings(
    val fontSize: Int = 18,
    val lineHeight: Float = 1.8f,
    val margins: Int = 16,
    val readerMode: String = ReaderMode.HORIZONTAL.name,
    val readerTheme: String = ReaderTheme.SYSTEM.name,
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsVoice: String? = null,
    val ttsLanguage: String? = null,
    val lastTtsChapter: Int = -1,
    val lastTtsBlockIndex: Int = -1,
    val brightness: Float = -1f, // -1 = system default, 0.0..1.0 = custom
    val ttsEngine: String? = null,
    val showProgressAsPercentage: Boolean = false
)

@Serializable
data class EpubChapter(
    val title: String,
    val htmlContent: String,
    val href: String = ""
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
    val htmlContent: String,
    val sectionId: Int = -1
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
    val htmlContent: String = "",
    val contentId: String = "" // Can be href (EPUB) or section index (FB2)
)

@Serializable
data class ParsedBook(
    val title: String,
    val author: String,
    val chapters: List<ParsedChapter>,
    val format: String,
    val filePath: String = ""
)

// ─── Book Source ───

enum class BookSource(val label: String) {
    FLIBUSTA("Flibusta"),
    COOLLIB("CoolLib"),
    FANTASY_WORLDS("Fantasy-worlds")
}

// ─── Search ───

data class SearchPage<T>(
    val items: List<T>,
    val nextPageUrl: String? = null
)

enum class SearchTab(val label: String) {
    ALL("Все"),
    BOOKS("Книги"),
    AUTHORS("Авторы"),
    SERIES("Серии")
}

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class BookSuccess(val books: List<Book>) : SearchUiState
    data class AuthorSuccess(val authors: List<Author>) : SearchUiState
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

// ─── Series Details ───

sealed interface SeriesDetailsUiState {
    data object Loading : SeriesDetailsUiState
    data class Success(val series: Series, val books: List<Book>) : SeriesDetailsUiState
    data class Error(val message: String) : SeriesDetailsUiState
}
