package com.foxybook.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.foxybook.app.core.models.BookSource
import com.foxybook.app.core.models.ReaderSettings
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

    // ─── New Books View Mode ───

    val newBooksViewMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[NEW_BOOKS_VIEW_MODE_KEY] ?: "LIST"
    }

    suspend fun setNewBooksViewMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[NEW_BOOKS_VIEW_MODE_KEY] = mode }
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
}
