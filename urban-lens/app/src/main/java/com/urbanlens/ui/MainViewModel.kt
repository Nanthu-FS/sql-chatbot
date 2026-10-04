package com.urbanlens.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.urbanlens.alerts.AlertScheduler
import com.urbanlens.appContainer
import com.urbanlens.core.air.AirSample
import com.urbanlens.core.air.PollutionField
import com.urbanlens.core.alerts.HealthProfile
import com.urbanlens.core.construction.ConstructionSite
import com.urbanlens.core.crowd.Comfort
import com.urbanlens.core.crowd.CrowdModel
import com.urbanlens.core.crowd.Place
import com.urbanlens.core.crowd.PlaceCategory
import com.urbanlens.core.data.CityDataRepository
import com.urbanlens.core.geo.BoundingBox
import com.urbanlens.core.geo.LatLng
import com.urbanlens.core.grid.HexGrid
import com.urbanlens.core.reports.CommunityReport
import com.urbanlens.core.reports.ReportPolicy
import com.urbanlens.core.routing.ExposureContext
import com.urbanlens.core.routing.RouteObstacle
import com.urbanlens.core.routing.RouteTag
import com.urbanlens.core.routing.TravelMode
import com.urbanlens.data.CameraSnapshot
import com.urbanlens.data.LocationResult
import com.urbanlens.data.MapLayer
import com.urbanlens.data.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** Where a tap landed on the map, if it hit one of our features. */
data class MapHit(val kind: String, val id: String)

/** The view the OpenStreetMap loader should cover; [nonce] changes to wake it (new view or retry). */
private data class AreaRequest(val viewport: BoundingBox?, val nonce: Int)

@OptIn(FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val container = application.appContainer

    private val _state = MutableStateFlow(UiState(settings = container.settings.current()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    // Raw inputs, refreshed from the network / database.
    @Volatile private var airSamples: List<AirSample> = emptyList()
    @Volatile private var osmSites: List<ConstructionSite> = emptyList()
    @Volatile private var places: List<Place> = emptyList()
    @Volatile private var reports: List<CommunityReport> = emptyList()

    // Models derived from the inputs; rebuilt off the main thread.
    @Volatile private var field = PollutionField(emptyList())
    @Volatile private var crowdModel = CrowdModel(emptyList())

    private val viewportFlow = MutableStateFlow<BoundingBox?>(null)
    private val areaRequest = MutableStateFlow(AreaRequest(null, 0))
    private var derivedJob: Job? = null
    private var routeJob: Job? = null
    private var cameraRequestCounter = 0L
    private var messageCounter = 0L
    private var initialLocateDone = false

    /** Where a newly created map should start. */
    var camera: CameraSnapshot = container.settings.lastCamera()
        ?: CameraSnapshot(SettingsStore.DEFAULT_CENTER, SettingsStore.DEFAULT_ZOOM)
        private set

    init {
        viewModelScope.launch {
            viewportFlow.filterNotNull().debounce(450).collectLatest { refresh(it, force = false) }
        }
        viewModelScope.launch { loadAreaForever() }
        viewModelScope.launch {
            container.reports.observe().collect { all ->
                val now = System.currentTimeMillis()
                reports = all.filter { ReportPolicy.isActive(it, now) }
                rebuild()
            }
        }
        viewModelScope.launch {
            container.savedPlaces.observe().collect { saved -> _state.update { it.copy(savedPlaces = saved) } }
        }
        viewModelScope.launch {
            container.settings.settings.collect { settings -> _state.update { it.copy(settings = settings) } }
        }
        viewModelScope.launch {
            // Crowds change through the day; re-estimate every minute.
            while (isActive) {
                delay(60_000)
                rebuild()
            }
        }
        viewModelScope.launch { attempt { container.reports.purgeExpired() } }
    }

    // region Map & data

    fun onCameraIdle(viewport: BoundingBox, zoom: Double, center: LatLng) {
        camera = CameraSnapshot(center, zoom)
        container.settings.saveCamera(camera)
        _state.update { it.copy(viewport = viewport, zoom = zoom) }
        viewportFlow.value = viewport
        areaRequest.update { AreaRequest(viewport, it.nonce + 1) }
        rebuild()
    }

    fun retry() {
        container.cityData.clearFailures()
        areaRequest.update { it.copy(nonce = it.nonce + 1) }
        val viewport = _state.value.viewport ?: return
        viewModelScope.launch { refresh(viewport, force = true) }
    }

    fun toggleLayer(layer: MapLayer) = container.settings.update {
        it.copy(layers = if (layer in it.layers) it.layers - layer else it.layers + layer)
    }

    /** Air quality and weather for the view. OpenStreetMap data has its own loader below. */
    private suspend fun refresh(viewport: BoundingBox, force: Boolean) = coroutineScope {
        _state.update { it.copy(loadingAir = true) }

        val weatherPoint = _state.value.userLocation ?: viewport.center
        val airJob = async { attempt { container.cityData.air(viewport, force) } }
        val weatherJob = async { attempt { container.cityData.weather(weatherPoint, force) } }

        val air = airJob.await()
        val weather = weatherJob.await().getOrNull()

        air.onSuccess { airSamples = it }
        _state.update {
            it.copy(
                loadingAir = false,
                airError = if (air.isFailure) "Air quality is unavailable right now" else null,
                weather = weather ?: it.weather,
            )
        }
        rebuild()
    }

    /**
     * Loads OpenStreetMap tiles for the current view, one request at a time. A request in flight
     * is never cancelled by panning (the server does the work anyway); the next tile is simply
     * picked for wherever the map is by then. Failed tiles are retried after a cooldown.
     */
    private suspend fun loadAreaForever() {
        var published: BoundingBox? = null
        while (true) {
            val request = areaRequest.first { it.viewport != null }
            val viewport = request.viewport ?: continue
            if (viewport != published) {
                publishArea(viewport)
                published = viewport
            }
            if (viewport.widthMeters > CityDataRepository.MAX_AREA_WIDTH_METERS) {
                _state.update { it.copy(zoomedOutTooFar = true, loadingArea = false, areaError = null) }
                areaRequest.first { it.nonce != request.nonce }
                continue
            }
            val box = viewport.expandedBy(AREA_MARGIN)
            val next = container.cityData.missingTiles(box).firstOrNull()
            if (next == null) {
                val failed = container.cityData.hasFailures(box)
                _state.update { it.copy(zoomedOutTooFar = false, loadingArea = false, areaError = if (failed) AREA_ERROR else null) }
                // Wait for the map to move or a retry; wake up anyway to retry failed or stale tiles.
                withTimeoutOrNull(CityDataRepository.FAILURE_COOLDOWN_MILLIS) { areaRequest.first { it.nonce != request.nonce } }
                continue
            }
            _state.update { it.copy(zoomedOutTooFar = false, loadingArea = true) }
            val result = attempt { container.cityData.loadTile(next) }
            if (result.isSuccess) {
                published = _state.value.viewport?.also { publishArea(it) }
            } else {
                _state.update { it.copy(areaError = AREA_ERROR) }
            }
        }
    }

    /** Shows everything cached around [viewport]: nearby tiles too, so edges don't pop. */
    private fun publishArea(viewport: BoundingBox) {
        val area = container.cityData.cachedArea(viewport.expandedBy(0.5))
        osmSites = area.sites
        places = area.places
        rebuild()
    }

    /** Recomputes everything drawn on the map from the current inputs. */
    private fun rebuild() {
        val viewport = _state.value.viewport ?: return
        val samples = airSamples
        val sites = osmSites
        val loadedPlaces = places
        val activeReports = reports
        val user = _state.value.userLocation

        derivedJob?.cancel()
        derivedJob = viewModelScope.launch(Dispatchers.Default) {
            val now = LocalDateTime.now()
            val hotspots = sites.map { it.toHotspot() } + activeReports.mapNotNull(ReportPolicy::toHotspot)
            val newField = PollutionField(samples, hotspots)
            val newCrowd = CrowdModel(loadedPlaces)

            // Crowds only exist where places are loaded; skip the grid when zoomed far out.
            val crowdCells = if (loadedPlaces.isEmpty() || viewport.widthMeters > MAX_CROWD_GRID_WIDTH_METERS) {
                emptyList()
            } else {
                val grid = HexGrid.forViewport(viewport)
                val cells = grid.cellsCovering(viewport)
                val levels = newCrowd.levels(cells.map(grid::center), now)
                cells.indices.mapNotNull { i ->
                    if (levels[i] < MIN_VISIBLE_CROWD) null else CrowdCell(grid.corners(cells[i]), levels[i])
                }
            }
            val placeLevels = loadedPlaces.zip(newCrowd.levels(loadedPlaces.map { it.location }, now))
                .associate { (place, level) -> place.id to level }
            ensureActive()
            val airCells = airCells(viewport, newField)
            ensureActive()

            field = newField
            crowdModel = newCrowd
            val calm = calmPlaces(viewport, loadedPlaces, now)
            val airHere = newField.at(user ?: viewport.center)
            ensureActive()

            _state.update { s ->
                s.copy(
                    sites = sites,
                    places = loadedPlaces,
                    placeLevels = placeLevels,
                    reports = activeReports,
                    crowdCells = crowdCells,
                    airCells = airCells,
                    calmPlaces = calm,
                    airHere = airHere,
                    now = now,
                    selection = s.selection?.let { refreshed(it, now) },
                )
            }
        }
    }

    /** AQI tiles on a fixed lattice so they don't shift while panning. */
    private fun airCells(viewport: BoundingBox, field: PollutionField): List<AirCell> {
        if (field.isEmpty) return emptyList()
        val box = viewport.expandedBy(0.15)
        val step = AIR_STEPS.firstOrNull { (box.east - box.west) / it <= 16 } ?: ((box.east - box.west) / 16)
        val rowStart = floor(box.south / step).toInt()
        val rowEnd = ceil(box.north / step).toInt()
        val colStart = floor(box.west / step).toInt()
        val colEnd = ceil(box.east / step).toInt()
        val cells = ArrayList<AirCell>()
        for (r in rowStart until rowEnd) {
            for (c in colStart until colEnd) {
                val s = r * step
                val w = c * step
                val aqi = field.at(LatLng(s + step / 2, w + step / 2))?.aqi ?: continue
                cells += AirCell(listOf(LatLng(s, w), LatLng(s, w + step), LatLng(s + step, w + step), LatLng(s + step, w)), aqi)
            }
        }
        return cells
    }

    private fun calmPlaces(viewport: BoundingBox, all: List<Place>, now: LocalDateTime): List<PlaceInsight> {
        val candidates = all.filter { it.category in EXPLORE_CATEGORIES && viewport.contains(it.location) }
        if (candidates.isEmpty()) return emptyList()
        val levels = crowdModel.levels(candidates.map { it.location }, now)
        return candidates.indices
            .map { i -> placeInsight(candidates[i], now, levels[i]) }
            .sortedByDescending { it.insight.comfort }
            .take(8)
    }

    private fun spotInsight(point: LatLng, now: LocalDateTime, crowdLevel: Int? = null): SpotInsight {
        val level = crowdLevel ?: crowdModel.levelAt(point, now)
        val air = field.at(point)
        val nearby = osmSites
            .map { it to it.shape.distanceMeters(point) }
            .filter { it.second <= NEARBY_METERS }
            .sortedBy { it.second }
            .map { it.first }
        return SpotInsight(level, air, nearby, Comfort.score(level, air?.aqi, nearby.size))
    }

    private fun placeInsight(place: Place, now: LocalDateTime, crowdLevel: Int? = null) = PlaceInsight(
        place = place,
        insight = spotInsight(place.location, now, crowdLevel),
        hourly = crowdModel.hourlyBusyness(place, now.toLocalDate()),
        bestHour = crowdModel.bestHourToVisit(place, now),
    )

    private fun refreshed(selection: Selection, now: LocalDateTime): Selection? = when (selection) {
        is Selection.OfPlace -> Selection.OfPlace(placeInsight(selection.details.place, now))
        is Selection.OfSite -> selection.copy(insight = spotInsight(selection.site.shape.center, now))
        is Selection.OfReport -> reports.firstOrNull { it.id == selection.report.id }
            ?.let { Selection.OfReport(it, spotInsight(it.location, now)) }
        is Selection.OfPoint -> selection.copy(insight = spotInsight(selection.location, now))
    }

    // endregion

    // region Selection

    fun onMapTap(point: LatLng, hit: MapHit?) {
        val current = _state.value
        if (current.draft != null) {
            _state.update { it.copy(draft = it.draft?.copy(location = point)) }
            return
        }
        if (current.route != null) {
            if (hit != null && hit.kind == KIND_ROUTE) hit.id.toIntOrNull()?.let { selectRoute(it) }
            return
        }
        val now = LocalDateTime.now()
        val hitId = hit?.id
        val selection = when (hit?.kind) {
            KIND_PLACE -> places.firstOrNull { it.id == hitId }?.let { Selection.OfPlace(placeInsight(it, now)) }
            KIND_SITE -> osmSites.firstOrNull { it.id == hitId }?.let { Selection.OfSite(it, spotInsight(it.shape.center, now)) }
            KIND_REPORT -> reports.firstOrNull { it.id.toString() == hitId }?.let { Selection.OfReport(it, spotInsight(it.location, now)) }
            else -> null
        }
        _state.update {
            it.copy(
                selection = selection
                    ?: if (it.selection != null) null else Selection.OfPoint(point, spotInsight(point, now)),
            )
        }
    }

    fun onMapLongPress(point: LatLng) {
        if (_state.value.route != null || _state.value.draft != null) return
        _state.update { it.copy(selection = Selection.OfPoint(point, spotInsight(point, LocalDateTime.now()))) }
    }

    fun selectPlace(place: Place) {
        _state.update { it.copy(selection = Selection.OfPlace(placeInsight(place, LocalDateTime.now()))) }
        requestCamera { CameraRequest.MoveTo(place.location, null, it) }
    }

    fun clearSelection() = _state.update { it.copy(selection = null) }

    // endregion

    // region Routing

    fun directionsTo(target: LatLng, name: String) {
        val s = _state.value
        val origin = s.userLocation ?: s.viewport?.center ?: return
        _state.update {
            it.copy(
                selection = null,
                route = RouteState(origin, s.userLocation != null, target, name, it.settings.travelMode),
            )
        }
        planRoute()
    }

    fun setTravelMode(mode: TravelMode) {
        container.settings.update { it.copy(travelMode = mode) }
        _state.update {
            it.copy(route = it.route?.copy(mode = mode, loading = true, options = emptyList(), selectedId = null, error = null))
        }
        planRoute()
    }

    fun selectRoute(id: Int) = _state.update { it.copy(route = it.route?.copy(selectedId = id)) }

    fun closeRoute() {
        routeJob?.cancel()
        _state.update { it.copy(route = null) }
    }

    private fun planRoute() {
        val route = _state.value.route ?: return
        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            val context = exposureContext()
            val result = attempt {
                withContext(Dispatchers.Default) {
                    container.routePlanner.plan(route.origin, route.destination, route.mode, context)
                }
            }
            result.onSuccess { options ->
                val preferred = options.firstOrNull { RouteTag.CLEANEST in it.tags } ?: options.firstOrNull()
                _state.update {
                    it.copy(
                        route = it.route?.copy(
                            loading = false,
                            options = options,
                            selectedId = preferred?.id,
                            error = if (options.isEmpty()) "No route found" else null,
                        ),
                    )
                }
                if (options.isNotEmpty()) {
                    val points = options.flatMap { it.path } + route.origin + route.destination
                    requestCamera { CameraRequest.Fit(BoundingBox.of(points), it) }
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(route = it.route?.copy(loading = false, error = "Couldn't plan a route: ${error.message ?: "network error"}"))
                }
            }
        }
    }

    private fun exposureContext(): ExposureContext {
        val currentField = field
        val currentCrowd = crowdModel
        val now = LocalDateTime.now()
        val obstacles = osmSites.map { RouteObstacle(it.id, it.kind.label, it.shape) } +
            reports.mapNotNull(ReportPolicy::toObstacle)
        return ExposureContext(
            air = currentField::at,
            crowd = { points -> currentCrowd.levels(points, now) },
            obstacles = obstacles,
        )
    }

    // endregion

    // region Reports

    fun startReport(at: LatLng) =
        _state.update { it.copy(draft = ReportDraft(at), selection = null, route = null) }

    fun updateDraft(transform: (ReportDraft) -> ReportDraft) =
        _state.update { it.copy(draft = it.draft?.let(transform)) }

    fun attachPhoto(uri: Uri) {
        viewModelScope.launch {
            val path = attempt { container.reports.savePhoto(uri) }.getOrNull()
            if (path == null) message("Couldn't read that photo") else replaceDraftPhoto(path)
        }
    }

    fun attachPhoto(bitmap: Bitmap) {
        viewModelScope.launch {
            val path = attempt { container.reports.savePhoto(bitmap) }.getOrNull()
            if (path == null) message("Couldn't save that photo") else replaceDraftPhoto(path)
        }
    }

    private fun replaceDraftPhoto(path: String) {
        _state.value.draft?.photoPath?.let(container.reports::deletePhoto)
        updateDraft { it.copy(photoPath = path) }
    }

    fun cancelDraft() {
        _state.value.draft?.photoPath?.let(container.reports::deletePhoto)
        _state.update { it.copy(draft = null) }
    }

    fun submitDraft() {
        val draft = _state.value.draft ?: return
        if (draft.saving) return
        updateDraft { it.copy(saving = true) }
        viewModelScope.launch {
            attempt { container.reports.add(draft.type, draft.location, draft.note, draft.photoPath) }
                .onSuccess {
                    _state.update { it.copy(draft = null) }
                    message("Thanks! ${draft.type.label} reported.")
                }
                .onFailure {
                    updateDraft { d -> d.copy(saving = false) }
                    message("Couldn't save the report")
                }
        }
    }

    fun confirmReport(id: Long) {
        viewModelScope.launch {
            attempt { container.reports.confirm(id) }.onSuccess { message("Thanks for confirming") }
        }
    }

    fun removeReport(id: Long) {
        viewModelScope.launch {
            attempt { container.reports.remove(id) }
            _state.update { it.copy(selection = null) }
            message("Report removed")
        }
    }

    // endregion

    // region Saved places, alerts & settings

    fun savePlace(name: String, location: LatLng) {
        viewModelScope.launch {
            attempt { container.savedPlaces.add(name, location) }
                .onSuccess { message("Saved \"${name.ifBlank { "Saved place" }}\"") }
                .onFailure { message("Couldn't save the place") }
        }
    }

    fun removeSavedPlace(id: Long) {
        viewModelScope.launch { attempt { container.savedPlaces.remove(id) } }
    }

    fun openSettings() = _state.update { it.copy(showSettings = true) }

    fun closeSettings() = _state.update { it.copy(showSettings = false) }

    fun setProfile(profile: HealthProfile) =
        container.settings.update { it.copy(profile = profile, threshold = profile.defaultThreshold) }

    fun setThreshold(threshold: Int) = container.settings.update { it.copy(threshold = threshold) }

    fun setAlertsEnabled(enabled: Boolean) {
        container.settings.update { it.copy(alertsEnabled = enabled) }
        AlertScheduler.apply(app, enabled)
        if (enabled) AlertScheduler.checkNow(app)
    }

    fun checkAlertsNow() {
        AlertScheduler.checkNow(app)
        message("Checking the air at your saved places")
    }

    // endregion

    // region Location

    fun hasLocationPermission(): Boolean = container.location.hasPermission()

    /** Called once the map screen is up: centers on the user the first time we can. */
    fun onScreenStarted() {
        if (!initialLocateDone && hasLocationPermission()) locate(userInitiated = false)
    }

    fun onLocationPermissionResult(granted: Boolean, userInitiated: Boolean) {
        when {
            granted -> locate(userInitiated)
            userInitiated -> message("Location permission is off", MessageAction.APP_SETTINGS)
            !initialLocateDone -> {
                initialLocateDone = true
                message("Location is off, showing Chennai")
            }
        }
    }

    fun locate(userInitiated: Boolean) {
        if (_state.value.locating) return
        _state.update { it.copy(locating = true) }
        viewModelScope.launch {
            val result = attempt { container.location.current() }.getOrDefault(LocationResult.Unavailable)
            val firstTry = !initialLocateDone
            initialLocateDone = true
            _state.update { it.copy(locating = false) }
            when (result) {
                is LocationResult.Found -> {
                    val here = result.location
                    _state.update { it.copy(userLocation = here) }
                    requestCamera { CameraRequest.MoveTo(here, max(_state.value.zoom, 14.5), it) }
                    rebuild()
                }
                LocationResult.Disabled ->
                    if (userInitiated || firstTry) message("Location is turned off", MessageAction.LOCATION_SETTINGS)
                LocationResult.NoPermission ->
                    if (userInitiated) message("Location permission is off", MessageAction.APP_SETTINGS)
                LocationResult.Unavailable ->
                    if (userInitiated) message("No location fix yet. Try again in a moment, ideally near a window.")
            }
        }
    }

    // endregion

    fun onCameraRequestHandled(id: Long) =
        _state.update { if (it.cameraRequest?.id == id) it.copy(cameraRequest = null) else it }

    fun messageShown(id: Long) = _state.update { if (it.message?.id == id) it.copy(message = null) else it }

    private fun message(text: String, action: MessageAction? = null) {
        messageCounter++
        val message = UiMessage(text, action, messageCounter)
        _state.update { it.copy(message = message) }
    }

    private fun requestCamera(build: (Long) -> CameraRequest) {
        cameraRequestCounter++
        val request = build(cameraRequestCounter)
        _state.update { it.copy(cameraRequest = request) }
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    companion object {
        const val KIND_PLACE = "place"
        const val KIND_SITE = "site"
        const val KIND_REPORT = "report"
        const val KIND_ROUTE = "route"

        private const val MIN_VISIBLE_CROWD = 4
        private const val AREA_MARGIN = 0.15
        private const val AREA_ERROR = "OpenStreetMap is busy. Retrying shortly"
        private const val MAX_CROWD_GRID_WIDTH_METERS = 15_000.0
        private const val NEARBY_METERS = 400.0
        private val AIR_STEPS = doubleArrayOf(0.002, 0.004, 0.008, 0.016, 0.032, 0.064, 0.128, 0.256)

        private val EXPLORE_CATEGORIES = setOf(
            PlaceCategory.CAFE, PlaceCategory.RESTAURANT, PlaceCategory.PARK, PlaceCategory.BEACH,
            PlaceCategory.MALL, PlaceCategory.ATTRACTION, PlaceCategory.MARKET, PlaceCategory.CINEMA,
        )
    }
}
