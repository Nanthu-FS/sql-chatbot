package com.smartnotes.features

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class WeatherNow(val place: String, val tempC: Double, val summary: String)

/** Live weather for the {{weather City}} block. Uses Open-Meteo: free, no API key. */
object Weather {
    private val json = Json { ignoreUnknownKeys = true }

    private const val TTL_MS = 10 * 60 * 1000L
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, WeatherNow>>()

    /** Last result for [city] if it's fresh, so re-opening a note doesn't show "Loading…" again. */
    fun cached(city: String): WeatherNow? =
        cache[city.lowercase()]?.takeIf { System.currentTimeMillis() - it.first < TTL_MS }?.second

    suspend fun now(city: String): WeatherNow {
        cached(city)?.let { return it }
        return fetch(city).also { cache[city.lowercase()] = System.currentTimeMillis() to it }
    }

    private suspend fun fetch(city: String): WeatherNow = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(city, "UTF-8")
        val geo = get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1")
        val hit = geo["results"]?.jsonArray?.firstOrNull()?.jsonObject ?: error("Unknown place: $city")
        val lat = hit["latitude"]!!.jsonPrimitive.double
        val lng = hit["longitude"]!!.jsonPrimitive.double
        val name = hit["name"]?.jsonPrimitive?.content ?: city
        val f = get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng&current=temperature_2m,weather_code")
        val current = f["current"]!!.jsonObject
        WeatherNow(name, current["temperature_2m"]!!.jsonPrimitive.double, describe(current["weather_code"]!!.jsonPrimitive.int))
    }

    private fun get(url: String): JsonObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        return try {
            json.parseToJsonElement(conn.inputStream.bufferedReader().readText()).jsonObject
        } finally {
            conn.disconnect()
        }
    }

    // WMO weather interpretation codes.
    private fun describe(code: Int) = when (code) {
        0 -> "Clear"
        1, 2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67, in 80..82 -> "Rain"
        in 71..77, 85, 86 -> "Snow"
        in 95..99 -> "Thunderstorm"
        else -> "—"
    }
}
