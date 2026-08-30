package com.strings.app.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SmsWorkScheduler {
    private const val PERIODIC_SYNC_WORK = "periodic_sms_sync"

    fun schedule(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val periodicSync = PeriodicWorkRequestBuilder<SmsSyncWorker>(
            15,
            TimeUnit.MINUTES
        ).build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicSync
        )
    }
}
