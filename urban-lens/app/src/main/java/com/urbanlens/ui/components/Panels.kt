package com.urbanlens.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.urbanlens.R
import com.urbanlens.core.air.AirEstimate
import com.urbanlens.core.air.AqiCategory
import com.urbanlens.core.crowd.CrowdLevel
import com.urbanlens.core.weather.WeatherCondition
import com.urbanlens.core.weather.WeatherNow
import com.urbanlens.data.MapLayer
import com.urbanlens.ui.PlaceInsight
import com.urbanlens.ui.theme.UrbanColors
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

fun MapLayer.icon(): ImageVector = when (this) {
    MapLayer.CROWD -> Icons.Filled.Groups
    MapLayer.AIR -> Icons.Filled.Air
    MapLayer.CONSTRUCTION -> Icons.Filled.Construction
    MapLayer.REPORTS -> Icons.Filled.Report
    MapLayer.PLACES -> Icons.Filled.Place
}

fun weatherIcon(weather: WeatherNow): ImageVector = when (weather.condition) {
    WeatherCondition.CLEAR -> if (weather.isDay) Icons.Filled.WbSunny else Icons.Filled.NightsStay
    WeatherCondition.PARTLY_CLOUDY -> Icons.Filled.WbCloudy
    WeatherCondition.CLOUDY, WeatherCondition.FOG -> Icons.Filled.Cloud
    WeatherCondition.DRIZZLE -> Icons.Filled.Grain
    WeatherCondition.RAIN -> Icons.Filled.Umbrella
    WeatherCondition.SNOW -> Icons.Filled.AcUnit
    WeatherCondition.THUNDERSTORM -> Icons.Filled.FlashOn
}

@Composable
fun TopBar(onSettings: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(44.dp),
        )
        Column(Modifier.weight(1f)) {
            Text("Urban Lens", style = MaterialTheme.typography.titleLarge, color = UrbanColors.Paper)
            Text("Crowds · Air · Construction", style = MaterialTheme.typography.labelSmall, color = UrbanColors.Muted)
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings and alerts", tint = UrbanColors.Paper)
        }
    }
}

/** Vertical layer switcher, echoing the category list in the design. */
@Composable
fun LayerRail(
    enabled: Set<MapLayer>,
    counts: Map<MapLayer, Int>,
    onToggle: (MapLayer) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = UrbanColors.Surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, UrbanColors.Outline),
    ) {
        Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            MapLayer.entries.forEach { layer ->
                val on = layer in enabled
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onToggle(layer) }
                        .width(56.dp)
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(if (on) UrbanColors.Orange.copy(alpha = 0.18f) else Color.Transparent, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                layer.icon(),
                                contentDescription = "${layer.label} layer ${if (on) "on" else "off"}",
                                tint = if (on) UrbanColors.Orange else UrbanColors.Muted,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        val count = counts[layer] ?: 0
                        if (on && count > 0) {
                            Text(
                                text = if (count > 99) "99+" else count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = UrbanColors.Ink,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .background(UrbanColors.Sand, RoundedCornerShape(50))
                                    .padding(horizontal = 4.dp),
                            )
                        }
                    }
                    Text(
                        layer.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (on) UrbanColors.Paper else UrbanColors.Muted,
                    )
                }
            }
        }
    }
}

/** Color scales for the area layers that are switched on. */
@Composable
fun Legend(showCrowd: Boolean, showAir: Boolean, modifier: Modifier = Modifier) {
    if (!showCrowd && !showAir) return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = UrbanColors.Surface.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, UrbanColors.Outline),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (showCrowd) {
                LegendScale("Crowd", "Quiet", "Packed", CrowdLevel.entries.map { UrbanColors.hex(it.color) })
            }
            if (showAir) {
                LegendScale("AQI", "Good", "Hazardous", AqiCategory.entries.map { UrbanColors.hex(it.color) })
            }
        }
    }
}

@Composable
private fun LegendScale(title: String, low: String, high: String, colors: List<Color>) {
    Column {
        SectionLabel(title)
        Box(
            Modifier
                .padding(vertical = 3.dp)
                .width(120.dp)
                .height(6.dp)
                .background(Brush.horizontalGradient(colors), RoundedCornerShape(50)),
        )
        Row(Modifier.width(120.dp)) {
            Text(low, style = MaterialTheme.typography.labelSmall, color = UrbanColors.Muted, modifier = Modifier.weight(1f))
            Text(high, style = MaterialTheme.typography.labelSmall, color = UrbanColors.Muted)
        }
    }
}

@Composable
fun MapControls(
    locating: Boolean,
    onLocate: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SquareButton(UrbanColors.Orange, onLocate) {
            if (locating) {
                CircularProgressIndicator(Modifier.size(20.dp), color = UrbanColors.Ink, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.MyLocation, contentDescription = "Show my location", tint = UrbanColors.Ink)
            }
        }
        Spacer(Modifier.height(4.dp))
        SquareButton(UrbanColors.Paper, onZoomIn) {
            Icon(Icons.Filled.Add, contentDescription = "Zoom in", tint = UrbanColors.Ink)
        }
        SquareButton(UrbanColors.Paper, onZoomOut) {
            Icon(Icons.Filled.Remove, contentDescription = "Zoom out", tint = UrbanColors.Ink)
        }
    }
}

@Composable
private fun SquareButton(color: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = color,
        shadowElevation = 6.dp,
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** Loading / error / hint line under the top bar. */
@Composable
fun StatusChip(
    loading: Boolean,
    zoomedOutTooFar: Boolean,
    error: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = when {
        error != null -> error
        loading -> "Loading map data"
        zoomedOutTooFar -> "Zoom in to see crowds & construction"
        else -> return
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = UrbanColors.SurfaceHigh.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, UrbanColors.Outline),
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = if (error != null) 4.dp else 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (loading && error == null) {
                CircularProgressIndicator(Modifier.size(14.dp), color = UrbanColors.Orange, strokeWidth = 2.dp)
            }
            Text(text, style = MaterialTheme.typography.labelMedium, color = UrbanColors.Paper)
            if (error != null) {
                TextButton(onClick = onRetry) { Text("Retry", color = UrbanColors.Orange) }
            }
        }
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("EEE, dd MMM · hh:mm a")

/** Weather + air right here, like the corner widget in the design. */
@Composable
fun WeatherWidget(weather: WeatherNow?, air: AirEstimate?, now: LocalDateTime, modifier: Modifier = Modifier) {
    MapCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (weather != null) {
                Icon(weatherIcon(weather), contentDescription = weather.condition.label, tint = UrbanColors.Orange, modifier = Modifier.size(28.dp))
                Text("${weather.temperatureC.roundToInt()}°", style = MaterialTheme.typography.headlineMedium, color = UrbanColors.Paper)
                Column {
                    Text(weather.windCompass, style = MaterialTheme.typography.labelMedium, color = UrbanColors.Paper)
                    Text("${weather.windKmh.roundToInt()} km/h", style = MaterialTheme.typography.labelSmall, color = UrbanColors.Muted)
                }
            } else {
                Text("Weather loading", style = MaterialTheme.typography.bodySmall, color = UrbanColors.Muted)
            }
        }
        Text(now.format(dateFormat), style = MaterialTheme.typography.labelSmall, color = UrbanColors.Muted, modifier = Modifier.padding(top = 4.dp))
        if (air != null) {
            Pill(
                "AQI ${air.aqi} · ${air.category.label}",
                aqiColor(air.aqi),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** "Calm right now": the quietest, cleanest spots in view, like the place list in the design. */
@Composable
fun CalmPlacesRow(places: List<PlaceInsight>, onSelect: (PlaceInsight) -> Unit, modifier: Modifier = Modifier) {
    if (places.isEmpty()) return
    Column(modifier) {
        SectionLabel("Calm right now", Modifier.padding(start = 16.dp, bottom = 6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        ) {
            items(places, key = { it.place.id }) { item ->
                Surface(
                    onClick = { onSelect(item) },
                    shape = RoundedCornerShape(16.dp),
                    color = UrbanColors.Surface.copy(alpha = 0.96f),
                    border = BorderStroke(1.dp, UrbanColors.Outline),
                    shadowElevation = 6.dp,
                ) {
                    Row(
                        Modifier.padding(10.dp).width(200.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val crowd = CrowdLevel.of(item.insight.crowdLevel)
                        Box(
                            Modifier.size(38.dp).background(crowdColor(item.insight.crowdLevel).copy(alpha = 0.22f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                item.place.category.label.take(1),
                                style = MaterialTheme.typography.titleMedium,
                                color = crowdColor(item.insight.crowdLevel),
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.place.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                color = UrbanColors.Paper,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val aqi = item.insight.air?.aqi?.let { " · AQI $it" }.orEmpty()
                            Text(
                                "${crowd.label}$aqi",
                                style = MaterialTheme.typography.bodySmall,
                                color = UrbanColors.Muted,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
