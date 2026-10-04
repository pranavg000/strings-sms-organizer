package com.strings.app.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strings.app.domain.backup.ImportResult
import com.strings.app.domain.usecase.ExportDataUseCase
import com.strings.app.domain.usecase.ImportDataUseCase
import com.strings.app.domain.usecase.SyncSmsUseCase
import com.strings.app.ui.common.ExclusiveOperationRunner
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class BackupViewModel(
    private val exportDataUseCase: ExportDataUseCase,
    private val importDataUseCase: ImportDataUseCase,
    private val syncSmsUseCase: SyncSmsUseCase
) : ViewModel() {
    private val operations = ExclusiveOperationRunner(viewModelScope)

    val isBusy: StateFlow<Boolean> = operations.isBusy
    val messages: SharedFlow<String> = operations.messages

    /** Builds the backup and hands the JSON to [write] (the caller owns the SAF stream). */
    fun export(write: suspend (String) -> Unit) {
        operations.run(onFailure = { e -> "Export failed: ${e.message ?: "unknown error"}" }) {
            write(exportDataUseCase.execute())
            "Backup exported"
        }
    }

    /** Reads the backup JSON via [read] (the caller owns the SAF stream) and restores it. */
    fun import(read: suspend () -> String) {
        operations.run(onFailure = { e -> e.message ?: "Import failed" }) {
            val jsonString: String = read()
            // Make sure every device SMS exists (and is parsed) before message
            // states are matched. Idempotent and mutex-guarded; without SMS
            // permission the import still runs and reports unmatched counts.
            try {
                syncSmsUseCase.importAll()
            } catch (_: SecurityException) {
            }
            importDataUseCase.execute(jsonString).toSummary()
        }
    }

    private fun ImportResult.toSummary(): String = buildString {
        append("Imported $tagsAdded tags, $filtersAdded filters, $tabsRestored tabs")
        if (filtersSkipped > 0) append(" ($filtersSkipped duplicate filters skipped)")
        if (accountsAdded > 0) append("; $accountsAdded accounts")
        if (messagesRestored > 0) append("; restored state on $messagesRestored messages")
        if (balancesRestored > 0) append(" incl. $balancesRestored balances")
        if (linkedTransactionsRestored > 0) append("; $linkedTransactionsRestored linked transactions")
        if (messagesUnmatched > 0) append("; $messagesUnmatched messages not found on this device")
    }
}
