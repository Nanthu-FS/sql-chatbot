package com.spendlens.app.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendlens.app.domain.BarEntry
import com.spendlens.app.domain.CategorySlice
import com.spendlens.app.domain.CategoryTrend
import com.spendlens.app.domain.Category
import com.spendlens.app.domain.CurrencyOption
import com.spendlens.app.domain.percentLabel
import com.spendlens.app.ui.Format
import com.spendlens.app.ui.components.Emphasized
import com.spendlens.app.ui.components.Hairline
import com.spendlens.app.ui.components.Label
import com.spendlens.app.ui.components.MiniSpark
import com.spendlens.app.ui.components.bouncy
import com.spendlens.app.ui.components.caps
import com.spendlens.app.ui.components.hardShadow
import com.spendlens.app.ui.components.numberStyle
import com.spendlens.app.ui.components.pressable
import com.spendlens.app.ui.components.rememberHaptics
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import com.spendlens.app.domain.Period
import com.spendlens.app.domain.PeriodType
import com.spendlens.app.ui.components.bevel
import com.spendlens.app.ui.theme.AuroraGlow
import com.spendlens.app.ui.theme.RisoBlue
import java.time.temporal.ChronoUnit
import kotlin.math.sqrt
import com.spendlens.app.ui.theme.Spend
import com.spendlens.app.ui.theme.contentOn
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

// ---------------------------------------------------------------- hero pieces

/** Dot matrix: one dot per day (or hour / month), sized by spend; the current one in the accent. */
@Composable
fun DotDays(bars: List<BarEntry>, modifier: Modifier = Modifier) {
    if (bars.isEmpty()) return
    val colors = Spend.ink
    val max = (bars.maxOf { it.value }).coerceAtLeast(1L)
    val currentIndex = bars.indexOfFirst { it.isCurrent }
    val perRow = if (bars.size > 16) 15 else bars.size
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        bars.chunked(perRow).forEachIndexed { row, chunk ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                chunk.forEachIndexed { col, bar ->
                    val i = row * perRow + col
                    val future = currentIndex >= 0 && i > currentIndex
                    val size = if (bar.value <= 0) 5.dp else (6 + 12 * (bar.value.toFloat() / max).coerceAtMost(1f)).dp
                    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(size)
                                .then(
                                    when {
                                        future -> Modifier.border(1.dp, colors.line, CircleShape)
                                        i == currentIndex -> Modifier.background(colors.accent, CircleShape)
                                        bar.value <= 0 -> Modifier.background(colors.line, CircleShape)
                                        else -> Modifier.background(colors.text, CircleShape)
                                    },
                                ),
                        )
                    }
                }
                repeat(perRow - chunk.size) { Spacer(Modifier.size(18.dp)) }
            }
        }
    }
}

/** Receipt: the period as a barcode — one thin bar per day. */
@Composable
fun Barcode(bars: List<BarEntry>, modifier: Modifier = Modifier) {
    if (bars.isEmpty()) return
    val colors = Spend.ink
    val max = (bars.maxOf { it.value }).coerceAtLeast(1L)
    val grow = remember { Animatable(0f) }
    LaunchedEffect(bars) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(900, easing = Emphasized))
    }
    Canvas(modifier.fillMaxWidth().height(52.dp)) {
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (bars.size - 1)) / bars.size
        bars.forEachIndexed { i, b ->
            val h = size.height * (0.06f + 0.94f * (b.value.toFloat() / max)) * grow.value
            val c = when {
                b.isCurrent -> colors.accent
                b.value <= 0 -> colors.line
                else -> colors.muted
            }
            drawRect(c, Offset(i * (w + gap), size.height - h), Size(w, h))
        }
    }
}

/** Swiss: stats in ruled cells. */
@Composable
fun StatCells(cells: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val colors = Spend.ink
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.lineStrong))
        Row(Modifier.fillMaxWidth().height(64.dp)) {
            cells.forEachIndexed { i, (label, value) ->
                if (i > 0) Box(Modifier.width(1.dp).height(64.dp).background(colors.line))
                Column(Modifier.weight(1f).padding(horizontal = if (i == 0) 0.dp else 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Label(label, color = colors.muted)
                    Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = colors.text, maxLines = 1)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.lineStrong))
    }
}

/** Bento: ring showing how much of the budget (or of the forecast) is gone. */
@Composable
fun BudgetRing(fraction: Float, centre: String, caption: String, modifier: Modifier = Modifier) {
    val colors = Spend.ink
    val sweep by animateFloatAsState(fraction, tween(1100, easing = Emphasized), label = "ring")
    Box(modifier.size(104.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(104.dp)) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(colors.line, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(colors.accent, -90f, 360f * sweep.coerceIn(0f, 1f), false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            if (sweep > 1f) {
                val s2 = 6.dp.toPx()
                val in2 = stroke + 6.dp.toPx() + s2 / 2
                val arc2 = Size(size.width - in2 * 2, size.height - in2 * 2)
                drawArc(colors.alert, -90f, 360f * (sweep - 1f).coerceIn(0f, 1f), false, Offset(in2, in2), arc2, style = Stroke(s2, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centre, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = colors.text)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = colors.muted)
        }
    }
}

/** Bento: a small rounded tile with a label, a big value and a footnote. */
@Composable
fun BentoTile(label: String, value: String, foot: String, modifier: Modifier = Modifier, accent: Boolean = false, footColor: Color? = null) {
    val colors = Spend.ink
    val bg = if (accent) colors.accent else colors.surface
    val fg = if (accent) colors.onAccent else colors.text
    val sub = if (accent) colors.onAccent.copy(alpha = 0.75f) else colors.muted
    Column(
        modifier
            .height(118.dp)
            .background(bg, RoundedCornerShape(Spend.look.radius))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = sub, maxLines = 1)
        Text(value, style = numberStyle(MaterialTheme.typography.headlineMedium), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Text(foot, style = MaterialTheme.typography.labelLarge, color = footColor ?: sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

fun dayPartLabel(day: java.time.DayOfWeek, part: com.spendlens.app.domain.DayPart): String =
    day.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + part.label

// ---------------------------------------------------------------- categories, per look

/** Receipt: "SHOPPING ×4 ......... 45,278". */
@Composable
fun ReceiptLines(slices: List<CategorySlice>, currency: CurrencyOption) {
    val colors = Spend.ink
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        slices.take(8).forEach { s ->
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${s.category.label.uppercase()} ×${s.count}", style = MaterialTheme.typography.bodyMedium, color = colors.text, maxLines = 1)
                Canvas(Modifier.weight(1f).height(10.dp).padding(horizontal = 6.dp)) {
                    var x = 0f
                    val step = 5.dp.toPx()
                    while (x < size.width) {
                        drawCircle(colors.faint, 0.8.dp.toPx(), Offset(x, size.height - 1.dp.toPx()))
                        x += step
                    }
                }
                Text(currency.format(s.amountMinor).removePrefix(currency.symbol), style = MaterialTheme.typography.bodyMedium, color = colors.text)
            }
        }
        val rest = slices.drop(8)
        if (rest.isNotEmpty()) {
            Label("+ ${rest.size} more · ${currency.format(rest.sumOf { it.amountMinor })}", color = colors.muted)
        }
    }
}

/** Dot matrix: ten dots per row, filled by share. */
@Composable
fun DotShares(slices: List<CategorySlice>, currency: CurrencyOption) {
    val colors = Spend.ink
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        slices.take(6).forEachIndexed { i, s ->
            val filled = (s.fraction * 10).roundToInt().coerceIn(if (s.fraction > 0f) 1 else 0, 10)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.category.label.uppercase(), style = MaterialTheme.typography.titleSmall, color = if (i == 0) colors.text else colors.muted, maxLines = 1)
                    Label(currency.format(s.amountMinor), color = colors.faint)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(10) { d ->
                        Box(
                            Modifier
                                .size(8.dp)
                                .then(
                                    if (d < filled) Modifier.background(if (i == 0) colors.accent else colors.text, CircleShape)
                                    else Modifier.border(1.dp, colors.lineStrong, CircleShape),
                                ),
                        )
                    }
                }
            }
        }
    }
}

/** Neo-brutal: thick boxed bars. */
@Composable
fun BoxedBars(slices: List<CategorySlice>) {
    val colors = Spend.ink
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        slices.take(6).forEachIndexed { i, s ->
            val grow by animateFloatAsState(s.fraction, bouncy(), label = "boxbar")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.category.short().uppercase(), style = MaterialTheme.typography.labelLarge, color = colors.text, modifier = Modifier.width(118.dp).padding(end = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f).height(18.dp).border(2.dp, colors.text).padding(2.dp)) {
                    Box(Modifier.fillMaxWidth(grow.coerceIn(0f, 1f)).height(14.dp).background(if (i == 0) colors.accent else colors.text))
                }
                Text("${(s.fraction * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge, color = colors.text, modifier = Modifier.width(44.dp).padding(start = 8.dp), maxLines = 1)
            }
        }
    }
}

private fun Category.short(): String = when (this) {
    Category.FOOD -> "Food"
    Category.BILLS -> "Bills"
    Category.HOUSING -> "Rent"
    else -> label
}

private val WalletColours = listOf(
    Color(0xFFF2C94C), Color(0xFF7AA2FF), Color(0xFF9BE3B7), Color(0xFFC9A7FF), Color(0xFFFFB08A), Color(0xFF8FD8E8),
)

/**
 * Wallet: each category is a card in a stack; tap one to bring it to the front and open it.
 * The open card shows how it compares with usual and a small trend line.
 */
@Composable
fun WalletStack(slices: List<CategorySlice>, trends: Map<Category, CategoryTrend>, currency: CurrencyOption) {
    if (slices.isEmpty()) return
    val colors = Spend.ink
    val haptics = rememberHaptics()
    val shown = slices.take(6)
    var open by remember(shown.map { it.category }) { mutableStateOf(0) }
    val order = shown.indices.filter { it != open } + open
    OverlapColumn(overlap = 12.dp) {
        order.forEach { i ->
            val s = shown[i]
            val isOpen = i == open
            val fill = if (isOpen) colors.accent else WalletColours[i % WalletColours.size]
            val ink = contentOn(fill)
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(fill, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = if (isOpen) 22.dp else 0.dp, bottomEnd = if (isOpen) 22.dp else 0.dp))
                    .pressable(pressedScale = 0.98f, haptic = false) {
                        if (!isOpen) {
                            haptics.tick()
                            open = i
                        }
                    }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.category.label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = ink, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(currency.format(s.amountMinor), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = ink)
                }
                if (isOpen) {
                    val trend = trends[s.category]
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text("vs usual", style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.75f))
                            Text(trend?.change?.let { percentLabel(it) } ?: "—", style = numberStyle(MaterialTheme.typography.headlineMedium), color = ink)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            if (trend != null && trend.monthly.count { it > 0 } >= 2) {
                                MiniSpark(trend.monthly, Modifier.width(120.dp).height(36.dp), line = ink.copy(alpha = 0.8f), marker = ink)
                            }
                            Text("${(s.fraction * 100).roundToInt()}% · ${s.count} payments", style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.75f))
                        }
                    }
                }
            }
        }
    }
}

/** A column whose children overlap by [overlap], later ones on top — the stacked-cards look. */
@Composable
private fun OverlapColumn(overlap: Dp, content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val o = overlap.roundToPx()
        val height = (placeables.sumOf { it.height } - o * (placeables.size - 1).coerceAtLeast(0)).coerceAtLeast(0)
        layout(constraints.maxWidth, height) {
            var y = 0
            placeables.forEach {
                it.placeRelative(0, y)
                y += it.height - o
            }
        }
    }
}

/** Swiss: two-column legend under the stacked bar. */
@Composable
fun SplitLegend(slices: List<CategorySlice>) {
    val colors = Spend.ink
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        slices.take(6).chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEachIndexed { j, s ->
                    val i = slices.indexOf(s)
                    Row(Modifier.weight(1f)) {
                        Text(
                            (i + 1).toString().padStart(2, '0') + " ",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = if (i == 0) colors.accent else colors.text,
                        )
                        Text(s.category.short(), style = MaterialTheme.typography.titleSmall, color = colors.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${(s.fraction * 100).roundToInt()}%", style = MaterialTheme.typography.titleSmall, color = colors.text)
                    }
                    if (pair.size == 1 && j == 0) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- terminal, aurora, riso, retro, blueprint

fun Period.span(): String = when (type) {
    PeriodType.DAY -> "24 HOURS"
    PeriodType.YEAR -> "12 MONTHS"
    else -> "${ChronoUnit.DAYS.between(start, endExclusive)} DAYS"
}

/** Blueprint: "◀ ———— 30 DAYS ———— ▶" under the total. */
@Composable
fun DimensionLine(text: String, modifier: Modifier = Modifier) {
    val colors = Spend.ink
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Label("◀", color = colors.text)
        Box(Modifier.weight(1f).padding(horizontal = 6.dp).height(1.dp).background(colors.text))
        Label(text, color = colors.text)
        Box(Modifier.weight(1f).padding(horizontal = 6.dp).height(1.dp).background(colors.text))
        Label("▶", color = colors.text)
    }
}

/** Terminal: glowing phosphor bars, the busiest day in the accent. */
@Composable
fun TerminalBars(bars: List<BarEntry>, modifier: Modifier = Modifier) {
    if (bars.isEmpty()) return
    val colors = Spend.ink
    val max = bars.maxOf { it.value }.coerceAtLeast(1L)
    val top = bars.indexOfFirst { it.value == max }
    Canvas(modifier.fillMaxWidth().height(48.dp)) {
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (bars.size - 1)) / bars.size
        bars.forEachIndexed { i, bar ->
            val h = (size.height * bar.value / max).coerceAtLeast(2.dp.toPx())
            val color = if (i == top) colors.accent else colors.text
            val x = i * (w + gap)
            drawRect(color.copy(alpha = 0.25f), Offset(x - 1.dp.toPx(), size.height - h - 1.dp.toPx()), Size(w + 2.dp.toPx(), h + 1.dp.toPx()))
            drawRect(color, Offset(x, size.height - h), Size(w, h))
        }
    }
}

/** Terminal: "SHOPPING  ████████░░░░░░░░  48%". */
@Composable
fun AsciiBars(slices: List<CategorySlice>) {
    val colors = Spend.ink
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        slices.take(6).forEachIndexed { i, s ->
            val filled = (s.fraction * 16).roundToInt().coerceIn(if (s.fraction > 0f) 1 else 0, 16)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.category.short().uppercase(), style = MaterialTheme.typography.bodyLarge, color = if (i == 0) colors.accent else colors.text, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.width(96.dp))
                Text("█".repeat(filled) + "░".repeat(16 - filled), style = MaterialTheme.typography.bodyLarge, color = if (i == 0) colors.accent else colors.text, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, modifier = Modifier.weight(1f))
                Text("${(s.fraction * 100).roundToInt()}%".padStart(4), style = MaterialTheme.typography.bodyLarge, color = colors.text, maxLines = 1, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
            }
        }
    }
}

/** Retro: budget as a row of blocks (red past the limit); falls back to a days strip without a budget. */
@Composable
fun BudgetBlocks(fraction: Float?, bars: List<BarEntry>, modifier: Modifier = Modifier) {
    val colors = Spend.ink
    Column(modifier.fillMaxWidth()) {
        Text(if (fraction != null) "Budget used:" else "Spending by day:", style = MaterialTheme.typography.bodyMedium, color = colors.text)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().height(24.dp).bevel(pressed = true, fill = colors.raised).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (fraction != null) {
                val blocks = 18
                val lit = (fraction * blocks / fraction.coerceAtLeast(1f)).roundToInt()
                val over = if (fraction > 1f) (blocks / fraction).roundToInt() else blocks
                repeat(blocks) { i ->
                    val color = when {
                        i >= lit -> Color.Transparent
                        i >= over -> colors.alert
                        else -> colors.accent
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().background(color))
                }
            } else {
                val max = bars.maxOfOrNull { it.value }?.coerceAtLeast(1L) ?: 1L
                bars.forEach { bar ->
                    Box(Modifier.weight(1f).fillMaxHeight().background(colors.accent.copy(alpha = 0.12f + 0.88f * bar.value / max)))
                }
            }
        }
    }
}

/** Retro: "▣ Shopping ........ ₹45,278". */
@Composable
fun RetroList(slices: List<CategorySlice>, currency: CurrencyOption) {
    val colors = Spend.ink
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        slices.take(6).forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("▣ " + s.category.label, style = MaterialTheme.typography.bodyLarge, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(currency.format(s.amountMinor).substringBefore('.'), style = MaterialTheme.typography.bodyLarge, color = colors.text, maxLines = 1)
            }
        }
    }
}

/** Blueprint: outlined bars, the largest hatched in the accent with a leader note. */
@Composable
fun HatchedBars(slices: List<CategorySlice>) {
    if (slices.isEmpty()) return
    val colors = Spend.ink
    val shown = slices.take(5)
    val max = shown.maxOf { it.fraction }.coerceAtLeast(0.01f)
    Column {
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 1.5.dp.toPx()
                drawRect(colors.text, Offset(0f, 0f), Size(stroke, size.height))
                drawRect(colors.text, Offset(0f, size.height - stroke), Size(size.width, stroke))
                val pad = 10.dp.toPx()
                val gap = 10.dp.toPx()
                val w = (size.width - pad * 2 - gap * (shown.size - 1)) / shown.size
                shown.forEachIndexed { i, s ->
                    val h = ((size.height - 24.dp.toPx()) * s.fraction / max).coerceAtLeast(4.dp.toPx())
                    val left = pad + i * (w + gap)
                    val topY = size.height - stroke - h
                    val color = if (i == 0) colors.accent else colors.text
                    if (i == 0) {
                        clipRect(left, topY, left + w, size.height - stroke) {
                            var x = left - h
                            while (x < left + w) {
                                drawLine(color.copy(alpha = 0.5f), Offset(x, size.height), Offset(x + h, size.height - h), 1.5.dp.toPx())
                                x += 7.dp.toPx()
                            }
                        }
                    }
                    drawRect(color, Offset(left, topY), Size(w, h), style = Stroke(stroke))
                }
            }
            Label(
                "← A: ${shown[0].category.short().uppercase()} ${(shown[0].fraction * 100).roundToInt()}%",
                color = colors.text,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            shown.forEach { s ->
                Text(s.category.short().uppercase().take(4), style = MaterialTheme.typography.labelMedium, color = colors.text, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

/** Aurora: a glowing split bar and a colour-keyed legend. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlowSplit(slices: List<CategorySlice>) {
    if (slices.isEmpty()) return
    val colors = Spend.ink
    val palette = listOf(colors.accent) + AuroraGlow
    val shown = slices.take(4)
    val rest = 1f - shown.sumOf { it.fraction.toDouble() }.toFloat()
    val grow by animateFloatAsState(1f, tween(900, easing = Emphasized), label = "glow")
    Row(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
            .drawBehind {
                drawRoundRect(colors.accent.copy(alpha = 0.35f), Offset(-6.dp.toPx(), -6.dp.toPx()), Size(size.width + 12.dp.toPx(), size.height + 12.dp.toPx()), CornerRadius(20.dp.toPx()))
            }
            .clip(RoundedCornerShape(999.dp)),
    ) {
        shown.forEachIndexed { i, s ->
            Box(Modifier.weight(s.fraction.coerceAtLeast(0.001f) * grow + 0.0001f).fillMaxHeight().background(palette[i]))
        }
        if (rest > 0.001f) Box(Modifier.weight(rest).fillMaxHeight().background(colors.text.copy(alpha = 0.3f)))
    }
    Spacer(Modifier.height(14.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEachIndexed { i, s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(palette[i], CircleShape))
                Spacer(Modifier.width(6.dp))
                Text("${s.category.short()} ${(s.fraction * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium, color = colors.text)
            }
        }
    }
}

/** Riso: overlapping ink circles sized by share, overprinted where they meet. */
@Composable
fun RisoBubbles(slices: List<CategorySlice>) {
    if (slices.isEmpty()) return
    val colors = Spend.ink
    val inks = listOf(colors.accent, if (colors.isDark) Color(0xFF3FA9F5) else RisoBlue, Color(0xFFFFD23F))
    val shown = slices.take(3)
    val top = shown[0].fraction.coerceAtLeast(0.01f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val base = 150f
        val d = shown.map { (base * sqrt(it.fraction / top)).coerceAtLeast(56f) }
        val pos = buildList {
            add(0f to 12f)
            if (d.size > 1) add(d[0] * 0.78f to 0f)
            if (d.size > 2) add(d[0] * 0.78f + d[1] * 0.55f to d[1] * 0.8f)
        }
        val right = d.indices.maxOf { pos[it].first + d[it] }
        val scale = (maxWidth.value / right).coerceAtMost(1f)
        val height = d.indices.maxOf { pos[it].second + d[it] } * scale
        Box(Modifier.fillMaxWidth().height(height.dp)) {
            shown.forEachIndexed { i, s ->
                val size = d[i] * scale
                Box(
                    Modifier
                        .offset((pos[i].first * scale).dp, (pos[i].second * scale).dp)
                        .size(size.dp)
                        .graphicsLayer { blendMode = if (colors.isDark) BlendMode.Screen else BlendMode.Multiply }
                        .background(inks[i], CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(s.fraction * 100).roundToInt()}%", style = numberStyle(MaterialTheme.typography.headlineSmall), color = contentOn(inks[i]))
                        if (size > 70f) Text(s.category.short().uppercase(), style = MaterialTheme.typography.labelMedium, color = contentOn(inks[i]), maxLines = 1)
                    }
                }
            }
        }
    }
    val others = slices.drop(3).take(4)
    if (others.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        others.forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(s.category.label, style = MaterialTheme.typography.bodyMedium, color = colors.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${(s.fraction * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
        }
    }
}
