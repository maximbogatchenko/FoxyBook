package com.foxybook.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryBookDao {
    @Query("SELECT * FROM library_books ORDER BY downloadDate DESC")
    fun getAllBooks(): Flow<List<LibraryBookEntity>>

    @Query("SELECT * FROM library_books WHERE id = :bookId")
    fun getBooksById(bookId: Int): Flow<List<LibraryBookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: LibraryBookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(books: List<LibraryBookEntity>)

    @Query("DELETE FROM library_books WHERE id = :bookId AND format = :format")
    suspend fun delete(bookId: Int, format: String)

    @Query("UPDATE library_books SET isFavorite = NOT isFavorite WHERE id = :bookId AND format = :format")
    suspend fun toggleFavorite(bookId: Int, format: String)

    @Query("UPDATE library_books SET lastReadDate = :timestamp WHERE id = :bookId AND format = :format")
    suspend fun updateLastReadDate(bookId: Int, format: String, timestamp: Long)

    @Query("UPDATE library_books SET readingProgress = :progress WHERE id = :bookId AND format = :format")
    suspend fun updateReadingProgress(bookId: Int, format: String, progress: Int)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun getBookmarksForBook(bookId: Int): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ReadingPositionDao {
    @Query("SELECT * FROM reading_positions WHERE bookId = :bookId AND format = :format")
    fun getPosition(bookId: Int, format: String): Flow<ReadingPositionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(position: ReadingPositionEntity)
}

@Dao
interface BookCollectionDao {
    @Query("SELECT * FROM book_collections ORDER BY createdAt DESC")
    fun getAllCollections(): Flow<List<BookCollectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(collection: BookCollectionEntity)

    @Query("UPDATE book_collections SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("DELETE FROM book_collections WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface BookCollectionEntryDao {
    @Query("SELECT collectionId FROM book_collection_entries WHERE bookId = :bookId AND format = :format")
    suspend fun getCollectionIdsForBook(bookId: Int, format: String): List<String>

    @Query("SELECT * FROM book_collection_entries")
    fun getAllEntries(): Flow<List<BookCollectionEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: BookCollectionEntryEntity)

    @Query("DELETE FROM book_collection_entries WHERE bookId = :bookId AND format = :format AND collectionId = :collectionId")
    suspend fun delete(bookId: Int, format: String, collectionId: String)

    @Query("DELETE FROM book_collection_entries WHERE collectionId = :collectionId")
    suspend fun deleteByCollectionId(collectionId: String)

    @Query("DELETE FROM book_collection_entries WHERE bookId = :bookId AND format = :format")
    suspend fun deleteByBook(bookId: Int, format: String)
}
