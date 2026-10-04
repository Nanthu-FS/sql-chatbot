package com.urbanlens.core.geo

import com.urbanlens.core.grid.HexCell
import com.urbanlens.core.grid.HexGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class GeoTest {
    private val chennai = LatLng(13.0827, 80.2707)

    @Test
    fun oneDegreeOfLatitudeIsAbout111Km() {
        val d = GeoMath.distanceMeters(LatLng(13.0, 80.0), LatLng(14.0, 80.0))
        assertEquals(111_195.0, d, 50.0)
    }

    @Test
    fun approxDistanceMatchesHaversineAtCityScale() {
        val other = GeoMath.offset(chennai, 3_000.0, 4_000.0)
        assertEquals(5_000.0, GeoMath.distanceMeters(chennai, other), 10.0)
        assertEquals(5_000.0, GeoMath.approxDistanceMeters(chennai, other), 10.0)
    }

    @Test
    fun samplePathSpacesPointsEvenlyAcrossSegments() {
        val a = chennai
        val b = GeoMath.offset(a, 0.0, 250.0)
        val c = GeoMath.offset(b, 250.0, 0.0)
        val samples = GeoMath.samplePath(listOf(a, b, c), 100.0)
        // 0, 100, 200, 300, 400, then the end point at 500.
        assertEquals(6, samples.size)
        assertEquals(a, samples.first())
        assertEquals(c, samples.last())
        for (i in 1 until samples.size) {
            val step = GeoMath.pathLength(listOf(samples[i - 1], samples[i]))
            assertTrue("step $i was $step", step <= 100.5)
        }
    }

    @Test
    fun distanceToSegmentHandlesEndsAndMiddle() {
        val a = chennai
        val b = GeoMath.offset(a, 0.0, 1_000.0)
        val above = GeoMath.offset(GeoMath.interpolate(a, b, 0.5), 30.0, 0.0)
        assertEquals(30.0, GeoMath.distanceToSegmentMeters(above, a, b), 0.5)
        val beyond = GeoMath.offset(b, 0.0, 40.0)
        assertEquals(40.0, GeoMath.distanceToSegmentMeters(beyond, a, b), 0.5)
    }

    @Test
    fun pointInPolygon() {
        val square = listOf(
            GeoMath.offset(chennai, -100.0, -100.0),
            GeoMath.offset(chennai, -100.0, 100.0),
            GeoMath.offset(chennai, 100.0, 100.0),
            GeoMath.offset(chennai, 100.0, -100.0),
            GeoMath.offset(chennai, -100.0, -100.0),
        )
        assertTrue(GeoMath.isInsidePolygon(chennai, square))
        assertFalse(GeoMath.isInsidePolygon(GeoMath.offset(chennai, 150.0, 0.0), square))
        val area = Shape(square, isArea = true)
        assertEquals(0.0, area.distanceMeters(chennai), 0.0)
        assertEquals(50.0, area.distanceMeters(GeoMath.offset(chennai, 150.0, 0.0)), 1.0)
    }

    @Test
    fun boundingBoxGridAndExpansion() {
        val box = BoundingBox.around(chennai, 1_000.0)
        assertEquals(2_000.0, box.widthMeters, 5.0)
        assertTrue(box.contains(chennai))
        val grid = box.gridCenters(4, 4)
        assertEquals(16, grid.size)
        assertTrue(grid.all(box::contains))
        assertTrue(box.expandedBy(0.25).contains(box))
    }

    @Test
    fun hexCellRoundTripsThroughItsCenter() {
        val grid = HexGrid(chennai, 150.0)
        for (q in -5..5) for (r in -5..5) {
            val cell = HexCell(q, r)
            assertEquals(cell, grid.cellAt(grid.center(cell)))
        }
    }

    @Test
    fun hexNeighboursAreRootThreeRadiiApartAndCornersAreOneRadiusOut() {
        val grid = HexGrid(chennai, 200.0)
        val c = HexCell(2, -1)
        val neighbour = HexCell(3, -1)
        val spacing = GeoMath.distanceMeters(grid.center(c), grid.center(neighbour))
        assertEquals(200.0 * sqrt(3.0), spacing, 1.0)
        grid.corners(c).forEach { assertEquals(200.0, GeoMath.distanceMeters(grid.center(c), it), 1.0) }
    }

    @Test
    fun hexCellsCoverTheViewport() {
        val box = BoundingBox.around(chennai, 2_000.0)
        val grid = HexGrid.forViewport(box)
        val cells = grid.cellsCovering(box).toSet()
        val probes = box.gridCenters(9, 9)
        probes.forEach { assertTrue("no cell for $it", grid.cellAt(it) in cells) }
    }
}
