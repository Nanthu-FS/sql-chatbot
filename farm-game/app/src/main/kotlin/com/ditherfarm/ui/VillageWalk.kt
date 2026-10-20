package com.ditherfarm.ui

import com.ditherfarm.Game
import com.ditherfarm.engine.Gfx
import com.ditherfarm.engine.Iso
import com.ditherfarm.game.pendingHeartEvent
import com.ditherfarm.game.villageTier
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The villagers out and about (round 6, friendships): each one the village has so far walks
 * between the houses, the shops and the Orders board, always by way of the village road so nobody
 * walks through a wall. They go home at night. A "!" over someone means a heart event is waiting.
 */
class VillageWalk(private val game: Game) {
    private class Walker(val id: String, var c: Float, var r: Float) {
        val path = ArrayDeque<Pair<Float, Float>>()
        var wait = Random.nextFloat() * 4f
        var anim = 0f
        var back = false
        var left = false
        var moving = false
        val x get() = (c - r) * Iso.HW
        val y get() = (c + r) * Iso.HH + Iso.HH
    }

    private val walkers = ArrayList<Walker>()
    private var time = 0f
    private val world get() = game.world

    /** The village road runs down this column (between tiles 11 and 12). */
    private val roadC = 11.5f

    /** Where villagers stop: in front of each standing house and shop, the Orders board, the road. */
    private fun stops(): List<Pair<Float, Float>> {
        val homes = FarmMap.HOUSES.take(FarmMap.houseCount(world.villageTier)) + listOfNotNull(
            FarmMap.SHOP_SEED.takeIf { world.villageTier >= 1 }, FarmMap.SHOP_SMITH.takeIf { world.villageTier >= 1 },
            FarmMap.SHOP_CARPENTER.takeIf { world.villageTier >= 2 }, FarmMap.SHOP_CAFE.takeIf { world.villageTier >= 3 },
        ) + listOf(FarmMap.ORDERS)
        return homes.map { (c, r) -> (if (c >= 12) c - 1.2f else c + 1.2f) to r + 0.4f } + listOf(roadC to 20f, roadC to 30f, roadC to 36f)
    }

    private fun night() = world.time.hour.let { it < 6.5f || it >= 21f }

    fun update(dt: Float) {
        time += dt
        val ids = world.villagersUnlocked().map { it.id }
        walkers.removeAll { it.id !in ids }
        val stops by lazy { stops() }
        for (id in ids) if (walkers.none { it.id == id }) stops.random().let { (c, r) -> walkers += Walker(id, c, r) }
        if (night()) return
        for (w in walkers) {
            w.moving = false
            val target = w.path.firstOrNull()
            if (target == null) {
                w.wait -= dt
                if (w.wait <= 0f) {
                    w.wait = 3f + Random.nextFloat() * 6f
                    val (tc, tr) = stops.random()
                    // Out to the road, along it, then up to the door.
                    w.path += roadC to w.r; w.path += roadC to tr; w.path += tc to tr
                }
                continue
            }
            val dc = target.first - w.c; val dr = target.second - w.r
            val d = hypot(dc, dr)
            if (d < 0.05f) { w.path.removeFirst(); continue }
            val step = min(d, 0.9f * dt)
            w.c += dc / d * step; w.r += dr / d * step
            w.moving = true
            w.anim += dt
            val sx = dc - dr; val sy = dc + dr
            w.back = sy < 0 && abs(sy) >= abs(sx) * 0.5f
            if (abs(sx) > 0.01f) w.left = sx < 0
        }
    }

    fun collect(list: DrawList, s: IsoMapScreen) {
        if (night()) return
        val g = game.gfx
        for (w in walkers) {
            val x = w.x; val y = w.y
            if (!s.inView(x, y)) continue
            val view = if (w.back) "ne" else "se"
            val pose = if (w.moving) "walk${(w.anim * 6).toInt() and 1}" else "idle"
            val art = "people/v_${w.id}_${view}_$pose"
            list.add(y) {
                g.sprite(art, x, y, flipX = w.left)
                if (world.pendingHeartEvent(w.id) != null) g.text("!", x.toInt(), (y - 30 + sin(time * 5f) * 1.5f).toInt(), Gfx.GOLD, align = 0)
            }
        }
    }

    /** The villager under world point (wx, wy), or null. */
    fun at(wx: Float, wy: Float): String? {
        if (night()) return null
        return walkers.filter { abs(wx - it.x) <= 7f && wy in it.y - 24f..it.y + 2f }.maxByOrNull { it.y }?.id
    }
}
