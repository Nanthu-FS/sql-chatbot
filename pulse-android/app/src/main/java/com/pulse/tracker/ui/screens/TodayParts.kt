package com.pulse.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pulse.tracker.ui.components.IconBadge
import com.pulse.tracker.ui.components.PBar
import com.pulse.tracker.ui.components.PIcons
import com.pulse.tracker.ui.components.PLabel
import com.pulse.tracker.ui.components.PText
import com.pulse.tracker.ui.components.PulseCard
import com.pulse.tracker.ui.components.Ring
import com.pulse.tracker.ui.components.RingStyle
import com.pulse.tracker.ui.components.SectionTitle
import com.pulse.tracker.ui.components.Seg
import com.pulse.tracker.ui.components.formatInt
import com.pulse.tracker.ui.components.sticker
import com.pulse.tracker.ui.theme.CardTint
import com.pulse.tracker.ui.theme.HeroStyle
import com.pulse.tracker.ui.theme.LocalMuted
import com.pulse.tracker.ui.theme.MacroStyle
import com.pulse.tracker.ui.theme.pulse
import kotlin.math.roundToInt

data class HeroData(
    val left: Int,
    val eaten: Int,
    val burned: Int,
    val goal: Int,
    val segments: List<Seg>,
    val usedFraction: Float,
    val proteinFraction: Float,
    val carbsFraction: Float = 0f,
    val fatFraction: Float = 0f,
) {
    val over get() = left < 0
    val pct get() = (usedFraction * 100).roundToInt()
}

data class MacroItem(
    val name: String,
    val short: String,
    val value: Double,
    val goal: Int,
    val color: Color,
    val tint: CardTint,
) {
    val fraction get() = if (goal <= 0) 0f else (value / goal).toFloat()
}

// ================================================================ hero

@Composable
fun CalorieHero(h: HeroData) {
    when (pulse.heroStyle) {
        HeroStyle.SEGMENT_RING -> VoltHero(h)
        HeroStyle.EDITORIAL -> PaperHero(h)
        HeroStyle.CHUNKY -> BrutalHero(h)
        HeroStyle.LED_RING -> NeonHero(h)
        HeroStyle.SOFT_RING -> MatchaHero(h)
        HeroStyle.SWISS -> SwissHero(h)
        HeroStyle.GRADIENT_RING -> AuroraHero(h)
        HeroStyle.DIAL -> NoirHero(h)
        HeroStyle.RAINBOW -> RetroHero(h)
        HeroStyle.PIXEL_BAR -> PixelHero(h)
        HeroStyle.JELLY -> CandyHero(h)
        HeroStyle.GAUGE -> BlueprintHero(h)
    }
}

internal fun leftLabel(h: HeroData) = if (h.over) "kcal over" else "kcal left"
internal fun leftValue(h: HeroData) = formatInt(kotlin.math.abs(h.left))

@Composable
private fun StatusChip(h: HeroData) {
    val t = pulse
    Row(
        Modifier.height(24.dp).clip(RoundedCornerShape(12.dp)).background(t.c.surfaceAlt).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(if (h.over) t.c.warm else t.c.accent))
        PText(if (h.over) "Over goal" else if (h.eaten == 0) "Fresh start" else "On track", size = 12.sp, color = t.c.text)
    }
}

@Composable
private fun StatCell(icon: ImageVector?, label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (icon != null) Icon(icon, null, Modifier.size(13.dp), tint = LocalMuted.current)
            PLabel(label)
        }
        PText(value, size = 18.sp, display = true, weight = FontWeight.SemiBold)
    }
}

@Composable
private fun VoltHero(h: HeroData) {
    val t = pulse
    PulseCard(padding = 18.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            PText("Calories", size = 13.sp, weight = FontWeight.SemiBold)
            StatusChip(h)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.size(210.dp).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
            Ring(h.segments, Modifier.fillMaxSize(), stroke = 16.dp, style = RingStyle.BUTT)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PText(leftValue(h), size = 50.sp, display = true, tracking = -0.04f, lineHeight = 52.sp)
                PText(leftLabel(h), size = 13.sp, color = t.c.muted)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            StatCell(PIcons.Utensils, "Eaten", formatInt(h.eaten), Modifier.weight(1f))
            Box(Modifier.width(1.dp).fillMaxHeight().background(t.c.border))
            StatCell(PIcons.Flame, "Burned", formatInt(h.burned), Modifier.weight(1f))
            Box(Modifier.width(1.dp).fillMaxHeight().background(t.c.border))
            StatCell(PIcons.Target, "Goal", formatInt(h.goal), Modifier.weight(1f))
        }
    }
}

@Composable
private fun PaperHero(h: HeroData) {
    val t = pulse
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            PText(leftValue(h), size = 104.sp, display = true, lineHeight = 96.sp, modifier = Modifier.weight(1f), maxLines = 1)
            PText(if (h.over) "calories\nover" else "calories\nleft", size = 19.sp, display = true, italic = true, align = TextAlign.End, lineHeight = 21.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        Spacer(Modifier.height(6.dp))
        FlatSegments(h, height = 6.dp, divider = false)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PText("0", size = 11.sp, color = t.c.muted)
            PText(formatInt(h.goal / 2), size = 11.sp, color = t.c.muted)
            PText("${formatInt(h.goal)} kcal", size = 11.sp, color = t.c.muted)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).drawBehind {
                drawLine(t.c.text, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                drawLine(t.c.text, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            },
        ) {
            listOf("Eaten" to h.eaten, "Burned" to h.burned, "Goal" to h.goal).forEachIndexed { i, (label, v) ->
                if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 8.dp).background(t.c.border))
                Column(Modifier.weight(1f).padding(start = if (i > 0) 14.dp else 0.dp, top = 12.dp, bottom = 12.dp)) {
                    PLabel(label, color = t.c.text)
                    PText(formatInt(v), size = 32.sp, display = true, lineHeight = 34.sp)
                }
            }
        }
    }
}

@Composable
private fun FlatSegments(h: HeroData, height: androidx.compose.ui.unit.Dp, divider: Boolean, track: Color = pulse.c.track) {
    val t = pulse
    val total = h.segments.sumOf { it.fraction.toDouble() }.toFloat()
    val scale = if (total > 1f) 1f / total else 1f
    val shape = RoundedCornerShape(if (divider) 8.dp else 0.dp)
    Row(
        Modifier.fillMaxWidth().height(height).clip(shape).background(track)
            .then(if (divider) Modifier.border(2.5.dp, t.c.border, shape) else Modifier),
    ) {
        h.segments.forEach { s ->
            val w = (s.fraction * scale).coerceIn(0f, 1f)
            if (w > 0.001f) {
                Box(
                    Modifier.fillMaxHeight().weight(w).background(s.color)
                        .then(if (divider) Modifier.drawBehind { drawLine(t.c.border, Offset(size.width, 0f), Offset(size.width, size.height), 2.5.dp.toPx()) } else Modifier),
                )
            }
        }
        val rest = 1f - (total * scale).coerceIn(0f, 1f)
        if (rest > 0.001f) Spacer(Modifier.weight(rest))
    }
}

@Composable
private fun BrutalHero(h: HeroData) {
    val t = pulse
    PulseCard(tint = CardTint.HERO, padding = 18.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            PText(if (h.over) "KCAL OVER" else "KCAL LEFT", size = 13.sp, weight = FontWeight.ExtraBold, tracking = 0.1f)
            Box(
                Modifier.height(26.dp).clip(RoundedCornerShape(13.dp)).background(Color.White)
                    .border(2.dp, t.c.border, RoundedCornerShape(13.dp)).padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) { PText(if (h.over) "OOPS" else "ON TRACK", size = 11.sp, weight = FontWeight.ExtraBold) }
        }
        PText(leftValue(h), size = 92.sp, display = true, tracking = -0.06f, lineHeight = 88.sp)
        Spacer(Modifier.height(10.dp))
        FlatSegments(h, height = 28.dp, divider = true, track = Color.White)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Ate" to h.eaten, "Burn" to h.burned, "Goal" to h.goal).forEach { (l, v) ->
                Row(
                    Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).background(Color.White)
                        .border(2.dp, t.c.border, RoundedCornerShape(10.dp)),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    PText("$l ", size = 13.sp, weight = FontWeight.SemiBold, color = t.c.text)
                    PText(formatInt(v), size = 13.sp, weight = FontWeight.ExtraBold, color = t.c.text)
                }
            }
        }
    }
}

@Composable
private fun NeonHero(h: HeroData) {
    val t = pulse
    PulseCard(padding = 16.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PText("CAL.SYS", size = 11.sp, color = t.c.muted, tracking = 0.1f)
            PText("${h.pct}%", size = 11.sp, color = t.c.carbs, tracking = 0.1f)
        }
        Box(Modifier.size(230.dp).align(Alignment.CenterHorizontally), contentAlignment = Alignment.Center) {
            Ring(
                listOf(Seg(h.usedFraction, t.c.accent)), Modifier.fillMaxSize(), stroke = 12.dp,
                style = RingStyle.LED, glow = true, cutColor = t.c.surface,
                inner = Seg(h.proteinFraction, t.c.carbs),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    leftValue(h).replace(",", ""),
                    style = TextStyle(
                        fontFamily = t.display, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp, color = t.c.text,
                        letterSpacing = (-0.02).em, shadow = Shadow(t.c.accent.copy(alpha = 0.8f), Offset.Zero, 28f),
                    ),
                )
                PText(if (h.over) "KCAL_OVER" else "KCAL_LEFT", size = 11.sp, color = t.c.muted, tracking = 0.14f)
            }
        }
        Spacer(Modifier.height(6.dp))
        Readout("EATEN", formatInt(h.eaten).replace(",", ""), t.c.text)
        Readout("BURNED", "+" + h.burned, t.c.fat)
        Readout("GOAL", h.goal.toString(), t.c.text)
    }
}

@Composable
fun Readout(label: String, value: String, valueColor: Color) {
    val t = pulse
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Bottom) {
        PText(label, size = 13.sp, color = t.c.muted)
        Box(
            Modifier.weight(1f).height(10.dp).padding(horizontal = 8.dp).drawBehind {
                drawLine(
                    t.c.border, Offset(0f, size.height), Offset(size.width, size.height), 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
                )
            },
        )
        PText(value, size = 13.sp, weight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun MatchaHero(h: HeroData) {
    val t = pulse
    PulseCard(padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                Ring(h.segments, Modifier.fillMaxSize(), stroke = 14.dp, style = RingStyle.ROUND, track = t.c.bg)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PText(leftValue(h), size = 30.sp, display = true, lineHeight = 32.sp)
                    PText(leftLabel(h), size = 13.sp, weight = FontWeight.Bold, color = t.c.muted)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MatchaStat(PIcons.Utensils, t.c.carbs, "Eaten", formatInt(h.eaten))
                MatchaStat(PIcons.Flame, t.c.fat, "Burned", formatInt(h.burned))
                MatchaStat(PIcons.Target, t.c.protein, "Goal", formatInt(h.goal))
            }
        }
    }
}

@Composable
private fun MatchaStat(icon: ImageVector, color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconBadge(icon, color, 34.dp)
        Column {
            PText(label, size = 12.sp, weight = FontWeight.Bold, color = LocalMuted.current)
            PText(value, size = 17.sp, weight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun SwissHero(h: HeroData) {
    val t = pulse
    Column {
        SectionTitle("01 — Energy")
        Spacer(Modifier.height(10.dp))
        PText(leftValue(h), size = 128.sp, display = true, tracking = -0.07f, lineHeight = 110.sp, modifier = Modifier.offset(x = (-6).dp), maxLines = 1)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            PText(if (h.over) "Kilocalories over" else "Kilocalories left", size = 14.sp, weight = FontWeight.SemiBold)
            PText("${h.pct}% used", size = 14.sp, weight = FontWeight.SemiBold, color = t.c.muted)
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.CenterStart) {
            PBar(h.usedFraction, t.c.text, Modifier.fillMaxWidth(), height = 12.dp)
            Box(Modifier.fillMaxWidth(h.usedFraction.coerceIn(0.005f, 1f)).height(20.dp), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(t.c.accent))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            PText("0", size = 11.sp, weight = FontWeight.SemiBold, color = t.c.muted)
            PText((h.goal / 2).toString(), size = 11.sp, weight = FontWeight.SemiBold, color = t.c.muted)
            PText(h.goal.toString(), size = 11.sp, weight = FontWeight.SemiBold, color = t.c.muted)
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).drawBehind {
                drawLine(t.c.text, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
            },
        ) {
            listOf("Eaten" to h.eaten, "Burned" to h.burned, "Goal" to h.goal).forEachIndexed { i, (l, v) ->
                if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(t.c.text))
                Column(Modifier.weight(1f).padding(start = if (i > 0) 12.dp else 0.dp, top = 10.dp, bottom = 10.dp)) {
                    PLabel(l)
                    PText(v.toString(), size = 30.sp, display = true, weight = FontWeight.ExtraBold, tracking = -0.04f)
                }
            }
        }
    }
}

// ================================================================ macros

@Composable
fun MacroSection(items: List<MacroItem>) {
    when (pulse.macroStyle) {
        MacroStyle.ARCS -> MacroArcs(items)
        MacroStyle.BLOCKS -> MacroBlocks(items)

        MacroStyle.CARDS -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEach { m ->
                PulseCard(Modifier.weight(1f), padding = 13.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(m.color))
                        PText(m.name, size = 12.sp, color = LocalMuted.current)
                    }
                    Spacer(Modifier.height(8.dp))
                    GramsLine(m, 22.sp)
                    Spacer(Modifier.height(8.dp))
                    PBar(m.fraction, m.color, Modifier.fillMaxWidth())
                }
            }
        }

        MacroStyle.TABLE -> Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                PText("Macronutrients", size = 24.sp, display = true, italic = true, modifier = Modifier.weight(1f))
                PText("grams / goal", size = 12.sp, color = pulse.c.muted)
            }
            items.forEach { m ->
                val t = pulse
                Row(
                    Modifier.fillMaxWidth().height(44.dp).drawBehind {
                        drawLine(
                            t.c.muted, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 3.dp.toPx())),
                        )
                    },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    PText(m.name, size = 14.sp, weight = FontWeight.Medium, modifier = Modifier.width(62.dp))
                    PBar(m.fraction, m.color, Modifier.weight(1f), height = 3.dp)
                    GramsLine(m, 21.sp, Modifier.width(84.dp), TextAlign.End, unit = false)
                }
            }
        }

        MacroStyle.STICKERS -> Row(Modifier.fillMaxWidth().padding(end = 5.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val tilts = listOf(-1.5f, 1f, -1f)
            items.forEachIndexed { i, m ->
                PulseCard(Modifier.weight(1f).sticker(tilts[i % 3]), tint = m.tint, padding = 12.dp) {
                    PText(m.name.uppercase(), size = 12.sp, weight = FontWeight.ExtraBold, tracking = 0.06f)
                    PText("${m.value.roundToInt()}g", size = 30.sp, display = true, lineHeight = 32.sp)
                    PText("of ${m.goal}g", size = 12.sp, weight = FontWeight.SemiBold)
                }
            }
        }

        MacroStyle.ASCII -> PulseCard(padding = 14.dp) {
            PText("// MACROS", size = 11.sp, color = pulse.c.muted, tracking = 0.1f)
            Spacer(Modifier.height(6.dp))
            items.forEach { m -> AsciiBar(m) }
        }

        MacroStyle.JARS -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEach { m ->
                PulseCard(Modifier.weight(1f), padding = 12.dp) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.width(40.dp).height(80.dp).clip(RoundedCornerShape(20.dp)).background(pulse.c.bg), contentAlignment = Alignment.BottomCenter) {
                            val f = m.fraction.coerceIn(0f, 1f)
                            if (f > 0f) Box(Modifier.fillMaxWidth().fillMaxHeight(f).clip(RoundedCornerShape(20.dp)).background(m.color))
                        }
                        Spacer(Modifier.height(8.dp))
                        PText("${m.value.roundToInt()}g", size = 20.sp, display = true)
                        PText("${m.name} · ${m.goal}", size = 12.sp, weight = FontWeight.Bold, color = LocalMuted.current)
                    }
                }
            }
        }

        MacroStyle.LETTERS -> Column {
            PulseCard { SectionTitle("02 — Macros") }
            items.forEach { m ->
                val t = pulse
                Row(
                    Modifier.fillMaxWidth().height(44.dp).drawBehind {
                        drawLine(t.c.track, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                    },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PText(m.short, size = 32.sp, display = true, color = if (m.color == t.c.accent) t.c.accent else t.c.text, modifier = Modifier.width(40.dp))
                    PBar(m.fraction, m.color, Modifier.weight(1f), height = 8.dp)
                    GramsLine(m, 17.sp, Modifier.width(88.dp), TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun GramsLine(m: MacroItem, size: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier, align: TextAlign? = null, unit: Boolean = true) {
    val t = pulse
    val muted = LocalMuted.current
    Text(
        buildAnnotatedString {
            append(m.value.roundToInt().toString())
            withStyle(SpanStyle(color = muted, fontSize = (size.value * 0.58f).sp, fontFamily = t.body, fontWeight = FontWeight.Normal)) {
                append(if (unit) "/${m.goal}g" else " / ${m.goal}")
            }
        },
        modifier = modifier,
        textAlign = align,
        style = TextStyle(fontFamily = t.display, fontWeight = if (t.displayWeight == FontWeight.Normal) FontWeight.Normal else FontWeight.SemiBold, fontSize = size, letterSpacing = (-0.02).em),
        maxLines = 1,
    )
}

@Composable
private fun AsciiBar(m: MacroItem) {
    val t = pulse
    val cells = 12
    val filled = (m.fraction.coerceIn(0f, 1f) * cells).roundToInt()
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            buildAnnotatedString {
                append(m.short.padEnd(3) + " [")
                withStyle(SpanStyle(color = m.color, shadow = Shadow(m.color.copy(alpha = 0.7f), Offset.Zero, 12f))) { append("█".repeat(filled)) }
                withStyle(SpanStyle(color = t.c.border)) { append("░".repeat(cells - filled)) }
                append("]")
            },
            style = TextStyle(fontFamily = t.body, fontSize = 14.sp, color = t.c.text),
        )
        Text(
            buildAnnotatedString {
                append(m.value.roundToInt().toString().padStart(3, '0'))
                withStyle(SpanStyle(color = t.c.muted)) { append("/" + m.goal.toString().padStart(3, '0')) }
            },
            style = TextStyle(fontFamily = t.body, fontSize = 14.sp, color = t.c.text),
        )
    }
}

// ================================================================ week strip

@Composable
fun DayPill(
    weekday: String,
    day: Int,
    selected: Boolean,
    logged: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = pulse
    val c = t.c
    val shape = RoundedCornerShape(
        when (t.id) {
            com.pulse.tracker.ui.theme.ThemeId.MATCHA, com.pulse.tracker.ui.theme.ThemeId.CANDY, com.pulse.tracker.ui.theme.ThemeId.RETRO -> 22.dp
            com.pulse.tracker.ui.theme.ThemeId.BRUTAL -> 12.dp
            com.pulse.tracker.ui.theme.ThemeId.VOLT, com.pulse.tracker.ui.theme.ThemeId.AURORA -> 16.dp
            else -> 0.dp
        },
    )
    val bg: Color
    val fg: Color
    val bd: Color
    when (t.id) {
        com.pulse.tracker.ui.theme.ThemeId.NEON -> { bg = if (selected) c.accent else Color.Transparent; fg = if (selected) c.onAccent else if (enabled) c.text else c.muted; bd = if (selected) c.accent else c.border }
        com.pulse.tracker.ui.theme.ThemeId.PAPER, com.pulse.tracker.ui.theme.ThemeId.SWISS -> { bg = if (selected) c.text else Color.Transparent; fg = if (selected) c.bg else if (enabled) c.text else c.muted; bd = if (selected) c.text else c.border }
        com.pulse.tracker.ui.theme.ThemeId.BRUTAL -> { bg = if (selected) c.accent else c.surface; fg = c.text; bd = c.border }
        com.pulse.tracker.ui.theme.ThemeId.MATCHA -> { bg = if (selected) c.accent else c.surface; fg = if (selected) c.onAccent else if (enabled) c.text else c.muted; bd = Color.Transparent }
        else -> { bg = if (selected) c.accent else if (enabled) c.surface else Color.Transparent; fg = if (selected) c.onAccent else if (enabled) c.text else c.muted; bd = if (selected) c.accent else c.border }
    }
    Column(
        modifier.height(58.dp).clip(shape).background(bg)
            .border(if (t.id == com.pulse.tracker.ui.theme.ThemeId.BRUTAL || t.id == com.pulse.tracker.ui.theme.ThemeId.PIXEL) 2.dp else 1.dp, bd, shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PText(if (t.upperLabels) weekday.uppercase() else weekday, size = 11.sp, color = fg.copy(alpha = if (selected) 1f else 0.75f), weight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        PText(day.toString(), size = 16.sp, display = true, weight = FontWeight.SemiBold, color = fg)
        Spacer(Modifier.height(3.dp))
        Box(Modifier.size(4.dp).clip(RoundedCornerShape(2.dp)).background(if (logged) (if (selected) fg else c.accentText) else Color.Transparent))
    }
}
