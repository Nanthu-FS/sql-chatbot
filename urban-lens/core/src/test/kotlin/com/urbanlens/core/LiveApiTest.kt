package com.urbanlens.core

import com.urbanlens.core.air.PollutionField
import com.urbanlens.core.data.CityDataRepository
import com.urbanlens.core.data.TileKey
import com.urbanlens.core.geo.BoundingBox
import com.urbanlens.core.geo.LatLng
import com.urbanlens.core.net.Http
import com.urbanlens.core.net.HttpException
import com.urbanlens.core.routing.ExposureContext
import com.urbanlens.core.routing.RoutePlanner
import com.urbanlens.core.routing.TravelMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Calls the real public APIs the app uses. Skipped unless LIVE_API_TESTS=1, because it needs
 * the network and depends on free servers being up.
 */
class LiveApiTest {
    private val chennaiCentral = LatLng(13.0827, 80.2707)
    private val marinaBeach = LatLng(13.0500, 80.2824)

    private val http = object : Http {
        private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()

        override suspend fun get(url: String): String =
            send(HttpRequest.newBuilder(URI(url)).GET())

        override suspend fun postForm(url: String, form: Map<String, String>): String {
            val body = form.entries.joinToString("&") { (k, v) ->
                URLEncoder.encode(k, Charsets.UTF_8) + "=" + URLEncoder.encode(v, Charsets.UTF_8)
            }
            return send(
                HttpRequest.newBuilder(URI(url))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)),
            )
        }

        private suspend fun send(builder: HttpRequest.Builder): String = withContext(Dispatchers.IO) {
            val request = builder
                .header("User-Agent", "UrbanLens-CI/0.1 (https://github.com/Nanthu-FS/sql-chatbot)")
                .timeout(Duration.ofSeconds(45))
                .build()
            val started = System.nanoTime()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            val ms = (System.nanoTime() - started) / 1_000_000
            println("LIVE ${request.method()} ${request.uri().host} -> ${response.statusCode()} in $ms ms, ${response.body().length} bytes")
            if (response.statusCode() !in 200..299) throw HttpException(response.statusCode(), "HTTP ${response.statusCode()}")
            response.body()
        }
    }

    @Before
    fun onlyWhenEnabled() {
        assumeTrue("set LIVE_API_TESTS=1 to run", System.getenv("LIVE_API_TESTS") == "1")
    }

    @Test
    fun overpassTileAroundChennaiCentral() = runBlocking {
        val repo = CityDataRepository(http)
        val tile = TileKey.covering(BoundingBox.around(chennaiCentral, 100.0)).first()
        val data = repo.loadTile(tile)
        println("LIVE overpass tile $tile: ${data.sites.size} construction sites, ${data.places.size} places")
        println("LIVE place kinds: " + data.places.groupingBy { it.category }.eachCount())
        assertTrue(data.places.isNotEmpty())
    }

    @Test
    fun eachOverpassServerAnswers() = runBlocking {
        val box = BoundingBox.around(chennaiCentral, 300.0)
        for (endpoint in com.urbanlens.core.net.Overpass.ENDPOINTS) {
            val result = runCatching {
                com.urbanlens.core.net.Overpass.parse(
                    http.postForm(endpoint, mapOf("data" to com.urbanlens.core.net.Overpass.query(box))),
                )
            }
            println("LIVE $endpoint: " + result.fold({ "ok, ${it.places.size} places" }, { "FAILED ${it.message}" }))
        }
    }

    @Test
    fun airAndWeather() = runBlocking {
        val repo = CityDataRepository(http)
        val samples = repo.air(BoundingBox.around(chennaiCentral, 2_000.0))
        println("LIVE air: " + samples.map { it.usAqi })
        val weather = repo.weather(chennaiCentral)
        println("LIVE weather: $weather")
        assertTrue(samples.any { it.usAqi != null })
        assertTrue(weather != null)
    }

    @Test
    fun walkingRoutesAreRanked() = runBlocking {
        val repo = CityDataRepository(http)
        val field = PollutionField(repo.air(BoundingBox.of(listOf(chennaiCentral, marinaBeach))))
        val options = RoutePlanner(http).plan(
            chennaiCentral,
            marinaBeach,
            TravelMode.WALK,
            ExposureContext(air = field::at, crowd = { points -> points.map { 0 } }, obstacles = emptyList()),
        )
        options.forEach {
            println("LIVE route ${it.id}: ${it.distanceMeters.toInt()} m, ${(it.durationSeconds / 60).toInt()} min, tags=${it.tags}, avgAqi=${it.score.avgAqi}")
        }
        assertTrue(options.isNotEmpty())
    }
}
