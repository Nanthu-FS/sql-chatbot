package app.monoworkspace.ui.theme.fx

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import app.monoworkspace.ui.theme.LocalAmbientMotion
import app.monoworkspace.ui.theme.LocalReduceMotion
import app.monoworkspace.ui.theme.LocalSurface
import app.monoworkspace.ui.theme.SurfaceColors
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Motion personality of the Sumi Ink theme: zen ink on cool rice paper.
 *
 * Ink-wash clouds breathe slowly behind the page, the Home greeting gets an
 * ensō (one tapered brush circle that writes itself) and a vermilion hanko
 * seal that stamps down beside it. Selections are wet brush strokes, the
 * celebrations are ink (a seal, splatter, a brush tick, a dripping drop),
 * screens bleed in, the theme spreads like an ink drop on paper, and the
 * pointer leaves a faint wash behind it.
 */
object SumiFx : ThemeFx() {

    private const val TAU = 6.2831855f
    private const val PI_F = 3.1415927f

    /** The one vermilion of the hanko seal (also the selection tick). */
    private val Vermilion = Color(0xFFB7372C)

    /** Paper tone for the characters carved into the seal. */
    private val SealPaper = Color(0xFFF9F9F7)

    // ---- Ensō timeline (ms) ----------------------------------------------------

    private const val ENSO_START = -1.05f
    private const val ENSO_SWEEP = 5.9f
    private const val ENSO_SAMPLES = 120
    private const val WRITE_MS = 1400f
    private const val STAMP_AT = 1400f
    private const val STAMP_MS = 420f
    private const val RING_AT = 1820f
    private const val RING_MS = 700f
    private const val HERO_TOTAL_MS = 2520
    private val WriteEase = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)

    // ---- Pointer ---------------------------------------------------------------

    private const val TRAIL_LIFE = 1.05f

    // ---- Backdrop --------------------------------------------------------------

    private class Cloud(
        val fx: Float,
        val fy: Float,
        val reach: Float,
        val period: Float,
        val phase: Float,
        val alpha: Float,
        val drift: Float,
    )

    /** Four slow ink washes. Alpha stays in the 0.03 to 0.06 band so the text stays readable. */
    private val clouds = arrayOf(
        Cloud(fx = 0.84f, fy = 0.12f, reach = 0.50f, period = 36f, phase = 0.04f, alpha = 0.060f, drift = 0.075f),
        Cloud(fx = 0.10f, fy = 0.94f, reach = 0.56f, period = 40f, phase = 0.52f, alpha = 0.050f, drift = 0.090f),
        Cloud(fx = 0.52f, fy = 0.52f, reach = 0.34f, period = 27f, phase = 0.27f, alpha = 0.034f, drift = 0.060f),
        Cloud(fx = 0.00f, fy = 0.22f, reach = 0.30f, period = 22f, phase = 0.78f, alpha = 0.040f, drift = 0.055f),
    )

    @Composable
    override fun Backdrop(modifier: Modifier) {
        val reduce = LocalReduceMotion.current
        val ambient = LocalAmbientMotion.current
        val ink = LocalSurface.current.ink
        val clock = rememberFxClock(running = true)
        val wash = remember(ink) { listOf(ink.copy(alpha = 0.9f), ink.copy(alpha = 0.4f), ink.copy(alpha = 0f)) }
        if (reduce) return
        Canvas(modifier) {
            // Still frame (t = 0) when ambient motion is off; rememberFxClock runs no loop then.
            val t = if (ambient) clock.value else 0f
            val w = size.width
            val h = size.height
            val span = max(w, h)
            for (c in clouds) {
                val a = TAU * (t / c.period + c.phase)
                val cx = c.fx * w + sin(a) * c.drift * w
                val cy = c.fy * h + cos(a * 0.7f) * c.drift * 0.8f * h
                val breathe = 1f + 0.07f * sin(TAU * (t / (c.period * 0.6f)) + c.phase * 9f)
                val r = c.reach * span * breathe
                drawCircle(
                    brush = Brush.radialGradient(wash, Offset(cx, cy), r),
                    radius = r,
                    center = Offset(cx, cy),
                    alpha = c.alpha,
                )
            }
            // Rice-paper fibres: fixed positions, barely there.
            for (i in 0 until 64) {
                val x = FxMath.hash(i, 11) * w
                val y = FxMath.hash(i, 12) * h
                val ang = FxMath.hash(i, 13) * PI_F
                val half = (3f + 6f * FxMath.hash(i, 14)) * density
                val dx = cos(ang) * half
                val dy = sin(ang) * half
                drawLine(
                    color = ink,
                    start = Offset(x - dx, y - dy),
                    end = Offset(x + dx, y + dy),
                    strokeWidth = 0.7f * density,
                    cap = StrokeCap.Round,
                    alpha = 0.035f,
                )
            }
        }
    }

    // ---- Home greeting ornament -------------------------------------------------

    @Composable
    override fun HeroOrnament(modifier: Modifier) {
        val reduce = LocalReduceMotion.current
        val ambient = LocalAmbientMotion.current
        val ink = LocalSurface.current.ink
        val clock = rememberFxClock(running = true)
        // One timeline in ms: the ensō writes (0 to 1400), the seal stamps (1400 to 1820),
        // then the ink ring spreads from the impact (1820 to 2520). Plays once.
        val timeline = remember { Animatable(0f) }
        LaunchedEffect(reduce) {
            if (reduce) {
                timeline.snapTo(HERO_TOTAL_MS.toFloat())
            } else {
                timeline.animateTo(HERO_TOTAL_MS.toFloat(), tween(HERO_TOTAL_MS, easing = LinearEasing))
            }
        }
        Canvas(modifier) {
            val tl = timeline.value
            val d = density
            val w = size.width
            val h = size.height
            val r = min(w * 0.066f, h * 0.38f)           // ensō radius
            val cx = w * 0.85f                           // ensō spans roughly 78 to 92 percent of the width
            val cy = h * 0.5f
            // Barely perceptible breathing of the finished ensō; none when ambient motion is off.
            val breathe = if (ambient) 0.955f + 0.045f * sin(clock.value * TAU / 7f) else 1f

            drawEnso(cx, cy, r, WriteEase.transform(FxMath.clamp01(tl / WRITE_MS)), ink, breathe)

            // The hanko seal sits at the open end of the ensō, up and to the right.
            val seal = Offset(cx + r * 0.88f, cy - r * 0.74f)
            val side = 22f * d
            val stamp = FxMath.clamp01((tl - STAMP_AT) / STAMP_MS)
            drawHanko(
                center = seal,
                side = side,
                scale = 1.4f - 0.4f * easeOutCubic(stamp),
                degrees = -3f,
                alpha = FxMath.clamp01(stamp * 3.5f),
            )

            // Impact: an ink ring spreads out and a few flecks fly off.
            val ring = FxMath.clamp01((tl - RING_AT) / RING_MS)
            if (ring > 0f && ring < 1f) {
                val e = easeOutCubic(ring)
                drawCircle(
                    color = ink,
                    radius = side * (0.62f + 1.5f * e),
                    center = seal,
                    alpha = 0.30f * (1f - ring),
                    style = Stroke(width = d * (1.3f * (1f - ring) + 0.3f)),
                )
                for (i in 0 until 6) {
                    val ang = TAU * ((i + FxMath.hash(i, 71)) / 6f)
                    val dist = side * (0.6f + 1.7f * FxMath.hash(i, 72)) * e
                    drawCircle(
                        color = ink,
                        radius = d * (0.7f + 0.8f * FxMath.hash(i, 73)),
                        center = seal + Offset(cos(ang), sin(ang)) * dist,
                        alpha = 0.5f * (1f - ring),
                    )
                }
            }
        }
    }

    /** One tapered ensō that writes itself: [progress] 0 to 1 along the stroke. */
    private fun DrawScope.drawEnso(cx: Float, cy: Float, r: Float, progress: Float, ink: Color, alpha: Float) {
        if (progress <= 0f || alpha <= 0f) return
        val n = max(2, (ENSO_SAMPLES * progress).toInt())
        val xs = FloatArray(n + 1)
        val ys = FloatArray(n + 1)
        val hw = FloatArray(n + 1)
        val maxHalf = r * 0.085f
        for (i in 0..n) {
            val s = progress * i / n
            val pt = ensoPoint(cx, cy, r, s)
            xs[i] = pt.x
            ys[i] = pt.y
            hw[i] = maxHalf * ensoWidth(s)
        }
        drawPath(ribbon(xs, ys, hw, n + 1), ink, alpha = alpha)

        // Dry-brush flecks where the pen lifts near the open end.
        for (k in 0 until 7) {
            val sk = 0.66f + 0.3f * FxMath.hash(k, 21)
            val f = FxMath.clamp01((progress - sk) / 0.03f)
            if (f <= 0f) continue
            val th = ENSO_START + ENSO_SWEEP * sk
            val out = (FxMath.hash(k, 22) - 0.5f) * maxHalf * 3.2f
            drawCircle(
                color = ink,
                radius = density * (0.6f + 0.6f * FxMath.hash(k, 23)),
                center = ensoPoint(cx, cy, r, sk) + Offset(cos(th), sin(th)) * out,
                alpha = alpha * 0.6f * f,
            )
        }
    }

    private fun ensoPoint(cx: Float, cy: Float, r: Float, s: Float): Offset {
        // A slightly spiralling, slightly wobbling circle, so it reads as hand-made.
        val rad = r * (1.03f - 0.05f * s + 0.03f * sin(TAU * 1.15f * s + 0.6f))
        val th = ENSO_START + ENSO_SWEEP * s
        return Offset(cx + rad * cos(th), cy + rad * sin(th))
    }

    /** Brush pressure along the ensō: a quick entry, a full body, and a thin lift at the open end. */
    private fun ensoWidth(s: Float): Float {
        val swell = 0.8f + 0.2f * sin(s * 17f + 1.3f)
        return (0.28f + 0.72f * smooth(0f, 0.12f, s)) * (1f - 0.82f * smooth(0.68f, 1f, s)) * swell
    }

    /** A vermilion hanko seal: a red square with characters knocked out in paper. */
    private fun DrawScope.drawHanko(center: Offset, side: Float, scale: Float, degrees: Float, alpha: Float) {
        if (alpha <= 0f) return
        val s = side * scale
        val corner = Offset(center.x - s / 2f, center.y - s / 2f)
        rotate(degrees = degrees, pivot = center) {
            drawRect(Vermilion, corner, Size(s, s), alpha = alpha)
            val inset = s * 0.13f
            drawRect(
                color = SealPaper,
                topLeft = corner + Offset(inset, inset),
                size = Size(s - 2f * inset, s - 2f * inset),
                alpha = alpha * 0.85f,
                style = Stroke(width = s * 0.05f),
            )
            val lw = s * 0.075f
            val carve = alpha * 0.9f
            drawLine(SealPaper, corner + Offset(s * 0.30f, s * 0.36f), corner + Offset(s * 0.70f, s * 0.36f), strokeWidth = lw, alpha = carve)
            drawLine(SealPaper, corner + Offset(s * 0.30f, s * 0.62f), corner + Offset(s * 0.70f, s * 0.62f), strokeWidth = lw, alpha = carve)
            drawLine(SealPaper, corner + Offset(s * 0.50f, s * 0.36f), corner + Offset(s * 0.50f, s * 0.80f), strokeWidth = lw, alpha = carve)
        }
    }

    // ---- Selection -------------------------------------------------------------

    /** The wet brush stroke breathes a little while the row is selected. */
    override val animatedSelection: Boolean get() = true

    override fun DrawScope.drawSelection(presence: Float, seconds: Float, colors: SurfaceColors) {
        val p = FxMath.clamp01(presence)
        if (p <= 0f) return
        val w = size.width
        val h = size.height
        val d = density
        val reach = w * easeOutCubic(p)
        val wet = 0.94f + 0.06f * sin(seconds * 0.9f)

        // The brush body: drawn in from the left, thick in the middle, thin at both ends.
        val n = 28
        val xs = FloatArray(n + 1)
        val ys = FloatArray(n + 1)
        val hw = FloatArray(n + 1)
        for (i in 0..n) {
            val u = i.toFloat() / n
            xs[i] = reach * u
            ys[i] = h * 0.5f + d * 0.9f * sin(u * 4.1f + 0.8f)
            hw[i] = h * 0.36f * (0.12f + 0.88f * sqrt(max(sin(PI_F * u), 0f)))
        }
        drawPath(ribbon(xs, ys, hw, n + 1), colors.ink, alpha = 0.10f * p * wet)

        // Two bristle streaks riding inside the body.
        for (k in 0 until 2) {
            val side = if (k == 0) -0.55f else 0.55f
            val by = FloatArray(n + 1) { ys[it] + side * hw[it] }
            val bw = FloatArray(n + 1) { hw[it] * 0.1f }
            drawPath(ribbon(xs, by, bw, n + 1), colors.ink, alpha = 0.05f * p * wet)
        }

        // A short vermilion tick at the left edge.
        val tick = h * 0.5f * easeOutCubic(p)
        drawRect(Vermilion, Offset(0f, h * 0.5f - tick / 2f), Size(2.6f * d, tick), alpha = p)
    }

    // ---- Screen transitions ----------------------------------------------------

    /** Ink bleeding into the paper. */
    override fun enter(pop: Boolean): EnterTransition =
        fadeIn(tween(320, easing = FastOutSlowInEasing)) +
            scaleIn(tween(320, easing = FastOutSlowInEasing), initialScale = 1.02f)

    override fun exit(pop: Boolean): ExitTransition =
        fadeOut(tween(160, easing = LinearEasing))

    override val revealMillis: Int get() = 820

    /**
     * An ink drop spreading on paper. The main blob wobbles (sum of four sines
     * on the angle) and four satellite droplets pop out and are pulled back in
     * to merge. Pure function of its inputs: at progress 1 the blob is at least
     * 1.1 times the farthest corner, so the whole [size] is covered.
     */
    override fun revealPath(size: Size, origin: Offset, progress: Float): Path {
        val p = FxMath.clamp01(progress)
        val path = Path()
        if (p <= 0f) return path

        val far = max(
            max(hypot(origin.x, origin.y), hypot(size.width - origin.x, origin.y)),
            max(hypot(origin.x, size.height - origin.y), hypot(size.width - origin.x, size.height - origin.y)),
        )
        val base = far * 1.3f * easeOutCubic(p) + 1f
        val wob = 0.45f + 0.55f * p
        addBlob(path, origin.x, origin.y, 96) { th ->
            base * (1f + wob * (
                0.055f * sin(3f * th + 0.7f) +
                    0.035f * sin(5f * th + 2.1f) +
                    0.020f * sin(8f * th + 4.0f) +
                    0.015f * sin(11f * th + 1.2f)
                ))
        }
        for (k in 0 until 4) {
            val start = 0.08f + 0.17f * k + 0.07f * FxMath.hash(k, 51)
            val appear = FxMath.clamp01((p - start) / 0.28f)
            if (appear <= 0f) continue
            val merge = FxMath.clamp01((p - start) / 0.62f)
            val ang = TAU * (0.07f + 0.25f * k + 0.12f * FxMath.hash(k, 52))
            val dist = base * (0.92f + 0.55f * (1f - easeOutCubic(merge)))
            val rad = far * (0.05f + 0.045f * FxMath.hash(k, 53)) * easeOutCubic(appear)
            addBlob(path, origin.x + cos(ang) * dist, origin.y + sin(ang) * dist, 28) { th ->
                rad * (1f + 0.12f * sin(3f * th + k))
            }
        }
        return path
    }

    /** A closed polygon around (cx, cy). Every blob uses the same orientation, so overlapping blobs union under NonZero. */
    private inline fun addBlob(path: Path, cx: Float, cy: Float, segments: Int, radiusAt: (Float) -> Float) {
        for (i in 0 until segments) {
            val th = TAU * i / segments
            val rr = radiusAt(th)
            val x = cx + cos(th) * rr
            val y = cy + sin(th) * rr
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    // ---- Pointer ---------------------------------------------------------------

    /** The last 12 pointer positions, kept in fixed arrays so nothing is allocated per frame. */
    private class InkTrail {
        val cap = 12
        val px = FloatArray(cap)
        val py = FloatArray(cap)
        val born = FloatArray(cap) { -1000f }
        private var next = 0

        fun push(x: Float, y: Float, now: Float) {
            px[next] = x
            py[next] = y
            born[next] = now
            next = (next + 1) % cap
        }

        fun alive(now: Float): Boolean {
            for (i in 0 until cap) if (now - born[i] < TRAIL_LIFE) return true
            return false
        }
    }

    /** Faint ink dots left where the pointer has been: they shrink and fade, never above 0.18 alpha. */
    @Composable
    override fun PointerTrail(pointer: () -> Offset?, colors: SurfaceColors) {
        val ambient = LocalAmbientMotion.current
        val dens = LocalDensity.current.density
        val ink = colors.ink
        val trail = remember { InkTrail() }
        val clock = remember { mutableFloatStateOf(0f) }
        val latest = rememberUpdatedState(pointer)
        LaunchedEffect(ambient, dens) {
            if (!ambient) return@LaunchedEffect
            var lastX = Float.NaN
            var lastY = Float.NaN
            var wasAlive = false
            val spacing = 5f * dens
            while (true) {
                withFrameNanos { nanos ->
                    val now = nanos / 1_000_000_000f
                    val p = latest.value()
                    if (p == null) {
                        lastX = Float.NaN
                    } else if (lastX.isNaN() || hypot(p.x - lastX, p.y - lastY) > spacing) {
                        trail.push(p.x, p.y, now)
                        lastX = p.x
                        lastY = p.y
                    }
                    val alive = trail.alive(now)
                    // Redraw while dots live, plus one last frame so the final dot disappears cleanly.
                    if (alive || wasAlive) clock.floatValue = now
                    wasAlive = alive
                }
            }
        }
        if (!ambient) return
        Canvas(Modifier.fillMaxSize()) {
            val now = clock.floatValue
            for (i in 0 until trail.cap) {
                val age = now - trail.born[i]
                if (age < 0f || age >= TRAIL_LIFE) continue
                val q = age / TRAIL_LIFE
                val fade = 1f - q
                drawCircle(
                    color = ink,
                    radius = dens * (3.4f - 2.6f * q),
                    center = Offset(trail.px[i], trail.py[i]),
                    alpha = 0.18f * fade * fade,
                )
            }
        }
    }

    // ---- Celebrations ----------------------------------------------------------

    @Composable
    override fun Burst(kind: BurstKind, origin: Offset, colors: SurfaceColors, onFinished: () -> Unit) {
        val total = when (kind) {
            BurstKind.Create -> 1000
            BurstKind.Favorite -> 920
            BurstKind.Complete -> 760
            BurstKind.Delete -> 1060
        }
        val p = remember { Animatable(0f) }
        val finished = rememberUpdatedState(onFinished)
        LaunchedEffect(Unit) {
            // finally: the overlay hears about the end even if this coroutine is cancelled mid-flight.
            try {
                p.animateTo(1f, tween(total, easing = LinearEasing))
            } finally {
                finished.value()
            }
        }
        val ink = colors.ink
        Canvas(Modifier.fillMaxSize()) {
            val t = p.value * total
            when (kind) {
                BurstKind.Create -> burstCreate(origin, t, ink)
                BurstKind.Favorite -> burstFavorite(origin, t, ink)
                BurstKind.Complete -> burstComplete(origin, t, ink)
                BurstKind.Delete -> burstDelete(origin, t, ink)
            }
        }
    }

    /** The seal stamps at the origin, an impact ring goes out, and ink dots splash off it. */
    private fun DrawScope.burstCreate(origin: Offset, t: Float, ink: Color) {
        val d = density
        val side = 26f * d
        val sp = FxMath.clamp01(t / 260f)
        drawHanko(
            center = origin,
            side = side,
            scale = 1.45f - 0.45f * easeOutCubic(sp),
            degrees = -3f,
            alpha = FxMath.clamp01(sp * 4f) * (1f - smooth(720f, 1000f, t)),
        )
        val rp = FxMath.clamp01((t - 240f) / 500f)
        if (rp > 0f && rp < 1f) {
            drawCircle(
                color = ink,
                radius = side * (0.6f + 1.5f * easeOutCubic(rp)),
                center = origin,
                alpha = 0.32f * (1f - rp),
                style = Stroke(width = d * (1.4f * (1f - rp) + 0.3f)),
            )
        }
        for (i in 0 until 12) {
            val q = FxMath.clamp01((t - 220f - 40f * FxMath.hash(i, 31)) / 600f)
            if (q <= 0f) continue
            val ang = TAU * ((i + FxMath.hash(i, 32)) / 12f)
            val dir = Offset(cos(ang), sin(ang))
            val reach = side * (0.7f + 2.6f * FxMath.hash(i, 33)) * easeOutCubic(q)
            val rad = d * (0.9f + 2.4f * FxMath.hash(i, 34)) * (1f - 0.35f * q)
            val fq = 1f - q
            drawCircle(ink, radius = rad, center = origin + dir * reach, alpha = 0.8f * fq * fq)
        }
    }

    /** An ink pool blooms and dries while ten splatter drops fly out, each trailing a short flick. */
    private fun DrawScope.burstFavorite(origin: Offset, t: Float, ink: Color) {
        val d = density
        val poolAlpha = 0.85f * (1f - smooth(420f, 880f, t))
        if (poolAlpha > 0f) {
            drawCircle(ink, radius = d * (3.2f + 4.2f * easeOutCubic(FxMath.clamp01(t / 200f))), center = origin, alpha = poolAlpha)
        }
        val fade = 1f - smooth(420f, 900f, t)
        for (i in 0 until 10) {
            val q = FxMath.clamp01((t - 30f * FxMath.hash(i, 41)) / 520f)
            if (q <= 0f) continue
            val ang = TAU * ((i + 0.35f * FxMath.hash(i, 42)) / 10f)
            val dir = Offset(cos(ang), sin(ang))
            val pos = origin + dir * (d * (24f + 56f * FxMath.hash(i, 43)) * easeOutCubic(q))
            val rad = d * (1.1f + 3.4f * FxMath.hash(i, 44)) * (1f - 0.4f * q)
            drawCircle(ink, radius = rad, center = pos, alpha = 0.85f * fade)
            val len = d * 10f * (1f - q)
            if (len > 0.6f * d) {
                drawLine(
                    color = ink,
                    start = pos - dir * len,
                    end = pos,
                    strokeWidth = rad * 1.1f,
                    cap = StrokeCap.Round,
                    alpha = 0.55f * fade * (1f - q),
                )
            }
        }
    }

    /** One confident brush tick over the box: a long stroke with a thin lift, plus a small pulse. */
    private fun DrawScope.burstComplete(origin: Offset, t: Float, ink: Color) {
        val alpha = 1f - smooth(560f, 760f, t)
        if (alpha <= 0f) return
        val d = density
        val drawn = 1f - (1f - FxMath.clamp01(t / 380f)).let { it * it }
        val pulse = 1f + 0.12f * FxMath.bump(FxMath.clamp01((t - 300f) / 420f))
        val a = origin + Offset(-6.5f * d * pulse, 0.6f * d * pulse)
        val b = origin + Offset(-2.2f * d * pulse, 6.4f * d * pulse)
        val c = origin + Offset(7.8f * d * pulse, -7.0f * d * pulse)
        val l1 = (b - a).getDistance()
        val l2 = (c - b).getDistance()
        val travel = (l1 + l2) * drawn
        val n = 16
        val xs = FloatArray(n + 1)
        val ys = FloatArray(n + 1)
        val hw = FloatArray(n + 1)
        val maxHalf = 1.7f * d
        for (i in 0..n) {
            val u = i.toFloat() / n
            val s = travel * u
            val pt = if (s <= l1) a + (b - a) * (s / l1) else b + (c - b) * ((s - l1) / l2)
            xs[i] = pt.x
            ys[i] = pt.y
            hw[i] = maxHalf * (0.25f + 0.75f * sin(PI_F * u))
        }
        drawPath(ribbon(xs, ys, hw, n + 1), ink, alpha = alpha)
    }

    /** An ink drop forms at the origin, stretches as it drips down, fades, and leaves a small ripple where it lands. */
    private fun DrawScope.burstDelete(origin: Offset, t: Float, ink: Color) {
        val d = density
        val formP = FxMath.clamp01(t / 200f)
        val fallP = FxMath.clamp01((t - 200f) / 560f)
        val fallPx = 96f * d
        val dropY = origin.y + fallPx * fallP * fallP        // gravity: the drop accelerates
        val r = d * 4.2f * (0.35f + 0.65f * easeOutCubic(formP))
        val stretch = 1f + 0.5f * fallP
        val alpha = 1f - smooth(640f, 900f, t)
        if (alpha > 0f) {
            drawLine(
                color = ink,
                start = origin,
                end = Offset(origin.x, dropY),
                strokeWidth = r * (1.05f - 0.5f * fallP),
                cap = StrokeCap.Round,
                alpha = 0.4f * alpha,
            )
            drawOval(
                color = ink,
                topLeft = Offset(origin.x - r, dropY - r * stretch),
                size = Size(r * 2f, r * 2f * stretch),
                alpha = alpha,
            )
        }
        val land = FxMath.clamp01((t - 760f) / 260f)
        if (land > 0f) {
            drawCircle(
                color = ink,
                radius = d * (1f + 9f * easeOutCubic(land)),
                center = Offset(origin.x, origin.y + fallPx),
                alpha = 0.4f * (1f - land),
                style = Stroke(width = d * (1f - 0.6f * land) + 0.3f),
            )
        }
    }

    // ---- Shared helpers --------------------------------------------------------

    /** A filled brush ribbon along the polyline (xs, ys) with half-width hw at each point. */
    private fun ribbon(xs: FloatArray, ys: FloatArray, hw: FloatArray, n: Int): Path {
        val path = Path()
        if (n < 2) return path
        val nx = FloatArray(n)
        val ny = FloatArray(n)
        for (i in 0 until n) {
            val a = max(i - 1, 0)
            val b = min(i + 1, n - 1)
            val tx = xs[b] - xs[a]
            val ty = ys[b] - ys[a]
            val len = max(sqrt(tx * tx + ty * ty), 1e-5f)
            nx[i] = -ty / len * hw[i]
            ny[i] = tx / len * hw[i]
        }
        for (i in 0 until n) {
            if (i == 0) path.moveTo(xs[i] + nx[i], ys[i] + ny[i]) else path.lineTo(xs[i] + nx[i], ys[i] + ny[i])
        }
        for (i in n - 1 downTo 0) path.lineTo(xs[i] - nx[i], ys[i] - ny[i])
        path.close()
        return path
    }

    private fun easeOutCubic(x: Float): Float {
        val u = 1f - FxMath.clamp01(x)
        return 1f - u * u * u
    }

    private fun smooth(a: Float, b: Float, x: Float): Float {
        val t = FxMath.clamp01((x - a) / (b - a))
        return t * t * (3f - 2f * t)
    }
}
