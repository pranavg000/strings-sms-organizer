package com.strings.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.strings.app.domain.usecase.SyncSmsUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SmsSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {
    private val syncSmsUseCase: SyncSmsUseCase by inject()

    override suspend fun doWork(): Result {
        return try {
            syncSmsUseCase.importAll()
            Result.success()
        } catch (_: SecurityException) {
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
