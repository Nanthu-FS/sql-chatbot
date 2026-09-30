package com.spendlens.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spendlens.app.domain.BarEntry
import com.spendlens.app.domain.CategorySlice
import com.spendlens.app.ui.theme.SpendTheme
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Rounds up to 1/2/2.5/5 × 10ⁿ (in major units) so grid lines land on friendly numbers. */
fun niceCeiling(valueMinor: Long): Long {
    if (valueMinor <= 0) return 0
    val major = valueMinor / 100.0
    val magnitude = 10.0.pow(floor(log10(major)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it * magnitude >= major }
    return (step * magnitude * 100).toLong()
}

/**
 * Interactive bar chart: bars grow in with a stagger, tap or scrub to see a tooltip,
 * dashed average line, friendly grid.
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
    height: Dp = 230.dp,
) {
    if (bars.isEmpty()) return
    val colors = SpendTheme.colors
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    val currentSelection by rememberUpdatedState(selectedIndex)
    val select by rememberUpdatedState(onSelect)

    val progress = remember { Animatable(0f) }
    LaunchedEffect(bars) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }

    val maxValue = niceCeiling(bars.maxOf { it.value })
    val axisStyle = typography.labelSmall.copy(color = colors.textFaint)
    val tipTitleStyle = typography.labelSmall.copy(color = scheme.surface.copy(alpha = 0.75f))
    val tipValueStyle = typography.titleSmall.copy(color = scheme.surface)
    val avgStyle = typography.labelSmall.copy(color = colors.textMuted)

    fun indexAt(x: Float, width: Int): Int = (x / (width.toFloat() / bars.size)).toInt().coerceIn(0, bars.lastIndex)

    fun choose(index: Int?) {
        if (index != currentSelection) {
            if (index != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            select(index)
        }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(bars) {
                detectTapGestures { offset ->
                    val index = indexAt(offset.x, size.width)
                    choose(if (index == currentSelection) null else index)
                }
            }
            .pointerInput(bars) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> choose(indexAt(offset.x, size.width)) },
                ) { change, _ ->
                    change.consume()
                    choose(indexAt(change.position.x, size.width))
                }
            },
    ) {
        val axisHeight = 24.dp.toPx()
        val top = 58.dp.toPx()
        val bottom = size.height - axisHeight
        val chartHeight = bottom - top
        val slot = size.width / bars.size
        val barWidth = min(slot * 0.62f, 26.dp.toPx())
        val minBar = 4.dp.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))

        // Grid: two dashed lines at half and full scale, labelled on the right.
        if (maxValue > 0) {
            for (step in 1..2) {
                val y = bottom - chartHeight * step / 2f
                drawLine(colors.chartTrack, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = dash)
                val label = measurer.measure(formatAxis(maxValue * step / 2), axisStyle)
                drawText(label, topLeft = Offset(size.width - label.size.width, y - label.size.height - 2.dp.toPx()))
            }
        }
        drawLine(colors.chartTrack, Offset(0f, bottom), Offset(size.width, bottom), strokeWidth = 1.dp.toPx())

        val selected = currentSelection
        bars.forEachIndexed { i, bar ->
            val stagger = i.toFloat() / bars.size * 0.4f
            val p = ((progress.value - stagger) / 0.6f).coerceIn(0f, 1f)
            val eased = 1f - (1f - p) * (1f - p)
            val full = if (maxValue > 0) bar.value.toFloat() / maxValue * chartHeight else 0f
            val h = max(full * eased, minBar)
            val left = i * slot + (slot - barWidth) / 2
            val dimmed = selected != null && selected != i
            val brush = when {
                bar.value == 0L -> SolidColor(colors.chartTrack)
                bar.isCurrent || selected == i -> Brush.verticalGradient(listOf(colors.brand[2], colors.brand[1]), startY = bottom - h, endY = bottom)
                else -> Brush.verticalGradient(listOf(colors.brand[1], colors.brand[0]), startY = bottom - h, endY = bottom)
            }
            drawRoundRect(
                brush = brush,
                topLeft = Offset(left, bottom - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
                alpha = if (dimmed) 0.3f else 1f,
            )
            if (bar.axisLabel.isNotEmpty()) {
                val label = measurer.measure(bar.axisLabel, axisStyle)
                drawText(label, topLeft = Offset(i * slot + slot / 2 - label.size.width / 2, bottom + 6.dp.toPx()))
            }
            if (bar.isCurrent) {
                drawCircle(colors.brand[2], radius = 2.5.dp.toPx(), center = Offset(i * slot + slot / 2, bottom + axisHeight - 3.dp.toPx()))
            }
        }

        // Average line
        if (average != null && average > 0 && maxValue > 0 && selected == null) {
            val y = bottom - average.toFloat() / maxValue * chartHeight * progress.value
            drawLine(colors.brand[2].copy(alpha = 0.8f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
            val label = measurer.measure("avg " + formatAxis(average), avgStyle)
            drawText(label, topLeft = Offset(0f, y - label.size.height - 2.dp.toPx()))
        }

        // Tooltip for the selected bar
        if (selected != null && selected in bars.indices) {
            val bar = bars[selected]
            val cx = selected * slot + slot / 2
            val barTop = bottom - max(if (maxValue > 0) bar.value.toFloat() / maxValue * chartHeight else 0f, minBar)
            val title = measurer.measure(bar.tooltipLabel, tipTitleStyle)
            val value = measurer.measure(formatValue(bar.value), tipValueStyle)
            val padH = 12.dp.toPx()
            val padV = 8.dp.toPx()
            val w = max(title.size.width, value.size.width) + padH * 2
            val h = title.size.height + value.size.height + padV * 2
            val x = (cx - w / 2).coerceIn(0f, size.width - w)
            val y = 0f
            drawLine(scheme.onSurface.copy(alpha = 0.25f), Offset(cx, y + h), Offset(cx, barTop - 4.dp.toPx()), strokeWidth = 1.dp.toPx(), pathEffect = dash)
            drawRoundRect(scheme.onSurface, topLeft = Offset(x, y), size = Size(w, h), cornerRadius = CornerRadius(12.dp.toPx()))
            drawText(title, topLeft = Offset(x + padH, y + padV))
            drawText(value, topLeft = Offset(x + padH, y + padV + title.size.height))
        }
    }
}

/** Animated donut with rounded segment ends. Tap a segment to focus it. */
@Composable
fun DonutChart(
    slices: List<CategorySlice>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    thickness: Dp = 22.dp,
    center: @Composable () -> Unit,
) {
    val colors = SpendTheme.colors
    val progress = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 1100, easing = FastOutSlowInEasing))
    }
    val currentSelection by rememberUpdatedState(selectedIndex)
    val select by rememberUpdatedState(onSelect)
    val haptics = LocalHapticFeedback.current

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(slices) {
                    detectTapGestures { offset ->
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val dx = offset.x - c.x
                        val dy = offset.y - c.y
                        val radius = min(size.width, size.height) / 2f
                        val distance = sqrt(dx * dx + dy * dy)
                        if (distance < radius - thickness.toPx() * 1.8f || distance > radius + 8.dp.toPx()) {
                            select(null)
                            return@detectTapGestures
                        }
                        var angle = (atan2(dy, dx) * 180f / PI.toFloat()) + 90f
                        if (angle < 0) angle += 360f
                        var acc = 0f
                        val hit = slices.indexOfFirst { slice -> acc += slice.fraction * 360f; angle <= acc }
                        val index = if (hit >= 0) hit else slices.lastIndex
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        select(if (index == currentSelection) null else index)
                    }
                },
        ) {
            val stroke = thickness.toPx()
            val diameter = min(size.width, size.height) - stroke - 8.dp.toPx()
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val arcSize = Size(diameter, diameter)
            drawArc(colors.chartTrack, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (slices.isEmpty()) return@Canvas

            val capDegrees = (stroke / 2f) / (diameter / 2f) * 180f / PI.toFloat()
            val gap = if (slices.size > 1) capDegrees * 2 + 3f else 0f
            var start = -90f
            val sweepTotal = 360f * progress.value
            slices.forEachIndexed { i, slice ->
                val sweep = slice.fraction * sweepTotal
                val drawSweep = (sweep - gap).coerceAtLeast(0.1f)
                val selected = currentSelection == i
                val dimmed = currentSelection != null && !selected
                drawArc(
                    color = slice.category.color.copy(alpha = if (dimmed) 0.3f else 1f),
                    startAngle = start + gap / 2,
                    sweepAngle = drawSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = if (selected) stroke * 1.35f else stroke, cap = StrokeCap.Round),
                )
                start += sweep
            }
        }
        center()
    }
}

/** Smooth cumulative line with a gradient fill; reveals left to right and pulses at the end. */
@Composable
fun Sparkline(
    values: List<Long>,
    totalPoints: Int,
    modifier: Modifier = Modifier,
    lineColor: Color = Color.White,
) {
    if (values.isEmpty() || totalPoints < 2) return
    val progress = remember { Animatable(0f) }
    LaunchedEffect(values) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 1200, easing = FastOutSlowInEasing))
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
        label = "pulse",
    )
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val maxV = max(values.max(), 1L).toFloat()
            val stepX = size.width / (totalPoints - 1)
            val pad = 6.dp.toPx()
            val points = values.mapIndexed { i, v -> Offset(i * stepX, pad + (size.height - pad * 2) * (1f - v / maxV)) }
            val line = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]
                    val cur = points[i]
                    val midX = (prev.x + cur.x) / 2
                    cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
                }
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(points.last().x, size.height)
                lineTo(points.first().x, size.height)
                close()
            }
            clipRect(right = size.width * progress.value) {
                drawPath(fill, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.35f), lineColor.copy(alpha = 0f))))
                drawPath(line, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            }
            if (progress.value > 0.98f) {
                val end = points.last()
                drawCircle(lineColor.copy(alpha = (1f - pulse) * 0.5f), radius = 4.dp.toPx() + 10.dp.toPx() * pulse, center = end)
                drawCircle(lineColor, radius = 4.dp.toPx(), center = end)
            }
        }
    }
}
