package com.spendlens.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.spendlens.app.SpendLensApplication
import java.util.concurrent.TimeUnit

/** Keeps the widget and lock-screen summary current (e.g. "today" rolls over at midnight). */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as SpendLensApplication).container.surfaces.refresh()
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "surfaces-refresh",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES).build(),
            )
        }
    }
}
