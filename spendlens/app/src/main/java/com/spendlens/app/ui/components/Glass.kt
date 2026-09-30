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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.spendlens.app.ui.theme.Ink
import com.spendlens.app.ui.theme.LocalInk
import com.spendlens.app.ui.theme.Spend

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

/** Soft pools of light behind the page — what the glass panels frost over. */
fun Modifier.backdrop(level: Float, ink: Ink): Modifier = drawBehind {
    drawRect(ink.canvas)
    if (level <= 0.01f) return@drawBehind
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

/** Opaque page with the backdrop; every screen sits in one so transitions never show two pages at once. */
@Composable
fun Screen(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().backdrop(LocalGlass.current, Spend.ink), content = content)
}
