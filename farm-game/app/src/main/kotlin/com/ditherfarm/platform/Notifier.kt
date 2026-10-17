package com.ditherfarm.platform

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ditherfarm.MainActivity
import com.ditherfarm.R

/**
 * "Your pumpkins are ready!" reminders. When the app goes to the background the game
 * schedules one inexact alarm for the soonest ready time; nothing runs while it waits.
 */
object Notifier {
    private const val CHANNEL = "ready"
    private const val REQUEST = 41

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, ctx.getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = ctx.getString(R.string.notif_channel_desc)
            },
        )
    }

    fun schedule(ctx: Context, atEpochMs: Long, title: String, text: String) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.set(AlarmManager.RTC_WAKEUP, atEpochMs, pending(ctx, title, text))
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(pending(ctx, "", ""))
        ctx.getSystemService(NotificationManager::class.java)?.cancel(REQUEST)
    }

    private fun pending(ctx: Context, title: String, text: String): PendingIntent {
        val i = Intent(ctx, ReadyNotificationReceiver::class.java).putExtra("title", title).putExtra("text", text)
        return PendingIntent.getBroadcast(ctx, REQUEST, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    internal fun post(ctx: Context, title: String, text: String) {
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { ctx.getSystemService(NotificationManager::class.java)?.notify(REQUEST, n) }
    }
}

class ReadyNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title").orEmpty()
        if (title.isNotEmpty()) Notifier.post(context, title, intent.getStringExtra("text").orEmpty())
    }
}
