package com.strings.app.data.prefs

import com.strings.app.domain.backup.BackupSettingsStore
import kotlinx.coroutines.flow.first

class DataStoreBackupSettings(
    private val settings: SettingsDataStore
) : BackupSettingsStore {
    override suspend fun getThemeMode(): String {
        return settings.themeMode.first().name
    }

    override suspend fun setThemeMode(value: String) {
        settings.setThemeMode(ThemeMode.fromStored(value))
    }

    override suspend fun getAppLockEnabled(): Boolean {
        return settings.appLockEnabled.first()
    }

    override suspend fun setAppLockEnabled(value: Boolean) {
        settings.setAppLockEnabled(value)
    }
}
