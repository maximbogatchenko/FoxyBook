package com.foxybook.app.features.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foxybook.app.core.datastore.DataStoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val themeMode: String = "system",
    val defaultFormat: String = "epub",
    val downloadDirectory: String? = null
)

class SettingsViewModel(
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
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
}
