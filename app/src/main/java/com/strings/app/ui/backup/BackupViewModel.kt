package com.strings.app.ui.backup

import androidx.lifecycle.ViewModel
import com.strings.app.domain.backup.ImportResult
import com.strings.app.domain.usecase.ExportDataUseCase
import com.strings.app.domain.usecase.ImportDataUseCase
import com.strings.app.domain.usecase.SyncSmsUseCase

class BackupViewModel(
    private val exportDataUseCase: ExportDataUseCase,
    private val importDataUseCase: ImportDataUseCase,
    private val syncSmsUseCase: SyncSmsUseCase
) : ViewModel() {
    suspend fun exportJson(): String {
        return exportDataUseCase.execute()
    }

    suspend fun import(json: String): ImportResult {
        // Make sure every device SMS exists (and is parsed) before message
        // states are matched. Idempotent and mutex-guarded; without SMS
        // permission the import still runs and reports unmatched counts.
        try {
            syncSmsUseCase.importAll()
        } catch (_: SecurityException) {
        }
        return importDataUseCase.execute(json)
    }
}
