package com.spendlens.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendlens.app.R

// ---------------------------------------------------------------- palette

val Violet = Color(0xFF7B5CFF)
val VioletDeep = Color(0xFF5B3DF5)
val Magenta = Color(0xFFE94BA9)
val Tangerine = Color(0xFFFF9A4D)
val Mint = Color(0xFF2FD39A)
val Coral = Color(0xFFFF6B6B)
val Amber = Color(0xFFFFC23D)

/** Colors Material's scheme doesn't have slots for. */
@Immutable
data class SpendColors(
    val brand: List<Color>,
    val heroStart: Color,
    val heroMid: Color,
    val heroEnd: Color,
    val card: Color,
    val cardBorder: Color,
    val subtle: Color,
    val textMuted: Color,
    val textFaint: Color,
    val positive: Color,
    val negative: Color,
    val warning: Color,
    val chartTrack: Color,
    val heatEmpty: Color,
    val isDark: Boolean,
) {
    val brandBrush: Brush get() = Brush.linearGradient(brand)
    val heroBrush: Brush get() = Brush.linearGradient(listOf(heroStart, heroMid, heroEnd))
}

private val DarkSpend = SpendColors(
    brand = listOf(Violet, Magenta, Tangerine),
    heroStart = Color(0xFF5B3DF5),
    heroMid = Color(0xFFB03FD0),
    heroEnd = Color(0xFFFF7A59),
    card = Color(0xFF16141F),
    cardBorder = Color(0xFF262236),
    subtle = Color(0xFF1E1B2B),
    textMuted = Color(0xFFA7A2C4),
    textFaint = Color(0xFF6B6690),
    positive = Mint,
    negative = Coral,
    warning = Amber,
    chartTrack = Color(0xFF242035),
    heatEmpty = Color(0xFF1F1C2C),
    isDark = true,
)

private val LightSpend = SpendColors(
    brand = listOf(VioletDeep, Magenta, Tangerine),
    heroStart = Color(0xFF6344FF),
    heroMid = Color(0xFFC04BC8),
    heroEnd = Color(0xFFFF8A55),
    card = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFECE9F6),
    subtle = Color(0xFFF1EFF8),
    textMuted = Color(0xFF6E6A88),
    textFaint = Color(0xFFA29FB8),
    positive = Color(0xFF12A874),
    negative = Color(0xFFE5484D),
    warning = Color(0xFFE09B00),
    chartTrack = Color(0xFFEDEAF6),
    heatEmpty = Color(0xFFEFEDF6),
    isDark = false,
)

private val DarkScheme = darkColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2C2458),
    onPrimaryContainer = Color(0xFFE3DDFF),
    secondary = Magenta,
    onSecondary = Color.White,
    tertiary = Tangerine,
    background = Color(0xFF0B0A12),
    onBackground = Color(0xFFF4F2FF),
    surface = Color(0xFF0B0A12),
    onSurface = Color(0xFFF4F2FF),
    surfaceVariant = Color(0xFF1E1B2B),
    onSurfaceVariant = Color(0xFFA7A2C4),
    surfaceContainerLowest = Color(0xFF0E0D16),
    surfaceContainerLow = Color(0xFF13111C),
    surfaceContainer = Color(0xFF16141F),
    surfaceContainerHigh = Color(0xFF1E1B2B),
    surfaceContainerHighest = Color(0xFF262236),
    outline = Color(0xFF3A3552),
    outlineVariant = Color(0xFF262236),
    error = Coral,
)

private val LightScheme = lightColorScheme(
    primary = VioletDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E3FF),
    onPrimaryContainer = Color(0xFF22135F),
    secondary = Magenta,
    onSecondary = Color.White,
    tertiary = Tangerine,
    background = Color(0xFFF6F5FB),
    onBackground = Color(0xFF15132A),
    surface = Color(0xFFF6F5FB),
    onSurface = Color(0xFF15132A),
    surfaceVariant = Color(0xFFF1EFF8),
    onSurfaceVariant = Color(0xFF6E6A88),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFAFF),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF1EFF8),
    surfaceContainerHighest = Color(0xFFE8E5F3),
    outline = Color(0xFFD5D1E6),
    outlineVariant = Color(0xFFECE9F6),
    error = Color(0xFFE5484D),
)

// ---------------------------------------------------------------- type

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
    Font(R.font.poppins_extrabold, FontWeight.ExtraBold),
)

private fun style(size: Int, weight: FontWeight, line: Int, spacing: Float = 0f) = TextStyle(
    fontFamily = Poppins,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.sp,
)

private val AppTypography = Typography(
    displayLarge = style(52, FontWeight.ExtraBold, 60, -1.5f),
    displayMedium = style(42, FontWeight.ExtraBold, 50, -1.2f),
    displaySmall = style(34, FontWeight.Bold, 42, -0.8f),
    headlineLarge = style(30, FontWeight.Bold, 38, -0.6f),
    headlineMedium = style(26, FontWeight.Bold, 34, -0.4f),
    headlineSmall = style(22, FontWeight.SemiBold, 30, -0.2f),
    titleLarge = style(20, FontWeight.SemiBold, 28),
    titleMedium = style(16, FontWeight.SemiBold, 24),
    titleSmall = style(14, FontWeight.SemiBold, 20),
    bodyLarge = style(16, FontWeight.Normal, 24),
    bodyMedium = style(14, FontWeight.Normal, 20),
    bodySmall = style(12, FontWeight.Normal, 17),
    labelLarge = style(14, FontWeight.SemiBold, 20),
    labelMedium = style(12, FontWeight.Medium, 16, 0.2f),
    labelSmall = style(11, FontWeight.Medium, 14, 0.4f),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val LocalSpendColors = staticCompositionLocalOf { DarkSpend }

object SpendTheme {
    val colors: SpendColors
        @Composable get() = LocalSpendColors.current
}

@Composable
fun SpendLensTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSpendColors provides if (darkTheme) DarkSpend else LightSpend) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
