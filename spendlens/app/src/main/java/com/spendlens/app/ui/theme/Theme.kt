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
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.spendlens.app.R

/**
 * Colours for one style in one brightness. [accent] is the single highlight colour (the user can
 * pick it); [alert] is reserved for "something is wrong".
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
    val accent: Color = text,
    val onAccent: Color = inverse,
) {
    /** Grey ramp for charts & heatmaps: 0 = empty, 1 = ink. */
    fun ramp(t: Float): Color = lerp(ghost, text, t.coerceIn(0f, 1f))

    /** Heatmap ramp towards the accent. */
    fun heat(t: Float): Color = lerp(ghost, accent, t.coerceIn(0f, 1f))

    fun withAccent(color: Color): Ink = copy(accent = color, onAccent = contentOn(color))
}

/** Dark text on light fills, white on dark ones. */
fun contentOn(fill: Color): Color = if (fill.luminance() > 0.45f) Color(0xFF111111) else Color.White

/** The app's looks. Each changes colours, type, shapes and a few layouts; any of them takes an accent. */
enum class Style(val key: String, val label: String, val blurb: String) {
    EDITORIAL("editorial", "Editorial", "Monochrome print, hairlines"),
    RECEIPT("receipt", "Receipt", "Paper slip, typewriter figures"),
    BENTO("bento", "Bento", "Rounded tiles, one bright accent"),
    SWISS("swiss", "Swiss", "Ruled grid, huge numbers"),
    DOT("dot", "Dot matrix", "Dotted figures, black and red"),
    CALENDAR("calendar", "Calendar", "Soft cards, month first"),
    BRUTAL("brutal", "Neo-brutal", "Thick borders, hard shadows"),
    WALLET("wallet", "Wallet", "Stacked category cards");

    companion object {
        fun from(key: String?): Style = entries.firstOrNull { it.key == key } ?: EDITORIAL
    }
}

/** How a dashboard section is framed. */
enum class CardStyle { PRINT, PAPER, TILE, RULED, OUTLINE, SOFT, BRUTAL }

/** Buttons, chips and toggles. */
enum class ControlStyle { BRACKET, PILL, BLOCK, SOLID }

/** Period tabs and similar segmented choices. */
enum class TabStyle { UNDERLINE, SEGMENTED, CELLS }

@Immutable
data class Look(
    val style: Style,
    val card: CardStyle,
    /** Corner radius of cards; controls use [controlRadius]. */
    val radius: Dp,
    val controlRadius: Dp,
    val control: ControlStyle,
    val tabs: TabStyle,
    /** Labels, statements and headings set in capitals. */
    val upper: Boolean,
    /** "(01)" numbers in front of section titles. */
    val numbered: Boolean,
    /** Dashed hairlines (receipt). */
    val dashed: Boolean,
    /** The tilted scrolling ribbon under the hero. */
    val ribbon: Boolean,
    /** Glass slider applies (frosted panels, see-through bar). */
    val glassy: Boolean,
    val numbers: FontFamily,
    val numberWeight: FontWeight,
    val numberTracking: Float,
) {
    fun caps(s: String): String = if (upper) s.uppercase() else s
}

// ---------------------------------------------------------------- fonts

val InterTight = FontFamily(
    Font(R.font.inter_tight, FontWeight.Light),
    Font(R.font.inter_tight, FontWeight.Normal),
    Font(R.font.inter_tight, FontWeight.Medium),
    Font(R.font.inter_tight, FontWeight.SemiBold),
)
private val PlexMono = FontFamily(Font(R.font.plex_mono_400, FontWeight.Normal), Font(R.font.plex_mono_600, FontWeight.SemiBold))
private val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_400, FontWeight.Normal),
    Font(R.font.space_grotesk_500, FontWeight.Medium),
    Font(R.font.space_grotesk_700, FontWeight.Bold),
)
private val Archivo = FontFamily(
    Font(R.font.archivo_400, FontWeight.Normal),
    Font(R.font.archivo_500, FontWeight.Medium),
    Font(R.font.archivo_700, FontWeight.Bold),
    Font(R.font.archivo_800, FontWeight.ExtraBold),
)
private val Doto = FontFamily(Font(R.font.doto_900, FontWeight.Black))
private val SpaceMono = FontFamily(Font(R.font.space_mono_400, FontWeight.Normal), Font(R.font.space_mono_700, FontWeight.Bold))
private val Manrope = FontFamily(
    Font(R.font.manrope_400, FontWeight.Normal),
    Font(R.font.manrope_600, FontWeight.SemiBold),
    Font(R.font.manrope_800, FontWeight.ExtraBold),
)
private val Syne = FontFamily(Font(R.font.syne_700, FontWeight.Bold), Font(R.font.syne_800, FontWeight.ExtraBold))
private val DmMono = FontFamily(Font(R.font.dm_mono_400, FontWeight.Normal), Font(R.font.dm_mono_500, FontWeight.Medium))
private val Sora = FontFamily(
    Font(R.font.sora_400, FontWeight.Normal),
    Font(R.font.sora_600, FontWeight.SemiBold),
    Font(R.font.sora_700, FontWeight.Bold),
)

// ---------------------------------------------------------------- looks

fun lookFor(style: Style): Look = when (style) {
    Style.EDITORIAL -> Look(style, CardStyle.PRINT, 10.dp, 2.dp, ControlStyle.BRACKET, TabStyle.UNDERLINE, upper = true, numbered = true, dashed = false, ribbon = true, glassy = true, numbers = InterTight, numberWeight = FontWeight.Normal, numberTracking = -0.04f)
    Style.RECEIPT -> Look(style, CardStyle.PAPER, 0.dp, 0.dp, ControlStyle.BRACKET, TabStyle.CELLS, upper = true, numbered = true, dashed = true, ribbon = false, glassy = false, numbers = PlexMono, numberWeight = FontWeight.SemiBold, numberTracking = -0.03f)
    Style.BENTO -> Look(style, CardStyle.TILE, 24.dp, 999.dp, ControlStyle.PILL, TabStyle.SEGMENTED, upper = false, numbered = false, dashed = false, ribbon = false, glassy = true, numbers = SpaceGrotesk, numberWeight = FontWeight.Bold, numberTracking = -0.04f)
    Style.SWISS -> Look(style, CardStyle.RULED, 0.dp, 0.dp, ControlStyle.SOLID, TabStyle.CELLS, upper = false, numbered = true, dashed = false, ribbon = false, glassy = false, numbers = Archivo, numberWeight = FontWeight.ExtraBold, numberTracking = -0.055f)
    Style.DOT -> Look(style, CardStyle.OUTLINE, 20.dp, 999.dp, ControlStyle.PILL, TabStyle.UNDERLINE, upper = true, numbered = false, dashed = false, ribbon = false, glassy = true, numbers = Doto, numberWeight = FontWeight.Black, numberTracking = -0.02f)
    Style.CALENDAR -> Look(style, CardStyle.SOFT, 22.dp, 14.dp, ControlStyle.PILL, TabStyle.SEGMENTED, upper = false, numbered = false, dashed = false, ribbon = false, glassy = false, numbers = Manrope, numberWeight = FontWeight.ExtraBold, numberTracking = -0.04f)
    Style.BRUTAL -> Look(style, CardStyle.BRUTAL, 0.dp, 0.dp, ControlStyle.BLOCK, TabStyle.CELLS, upper = true, numbered = false, dashed = false, ribbon = false, glassy = false, numbers = Syne, numberWeight = FontWeight.ExtraBold, numberTracking = -0.04f)
    Style.WALLET -> Look(style, CardStyle.TILE, 22.dp, 16.dp, ControlStyle.PILL, TabStyle.SEGMENTED, upper = false, numbered = false, dashed = false, ribbon = false, glassy = true, numbers = Sora, numberWeight = FontWeight.Bold, numberTracking = -0.04f)
}

// ---------------------------------------------------------------- palettes

private fun ink(
    canvas: Long, surface: Long, raised: Long, line: Long, lineStrong: Long, text: Long, muted: Long, faint: Long, ghost: Long,
    inverse: Long, alert: Long, dark: Boolean, accent: Long? = null,
): Ink {
    val base = Ink(
        Color(canvas), Color(surface), Color(raised), Color(line), Color(lineStrong), Color(text), Color(muted), Color(faint),
        Color(ghost), Color(inverse), Color(alert), dark,
    )
    return if (accent == null) base else base.withAccent(Color(accent))
}

fun inkFor(style: Style, dark: Boolean): Ink = when (style) {
    Style.EDITORIAL -> if (dark) {
        ink(0xFF0A0A0A, 0xFF111111, 0xFF181818, 0xFF232323, 0xFF3A3A3A, 0xFFEDEDEA, 0xFF8A8A87, 0xFF55554F, 0xFF1C1C1C, 0xFF0A0A0A, 0xFFE5534B, true)
    } else {
        ink(0xFFF1F0EC, 0xFFF7F6F3, 0xFFFFFFFF, 0xFFDCDAD4, 0xFFB9B7B0, 0xFF0B0B0B, 0xFF6B6A66, 0xFF9E9C96, 0xFFE4E2DC, 0xFFF1F0EC, 0xFFC4332B, false)
    }
    Style.RECEIPT -> if (dark) {
        ink(0xFF1A1917, 0xFF22211E, 0xFF2A2925, 0xFF3A3832, 0xFF5A564D, 0xFFEAE6DC, 0xFFA39E92, 0xFF7D796F, 0xFF2E2C27, 0xFF1A1917, 0xFFFF6A4D, true, 0xFFFF6A4D)
    } else {
        ink(0xFFFBFAF7, 0xFFF3F0E9, 0xFFFFFFFF, 0xFFD6D2C8, 0xFFA9A59C, 0xFF1B1B19, 0xFF5E5B55, 0xFF85817A, 0xFFE9E5DC, 0xFFFBFAF7, 0xFFC2371E, false, 0xFFC2371E)
    }
    Style.BENTO -> if (dark) {
        ink(0xFF0D0E0B, 0xFF1B1D18, 0xFF24271F, 0xFF2C2F27, 0xFF454A3D, 0xFFF2F2EE, 0xFFA3A69C, 0xFF72766A, 0xFF2A2D25, 0xFF0D0E0B, 0xFFFF6B4A, true, 0xFFD4FF3A)
    } else {
        ink(0xFFECEEE6, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFDDE0D5, 0xFFB9BDB0, 0xFF11130E, 0xFF5C6055, 0xFF8A8E83, 0xFFE3E6DB, 0xFFFFFFFF, 0xFFD8431F, false, 0xFF5E8C00)
    }
    Style.SWISS -> if (dark) {
        ink(0xFF0E0E0E, 0xFF0E0E0E, 0xFF1A1A1A, 0xFF2E2E2E, 0xFFF2F2F2, 0xFFF2F2F2, 0xFF9A9A9A, 0xFF6E6E6E, 0xFF222222, 0xFF0E0E0E, 0xFFFF5A4F, true, 0xFFFF5A1F)
    } else {
        ink(0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFD5D5D5, 0xFF111111, 0xFF111111, 0xFF6B6B6B, 0xFF949494, 0xFFEDEDED, 0xFFFFFFFF, 0xFFC8102E, false, 0xFFE8450A)
    }
    Style.DOT -> if (dark) {
        ink(0xFF000000, 0xFF0B0B0B, 0xFF141414, 0xFF2A2A2A, 0xFF444444, 0xFFEDEDED, 0xFF9A9A9A, 0xFF6A6A6A, 0xFF1C1C1C, 0xFF000000, 0xFFFF3B2F, true, 0xFFFF3B2F)
    } else {
        ink(0xFFF2F2F0, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFD9D9D6, 0xFFB0B0AC, 0xFF0A0A0A, 0xFF5F5F5C, 0xFF8A8A86, 0xFFE4E4E1, 0xFFF2F2F0, 0xFFE0281E, false, 0xFFE0281E)
    }
    Style.CALENDAR -> if (dark) {
        ink(0xFF0E1511, 0xFF16201A, 0xFF1D2922, 0xFF26332B, 0xFF3C4B42, 0xFFE8F0EA, 0xFF9DAAA1, 0xFF6B7A70, 0xFF1F2B24, 0xFF0E1511, 0xFFFF7A6E, true, 0xFF4CC38A)
    } else {
        ink(0xFFF3F5F1, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFE3E8E1, 0xFFC3CCC4, 0xFF0F1F17, 0xFF56645B, 0xFF849088, 0xFFE9EEE8, 0xFFFFFFFF, 0xFFB3261E, false, 0xFF1D6B45)
    }
    Style.BRUTAL -> if (dark) {
        ink(0xFF151515, 0xFF232323, 0xFF232323, 0xFFF5F5F5, 0xFFF5F5F5, 0xFFF5F5F5, 0xFFC2C2C2, 0xFF8E8E8E, 0xFF2E2E2E, 0xFF151515, 0xFFFF6B5A, true, 0xFFFFE14D)
    } else {
        ink(0xFFFFE14D, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF000000, 0xFF000000, 0xFF000000, 0xFF333333, 0xFF555555, 0xFFF1D23F, 0xFFFFFFFF, 0xFFC7261A, false, 0xFFFF6B5A)
    }
    Style.WALLET -> if (dark) {
        ink(0xFF0A0F1E, 0xFF141B30, 0xFF1C2440, 0xFF232C4A, 0xFF3A4570, 0xFFEEF1FA, 0xFF9AA3BD, 0xFF6B7596, 0xFF1C2440, 0xFF0A0F1E, 0xFFFF7A66, true, 0xFFE0533D)
    } else {
        ink(0xFFEEF1F8, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFDCE1EE, 0xFFB5BDD4, 0xFF0A0F1E, 0xFF56607A, 0xFF858EA8, 0xFFE3E8F3, 0xFFFFFFFF, 0xFFD0402A, false, 0xFFE0533D)
    }
}

/** Accent choices offered in Settings (null = the style's own). */
val AccentChoices: List<Color> = listOf(
    Color(0xFFE5534B), Color(0xFFFF7A1A), Color(0xFFF2C94C), Color(0xFFD4FF3A), Color(0xFF4CC38A), Color(0xFF14B8A6),
    Color(0xFF4DA3FF), Color(0xFF2B3BE8), Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF8A8A87),
)

// ---------------------------------------------------------------- type

private class Faces(
    val display: FontFamily,
    val displayWeight: FontWeight,
    val body: FontFamily,
    val label: FontFamily,
    val labelWeight: FontWeight,
    /** Multiplier on the (negative) tracking of big type; 0 for monospace. */
    val tighten: Double,
    val displayScale: Float,
    val labelTracking: Double,
    /** Sentence-case labels read better a size up. */
    val labelBump: Int,
)

private fun facesFor(style: Style): Faces = when (style) {
    Style.EDITORIAL -> Faces(InterTight, FontWeight.Normal, InterTight, InterTight, FontWeight.Medium, 1.0, 1f, 0.08, 0)
    Style.RECEIPT -> Faces(PlexMono, FontWeight.SemiBold, PlexMono, PlexMono, FontWeight.Normal, 0.0, 0.78f, 0.04, 0)
    Style.BENTO -> Faces(SpaceGrotesk, FontWeight.Medium, SpaceGrotesk, SpaceGrotesk, FontWeight.Medium, 1.0, 0.95f, 0.0, 2)
    Style.SWISS -> Faces(Archivo, FontWeight.ExtraBold, Archivo, Archivo, FontWeight.Bold, 1.2, 0.95f, 0.0, 2)
    Style.DOT -> Faces(SpaceMono, FontWeight.Bold, SpaceMono, SpaceMono, FontWeight.Normal, 0.0, 0.72f, 0.06, 0)
    Style.CALENDAR -> Faces(Manrope, FontWeight.ExtraBold, Manrope, Manrope, FontWeight.SemiBold, 1.0, 0.9f, 0.0, 2)
    Style.BRUTAL -> Faces(Syne, FontWeight.ExtraBold, DmMono, DmMono, FontWeight.Medium, 0.6, 0.85f, 0.02, 0)
    Style.WALLET -> Faces(Sora, FontWeight.Bold, Sora, Sora, FontWeight.SemiBold, 0.8, 0.88f, 0.0, 2)
}

fun typographyFor(style: Style): Typography {
    val f = facesFor(style)
    fun display(size: Int, line: Int, tracking: Double) = TextStyle(
        fontFamily = f.display, fontWeight = f.displayWeight,
        fontSize = (size * f.displayScale).sp, lineHeight = (line * f.displayScale).sp, letterSpacing = (tracking * f.tighten).em,
    )
    fun body(size: Int, weight: FontWeight, line: Int, tracking: Double) = TextStyle(
        fontFamily = f.body, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = (tracking * f.tighten).em,
    )
    fun label(size: Int, line: Int, tracking: Double) = TextStyle(
        fontFamily = f.label, fontWeight = f.labelWeight,
        fontSize = (size + f.labelBump).sp, lineHeight = (line + f.labelBump).sp,
        letterSpacing = (if (f.labelTracking == 0.0) 0.0 else tracking * f.labelTracking / 0.08).em,
    )
    return Typography(
        displayLarge = display(64, 60, -0.04),
        displayMedium = display(48, 46, -0.035),
        displaySmall = display(36, 36, -0.03),
        headlineLarge = display(30, 31, -0.025),
        headlineMedium = display(24, 26, -0.02),
        headlineSmall = display(20, 23, -0.015).copy(fontWeight = if (f.displayWeight == FontWeight.Normal) FontWeight.Medium else f.displayWeight),
        titleLarge = body(18, FontWeight.Medium, 22, -0.01),
        titleMedium = body(15, FontWeight.Medium, 19, -0.005),
        titleSmall = body(13, FontWeight.Medium, 17, 0.0),
        bodyLarge = body(15, FontWeight.Normal, 21, 0.0),
        bodyMedium = body(13, FontWeight.Normal, 18, 0.0),
        bodySmall = body(11, FontWeight.Normal, 15, 0.01),
        labelLarge = label(12, 14, 0.06),
        labelMedium = label(10, 12, 0.08),
        labelSmall = label(9, 11, 0.1),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

val LocalInk = staticCompositionLocalOf { inkFor(Style.EDITORIAL, true) }
val LocalLook = staticCompositionLocalOf { lookFor(Style.EDITORIAL) }

object Spend {
    val ink: Ink
        @Composable get() = LocalInk.current
    val look: Look
        @Composable get() = LocalLook.current
}

@Composable
fun SpendLensTheme(darkTheme: Boolean, style: Style = Style.EDITORIAL, accent: Color? = null, content: @Composable () -> Unit) {
    val ink = remember(style, darkTheme, accent) { inkFor(style, darkTheme).let { if (accent != null) it.withAccent(accent) else it } }
    val look = remember(style) { lookFor(style) }
    val type = remember(style) { typographyFor(style) }
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = ink.accent, onPrimary = ink.onAccent,
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
            primary = ink.accent, onPrimary = ink.onAccent,
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
    CompositionLocalProvider(LocalInk provides ink, LocalLook provides look) {
        MaterialTheme(colorScheme = scheme, typography = type, shapes = AppShapes, content = content)
    }
}
