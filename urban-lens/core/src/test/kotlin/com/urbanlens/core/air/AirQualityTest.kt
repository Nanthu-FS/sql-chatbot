package com.urbanlens.core.air

import com.urbanlens.core.geo.GeoMath
import com.urbanlens.core.geo.LatLng
import com.urbanlens.core.geo.Shape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AirQualityTest {
    private val here = LatLng(13.0827, 80.2707)

    @Test
    fun pm25BreakpointsMatchEpaTable() {
        assertEquals(0, AqiCalculator.fromPm25(0.0))
        assertEquals(50, AqiCalculator.fromPm25(9.0))
        assertEquals(51, AqiCalculator.fromPm25(9.1))
        assertEquals(56, AqiCalculator.fromPm25(12.0))
        assertEquals(100, AqiCalculator.fromPm25(35.4))
        assertEquals(151, AqiCalculator.fromPm25(55.5))
        assertEquals(500, AqiCalculator.fromPm25(900.0))
    }

    @Test
    fun pm10BreakpointsMatchEpaTable() {
        assertEquals(50, AqiCalculator.fromPm10(54.0))
        assertEquals(100, AqiCalculator.fromPm10(154.9))
        assertEquals(101, AqiCalculator.fromPm10(155.0))
    }

    @Test
    fun categoriesFollowAqiBands() {
        assertEquals(AqiCategory.GOOD, AqiCategory.of(0))
        assertEquals(AqiCategory.GOOD, AqiCategory.of(50))
        assertEquals(AqiCategory.MODERATE, AqiCategory.of(51))
        assertEquals(AqiCategory.UNHEALTHY_SENSITIVE, AqiCategory.of(150))
        assertEquals(AqiCategory.UNHEALTHY, AqiCategory.of(151))
        assertEquals(AqiCategory.HAZARDOUS, AqiCategory.of(450))
    }

    @Test
    fun fieldInterpolatesBetweenSamples() {
        val west = GeoMath.offset(here, 0.0, -2_000.0)
        val east = GeoMath.offset(here, 0.0, 2_000.0)
        val field = PollutionField(
            listOf(AirSample(west, 40, 10.0, 30.0), AirSample(east, 80, 25.0, 60.0)),
        )
        assertEquals(40, field.at(west)!!.aqi)
        assertEquals(60, field.at(here)!!.aqi)
        assertEquals(17.5, field.at(here)!!.pm25!!, 0.01)
    }

    @Test
    fun emptyFieldHasNoEstimate() {
        assertNull(PollutionField(emptyList()).at(here))
        assertNull(PollutionField(listOf(AirSample(here, null, null, null))).at(here))
    }

    @Test
    fun constructionDustRaisesAqiNearbyOnly() {
        val site = PollutionHotspot.constructionDust("Building construction", Shape.point(here), 1.0)
        val field = PollutionField(listOf(AirSample(here, 60, 15.0, 40.0)), listOf(site))
        val atSite = field.at(here)!!
        val far = field.at(GeoMath.offset(here, 1_500.0, 0.0))!!
        assertTrue("expected a local bump, got ${atSite.localBoost}", atSite.localBoost > 5)
        assertEquals(listOf("Building construction"), atSite.localSources)
        assertEquals(0, far.localBoost)
        assertEquals(60, far.aqi)
    }
}
