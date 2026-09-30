package com.spendlens.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.spendlens.app.domain.CurrencyOption
import com.spendlens.app.domain.Txn
import com.spendlens.app.ui.Format
import com.spendlens.app.ui.theme.Spend
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

/** DAY  MONTH  YEAR with a 2dp underline that springs (and overshoots) to the selection. */
@Composable
fun UnderlineTabs(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = Spend.ink
    val haptics = rememberHaptics()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * selectedIndex, bouncy(), label = "tab")
        Column {
            Row(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, label ->
                    val color by animateColorAsState(if (i == selectedIndex) colors.text else colors.faint, label = "tabColor")
                    Box(
                        Modifier
                            .weight(1f)
                            .pressable(haptic = false) {
                                if (i != selectedIndex) {
                                    haptics.tick()
                                    onSelect(i)
                                }
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label.uppercase(), style = MaterialTheme.typography.labelLarge, color = color, textAlign = TextAlign.Center)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(2.dp)) {
                Hairline(Modifier.align(Alignment.BottomStart))
                Box(Modifier.offset(x = offset + segment * 0.3f).width(segment * 0.4f).height(2.dp).background(colors.text))
            }
        }
    }
}

data class NavItem(val route: String, val label: String)

/** Flat bottom bar: text tabs with a sliding square marker + an ink scan button. */
@Composable
fun BottomBar(
    items: List<NavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
    haze: HazeState? = null,
) {
    val colors = Spend.ink
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val glass = LocalGlass.current
    val centers = remember { mutableStateMapOf<String, Float>() }
    val target = currentRoute?.let { centers[it] } ?: 0f
    val marker by animateDpAsState(with(density) { target.toDp() }, bouncy(), label = "navMarker")

    // With glass on, the page shows through, blurred; otherwise a solid bar.
    val surface = if (haze != null && glass > 0.01f) {
        Modifier.hazeEffect(haze) {
            blurRadius = (8 + 22 * glass).dp
            tints = listOf(HazeTint(colors.canvas.copy(alpha = 0.78f - 0.38f * glass)))
            noiseFactor = 0.06f * glass
        }
    } else {
        Modifier.background(colors.canvas)
    }

    Column(modifier.fillMaxWidth().then(surface)) {
        Hairline(color = if (glass > 0.01f) colors.lineStrong.copy(alpha = 0.6f) else colors.line)
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    items.forEach { item ->
                        val selected = item.route == currentRoute
                        val color by animateColorAsState(if (selected) colors.text else colors.faint, label = "navColor")
                        Text(
                            item.label.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = color,
                            modifier = Modifier
                                .onGloballyPositioned { centers[item.route] = it.positionInParent().x + it.size.width / 2f }
                                .pressable(haptic = false) {
                                    if (!selected) haptics.tick()
                                    onNavigate(item.route)
                                }
                                .padding(vertical = 14.dp),
                        )
                    }
                }
                if (currentRoute != null && centers.containsKey(currentRoute)) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = marker - 2.dp)
                            .size(4.dp)
                            .background(colors.text),
                    )
                }
            }
            Box(
                Modifier
                    .size(48.dp)
                    .background(colors.text, RoundedCornerShape(2.dp))
                    .pressable(pressedScale = 0.88f, onClick = onScan),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add payments", tint = colors.inverse, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/**
 * Hairline slider with a square thumb.
 * - ticks a haptic on every step, a heavier one at either end
 * - the thumb swells while held and springs back on release
 * - dragging past an end stretches with resistance, then bounces back
 */
@Composable
fun LineSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {},
) {
    val colors = Spend.ink
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val thumbScale = remember { Animatable(1f) }
    val overshoot = remember { Animatable(0f) }
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onFinished)
    val span = range.endInclusive - range.start
    val fraction = ((value - range.start) / span).coerceIn(0f, 1f)
    var rawFraction by remember { mutableFloatStateOf(fraction) }

    fun snap(f: Float): Float {
        val clamped = f.coerceIn(0f, 1f)
        return if (steps > 0) (clamped * steps).roundToInt() / steps.toFloat() else clamped
    }

    fun update(f: Float) {
        rawFraction = f
        val snapped = snap(f)
        val newValue = range.start + snapped * span
        if (newValue != current) {
            if (snapped == 0f || snapped == 1f) haptics.edge() else haptics.tick()
            change(newValue)
        }
        // Rubber band beyond the ends: moves sqrt-ish of the excess.
        val excess = when {
            f < 0f -> f
            f > 1f -> f - 1f
            else -> 0f
        }
        scope.launch { overshoot.snapTo(sign(excess) * sqrt(abs(excess)) * 0.12f) }
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(steps, span) {
                detectTapGestures(
                    onPress = {
                        scope.launch { thumbScale.animateTo(1.5f, snappy()) }
                        tryAwaitRelease()
                        scope.launch { thumbScale.animateTo(1f, bouncy()) }
                    },
                    onTap = { update(it.x / size.width); finished() },
                )
            }
            .pointerInput(steps, span) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        rawFraction = ((current - range.start) / span).coerceIn(0f, 1f)
                        scope.launch { thumbScale.animateTo(1.5f, snappy()) }
                    },
                    onDragEnd = {
                        scope.launch { thumbScale.animateTo(1f, bouncy()) }
                        scope.launch { overshoot.animateTo(0f, bouncy()) }
                        finished()
                    },
                    onDragCancel = {
                        scope.launch { thumbScale.animateTo(1f, bouncy()) }
                        scope.launch { overshoot.animateTo(0f, bouncy()) }
                    },
                ) { change, amount ->
                    change.consume()
                    update(rawFraction + amount / size.width)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val width = maxWidth
        val thumbX: Dp = width * (fraction + overshoot.value).coerceIn(-0.05f, 1.05f)
        Hairline(color = colors.lineStrong)
        Box(Modifier.width(width * fraction).height(1.dp).background(colors.text))
        if (steps in 1..24) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                repeat(steps + 1) { Box(Modifier.width(1.dp).height(5.dp).background(colors.lineStrong)) }
            }
        }
        Box(
            Modifier
                .offset { IntOffset((thumbX - 7.dp).roundToPx(), 0) }
                .size(14.dp)
                .graphicsLayer {
                    scaleX = thumbScale.value
                    scaleY = thumbScale.value
                }
                .background(colors.text),
        )
    }
}

/** "[ ✓ INCLUDED ]" / "[   SKIPPED ]" toggle with a bounce and a haptic. */
@Composable
fun BracketToggle(checked: Boolean, onChange: (Boolean) -> Unit, on: String, off: String, modifier: Modifier = Modifier) {
    val colors = Spend.ink
    val haptics = rememberHaptics()
    val border by animateColorAsState(if (checked) colors.text else colors.line, label = "toggle")
    Row(
        modifier
            .border(1.dp, border, RoundedCornerShape(2.dp))
            .pressable(haptic = false) {
                if (checked) haptics.tick() else haptics.confirm()
                onChange(!checked)
            }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .border(1.dp, colors.text)
                .background(if (checked) colors.text else colors.canvas),
        )
        Spacer(Modifier.width(8.dp))
        Text((if (checked) on else off).uppercase(), style = MaterialTheme.typography.labelLarge, color = if (checked) colors.text else colors.faint)
    }
}

/** Editorial list row: time · payee / category · amount. */
@Composable
fun TransactionRow(txn: Txn, currency: CurrencyOption, onClick: () -> Unit, modifier: Modifier = Modifier, showDate: Boolean = false) {
    val colors = Spend.ink
    Column(modifier.fillMaxWidth().pressable(pressedScale = 0.98f, onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.Top) {
            Text(
                if (showDate) Format.shortDate(txn.dateTime.toLocalDate()).uppercase() else Format.time(txn.dateTime).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.faint,
                modifier = Modifier.width(58.dp).padding(top = 3.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(txn.merchant, style = MaterialTheme.typography.titleMedium, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Label(listOfNotNull(txn.category.short, txn.paymentApp).joinToString("  ·  "), color = colors.faint)
            }
            Spacer(Modifier.width(12.dp))
            Text(currency.format(txn.amountMinor), style = MaterialTheme.typography.titleMedium, color = colors.text)
        }
        Hairline()
    }
}
