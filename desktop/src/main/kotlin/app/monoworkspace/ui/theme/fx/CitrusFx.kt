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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import app.monoworkspace.ui.theme.LocalAmbientMotion
import app.monoworkspace.ui.theme.LocalReduceMotion
import app.monoworkspace.ui.theme.SurfaceColors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Motion personality of the Citrus Slice theme: lemon, lime and blood-orange
 * slices drift behind the page with fizzing bubbles, the Home greeting gets a
 * big slice that drips juice, every celebration is juicy (a splash, lemon
 * wedges, zest confetti, a peel curl), and switching to the theme opens a slice.
 */
object CitrusFx : ThemeFx() {

    private const val TAU = 6.2831855f
    private const val DEG = 0.017453292f
    private const val TRAIL_LIFE = 0.55f

    /** The palette from the Citrus Slice mockup and brief. */
    private object Fruit {
        val Lemon = Color(0xFFFFD21F)
        val LemonPale = Color(0xFFFFE97A)
        val Lime = Color(0xFF7CB518)
        val Orange = Color(0xFFFF8A2A)
        val Blood = Color(0xFFC2401D)
        val Pith = Color(0xFFFFFDF2)
    }

    private val confetti = listOf(Fruit.Lemon, Fruit.Lime, Fruit.Orange, Fruit.Blood)

    /** A slice drifting near the edge of the pane. Positions are fractions of the pane. */
    private class DriftSlice(
        val fx: Float,
        val fy: Float,
        val radius: Float, // dp
        val spin: Float, // degrees per second
        val phase: Float, // 0..1
        val period: Float, // seconds per drift loop
        val segments: Int,
    )

    private val backdropSlices = listOf(
        DriftSlice(0.93f, 0.12f, 120f, 3.0f, 0.10f, 48f, 9),
        DriftSlice(0.02f, 0.93f, 150f, -2.2f, 0.57f, 58f, 10),
        DriftSlice(-0.03f, 0.30f, 76f, 4.4f, 0.33f, 38f, 8),
        DriftSlice(0.99f, 0.72f, 100f, -3.6f, 0.81f, 52f, 9),
        DriftSlice(0.58f, -0.06f, 64f, 2.5f, 0.45f, 44f, 8),
    )

    // ---- Backdrop -----------------------------------------------------------

    @Composable
    override fun Backdrop(modifier: Modifier) {
        val ambient = LocalAmbientMotion.current
        val reduce = LocalReduceMotion.current
        val clock = rememberFxClock(running = true)
        if (reduce) return
        Canvas(modifier) {
            // Still frame (t = 0) when ambient motion is off: no loop runs then.
            val t = if (ambient) clock.value else 0f
            val w = size.width
            val h = size.height
            for (s in backdropSlices) {
                val a = TAU * (t / s.period + s.phase)
                val cx = s.fx * w + sin(a) * 18f * density
                val cy = s.fy * h + cos(a * 0.7f) * 12f * density
                drawSlice(cx, cy, s.radius * density, s.phase * 360f + s.spin * t, s.segments, alpha = 0.13f, shine = false)
            }
            drawFizz(t, w, h)
        }
    }

    /** Tiny bubbles rising from the bottom edge, like fizz in a glass. */
    private fun DrawScope.drawFizz(t: Float, w: Float, h: Float) {
        val ring = Stroke(width = density)
        for (i in 0 until 22) {
            val period = 7f + 7f * FxMath.hash(i, 2)
            val u = (t / period + FxMath.hash(i, 3)) % 1f
            val x = FxMath.hash(i, 1) * w + sin(t * 1.3f + i * 1.7f) * 7f * density
            val y = h * (1.03f - 1.08f * u)
            val r = (1.5f + 2.2f * FxMath.hash(i, 4)) * density
            val a = 0.34f * smooth(0f, 0.12f, u) * (1f - smooth(0.8f, 1f, u))
            if (a <= 0.01f) continue
            drawCircle(if (i % 3 == 0) Fruit.Lime else Fruit.Lemon, r, Offset(x, y), alpha = a, style = ring)
        }
    }

    /**
     * One citrus slice: a lime rind, a cream pith and [segments] lemon wedges
     * separated by thin pith membranes. [rotation] is in degrees.
     */
    private fun DrawScope.drawSlice(
        cx: Float,
        cy: Float,
        r: Float,
        rotation: Float,
        segments: Int,
        alpha: Float,
        shine: Boolean,
    ) {
        if (r < 0.5f || alpha <= 0f) return
        val center = Offset(cx, cy)
        val step = 360f / segments
        val gap = 5f
        val inner = r * 0.84f
        val topLeft = Offset(cx - inner, cy - inner)
        val box = Size(inner * 2f, inner * 2f)
        drawCircle(Fruit.Lime, r, center, alpha = alpha)
        drawCircle(Fruit.Pith, r * 0.9f, center, alpha = alpha)
        for (i in 0 until segments) {
            drawArc(
                color = if (i % 2 == 0) Fruit.Lemon else Fruit.LemonPale,
                startAngle = rotation + i * step + gap / 2f,
                sweepAngle = step - gap,
                useCenter = true,
                topLeft = topLeft,
                size = box,
                alpha = alpha,
            )
        }
        drawCircle(Fruit.Pith, r * 0.1f, center, alpha = alpha)
        if (shine) {
            drawArc(
                color = Fruit.Pith,
                startAngle = rotation + 205f,
                sweepAngle = 40f,
                useCenter = false,
                topLeft = Offset(cx - r * 0.72f, cy - r * 0.72f),
                size = Size(r * 1.44f, r * 1.44f),
                alpha = alpha * 0.6f,
                style = Stroke(width = r * 0.08f, cap = StrokeCap.Round),
            )
        }
    }

    // ---- Home greeting ------------------------------------------------------

    @Composable
    override fun HeroOrnament(modifier: Modifier) {
        val reduce = LocalReduceMotion.current
        val ambient = LocalAmbientMotion.current
        val clock = rememberFxClock(running = true)
        // One-shot intro: the slice springs open once, even without ambient motion.
        val intro = remember { Animatable(0f) }
        LaunchedEffect(Unit) { intro.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 260f)) }
        if (reduce) return
        Canvas(modifier) {
            val k = intro.value.coerceIn(0f, 1.2f)
            if (k <= 0.001f) return@Canvas
            val w = size.width
            val h = size.height
            val t = if (ambient) clock.value else 0f
            // Sits at 85% of the width, so the greeting on the left stays clear.
            val r = min(h * 0.36f, w * 0.12f) * k
            val cx = w * 0.85f
            val cy = h * 0.5f
            drawCircle(Fruit.Blood, r * 1.02f, Offset(cx, cy + r * 0.12f), alpha = 0.14f * k)
            drawSlice(cx, cy, r, (1f - k) * -80f + t * 4.5f, 10, alpha = 1f, shine = true)
            drawDrips(cx, cy, r, h, t, min(k, 1f))
        }
    }

    /** Four juice drops bead on the rim, fall, and are pulled back into the slice. */
    private fun DrawScope.drawDrips(cx: Float, cy: Float, r: Float, h: Float, t: Float, k: Float) {
        val rd = h * 0.045f * k
        val fall = h * 0.09f
        for (i in 0 until 4) {
            val ang = (68f + i * 24f + (FxMath.hash(i, 71) - 0.5f) * 8f) * DEG
            val x = cx + cos(ang) * r * 0.9f
            val y0 = cy + sin(ang) * r * 0.9f
            val period = 3.4f + 1.2f * FxMath.hash(i, 72)
            val u = (t / period + FxMath.hash(i, 73)) % 1f
            var dy = 0f
            var rr = rd
            var stretch = 1f
            var a = 1f
            when {
                // A bead forms on the rim and overshoots as it settles.
                u < 0.16f -> { rr = rd * easeOutBack(u / 0.16f) }
                // It hangs while the neck stretches.
                u < 0.34f -> {
                    val s = (u - 0.16f) / 0.18f
                    dy = fall * 0.05f * s
                    stretch = 1f + 0.5f * s
                }
                // It drips with gravity and thins out.
                u < 0.62f -> {
                    val f = (u - 0.34f) / 0.28f
                    dy = fall * (0.05f + 0.95f * f * f)
                    stretch = 1.5f + 0.7f * f
                    rr = rd * (1f - 0.2f * f)
                }
                // It is pulled back up and re-forms into the rim.
                u < 0.86f -> {
                    val g = smooth(0f, 1f, (u - 0.62f) / 0.24f)
                    dy = fall * (1f - g)
                    rr = rd * (0.8f - 0.3f * g)
                    stretch = 2.2f - g
                    a = 1f - smooth(0.6f, 1f, g)
                }
                else -> a = 0f
            }
            if (a <= 0.01f || rr < 0.3f) continue
            val ry = rr * stretch
            val rx = rr * (1f - 0.08f * (stretch - 1f))
            val dropCy = y0 + dy + (ry - rr)
            drawOval(Fruit.Orange, Offset(x - rx, dropCy - ry), Size(rx * 2f, ry * 2f), alpha = a)
            drawCircle(Fruit.Pith, rr * 0.3f, Offset(x - rx * 0.35f, dropCy - ry * 0.35f), alpha = 0.7f * a)
        }
    }

    // ---- Sidebar selection --------------------------------------------------

    override val animatedSelection: Boolean get() = true

    /** A jelly pill in the sidebar tint, wobbling gently, with a lime dot at the left. */
    override fun DrawScope.drawSelection(presence: Float, seconds: Float, colors: SurfaceColors) {
        val p = presence.coerceIn(0f, 1f)
        if (p <= 0f) return
        val w = size.width
        val h = size.height
        val wob = sin(seconds * TAU * 0.9f)
        val sx = 1f + 0.025f * wob * p
        val sy = 1f - 0.035f * wob * p + 0.012f * sin(seconds * TAU * 2.1f) * p
        val pw = w * sx
        val ph = h * sy
        val left = (w - pw) / 2f
        val top = (h - ph) / 2f
        drawRoundRect(
            color = colors.tint.copy(alpha = colors.tint.alpha * p),
            topLeft = Offset(left, top),
            size = Size(pw, ph),
            cornerRadius = CornerRadius(ph / 2f),
        )
        val dotR = max(2.6f * density * easeOutBack(p), 0f)
        drawCircle(Fruit.Lime, dotR, Offset(left + 5.5f * density, h / 2f), alpha = p)
    }

    // ---- Bursts -------------------------------------------------------------

    @Composable
    override fun Burst(kind: BurstKind, origin: Offset, colors: SurfaceColors, onFinished: () -> Unit) {
        val seconds = when (kind) {
            BurstKind.Create -> 1.0f
            BurstKind.Favorite -> 0.9f
            BurstKind.Complete -> 1.1f
            BurstKind.Delete -> 1.1f
        }
        val p = remember { Animatable(0f) }
        val finished by rememberUpdatedState(onFinished)
        // One Path reused by the wedge and peel shapes, so no Path is built every frame.
        val scratch = remember { Path() }
        LaunchedEffect(Unit) {
            // finally: onFinished is called on every exit path, including cancellation.
            try {
                p.animateTo(1f, tween(durationMillis = (seconds * 1000f).toInt(), easing = LinearEasing))
            } finally {
                finished()
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val t = p.value
            val tau = t * seconds
            when (kind) {
                BurstKind.Create -> burstCreate(t, tau, origin)
                BurstKind.Favorite -> burstFavorite(t, origin, scratch)
                BurstKind.Complete -> burstComplete(t, tau, origin)
                BurstKind.Delete -> burstDelete(t, origin, scratch)
            }
        }
    }

    /** Juice splash: droplets flung out under gravity while a slice pops open. */
    private fun DrawScope.burstCreate(t: Float, tau: Float, o: Offset) {
        val d = density
        val ringR = (6f + 46f * easeOutCubic(FxMath.clamp01(t / 0.6f))) * d
        drawCircle(Fruit.Lime, ringR, o, alpha = (1f - t) * 0.8f, style = Stroke(width = (2.5f * (1f - t) + 0.5f) * d))
        drawSlice(
            o.x, o.y, 24f * d * easeOutBack(FxMath.clamp01(t / 0.3f)), t * 160f, 8,
            alpha = 1f - smooth(0.55f, 1f, t), shine = true,
        )
        val g = 1100f * d
        val s = (1f - exp(-2.6f * tau)) / 2.6f
        val a = 1f - smooth(0.45f, 1f, t)
        for (i in 0 until 15) {
            val ang = i * 24f + (FxMath.hash(i, 41) - 0.5f) * 26f
            val v = (300f + 320f * FxMath.hash(i, 42)) * d
            val x = o.x + cos(ang * DEG) * v * s
            val y = o.y + sin(ang * DEG) * v * s + 0.5f * g * tau * tau
            val r = (2.4f + 3.2f * FxMath.hash(i, 43)) * d * (1f - 0.4f * t)
            val color = when (i % 3) {
                0 -> Fruit.Lemon
                1 -> Fruit.Lime
                else -> Fruit.Orange
            }
            drawCircle(color, r, Offset(x, y), alpha = a)
        }
    }

    /** Three lemon wedges fly out from the star, spinning as they go. */
    private fun DrawScope.burstFavorite(t: Float, o: Offset, wedge: Path) {
        val d = density
        val a = 1f - smooth(0.6f, 1f, t)
        val travel = easeOutCubic(FxMath.clamp01(t / 0.75f))
        val pop = easeOutBack(FxMath.clamp01(t / 0.18f))
        for (i in 0 until 3) {
            val heading = -90f + i * 120f
            val dist = (26f + 150f * travel) * d
            val cx = o.x + cos(heading * DEG) * dist
            val cy = o.y + sin(heading * DEG) * dist + t * t * 26f * d
            val radius = 24f * d * pop
            if (radius < 0.5f) continue
            val start = heading + t * 520f * (if (i % 2 == 0) 1f else -1f) - 30f
            wedge.reset()
            wedge.moveTo(cx, cy)
            wedge.arcTo(Rect(cx - radius, cy - radius, cx + radius, cy + radius), start, 60f, false)
            wedge.close()
            drawPath(wedge, Fruit.Lemon, alpha = a)
            drawArc(
                color = Fruit.Lime,
                startAngle = start,
                sweepAngle = 60f,
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2f, radius * 2f),
                alpha = a,
                style = Stroke(width = 3f * d, cap = StrokeCap.Round),
            )
        }
    }

    /** Zest confetti: small flipping rectangles thrown up, then falling with gravity. */
    private fun DrawScope.burstComplete(t: Float, tau: Float, o: Offset) {
        val d = density
        val g = 1050f * d
        val s = (1f - exp(-2.2f * tau)) / 2.2f
        val a = 1f - smooth(0.7f, 1f, t)
        for (i in 0 until 26) {
            val ang = -90f + (FxMath.hash(i, 51) - 0.5f) * 130f
            val v = (420f + 520f * FxMath.hash(i, 52)) * d
            val flutter = sin(tau * (3f + 3f * FxMath.hash(i, 53)) + i) * 7f * d
            val x = o.x + cos(ang * DEG) * v * s + flutter
            val y = o.y + sin(ang * DEG) * v * s + 0.5f * g * tau * tau
            val flip = tau * (8f + 10f * FxMath.hash(i, 54)) + FxMath.hash(i, 55) * TAU
            val w = (4.5f + 3f * FxMath.hash(i, 56)) * d * max(0.15f, abs(cos(flip)))
            val h = (2.2f + 1.6f * FxMath.hash(i, 57)) * d
            drawRect(confetti[i % confetti.size], Offset(x - w / 2f, y - h / 2f), Size(w, h), alpha = a)
        }
    }

    /** A peel curl that unwinds outward, sags and fades away. */
    private fun DrawScope.burstDelete(t: Float, o: Offset, peel: Path) {
        val d = density
        val grow = easeOutCubic(FxMath.clamp01(t / 0.7f))
        val a = 1f - smooth(0.45f, 1f, t)
        val maxR = 64f * d * grow
        val sag = t * t * 56f * d
        val spin = t * 560f * DEG
        val steps = 56
        peel.reset()
        for (k in 0..steps) {
            val u = k / steps.toFloat()
            val th = u * 2.3f * TAU + spin
            val r = maxR * u
            val x = o.x + cos(th) * r
            val y = o.y + sin(th) * r + sag * (0.35f + 0.65f * u)
            if (k == 0) peel.moveTo(x, y) else peel.lineTo(x, y)
        }
        drawPath(
            peel, Fruit.Lime, alpha = a,
            style = Stroke(width = (7f - 3.5f * t) * d, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawPath(
            peel, Fruit.Lemon, alpha = a,
            style = Stroke(width = (4.2f - 2.4f * t) * d, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

    // ---- Screen transitions -------------------------------------------------

    override fun enter(pop: Boolean): EnterTransition =
        fadeIn(tween(200, delayMillis = 40)) +
            scaleIn(spring(dampingRatio = 0.55f, stiffness = 320f), initialScale = if (pop) 1.04f else 0.93f) +
            slideInVertically(spring(dampingRatio = 0.6f, stiffness = 360f)) { if (pop) -it / 24 else it / 14 }

    override fun exit(pop: Boolean): ExitTransition =
        fadeOut(tween(140)) +
            scaleOut(tween(220, easing = FastOutSlowInEasing), targetScale = if (pop) 0.96f else 0.97f) +
            slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { if (pop) it / 18 else -it / 24 }

    override val revealMillis: Int get() = 820

    /**
     * A citrus slice opening: nine pie wedges grow from [origin], one after
     * another around the slice. Each wedge overshoots a little, and at progress
     * 1 every wedge reaches past the farthest corner of [size].
     */
    override fun revealPath(size: Size, origin: Offset, progress: Float): Path {
        val path = Path()
        val p = FxMath.clamp01(progress)
        if (p <= 0f) return path
        var far = 0f
        for (c in arrayOf(Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height))) {
            val dx = c.x - origin.x
            val dy = c.y - origin.y
            far = max(far, sqrt(dx * dx + dy * dy))
        }
        val target = far * 1.04f + 2f
        val n = 9
        val step = 360f / n
        for (i in 0 until n) {
            val begin = i / n.toFloat() * 0.32f
            val q = easeOutBack(FxMath.clamp01((p - begin) / 0.68f))
            val r = target * q
            if (r < 0.5f) continue
            // Each wedge overlaps its neighbours by a degree so no hairline seam shows the old page.
            path.moveTo(origin.x, origin.y)
            path.arcTo(Rect(origin.x - r, origin.y - r, origin.x + r, origin.y + r), -90f + i * step - 1f, step + 2f, false)
            path.close()
        }
        return path
    }

    // ---- Pointer -------------------------------------------------------------

    /** Light fizz bubbles that pop where the pointer has been. */
    @Composable
    override fun PointerTrail(pointer: () -> Offset?, colors: SurfaceColors) {
        val ambient = LocalAmbientMotion.current
        val dens = LocalDensity.current.density
        val trail = remember { Trail(listOf(Fruit.Lemon, Fruit.Orange, Fruit.Lime), TRAIL_LIFE) }
        val clock = remember { mutableFloatStateOf(0f) }
        val latest = rememberUpdatedState(pointer)
        LaunchedEffect(ambient, dens) {
            if (!ambient) return@LaunchedEffect
            var lastX = Float.NaN
            var lastY = Float.NaN
            val spacing = 14f * dens
            while (true) {
                withFrameNanos { nanos ->
                    val now = nanos / 1_000_000_000f
                    val p = latest.value()
                    if (p == null) {
                        lastX = Float.NaN
                    } else {
                        val dx = p.x - lastX
                        val dy = p.y - lastY
                        if (lastX.isNaN() || sqrt(dx * dx + dy * dy) > spacing) {
                            trail.spawn(p.x, p.y, now, dens)
                            lastX = p.x
                            lastY = p.y
                        }
                    }
                    if (trail.active(now)) clock.floatValue = now
                }
            }
        }
        if (!ambient) return
        Canvas(Modifier.fillMaxSize()) {
            trail.draw(this, clock.floatValue, dens)
        }
    }

    /** A fixed pool of pop bubbles, so nothing is allocated per frame. */
    private class Trail(private val colors: List<Color>, private val life: Float) {
        private val cap = 16
        private val px = FloatArray(cap)
        private val py = FloatArray(cap)
        private val born = FloatArray(cap) { -100f }
        private val radius = FloatArray(cap)
        private val tone = IntArray(cap)
        private var next = 0
        private var seed = 0

        fun spawn(x: Float, y: Float, now: Float, dens: Float) {
            val i = next
            next = (next + 1) % cap
            seed++
            px[i] = x + (FxMath.hash(seed, 61) - 0.5f) * 12f * dens
            py[i] = y + (FxMath.hash(seed, 62) - 0.5f) * 8f * dens
            born[i] = now
            radius[i] = (2f + 2.2f * FxMath.hash(seed, 63)) * dens
            tone[i] = seed % colors.size
        }

        fun active(now: Float): Boolean {
            for (i in 0 until cap) {
                val age = now - born[i]
                if (age >= 0f && age < life + 0.1f) return true
            }
            return false
        }

        fun draw(scope: DrawScope, now: Float, dens: Float) {
            with(scope) {
                for (i in 0 until cap) {
                    val age = now - born[i]
                    if (age < 0f || age > life) continue
                    val u = age / life
                    val e = 1f - (1f - u) * (1f - u) * (1f - u)
                    val center = Offset(px[i], py[i] - 22f * dens * e)
                    val color = colors[tone[i]]
                    drawCircle(
                        color,
                        radius[i] * (1f + 1.6f * e),
                        center,
                        alpha = (1f - u) * 0.9f,
                        style = Stroke(width = (1.2f * (1f - u) + 0.4f) * dens),
                    )
                    drawCircle(color, radius[i] * (1f - 0.5f * e), center, alpha = (1f - u) * 0.35f)
                }
            }
        }
    }

    // ---- Shared math -----------------------------------------------------------

    private fun easeOutBack(x: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val u = x - 1f
        return 1f + c3 * u * u * u + c1 * u * u
    }

    private fun easeOutCubic(x: Float): Float {
        val u = 1f - x
        return 1f - u * u * u
    }

    private fun smooth(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
