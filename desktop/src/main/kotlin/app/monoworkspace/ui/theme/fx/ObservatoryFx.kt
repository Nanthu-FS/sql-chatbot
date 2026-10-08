package app.monoworkspace.ui.theme.fx

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import app.monoworkspace.ui.theme.LocalAmbientMotion
import app.monoworkspace.ui.theme.LocalReduceMotion
import app.monoworkspace.ui.theme.SurfaceColors
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAU = 6.2831855f
private const val PI_F = 3.1415927f

private const val FAR_N = 56
private const val MID_N = 40
private const val NEAR_N = 24
private const val SKY_N = FAR_N + MID_N + NEAR_N
private const val SHOOT_CYCLE = 11f
private const val SHOOT_LIFE = 1.15f
private const val IRIS_SIDES = 8
private const val SPARKS = 14
private const val DUST = 36
private const val TRAIL_N = 16
private const val TRAIL_LIFE = 0.6f

// Palette from Themes.Observatory and the t44 mockup.
private val StarWhite = Color(0xFFE8EEFF)
private val StarBlue = Color(0xFFB8C6EA)
private val StarSteel = Color(0xFF9FB0DC)
private val StarGold = Color(0xFFF3DCA6)
private val SkyGold = Color(0xFFE2B865)
private val SilverRing = Color(0xFFBECCF0)
private val NebulaBlue = Color(0xFF4660AA)

/** Parallax speed of each depth layer, as fractions of the pane per second. */
private val LayerSpeed = floatArrayOf(0.0010f, 0.0021f, 0.0038f)

/** Constellation pairs: consecutive star indices are joined by a line. */
private val Constellations = arrayOf(intArrayOf(58, 59, 60), intArrayOf(104, 105, 106))

private fun c01(v: Float): Float = v.coerceIn(0f, 1f)

/** Cubic ease-out over 0..1. */
private fun easeOut(t: Float): Float {
    val u = 1f - c01(t)
    return 1f - u * u * u
}

private fun wrap01(v: Float): Float {
    val f = v % 1f
    return if (f < 0f) f + 1f else f
}

/** Point on a quadratic Bezier curve at parameter [v]. */
private fun bez(p0: Offset, c: Offset, p2: Offset, v: Float): Offset {
    val m = 1f - v
    val a = m * m
    val b = 2f * m * v
    val q = v * v
    return Offset(a * p0.x + b * c.x + q * p2.x, a * p0.y + b * c.y + q * p2.y)
}

/** Point on a tilted ellipse centred at ([cx], [cy]) for parametric angle [a]. */
private fun orbitPoint(cx: Float, cy: Float, rx: Float, ry: Float, cosT: Float, sinT: Float, a: Float): Offset {
    val lx = rx * cos(a)
    val ly = ry * sin(a)
    return Offset(cx + lx * cosT - ly * sinT, cy + lx * sinT + ly * cosT)
}

/** Four long points (N, E, S, W) with thin waists between them. */
private fun fourPointStar(o: Offset, r: Float, inner: Float): Path {
    val path = Path()
    for (k in 0 until 8) {
        val a = k * PI_F / 4f - PI_F / 2f
        val rr = if (k % 2 == 0) r else inner
        val x = o.x + rr * cos(a)
        val y = o.y + rr * sin(a)
        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

object ObservatoryFx : ThemeFx() {

    /** The starfield: 120 stars in three depth layers, plus two pinned constellations. */
    private class Sky {
        val bx = FloatArray(SKY_N)
        val by = FloatArray(SKY_N)
        val rad = FloatArray(SKY_N)
        val alpha = FloatArray(SKY_N)
        val phase = FloatArray(SKY_N)
        val period = FloatArray(SKY_N)
        val layer = IntArray(SKY_N)
        val tone = IntArray(SKY_N)
        val anchored = BooleanArray(SKY_N)

        init {
            for (i in 0 until SKY_N) {
                val l = if (i < FAR_N) 0 else if (i < FAR_N + MID_N) 1 else 2
                layer[i] = l
                bx[i] = FxMath.hash(i, 11)
                by[i] = FxMath.hash(i, 12)
                val size = FxMath.hash(i, 13)
                val bright = FxMath.hash(i, 14)
                rad[i] = when (l) {
                    0 -> 0.6f + 0.3f * size
                    1 -> 0.9f + 0.5f * size
                    else -> 1.4f + 0.6f * size
                }
                alpha[i] = when (l) {
                    0 -> 0.26f + 0.22f * bright
                    1 -> 0.42f + 0.28f * bright
                    else -> 0.58f + 0.26f * bright
                }
                phase[i] = FxMath.hash(i, 15)
                period[i] = 2.4f + 4.6f * FxMath.hash(i, 16)
                val hue = FxMath.hash(i, 17)
                tone[i] = if (hue < 0.14f) 2 else if (hue < 0.5f) 1 else 0
            }
            pin(58, 0.70f, 0.17f)
            pin(59, 0.755f, 0.25f)
            pin(60, 0.83f, 0.20f)
            pin(104, 0.08f, 0.66f)
            pin(105, 0.135f, 0.60f)
            pin(106, 0.19f, 0.67f)
        }

        private fun pin(i: Int, x: Float, y: Float) {
            bx[i] = x
            by[i] = y
            anchored[i] = true
            tone[i] = 0
            alpha[i] = 0.8f
            rad[i] = 1.5f
        }

        /** Horizontal position as a fraction of the pane; drifts with its layer and wraps. */
        fun xAt(i: Int, t: Float): Float =
            if (anchored[i]) bx[i] + 0.006f * sin(TAU * (t / 110f + phase[i])) else wrap01(bx[i] + LayerSpeed[layer[i]] * t)

        /** Vertical position as a fraction of the pane. */
        fun yAt(i: Int, t: Float): Float =
            if (anchored[i]) by[i] + 0.004f * sin(TAU * (t / 130f + phase[i] + 0.25f)) else wrap01(by[i] + 0.22f * LayerSpeed[layer[i]] * t)
    }

    /** One thin orbit ring with a planet travelling along it. Angles are in degrees, speeds in rad/s. */
    private class Orbit(
        val rx: Float,
        val ry: Float,
        val tilt: Float,
        val ring: Color,
        val ringAlpha: Float,
        val omega: Float,
        val phase: Float,
        val planet: Color,
        val planetDp: Float,
    )

    /** Ring of comet-tail samples: the last [TRAIL_N] pointer positions and when they were taken. */
    private class Trail {
        val x = FloatArray(TRAIL_N)
        val y = FloatArray(TRAIL_N)
        val born = FloatArray(TRAIL_N)
        var head = 0
        var count = 0
        var wasAlive = false

        fun push(px: Float, py: Float, time: Float) {
            x[head] = px
            y[head] = py
            born[head] = time
            head = (head + 1) % TRAIL_N
            if (count < TRAIL_N) count++
        }

        /** Buffer index of the k-th newest sample (k = 0 is the newest). */
        fun newest(k: Int): Int = (head - 1 - k + TRAIL_N * 2) % TRAIL_N
    }

    private val orbits = listOf(
        Orbit(rx = 0.34f, ry = 0.40f, tilt = -7f, ring = SilverRing, ringAlpha = 0.30f, omega = TAU / 24f, phase = 0.4f, planet = StarWhite, planetDp = 2.0f),
        Orbit(rx = 0.25f, ry = 0.27f, tilt = 9f, ring = SkyGold, ringAlpha = 0.55f, omega = -TAU / 33f, phase = 2.1f, planet = SkyGold, planetDp = 2.6f),
        Orbit(rx = 0.44f, ry = 0.47f, tilt = -15f, ring = StarSteel, ringAlpha = 0.16f, omega = TAU / 58f, phase = 4.0f, planet = StarBlue, planetDp = 1.4f),
    )

    private val sparkAng = FloatArray(SPARKS) { i -> TAU * (i + 0.6f * FxMath.hash(i, 4)) / SPARKS }
    private val dustAng = FloatArray(DUST) { i -> TAU * FxMath.hash(i, 21) }
    private val dustReach = FloatArray(DUST) { i -> 0.55f + 0.45f * FxMath.hash(i, 22) }
    private val dustDelay = FloatArray(DUST) { i -> FxMath.hash(i, 23) }
    private val dustSize = FloatArray(DUST) { i -> 0.6f + 1.0f * FxMath.hash(i, 24) }
    private val dustTone = IntArray(DUST) { i ->
        val h = FxMath.hash(i, 25)
        if (h < 0.2f) 2 else if (h < 0.55f) 1 else 0
    }

    // ------------------------------------------------------------ backdrop

    @Composable
    override fun Backdrop(modifier: Modifier) {
        val ambient = LocalAmbientMotion.current
        val sky = remember { Sky() }
        val clock = rememberFxClock(running = true)
        Canvas(modifier) {
            val t = if (ambient) clock.value else 0f
            drawNebula()
            drawConstellations(sky, t)
            drawStars(sky, t)
            if (ambient) drawShooters(t)
        }
    }

    private fun DrawScope.drawNebula() {
        val w = size.width
        val h = size.height
        val big = max(w, h)
        drawRect(
            Brush.radialGradient(
                0f to NebulaBlue.copy(alpha = 0.22f),
                0.7f to Color.Transparent,
                center = Offset(0.18f * w, 0.20f * h),
                radius = 0.5f * big,
            ),
        )
        drawRect(
            Brush.radialGradient(
                0f to SkyGold.copy(alpha = 0.07f),
                0.7f to Color.Transparent,
                center = Offset(0.92f * w, 0.96f * h),
                radius = 0.4f * big,
            ),
        )
    }

    private fun DrawScope.drawConstellations(sky: Sky, t: Float) {
        val w = size.width
        val h = size.height
        val breathe = 0.13f + 0.05f * sin(TAU * t / 9f)
        for (line in Constellations) {
            for (k in 0 until line.size - 1) {
                val a = line[k]
                val b = line[k + 1]
                drawLine(
                    color = StarSteel.copy(alpha = breathe),
                    start = Offset(sky.xAt(a, t) * w, sky.yAt(a, t) * h),
                    end = Offset(sky.xAt(b, t) * w, sky.yAt(b, t) * h),
                    strokeWidth = 0.7f * density,
                    cap = StrokeCap.Round,
                )
            }
        }
    }

    private fun DrawScope.drawStars(sky: Sky, t: Float) {
        val w = size.width
        val h = size.height
        val d = density
        for (i in 0 until SKY_N) {
            val twinkle = 0.55f + 0.45f * sin(TAU * (t / sky.period[i] + sky.phase[i]))
            val tone = when (sky.tone[i]) {
                2 -> StarGold
                1 -> StarBlue
                else -> StarWhite
            }
            val c = Offset(sky.xAt(i, t) * w, sky.yAt(i, t) * h)
            if (sky.layer[i] == 2) drawCircle(tone.copy(alpha = 0.10f * twinkle), 3.4f * d, c)
            drawCircle(tone.copy(alpha = sky.alpha[i] * twinkle), sky.rad[i] * d, c)
        }
    }

    /** One shooting star per cycle, starting 4 to 7 s into each 11 s slot, so gaps run 8 to 14 s. */
    private fun DrawScope.drawShooters(t: Float) {
        val k = floor(t / SHOOT_CYCLE).toInt()
        for (j in (k - 1)..k) {
            if (j < 0) continue
            val start = j * SHOOT_CYCLE + 5.5f + (FxMath.hash(j, 90) - 0.5f) * 3f
            val u = (t - start) / SHOOT_LIFE
            if (u >= 0f && u < 1f) drawShooter(j, u)
        }
    }

    private fun DrawScope.drawShooter(j: Int, u: Float) {
        val w = size.width
        val h = size.height
        val d = density
        val sx = (0.10f + 0.62f * FxMath.hash(j, 91)) * w
        val sy = (0.03f + 0.20f * FxMath.hash(j, 92)) * h
        val ang = (24f + 14f * FxMath.hash(j, 93)) * PI_F / 180f
        val dx = cos(ang)
        val dy = sin(ang)
        val len = (0.22f + 0.10f * FxMath.hash(j, 94)) * w
        val e = easeOut(u)
        val hx = sx + dx * len * e
        val hy = sy + dy * len * e
        val tail = min(len * e, 150f * d)
        if (tail < 1f) return
        val tx = hx - dx * tail
        val ty = hy - dy * tail
        val fade = c01(u * 6f) * c01((1f - u) * 2.4f)
        drawLine(
            brush = Brush.linearGradient(
                0f to Color.Transparent,
                0.8f to StarGold.copy(alpha = 0.35f * fade),
                1f to StarWhite.copy(alpha = 0.9f * fade),
                start = Offset(tx, ty),
                end = Offset(hx, hy),
            ),
            start = Offset(tx, ty),
            end = Offset(hx, hy),
            strokeWidth = 1.3f * d,
            cap = StrokeCap.Round,
        )
        drawCircle(StarWhite.copy(alpha = 0.85f * fade), 1.5f * d, Offset(hx, hy))
        drawCircle(StarGold.copy(alpha = 0.18f * fade), 4f * d, Offset(hx, hy))
    }

    // ------------------------------------------------------------ greeting

    @Composable
    override fun HeroOrnament(modifier: Modifier) {
        val ambient = LocalAmbientMotion.current
        val reduce = LocalReduceMotion.current
        val clock = rememberFxClock(running = !reduce)
        val intro = remember { Animatable(if (reduce) 1f else 0f) }
        LaunchedEffect(Unit) {
            if (intro.value < 1f) intro.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
        }
        Canvas(modifier.clipToBounds()) {
            val t = if (ambient && !reduce) clock.value else 0f
            drawOrbits(t, intro.value)
        }
    }

    /** Ornament opacity at horizontal position [x]: a fifth over the greeting (left), full from about 58% of the width. */
    private fun heroFade(x: Float, w: Float): Float {
        val s = c01((x / w - 0.22f) / 0.36f)
        return 0.2f + 0.8f * s * s * (3f - 2f * s)
    }

    /** Rings sit to the right of the greeting's centre; their left arcs are faint so the text stays clear. */
    private fun DrawScope.drawOrbits(t: Float, intro: Float) {
        if (intro <= 0f) return
        val w = size.width
        val h = size.height
        val d = density
        val cx = 0.37f * w
        val cy = 0.5f * h
        for (o in orbits) {
            val rx = o.rx * w
            val ry = o.ry * h
            val rad = o.tilt * PI_F / 180f
            val cosT = cos(rad)
            val sinT = sin(rad)
            // Brightest on the right; over the greeting (left) the ring keeps a fifth of its alpha.
            val ringBrush = Brush.linearGradient(
                0f to o.ring.copy(alpha = o.ringAlpha * 0.2f * intro),
                1f to o.ring.copy(alpha = o.ringAlpha * intro),
                start = Offset(0.22f * w, cy),
                end = Offset(0.58f * w, cy),
            )
            withTransform({ rotate(o.tilt, Offset(cx, cy)) }) {
                drawArc(
                    brush = ringBrush,
                    startAngle = -90f,
                    sweepAngle = 360f * intro,
                    useCenter = false,
                    topLeft = Offset(cx - rx, cy - ry),
                    size = Size(2f * rx, 2f * ry),
                    style = Stroke(width = d),
                )
            }
            if (intro < 0.6f) continue
            val pa = ((intro - 0.6f) / 0.4f).coerceIn(0f, 1f)
            val back = if (o.omega >= 0f) -1f else 1f
            val th = o.omega * t + o.phase
            for (k in 6 downTo 1) {
                val p = orbitPoint(cx, cy, rx, ry, cosT, sinT, th + back * k * 0.085f)
                drawCircle(o.planet.copy(alpha = 0.30f * pa * (1f - k / 7f) * heroFade(p.x, w)), 1.1f * d, p)
            }
            val head = orbitPoint(cx, cy, rx, ry, cosT, sinT, th)
            val hf = pa * heroFade(head.x, w)
            drawCircle(o.planet.copy(alpha = 0.16f * hf), 5.5f * d, head)
            drawCircle(o.planet.copy(alpha = hf), o.planetDp * d, head)
        }
    }

    // ------------------------------------------------------------ selection

    override val animatedSelection: Boolean get() = true

    override fun DrawScope.drawSelection(presence: Float, seconds: Float, colors: SurfaceColors) {
        val p = c01(presence)
        if (p <= 0f) return
        val w = size.width
        val h = size.height
        val d = density
        drawRect(colors.tint.copy(alpha = colors.tint.alpha * p))
        drawRect(colors.accent, Offset(0f, h * (1f - p) / 2f), Size(3f * d, h * p))
        // A satellite circling a small ellipse at the row's right end.
        val cx = w - 12f * d
        val cy = h / 2f
        val rx = 5.2f * d
        val ry = 2.1f * d
        drawOval(
            color = colors.accent.copy(alpha = 0.35f * p),
            topLeft = Offset(cx - rx, cy - ry),
            size = Size(2f * rx, 2f * ry),
            style = Stroke(width = 0.6f * d),
        )
        val a = seconds * 2.2f
        drawCircle(colors.accent.copy(alpha = p), 1.6f * d, Offset(cx + rx * cos(a), cy + ry * sin(a)))
    }

    // ------------------------------------------------------------ bursts

    @Composable
    override fun Burst(kind: BurstKind, origin: Offset, colors: SurfaceColors, onFinished: () -> Unit) {
        val millis = when (kind) {
            BurstKind.Create -> 900
            BurstKind.Favorite -> 820
            BurstKind.Complete -> 820
            BurstKind.Delete -> 1000
        }
        val p = remember { Animatable(0f) }
        val finish by rememberUpdatedState(onFinished)
        LaunchedEffect(Unit) {
            // finally: onFinished also fires when the overlay cancels this burst early.
            try {
                p.animateTo(1f, tween(millis, easing = LinearEasing))
            } finally {
                finish()
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val t = p.value
            when (kind) {
                BurstKind.Create -> supernova(t, origin, colors.accent)
                BurstKind.Favorite -> starFlare(t, origin, colors.accent)
                BurstKind.Complete -> cometArc(t, origin, colors.accent)
                BurstKind.Delete -> blackHole(t, origin, colors.accent)
            }
        }
    }

    /** Create: a flash, an expanding shock ring, a lagging ring and sparks flung outward. */
    private fun DrawScope.supernova(t: Float, o: Offset, gold: Color) {
        val d = density
        val fade = 1f - t
        val e = easeOut(t)
        val flash = c01(1f - t * 2.6f)
        if (flash > 0f) drawCircle(StarWhite.copy(alpha = 0.9f * flash), (3f + 9f * e) * d, o)
        drawCircle(gold.copy(alpha = 0.9f * fade), (5f + 84f * e) * d, o, style = Stroke(width = (2.4f * fade + 0.4f) * d))
        val lag = easeOut((t - 0.14f) / 0.86f)
        if (t > 0.14f) drawCircle(StarBlue.copy(alpha = 0.45f * (1f - lag)), (4f + 60f * lag) * d, o, style = Stroke(width = 0.8f * d))
        for (i in 0 until SPARKS) {
            val a = sparkAng[i]
            val dist = (48f + 62f * FxMath.hash(i, 5)) * d * e
            val len = (5f + 7f * FxMath.hash(i, 6)) * d
            val dx = cos(a)
            val dy = sin(a)
            val hx = o.x + dx * dist
            val hy = o.y + dy * dist
            val back = min(len, dist)
            val col = if (i % 3 == 0) StarWhite else gold
            drawLine(
                color = col.copy(alpha = fade),
                start = Offset(hx - dx * back, hy - dy * back),
                end = Offset(hx, hy),
                strokeWidth = (1.6f * fade + 0.2f) * d,
                cap = StrokeCap.Round,
            )
        }
    }

    /** Favorite: a four-point star swells, twinkles and throws cross streaks, then fades. */
    private fun DrawScope.starFlare(t: Float, o: Offset, gold: Color) {
        val d = density
        val swell = sqrt(max(0f, sin(PI_F * t)))
        val fade = (1f - t) * (1f - t)
        val twinkle = 0.78f + 0.22f * sin(TAU * 2.5f * t)
        val r = (2.5f + 17f * swell) * d * twinkle
        val a = fade * (0.35f + 0.65f * swell)
        drawCircle(gold.copy(alpha = 0.20f * a), r * 1.7f, o)
        drawPath(fourPointStar(o, r, r * 0.16f), color = gold.copy(alpha = a))
        val streak = a * 0.6f
        drawLine(StarWhite.copy(alpha = streak), Offset(o.x - 2.4f * r, o.y), Offset(o.x + 2.4f * r, o.y), strokeWidth = 0.8f * d, cap = StrokeCap.Round)
        drawLine(StarWhite.copy(alpha = streak), Offset(o.x, o.y - 2.4f * r), Offset(o.x, o.y + 2.4f * r), strokeWidth = 0.8f * d, cap = StrokeCap.Round)
        for (k in 0 until 4) {
            val ang = k * PI_F / 2f + t * 2f
            val sp = Offset(o.x + cos(ang) * r * 1.35f, o.y + sin(ang) * r * 1.35f)
            drawCircle(gold.copy(alpha = 0.6f * a), 0.8f * d, sp)
        }
        drawCircle(StarWhite.copy(alpha = a), 1.3f * d, o)
    }

    /** Complete: a comet streak arcs in and lands on the box, where a small ring pops. */
    private fun DrawScope.cometArc(t: Float, o: Offset, gold: Color) {
        val d = density
        val p0 = Offset(o.x - 120f * d, o.y - 86f * d)
        val c = Offset(o.x - 58f * d, o.y - 118f * d)
        val head = c01(t / 0.7f).let { it * it }
        val alpha = c01(t * 10f) * c01((0.82f - t) / 0.25f)
        if (alpha > 0f && head > 0f) {
            for (k in 0 until 10) {
                val v0 = max(0f, head - k * 0.04f)
                val v1 = max(0f, head - (k + 1) * 0.04f)
                val fk = 1f - k / 10f
                drawLine(
                    color = (if (k == 0) StarWhite else gold).copy(alpha = alpha * fk * fk),
                    start = bez(p0, c, o, v0),
                    end = bez(p0, c, o, v1),
                    strokeWidth = (2.4f - 1.8f * k / 10f) * d,
                    cap = StrokeCap.Round,
                )
            }
        }
        if (t > 0.7f) {
            val iu = c01((t - 0.7f) / 0.3f)
            val e = easeOut(iu)
            drawCircle(gold.copy(alpha = 0.8f * (1f - iu)), (2f + 16f * e) * d, o, style = Stroke(width = (1.2f * (1f - iu) + 0.3f) * d))
            for (k in 0 until 4) {
                val ang = PI_F / 4f + k * PI_F / 2f
                val dx = cos(ang)
                val dy = sin(ang)
                drawLine(
                    color = StarWhite.copy(alpha = 0.7f * (1f - iu)),
                    start = Offset(o.x + dx * (4f + 12f * e) * d * 0.4f, o.y + dy * (4f + 12f * e) * d * 0.4f),
                    end = Offset(o.x + dx * (4f + 12f * e) * d, o.y + dy * (4f + 12f * e) * d),
                    strokeWidth = 0.9f * d,
                    cap = StrokeCap.Round,
                )
            }
        }
    }

    /** Delete: a black hole grows, a tilted accretion disc tightens, and dust spirals into the centre. */
    private fun DrawScope.blackHole(t: Float, o: Offset, gold: Color) {
        val d = density
        val swell = FxMath.bump(t)
        val fade = 1f - c01((t - 0.8f) / 0.2f)
        val rx = (20f - 9f * t + 4f * swell) * d
        val ry = rx * 0.32f
        withTransform({ rotate(-18f, o) }) {
            drawOval(
                color = gold.copy(alpha = 0.65f * fade),
                topLeft = Offset(o.x - rx, o.y - ry),
                size = Size(2f * rx, 2f * ry),
                style = Stroke(width = 1.3f * d),
            )
        }
        drawCircle(Color.Black.copy(alpha = 0.92f * c01(t * 6f) * fade), (2.5f + 7f * swell) * d, o)
        for (i in 0 until DUST) {
            val q = c01((t - dustDelay[i] * 0.3f) / 0.7f)
            if (q <= 0f) continue
            val fall = 1f - q
            val r = dustReach[i] * 120f * d * fall * fall * sqrt(fall)
            val a = dustAng[i] + 2.4f * TAU * q
            val alpha = c01(q * 5f) * (1f - c01((q - 0.75f) / 0.25f)) * 0.9f * fade
            val tone = when (dustTone[i]) {
                2 -> gold
                1 -> StarBlue
                else -> StarWhite
            }
            drawCircle(tone.copy(alpha = alpha), dustSize[i] * d, Offset(o.x + cos(a) * r, o.y + sin(a) * r))
        }
    }

    // ------------------------------------------------------------ transitions

    override fun enter(pop: Boolean): EnterTransition =
        fadeIn(tween(260, easing = FastOutSlowInEasing)) +
            scaleIn(tween(260, easing = FastOutSlowInEasing), initialScale = if (pop) 1.08f else 0.9f)

    override fun exit(pop: Boolean): ExitTransition =
        fadeOut(tween(180, easing = FastOutSlowInEasing)) +
            scaleOut(tween(180, easing = FastOutSlowInEasing), targetScale = if (pop) 0.9f else 1.08f)

    override val revealMillis: Int get() = 820

    /**
     * An iris: a regular eight-sided aperture opening from [origin]. Its circumradius is
     * chosen so that the inscribed circle (radius r * cos(pi / 8)) reaches the farthest
     * corner, which makes the shape cover the whole [size] at progress 1.
     */
    override fun revealPath(size: Size, origin: Offset, progress: Float): Path {
        val p = c01(progress)
        val far = max(
            max(hypot(origin.x, origin.y), hypot(size.width - origin.x, origin.y)),
            max(hypot(origin.x, size.height - origin.y), hypot(size.width - origin.x, size.height - origin.y)),
        )
        val reach = far / cos(PI_F / IRIS_SIDES) + 4f
        val e = 1f - (1f - p) * (1f - p) * (1f - p)
        val r = reach * e
        val rot = p * 0.8f * TAU / IRIS_SIDES
        val path = Path()
        for (k in 0 until IRIS_SIDES) {
            val a = rot + TAU * k / IRIS_SIDES
            val x = origin.x + r * cos(a)
            val y = origin.y + r * sin(a)
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    // ------------------------------------------------------------ pointer

    /** The last sixteen pointer positions, drawn as gold sparks that fade within 0.6 s. */
    @Composable
    override fun PointerTrail(pointer: () -> Offset?, colors: SurfaceColors) {
        if (!LocalAmbientMotion.current) return
        val pxPerDp = LocalDensity.current.density
        val trail = remember { Trail() }
        val clock = remember { mutableFloatStateOf(0f) }
        val source by rememberUpdatedState(pointer)
        LaunchedEffect(Unit) {
            while (true) {
                withFrameNanos { nanos ->
                    val now = nanos / 1_000_000_000f
                    val p = source()
                    var moved = false
                    if (p != null) {
                        val last = trail.newest(0)
                        if (trail.count == 0 || hypot(p.x - trail.x[last], p.y - trail.y[last]) > 2f * pxPerDp) {
                            trail.push(p.x, p.y, now)
                            moved = true
                        }
                    }
                    val alive = trail.count > 0 && now - trail.born[trail.newest(0)] < TRAIL_LIFE
                    // One extra write after the last sample expires clears the canvas.
                    if (moved || alive || trail.wasAlive) clock.floatValue = now
                    trail.wasAlive = alive
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val now = clock.floatValue
            for (k in 0 until trail.count) {
                val i = trail.newest(k)
                val age = now - trail.born[i]
                if (age < 0f || age >= TRAIL_LIFE) continue
                val u = age / TRAIL_LIFE
                val rank = 1f - k.toFloat() / TRAIL_N
                val a = 0.35f * (1f - u) * (1f - u) * rank
                val pos = Offset(trail.x[i], trail.y[i])
                drawCircle(colors.accent.copy(alpha = a), (1.3f - 0.6f * u) * density, pos)
                if (k + 1 < trail.count) {
                    val j = trail.newest(k + 1)
                    val ageJ = now - trail.born[j]
                    if (ageJ >= 0f && ageJ < TRAIL_LIFE) {
                        drawLine(
                            color = colors.accent.copy(alpha = a * 0.5f),
                            start = pos,
                            end = Offset(trail.x[j], trail.y[j]),
                            strokeWidth = 0.7f * density,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }
    }
}
