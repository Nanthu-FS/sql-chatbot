package com.urbanlens.core.crowd

import com.urbanlens.core.geo.GeoMath
import com.urbanlens.core.geo.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CrowdModelTest {
    private val here = LatLng(13.0827, 80.2707)
    private val mall = Place("osm:way/1", "Express Avenue", PlaceCategory.MALL, here)
    private val model = CrowdModel(listOf(mall))

    // 2026-10-07 is a Wednesday, 2026-10-11 a Sunday.
    private val wednesday = LocalDate.of(2026, 10, 7)
    private val sunday = LocalDate.of(2026, 10, 11)

    @Test
    fun mallIsBusierInTheEveningThanTheMorning() {
        val morning = model.busyness(mall, wednesday.atTime(9, 0))
        val evening = model.busyness(mall, wednesday.atTime(19, 30))
        assertTrue("morning=$morning evening=$evening", evening > morning + 0.5)
    }

    @Test
    fun weekendsAreBusierForMalls() {
        val weekday = model.hourlyBusyness(mall, wednesday).sum()
        val weekend = model.hourlyBusyness(mall, sunday).sum()
        assertTrue(weekend > weekday)
    }

    @Test
    fun densityFallsOffWithDistance() {
        val time = wednesday.atTime(19, 0)
        val levels = model.levels(
            listOf(here, GeoMath.offset(here, 200.0, 0.0), GeoMath.offset(here, 2_000.0, 0.0)),
            time,
        )
        assertTrue(levels[0] > levels[1])
        assertTrue(levels[1] > levels[2])
        assertEquals(0, levels[2])
        assertTrue(levels.all { it in 0..100 })
    }

    @Test
    fun busynessIsDeterministic() {
        val t = LocalDateTime.of(2026, 10, 7, 18, 20)
        assertEquals(model.busyness(mall, t), CrowdModel(listOf(mall)).busyness(mall, t), 0.0)
    }

    @Test
    fun bestHourIsTheQuietestRemainingHour() {
        val from = wednesday.atTime(10, 15)
        val best = model.bestHourToVisit(mall, from)!!
        val curve = model.hourlyBusyness(mall, wednesday)
        assertTrue(best in 11 until 22)
        assertEquals((11 until 22).minOf { curve[it] }, curve[best], 0.0)
        assertNull(model.bestHourToVisit(mall, wednesday.atTime(21, 30)))
    }

    @Test
    fun crowdLevelBands() {
        assertEquals(CrowdLevel.QUIET, CrowdLevel.of(0))
        assertEquals(CrowdLevel.MODERATE, CrowdLevel.of(25))
        assertEquals(CrowdLevel.BUSY, CrowdLevel.of(74))
        assertEquals(CrowdLevel.PACKED, CrowdLevel.of(100))
    }

    @Test
    fun osmTagsMapToCategories() {
        assertEquals(PlaceCategory.MALL, PlaceCategory.fromOsmTags(mapOf("shop" to "mall")))
        assertEquals(PlaceCategory.TRANSIT_STATION, PlaceCategory.fromOsmTags(mapOf("railway" to "station")))
        assertEquals(PlaceCategory.BEACH, PlaceCategory.fromOsmTags(mapOf("natural" to "beach")))
        assertNull(PlaceCategory.fromOsmTags(mapOf("amenity" to "bench")))
    }

    @Test
    fun comfortPrefersQuietCleanPlaces() {
        val calm = Comfort.score(crowdLevel = 10, aqi = 30, constructionNearby = 0)
        val hectic = Comfort.score(crowdLevel = 85, aqi = 160, constructionNearby = 2)
        assertTrue(calm > 80)
        assertTrue(hectic < 20)
    }
}
