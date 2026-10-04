package com.urbanlens.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object UrbanColors {
    val Ink = Color(0xFF15120F)
    val Surface = Color(0xFF1F1B17)
    val SurfaceHigh = Color(0xFF2A241F)
    val Outline = Color(0xFF3A322B)
    val Paper = Color(0xFFF3EDE6)
    val Muted = Color(0xFFA79C91)
    val Orange = Color(0xFFF07F2B)
    val Teal = Color(0xFF2BA389)
    val Sand = Color(0xFFF4E3D3)
    val Red = Color(0xFFE5484D)

    /** Parses "#RRGGBB" from the core module's palette. */
    fun hex(value: String): Color = Color(android.graphics.Color.parseColor(value))
}

private val colors = darkColorScheme(
    primary = UrbanColors.Orange,
    onPrimary = UrbanColors.Ink,
    secondary = UrbanColors.Teal,
    onSecondary = UrbanColors.Ink,
    tertiary = UrbanColors.Sand,
    background = UrbanColors.Ink,
    onBackground = UrbanColors.Paper,
    surface = UrbanColors.Surface,
    onSurface = UrbanColors.Paper,
    surfaceVariant = UrbanColors.SurfaceHigh,
    onSurfaceVariant = UrbanColors.Muted,
    surfaceContainer = UrbanColors.Surface,
    surfaceContainerHigh = UrbanColors.SurfaceHigh,
    surfaceContainerLow = UrbanColors.Surface,
    inverseSurface = UrbanColors.SurfaceHigh,
    inverseOnSurface = UrbanColors.Paper,
    inversePrimary = UrbanColors.Orange,
    outline = UrbanColors.Outline,
    outlineVariant = UrbanColors.Outline,
    error = UrbanColors.Red,
)

private val serif = FontFamily.Serif

private val typography = Typography(
    headlineMedium = TextStyle(fontFamily = serif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = serif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.6.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp),
)

@Composable
fun UrbanLensTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
