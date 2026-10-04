package com.urbanlens.core.data

import com.urbanlens.core.geo.BoundingBox
import com.urbanlens.core.geo.LatLng
import com.urbanlens.core.net.Http
import com.urbanlens.core.net.HttpException
import com.urbanlens.core.net.Overpass
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CityDataRepositoryTest {
    private var now = 1_000_000L

    /** Answers Overpass with one café per call, or fails the first [failures] calls. */
    private inner class FakeOverpass(var failures: Int = 0, val failWith: () -> Exception = { HttpException(429, "Too Many Requests") }) : Http {
        val endpoints = mutableListOf<String>()
        private var calls = 0

        override suspend fun get(url: String): String = error("unused")

        override suspend fun postForm(url: String, form: Map<String, String>): String {
            endpoints += url
            calls++
            if (calls <= failures) throw failWith()
            return """{"elements":[
                {"type":"node","id":$calls,"lat":13.08,"lon":80.27,"tags":{"amenity":"cafe","name":"Cafe $calls"}},
                {"type":"node","id":999,"lat":13.081,"lon":80.271,"tags":{"amenity":"cafe","name":"Shared"}}
            ]}"""
        }
    }

    private fun repo(http: Http) = CityDataRepository(http, clock = { now }, retryDelaysMillis = listOf(10L, 10L))

    private val chennai = BoundingBox.around(LatLng(13.0827, 80.2707), 400.0)

    @Test
    fun tilesCoverTheBoxOnAFixedLattice() {
        val box = BoundingBox(13.04, 80.24, 13.12, 80.31)
        val tiles = TileKey.covering(box)
        // Latitude 13.00-13.15 and longitude 80.20-80.35: 3 x 3 tiles.
        assertEquals(9, tiles.size)
        tiles.forEach { tile ->
            assertEquals(TileKey.SIZE, tile.bounds.north - tile.bounds.south, 1e-9)
        }
        assertTrue(tiles.any { it.bounds.contains(LatLng(13.0827, 80.2707)) })
    }

    @Test
    fun missingTilesStartNearestTheCenter() {
        val box = BoundingBox(13.04, 80.24, 13.12, 80.31)
        val missing = repo(FakeOverpass()).missingTiles(box)
        assertEquals(9, missing.size)
        assertTrue(missing.first().bounds.contains(box.center))
    }

    @Test
    fun loadedTilesAreCachedAndMerged() = runTest {
        val http = FakeOverpass()
        val repo = repo(http)
        val tile = repo.missingTiles(chennai).single()

        repo.loadTile(tile)

        assertTrue(repo.missingTiles(chennai).isEmpty())
        val area = repo.cachedArea(chennai)
        assertEquals(listOf("osm:node/1", "osm:node/999"), area.places.map { it.id })
        assertEquals(1, http.endpoints.size)
    }

    @Test
    fun tilesExpireAfterTheirTtl() = runTest {
        val repo = repo(FakeOverpass())
        repo.loadTile(repo.missingTiles(chennai).single())
        now += CityDataRepository.AREA_TTL_MILLIS + 1
        assertEquals(1, repo.missingTiles(chennai).size)
        // Stale data stays visible until the refresh lands.
        assertEquals(2, repo.cachedArea(chennai).places.size)
    }

    @Test
    fun rateLimitedRequestsRetryTheMainServerThenTheMirror() = runTest {
        val http = FakeOverpass(failures = 2)
        val repo = repo(http)

        repo.loadTile(repo.missingTiles(chennai).single())

        assertEquals(listOf(Overpass.MAIN, Overpass.MAIN, Overpass.MIRROR), http.endpoints)
        assertFalse(repo.hasFailures(chennai))
    }

    @Test
    fun failedTilesCoolDownBeforeRetrying() = runTest {
        val http = FakeOverpass(failures = 99)
        val repo = repo(http)
        val tile = repo.missingTiles(chennai).single()

        try {
            repo.loadTile(tile)
            fail("expected the load to fail")
        } catch (e: HttpException) {
            assertEquals(429, e.code)
        }

        assertTrue(repo.hasFailures(chennai))
        assertTrue(repo.missingTiles(chennai).isEmpty())
        now += CityDataRepository.FAILURE_COOLDOWN_MILLIS + 1
        assertEquals(listOf(tile), repo.missingTiles(chennai))
    }

    @Test
    fun clearFailuresAllowsAnImmediateRetry() = runTest {
        val http = FakeOverpass(failures = 3)
        val repo = repo(http)
        val tile = repo.missingTiles(chennai).single()
        runCatching { repo.loadTile(tile) }

        repo.clearFailures()

        assertEquals(listOf(tile), repo.missingTiles(chennai))
        repo.loadTile(tile)
        assertFalse(repo.hasFailures(chennai))
    }

    @Test
    fun serverSideQueryErrorsCountAsFailures() = runTest {
        val http = object : Http {
            var calls = 0
            override suspend fun get(url: String): String = error("unused")
            override suspend fun postForm(url: String, form: Map<String, String>): String {
                calls++
                return """{"elements":[],"remark":"runtime error: Query timed out in \"query\" at line 3 after 26 seconds."}"""
            }
        }
        val repo = repo(http)
        val tile = repo.missingTiles(chennai).single()

        val result = runCatching { repo.loadTile(tile) }

        assertTrue(result.isFailure)
        assertEquals(3, http.calls)
        assertTrue(repo.cachedArea(chennai).places.isEmpty())
    }
}
