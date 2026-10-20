package com.ditherfarm.game

/**
 * Deep-mine treasure (round 6): sealed treasure rooms with a chest, and eight relics to collect.
 * Relic rocks and chests below floor 40 turn up the first seven; the Golem Heart comes only from
 * the Crystal Golem. All eight pay [RELIC_REWARD] once.
 */
val RELICS = linkedMapOf(
    "idol" to "Stone Idol", "fossil" to "Fossil Fish", "sword" to "Ancient Sword", "skull" to "Crystal Skull",
    "scarab" to "Golden Scarab", "tablet" to "Rune Tablet", "crown" to "Miner's Crown", "golem_heart" to "Golem Heart",
)
const val GOLEM_RELIC = "golem_heart"
val RELIC_REWARD = mapOf("coins" to 15_000, "prismatic_shard" to 1)

/** Chance a deep floor has a treasure room, and a relic rock. */
const val TREASURE_ROOM_CHANCE = 0.18
const val RELIC_ROCK_CHANCE = 0.12

val World.relicsFound get() = state.relics.size
val World.relicsComplete get() = RELICS.keys.all { it in state.relics }

/** Gives a relic the player lacks (not the Golem's); false if they have all of those. */
internal fun World.findRelic(rng: Rng): Boolean {
    val missing = RELICS.keys.filter { it != GOLEM_RELIC && it !in state.relics }
    if (missing.isEmpty()) return false
    giveRelic(missing[rng.nextInt(missing.size)])
    return true
}

internal fun World.giveRelic(id: String) {
    if (!state.relics.add(id)) return
    state.bump("relics")
    events += WorldEvent.RelicFound(id, state.relics.size)
}

/** What a broken treasure chest or relic rock gives, on top of its node drops. */
internal fun World.treasureLoot(node: String, depth: Int, rng: Rng, drop: (String, Int) -> Unit) {
    when (node) {
        "relic_rock" -> if (!findRelic(rng)) { drop("old_coin", 2); state.coins += 300 }
        "treasure_chest" -> {
            val band = data.mine.band(depth).band
            val coins = 300 + band * 150 + rng.nextInt(400)
            state.coins += coins
            repeat(2) { drop(listOf("emerald", "emerald", "ruby", "ruby", "diamond")[rng.nextInt(5)], 1) }
            drop("crystal_geode", 1 + rng.nextInt(2))
            if (rng.chance(0.35)) findRelic(rng)
            state.bump("chests")
            events += WorldEvent.TreasureOpened(coins)
        }
    }
}

/**
 * A treasure room: a chest walled in by a 3x3 ring of sealed blocks (they need a gold pick), well
 * away from the entry. Called while a deep floor is generated, before the critters arrive.
 */
internal fun World.placeTreasure(floor: Int, rng: Rng) {
    if (floor < CRITTER_FLOOR || floor >= MINE_BOTTOM) return
    val m = state.mine
    val n = m.grid
    val entry = entryCell(n)
    if (rng.chance(TREASURE_ROOM_CHANCE)) {
        val centres = (0 until n * n).filter { i ->
            i % n in 2 until n - 2 && i / n in 2 until n - 2 && kotlin.math.abs(i % n - entry % n) + kotlin.math.abs(i / n - entry / n) >= 5 && i != m.ladder
        }
        if (centres.isNotEmpty()) {
            val c = centres[rng.nextInt(centres.size)]
            for (dr in -1..1) for (dc in -1..1) {
                val i = c + dr * n + dc
                if (i == m.ladder) m.ladder = -1
                m.cells[i].node = if (i == c) "treasure_chest" else "sealed_wall"; m.cells[i].damage = 0
                m.markers.remove(i)
            }
        }
    }
    if (rng.chance(RELIC_ROCK_CHANCE)) {
        val spots = m.cells.indices.filter { i -> m.cells[i].node == "stone" && i != m.ladder }
        if (spots.isNotEmpty()) m.cells[spots[rng.nextInt(spots.size)]].node = "relic_rock"
    }
}

fun World.relicRewardBlocker(): String? = when {
    state.relicsClaimed -> "Already claimed"
    !relicsComplete -> "Find all ${RELICS.size} relics first"
    else -> null
}

fun World.claimRelicReward(): Boolean {
    relicRewardBlocker()?.let { events += WorldEvent.Blocked(it); return false }
    RELIC_REWARD.forEach { (k, v) -> if (k == "coins") state.coins += v else add(k, v) }
    state.relicsClaimed = true
    events += WorldEvent.RelicsComplete
    gainXp(500)
    return true
}
