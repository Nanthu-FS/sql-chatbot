package com.urbanlens.core.weather

import kotlin.math.roundToInt

enum class WeatherCondition(val label: String) {
    CLEAR("Clear"),
    PARTLY_CLOUDY("Partly cloudy"),
    CLOUDY("Cloudy"),
    FOG("Fog"),
    DRIZZLE("Drizzle"),
    RAIN("Rain"),
    SNOW("Snow"),
    THUNDERSTORM("Thunderstorm");

    companion object {
        /** WMO weather interpretation codes, as used by Open-Meteo. */
        fun fromWmoCode(code: Int): WeatherCondition = when (code) {
            0 -> CLEAR
            1, 2 -> PARTLY_CLOUDY
            3 -> CLOUDY
            45, 48 -> FOG
            in 51..57 -> DRIZZLE
            in 61..67, in 80..82 -> RAIN
            in 71..77, 85, 86 -> SNOW
            in 95..99 -> THUNDERSTORM
            else -> CLOUDY
        }
    }
}

data class WeatherNow(
    val temperatureC: Double,
    val windKmh: Double,
    val windDirectionDeg: Double,
    val weatherCode: Int,
    val isDay: Boolean,
) {
    val condition: WeatherCondition get() = WeatherCondition.fromWmoCode(weatherCode)
    val windCompass: String get() = compassPoint(windDirectionDeg)

    companion object {
        private val POINTS = listOf(
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
        )

        fun compassPoint(degrees: Double): String {
            val normalized = ((degrees % 360) + 360) % 360
            return POINTS[(normalized / 22.5).roundToInt() % 16]
        }
    }
}
