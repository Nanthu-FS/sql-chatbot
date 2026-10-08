package app.monoworkspace.ui.theme.fx

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import app.monoworkspace.ui.theme.SurfaceColors
import kotlin.math.hypot

/** What just happened, so a theme can celebrate it in its own way. */
enum class BurstKind {
    /** A page, database or row was created. */
    Create,

    /** A page was starred. */
    Favorite,

    /** A to-do was checked off. */
    Complete,

    /** Something went to the trash. */
    Delete,
}

/**
 * A theme's motion personality. Every hook has a calm default (the Mono
 * look), so a theme only overrides what it wants to make its own. All hooks
 * must respect [app.monoworkspace.ui.theme.LocalReduceMotion] (no motion at
 * all) and [app.monoworkspace.ui.theme.LocalAmbientMotion] (no looping
 * ambient motion). Colors come from the theme's palette, never hard-coded
 * outside the theme's own file.
 */
@Stable
abstract class ThemeFx {
    /**
     * Ambient layer drawn behind every screen's content in the main pane
     * (screens are transparent over it). Fills [modifier]'s bounds. Keep it
     * subtle: it sits behind text. Loop only while LocalAmbientMotion is true.
     */
    @Composable
    open fun Backdrop(modifier: Modifier) {
    }

    /**
     * Decoration behind and around the Home greeting ("Good evening.").
     * [modifier] fills a box about 900 x 150 dp with the greeting text at the
     * left, vertically centered; draw behind/around it, never over the text.
     */
    @Composable
    open fun HeroOrnament(modifier: Modifier) {
    }

    /** True when [drawSelection] uses [DrawScope] time and needs redrawing every frame. */
    open val animatedSelection: Boolean get() = false

    /**
     * Marker for the selected row in the sidebar (and the command palette's
     * current row). [presence] goes 0 to 1 when the row becomes selected;
     * [seconds] is a running clock (only advances when [animatedSelection]).
     * Draw within [DrawScope.size]; the row's text is drawn on top.
     */
    open fun DrawScope.drawSelection(presence: Float, seconds: Float, colors: SurfaceColors) {
        val h = size.height
        drawRect(colors.tint.copy(alpha = colors.tint.alpha * presence))
        drawRect(colors.accent, Offset(0f, h * (1f - presence) / 2f), Size(3f * density, h * presence))
    }

    /**
     * One-shot celebration at [origin] (window coordinates, px) drawn in a
     * full-window overlay above everything. Call [onFinished] when done
     * (keep it under ~1.2 s).
     */
    @Composable
    open fun Burst(kind: BurstKind, origin: Offset, colors: SurfaceColors, onFinished: () -> Unit) {
        val p = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            p.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
            onFinished()
        }
        Canvas(Modifier.fillMaxSize()) {
            val r = 8f * density + p.value * 46f * density
            drawCircle(colors.accent.copy(alpha = (1f - p.value) * 0.9f), r, origin, style = Stroke(width = 2f * density * (1f - p.value) + 0.5f))
        }
    }

    /** Screen transition into a page ([pop] = going back). */
    open fun enter(pop: Boolean): EnterTransition {
        val dir = if (pop) -1 else 1
        return fadeIn(tween(220, delayMillis = 70)) +
            scaleIn(spring(dampingRatio = 0.82f, stiffness = 380f), initialScale = if (pop) 1.04f else 0.965f) +
            slideInHorizontally(spring(dampingRatio = 0.86f, stiffness = 420f)) { dir * it / 14 }
    }

    /** Screen transition out of a page. */
    open fun exit(pop: Boolean): ExitTransition =
        fadeOut(tween(140)) + scaleOut(tween(260, easing = FastOutSlowInEasing), targetScale = if (pop) 0.965f else 1.03f)

    /** How long switching TO this theme takes. */
    open val revealMillis: Int get() = 650

    /**
     * Shape of the newly applied theme while switching to it: at [progress]
     * 0 nothing is visible, at 1 it must cover the whole [size]. [origin] is
     * where the user clicked.
     */
    open fun revealPath(size: Size, origin: Offset, progress: Float): Path {
        val far = listOf(Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height))
            .maxOf { hypot(it.x - origin.x, it.y - origin.y) }
        return Path().apply { addOval(androidx.compose.ui.geometry.Rect(origin, far * progress + 1f)) }
    }

    /**
     * Optional effect that follows the mouse, drawn in the full-window
     * overlay. [pointer] returns the current position (window px) or null
     * when the mouse is outside. Only active while LocalAmbientMotion is true.
     */
    @Composable
    open fun PointerTrail(pointer: () -> Offset?, colors: SurfaceColors) {
    }
}

/** The calm Mono defaults. */
object MonoFx : ThemeFx()

/** Queue of bursts waiting to be drawn by the overlay. */
@Stable
object FxBus {
    class Fired(val id: Long, val kind: BurstKind, val origin: Offset)

    internal val active = mutableStateListOf<Fired>()
    private var next = 0L

    /** Last known mouse position in window px; bursts without an origin start here. */
    @Volatile
    var pointer: Offset? = null

    fun fire(kind: BurstKind, origin: Offset? = null) {
        val at = origin ?: pointer ?: return
        if (active.size > 12) active.removeAt(0)
        active.add(Fired(next++, kind, at))
    }

    internal fun done(f: Fired) {
        active.remove(f)
    }
}

/** Shared easing helpers for theme files. */
object FxMath {
    fun clamp01(v: Float) = v.coerceIn(0f, 1f)

    /** 0 → 1 → 0 bump over [p] in 0..1. */
    fun bump(p: Float) = (1f - kotlin.math.abs(p * 2f - 1f)).coerceIn(0f, 1f)

    /** Deterministic pseudo-random in 0..1 for index [i] and salt [s]. */
    fun hash(i: Int, s: Int = 0): Float {
        var x = i * 374761393 + s * 668265263
        x = (x xor (x ushr 13)) * 1274126177
        x = x xor (x ushr 16)
        return (x and 0xFFFFFF) / 16777215f
    }

    val Linear = LinearEasing
}

/** Runs [ThemeFx.drawSelection] inside a box of [box] size (the sliding marker can be taller than the row). */
fun ThemeFx.drawSelectionScaled(scope: DrawScope, presence: Float, seconds: Float, colors: SurfaceColors, box: Size) {
    with(scope) {
        val w = box.width
        val h = box.height
        // Draw into a sub-region the size of the moving marker.
        drawContext.canvas.save()
        drawContext.canvas.clipRect(-w, -h, w * 2, h * 2)
        val old = drawContext.size
        drawContext.size = box
        drawSelection(presence, seconds, colors)
        drawContext.size = old
        drawContext.canvas.restore()
    }
}

/** A seconds clock that only ticks while [running]; frozen at 0 otherwise. */
@Composable
fun rememberFxClock(running: Boolean): androidx.compose.runtime.State<Float> {
    val state = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val ambient = app.monoworkspace.ui.theme.LocalAmbientMotion.current
    LaunchedEffect(running && ambient) {
        if (!(running && ambient)) return@LaunchedEffect
        val start = androidx.compose.runtime.withFrameNanos { it }
        while (true) {
            androidx.compose.runtime.withFrameNanos { now -> state.floatValue = (now - start) / 1_000_000_000f }
        }
    }
    return state
}
