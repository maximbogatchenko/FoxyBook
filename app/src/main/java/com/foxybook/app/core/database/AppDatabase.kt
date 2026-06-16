package com.foxybook.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LibraryBookEntity::class,
        BookmarkEntity::class,
        ReadingPositionEntity::class,
        BookCollectionEntity::class,
        BookCollectionEntryEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun libraryBookDao(): LibraryBookDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun readingPositionDao(): ReadingPositionDao
    abstract fun bookCollectionDao(): BookCollectionDao
    abstract fun bookCollectionEntryDao(): BookCollectionEntryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No-op: schema unchanged between v1 and v2
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE library_books ADD COLUMN readingProgress INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS book_collection_entries (
                        bookId INTEGER NOT NULL,
                        format TEXT NOT NULL,
                        collectionId TEXT NOT NULL,
                        PRIMARY KEY(bookId, format, collectionId)
                    )
                """)
                // Migrate existing CSV collectionIds from library_books
                db.execSQL("""
                    INSERT OR IGNORE INTO book_collection_entries (bookId, format, collectionId)
                    SELECT library_books.id, library_books.format, book_collections.id
                    FROM library_books
                    JOIN book_collections ON ',' || library_books.collectionIds || ',' LIKE '%,' || book_collections.id || ',%'
                    WHERE library_books.collectionIds IS NOT NULL AND library_books.collectionIds != ''
                """)
                db.execSQL("ALTER TABLE library_books DROP COLUMN collectionIds")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "foxybook.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
