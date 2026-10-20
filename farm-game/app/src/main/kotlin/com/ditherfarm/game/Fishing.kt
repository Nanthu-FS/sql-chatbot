package com.ditherfarm.game

/**
 * Fishing in the farm river (round 6, owner picks): with a rod from the Workbench (level
 * [FISHING_LEVEL]) a tap on the river casts for [CAST_ENERGY] energy. Which fish bites is decided at
 * the cast by season, hour and weather; landing it is a timing bar whose green zone shrinks and
 * marker speeds up with the fish's difficulty (ui/Fishing.kt).
 */
const val FISHING_LEVEL = 6
const val CAST_ENERGY = 3

class FishDef(val id: String, val seasons: Set<Season>?, val weight: Double, val difficulty: Int, val night: Boolean = false) {
    /** Width of the green zone on the bar (0..1). */
    val zone get() = 0.36f - 0.05f * difficulty
    /** Sweeps of the marker across the bar per second. */
    val speed get() = 0.7f + 0.22f * difficulty
    val xp get() = 3 + 3 * difficulty
}

val FISH = listOf(
    FishDef("minnow", null, 40.0, 0),
    FishDef("perch", setOf(Season.SPRING, Season.SUMMER, Season.FALL), 25.0, 1),
    FishDef("trout", setOf(Season.SPRING, Season.FALL), 15.0, 2),
    FishDef("bass", setOf(Season.SUMMER), 14.0, 2),
    // Catfish bite four times as often at night and in the rain.
    FishDef("catfish", null, 4.0, 3, night = true),
    FishDef("salmon", setOf(Season.FALL), 10.0, 3),
    FishDef("pike", setOf(Season.WINTER), 14.0, 3),
    FishDef("golden_koi", null, 1.0, 4),
).associateBy { it.id }

val World.hasRod get() = toolTier("rod") >= 1

fun World.fishBlocker(): String? = when {
    level < FISHING_LEVEL -> "Fishing opens at level $FISHING_LEVEL"
    !hasRod -> "Craft a fishing rod at the Workbench"
    state.energy < CAST_ENERGY -> "Too tired to fish"
    else -> null
}

/** The fish that can bite now, with their weights. */
fun World.fishWeights(): List<Pair<FishDef, Double>> {
    val season = time.season
    val hour = time.hour
    val boost = hour >= 20f || hour < 5f || state.weather.waters
    return FISH.values.filter { it.seasons == null || season in it.seasons }.map { it to it.weight * if (it.night && boost) 4.0 else 1.0 }
}

/** Casts: spends the energy and decides which fish will bite (null if blocked). */
fun World.cast(): FishDef? {
    fishBlocker()?.let { events += WorldEvent.Blocked(it); return null }
    state.energy -= CAST_ENERGY
    state.bump("casts")
    val pool = fishWeights()
    var r = rng().nextDouble() * pool.sumOf { it.second }
    return (pool.firstOrNull { (_, w) -> r -= w; r <= 0 } ?: pool.last()).first
}

/** The catch: the fish goes into storage. */
fun World.landFish(f: FishDef): Boolean {
    if (!hasRoom(f.id)) { events += WorldEvent.Blocked("Barn full"); return false }
    add(f.id, 1)
    state.bump("fish_caught")
    state.bump("fish_${f.id}")
    events += WorldEvent.FishCaught(f.id, f.xp)
    gainXp(f.xp)
    return true
}

/** Fish that can be caught this season (for orders); the koi is too rare to ask for. */
fun World.fishNow(): Set<String> =
    if (!hasRod) emptySet() else FISH.values.filter { it.id != "golden_koi" && (it.seasons == null || time.season in it.seasons) }.map { it.id }.toSet()
