package com.ditherfarm.ui

import com.ditherfarm.Game
import com.ditherfarm.engine.Gfx
import com.ditherfarm.engine.Iso
import com.ditherfarm.game.FieldLayout
import com.ditherfarm.game.TRUCK_CAP
import com.ditherfarm.game.TRUCK_TRIP_SECONDS
import com.ditherfarm.game.WorkKind
import com.ditherfarm.game.blockOwned
import com.ditherfarm.game.truckLoad
import com.ditherfarm.game.truckSecondsLeft
import com.ditherfarm.game.workerOn
import com.ditherfarm.game.workerSeconds
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The farm's moving crew, drawn on top of the simulation in Workers.kt (which has already
 * done the work): farmhands walking to the plots they tend and carrying crops to their
 * field's crate, the crates, the harvester's runs and the truck's trips to town.
 */
internal class FarmCrew(private val game: Game, private val screen: FarmScreen) {
    private val world get() = game.world
    private val g get() = game.gfx

    private enum class Pose { IDLE, WALK, WORK, CARRY }

    /** A farmhand on screen, in fractional tile coordinates. */
    private class Walker(val id: String) {
        var c = 0f; var r = 0f
        var tc = 0f; var tr = 0f
        var pose = Pose.IDLE
        var poseTime = 0f
        var dc = 1f; var dr = 0f
        var job: WorkKind? = null
        var jobPlot = -1
        var carry = false
        var hurry = 1f
    }
    private val walkers = LinkedHashMap<String, Walker>()
    private var time = 0f

    /** Harvester position along the main road (tile k = (k, k)) and its trip end; < 0 when parked. */
    private var harvesterK = -1f
    private var harvesterTurn = 0f
    private var harvesterBack = false

    // ------------------------------------------------------------------ simulation hooks
    /** A farmhand started a job: walk to the plot (the screen already shows the result). */
    fun onActed(id: String, plot: Int, kind: WorkKind) {
        val w = walkers.getOrPut(id) { spawn(id) }
        val (c, r) = screen.plotTile(plot)
        w.hurry = if (w.job != null || w.carry) 2f else 1f
        w.job = kind; w.jobPlot = plot; w.carry = false
        // Stand on the plot's front half so the crop is behind the farmhand.
        w.tc = c + 0.55f; w.tr = r + 0.55f
    }

    /** The harvester drove the crates to the truck or Silo: send it down the road and back. */
    fun onHarvesterRan() {
        val furthest = (0 until FieldLayout.BLOCKS).filter { world.blockOwned(it) }.maxOfOrNull { crateTile(it).let { (c, r) -> (c + r) / 2f } } ?: return
        if (harvesterK >= 0) return
        harvesterK = HARVESTER_HOME_K
        harvesterTurn = max(furthest, HARVESTER_HOME_K + 2)
        harvesterBack = false
    }

    // ------------------------------------------------------------------ update
    fun update(dt: Float) {
        time += dt
        val state = world.state
        walkers.keys.retainAll(state.workers.keys)
        for ((id, ws) in state.workers) {
            val w = walkers.getOrPut(id) { spawn(id) }
            if (ws.field < 0 || ws.hungry || !world.blockOwned(maxOf(0, ws.field))) {
                if (ws.field < 0) { val (c, r) = idleSpot(id); w.tc = c; w.tr = r }
                w.job = null; w.carry = false
            }
            step(w, workerSeconds(ws.level).toFloat(), dt)
        }
        if (harvesterK >= 0) {
            val v = HARVESTER_SPEED * dt
            if (!harvesterBack) { harvesterK += v; if (harvesterK >= harvesterTurn) harvesterBack = true }
            else { harvesterK -= v; if (harvesterK <= HARVESTER_HOME_K) harvesterK = -1f }
        }
    }

    private fun step(w: Walker, interval: Float, dt: Float) {
        if (w.pose == Pose.WORK) {
            w.poseTime -= dt
            if (w.poseTime > 0) return
            val done = w.job
            w.job = null
            if (done == WorkKind.HARVEST || done == WorkKind.CLEAR) {
                // Carry the crop (or the dead plant) to the field's crate.
                val f = world.state.workers[w.id]?.field ?: -1
                if (f >= 0) { val (c, r) = crateTile(f); w.tc = c + 0.5f; w.tr = r + 0.2f; w.carry = true }
            }
            w.pose = Pose.IDLE
        }
        val ec = w.tc - w.c; val er = w.tr - w.r
        if (abs(ec) < 0.02f && abs(er) < 0.02f) {
            w.c = w.tc; w.r = w.tr
            when {
                w.job != null -> { w.hurry = 1f; w.pose = Pose.WORK; w.poseTime = min(0.7f, interval * 0.4f); w.dc = 1f; w.dr = 0f; jobEffects(w) }
                w.carry -> { w.carry = false; w.hurry = 1f; w.pose = Pose.IDLE; screen.fx.burst("fx/dust", wx(w), wy(w), 3, speed = 20f, gravity = 30f, life = 0.3f) }
                else -> w.pose = Pose.IDLE
            }
            return
        }
        // Walk along the tile axes (columns first) so moves read as isometric.
        val dist = abs(ec) + abs(er)
        val speed = max(WALK_SPEED, dist / max(0.3f, interval * 0.5f)) * w.hurry
        var move = speed * dt
        if (abs(ec) > 0.01f) {
            val m = min(move, abs(ec)); w.c += m * sign(ec); move -= m; w.dc = sign(ec); w.dr = 0f
        }
        if (move > 0 && abs(er) > 0.01f) {
            val m = min(move, abs(er)); w.r += m * sign(er); w.dc = 0f; w.dr = sign(er)
        }
        w.pose = if (w.carry) Pose.CARRY else Pose.WALK
    }

    private fun sign(f: Float) = if (f < 0) -1f else 1f

    /** Sounds and particles when a farmhand reaches a plot, only if it is on screen. */
    private fun jobEffects(w: Walker) {
        val x = wx(w); val y = wy(w)
        if (!screen.inView(x, y, 8f)) return
        val fx = screen.fx
        when (w.job) {
            WorkKind.HARVEST -> { game.sfx.play("harvest", 0.35f); fx.burst("fx/leaf", x, y - 10, 3, speed = 35f) }
            WorkKind.PLANT -> { game.sfx.play("plant", 0.35f); fx.burst("fx/dust", x, y - 2, 3, speed = 20f, gravity = 30f, life = 0.4f) }
            WorkKind.WATER -> { game.sfx.play("water", 0.3f); fx.burst("fx/drop", x + 6, y - 8, 4, speed = 30f, gravity = 120f, life = 0.5f) }
            WorkKind.CLEAR -> { game.sfx.play("pickup", 0.35f); fx.burst("fx/leaf", x, y - 6, 3, speed = 30f) }
            null -> {}
        }
    }

    private fun spawn(id: String): Walker {
        val w = Walker(id)
        val f = world.state.workers[id]?.field ?: -1
        val (c, r) = if (f >= 0) crateTile(f).let { (c, r) -> c + 0.5f to r + 0.2f } else idleSpot(id)
        w.c = c; w.r = r; w.tc = c; w.tr = r
        return w
    }

    private fun idleSpot(id: String): Pair<Float, Float> {
        val i = world.state.workers.keys.indexOf(id).coerceAtLeast(0)
        val (c, r) = IDLE_SPOTS[i % IDLE_SPOTS.size]
        return c + 0.5f to r + 0.5f
    }

    private fun wx(w: Walker) = (w.c - w.r) * Iso.HW
    private fun wy(w: Walker) = (w.c + w.r) * Iso.HH

    // ------------------------------------------------------------------ drawing
    fun collect(list: DrawList) {
        for (b in 0 until FieldLayout.BLOCKS) {
            if (!world.blockOwned(b)) continue
            val crate = world.state.fields[b].crate.values.sum()
            if (crate == 0 && world.workerOn(b) == null) continue
            val (c, r) = crateTile(b)
            val x = Iso.x(c, r).toFloat(); val y = Iso.centerY(c, r).toFloat()
            if (!screen.inView(x, y)) continue
            list.add(y) {
                val full = crate > 0
                g.sprite(if (full) "deco/crate_full" else "deco/crate_empty", x, y)
                if (full) g.text(if (crate >= com.ditherfarm.game.CRATE_CAP) "FULL" else "$crate", x.toInt(), (y - screen.spriteTop("deco/crate_full") - 9).toInt(), if (crate >= com.ditherfarm.game.CRATE_CAP) Gfx.RED else Gfx.WHITE, align = 0)
            }
        }
        for ((id, w) in walkers) {
            val ws = world.state.workers[id] ?: continue
            val x = wx(w); val y = wy(w)
            if (!screen.inView(x, y, 32f)) continue
            list.add(y + 0.2f) {
                g.sprite(frame(id, w), x, y, flipX = flipped(w))
                if (ws.hungry) g.sprite("icons/energy_food", x, y - screen.spriteTop("people/${id}_se_idle") - 6 + sin(time * 4f) * 1.5f)
            }
        }
        drawTruck(list)
        if (harvesterK < 0 && world.state.workers.isNotEmpty()) {
            val (hc, hr) = HARVESTER_PARK
            val x = Iso.x(hc, hr).toFloat(); val y = Iso.centerY(hc, hr).toFloat()
            if (screen.inView(x, y, 40f)) list.add(y) { g.sprite("vehicles/harvester_se_0", x, y) }
        }
        if (harvesterK >= 0) {
            val k = harvesterK
            val lane = if (harvesterBack) 0.25f else 0.75f
            var x = (lane) * Iso.TW / 2f
            var y = (2 * k + lane) * Iso.HH + Iso.HH
            // Pull out of (and back into) the parking spot over the first road tile.
            val t = k - HARVESTER_HOME_K
            if (t < 1f) {
                val (hc, hr) = HARVESTER_PARK
                x = Iso.x(hc, hr) + (x - Iso.x(hc, hr)) * t
                y = Iso.centerY(hc, hr) + (y - Iso.centerY(hc, hr)) * t
            }
            if (screen.inView(x, y, 40f)) list.add(y) {
                val f = ((time * 6).toInt() and 1)
                g.sprite(if (harvesterBack) "vehicles/harvester_ne_$f" else "vehicles/harvester_se_$f", x, y, flipX = harvesterBack)
            }
        }
    }

    private fun drawTruck(list: DrawList) {
        val t = world.state.truck
        val home = TRUCK_HOME_K
        val left = world.truckSecondsLeft().toFloat()
        val elapsed = TRUCK_TRIP_SECONDS.toFloat() - left
        val (k, back) = when {
            !t.away -> home to false
            elapsed < TRUCK_DRIVE -> home + (TRUCK_END_K - home) * (elapsed / TRUCK_DRIVE) to false
            left < TRUCK_DRIVE -> home + (TRUCK_END_K - home) * (left / TRUCK_DRIVE) to true
            else -> return
        }
        val lane = if (back) 0.25f else 0.75f
        val x = lane * Iso.TW / 2f
        val y = (2 * k + lane) * Iso.HH + Iso.HH
        if (!screen.inView(x, y, 48f)) return
        val moving = t.away
        val load = world.truckLoad()
        list.add(y) {
            val f = if (moving) ((time * 8).toInt() and 1) else 0
            g.sprite(if (back) "vehicles/truck_ne_$f" else "vehicles/truck_se_$f", x, y, flipX = back)
            if (!t.away && load > 0) {
                g.sprite("vehicles/truck_load", x, y)
                val full = load >= TRUCK_CAP
                val bob = sin(time * 4f) * 1.5f
                val top = screen.spriteTop("vehicles/truck_se_0")
                g.sprite("icons/sell", x + 2, y - top - 4 + bob)
                g.text(if (full) "FULL" else "$load", x.toInt() + 2, (y - top - 20 + bob).toInt(), if (full) Gfx.RED else Gfx.GOLD, align = 0)
            }
        }
    }

    private fun frame(id: String, w: Walker): String {
        val dir = if (w.dr < 0 || (w.dc < 0 && w.dr == 0f)) "ne" else "se"
        val alt = ((time * 6).toInt() and 1)
        val name = when (w.pose) {
            Pose.IDLE -> "${dir}_idle"
            Pose.WALK -> "${dir}_walk$alt"
            Pose.CARRY -> "${dir}_carry$alt"
            Pose.WORK -> "se_work${(time * 4).toInt() and 1}"
        }
        return "people/${id}_$name".takeIf { game.assets.has(it) } ?: "people/${id}_se_idle"
    }

    /** se art faces +c; +r is its mirror. ne art faces -r; -c is its mirror. */
    private fun flipped(w: Walker) = when {
        w.pose == Pose.WORK -> false
        w.dr > 0 -> true
        w.dc < 0 -> true
        else -> false
    }

    companion object {
        const val WALK_SPEED = 2.2f
        const val HARVESTER_SPEED = 3.5f
        const val HARVESTER_HOME_K = 16f
        /** Where the harvester waits between runs, beside the truck. */
        val HARVESTER_PARK = 18 to 15
        val TRUCK_HOME_K = FarmMap.TRUCK.first.toFloat()
        const val TRUCK_END_K = 41f
        /** Seconds the truck takes to drive off the map, and to drive back. */
        const val TRUCK_DRIVE = 10f
        private val IDLE_SPOTS = listOf(11 to 15, 11 to 16, 12 to 16, 9 to 16, 10 to 17)

        /** Tile of block [b]'s crate: on the side facing the main road. */
        fun crateTile(b: Int): Pair<Int, Int> {
            val (bc, br) = FarmMap.BLOCKS[b]
            return if (bc - br < 0) bc + FieldLayout.SIDE to br else bc to br + FieldLayout.SIDE
        }

        /** True if (c, r) is the parked truck (it covers its tile and the next one down the road). */
        fun isTruck(c: Int, r: Int): Boolean {
            val (tc, tr) = FarmMap.TRUCK
            return (c == tc || c == tc + 1) && (r == tr || r == tr + 1)
        }
    }
}
