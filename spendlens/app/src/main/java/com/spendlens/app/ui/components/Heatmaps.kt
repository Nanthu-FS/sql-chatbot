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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spendlens.app.domain.HeatDay
import com.spendlens.app.ui.theme.Spend
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

/** Month as a calendar of grey squares. Tap a day to open it. */
@Composable
fun MonthHeatmap(days: List<HeatDay>, today: LocalDate, onDayClick: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    if (days.isEmpty()) return
    val colors = Spend.ink
    val haptics = rememberHaptics()
    val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val leading = ((days.first().date.dayOfWeek.value - firstDay.value) + 7) % 7
    val cells: List<HeatDay?> = List(leading) { null } + days
    val appear = remember(days.first().date) { Animatable(0f) }
    LaunchedEffect(days.first().date) { appear.animateTo(1f, tween(900, easing = EmphasizedDecelerate)) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (0 until 7).forEach {
                Text(
                    firstDay.plus(it.toLong()).getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.faint,
                    textAlign = TextAlign.Center,
                )
            }
        }
        cells.chunked(7).forEachIndexed { row, week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until 7).forEach { col ->
                    val cell = week.getOrNull(col)
                    val shade = if (cell != null && cell.amountMinor > 0) 0.15f + cell.intensity * 0.85f else 0f
                    val local = (appear.value * 1.5f - (row * 7 + col) / 62f).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .graphicsLayer { alpha = local }
                            .then(
                                if (cell == null) {
                                    Modifier
                                } else {
                                    Modifier
                                        .background(colors.ramp(shade))
                                        .then(if (cell.date == today) Modifier.border(1.dp, colors.text) else Modifier)
                                        .pressable(pressedScale = 0.85f, haptic = false) {
                                            haptics.tick()
                                            onDayClick(cell.date)
                                        }
                                },
                            ),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        if (cell != null) {
                            Text(
                                cell.date.dayOfMonth.toString(),
                                modifier = Modifier.padding(4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    shade > 0.3f -> colors.inverse
                                    cell.date.isAfter(today) -> colors.faint.copy(alpha = 0.5f)
                                    else -> colors.muted
                                },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        HeatLegend()
    }
}

@Composable
fun HeatLegend(modifier: Modifier = Modifier) {
    val colors = Spend.ink
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Label("Less", color = colors.faint)
        Spacer(Modifier.width(6.dp))
        listOf(0f, 0.3f, 0.55f, 0.8f, 1f).forEach {
            Box(Modifier.padding(horizontal = 1.dp).size(10.dp).background(colors.ramp(it)))
        }
        Spacer(Modifier.width(6.dp))
        Label("More", color = colors.faint)
    }
}

/** Contribution-style grid for the whole year; scrolls to the current week. */
@Composable
fun YearHeatmap(days: List<HeatDay>, today: LocalDate, onDayClick: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    if (days.isEmpty()) return
    val colors = Spend.ink
    val haptics = rememberHaptics()
    val measurer = rememberTextMeasurer()
    val label = MaterialTheme.typography.labelSmall.copy(color = colors.faint)
    val density = LocalDensity.current
    val cell = 11.dp
    val gap = 3.dp
    val top = 16.dp
    val start = days.first().date
    val gridStart = start.minusDays(((start.dayOfWeek.value - DayOfWeek.MONDAY.value) + 7L) % 7)
    val weeks = (ChronoUnit.DAYS.between(gridStart, days.last().date) / 7 + 1).toInt()
    val byDate = remember(days) { days.associateBy { it.date } }
    val scroll = rememberScrollState()
    LaunchedEffect(start) {
        val focus = if (today.year == start.year) today else days.last().date
        val week = (ChronoUnit.DAYS.between(gridStart, focus) / 7).toInt()
        scroll.scrollTo(with(density) { ((cell + gap) * week - 300.dp).roundToPx() }.coerceAtLeast(0))
    }
    Column(modifier) {
        Row(Modifier.horizontalScroll(scroll)) {
            Canvas(
                Modifier
                    .width((cell + gap) * weeks)
                    .height(top + (cell + gap) * 7)
                    .pointerInput(days) {
                        detectTapGestures { o ->
                            val step = (cell + gap).toPx()
                            val row = ((o.y - top.toPx()) / step).toInt()
                            val date = gridStart.plusDays((o.x / step).toInt() * 7L + row)
                            if (row in 0..6 && byDate.containsKey(date)) {
                                haptics.tick()
                                onDayClick(date)
                            }
                        }
                    },
            ) {
                val step = (cell + gap).toPx()
                val c = cell.toPx()
                var lastMonth = -1
                for (w in 0 until weeks) for (d in 0 until 7) {
                    val date = gridStart.plusDays(w * 7L + d)
                    val heat = byDate[date] ?: continue
                    val o = Offset(w * step, top.toPx() + d * step)
                    drawRect(colors.ramp(if (heat.amountMinor > 0) 0.15f + heat.intensity * 0.85f else 0f), o, Size(c, c))
                    if (date == today) drawRect(colors.text, o, Size(c, c), style = Stroke(1.dp.toPx()))
                    if (d == 0 && date.dayOfMonth <= 7 && date.monthValue != lastMonth) {
                        lastMonth = date.monthValue
                        drawText(measurer.measure(date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(), label), topLeft = Offset(w * step, 0f))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        HeatLegend()
    }
}
