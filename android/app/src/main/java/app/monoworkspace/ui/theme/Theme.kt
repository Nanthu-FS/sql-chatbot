package app.monoworkspace.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.monoworkspace.R

/** Mono Swiss palette. No other hues anywhere; dynamic color is never used. */
object MonoColors {
    val Background = Color(0xFFFFFFFF)
    val Ink = Color(0xFF000000)
    val Secondary = Color(0xFF6B6B6B)
    val Tertiary = Color(0xFF9A9A9A)
    val Hairline = Color(0xFFE0E0E0)
    val Rule = Ink
    val Tint = Color(0xFFF2F2F2)
    val Destructive = Color(0xFFD0021B)
    val Scrim = Color(0x52000000)
    val White = Background
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** The single type scale. Labels are uppercased by the components that use them. */
object MonoType {
    val display = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 52.sp, letterSpacing = (-0.01).em, color = MonoColors.Ink)
    val h1 = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.005).em, color = MonoColors.Ink)
    val h2 = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, color = MonoColors.Ink)
    val h3 = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, color = MonoColors.Ink)
    val body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.4.sp, color = MonoColors.Ink)
    val bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, color = MonoColors.Ink)
    val label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.05.em, color = MonoColors.Ink)
    val caption = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, color = MonoColors.Secondary)
    val code = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, color = MonoColors.Ink)
}

/** Tabular figures for numbers that line up. */
fun TextStyle.tnum(): TextStyle = copy(fontFeatureSettings = "tnum")

object Space {
    val xs: Dp = 4.dp
    val s: Dp = 8.dp
    val m: Dp = 12.dp
    val l: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val x3: Dp = 48.dp
    val x4: Dp = 64.dp
}

object Motion {
    const val FAST = 150
    const val MEDIUM = 300
    const val SLOW = 400
}

/** True when the system "Remove animations" setting is on. */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
@ReadOnlyComposable
fun motionMs(ms: Int): Int = if (LocalReduceMotion.current) 0 else ms

@Composable
fun <T> monoTween(ms: Int = Motion.FAST): TweenSpec<T> = tween(durationMillis = motionMs(ms), easing = FastOutSlowInEasing)

private val MonoColorScheme = lightColorScheme(
    primary = MonoColors.Ink,
    onPrimary = MonoColors.White,
    primaryContainer = MonoColors.Tint,
    onPrimaryContainer = MonoColors.Ink,
    inversePrimary = MonoColors.White,
    secondary = MonoColors.Secondary,
    onSecondary = MonoColors.White,
    secondaryContainer = MonoColors.Tint,
    onSecondaryContainer = MonoColors.Ink,
    tertiary = MonoColors.Tertiary,
    onTertiary = MonoColors.White,
    tertiaryContainer = MonoColors.Tint,
    onTertiaryContainer = MonoColors.Ink,
    background = MonoColors.Background,
    onBackground = MonoColors.Ink,
    surface = MonoColors.Background,
    onSurface = MonoColors.Ink,
    surfaceVariant = MonoColors.Tint,
    onSurfaceVariant = MonoColors.Secondary,
    surfaceTint = MonoColors.Background,
    inverseSurface = MonoColors.Ink,
    inverseOnSurface = MonoColors.White,
    error = MonoColors.Destructive,
    onError = MonoColors.White,
    errorContainer = MonoColors.White,
    onErrorContainer = MonoColors.Destructive,
    outline = MonoColors.Ink,
    outlineVariant = MonoColors.Hairline,
    scrim = MonoColors.Scrim,
    surfaceBright = MonoColors.Background,
    surfaceDim = MonoColors.Background,
    surfaceContainer = MonoColors.Background,
    surfaceContainerHigh = MonoColors.Background,
    surfaceContainerHighest = MonoColors.Background,
    surfaceContainerLow = MonoColors.Background,
    surfaceContainerLowest = MonoColors.Background,
)

private val MonoTypography = Typography(
    displayLarge = MonoType.display,
    displayMedium = MonoType.display,
    displaySmall = MonoType.h1,
    headlineLarge = MonoType.h1,
    headlineMedium = MonoType.h2,
    headlineSmall = MonoType.h3,
    titleLarge = MonoType.h3,
    titleMedium = MonoType.body.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = MonoType.bodySmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = MonoType.body,
    bodyMedium = MonoType.bodySmall,
    bodySmall = MonoType.caption,
    labelLarge = MonoType.label,
    labelMedium = MonoType.label,
    labelSmall = MonoType.caption,
)

private val SquareShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

@Composable
fun MonoTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val selection = TextSelectionColors(handleColor = MonoColors.Ink, backgroundColor = MonoColors.Ink.copy(alpha = 0.18f))
    MaterialTheme(colorScheme = MonoColorScheme, typography = MonoTypography, shapes = SquareShapes) {
        CompositionLocalProvider(
            LocalReduceMotion provides reduceMotion,
            LocalRippleConfiguration provides RippleConfiguration(
                color = MonoColors.Ink,
                rippleAlpha = RippleAlpha(0.08f, 0.08f, 0.08f, 0.08f),
            ),
            LocalTextSelectionColors provides selection,
            content = content,
        )
    }
}
