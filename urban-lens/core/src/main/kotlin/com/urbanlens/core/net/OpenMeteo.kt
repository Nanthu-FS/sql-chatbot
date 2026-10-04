package com.urbanlens.core.net

import com.urbanlens.core.air.AirSample
import com.urbanlens.core.geo.LatLng
import com.urbanlens.core.weather.WeatherNow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Open-Meteo air quality (CAMS model). Free, no key. https://open-meteo.com/en/docs/air-quality-api */
object OpenMeteoAirQuality {
    const val ENDPOINT = "https://air-quality-api.open-meteo.com/v1/air-quality"

    fun url(points: List<LatLng>): String {
        require(points.isNotEmpty())
        val lats = points.joinToString(",") { coord(it.lat) }
        val lngs = points.joinToString(",") { coord(it.lng) }
        return "$ENDPOINT?latitude=$lats&longitude=$lngs" +
            "&current=us_aqi,pm2_5,pm10,nitrogen_dioxide,ozone,carbon_monoxide&timezone=auto"
    }

    /** Parses a single-location object or a multi-location array, matched to [requested] by order. */
    fun parse(json: String, requested: List<LatLng>): List<AirSample> {
        val items = Json.parseToJsonElement(json).asObjectList()
        return items.mapIndexedNotNull { i, item ->
            val current = item.obj("current") ?: return@mapIndexedNotNull null
            val location = requested.getOrNull(i)
                ?: LatLng(item.double("latitude") ?: return@mapIndexedNotNull null, item.double("longitude") ?: 0.0)
            AirSample(
                location = location,
                usAqi = current.int("us_aqi"),
                pm25 = current.double("pm2_5"),
                pm10 = current.double("pm10"),
                no2 = current.double("nitrogen_dioxide"),
                o3 = current.double("ozone"),
                co = current.double("carbon_monoxide"),
            )
        }
    }
}

/** Open-Meteo current weather. Free, no key. https://open-meteo.com/en/docs */
object OpenMeteoWeather {
    const val ENDPOINT = "https://api.open-meteo.com/v1/forecast"

    fun url(point: LatLng): String =
        "$ENDPOINT?latitude=${coord(point.lat)}&longitude=${coord(point.lng)}" +
            "&current=temperature_2m,wind_speed_10m,wind_direction_10m,weather_code,is_day&timezone=auto"

    fun parse(json: String): WeatherNow? {
        val root = Json.parseToJsonElement(json) as? JsonObject ?: return null
        val current = root.obj("current") ?: return null
        return WeatherNow(
            temperatureC = current.double("temperature_2m") ?: return null,
            windKmh = current.double("wind_speed_10m") ?: 0.0,
            windDirectionDeg = current.double("wind_direction_10m") ?: 0.0,
            weatherCode = current.int("weather_code") ?: 0,
            isDay = (current.int("is_day") ?: 1) == 1,
        )
    }
}
