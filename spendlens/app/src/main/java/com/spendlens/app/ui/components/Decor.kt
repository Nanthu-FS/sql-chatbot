package com.spendlens.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.spendlens.app.domain.CurrencyOption
import com.spendlens.app.ui.theme.Spend
import kotlin.math.abs

/** Total that rolls up to its new value instead of jumping. */
@Composable
fun RollingAmount(amountMinor: Long, currency: CurrencyOption, style: TextStyle, modifier: Modifier = Modifier, color: Color = Spend.ink.text) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(amountMinor) { anim.animateTo(amountMinor.toFloat(), tween(1100, easing = Emphasized)) }
    val v = anim.value
    val shown = if (abs(v - amountMinor.toFloat()) < 0.5f) amountMinor else v.toLong() / 100 * 100
    AmountText(shown, currency, style, modifier, color)
}

/** Slightly tilted ribbon with scrolling uppercase text — the reference's "TURNING COMPLEXITY…" band. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Ribbon(text: String, modifier: Modifier = Modifier, angle: Float = -2.5f) {
    val colors = Spend.ink
    Column(
        modifier
            .fillMaxWidth()
            .rotate(angle)
            .background(colors.raised),
    ) {
        Hairline(color = colors.lineStrong)
        Text(
            (text.uppercase() + "     •  •     ").repeat(4),
            style = MaterialTheme.typography.labelLarge,
            color = colors.muted,
            maxLines = 1,
            modifier = Modifier
                .padding(vertical = 9.dp)
                .then(if (MotionSettings.loops) Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 0, velocity = 28.dp) else Modifier),
        )
        Hairline(color = colors.lineStrong)
    }
}

/** A single white line sweeping over a screenshot while it's being read. */
@Composable
fun ScanOverlay(modifier: Modifier = Modifier) {
    if (!MotionSettings.loops) {
        Canvas(modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = 0.45f))
            drawLine(Color.White, Offset(0f, size.height * 0.4f), Offset(size.width, size.height * 0.4f), 1.dp.toPx())
        }
        return
    }
    val y by rememberInfiniteTransition(label = "scan").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1300, easing = Emphasized), RepeatMode.Reverse), label = "y",
    )
    Canvas(modifier.fillMaxSize()) {
        drawRect(Color.Black.copy(alpha = 0.45f))
        val ly = size.height * y
        drawLine(Color.White, Offset(0f, ly), Offset(size.width, ly), 1.dp.toPx())
    }
}
