package com.smartnotes.features.places

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.smartnotes.app
import com.smartnotes.data.PlaceReminderEntity
import com.smartnotes.features.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** "Remind me of this when I'm at the store": geofences backed by Play Services. */
object Places {

    fun hasForeground(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackground(context: Context) =
        Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): Pair<Double, Double>? {
        if (!hasForeground(context)) return null
        val loc = LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await() ?: return null
        return loc.latitude to loc.longitude
    }

    /** Saves a reminder for [noteId] at the current location and registers its geofence. */
    suspend fun remindHere(context: Context, noteId: Long, placeName: String): PlaceReminderEntity? {
        val (lat, lng) = currentLocation(context) ?: return null
        val r = PlaceReminderEntity(noteId = noteId, placeName = placeName, lat = lat, lng = lng)
        val id = context.app.repo.addReminder(r)
        val saved = r.copy(id = id)
        register(context, listOf(saved))
        return saved
    }

    suspend fun remove(context: Context, id: Long) {
        LocationServices.getGeofencingClient(context).removeGeofences(listOf(id.toString()))
        context.app.repo.deleteReminder(id)
    }

    @SuppressLint("MissingPermission")
    fun register(context: Context, reminders: List<PlaceReminderEntity>) {
        if (reminders.isEmpty() || !hasForeground(context) || !hasBackground(context)) return
        val fences = reminders.map {
            Geofence.Builder()
                .setRequestId(it.id.toString())
                .setCircularRegion(it.lat, it.lng, it.radiusMeters)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()
        }
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(fences)
            .build()
        LocationServices.getGeofencingClient(context).addGeofences(request, pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, GeofenceReceiver::class.java),
        // Geofencing fills in the event extras, so this must be mutable.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
}

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError() || event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return
        val ids = event.triggeringGeofences.orEmpty().mapNotNull { it.requestId.toLongOrNull() }
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = context.app.repo
                for (id in ids) {
                    val r = repo.reminder(id) ?: continue
                    val note = repo.get(r.noteId) ?: continue
                    Notifications.showNoteReminder(context, note.id, "You're at ${r.placeName}", note.title)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Geofences don't survive a reboot; re-register them. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                Places.register(context, context.app.repo.allReminders())
            } finally {
                pending.finish()
            }
        }
    }
}
