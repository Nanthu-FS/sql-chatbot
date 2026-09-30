package com.spendlens.app.ui.components

import com.spendlens.app.ui.theme.CardStyle
import com.spendlens.app.ui.theme.Style
import com.spendlens.app.ui.theme.AuroraGlow
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
import com.spendlens.app.ui.theme.ControlStyle
import com.spendlens.app.ui.theme.Spend
import com.spendlens.app.ui.theme.TabStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * DAY  WEEK  MONTH  YEAR. Print looks: a 2dp underline that springs (and overshoots) to the
 * selection. Card looks: a sliding accent pill. Grid looks: ruled cells with a sliding ink block.
 */
@Composable
fun UnderlineTabs(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = Spend.ink
    val look = Spend.look
    val haptics = rememberHaptics()
    val segmented = look.tabs == TabStyle.SEGMENTED
    val cells = look.tabs == TabStyle.CELLS
    val brutal = look.control == ControlStyle.BLOCK
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .then(
                when {
                    segmented -> Modifier.background(colors.ghost, RoundedCornerShape(look.controlRadius.coerceAtMost(100.dp))).padding(4.dp)
                    cells && brutal -> Modifier.hardShadow(colors.text, 4.dp).background(colors.raised).border(3.dp, colors.text)
                    cells -> Modifier.border(1.dp, colors.lineStrong)
                    else -> Modifier
                },
            ),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * selectedIndex, bouncy(), label = "tab")
        val selectedFill = if (cells) colors.text else colors.accent
        val selectedText = if (cells) colors.inverse else colors.onAccent
        if (segmented || cells) {
            Box(
                Modifier
                    .offset(x = offset)
                    .width(segment)
                    .height(44.dp)
                    .background(selectedFill, if (segmented) RoundedCornerShape(look.controlRadius.coerceAtMost(100.dp)) else RoundedCornerShape(0.dp)),
            )
        }
        Column {
            Row(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, label ->
                    val selected = i == selectedIndex
                    val color by animateColorAsState(
                        when {
                            selected && (segmented || cells) -> selectedText
                            selected -> colors.text
                            else -> if (segmented || cells) colors.muted else colors.faint
                        },
                        label = "tabColor",
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(if (segmented || cells) 44.dp else 46.dp)
                            .pressable(haptic = false) {
                                if (!selected) {
                                    haptics.tick()
                                    onSelect(i)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(caps(label), style = MaterialTheme.typography.labelLarge, color = color, textAlign = TextAlign.Center)
                    }
                }
            }
            if (!segmented && !cells) {
                Box(Modifier.fillMaxWidth().height(2.dp)) {
                    Hairline(Modifier.align(Alignment.BottomStart))
                    Box(Modifier.offset(x = offset + segment * 0.3f).width(segment * 0.4f).height(2.dp).background(colors.accent))
                }
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
            backgroundColor = colors.canvas
            blurRadius = (8 + 22 * glass).dp
            tints = listOf(HazeTint(colors.canvas.copy(alpha = 0.78f - 0.38f * glass)))
            noiseFactor = 0.06f * glass
        }
    } else if (Spend.look.card == CardStyle.BEVEL) {
        Modifier.bevel()
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
                            caps(item.label),
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
                            .background(colors.accent),
                    )
                }
            }
            val look = Spend.look
            Box(
                Modifier
                    .size(48.dp)
                    .pressable(pressedScale = 0.88f, onClick = onScan)
                    .then(
                        if (look.control == ControlStyle.BLOCK) {
                            Modifier.hardShadow(colors.text, 3.dp).background(colors.accent).border(3.dp, colors.text)
                        } else if (look.control == ControlStyle.BEVEL) {
                            Modifier.bevel()
                        } else if (look.style == Style.AURORA) {
                            Modifier.background(Brush.linearGradient(listOf(colors.accent, AuroraGlow[0])), CircleShape)
                        } else {
                            Modifier.background(colors.accent, RoundedCornerShape(look.controlRadius.coerceIn(2.dp, 100.dp)))
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add payments", tint = if (look.control == ControlStyle.BEVEL) colors.text else colors.onAccent, modifier = Modifier.size(22.dp))
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
    val round = Spend.look.controlRadius > 8.dp
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
        Box(Modifier.width(width * fraction).height(if (round) 2.dp else 1.dp).background(colors.accent))
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
                .background(colors.accent, if (round) CircleShape else RectangleShape),
        )
    }
}

/** "[ ✓ INCLUDED ]" / "[   SKIPPED ]" toggle with a bounce and a haptic. */
@Composable
fun BracketToggle(checked: Boolean, onChange: (Boolean) -> Unit, on: String, off: String, modifier: Modifier = Modifier) {
    val colors = Spend.ink
    val haptics = rememberHaptics()
    val look = Spend.look
    val border by animateColorAsState(if (checked) colors.text else colors.line, label = "toggle")
    val pill = look.control == ControlStyle.PILL
    val bg by animateColorAsState(if (pill && checked) colors.accent else if (pill) colors.ghost else Color.Transparent, label = "toggleBg")
    val ink = if (pill && checked) colors.onAccent else colors.text
    Row(
        modifier
            .then(
                when (look.control) {
                    ControlStyle.PILL -> Modifier.background(bg, controlShape())
                    ControlStyle.BLOCK -> Modifier.background(if (checked) colors.accent else colors.raised).border(2.dp, colors.text)
                    ControlStyle.BEVEL -> Modifier.bevel(pressed = checked)
                    else -> Modifier.border(1.dp, border, controlShape())
                },
            )
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
                .border(1.dp, ink, if (pill) CircleShape else RectangleShape)
                .background(if (checked) ink else Color.Transparent, if (pill) CircleShape else RectangleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(caps(if (checked) on else off), style = MaterialTheme.typography.labelLarge, color = if (checked) ink else colors.muted)
    }
}

/** Editorial list row: time · payee / category · amount. */
@Composable
fun TransactionRow(txn: Txn, currency: CurrencyOption, onClick: () -> Unit, modifier: Modifier = Modifier, showDate: Boolean = false) {
    val colors = Spend.ink
    Column(modifier.fillMaxWidth().pressable(pressedScale = 0.98f, onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.Top) {
            Text(
                caps(if (showDate) Format.shortDate(txn.dateTime.toLocalDate()) else Format.time(txn.dateTime)),
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
