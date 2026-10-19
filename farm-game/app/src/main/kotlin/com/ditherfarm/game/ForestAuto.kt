package com.ditherfarm.game

import kotlin.math.roundToInt

// ------------------------------------------------------------------ Logging Hut and Tree Nursery
/** Hut tier by player level: 1 (Pine, Birch, 20 min), 2 at L15 (+Oak, 15 min, clears stumps), 3 at L22 (+Maple, 10 min). */
fun World.hutTier() = when { state.level >= 22 -> 3; state.level >= 15 -> 2; else -> 1 }

fun World.hutCap() = intArrayOf(20, 35, 50)[hutTier() - 1]

private fun World.hutTrees() = when (hutTier()) { 1 -> setOf("pine", "birch"); 2 -> setOf("pine", "birch", "oak"); else -> setOf("pine", "birch", "oak", "maple") }

private fun World.autoPeriod(id: String): Double = Time.realSecondsToGameMinutes(60.0 * when (id) {
    "logging_hut" -> intArrayOf(20, 15, 10)[hutTier() - 1]
    else -> if (state.level >= 18) 5 else 10
})

/** Runs the Logging Hut and Tree Nursery on their timers (offline too). */
internal fun World.tickForestAuto() {
    for (id in listOf("logging_hut", "tree_nursery")) {
        if (!isBuilt(id)) continue
        var at = state.shedNext[id] ?: state.minutes
        var runs = 0
        while (state.minutes >= at && runs < 300) {
            if (id == "logging_hut") runHut() else runNursery()
            at += autoPeriod(id)
            runs++
        }
        state.shedNext[id] = at
    }
}

private fun BuildingState.put(item: String, n: Int) { if (n > 0) tray[item] = (tray[item] ?: 0) + n }

/** Fells one eligible mature tree into the hut's tray at 80% yield (never Ancient Oaks or hive trees). */
internal fun World.runHut() {
    val b = building("logging_hut")
    if (b.tray.values.sum() >= hutCap()) { state.shedBlocked["logging_hut"] = true; return }
    state.shedBlocked["logging_hut"] = false
    val kinds = hutTrees()
    val rng = rng()
    val tree = forestSpots.firstOrNull { sp ->
        sp.kind == SpotKind.TREE && zoneOpen(sp.zone) && sp.index != state.forest.hive &&
            treeStage(sp.index) == TreeStage.MATURE && spot(sp.index).tree in kinds
    }
    if (tree != null) {
        val s = spot(tree.index)
        val def = data.trees[s.tree] ?: return
        fun roll(r: IntRange) = ((r.first + rng.nextInt(r.last - r.first + 1)) * 0.8).roundToInt()
        b.put("wood", roll(def.wood))
        if (def.hardwood.last > 0) b.put("hardwood", roll(def.hardwood))
        if (rng.chance(def.seedChance)) b.put(def.seed, 1)
        s.stage = TreeStage.STUMP; s.damage = 0; s.since = state.minutes
        state.bump("fells"); state.bump("fell_${def.id}")
        return
    }
    if (hutTier() >= 2) {
        val stump = forestSpots.firstOrNull { sp -> sp.kind == SpotKind.TREE && zoneOpen(sp.zone) && treeStage(sp.index) == TreeStage.STUMP && spot(sp.index).tree != "ancient_oak" }
        if (stump != null) {
            val s = spot(stump.index)
            b.put("wood", 2)
            s.tree = null; s.stage = TreeStage.EMPTY; s.damage = 0; s.since = state.minutes
        }
    }
}

/** Clears one stump, or plants one seed from the Barn on an empty spot that suits it. */
internal fun World.runNursery() {
    val b = building("tree_nursery")
    val stump = forestSpots.firstOrNull { sp -> sp.kind == SpotKind.TREE && zoneOpen(sp.zone) && treeStage(sp.index) == TreeStage.STUMP }
    if (stump != null) {
        val s = spot(stump.index)
        b.put("wood", 1)
        s.tree = null; s.stage = TreeStage.EMPTY; s.damage = 0; s.since = state.minutes
        return
    }
    for (sp in forestSpots) {
        if (sp.kind != SpotKind.TREE || !zoneOpen(sp.zone) || treeStage(sp.index) != TreeStage.EMPTY) continue
        val native = sp.native ?: continue
        if (native == "ancient_oak") continue
        val seed = data.trees[native]?.seed ?: continue
        if (count(seed) > 0 && plantTree(sp.index, seed)) return
    }
}

// ------------------------------------------------------------------ wild beehive
const val HIVE_DAYS = 6

/** At dawn from level 5, a wild beehive may settle on a mature Birch, Oak or Maple for 6 days. */
internal fun World.hiveDawn(day: Int, rng: Rng) {
    val f = state.forest
    if (f.hive >= 0 && (day >= f.hiveUntil || treeStage(f.hive) != TreeStage.MATURE)) f.hive = -1
    if (f.hive >= 0 || state.level < 5 || !rng.chance(0.15)) return
    val hosts = forestSpots.filter { sp ->
        sp.kind == SpotKind.TREE && zoneOpen(sp.zone) && treeStage(sp.index) == TreeStage.MATURE && spot(sp.index).tree in setOf("birch", "oak", "maple")
    }
    if (hosts.isEmpty()) return
    f.hive = hosts[rng.nextInt(hosts.size)].index
    f.hiveUntil = day + HIVE_DAYS
}

/** Smokes the hive with a torch (or 4 energy): 3 Honey, 10 XP and maybe a Wildflower Seed. */
fun World.smokeHive(): Boolean {
    val f = state.forest
    if (f.hive < 0) return false
    if (!removeAny("torch", 1)) {
        if (state.energy < 4) { events += WorldEvent.Blocked("Too tired"); return false }
        state.energy -= 4
    }
    val spot = f.hive
    f.hive = -1
    add("honey", 3)
    val seed = rng().chance(0.25)
    if (seed) add("wildflower_seed", 1)
    state.bump("hives_smoked")
    events += WorldEvent.HiveSmoked(spot, seed)
    gainXp(10)
    return true
}

/** Chopping a hive tree without smoking it: the bees sting (-10 energy) and drop 1 Honey. */
internal fun World.stingIfHive(i: Int) {
    val f = state.forest
    if (f.hive != i) return
    f.hive = -1
    state.energy = maxOf(0.0, state.energy - 10)
    add("honey", 1)
    events += WorldEvent.Stung(i)
}
