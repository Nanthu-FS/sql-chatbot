package com.ditherfarm.ui

import com.ditherfarm.Game
import com.ditherfarm.engine.Iso
import com.ditherfarm.game.AnimalKind
import com.ditherfarm.game.ranch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The pasture's animals on screen (round 6): each wanders between spots inside the fence, stops to
 * graze now and then, and shows its product (egg, milk, wool) over its back when it is ready.
 * Positions live here only; the animals themselves are [com.ditherfarm.game.RanchState].
 */
class Herd(private val game: Game) {
    private class Walker(var c: Float, var r: Float) {
        var tc = c; var tr = r
        var wait = Random.nextFloat() * 3f
        var eat = 0f
        var anim = 0f
        var left = Random.nextBoolean()
        val x get() = (c - r) * Iso.HW
        val y get() = (c + r) * Iso.HH + Iso.HH
    }

    private val walkers = ArrayList<Walker>()
    private var time = 0f
    private val world get() = game.world

    /** A random spot in the pasture, a little apart from where the others are heading. */
    private fun spot(): Pair<Float, Float> {
        var best = C0 to R0
        var bestGap = -1f
        repeat(6) {
            val c = C0 + Random.nextFloat() * (C1 - C0)
            val r = R0 + Random.nextFloat() * (R1 - R0)
            if (c - r > MAX_U) return@repeat
            val gap = walkers.minOfOrNull { hypot(it.tc - c, it.tr - r) } ?: 9f
            if (gap > bestGap) { best = c to r; bestGap = gap }
        }
        return best
    }

    fun update(dt: Float) {
        time += dt
        val animals = world.ranch.animals
        while (walkers.size < animals.size) spot().let { (c, r) -> walkers += Walker(c, r) }
        while (walkers.size > animals.size) walkers.removeAt(walkers.size - 1)
        walkers.forEachIndexed { i, w ->
            val speed = if (animals[i].kind == AnimalKind.HEN) 0.9f else 0.5f
            val dc = w.tc - w.c; val dr = w.tr - w.r
            val d = hypot(dc, dr)
            if (d > 0.05f) {
                val step = min(d, speed * dt)
                w.c += dc / d * step; w.r += dr / d * step
                w.anim += dt
                val dx = dc - dr
                if (abs(dx) > 0.02f) w.left = dx < 0
            } else if (w.eat > 0f) {
                w.eat -= dt
            } else {
                w.wait -= dt
                if (w.wait <= 0f) {
                    w.wait = 2f + Random.nextFloat() * 5f
                    if (Random.nextFloat() < 0.4f) w.eat = 1.5f + Random.nextFloat() * 2f
                    else spot().let { (c, r) -> w.tc = c; w.tr = r }
                }
            }
        }
    }

    private fun moving(w: Walker) = hypot(w.tc - w.c, w.tr - w.r) > 0.05f

    fun collect(list: DrawList, s: FarmScreen) {
        val animals = world.ranch.animals
        val g = game.gfx
        walkers.forEachIndexed { i, w ->
            val a = animals.getOrNull(i) ?: return@forEachIndexed
            val x = w.x; val y = w.y
            if (!s.inView(x, y)) return@forEachIndexed
            val base = if (a.kind == AnimalKind.SHEEP && !a.product) "sheep_shorn" else a.kind.id
            val pose = when { moving(w) -> ((w.anim * 6).toInt() and 1).toString(); w.eat > 0f -> "eat"; else -> "0" }
            val art = "animals/${base}_$pose"
            list.add(y) {
                g.sprite(art, x, y, flipX = w.left)
                // Something to collect: the sparkle ripe crops show.
                if (a.product && (time * 2 + i * 0.37f) % 3f < 2f) g.sprite("fx/sparkle", x + 3f, y - game.assets[art].ay - 3 + sin(time * 4f + i) * 1.5f)
            }
        }
    }

    /** The animal under world point (wx, wy), or -1. */
    fun at(wx: Float, wy: Float): Int {
        var best = -1
        var bestD = Float.MAX_VALUE
        walkers.forEachIndexed { i, w ->
            val reach = if (world.ranch.animals.getOrNull(i)?.kind == AnimalKind.COW) 12f else 9f
            val d = abs(wx - w.x) + abs(wy - (w.y - 5f))
            if (abs(wx - w.x) <= reach && wy in w.y - 18f..w.y + 3f && d < bestD) { best = i; bestD = d }
        }
        return best
    }

    /** World position over animal [i] (for effects). */
    fun head(i: Int): Pair<Float, Float>? = walkers.getOrNull(i)?.let { it.x to it.y - 14f }

    companion object {
        /** Where the animals roam, in tile units inside the pasture fence (clear of its far corner, column [MAX_U]). */
        const val C0 = 33.1f
        const val C1 = 35.8f
        const val R0 = 13.4f
        const val R1 = 16.5f
        const val MAX_U = 22f
    }
}
