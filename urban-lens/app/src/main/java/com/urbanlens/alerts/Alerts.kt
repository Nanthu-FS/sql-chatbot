package com.urbanlens.alerts

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.urbanlens.R
import com.urbanlens.appContainer
import com.urbanlens.core.air.AqiCategory
import com.urbanlens.core.alerts.AlertKind
import com.urbanlens.core.alerts.AlertState
import com.urbanlens.core.alerts.AqiAlertPolicy
import com.urbanlens.core.alerts.HealthProfile
import com.urbanlens.core.geo.LatLng
import com.urbanlens.ui.MainActivity
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

object AlertNotifier {
    const val CHANNEL_ID = "aqi_alerts"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alerts_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.alerts_channel_description) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun notify(context: Context, id: Int, title: String, text: String) {
        if (!canNotify(context)) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_air)
            .setColor(0xFFF07F2B.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}

object AlertScheduler {
    private const val PERIODIC = "aqi-alerts"
    private const val ONE_OFF = "aqi-alerts-now"

    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun apply(context: Context, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<AqiAlertWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            workManager.cancelUniqueWork(PERIODIC)
        }
    }

    fun checkNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<AqiAlertWorker>().setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_OFF, ExistingWorkPolicy.REPLACE, request)
    }
}

/** Hourly: fetches AQI at each saved place and notifies when it crosses the user's threshold. */
class AqiAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        val settings = container.settings.current()
        if (!settings.alertsEnabled) return Result.success()
        val dao = container.database.savedPlaces()
        val places = dao.getAll()
        if (places.isEmpty()) return Result.success()

        val samples = try {
            container.cityData.airAt(places.map { LatLng(it.lat, it.lng) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }

        val now = System.currentTimeMillis()
        for (place in places) {
            val location = LatLng(place.lat, place.lng)
            val aqi = samples.firstOrNull { it.location == location }?.usAqi ?: continue
            val decision = AqiAlertPolicy.evaluate(aqi, settings.threshold, AlertState(place.alerting, place.lastNotifiedAt), now)
            val category = AqiCategory.of(aqi)
            when (decision.notify) {
                AlertKind.WORSENED -> {
                    val advice = if (settings.profile == HealthProfile.SENSITIVE) category.sensitiveAdvice else category.advice
                    AlertNotifier.notify(
                        applicationContext,
                        place.id.toInt(),
                        "Air at ${place.name}: ${category.label}",
                        "AQI $aqi. $advice",
                    )
                }
                AlertKind.RECOVERED -> AlertNotifier.notify(
                    applicationContext,
                    place.id.toInt(),
                    "Air at ${place.name} is better",
                    "AQI $aqi (${category.label}).",
                )
                null -> Unit
            }
            dao.update(
                place.copy(
                    alerting = decision.state.alerting,
                    lastNotifiedAt = decision.state.lastNotifiedAtMillis,
                    lastAqi = aqi,
                    lastCheckedAt = now,
                ),
            )
        }
        return Result.success()
    }
}
