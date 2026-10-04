package com.urbanlens.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.urbanlens.core.crowd.CrowdLevel
import com.urbanlens.core.routing.RouteOption
import com.urbanlens.core.routing.RouteTag
import com.urbanlens.core.routing.TravelMode
import com.urbanlens.ui.RouteState
import com.urbanlens.ui.theme.UrbanColors

@Suppress("DEPRECATION")
fun TravelMode.icon(): ImageVector = when (this) {
    TravelMode.WALK -> Icons.Filled.DirectionsWalk
    TravelMode.CYCLE -> Icons.Filled.DirectionsBike
    TravelMode.DRIVE -> Icons.Filled.DirectionsCar
}

private fun RouteTag.color(): Color = when (this) {
    RouteTag.FASTEST -> UrbanColors.Paper
    RouteTag.CLEANEST -> UrbanColors.hex("#3FB950")
    RouteTag.QUIETEST -> UrbanColors.Teal
    RouteTag.AVOIDS_CONSTRUCTION -> UrbanColors.Orange
}

@Composable
fun RoutePanel(
    route: RouteState,
    onModeChange: (TravelMode) -> Unit,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MapCard(modifier) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                SectionLabel(if (route.originIsUser) "From your location" else "From the map center")
                Text(
                    "To ${route.destinationName}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = UrbanColors.Paper,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close directions", tint = UrbanColors.Muted)
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TravelMode.entries.forEach { mode ->
                FilterChip(
                    selected = route.mode == mode,
                    onClick = { onModeChange(mode) },
                    label = { Text(mode.label) },
                    leadingIcon = { Icon(mode.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = UrbanColors.Orange.copy(alpha = 0.25f),
                        selectedLabelColor = UrbanColors.Paper,
                        selectedLeadingIconColor = UrbanColors.Orange,
                    ),
                )
            }
        }
        when {
            route.loading -> {
                Text(
                    "Comparing routes for air, crowds and construction",
                    style = MaterialTheme.typography.bodySmall,
                    color = UrbanColors.Muted,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                )
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = UrbanColors.Orange, trackColor = UrbanColors.SurfaceHigh)
            }
            route.error != null -> Text(
                route.error,
                style = MaterialTheme.typography.bodyMedium,
                color = UrbanColors.Red,
                modifier = Modifier.padding(top = 12.dp),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(top = 10.dp).heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(route.options, key = { it.id }) { option ->
                    RouteRow(option, selected = option.id == route.selectedId, onClick = { onSelect(option.id) })
                }
            }
        }
    }
}

@Composable
private fun RouteRow(option: RouteOption, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) UrbanColors.SurfaceHigh else UrbanColors.Surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) UrbanColors.Orange else UrbanColors.Outline),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatDuration(option.durationSeconds), style = MaterialTheme.typography.titleLarge, color = UrbanColors.Paper)
                Text(formatDistance(option.distanceMeters), style = MaterialTheme.typography.bodySmall, color = UrbanColors.Muted)
            }
            if (option.tags.isNotEmpty()) {
                Row(
                    Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    option.tags.sortedBy { it.ordinal }.forEach { Pill(it.label, it.color()) }
                }
            }
            val score = option.score
            val parts = buildList {
                score.avgAqi?.let { add("Avg AQI $it") }
                when {
                    option.exposureVsFastestPct > 0 -> add("${option.exposureVsFastestPct}% less pollution")
                    option.exposureVsFastestPct < 0 -> add("${-option.exposureVsFastestPct}% more pollution")
                }
                add("Crowd: ${CrowdLevel.of(score.avgCrowd).label.lowercase()}")
                add(
                    when (score.obstacles.size) {
                        0 -> "No construction"
                        1 -> "Passes ${score.obstacles.first().lowercase()}"
                        else -> "${score.obstacles.size} construction zones"
                    },
                )
            }
            Text(
                parts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = UrbanColors.Muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
