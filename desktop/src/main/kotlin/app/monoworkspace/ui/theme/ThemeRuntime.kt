package app.monoworkspace.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import app.monoworkspace.data.repo.AppSettings
import app.monoworkspace.ui.theme.fx.FxBus

/** Windows' "Choose your app mode" (Settings › Personalization › Colors). */
object WindowsAppearance {
    fun isDark(): Boolean = runCatching {
        if (!System.getProperty("os.name").lowercase().contains("win")) return@runCatching false
        val p = ProcessBuilder(
            "reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "/v", "AppsUseLightTheme",
        ).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        Regex("AppsUseLightTheme\\s+REG_DWORD\\s+0x(\\d+)").find(out)?.groupValues?.get(1) == "0"
    }.getOrDefault(false)
}

/** The theme that should be showing for these settings and the current Windows mode. */
fun resolveTheme(settings: AppSettings, systemDark: Boolean): ThemeSpec {
    val dark = when (settings.themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
    return if (dark) Themes.byId(settings.darkTheme)?.takeIf { it.isDark } ?: Themes.Observatory
    else Themes.byId(settings.lightTheme)?.takeIf { !it.isDark } ?: Themes.Mono
}

/**
 * Applies [theme] to [content] and animates changes: the old frame is kept
 * as a picture on top and the new theme is revealed through the incoming
 * theme's own reveal shape, starting where the user last clicked.
 */
@Composable
fun ThemeRevealHost(theme: ThemeSpec, reduceMotion: Boolean, content: @Composable (ThemeSpec) -> Unit) {
    val layer = rememberGraphicsLayer()
    var shown by remember { mutableStateOf(theme) }
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(theme.id) {
        if (theme.id == shown.id) return@LaunchedEffect
        if (reduceMotion) {
            shown = theme
            return@LaunchedEffect
        }
        snapshot = runCatching { layer.toImageBitmap() }.getOrNull()
        origin = FxBus.pointer ?: Offset(layer.size.width / 2f, layer.size.height / 2f)
        shown = theme
        progress.snapTo(0f)
        progress.animateTo(1f, tween(theme.fx.revealMillis, easing = FastOutSlowInEasing))
        snapshot = null
    }

    Box(
        Modifier
            .fillMaxSize()
            // Remember where the pointer is so reveals and bursts start under it.
            .onPointerEvent(PointerEventType.Move) { e -> FxBus.pointer = e.changes.first().position }
            .onPointerEvent(PointerEventType.Press) { e -> FxBus.pointer = e.changes.first().position }
            .onPointerEvent(PointerEventType.Exit) { FxBus.pointer = null },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
        ) {
            content(shown)
        }
        val old = snapshot
        if (old != null) {
            Canvas(Modifier.fillMaxSize()) {
                val hole = shown.fx.revealPath(size, origin, progress.value)
                clipPath(hole, ClipOp.Difference) { drawImage(old) }
            }
        }
    }
}
