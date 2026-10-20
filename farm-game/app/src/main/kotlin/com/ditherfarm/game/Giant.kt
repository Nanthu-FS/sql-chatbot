package com.ditherfarm.game

import kotlin.math.ceil

/**
 * Giant crops (round 6): at dawn a ripe 3x3 square of one pumpkin, melon or cabbage crop may
 * merge ([GIANT_CHANCE]) into one giant. Harvesting it gives twice what the nine plots would, all
 * gold, and frees the plots. Farmhands leave giants for the player.
 */
val GIANT_CROPS = setOf("pumpkin", "melon", "cabbage")
const val GIANT_CHANCE = 0.15
const val GIANT_SIDE = 3

/** The plots a giant anchored at plot [a] (its top-left in the block) covers. */
fun giantPlots(a: Int): List<Int> {
    val b = FieldLayout.blockOf(a)
    val (c, r) = FieldLayout.cell(a)
    return (0 until GIANT_SIDE).flatMap { dr -> (0 until GIANT_SIDE).map { dc -> FieldLayout.plotAt(b, c + dc, r + dr) } }
}

/** The middle plot of a giant (where it is drawn and tapped). */
fun giantCenter(a: Int): Int = giantPlots(a)[GIANT_SIDE * GIANT_SIDE / 2]

internal fun World.giantDawn(day: Int) {
    val rng = Rng.forDay(state.seed, day, 53)
    for (b in 0 until FieldLayout.BLOCKS) {
        if (!blockOwned(b)) continue
        for (r0 in 0..FieldLayout.SIDE - GIANT_SIDE) for (c0 in 0..FieldLayout.SIDE - GIANT_SIDE) {
            val a = FieldLayout.plotAt(b, c0, r0)
            val ps = giantPlots(a).map { plot(it) }
            val crop = ps[0].crop ?: continue
            if (crop !in GIANT_CROPS || ps.any { it.crop != crop || it.giant >= 0 || it.needsClearing || !isReady(it) }) continue
            if (!rng.chance(GIANT_CHANCE)) continue
            ps.forEach { it.giant = a }
            state.bump("giants_grown")
            events += WorldEvent.GiantGrew(a, crop)
        }
    }
}

/** Harvests the giant on anchor [a]: twice the nine plots' crop, all gold. */
fun World.harvestGiant(a: Int): Boolean {
    val crop = plot(a).crop ?: return false
    if (!hasRoom(crop)) { events += WorldEvent.Blocked("Silo full", giantCenter(a)); return false }
    val def = data.crop(crop)
    val qty = def.yield * GIANT_SIDE * GIANT_SIDE * 2
    add(Quality.GOLD.key(crop), qty)
    for (i in giantPlots(a)) plot(i).clear()
    state.bump("harvested", qty.toLong())
    state.bump("harvest_$crop", qty.toLong())
    state.bump("harvest_gold", qty.toLong())
    state.bump("giants_harvested")
    val xp = ceil(def.xp * Quality.GOLD.mult).toInt() * GIANT_SIDE * GIANT_SIDE
    events += WorldEvent.GiantHarvested(a, crop, qty, xp)
    gainXp(xp)
    return true
}
