package com.strings.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strings.app.data.prefs.SettingsDataStore
import com.strings.app.data.prefs.ThemeMode
import com.strings.app.domain.usecase.ClearFinanceDataUseCase
import com.strings.app.domain.usecase.ExportCategorizationUseCase
import com.strings.app.domain.usecase.RecategorizeResult
import com.strings.app.domain.usecase.RecategorizeTransactionsUseCase
import com.strings.app.ui.common.ExclusiveOperationRunner
import kotlinx.coroutines.flow.SharedFlow
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

    private val operations = ExclusiveOperationRunner(viewModelScope)

    /** True while a recategorization or finance clear is running; gates the Settings cards. */
    val isBusy: StateFlow<Boolean> = operations.isBusy

    /** One-shot outcome messages for the snackbar. */
    val messages: SharedFlow<String> = operations.messages

    fun recategorizeRecent() {
        recategorize(windowMillis = RECATEGORIZE_3M_MS)
    }

    fun recategorizeLastYear() {
        recategorize(windowMillis = RECATEGORIZE_1Y_MS)
    }

    fun clearFinanceData() {
        operations.run(onFailure = { e -> "Clearing finance data failed: ${e.message ?: "unknown error"}" }) {
            val removed: Int = clearFinanceDataUseCase.execute()
            "Cleared $removed Finance tags + all transactions/accounts"
        }
    }

    private fun recategorize(windowMillis: Long) {
        operations.run(onFailure = { e -> "Recategorization failed: ${e.message ?: "unknown error"}" }) {
            val result: RecategorizeResult =
                recategorizeTransactionsUseCase.execute(System.currentTimeMillis() - windowMillis)
            "Recategorized ${result.categorized} of ${result.scanned} messages"
        }
    }

    suspend fun exportCategorizationJson(): String {
        val sinceMillis: Long = System.currentTimeMillis() - RECATEGORIZE_3M_MS
        return exportCategorizationUseCase.execute(sinceMillis, RECATEGORIZE_3M_DAYS)
    }

    suspend fun exportCategorizationLastYearJson(): String {
        val sinceMillis: Long = System.currentTimeMillis() - RECATEGORIZE_1Y_MS
        return exportCategorizationUseCase.execute(sinceMillis, RECATEGORIZE_1Y_DAYS)
    }

    private companion object {
        const val RECATEGORIZE_3M_DAYS: Int = 90
        const val RECATEGORIZE_3M_MS: Long = RECATEGORIZE_3M_DAYS * 24L * 60 * 60 * 1000
        const val RECATEGORIZE_1Y_DAYS: Int = 365
        const val RECATEGORIZE_1Y_MS: Long = RECATEGORIZE_1Y_DAYS * 24L * 60 * 60 * 1000
    }
}
