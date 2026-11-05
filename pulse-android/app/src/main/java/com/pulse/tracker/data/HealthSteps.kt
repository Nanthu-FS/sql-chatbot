package com.pulse.tracker.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.Period

/** Whether Health Connect can be used on this phone, and whether Pulse may read steps. */
enum class HealthStatus {
    /** Not checked yet. */
    UNKNOWN,
    /** This device can't run Health Connect (Android 8 / 8.1). */
    UNSUPPORTED,
    /** Health Connect needs installing or updating from Google Play. */
    NEEDS_INSTALL,
    /** Available, but Pulse hasn't been given step access. */
    NEEDS_PERMISSION,
    /** Pulse can read steps. */
    CONNECTED,
}

/**
 * Reads daily step totals from Health Connect, which merges steps from the
 * phone's own counter, Google Fit, Samsung Health, watches and other apps.
 */
class HealthSteps(private val context: Context) {

    val permissions: Set<String> = setOf(HealthPermission.getReadPermission(StepsRecord::class))

    private var cached: HealthConnectClient? = null

    /** Created lazily, and only once Health Connect is actually available (it can be installed mid-session). */
    private fun client(): HealthConnectClient? = cached
        ?: if (sdkStatus() == HealthConnectClient.SDK_AVAILABLE) HealthConnectClient.getOrCreate(context).also { cached = it } else null

    private fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context, PROVIDER)

    suspend fun status(): HealthStatus = when (sdkStatus()) {
        HealthConnectClient.SDK_UNAVAILABLE -> HealthStatus.UNSUPPORTED
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthStatus.NEEDS_INSTALL
        else -> {
            val granted = runCatching { client()?.permissionController?.getGrantedPermissions() }.getOrNull().orEmpty()
            if (granted.containsAll(permissions)) HealthStatus.CONNECTED else HealthStatus.NEEDS_PERMISSION
        }
    }

    /**
     * Step totals per local calendar day from [from] to [to], inclusive.
     * Days with no step data are left out so they never overwrite a manual entry.
     */
    suspend fun dailySteps(from: LocalDate, to: LocalDate): Map<LocalDate, Int> {
        val c = client() ?: return emptyMap()
        val groups = c.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()),
                timeRangeSlicer = Period.ofDays(1),
            ),
        )
        return buildMap {
            groups.forEach { g ->
                val steps = g.result[StepsRecord.COUNT_TOTAL]
                if (steps != null) put(g.startTime.toLocalDate(), steps.toInt())
            }
        }
    }

    /** Opens Google Play so the person can install or update Health Connect. */
    fun openInstall() {
        val uri = Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding")
        val play = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.android.vending")
            putExtra("overlay", true)
            putExtra("callerId", context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PROVIDER"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(play) }.recoverCatching { context.startActivity(web) }
    }

    /** Opens Health Connect's own settings, where access can be reviewed or revoked. */
    fun openSettings() {
        val action = if (Build.VERSION.SDK_INT >= 34) {
            "android.health.connect.action.HEALTH_HOME_SETTINGS"
        } else {
            "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"
        }
        runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    companion object {
        const val PROVIDER = "com.google.android.apps.healthdata"
    }
}
