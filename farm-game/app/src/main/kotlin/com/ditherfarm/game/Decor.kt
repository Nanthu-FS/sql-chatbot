package com.ditherfarm.game

import org.json.JSONObject
import kotlin.math.min

/**
 * Farm decorations (round 5, owner picks): bought with coins and placed on free grass on the farm,
 * one per tile. Each adds beauty; every [BEAUTY_PER_PERCENT] beauty adds 1% to order coins, up to
 * [BEAUTY_MAX_PERCENT]%. Picking one up returns half its price.
 */
class DecorDef(
    val id: String, val name: String, val price: Int, val beauty: Int, val unlockLevel: Int,
    /** Sold only in this season (seasonal pieces stay once placed). */
    val season: Season? = null,
    /** Lies flat on the ground (paths): drawn under everything standing. */
    val flat: Boolean = false,
)

val DECOR = listOf(
    DecorDef("flowers_red", "Red flower bed", 150, 2, 1),
    DecorDef("flowers_sun", "Sunny flowers", 150, 2, 1),
    DecorDef("bench", "Garden bench", 300, 3, 2),
    DecorDef("gnome", "Garden gnome", 400, 4, 4),
    DecorDef("birdbath", "Birdbath", 600, 5, 5),
    DecorDef("lamp", "Garden lamp", 800, 6, 6),
    DecorDef("topiary", "Round topiary", 900, 7, 8),
    DecorDef("pond", "Lily pond", 1500, 12, 10),
    DecorDef("arch", "Rose arch", 2000, 15, 12),
    DecorDef("sheaf", "Golden sheaf", 4000, 30, 15),
    // Round 6: fences, paths, seasonal pieces and a fountain.
    DecorDef("picket", "Picket fence", 60, 1, 1),
    DecorDef("path_stone", "Stone path", 40, 1, 1, flat = true),
    DecorDef("path_brick", "Brick path", 70, 1, 3, flat = true),
    DecorDef("haystack", "Hay stack", 200, 2, 2),
    DecorDef("sunflowers", "Sunflowers", 250, 3, 3, season = Season.SUMMER),
    DecorDef("blossom", "Blossom tree", 900, 6, 4, season = Season.SPRING),
    DecorDef("pumpkins", "Pumpkin pile", 350, 4, 4, season = Season.FALL),
    DecorDef("snowman", "Snowman", 300, 4, 2, season = Season.WINTER),
    DecorDef("vane", "Weathervane", 700, 5, 6),
    DecorDef("fountain", "Fountain", 3000, 20, 12),
).associateBy { it.id }

const val BEAUTY_PER_PERCENT = 5
const val BEAUTY_MAX_PERCENT = 10
/** "Best Farm in the Valley": at this much beauty, order coins get [BEST_FARM_PERCENT]% more. */
const val BEST_FARM_BEAUTY = 100
const val BEST_FARM_PERCENT = 5
/** One-time coin rewards as beauty grows. */
val BEAUTY_MILESTONES = listOf(25 to 500, 50 to 1500, BEST_FARM_BEAUTY to 5000)

/** Tile key → decoration id. */
fun decorKey(c: Int, r: Int) = c * 1000L + r

val World.beauty get() = state.decor.values.sumOf { DECOR[it]?.beauty ?: 0 }

/** Order coin bonus from beauty, as a fraction (up to 0.10, or 0.15 for the Best Farm). */
fun World.decorBonus() = (min(beauty / BEAUTY_PER_PERCENT, BEAUTY_MAX_PERCENT) + if (beauty >= BEST_FARM_BEAUTY) BEST_FARM_PERCENT else 0) / 100.0

/** The next beauty milestone not yet claimed, if any. */
fun World.nextBeautyMilestone(): Pair<Int, Int>? = BEAUTY_MILESTONES.firstOrNull { it.first !in state.beautyClaimed }

fun World.claimBeautyMilestone(): Boolean {
    val (need, coins) = nextBeautyMilestone() ?: return false
    if (beauty < need) return false
    state.beautyClaimed += need
    state.coins += coins
    events += WorldEvent.BeautyMilestone(need, coins)
    return true
}

fun World.decorBlocker(id: String): String? {
    val d = DECOR[id] ?: return "Unknown decoration"
    return when {
        level < d.unlockLevel -> "Unlocks at level ${d.unlockLevel}"
        d.season != null && time.season != d.season -> "Sold in ${d.season.name.lowercase()}"
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
