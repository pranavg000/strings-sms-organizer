package com.strings.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.strings.app.domain.usecase.ApplyFilterToExistingUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ApplyFilterWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {
    private val applyFilterToExisting: ApplyFilterToExistingUseCase by inject()

    override suspend fun doWork(): Result {
        val filterId: Long = inputData.getLong(KEY_FILTER_ID, -1L)
        if (filterId <= 0L) return Result.success()
        return try {
            applyFilterToExisting.execute(filterId)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_FILTER_ID = "filterId"
    }
}
