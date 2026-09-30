package com.spendlens.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.spendlens.app.ui.theme.AuroraGlow
import com.spendlens.app.ui.theme.Ink
import com.spendlens.app.ui.theme.LocalInk
import com.spendlens.app.ui.theme.Spend
import com.spendlens.app.ui.theme.Style

/** Glass strength from Settings: 0 = flat editorial print, 1 = full frosted glass. */
val LocalGlass = compositionLocalOf { 0.55f }

val GlassShape = RoundedCornerShape(10.dp)

/** Frosted panel: a faint milky fill, a specular edge that catches light at the top-left. */
fun Modifier.glass(shape: Shape = GlassShape): Modifier = composed {
    val level = LocalGlass.current
    val ink = LocalInk.current
    if (level <= 0.01f) this else glassWith(level, ink, shape)
}

fun Modifier.glassWith(level: Float, ink: Ink, shape: Shape = GlassShape): Modifier {
    val (top, bottom) = if (ink.isDark) {
        Color.White.copy(alpha = 0.085f * level) to Color.White.copy(alpha = 0.025f * level)
    } else {
        Color.White.copy(alpha = 0.78f * level) to Color.White.copy(alpha = 0.42f * level)
    }
    val edge = if (ink.isDark) {
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.26f * level), Color.White.copy(alpha = 0.05f * level), Color.White.copy(alpha = 0.12f * level)))
    } else {
        Brush.linearGradient(listOf(Color.White.copy(alpha = level), ink.line.copy(alpha = 0.6f), Color.White.copy(alpha = 0.7f * level)))
    }
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(top, bottom)))
        .border(1.dp, edge, shape)
}

/** Soft pools of light behind the page — what the glass panels frost over — plus the look's own texture. */
fun Modifier.backdrop(level: Float, ink: Ink, style: Style = Style.EDITORIAL): Modifier = drawBehind {
    drawRect(ink.canvas)
    texture(style, ink)
    if (level <= 0.01f || style == Style.AURORA) return@drawBehind
    val pool = if (ink.isDark) Color(0xFF4A4A48) else Color(0xFFD6D3CA)
    val strength = if (ink.isDark) 0.55f * level else 0.8f * level
    listOf(
        Offset(size.width * 0.08f, size.height * 0.06f) to size.width * 0.85f,
        Offset(size.width * 1.02f, size.height * 0.42f) to size.width * 0.7f,
        Offset(size.width * 0.12f, size.height * 0.9f) to size.width * 0.75f,
    ).forEach { (center, radius) ->
        drawCircle(Brush.radialGradient(listOf(pool.copy(alpha = strength), Color.Transparent), center, radius), radius, center)
    }
}

/** Scanlines, aurora glow, riso grain or a drafting grid, drawn over the canvas colour. */
fun DrawScope.texture(style: Style, ink: Ink) {
    val w = size.width
    val h = size.height
    when (style) {
        Style.TERMINAL -> {
            drawCircle(Brush.radialGradient(listOf(ink.text.copy(alpha = if (ink.isDark) 0.10f else 0.06f), Color.Transparent), Offset(w * 0.3f, 0f), w), w, Offset(w * 0.3f, 0f))
            val step = 3.dp.toPx()
            val line = if (ink.isDark) Color.Black.copy(alpha = 0.28f) else ink.text.copy(alpha = 0.035f)
            var y = 0f
            while (y < h) {
                drawRect(line, Offset(0f, y), Size(w, 1.dp.toPx()))
                y += step
            }
        }
        Style.AURORA -> {
            val a = if (ink.isDark) 1f else 0.55f
            listOf(
                Triple(ink.accent, Offset(w * 0.05f, h * 0.05f), 0.55f),
                Triple(AuroraGlow[0], Offset(w * 1.05f, h * 0.35f), 0.35f),
                Triple(AuroraGlow[1], Offset(w * 0.3f, h * 0.85f), 0.30f),
            ).forEach { (color, center, alpha) ->
                val r = w * 0.9f
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha * a), Color.Transparent), center, r), r, center)
            }
        }
        Style.RISO -> {
            val step = 6.dp.toPx()
            val dot = (if (ink.isDark) Color.White else Color.Black).copy(alpha = 0.07f)
            val r = 0.6.dp.toPx()
            var y = step / 2
            while (y < h) {
                var x = step / 2
                while (x < w) {
                    drawCircle(dot, r, Offset(x, y))
                    x += step
                }
                y += step
            }
        }
        Style.BLUEPRINT -> {
            val minor = 20.dp.toPx()
            var i = 0
            var x = 0f
            while (x < w) {
                drawRect(ink.text.copy(alpha = if (i % 5 == 0) 0.14f else 0.06f), Offset(x, 0f), Size(1f, h))
                x += minor
                i++
            }
            i = 0
            var y = 0f
            while (y < h) {
                drawRect(ink.text.copy(alpha = if (i % 5 == 0) 0.14f else 0.06f), Offset(0f, y), Size(w, 1f))
                y += minor
                i++
            }
        }
        else -> Unit
    }
}

/** Opaque page with the backdrop; every screen sits in one so transitions never show two pages at once. */
@Composable
fun Screen(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().backdrop(LocalGlass.current, Spend.ink, Spend.look.style), content = content)
}
