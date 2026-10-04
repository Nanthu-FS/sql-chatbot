package com.urbanlens.core.data

import com.urbanlens.core.air.AirSample
import com.urbanlens.core.geo.BoundingBox
import com.urbanlens.core.geo.GeoMath
import com.urbanlens.core.geo.LatLng
import com.urbanlens.core.net.AreaData
import com.urbanlens.core.net.Http
import com.urbanlens.core.net.OpenMeteoAirQuality
import com.urbanlens.core.net.OpenMeteoWeather
import com.urbanlens.core.net.Overpass
import com.urbanlens.core.weather.WeatherNow
import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * Fetches and caches open data for the visible area. Each cache entry covers a box a bit larger
 * than the viewport, so small pans and zooms are served from memory.
 */
class CityDataRepository(
    private val http: Http,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Cached<T>(val box: BoundingBox, val value: T, val atMillis: Long)

    @Volatile private var areaCache: Cached<AreaData>? = null
    @Volatile private var airCache: Cached<List<AirSample>>? = null
    @Volatile private var weatherCache: Cached<WeatherNow>? = null

    /** Construction sites and places. Callers should skip this when the viewport is wider than [MAX_AREA_WIDTH_METERS]. */
    suspend fun area(viewport: BoundingBox, forceRefresh: Boolean = false): AreaData {
        areaCache?.takeIf { !forceRefresh && it.box.contains(viewport) && isFresh(it, AREA_TTL_MILLIS) }?.let { return it.value }
        val box = viewport.expandedBy(0.25)
        val data = fetchArea(box)
        areaCache = Cached(box, data, clock())
        return data
    }

    /** A 4x4 grid of air samples around the viewport. */
    suspend fun air(viewport: BoundingBox, forceRefresh: Boolean = false): List<AirSample> {
        airCache?.takeIf { !forceRefresh && it.box.contains(viewport) && isFresh(it, AIR_TTL_MILLIS) }?.let { return it.value }
        val box = viewport.expandedBy(0.3)
        val samples = airAt(box.gridCenters(4, 4).map { it.copy(lat = it.lat.coerceIn(-85.0, 85.0)) })
        airCache = Cached(box, samples, clock())
        return samples
    }

    /** Uncached air samples at exact points (used by background alerts). */
    suspend fun airAt(points: List<LatLng>): List<AirSample> =
        OpenMeteoAirQuality.parse(http.get(OpenMeteoAirQuality.url(points)), points)

    suspend fun weather(point: LatLng, forceRefresh: Boolean = false): WeatherNow? {
        weatherCache?.takeIf {
            !forceRefresh && isFresh(it, WEATHER_TTL_MILLIS) && GeoMath.distanceMeters(it.box.center, point) < 3_000
        }?.let { return it.value }
        val weather = OpenMeteoWeather.parse(http.get(OpenMeteoWeather.url(point))) ?: return null
        weatherCache = Cached(BoundingBox(point.lat, point.lng, point.lat, point.lng), weather, clock())
        return weather
    }

    private suspend fun fetchArea(box: BoundingBox): AreaData {
        var lastError: Exception? = null
        for (endpoint in Overpass.ENDPOINTS) {
            try {
                return Overpass.parse(http.postForm(endpoint, mapOf("data" to Overpass.query(box))))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("OpenStreetMap data is unavailable")
    }

    private fun isFresh(entry: Cached<*>, ttl: Long) = clock() - entry.atMillis < ttl

    companion object {
        const val AREA_TTL_MILLIS = 30 * 60_000L
        const val AIR_TTL_MILLIS = 20 * 60_000L
        const val WEATHER_TTL_MILLIS = 15 * 60_000L

        /** Wider than this, the OpenStreetMap query gets too heavy for a free public server. */
        const val MAX_AREA_WIDTH_METERS = 7_000.0
    }
}
