package com.foxybook.app.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "library_books", primaryKeys = ["id", "format"])
data class LibraryBookEntity(
    val id: Int,
    val title: String,
    val author: String,
    val format: String,
    val filePath: String,
    val coverUrl: String = "",
    val downloadDate: Long,
    val isFavorite: Boolean = false,
    val lastReadDate: Long = 0L,
    val readingProgress: Int = 0
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey
    val id: String,
    val bookId: Int,
    val chapterIndex: Int,
    val chapterTitle: String = "",
    val pageIndex: Int = 0,
    val scrollPosition: Int = 0,
    val scrollOffset: Int = 0,
    val textOffset: Int = 0,
    val shortTextPreview: String,
    val createdAt: Long
)

@Entity(tableName = "reading_positions", primaryKeys = ["bookId", "format"])
data class ReadingPositionEntity(
    val bookId: Int,
    val format: String,
    val chapterIndex: Int = 0,
    val pageIndex: Int = 0,
    val scrollPosition: Int = 0,
    val scrollOffset: Int = 0,
    val textOffset: Int = 0,
    val lastUpdated: Long
)

@Entity(tableName = "book_collections")
data class BookCollectionEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val createdAt: Long
)

@Entity(
    tableName = "book_collection_entries",
    primaryKeys = ["bookId", "format", "collectionId"]
)
data class BookCollectionEntryEntity(
    val bookId: Int,
    val format: String,
    val collectionId: String
)
