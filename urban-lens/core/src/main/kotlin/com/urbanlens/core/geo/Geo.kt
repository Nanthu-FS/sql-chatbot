package com.urbanlens.core.geo

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val lat: Double, val lng: Double)

data class BoundingBox(val south: Double, val west: Double, val north: Double, val east: Double) {
    init {
        require(north >= south) { "north ($north) must be >= south ($south)" }
        require(east >= west) { "east ($east) must be >= west ($west)" }
    }

    val center: LatLng get() = LatLng((south + north) / 2, (west + east) / 2)

    val widthMeters: Double
        get() = GeoMath.distanceMeters(LatLng(center.lat, west), LatLng(center.lat, east))

    val heightMeters: Double
        get() = GeoMath.distanceMeters(LatLng(south, center.lng), LatLng(north, center.lng))

    fun contains(p: LatLng): Boolean = p.lat in south..north && p.lng in west..east

    fun contains(other: BoundingBox): Boolean =
        other.south >= south && other.north <= north && other.west >= west && other.east <= east

    fun expandedBy(fraction: Double): BoundingBox {
        val dLat = (north - south) * fraction
        val dLng = (east - west) * fraction
        return BoundingBox(south - dLat, west - dLng, north + dLat, east + dLng)
    }

    fun expandedByMeters(meters: Double): BoundingBox {
        val sw = GeoMath.offset(LatLng(south, west), -meters, -meters)
        val ne = GeoMath.offset(LatLng(north, east), meters, meters)
        return BoundingBox(sw.lat, sw.lng, ne.lat, ne.lng)
    }

    /** Centers of a [rows] x [cols] grid of equal cells covering the box, row by row from the south-west. */
    fun gridCenters(rows: Int, cols: Int): List<LatLng> {
        require(rows > 0 && cols > 0)
        val dLat = (north - south) / rows
        val dLng = (east - west) / cols
        return buildList {
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    add(LatLng(south + dLat * (r + 0.5), west + dLng * (c + 0.5)))
                }
            }
        }
    }

    companion object {
        fun around(center: LatLng, radiusMeters: Double): BoundingBox {
            val sw = GeoMath.offset(center, -radiusMeters, -radiusMeters)
            val ne = GeoMath.offset(center, radiusMeters, radiusMeters)
            return BoundingBox(sw.lat, sw.lng, ne.lat, ne.lng)
        }

        fun of(points: Collection<LatLng>): BoundingBox {
            require(points.isNotEmpty()) { "points must not be empty" }
            return BoundingBox(
                south = points.minOf { it.lat },
                west = points.minOf { it.lng },
                north = points.maxOf { it.lat },
                east = points.maxOf { it.lng },
            )
        }
    }
}

object GeoMath {
    const val EARTH_RADIUS_M = 6_371_008.8
    const val METERS_PER_DEG_LAT = PI * EARTH_RADIUS_M / 180.0

    /** Great-circle distance (haversine). */
    fun distanceMeters(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Equirectangular approximation; accurate to well under 1% for city-scale distances. */
    fun approxDistanceMeters(a: LatLng, b: LatLng): Double {
        val x = (b.lng - a.lng) * cos(Math.toRadians((a.lat + b.lat) / 2)) * METERS_PER_DEG_LAT
        val y = (b.lat - a.lat) * METERS_PER_DEG_LAT
        return sqrt(x * x + y * y)
    }

    fun offset(origin: LatLng, northMeters: Double, eastMeters: Double): LatLng {
        val dLat = northMeters / METERS_PER_DEG_LAT
        val dLng = eastMeters / (METERS_PER_DEG_LAT * cos(Math.toRadians(origin.lat)))
        return LatLng(origin.lat + dLat, origin.lng + dLng)
    }

    fun interpolate(a: LatLng, b: LatLng, t: Double): LatLng =
        LatLng(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)

    fun pathLength(path: List<LatLng>): Double {
        var total = 0.0
        for (i in 1 until path.size) total += distanceMeters(path[i - 1], path[i])
        return total
    }

    /** Points every [spacingMeters] along [path], always including the first and last point. */
    fun samplePath(path: List<LatLng>, spacingMeters: Double): List<LatLng> {
        require(spacingMeters > 0)
        if (path.size < 2) return path
        val out = mutableListOf(path.first())
        var sinceLast = 0.0
        for (i in 1 until path.size) {
            val a = path[i - 1]
            val b = path[i]
            val seg = distanceMeters(a, b)
            if (seg == 0.0) continue
            var pos = spacingMeters - sinceLast
            while (pos <= seg) {
                out += interpolate(a, b, pos / seg)
                pos += spacingMeters
            }
            sinceLast = seg - (pos - spacingMeters)
        }
        if (out.last() != path.last()) out += path.last()
        return out
    }

    /** Distance from [p] to segment [a]-[b], using a local planar projection centred on [p]. */
    fun distanceToSegmentMeters(p: LatLng, a: LatLng, b: LatLng): Double {
        val k = cos(Math.toRadians(p.lat)) * METERS_PER_DEG_LAT
        val ax = (a.lng - p.lng) * k
        val ay = (a.lat - p.lat) * METERS_PER_DEG_LAT
        val bx = (b.lng - p.lng) * k
        val by = (b.lat - p.lat) * METERS_PER_DEG_LAT
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
        val cx = ax + t * dx
        val cy = ay + t * dy
        return sqrt(cx * cx + cy * cy)
    }

    fun distanceToPathMeters(p: LatLng, path: List<LatLng>): Double = when (path.size) {
        0 -> Double.POSITIVE_INFINITY
        1 -> approxDistanceMeters(p, path[0])
        else -> {
            var best = Double.POSITIVE_INFINITY
            for (i in 1 until path.size) {
                val d = distanceToSegmentMeters(p, path[i - 1], path[i])
                if (d < best) best = d
            }
            best
        }
    }

    /** Ray-casting test against a single ring; lat/lng are treated as planar, fine at city scale. */
    fun isInsidePolygon(p: LatLng, ring: List<LatLng>): Boolean {
        if (ring.size < 3) return false
        var inside = false
        var j = ring.lastIndex
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[j]
            if ((a.lat > p.lat) != (b.lat > p.lat)) {
                val crossLng = (b.lng - a.lng) * (p.lat - a.lat) / (b.lat - a.lat) + a.lng
                if (p.lng < crossLng) inside = !inside
            }
            j = i
        }
        return inside
    }

    fun centroid(points: List<LatLng>): LatLng {
        require(points.isNotEmpty())
        return LatLng(points.sumOf { it.lat } / points.size, points.sumOf { it.lng } / points.size)
    }
}

/** A line or area on the map that something can be "near". */
data class Shape(val points: List<LatLng>, val isArea: Boolean) {
    init {
        require(points.isNotEmpty()) { "shape needs at least one point" }
    }

    val center: LatLng by lazy { GeoMath.centroid(points) }
    val bounds: BoundingBox by lazy { BoundingBox.of(points) }

    fun distanceMeters(p: LatLng): Double =
        if (isArea && GeoMath.isInsidePolygon(p, points)) 0.0 else GeoMath.distanceToPathMeters(p, points)

    companion object {
        fun point(p: LatLng) = Shape(listOf(p), isArea = false)
    }
}
