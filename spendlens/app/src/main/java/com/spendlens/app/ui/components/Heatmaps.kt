package com.spendlens.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.spendlens.app.domain.HeatDay
import com.spendlens.app.ui.theme.SpendColors
import com.spendlens.app.ui.theme.SpendTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

/** Empty → violet → magenta → orange as intensity rises. */
fun SpendColors.heat(intensity: Float): Color = when {
    intensity <= 0f -> heatEmpty
    intensity < 0.5f -> lerp(lerp(heatEmpty, brand[0], 0.35f), brand[0], intensity * 2)
    intensity < 0.85f -> lerp(brand[0], brand[1], (intensity - 0.5f) / 0.35f)
    else -> lerp(brand[1], brand[2], (intensity - 0.85f) / 0.15f)
}

/** Calendar grid for one month. Tap a day to drill into it. */
@Composable
fun MonthHeatmap(
    days: List<HeatDay>,
    today: LocalDate,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) return
    val colors = SpendTheme.colors
    val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val weekdays = (0 until 7).map { firstDay.plus(it.toLong()) }
    val leading = ((days.first().date.dayOfWeek.value - firstDay.value) + 7) % 7
    val cells: List<HeatDay?> = List(leading) { null } + days
    val appear = remember(days.first().date) { Animatable(0f) }
    LaunchedEffect(days.first().date) { appear.animateTo(1f, tween(700)) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            weekdays.forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textFaint,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        cells.chunked(7).forEachIndexed { row, week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0 until 7).forEach { col ->
                    val cell = week.getOrNull(col)
                    val index = row * 7 + col
                    val local = ((appear.value * 1.6f) - index / 60f).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .graphicsLayer {
                                alpha = local
                                scaleX = 0.7f + 0.3f * local
                                scaleY = 0.7f + 0.3f * local
                            }
                            .clip(RoundedCornerShape(10.dp))
                            .then(
                                if (cell == null) {
                                    Modifier
                                } else {
                                    Modifier
                                        .background(colors.heat(cell.intensity))
                                        .then(
                                            if (cell.date == today) Modifier.border(2.dp, colors.brand[2], RoundedCornerShape(10.dp)) else Modifier,
                                        )
                                        .bounceClick { onDayClick(cell.date) }
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (cell != null) {
                            Text(
                                cell.date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (cell.date == today) FontWeight.Bold else FontWeight.Medium,
                                color = when {
                                    cell.intensity > 0.45f -> Color.White
                                    cell.date.isAfter(today) -> colors.textFaint.copy(alpha = 0.5f)
                                    else -> colors.textMuted
                                },
                            )
                        }
                    }
                }
            }
        }
        HeatLegend(Modifier.padding(top = 6.dp))
    }
}

@Composable
fun HeatLegend(modifier: Modifier = Modifier) {
    val colors = SpendTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Text("Less", style = MaterialTheme.typography.labelSmall, color = colors.textFaint)
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { i ->
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .size(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.heat(i)),
            )
        }
        Text("More", style = MaterialTheme.typography.labelSmall, color = colors.textFaint)
    }
}

/** GitHub-style contribution grid for a whole year. Scrolls to the current week. */
@Composable
fun YearHeatmap(
    days: List<HeatDay>,
    today: LocalDate,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) return
    val colors = SpendTheme.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.textFaint)
    val density = LocalDensity.current
    val cell = 13.dp
    val gap = 3.dp
    val labelHeight = 18.dp
    val start = days.first().date
    val gridStart = start.minusDays(((start.dayOfWeek.value - DayOfWeek.MONDAY.value) + 7L) % 7)
    val weeks = (ChronoUnit.DAYS.between(gridStart, days.last().date) / 7 + 1).toInt()
    val width = (cell + gap) * weeks
    val scroll = rememberScrollState()
    val byDate = remember(days) { days.associateBy { it.date } }

    LaunchedEffect(start) {
        val focus = if (today.year == start.year) today else days.last().date
        val week = ChronoUnit.DAYS.between(gridStart, focus) / 7
        val target = with(density) { ((cell + gap) * week.toInt()).roundToPx() } - with(density) { 120.dp.roundToPx() }
        scroll.scrollTo(target.coerceAtLeast(0))
    }

    Column(modifier) {
        Row(Modifier.horizontalScroll(scroll)) {
            Canvas(
                Modifier
                    .width(width)
                    .height(labelHeight + (cell + gap) * 7)
                    .pointerInput(days) {
                        detectTapGestures { offset ->
                            val step = (cell + gap).toPx()
                            val col = (offset.x / step).toInt()
                            val row = ((offset.y - labelHeight.toPx()) / step).toInt()
                            if (row in 0..6) {
                                val date = gridStart.plusDays(col * 7L + row)
                                if (byDate.containsKey(date)) onDayClick(date)
                            }
                        }
                    },
            ) {
                val step = (cell + gap).toPx()
                val c = cell.toPx()
                val top = labelHeight.toPx()
                var lastMonth = -1
                for (w in 0 until weeks) {
                    for (d in 0 until 7) {
                        val date = gridStart.plusDays(w * 7L + d)
                        val heat = byDate[date] ?: continue
                        val origin = Offset(w * step, top + d * step)
                        drawRoundRect(colors.heat(heat.intensity), origin, Size(c, c), CornerRadius(3.dp.toPx()))
                        if (date == today) {
                            drawRoundRect(colors.brand[2], origin, Size(c, c), CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                        }
                        if (date.dayOfMonth <= 7 && d == 0 && date.monthValue != lastMonth) {
                            lastMonth = date.monthValue
                            val label = measurer.measure(date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()), labelStyle)
                            drawText(label, topLeft = Offset(w * step, 0f))
                        }
                    }
                }
            }
        }
        HeatLegend(Modifier.padding(top = 10.dp))
    }
}
