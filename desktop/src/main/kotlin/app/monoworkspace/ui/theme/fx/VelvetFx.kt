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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import app.monoworkspace.ui.theme.LocalAmbientMotion
import app.monoworkspace.ui.theme.LocalReduceMotion
import app.monoworkspace.ui.theme.SurfaceColors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * Motion personality of the Velvet Rose theme: aubergine velvet, rose-gold
 * edges and a soft pink glow.
 *
 * Bokeh glows breathe slowly behind the screens and now and then a petal drifts
 * down. The Home greeting catches a rose-gold sheen every few seconds, with
 * petals resting on its right edge. The selected row is a lit pill with a
 * pulsing halo. Screens zoom in like a velvet curtain, switching to this theme
 * blooms open like a rose, and celebrations are a bloom, a twinkle, falling
 * glitter or wilting petals. The pointer leaves a little glitter dust behind.
 */
object VelvetFx : ThemeFx() {

    private const val TAU = 6.2831855f
    private const val PI_F = 3.1415927f
    private const val HALF_PI = 1.5707964f
    private const val RAD_TO_DEG = 57.29578f

    /** Clock value used for the single still frame when ambient motion is off (a petal mid-fall). */
    private const val STILL_T = 7f

    /** Number of rose petals in the reveal outline. */
    private const val PETALS = 5f

    private const val PETAL_EVERY = 8.4f
    private const val PETAL_FALL = 14f
    private const val SHEEN_EVERY = 7f
    private const val SHEEN_SPAN = 2.4f
    private const val GLITTER_COUNT = 34
    private const val WILT_COUNT = 6
    private const val DUST_CAP = 56
    private const val DUST_RATE = 20f

    // ---- Palette: rose-gold, blush and pink from the brief; two plums from the Velvet ThemeSpec ----

    private val RoseGold = Color(0xFFE6B29C)
    private val Blush = Color(0xFFF4CFBD)
    private val Pink = Color(0xFFE9A7B4)

    /** The Velvet cover gradient's plum stop (Themes.kt). */
    private val Plum = Color(0xFF6D2B52)

    /** The Velvet hot-surface secondary wine (Themes.kt). */
    private val Wilt = Color(0xFF5A2A45)

    // ---- Unit shapes, built once ---------------------------------------------------

    /** Curved teardrop petal: base at the origin, tip at (0, -1). */
    private val petalPath: Path by lazy {
        Path().apply {
            moveTo(0f, 0f)
            cubicTo(0.66f, -0.16f, 0.50f, -0.92f, 0f, -1f)
            cubicTo(-0.48f, -0.92f, -0.64f, -0.16f, 0f, 0f)
            close()
        }
    }

    /** Four-point sparkle with tips at unit distance on both axes. */
    private val sparklePath: Path by lazy {
        Path().apply {
            moveTo(0f, -1f)
            quadraticBezierTo(0.12f, -0.12f, 1f, 0f)
            quadraticBezierTo(0.12f, 0.12f, 0f, 1f)
            quadraticBezierTo(-0.12f, 0.12f, -1f, 0f)
            quadraticBezierTo(-0.12f, -0.12f, 0f, -1f)
            close()
        }
    }

    // ---- Small math helpers ----------------------------------------------------------

    private fun clamp01(v: Float) = v.coerceIn(0f, 1f)

    private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = clamp01((x - e0) / (e1 - e0))
        return t * t * (3f - 2f * t)
    }

    private fun easeOut(t: Float): Float {
        val u = 1f - clamp01(t)
        return 1f - u * u * u
    }

    private fun backOut(t: Float): Float {
        val x = clamp01(t) - 1f
        return 1f + 2.70158f * x * x * x + 1.70158f * x * x
    }

    private fun mix(a: Color, b: Color, t: Float): Color {
        val k = clamp01(t)
        return Color(
            red = a.red + (b.red - a.red) * k,
            green = a.green + (b.green - a.green) * k,
            blue = a.blue + (b.blue - a.blue) * k,
            alpha = a.alpha + (b.alpha - a.alpha) * k,
        )
    }

    /** A petal whose base sits at [base] and whose tip points along [angle] (radians, screen space, 0 = right, y down). */
    private fun DrawScope.petal(base: Offset, angle: Float, length: Float, color: Color, alpha: Float, width: Float) {
        if (length <= 0.01f || alpha <= 0.002f) return
        translate(base.x, base.y) {
            rotate(degrees = (angle + HALF_PI) * RAD_TO_DEG, pivot = Offset.Zero) {
                scale(scaleX = length * width, scaleY = length, pivot = Offset.Zero) {
                    drawPath(petalPath, color, alpha = alpha)
                }
            }
        }
    }

    /** A four-point sparkle centred on [center] with [radius] to its tips. */
    private fun DrawScope.sparkle(center: Offset, radius: Float, angle: Float, color: Color, alpha: Float) {
        if (radius <= 0.01f || alpha <= 0.002f) return
        translate(center.x, center.y) {
            rotate(degrees = angle * RAD_TO_DEG, pivot = Offset.Zero) {
                scale(scaleX = radius, scaleY = radius, pivot = Offset.Zero) {
                    drawPath(sparklePath, color, alpha = alpha)
                }
            }
        }
    }

    // ---- Backdrop: bokeh orbs and falling petals ---------------------------------

    private class Orb(
        val fx: Float,
        val fy: Float,
        val reach: Float,
        val period: Float,
        val phase: Float,
        val alpha: Float,
        val drift: Float,
        tone: Color,
    ) {
        val stops: List<Color> = listOf(tone, tone.copy(alpha = 0f))
    }

    /** Seven bokeh orbs. Their alpha stays in the 0.05 to 0.11 band, so text stays readable. */
    private val orbs = arrayOf(
        Orb(fx = 0.10f, fy = 0.16f, reach = 0.40f, period = 44f, phase = 0.00f, alpha = 0.10f, drift = 0.060f, tone = Pink),
        Orb(fx = 0.90f, fy = 0.12f, reach = 0.34f, period = 38f, phase = 0.31f, alpha = 0.08f, drift = 0.050f, tone = RoseGold),
        Orb(fx = 0.72f, fy = 0.88f, reach = 0.46f, period = 52f, phase = 0.64f, alpha = 0.11f, drift = 0.040f, tone = Pink),
        Orb(fx = 0.32f, fy = 0.60f, reach = 0.28f, period = 31f, phase = 0.12f, alpha = 0.06f, drift = 0.070f, tone = Blush),
        Orb(fx = 0.04f, fy = 0.96f, reach = 0.42f, period = 47f, phase = 0.77f, alpha = 0.10f, drift = 0.050f, tone = Plum),
        Orb(fx = 0.96f, fy = 0.52f, reach = 0.26f, period = 36f, phase = 0.45f, alpha = 0.07f, drift = 0.060f, tone = RoseGold),
        Orb(fx = 0.56f, fy = 0.20f, reach = 0.22f, period = 27f, phase = 0.90f, alpha = 0.05f, drift = 0.080f, tone = Blush),
    )

    @Composable
    override fun Backdrop(modifier: Modifier) {
        val ambient = LocalAmbientMotion.current
        val clock = rememberFxClock(running = true)
        if (LocalReduceMotion.current) return
        Canvas(modifier) {
            // With ambient motion off this is one still frame: a petal mid-fall, the orbs at rest.
            val t = if (ambient) clock.value else STILL_T
            val w = size.width
            val h = size.height
            val span = max(w, h)

            // Static velvet light: a pink wash top right and a plum pool bottom left.
            drawRect(
                Brush.radialGradient(
                    listOf(Pink.copy(alpha = 0.07f), Pink.copy(alpha = 0f)),
                    center = Offset(w * 0.92f, 0f),
                    radius = w * 0.55f,
                ),
            )
            drawRect(
                Brush.radialGradient(
                    listOf(Plum.copy(alpha = 0.20f), Plum.copy(alpha = 0f)),
                    center = Offset(0f, h),
                    radius = w * 0.6f,
                ),
            )

            // Bokeh orbs: they drift on slow loops and breathe between 86% and 100% of their alpha.
            for (o in orbs) {
                val a = TAU * (t / o.period + o.phase)
                val cx = o.fx * w + sin(a) * o.drift * w
                val cy = o.fy * h + cos(a * 0.7f) * o.drift * 0.8f * h
                val breathe = 0.86f + 0.14f * (0.5f + 0.5f * sin(TAU * (t / (o.period * 0.45f)) + o.phase * 6f))
                val r = o.reach * span * (0.94f + 0.06f * sin(TAU * t / (o.period * 0.9f)))
                drawCircle(
                    brush = Brush.radialGradient(o.stops, center = Offset(cx, cy), radius = r),
                    radius = r,
                    center = Offset(cx, cy),
                    alpha = o.alpha * breathe,
                )
            }

            // One petal every PETAL_EVERY seconds; each falls for PETAL_FALL seconds and sways as it goes.
            val first = floor((t - PETAL_FALL) / PETAL_EVERY).toInt() + 1
            val last = floor(t / PETAL_EVERY).toInt()
            for (k in first..last) {
                val age = t - k * PETAL_EVERY
                val f = age / PETAL_FALL
                if (f < 0f || f > 1f) continue
                val x0 = (0.06f + 0.88f * FxMath.hash(k, 41)) * w
                val sway = sin(age * 0.9f + FxMath.hash(k, 42) * TAU) * 30f * density
                val y = lerpF(-30f * density, h + 30f * density, f)
                val fade = smooth(0f, 0.08f, f) * (1f - smooth(0.72f, 1f, f))
                val tip = HALF_PI + sin(age * 1.2f + k) * 0.32f
                petal(
                    base = Offset(x0 + sway, y),
                    angle = tip,
                    length = (13f + 7f * FxMath.hash(k, 43)) * density,
                    color = Pink,
                    alpha = 0.28f * fade,
                    width = 0.6f,
                )
            }
        }
    }

    // ---- Hero: rose-gold sheen and resting petals ---------------------------------

    private class Resting(
        val fx: Float,
        val fy: Float,
        val length: Float,
        val angle: Float,
        val phase: Float,
        val tone: Color,
    )

    /** Three petals lying on the right edge of the greeting, well clear of the text. */
    private val restingPetals = arrayOf(
        Resting(fx = 0.82f, fy = 0.70f, length = 30f, angle = 0.45f, phase = 0.0f, tone = Pink),
        Resting(fx = 0.91f, fy = 0.30f, length = 24f, angle = -2.3f, phase = 1.7f, tone = Blush),
        Resting(fx = 0.97f, fy = 0.60f, length = 17f, angle = 1.2f, phase = 3.1f, tone = RoseGold),
    )

    @Composable
    override fun HeroOrnament(modifier: Modifier) {
        val ambient = LocalAmbientMotion.current
        val clock = rememberFxClock(running = true)
        // One-shot intro: the petals settle onto the edge once. Skipped when ambient motion is off.
        val settle = remember { Animatable(if (ambient) 0f else 1f) }
        LaunchedEffect(ambient) {
            // Turning ambient motion off mid-intro jumps to the settled pose instead of freezing part-way.
            if (!ambient) settle.snapTo(1f)
            else if (settle.value < 1f) settle.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        }
        if (LocalReduceMotion.current) return
        Canvas(modifier) {
            val t = if (ambient) clock.value else STILL_T
            val s = settle.value
            val w = size.width
            val h = size.height

            // A faint warm halo behind the greeting. The text sits on top, so it stays low.
            drawRect(
                Brush.radialGradient(
                    listOf(Pink.copy(alpha = 0.10f), Pink.copy(alpha = 0f)),
                    center = Offset(w * 0.14f, h * 0.5f),
                    radius = h * 1.5f,
                ),
            )

            // Rose-gold sheen: one narrow diagonal band sweeps across every SHEEN_EVERY seconds.
            val sweep = (t % SHEEN_EVERY) / SHEEN_SPAN
            if (sweep < 1f) {
                val env = sin(PI_F * sweep)
                val cx = lerpF(-0.2f * w, 1.2f * w, smooth(0f, 1f, sweep))
                val half = 62f * density
                rotate(degrees = -22f, pivot = Offset(cx, h * 0.5f)) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                RoseGold.copy(alpha = 0f),
                                RoseGold.copy(alpha = 0.15f * env),
                                Blush.copy(alpha = 0.12f * env),
                                RoseGold.copy(alpha = 0f),
                            ),
                            startX = cx - half,
                            endX = cx + half,
                        ),
                        topLeft = Offset(cx - half, -h),
                        size = Size(half * 2f, h * 3f),
                    )
                }
            }

            // Resting petals: they sway a few degrees and settle in during the intro.
            for (r in restingPetals) {
                val base = Offset(r.fx * w, r.fy * h - (1f - s) * 44f * density)
                val angle = r.angle + sin(t * 0.8f + r.phase) * 0.09f
                petal(
                    base = base,
                    angle = angle,
                    length = r.length * density,
                    color = r.tone,
                    alpha = 0.62f * s,
                    width = 0.55f,
                )
            }
        }
    }

    // ---- Selection: a lit rose-gold pill ---------------------------------------------

    override val animatedSelection: Boolean get() = true

    override fun DrawScope.drawSelection(presence: Float, seconds: Float, colors: SurfaceColors) {
        val p = clamp01(presence)
        if (p <= 0f) return
        val w = size.width
        val h = size.height
        val pulse = 0.5f + 0.5f * sin(seconds * TAU / 2.6f)
        val glow = p * (0.55f + 0.45f * pulse)
        val pillW = (w - 8f * density) * (0.9f + 0.1f * p)
        val pillH = (h - 6f * density) * (0.86f + 0.14f * p)
        val left = (w - pillW) / 2f
        val top = (h - pillH) / 2f

        // Soft rose-gold halo: a few stacked rounded rects that fade outwards.
        for (i in 4 downTo 1) {
            val e = i * 2.4f * density
            drawRoundRect(
                color = RoseGold,
                topLeft = Offset(left - e, top - e),
                size = Size(pillW + 2f * e, pillH + 2f * e),
                cornerRadius = CornerRadius(pillH / 2f + e),
                alpha = glow * 0.14f / i,
            )
        }

        // The pill itself, in the row's tint.
        drawRoundRect(
            color = colors.tint,
            topLeft = Offset(left, top),
            size = Size(pillW, pillH),
            cornerRadius = CornerRadius(pillH / 2f),
            alpha = colors.tint.alpha * p,
        )

        // Hairline rose-gold edge that breathes with the glow.
        drawRoundRect(
            color = RoseGold,
            topLeft = Offset(left, top),
            size = Size(pillW, pillH),
            cornerRadius = CornerRadius(pillH / 2f),
            alpha = 0.55f * p * (0.6f + 0.4f * pulse),
            style = Stroke(width = 1f * density),
        )

        // The 3dp rose-gold bar on the leading edge.
        val barH = pillH * p
        drawRoundRect(
            color = RoseGold,
            topLeft = Offset(0f, (h - barH) / 2f),
            size = Size(3f * density, barH),
            cornerRadius = CornerRadius(1.5f * density),
            alpha = p,
        )
    }

    // ---- Bursts -------------------------------------------------------------------------

    private class Spark(val dx: Float, val dy: Float, val size: Float, val delay: Float)

    /** Star cluster for Favorite: offsets and sizes in dp, staggered starts. */
    private val sparks = arrayOf(
        Spark(0f, 0f, 12f, 0.00f),
        Spark(-15f, -9f, 6.5f, 0.12f),
        Spark(12f, -14f, 8f, 0.07f),
        Spark(11f, 12f, 5f, 0.18f),
    )

    @Composable
    override fun Burst(kind: BurstKind, origin: Offset, colors: SurfaceColors, onFinished: () -> Unit) {
        val millis = when (kind) {
            BurstKind.Create -> 900
            BurstKind.Favorite -> 980
            BurstKind.Complete -> 1100
            BurstKind.Delete -> 1080
        }
        val p = remember { Animatable(0f) }
        val done by rememberUpdatedState(onFinished)
        LaunchedEffect(Unit) {
            p.animateTo(1f, tween(durationMillis = millis, easing = LinearEasing))
            done()
        }
        Canvas(Modifier.fillMaxSize()) {
            val t = p.value
            when (kind) {
                BurstKind.Create -> bloom(origin, t)
                BurstKind.Favorite -> twinkle(origin, t)
                BurstKind.Complete -> glitter(origin, t)
                BurstKind.Delete -> wilt(origin, t)
            }
        }
    }

    /** Create: seven petals open radially from the origin, then fade. */
    private fun DrawScope.bloom(origin: Offset, t: Float) {
        val fade = 1f - smooth(0.45f, 1f, t)
        if (fade <= 0f) return
        val open = easeOut(t / 0.6f)

        val glowR = (6f + 30f * open) * density
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Blush.copy(alpha = 0.55f * fade), Blush.copy(alpha = 0f)),
                center = origin,
                radius = glowR,
            ),
            radius = glowR,
            center = origin,
        )

        for (i in 0 until 7) {
            val dir = TAU * i / 7f + (FxMath.hash(i, 51) - 0.5f) * 0.4f + 0.35f * t
            val d = (3f + 17f * open) * density
            val len = (15f + 9f * FxMath.hash(i, 52)) * open * density
            val tone = when (i % 3) {
                0 -> Pink
                1 -> Blush
                else -> RoseGold
            }
            petal(
                base = Offset(origin.x + cos(dir) * d, origin.y + sin(dir) * d),
                angle = dir,
                length = len,
                color = tone,
                alpha = 0.9f * fade,
                width = 0.5f,
            )
        }
    }

    /** Favorite: a rose-gold halo ring and a cluster of four-point sparkles that pop and twinkle. */
    private fun DrawScope.twinkle(origin: Offset, t: Float) {
        val fade = 1f - smooth(0.6f, 1f, t)
        if (fade <= 0f) return

        drawCircle(
            color = RoseGold,
            radius = (4f + 22f * easeOut(t / 0.75f)) * density,
            center = origin,
            alpha = 0.35f * (1f - smooth(0.15f, 0.9f, t)),
            style = Stroke(width = 1.3f * density),
        )

        for (s in sparks) {
            val local = clamp01((t - s.delay) / 0.5f)
            if (local <= 0f) continue
            val pop = backOut(local)
            val twinkleK = 0.5f + 0.5f * sin(t * TAU * 1.5f + s.delay * 25f)
            sparkle(
                center = Offset(origin.x + s.dx * density, origin.y + s.dy * density),
                radius = s.size * density * pop * (0.85f + 0.15f * twinkleK),
                angle = 0.25f * t,
                color = mix(RoseGold, Blush, twinkleK),
                alpha = fade,
            )
        }
    }

    /** Complete: rose-gold glitter sprays up from the origin, then falls under gravity. */
    private fun DrawScope.glitter(origin: Offset, t: Float) {
        val fade = 1f - smooth(0.55f, 1f, t)
        if (fade <= 0f) return
        val secs = t * 1.1f

        drawCircle(
            color = Blush,
            radius = (6f + 24f * easeOut(t / 0.3f)) * density,
            center = origin,
            alpha = 0.3f * (1f - smooth(0.05f, 0.4f, t)),
        )

        for (i in 0 until GLITTER_COUNT) {
            val up = 180f + 230f * FxMath.hash(i, 62)
            val spread = (FxMath.hash(i, 63) - 0.5f) * 2f * (60f + 160f * FxMath.hash(i, 64))
            val x = origin.x + spread * secs * density
            val y = origin.y + (-up * secs + 0.5f * 880f * secs * secs) * density
            val twinkle = 0.45f + 0.55f * sin(t * TAU * (1.5f + 2f * FxMath.hash(i, 65)) + FxMath.hash(i, 66) * TAU)
            val tone = when (i % 3) {
                0 -> Blush
                1 -> RoseGold
                else -> Pink
            }
            sparkle(
                center = Offset(x, y),
                radius = (1.6f + 2.2f * FxMath.hash(i, 67)) * density * twinkle,
                angle = t * 2f * (if (i % 2 == 0) 1f else -1f) + i,
                color = tone,
                alpha = fade * (0.5f + 0.5f * twinkle),
            )
        }
    }

    /** Delete: six petals stand up, wilt over to their sides and drop with gravity, darkening as they go. */
    private fun DrawScope.wilt(origin: Offset, t: Float) {
        val fade = 1f - smooth(0.55f, 1f, t)
        if (fade <= 0f) return
        val secs = t * 1.08f
        val wilted = smooth(0.04f, 0.5f, t)

        for (i in 0 until WILT_COUNT) {
            val side = (FxMath.hash(i, 71) - 0.5f) * 2f
            val vx = side * (36f + 64f * FxMath.hash(i, 72))
            val vy = -(26f + 44f * FxMath.hash(i, 73))
            val base = Offset(
                origin.x + vx * secs * density,
                origin.y + (vy * secs + 0.5f * 620f * secs * secs) * density,
            )
            // The tip swings from straight up, past the side, to pointing down.
            val angle = -HALF_PI + (HALF_PI + side * 0.7f) * wilted
            petal(
                base = base,
                angle = angle,
                length = (12f + 6f * FxMath.hash(i, 74)) * density * (1f - 0.3f * wilted),
                color = mix(Pink, Wilt, wilted),
                alpha = 0.9f * fade,
                width = 0.55f - 0.15f * wilted,
            )
        }
    }

    // ---- Screen transitions -----------------------------------------------------------

    /** Velvet zoom in: fade up while the screen settles from 0.94 on a soft spring. */
    override fun enter(pop: Boolean): EnterTransition =
        fadeIn(tween(300)) +
            scaleIn(
                animationSpec = spring(dampingRatio = 0.74f, stiffness = 300f),
                initialScale = if (pop) 1.02f else 0.94f,
            )

    /** Velvet zoom out: fade away while the screen drifts to 1.02. */
    override fun exit(pop: Boolean): ExitTransition =
        fadeOut(tween(200)) +
            scaleOut(
                animationSpec = tween(320, easing = FastOutSlowInEasing),
                targetScale = if (pop) 0.94f else 1.02f,
            )

    override val revealMillis: Int get() = 820

    /**
     * A blooming rose: a five-petal outline whose radius is modulated by
     * |cos(PETALS/2 * angle)|. It opens out of [origin] and unwinds its twist
     * as it grows. Its smallest radius is 1.06 times the farthest corner
     * distance at progress 1, so it covers the whole [size].
     */
    override fun revealPath(size: Size, origin: Offset, progress: Float): Path {
        val t = clamp01(progress)
        val far = maxOf(
            hypot(origin.x, origin.y),
            hypot(size.width - origin.x, origin.y),
            hypot(origin.x, size.height - origin.y),
            hypot(size.width - origin.x, size.height - origin.y),
        ).coerceAtLeast(1f)
        val g = easeOut(t)
        val twist = (1f - g) * 0.9f
        val path = Path()
        val n = 180
        for (i in 0..n) {
            val a = TAU * i / n
            val lobe = abs(cos(PETALS * 0.5f * (a + twist))).pow(0.7f)
            val r = far * g * (1.06f + 0.82f * lobe)
            val x = origin.x + cos(a) * r
            val y = origin.y + sin(a) * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    // ---- Pointer glitter dust ---------------------------------------------------------

    /** Ring buffer of glitter particles, stepped once per frame. Positions are in px, sizes and speeds in dp. */
    private class Dust {
        val x = FloatArray(DUST_CAP)
        val y = FloatArray(DUST_CAP)
        val vx = FloatArray(DUST_CAP)
        val vy = FloatArray(DUST_CAP)
        val age = FloatArray(DUST_CAP)
        val life = FloatArray(DUST_CAP)
        val size = FloatArray(DUST_CAP)
        val tone = IntArray(DUST_CAP)
        var alive = 0
        private var emit = 0f
        private var seed = 0

        fun step(dt: Float, pointer: Offset?, density: Float) {
            if (pointer != null) {
                emit += dt * DUST_RATE
                while (emit >= 1f) {
                    emit -= 1f
                    spawn(pointer, density)
                }
            } else {
                emit = 0f
            }
            var n = 0
            for (i in 0 until DUST_CAP) {
                if (age[i] >= life[i]) continue
                age[i] += dt
                if (age[i] >= life[i]) continue
                val sway = sin(age[i] * 5f + tone[i] * 2f) * 9f
                x[i] += (vx[i] + sway) * dt * density
                y[i] += vy[i] * dt * density
                vy[i] += 14f * dt
                n++
            }
            alive = n
        }

        private fun spawn(p: Offset, density: Float) {
            val start = seed % DUST_CAP
            var slot = -1
            for (k in 0 until DUST_CAP) {
                val i = (start + k) % DUST_CAP
                if (age[i] >= life[i]) {
                    slot = i
                    break
                }
            }
            if (slot < 0) return
            seed = (seed + 1) % 1_000_003
            val r = seed
            x[slot] = p.x + (FxMath.hash(r, 1) - 0.5f) * 16f * density
            y[slot] = p.y + (FxMath.hash(r, 2) - 0.5f) * 10f * density
            vx[slot] = (FxMath.hash(r, 3) - 0.5f) * 14f
            vy[slot] = 24f + 26f * FxMath.hash(r, 4)
            age[slot] = 0f
            life[slot] = 0.8f + 0.55f * FxMath.hash(r, 5)
            size[slot] = 0.9f + 1.3f * FxMath.hash(r, 6)
            tone[slot] = r % 3
        }
    }

    private fun DrawScope.drawDust(d: Dust) {
        for (i in 0 until DUST_CAP) {
            val life = d.life[i]
            if (d.age[i] >= life) continue
            val f = d.age[i] / life
            val a = 0.42f * (1f - f) * smooth(0f, 0.12f, f)
            if (a <= 0.005f) continue
            val center = Offset(d.x[i], d.y[i])
            val r = d.size[i] * density
            when (d.tone[i]) {
                0 -> sparkle(center, r * 1.7f, d.age[i] * 2.2f, RoseGold, a)
                1 -> drawCircle(color = Pink, radius = r, center = center, alpha = a)
                else -> drawCircle(color = Blush, radius = r * 0.8f, center = center, alpha = a)
            }
        }
    }

    @Composable
    override fun PointerTrail(pointer: () -> Offset?, colors: SurfaceColors) {
        val dust = remember { Dust() }
        val frame = remember { mutableIntStateOf(0) }
        val density = LocalDensity.current.density
        val currentPointer by rememberUpdatedState(pointer)
        if (!LocalAmbientMotion.current) return
        LaunchedEffect(Unit) {
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last < 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    last = now
                    val p = currentPointer()
                    dust.step(dt, p, density)
                    if (p != null || dust.alive > 0) frame.intValue++
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            // Reading the frame counter here makes the canvas redraw once per step.
            if (frame.intValue >= 0) drawDust(dust)
        }
    }
}
