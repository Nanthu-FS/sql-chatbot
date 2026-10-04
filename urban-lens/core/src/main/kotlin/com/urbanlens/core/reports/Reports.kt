package com.urbanlens.core.reports

import com.urbanlens.core.air.PollutionHotspot
import com.urbanlens.core.construction.ConstructionKind
import com.urbanlens.core.geo.LatLng
import com.urbanlens.core.geo.Shape
import com.urbanlens.core.routing.RouteObstacle
import kotlin.math.max
import kotlin.math.min

enum class ReportType(
    val label: String,
    /** How long a report stays on the map without confirmations. */
    val ttlHours: Long,
    val color: String,
    val blocksRoutes: Boolean,
) {
    CONSTRUCTION("Construction", 24 * 14, "#F08A24", blocksRoutes = true),
    BLOCKED_ROAD("Blocked road", 24, "#E5484D", blocksRoutes = true),
    SMOKE("Smoke / burning", 6, "#9AA0A6", blocksRoutes = false),
    WATERLOGGING("Waterlogging", 12, "#3B82F6", blocksRoutes = true),
    OTHER("Other hazard", 24, "#C792EA", blocksRoutes = false),
}

data class CommunityReport(
    val id: Long,
    val type: ReportType,
    val location: LatLng,
    val note: String,
    val photoPath: String?,
    val createdAtMillis: Long,
    val confirmations: Int = 0,
    val lastConfirmedAtMillis: Long = createdAtMillis,
)

object ReportPolicy {
    private const val HOUR_MILLIS = 3_600_000L

    /** Each confirmation restarts the clock and stretches it by 50%, up to 3x. */
    fun expiresAtMillis(report: CommunityReport): Long {
        val base = max(report.createdAtMillis, report.lastConfirmedAtMillis)
        val stretch = 1.0 + min(report.confirmations, 4) * 0.5
        return base + (report.type.ttlHours * HOUR_MILLIS * stretch).toLong()
    }

    fun isActive(report: CommunityReport, nowMillis: Long): Boolean = nowMillis < expiresAtMillis(report)

    fun confidenceLabel(report: CommunityReport): String = when (report.confirmations) {
        0 -> "Unconfirmed"
        1 -> "Confirmed once"
        else -> "Confirmed ${report.confirmations} times"
    }

    fun confirmed(report: CommunityReport, nowMillis: Long): CommunityReport =
        report.copy(confirmations = report.confirmations + 1, lastConfirmedAtMillis = nowMillis)

    fun toObstacle(report: CommunityReport): RouteObstacle? =
        if (report.type.blocksRoutes) {
            RouteObstacle("report:${report.id}", report.type.label, Shape.point(report.location), bufferMeters = 60.0)
        } else {
            null
        }

    fun toHotspot(report: CommunityReport): PollutionHotspot? = when (report.type) {
        ReportType.SMOKE -> PollutionHotspot.smoke(report.location)
        ReportType.CONSTRUCTION -> PollutionHotspot.constructionDust(
            "Reported construction", Shape.point(report.location), ConstructionKind.SITE.dustFactor,
        )
        else -> null
    }
}
