package com.smartnotes.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val lat: Double, val lng: Double)

data class PlaceReminder(val noteId: Long, val placeName: String, val center: LatLng, val radiusMeters: Float = 150f)

/** Fallback geofence check, used when Play Services geofencing is unavailable. */
object GeoFence {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun distanceMeters(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    fun triggered(reminders: List<PlaceReminder>, here: LatLng): List<PlaceReminder> =
        reminders.filter { distanceMeters(it.center, here) <= it.radiusMeters }
}
