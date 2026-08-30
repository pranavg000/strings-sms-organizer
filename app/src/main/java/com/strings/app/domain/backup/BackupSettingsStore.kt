package com.strings.app.domain.backup

/**
 * Narrow, Android-free view of the user settings that belong in a backup.
 * Implemented by an adapter over SettingsDataStore in the data layer so the
 * export/import use cases stay JVM-unit-testable. Device-specific keys
 * (system tag ids, last sync timestamp, first-run flag) are deliberately
 * excluded -- they must be re-derived on the target device.
 */
interface BackupSettingsStore {
    suspend fun getThemeMode(): String
    suspend fun setThemeMode(value: String)
    suspend fun getAppLockEnabled(): Boolean
    suspend fun setAppLockEnabled(value: Boolean)
}
