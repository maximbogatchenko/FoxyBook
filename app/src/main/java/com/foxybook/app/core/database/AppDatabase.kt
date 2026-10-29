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
    version = 7,
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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS library_books_new (
                        id INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        author TEXT NOT NULL,
                        format TEXT NOT NULL,
                        filePath TEXT NOT NULL,
                        coverUrl TEXT NOT NULL DEFAULT '',
                        downloadDate INTEGER NOT NULL,
                        isFavorite INTEGER NOT NULL DEFAULT 0,
                        lastReadDate INTEGER NOT NULL DEFAULT 0,
                        readingProgress INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(id, format)
                    )
                """)
                db.execSQL("INSERT OR IGNORE INTO library_books_new SELECT * FROM library_books")
                db.execSQL("DROP TABLE library_books")
                db.execSQL("ALTER TABLE library_books_new RENAME TO library_books")
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
                // Удаление колонки делаем пересборкой таблицы: ALTER TABLE ... DROP COLUMN
                // требует SQLite 3.35+ (Android 13+), а приложение работает с API 28+.
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS library_books_v3 (
                        id INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        author TEXT NOT NULL,
                        format TEXT NOT NULL,
                        filePath TEXT NOT NULL,
                        coverUrl TEXT NOT NULL DEFAULT '',
                        downloadDate INTEGER NOT NULL,
                        isFavorite INTEGER NOT NULL DEFAULT 0,
                        lastReadDate INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(id, format)
                    )
                """)
                db.execSQL("""
                    INSERT OR IGNORE INTO library_books_v3
                        (id, title, author, format, filePath, coverUrl, downloadDate, isFavorite, lastReadDate)
                    SELECT id, title, author, format, filePath, coverUrl, downloadDate, isFavorite, lastReadDate
                    FROM library_books
                """)
                db.execSQL("DROP TABLE library_books")
                db.execSQL("ALTER TABLE library_books_v3 RENAME TO library_books")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ручная отметка чтения поверх автоматической (прогресс + дата открытия).
                // ALTER TABLE ... ADD COLUMN с DEFAULT поддерживается всеми версиями SQLite.
                db.execSQL("ALTER TABLE library_books ADD COLUMN readState TEXT NOT NULL DEFAULT 'AUTO'")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_library_books_downloadDate ON library_books (downloadDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_bookId ON bookmarks (bookId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_createdAt ON bookmarks (createdAt)")

                // book_collection_entries: индекс по collectionId + внешний ключ с CASCADE.
                // Внешние клюпы в SQLite нельзя добавить ALTER'ом — только пересборкой таблицы.
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS book_collection_entries_new (
                        bookId INTEGER NOT NULL,
                        format TEXT NOT NULL,
                        collectionId TEXT NOT NULL,
                        PRIMARY KEY(bookId, format, collectionId),
                        FOREIGN KEY(collectionId) REFERENCES book_collections(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)
                db.execSQL(
                    "INSERT OR IGNORE INTO book_collection_entries_new (bookId, format, collectionId) " +
                        "SELECT bookId, format, collectionId FROM book_collection_entries"
                )
                db.execSQL("DROP TABLE book_collection_entries")
                db.execSQL("ALTER TABLE book_collection_entries_new RENAME TO book_collection_entries")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_book_collection_entries_collectionId ON book_collection_entries (collectionId)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "foxybook.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
