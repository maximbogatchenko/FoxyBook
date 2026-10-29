package com.foxybook.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.foxybook.app.core.models.BookSource
import com.foxybook.app.core.models.ReaderSettings
import com.foxybook.app.core.models.LibrarySortSpec
import com.foxybook.app.core.models.SearchHistory
import com.foxybook.app.core.models.StartScreen
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
        private val READER_SETTINGS_KEY = stringPreferencesKey("reader_settings_v2")
        private val THEME_KEY = stringPreferencesKey("theme_mode")
        private val DEFAULT_FORMAT_KEY = stringPreferencesKey("default_format")
        private val DOWNLOAD_DIR_KEY = stringPreferencesKey("download_directory")
        private val VIEW_MODE_KEY = stringPreferencesKey("library_view_mode")
        private val NEW_BOOKS_VIEW_MODE_KEY = stringPreferencesKey("new_books_view_mode")
        private val BOOK_SOURCE_KEY = stringPreferencesKey("book_source")
        private val LANGUAGE_KEY = stringPreferencesKey("app_language")
        private val START_SCREEN_KEY = stringPreferencesKey("start_screen")
        private val LIB_SORT_KEY = stringPreferencesKey("library_sort_spec")
        private val SEARCH_HISTORY_KEY = stringPreferencesKey("search_history")
    }

    // ─── Book Source ───

    val bookSource: Flow<BookSource> = context.dataStore.data.map { prefs ->
        val name = prefs[BOOK_SOURCE_KEY] ?: BookSource.FLIBUSTA.name
        try { BookSource.valueOf(name) } catch (_: Exception) { BookSource.FLIBUSTA }
    }

    suspend fun setBookSource(source: BookSource) {
        context.dataStore.edit { prefs -> prefs[BOOK_SOURCE_KEY] = source.name }
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

    val downloadDirectory: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[DOWNLOAD_DIR_KEY]
    }

    // ─── Library View Mode ───

    val libraryViewMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[VIEW_MODE_KEY] ?: "LIST"
    }

    suspend fun setLibraryViewMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[VIEW_MODE_KEY] = mode }
    }

    // ─── Library Sort ───

    val librarySortSpec: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[LIB_SORT_KEY] ?: LibrarySortSpec.DEFAULT.encode()
    }

    suspend fun setLibrarySortSpec(spec: LibrarySortSpec) {
        context.dataStore.edit { prefs -> prefs[LIB_SORT_KEY] = spec.encode() }
    }

    // ─── New Books View Mode ───

    val newBooksViewMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[NEW_BOOKS_VIEW_MODE_KEY] ?: "LIST"
    }

    suspend fun setNewBooksViewMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[NEW_BOOKS_VIEW_MODE_KEY] = mode }
    }

    // ─── Search History ───
    //
    // Хранится JSON-массивом, а не множеством строк: порядок важен —
    // первым идёт самый свежий запрос.

    val searchHistory: Flow<List<String>> = context.dataStore.data.map { prefs ->
        decodeHistory(prefs[SEARCH_HISTORY_KEY])
    }

    /**
     * Добавляет запрос в начало истории. Повтор (в т.ч. другого регистра)
     * не дублируется — он просто переносится наверх, как в браузере.
     */
    suspend fun addSearchHistoryItem(query: String) {
        if (query.isBlank()) return
        context.dataStore.edit { prefs ->
            val updated = SearchHistory.add(decodeHistory(prefs[SEARCH_HISTORY_KEY]), query)
            prefs[SEARCH_HISTORY_KEY] = json.encodeToString(updated)
        }
    }

    suspend fun clearSearchHistory() {
        context.dataStore.edit { prefs -> prefs.remove(SEARCH_HISTORY_KEY) }
    }

    private fun decodeHistory(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            SearchHistory.sanitize(json.decodeFromString<List<String>>(raw))
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun setDownloadDirectory(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) {
                prefs.remove(DOWNLOAD_DIR_KEY)
            } else {
                prefs[DOWNLOAD_DIR_KEY] = uri
            }
        }
    }

    // ─── Language ───

    val appLanguage: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[LANGUAGE_KEY] ?: "ru"
    }

    suspend fun setAppLanguage(lang: String) {
        context.dataStore.edit { prefs -> prefs[LANGUAGE_KEY] = lang }
    }

    // ─── Start Screen ───

    val startScreen: Flow<String> = context.dataStore.data.map { prefs ->
        StartScreen.normalize(prefs[START_SCREEN_KEY])
    }

    suspend fun setStartScreen(route: String) {
        context.dataStore.edit { prefs ->
            prefs[START_SCREEN_KEY] = StartScreen.normalize(route)
        }
    }
}
