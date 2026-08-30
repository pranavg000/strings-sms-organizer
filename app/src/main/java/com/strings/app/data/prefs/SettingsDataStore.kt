package com.strings.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "strings_settings")

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromStored(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

class SettingsDataStore(private val context: Context) {
    private val firstRunCompletedKey = booleanPreferencesKey("first_run_completed")
    private val lastSyncTimestampKey = longPreferencesKey("last_sync_timestamp")
    private val inboxTagIdKey = longPreferencesKey("inbox_tag_id")
    private val otpTagIdKey = longPreferencesKey("otp_tag_id")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val appLockEnabledKey = booleanPreferencesKey("app_lock_enabled")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        ThemeMode.fromStored(preferences[themeModeKey])
    }

    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[appLockEnabledKey] ?: false
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences -> preferences[themeModeKey] = mode.name }
    }

    suspend fun setAppLockEnabled(value: Boolean) {
        context.dataStore.edit { preferences -> preferences[appLockEnabledKey] = value }
    }

    suspend fun isFirstRunCompleted(): Boolean {
        return context.dataStore.data.first()[firstRunCompletedKey] ?: false
    }

    suspend fun setFirstRunCompleted(value: Boolean) {
        context.dataStore.edit { preferences -> preferences[firstRunCompletedKey] = value }
    }

    suspend fun getLastSyncTimestamp(): Long {
        return context.dataStore.data.first()[lastSyncTimestampKey] ?: 0L
    }

    suspend fun setLastSyncTimestamp(value: Long) {
        context.dataStore.edit { preferences -> preferences[lastSyncTimestampKey] = value }
    }

    suspend fun getInboxTagId(): Long {
        return context.dataStore.data.first()[inboxTagIdKey] ?: -1L
    }

    suspend fun setInboxTagId(value: Long) {
        context.dataStore.edit { preferences -> preferences[inboxTagIdKey] = value }
    }

    suspend fun getOtpTagId(): Long {
        return context.dataStore.data.first()[otpTagIdKey] ?: -1L
    }

    suspend fun setOtpTagId(value: Long) {
        context.dataStore.edit { preferences -> preferences[otpTagIdKey] = value }
    }
}
