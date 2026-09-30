package com.spendlens.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.spendlens.app.R

/**
 * Monochrome, editorial palette: near-black canvas, hairlines, one ink colour,
 * greys for hierarchy. Colour is reserved for "you went over budget".
 */
@Immutable
data class Ink(
    val canvas: Color,
    val surface: Color,
    val raised: Color,
    val line: Color,
    val lineStrong: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val ghost: Color,
    val inverse: Color,
    val alert: Color,
    val isDark: Boolean,
) {
    /** Grey ramp for charts & heatmaps: 0 = empty, 1 = ink. */
    fun ramp(t: Float): Color = lerp(ghost, text, t.coerceIn(0f, 1f))
}

private val Dark = Ink(
    canvas = Color(0xFF0A0A0A),
    surface = Color(0xFF111111),
    raised = Color(0xFF181818),
    line = Color(0xFF232323),
    lineStrong = Color(0xFF3A3A3A),
    text = Color(0xFFEDEDEA),
    muted = Color(0xFF8A8A87),
    faint = Color(0xFF55554F),
    ghost = Color(0xFF1C1C1C),
    inverse = Color(0xFF0A0A0A),
    alert = Color(0xFFE5534B),
    isDark = true,
)

private val Light = Ink(
    canvas = Color(0xFFF1F0EC),
    surface = Color(0xFFF7F6F3),
    raised = Color(0xFFFFFFFF),
    line = Color(0xFFDCDAD4),
    lineStrong = Color(0xFFB9B7B0),
    text = Color(0xFF0B0B0B),
    muted = Color(0xFF6B6A66),
    faint = Color(0xFF9E9C96),
    ghost = Color(0xFFE4E2DC),
    inverse = Color(0xFFF1F0EC),
    alert = Color(0xFFC4332B),
    isDark = false,
)

val InterTight = FontFamily(
    Font(R.font.inter_tight, FontWeight.Light),
    Font(R.font.inter_tight, FontWeight.Normal),
    Font(R.font.inter_tight, FontWeight.Medium),
    Font(R.font.inter_tight, FontWeight.SemiBold),
)

private fun type(size: Int, weight: FontWeight, line: Int, tracking: Double) = TextStyle(
    fontFamily = InterTight,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

private val AppType = Typography(
    // Big uppercase statements
    displayLarge = type(64, FontWeight.Normal, 60, -0.04),
    displayMedium = type(48, FontWeight.Normal, 46, -0.035),
    displaySmall = type(36, FontWeight.Normal, 36, -0.03),
    headlineLarge = type(30, FontWeight.Normal, 31, -0.025),
    headlineMedium = type(24, FontWeight.Normal, 26, -0.02),
    headlineSmall = type(20, FontWeight.Medium, 23, -0.015),
    titleLarge = type(18, FontWeight.Medium, 22, -0.01),
    titleMedium = type(15, FontWeight.Medium, 19, -0.005),
    titleSmall = type(13, FontWeight.Medium, 17, 0.0),
    bodyLarge = type(15, FontWeight.Normal, 21, 0.0),
    bodyMedium = type(13, FontWeight.Normal, 18, 0.0),
    bodySmall = type(11, FontWeight.Normal, 15, 0.01),
    // Tiny uppercase labels, "[ BUTTONS ]"
    labelLarge = type(12, FontWeight.Medium, 14, 0.06),
    labelMedium = type(10, FontWeight.Medium, 12, 0.08),
    labelSmall = type(9, FontWeight.Medium, 11, 0.1),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

val LocalInk = staticCompositionLocalOf { Dark }

object Spend {
    val ink: Ink
        @Composable get() = LocalInk.current
}

@Composable
fun SpendLensTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val ink = if (darkTheme) Dark else Light
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = ink.text, onPrimary = ink.inverse,
            secondary = ink.muted, onSecondary = ink.inverse,
            background = ink.canvas, onBackground = ink.text,
            surface = ink.canvas, onSurface = ink.text,
            surfaceVariant = ink.surface, onSurfaceVariant = ink.muted,
            surfaceContainerLowest = ink.canvas, surfaceContainerLow = ink.surface,
            surfaceContainer = ink.surface, surfaceContainerHigh = ink.raised,
            surfaceContainerHighest = ink.raised,
            outline = ink.lineStrong, outlineVariant = ink.line, error = ink.alert,
        )
    } else {
        lightColorScheme(
            primary = ink.text, onPrimary = ink.inverse,
            secondary = ink.muted, onSecondary = ink.inverse,
            background = ink.canvas, onBackground = ink.text,
            surface = ink.canvas, onSurface = ink.text,
            surfaceVariant = ink.surface, onSurfaceVariant = ink.muted,
            surfaceContainerLowest = ink.raised, surfaceContainerLow = ink.surface,
            surfaceContainer = ink.surface, surfaceContainerHigh = ink.raised,
            surfaceContainerHighest = ink.raised,
            outline = ink.lineStrong, outlineVariant = ink.line, error = ink.alert,
        )
    }
    CompositionLocalProvider(LocalInk provides ink) {
        MaterialTheme(colorScheme = scheme, typography = AppType, shapes = AppShapes, content = content)
    }
}
