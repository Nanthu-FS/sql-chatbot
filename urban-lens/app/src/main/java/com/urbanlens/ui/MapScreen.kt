package com.urbanlens.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.urbanlens.data.MapLayer
import com.urbanlens.ui.components.CalmPlacesRow
import com.urbanlens.ui.components.LayerRail
import com.urbanlens.ui.components.Legend
import com.urbanlens.ui.components.MapControls
import com.urbanlens.ui.components.ReportComposer
import com.urbanlens.ui.components.RoutePanel
import com.urbanlens.ui.components.SelectionCard
import com.urbanlens.ui.components.SettingsSheet
import com.urbanlens.ui.components.StatusChip
import com.urbanlens.ui.components.TopBar
import com.urbanlens.ui.components.WeatherWidget
import com.urbanlens.ui.map.UrbanMap
import com.urbanlens.ui.map.UrbanMapState
import com.urbanlens.ui.theme.UrbanColors

@Composable
fun MapScreen(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val mapState = remember { UrbanMapState() }
    val snackbar = remember { SnackbarHostState() }
    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val bottomInsetPx = with(density) { (screenHeightDp * 0.45f).dp.roundToPx() }
    val layers = state.settings.layers
    val panelOpen = state.draft != null || state.route != null || state.selection != null

    var askedForLocation by rememberSaveable { mutableStateOf(false) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        vm.onLocationPermissionResult(result.values.any { it })
    }
    LaunchedEffect(Unit) {
        if (vm.hasLocationPermission()) {
            vm.onScreenStarted()
        } else if (!askedForLocation) {
            askedForLocation = true
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        vm.messageShown()
    }

    BackHandler(enabled = panelOpen) {
        when {
            state.draft != null -> vm.cancelDraft()
            state.route != null -> vm.closeRoute()
            else -> vm.clearSelection()
        }
    }

    Box(Modifier.fillMaxSize().background(UrbanColors.Ink)) {
        UrbanMap(
            state = state,
            mapState = mapState,
            initialCamera = vm.camera,
            bottomInsetPx = bottomInsetPx,
            onCameraIdle = vm::onCameraIdle,
            onTap = vm::onMapTap,
            onLongPress = vm::onMapLongPress,
            onCameraRequestHandled = vm::onCameraRequestHandled,
            modifier = Modifier.fillMaxSize(),
        )

        // Keeps the title readable over bright map areas.
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.verticalGradient(listOf(UrbanColors.Ink.copy(alpha = 0.92f), Color.Transparent))),
        )

        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            TopBar(onSettings = vm::openSettings)
            StatusChip(
                loading = state.loadingArea || state.loadingAir,
                zoomedOutTooFar = state.zoomedOutTooFar,
                error = state.areaError ?: state.airError,
                onRetry = vm::retry,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        Column(
            Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 12.dp, top = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LayerRail(
                enabled = layers,
                counts = mapOf(
                    MapLayer.CONSTRUCTION to state.sites.size,
                    MapLayer.REPORTS to state.reports.size,
                    MapLayer.PLACES to state.places.size,
                ),
                onToggle = vm::toggleLayer,
            )
            if (!panelOpen) {
                Legend(showCrowd = MapLayer.CROWD in layers, showAir = MapLayer.AIR in layers)
            }
        }

        MapControls(
            locating = state.locating,
            onLocate = { vm.locate(userInitiated = true) },
            onZoomIn = mapState::zoomIn,
            onZoomOut = mapState::zoomOut,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 150.dp, end = 12.dp),
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 12.dp),
        ) {
            val cardModifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()
            val draft = state.draft
            val route = state.route
            val selection = state.selection
            when {
                draft != null -> ReportComposer(
                    draft = draft,
                    onChange = vm::updateDraft,
                    onPickedPhoto = vm::attachPhoto,
                    onTookPhoto = vm::attachPhoto,
                    onSubmit = vm::submitDraft,
                    onCancel = vm::cancelDraft,
                    modifier = cardModifier,
                )
                route != null -> RoutePanel(
                    route = route,
                    onModeChange = vm::setTravelMode,
                    onSelect = vm::selectRoute,
                    onClose = vm::closeRoute,
                    modifier = cardModifier,
                )
                selection != null -> SelectionCard(
                    selection = selection,
                    now = state.now,
                    profile = state.settings.profile,
                    onClose = vm::clearSelection,
                    onDirections = vm::directionsTo,
                    onReportHere = vm::startReport,
                    onSavePlace = { location, name -> vm.savePlace(name, location) },
                    onConfirmReport = vm::confirmReport,
                    onRemoveReport = vm::removeReport,
                    modifier = cardModifier,
                )
                else -> {
                    WeatherWidget(
                        weather = state.weather,
                        air = state.airHere,
                        now = state.now,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    if (MapLayer.PLACES in layers) {
                        CalmPlacesRow(
                            places = state.calmPlaces,
                            onSelect = { vm.selectPlace(it.place) },
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 96.dp, start = 12.dp, end = 12.dp),
        )

        if (state.showSettings) {
            SettingsSheet(
                settings = state.settings,
                savedPlaces = state.savedPlaces,
                userLocation = state.userLocation,
                mapCenter = state.viewport?.center,
                onDismiss = vm::closeSettings,
                onProfile = vm::setProfile,
                onThreshold = vm::setThreshold,
                onAlertsEnabled = vm::setAlertsEnabled,
                onCheckNow = vm::checkAlertsNow,
                onSavePlace = { name, location -> vm.savePlace(name, location) },
                onRemovePlace = vm::removeSavedPlace,
            )
        }
    }
}
