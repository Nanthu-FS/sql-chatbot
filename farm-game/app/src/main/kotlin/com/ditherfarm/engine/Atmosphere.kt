package com.ditherfarm.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.max
import kotlin.random.Random

/**
 * Day/night lighting. The scene is darkened by a tinted layer whose opacity follows
 * the clock; light sources punch dithered holes in that layer, so night lighting
 * stays pure pixel art instead of smooth gradients.
 */
class Lighting {
    private var layer: Bitmap? = null
    private var layerCanvas: Canvas? = null
    private val cut = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT); isFilterBitmap = false }
    private val glow = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD); isFilterBitmap = false }
    private val plain = Paint().apply { isFilterBitmap = false }
    private val lightMasks = HashMap<Int, Bitmap>()

    /** Night/dusk tint for a clock hour 0..24: ARGB where alpha is darkness. */
    fun ambient(hour: Float): Int {
        // Keyframes: hour -> colour (alpha = how much the layer covers).
        val keys = floatArrayOf(0f, 4.5f, 6f, 7.5f, 17f, 18.5f, 20f, 24f)
        val cols = intArrayOf(
            0xB0141030.toInt(), 0xB0141030.toInt(), 0x70683860, 0x00000000,
            0x00000000, 0x30c05020, 0x88201848.toInt(), 0xB0141030.toInt(),
        )
        val h = ((hour % 24f) + 24f) % 24f
        var i = 0
        while (i < keys.size - 2 && h > keys[i + 1]) i++
        val t = ((h - keys[i]) / (keys[i + 1] - keys[i])).coerceIn(0f, 1f)
        return lerpColor(cols[i], cols[i + 1], t)
    }

    /** Darkness 0..1 for a tint from [ambient], used to decide whether lamps are lit. */
    fun darkness(tint: Int): Float = (tint ushr 24) / 255f

    class Light(var x: Float, var y: Float, var radius: Int, var warm: Boolean = true)

    /**
     * Applies lighting to [target] (already holding the world). Lights are in buffer pixels.
     * [flash] 0..1 adds a lightning flash.
     */
    fun apply(target: Canvas, w: Int, h: Int, tint: Int, lights: List<Light>, flash: Float = 0f) {
        val dark = darkness(tint)
        if (dark > 0.01f) {
            val lc = ensure(w, h)
            lc.drawColor(tint, PorterDuff.Mode.SRC)
            if (dark > 0.25f) for (l in lights) {
                val m = mask(l.radius)
                lc.drawBitmap(m, l.x - l.radius, l.y - l.radius, cut)
            }
            target.drawBitmap(layer!!, 0f, 0f, plain)
            if (dark > 0.25f) for (l in lights) if (l.warm) {
                glow.alpha = (22 * dark).toInt()
                target.drawBitmap(mask(l.radius), l.x - l.radius, l.y - l.radius, glow)
            }
        }
        if (flash > 0f) {
            plain.color = 0xFFFFFFFF.toInt()
            plain.alpha = (200 * flash).toInt().coerceIn(0, 255)
            target.drawRect(0f, 0f, w.toFloat(), h.toFloat(), plain)
            plain.alpha = 255
        }
    }

    private fun ensure(w: Int, h: Int): Canvas {
        val l = layer
        if (l == null || l.width != w || l.height != h) {
            l?.recycle()
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            layer = b
            layerCanvas = Canvas(b)
        }
        return layerCanvas!!
    }

    /** Round light with a Bayer-dithered falloff; opaque = full light. */
    private fun mask(r: Int): Bitmap = lightMasks.getOrPut(r) {
        val size = r * 2
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
        for (y in 0 until size) for (x in 0 until size) {
            // Iso-squashed circle so pools of light sit flat on the ground.
            val dx = (x - r + 0.5f) / r
            val dy = (y - r + 0.5f) / (r * 0.75f)
            val d = dx * dx + dy * dy
            if (d >= 1f) continue
            val level = ((1f - d) * 22f).toInt()
            val on = bayer[(y % 4) * 4 + (x % 4)] < level
            if (on) b.setPixel(x, y, 0xFFFF9040.toInt())
        }
        b
    }

    companion object {
        fun lerpColor(a: Int, b: Int, t: Float): Int {
            fun ch(c: Int, s: Int) = (c ushr s) and 0xFF
            fun mix(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * t).toInt().coerceIn(0, 255)
            return (mix(24) shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
        }
    }
}

/** Screen-space weather particles: rain streaks, snow, falling leaves, lightning timing. */
class Weather {
    enum class Kind { CLEAR, RAIN, STORM, SNOW, LEAVES }

    var kind = Kind.CLEAR
    var flash = 0f
        private set
    private val rnd = Random(11)
    private val xs = FloatArray(140)
    private val ys = FloatArray(140)
    private val vs = FloatArray(140)
    private var seeded = false
    private var nextBolt = 6f
    var onThunder: (() -> Unit)? = null

    fun update(dt: Float, w: Int, h: Int) {
        if (!seeded) { for (i in xs.indices) { xs[i] = rnd.nextFloat() * w; ys[i] = rnd.nextFloat() * h; vs[i] = 0.6f + rnd.nextFloat() * 0.6f }; seeded = true }
        val (vx, vy) = when (kind) {
            Kind.RAIN -> -40f to 220f
            Kind.STORM -> -90f to 300f
            Kind.SNOW -> -8f to 22f
            Kind.LEAVES -> -30f to 26f
            Kind.CLEAR -> 0f to 0f
        }
        if (kind != Kind.CLEAR) for (i in xs.indices) {
            xs[i] += vx * vs[i] * dt + if (kind == Kind.SNOW || kind == Kind.LEAVES) kotlin.math.sin((ys[i] + i * 13) * 0.05f) * 6f * dt else 0f
            ys[i] += vy * vs[i] * dt
            if (ys[i] > h) { ys[i] -= h + 4; xs[i] = rnd.nextFloat() * (w + 60) }
            if (xs[i] < -4) xs[i] += w + 8
        }
        flash = max(0f, flash - dt * 3f)
        if (kind == Kind.STORM) {
            nextBolt -= dt
            if (nextBolt <= 0f) { flash = 1f; nextBolt = 4f + rnd.nextFloat() * 8f; onThunder?.invoke() }
        }
    }

    fun draw(g: Gfx, w: Int, h: Int) {
        val c = g.canvas
        val p = g.paint
        when (kind) {
            Kind.CLEAR -> return
            Kind.RAIN, Kind.STORM -> {
                p.color = 0xAAC0CBDC.toInt()
                val n = if (kind == Kind.STORM) xs.size else xs.size * 2 / 3
                val slant = if (kind == Kind.STORM) 2f else 1f
                for (i in 0 until n) {
                    val x = xs[i].toInt().toFloat(); val y = ys[i].toInt().toFloat()
                    c.drawRect(x, y, x + 1, y + 3, p)
                    c.drawRect(x - slant, y + 3, x - slant + 1, y + 5, p)
                }
            }
            Kind.SNOW -> {
                for (i in xs.indices) {
                    p.color = if (i % 3 == 0) 0xFFFFFFFF.toInt() else 0xCCC0CBDC.toInt()
                    val s = if (i % 5 == 0) 2f else 1f
                    c.drawRect(xs[i].toInt().toFloat(), ys[i].toInt().toFloat(), xs[i].toInt() + s, ys[i].toInt() + s, p)
                }
            }
            Kind.LEAVES -> {
                for (i in 0 until 24) {
                    p.color = if (i % 2 == 0) 0xFFD77643.toInt() else 0xFFFEAE34.toInt()
                    c.drawRect(xs[i].toInt().toFloat(), ys[i].toInt().toFloat(), xs[i].toInt() + 2f, ys[i].toInt() + 1f, p)
                }
            }
        }
    }
}
