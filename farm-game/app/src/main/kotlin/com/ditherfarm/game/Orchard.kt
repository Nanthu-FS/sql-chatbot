package com.ditherfarm.game

import org.json.JSONArray
import org.json.JSONObject

/**
 * The orchard (round 6, owner picks): a fenced plot across the river that the player buys at
 * [ORCHARD_LEVEL]. Each of its [ORCHARD_SPOTS] spots takes a fruit tree bought as a sapling. A tree
 * grows for a day or two, then fruits every two or three days in its seasons and rests in the
 * other one. Fruit waits on the tree until it is picked.
 */
const val ORCHARD_LEVEL = 10
const val ORCHARD_SPOTS = 8
val ORCHARD_COST = mapOf("coins" to 1500, "plank" to 10)

class FruitTreeDef(
    val id: String, val name: String, val fruit: String, val price: Int, val unlockLevel: Int,
    val growDays: Int, val everyDays: Int, val yield: Int, val seasons: Set<Season>,
)

val FRUIT_TREES = listOf(
    FruitTreeDef("cherry", "Cherry tree", "cherry", 300, 10, 1, 2, 6, setOf(Season.SPRING, Season.SUMMER, Season.FALL)),
    FruitTreeDef("apple", "Apple tree", "apple", 400, 12, 1, 2, 8, setOf(Season.SUMMER, Season.FALL, Season.WINTER)),
    FruitTreeDef("peach", "Peach tree", "peach", 600, 14, 2, 3, 5, setOf(Season.SPRING, Season.SUMMER, Season.FALL)),
    FruitTreeDef("orange", "Orange tree", "orange", 800, 16, 2, 3, 5, setOf(Season.FALL, Season.WINTER, Season.SPRING)),
).associateBy { it.id }

/** A planted tree: grown from [plantedDay] + its grow days; its next fruit forms at dawn on [nextDay] or later. */
class OrchardTree(val kind: String, val plantedDay: Int, var nextDay: Int, var fruit: Int = 0) {
    val def get() = FRUIT_TREES.getValue(kind)
    val matureDay get() = plantedDay + def.growDays

    fun toJson(): JSONObject = JSONObject().apply { put("k", kind); put("p", plantedDay); put("n", nextDay); put("f", fruit) }

    companion object {
        fun fromJson(o: JSONObject) = o.optString("k").takeIf { it in FRUIT_TREES }?.let {
            OrchardTree(it, o.optInt("p"), o.optInt("n"), o.optInt("f"))
        }
    }
}

class OrchardState {
    var open = false
    val trees = arrayOfNulls<OrchardTree>(ORCHARD_SPOTS)

    fun toJson(): JSONObject = JSONObject().apply {
        put("open", open)
        put("trees", JSONArray().apply { trees.forEach { put(it?.toJson() ?: JSONObject.NULL) } })
    }

    companion object {
        fun fromJson(o: JSONObject) = OrchardState().apply {
            open = o.optBoolean("open")
            o.optJSONArray("trees")?.let { a -> for (k in 0 until minOf(a.length(), ORCHARD_SPOTS)) trees[k] = a.optJSONObject(k)?.let { OrchardTree.fromJson(it) } }
        }
    }
}

val World.orchard: OrchardState get() = state.orchard
private val World.today get() = time.dayIndex

fun World.orchardBlocker(): String? = when {
    orchard.open -> "Already yours"
    level < ORCHARD_LEVEL -> "Unlocks at level $ORCHARD_LEVEL"
    !canAfford(ORCHARD_COST) -> "Needs " + ORCHARD_COST.entries.joinToString(", ") { (k, v) -> if (k == "coins") "${v}c" else "$v ${data.name(k)}" }
    else -> null
}

fun World.buyOrchard(): Boolean {
    orchardBlocker()?.let { events += WorldEvent.Blocked(it); return false }
    pay(ORCHARD_COST)
    orchard.open = true
    events += WorldEvent.OrchardOpened
    return true
}

fun World.treeBlocker(spot: Int, kind: String): String? {
    val def = FRUIT_TREES[kind] ?: return "Unknown tree"
    return when {
        !orchard.open -> "Buy the orchard first"
        orchard.trees.getOrNull(spot) != null -> "A tree grows here"
        level < def.unlockLevel -> "Unlocks at level ${def.unlockLevel}"
        state.coins < def.price -> "Needs ${def.price}c"
        else -> null
    }
}

fun World.plantOrchardTree(spot: Int, kind: String): Boolean {
    treeBlocker(spot, kind)?.let { events += WorldEvent.Blocked(it); return false }
    val def = FRUIT_TREES.getValue(kind)
    state.coins -= def.price
    orchard.trees[spot] = OrchardTree(kind, today, today + def.growDays)
    state.bump("trees_planted")
    events += WorldEvent.OrchardPlanted(spot, kind)
    return true
}

fun World.digUpTree(spot: Int): Boolean {
    orchard.trees.getOrNull(spot) ?: return false
    orchard.trees[spot] = null
    events += WorldEvent.TreeDugUp(spot)
    return true
}

fun World.treeMature(t: OrchardTree) = today >= t.matureDay

/** At dawn every grown tree in season whose rest is over fruits (fruit does not stack). */
internal fun World.orchardDawn(day: Int) {
    if (!orchard.open) return
    val season = GameTime(day.toDouble() * Time.MINUTES_PER_DAY).season
    var n = 0
    for (t in orchard.trees) {
        if (t == null || t.fruit > 0 || day < t.matureDay || day < t.nextDay || season !in t.def.seasons) continue
        t.fruit = t.def.yield
        n++
    }
    if (n > 0) events += WorldEvent.OrchardFruit(n)
}

fun World.pickFruit(spot: Int): Boolean {
    val t = orchard.trees.getOrNull(spot) ?: return false
    if (t.fruit <= 0) return false
    val item = t.def.fruit
    if (!hasRoom(item)) { events += WorldEvent.Blocked("Barn full"); return false }
    val qty = t.fruit
    add(item, qty)
    t.fruit = 0
    t.nextDay = today + t.def.everyDays
    state.bump("fruit_picked", qty.toLong())
    events += WorldEvent.FruitPicked(spot, item, qty, qty)
    gainXp(qty)
    return true
}

/** What a tree is doing, for its page: "Fruit ready", "Grown in 1 day", "Fruit in 2 days", "Rests until Fall". */
fun World.treeStatus(t: OrchardTree): String {
    if (t.fruit > 0) return "Fruit ready"
    val today = today
    if (today < t.matureDay) return "Grown in " + days(t.matureDay - today)
    // The first day on or after the rest that falls in one of its seasons.
    var d = maxOf(today + 1, t.nextDay)
    repeat(Time.DAYS_PER_YEAR) {
        val s = GameTime(d.toDouble() * Time.MINUTES_PER_DAY).season
        if (s in t.def.seasons) {
            val now = GameTime(today.toDouble() * Time.MINUTES_PER_DAY).season
            return if (s != now && now !in t.def.seasons) "Rests until ${s.label}" else "Fruit in " + days(d - today)
        }
        d++
    }
    return "Resting"
}

private fun days(n: Int) = if (n == 1) "1 day" else "$n days"

/** Fruit the orchard's trees can give this season (for orders). */
fun World.orchardFruitNow(): Set<String> {
    val season = time.season
    return orchard.trees.filterNotNull().filter { season in it.def.seasons }.map { it.def.fruit }.toSet()
}
