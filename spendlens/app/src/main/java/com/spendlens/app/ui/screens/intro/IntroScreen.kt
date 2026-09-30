package com.spendlens.app.ui.screens.intro

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spendlens.app.domain.CurrencyOption
import com.spendlens.app.ui.components.AmountText
import com.spendlens.app.ui.components.BracketButton
import com.spendlens.app.ui.components.Label
import com.spendlens.app.ui.components.LocalCurrency
import com.spendlens.app.ui.components.MotionSettings
import com.spendlens.app.ui.components.Screen
import com.spendlens.app.ui.components.Statement
import com.spendlens.app.ui.components.caps
import com.spendlens.app.ui.components.numberStyle
import com.spendlens.app.ui.screens.lock.LensMark
import com.spendlens.app.ui.theme.Spend
import com.spendlens.app.ui.theme.SpendLensTheme
import com.spendlens.app.ui.theme.Style
import kotlin.math.min
import kotlin.math.pow

/** Length of the whole piece, in milliseconds. */
const val INTRO_MS = 12_000f

/**
 * A short motion piece shown on first launch (and from Settings): the lens draws itself, a payment
 * screenshot is scanned into figures, the figures become a chart, the looks flip by, and a lock
 * closes on "private by design". Skippable at any point.
 */
@Composable
fun IntroScreen(onDone: () -> Unit) {
    val time = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (MotionSettings.loops) time.animateTo(INTRO_MS, tween(INTRO_MS.toInt(), easing = LinearEasing)) else time.snapTo(INTRO_MS)
    }
    BackHandler(onBack = onDone)
    IntroContent(time.value, onDone)
}

// Scene windows (ms).
private const val LOGO_END = 2_400f
private const val SCAN_START = 2_200f
private const val SCAN_END = 5_000f
private const val CHART_START = 4_800f
private const val CHART_END = 7_600f
private const val LOOKS_START = 7_400f
private const val LOOKS_END = 9_800f
private const val PRIVATE_START = 9_600f

private fun p(t: Float, a: Float, b: Float): Float = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun easeOut(x: Float): Float = 1f - (1f - x).pow(3)
private fun window(t: Float, a: Float, b: Float, fade: Float = 350f, last: Boolean = false): Float =
    min(p(t, a, a + fade), if (last) 1f else 1f - p(t, b - fade, b))

@Composable
fun IntroContent(t: Float, onDone: () -> Unit) {
    val colors = Spend.ink
    Screen {
        Scene(window(t, 0f, LOGO_END)) { LogoScene(t) }
        Scene(window(t, SCAN_START, SCAN_END)) { ScanScene(t) }
        Scene(window(t, CHART_START, CHART_END)) { ChartScene(t) }
        Scene(window(t, LOOKS_START, LOOKS_END)) { LooksScene(t) }
        Scene(window(t, PRIVATE_START, INTRO_MS, last = true)) { PrivateScene(t, onDone) }

        // Progress along the top, skip in the corner.
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(colors.line)) {
                Box(Modifier.fillMaxWidth(p(t, 0f, INTRO_MS)).height(2.dp).background(colors.accent))
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Label("SpendLens", color = colors.muted, modifier = Modifier.weight(1f))
                if (t < INTRO_MS - 1_500f) BracketButton("Skip", onClick = onDone, color = colors.muted)
            }
        }
    }
}

@Composable
private fun BoxScope.Scene(alpha: Float, content: @Composable BoxScope.() -> Unit) {
    if (alpha <= 0f) return
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 70.dp, bottom = 32.dp),
        content = content,
    )
}

// ---------------------------------------------------------------- 1. the lens draws itself

@Composable
private fun BoxScope.LogoScene(t: Float) {
    val colors = Spend.ink
    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
        LensMark(Modifier.size(120.dp), progress = easeOut(p(t, 100f, 1_300f)))
        Spacer(Modifier.height(22.dp))
        Row {
            "spendlens".forEachIndexed { i, ch ->
                val k = easeOut(p(t, 600f + i * 60f, 950f + i * 60f))
                Text(
                    ch.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.text,
                    modifier = Modifier.graphicsLayer {
                        alpha = k
                        translationY = (1f - k) * 28.dp.toPx()
                    },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Label(
            "Every payment, understood",
            color = colors.muted,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.graphicsLayer { alpha = p(t, 1_300f, 1_700f) },
        )
    }
}

// ---------------------------------------------------------------- 2. a screenshot is read

@Composable
private fun BoxScope.ScanScene(t: Float) {
    val colors = Spend.ink
    val currency = LocalCurrency.current
    val scan = p(t, 2_700f, 3_900f)
    Column(Modifier.fillMaxSize()) {
        Statement("Screenshots ", "in.", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The "screenshot".
            Box(
                Modifier
                    .width(170.dp)
                    .height(300.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.surface)
                    .padding(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(34.dp).background(colors.ghost, RoundedCornerShape(10.dp)))
                    Spacer(Modifier.height(8.dp))
                    Text("Paid to", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                    Text("Swiggy", style = MaterialTheme.typography.titleLarge, color = colors.text)
                    Text(currency.format(486_00), style = numberStyle(MaterialTheme.typography.headlineLarge), color = colors.text)
                    Text("Google Pay · 9:05 PM", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                    Spacer(Modifier.height(6.dp))
                    repeat(3) { Box(Modifier.fillMaxWidth(0.9f - it * 0.2f).height(6.dp).background(colors.ghost, RoundedCornerShape(3.dp))) }
                }
                // Scan line with a soft trail.
                if (scan in 0.001f..0.999f) {
                    Canvas(Modifier.matchParentSizeCompat()) {
                        val y = size.height * scan
                        drawRect(
                            Brush.verticalGradient(listOf(colors.accent.copy(alpha = 0f), colors.accent.copy(alpha = 0.22f)), startY = y - 70.dp.toPx(), endY = y),
                            topLeft = Offset(-16.dp.toPx(), y - 70.dp.toPx()),
                            size = Size(size.width + 32.dp.toPx(), 70.dp.toPx()),
                        )
                        drawLine(colors.accent, Offset(-16.dp.toPx(), y), Offset(size.width + 16.dp.toPx(), y), 2.dp.toPx())
                    }
                }
            }
            Spacer(Modifier.width(18.dp))
            // What was read, flying out one by one.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("Amount" to currency.format(486_00), "Paid to" to "Swiggy", "Category" to "Food", "When" to "9:05 PM").forEachIndexed { i, (k, v) ->
                    val a = easeOut(p(t, 3_300f + i * 220f, 3_750f + i * 220f))
                    Column(
                        Modifier
                            .graphicsLayer {
                                alpha = a
                                translationX = (1f - a) * -60.dp.toPx()
                            }
                            .background(colors.surface, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Label(k, color = colors.faint)
                        Text(v, style = MaterialTheme.typography.titleMedium, color = colors.text)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Share a payment screenshot, pick a few, or let it read your bank SMS.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.muted,
            modifier = Modifier.graphicsLayer { alpha = p(t, 3_900f, 4_300f) },
        )
    }
}

/** matchParentSize without needing the BoxScope receiver inside nested lambdas. */
private fun Modifier.matchParentSizeCompat(): Modifier = this.fillMaxSize()

// ---------------------------------------------------------------- 3. figures become a picture

private val BarShape = listOf(0.35f, 0.2f, 0.5f, 0.3f, 0.65f, 0.25f, 0.4f, 0.95f, 0.3f, 0.45f, 0.28f, 0.6f, 0.38f, 0.72f)

@Composable
private fun BoxScope.ChartScene(t: Float) {
    val colors = Spend.ink
    val currency = LocalCurrency.current
    val roll = easeOut(p(t, 5_100f, 6_500f))
    Column(Modifier.fillMaxSize()) {
        Statement("Clarity ", "out.", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(24.dp))
        Label("Spent this month", color = colors.muted)
        AmountText((95_232_00L * roll).toLong() / 100 * 100, currency, MaterialTheme.typography.displayLarge)
        Label(
            "At this pace ≈ ${currency.compact(95_500_00)} by month end",
            color = colors.text,
            modifier = Modifier.graphicsLayer { alpha = p(t, 6_200f, 6_600f) },
        )
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            BarShape.forEachIndexed { i, h ->
                val g = easeOut(p(t, 5_200f + i * 70f, 5_900f + i * 70f))
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight((h * g).coerceAtLeast(0.01f))
                        .background(if (i == 7) colors.accent else colors.muted, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        val chips = listOf("Forecasts", "Double-charge alerts", "Streaks", "Wrapped")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.take(2).forEachIndexed { i, c -> Chip(c, p(t, 6_400f + i * 180f, 6_800f + i * 180f)) }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.drop(2).forEachIndexed { i, c -> Chip(c, p(t, 6_760f + i * 180f, 7_160f + i * 180f)) }
        }
    }
}

@Composable
private fun Chip(text: String, a: Float) {
    val colors = Spend.ink
    Text(
        caps(text),
        style = MaterialTheme.typography.labelLarge,
        color = colors.text,
        modifier = Modifier
            .graphicsLayer {
                alpha = a
                scaleX = 0.8f + 0.2f * easeOut(a)
                scaleY = 0.8f + 0.2f * easeOut(a)
            }
            .background(colors.ghost, RoundedCornerShape(99.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

// ---------------------------------------------------------------- 4. the looks flip by

private val ShownLooks = listOf(Style.RECEIPT, Style.BENTO, Style.DOT, Style.BRUTAL)

@Composable
private fun BoxScope.LooksScene(t: Float) {
    val colors = Spend.ink
    val dark = colors.isDark
    Column(Modifier.fillMaxSize()) {
        Statement("Eight ", "looks.", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(6.dp))
        Label("Any accent colour", color = colors.muted, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(24.dp))
        ShownLooks.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEachIndexed { col, style ->
                    val i = row * 2 + col
                    val k = easeOut(p(t, 7_600f + i * 220f, 8_150f + i * 220f))
                    Box(
                        Modifier
                            .weight(1f)
                            .graphicsLayer {
                                alpha = k
                                rotationY = (1f - k) * 70f
                                cameraDistance = 12f * density
                            },
                    ) {
                        SpendLensTheme(darkTheme = dark, style = style) { LookCard(style) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun LookCard(style: Style) {
    val ink = Spend.ink
    Column(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(ink.canvas, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Text(caps(style.label), style = MaterialTheme.typography.labelLarge, color = ink.text)
        Spacer(Modifier.weight(1f))
        Text("₹95,232", style = numberStyle(MaterialTheme.typography.headlineMedium), color = ink.text, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            listOf(0.4f, 0.7f, 0.5f, 1f, 0.6f).forEachIndexed { i, h ->
                Box(Modifier.width(10.dp).height((22 * h).dp).background(if (i == 3) ink.accent else ink.muted, RoundedCornerShape(2.dp)))
            }
        }
    }
}

// ---------------------------------------------------------------- 5. private by design

@Composable
private fun BoxScope.PrivateScene(t: Float, onDone: () -> Unit) {
    val colors = Spend.ink
    val close = easeOut(p(t, 10_000f, 10_600f))
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Canvas(Modifier.size(110.dp)) {
            val w = size.width
            val body = Size(w * 0.62f, w * 0.46f)
            val bodyTop = Offset((w - body.width) / 2, w * 0.46f)
            // Shackle drops into the body as it closes.
            val lift = (1f - close) * w * 0.16f
            val r = body.width * 0.3f
            drawArc(
                colors.text, 180f, 180f, false,
                Offset(w / 2 - r, bodyTop.y - r * 1.5f - lift), Size(r * 2, r * 2),
                style = Stroke(w * 0.07f, cap = StrokeCap.Round),
            )
            drawLine(colors.text, Offset(w / 2 - r, bodyTop.y - r * 0.5f - lift), Offset(w / 2 - r, bodyTop.y + 4f), w * 0.07f)
            drawLine(colors.text, Offset(w / 2 + r, bodyTop.y - r * 0.5f - lift), Offset(w / 2 + r, bodyTop.y + 4f - (1f - close) * w * 0.12f), w * 0.07f)
            drawRoundRect(colors.accent, bodyTop, body, androidx.compose.ui.geometry.CornerRadius(w * 0.08f))
            drawCircle(colors.onAccent, w * 0.05f, Offset(w / 2, bodyTop.y + body.height * 0.45f))
        }
        Spacer(Modifier.height(28.dp))
        Statement("Private ", "by design.", style = MaterialTheme.typography.displaySmall, modifier = Modifier.graphicsLayer { alpha = p(t, 10_200f, 10_600f) })
        Spacer(Modifier.height(12.dp))
        Text(
            "Screenshots and bank SMS are read on this phone. Nothing is uploaded. Lock it with a PIN or your fingerprint.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { alpha = p(t, 10_500f, 10_900f) },
        )
        Spacer(Modifier.weight(1f))
        BracketButton(
            "Get started",
            onClick = onDone,
            filled = true,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = p(t, 10_900f, 11_300f)
                    translationY = (1f - easeOut(p(t, 10_900f, 11_300f))) * 24.dp.toPx()
                },
        )
    }
}

@Suppress("unused")
private val PreviewCurrency = CurrencyOption.INR
