package com.pulse.tracker.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.tracker.PulseViewModel
import com.pulse.tracker.data.AppData
import com.pulse.tracker.data.HealthStatus
import com.pulse.tracker.ui.theme.LocalMuted
import com.pulse.tracker.ui.theme.pulse
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Returns an action that does the right next step for Health Connect:
 * install it, ask for step access, or switch syncing back on.
 */
@Composable
fun rememberConnectHealth(vm: PulseViewModel): () -> Unit {
    val status by vm.healthStatus.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        vm.onHealthPermissionsResult(granted)
    }
    return {
        when (status) {
            HealthStatus.UNSUPPORTED -> Unit
            HealthStatus.NEEDS_INSTALL -> vm.openHealthInstall()
            HealthStatus.CONNECTED -> vm.onHealthPermissionsResult(vm.healthPermissions)
            else -> runCatching { launcher.launch(vm.healthPermissions) }.onFailure { vm.openHealthInstall() }
        }
    }
}

/** True when steps are being read from Health Connect right now. */
fun AppData.stepsAreSynced(status: HealthStatus) = stepsFromHealthConnect && status == HealthStatus.CONNECTED

fun lastSyncedLabel(at: Long): String {
    if (at <= 0L) return "Not synced yet"
    val mins = (System.currentTimeMillis() - at) / 60_000
    return when {
        mins < 1 -> "Synced just now"
        mins < 60 -> "Synced $mins min ago"
        else -> "Synced at " + Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
    }
}

/** Status and actions for the Health Connect step link. */
@Composable
fun HealthConnectPanel(vm: PulseViewModel, data: AppData, modifier: Modifier = Modifier) {
    val t = pulse
    val status by vm.healthStatus.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val connect = rememberConnectHealth(vm)
    val on = data.stepsAreSynced(status)

    PulseCard(modifier.fillMaxWidth(), padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(PIcons.Activity, t.c.accentText, 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                PText("Health Connect", size = 15.sp, weight = FontWeight.SemiBold)
                PText(
                    when {
                        on && syncing -> "Syncing steps…"
                        on -> "Steps sync automatically · ${lastSyncedLabel(data.stepsSyncedAt).replaceFirstChar { it.lowercase() }}"
                        status == HealthStatus.UNSUPPORTED -> "Not available on this phone (needs Android 9 or newer)"
                        status == HealthStatus.NEEDS_INSTALL -> "Install it to sync steps automatically"
                        status == HealthStatus.UNKNOWN -> "Checking…"
                        else -> "Off — steps are entered by hand"
                    },
                    size = 12.sp, color = LocalMuted.current,
                )
            }
        }
        if (!on && status != HealthStatus.UNSUPPORTED) {
            Spacer(Modifier.height(10.dp))
            PText(
                "Reads your daily step count from your phone’s step counter, Google Fit, Samsung Health, " +
                    "Fitbit or your watch — whatever already writes to Health Connect. Pulse only reads steps and never uploads them.",
                size = 12.sp, color = LocalMuted.current,
            )
        }
        Spacer(Modifier.height(12.dp))
        when {
            on -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PButton(if (syncing) "Syncing…" else "Sync now", { vm.syncSteps(force = true) }, Modifier.weight(1f), icon = PIcons.Refresh, height = 44.dp, enabled = !syncing)
                TextAction("Manage") { vm.openHealthSettings() }
                TextAction("Turn off") { vm.disconnectHealth() }
            }
            status == HealthStatus.UNSUPPORTED -> Unit
            status == HealthStatus.NEEDS_INSTALL -> PButton("Install Health Connect", connect, Modifier.fillMaxWidth(), height = 46.dp)
            else -> PButton("Connect Health Connect", connect, Modifier.fillMaxWidth(), icon = PIcons.Activity, height = 46.dp)
        }
    }
}

@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    PText(
        if (pulse.upperLabels) label.uppercase() else label,
        size = 13.sp, weight = FontWeight.SemiBold, color = pulse.c.muted,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp),
    )
}
