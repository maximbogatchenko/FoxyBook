package com.foxybook.app.core.database

import com.foxybook.app.core.models.BookCollection
import com.foxybook.app.core.models.Bookmark
import com.foxybook.app.core.models.LibraryBook
import com.foxybook.app.core.models.ReadingPosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class BookDataRepository(
    private val db: AppDatabase
) {
    private val libraryBookDao = db.libraryBookDao()
    private val bookmarkDao = db.bookmarkDao()
    private val readingPositionDao = db.readingPositionDao()
    private val bookCollectionDao = db.bookCollectionDao()
    private val bookCollectionEntryDao = db.bookCollectionEntryDao()

    // ─── Library Books ───

    fun getAllBooks(): Flow<List<LibraryBook>> =
        combine(
            libraryBookDao.getAllBooks(),
            bookCollectionEntryDao.getAllEntries()
        ) { books, entries ->
            val entriesByBook = entries.groupBy { it.bookId to it.format }
            books.map { entity ->
                val ids = entriesByBook[entity.id to entity.format]?.map { it.collectionId } ?: emptyList()
                entity.toDomain(ids)
            }
        }

    fun getBooksById(bookId: Int): Flow<List<LibraryBook>> =
        libraryBookDao.getBooksById(bookId).map { entities ->
            entities.map { entity ->
                val ids = bookCollectionEntryDao.getCollectionIdsForBook(entity.id, entity.format)
                entity.toDomain(ids)
            }
        }

    suspend fun addLibraryBook(book: LibraryBook) {
        libraryBookDao.upsert(book.toEntity())
        // Restore collection associations if any
        book.collectionIds.forEach { collectionId ->
            bookCollectionEntryDao.insert(
                BookCollectionEntryEntity(book.id, book.format, collectionId)
            )
        }
    }

    suspend fun removeLibraryBook(bookId: Int, format: String) {
        bookCollectionEntryDao.deleteByBook(bookId, format)
        libraryBookDao.delete(bookId, format)
    }

    suspend fun toggleFavorite(bookId: Int, format: String) =
        libraryBookDao.toggleFavorite(bookId, format)

    suspend fun updateLastReadDate(bookId: Int, format: String) =
        libraryBookDao.updateLastReadDate(bookId, format, System.currentTimeMillis())

    suspend fun updateReadingProgress(bookId: Int, format: String, progress: Int) =
        libraryBookDao.updateReadingProgress(bookId, format, progress)

    // ─── Collections ───

    suspend fun addBookToCollection(bookId: Int, format: String, collectionId: String) {
        bookCollectionEntryDao.insert(
            BookCollectionEntryEntity(bookId, format, collectionId)
        )
    }

    suspend fun removeBookFromCollection(bookId: Int, format: String, collectionId: String) {
        bookCollectionEntryDao.delete(bookId, format, collectionId)
    }

    // ─── Bookmarks ───

    fun getBookmarksForBook(bookId: Int): Flow<List<Bookmark>> =
        bookmarkDao.getBookmarksForBook(bookId).map { entities -> entities.map { it.toDomain() } }

    suspend fun addBookmark(bookmark: Bookmark) =
        bookmarkDao.insert(bookmark.toEntity())

    suspend fun removeBookmark(bookmark: Bookmark) =
        bookmarkDao.delete(bookmark.id)

    // ─── Reading Positions ───

    fun getReadingPosition(bookId: Int, format: String): Flow<ReadingPosition?> =
        readingPositionDao.getPosition(bookId, format).map { it?.toDomain() }

    suspend fun saveReadingPosition(position: ReadingPosition) =
        readingPositionDao.upsert(position.toEntity())

    // ─── Collections Management ───

    fun getAllCollections(): Flow<List<BookCollection>> =
        bookCollectionDao.getAllCollections().map { entities -> entities.map { it.toDomain() } }

    suspend fun createCollection(name: String): String {
        val id = java.util.UUID.randomUUID().toString()
        val collection = BookCollection(
            id = id,
            name = name,
            createdAt = System.currentTimeMillis()
        )
        bookCollectionDao.upsert(collection.toEntity())
        return id
    }

    suspend fun renameCollection(id: String, name: String) =
        bookCollectionDao.rename(id, name)

    suspend fun deleteCollection(id: String) {
        bookCollectionEntryDao.deleteByCollectionId(id)
        bookCollectionDao.delete(id)
    }
}

// ─── Entity ↔ Domain conversions ───

internal fun LibraryBookEntity.toDomain(collectionIds: List<String> = emptyList()) = LibraryBook(
    id = id,
    title = title,
    author = author,
    format = format,
    filePath = filePath,
    coverUrl = coverUrl,
    downloadDate = downloadDate,
    isFavorite = isFavorite,
    lastReadDate = lastReadDate,
    collectionIds = collectionIds,
    readingProgress = readingProgress
)

internal fun LibraryBook.toEntity() = LibraryBookEntity(
    id = id,
    title = title,
    author = author,
    format = format,
    filePath = filePath,
    coverUrl = coverUrl,
    downloadDate = downloadDate,
    isFavorite = isFavorite,
    lastReadDate = lastReadDate,
    readingProgress = readingProgress
)

internal fun BookmarkEntity.toDomain() = Bookmark(
    id = id,
    bookId = bookId,
    chapterIndex = chapterIndex,
    chapterTitle = chapterTitle,
    pageIndex = pageIndex,
    scrollPosition = scrollPosition,
    scrollOffset = scrollOffset,
    textOffset = textOffset,
    shortTextPreview = shortTextPreview,
    createdAt = createdAt
)

internal fun Bookmark.toEntity() = BookmarkEntity(
    id = id,
    bookId = bookId,
    chapterIndex = chapterIndex,
    chapterTitle = chapterTitle,
    pageIndex = pageIndex,
    scrollPosition = scrollPosition,
    scrollOffset = scrollOffset,
    textOffset = textOffset,
    shortTextPreview = shortTextPreview,
    createdAt = createdAt
)

internal fun ReadingPositionEntity.toDomain() = ReadingPosition(
    bookId = bookId,
    format = format,
    chapterIndex = chapterIndex,
    pageIndex = pageIndex,
    scrollPosition = scrollPosition,
    scrollOffset = scrollOffset,
    textOffset = textOffset,
    lastUpdated = lastUpdated
)

internal fun ReadingPosition.toEntity() = ReadingPositionEntity(
    bookId = bookId,
    format = format,
    chapterIndex = chapterIndex,
    pageIndex = pageIndex,
    scrollPosition = scrollPosition,
    scrollOffset = scrollOffset,
    textOffset = textOffset,
    lastUpdated = lastUpdated
)

internal fun BookCollectionEntity.toDomain() = BookCollection(
    id = id,
    name = name,
    createdAt = createdAt
)

internal fun BookCollection.toEntity() = BookCollectionEntity(
    id = id,
    name = name,
    createdAt = createdAt
)
