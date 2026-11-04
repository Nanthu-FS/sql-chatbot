package com.pulse.tracker.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.tracker.PulseViewModel
import com.pulse.tracker.data.AppData
import com.pulse.tracker.data.DrinkKind
import com.pulse.tracker.streak
import com.pulse.tracker.summarize
import com.pulse.tracker.ui.components.IconBadge
import com.pulse.tracker.ui.components.PIconButton
import com.pulse.tracker.ui.components.PIcons
import com.pulse.tracker.ui.components.PLabel
import com.pulse.tracker.ui.components.PText
import com.pulse.tracker.ui.components.PulseCard
import com.pulse.tracker.ui.components.SectionTitle
import com.pulse.tracker.ui.components.formatInt
import com.pulse.tracker.ui.theme.CardStyle
import com.pulse.tracker.ui.theme.ThemeId
import com.pulse.tracker.ui.theme.pulse
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun WaterScreen(vm: PulseViewModel, data: AppData, date: LocalDate) {
    val t = pulse
    val haptics = LocalHapticFeedback.current
    val s = summarize(data, date)
    val goal = data.goals.waterMl
    val pct = (s.waterMl * 100f / goal).roundToInt()
    val byDay = data.water.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.ml } }
    val waterStreak = streak(LocalDate.now()) { (byDay[it] ?: 0) >= goal }
    val avg7 = (1..7).map { byDay[LocalDate.now().minusDays(it.toLong())] ?: 0 }.average() / 1000.0
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    fun add(kind: DrinkKind) {
        vm.addWater(kind.ml, kind.label, date)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
            .padding(horizontal = 20.dp).padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PLabel("Daily goal %.1f L".format(goal / 1000f))
                PText(if (t.id == ThemeId.NEON) "H2O.SYS" else "Hydration", size = if (t.id == ThemeId.SWISS) 30.sp else 24.sp, display = true, italic = t.id == ThemeId.PAPER)
            }
            if (waterStreak > 0) {
                Row(
                    Modifier.height(34.dp).clip(RoundedCornerShape(if (t.radius > 0.dp) 17.dp else 0.dp))
                        .background(if (t.cardStyle == CardStyle.RULED || t.cardStyle == CardStyle.SWISS) Color.Transparent else t.c.surface)
                        .border(if (t.id == ThemeId.BRUTAL) 2.5.dp else 1.dp, t.c.border, RoundedCornerShape(if (t.radius > 0.dp) 17.dp else 0.dp))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(PIcons.Flame, null, Modifier.size(15.dp), tint = t.c.warm)
                    PText("$waterStreak-day streak", size = 13.sp, weight = FontWeight.SemiBold)
                }
            }
        }

        Row(Modifier.fillMaxWidth().height(320.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Bottle(s.waterMl.toFloat() / goal, goal, Modifier.width(160.dp).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight().padding(vertical = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        PText("%.2f".format(s.waterMl / 1000f), size = 50.sp, display = true, tracking = -0.04f, lineHeight = 52.sp)
                        PText(" L", size = 20.sp, display = true, color = t.c.muted, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(if (t.radius > 0.dp) 13.dp else 0.dp))
                            .background(t.c.water.copy(alpha = if (t.isDark) 0.16f else 0.18f)).padding(horizontal = 10.dp, vertical = 5.dp),
                    ) { PText("$pct% of goal", size = 13.sp, weight = FontWeight.Bold, color = t.c.waterText) }
                }
                StatPair(formatInt((goal - s.waterMl).coerceAtLeast(0)) + " ml", if (s.waterMl >= goal) "goal smashed" else "left to drink")
                StatPair("%.1f L".format(avg7), "7-day average")
                StatPair(s.drinks.size.toString(), if (s.drinks.size == 1) "drink today" else "drinks today")
            }
        }

        Row(Modifier.fillMaxWidth().padding(end = if (t.id == ThemeId.BRUTAL) 5.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DrinkButton(PIcons.Cup, "+150", Modifier.weight(1f)) { add(DrinkKind.CUP) }
            DrinkButton(PIcons.Glass, "+250", Modifier.weight(1f)) { add(DrinkKind.GLASS) }
            DrinkButton(PIcons.Bottle, "+500", Modifier.weight(1f), highlighted = true) { add(DrinkKind.BOTTLE) }
            DrinkButton(PIcons.Undo, "Undo", Modifier.weight(1f), enabled = s.drinks.isNotEmpty()) { vm.undoWater(date) }
        }

        SectionTitle(if (date == LocalDate.now()) "Today" else date.format(DateTimeFormatter.ofPattern("EEE d MMM")))
        if (s.drinks.isEmpty()) {
            PText("No drinks logged yet. A glass now is a great start.", size = 14.sp, color = t.c.muted)
        }
        s.drinks.forEach { e ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(PIcons.Drop, t.c.water, 34.dp)
                Column(Modifier.weight(1f)) {
                    PText(e.kind, size = 14.sp, weight = FontWeight.SemiBold)
                    PText(Instant.ofEpochMilli(e.loggedAt).atZone(ZoneId.systemDefault()).format(timeFmt), size = 12.sp, color = t.c.muted)
                }
                PText("+${e.ml} ml", size = 15.sp, display = true, weight = FontWeight.SemiBold)
                PIconButton(PIcons.Close, "Remove drink", { vm.deleteWater(e.id) }, size = 34.dp)
            }
        }
        Spacer(Modifier.height(130.dp))
    }
}

@Composable
private fun StatPair(value: String, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        PText(value, size = 21.sp, display = true, weight = FontWeight.SemiBold)
        PText(label, size = 13.sp, color = pulse.c.muted)
    }
}

@Composable
private fun DrinkButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val t = pulse
    if (highlighted) {
        val fg = if (t.c.water.luminance() > 0.45f) Color(0xFF09090C) else Color.White
        val shape = RoundedCornerShape(if (t.radius > 0.dp) 20.dp else 0.dp)
        Column(
            modifier.height(80.dp)
                .then(if (t.id == ThemeId.BRUTAL) Modifier.drawBehind {
                    drawRoundRect(t.c.border, Offset(4.dp.toPx(), 4.dp.toPx()), size, CornerRadius(20.dp.toPx()))
                } else Modifier)
                .clip(shape).background(t.c.water)
                .then(if (t.id == ThemeId.BRUTAL) Modifier.border(2.5.dp, t.c.border, shape) else Modifier)
                .clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Icon(icon, null, Modifier.size(22.dp), tint = fg)
            PText(label, size = 13.sp, weight = FontWeight.Bold, color = fg)
        }
    } else {
        PulseCard(modifier.height(80.dp), onClick = if (enabled) onClick else null, padding = 0.dp) {
            Column(
                Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            ) {
                Icon(icon, null, Modifier.size(22.dp), tint = if (!enabled) t.c.muted else if (label == "Undo") t.c.text else t.c.waterText)
                PText(label, size = 13.sp, weight = FontWeight.Bold, color = if (enabled) t.c.text else t.c.muted)
            }
        }
    }
}

@Composable
private fun Bottle(fraction: Float, goalMl: Int, modifier: Modifier) {
    val t = pulse
    val level by animateFloatAsState(fraction.coerceIn(0f, 1f), spring(dampingRatio = 0.7f, stiffness = 60f), label = "level")
    val wave = rememberInfiniteTransition(label = "wave")
    val phase by wave.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val rounded = t.radius > 0.dp
    Canvas(modifier) {
        val bw = when (t.id) { ThemeId.BRUTAL -> 3.dp.toPx(); ThemeId.SWISS, ThemeId.PAPER -> 1.5.dp.toPx(); else -> 2.dp.toPx() }
        val corner = if (rounded) size.width / 2 else 0f
        val shape = RoundRect(bw / 2, bw / 2, size.width - bw / 2, size.height - bw / 2, CornerRadius(corner))
        val clip = Path().apply { addRoundRect(shape) }
        val bgFill = if (t.cardStyle == CardStyle.RULED || t.cardStyle == CardStyle.SWISS) t.c.surfaceAlt else t.c.surface
        clipPath(clip) {
            drawRect(bgFill)
            val top = size.height * (1f - level)
            val amp = if (level in 0.01f..0.99f) 6.dp.toPx() else 0f
            fun wavePath(ph: Float, a: Float): Path = Path().apply {
                moveTo(0f, size.height)
                var x = 0f
                lineTo(0f, top + a * sin(ph))
                while (x <= size.width) {
                    lineTo(x, top + a * sin(x / size.width * 2f * PI.toFloat() * 1.3f + ph))
                    x += 4f
                }
                lineTo(size.width, size.height)
                close()
            }
            if (level > 0f) {
                drawPath(wavePath(phase + 1.6f, amp * 1.3f), t.c.water.copy(alpha = 0.35f))
                drawPath(wavePath(phase, amp), t.c.water)
                if (t.id == ThemeId.NEON) {
                    var y = size.height - 8.dp.toPx()
                    while (y > top) {
                        drawLine(t.c.bg, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                        y -= 8.dp.toPx()
                    }
                }
                if (t.id == ThemeId.VOLT || t.id == ThemeId.MATCHA) {
                    val bubble = Color.White.copy(alpha = 0.4f)
                    listOf(0.22f to 0.25f, 0.6f to 0.45f, 0.4f to 0.7f, 0.72f to 0.82f).forEach { (fx, fy) ->
                        val cy = top + (size.height - top) * fy
                        if (cy > top + 12.dp.toPx()) drawCircle(bubble, 4.dp.toPx(), Offset(size.width * fx, cy), style = Stroke(2.dp.toPx()))
                    }
                }
            }
            val liters = goalMl / 1000
            for (l in 1 until (liters.coerceAtLeast(1) + 1)) {
                val y = size.height * (1f - l * 1000f / goalMl)
                if (y > 0f) drawLine(t.c.text.copy(alpha = 0.14f), Offset(size.width * 0.12f, y), Offset(size.width * 0.88f, y), 1.dp.toPx())
            }
        }
        drawRoundRect(t.c.border, shape.topLeftOffset(), shape.sizeOf(), CornerRadius(corner), style = Stroke(bw))
    }
}

private fun RoundRect.topLeftOffset() = Offset(left, top)
private fun RoundRect.sizeOf() = androidx.compose.ui.geometry.Size(width, height)
