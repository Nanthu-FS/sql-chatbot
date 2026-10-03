package com.genshincalc.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.genshincalc.core.model.Element

private val Gold = Color(0xFFE2B45C)
private val GoldDark = Color(0xFF8A6420)
private val Ink = Color(0xFF14161F)
private val InkSurface = Color(0xFF1C1F2B)
private val InkSurfaceHigh = Color(0xFF262A39)

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF2A1E05),
    primaryContainer = Color(0xFF4A3A16),
    onPrimaryContainer = Color(0xFFFFE3A8),
    secondary = Color(0xFF9DB4E8),
    onSecondary = Color(0xFF0E1A33),
    secondaryContainer = Color(0xFF2B3550),
    onSecondaryContainer = Color(0xFFD8E2FF),
    tertiary = Color(0xFF8FD6C2),
    background = Ink,
    onBackground = Color(0xFFE6E3DA),
    surface = Ink,
    onSurface = Color(0xFFE6E3DA),
    surfaceVariant = InkSurfaceHigh,
    onSurfaceVariant = Color(0xFFC3C0B5),
    surfaceContainer = InkSurface,
    surfaceContainerHigh = InkSurfaceHigh,
    surfaceContainerHighest = Color(0xFF30344A),
    surfaceContainerLow = Color(0xFF181A25),
    outline = Color(0xFF6F6C64),
    outlineVariant = Color(0xFF3A3D4C),
)

private val LightColors = lightColorScheme(
    primary = GoldDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3A8),
    onPrimaryContainer = Color(0xFF2A1E05),
    secondary = Color(0xFF3F5A91),
    secondaryContainer = Color(0xFFD8E2FF),
    onSecondaryContainer = Color(0xFF0E1A33),
    tertiary = Color(0xFF2E7D6B),
    background = Color(0xFFF8F5EE),
    surface = Color(0xFFF8F5EE),
    surfaceVariant = Color(0xFFE9E4D8),
    surfaceContainer = Color(0xFFF1ECE1),
    surfaceContainerHigh = Color(0xFFEBE5D8),
    surfaceContainerHighest = Color(0xFFE4DDCF),
    surfaceContainerLow = Color(0xFFF5F1E8),
)

@Composable
fun GenshinCalcTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}

val Element.color: Color
    get() = when (this) {
        Element.PYRO -> Color(0xFFEF7938)
        Element.HYDRO -> Color(0xFF4CC2F1)
        Element.ANEMO -> Color(0xFF74C2A8)
        Element.ELECTRO -> Color(0xFFB08FC2)
        Element.DENDRO -> Color(0xFFA5C83B)
        Element.CRYO -> Color(0xFF9FD6E3)
        Element.GEO -> Color(0xFFFAB72E)
        Element.PHYSICAL -> Color(0xFFBDBDBD)
    }

fun rarityColor(rarity: Int): Color = when (rarity) {
    5 -> Color(0xFFE2A54A)
    4 -> Color(0xFFA77CD6)
    3 -> Color(0xFF5B9BD5)
    2 -> Color(0xFF6BAF7A)
    else -> Color(0xFF9E9E9E)
}
