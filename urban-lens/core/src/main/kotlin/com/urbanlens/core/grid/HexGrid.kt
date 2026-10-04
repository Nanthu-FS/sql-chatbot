package com.urbanlens.core.grid

import com.urbanlens.core.geo.BoundingBox
import com.urbanlens.core.geo.GeoMath
import com.urbanlens.core.geo.LatLng
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

data class HexCell(val q: Int, val r: Int)

/**
 * Pointy-top hexagonal grid (axial coordinates) laid over a local planar projection around [origin].
 * [radiusMeters] is the center-to-corner distance of each hexagon.
 */
class HexGrid(val origin: LatLng, val radiusMeters: Double) {
    init {
        require(radiusMeters > 0)
    }

    private val metersPerDegLng = cos(Math.toRadians(origin.lat)) * GeoMath.METERS_PER_DEG_LAT

    private fun x(p: LatLng) = (p.lng - origin.lng) * metersPerDegLng
    private fun y(p: LatLng) = (p.lat - origin.lat) * GeoMath.METERS_PER_DEG_LAT
    private fun toLatLng(x: Double, y: Double) =
        LatLng(origin.lat + y / GeoMath.METERS_PER_DEG_LAT, origin.lng + x / metersPerDegLng)

    private fun centerX(cell: HexCell) = radiusMeters * SQRT3 * (cell.q + cell.r / 2.0)
    private fun centerY(cell: HexCell) = radiusMeters * 1.5 * cell.r

    fun center(cell: HexCell): LatLng = toLatLng(centerX(cell), centerY(cell))

    fun cellAt(p: LatLng): HexCell {
        val px = x(p)
        val py = y(p)
        val qf = (SQRT3 / 3 * px - py / 3) / radiusMeters
        val rf = (2.0 / 3 * py) / radiusMeters
        return cubeRound(qf, rf)
    }

    /** Corners of [cell], counter-clockwise, not closed. */
    fun corners(cell: HexCell): List<LatLng> {
        val cx = centerX(cell)
        val cy = centerY(cell)
        return (0 until 6).map { i ->
            val angle = Math.toRadians(60.0 * i - 30.0)
            toLatLng(cx + radiusMeters * cos(angle), cy + radiusMeters * sin(angle))
        }
    }

    /** All cells whose center lies inside [box] grown by one hexagon radius. */
    fun cellsCovering(box: BoundingBox): List<HexCell> {
        val grown = box.expandedByMeters(radiusMeters)
        val x0 = x(LatLng(grown.south, grown.west))
        val x1 = x(LatLng(grown.north, grown.east))
        val y0 = y(LatLng(grown.south, grown.west))
        val y1 = y(LatLng(grown.north, grown.east))
        val rowHeight = 1.5 * radiusMeters
        val colWidth = SQRT3 * radiusMeters
        val cells = mutableListOf<HexCell>()
        for (r in floor(y0 / rowHeight).toInt()..ceil(y1 / rowHeight).toInt()) {
            val qMin = floor(x0 / colWidth - r / 2.0).toInt()
            val qMax = ceil(x1 / colWidth - r / 2.0).toInt()
            for (q in qMin..qMax) {
                val cell = HexCell(q, r)
                val cx = centerX(cell)
                val cy = centerY(cell)
                if (cx in x0..x1 && cy in y0..y1) cells += cell
            }
        }
        return cells
    }

    private fun cubeRound(qf: Double, rf: Double): HexCell {
        val sf = -qf - rf
        var q = round(qf)
        var r = round(rf)
        val s = round(sf)
        val dq = abs(q - qf)
        val dr = abs(r - rf)
        val ds = abs(s - sf)
        if (dq > dr && dq > ds) {
            q = -r - s
        } else if (dr > ds) {
            r = -q - s
        }
        return HexCell(q.toInt(), r.toInt())
    }

    companion object {
        private val SQRT3 = sqrt(3.0)

        /** Picks a hexagon size so that roughly [cellsAcross] hexagons span the viewport width. */
        fun forViewport(box: BoundingBox, cellsAcross: Int = 34, minRadius: Double = 60.0, maxRadius: Double = 900.0): HexGrid {
            val radius = (box.widthMeters / (cellsAcross * SQRT3)).coerceIn(minRadius, maxRadius)
            // Snap the origin to a coarse lattice so cells stay put while panning.
            val snap = 0.05
            val origin = LatLng(floor(box.center.lat / snap) * snap, floor(box.center.lng / snap) * snap)
            return HexGrid(origin, snapRadius(radius))
        }

        /** Rounds to a small set of sizes so zooming slightly doesn't reshuffle every cell. */
        private fun snapRadius(radius: Double): Double {
            val steps = doubleArrayOf(60.0, 90.0, 130.0, 180.0, 250.0, 350.0, 500.0, 700.0, 900.0)
            return steps.firstOrNull { it >= radius } ?: steps.last()
        }
    }
}
