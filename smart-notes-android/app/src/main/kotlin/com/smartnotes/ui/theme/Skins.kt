package com.smartnotes.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The five selectable looks, matching the approved mockups (1, 5, 7, 8, 9). */
enum class Skin(val label: String, val blurb: String) {
    SWISS("Mono Swiss", "White, black, one red accent. Dense and typographic."),
    BRUTAL("Neo-Brutalist", "Thick borders, hard shadows, loud yellow."),
    RETRO("Retro OS", "Notes as windows on a 90s desktop."),
    ROLODEX("Rolodex", "Index cards on lined paper in a green drawer."),
    TERMINAL("Terminal", "Amber phosphor command line."),
}

/** How cards (note rows, panels) are drawn. */
enum class CardStyle { RULED, BOXED, WINDOW, INDEX, PROMPT }

enum class Backdrop { PLAIN, DOTS, SCANLINES }

data class SkinTokens(
    val skin: Skin,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val accent: Color,
    val onAccent: Color,
    val muted: Color,
    val border: Color,
    val bar: Color,
    val onBar: Color,
    val highlight: Color,
    val display: FontFamily,
    val body: FontFamily,
    val label: FontFamily,
    val cardBody: FontFamily,
    val corner: Dp,
    val borderWidth: Dp,
    val shadowOffset: Dp,
    val shadowColor: Color,
    val upperCaseTitles: Boolean,
    val dark: Boolean,
    val cardStyle: CardStyle,
    val backdrop: Backdrop,
)

object Skins {
    fun tokens(skin: Skin): SkinTokens = when (skin) {
        Skin.SWISS -> SkinTokens(
            skin = skin,
            background = Color(0xFFFFFFFF), onBackground = Color(0xFF111111),
            surface = Color(0xFFFFFFFF), onSurface = Color(0xFF111111),
            accent = Color(0xFFD62D20), onAccent = Color.White,
            muted = Color(0xFF555555), border = Color(0xFF111111),
            bar = Color(0xFFFFFFFF), onBar = Color(0xFF111111), highlight = Color(0xFFFFE4E1),
            display = FontFamily.SansSerif, body = FontFamily.SansSerif,
            label = FontFamily.Monospace, cardBody = FontFamily.SansSerif,
            corner = 0.dp, borderWidth = 2.dp, shadowOffset = 0.dp, shadowColor = Color.Transparent,
            upperCaseTitles = false, dark = false, cardStyle = CardStyle.RULED, backdrop = Backdrop.PLAIN,
        )
        Skin.BRUTAL -> SkinTokens(
            skin = skin,
            background = Color(0xFFFFE14D), onBackground = Color.Black,
            surface = Color.White, onSurface = Color.Black,
            accent = Color.Black, onAccent = Color(0xFFFFE14D),
            muted = Color(0xFF3D3D3D), border = Color.Black,
            bar = Color(0xFFFFE14D), onBar = Color.Black, highlight = Color(0xFFA7F3D0),
            display = FontFamily.SansSerif, body = FontFamily.SansSerif,
            label = FontFamily.SansSerif, cardBody = FontFamily.SansSerif,
            corner = 0.dp, borderWidth = 3.dp, shadowOffset = 5.dp, shadowColor = Color.Black,
            upperCaseTitles = true, dark = false, cardStyle = CardStyle.BOXED, backdrop = Backdrop.DOTS,
        )
        Skin.RETRO -> SkinTokens(
            skin = skin,
            background = Color(0xFF3A6EA5), onBackground = Color.White,
            surface = Color.White, onSurface = Color.Black,
            accent = Color.Black, onAccent = Color.White,
            muted = Color(0xFF444444), border = Color.Black,
            bar = Color(0xFFE6E6E6), onBar = Color.Black, highlight = Color(0xFFFFFFCC),
            display = FontFamily.Monospace, body = FontFamily.Monospace,
            label = FontFamily.Monospace, cardBody = FontFamily.Monospace,
            corner = 0.dp, borderWidth = 2.dp, shadowOffset = 4.dp, shadowColor = Color.Black,
            upperCaseTitles = false, dark = false, cardStyle = CardStyle.WINDOW, backdrop = Backdrop.PLAIN,
        )
        Skin.ROLODEX -> SkinTokens(
            skin = skin,
            background = Color(0xFF2E3B2F), onBackground = Color(0xFFF3EBD8),
            surface = Color(0xFFF7F0DC), onSurface = Color(0xFF2B2418),
            accent = Color(0xFFB23A2B), onAccent = Color(0xFFF7F0DC),
            muted = Color(0xFFB9C4A8), border = Color(0xFFD7CDB3),
            bar = Color(0xFF2E3B2F), onBar = Color(0xFFF3EBD8), highlight = Color(0xFFE9DFC6),
            display = FontFamily.SansSerif, body = FontFamily.SansSerif,
            label = FontFamily.SansSerif, cardBody = FontFamily.Monospace,
            corner = 10.dp, borderWidth = 0.dp, shadowOffset = 0.dp, shadowColor = Color.Transparent,
            upperCaseTitles = true, dark = true, cardStyle = CardStyle.INDEX, backdrop = Backdrop.PLAIN,
        )
        Skin.TERMINAL -> SkinTokens(
            skin = skin,
            background = Color(0xFF0C0A05), onBackground = Color(0xFFFFB000),
            surface = Color(0xFF0C0A05), onSurface = Color(0xFFFFB000),
            accent = Color(0xFFFFB000), onAccent = Color(0xFF0C0A05),
            muted = Color(0xFFA08B4F), border = Color(0xFFFFB000),
            bar = Color(0xFF0C0A05), onBar = Color(0xFFFFB000), highlight = Color(0xFF2A2208),
            display = FontFamily.Monospace, body = FontFamily.Monospace,
            label = FontFamily.Monospace, cardBody = FontFamily.Monospace,
            corner = 0.dp, borderWidth = 1.dp, shadowOffset = 0.dp, shadowColor = Color.Transparent,
            upperCaseTitles = false, dark = true, cardStyle = CardStyle.PROMPT, backdrop = Backdrop.SCANLINES,
        )
    }
}

val LocalSkin = staticCompositionLocalOf { Skins.tokens(Skin.SWISS) }

/** Applies a skin to everything below it, including stock Material 3 widgets. */
@Composable
fun SkinTheme(skin: Skin, content: @Composable () -> Unit) {
    val t = Skins.tokens(skin)
    val scheme = if (t.dark) {
        darkColorScheme(
            primary = t.accent, onPrimary = t.onAccent,
            background = t.background, onBackground = t.onBackground,
            surface = t.surface, onSurface = t.onSurface,
            surfaceVariant = t.highlight, onSurfaceVariant = t.muted,
            outline = t.border,
        )
    } else {
        lightColorScheme(
            primary = t.accent, onPrimary = t.onAccent,
            background = t.background, onBackground = t.onBackground,
            surface = t.surface, onSurface = t.onSurface,
            surfaceVariant = t.highlight, onSurfaceVariant = t.muted,
            outline = t.border,
        )
    }
    CompositionLocalProvider(LocalSkin provides t) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
