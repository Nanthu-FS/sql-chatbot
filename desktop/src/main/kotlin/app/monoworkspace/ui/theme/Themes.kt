package app.monoworkspace.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.monoworkspace.ui.theme.fx.CitrusFx
import app.monoworkspace.ui.theme.fx.CyanotypeFx
import app.monoworkspace.ui.theme.fx.IndigoFx
import app.monoworkspace.ui.theme.fx.MonoFx
import app.monoworkspace.ui.theme.fx.ObservatoryFx
import app.monoworkspace.ui.theme.fx.SumiFx
import app.monoworkspace.ui.theme.fx.ThemeFx
import app.monoworkspace.ui.theme.fx.VelvetFx

/**
 * One complete look: surface palettes, signature brushes, fonts, corner
 * radius and its motion personality ([fx]).
 */
@Immutable
class ThemeSpec(
    val id: String,
    val name: String,
    val tagline: String,
    val isDark: Boolean,
    val main: SurfaceColors,
    val side: SurfaceColors,
    val card: SurfaceColors,
    val hot: SurfaceColors,
    /** Sidebar fill; may be a gradient. */
    val sideBrush: Brush,
    /** Highlighted tile fill; may be a gradient. */
    val hotBrush: Brush,
    /** Card cover fill (behind the page glyph) when a page has no cover image. */
    val cover: (androidx.compose.ui.geometry.Size) -> Brush,
    private val body: () -> FontFamily,
    private val display: () -> FontFamily,
    val displayWeight: FontWeight,
    val radius: Dp,
    val ruleWidth: Dp,
    val fx: ThemeFx,
) {
    val bodyFont: FontFamily by lazy(body)
    val displayFont: FontFamily by lazy(display)
}

private fun c(hex: Long) = Color(hex)
private fun a(hex: Long, alpha: Float) = Color(hex).copy(alpha = alpha)

private fun surface(
    background: Long, ink: Long, secondary: Long, tertiary: Long, hairline: Color, rule: Color, tint: Long,
    accent: Long, onAccent: Long, onInk: Long, destructive: Long = 0xFFD0021B, scrim: Color = a(0xFF000000, 0.32f),
) = SurfaceColors(c(background), c(ink), c(secondary), c(tertiary), hairline, rule, c(tint), c(accent), c(onAccent), c(onInk), c(destructive), scrim)

object Themes {
    val Mono = run {
        val main = surface(0xFFFFFFFF, 0xFF000000, 0xFF6B6B6B, 0xFF9A9A9A, c(0xFFE0E0E0), c(0xFF000000), 0xFFF2F2F2, 0xFF000000, 0xFFFFFFFF, 0xFFFFFFFF)
        ThemeSpec(
            id = "mono", name = "Mono", tagline = "Black on white. Square, ruled, quiet.", isDark = false,
            main = main, side = main, card = main,
            hot = surface(0xFF000000, 0xFFFFFFFF, 0xFFBDBDBD, 0xFF9A9A9A, c(0xFF333333), c(0xFFFFFFFF), 0xFF1A1A1A, 0xFFFFFFFF, 0xFF000000, 0xFF000000),
            sideBrush = SolidColor(c(0xFFFFFFFF)), hotBrush = SolidColor(c(0xFF000000)),
            cover = { SolidColor(c(0xFFF2F2F2)) },
            body = { BundledFonts.Inter }, display = { BundledFonts.Inter }, displayWeight = FontWeight.Bold,
            radius = 0.dp, ruleWidth = 1.dp, fx = MonoFx,
        )
    }

    val Indigo = ThemeSpec(
        id = "indigo", name = "Indigo Sashiko", tagline = "Aizome indigo cloth with white running stitches.", isDark = false,
        main = surface(0xFFEBE3CF, 0xFF1D3459, 0xFF4B6186, 0xFF5F7396, a(0xFF1D3459, 0.28f), c(0xFF1D3459), 0xFFE1D7BE, 0xFF2D4F80, 0xFFF4EEE0, 0xFFF4EEE0, 0xFFB3261E),
        side = surface(0xFF26467A, 0xFFF4EEE0, 0xFFB9C9E2, 0xFFB3C4DF, a(0xFFF4EEE0, 0.30f), a(0xFFF4EEE0, 0.55f), 0xFF3E6496, 0xFFF4EEE0, 0xFF1D3459, 0xFF1D3459, 0xFFFFB4A9),
        card = surface(0xFFF7F2E6, 0xFF1D3459, 0xFF4F6489, 0xFF5F7396, a(0xFF1D3459, 0.22f), a(0xFF1D3459, 0.6f), 0xFFEDE6D4, 0xFF2D4F80, 0xFFF4EEE0, 0xFFF4EEE0, 0xFFB3261E),
        hot = surface(0xFF2D4F80, 0xFFF5EFE0, 0xFFC9D6EA, 0xFFB9C9E2, a(0xFFF5EFE0, 0.3f), a(0xFFF5EFE0, 0.6f), 0xFF3B5E92, 0xFFF5EFE0, 0xFF1D3459, 0xFF1D3459, 0xFFFFB4A9),
        sideBrush = Brush.verticalGradient(listOf(c(0xFF213F6C), c(0xFF2D4F80))),
        hotBrush = SolidColor(c(0xFF2D4F80)),
        cover = { s -> Brush.linearGradient(listOf(c(0xFF2C4D7E), c(0xFF203D6B)), Offset.Zero, Offset(s.width, s.height)) },
        body = { BundledFonts.Hanken }, display = { BundledFonts.Newsreader }, displayWeight = FontWeight.Normal,
        radius = 4.dp, ruleWidth = 2.dp, fx = IndigoFx,
    )

    val Citrus = ThemeSpec(
        id = "citrus", name = "Citrus Slice", tagline = "Lemon, lime and blood orange on crisp white.", isDark = false,
        main = surface(0xFFFFFFFF, 0xFF1D2410, 0xFF4F5E35, 0xFF5F6F3C, c(0xFFE7EFD4), c(0xFFC4D39A), 0xFFF4F9E4, 0xFFC2401D, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFB3261E),
        side = surface(0xFFF4F9E4, 0xFF1D2410, 0xFF4E6A1C, 0xFF5F7340, c(0xFFDBE6C2), c(0xFFD3E0B4), 0xFFFFE27A, 0xFFC2401D, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFB3261E),
        card = surface(0xFFFFFFFF, 0xFF1D2410, 0xFF5E6E3F, 0xFF5F6F3C, c(0xFFD6E2B8), c(0xFFC4D39A), 0xFFF7FBEC, 0xFFC2401D, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFB3261E),
        hot = surface(0xFFC2401D, 0xFFFFFFFF, 0xFFFFE0D0, 0xFFFFD0BC, a(0xFFFFFFFF, 0.3f), a(0xFFFFFFFF, 0.6f), 0xFFD0522E, 0xFFFFD21F, 0xFF1D2410, 0xFFC2401D, 0xFFFFFFFF),
        sideBrush = SolidColor(c(0xFFF4F9E4)),
        hotBrush = Brush.linearGradient(listOf(c(0xFFD9542B), c(0xFFC2401D))),
        cover = { SolidColor(c(0xFFE9F8C3)) },
        body = { BundledFonts.Nunito }, display = { BundledFonts.Nunito }, displayWeight = FontWeight.ExtraBold,
        radius = 14.dp, ruleWidth = 1.dp, fx = CitrusFx,
    )

    val Sumi = ThemeSpec(
        id = "sumi", name = "Sumi Ink", tagline = "Rice paper, ink-wash fades and one vermilion seal.", isDark = false,
        main = surface(0xFFF2F3F0, 0xFF1C1D1F, 0xFF4E5155, 0xFF5F6266, c(0xFFDFE1DD), c(0xFF8D9094), 0xFFE7E8E5, 0xFFB7372C, 0xFFF9F9F7, 0xFFF2F3F0, 0xFFB7372C),
        side = surface(0xFFE7E8E5, 0xFF1C1D1F, 0xFF55595D, 0xFF5C6064, c(0xFFD2D4D0), c(0xFF8D9094), 0xFFD8DAD6, 0xFF1C1D1F, 0xFFF2F3F0, 0xFFF2F3F0, 0xFFB7372C),
        card = surface(0xFFF9F9F7, 0xFF1C1D1F, 0xFF53565A, 0xFF5F6266, c(0xFFD4D6D2), c(0xFF8D9094), 0xFFEFF0EC, 0xFFB7372C, 0xFFF9F9F7, 0xFFF9F9F7, 0xFFB7372C),
        hot = surface(0xFF1C1D1F, 0xFFF2F3F0, 0xFFB9BCBF, 0xFF9A9DA1, a(0xFFF2F3F0, 0.2f), a(0xFFF2F3F0, 0.5f), 0xFF2A2B2E, 0xFFB7372C, 0xFFF9F9F7, 0xFF1C1D1F, 0xFFFF8A7A),
        sideBrush = SolidColor(c(0xFFE7E8E5)),
        hotBrush = SolidColor(c(0xFF1C1D1F)),
        cover = { s ->
            Brush.radialGradient(
                listOf(a(0xFF1C1D1F, 0.88f), a(0xFF1C1D1F, 0.4f), a(0xFF1C1D1F, 0f)),
                center = Offset(s.width * 0.8f, s.height * 0.18f), radius = s.maxDimension * 0.7f,
            )
        },
        body = { BundledFonts.Hanken }, display = { BundledFonts.Cormorant }, displayWeight = FontWeight.Medium,
        radius = 2.dp, ruleWidth = 1.dp, fx = SumiFx,
    )

    val Cyanotype = ThemeSpec(
        id = "cyanotype", name = "Cyanotype", tagline = "Prussian-blue sun prints with pale botanical light.", isDark = true,
        main = surface(0xFF0F3A60, 0xFFF2FBFF, 0xFFA9DCEF, 0xFF8FC6DE, c(0xFF245A81), c(0xFF5B97BF), 0xFF134A74, 0xFFEEF9FD, 0xFF0F3A60, 0xFF0F3A60, 0xFFFF9A8C, a(0xFF03101E, 0.55f)),
        side = surface(0xFF0A2C4C, 0xFFE6F6FC, 0xFF8EC3DD, 0xFF7FB3CF, c(0xFF1B4A71), c(0xFF2A6392), 0xFF1D5B8A, 0xFFEEF9FD, 0xFF0F3A60, 0xFF0A2C4C, 0xFFFF9A8C, a(0xFF03101E, 0.55f)),
        card = surface(0xFF134A74, 0xFFF2FBFF, 0xFFA9DCEF, 0xFF8FC6DE, c(0xFF2F6D98), c(0xFF5B97BF), 0xFF185783, 0xFFEEF9FD, 0xFF0F3A60, 0xFF134A74, 0xFFFF9A8C, a(0xFF03101E, 0.55f)),
        hot = surface(0xFFEEF9FD, 0xFF0F3A60, 0xFF2A6592, 0xFF3D78A3, c(0xFFC5E3F0), c(0xFF2A6392), 0xFFDDF1F9, 0xFF0F3A60, 0xFFEEF9FD, 0xFFEEF9FD, 0xFFB3261E, a(0xFF03101E, 0.55f)),
        sideBrush = SolidColor(c(0xFF0A2C4C)),
        hotBrush = SolidColor(c(0xFFEEF9FD)),
        cover = { s -> Brush.linearGradient(listOf(c(0xFF092A4A), c(0xFF0C3558), c(0xFF0F3F67)), Offset.Zero, Offset(s.width * 0.6f, s.height)) },
        body = { BundledFonts.Hanken }, display = { BundledFonts.Cormorant }, displayWeight = FontWeight.Medium,
        radius = 3.dp, ruleWidth = 1.dp, fx = CyanotypeFx,
    )

    val Velvet = ThemeSpec(
        id = "velvet", name = "Velvet Rose", tagline = "Aubergine velvet, rose-gold edges and soft pink glow.", isDark = true,
        main = surface(0xFF2B1833, 0xFFF7EBEF, 0xFFDCB4C3, 0xFFB38FA0, c(0xFF3F2A48), c(0xFF8A6160), 0xFF36203D, 0xFFE6B29C, 0xFF2B1833, 0xFF2B1833, 0xFFFF8FA3, a(0xFF080210, 0.6f)),
        side = surface(0xFF1F0F25, 0xFFF3E2E8, 0xFFCFA9BA, 0xFFB38FA0, c(0xFF35213C), c(0xFF5B3F57), 0xFF3D2446, 0xFFE6B29C, 0xFF1F0F25, 0xFF1F0F25, 0xFFFF8FA3, a(0xFF080210, 0.6f)),
        card = surface(0xFF36203D, 0xFFF7EBEF, 0xFFD2AABB, 0xFFB38FA0, c(0xFF5A3C58), c(0xFF8A6160), 0xFF432849, 0xFFE6B29C, 0xFF2B1833, 0xFF36203D, 0xFFFF8FA3, a(0xFF080210, 0.6f)),
        hot = surface(0xFFE6B5AE, 0xFF2B1833, 0xFF5A2A45, 0xFF6E3A57, c(0xFFC98E99), c(0xFF5A2A45), 0xFFEDC3BA, 0xFF2B1833, 0xFFF4CFBD, 0xFFF4CFBD, 0xFF8E1B3A, a(0xFF080210, 0.6f)),
        sideBrush = SolidColor(c(0xFF1F0F25)),
        hotBrush = Brush.linearGradient(listOf(c(0xFFF4CFBD), c(0xFFD99AA6))),
        cover = { s -> Brush.linearGradient(listOf(c(0xFF6D2B52), c(0xFF3C1D46), c(0xFF7E4558)), Offset.Zero, Offset(s.width, s.height)) },
        body = { BundledFonts.Manrope }, display = { BundledFonts.Cormorant }, displayWeight = FontWeight.SemiBold,
        radius = 14.dp, ruleWidth = 1.dp, fx = VelvetFx,
    )

    val Observatory = ThemeSpec(
        id = "observatory", name = "Observatory", tagline = "Night sky, orbit rings and a warm star-gold accent.", isDark = true,
        main = surface(0xFF060913, 0xFFE6EBF7, 0xFF9BA8C9, 0xFF8390B3, c(0xFF131B30), c(0xFF2A3758), 0xFF0C1326, 0xFFE2B865, 0xFF070A14, 0xFF060913, 0xFFFF8A80, a(0xFF000000, 0.6f)),
        side = surface(0xFF090D1B, 0xFFD7DFEF, 0xFF8E9ABD, 0xFF74819F, c(0xFF182241), c(0xFF1F2B4D), 0xFF16203A, 0xFFE2B865, 0xFF070A14, 0xFF090D1B, 0xFFFF8A80, a(0xFF000000, 0.6f)),
        card = surface(0xFF0C1326, 0xFFE6EBF7, 0xFF8E9ABD, 0xFF8390B3, c(0xFF1F2B4D), c(0xFF2A3758), 0xFF111A33, 0xFFE2B865, 0xFF070A14, 0xFF0C1326, 0xFFFF8A80, a(0xFF000000, 0.6f)),
        hot = surface(0xFFE2B865, 0xFF070A14, 0xFF3F2F0F, 0xFF5A4518, c(0xFFC79F52), c(0xFF3F2F0F), 0xFFEAC57C, 0xFF070A14, 0xFFE2B865, 0xFFE2B865, 0xFF8E1B1B, a(0xFF000000, 0.6f)),
        sideBrush = SolidColor(c(0xFF090D1B)),
        hotBrush = SolidColor(c(0xFFE2B865)),
        cover = { s ->
            Brush.radialGradient(
                listOf(a(0xFF607ACC, 0.32f), c(0xFF0E1831)),
                center = Offset(s.width * 0.78f, s.height * 0.28f), radius = s.maxDimension * 0.7f,
            )
        },
        body = { BundledFonts.Manrope }, display = { BundledFonts.Cormorant }, displayWeight = FontWeight.Light,
        radius = 4.dp, ruleWidth = 1.dp, fx = ObservatoryFx,
    )

    val all: List<ThemeSpec> = listOf(Mono, Indigo, Citrus, Sumi, Cyanotype, Velvet, Observatory)
    fun byId(id: String?): ThemeSpec? = all.firstOrNull { it.id == id }
}
