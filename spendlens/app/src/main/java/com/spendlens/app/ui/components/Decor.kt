package com.spendlens.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendlens.app.domain.CurrencyOption
import com.spendlens.app.ui.theme.SpendTheme
import kotlin.math.abs
import kotlin.math.sin

/** Counts up/down to the new amount instead of jumping. */
@Composable
fun AnimatedAmount(
    amountMinor: Long,
    currency: CurrencyOption,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val animated by animateFloatAsState(
        targetValue = amountMinor.toFloat(),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "amount",
    )
    val shown = if (abs(animated - amountMinor.toFloat()) < 0.5f) amountMinor else animated.toLong()
    // Round to whole units while counting so the decimals don't flicker.
    val display = if (shown == amountMinor) shown else shown / 100 * 100
    Text(currency.format(display), style = style, color = color, modifier = modifier, maxLines = 1)
}

/** Laser line sweeping over a screenshot while OCR runs. */
@Composable
fun ScanOverlay(modifier: Modifier = Modifier) {
    val colors = SpendTheme.colors
    val transition = rememberInfiniteTransition(label = "scan")
    val y by transition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scanY",
    )
    Canvas(modifier.fillMaxSize()) {
        drawRect(Color.Black.copy(alpha = 0.35f))
        val lineY = size.height * y
        val band = size.height * 0.18f
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, colors.brand[0].copy(alpha = 0.45f)),
                startY = lineY - band,
                endY = lineY,
            ),
            topLeft = Offset(0f, lineY - band),
            size = Size(size.width, band),
        )
        drawLine(
            Brush.horizontalGradient(colors.brand),
            Offset(0f, lineY),
            Offset(size.width, lineY),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
        // Corner brackets
        val arm = size.minDimension * 0.16f
        val inset = 8.dp.toPx()
        val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        listOf(
            Triple(Offset(inset, inset), 1f, 1f),
            Triple(Offset(size.width - inset, inset), -1f, 1f),
            Triple(Offset(inset, size.height - inset), 1f, -1f),
            Triple(Offset(size.width - inset, size.height - inset), -1f, -1f),
        ).forEach { (corner, dx, dy) ->
            val path = Path().apply {
                moveTo(corner.x, corner.y + arm * dy)
                lineTo(corner.x, corner.y)
                lineTo(corner.x + arm * dx, corner.y)
            }
            drawPath(path, Color.White, style = stroke)
        }
    }
}

/** Hand-drawn hero illustration for empty states: phone + receipt + floating chart and coin. */
@Composable
fun EmptyIllustration(modifier: Modifier = Modifier) {
    val colors = SpendTheme.colors
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val t by rememberInfiniteTransition(label = "float").animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "t",
    )
    val coinText = measurer.measure("₹", TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White))
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2
            // Glow
            drawCircle(
                Brush.radialGradient(listOf(colors.brand[0].copy(alpha = 0.35f), Color.Transparent), center = Offset(cx, h / 2), radius = w * 0.45f),
                radius = w * 0.45f,
                center = Offset(cx, h / 2),
            )
            // Phone
            val pw = w * 0.36f
            val ph = h * 0.78f
            val phoneTopLeft = Offset(cx - pw / 2, (h - ph) / 2 + sin(t) * 4.dp.toPx())
            drawRoundRect(scheme.surfaceContainerHigh, phoneTopLeft, Size(pw, ph), CornerRadius(22.dp.toPx()))
            drawRoundRect(colors.cardBorder, phoneTopLeft, Size(pw, ph), CornerRadius(22.dp.toPx()), style = Stroke(2.dp.toPx()))
            translate(phoneTopLeft.x, phoneTopLeft.y) {
                drawRoundRect(colors.cardBorder, Offset(pw * 0.38f, 10.dp.toPx()), Size(pw * 0.24f, 5.dp.toPx()), CornerRadius(3.dp.toPx()))
                drawCircle(colors.positive, radius = pw * 0.1f, center = Offset(pw / 2, ph * 0.2f))
                val check = Path().apply {
                    moveTo(pw / 2 - pw * 0.045f, ph * 0.2f)
                    lineTo(pw / 2 - pw * 0.01f, ph * 0.2f + pw * 0.035f)
                    lineTo(pw / 2 + pw * 0.05f, ph * 0.2f - pw * 0.035f)
                }
                drawPath(check, Color.White, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                drawRoundRect(Brush.horizontalGradient(colors.brand), Offset(pw * 0.2f, ph * 0.34f), Size(pw * 0.6f, ph * 0.07f), CornerRadius(8.dp.toPx()))
                listOf(0.5f to 0.7f, 0.58f to 0.5f, 0.66f to 0.6f).forEach { (y, len) ->
                    drawRoundRect(colors.chartTrack, Offset(pw * 0.2f, ph * y), Size(pw * len * 0.85f, ph * 0.03f), CornerRadius(4.dp.toPx()))
                }
            }
            // Floating mini chart card
            val cardW = w * 0.28f
            val cardH = h * 0.26f
            val cardOrigin = Offset(cx - pw / 2 - cardW * 0.7f, h * 0.52f + sin(t + 1.5f) * 6.dp.toPx())
            rotate(-8f, pivot = Offset(cardOrigin.x + cardW / 2, cardOrigin.y + cardH / 2)) {
                drawRoundRect(scheme.surfaceContainerHighest, cardOrigin, Size(cardW, cardH), CornerRadius(14.dp.toPx()))
                val barW = cardW * 0.14f
                listOf(0.35f, 0.6f, 0.45f, 0.85f).forEachIndexed { i, fraction ->
                    val bh = cardH * 0.7f * fraction
                    drawRoundRect(
                        Brush.verticalGradient(listOf(colors.brand[1], colors.brand[0])),
                        Offset(cardOrigin.x + cardW * 0.14f + i * barW * 1.6f, cardOrigin.y + cardH * 0.85f - bh),
                        Size(barW, bh),
                        CornerRadius(barW / 2),
                    )
                }
            }
            // Coin
            val coinCenter = Offset(cx + pw / 2 + w * 0.06f, h * 0.3f + sin(t + 3f) * 7.dp.toPx())
            val r = w * 0.075f
            drawCircle(Brush.linearGradient(listOf(Color(0xFFFFD166), Color(0xFFFF9A4D)), coinCenter - Offset(r, r), coinCenter + Offset(r, r)), r, coinCenter)
            drawText(coinText, topLeft = coinCenter - Offset(coinText.size.width / 2f, coinText.size.height / 2f))
            // Sparkles
            listOf(Offset(w * 0.18f, h * 0.2f) to 0f, Offset(w * 0.84f, h * 0.7f) to 2f, Offset(w * 0.7f, h * 0.1f) to 4f).forEach { (p, phase) ->
                val s = (6 + 3 * sin(t * 2 + phase)).dp.toPx()
                drawLine(colors.brand[2], p - Offset(s, 0f), p + Offset(s, 0f), 2.dp.toPx(), StrokeCap.Round)
                drawLine(colors.brand[2], p - Offset(0f, s), p + Offset(0f, s), 2.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}
