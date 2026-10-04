package com.urbanlens.core.net

import com.urbanlens.core.construction.ConstructionKind
import com.urbanlens.core.crowd.PlaceCategory
import com.urbanlens.core.geo.BoundingBox
import com.urbanlens.core.geo.LatLng
import com.urbanlens.core.routing.TravelMode
import com.urbanlens.core.weather.WeatherCondition
import com.urbanlens.core.weather.WeatherNow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {

    @Test
    fun openMeteoAirSingleLocation() {
        val json = """
            {"latitude":13.1,"longitude":80.3,"current_units":{"us_aqi":"USAQI"},
             "current":{"time":"2026-10-04T19:00","interval":3600,"us_aqi":57,"pm2_5":14.2,"pm10":31.5,
                        "nitrogen_dioxide":18.0,"ozone":40.1,"carbon_monoxide":310.0}}
        """.trimIndent()
        val point = LatLng(13.0827, 80.2707)
        val samples = OpenMeteoAirQuality.parse(json, listOf(point))
        assertEquals(1, samples.size)
        assertEquals(point, samples[0].location)
        assertEquals(57, samples[0].usAqi)
        assertEquals(14.2, samples[0].pm25!!, 0.0)
    }

    @Test
    fun openMeteoAirMultipleLocationsKeepOrderAndNulls() {
        val json = """
            [{"latitude":13.0,"longitude":80.2,"current":{"us_aqi":40,"pm2_5":8.0,"pm10":20.0}},
             {"latitude":13.1,"longitude":80.3,"current":{"us_aqi":null,"pm2_5":null,"pm10":22.0}}]
        """.trimIndent()
        val points = listOf(LatLng(13.0, 80.2), LatLng(13.1, 80.3))
        val samples = OpenMeteoAirQuality.parse(json, points)
        assertEquals(2, samples.size)
        assertEquals(40, samples[0].usAqi)
        assertNull(samples[1].usAqi)
        assertEquals(22.0, samples[1].pm10!!, 0.0)
    }

    @Test
    fun openMeteoAirUrlUsesDotDecimalsAndCommaLists() {
        val url = OpenMeteoAirQuality.url(listOf(LatLng(13.08, 80.27), LatLng(13.1, 80.3)))
        assertTrue(url, url.contains("latitude=13.08000,13.10000"))
        assertTrue(url, url.contains("longitude=80.27000,80.30000"))
    }

    @Test
    fun openMeteoWeather() {
        val json = """{"current":{"temperature_2m":28.4,"wind_speed_10m":13.0,"wind_direction_10m":337.0,"weather_code":2,"is_day":1}}"""
        val weather = OpenMeteoWeather.parse(json)!!
        assertEquals(28.4, weather.temperatureC, 0.0)
        assertEquals("NNW", weather.windCompass)
        assertEquals(WeatherCondition.PARTLY_CLOUDY, weather.condition)
        assertTrue(weather.isDay)
    }

    @Test
    fun compassPoints() {
        assertEquals("N", WeatherNow.compassPoint(0.0))
        assertEquals("N", WeatherNow.compassPoint(359.0))
        assertEquals("E", WeatherNow.compassPoint(90.0))
        assertEquals("SW", WeatherNow.compassPoint(225.0))
        assertEquals("N", WeatherNow.compassPoint(-5.0))
    }

    @Test
    fun overpassSplitsConstructionFromPlaces() {
        val json = """
            {"elements":[
              {"type":"way","id":11,"tags":{"highway":"construction","construction":"primary","name":"Anna Salai"},
               "geometry":[{"lat":13.05,"lon":80.25},{"lat":13.06,"lon":80.26}]},
              {"type":"way","id":12,"tags":{"landuse":"construction","construction":"subway","opening_date":"2027"},
               "geometry":[{"lat":13.0,"lon":80.0},{"lat":13.0,"lon":80.01},{"lat":13.01,"lon":80.01},{"lat":13.0,"lon":80.0}]},
              {"type":"node","id":21,"lat":13.07,"lon":80.27,"tags":{"amenity":"cafe","name":"Saint's Dark Coffee"}},
              {"type":"way","id":22,"center":{"lat":13.08,"lon":80.28},"tags":{"shop":"mall","name":"Express Avenue"}},
              {"type":"node","id":23,"lat":13.09,"lon":80.29,"tags":{"amenity":"bench"}},
              {"type":"node","id":21,"lat":13.07,"lon":80.27,"tags":{"amenity":"cafe","name":"Saint's Dark Coffee"}}
            ]}
        """.trimIndent()
        val data = Overpass.parse(json)
        assertEquals(2, data.sites.size)
        val road = data.sites[0]
        assertEquals("osm:way/11", road.id)
        assertEquals(ConstructionKind.ROAD, road.kind)
        assertEquals("Anna Salai", road.title)
        assertFalse(road.shape.isArea)
        val metro = data.sites[1]
        assertEquals(ConstructionKind.RAIL, metro.kind)
        assertTrue(metro.shape.isArea)
        assertEquals("2027", metro.expectedEnd)

        assertEquals(2, data.places.size)
        assertEquals(PlaceCategory.CAFE, data.places[0].category)
        assertEquals(PlaceCategory.MALL, data.places[1].category)
        assertEquals(LatLng(13.08, 80.28), data.places[1].location)
    }

    @Test
    fun overpassQueryUsesSouthWestNorthEastOrder() {
        val q = Overpass.query(BoundingBox(13.0, 80.2, 13.1, 80.3))
        assertTrue(q, q.contains("(13.00000,80.20000,13.10000,80.30000)"))
        assertTrue(q.startsWith("[out:json]"))
    }

    @Test
    fun osrmParsesRoutesAsLatLng() {
        val json = """
            {"code":"Ok","routes":[
              {"geometry":{"type":"LineString","coordinates":[[80.27,13.08],[80.28,13.09]]},"distance":1500.5,"duration":1100.0},
              {"geometry":{"type":"LineString","coordinates":[[80.27,13.08],[80.275,13.10],[80.28,13.09]]},"distance":1900.0,"duration":1400.0}
            ],"waypoints":[]}
        """.trimIndent()
        val routes = Osrm.parse(json)
        assertEquals(2, routes.size)
        assertEquals(LatLng(13.08, 80.27), routes[0].path.first())
        assertEquals(1100.0, routes[0].durationSeconds, 0.0)
    }

    @Test(expected = RoutingException::class)
    fun osrmErrorCodesThrow() {
        Osrm.parse("""{"code":"NoRoute","message":"Impossible route between points"}""")
    }

    @Test
    fun osrmUrlUsesLngLatAndProfile() {
        val url = Osrm.url(TravelMode.CYCLE, listOf(LatLng(13.08, 80.27), LatLng(13.09, 80.28)), alternatives = 2)
        assertTrue(url, url.startsWith("https://routing.openstreetmap.de/routed-bike/route/v1/driving/80.27000,13.08000;80.28000,13.09000?"))
        assertTrue(url, url.contains("alternatives=2"))
        assertTrue(url, url.contains("geometries=geojson"))
    }
}
