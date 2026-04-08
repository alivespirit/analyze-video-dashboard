package com.spoglyadayko.dashboard.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    companion object {
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val POLL_INTERVAL_SECONDS = intPreferencesKey("poll_interval_seconds")
        private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val LAST_EVENT_TS = stringPreferencesKey("last_event_ts")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val EXCLUDED_STATUSES = stringSetPreferencesKey("excluded_statuses")

        const val DEFAULT_SERVER_URL = "http://192.168.1.33:8192"
        const val DEFAULT_POLL_INTERVAL = 30
        const val THEME_AUTO = "auto"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
    }

    val serverUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SERVER_URL] ?: DEFAULT_SERVER_URL
    }

    val pollIntervalSeconds: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[POLL_INTERVAL_SECONDS] ?: DEFAULT_POLL_INTERVAL
    }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[NOTIFICATIONS_ENABLED] ?: true
    }

    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[THEME_MODE] ?: THEME_AUTO
    }

    val excludedStatuses: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[EXCLUDED_STATUSES] ?: emptySet()
    }

    val lastEventTs: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[LAST_EVENT_TS]
    }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { prefs -> prefs[SERVER_URL] = url }
    }

    suspend fun setPollIntervalSeconds(seconds: Int) {
        context.dataStore.edit { prefs -> prefs[POLL_INTERVAL_SECONDS] = seconds }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[THEME_MODE] = mode }
    }

    suspend fun setExcludedStatuses(statuses: Set<String>) {
        context.dataStore.edit { prefs -> prefs[EXCLUDED_STATUSES] = statuses }
    }

    suspend fun setLastEventTs(ts: String) {
        context.dataStore.edit { prefs -> prefs[LAST_EVENT_TS] = ts }
    }
}
