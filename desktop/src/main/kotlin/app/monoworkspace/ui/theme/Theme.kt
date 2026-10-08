package app.monoworkspace.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The colors of one surface (main pane, sidebar, card or highlighted tile).
 * Components read the surface they sit on through [MonoColors], so the same
 * component adapts when it is placed inside a differently colored surface.
 */
@Immutable
data class SurfaceColors(
    val background: Color,
    val ink: Color,
    val secondary: Color,
    val tertiary: Color,
    val hairline: Color,
    val rule: Color,
    val tint: Color,
    val accent: Color,
    val onAccent: Color,
    val onInk: Color,
    val destructive: Color,
    val scrim: Color,
)

val LocalSurface = staticCompositionLocalOf { Themes.Mono.main }
val LocalTheme = staticCompositionLocalOf { Themes.Mono }

/** Colors of the surface currently being composed. */
object MonoColors {
    val Background: Color @Composable @ReadOnlyComposable get() = LocalSurface.current.background
    val Ink: Color @Composable @ReadOnlyComposable get() = LocalSurface.current.ink
    val Secondary: Color @Composable @ReadOnlyComposable get() = LocalSurface.current.secondary
    val Tertiary: Color @Composable @ReadOnlyComposable get() = LocalSurface.current.tertiary
    val Hairline: Color @Composable @ReadOnlyComposable get() = LocalSurface.current.hairline
    val Rule: Color @Composable @ReadOnlyComposable get() = LocalSurface.current.rule
    val Tint: Color @Composable @ReadOnlyComposable get() = LocalSurface.current.tint
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalSurface.current.accent
    val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalSurface.current.onAccent

    /** Text and icons drawn on an ink-filled shape. */
    val OnInk: Color @Composable @ReadOnlyComposable get() = LocalSurface.current.onInk
    val Destructive: Color @Composable @ReadOnlyComposable get() = LocalSurface.current.destructive
    val Scrim: Color @Composable @ReadOnlyComposable get() = LocalSurface.current.scrim
}

/** Font files bundled in resources/; each family loads lazily the first time a theme uses it. */
internal object BundledFonts {
    private fun font(file: String, weight: FontWeight, style: FontStyle = FontStyle.Normal) = Font(
        identity = "$file-${style}",
        data = BundledFonts::class.java.classLoader.getResourceAsStream(file)!!.use { it.readBytes() },
        weight = weight,
        style = style,
    )

    val Inter by lazy {
        FontFamily(
            font("inter_regular.ttf", FontWeight.Normal),
            font("inter_semibold.ttf", FontWeight.SemiBold),
            font("inter_bold.ttf", FontWeight.Bold),
        )
    }
    val Hanken by lazy {
        FontFamily(
            font("fonts/hanken_grotesk_400.ttf", FontWeight.Normal),
            font("fonts/hanken_grotesk_600.ttf", FontWeight.SemiBold),
            font("fonts/hanken_grotesk_700.ttf", FontWeight.Bold),
        )
    }
    val Newsreader by lazy {
        FontFamily(
            font("fonts/newsreader_400.ttf", FontWeight.Normal),
            font("fonts/newsreader_600.ttf", FontWeight.SemiBold),
        )
    }
    val Nunito by lazy {
        FontFamily(
            font("fonts/nunito_400.ttf", FontWeight.Normal),
            font("fonts/nunito_600.ttf", FontWeight.SemiBold),
            font("fonts/nunito_700.ttf", FontWeight.Bold),
            font("fonts/nunito_800.ttf", FontWeight.ExtraBold),
        )
    }
    val Cormorant by lazy {
        FontFamily(
            font("fonts/cormorant_garamond_300.ttf", FontWeight.Light),
            font("fonts/cormorant_garamond_500.ttf", FontWeight.Medium),
            font("fonts/cormorant_garamond_600.ttf", FontWeight.SemiBold),
            font("fonts/cormorant_garamond_700.ttf", FontWeight.Bold),
        )
    }
    val Manrope by lazy {
        FontFamily(
            font("fonts/manrope_400.ttf", FontWeight.Normal),
            font("fonts/manrope_600.ttf", FontWeight.SemiBold),
            font("fonts/manrope_700.ttf", FontWeight.Bold),
        )
    }
}

/** The single type scale, built per theme (fonts) and per surface (ink color). */
@Immutable
class MonoTypeScale(body: FontFamily, display: FontFamily, displayWeight: FontWeight, ink: Color, secondary: Color) {
    val display = TextStyle(fontFamily = display, fontWeight = displayWeight, fontSize = 48.sp, lineHeight = 52.sp, letterSpacing = (-0.01).em, color = ink)
    val h1 = TextStyle(fontFamily = display, fontWeight = if (displayWeight < FontWeight.SemiBold) FontWeight.SemiBold else displayWeight, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.005).em, color = ink)
    val h2 = TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, color = ink)
    val h3 = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, color = ink)
    val body = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.4.sp, color = ink)
    val bodySmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, color = ink)
    val label = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.05.em, color = ink)
    val caption = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, color = secondary)
    val code = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, color = ink)
}

val LocalMonoType = staticCompositionLocalOf<MonoTypeScale> { error("MonoTheme not applied") }

object MonoType {
    val display: TextStyle @Composable @ReadOnlyComposable get() = LocalMonoType.current.display
    val h1: TextStyle @Composable @ReadOnlyComposable get() = LocalMonoType.current.h1
    val h2: TextStyle @Composable @ReadOnlyComposable get() = LocalMonoType.current.h2
    val h3: TextStyle @Composable @ReadOnlyComposable get() = LocalMonoType.current.h3
    val body: TextStyle @Composable @ReadOnlyComposable get() = LocalMonoType.current.body
    val bodySmall: TextStyle @Composable @ReadOnlyComposable get() = LocalMonoType.current.bodySmall
    val label: TextStyle @Composable @ReadOnlyComposable get() = LocalMonoType.current.label
    val caption: TextStyle @Composable @ReadOnlyComposable get() = LocalMonoType.current.caption
    val code: TextStyle @Composable @ReadOnlyComposable get() = LocalMonoType.current.code
}

/** Corner radius of the current theme for cards, tiles, panels and selections. */
object MonoShapes {
    val radius: Dp @Composable @ReadOnlyComposable get() = LocalTheme.current.radius
    val card: Shape @Composable @ReadOnlyComposable get() = RoundedCornerShape(LocalTheme.current.radius)
    val small: Shape @Composable @ReadOnlyComposable get() = RoundedCornerShape(LocalTheme.current.radius * 0.6f)
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

/** True when Windows animations are off or the in-app "Reduce motion" setting is on. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** False when the user turned ambient effects off or the window is in the background. */
val LocalAmbientMotion = staticCompositionLocalOf { true }

@Composable
@ReadOnlyComposable
fun motionMs(ms: Int): Int = if (LocalReduceMotion.current) 0 else ms

@Composable
fun <T> monoTween(ms: Int = Motion.FAST): TweenSpec<T> = tween(durationMillis = motionMs(ms), easing = FastOutSlowInEasing)

/** Places [content] on [surface]: every color, text style and content color inside follows it. */
@Composable
fun ProvideSurface(surface: SurfaceColors, content: @Composable () -> Unit) {
    val theme = LocalTheme.current
    val type = remember(theme.id, surface) { MonoTypeScale(theme.bodyFont, theme.displayFont, theme.displayWeight, surface.ink, surface.secondary) }
    val selection = remember(surface) { TextSelectionColors(handleColor = surface.ink, backgroundColor = surface.ink.copy(alpha = 0.22f)) }
    CompositionLocalProvider(
        LocalSurface provides surface,
        LocalMonoType provides type,
        LocalContentColor provides surface.ink,
        LocalTextSelectionColors provides selection,
        content = content,
    )
}

@Composable
fun SideSurface(content: @Composable () -> Unit) = ProvideSurface(LocalTheme.current.side, content)

@Composable
fun CardSurface(content: @Composable () -> Unit) = ProvideSurface(LocalTheme.current.card, content)

@Composable
fun HotSurface(content: @Composable () -> Unit) = ProvideSurface(LocalTheme.current.hot, content)

private fun colorScheme(t: ThemeSpec) = (if (t.isDark) darkColorScheme() else lightColorScheme()).copy(
    primary = t.main.ink,
    onPrimary = t.main.onInk,
    primaryContainer = t.main.tint,
    onPrimaryContainer = t.main.ink,
    secondary = t.main.secondary,
    onSecondary = t.main.onInk,
    secondaryContainer = t.main.tint,
    onSecondaryContainer = t.main.ink,
    tertiary = t.main.accent,
    onTertiary = t.main.onAccent,
    background = t.main.background,
    onBackground = t.main.ink,
    surface = t.main.background,
    onSurface = t.main.ink,
    surfaceVariant = t.main.tint,
    onSurfaceVariant = t.main.secondary,
    surfaceTint = t.main.background,
    inverseSurface = t.main.ink,
    inverseOnSurface = t.main.onInk,
    error = t.main.destructive,
    onError = t.main.onInk,
    outline = t.main.rule,
    outlineVariant = t.main.hairline,
    scrim = t.main.scrim,
    surfaceBright = t.main.background,
    surfaceDim = t.main.background,
    surfaceContainer = t.main.background,
    surfaceContainerHigh = t.main.background,
    surfaceContainerHighest = t.main.background,
    surfaceContainerLow = t.main.background,
    surfaceContainerLowest = t.main.background,
)

@Composable
fun MonoTheme(theme: ThemeSpec = Themes.Mono, reduceMotion: Boolean = false, ambient: Boolean = true, content: @Composable () -> Unit) {
    val scheme = remember(theme.id) { colorScheme(theme) }
    val shapes = remember(theme.id) {
        val r = RoundedCornerShape(theme.radius)
        Shapes(extraSmall = r, small = r, medium = r, large = r, extraLarge = r)
    }
    val material = remember(theme.id) {
        val t = MonoTypeScale(theme.bodyFont, theme.displayFont, theme.displayWeight, theme.main.ink, theme.main.secondary)
        Typography(
            displayLarge = t.display, displayMedium = t.display, displaySmall = t.h1,
            headlineLarge = t.h1, headlineMedium = t.h2, headlineSmall = t.h3,
            titleLarge = t.h3, titleMedium = t.body.copy(fontWeight = FontWeight.SemiBold), titleSmall = t.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            bodyLarge = t.body, bodyMedium = t.bodySmall, bodySmall = t.caption,
            labelLarge = t.label, labelMedium = t.label, labelSmall = t.caption,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = material, shapes = shapes) {
        CompositionLocalProvider(
            LocalTheme provides theme,
            LocalReduceMotion provides reduceMotion,
            LocalAmbientMotion provides (ambient && !reduceMotion),
            LocalRippleConfiguration provides RippleConfiguration(
                color = theme.main.ink,
                rippleAlpha = RippleAlpha(0.08f, 0.08f, 0.08f, 0.08f),
            ),
        ) {
            ProvideSurface(theme.main, content)
        }
    }
}
