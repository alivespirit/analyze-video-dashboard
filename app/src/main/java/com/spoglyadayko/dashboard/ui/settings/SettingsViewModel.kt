package com.spoglyadayko.dashboard.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spoglyadayko.dashboard.data.preferences.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SettingsUiState(
    val serverUrl: String = SettingsStore.DEFAULT_SERVER_URL,
    val pollIntervalSeconds: Int = SettingsStore.DEFAULT_POLL_INTERVAL,
    val notificationsEnabled: Boolean = true,
    val themeMode: String = SettingsStore.THEME_AUTO,
)

class SettingsViewModel(private val store: SettingsStore) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            _uiState.value = SettingsUiState(
                serverUrl = store.serverUrl.first(),
                pollIntervalSeconds = store.pollIntervalSeconds.first(),
                notificationsEnabled = store.notificationsEnabled.first(),
                themeMode = store.themeMode.first(),
            )
        }
    }

    fun setServerUrl(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url)
        viewModelScope.launch { store.setServerUrl(url) }
    }

    fun setPollInterval(seconds: Int) {
        _uiState.value = _uiState.value.copy(pollIntervalSeconds = seconds)
        viewModelScope.launch { store.setPollIntervalSeconds(seconds) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(notificationsEnabled = enabled)
        viewModelScope.launch { store.setNotificationsEnabled(enabled) }
    }

    fun setThemeMode(mode: String) {
        _uiState.value = _uiState.value.copy(themeMode = mode)
        viewModelScope.launch { store.setThemeMode(mode) }
    }
}
