package com.ditherfarm.ui

import android.graphics.LightingColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import com.ditherfarm.Game
import com.ditherfarm.engine.Gfx
import com.ditherfarm.engine.Screen
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The launch intro (round 7, owner picks: a fake loading screen, then the title reveal; "go all
 * out"). First a loading bar that stalls and lurches like a real one while a wheat sprout grows over
 * it, with farm puns and Grandpa Elias's sayings underneath. Then the title: night over ruined
 * Willowmere turns to sunrise; the title's letters drop in as grey broken boards, and one by one are
 * hammered back into fresh planks (a flash, a pop, wood chips, a clink); the ribbon with THE unfurls,
 * a shimmer runs across, and the title breathes while birds cross and petals drift. Any tap skips to
 * the finished title; a tap there starts the game.
 */
class IntroScreen(private val game: Game, private val onDone: () -> Unit) : Screen() {
    private val g get() = game.gfx
    private enum class Phase { LOADING, TITLE }

    private var phase = Phase.LOADING
    private var t = 0f
    private var lines = emptyList<String>()
    private val rnd = Random(game.host.nowMs())
    private var tinked = BooleanArray(LETTERS)
    private var unfurled = false
    private var leaving = false

    override fun enter() {
        val puns = PUNS.shuffled(rnd)
        val sayings = SAYINGS.shuffled(rnd)
        lines = (0 until 6).map { if (it % 2 == 0) puns[it / 2] else sayings[it / 2] }
    }

    override fun update(dt: Float) {
        t += dt
        if (phase == Phase.LOADING && t >= LOAD_TIME + 0.35f) {
            phase = Phase.TITLE
            t = 0f
            game.engine.dissolve()
        }
        if (phase == Phase.TITLE) {
            for (i in 0 until LETTERS) if (!tinked[i] && t >= restoreAt(i)) {
                tinked[i] = true
                game.sfx.play("tink", 0.55f, 0.15f)
                game.haptics.tick()
            }
            if (!unfurled && t >= RIBBON_AT) { unfurled = true; game.sfx.play("swap", 0.6f) }
        }
        chips.removeAll { it.update(dt) }
    }

    override fun tap(x: Float, y: Float) {
        when {
            phase == Phase.LOADING -> { t = LOAD_TIME + 0.35f }
            t < IDLE_AT -> { t = IDLE_AT; tinked.fill(true); unfurled = true; chips.clear() }
            !leaving -> { leaving = true; game.sfx.play("unlock", 0.7f); onDone() }
        }
    }

    override fun draw(g: Gfx) {
        if (phase == Phase.LOADING) drawLoading() else drawTitle()
    }

    // ------------------------------------------------------------ loading

    /** Fake progress: quick to a third, a stall, a lurch, a crawl, a last stall, done. */
    private fun progress(): Float {
        val k = (t / LOAD_TIME).coerceIn(0f, 1f)
        return when {
            k < 0.25f -> k / 0.25f * 0.36f
            k < 0.42f -> 0.36f + (k - 0.25f) * 0.12f
            k < 0.5f -> 0.38f + (k - 0.42f) / 0.08f * 0.32f
            k < 0.8f -> 0.7f + (k - 0.5f) / 0.3f * 0.22f
            k < 0.94f -> 0.92f + (k - 0.8f) * 0.2f
            else -> 0.948f + (k - 0.94f) / 0.06f * 0.052f
        }.coerceIn(0f, 1f)
    }

    private fun drawLoading() {
        val w = game.engine.w
        val h = game.engine.h
        g.rect(0, 0, w, h, INK)
        // Dark corners.
        for (k in 0 until 5) {
            val a = (0x18 + k * 0x0C) shl 24
            val inset = 30 - k * 6
            g.rect(0, 0, w, inset, a); g.rect(0, h - inset, w, h, a)
        }
        val p = progress()
        val cx = w / 2f
        val cy = h / 2f - 30f
        // The sprout grows with the bar (and does a little dance when it's done).
        val stage = (p * 4f).toInt().coerceAtMost(3)
        val bob = if (p >= 1f) -abs(sin(t * 9f)) * 3f else 0f
        // A mound of soil with the sprout in it, a soft light on it.
        for (r in 4 downTo 1) {
            val rr = 18 + r * 6
            g.rect((cx - rr).toInt(), (cy + 22 - r).toInt(), (cx + rr).toInt(), (cy + 26 + r).toInt(), ((10 + r * 6) shl 24) or 0xFEE761)
        }
        g.rect((cx - 22).toInt(), (cy + 20).toInt(), (cx + 22).toInt(), (cy + 26).toInt(), 0xFF733E39.toInt())
        g.rect((cx - 18).toInt(), (cy + 17).toInt(), (cx + 18).toInt(), (cy + 20).toInt(), 0xFF733E39.toInt())
        g.rect((cx - 18).toInt(), (cy + 17).toInt(), (cx + 18).toInt(), (cy + 18).toInt(), 0xFFB86F50.toInt())
        g.rect((cx - 22).toInt(), (cy + 26).toInt(), (cx + 22).toInt(), (cy + 28).toInt(), 0xFF3E2731.toInt())
        g.spriteScaled("crops/wheat_$stage", cx, cy + 20f + bob, 4f, 4f)
        val c = g.canvas
        c.save(); c.translate(w / 2f, cy - 92f); c.scale(2f, 2f)
        g.text("REBUILD THE VILLAGE", 0, 0, Gfx.GOLD, align = 0)
        c.restore()
        // The bar.
        val bw = 160; val bh = 10
        val bx = w / 2 - bw / 2; val by = (cy + 40).toInt()
        g.rect(bx - 2, by - 2, bx + bw + 2, by + bh + 2, 0xFF3A4466.toInt())
        g.rect(bx - 1, by - 1, bx + bw + 1, by + bh + 1, INK)
        g.rect(bx, by, bx + bw, by + bh, 0xFF262B44.toInt())
        val fw = (bw * p).toInt()
        g.rect(bx, by, bx + fw, by + bh, 0xFF3E8948.toInt())
        g.rect(bx, by, bx + fw, by + 3, 0xFF63C74D.toInt())
        g.rect(bx, by + bh - 2, bx + fw, by + bh, 0xFF265C42.toInt())
        // A glint running along the filled part.
        if (fw > 4) {
            val gx = bx + ((t * 90f) % fw).toInt()
            g.rect(gx, by + 1, gx + 2, by + 3, 0xFFFFFFFF.toInt())
        }
        g.text("${(p * 100).toInt()}%", bx + bw + 6, by + 1, Gfx.WHITE)
        // What the farm is up to.
        val i = ((t / 0.62f).toInt()).coerceAtMost(lines.size - 1)
        val line = if (p >= 1f) "READY!" else lines[i]
        val quote = line.startsWith("\"")
        val wrapped = wrap(line, w - 40)
        wrapped.forEachIndexed { k, l -> g.text(l, w / 2, by + 22 + k * 11, if (quote) 0xFFEAD4AA.toInt() else 0xFFC0CBDC.toInt(), align = 0) }
        if (quote) g.text("- GRANDPA ELIAS", w / 2, by + 26 + wrapped.size * 11, 0xFF8B9BB4.toInt(), align = 0)
    }

    private fun wrap(s: String, maxW: Int): List<String> {
        val out = ArrayList<String>()
        var cur = ""
        for (word in s.split(" ")) {
            val next = if (cur.isEmpty()) word else "$cur $word"
            if (g.textWidth(next) > maxW && cur.isNotEmpty()) { out += cur; cur = word } else cur = next
        }
        if (cur.isNotEmpty()) out += cur
        return out
    }

    // ------------------------------------------------------------ title

    private fun dropAt(i: Int) = 1.0f + i * 0.07f
    private fun restoreAt(i: Int) = 2.9f + i * 0.11f

    /** Sky keyframes (title/sky_0..4) and when each is fully in. */
    private val skyTimes = floatArrayOf(0f, 1.0f, 2.0f, 3.0f, 4.8f)
    /** The land's light at the same keyframes: night blue to full colour. */
    private val landTints = intArrayOf(0x2A2A4A, 0x504468, 0x9A6E86, 0xF0B898, 0xFFFFFF)
    private val tintCache = HashMap<Int, LightingColorFilter>()

    private fun keyframe(): Pair<Int, Float> {
        for (k in 0 until skyTimes.size - 1) if (t < skyTimes[k + 1]) return k to ((t - skyTimes[k]) / (skyTimes[k + 1] - skyTimes[k])).coerceIn(0f, 1f)
        return skyTimes.size - 2 to 1f
    }

    private fun mix(a: Int, b: Int, f: Float): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) + ((((b shr s) and 0xFF) - ((a shr s) and 0xFF)) * f)).toInt().coerceIn(0, 255)
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun landFilter(): LightingColorFilter? {
        val (k, f) = keyframe()
        val c = mix(landTints[k], landTints[k + 1], f)
        if (c == 0xFFFFFF) return null
        // Quantised so the filters can be kept.
        val key = c and 0xF8F8F8
        return tintCache.getOrPut(key) { LightingColorFilter(key, 0) }
    }

    private fun drawTitle() {
        val w = game.engine.w
        val h = game.engine.h
        val groundTop = h - 150
        val farTop = groundTop - 128
        val skyTop = farTop + 40 - 300
        val (k, f) = keyframe()
        // Sky: the keyframe and the next one crossfading; above the art, the sky's top colour.
        val a = game.assets["title/sky_$k"]
        g.rect(0, 0, w, h, a.bmp.getPixel(0, 0))
        g.sprite(a, 0f, skyTop.toFloat())
        if (f > 0f) {
            val b = game.assets["title/sky_${k + 1}"]
            if (skyTop > 0) g.rect(0, 0, w, skyTop, (((f * 255).toInt()) shl 24) or (b.bmp.getPixel(0, 0) and 0xFFFFFF))
            g.sprite(b, 0f, skyTop.toFloat(), (f * 255).toInt())
        }
        // Twinkling stars while it's still dark.
        val night = (1f - t / 2.2f).coerceIn(0f, 1f)
        if (night > 0f) for (s in 0 until 22) {
            val sx = (s * 97) % w; val sy = 10 + (s * 53) % (farTop - 20).coerceAtLeast(40)
            val tw = (sin(t * 3f + s * 1.7f) * 0.5f + 0.5f) * night
            g.rect(sx, sy, sx + 1, sy + 1, (((tw * 255).toInt()) shl 24) or 0xFFFFFF)
        }
        // The camera settles: it starts low and rises as the sun does, the near layers moving further
        // than the far ones (parallax), then sways a touch while the title idles.
        val settle = (1f - (t / IDLE_AT).coerceIn(0f, 1f)).let { it * it }
        fun lift(depth: Float) = settle * 80f * depth + (if (t > IDLE_AT) sin(t * 0.7f) * 1.5f * depth else 0f)
        // The sun climbs out from behind the far hills, its rays turning slowly.
        val rise = ((t - 0.8f) / 4.2f).coerceIn(0f, 1f)
        val ease = 1f - (1f - rise) * (1f - rise)
        val sunY = farTop + 50f - ease * 120f + lift(0.1f)
        if (rise > 0f) {
            val c = g.canvas
            c.save(); c.translate(192f, sunY); c.rotate(t * 3f)
            g.sprite("title/rays", 0f, 0f, (rise * 200).toInt())
            c.restore()
        }
        g.sprite("title/sun", 192f, sunY, (80 + 175 * rise).toInt())
        // Clouds drift.
        g.sprite("title/cloud_a", ((t * 4f + 30f) % (w + 80f)) - 40f, farTop - 110f, (120 + 135 * rise).toInt())
        g.sprite("title/cloud_b", w + 30f - ((t * 2.5f + 60f) % (w + 80f)), farTop - 150f, (120 + 135 * rise).toInt())
        // Birds cross once the sun is up.
        if (t > 3.2f) for (bi in 0 until 5) {
            val bx = (t - 3.2f) * 34f - bi * 14f - 20f
            val byy = farTop - 70f + bi % 2 * 6f + sin(t * 2f + bi) * 3f
            if (bx > -10 && bx < w + 10) bird(bx.toInt(), byy.toInt(), ((t * 8f).toInt() + bi) % 2 == 0)
        }
        // The land, lit as the sun rises.
        val lit = landFilter()
        fun layer(name: String, y: Float) = if (lit != null) g.spriteFiltered(name, 0f, y, lit) else g.sprite(name, 0f, y)
        // Haze between the ridges takes the colour of the sky at the horizon, so the far land melts
        // into the air.
        val horizon = (if (f > 0.5f) game.assets["title/sky_${k + 1}"] else a).let { it.bmp.getPixel(0, it.h - 1) } and 0xFFFFFF
        fun mist(y: Float, strength: Int) {
            for (m in 0 until 6) g.rect(0, (y - m * 4).toInt(), w, (y - m * 4 + 4).toInt(), ((strength * (6 - m) / 6) shl 24) or horizon)
        }
        layer("title/mountains", farTop - 34 + lift(0.18f))
        mist(farTop + 70 + lift(0.18f), 120)
        layer("title/hills_far", farTop + lift(0.32f))
        mist(groundTop - 30 + lift(0.32f), 80)
        val groundFill = lit?.let { mulColor(0xFF265C42.toInt(), landTints[k], landTints[k + 1], f) } ?: 0xFF265C42.toInt()
        g.rect(0, (groundTop - 30 + lift(0.55f)).toInt(), w, h, groundFill)
        layer("title/hills", groundTop - 96 + lift(0.55f))
        layer("title/village", groundTop - 86 + lift(0.62f))
        layer("title/ground", groundTop - 20 + lift(0.85f))
        layer("title/fore", h + 4f + lift(1.4f))
        // A soft vignette.
        for (v in 0 until 6) {
            val va = (0x10 + v * 0x08) shl 24
            val inset = 36 - v * 6
            g.rect(0, h - inset, w, h, va or 0x181425)
            g.rect(0, 0, w, inset / 2, va or 0x181425)
        }
        drawLetters(w, h)
        drawPetals(w, h)
        for (c in chips) c.draw(g)
        if (t > IDLE_AT + 0.2f) {
            val blink = (sin(t * 4f) * 0.5f + 0.5f)
            if (blink > 0.25f) g.text("TAP TO START", w / 2, h - 58, Gfx.GOLD, align = 0)
            g.text("V0.2", w - 6, h - 12, 0xFFC0CBDC.toInt(), align = 1)
        }
    }

    private fun mulColor(base: Int, a: Int, b: Int, f: Float): Int {
        val m = mix(a, b, f)
        fun ch(s: Int) = ((base shr s) and 0xFF) * ((m shr s) and 0xFF) / 255
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun bird(x: Int, y: Int, up: Boolean) {
        val c = INK
        g.rect(x, y, x + 1, y + 1, c)
        if (up) { g.rect(x - 2, y - 1, x, y, c); g.rect(x + 1, y - 1, x + 3, y, c) }
        else { g.rect(x - 2, y + 1, x, y + 2, c); g.rect(x + 1, y + 1, x + 3, y + 2, c) }
    }

    private val white = PorterDuffColorFilter(0xFFFFFFFF.toInt(), PorterDuff.Mode.SRC_IN)
    private val shimmer = LightingColorFilter(0xFFFFFF, 0x505050)

    private fun drawLetters(w: Int, h: Int) {
        val y1 = (h * 0.2f).toInt() + 36
        val rows = listOf("rebuild" to y1, "village" to y1 + 66)
        var idx = 0
        for ((word, by) in rows) {
            val widths = (0 until 7).map { game.assets["title/${word}_${it}_new"].w - 14 }
            var x = (w - widths.sum()) / 2f
            for (i in 0 until 7) {
                val cx = x + widths[i] / 2f + 3f
                x += widths[i]
                val gi = idx++
                val drop = dropAt(gi)
                if (t < drop) continue
                // Falling in as a broken board, with a little bounce.
                val d = ((t - drop) / 0.45f).coerceIn(0f, 1f)
                val fall = (1f - bounce(d)) * -70f
                val wave = if (t > IDLE_AT) sin(t * 2.2f + gi * 0.45f) * 1.2f else 0f
                val y = by + fall + wave
                val restore = restoreAt(gi)
                val alpha = (d * 3f).coerceAtMost(1f)
                when {
                    t < restore -> g.sprite("title/${word}_${i}_old", cx, y, (alpha * 255).toInt())
                    t < restore + 0.07f -> {
                        g.spriteFiltered("title/${word}_${i}_new", cx, y, white)
                        if (chipsFor[gi] != true) { chipsFor[gi] = true; burst(cx, y - 18f) }
                    }
                    else -> {
                        val pop = ((t - restore - 0.07f) / 0.2f).coerceIn(0f, 1f)
                        val k = 1f + 0.32f * (1f - pop) * (1f - pop)
                        g.spriteScaled("title/${word}_${i}_new", cx, y, k, k)
                        // The shimmer: a bright band sweeping left to right.
                        val sx = (t - SHIMMER_AT) / 0.7f * (w + 80f) - 40f
                        if (t in SHIMMER_AT..(SHIMMER_AT + 0.7f) && abs(sx - cx) < 14f) {
                            g.spriteScaled("title/${word}_${i}_new", cx, y, 1f, 1f, alpha = (200 * (1f - abs(sx - cx) / 14f)).toInt(), filter = shimmer)
                        }
                        if (t - restore < 0.5f) g.sprite("fx/sparkle", cx + 10f, y - 34f + (t - restore) * -20f, ((1f - (t - restore) / 0.5f) * 255).toInt())
                    }
                }
            }
        }
        // THE on its ribbon, unfurling between the words.
        if (t >= RIBBON_AT) {
            val u = ((t - RIBBON_AT) / 0.25f).coerceIn(0f, 1f)
            val wave = if (t > IDLE_AT) sin(t * 2.2f + 3f) * 1f else 0f
            g.spriteScaled("title/ribbon", w / 2f, y1 + 16f + wave, u.coerceAtLeast(0.05f), 1f)
        }
    }

    private val chipsFor = HashMap<Int, Boolean>()

    private fun bounce(x: Float): Float {
        val n = 7.5625f; val d = 2.75f
        return when {
            x < 1f / d -> n * x * x
            x < 2f / d -> { val y = x - 1.5f / d; n * y * y + 0.75f }
            x < 2.5f / d -> { val y = x - 2.25f / d; n * y * y + 0.9375f }
            else -> { val y = x - 2.625f / d; n * y * y + 0.984375f }
        }
    }

    private fun drawPetals(w: Int, h: Int) {
        if (t < 3.8f) return
        val a = ((t - 3.8f) / 1f).coerceIn(0f, 1f)
        for (k in 0 until 12) {
            val life = (t * (0.08f + k % 4 * 0.015f) + k * 0.137f) % 1f
            val px = (k * 41 % w) + life * 120f - 60f + sin(t * 1.6f + k) * 10f
            val py = life * h
            val c = if (k % 3 == 0) 0xFFFFFF else 0xF6757A
            g.rect(px.toInt(), py.toInt(), px.toInt() + 2, py.toInt() + 1, (((a * 220).toInt()) shl 24) or c)
        }
    }

    // ------------------------------------------------------------ wood chips

    private class Chip(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val art: String) {
        fun update(dt: Float): Boolean {
            vy += 260f * dt
            x += vx * dt; y += vy * dt
            life -= dt
            return life <= 0f
        }
        fun draw(g: Gfx) = g.sprite(art, x, y, (min(1f, life * 3f) * 255).toInt())
    }

    private val chips = ArrayList<Chip>()

    private fun burst(x: Float, y: Float) {
        for (k in 0 until 7) {
            val a = -Math.PI.toFloat() * (0.1f + 0.8f * k / 6f)
            val sp = 70f + rnd.nextFloat() * 50f
            chips += Chip(x, y, cos(a) * sp, sin(a) * sp, 0.6f + rnd.nextFloat() * 0.3f, if (k % 3 == 0) "fx/dust" else "fx/chip_wood")
        }
    }

    companion object {
        const val LETTERS = 14
        const val LOAD_TIME = 3.2f
        const val RIBBON_AT = 4.55f
        const val SHIMMER_AT = 4.85f
        const val IDLE_AT = 5.4f
        val INK = 0xFF181425.toInt()

        val PUNS = listOf(
            "PLOWING FIELDS...", "COUNTING CHICKENS...", "CONVINCING THE BANK...", "WATERING THE SEEDS...",
            "SHOOING THE CROWS...", "SHARPENING THE SCYTHE...", "TEACHING THE SHEEP TO SIT...", "FILLING OUT FORM 27B...",
            "UNTANGLING THE FENCE...", "WAKING THE ROOSTER...",
        )
        val SAYINGS = listOf(
            "\"GOOD SOIL REMEMBERS WHO LOVED IT.\"", "\"A VILLAGE IS JUST NEIGHBOURS WHO STAYED.\"",
            "\"PLANT TODAY. ARGUE WITH THE BANK TOMORROW.\"", "\"NOTHING GROWS IN A HURRY. NOT EVEN STUBBORNNESS.\"",
            "\"IF THE BRIDGE IS OUT, BUILD A BRIDGE.\"", "\"RAIN IS JUST THE SKY HELPING OUT.\"",
        )
    }
}
