package app.monoworkspace

import android.app.Application
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MonoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch { container.seeder.seedIfNeeded() }
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            TrashPurgeWorker.NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TrashPurgeWorker>(1, TimeUnit.DAYS).build(),
        )
    }
}

/** Daily job: deletes trash older than 30 days, including its media. */
class TrashPurgeWorker(context: android.content.Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MonoApp
        return runCatching { app.container.pages.purgeExpired() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        const val NAME = "trash-purge"
    }
}
