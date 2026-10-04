package com.urbanlens.core.crowd

import com.urbanlens.core.geo.GeoMath
import com.urbanlens.core.geo.LatLng
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Simulated crowd density. There is no free real-time source for people counts, so density is
 * estimated from real places (OpenStreetMap) and typical hourly patterns for each kind of place,
 * plus a small deterministic per-place variation so the map feels alive but stays stable.
 */
class CrowdModel(val places: List<Place>) {

    /** How busy [place] is at [time], 0..1. */
    fun busyness(place: Place, time: LocalDateTime): Double {
        val hour = time.hour
        val frac = time.minute / 60.0
        val now = curveValue(place, time.toLocalDate(), hour)
        val next = curveValue(place, time.toLocalDate(), hour + 1)
        val base = now + (next - now) * frac
        val wobble = 1.0 + 0.05 * unitNoise(place.id, time.toLocalDate().toEpochDay(), hour * 6 + time.minute / 10)
        return (base * wobble).coerceIn(0.0, 1.0)
    }

    /** Busyness for each hour (0..23) of [date], 0..1. */
    fun hourlyBusyness(place: Place, date: LocalDate): List<Double> =
        (0 until 24).map { curveValue(place, date, it) }

    /**
     * The quietest hour from [from] until [untilHour] (exclusive) on the same day, or null when
     * there is no time left today.
     */
    fun bestHourToVisit(place: Place, from: LocalDateTime, untilHour: Int = 22): Int? {
        val startHour = if (from.minute == 0) from.hour else from.hour + 1
        if (startHour >= untilHour) return null
        val curve = hourlyBusyness(place, from.toLocalDate())
        return (startHour.coerceAtLeast(6) until untilHour).minByOrNull { curve[it] }
    }

    /** Crowd level 0..100 at each of [points] at [time]. */
    fun levels(points: List<LatLng>, time: LocalDateTime): List<Int> {
        val weighted = places.map { place ->
            Weighted(place, place.category.peopleScale * busyness(place, time))
        }.filter { it.weight > 0.01 }
        return points.map { toLevel(density(it, weighted)) }
    }

    fun levelAt(point: LatLng, time: LocalDateTime): Int = levels(listOf(point), time).first()

    private fun density(point: LatLng, weighted: List<Weighted>): Double {
        var sum = 0.0
        for (w in weighted) {
            val sigma = w.place.category.spreadMeters
            val dLat = (w.place.location.lat - point.lat) * GeoMath.METERS_PER_DEG_LAT
            if (dLat > 3 * sigma || dLat < -3 * sigma) continue
            val d = GeoMath.approxDistanceMeters(point, w.place.location)
            if (d > 3 * sigma) continue
            sum += w.weight * exp(-(d * d) / (2 * sigma * sigma))
        }
        return sum
    }

    private fun curveValue(place: Place, date: LocalDate, hour: Int): Double {
        val day = if (hour >= 24) date.plusDays(1) else date
        val h = hour.mod(24)
        val category = place.category
        val factor = when (day.dayOfWeek) {
            DayOfWeek.SUNDAY -> category.weekendFactor
            DayOfWeek.SATURDAY -> 1 + (category.weekendFactor - 1) * 0.6
            else -> 1.0
        }
        val variation = 1.0 + 0.12 * unitNoise(place.id, day.toEpochDay(), h)
        return (category.weekdayBusyness(h) * factor * variation).coerceIn(0.0, 1.0)
    }

    private data class Weighted(val place: Place, val weight: Double)

    companion object {
        /** Raw density at which the level reaches ~63/100. */
        private const val DENSITY_SCALE = 6.0

        fun toLevel(density: Double): Int = (100 * (1 - exp(-density / DENSITY_SCALE))).roundToInt().coerceIn(0, 100)

        /** Deterministic pseudo-random value in -1..1. */
        internal fun unitNoise(id: String, day: Long, slot: Int): Double {
            var h = id.hashCode().toLong() * 0x9E3779B97F4A7C15uL.toLong()
            h = h xor (day * 0x632BE59BD9B4E019L)
            h = h xor (slot.toLong() * 0x85EBCA77C2B2AE63uL.toLong())
            h = h xor (h ushr 29)
            h *= 0xBF58476D1CE4E5B9uL.toLong()
            h = h xor (h ushr 32)
            return ((h ushr 11).toDouble() / (1L shl 53).toDouble()) * 2 - 1
        }
    }
}
