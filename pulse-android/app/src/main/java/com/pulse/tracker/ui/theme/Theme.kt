package com.pulse.tracker.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pulse.tracker.R

enum class ThemeId { VOLT, PAPER, BRUTAL, NEON, MATCHA, SWISS }

/** How the calorie summary on Today is drawn. */
enum class HeroStyle { SEGMENT_RING, EDITORIAL, CHUNKY, LED_RING, SOFT_RING, SWISS }

/** How containers are drawn. */
enum class CardStyle { OUTLINED, RULED, BRUTAL, BRACKETS, SOFT, SWISS }

enum class NavStyle { FLOATING, EDITORIAL, BRUTAL, TERMINAL, PILL, BLOCK }

enum class MacroStyle { CARDS, TABLE, STICKERS, ASCII, JARS, LETTERS }

/** Semantic card colours. Only some themes (Brutal) give each one its own fill. */
enum class CardTint { DEFAULT, HERO, PROTEIN, CARBS, FAT, WATER, STEPS, SLEEP }

@Immutable
data class PulseColors(
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentText: Color,
    val protein: Color,
    val carbs: Color,
    val fat: Color,
    val water: Color,
    val waterText: Color,
    val warm: Color,
    val calm: Color,
    val track: Color,
)

@Immutable
data class PulseTheme(
    val id: ThemeId,
    val name: String,
    val tagline: String,
    val isDark: Boolean,
    val c: PulseColors,
    val display: FontFamily,
    val body: FontFamily,
    val displayWeight: FontWeight,
    val displayTracking: Float,
    val radius: Dp,
    val chipRadius: Dp,
    val borderWidth: Dp,
    val cardStyle: CardStyle,
    val heroStyle: HeroStyle,
    val navStyle: NavStyle,
    val macroStyle: MacroStyle,
    val upperLabels: Boolean,
    val labelTracking: Float,
    val tints: Map<CardTint, Pair<Color, Color>> = emptyMap(),
) {
    /** Background and content colour for a card of the given tint. */
    fun tint(t: CardTint): Pair<Color, Color> = tints[t] ?: (c.surface to c.text)
}

// ---------------------------------------------------------------- fonts

private val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold),
)
private val Inter = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold),
)
private val InstrumentSerif = FontFamily(
    Font(R.font.instrument_serif, FontWeight.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)
private val DmSans = FontFamily(
    Font(R.font.dm_sans, FontWeight.Normal),
    Font(R.font.dm_sans, FontWeight.Medium),
    Font(R.font.dm_sans, FontWeight.Bold),
)
private val Bricolage = FontFamily(
    Font(R.font.bricolage_grotesque, FontWeight.Medium),
    Font(R.font.bricolage_grotesque, FontWeight.SemiBold),
    Font(R.font.bricolage_grotesque, FontWeight.Bold),
    Font(R.font.bricolage_grotesque, FontWeight.ExtraBold),
)
private val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold),
    Font(R.font.jetbrains_mono, FontWeight.ExtraBold),
)
private val Nunito = FontFamily(
    Font(R.font.nunito, FontWeight.Medium),
    Font(R.font.nunito, FontWeight.SemiBold),
    Font(R.font.nunito, FontWeight.Bold),
    Font(R.font.nunito, FontWeight.ExtraBold),
    Font(R.font.nunito, FontWeight.Black),
)
private val InterTight = FontFamily(
    Font(R.font.inter_tight, FontWeight.Medium),
    Font(R.font.inter_tight, FontWeight.SemiBold),
    Font(R.font.inter_tight, FontWeight.Bold),
    Font(R.font.inter_tight, FontWeight.ExtraBold),
    Font(R.font.inter_tight, FontWeight.Black),
)

// ---------------------------------------------------------------- themes

val VoltTheme = PulseTheme(
    id = ThemeId.VOLT, name = "Volt", tagline = "Dark · electric", isDark = true,
    c = PulseColors(
        bg = Color(0xFF09090C), surface = Color(0xFF14141A), surfaceAlt = Color(0xFF1C1C24),
        border = Color(0xFF26262F), text = Color(0xFFF4F4F6), muted = Color(0xFF9A9AAB),
        accent = Color(0xFFC6FF3D), onAccent = Color(0xFF09090C), accentText = Color(0xFFC6FF3D),
        protein = Color(0xFFC6FF3D), carbs = Color(0xFF8B7CFF), fat = Color(0xFFFF9E4A),
        water = Color(0xFF4FD1FF), waterText = Color(0xFF4FD1FF), warm = Color(0xFFFF9E4A),
        calm = Color(0xFF8B7CFF), track = Color(0xFF23232C),
    ),
    display = SpaceGrotesk, body = Inter, displayWeight = FontWeight.Bold, displayTracking = -0.03f,
    radius = 24.dp, chipRadius = 14.dp, borderWidth = 1.dp,
    cardStyle = CardStyle.OUTLINED, heroStyle = HeroStyle.SEGMENT_RING, navStyle = NavStyle.FLOATING,
    macroStyle = MacroStyle.CARDS, upperLabels = false, labelTracking = 0f,
)

val PaperTheme = PulseTheme(
    id = ThemeId.PAPER, name = "Paper", tagline = "Editorial · serif", isDark = false,
    c = PulseColors(
        bg = Color(0xFFF4EFE6), surface = Color(0xFFF4EFE6), surfaceAlt = Color(0xFFEAE3D6),
        border = Color(0xFFCFC5B3), text = Color(0xFF1B1A17), muted = Color(0xFF6B655B),
        accent = Color(0xFF1B1A17), onAccent = Color(0xFFF4EFE6), accentText = Color(0xFFA8401F),
        protein = Color(0xFFA8401F), carbs = Color(0xFF5D6B2F), fat = Color(0xFF2B4C7E),
        water = Color(0xFF2B4C7E), waterText = Color(0xFF2B4C7E), warm = Color(0xFFA8401F),
        calm = Color(0xFF5D6B2F), track = Color(0xFFE2DACB),
    ),
    display = InstrumentSerif, body = DmSans, displayWeight = FontWeight.Normal, displayTracking = -0.02f,
    radius = 0.dp, chipRadius = 20.dp, borderWidth = 1.dp,
    cardStyle = CardStyle.RULED, heroStyle = HeroStyle.EDITORIAL, navStyle = NavStyle.EDITORIAL,
    macroStyle = MacroStyle.TABLE, upperLabels = true, labelTracking = 0.18f,
)

val BrutalTheme = PulseTheme(
    id = ThemeId.BRUTAL, name = "Brutal", tagline = "Bold · playful", isDark = false,
    c = PulseColors(
        bg = Color(0xFFFFF8E7), surface = Color(0xFFFFFFFF), surfaceAlt = Color(0xFFFFF1C9),
        border = Color(0xFF111111), text = Color(0xFF111111), muted = Color(0xFF4A4A4A),
        accent = Color(0xFFFF5FA2), onAccent = Color(0xFF111111), accentText = Color(0xFF111111),
        protein = Color(0xFFFF5FA2), carbs = Color(0xFF3B5BFF), fat = Color(0xFFFF8A3D),
        water = Color(0xFF3B5BFF), waterText = Color(0xFF3B5BFF), warm = Color(0xFFFF8A3D),
        calm = Color(0xFFB8F35A), track = Color(0xFFFFFFFF),
    ),
    display = Bricolage, body = Bricolage, displayWeight = FontWeight.ExtraBold, displayTracking = -0.05f,
    radius = 20.dp, chipRadius = 12.dp, borderWidth = 2.5.dp,
    cardStyle = CardStyle.BRUTAL, heroStyle = HeroStyle.CHUNKY, navStyle = NavStyle.BRUTAL,
    macroStyle = MacroStyle.STICKERS, upperLabels = true, labelTracking = 0.06f,
    tints = mapOf(
        CardTint.HERO to (Color(0xFFFFD93D) to Color(0xFF111111)),
        CardTint.PROTEIN to (Color(0xFFFF5FA2) to Color(0xFF111111)),
        CardTint.CARBS to (Color(0xFF3B5BFF) to Color(0xFFFFFFFF)),
        CardTint.FAT to (Color(0xFFFF8A3D) to Color(0xFF111111)),
        CardTint.WATER to (Color(0xFF3B5BFF) to Color(0xFFFFFFFF)),
        CardTint.STEPS to (Color(0xFFB8F35A) to Color(0xFF111111)),
        CardTint.SLEEP to (Color(0xFFC9B8FF) to Color(0xFF111111)),
    ),
)

val NeonTheme = PulseTheme(
    id = ThemeId.NEON, name = "Neon", tagline = "Synth · mono", isDark = true,
    c = PulseColors(
        bg = Color(0xFF07060B), surface = Color(0xFF0E0C16), surfaceAlt = Color(0xFF14112A),
        border = Color(0xFF2A2340), text = Color(0xFFF2ECFF), muted = Color(0xFFA59CC2),
        accent = Color(0xFFFF3EA5), onAccent = Color(0xFF07060B), accentText = Color(0xFFFF3EA5),
        protein = Color(0xFFFF3EA5), carbs = Color(0xFF2DE2E6), fat = Color(0xFFF9F871),
        water = Color(0xFF2DE2E6), waterText = Color(0xFF2DE2E6), warm = Color(0xFFF9F871),
        calm = Color(0xFFA99DFF), track = Color(0xFF1B1630),
    ),
    display = JetBrainsMono, body = JetBrainsMono, displayWeight = FontWeight.ExtraBold, displayTracking = -0.02f,
    radius = 0.dp, chipRadius = 0.dp, borderWidth = 1.dp,
    cardStyle = CardStyle.BRACKETS, heroStyle = HeroStyle.LED_RING, navStyle = NavStyle.TERMINAL,
    macroStyle = MacroStyle.ASCII, upperLabels = true, labelTracking = 0.1f,
)

val MatchaTheme = PulseTheme(
    id = ThemeId.MATCHA, name = "Matcha", tagline = "Soft · calm", isDark = false,
    c = PulseColors(
        bg = Color(0xFFEEF1E6), surface = Color(0xFFFBFCF7), surfaceAlt = Color(0xFFDCE8CC),
        border = Color(0xFFDCE2D1), text = Color(0xFF233024), muted = Color(0xFF5E6B5C),
        accent = Color(0xFF5E8C3A), onAccent = Color(0xFFFFFFFF), accentText = Color(0xFF4A7330),
        protein = Color(0xFF7FAE55), carbs = Color(0xFFE8C65A), fat = Color(0xFFF2B38A),
        water = Color(0xFF8DB8D9), waterText = Color(0xFF3F6F93), warm = Color(0xFFF2B38A),
        calm = Color(0xFF8DB8D9), track = Color(0xFFE6EADD),
    ),
    display = Nunito, body = Nunito, displayWeight = FontWeight.Black, displayTracking = -0.01f,
    radius = 30.dp, chipRadius = 22.dp, borderWidth = 0.dp,
    cardStyle = CardStyle.SOFT, heroStyle = HeroStyle.SOFT_RING, navStyle = NavStyle.PILL,
    macroStyle = MacroStyle.JARS, upperLabels = false, labelTracking = 0f,
)

val SwissTheme = PulseTheme(
    id = ThemeId.SWISS, name = "Swiss", tagline = "Stark · grid", isDark = false,
    c = PulseColors(
        bg = Color(0xFFFAFAFA), surface = Color(0xFFFAFAFA), surfaceAlt = Color(0xFFEFEFEF),
        border = Color(0xFF0A0A0A), text = Color(0xFF0A0A0A), muted = Color(0xFF6E6E6E),
        accent = Color(0xFFD21F14), onAccent = Color(0xFFFFFFFF), accentText = Color(0xFFD21F14),
        protein = Color(0xFFD21F14), carbs = Color(0xFF0A0A0A), fat = Color(0xFF8A8A8A),
        water = Color(0xFF0A0A0A), waterText = Color(0xFF0A0A0A), warm = Color(0xFFD21F14),
        calm = Color(0xFF0A0A0A), track = Color(0xFFE4E4E4),
    ),
    display = InterTight, body = InterTight, displayWeight = FontWeight.Black, displayTracking = -0.06f,
    radius = 0.dp, chipRadius = 0.dp, borderWidth = 1.dp,
    cardStyle = CardStyle.SWISS, heroStyle = HeroStyle.SWISS, navStyle = NavStyle.BLOCK,
    macroStyle = MacroStyle.LETTERS, upperLabels = true, labelTracking = 0.1f,
)

val AllThemes = listOf(VoltTheme, PaperTheme, BrutalTheme, NeonTheme, MatchaTheme, SwissTheme)

fun themeFor(id: String): PulseTheme = AllThemes.firstOrNull { it.id.name == id } ?: VoltTheme

// ---------------------------------------------------------------- provider

val LocalPulse = staticCompositionLocalOf { VoltTheme }

/** Muted text colour for the current container (cards with coloured fills override it). */
val LocalMuted = staticCompositionLocalOf { VoltTheme.c.muted }

val pulse: PulseTheme
    @Composable @ReadOnlyComposable get() = LocalPulse.current

@Composable
fun PulseProvider(theme: PulseTheme, content: @Composable () -> Unit) {
    val c = theme.c
    val scheme = if (theme.isDark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, secondary = c.carbs, background = c.bg,
            onBackground = c.text, surface = c.surface, onSurface = c.text, surfaceVariant = c.surfaceAlt,
            onSurfaceVariant = c.muted, outline = c.border, surfaceContainerHigh = c.surface,
            surfaceContainerLow = c.surface, surfaceContainer = c.surface,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, secondary = c.carbs, background = c.bg,
            onBackground = c.text, surface = c.surface, onSurface = c.text, surfaceVariant = c.surfaceAlt,
            onSurfaceVariant = c.muted, outline = c.border, surfaceContainerHigh = c.surface,
            surfaceContainerLow = c.surface, surfaceContainer = c.surface,
        )
    }
    val base = TextStyle(fontFamily = theme.body, color = c.text)
    val typography = Typography().let { t ->
        Typography(
            bodyLarge = t.bodyLarge.merge(base), bodyMedium = t.bodyMedium.merge(base), bodySmall = t.bodySmall.merge(base),
            titleLarge = t.titleLarge.merge(base), titleMedium = t.titleMedium.merge(base), titleSmall = t.titleSmall.merge(base),
            labelLarge = t.labelLarge.merge(base), labelMedium = t.labelMedium.merge(base), labelSmall = t.labelSmall.merge(base),
            headlineSmall = t.headlineSmall.merge(base.copy(fontFamily = theme.display)),
        )
    }
    MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(LocalPulse provides theme, LocalMuted provides c.muted, LocalContentColor provides c.text) {
            content()
        }
    }
}
