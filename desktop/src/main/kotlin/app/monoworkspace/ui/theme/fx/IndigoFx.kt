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
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ImageCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import app.monoworkspace.ui.theme.LocalAmbientMotion
import app.monoworkspace.ui.theme.LocalReduceMotion
import app.monoworkspace.ui.theme.LocalSurface
import app.monoworkspace.ui.theme.LocalTheme
import app.monoworkspace.ui.theme.SurfaceColors
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Indigo Sashiko: aizome indigo cloth stitched with white running stitches.
 *
 * - Backdrop: a faint seigaiha (wave-scale) pattern in dashed stitches. Every
 *   few seconds one row of waves gets stitched, dashes appearing left to right
 *   with a needle dot leading, then the row rests.
 * - HeroOrnament: a running-stitch line sews itself under the greeting once,
 *   then an indigo patch with a stitched white border is sewn on to the right.
 * - drawSelection: a stitched outline that draws itself with [presence] and
 *   marches slowly, plus a solid accent bar on the left.
 * - Bursts: Create = thread spokes, Favorite = a hitomezashi star stitched
 *   outward, Complete = a cross-stitch X, Delete = loose stitches unravelling.
 * - enter/exit: the cloth is pulled in (slide, small scale, fade).
 * - revealPath: an indigo dye blob, its edge wobbling on low sine harmonics.
 */
object IndigoFx : ThemeFx() {

    override val animatedSelection: Boolean get() = true

    override val revealMillis: Int get() = 820

    // ---------------------------------------------------------------- Backdrop

    @Composable
    override fun Backdrop(modifier: Modifier) {
        if (LocalReduceMotion.current) return
        val ambient = LocalAmbientMotion.current
        val ink = LocalSurface.current.ink
        val dens = LocalDensity.current.density
        var px by remember { mutableStateOf(IntSize.Zero) }
        // The lattice is keyed on 128 px buckets, so a window drag rebuilds it only when a bucket is crossed.
        val bw = (px.width + 127) / 128 * 128
        val bh = (px.height + 127) / 128 * 128
        val pattern = remember(bw, bh, ink, dens) {
            if (bw <= 0 || bh <= 0) null else buildSeigaiha(bw, bh, dens, ink)
        }
        // Only a true clock while ambient motion is allowed; frozen at 0 otherwise.
        val clock = rememberFxClock(running = true)

        Box(modifier.onSizeChanged { px = it }) {
            // The still lattice is rendered once into a bitmap and blitted per frame, cropped to the window.
            Canvas(Modifier.fillMaxSize()) {
                val s = pattern ?: return@Canvas
                val w = min(size.width.roundToInt(), s.width)
                val h = min(size.height.roundToInt(), s.height)
                drawImage(s.bitmap, srcSize = IntSize(w, h), dstSize = IntSize(w, h), alpha = BACKDROP_ALPHA)
            }
            if (ambient) {
                Canvas(Modifier.fillMaxSize()) {
                    val s = pattern ?: return@Canvas
                    // Only rows inside the visible height can be stitched; row 0 is skipped (it is cut off at the top).
                    val visible = min((size.height / s.step).toInt(), s.rows.size - 1)
                    val span = visible - 1
                    if (span < 1) return@Canvas
                    val t = clock.value
                    val idx = floor(t / CYCLE_SECONDS).toInt()
                    val local = t - idx * CYCLE_SECONDS
                    val row = 1 + (FxMath.hash(idx, 41) * span).toInt().coerceAtMost(span - 1)
                    val p = FxMath.clamp01(local / STITCH_SECONDS)
                    val a = ACTIVE_ALPHA * (1f - smooth(STITCH_SECONDS + HOLD_SECONDS, CYCLE_SECONDS, local))
                    if (p <= 0f || a <= 0.002f) return@Canvas
                    val reach = size.width * p
                    clipRect(0f, 0f, reach, size.height) {
                        drawPath(s.rows[row], ink, alpha = a, style = s.stroke)
                    }
                    if (p < 1f) drawCircle(ink, radius = 2.2f * dens, center = Offset(reach, s.rowY[row]), alpha = 0.55f)
                }
            }
        }
    }

    // ----------------------------------------------------------- HeroOrnament

    @Composable
    override fun HeroOrnament(modifier: Modifier) {
        val reduce = LocalReduceMotion.current
        val surface = LocalSurface.current
        val ink = surface.ink
        val cream = surface.onInk
        val cover = LocalTheme.current.cover
        val dens = LocalDensity.current.density
        // One-shot intro: the stitch line sews itself, then the patch goes on.
        val sew = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            if (reduce) sew.snapTo(1f) else sew.animateTo(1f, tween(HERO_SEW_MILLIS, easing = LinearEasing))
        }
        Canvas(modifier) {
            val p = sew.value
            val w = size.width
            val h = size.height

            // Running stitch under the greeting, sewn left to right.
            val lineY = min(HERO_LINE_TOP * dens, h - 8f * dens)
            val reveal = w * HERO_LINE_FRACTION * p
            if (reveal > 0.5f) {
                drawCircle(ink, radius = 1.8f * dens, center = Offset(0f, lineY), alpha = 0.6f)
                drawPath(
                    Path().apply {
                        moveTo(0f, lineY)
                        lineTo(reveal, lineY)
                    },
                    ink,
                    alpha = 0.6f,
                    style = Stroke(width = 1.6f * dens, cap = StrokeCap.Butt, pathEffect = stitch(5f * dens, 3f * dens)),
                )
                if (p < 1f) drawCircle(ink, radius = 2.4f * dens, center = Offset(reveal, lineY), alpha = 0.85f)
            }

            // Repaired indigo patch with a stitched white border, right of the greeting.
            val a = smooth(0.55f, 1f, p)
            if (a > 0f) {
                val s = HERO_PATCH * dens
                // The hero box spans the content width; the patch sits in the right third, clear of the greeting.
                val left = w * 0.72f
                withTransform({
                    translate(left, 6f * dens)
                    rotate(-4f, Offset(s / 2f, s / 2f))
                }) {
                    drawRoundRect(
                        brush = cover(Size(s, s)),
                        topLeft = Offset.Zero,
                        size = Size(s, s),
                        cornerRadius = CornerRadius(4f * dens),
                        alpha = a,
                    )
                    val inset = 6f * dens
                    drawRoundRect(
                        color = cream,
                        topLeft = Offset(inset, inset),
                        size = Size(s - 2f * inset, s - 2f * inset),
                        cornerRadius = CornerRadius(2f * dens),
                        alpha = 0.85f * a,
                        style = Stroke(width = 1.6f * dens, pathEffect = stitch(4f * dens, 2.5f * dens)),
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------- Selection

    override fun DrawScope.drawSelection(presence: Float, seconds: Float, colors: SurfaceColors) {
        if (presence <= 0f) return
        val w = size.width
        val h = size.height
        val d = density
        // A faint wash, then the stitched outline drawing itself around the row.
        drawRect(colors.tint.copy(alpha = colors.tint.alpha * 0.5f * presence))
        val inset = 2.5f * d
        val xs = floatArrayOf(inset, w - inset, w - inset, inset)
        val ys = floatArrayOf(inset, inset, h - inset, h - inset)
        val outline = Path().apply { partialPolyline(xs, ys, presence, closed = true) }
        val marchPhase = (seconds * 5f * d) % (8f * d)
        drawPath(
            outline,
            colors.ink,
            alpha = 0.9f,
            style = Stroke(width = 1.2f * d, cap = StrokeCap.Butt, pathEffect = stitch(5f * d, 3f * d, marchPhase)),
        )
        // Solid accent bar on the left, growing with presence.
        drawRect(colors.accent, Offset(0f, h * (1f - presence) / 2f), Size(3f * d, h * presence))
    }

    // ----------------------------------------------------------------- Bursts

    @Composable
    override fun Burst(kind: BurstKind, origin: Offset, colors: SurfaceColors, onFinished: () -> Unit) {
        val p = remember { Animatable(0f) }
        val finished by rememberUpdatedState(onFinished)
        val millis = when (kind) {
            BurstKind.Create -> 820
            BurstKind.Favorite -> 900
            BurstKind.Complete -> 560
            BurstKind.Delete -> 1000
        }
        LaunchedEffect(Unit) {
            // finally: onFinished also runs if the overlay drops this burst before it ends.
            try {
                p.animateTo(1f, tween(millis, easing = LinearEasing))
            } finally {
                finished()
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val t = p.value
            val ox = origin.x
            val oy = origin.y
            val ink = colors.ink
            val halo = colors.onInk
            when (kind) {
                BurstKind.Create -> burstCreate(t, ox, oy, ink, halo)
                BurstKind.Favorite -> burstFavorite(t, ox, oy, ink, halo)
                BurstKind.Complete -> burstComplete(t, ox, oy, ink, halo)
                BurstKind.Delete -> burstDelete(t, ox, oy, ink)
            }
        }
    }

    // ------------------------------------------------------------- Transitions

    /**
     * Cloth pulled in: slides from the side and fades up. Compose transitions can
     * only scale uniformly, so the "skew" is a slight uniform squeeze (0.96 to 1).
     */
    override fun enter(pop: Boolean): EnterTransition {
        val dir = if (pop) -1 else 1
        return fadeIn(tween(240, delayMillis = 60)) +
            scaleIn(spring(dampingRatio = 0.9f, stiffness = 340f), initialScale = if (pop) 1.012f else 0.96f) +
            slideInHorizontally(spring(dampingRatio = 0.86f, stiffness = 360f)) { dir * it / 16 }
    }

    override fun exit(pop: Boolean): ExitTransition {
        val dir = if (pop) -1 else 1
        return fadeOut(tween(150)) +
            scaleOut(tween(240, easing = FastOutSlowInEasing), targetScale = if (pop) 1.01f else 0.985f) +
            slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { -dir * it / 22 }
    }

    // ------------------------------------------------------------ Dye reveal

    /**
     * Indigo dye spreading from [origin]. The edge is a radius wobbling on three
     * low-frequency harmonics (amplitude at most 10.5%). At progress 1 the base
     * radius is 1.22 x the farthest corner, so the minimum radius (x0.895) is
     * still about 1.09 x that corner distance and the whole [size] is covered.
     */
    override fun revealPath(size: Size, origin: Offset, progress: Float): Path {
        val path = Path()
        val p = FxMath.clamp01(progress)
        if (p <= 0f) return path
        val far = listOf(Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height))
            .maxOf { hypot(it.x - origin.x, it.y - origin.y) }
        val u = 1f - p
        val grow = 1f - u * u * u
        val radius = far * 1.22f * grow
        val drift = p * 2.6f
        val steps = 128
        for (i in 0..steps) {
            val th = TAU * i / steps
            val wob = 0.05f * sin(2f * th + 1.1f + drift) +
                0.035f * sin(3f * th + 0.4f - drift * 1.3f) +
                0.02f * sin(5f * th + 2.2f + drift * 0.7f)
            val r = radius * (1f + wob)
            val x = origin.x + cos(th) * r
            val y = origin.y + sin(th) * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }
}

// ------------------------------------------------------------------- constants

private const val TAU = 6.2831855f
private const val BACKDROP_ALPHA = 0.075f
private const val ACTIVE_ALPHA = 0.2f
private const val CYCLE_SECONDS = 7.5f
private const val STITCH_SECONDS = 2.6f
private const val HOLD_SECONDS = 1.1f
private const val HERO_SEW_MILLIS = 1500
private const val HERO_LINE_TOP = 72f
private const val HERO_LINE_FRACTION = 0.56f
private const val HERO_PATCH = 58f

// --------------------------------------------------------------------- helpers

private fun smooth(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun easeOut(t: Float): Float {
    val u = 1f - t.coerceIn(0f, 1f)
    return 1f - u * u * u
}

/** A running-stitch dash effect; lengths are in px. */
private fun stitch(on: Float, off: Float, phase: Float = 0f): PathEffect =
    PathEffect.dashPathEffect(floatArrayOf(on, off), phase)

/**
 * Adds the first [fraction] of a polyline (or closed polygon) to this path as
 * one contour, so a dash effect runs continuously across its corners.
 */
private fun Path.partialPolyline(xs: FloatArray, ys: FloatArray, fraction: Float, closed: Boolean) {
    val n = xs.size
    val segs = if (closed) n else n - 1
    val lens = FloatArray(segs)
    var total = 0f
    for (i in 0 until segs) {
        val j = (i + 1) % n
        lens[i] = hypot(xs[j] - xs[i], ys[j] - ys[i])
        total += lens[i]
    }
    var remain = total * fraction.coerceIn(0f, 1f)
    moveTo(xs[0], ys[0])
    for (i in 0 until segs) {
        if (remain <= 0f) break
        val j = (i + 1) % n
        val take = min(remain, lens[i])
        val t = if (lens[i] > 0f) take / lens[i] else 0f
        lineTo(xs[i] + (xs[j] - xs[i]) * t, ys[i] + (ys[j] - ys[i]) * t)
        remain -= take
    }
}

// ------------------------------------------------------------ seigaiha lattice

private class Seigaiha(
    val width: Int,
    val height: Int,
    val step: Float,
    val rows: Array<Path>,
    val rowY: FloatArray,
    val bitmap: ImageBitmap,
    val stroke: Stroke,
)

/**
 * Seigaiha waves: each cell is four concentric upper half-arcs, and every
 * other row is offset by half a cell. Each row is one contour per arc so the
 * dashes restart on every wave. The full lattice is rasterized once into a
 * bitmap (sized in 128 px buckets so resizing does not re-render every frame).
 */
private fun buildSeigaiha(w: Int, h: Int, dens: Float, ink: Color): Seigaiha {
    val r = 26f * dens
    val cols = ceil(w / (2f * r)).toInt() + 3
    val rowCount = ceil(h / r).toInt() + 2
    val rowY = FloatArray(rowCount) { it * r }
    val rows = Array(rowCount) { row ->
        val cy = rowY[row]
        val shift = if (row % 2 == 1) r else 0f
        Path().also { path ->
            for (c in -1..cols) {
                val cx = c * 2f * r + shift
                for (k in 0 until 4) {
                    val rad = r * (1f - 0.25f * k)
                    path.arcTo(Rect(cx - rad, cy - rad, cx + rad, cy + rad), 180f, 180f, true)
                }
            }
        }
    }
    val stroke = Stroke(width = 1.15f * dens, cap = StrokeCap.Butt, pathEffect = stitch(4f * dens, 2.6f * dens))
    val bw = ((w + 127) / 128) * 128
    val bh = ((h + 127) / 128) * 128
    val bitmap = ImageBitmap(bw, bh)
    CanvasDrawScope().draw(Density(dens), LayoutDirection.Ltr, ImageCanvas(bitmap), Size(bw.toFloat(), bh.toFloat())) {
        for (row in rows) drawPath(row, ink, style = stroke)
    }
    return Seigaiha(w, h, r, rows, rowY, bitmap, stroke)
}

// --------------------------------------------------------------------- bursts

/** Thread spokes radiating from a knot, like a burst of thread. */
private fun DrawScope.burstCreate(t: Float, ox: Float, oy: Float, ink: Color, halo: Color) {
    val d = density
    val e = easeOut(t)
    val fade = 1f - smooth(0.35f, 1f, t)
    val n = 14
    for (i in 0 until n) {
        val ang = TAU * i / n + (FxMath.hash(i, 3) - 0.5f) * 0.28f
        val c = cos(ang)
        val s = sin(ang)
        val r0 = (5f + 40f * e) * d
        val len = (8f + 12f * FxMath.hash(i, 4)) * d * (1f - 0.35f * t)
        val a = Offset(ox + c * r0, oy + s * r0)
        val b = Offset(ox + c * (r0 + len), oy + s * (r0 + len))
        drawLine(halo, a, b, strokeWidth = 3.2f * d, cap = StrokeCap.Butt, alpha = 0.55f * fade)
        drawLine(ink, a, b, strokeWidth = 1.5f * d, cap = StrokeCap.Butt, pathEffect = stitch(3.2f * d, 2.2f * d), alpha = fade)
    }
    drawCircle(ink, radius = (2.5f + 2f * (1f - t)) * d, center = Offset(ox, oy), alpha = fade)
}

/** A hitomezashi star, stitched outward from its centre, with spokes to each tip. */
private fun DrawScope.burstFavorite(t: Float, ox: Float, oy: Float, ink: Color, halo: Color) {
    val d = density
    val fade = 1f - smooth(0.6f, 1f, t)
    val outer = (4f + 30f * easeOut(t)) * d
    val inner = outer * 0.46f
    val n = 10
    val xs = FloatArray(n)
    val ys = FloatArray(n)
    for (k in 0 until n) {
        val r = if (k % 2 == 0) outer else inner
        val ang = k * 0.6283185f - 1.5707964f
        xs[k] = ox + cos(ang) * r
        ys[k] = oy + sin(ang) * r
    }
    val star = Path().apply { partialPolyline(xs, ys, FxMath.clamp01(t * 1.6f), closed = true) }
    for (k in 0 until n step 2) {
        val ang = k * 0.6283185f - 1.5707964f
        drawLine(
            ink,
            Offset(ox + cos(ang) * outer * 0.3f, oy + sin(ang) * outer * 0.3f),
            Offset(ox + cos(ang) * outer, oy + sin(ang) * outer),
            strokeWidth = 1.2f * d,
            pathEffect = stitch(3f * d, 2.5f * d),
            alpha = 0.6f * fade,
        )
    }
    drawPath(star, halo, alpha = 0.5f * fade, style = Stroke(width = 3f * d))
    drawPath(star, ink, alpha = fade, style = Stroke(width = 1.5f * d, cap = StrokeCap.Butt, pathEffect = stitch(4.2f * d, 2.6f * d)))
}

/** A cross-stitch X sewn over the checkbox: one arm, then the other. */
private fun DrawScope.burstComplete(t: Float, ox: Float, oy: Float, ink: Color, halo: Color) {
    val d = density
    val fade = 1f - smooth(0.7f, 1f, t)
    val h = 8.5f * d
    val a1 = FxMath.clamp01(t / 0.46f)
    val a2 = FxMath.clamp01((t - 0.36f) / 0.46f)
    val s1 = Offset(ox - h, oy - h)
    val e1 = Offset(ox - h + 2f * h * a1, oy - h + 2f * h * a1)
    val s2 = Offset(ox + h, oy - h)
    val e2 = Offset(ox + h - 2f * h * a2, oy - h + 2f * h * a2)
    if (a1 > 0f) {
        drawLine(halo, s1, e1, strokeWidth = 4.2f * d, cap = StrokeCap.Round, alpha = 0.5f * fade)
        drawLine(ink, s1, e1, strokeWidth = 2.4f * d, cap = StrokeCap.Round, alpha = fade)
    }
    if (a2 > 0f) {
        drawLine(halo, s2, e2, strokeWidth = 4.2f * d, cap = StrokeCap.Round, alpha = 0.5f * fade)
        drawLine(ink, s2, e2, strokeWidth = 2.4f * d, cap = StrokeCap.Round, alpha = fade)
    }
    if (a1 in 0f..0.999f) drawCircle(ink, radius = 2.2f * d, center = e1, alpha = fade)
    if (a2 in 0f..0.999f) drawCircle(ink, radius = 2.2f * d, center = e2, alpha = fade)
}

/** Loose stitches unspool outward from the origin, droop, and fade out. */
private fun DrawScope.burstDelete(t: Float, ox: Float, oy: Float, ink: Color) {
    val d = density
    val fade = 1f - smooth(0.25f, 1f, t)
    val n = 14
    val steps = 10
    val path = Path() // one reused path for all strands
    for (i in 0 until n) {
        val ang = TAU * i / n + (FxMath.hash(i, 11) - 0.5f) * 0.5f
        val dx = cos(ang)
        val dy = sin(ang)
        val len = (16f + 24f * FxMath.hash(i, 12)) * d
        val ph = FxMath.hash(i, 13) * TAU
        val reach = FxMath.clamp01(0.15f + t * 1.25f - 0.25f * FxMath.hash(i, 14))
        path.reset()
        for (k in 0..steps) {
            val u = reach * k / steps
            val along = len * u
            val wob = sin(u * 7f + ph + t * 6f) * 3.2f * d * u
            val droop = t * t * u * u * 34f * d
            val x = ox + dx * along - dy * wob
            val y = oy + dy * along + dx * wob + droop
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            ink,
            alpha = 0.85f * fade,
            style = Stroke(width = (1.6f - 0.9f * t) * d, cap = StrokeCap.Round, pathEffect = stitch(3.5f * d, 2.2f * d)),
        )
    }
    drawCircle(ink, radius = (3f * (1f - t) + 0.5f) * d, center = Offset(ox, oy), alpha = fade)
}
