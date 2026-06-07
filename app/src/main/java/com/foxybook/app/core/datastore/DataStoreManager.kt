package com.foxybook.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.foxybook.app.core.models.BookCollection
import com.foxybook.app.core.models.LibraryBook
import com.foxybook.app.core.models.ReaderSettings
import com.foxybook.app.core.models.ReadingPosition
import com.foxybook.app.core.models.SearchHistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "foxybook_settings")

class DataStoreManager(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        private val LIBRARY_BOOKS_KEY = stringPreferencesKey("library_books")
        private val COLLECTIONS_KEY = stringPreferencesKey("collections")
        private val READING_POSITIONS_KEY = stringPreferencesKey("reading_positions")
        private val READER_SETTINGS_KEY = stringPreferencesKey("reader_settings_v2")
        private val THEME_KEY = stringPreferencesKey("theme_mode")
        private val DEFAULT_FORMAT_KEY = stringPreferencesKey("default_format")
        private val SEARCH_HISTORY_KEY = stringPreferencesKey("search_history")
    }

    // ─── Library Books ───

    val libraryBooks: Flow<List<LibraryBook>> = context.dataStore.data.map { prefs ->
        decodeList(prefs[LIBRARY_BOOKS_KEY])
    }

    suspend fun addLibraryBook(book: LibraryBook) {
        context.dataStore.edit { prefs ->
            val books = decodeList<LibraryBook>(prefs[LIBRARY_BOOKS_KEY])
            if (books.none { it.id == book.id && it.format == book.format }) {
                prefs[LIBRARY_BOOKS_KEY] = json.encodeToString(books + book)
            }
        }
    }

    suspend fun removeLibraryBook(bookId: Int, format: String) {
        context.dataStore.edit { prefs ->
            val books = decodeList<LibraryBook>(prefs[LIBRARY_BOOKS_KEY])
            prefs[LIBRARY_BOOKS_KEY] = json.encodeToString(
                books.filterNot { it.id == bookId && it.format == format }
            )
        }
    }

    suspend fun updateLibraryBook(bookId: Int, format: String, transform: (LibraryBook) -> LibraryBook) {
        context.dataStore.edit { prefs ->
            val books = decodeList<LibraryBook>(prefs[LIBRARY_BOOKS_KEY])
            prefs[LIBRARY_BOOKS_KEY] = json.encodeToString(
                books.map { if (it.id == bookId && it.format == format) transform(it) else it }
            )
        }
    }

    suspend fun toggleFavorite(bookId: Int, format: String) {
        updateLibraryBook(bookId, format) { it.copy(isFavorite = !it.isFavorite) }
    }

    suspend fun updateLastReadDate(bookId: Int, format: String) {
        updateLibraryBook(bookId, format) { it.copy(lastReadDate = System.currentTimeMillis()) }
    }

    suspend fun addBookToCollection(bookId: Int, format: String, collectionId: String) {
        updateLibraryBook(bookId, format) {
            it.copy(collectionIds = (it.collectionIds + collectionId).distinct())
        }
    }

    suspend fun removeBookFromCollection(bookId: Int, format: String, collectionId: String) {
        updateLibraryBook(bookId, format) {
            it.copy(collectionIds = it.collectionIds.filterNot { id -> id == collectionId })
        }
    }

    // ─── Collections ───

    val collections: Flow<List<BookCollection>> = context.dataStore.data.map { prefs ->
        decodeList(prefs[COLLECTIONS_KEY])
    }

    suspend fun createCollection(name: String) {
        context.dataStore.edit { prefs ->
            val list = decodeList<BookCollection>(prefs[COLLECTIONS_KEY])
            val id = java.util.UUID.randomUUID().toString()
            prefs[COLLECTIONS_KEY] = json.encodeToString(list + BookCollection(id, name))
        }
    }

    suspend fun renameCollection(collectionId: String, newName: String) {
        context.dataStore.edit { prefs ->
            val list = decodeList<BookCollection>(prefs[COLLECTIONS_KEY])
            prefs[COLLECTIONS_KEY] = json.encodeToString(
                list.map { if (it.id == collectionId) it.copy(name = newName) else it }
            )
        }
    }

    suspend fun deleteCollection(collectionId: String) {
        context.dataStore.edit { prefs ->
            val list = decodeList<BookCollection>(prefs[COLLECTIONS_KEY])
            prefs[COLLECTIONS_KEY] = json.encodeToString(list.filterNot { it.id == collectionId })
        }
        context.dataStore.edit { prefs ->
            val books = decodeList<LibraryBook>(prefs[LIBRARY_BOOKS_KEY])
            prefs[LIBRARY_BOOKS_KEY] = json.encodeToString(
                books.map { it.copy(collectionIds = it.collectionIds.filterNot { cid -> cid == collectionId }) }
            )
        }
    }

    // ─── Reading Positions ───

    suspend fun saveReadingPosition(position: ReadingPosition) {
        context.dataStore.edit { prefs ->
            val list = decodeList<ReadingPosition>(prefs[READING_POSITIONS_KEY])
            val updated = list.filterNot {
                it.bookId == position.bookId && it.format == position.format
            } + position
            prefs[READING_POSITIONS_KEY] = json.encodeToString(updated)
        }
    }

    fun readingPositionForBook(bookId: Int, format: String): Flow<ReadingPosition?> =
        context.dataStore.data.map { prefs ->
            decodeList<ReadingPosition>(prefs[READING_POSITIONS_KEY])
                .firstOrNull { it.bookId == bookId && it.format == format }
        }

    // ─── Reader Settings ───

    val readerSettings: Flow<ReaderSettings> = context.dataStore.data.map { prefs ->
        prefs[READER_SETTINGS_KEY]?.let {
            try { json.decodeFromString<ReaderSettings>(it) } catch (_: Exception) { ReaderSettings() }
        } ?: ReaderSettings()
    }

    suspend fun saveReaderSettings(settings: ReaderSettings) {
        context.dataStore.edit { prefs ->
            prefs[READER_SETTINGS_KEY] = json.encodeToString(settings)
        }
    }

    // ─── Theme & Format ───

    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[THEME_KEY] ?: "system"
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[THEME_KEY] = mode }
    }

    val defaultFormat: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DEFAULT_FORMAT_KEY] ?: "epub"
    }

    suspend fun setDefaultFormat(format: String) {
        context.dataStore.edit { prefs -> prefs[DEFAULT_FORMAT_KEY] = format }
    }

    // ─── Search History ───

    val searchHistory: Flow<List<SearchHistoryEntry>> = context.dataStore.data.map { prefs ->
        decodeList(prefs[SEARCH_HISTORY_KEY])
    }

    suspend fun addSearchHistory(query: String, searchMode: String, resultCount: Int) {
        context.dataStore.edit { prefs ->
            val history = decodeList<SearchHistoryEntry>(prefs[SEARCH_HISTORY_KEY])
            val updated = (listOf(SearchHistoryEntry(query, searchMode, System.currentTimeMillis(), resultCount))
                + history.filterNot { it.query == query && it.searchMode == searchMode })
                .take(50)
            prefs[SEARCH_HISTORY_KEY] = json.encodeToString(updated)
        }
    }

    suspend fun clearSearchHistory() {
        context.dataStore.edit { prefs -> prefs[SEARCH_HISTORY_KEY] = "[]" }
    }

    // ─── Helper ───

    private inline fun <reified T> decodeList(jsonString: String?): List<T> {
        return try {
            jsonString?.let { json.decodeFromString<List<T>>(it) } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
