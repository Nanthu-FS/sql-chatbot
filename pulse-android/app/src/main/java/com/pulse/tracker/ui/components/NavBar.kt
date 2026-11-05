package com.pulse.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.tracker.ui.theme.NavStyle
import com.pulse.tracker.ui.theme.pulse

enum class Tab(val label: String, val terminal: String, val editorial: String) {
    TODAY("Today", "TODAY", "Today"),
    INSIGHTS("Insights", "STATS", "Trends"),
    WATER("Water", "H2O", "Water"),
    HABITS("Habits", "HABIT", "Habits");

    val icon: ImageVector
        get() = when (this) {
            TODAY -> PIcons.Home
            INSIGHTS -> PIcons.Chart
            WATER -> PIcons.Drop
            HABITS -> PIcons.Habits
        }
}

@Composable
fun PulseNavBar(current: Tab, onSelect: (Tab) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val left = listOf(Tab.TODAY, Tab.INSIGHTS)
    val right = listOf(Tab.WATER, Tab.HABITS)
    val t = pulse
    val c = t.c
    when (t.navStyle) {
        NavStyle.FLOATING -> Row(
            modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth().height(68.dp)
                .shadow(24.dp, RoundedCornerShape(26.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(26.dp)).background(t.solidSurface.copy(alpha = 0.96f))
                .border(1.dp, c.border, RoundedCornerShape(26.dp)).padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically,
        ) {
            left.forEach { IconLabelItem(it, it == current, c.accent, c.muted) { onSelect(it) } }
            Fab(onAdd, RoundedCornerShape(20.dp), c.accent, c.onAccent)
            right.forEach { IconLabelItem(it, it == current, c.accent, c.muted) { onSelect(it) } }
        }

        NavStyle.EDITORIAL -> Row(
            modifier.fillMaxWidth().background(c.bg).drawBehind {
                drawLine(c.accent, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
            }.navigationBarsPadding().height(72.dp).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            left.forEach { TextItem(it, it.editorial.uppercase(), it == current, c.text, c.muted, underline = true) { onSelect(it) } }
            Fab(onAdd, RoundedCornerShape(50), c.accent, c.onAccent, size = 54)
            right.forEach { TextItem(it, it.editorial.uppercase(), it == current, c.text, c.muted, underline = true) { onSelect(it) } }
        }

        NavStyle.BRUTAL -> Row(
            modifier.navigationBarsPadding().padding(start = 16.dp, end = 21.dp, bottom = 21.dp, top = 16.dp).fillMaxWidth().height(68.dp)
                .drawBehind { drawRoundRect(c.border, Offset(5.dp.toPx(), 5.dp.toPx()), size, CornerRadius(20.dp.toPx())) }
                .clip(RoundedCornerShape(20.dp)).background(Color.White)
                .border(2.5.dp, c.border, RoundedCornerShape(20.dp)).padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically,
        ) {
            left.forEach { BrutalItem(it, it == current) { onSelect(it) } }
            Box(
                Modifier.size(58.dp).rotate(-4f).clip(RoundedCornerShape(16.dp)).background(c.accent)
                    .border(2.5.dp, c.border, RoundedCornerShape(16.dp)).clickable(onClick = onAdd)
                    .semantics { contentDescription = "Log food" },
                contentAlignment = Alignment.Center,
            ) { Icon(PIcons.Plus, null, Modifier.size(28.dp), tint = c.text) }
            right.forEach { BrutalItem(it, it == current) { onSelect(it) } }
        }

        NavStyle.TERMINAL -> Row(
            modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth().height(64.dp)
                .background(c.surface).border(1.dp, c.border).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically,
        ) {
            left.forEach { val on = it == current; TextItem(it, if (on) "[${it.terminal}]" else it.terminal, on, c.accent, c.muted) { onSelect(it) } }
            Box(
                Modifier.width(52.dp).height(44.dp).shadow(16.dp, RoundedCornerShape(0.dp), ambientColor = c.accent, spotColor = c.accent)
                    .background(c.accent).clickable(onClick = onAdd).semantics { contentDescription = "Log food" },
                contentAlignment = Alignment.Center,
            ) { PText("+", size = 24.sp, weight = FontWeight.ExtraBold, color = c.onAccent) }
            right.forEach { val on = it == current; TextItem(it, if (on) "[${it.terminal}]" else it.terminal, on, c.accent, c.muted) { onSelect(it) } }
        }

        NavStyle.PILL -> Row(
            modifier.navigationBarsPadding().padding(20.dp).fillMaxWidth().height(70.dp)
                .shadow(18.dp, RoundedCornerShape(35.dp), ambientColor = c.text.copy(alpha = 0.2f), spotColor = c.text.copy(alpha = 0.25f))
                .clip(RoundedCornerShape(35.dp)).background(c.surface).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            left.forEach { PillItem(it, it == current) { onSelect(it) } }
            Fab(onAdd, RoundedCornerShape(50), c.accent, c.onAccent, size = 54)
            right.forEach { PillItem(it, it == current) { onSelect(it) } }
        }

        NavStyle.RETRO -> Row(
            modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 21.dp).fillMaxWidth().height(70.dp)
                .drawBehind { drawRoundRect(c.warm, Offset(0f, 5.dp.toPx()), size, CornerRadius(35.dp.toPx())) }
                .clip(RoundedCornerShape(35.dp)).background(c.surface)
                .border(2.dp, c.border, RoundedCornerShape(35.dp)).padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically,
        ) {
            left.forEach { IconLabelItem(it, it == current, c.accentText, c.muted) { onSelect(it) } }
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(50)).drawBehind {
                    drawCircle(c.fat, size.minDimension / 2)
                    drawCircle(c.carbs, size.minDimension / 2 - 5.dp.toPx())
                    drawCircle(c.accent, size.minDimension / 2 - 10.dp.toPx())
                }.border(2.dp, c.border, RoundedCornerShape(50)).clickable(onClick = onAdd)
                    .semantics { contentDescription = "Log food" },
                contentAlignment = Alignment.Center,
            ) { Icon(PIcons.Plus, null, Modifier.size(24.dp), tint = c.onAccent) }
            right.forEach { IconLabelItem(it, it == current, c.accentText, c.muted) { onSelect(it) } }
        }

        NavStyle.PIXEL -> Row(
            modifier.navigationBarsPadding().padding(start = 16.dp, end = 20.dp, top = 16.dp, bottom = 20.dp).fillMaxWidth().height(64.dp)
                .drawBehind { drawRect(c.carbs, Offset(4.dp.toPx(), 4.dp.toPx()), size) }
                .clip(CutCornerShape(6.dp)).background(c.surface)
                .border(3.dp, c.border, CutCornerShape(6.dp)).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically,
        ) {
            left.forEach { val on = it == current; TextItem(it, (if (on) "▶" else " ") + it.terminal, on, c.text, c.muted) { onSelect(it) } }
            Box(
                Modifier.size(46.dp).clip(CutCornerShape(5.dp)).background(c.text).clickable(onClick = onAdd)
                    .semantics { contentDescription = "Log food" },
                contentAlignment = Alignment.Center,
            ) { PText("+", size = 26.sp, weight = FontWeight.Bold, color = c.bg) }
            right.forEach { val on = it == current; TextItem(it, (if (on) "▶" else " ") + it.terminal, on, c.text, c.muted) { onSelect(it) } }
        }

        NavStyle.BLOCK -> Row(
            modifier.fillMaxWidth().background(c.text).navigationBarsPadding().height(72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            left.forEach { BlockItem(it, it == current, Modifier.weight(1f)) { onSelect(it) } }
            Box(
                Modifier.weight(1f).fillMaxHeight().offset(y = (-10).dp).background(c.accent).clickable(onClick = onAdd)
                    .semantics { contentDescription = "Log food" },
                contentAlignment = Alignment.Center,
            ) { Icon(PIcons.Plus, null, Modifier.size(28.dp), tint = c.onAccent) }
            right.forEach { BlockItem(it, it == current, Modifier.weight(1f)) { onSelect(it) } }
        }
    }
}

@Composable
private fun Fab(onClick: () -> Unit, shape: RoundedCornerShape, bg: Color, fg: Color, size: Int = 56) {
    Box(
        Modifier.size(size.dp).clip(shape).background(bg).clickable(onClick = onClick)
            .semantics { contentDescription = "Log food" },
        contentAlignment = Alignment.Center,
    ) { Icon(PIcons.Plus, null, Modifier.size(26.dp), tint = fg) }
}

@Composable
private fun IconLabelItem(tab: Tab, selected: Boolean, on: Color, off: Color, onClick: () -> Unit) {
    Column(
        Modifier.width(60.dp).height(54.dp).clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        Icon(tab.icon, null, Modifier.size(22.dp), tint = if (selected) on else off)
        PText(tab.label, size = 11.sp, weight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = if (selected) on else off)
    }
}

@Composable
private fun TextItem(tab: Tab, text: String, selected: Boolean, on: Color, off: Color, underline: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.height(48.dp).clickable(onClick = onClick).semantics { this.selected = selected; contentDescription = tab.label }
            .then(if (underline && selected) Modifier.drawBehind {
                drawLine(on, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 2.dp.toPx())
            } else Modifier)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        PText(text, size = 12.sp, weight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) on else off, tracking = 0.12f, maxLines = 1)
    }
}

@Composable
private fun BrutalItem(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val c = pulse.c
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFFFFD93D) else Color.Transparent)
            .then(if (selected) Modifier.border(2.dp, c.border, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick).semantics { this.selected = selected; contentDescription = tab.label },
        contentAlignment = Alignment.Center,
    ) { Icon(tab.icon, null, Modifier.size(22.dp), tint = c.text) }
}

@Composable
private fun PillItem(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val c = pulse.c
    if (selected) {
        Row(
            Modifier.height(50.dp).clip(RoundedCornerShape(25.dp)).background(c.surfaceAlt).clickable(onClick = onClick)
                .semantics { this.selected = true }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(tab.icon, null, Modifier.size(20.dp), tint = c.accentText)
            PText(tab.label, size = 13.sp, weight = FontWeight.ExtraBold, color = c.accentText)
        }
    } else {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(23.dp)).clickable(onClick = onClick)
                .semantics { contentDescription = tab.label },
            contentAlignment = Alignment.Center,
        ) { Icon(tab.icon, null, Modifier.size(21.dp), tint = c.muted) }
    }
}

@Composable
private fun BlockItem(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = pulse.c
    Box(
        modifier.fillMaxHeight().clickable(onClick = onClick).semantics { this.selected = selected; contentDescription = tab.label }
            .drawBehind { if (selected) drawLine(c.accent, Offset(0f, 1.5.dp.toPx()), Offset(size.width, 1.5.dp.toPx()), 3.dp.toPx()) },
        contentAlignment = Alignment.Center,
    ) {
        PText(tab.editorial.uppercase(), size = 11.sp, weight = if (selected) FontWeight.ExtraBold else FontWeight.Bold, color = if (selected) Color.White else Color(0xFFA8A8A8), tracking = 0.1f)
    }
}

