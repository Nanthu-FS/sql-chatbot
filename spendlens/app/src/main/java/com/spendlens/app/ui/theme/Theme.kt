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
    WALLET("wallet", "Wallet", "Stacked category cards"),
    TERMINAL("terminal", "Terminal", "Phosphor text, commands, scanlines"),
    AURORA("aurora", "Aurora", "Glass cards over glowing colour"),
    RISO("riso", "Risograph", "Overprinted ink, grain, sticky notes"),
    RETRO("retro", "Retro desktop", "Bevelled windows, title bars"),
    BLUEPRINT("blueprint", "Blueprint", "Drafting grid, notes, dimensions");

    companion object {
        fun from(key: String?): Style = entries.firstOrNull { it.key == key } ?: EDITORIAL
    }
}

/** How a dashboard section is framed. */
enum class CardStyle { PRINT, PAPER, TILE, RULED, OUTLINE, SOFT, BRUTAL, TERMINAL, GLASS, RISO, BEVEL, BLUEPRINT }

/** Buttons, chips and toggles. */
enum class ControlStyle { BRACKET, PILL, BLOCK, SOLID, BEVEL }

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

private val Vt323 = FontFamily(Font(R.font.vt323_400, FontWeight.Normal))
private val Unbounded = FontFamily(Font(R.font.unbounded_600, FontWeight.SemiBold), Font(R.font.unbounded_800, FontWeight.ExtraBold))
private val Outfit = FontFamily(
    Font(R.font.outfit_400, FontWeight.Normal),
    Font(R.font.outfit_500, FontWeight.Medium),
    Font(R.font.outfit_600, FontWeight.SemiBold),
)
private val Bagel = FontFamily(Font(R.font.bagel_400, FontWeight.Normal))
private val Karla = FontFamily(
    Font(R.font.karla_400, FontWeight.Normal),
    Font(R.font.karla_600, FontWeight.SemiBold),
    Font(R.font.karla_800, FontWeight.ExtraBold),
)
private val Pixelify = FontFamily(Font(R.font.pixelify_400, FontWeight.Normal), Font(R.font.pixelify_600, FontWeight.SemiBold))
private val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_400, FontWeight.Normal),
    Font(R.font.jetbrains_mono_700, FontWeight.Bold),
    Font(R.font.jetbrains_mono_800, FontWeight.ExtraBold),
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
    Style.TERMINAL -> Look(style, CardStyle.TERMINAL, 0.dp, 0.dp, ControlStyle.BRACKET, TabStyle.CELLS, upper = false, numbered = false, dashed = true, ribbon = false, glassy = false, numbers = Vt323, numberWeight = FontWeight.Normal, numberTracking = 0f)
    Style.AURORA -> Look(style, CardStyle.GLASS, 24.dp, 999.dp, ControlStyle.PILL, TabStyle.SEGMENTED, upper = false, numbered = false, dashed = false, ribbon = false, glassy = true, numbers = Unbounded, numberWeight = FontWeight.ExtraBold, numberTracking = -0.03f)
    Style.RISO -> Look(style, CardStyle.RISO, 0.dp, 999.dp, ControlStyle.PILL, TabStyle.UNDERLINE, upper = false, numbered = false, dashed = false, ribbon = false, glassy = false, numbers = Bagel, numberWeight = FontWeight.Normal, numberTracking = 0f)
    Style.RETRO -> Look(style, CardStyle.BEVEL, 0.dp, 0.dp, ControlStyle.BEVEL, TabStyle.SEGMENTED, upper = false, numbered = false, dashed = false, ribbon = false, glassy = false, numbers = Pixelify, numberWeight = FontWeight.SemiBold, numberTracking = 0.02f)
    Style.BLUEPRINT -> Look(style, CardStyle.BLUEPRINT, 0.dp, 0.dp, ControlStyle.BRACKET, TabStyle.UNDERLINE, upper = true, numbered = false, dashed = false, ribbon = false, glassy = false, numbers = JetBrainsMono, numberWeight = FontWeight.ExtraBold, numberTracking = -0.03f)
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
    Style.TERMINAL -> if (dark) {
        ink(0xFF040804, 0xFF07100A, 0xFF0B170E, 0xFF164D24, 0xFF1F8A3D, 0xFF5CFF85, 0xFF2FB257, 0xFF1F8A3D, 0xFF0E2414, 0xFF040804, 0xFFFF6B4A, true, 0xFFFFB000)
    } else {
        ink(0xFFEEF3E8, 0xFFF6F9F2, 0xFFFFFFFF, 0xFFC9D8C0, 0xFF6E8F64, 0xFF103B1A, 0xFF3E6B45, 0xFF6F8F70, 0xFFDCE7D3, 0xFFEEF3E8, 0xFFC2410C, false, 0xFFB45309)
    }
    Style.AURORA -> if (dark) {
        ink(0xFF070A1C, 0xFF11152B, 0xFF181D38, 0xFF262B4A, 0xFF3C4270, 0xFFF4F2FF, 0xFFA9A6C8, 0xFF75729A, 0xFF1B1F3A, 0xFF070A1C, 0xFFFF8FA3, true, 0xFF8B6CFF)
    } else {
        ink(0xFFF3F1FF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFE2DEF7, 0xFFBDB6E6, 0xFF140F33, 0xFF5E5883, 0xFF8C87AD, 0xFFE9E6FA, 0xFFFFFFFF, 0xFFD6336C, false, 0xFF6D4AFF)
    }
    Style.RISO -> if (dark) {
        ink(0xFF1B1A24, 0xFF25232F, 0xFF2C2A38, 0xFF3A3848, 0xFFF4EEE3, 0xFFF4EEE3, 0xFFB9B2A4, 0xFF857F73, 0xFF2E2C3A, 0xFF1B1A24, 0xFFFF7A6E, true, 0xFFFF48B0)
    } else {
        ink(0xFFF4EEE3, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFD9CFBE, 0xFF1A1A2E, 0xFF1A1A2E, 0xFF55546A, 0xFF8A8779, 0xFFE8E0D0, 0xFFF4EEE3, 0xFFD62828, false, 0xFFFF48B0)
    }
    Style.RETRO -> if (dark) {
        ink(0xFF2B2B2B, 0xFF3C3C3C, 0xFF1E1E1E, 0xFF5A5A5A, 0xFF0A0A0A, 0xFFF0F0F0, 0xFFBDBDBD, 0xFF8A8A8A, 0xFF4A4A4A, 0xFF000000, 0xFFFF5A5A, true, 0xFF3A6EFF)
    } else {
        ink(0xFFC0C0C0, 0xFFC0C0C0, 0xFFFFFFFF, 0xFF808080, 0xFF404040, 0xFF000000, 0xFF303030, 0xFF5A5A5A, 0xFFA8A8A8, 0xFFFFFFFF, 0xFFC00000, false, 0xFF000080)
    }
    Style.BLUEPRINT -> if (dark) {
        ink(0xFF0B3D91, 0xFF0B3D91, 0xFF124AA6, 0xFF3D69B3, 0xFFEAF2FF, 0xFFEAF2FF, 0xFFA9C1EA, 0xFF7898CF, 0xFF174A9E, 0xFF0B3D91, 0xFFFFD23F, true, 0xFFFF8A3D)
    } else {
        ink(0xFFF4F7FC, 0xFFF4F7FC, 0xFFFFFFFF, 0xFFB9CBE8, 0xFF0B3D91, 0xFF0B3D91, 0xFF4A6BA8, 0xFF7F97C4, 0xFFE1E9F6, 0xFFF4F7FC, 0xFFC8102E, false, 0xFFE8590C)
    }
}

/** Second print colour of the risograph look (the first is the accent). */
val RisoBlue = Color(0xFF0078BF)

/** Extra glow colours of the aurora look (the first is the accent). */
val AuroraGlow = listOf(Color(0xFF2DE2E6), Color(0xFFFF4FD8), Color(0xFFFFD166))

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
    /** Pixel and terminal faces run small; body sizes go up by this. */
    val bodyBump: Int = 0,
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
    Style.TERMINAL -> Faces(Vt323, FontWeight.Normal, Vt323, Vt323, FontWeight.Normal, 0.0, 1.05f, 0.02, 5, bodyBump = 5)
    Style.AURORA -> Faces(Unbounded, FontWeight.ExtraBold, Outfit, Outfit, FontWeight.Medium, 0.7, 0.8f, 0.0, 2)
    Style.RISO -> Faces(Bagel, FontWeight.Normal, Karla, Karla, FontWeight.ExtraBold, 0.2, 0.85f, 0.06, 1, bodyBump = 1)
    Style.RETRO -> Faces(Pixelify, FontWeight.SemiBold, Pixelify, Pixelify, FontWeight.Normal, 0.0, 0.9f, 0.0, 3, bodyBump = 1)
    Style.BLUEPRINT -> Faces(JetBrainsMono, FontWeight.ExtraBold, JetBrainsMono, JetBrainsMono, FontWeight.Normal, 0.5, 0.78f, 0.1, 0)
}

fun typographyFor(style: Style): Typography {
    val f = facesFor(style)
    fun display(size: Int, line: Int, tracking: Double) = TextStyle(
        fontFamily = f.display, fontWeight = f.displayWeight,
        fontSize = (size * f.displayScale).sp, lineHeight = (line * f.displayScale).sp, letterSpacing = (tracking * f.tighten).em,
    )
    fun body(size: Int, weight: FontWeight, line: Int, tracking: Double) = TextStyle(
        fontFamily = f.body, fontWeight = weight, fontSize = (size + f.bodyBump).sp, lineHeight = (line + f.bodyBump).sp, letterSpacing = (tracking * f.tighten).em,
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
