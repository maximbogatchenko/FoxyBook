package com.foxybook.app.features.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxybook.app.R
import com.foxybook.app.core.database.BookDataRepository
import com.foxybook.app.core.datastore.DataStoreManager
import com.foxybook.app.core.models.BookSource
import com.foxybook.app.core.models.StartScreen
import com.foxybook.app.core.network.OkHttpClientProvider
import com.foxybook.app.core.updater.UpdateChecker
import com.foxybook.app.core.updater.UpdateInfo
import com.foxybook.app.core.utils.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data object Downloading : UpdateState()
    data class Downloaded(val apkUri: Uri) : UpdateState()
    data class Error(val message: String) : UpdateState()
    data object NoUpdate : UpdateState()
}

data class SettingsState(
    val themeMode: String = "system",
    val defaultFormat: String = "epub",
    val downloadDirectory: String? = null,
    val currentVersion: String = "",
    val updateState: UpdateState = UpdateState.Idle,
    val downloadProgress: Float = 0f,
    val bookSource: BookSource = BookSource.FLIBUSTA,
    val language: String = "ru",
    val startScreen: String = StartScreen.NEW_BOOKS
)

class SettingsViewModel(
    private val application: Application,
    private val dataStoreManager: DataStoreManager,
    private val updateChecker: UpdateChecker,
    private val networkProvider: OkHttpClientProvider,
    private val bookDataRepository: BookDataRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        _state.update { it.copy(currentVersion = updateChecker.getCurrentVersion()) }
        viewModelScope.launch {
            dataStoreManager.themeMode.collect { mode ->
                _state.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            dataStoreManager.defaultFormat.collect { format ->
                _state.update { it.copy(defaultFormat = format) }
            }
        }
        viewModelScope.launch {
            dataStoreManager.downloadDirectory.collect { dir ->
                _state.update { it.copy(downloadDirectory = dir) }
            }
        }
        viewModelScope.launch {
            dataStoreManager.bookSource.collect { source ->
                _state.update { it.copy(bookSource = source) }
            }
        }
        viewModelScope.launch {
            dataStoreManager.appLanguage.collect { lang ->
                _state.update { it.copy(language = lang) }
            }
        }
        viewModelScope.launch {
            dataStoreManager.startScreen.collect { screen ->
                _state.update { it.copy(startScreen = screen) }
            }
        }
    }

    fun setBookSource(source: BookSource) {
        viewModelScope.launch {
            com.foxybook.app.core.models.BookCache.clear()
            networkProvider.switchSource(source)
            dataStoreManager.setBookSource(source)
        }
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            _state.update { it.copy(updateState = UpdateState.Checking) }
            try {
                val info = updateChecker.checkForUpdate(updateChecker.getCurrentVersion())
                if (info != null) {
                    _state.update { it.copy(updateState = UpdateState.Available(info)) }
                } else {
                    _state.update { it.copy(updateState = UpdateState.NoUpdate) }
                    delay(3000)
                    _state.update { it.copy(updateState = UpdateState.Idle) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
                _state.update { it.copy(updateState = UpdateState.Error(e.message ?: application.getString(R.string.settings_unknown_error))) }
            }
        }
    }

    fun downloadUpdate() {
        val info = (_state.value.updateState as? UpdateState.Available)?.info ?: return
        viewModelScope.launch {
            _state.update { it.copy(updateState = UpdateState.Downloading, downloadProgress = 0f) }
            try {
                val apkUri = updateChecker.withExpectedSize(info.size).downloadApk(info.downloadUrl) { progress ->
                    _state.update { it.copy(downloadProgress = progress) }
                }
                _state.update { it.copy(updateState = UpdateState.Downloaded(apkUri), downloadProgress = 1f) }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                _state.update { it.copy(updateState = UpdateState.Error(e.message ?: application.getString(R.string.settings_download_error))) }
            }
        }
    }

    fun installUpdate(context: Context) {
        val apkUri = (_state.value.updateState as? UpdateState.Downloaded)?.apkUri ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun dismissUpdateResult() {
        _state.update { it.copy(updateState = UpdateState.Idle) }
    }

    companion object {
        private const val TAG = "SettingsVM"
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            dataStoreManager.setThemeMode(mode)
        }
    }

    fun setDefaultFormat(format: String) {
        viewModelScope.launch {
            dataStoreManager.setDefaultFormat(format)
        }
    }

    fun setDownloadDirectory(uri: String?) {
        viewModelScope.launch {
            dataStoreManager.setDownloadDirectory(uri)
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            dataStoreManager.setAppLanguage(lang)
        }
    }

    fun setStartScreen(route: String) {
        viewModelScope.launch {
            dataStoreManager.setStartScreen(route)
        }
    }

    /**
     * Очищает кэши. Файлы книг не удаляются: из imported_books убираются только
     * файлы, на которые больше нет записи в библиотеке.
     */
    fun clearCaches(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                val known = runCatching { bookDataRepository.getAllFilePaths().toSet() }.getOrDefault(emptySet())
                val msg = StorageHelper.clearTempCaches(application)
                if (known.isEmpty()) msg
                else {
                    val (count, bytes) = StorageHelper.cleanupOrphanedImportedFiles(application, known)
                    val sizeStr = when {
                        bytes >= 1_000_000 -> "${"%.1f".format(bytes / 1_000_000f)} MB"
                        bytes >= 1_000 -> "${"%.0f".format(bytes / 1_000_000f)} KB"
                        else -> "$bytes B"
                    }
                    if (count > 0) "$msg + осиротевших файлов: $count ($sizeStr)" else msg
                }
            }
            onResult(message)
        }
    }
}
