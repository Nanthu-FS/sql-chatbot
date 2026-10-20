package com.ditherfarm.game

import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max

/**
 * Cave critters (round 6, owner picks "tap to fight"): bats, slimes and beetles roam the deep floors
 * ([CRITTER_FLOOR] and below). Once one is near the ground the player has opened it creeps closer
 * and bites now and then, costing energy. A few taps beat it for loot. At zero energy the player
 * is knocked out: carried up to the elevator stop above and half of what this trip found is lost.
 * Critters act only while the mine is on screen ([tickCritters] is driven by the screen, in real
 * seconds), never during offline catch-up.
 */
const val CRITTER_FLOOR = 41
const val FIGHT_ENERGY = 1

enum class CritterKind(val hp: Int, val bite: Int, val biteEvery: Double, val moveEvery: Double, val xp: Int, val wake: Int) {
    BAT(2, 2, 3.5, 0.8, 4, 3), SLIME(3, 3, 5.0, 2.0, 5, 2), BEETLE(5, 4, 6.5, 2.6, 7, 2);
    val id get() = name.lowercase()
}

class Critter(val kind: CritterKind, var cell: Int, var hp: Int = kind.hp) {
    var biteIn = kind.biteEvery
    var moveIn = kind.moveEvery

    fun toJson(): JSONObject = JSONObject().apply { put("k", kind.name); put("c", cell); put("hp", hp) }

    companion object {
        fun fromJson(o: JSONObject) = runCatching { CritterKind.valueOf(o.getString("k")) }.getOrNull()?.let { Critter(it, o.optInt("c"), o.optInt("hp", it.hp)) }
    }
}

private fun cheb(a: Int, b: Int, n: Int) = max(abs(a % n - b % n), abs(a / n - b / n))

/** Puts this floor's critters on empty ground away from the entry (called by floor generation). */
internal fun World.spawnCritters(floor: Int, rng: Rng) {
    val m = state.mine
    m.critters.clear()
    if (floor < CRITTER_FLOOR || floor >= MINE_BOTTOM) return
    val n = m.grid
    val band = data.mine.band(floor).band
    val entry = entryCell(n)
    val free = m.cells.indices.filter { i ->
        m.cells[i].node == null && i % n in 1 until n - 1 && i / n in 1 until n - 1 && cheb(i, entry, n) >= 3
    }.toMutableList()
    val weights = when (band) { 5 -> listOf(60, 40, 0); 6 -> listOf(40, 35, 25); else -> listOf(30, 30, 40) }
    repeat(1 + rng.nextInt(2) + (band - 5)) {
        if (free.isEmpty()) return
        var r = rng.nextInt(weights.sum())
        val kind = CritterKind.entries.first { k -> r -= weights[k.ordinal]; r < 0 }
        m.critters += Critter(kind, free.removeAt(rng.nextInt(free.size)))
    }
}

/** Awake when it is within its [CritterKind.wake] range of ground the player has opened. */
fun World.critterAwake(c: Critter): Boolean {
    val n = state.mine.grid
    return state.mine.opened.any { cheb(it, c.cell, n) <= c.kind.wake }
}

/** Moves and bites for [dt] real seconds; the mine screen calls it each frame. */
fun World.tickCritters(dt: Double) {
    val m = state.mine
    if (m.floor < CRITTER_FLOOR || m.critters.isEmpty() || dt <= 0) return
    val n = m.grid
    val rng = rng()
    for (c in m.critters.toList()) {
        val awake = critterAwake(c)
        c.moveIn -= dt
        if (c.moveIn <= 0) {
            c.moveIn = c.kind.moveEvery * (0.7 + rng.nextDouble() * 0.6)
            val steps = if (c.kind == CritterKind.BAT) listOf(-1, 1, -n, n, -n - 1, -n + 1, n - 1, n + 1) else listOf(-1, 1, -n, n)
            val options = steps.map { c.cell + it }.filter { i ->
                i in m.cells.indices && cheb(i, c.cell, n) == 1 && i % n in 1 until n - 1 && i / n in 1 until n - 1 &&
                    m.cells[i].node == null && m.critters.none { it.cell == i }
            }
            if (options.isNotEmpty()) {
                // Awake critters close in on the player's ground; sleepy ones drift.
                c.cell = if (awake) options.minBy { i -> m.opened.minOf { cheb(it, i, n) } } else options[rng.nextInt(options.size)]
            }
        }
        if (!awake) continue
        c.biteIn -= dt
        if (c.biteIn <= 0 && m.opened.any { cheb(it, c.cell, n) <= 1 }) {
            c.biteIn = c.kind.biteEvery
            state.energy = max(0.0, state.energy - c.kind.bite)
            events += WorldEvent.CritterBit(m.critters.indexOf(c), c.kind, c.kind.bite)
            if (state.energy <= 0.0) { knockOut(); return }
        }
    }
}

fun World.critterAt(cell: Int): Int = state.mine.critters.indexOfFirst { it.cell == cell }

/** One swing at critter [i]: a point of damage (more with gold and crystal picks); it flinches. */
fun World.hitCritter(i: Int): Boolean {
    val m = state.mine
    val c = m.critters.getOrNull(i) ?: return false
    if (state.energy < FIGHT_ENERGY) { events += WorldEvent.Blocked("Too tired"); return false }
    state.energy -= FIGHT_ENERGY
    val tier = pickTier()
    val crit = rng().chance(data.tools.crit[tier])
    c.hp -= (1 + (if (tier >= 3) 1 else 0) + (if (tier >= 4) 1 else 0)) * if (crit) 2 else 1
    c.biteIn = max(c.biteIn, 0.8)
    if (c.hp > 0) { events += WorldEvent.CritterHit(i, c.kind, c.hp, crit); return true }
    m.critters.removeAt(i)
    val rng = rng()
    val loot = LinkedHashMap<String, Int>()
    when (c.kind) {
        CritterKind.BAT -> { loot["coins"] = 10 + rng.nextInt(21); if (rng.chance(0.25)) loot[data.mine.band(m.floor).geode] = 1 }
        CritterKind.SLIME -> loot["crystalite_ore"] = 1 + rng.nextInt(2)
        CritterKind.BEETLE -> if (rng.chance(0.35)) loot[if (rng.chance(0.6)) "emerald" else "ruby"] = 1 else loot["gold_ore"] = 2 + rng.nextInt(2)
    }
    loot.forEach { (k, v) -> if (k == "coins") state.coins += v else add(k, v) }
    state.bump("critters")
    state.bump("critter_${c.kind.id}")
    events += WorldEvent.CritterDefeated(c.cell, c.kind, loot, c.kind.xp)
    gainXp(c.kind.xp)
    return true
}

/**
 * Out of energy on a deep floor: the player wakes at the elevator stop above with a quarter of their
 * energy, and half of everything gained since this trip down began is lost.
 */
fun World.knockOut() {
    val m = state.mine
    val from = m.floor
    val lost = LinkedHashMap<String, Int>()
    for ((k, have) in state.inventory.toList()) {
        val gained = have - (m.tripStart[k] ?: 0)
        if (gained >= 2) { remove(k, gained / 2); lost[k] = gained / 2 }
    }
    state.energy = max(state.energy, maxEnergy * 0.25)
    val stop = (from - 1) / 5 * 5
    state.bump("knockouts")
    events += WorldEvent.KnockedOut(from, stop, lost)
    enterFloor(stop)
    m.tripStart.clear(); m.tripStart.putAll(state.inventory)
}
