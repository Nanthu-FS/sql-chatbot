package com.pulse.tracker.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.tracker.ui.components.PLabel
import com.pulse.tracker.ui.components.PText
import com.pulse.tracker.ui.components.PulseCard
import com.pulse.tracker.ui.components.drawBlueprintFrame
import com.pulse.tracker.ui.components.formatInt
import com.pulse.tracker.ui.theme.LocalMuted
import com.pulse.tracker.ui.theme.pulse
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
private fun rememberIntro(): Float {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(1200, easing = FastOutSlowInEasing)) }
    return p.value
}

@Composable
private fun StatTrio(h: HeroData, cell: @Composable (label: String, value: String, modifier: Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cell("Eaten", formatInt(h.eaten), Modifier.weight(1f))
        cell("Burned", formatInt(h.burned), Modifier.weight(1f))
        cell("Goal", formatInt(h.goal), Modifier.weight(1f))
    }
}

// ================================================================ Aurora — glowing gradient ring

@Composable
fun AuroraHero(h: HeroData) {
    val t = pulse
    val intro = rememberIntro()
    PulseCard(padding = 18.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PText("Today’s energy", size = 13.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            PText("${h.pct}%", size = 13.sp, weight = FontWeight.SemiBold, color = t.c.accentText)
        }
        Box(Modifier.size(224.dp).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
            val colors = listOf(t.c.protein, t.c.carbs, t.c.fat, t.c.protein)
            Canvas(Modifier.fillMaxSize()) {
                val sw = 18.dp.toPx()
                val pad = sw * 1.4f
                val arc = Size(size.width - pad * 2, size.height - pad * 2)
                val tl = Offset(pad, pad)
                drawArc(t.c.track, 0f, 360f, false, tl, arc, style = Stroke(sw))
                val sweep = 360f * h.usedFraction * intro
                if (sweep > 0.5f) {
                    rotate(-90f) {
                        val brush = Brush.sweepGradient(colors, center)
                        drawArc(brush, 0f, sweep, false, tl, arc, alpha = 0.18f, style = Stroke(sw * 2.4f, cap = StrokeCap.Round))
                        drawArc(brush, 0f, sweep, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PText(leftValue(h), size = 52.sp, display = true, lineHeight = 54.sp)
                PText(leftLabel(h), size = 13.sp, color = t.c.muted)
            }
        }
        Spacer(Modifier.height(8.dp))
        StatTrio(h) { label, value, m ->
            Column(
                m.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp)).padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PText(label, size = 12.sp, color = t.c.muted)
                PText(value, size = 17.sp, display = true, weight = FontWeight.SemiBold)
            }
        }
    }
}

// ================================================================ Noir — watch dial

@Composable
fun NoirHero(h: HeroData) {
    val t = pulse
    val intro = rememberIntro()
    PulseCard(padding = 20.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            PLabel("Today · ${h.pct}%", color = t.c.accentText)
            Box(Modifier.size(232.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2 - 2.dp.toPx()
                    drawCircle(t.c.border, r, center, style = Stroke(0.8.dp.toPx()))
                    val done = (h.usedFraction * 60 * intro).roundToInt()
                    for (i in 0 until 60) {
                        val a = (-90f + i * 6f) * (PI.toFloat() / 180f)
                        val major = i % 5 == 0
                        val r1 = r - (if (major) 16.dp else 9.dp).toPx()
                        val r2 = r - 4.dp.toPx()
                        drawLine(
                            if (i < done) t.c.accent else t.c.border,
                            Offset(center.x + cos(a) * r1, center.y + sin(a) * r1),
                            Offset(center.x + cos(a) * r2, center.y + sin(a) * r2),
                            (if (major) 1.6.dp else 0.8.dp).toPx(),
                        )
                    }
                    val ir = r - 26.dp.toPx()
                    val tl = Offset(center.x - ir, center.y - ir)
                    drawArc(t.c.track, 0f, 360f, false, tl, Size(ir * 2, ir * 2), style = Stroke(1.dp.toPx()))
                    val sweep = 360f * h.usedFraction * intro
                    drawArc(t.c.accent, -90f, sweep, false, tl, Size(ir * 2, ir * 2), style = Stroke(2.dp.toPx()))
                    val head = (-90f + sweep) * (PI.toFloat() / 180f)
                    drawCircle(t.c.accent, 3.5.dp.toPx(), Offset(center.x + cos(head) * ir, center.y + sin(head) * ir))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PText(leftValue(h), size = 62.sp, display = true, lineHeight = 60.sp)
                    PLabel(if (h.over) "kcal over" else "kcal remaining")
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.size(5.dp).background(t.c.accent))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                listOf("Eaten" to h.eaten, "Burned" to h.burned, "Goal" to h.goal).forEachIndexed { i, (l, v) ->
                    if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(t.c.accent.copy(alpha = 0.4f)))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        PLabel(l)
                        PText(formatInt(v), size = 26.sp, display = true)
                    }
                }
            }
        }
    }
}

// ================================================================ Retro — 70s rainbow

@Composable
fun RetroHero(h: HeroData) {
    val t = pulse
    val intro = rememberIntro()
    val bands = listOf(
        h.usedFraction to t.c.accent,
        h.proteinFraction to t.c.protein,
        h.carbsFraction to t.c.carbs,
        h.fatFraction to t.c.fat,
    )
    PulseCard(padding = 18.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PText("Fuel gauge", size = 20.sp, display = true, modifier = Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(14.dp)).background(t.c.surfaceAlt).padding(horizontal = 10.dp, vertical = 4.dp)) {
                PText("${h.pct}% used", size = 12.sp, weight = FontWeight.Bold, color = t.c.accentText)
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(172.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 15.dp.toPx()
                val gap = 4.dp.toPx()
                val outer = min(size.width / 2, size.height) - sw / 2
                bands.forEachIndexed { i, (frac, color) ->
                    val r = outer - i * (sw + gap)
                    val tl = Offset(size.width / 2 - r, size.height - r)
                    val arc = Size(r * 2, r * 2)
                    drawArc(t.c.track, 180f, 180f, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
                    val sweep = 180f * frac.coerceIn(0f, 1f) * intro
                    if (sweep > 0.5f) drawArc(color, 180f, sweep, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
                }
            }
            Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                PText(leftValue(h), size = 40.sp, display = true, lineHeight = 42.sp)
                PText(leftLabel(h), size = 12.sp, color = t.c.muted)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Calories" to t.c.accent, "Protein" to t.c.protein, "Carbs" to t.c.carbs, "Fat" to t.c.fat).forEach { (l, col) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(9.dp).clip(RoundedCornerShape(5.dp)).background(col))
                    PText(l, size = 12.sp, weight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        StatTrio(h) { label, value, m ->
            Column(m.clip(RoundedCornerShape(18.dp)).background(t.c.surfaceAlt).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PText(label, size = 12.sp, color = t.c.muted)
                PText(value, size = 18.sp, display = true)
            }
        }
    }
}

// ================================================================ Pixel — 8-bit energy bar

@Composable
fun PixelBlocks(fraction: Float, blocks: Int, height: androidx.compose.ui.unit.Dp, color: Color, modifier: Modifier = Modifier) {
    val t = pulse
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "blocks")
    val filled = (anim * blocks).roundToInt()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(blocks) { i ->
            Box(Modifier.weight(1f).height(height).background(if (i < filled) color else t.c.track))
        }
    }
}

@Composable
fun PixelHero(h: HeroData) {
    val t = pulse
    PulseCard(padding = 16.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PText("ENERGY", size = 14.sp, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
            PText(if (h.over) "!! OVER" else "${h.pct}%", size = 14.sp, weight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        PText(leftValue(h), size = 58.sp, display = true, lineHeight = 60.sp)
        PText(if (h.over) "KCAL OVER GOAL" else "KCAL LEFT", size = 13.sp, color = LocalMuted.current, tracking = 0.06f)
        Spacer(Modifier.height(12.dp))
        PixelBlocks(h.usedFraction, 16, 18.dp, t.c.text, Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Readout("EAT", formatInt(h.eaten), t.c.text)
        Readout("BURN", formatInt(h.burned), t.c.text)
        Readout("GOAL", formatInt(h.goal), t.c.text)
    }
}

// ================================================================ Candy — jelly bubble

@Composable
fun CandyHero(h: HeroData) {
    val t = pulse
    val level by animateFloatAsState(h.usedFraction.coerceIn(0f, 1f), tween(1100, easing = FastOutSlowInEasing), label = "jelly")
    val wave = rememberInfiniteTransition(label = "jellyWave")
    val phase by wave.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    PulseCard(padding = 18.dp) {
        Box(Modifier.size(214.dp).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2 - 6.dp.toPx()
                val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r)) }
                clipPath(clip) {
                    drawCircle(t.c.track, r, center)
                    val top = center.y + r - 2 * r * level
                    fun jelly(ph: Float, amp: Float) = Path().apply {
                        moveTo(0f, size.height)
                        var x = 0f
                        while (x <= size.width) {
                            lineTo(x, top + amp * sin(x / size.width * 2f * PI.toFloat() * 1.2f + ph))
                            x += 4f
                        }
                        lineTo(size.width, size.height)
                        close()
                    }
                    if (level > 0f) {
                        val amp = if (level < 0.99f) 7.dp.toPx() else 0f
                        drawPath(jelly(phase + 2f, amp * 1.2f), t.c.calm.copy(alpha = 0.55f))
                        drawPath(jelly(phase, amp), t.c.accent.copy(alpha = 0.85f))
                    }
                    drawOval(
                        Color.White.copy(alpha = 0.55f),
                        topLeft = Offset(center.x - r * 0.62f, center.y - r * 0.78f),
                        size = Size(r * 0.55f, r * 0.30f),
                    )
                }
                drawCircle(Color.White, r, center, style = Stroke(5.dp.toPx()))
                drawCircle(t.c.border, r + 3.5.dp.toPx(), center, style = Stroke(2.dp.toPx()))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PText(leftValue(h), size = 46.sp, display = true, lineHeight = 48.sp)
                PText(leftLabel(h), size = 13.sp, weight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(12.dp))
        StatTrio(h) { label, value, m ->
            Column(m.clip(RoundedCornerShape(20.dp)).background(t.c.surfaceAlt).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PText(label, size = 12.sp, color = t.c.muted)
                PText(value, size = 17.sp, display = true)
            }
        }
    }
}

// ================================================================ Blueprint — drafted gauge

@Composable
fun BlueprintHero(h: HeroData) {
    val t = pulse
    val intro = rememberIntro()
    val measurer = rememberTextMeasurer()
    val tickStyle = TextStyle(fontFamily = t.body, fontSize = 10.sp, color = t.c.muted)
    PulseCard(padding = 16.dp) {
        Row(Modifier.fillMaxWidth()) {
            PText("FIG. 01 — ENERGY", size = 11.sp, color = t.c.muted, tracking = 0.1f, modifier = Modifier.weight(1f))
            PText("${h.pct}%", size = 11.sp, color = t.c.accentText, tracking = 0.1f)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(184.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2
                val cy = size.height - 10.dp.toPx()
                val r = min(size.width / 2 - 20.dp.toPx(), cy - 14.dp.toPx())
                val tl = Offset(cx - r, cy - r)
                val arc = Size(r * 2, r * 2)
                drawArc(t.c.border.copy(alpha = 0.6f), 180f, 180f, false, tl, arc, style = Stroke(1.dp.toPx()))
                for (i in 0..20) {
                    val a = (180f + i * 9f) * (PI.toFloat() / 180f)
                    val major = i % 5 == 0
                    val r1 = r + 3.dp.toPx()
                    val r2 = r + (if (major) 13.dp else 8.dp).toPx()
                    drawLine(t.c.border, Offset(cx + cos(a) * r1, cy + sin(a) * r1), Offset(cx + cos(a) * r2, cy + sin(a) * r2), 1.dp.toPx())
                    if (major) {
                        val label = "${i * 5}"
                        val m = measurer.measure(label, tickStyle)
                        val lr = r - 16.dp.toPx()
                        drawText(m, topLeft = Offset(cx + cos(a) * lr - m.size.width / 2f, cy + sin(a) * lr - m.size.height / 2f))
                    }
                }
                val sweep = 180f * h.usedFraction * intro
                val ir = r - 34.dp.toPx()
                if (sweep > 0.5f) drawArc(t.c.accentText, 180f, sweep, false, Offset(cx - ir, cy - ir), Size(ir * 2, ir * 2), style = Stroke(8.dp.toPx()))
                val na = (180f + sweep) * (PI.toFloat() / 180f)
                drawLine(t.c.text, Offset(cx, cy), Offset(cx + cos(na) * (r - 6.dp.toPx()), cy + sin(na) * (r - 6.dp.toPx())), 2.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(t.c.bg, 6.dp.toPx(), Offset(cx, cy))
                drawCircle(t.c.text, 6.dp.toPx(), Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
            }
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PText(leftValue(h), size = 46.sp, display = true, lineHeight = 46.sp)
                PText(if (h.over) "KCAL OVER" else "KCAL LEFT", size = 10.sp, color = t.c.muted, tracking = 0.12f)
            }
        }
        // Dimension line: |<—— GOAL 2,400 KCAL ——>|
        Box(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val y = size.height / 2
                val w = 1.dp.toPx()
                val a = 5.dp.toPx()
                drawLine(t.c.border, Offset(0f, y - a), Offset(0f, y + a), w)
                drawLine(t.c.border, Offset(size.width, y - a), Offset(size.width, y + a), w)
                drawLine(t.c.border, Offset(0f, y), Offset(size.width, y), w)
                drawLine(t.c.border, Offset(0f, y), Offset(a, y - a * 0.6f), w); drawLine(t.c.border, Offset(0f, y), Offset(a, y + a * 0.6f), w)
                drawLine(t.c.border, Offset(size.width, y), Offset(size.width - a, y - a * 0.6f), w); drawLine(t.c.border, Offset(size.width, y), Offset(size.width - a, y + a * 0.6f), w)
            }
            Box(Modifier.background(t.c.surface).padding(horizontal = 8.dp)) {
                PText("GOAL ${formatInt(h.goal)} KCAL", size = 10.sp, color = t.c.text, tracking = 0.1f)
            }
        }
        Spacer(Modifier.height(10.dp))
        StatTrio(h) { label, value, m ->
            Column(m.drawWithContent { drawContent(); drawBlueprintFrame(t.c.border) }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PText(label.uppercase(), size = 10.sp, color = t.c.muted, tracking = 0.1f)
                PText(value, size = 20.sp, display = true, align = TextAlign.Center)
            }
        }
    }
}

// ================================================================ macro styles

@Composable
fun MacroArcs(items: List<MacroItem>) {
    val t = pulse
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { m ->
            PulseCard(Modifier.weight(1f), padding = 12.dp) {
                Box(Modifier.fillMaxWidth().height(58.dp), contentAlignment = Alignment.BottomCenter) {
                    val anim by animateFloatAsState(m.fraction.coerceIn(0f, 1f), tween(900), label = "arc")
                    Canvas(Modifier.fillMaxSize()) {
                        val sw = 9.dp.toPx()
                        val r = min(size.width / 2, size.height) - sw / 2
                        val tl = Offset(size.width / 2 - r, size.height - r)
                        drawArc(t.c.track, 180f, 180f, false, tl, Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round))
                        if (anim > 0.005f) drawArc(m.color, 180f, 180f * anim, false, tl, Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round))
                    }
                    PText("${m.value.roundToInt()}g", size = 18.sp, display = true)
                }
                Spacer(Modifier.height(6.dp))
                PText(m.name, size = 13.sp, weight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally))
                PText("of ${m.goal}g", size = 11.sp, color = LocalMuted.current, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
fun MacroBlocks(items: List<MacroItem>) {
    PulseCard(padding = 14.dp) {
        PText("▶ MACROS", size = 14.sp, weight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        items.forEach { m ->
            Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PText(m.name.take(3).uppercase(), size = 14.sp, weight = FontWeight.Bold, modifier = Modifier.width(38.dp))
                PixelBlocks(m.fraction, 10, 14.dp, m.color, Modifier.weight(1f))
                PText("${m.value.roundToInt()}/${m.goal}", size = 13.sp, weight = FontWeight.Bold, align = TextAlign.End, modifier = Modifier.width(66.dp))
            }
        }
    }
}
