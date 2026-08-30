package com.strings.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strings.app.data.prefs.SettingsDataStore
import com.strings.app.data.prefs.ThemeMode
import com.strings.app.domain.usecase.ClearFinanceDataUseCase
import com.strings.app.domain.usecase.ExportCategorizationUseCase
import com.strings.app.domain.usecase.RecategorizeResult
import com.strings.app.domain.usecase.RecategorizeTransactionsUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsDataStore: SettingsDataStore,
    private val recategorizeTransactionsUseCase: RecategorizeTransactionsUseCase,
    private val exportCategorizationUseCase: ExportCategorizationUseCase,
    private val clearFinanceDataUseCase: ClearFinanceDataUseCase
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = settingsDataStore.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    val appLockEnabled: StateFlow<Boolean> = settingsDataStore.appLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsDataStore.setThemeMode(mode)
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setAppLockEnabled(enabled)
        }
    }

    suspend fun recategorizeRecent(): RecategorizeResult {
        val sinceMillis: Long = System.currentTimeMillis() - RECATEGORIZE_3M_MS
        return recategorizeTransactionsUseCase.execute(sinceMillis)
    }

    suspend fun recategorizeLastYear(): RecategorizeResult {
        val sinceMillis: Long = System.currentTimeMillis() - RECATEGORIZE_1Y_MS
        return recategorizeTransactionsUseCase.execute(sinceMillis)
    }

    suspend fun exportCategorizationJson(): String {
        val sinceMillis: Long = System.currentTimeMillis() - RECATEGORIZE_3M_MS
        return exportCategorizationUseCase.execute(sinceMillis, RECATEGORIZE_3M_DAYS)
    }

    suspend fun exportCategorizationLastYearJson(): String {
        val sinceMillis: Long = System.currentTimeMillis() - RECATEGORIZE_1Y_MS
        return exportCategorizationUseCase.execute(sinceMillis, RECATEGORIZE_1Y_DAYS)
    }

    suspend fun clearFinanceData(): Int {
        return clearFinanceDataUseCase.execute()
    }

    private companion object {
        const val RECATEGORIZE_3M_DAYS: Int = 90
        const val RECATEGORIZE_3M_MS: Long = RECATEGORIZE_3M_DAYS * 24L * 60 * 60 * 1000
        const val RECATEGORIZE_1Y_DAYS: Int = 365
        const val RECATEGORIZE_1Y_MS: Long = RECATEGORIZE_1Y_DAYS * 24L * 60 * 60 * 1000
    }
}
