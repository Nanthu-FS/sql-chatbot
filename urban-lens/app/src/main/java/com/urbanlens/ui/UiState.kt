package com.urbanlens.ui

import com.urbanlens.core.air.AirEstimate
import com.urbanlens.core.construction.ConstructionSite
import com.urbanlens.core.crowd.Place
import com.urbanlens.core.geo.BoundingBox
import com.urbanlens.core.geo.LatLng
import com.urbanlens.core.reports.CommunityReport
import com.urbanlens.core.reports.ReportType
import com.urbanlens.core.routing.RouteOption
import com.urbanlens.core.routing.TravelMode
import com.urbanlens.core.weather.WeatherNow
import com.urbanlens.data.AppSettings
import com.urbanlens.data.SavedPlace
import java.time.LocalDateTime

data class CrowdCell(val corners: List<LatLng>, val level: Int)

data class AirCell(val corners: List<LatLng>, val aqi: Int)

/** What it's like at one spot right now. */
data class SpotInsight(
    val crowdLevel: Int,
    val air: AirEstimate?,
    val nearbySites: List<ConstructionSite>,
    val comfort: Int,
)

data class PlaceInsight(
    val place: Place,
    val insight: SpotInsight,
    /** 0..1 for each hour of today. */
    val hourly: List<Double>,
    val bestHour: Int?,
)

sealed interface Selection {
    val location: LatLng

    data class OfPlace(val details: PlaceInsight) : Selection {
        override val location get() = details.place.location
    }

    data class OfSite(val site: ConstructionSite, val insight: SpotInsight) : Selection {
        override val location get() = site.shape.center
    }

    data class OfReport(val report: CommunityReport, val insight: SpotInsight) : Selection {
        override val location get() = report.location
    }

    data class OfPoint(override val location: LatLng, val insight: SpotInsight) : Selection
}

data class RouteState(
    val origin: LatLng,
    val originIsUser: Boolean,
    val destination: LatLng,
    val destinationName: String,
    val mode: TravelMode,
    val loading: Boolean = true,
    val options: List<RouteOption> = emptyList(),
    val selectedId: Int? = null,
    val error: String? = null,
) {
    val selected: RouteOption? get() = options.firstOrNull { it.id == selectedId }
}

data class ReportDraft(
    val location: LatLng,
    val type: ReportType = ReportType.CONSTRUCTION,
    val note: String = "",
    val photoPath: String? = null,
    val saving: Boolean = false,
)

sealed interface CameraRequest {
    val id: Long

    data class MoveTo(val target: LatLng, val zoom: Double?, override val id: Long) : CameraRequest
    data class Fit(val bounds: BoundingBox, override val id: Long) : CameraRequest
}

data class UiState(
    val settings: AppSettings = AppSettings(),
    val viewport: BoundingBox? = null,
    val zoom: Double = 13.5,
    val userLocation: LatLng? = null,
    val locating: Boolean = false,
    val weather: WeatherNow? = null,
    /** Air at the user's location, or the map center when location is off. */
    val airHere: AirEstimate? = null,
    val sites: List<ConstructionSite> = emptyList(),
    val places: List<Place> = emptyList(),
    /** Crowd level (0..100) around each place, by place id. */
    val placeLevels: Map<String, Int> = emptyMap(),
    val reports: List<CommunityReport> = emptyList(),
    val crowdCells: List<CrowdCell> = emptyList(),
    val airCells: List<AirCell> = emptyList(),
    val calmPlaces: List<PlaceInsight> = emptyList(),
    val selection: Selection? = null,
    val route: RouteState? = null,
    val draft: ReportDraft? = null,
    val showSettings: Boolean = false,
    val savedPlaces: List<SavedPlace> = emptyList(),
    val loadingArea: Boolean = false,
    val loadingAir: Boolean = false,
    val zoomedOutTooFar: Boolean = false,
    val areaError: String? = null,
    val airError: String? = null,
    val message: String? = null,
    val cameraRequest: CameraRequest? = null,
    val now: LocalDateTime = LocalDateTime.now(),
)
