package com.ditherfarm.game

import kotlin.math.min

// ------------------------------------------------------------------ the goal chip
/**
 * What the goal chip shows: [text] with [have]/[need] (as a percentage when [percent]), and
 * [ready] when tapping it finishes the goal. [icon] is an item id or a sprite path.
 */
class GoalLine(val icon: String, val text: String, val have: Long, val need: Long, val ready: Boolean, val percent: Boolean = false)

/**
 * Keys: "quest", "village", "bundle:<id>", "ach:<id>:<tier>", "build:<id>", "field:<block>",
 * "chore:<day>:<i>", "contract:<week>". Null once the goal is finished or gone.
 */
fun World.goalLine(key: String): GoalLine? {
    val kind = key.substringBefore(':')
    val rest = key.substringAfter(':', "")
    return when (kind) {
        "quest" -> quest?.let { q -> questProgress().let { (h, n) -> GoalLine("icons/book", q.text, h, n, questDone) } }
        "village" -> villageGoal()
        "bundle" -> bundleGoal(rest)
        "ach" -> achievementGoal(rest.substringBefore(':'), rest.substringAfter(':').toIntOrNull() ?: return null)
        "build" -> buildGoal(rest)
        "field" -> fieldGoal(rest.toIntOrNull() ?: return null)
        "chore" -> {
            if (rest.substringBefore(':').toLongOrNull() != state.choreDay) return null
            val c = state.chores.getOrNull(rest.substringAfter(':').toIntOrNull() ?: return null)?.takeIf { !it.claimed } ?: return null
            GoalLine(c.icon, c.text, choreProgress(c), c.n, choreDone(c))
        }
        "contract" -> {
            val c = state.contract?.takeIf { !it.claimed && it.week == rest.toLongOrNull() } ?: return null
            GoalLine("icons/orders", "Truck contract", c.crops.keys.sumOf { contractShipped(c, it) }, c.crops.values.sum().toLong(), contractDone(c))
        }
        else -> null
    }
}

private fun World.bundleGoal(id: String): GoalLine? {
    val b = nextTier?.bundles?.firstOrNull { it.id == id }?.takeIf { !bundleDone(it) } ?: return null
    var have = 0L
    var need = 0L
    for ((k, n) in b.items) { have += min(donated(b.id, k) + count(k, anyQuality = true), n); need += n.toLong() }
    return GoalLine(b.items.keys.first(), b.name, have, need, have >= need && donateBlocker() == null, percent = true)
}

/** With nothing pinned and the notebook done: the next village bundle, the coins, then the rebuild. */
private fun World.villageGoal(): GoalLine? {
    val t = nextTier ?: return null
    if (state.level < t.level) return GoalLine("icons/level", "${t.name}: level ${t.level}", state.level.toLong(), t.level.toLong(), false)
    t.bundles.firstOrNull { !bundleDone(it) }?.let { return bundleGoal(it.id) }
    if (coinsDonated() < t.coins) return GoalLine("icons/coin", "Coins for the village", min(coinsDonated() + state.coins, t.coins).toLong(), t.coins.toLong(), coinsDonated() + state.coins >= t.coins, percent = true)
    return GoalLine("icons/build", "Rebuild the village", 1, 1, true)
}

private fun World.achievementGoal(id: String, tier: Int): GoalLine? {
    val a = data.goals.achievements.firstOrNull { it.id == id } ?: return null
    if (tier >= a.tiers.size || claimedTiers(id) != tier) return null
    val target = target(a, tier)
    val roman = if (a.tiers.size > 1) " " + listOf("I", "II", "III", "IV", "V").getOrElse(tier) { "${tier + 1}" } else ""
    return GoalLine("icons/trophy", a.name + roman, min(metric(a.metric), target), target, claimable(a))
}

private fun World.buildGoal(id: String): GoalLine? {
    val def = data.buildings[id]?.takeIf { !isBuilt(id) } ?: return null
    if (def.unlockLevel > state.level) return GoalLine("icons/level", "${def.name}: level ${def.unlockLevel}", state.level.toLong(), def.unlockLevel.toLong(), false)
    BLUEPRINTS[id]?.takeIf { !met(it) }?.let { f -> return GoalLine("icons/book", "${def.name}: ${f.text}", min(state.stat(f.stat), f.n), f.n, false) }
    // Coins count one unit per 100, as on the village bar.
    var have = 0L
    var need = 0L
    for ((k, v) in buildCost(id)) {
        val got = if (k == "coins") state.coins else count(k, anyQuality = true)
        if (k == "coins") { have += min(got, v) / 100; need += v / 100 } else { have += min(got, v); need += v.toLong() }
    }
    return GoalLine("icons/build", "Build ${def.name}", have, need, buildBlocker(id) == null, percent = true)
}

private fun World.fieldGoal(block: Int): GoalLine? {
    if (block !in 1 until FieldLayout.BLOCKS || blockOwned(block)) return null
    val name = FieldLayout.name(block)
    val lvl = FieldLayout.UNLOCK[block]
    if (state.level < lvl) return GoalLine("icons/level", "Field $name: level $lvl", state.level.toLong(), lvl.toLong(), false)
    val price = blockPrice(block)
    return GoalLine("icons/coin", "Buy Field $name", min(state.coins, price).toLong(), price.toLong(), state.coins >= price)
}

/** The chip's goal: the pinned one while it lasts, else the notebook quest, else the village. */
fun World.chipGoal(): Pair<String, GoalLine>? {
    if (state.pinned.isNotEmpty()) goalLine(state.pinned)?.let { return state.pinned to it }
    goalLine("quest")?.let { return "quest" to it }
    return goalLine("village")?.let { "village" to it }
}

fun World.togglePin(key: String): Boolean {
    state.pinned = if (state.pinned == key) "" else key
    return state.pinned == key
}
