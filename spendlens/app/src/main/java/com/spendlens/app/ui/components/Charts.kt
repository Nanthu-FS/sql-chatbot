package com.spendlens.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spendlens.app.domain.BarEntry
import com.spendlens.app.ui.theme.Spend
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Rounds up to 1/2/2.5/5 × 10ⁿ (major units) so grid lines land on friendly numbers. */
fun niceCeiling(valueMinor: Long): Long {
    if (valueMinor <= 0) return 0
    val major = valueMinor / 100.0
    val magnitude = 10.0.pow(floor(log10(major)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it * magnitude >= major }
    return (step * magnitude * 100).toLong()
}

/**
 * Monochrome bar chart. Bars rise in sequence; tap or scrub to read one (haptic tick per bar,
 * the picked bar pops and settles with a spring).
 */
@Composable
fun BarChart(
    bars: List<BarEntry>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    formatValue: (Long) -> String,
    formatAxis: (Long) -> String,
    modifier: Modifier = Modifier,
    average: Long? = null,
    height: Dp = 220.dp,
) {
    if (bars.isEmpty()) return
    val colors = Spend.ink
    val type = MaterialTheme.typography
    val measurer = rememberTextMeasurer()
    val haptics = rememberHaptics()
    val selection by rememberUpdatedState(selectedIndex)
    val select by rememberUpdatedState(onSelect)

    val grow = remember { Animatable(0f) }
    LaunchedEffect(bars) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(900, easing = Emphasized))
    }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex != null) {
            pop.snapTo(1.35f)
            pop.animateTo(1f, bouncy())
        }
    }

    val maxValue = niceCeiling(bars.maxOf { it.value })
    val axis = type.labelSmall.copy(color = colors.faint)
    val tipLabel = type.labelSmall.copy(color = colors.muted)
    val tipValue = type.titleLarge.copy(color = colors.text)

    fun indexAt(x: Float, width: Int) = (x / (width.toFloat() / bars.size)).toInt().coerceIn(0, bars.lastIndex)
    fun choose(i: Int?) {
        if (i != selection) {
            if (i != null) haptics.tick()
            select(i)
        }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(bars) {
                detectTapGestures { o -> indexAt(o.x, size.width).let { choose(if (it == selection) null else it) } }
            }
            .pointerInput(bars) {
                detectHorizontalDragGestures(onDragStart = { choose(indexAt(it.x, size.width)) }) { change, _ ->
                    change.consume()
                    choose(indexAt(change.position.x, size.width))
                }
            },
    ) {
        val top = 52.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        val chartH = bottom - top
        val slot = size.width / bars.size
        val barW = min(slot * 0.5f, 14.dp.toPx())
        val dotted = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 4.dp.toPx()))

        if (maxValue > 0) {
            for (step in 1..2) {
                val y = bottom - chartH * step / 2f
                drawLine(colors.lineStrong, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = dotted)
                val label = measurer.measure(formatAxis(maxValue * step / 2).uppercase(), axis)
                drawText(label, topLeft = Offset(size.width - label.size.width, y - label.size.height - 3.dp.toPx()))
            }
        }
        drawLine(colors.lineStrong, Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())

        val sel = selection
        bars.forEachIndexed { i, bar ->
            val stagger = i.toFloat() / bars.size * 0.45f
            val p = ((grow.value - stagger) / 0.55f).coerceIn(0f, 1f)
            val eased = 1f - (1f - p) * (1f - p) * (1f - p)
            val h = max(if (maxValue > 0) bar.value.toFloat() / maxValue * chartH * eased else 0f, 2.dp.toPx())
            val w = if (sel == i) barW * pop.value else barW
            val color = when {
                sel == i -> colors.text
                sel != null -> colors.line
                bar.isCurrent -> colors.text
                bar.value == 0L -> colors.ghost
                else -> colors.muted
            }
            drawRect(color, Offset(i * slot + (slot - w) / 2, bottom - h), Size(w, h))
            if (bar.axisLabel.isNotEmpty()) {
                val l = measurer.measure(bar.axisLabel.uppercase(), axis)
                drawText(l, topLeft = Offset(i * slot + slot / 2 - l.size.width / 2, bottom + 7.dp.toPx()))
            }
        }

        if (average != null && average > 0 && maxValue > 0 && sel == null) {
            val y = bottom - average.toFloat() / maxValue * chartH * grow.value
            drawLine(colors.text, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
            val l = measurer.measure("AVG " + formatAxis(average).uppercase(), axis.copy(color = colors.text))
            drawText(l, topLeft = Offset(0f, y - l.size.height - 3.dp.toPx()))
        }

        // Readout sits top-left, like a caption, with a hairline dropping to the bar.
        if (sel != null && sel in bars.indices) {
            val bar = bars[sel]
            val cx = sel * slot + slot / 2
            val label = measurer.measure(bar.tooltipLabel.uppercase(), tipLabel)
            val value = measurer.measure(formatValue(bar.value), tipValue)
            val x = (cx - 6.dp.toPx()).coerceIn(0f, size.width - max(label.size.width, value.size.width).toFloat())
            drawText(label, topLeft = Offset(x, 0f))
            drawText(value, topLeft = Offset(x, label.size.height + 2.dp.toPx()))
            val barTop = bottom - max(if (maxValue > 0) bar.value.toFloat() / maxValue * chartH else 0f, 2.dp.toPx())
            drawLine(colors.text, Offset(cx, label.size.height + value.size.height + 6.dp.toPx()), Offset(cx, barTop - 3.dp.toPx()), 1.dp.toPx())
        }
    }
}

/** Thin cumulative line, drawn left to right, ending in a square marker. */
@Composable
fun Sparkline(values: List<Long>, totalPoints: Int, modifier: Modifier = Modifier) {
    if (values.size < 2 || totalPoints < 2) return
    val colors = Spend.ink
    val draw = remember { Animatable(0f) }
    LaunchedEffect(values) {
        draw.snapTo(0f)
        draw.animateTo(1f, tween(1400, easing = Emphasized))
    }
    Canvas(modifier) {
        val maxV = max(values.max(), 1L).toFloat()
        val stepX = size.width / (totalPoints - 1)
        val pad = 4.dp.toPx()
        val pts = values.mapIndexed { i, v -> Offset(i * stepX, pad + (size.height - 2 * pad) * (1f - v / maxV)) }
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val a = pts[i - 1]
                val b = pts[i]
                val mx = (a.x + b.x) / 2
                cubicTo(mx, a.y, mx, b.y, b.x, b.y)
            }
        }
        drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        clipRect(right = size.width * draw.value) {
            drawPath(path, colors.text, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Square))
        }
        if (draw.value > 0.98f) {
            val end = pts.last()
            val s = 6.dp.toPx()
            drawRect(colors.text, Offset(end.x - s / 2, end.y - s / 2), Size(s, s))
        }
    }
}

/** One segmented line splitting a total into parts, darkest first. Selected part stays ink. */
@Composable
fun SplitBar(fractions: List<Float>, selected: Int?, modifier: Modifier = Modifier) {
    val colors = Spend.ink
    val grow = remember { Animatable(0f) }
    LaunchedEffect(fractions) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(1000, easing = Emphasized))
    }
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val gap = 2.dp.toPx()
        var x = 0f
        val n = fractions.size
        fractions.forEachIndexed { i, f ->
            val w = size.width * f * grow.value
            val shade = if (n <= 1) 1f else 1f - i.toFloat() / (n - 1) * 0.8f
            val color = when {
                selected == null -> colors.ramp(shade)
                selected == i -> colors.text
                else -> colors.ghost
            }
            if (w > gap) drawRect(color, Offset(x, 0f), Size(w - gap, size.height))
            x += w
        }
    }
}
