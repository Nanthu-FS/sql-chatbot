package com.pulse.tracker.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.tracker.PulseViewModel
import com.pulse.tracker.bestStreak
import com.pulse.tracker.data.AppData
import com.pulse.tracker.streak
import com.pulse.tracker.ui.components.PChip
import com.pulse.tracker.ui.components.PIcons
import com.pulse.tracker.ui.components.PLabel
import com.pulse.tracker.ui.components.PText
import com.pulse.tracker.ui.components.PulseCard
import com.pulse.tracker.ui.components.formatInt
import com.pulse.tracker.ui.theme.LocalMuted
import com.pulse.tracker.ui.theme.ThemeId
import com.pulse.tracker.ui.theme.pulse
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class Period(val label: String) { WEEK("Week"), MONTH("Month"), YEAR("Year") }

private data class Bar(val label: String, val value: Double, val current: Boolean)

@Composable
fun InsightsScreen(vm: PulseViewModel, data: AppData) {
    val t = pulse
    var period by rememberSaveable { mutableStateOf(Period.WEEK) }
    var weightDialog by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val kcalByDay = remember(data.foods) { data.foods.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.kcal } } }
    val mealsByDay = remember(data.foods) { data.foods.groupBy { it.date }.mapValues { (_, v) -> v.map { it.meal }.distinct().size } }

    fun avgOf(days: List<LocalDate>): Double {
        val vals = days.mapNotNull { kcalByDay[it] }
        return if (vals.isEmpty()) 0.0 else vals.average()
    }

    val bars: List<Bar>
    val avg: Double
    val prevAvg: Double
    val range: String
    when (period) {
        Period.WEEK -> {
            val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
            bars = days.map { Bar(it.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), kcalByDay[it] ?: 0.0, it == today) }
            avg = avgOf(days)
            prevAvg = avgOf((13 downTo 7).map { today.minusDays(it.toLong()) })
            range = "Avg daily intake · last 7 days"
        }
        Period.MONTH -> {
            val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
            bars = (3 downTo 0).map { w ->
                val end = today.minusDays(w * 7L)
                val days = (6 downTo 0).map { end.minusDays(it.toLong()) }
                Bar(days.first().format(fmt), avgOf(days), w == 0)
            }
            avg = avgOf((27 downTo 0).map { today.minusDays(it.toLong()) })
            prevAvg = avgOf((55 downTo 28).map { today.minusDays(it.toLong()) })
            range = "Avg daily intake · last 4 weeks"
        }
        Period.YEAR -> {
            bars = (1..today.monthValue).map { m ->
                val start = LocalDate.of(today.year, m, 1)
                val days = (0 until start.lengthOfMonth()).map { start.plusDays(it.toLong()) }.filter { !it.isAfter(today) }
                Bar(start.month.getDisplayName(TextStyle.NARROW, Locale.getDefault()), avgOf(days), m == today.monthValue)
            }
            avg = avgOf((0 until today.dayOfYear).map { today.minusDays(it.toLong()) })
            prevAvg = 0.0
            range = "Avg daily intake · ${today.year}"
        }
    }
    val delta = if (prevAvg > 0 && avg > 0) ((avg - prevAvg) / prevAvg * 100).roundToInt() else null

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
            .padding(horizontal = 20.dp).padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PLabel("Your trends")
            PText(if (t.id == ThemeId.NEON) "STATS.LOG" else "Insights", size = if (t.id == ThemeId.SWISS) 30.sp else 24.sp, display = true, italic = t.id == ThemeId.PAPER)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Period.entries.forEach { p -> PChip(p.label, p == period, { period = p }, Modifier.weight(1f), height = 38.dp) }
        }

        PulseCard(padding = 18.dp) {
            PText(range, size = 13.sp, color = LocalMuted.current)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    PText(if (avg > 0) formatInt(avg) else "—", size = 38.sp, display = true, tracking = -0.03f)
                    PText(" kcal / day", size = 14.sp, color = LocalMuted.current, modifier = Modifier.padding(bottom = 6.dp))
                }
                if (delta != null) {
                    Row(
                        Modifier.clip(RoundedCornerShape(if (t.radius > 0.dp) 14.dp else 0.dp)).background(t.c.surfaceAlt).padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(if (delta <= 0) PIcons.TrendDown else PIcons.TrendUp, null, Modifier.size(14.dp), tint = t.c.accentText)
                        PText("${abs(delta)}%", size = 13.sp, weight = FontWeight.Bold, color = t.c.accentText)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            BarChart(bars, data.goals.kcal, period.name)
        }

        Row(Modifier.fillMaxWidth().padding(end = if (t.id == ThemeId.BRUTAL) 5.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WeightCard(data, Modifier.weight(1f)) { weightDialog = true }
            MacroSplitCard(data, Modifier.weight(1f))
        }

        AveragesRow(data)
        ConsistencyCard(data, mealsByDay)
        Spacer(Modifier.height(130.dp))
    }

    if (weightDialog) WeightDialog(data.weights.lastOrNull()?.kg, onDismiss = { weightDialog = false }) { kg ->
        vm.logWeight(kg, LocalDate.now()); weightDialog = false
    }
}

@Composable
private fun BarChart(bars: List<Bar>, goal: Int, key: String) {
    val t = pulse
    val progress = remember { Animatable(0f) }
    LaunchedEffect(key) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    val maxV = maxOf(goal * 1.2, bars.maxOfOrNull { it.value } ?: 0.0, 1.0)
    val idle = t.c.text.copy(alpha = if (t.isDark) 0.18f else 0.14f)
    Column {
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val n = bars.size
                val gap = if (n > 8) 5.dp.toPx() else 9.dp.toPx()
                val bw = (size.width - gap * (n - 1)) / n
                val radius = if (t.radius > 0.dp) minOf(8.dp.toPx(), bw / 2) else 0f
                bars.forEachIndexed { i, b ->
                    val h = (b.value / maxV * size.height * progress.value).toFloat()
                    if (h > 0f) {
                        val color = when {
                            b.current -> t.c.accent
                            b.value > goal -> t.c.warm
                            else -> idle
                        }
                        drawRoundRect(color, Offset(i * (bw + gap), size.height - h), Size(bw, h), CornerRadius(radius))
                        if (t.id == ThemeId.BRUTAL) {
                            drawRoundRect(t.c.border, Offset(i * (bw + gap), size.height - h), Size(bw, h), CornerRadius(radius), style = Stroke(2.dp.toPx()))
                        }
                    }
                }
                val gy = (size.height - goal / maxV * size.height).toFloat()
                drawLine(
                    t.c.muted, Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                )
            }
            PText("Goal ${formatInt(goal)}", size = 11.sp, color = t.c.muted, modifier = Modifier.align(Alignment.TopEnd))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (bars.size > 8) 5.dp else 9.dp)) {
            bars.forEach { b ->
                PText(b.label, size = 11.sp, color = if (b.current) t.c.text else t.c.muted, align = TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 1, weight = if (b.current) FontWeight.Bold else null)
            }
        }
    }
}

@Composable
private fun WeightCard(data: AppData, modifier: Modifier, onLog: () -> Unit) {
    val t = pulse
    val since = LocalDate.now().minusDays(30)
    val recent = data.weights.filter { !it.date.isBefore(since) }.sortedBy { it.date }
    val latest = data.weights.maxByOrNull { it.date }
    PulseCard(modifier, onClick = onLog, padding = 14.dp) {
        PText("Weight", size = 13.sp, color = LocalMuted.current)
        Spacer(Modifier.height(6.dp))
        if (latest == null) {
            PText("—", size = 24.sp, display = true)
            Spacer(Modifier.height(8.dp))
            PText("Tap to log your weight and see a trend.", size = 12.sp, color = LocalMuted.current)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                PText("%.1f".format(latest.kg), size = 24.sp, display = true, weight = FontWeight.SemiBold)
                PText(" kg", size = 12.sp, color = LocalMuted.current, modifier = Modifier.padding(bottom = 3.dp))
            }
            Spacer(Modifier.height(8.dp))
            if (recent.size >= 2) {
                val lo = recent.minOf { it.kg }
                val hi = recent.maxOf { it.kg }
                val span = (hi - lo).coerceAtLeast(0.5)
                Canvas(Modifier.fillMaxWidth().height(48.dp)) {
                    val pts = recent.mapIndexed { i, w ->
                        Offset(i * size.width / (recent.size - 1), (4.dp.toPx() + (hi - w.kg) / span * (size.height - 8.dp.toPx())).toFloat())
                    }
                    val line = Path().apply { pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
                    val area = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                    drawPath(area, t.c.accentText.copy(alpha = 0.12f))
                    drawPath(line, t.c.accentText, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawCircle(t.c.accentText, 3.5.dp.toPx(), pts.last())
                }
                val change = recent.last().kg - recent.first().kg
                Spacer(Modifier.height(6.dp))
                PText("%+.1f kg in 30 days".format(change), size = 12.sp, weight = FontWeight.SemiBold, color = t.c.accentText)
            } else {
                PText("Log again to see your trend.", size = 12.sp, color = LocalMuted.current)
            }
        }
    }
}

@Composable
private fun MacroSplitCard(data: AppData, modifier: Modifier) {
    val t = pulse
    val since = LocalDate.now().minusDays(6)
    val week = data.foods.filter { !it.date.isBefore(since) }
    val p = week.sumOf { it.protein } * 4
    val c = week.sumOf { it.carbs } * 4
    val f = week.sumOf { it.fat } * 9
    val total = p + c + f
    PulseCard(modifier, padding = 14.dp) {
        PText("Macro split · 7d", size = 13.sp, color = LocalMuted.current)
        Spacer(Modifier.height(10.dp))
        if (total <= 0) {
            PText("Log a few meals to see your balance.", size = 12.sp, color = LocalMuted.current)
        } else {
            val items = listOf(Triple("Protein", p / total, t.c.protein), Triple("Carbs", c / total, t.c.carbs), Triple("Fat", f / total, t.c.fat))
            Row(Modifier.fillMaxWidth().height(12.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                items.forEach { (_, frac, col) ->
                    if (frac > 0.005) Box(Modifier.weight(frac.toFloat()).height(12.dp).clip(RoundedCornerShape(if (t.radius > 0.dp) 4.dp else 0.dp)).background(col))
                }
            }
            Spacer(Modifier.height(10.dp))
            items.forEach { (name, frac, col) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(col))
                    Spacer(Modifier.width(8.dp))
                    PText(name, size = 13.sp, modifier = Modifier.weight(1f))
                    PText("${(frac * 100).roundToInt()}%", size = 13.sp, display = true, weight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun AveragesRow(data: AppData) {
    val t = pulse
    val days = (1..7).map { LocalDate.now().minusDays(it.toLong()) }
    val water = days.map { d -> data.water.filter { it.date == d }.sumOf { it.ml } }.average() / 1000
    val steps = days.map { d -> data.metrics[d]?.steps ?: 0 }.average()
    val sleepDays = days.mapNotNull { d -> data.metrics[d]?.sleepMinutes?.takeIf { it > 0 } }
    val sleep = if (sleepDays.isEmpty()) null else sleepDays.average()
    Row(Modifier.fillMaxWidth().padding(end = if (t.id == ThemeId.BRUTAL) 5.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AvgCell("Water", "%.1f L".format(water), Modifier.weight(1f))
        AvgCell("Steps", formatInt(steps.roundToInt()), Modifier.weight(1f))
        AvgCell("Sleep", sleep?.let { "${(it / 60).toInt()}h ${(it % 60).roundToInt()}m" } ?: "—", Modifier.weight(1f))
    }
}

@Composable
private fun AvgCell(label: String, value: String, modifier: Modifier) {
    PulseCard(modifier, padding = 12.dp) {
        PLabel("$label · avg")
        Spacer(Modifier.height(4.dp))
        PText(value, size = 18.sp, display = true, weight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun ConsistencyCard(data: AppData, mealsByDay: Map<LocalDate, Int>) {
    val t = pulse
    val today = LocalDate.now()
    val logged = mealsByDay.keys
    val days = (34 downTo 0).map { today.minusDays(it.toLong()) }
    val count = days.count { it in logged }
    val current = streak(today) { it in logged }
    val first = logged.minOrNull() ?: today
    val allDays = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
    val best = bestStreak(allDays) { it in logged }
    PulseCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PText("Consistency", size = 13.sp, color = LocalMuted.current)
                Row(verticalAlignment = Alignment.Bottom) {
                    PText("$count", size = 26.sp, display = true, weight = FontWeight.SemiBold)
                    PText("/35 days", size = 14.sp, color = LocalMuted.current, modifier = Modifier.padding(bottom = 3.dp))
                }
                PText("Streak $current · best $best", size = 12.sp)
            }
            Column(Modifier.width(140.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                days.chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { d ->
                            val meals = mealsByDay[d] ?: 0
                            val alpha = when (meals) { 0 -> 0f; 1 -> 0.35f; 2 -> 0.65f; else -> 1f }
                            Box(
                                Modifier.weight(1f).aspectRatio(1f)
                                    .clip(RoundedCornerShape(if (t.radius > 0.dp) 4.dp else 0.dp))
                                    .background(if (meals == 0) t.c.track else t.c.accentText.copy(alpha = alpha)),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Shared by Insights and Essentials. */
@Composable
fun WeightDialog(current: Double?, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by remember { mutableStateOf(current?.let { "%.1f".format(Locale.US, it) } ?: "") }
    val value = text.replace(',', '.').toDoubleOrNull()
    com.pulse.tracker.ui.components.PDialog(
        title = "Log weight",
        onDismiss = onDismiss,
        confirmEnabled = value != null && value in 20.0..400.0,
        onConfirm = { value?.let(onSave) },
    ) {
        com.pulse.tracker.ui.components.PTextField(text, { text = it }, "Weight in kg", Modifier.fillMaxWidth(), number = true)
    }
}
