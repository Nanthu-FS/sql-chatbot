package com.urbanlens.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.urbanlens.core.alerts.HealthProfile
import com.urbanlens.core.construction.ConstructionSite
import com.urbanlens.core.crowd.CrowdLevel
import com.urbanlens.core.geo.LatLng
import com.urbanlens.core.reports.CommunityReport
import com.urbanlens.core.reports.ReportPolicy
import com.urbanlens.ui.PlaceInsight
import com.urbanlens.ui.Selection
import com.urbanlens.ui.SpotInsight
import com.urbanlens.ui.theme.UrbanColors
import java.time.LocalDateTime
import java.util.Locale

@Composable
fun SelectionCard(
    selection: Selection,
    now: LocalDateTime,
    profile: HealthProfile,
    onClose: () -> Unit,
    onDirections: (LatLng, String) -> Unit,
    onReportHere: (LatLng) -> Unit,
    onSavePlace: (LatLng, String) -> Unit,
    onConfirmReport: (Long) -> Unit,
    onRemoveReport: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var saving by remember(selection.location) { mutableStateOf<String?>(null) }

    MapCard(modifier) {
        when (selection) {
            is Selection.OfPlace -> PlaceContent(
                details = selection.details,
                now = now,
                profile = profile,
                onClose = onClose,
                onDirections = { onDirections(selection.location, selection.details.place.displayName) },
                onSave = { saving = selection.details.place.displayName },
            )
            is Selection.OfSite -> SiteContent(
                site = selection.site,
                insight = selection.insight,
                onClose = onClose,
                onReport = { onReportHere(selection.location) },
            )
            is Selection.OfReport -> ReportContent(
                report = selection.report,
                insight = selection.insight,
                profile = profile,
                onClose = onClose,
                onConfirm = { onConfirmReport(selection.report.id) },
                onRemove = { onRemoveReport(selection.report.id) },
            )
            is Selection.OfPoint -> PointContent(
                location = selection.location,
                insight = selection.insight,
                profile = profile,
                onClose = onClose,
                onDirections = { onDirections(selection.location, "Dropped pin") },
                onReport = { onReportHere(selection.location) },
                onSave = { saving = "" },
            )
        }
    }

    saving?.let { initial ->
        SavePlaceDialog(
            initialName = initial,
            onSave = { name ->
                onSavePlace(selection.location, name)
                saving = null
            },
            onDismiss = { saving = null },
        )
    }
}

@Composable
private fun Header(kicker: String, title: String, onClose: () -> Unit, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            SectionLabel(kicker)
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = UrbanColors.Paper,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = UrbanColors.Muted)
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = UrbanColors.Muted)
        }
    }
}

/** Crowd, AQI and construction at a glance, plus health advice. */
@Composable
fun InsightRow(insight: SpotInsight, profile: HealthProfile) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val crowd = CrowdLevel.of(insight.crowdLevel)
        Metric("Crowd", "${insight.crowdLevel}", crowd.label, crowdColor(insight.crowdLevel), Modifier.weight(1f))
        val air = insight.air
        if (air != null) {
            Metric("AQI", "${air.aqi}", air.category.label, aqiColor(air.aqi), Modifier.weight(1f))
        } else {
            Metric("AQI", "–", "Unavailable", UrbanColors.Muted, Modifier.weight(1f))
        }
        val works = insight.nearbySites.size
        Metric(
            "Works",
            "$works",
            if (works == 0) "None nearby" else "within 400 m",
            if (works == 0) UrbanColors.Teal else UrbanColors.Orange,
            Modifier.weight(1f),
        )
    }
    insight.air?.let { air ->
        val advice = if (profile == HealthProfile.SENSITIVE) air.category.sensitiveAdvice else air.category.advice
        val dust = if (air.localBoost > 0) " Local ${air.localSources.joinToString(", ").lowercase(Locale.getDefault())} adds about ${air.localBoost} AQI here." else ""
        Text(
            advice + dust,
            style = MaterialTheme.typography.bodySmall,
            color = UrbanColors.Muted,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun PlaceContent(
    details: PlaceInsight,
    now: LocalDateTime,
    profile: HealthProfile,
    onClose: () -> Unit,
    onDirections: () -> Unit,
    onSave: () -> Unit,
) {
    Header(details.place.category.label, details.place.displayName, onClose)
    InsightRow(details.insight, profile)
    Spacer(Modifier.height(12.dp))
    SectionLabel("Usually busy")
    BusyChart(details.hourly, now.hour, details.bestHour)
    Text(
        details.bestHour?.let { "Quietest time left today: ${hourLabel(it)}" } ?: "Quieter tomorrow morning",
        style = MaterialTheme.typography.bodySmall,
        color = UrbanColors.Muted,
    )
    NearbyWorks(details.insight.nearbySites)
    ActionRow {
        PrimaryAction("Directions", Icons.Filled.Directions, onDirections)
        SecondaryAction("Save", Icons.Filled.BookmarkBorder, onSave)
    }
}

@Composable
private fun SiteContent(site: ConstructionSite, insight: SpotInsight, onClose: () -> Unit, onReport: () -> Unit) {
    Header(site.kind.label, site.title, onClose, subtitle = "Source: ${site.source.label}")
    Row(
        Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        site.kind.impacts.forEach { Pill(it, UrbanColors.Orange) }
    }
    val facts = listOfNotNull(
        site.startDate?.let { "Started: $it" },
        site.expectedEnd?.let { "Expected to open: $it" },
        site.operator?.let { "By: $it" },
        site.note,
    )
    facts.forEach {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = UrbanColors.Paper, modifier = Modifier.padding(top = 6.dp))
    }
    insight.air?.let { air ->
        val text = if (air.localBoost > 0) {
            "Dust here adds an estimated ${air.localBoost} AQI (now ${air.aqi}, ${air.category.label.lowercase(Locale.getDefault())})."
        } else {
            "AQI here: ${air.aqi} (${air.category.label.lowercase(Locale.getDefault())})."
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = UrbanColors.Muted, modifier = Modifier.padding(top = 8.dp))
    }
    Text(
        "Routes steer around this site. OpenStreetMap data can lag behind reality.",
        style = MaterialTheme.typography.bodySmall,
        color = UrbanColors.Muted,
        modifier = Modifier.padding(top = 4.dp),
    )
    ActionRow {
        SecondaryAction("Report update", Icons.Filled.Report, onReport)
    }
}

@Composable
private fun ReportContent(
    report: CommunityReport,
    insight: SpotInsight,
    profile: HealthProfile,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
    onRemove: () -> Unit,
) {
    Header(
        "Community report",
        report.type.label,
        onClose,
        subtitle = "${relativeTime(report.createdAtMillis)} · ${ReportPolicy.confidenceLabel(report)}",
    )
    if (report.note.isNotBlank()) {
        Text(report.note, style = MaterialTheme.typography.bodyMedium, color = UrbanColors.Paper, modifier = Modifier.padding(top = 8.dp))
    }
    rememberPhoto(report.photoPath)?.let { photo ->
        Image(
            bitmap = photo,
            contentDescription = "Report photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
    }
    InsightRow(insight, profile)
    Text(
        "Shown until ${relativeTime(ReportPolicy.expiresAtMillis(report))} unless someone confirms it.",
        style = MaterialTheme.typography.bodySmall,
        color = UrbanColors.Muted,
        modifier = Modifier.padding(top = 6.dp),
    )
    ActionRow {
        PrimaryAction("Still there", Icons.Filled.ThumbUp, onConfirm)
        SecondaryAction("It's gone", Icons.Filled.Delete, onRemove)
    }
}

@Composable
private fun PointContent(
    location: LatLng,
    insight: SpotInsight,
    profile: HealthProfile,
    onClose: () -> Unit,
    onDirections: () -> Unit,
    onReport: () -> Unit,
    onSave: () -> Unit,
) {
    val mood = when {
        insight.comfort >= 70 -> "Calm right now"
        insight.comfort >= 45 -> "A bit lively"
        else -> "Hectic right now"
    }
    Header(
        "This spot",
        mood,
        onClose,
        subtitle = String.format(Locale.ROOT, "%.5f, %.5f", location.lat, location.lng),
    )
    InsightRow(insight, profile)
    NearbyWorks(insight.nearbySites)
    ActionRow {
        PrimaryAction("Go", Icons.Filled.Directions, onDirections)
        SecondaryAction("Report", Icons.Filled.Report, onReport)
        SecondaryAction("Save", Icons.Filled.BookmarkBorder, onSave)
    }
}

@Composable
private fun NearbyWorks(sites: List<ConstructionSite>) {
    if (sites.isEmpty()) return
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = UrbanColors.Orange, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "Nearby: " + sites.take(3).joinToString(", ") { it.title },
            style = MaterialTheme.typography.bodySmall,
            color = UrbanColors.Paper,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Typical busyness from 6 am to midnight; the current hour is orange, the best remaining hour teal. */
@Composable
fun BusyChart(hourly: List<Double>, currentHour: Int, bestHour: Int?) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for (hour in 6..23) {
                val value = hourly.getOrElse(hour) { 0.0 }.toFloat()
                val color = when (hour) {
                    currentHour -> UrbanColors.Orange
                    bestHour -> UrbanColors.Teal
                    else -> UrbanColors.Muted.copy(alpha = 0.45f)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(value.coerceIn(0.06f, 1f))
                        .background(color, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            listOf("6a", "12p", "6p", "11p").forEachIndexed { i, label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = UrbanColors.Muted,
                    modifier = Modifier.weight(if (i == 3) 0.5f else 1f),
                )
            }
        }
    }
}

@Composable
private fun ActionRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun RowScope.PrimaryAction(label: String, icon: ImageVector, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.buttonColors(containerColor = UrbanColors.Sand, contentColor = UrbanColors.Ink),
        shape = RoundedCornerShape(10.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label.uppercase(), style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun RowScope.SecondaryAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = UrbanColors.Paper),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SavePlaceDialog(initialName: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = UrbanColors.Surface,
        title = { Text("Save place", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Saved places get air quality alerts when alerts are on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = UrbanColors.Muted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Home", "Work", "School").forEach { option ->
                        FilterChip(
                            selected = name == option,
                            onClick = { name = option },
                            label = { Text(option) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = UrbanColors.Orange.copy(alpha = 0.25f)),
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Name") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Save", color = UrbanColors.Orange) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = UrbanColors.Muted) }
        },
    )
}
