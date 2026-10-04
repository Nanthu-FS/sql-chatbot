package com.urbanlens.core.air

import com.urbanlens.core.geo.BoundingBox
import com.urbanlens.core.geo.GeoMath
import com.urbanlens.core.geo.LatLng
import com.urbanlens.core.geo.Shape
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** US EPA AQI categories. Colors are tuned for a dark map. */
enum class AqiCategory(
    val label: String,
    val upperBound: Int,
    val color: String,
    val advice: String,
    val sensitiveAdvice: String,
) {
    GOOD("Good", 50, "#3FB950", "Great air for being outside.", "Great air for being outside."),
    MODERATE(
        "Moderate", 100, "#E3C04B",
        "Fine for most people.",
        "Consider shorter outdoor workouts if you are very sensitive.",
    ),
    UNHEALTHY_SENSITIVE(
        "Unhealthy for sensitive groups", 150, "#F08A24",
        "Most people are fine; take it easy on long workouts.",
        "Reduce long or intense time outdoors. A mask helps.",
    ),
    UNHEALTHY(
        "Unhealthy", 200, "#E5484D",
        "Cut down on long or intense time outdoors.",
        "Avoid outdoor exertion and keep windows closed.",
    ),
    VERY_UNHEALTHY(
        "Very unhealthy", 300, "#A86CC1",
        "Avoid outdoor exertion; wear a mask outside.",
        "Stay indoors with windows closed.",
    ),
    HAZARDOUS(
        "Hazardous", Int.MAX_VALUE, "#B8325A",
        "Health alert: stay indoors.",
        "Health alert: stay indoors and use an air purifier if you can.",
    );

    companion object {
        fun of(aqi: Int): AqiCategory = entries.first { aqi <= it.upperBound }
    }
}

/** Converts pollutant concentrations to US AQI (EPA breakpoints, 2024 PM2.5 revision). */
object AqiCalculator {
    private class Breakpoint(val cLow: Double, val cHigh: Double, val iLow: Int, val iHigh: Int)

    private val pm25 = listOf(
        Breakpoint(0.0, 9.0, 0, 50),
        Breakpoint(9.1, 35.4, 51, 100),
        Breakpoint(35.5, 55.4, 101, 150),
        Breakpoint(55.5, 125.4, 151, 200),
        Breakpoint(125.5, 225.4, 201, 300),
        Breakpoint(225.5, 325.4, 301, 500),
    )

    private val pm10 = listOf(
        Breakpoint(0.0, 54.0, 0, 50),
        Breakpoint(55.0, 154.0, 51, 100),
        Breakpoint(155.0, 254.0, 101, 150),
        Breakpoint(255.0, 354.0, 151, 200),
        Breakpoint(355.0, 424.0, 201, 300),
        Breakpoint(425.0, 604.0, 301, 500),
    )

    /** [ugM3] in µg/m³. */
    fun fromPm25(ugM3: Double): Int = index(floor(max(ugM3, 0.0) * 10) / 10, pm25)

    /** [ugM3] in µg/m³. */
    fun fromPm10(ugM3: Double): Int = index(floor(max(ugM3, 0.0)), pm10)

    private fun index(c: Double, table: List<Breakpoint>): Int {
        val bp = table.firstOrNull { c <= it.cHigh } ?: return 500
        val cLow = minOf(bp.cLow, c)
        return ((bp.iHigh - bp.iLow) / (bp.cHigh - cLow) * (c - cLow) + bp.iLow).roundToInt()
    }
}

/** A modelled air reading at one point from Open-Meteo (CAMS). */
data class AirSample(
    val location: LatLng,
    val usAqi: Int?,
    val pm25: Double?,
    val pm10: Double?,
    val no2: Double? = null,
    val o3: Double? = null,
    val co: Double? = null,
)

/** Something that locally makes the air worse: construction dust, reported smoke. */
data class PollutionHotspot(
    val label: String,
    val shape: Shape,
    val pm25Boost: Double,
    val pm10Boost: Double,
    val sigmaMeters: Double,
) {
    internal val reach: BoundingBox by lazy { shape.bounds.expandedByMeters(3 * sigmaMeters) }

    /** Extra (pm2.5, pm10) at [p]. */
    fun boostAt(p: LatLng): Pair<Double, Double> {
        if (!reach.contains(p)) return 0.0 to 0.0
        val d = shape.distanceMeters(p)
        val f = exp(-(d * d) / (2 * sigmaMeters * sigmaMeters))
        return if (f < 0.02) 0.0 to 0.0 else (pm25Boost * f) to (pm10Boost * f)
    }

    companion object {
        /** Construction dust. Magnitudes are rough estimates for a busy site, scaled by [dustFactor]. */
        fun constructionDust(label: String, shape: Shape, dustFactor: Double) =
            PollutionHotspot(label, shape, pm25Boost = 6.0 * dustFactor, pm10Boost = 35.0 * dustFactor, sigmaMeters = 120.0)

        fun smoke(location: LatLng) =
            PollutionHotspot("Reported smoke", Shape.point(location), pm25Boost = 40.0, pm10Boost = 50.0, sigmaMeters = 220.0)
    }
}

data class AirEstimate(
    val aqi: Int,
    val pm25: Double?,
    val pm10: Double?,
    /** AQI before local hotspots were added. */
    val baseAqi: Int,
    /** Labels of hotspots that raised the AQI here. */
    val localSources: List<String>,
) {
    val category: AqiCategory get() = AqiCategory.of(aqi)
    val localBoost: Int get() = aqi - baseAqi
}

/**
 * Air quality anywhere in the viewport: inverse-distance interpolation between Open-Meteo samples,
 * plus local bumps from hotspots (construction dust, reported smoke).
 */
class PollutionField(samples: List<AirSample>, private val hotspots: List<PollutionHotspot> = emptyList()) {
    private val valid = samples.filter { it.usAqi != null }

    val isEmpty: Boolean get() = valid.isEmpty()

    fun at(p: LatLng): AirEstimate? {
        if (valid.isEmpty()) return null
        var wSum = 0.0
        var aqiSum = 0.0
        var pm25Sum = 0.0
        var pm25W = 0.0
        var pm10Sum = 0.0
        var pm10W = 0.0
        for (s in valid) {
            val d = max(GeoMath.approxDistanceMeters(p, s.location), 25.0)
            val w = 1.0 / (d * d)
            wSum += w
            aqiSum += w * s.usAqi!!
            s.pm25?.let { pm25Sum += w * it; pm25W += w }
            s.pm10?.let { pm10Sum += w * it; pm10W += w }
        }
        val baseAqi = (aqiSum / wSum).roundToInt()
        val pm25 = if (pm25W > 0) pm25Sum / pm25W else null
        val pm10 = if (pm10W > 0) pm10Sum / pm10W else null

        var boost25 = 0.0
        var boost10 = 0.0
        val sources = mutableListOf<String>()
        for (h in hotspots) {
            val (b25, b10) = h.boostAt(p)
            if (b25 > 0 || b10 > 0) {
                boost25 += b25
                boost10 += b10
                if (h.label !in sources) sources += h.label
            }
        }
        if (sources.isEmpty()) return AirEstimate(baseAqi, pm25, pm10, baseAqi, emptyList())

        val ref25 = pm25 ?: DEFAULT_PM25
        val ref10 = pm10 ?: DEFAULT_PM10
        val delta = max(
            AqiCalculator.fromPm25(ref25 + boost25) - AqiCalculator.fromPm25(ref25),
            AqiCalculator.fromPm10(ref10 + boost10) - AqiCalculator.fromPm10(ref10),
        )
        return AirEstimate(
            aqi = baseAqi + max(delta, 0),
            pm25 = pm25?.plus(boost25),
            pm10 = pm10?.plus(boost10),
            baseAqi = baseAqi,
            localSources = sources,
        )
    }

    private companion object {
        const val DEFAULT_PM25 = 10.0
        const val DEFAULT_PM10 = 40.0
    }
}
