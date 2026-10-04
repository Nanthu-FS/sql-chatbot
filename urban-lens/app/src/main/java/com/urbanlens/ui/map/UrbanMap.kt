package com.urbanlens.ui.map

import android.graphics.RectF
import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.urbanlens.core.crowd.CrowdLevel
import com.urbanlens.core.geo.BoundingBox
import com.urbanlens.core.geo.LatLng
import com.urbanlens.data.CameraSnapshot
import com.urbanlens.data.MapLayer
import com.urbanlens.ui.CameraRequest
import com.urbanlens.ui.MainViewModel
import com.urbanlens.ui.MapHit
import com.urbanlens.ui.UiState
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlin.math.max
import kotlin.math.min
import org.maplibre.android.geometry.LatLng as MapLatLng

/** Imperative handle for the map buttons (zoom). */
class UrbanMapState {
    internal var map: MapLibreMap? = null

    fun zoomIn() {
        map?.animateCamera(CameraUpdateFactory.zoomIn())
    }

    fun zoomOut() {
        map?.animateCamera(CameraUpdateFactory.zoomOut())
    }
}

private class ReadyMap(val map: MapLibreMap, val renderer: MapRenderer)

@Composable
fun UrbanMap(
    state: UiState,
    mapState: UrbanMapState,
    initialCamera: CameraSnapshot,
    bottomInsetPx: Int,
    onCameraIdle: (BoundingBox, Double, LatLng) -> Unit,
    onTap: (LatLng, MapHit?) -> Unit,
    onLongPress: (LatLng) -> Unit,
    onCameraRequestHandled: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember { MapView(context).also { it.onCreate(null) } }
    var ready by remember { mutableStateOf<ReadyMap?>(null) }

    val currentOnIdle by rememberUpdatedState(onCameraIdle)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)

    val tapRadiusPx = with(density) { 14.dp.toPx() }
    val topMarginPx = with(density) { 96.dp.roundToPx() }
    val sideMarginPx = with(density) { 12.dp.roundToPx() }

    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
            mapState.map = null
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            map.uiSettings.apply {
                setRotateGesturesEnabled(false)
                setTiltGesturesEnabled(false)
                setLogoEnabled(false)
                setCompassEnabled(false)
                setAttributionGravity(Gravity.TOP or Gravity.END)
                setAttributionMargins(0, topMarginPx, sideMarginPx, 0)
                setAttributionTintColor(0xFFA79C91.toInt())
            }
            map.setMinZoomPreference(4.0)
            map.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    MapLatLng(initialCamera.target.lat, initialCamera.target.lng),
                    initialCamera.zoom,
                ),
            )
            map.addOnCameraIdleListener {
                val bounds = map.projection.visibleRegion.latLngBounds
                val box = BoundingBox(
                    south = min(bounds.latitudeSouth, bounds.latitudeNorth),
                    west = min(bounds.longitudeWest, bounds.longitudeEast),
                    north = max(bounds.latitudeSouth, bounds.latitudeNorth),
                    east = max(bounds.longitudeWest, bounds.longitudeEast),
                )
                val camera = map.cameraPosition
                val target = camera.target ?: return@addOnCameraIdleListener
                currentOnIdle(box, camera.zoom, LatLng(target.latitude, target.longitude))
            }
            map.addOnMapClickListener { point ->
                currentOnTap(LatLng(point.latitude, point.longitude), hitAt(map, point, tapRadiusPx))
                true
            }
            map.addOnMapLongClickListener { point ->
                currentOnLongPress(LatLng(point.latitude, point.longitude))
                true
            }
            map.setStyle(Style.Builder().fromUri(MapRenderer.STYLE_URL)) { style ->
                val renderer = MapRenderer(style).also { it.install() }
                mapState.map = map
                ready = ReadyMap(map, renderer)
            }
        }
    }

    ready?.let { r ->
        val layers = state.settings.layers
        val showAir = MapLayer.AIR in layers
        val showCrowd = MapLayer.CROWD in layers
        val showSites = MapLayer.CONSTRUCTION in layers
        val showPlaces = MapLayer.PLACES in layers
        val showReports = MapLayer.REPORTS in layers

        LaunchedEffect(r, state.airCells, showAir) { r.renderer.showAir(state.airCells, showAir) }
        LaunchedEffect(r, state.crowdCells, showCrowd) { r.renderer.showCrowd(state.crowdCells, showCrowd) }
        LaunchedEffect(r, state.sites, showSites) { r.renderer.showSites(state.sites, showSites) }
        LaunchedEffect(r, state.places, state.placeLevels, showPlaces) {
            val colors = state.placeLevels.mapValues { (_, level) -> CrowdLevel.of(level).color }
            r.renderer.showPlaces(state.places, colors, showPlaces)
        }
        LaunchedEffect(r, state.reports, showReports) { r.renderer.showReports(state.reports, showReports) }
        LaunchedEffect(r, state.route) {
            val route = state.route
            r.renderer.showRoutes(route?.options.orEmpty(), route?.selectedId, route?.origin, route?.destination)
        }
        LaunchedEffect(r, state.userLocation) { r.renderer.showUser(state.userLocation) }
        val marker = state.draft?.location ?: state.selection?.location
        LaunchedEffect(r, marker) { r.renderer.showSelection(marker) }
        LaunchedEffect(r, state.cameraRequest) {
            val request = state.cameraRequest ?: return@LaunchedEffect
            when (request) {
                is CameraRequest.MoveTo -> r.map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        MapLatLng(request.target.lat, request.target.lng),
                        request.zoom ?: r.map.cameraPosition.zoom,
                    ),
                    650,
                )
                is CameraRequest.Fit -> {
                    val b = request.bounds
                    val bounds = LatLngBounds.Builder()
                        .include(MapLatLng(b.south, b.west))
                        .include(MapLatLng(b.north, b.east))
                        .build()
                    val pad = with(density) { 40.dp.roundToPx() }
                    r.map.animateCamera(
                        CameraUpdateFactory.newLatLngBounds(bounds, pad, topMarginPx + pad, pad, bottomInsetPx + pad),
                        650,
                    )
                }
            }
            onCameraRequestHandled(request.id)
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private val HIT_PRIORITY = listOf(MainViewModel.KIND_REPORT, MainViewModel.KIND_PLACE, MainViewModel.KIND_SITE, MainViewModel.KIND_ROUTE)

private fun hitAt(map: MapLibreMap, point: MapLatLng, radiusPx: Float): MapHit? {
    val screen = map.projection.toScreenLocation(point)
    val area = RectF(screen.x - radiusPx, screen.y - radiusPx, screen.x + radiusPx, screen.y + radiusPx)
    return map.queryRenderedFeatures(area, *MapRenderer.TAPPABLE_LAYERS)
        .mapNotNull { feature ->
            if (!feature.hasProperty("kind") || !feature.hasProperty("id")) return@mapNotNull null
            MapHit(feature.getStringProperty("kind"), feature.getStringProperty("id"))
        }
        .minByOrNull { HIT_PRIORITY.indexOf(it.kind).let { i -> if (i < 0) Int.MAX_VALUE else i } }
}
