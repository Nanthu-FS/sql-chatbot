package com.spendlens.app.notify

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
import com.spendlens.app.MainActivity
import com.spendlens.app.R

/** Everything that talks to the notification shade. */
class Notifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    init {
        val system = context.getSystemService(NotificationManager::class.java)
        system?.createNotificationChannels(
            listOf(
                NotificationChannel(CH_SUMMARY, "Spending summary", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Today's and this month's spend — also on the lock screen"
                    setShowBadge(false)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(CH_ALERTS, "Payment alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Unusual payments, possible double charges and payments added from SMS"
                },
            ),
        )
    }

    fun canPost(): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && manager.areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    fun showSummary(title: String, text: String, detail: String) {
        if (!canPost()) return
        val n = NotificationCompat.Builder(context, CH_SUMMARY)
            .setSmallIcon(R.drawable.ic_stat_lens)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n$detail"))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open(REQUEST_SUMMARY))
            .addAction(0, "Add payment", open(REQUEST_SCAN, action = ACTION_SCAN))
            .build()
        manager.notify(ID_SUMMARY, n)
    }

    fun hideSummary() = manager.cancel(ID_SUMMARY)

    @SuppressLint("MissingPermission")
    fun alert(key: String, title: String, text: String, txnId: Long?) {
        if (!canPost()) return
        val n = NotificationCompat.Builder(context, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_lens)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(open(key.hashCode(), txnId = txnId))
            .build()
        manager.notify(key.hashCode(), n)
    }

    fun paymentAdded(id: Long, amount: String, merchant: String, via: String) =
        alert("sms:$id", "Added $amount · $merchant", "From a $via message. Tap to check or edit.", id)

    private fun open(request: Int, action: String? = null, txnId: Long? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        action?.let { intent.putExtra(EXTRA_ACTION, it) }
        txnId?.let { intent.putExtra(EXTRA_OPEN_TXN, it) }
        return PendingIntent.getActivity(context, request, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        const val CH_SUMMARY = "summary"
        const val CH_ALERTS = "alerts"
        const val ID_SUMMARY = 1001
        private const val REQUEST_SUMMARY = 1
        private const val REQUEST_SCAN = 2
        const val EXTRA_ACTION = "spendlens.action"
        const val EXTRA_OPEN_TXN = "spendlens.open_txn"
        const val ACTION_SCAN = "scan"
    }
}
