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
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import kotlin.math.floor

/** A fixed square of the map; OpenStreetMap data is fetched and cached one tile at a time. */
data class TileKey(val x: Int, val y: Int) {
    val bounds: BoundingBox get() = BoundingBox(y * SIZE, x * SIZE, (y + 1) * SIZE, (x + 1) * SIZE)
    val center: LatLng get() = LatLng((y + 0.5) * SIZE, (x + 0.5) * SIZE)

    companion object {
        /** Degrees; about 5.5 km, small enough for a quick query on a free server. */
        const val SIZE = 0.05

        fun covering(box: BoundingBox): List<TileKey> {
            val x0 = floor(box.west / SIZE).toInt()
            val x1 = floor(box.east / SIZE).toInt()
            val y0 = floor(box.south / SIZE).toInt()
            val y1 = floor(box.north / SIZE).toInt()
            return buildList {
                for (y in y0..y1) for (x in x0..x1) add(TileKey(x, y))
            }
        }
    }
}

/**
 * Fetches and caches open data.
 *
 * OpenStreetMap (Overpass) data is loaded per [TileKey], one request at a time, with retries and
 * fallback servers: public Overpass servers rate-limit each client, and firing a new query on
 * every pan is the quickest way to get "429 Too Many Requests".
 */
class CityDataRepository(
    private val http: Http,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Pause before each retry; one more attempt is made than there are delays. */
    private val retryDelaysMillis: List<Long> = listOf(3_000L, 5_000L),
) {
    private class Cached<T>(val box: BoundingBox, val value: T, val atMillis: Long)
    private class Tile(val data: AreaData, val fetchedAtMillis: Long)

    private val lock = Any()
    private val tiles = LinkedHashMap<TileKey, Tile>()
    private val failedAt = HashMap<TileKey, Long>()
    private val overpass = Mutex()

    @Volatile private var airCache: Cached<List<AirSample>>? = null
    @Volatile private var weatherCache: Cached<WeatherNow>? = null

    /** Everything already cached for the tiles under [box], without touching the network. */
    fun cachedArea(box: BoundingBox): AreaData {
        val parts = synchronized(lock) { TileKey.covering(box).mapNotNull { tiles[it]?.data } }
        return AreaData(
            sites = parts.flatMap { it.sites }.distinctBy { it.id },
            places = parts.flatMap { it.places }.distinctBy { it.id },
        )
    }

    /** Tiles under [box] that need (re)loading, nearest to its center first. Recently failed tiles wait. */
    fun missingTiles(box: BoundingBox): List<TileKey> {
        val now = clock()
        val center = box.center
        return synchronized(lock) {
            TileKey.covering(box).filter { key ->
                val fresh = tiles[key]?.let { now - it.fetchedAtMillis < AREA_TTL_MILLIS } ?: false
                val coolingDown = failedAt[key]?.let { now - it < FAILURE_COOLDOWN_MILLIS } ?: false
                !fresh && !coolingDown
            }
        }.sortedBy { GeoMath.approxDistanceMeters(it.center, center) }
    }

    /** Whether a tile under [box] failed recently and is waiting to be retried. */
    fun hasFailures(box: BoundingBox): Boolean = synchronized(lock) {
        TileKey.covering(box).any { it in failedAt }
    }

    /** Lets failed tiles be retried right away. */
    fun clearFailures() = synchronized(lock) { failedAt.clear() }

    /** Loads one tile from Overpass (serialized app-wide) and caches it. */
    suspend fun loadTile(key: TileKey): AreaData {
        val data = try {
            overpass.withLock { fetchArea(key.bounds) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            synchronized(lock) { failedAt[key] = clock() }
            throw e
        }
        synchronized(lock) {
            failedAt.remove(key)
            tiles.remove(key)
            tiles[key] = Tile(data, clock())
            while (tiles.size > MAX_TILES) tiles.remove(tiles.keys.first())
        }
        return data
    }

    /** A 4x4 grid of air samples around the viewport. */
    suspend fun air(viewport: BoundingBox, forceRefresh: Boolean = false): List<AirSample> {
        airCache?.takeIf { !forceRefresh && it.box.contains(viewport) && isFresh(it.atMillis, AIR_TTL_MILLIS) }?.let { return it.value }
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
            !forceRefresh && isFresh(it.atMillis, WEATHER_TTL_MILLIS) && GeoMath.distanceMeters(it.box.center, point) < 3_000
        }?.let { return it.value }
        val weather = OpenMeteoWeather.parse(http.get(OpenMeteoWeather.url(point))) ?: return null
        weatherCache = Cached(BoundingBox(point.lat, point.lng, point.lat, point.lng), weather, clock())
        return weather
    }

    /** Tries the Overpass servers in [Overpass.ATTEMPTS] order, backing off between attempts. */
    private suspend fun fetchArea(box: BoundingBox): AreaData {
        var lastError: Exception? = null
        for (attempt in 0..retryDelaysMillis.size) {
            val endpoint = Overpass.ATTEMPTS[attempt.coerceAtMost(Overpass.ATTEMPTS.lastIndex)]
            try {
                return Overpass.parse(http.postForm(endpoint, mapOf("data" to Overpass.query(box))))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                retryDelaysMillis.getOrNull(attempt)?.let { delay(it) }
            }
        }
        throw lastError ?: IOException("OpenStreetMap data is unavailable")
    }

    private fun isFresh(atMillis: Long, ttl: Long) = clock() - atMillis < ttl

    companion object {
        const val AREA_TTL_MILLIS = 30 * 60_000L
        const val AIR_TTL_MILLIS = 20 * 60_000L
        const val WEATHER_TTL_MILLIS = 15 * 60_000L
        const val FAILURE_COOLDOWN_MILLIS = 30_000L
        private const val MAX_TILES = 48

        /** Wider than this, the view needs too many tiles for a free public server. */
        const val MAX_AREA_WIDTH_METERS = 6_000.0
    }
}
