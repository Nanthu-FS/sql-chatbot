package com.urbanlens.core.routing

import com.urbanlens.core.air.AirEstimate
import com.urbanlens.core.geo.BoundingBox
import com.urbanlens.core.geo.GeoMath
import com.urbanlens.core.geo.LatLng
import com.urbanlens.core.geo.Shape
import com.urbanlens.core.net.Http
import com.urbanlens.core.net.Osrm
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class TravelMode(
    val label: String,
    val osrmProfile: String,
    /** Relative breathing rate while travelling; cycling moves much more air than sitting in a car. */
    val inhalationFactor: Double,
) {
    WALK("Walk", "routed-foot", 1.0),
    CYCLE("Cycle", "routed-bike", 2.0),
    DRIVE("Drive", "routed-car", 0.6),
}

data class RouteGeometry(val path: List<LatLng>, val distanceMeters: Double, val durationSeconds: Double)

/** Something a route should steer around: a construction site or a reported blocked road. */
data class RouteObstacle(val id: String, val label: String, val shape: Shape, val bufferMeters: Double = 40.0) {
    private val reach: BoundingBox by lazy { shape.bounds.expandedByMeters(bufferMeters) }

    fun touches(p: LatLng): Boolean = reach.contains(p) && shape.distanceMeters(p) <= bufferMeters
}

/** What the route is scored against. */
class ExposureContext(
    val air: (LatLng) -> AirEstimate?,
    /** Crowd level 0..100 at each point. */
    val crowd: (List<LatLng>) -> List<Int>,
    val obstacles: List<RouteObstacle>,
)

data class RouteScore(
    val avgAqi: Int?,
    val maxAqi: Int?,
    /** Average PM2.5 along the route (µg/m³) when known. */
    val avgPm25: Double?,
    /** Relative inhaled dose: concentration x minutes x breathing rate. Only comparable within one search. */
    val exposureDose: Double,
    val avgCrowd: Int,
    val obstacles: List<String>,
)

enum class RouteTag(val label: String) {
    FASTEST("Fastest"),
    CLEANEST("Cleanest air"),
    QUIETEST("Least crowded"),
    AVOIDS_CONSTRUCTION("Avoids construction"),
}

data class RouteOption(
    val id: Int,
    val mode: TravelMode,
    val path: List<LatLng>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val score: RouteScore,
    val tags: Set<RouteTag>,
    /** Percent less inhaled pollution than the fastest route (negative = more). */
    val exposureVsFastestPct: Int,
)

class RouteScorer(private val context: ExposureContext, private val spacingMeters: Double = 40.0) {
    fun score(route: RouteGeometry, mode: TravelMode): RouteScore {
        val samples = GeoMath.samplePath(route.path, spacingMeters)
        val air = samples.mapNotNull(context.air)
        val avgAqi = air.takeIf { it.isNotEmpty() }?.map { it.aqi }?.average()?.roundToInt()
        val maxAqi = air.maxOfOrNull { it.aqi }
        val pm25 = air.mapNotNull { it.pm25 }
        val avgPm25 = pm25.takeIf { it.isNotEmpty() }?.average()
        val concentration = avgPm25 ?: avgAqi?.toDouble() ?: 0.0
        val dose = concentration * (route.durationSeconds / 60.0) * mode.inhalationFactor
        val crowd = context.crowd(samples)
        val avgCrowd = if (crowd.isEmpty()) 0 else crowd.average().roundToInt()
        val hit = context.obstacles.filter { obstacle -> samples.any(obstacle::touches) }.map { it.label }
        return RouteScore(avgAqi, maxAqi, avgPm25, dose, avgCrowd, hit)
    }
}

/**
 * Finds a handful of candidate routes (OSRM alternatives plus two forced detours, one on each
 * side of the direct line) and labels the fastest, cleanest, least crowded and construction-free.
 */
class RoutePlanner(private val http: Http) {

    suspend fun plan(origin: LatLng, destination: LatLng, mode: TravelMode, context: ExposureContext): List<RouteOption> =
        coroutineScope {
            val direct = async { Osrm.parse(http.get(Osrm.url(mode, listOf(origin, destination), alternatives = 2))) }
            val detours = detourWaypoints(origin, destination).map { via ->
                async {
                    runCatching { Osrm.parse(http.get(Osrm.url(mode, listOf(origin, via, destination)))) }
                        .getOrDefault(emptyList())
                }
            }
            val candidates = direct.await() + detours.awaitAll().flatten()
            rank(dedupe(candidates), mode, RouteScorer(context))
        }

    companion object {
        /** Routes slower than this multiple of the fastest are not worth offering. */
        const val MAX_SLOWDOWN = 1.8
        const val MAX_OPTIONS = 4

        fun detourWaypoints(origin: LatLng, destination: LatLng): List<LatLng> {
            val distance = GeoMath.distanceMeters(origin, destination)
            if (distance < 400) return emptyList()
            val mid = GeoMath.interpolate(origin, destination, 0.5)
            val north = (destination.lat - origin.lat) * GeoMath.METERS_PER_DEG_LAT
            val east = (destination.lng - origin.lng) * GeoMath.METERS_PER_DEG_LAT * cos(Math.toRadians(mid.lat))
            val length = hypot(north, east)
            val perpNorth = -east / length
            val perpEast = north / length
            val offset = (distance * 0.22).coerceAtMost(2_500.0)
            return listOf(
                GeoMath.offset(mid, perpNorth * offset, perpEast * offset),
                GeoMath.offset(mid, -perpNorth * offset, -perpEast * offset),
            )
        }

        /** Drops routes that mostly retrace one already kept. */
        fun dedupe(routes: List<RouteGeometry>, thresholdMeters: Double = 35.0): List<RouteGeometry> {
            val kept = mutableListOf<RouteGeometry>()
            for (route in routes.sortedBy { it.durationSeconds }) {
                val samples = GeoMath.samplePath(route.path, 100.0)
                val duplicate = kept.any { other ->
                    samples.map { GeoMath.distanceToPathMeters(it, other.path) }.average() < thresholdMeters
                }
                if (!duplicate) kept += route
            }
            return kept
        }

        fun rank(routes: List<RouteGeometry>, mode: TravelMode, scorer: RouteScorer): List<RouteOption> {
            if (routes.isEmpty()) return emptyList()
            val fastestDuration = routes.minOf { it.durationSeconds }
            val scored = routes
                .filter { it.durationSeconds <= fastestDuration * MAX_SLOWDOWN }
                .map { it to scorer.score(it, mode) }
            val fastest = scored.minBy { it.first.durationSeconds }
            val cleanest = scored.minBy { it.second.exposureDose }
            val quietest = scored.minWith(compareBy({ it.second.avgCrowd }, { it.first.durationSeconds }))
            val clear = scored.filter { it.second.obstacles.isEmpty() }.minByOrNull { it.first.durationSeconds }

            fun tagsFor(candidate: Pair<RouteGeometry, RouteScore>) = buildSet {
                if (candidate === fastest) add(RouteTag.FASTEST)
                if (candidate === cleanest && cleanest.second.exposureDose < fastest.second.exposureDose * 0.97) add(RouteTag.CLEANEST)
                if (candidate === quietest && quietest.second.avgCrowd < fastest.second.avgCrowd) add(RouteTag.QUIETEST)
                if (candidate === clear) add(RouteTag.AVOIDS_CONSTRUCTION)
            }

            val baseDose = fastest.second.exposureDose
            val ordered = listOf(fastest) + scored.filter { it !== fastest }
                .sortedWith(compareBy({ tagsFor(it).isEmpty() }, { it.second.exposureDose }))
            return ordered.take(MAX_OPTIONS).mapIndexed { index, candidate ->
                val (geometry, score) = candidate
                RouteOption(
                    id = index,
                    mode = mode,
                    path = geometry.path,
                    distanceMeters = geometry.distanceMeters,
                    durationSeconds = geometry.durationSeconds,
                    score = score,
                    tags = tagsFor(candidate),
                    exposureVsFastestPct = if (baseDose <= 0) 0 else ((baseDose - score.exposureDose) / baseDose * 100).roundToInt(),
                )
            }
        }
    }
}
