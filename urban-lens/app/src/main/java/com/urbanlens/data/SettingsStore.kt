package com.urbanlens.data

import android.content.Context
import com.urbanlens.core.alerts.HealthProfile
import com.urbanlens.core.geo.LatLng
import com.urbanlens.core.routing.TravelMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MapLayer(val label: String) {
    CROWD("Crowd"),
    AIR("Air"),
    CONSTRUCTION("Works"),
    REPORTS("Reports"),
    PLACES("Places"),
}

data class AppSettings(
    val profile: HealthProfile = HealthProfile.GENERAL,
    val threshold: Int = profile.defaultThreshold,
    val alertsEnabled: Boolean = false,
    val layers: Set<MapLayer> = MapLayer.entries.toSet(),
    val travelMode: TravelMode = TravelMode.WALK,
)

data class CameraSnapshot(val target: LatLng, val zoom: Double)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("urban_lens", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun current(): AppSettings = _settings.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putString(KEY_PROFILE, next.profile.name)
            .putInt(KEY_THRESHOLD, next.threshold)
            .putBoolean(KEY_ALERTS, next.alertsEnabled)
            .putStringSet(KEY_LAYERS, next.layers.map { it.name }.toSet())
            .putString(KEY_MODE, next.travelMode.name)
            .apply()
        _settings.value = next
    }

    fun lastCamera(): CameraSnapshot? {
        if (!prefs.contains(KEY_CAM_LAT)) return null
        return CameraSnapshot(
            LatLng(prefs.getFloat(KEY_CAM_LAT, 0f).toDouble(), prefs.getFloat(KEY_CAM_LNG, 0f).toDouble()),
            prefs.getFloat(KEY_CAM_ZOOM, DEFAULT_ZOOM.toFloat()).toDouble(),
        )
    }

    fun saveCamera(camera: CameraSnapshot) {
        prefs.edit()
            .putFloat(KEY_CAM_LAT, camera.target.lat.toFloat())
            .putFloat(KEY_CAM_LNG, camera.target.lng.toFloat())
            .putFloat(KEY_CAM_ZOOM, camera.zoom.toFloat())
            .apply()
    }

    private fun read(): AppSettings {
        val profile = enumValue(prefs.getString(KEY_PROFILE, null), HealthProfile.GENERAL)
        val layers = prefs.getStringSet(KEY_LAYERS, null)
            ?.mapNotNull { name -> MapLayer.entries.firstOrNull { it.name == name } }
            ?.toSet()
            ?: MapLayer.entries.toSet()
        return AppSettings(
            profile = profile,
            threshold = prefs.getInt(KEY_THRESHOLD, profile.defaultThreshold),
            alertsEnabled = prefs.getBoolean(KEY_ALERTS, false),
            layers = layers,
            travelMode = enumValue(prefs.getString(KEY_MODE, null), TravelMode.WALK),
        )
    }

    private inline fun <reified T : Enum<T>> enumValue(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    companion object {
        /** Chennai Central, used until we know where the user is. */
        val DEFAULT_CENTER = LatLng(13.0827, 80.2707)
        const val DEFAULT_ZOOM = 13.5

        private const val KEY_PROFILE = "profile"
        private const val KEY_THRESHOLD = "threshold"
        private const val KEY_ALERTS = "alerts_enabled"
        private const val KEY_LAYERS = "layers"
        private const val KEY_MODE = "travel_mode"
        private const val KEY_CAM_LAT = "camera_lat"
        private const val KEY_CAM_LNG = "camera_lng"
        private const val KEY_CAM_ZOOM = "camera_zoom"
    }
}
