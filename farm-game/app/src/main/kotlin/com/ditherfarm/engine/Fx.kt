package com.ditherfarm.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object Ease {
    fun outCubic(t: Float) = 1f - (1f - t).let { it * it * it }
    fun outBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val u = t - 1f
        return 1f + c3 * u * u * u + c1 * u * u
    }
    fun inOutSine(t: Float) = (-(cos(PI * t) - 1) / 2).toFloat()
}

/** Pooled sprite particles and floating text, drawn in world space. */
class Fx {
    private class Particle {
        var sprite = ""
        var x = 0f; var y = 0f; var vx = 0f; var vy = 0f; var g = 0f
        var life = 0f; var max = 1f
        var fade = true
        var alive = false
    }

    private class Floater {
        var text = ""
        var icon: String? = null
        var color = Gfx.WHITE
        var x = 0f; var y = 0f
        var life = 0f
        var alive = false
    }

    private val parts = Array(160) { Particle() }
    private val floaters = Array(24) { Floater() }
    var shake = 0f
        private set
    private val rnd = Random(7)

    fun burst(sprite: String, x: Float, y: Float, count: Int, speed: Float = 40f, gravity: Float = 90f, life: Float = 0.6f, up: Boolean = true) {
        repeat(count) {
            val p = parts.firstOrNull { !it.alive } ?: return
            val a = if (up) (-PI / 2 + (rnd.nextFloat() - 0.5f) * PI * 0.9f) else rnd.nextFloat() * 2 * PI
            val s = speed * (0.5f + rnd.nextFloat() * 0.7f)
            p.sprite = sprite; p.x = x; p.y = y
            p.vx = (cos(a) * s).toFloat(); p.vy = (sin(a) * s).toFloat(); p.g = gravity
            p.max = life * (0.7f + rnd.nextFloat() * 0.5f); p.life = p.max
            p.fade = true; p.alive = true
        }
    }

    /** A single particle that rises and fades, e.g. a sparkle over a ready crop. */
    fun rise(sprite: String, x: Float, y: Float, life: Float = 0.8f) {
        val p = parts.firstOrNull { !it.alive } ?: return
        p.sprite = sprite; p.x = x; p.y = y; p.vx = 0f; p.vy = -14f; p.g = 0f
        p.max = life; p.life = life; p.fade = true; p.alive = true
    }

    fun floatText(text: String, x: Float, y: Float, color: Int = Gfx.WHITE, icon: String? = null) {
        val f = floaters.firstOrNull { !it.alive } ?: floaters.minByOrNull { it.life } ?: return
        f.text = text; f.icon = icon; f.color = color; f.x = x; f.y = y; f.life = 1.1f; f.alive = true
    }

    fun shake(amount: Float) { shake = maxOf(shake, amount) }

    fun update(dt: Float) {
        for (p in parts) if (p.alive) {
            p.life -= dt
            if (p.life <= 0f) { p.alive = false; continue }
            p.vy += p.g * dt
            p.x += p.vx * dt; p.y += p.vy * dt
        }
        for (f in floaters) if (f.alive) {
            f.life -= dt
            if (f.life <= 0f) f.alive = false else f.y -= 18f * dt
        }
        shake = (shake - dt * 10f).coerceAtLeast(0f)
    }

    fun drawWorld(g: Gfx) {
        for (p in parts) if (p.alive) {
            val a = if (p.fade) (255 * (p.life / p.max).coerceIn(0f, 1f)).toInt() else 255
            g.sprite(p.sprite, p.x, p.y, a)
        }
    }

    /** Floating text is drawn at zoom 1 so it stays readable; caller maps world→buffer. */
    fun drawText(g: Gfx, toBx: (Float) -> Float, toBy: (Float) -> Float) {
        for (f in floaters) if (f.alive) {
            val bx = toBx(f.x).toInt()
            val by = toBy(f.y).toInt()
            val w = g.textWidth(f.text) + if (f.icon != null) 11 else 0
            var x = bx - w / 2
            f.icon?.let { g.sprite(it, (x + 4).toFloat(), (by + 8).toFloat()); x += 11 }
            g.text(f.text, x, by, f.color)
        }
    }

    fun shakeOffset(t: Float): Int = if (shake <= 0f) 0 else (sin(t * 70f) * shake).toInt()
}
