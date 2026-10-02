package com.smartnotes.features

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit

data class CalendarEvent(val title: String, val begin: Long, val end: Long)

/** Reads upcoming events from the device calendar (read-only). */
object CalendarSource {

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun events(context: Context, fromMs: Long, toMs: Long): List<CalendarEvent> {
        if (!hasPermission(context)) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, fromMs)
            ContentUris.appendId(it, toMs)
        }.build()
        val projection = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END)
        val out = ArrayList<CalendarEvent>()
        context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                val title = c.getString(0) ?: continue
                out += CalendarEvent(title, c.getLong(1), c.getLong(2))
            }
        }
        return out
    }

    /** Events happening now or starting within [hours]. */
    fun upcoming(context: Context, hours: Long = 3): List<CalendarEvent> {
        val now = System.currentTimeMillis()
        return events(context, now - TimeUnit.HOURS.toMillis(1), now + TimeUnit.HOURS.toMillis(hours)).filter { it.end >= now }
    }

    fun current(context: Context): CalendarEvent? {
        val now = System.currentTimeMillis()
        return upcoming(context, 1).firstOrNull { it.begin - TimeUnit.MINUTES.toMillis(5) <= now && now <= it.end }
    }
}
