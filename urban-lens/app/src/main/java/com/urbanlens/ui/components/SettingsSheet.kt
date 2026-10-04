package com.urbanlens.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.urbanlens.core.air.AqiCategory
import com.urbanlens.core.alerts.HealthProfile
import com.urbanlens.core.geo.LatLng
import com.urbanlens.data.AppSettings
import com.urbanlens.data.SavedPlace
import com.urbanlens.ui.theme.UrbanColors
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: AppSettings,
    savedPlaces: List<SavedPlace>,
    userLocation: LatLng?,
    mapCenter: LatLng?,
    onDismiss: () -> Unit,
    onProfile: (HealthProfile) -> Unit,
    onThreshold: (Int) -> Unit,
    onAlertsEnabled: (Boolean) -> Unit,
    onCheckNow: () -> Unit,
    onSavePlace: (String, LatLng) -> Unit,
    onRemovePlace: (Long) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var saveAt by remember { mutableStateOf<LatLng?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onAlertsEnabled(granted)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = UrbanColors.Surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Text("Alerts & health", style = MaterialTheme.typography.headlineSmall, color = UrbanColors.Paper)
            Spacer(Modifier.height(12.dp))

            SectionLabel("Health profile")
            HealthProfile.entries.forEach { profile ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onProfile(profile) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = settings.profile == profile,
                        onClick = { onProfile(profile) },
                        colors = RadioButtonDefaults.colors(selectedColor = UrbanColors.Orange),
                    )
                    Column {
                        Text(profile.label, style = MaterialTheme.typography.titleSmall, color = UrbanColors.Paper)
                        Text(profile.description, style = MaterialTheme.typography.bodySmall, color = UrbanColors.Muted)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            var sliderValue by remember(settings.threshold) { mutableFloatStateOf(settings.threshold.toFloat()) }
            val previewAqi = sliderValue.roundToInt()
            SectionLabel("Alert when AQI reaches")
            Text(
                "$previewAqi · ${AqiCategory.of(previewAqi).label}",
                style = MaterialTheme.typography.titleMedium,
                color = aqiColor(previewAqi),
            )
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onThreshold(sliderValue.roundToInt()) },
                valueRange = 50f..250f,
                steps = 19,
                colors = SliderDefaults.colors(thumbColor = UrbanColors.Orange, activeTrackColor = UrbanColors.Orange),
            )

            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Air quality alerts", style = MaterialTheme.typography.titleSmall, color = UrbanColors.Paper)
                    Text(
                        "Checks your saved places about every hour.",
                        style = MaterialTheme.typography.bodySmall,
                        color = UrbanColors.Muted,
                    )
                }
                Switch(
                    checked = settings.alertsEnabled,
                    onCheckedChange = { enabled ->
                        val needsPermission = enabled &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        if (needsPermission) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onAlertsEnabled(enabled)
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = UrbanColors.Orange, checkedThumbColor = UrbanColors.Ink),
                )
            }
            if (settings.alertsEnabled && savedPlaces.isNotEmpty()) {
                OutlinedButton(onClick = onCheckNow, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Check now", color = UrbanColors.Paper)
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = UrbanColors.Outline)

            SectionLabel("Saved places")
            if (savedPlaces.isEmpty()) {
                Text(
                    "No saved places yet. Save your location, the map center, or long-press anywhere on the map.",
                    style = MaterialTheme.typography.bodySmall,
                    color = UrbanColors.Muted,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            savedPlaces.forEach { place ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(place.name, style = MaterialTheme.typography.titleSmall, color = UrbanColors.Paper)
                        val status = place.lastAqi?.let { aqi ->
                            "Last AQI $aqi · ${AqiCategory.of(aqi).label} · checked ${relativeTime(place.lastCheckedAtMillis)}"
                        } ?: "Not checked yet"
                        Text(status, style = MaterialTheme.typography.bodySmall, color = UrbanColors.Muted)
                    }
                    IconButton(onClick = { onRemovePlace(place.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove ${place.name}", tint = UrbanColors.Muted)
                    }
                }
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (userLocation != null) {
                    OutlinedButton(onClick = { saveAt = userLocation }) { Text("Save my location", color = UrbanColors.Paper) }
                }
                if (mapCenter != null) {
                    OutlinedButton(onClick = { saveAt = mapCenter }) { Text("Save map center", color = UrbanColors.Paper) }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = UrbanColors.Outline)

            SectionLabel("About the data")
            Text(
                "Air quality: Open-Meteo (CAMS model), refined with estimated construction dust and reported smoke. " +
                    "Construction and places: © OpenStreetMap contributors via Overpass. " +
                    "Routing: OSRM on FOSSGIS servers. Map: © CARTO, © OpenStreetMap contributors. " +
                    "Crowd levels are simulated from nearby places and typical hourly patterns, not live counts.",
                style = MaterialTheme.typography.bodySmall,
                color = UrbanColors.Muted,
                modifier = Modifier.padding(top = 6.dp, bottom = 24.dp),
            )
        }
    }

    saveAt?.let { location ->
        SavePlaceDialog(
            initialName = if (location == userLocation) "Home" else "",
            onSave = { name ->
                onSavePlace(name, location)
                saveAt = null
            },
            onDismiss = { saveAt = null },
        )
    }
}
