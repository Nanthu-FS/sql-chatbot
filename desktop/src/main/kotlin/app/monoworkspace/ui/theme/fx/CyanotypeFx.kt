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
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import app.monoworkspace.ui.theme.LocalAmbientMotion
import app.monoworkspace.ui.theme.LocalReduceMotion
import app.monoworkspace.ui.theme.SurfaceColors
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Motion personality of the Cyanotype theme: a sun print on Prussian blue.
 *
 * - Backdrop: white fern silhouettes sway a few degrees, and every twelve
 *   seconds a soft diagonal band of light crosses the pane like an exposure.
 * - Greeting: a fiddlehead on a logarithmic-spiral coil uncurls into a frond.
 * - Selection: the row develops from white-cyan into its tint, with a pale
 *   two-dp edge and a slow glint.
 * - Bursts: pressed leaves settle, sun rays flare, dandelion seeds drift up,
 *   and deletion overexposes to white.
 * - Transitions: a slow-start develop in, a quick fade out, and a wipe of
 *   light from the edge nearest the click when the theme changes.
 */
object CyanotypeFx : ThemeFx() {

    private const val TAU = 6.2831855f
    private const val HALF_PI = 1.5707964f
    private const val PI_F = 3.1415927f
    private const val RAD_TO_DEG = 57.29578f

    // Colors from Themes.Cyanotype: the print's white, its pale cyan and the frond stalk.
    private val Paper = Color(0xFFF2FBFF)
    private val PaleCyan = Color(0xFFA9DCEF)
    private val Frond = Color(0xFFBDE9F7)
    private val HotWhite = Color(0xFFFFFFFF)

    /** Slow start, soft landing: the image comes up out of the paper. */
    private val Develop = CubicBezierEasing(0.5f, 0f, 0.3f, 1f)

    private const val CURL_N = 96
    private const val COIL_START = 0.45f
    private const val COIL_EYE = 0.12f
    private const val CURL_TURN = 4.5f
    private const val CURL_ARCH = 2.2f

    // ---------------------------------------------------------------- backdrop

    private class FrondSpec(
        /** Unit silhouette: base at (0, 0), growing toward negative y. */
        val path: Path,
        /** Anchor as fractions of the pane size. */
        val ax: Float,
        val ay: Float,
        /** Length as a fraction of the pane height. */
        val len: Float,
        /** -1 flips the arch to the other side. */
        val mirror: Float,
        /** Resting rotation, degrees. */
        val angle: Float,
        val phase: Float,
        val alpha: Float,
    )

    private val fronds: List<FrondSpec> by lazy {
        listOf(
            frond(seed = 1, ax = 0.97f, ay = 1.03f, len = 0.80f, mirror = -1f, angle = -6f, phase = 0f, alpha = 0.085f),
            frond(seed = 2, ax = 0.03f, ay = 1.04f, len = 0.62f, mirror = 1f, angle = 8f, phase = 1.9f, alpha = 0.07f),
            frond(seed = 3, ax = 0.56f, ay = 1.05f, len = 0.40f, mirror = -1f, angle = 2f, phase = 3.3f, alpha = 0.065f),
            frond(seed = 4, ax = 1.03f, ay = 0.02f, len = 0.52f, mirror = 1f, angle = 158f, phase = 4.6f, alpha = 0.06f),
        )
    }

    @Composable
    override fun Backdrop(modifier: Modifier) {
        val ambient = LocalAmbientMotion.current
        val clock = rememberFxClock(running = true)
        Canvas(modifier) {
            val t = if (ambient) clock.value else 0f
            val w = size.width
            val h = size.height
            for (f in fronds) {
                val sway = if (ambient) 3.5f * sin(TAU * t / 8f + f.phase) else 0f
                withTransform({
                    translate(f.ax * w, f.ay * h)
                    rotate(f.angle + sway, Offset.Zero)
                    scale(f.len * h * f.mirror, f.len * h, Offset.Zero)
                }) {
                    drawPath(f.path, Paper.copy(alpha = f.alpha))
                }
            }
            if (ambient) exposureSweep(t)
        }
    }

    /** A soft diagonal band of light, one pass every twelve seconds. */
    private fun DrawScope.exposureSweep(t: Float) {
        val cycle = 12f
        val run = 3.4f
        val at = t % cycle
        if (at >= run) return
        val e = (1f - cos(PI_F * at / run)) / 2f
        val w = size.width
        val h = size.height
        val nx = 0.7508f
        val ny = 0.6606f
        val reach = nx * w + ny * h
        val half = reach * 0.12f
        val c = -half + (reach + 2f * half) * e
        drawRect(
            brush = Brush.linearGradient(
                0f to Color.Transparent,
                0.5f to Paper.copy(alpha = 0.065f),
                1f to Color.Transparent,
                start = Offset(nx * (c - half), ny * (c - half)),
                end = Offset(nx * (c + half), ny * (c + half)),
            ),
        )
    }

    /** Builds a fern frond: a tapered rachis with alternating pinnae, arching to the right. */
    private fun frond(
        seed: Int, ax: Float, ay: Float, len: Float, mirror: Float, angle: Float, phase: Float, alpha: Float,
    ): FrondSpec {
        val n = 40
        val xs = FloatArray(n + 1)
        val ys = FloatArray(n + 1)
        val hd = FloatArray(n + 1)
        var x = 0f
        var y = 0f
        for (i in 0..n) {
            val s = i / n.toFloat()
            xs[i] = x
            ys[i] = y
            hd[i] = 1.3f * s * s
            if (i == n) break
            val mid = 1.3f * ((i + 0.5f) / n).let { it * it }
            x += sin(mid) / n
            y -= cos(mid) / n
        }
        // The rachis runs along (sin h, -cos h), so its width offset must be the normal (cos h, sin h).
        val path = Path()
        for (i in 0..n) {
            val s = i / n.toFloat()
            val hw = 0.0035f * (1f - 0.7f * s)
            val lx = xs[i] + cos(hd[i]) * hw
            val ly = ys[i] + sin(hd[i]) * hw
            if (i == 0) path.moveTo(lx, ly) else path.lineTo(lx, ly)
        }
        for (i in n downTo 0) {
            val s = i / n.toFloat()
            val hw = 0.0035f * (1f - 0.7f * s)
            path.lineTo(xs[i] - cos(hd[i]) * hw, ys[i] - sin(hd[i]) * hw)
        }
        path.close()
        var k = 0
        for (i in 5 until n - 1 step 3) {
            val s = i / n.toFloat()
            val side = if (k % 2 == 0) 1f else -1f
            val dx = sin(hd[i])
            val dy = -cos(hd[i])
            val a = side * 0.95f
            val ca = cos(a)
            val sa = sin(a)
            val ex = dx * ca - dy * sa
            val ey = dx * sa + dy * ca
            val lf = 0.12f * (1f - 0.5f * s) * (0.85f + 0.3f * FxMath.hash(i, seed))
            val bx = xs[i]
            val by = ys[i]
            val mx = bx + ex * lf * 0.5f
            val my = by + ey * lf * 0.5f
            val px = -ey * lf * 0.22f
            val py = ex * lf * 0.22f
            path.moveTo(bx, by)
            path.quadraticBezierTo(mx + px, my + py, bx + ex * lf, by + ey * lf)
            path.quadraticBezierTo(mx - px, my - py, bx, by)
            path.close()
            k++
        }
        return FrondSpec(path, ax, ay, len, mirror, angle, phase, alpha)
    }

    // ---------------------------------------------------------------- greeting

    private class CurlBuf {
        val x = FloatArray(CURL_N + 1)
        val y = FloatArray(CURL_N + 1)
        val heading = FloatArray(CURL_N + 1)
    }

    @Composable
    override fun HeroOrnament(modifier: Modifier) {
        val ambient = LocalAmbientMotion.current
        val reduce = LocalReduceMotion.current
        val unfurl = remember { Animatable(if (reduce) 1f else 0f) }
        LaunchedEffect(Unit) {
            if (!reduce) unfurl.animateTo(1f, tween(2000, easing = FastOutSlowInEasing))
        }
        val clock = rememberFxClock(running = true)
        val curl = remember { CurlBuf() }
        val stem = remember { Path() }
        val leaves = remember { Path() }
        Canvas(modifier) {
            val g = unfurl.value.coerceIn(0f, 1f)
            val w = size.width
            val h = size.height
            integrateCurl(curl, 1f - g)
            buildFiddlehead(curl, g, stem, leaves)
            val base = Offset(w * 0.84f, h * 0.9f)
            val len = h * 0.72f * (0.45f + 0.55f * g)
            val sway = if (ambient) 1.4f * sin(TAU * clock.value / 8f + 0.6f) else 0f
            val glowAt = Offset(base.x, base.y - len * 0.5f)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Frond.copy(alpha = 0.16f * g),
                    1f to Frond.copy(alpha = 0f),
                    center = glowAt,
                    radius = len * 0.9f,
                ),
                radius = len * 0.9f,
                center = glowAt,
            )
            withTransform({
                translate(base.x, base.y)
                rotate(sway, Offset.Zero)
                scale(len, len, Offset.Zero)
            }) {
                drawPath(stem, Frond.copy(alpha = 0.9f))
                drawPath(leaves, PaleCyan.copy(alpha = 0.6f))
            }
        }
    }

    /**
     * Integrates the frond's heading along its length (unit total length,
     * base at the origin, up is negative y). The open arch is a constant
     * curvature; the coil adds a logarithmic-spiral term that grows toward
     * the tip. [coil] 1 is a closed fiddlehead, 0 is an open frond.
     */
    private fun integrateCurl(buf: CurlBuf, coil: Float) {
        val gain = CURL_TURN * coil
        val ds = 1f / CURL_N
        var x = 0f
        var y = 0f
        var phi = -HALF_PI - 0.35f
        for (i in 0..CURL_N) {
            buf.x[i] = x
            buf.y[i] = y
            buf.heading[i] = phi
            if (i == CURL_N) break
            val s = (i + 0.5f) / CURL_N
            val kappa = CURL_ARCH + (if (s > COIL_START) gain / max(1f - s, COIL_EYE) else 0f)
            val mid = phi + 0.5f * kappa * ds
            x += cos(mid) * ds
            y += sin(mid) * ds
            phi += kappa * ds
        }
    }

    /** Turns the integrated curl into a stalk ribbon and, once unfurling, pinnae along it. */
    private fun buildFiddlehead(buf: CurlBuf, g: Float, stem: Path, leaves: Path) {
        stem.reset()
        leaves.reset()
        for (i in 0..CURL_N) {
            val s = i / CURL_N.toFloat()
            val hw = 0.008f * (1f - 0.6f * s)
            val lx = buf.x[i] - sin(buf.heading[i]) * hw
            val ly = buf.y[i] + cos(buf.heading[i]) * hw
            if (i == 0) stem.moveTo(lx, ly) else stem.lineTo(lx, ly)
        }
        for (i in CURL_N downTo 0) {
            val s = i / CURL_N.toFloat()
            val hw = 0.008f * (1f - 0.6f * s)
            stem.lineTo(buf.x[i] + sin(buf.heading[i]) * hw, buf.y[i] - cos(buf.heading[i]) * hw)
        }
        stem.close()

        val grow = smoothstep(0.25f, 0.95f, g)
        if (grow <= 0f) return
        var k = 0
        for (i in 16..CURL_N - 6 step 6) {
            val s = i / CURL_N.toFloat()
            val side = if (k % 2 == 0) 1f else -1f
            val dx = cos(buf.heading[i])
            val dy = sin(buf.heading[i])
            val a = side * 0.9f
            val ca = cos(a)
            val sa = sin(a)
            val ex = dx * ca - dy * sa
            val ey = dx * sa + dy * ca
            val lf = 0.16f * (1f - 0.5f * s) * grow * (0.85f + 0.3f * FxMath.hash(i, 7))
            val bx = buf.x[i]
            val by = buf.y[i]
            val mx = bx + ex * lf * 0.5f
            val my = by + ey * lf * 0.5f
            val px = -ey * lf * 0.2f
            val py = ex * lf * 0.2f
            leaves.moveTo(bx, by)
            leaves.quadraticBezierTo(mx + px, my + py, bx + ex * lf, by + ey * lf)
            leaves.quadraticBezierTo(mx - px, my - py, bx, by)
            leaves.close()
            k++
        }
    }

    // ---------------------------------------------------------------- selection

    override val animatedSelection: Boolean get() = true

    /**
     * The row develops: a white-cyan front at the left settles into the tint
     * as presence grows, a 2 dp pale edge grows from the center, and a glint
     * drifts across every seven seconds.
     */
    override fun DrawScope.drawSelection(presence: Float, seconds: Float, colors: SurfaceColors) {
        val p = presence.coerceIn(0f, 1f)
        if (p <= 0f) return
        val w = size.width
        val h = size.height
        val bright = lerp(colors.tint, colors.accent, 0.85f)
        val reach = 0.2f + 0.7f * easeOutCubic(p)
        drawRect(
            brush = Brush.linearGradient(
                0f to bright.copy(alpha = 0.95f),
                reach to colors.tint,
                1f to colors.tint,
                start = Offset.Zero,
                end = Offset(w, 0f),
            ),
            alpha = p,
        )
        drawRect(colors.accent.copy(alpha = p), Offset(0f, h * (1f - p) / 2f), Size(2f * density, h * p))

        val ph = (seconds % 7f) / 7f
        if (ph < 0.42f) {
            val cx = w * (-0.25f + 1.5f * (ph / 0.42f))
            val half = w * 0.2f
            drawRect(
                brush = Brush.linearGradient(
                    0f to Color.Transparent,
                    0.5f to bright.copy(alpha = 0.22f * p),
                    1f to Color.Transparent,
                    start = Offset(cx - half, 0f),
                    end = Offset(cx + half, 0f),
                ),
            )
        }
    }

    // ---------------------------------------------------------------- bursts

    private val pressedLeaf: Path by lazy {
        Path().apply {
            moveTo(0f, 0f)
            quadraticBezierTo(0.42f, -0.46f, 1f, 0.02f)
            quadraticBezierTo(0.5f, 0.34f, 0f, 0f)
            close()
        }
    }

    @Composable
    override fun Burst(kind: BurstKind, origin: Offset, colors: SurfaceColors, onFinished: () -> Unit) {
        val millis = when (kind) {
            BurstKind.Create -> 900
            BurstKind.Favorite -> 760
            BurstKind.Complete -> 1000
            BurstKind.Delete -> 760
        }
        val p = remember { Animatable(0f) }
        val finished = rememberUpdatedState(onFinished)
        LaunchedEffect(Unit) {
            try {
                p.animateTo(1f, tween(millis, easing = LinearEasing))
            } finally {
                // Runs on early cancellation too, so the overlay always releases this burst.
                finished.value()
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val t = p.value
            when (kind) {
                BurstKind.Create -> leafScatter(t, origin, colors)
                BurstKind.Favorite -> sunFlare(t, origin, colors)
                BurstKind.Complete -> dandelionSeeds(t, origin, colors)
                BurstKind.Delete -> overexpose(t, origin)
            }
        }
    }

    /** Seven pressed leaves scatter outward, drop a little, and settle away. */
    private fun DrawScope.leafScatter(t: Float, origin: Offset, colors: SurfaceColors) {
        val alpha = sm(t / 0.12f) * (1f - sm((t - 0.5f) / 0.5f))
        if (alpha <= 0f) return
        val settle = easeOutCubic(t)
        val fall = t * t * 12f * density
        for (i in 0 until 7) {
            val ang = (i / 7f) * TAU + (FxMath.hash(i, 11) - 0.5f) * 0.6f - HALF_PI
            val dist = (30f + FxMath.hash(i, 12) * 28f) * density * settle
            val pos = Offset(origin.x + cos(ang) * dist, origin.y + sin(ang) * dist + fall)
            val leafLen = (16f + FxMath.hash(i, 13) * 8f) * density
            val spin = if (i % 2 == 0) 1f else -1f
            val rot = ang * RAD_TO_DEG + 90f + (1f - settle) * 160f * spin
            withTransform({
                translate(pos.x, pos.y)
                rotate(rot, Offset.Zero)
                scale(leafLen, leafLen, Offset.Zero)
            }) {
                drawPath(pressedLeaf, colors.accent.copy(alpha = alpha * 0.9f))
            }
        }
    }

    /** A bright core, a glow, and fourteen thin rays that reach out and thin away. */
    private fun DrawScope.sunFlare(t: Float, origin: Offset, colors: SurfaceColors) {
        val fade = 1f - t
        val grow = easeOutCubic(t)
        val glow = (10f + 26f * grow) * density
        drawCircle(
            brush = Brush.radialGradient(
                0f to colors.accent.copy(alpha = 0.85f * fade),
                1f to colors.accent.copy(alpha = 0f),
                center = origin,
                radius = glow,
            ),
            radius = glow,
            center = origin,
        )
        drawCircle(colors.accent.copy(alpha = fade), (3.5f + 2f * fade) * density, origin)
        drawCircle(
            colors.accent.copy(alpha = 0.35f * fade),
            (6f + 22f * grow) * density,
            origin,
            style = Stroke(width = 1f * density),
        )
        for (i in 0 until 14) {
            val ang = i / 14f * TAU + 0.12f
            val dir = Offset(cos(ang), sin(ang))
            val long = if (i % 2 == 0) 1f else 0.55f
            val inner = (7f + 5f * grow) * density
            val outer = inner + (12f + 46f * long * grow) * density
            drawLine(
                color = colors.accent.copy(alpha = 0.9f * fade),
                start = origin + dir * inner,
                end = origin + dir * outer,
                strokeWidth = (1.3f - 0.6f * t) * density,
                cap = StrokeCap.Round,
            )
        }
    }

    /** Nine dandelion seeds: a dot with a fan of fine filaments, drifting upward. */
    private fun DrawScope.dandelionSeeds(t: Float, origin: Offset, colors: SurfaceColors) {
        val alpha = sm(t / 0.14f) * (1f - sm((t - 0.55f) / 0.45f))
        if (alpha <= 0f) return
        val rise = easeOutCubic(t)
        for (i in 0 until 9) {
            val drift = (FxMath.hash(i, 21) - 0.5f) * 56f * density
            val height = (40f + FxMath.hash(i, 22) * 46f) * density * rise
            val wobble = sin(t * 8f + FxMath.hash(i, 23) * TAU) * 6f * density * t
            val seed = Offset(origin.x + drift + wobble, origin.y - height)
            drawCircle(colors.accent.copy(alpha = alpha), 1.8f * density, seed)
            val filament = (5.5f + FxMath.hash(i, 24) * 3f) * density
            val tilt = FxMath.hash(i, 25) * 0.3f
            for (k in 0 until 7) {
                val a = -HALF_PI + (k - 3) * 0.32f + tilt
                drawLine(
                    colors.accent.copy(alpha = alpha * 0.6f),
                    seed,
                    seed + Offset(cos(a), sin(a)) * filament,
                    strokeWidth = 0.6f * density,
                )
            }
        }
    }

    /** The spot overexposes to white, then fades. */
    private fun DrawScope.overexpose(t: Float, origin: Offset) {
        val flash = if (t < 0.3f) sm(t / 0.3f) else 1f - sm((t - 0.3f) / 0.7f)
        if (flash <= 0f) return
        val r = (18f + 24f * easeOutCubic(t)) * density
        drawCircle(
            brush = Brush.radialGradient(
                0f to HotWhite.copy(alpha = flash),
                0.6f to HotWhite.copy(alpha = flash * 0.55f),
                1f to HotWhite.copy(alpha = 0f),
                center = origin,
                radius = r,
            ),
            radius = r,
            center = origin,
        )
    }

    // ---------------------------------------------------------------- transitions

    override fun enter(pop: Boolean): EnterTransition {
        val dir = if (pop) -1 else 1
        return fadeIn(tween(420, delayMillis = 60, easing = Develop)) +
            slideInVertically(tween(420, delayMillis = 60, easing = Develop)) { (dir * it * 0.02f).toInt() }
    }

    override fun exit(pop: Boolean): ExitTransition = fadeOut(tween(160))

    override val revealMillis: Int get() = 780

    /**
     * An exposure wipe. The front is a line tilted about ten degrees off the
     * axis, starting at the window edge nearest [origin] and moving across.
     * Its leading edge wavers by up to half of [soft] so it reads as a
     * feathered fringe, and it is drawn as a polygon of sampled points.
     */
    override fun revealPath(size: Size, origin: Offset, progress: Float): Path {
        val w = size.width
        val h = size.height
        val tilt = 0.17f
        val c = cos(tilt)
        val s = sin(tilt)
        val nearest = min(min(origin.x, w - origin.x), min(origin.y, h - origin.y))
        val nx: Float
        val ny: Float
        if (nearest == origin.x) {
            nx = c
            ny = s
        } else if (nearest == w - origin.x) {
            nx = -c
            ny = s
        } else if (nearest == origin.y) {
            nx = s
            ny = c
        } else {
            nx = -s
            ny = -c
        }

        // Project the window onto the wipe normal (u) and its tangent (v).
        val cxs = floatArrayOf(0f, w, 0f, w)
        val cys = floatArrayOf(0f, 0f, h, h)
        var umin = Float.MAX_VALUE
        var umax = -Float.MAX_VALUE
        var vmin = Float.MAX_VALUE
        var vmax = -Float.MAX_VALUE
        for (k in 0..3) {
            val u = cxs[k] * nx + cys[k] * ny
            val v = -cxs[k] * ny + cys[k] * nx
            umin = min(umin, u)
            umax = max(umax, u)
            vmin = min(vmin, v)
            vmax = max(vmax, v)
        }

        val soft = min(w, h) * 0.14f
        val pad = soft * 0.5f + 2f
        val pr = progress.coerceIn(0f, 1f)
        val front = (umin - pad) + (umax - umin + 2f * pad) * pr
        val vlo = vmin - 2f
        val vhi = vmax + 2f
        val vspan = vhi - vlo
        val far = umin - (w + h)

        fun toXY(u: Float, v: Float) = Offset(u * nx - v * ny, u * ny + v * nx)

        val steps = 28
        val path = Path()
        val first = toXY(far, vlo)
        path.moveTo(first.x, first.y)
        for (k in 0..steps) {
            val v = vlo + vspan * k / steps
            val wave = 0.6f * sin(TAU * v / (vspan * 0.8f) + 0.8f) + 0.4f * sin(TAU * v / (vspan * 0.31f) + 2.3f)
            val edge = front + soft * 0.5f * wave
            val pt = toXY(edge, v)
            path.lineTo(pt.x, pt.y)
        }
        val last = toXY(far, vhi)
        path.lineTo(last.x, last.y)
        path.close()
        return path
    }

    // ---------------------------------------------------------------- helpers

    /** Smoothstep from 0 to 1 over [0, 1]. */
    private fun sm(x: Float): Float = smoothstep(0f, 1f, x)

    private fun smoothstep(a: Float, b: Float, x: Float): Float {
        val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun easeOutCubic(x: Float): Float {
        val c = 1f - x.coerceIn(0f, 1f)
        return 1f - c * c * c
    }
}
