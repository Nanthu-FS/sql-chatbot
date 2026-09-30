package com.spendlens.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.spendlens.app.ui.components.IconTile
import com.spendlens.app.ui.components.Pill
import com.spendlens.app.ui.components.bounceClick
import com.spendlens.app.ui.theme.SpendTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSheet(onDismiss: () -> Unit, onPick: () -> Unit, onAutoFind: () -> Unit, onManual: () -> Unit) {
    val colors = SpendTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Text("Add payments", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Screenshots are read on your phone. Nothing is uploaded.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
            Spacer(Modifier.height(20.dp))
            Option(
                icon = Icons.Rounded.PhotoLibrary,
                brush = colors.brandBrush,
                title = "Choose screenshots",
                body = "Pick one or many payment screenshots",
                onClick = onPick,
            )
            Option(
                icon = Icons.Rounded.AutoAwesome,
                brush = Brush.linearGradient(listOf(Color(0xFF5B7CFF), colors.brand[0])),
                title = "Auto-find payments",
                body = "Scan your Screenshots folder from the last 30 days",
                badge = "Smart",
                onClick = onAutoFind,
            )
            Option(
                icon = Icons.Rounded.EditNote,
                brush = Brush.linearGradient(listOf(colors.positive, Color(0xFF1FA2A0))),
                title = "Add manually",
                body = "Log a cash or card payment",
                onClick = onManual,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Tip: you can also share a screenshot to SpendLens straight from Google Pay, PhonePe, Paytm or your gallery.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textFaint,
            )
        }
    }
}

@Composable
private fun Option(
    icon: ImageVector,
    brush: Brush,
    title: String,
    body: String,
    onClick: () -> Unit,
    badge: String? = null,
) {
    val colors = SpendTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.card)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(24.dp))
            .bounceClick(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, brush = brush, size = 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Pill(badge, colors.brand[1])
                }
            }
            Text(body, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = colors.textFaint)
    }
}
