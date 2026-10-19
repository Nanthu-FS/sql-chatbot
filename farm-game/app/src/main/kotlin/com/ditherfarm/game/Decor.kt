package com.ditherfarm.game

import org.json.JSONObject
import kotlin.math.min

/**
 * Farm decorations (round 5, owner picks): bought with coins and placed on free grass on the farm,
 * one per tile. Each adds beauty; every [BEAUTY_PER_PERCENT] beauty adds 1% to order coins, up to
 * [BEAUTY_MAX_PERCENT]%. Picking one up returns half its price.
 */
class DecorDef(val id: String, val name: String, val price: Int, val beauty: Int, val unlockLevel: Int)

val DECOR = listOf(
    DecorDef("flowers_red", "Red flower bed", 150, 2, 1),
    DecorDef("flowers_sun", "Sunny flower bed", 150, 2, 1),
    DecorDef("bench", "Garden bench", 300, 3, 2),
    DecorDef("gnome", "Garden gnome", 400, 4, 4),
    DecorDef("birdbath", "Birdbath", 600, 5, 5),
    DecorDef("lamp", "Garden lamp", 800, 6, 6),
    DecorDef("topiary", "Round topiary", 900, 7, 8),
    DecorDef("pond", "Lily pond", 1500, 12, 10),
    DecorDef("arch", "Rose arch", 2000, 15, 12),
    DecorDef("sheaf", "Golden Sheaf statue", 4000, 30, 15),
).associateBy { it.id }

const val BEAUTY_PER_PERCENT = 5
const val BEAUTY_MAX_PERCENT = 10

/** Tile key → decoration id. */
fun decorKey(c: Int, r: Int) = c * 1000L + r

val World.beauty get() = state.decor.values.sumOf { DECOR[it]?.beauty ?: 0 }

/** Order coin bonus from beauty, as a fraction (0.0 to 0.10). */
fun World.decorBonus() = min(beauty / BEAUTY_PER_PERCENT, BEAUTY_MAX_PERCENT) / 100.0

fun World.decorBlocker(id: String): String? {
    val d = DECOR[id] ?: return "Unknown decoration"
    return when {
        level < d.unlockLevel -> "Unlocks at level ${d.unlockLevel}"
        state.coins < d.price -> "Needs ${d.price} coins"
        else -> null
    }
}

/** Places [id] on tile (c, r); the screen has already checked the tile is free grass. */
fun World.placeDecor(c: Int, r: Int, id: String): Boolean {
    decorBlocker(id)?.let { events += WorldEvent.Blocked(it); return false }
    val k = decorKey(c, r)
    if (state.decor.containsKey(k)) { events += WorldEvent.Blocked("Something is already there"); return false }
    state.coins -= DECOR.getValue(id).price
    state.decor[k] = id
    state.bump("decor_placed")
    events += WorldEvent.DecorPlaced(c, r, id)
    return true
}

/** Picks up the decoration on (c, r) for half its price back. */
fun World.pickUpDecor(c: Int, r: Int): Boolean {
    val id = state.decor.remove(decorKey(c, r)) ?: return false
    state.coins += (DECOR[id]?.price ?: 0) / 2
    events += WorldEvent.DecorRemoved(c, r, id)
    return true
}

internal fun decorToJson(m: Map<Long, String>) = JSONObject().apply { m.forEach { (k, v) -> put(k.toString(), v) } }
internal fun decorFromJson(o: JSONObject, into: MutableMap<Long, String>) {
    o.keys().forEach { k -> o.getString(k).takeIf { DECOR.containsKey(it) }?.let { into[k.toLong()] = it } }
}
