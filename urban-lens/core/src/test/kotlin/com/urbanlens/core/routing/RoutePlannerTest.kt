package com.urbanlens.core.routing

import com.urbanlens.core.air.AirSample
import com.urbanlens.core.air.PollutionField
import com.urbanlens.core.air.PollutionHotspot
import com.urbanlens.core.geo.GeoMath
import com.urbanlens.core.geo.LatLng
import com.urbanlens.core.geo.Shape
import com.urbanlens.core.net.Http
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePlannerTest {
    private val origin = LatLng(13.0600, 80.2500)
    private val destination = GeoMath.offset(origin, 0.0, 2_000.0)

    /** A straight route along the direct line and a detour 500 m north of it. */
    private val direct = listOf(origin, destination)
    private val northern = listOf(
        origin,
        GeoMath.offset(origin, 500.0, 0.0),
        GeoMath.offset(destination, 500.0, 0.0),
        destination,
    )

    private fun osrm(vararg routes: Pair<List<LatLng>, Double>): String = buildString {
        append("""{"code":"Ok","routes":[""")
        append(
            routes.joinToString(",") { (path, duration) ->
                val coords = path.joinToString(",") { "[${it.lng},${it.lat}]" }
                """{"geometry":{"type":"LineString","coordinates":[$coords]},"distance":${GeoMath.pathLength(path)},"duration":$duration}"""
            },
        )
        append("]}")
    }

    private val fakeHttp = object : Http {
        override suspend fun get(url: String): String =
            if (url.count { it == ';' } == 1) osrm(direct to 1_500.0, northern to 1_900.0) else osrm(direct to 1_500.0)

        override suspend fun postForm(url: String, form: Map<String, String>): String = error("unused")
    }

    /** Smoky along the direct line, clean 500 m north; a construction site sits on the direct line. */
    private fun context(): ExposureContext {
        val samples = listOf(
            AirSample(GeoMath.interpolate(origin, destination, 0.5), 120, 45.0, 80.0),
            AirSample(GeoMath.offset(GeoMath.interpolate(origin, destination, 0.5), 600.0, 0.0), 40, 9.0, 20.0),
        )
        val site = Shape.point(GeoMath.interpolate(origin, destination, 0.5))
        val field = PollutionField(samples, listOf(PollutionHotspot.constructionDust("Road works", site, 0.6)))
        return ExposureContext(
            air = field::at,
            crowd = { points -> points.map { p -> if (p.lat > origin.lat + 0.002) 10 else 60 } },
            obstacles = listOf(RouteObstacle("osm:way/1", "Road works", site)),
        )
    }

    @Test
    fun labelsFastestCleanestQuietestAndConstructionFree() = runTest {
        val options = RoutePlanner(fakeHttp).plan(origin, destination, TravelMode.WALK, context())
        assertEquals(2, options.size)

        val fastest = options[0]
        assertEquals(setOf(RouteTag.FASTEST), fastest.tags)
        assertEquals(listOf("Road works"), fastest.score.obstacles)
        assertEquals(0, fastest.exposureVsFastestPct)

        val alternative = options[1]
        assertTrue(alternative.tags.containsAll(listOf(RouteTag.CLEANEST, RouteTag.QUIETEST, RouteTag.AVOIDS_CONSTRUCTION)))
        assertTrue(alternative.exposureVsFastestPct > 0)
        assertTrue(alternative.score.obstacles.isEmpty())
    }

    @Test
    fun detoursSitOnEitherSideOfTheDirectLine() {
        val vias = RoutePlanner.detourWaypoints(origin, destination)
        assertEquals(2, vias.size)
        val mid = GeoMath.interpolate(origin, destination, 0.5)
        vias.forEach { assertEquals(440.0, GeoMath.distanceMeters(mid, it), 5.0) }
        assertTrue(vias[0].lat > mid.lat != vias[1].lat > mid.lat)
        assertTrue(RoutePlanner.detourWaypoints(origin, GeoMath.offset(origin, 100.0, 0.0)).isEmpty())
    }

    @Test
    fun dedupeDropsRetracedRoutes() {
        val routes = listOf(
            RouteGeometry(direct, 2_000.0, 1_500.0),
            RouteGeometry(listOf(origin, GeoMath.interpolate(origin, destination, 0.5), destination), 2_000.0, 1_510.0),
            RouteGeometry(northern, 3_000.0, 1_900.0),
        )
        assertEquals(2, RoutePlanner.dedupe(routes).size)
    }

    @Test
    fun cyclingInhalesMoreThanDrivingForTheSameTrip() {
        val scorer = RouteScorer(context())
        val route = RouteGeometry(direct, 2_000.0, 600.0)
        assertTrue(scorer.score(route, TravelMode.CYCLE).exposureDose > scorer.score(route, TravelMode.DRIVE).exposureDose)
    }
}
