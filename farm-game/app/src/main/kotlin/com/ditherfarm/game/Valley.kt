package com.ditherfarm.game

import org.json.JSONObject
import kotlin.math.min
import kotlin.math.roundToInt

// ------------------------------------------------------------------ restore the valley
/** One bundle of goods a village tier needs. */
class BundleDef(val id: String, val name: String, val items: Map<String, Int>)

/** A village tier: from [level], paid with [bundles] and [coins]; [rewards] describe what it brings. */
class TierDef(val tier: Int, val name: String, val level: Int, val coins: Int, val bundles: List<BundleDef>, val rewards: List<String>) {
    companion object {
        fun parse(json: String): List<TierDef> = JSONObject(json).getJSONArray("tiers").objects().map { o ->
            TierDef(
                o.getInt("tier"), o.getString("name"), o.getInt("level"), o.getInt("coins"),
                o.getJSONArray("bundles").objects().map { b -> BundleDef(b.getString("id"), b.getString("name"), b.getJSONObject("items").toIntMap()) },
                o.getJSONArray("rewards").strings(),
            )
        }
    }
}

/** Tier names, 0 being the village of wooden huts the game starts with. */
val VILLAGE_TIERS = listOf("Wooden huts", "Stone village", "Brick village", "Concrete town")

val World.villageTier get() = state.villageTier

/** The tier being worked towards, or null once the town is finished. */
val World.nextTier: TierDef? get() = data.valley.firstOrNull { it.tier == state.villageTier + 1 }

fun World.donated(bundle: String, item: String) = state.donated["$bundle:$item"] ?: 0
fun World.coinsDonated() = state.donated["coins"] ?: 0

fun World.bundleDone(b: BundleDef) = b.items.all { (k, n) -> donated(b.id, k) >= n }

/** Units still owed across the next tier (coins count as one unit per 100). */
fun World.tierProgress(): Pair<Int, Int> {
    val t = nextTier ?: return 1 to 1
    var have = min(coinsDonated(), t.coins) / 100
    var need = t.coins / 100
    for (b in t.bundles) for ((k, n) in b.items) { have += min(donated(b.id, k), n); need += n }
    return have to need
}

fun World.donateBlocker(): String? {
    val t = nextTier ?: return "The town is finished"
    return if (state.level < t.level) "The ${t.name.lowercase()} needs level ${t.level}" else null
}

/** Gives as much of a bundle as the Barn and Silo hold (any quality); returns the units given. */
fun World.donate(bundleId: String): Int {
    donateBlocker()?.let { events += WorldEvent.Blocked(it); return 0 }
    val b = nextTier!!.bundles.firstOrNull { it.id == bundleId } ?: return 0
    var given = 0
    for ((k, n) in b.items) {
        val give = min(n - donated(b.id, k), count(k, anyQuality = true))
        if (give <= 0) continue
        removeAny(k, give)
        state.donated["${b.id}:$k"] = donated(b.id, k) + give
        given += give
    }
    if (given == 0) { events += WorldEvent.Blocked("Nothing to give yet"); return 0 }
    state.bump("donated", given.toLong())
    events += WorldEvent.Donated(b.id, given)
    gainXp(maxOf(1, given / 5))
    return given
}

fun World.donateCoins(): Int {
    donateBlocker()?.let { events += WorldEvent.Blocked(it); return 0 }
    val give = min(nextTier!!.coins - coinsDonated(), state.coins)
    if (give <= 0) return 0
    state.coins -= give
    state.donated["coins"] = coinsDonated() + give
    events += WorldEvent.Donated("coins", give)
    return give
}

fun World.rebuildBlocker(): String? {
    val t = nextTier ?: return "The town is finished"
    return when {
        state.level < t.level -> "Needs level ${t.level}"
        t.bundles.any { !bundleDone(it) } -> "Finish every bundle"
        coinsDonated() < t.coins -> "Needs ${t.coins - coinsDonated()} more coins"
        else -> null
    }
}

/** Every house and road is rebuilt in the next material; the tier's rewards apply from now on. */
fun World.rebuild(): Boolean {
    rebuildBlocker()?.let { events += WorldEvent.Blocked(it); return false }
    val t = nextTier!!
    state.villageTier = t.tier
    state.donated.clear()
    if (t.tier >= 1) building("blacksmith").built = true
    ensureOrderSlots()
    events += WorldEvent.VillageRebuilt(t.tier)
    gainXp(400 * t.tier)
    return true
}

// ------------------------------------------------------------------ what the tiers bring
/** One more order slot from the stone village on. */
val World.villageOrderSlots get() = if (state.villageTier >= 1) 1 else 0

/** Orders pay 10% more from the brick village on. */
fun World.orderPayMult() = if (state.villageTier >= 2) 1.1 else 1.0

/** The Carpenter (brick village) takes 15% off building and field coins. */
fun World.carpenterMult() = if (state.villageTier >= 2) 0.85 else 1.0

fun World.buildCost(id: String): Map<String, Int> =
    data.buildings.getValue(id).cost.mapValues { (k, v) -> if (k == "coins") (v * carpenterMult()).roundToInt() else v }

fun World.blockPrice(block: Int) = (FieldLayout.PRICE[block] * carpenterMult()).roundToInt()

/** The Cafe (concrete town) buys dishes for 25% more. */
fun World.cafeMult(id: String) = if (state.villageTier >= 3 && data.item(id).category == "dish") 1.25 else 1.0

/** The truck carries 50% more in the concrete town. */
val World.truckCap get() = if (state.villageTier >= 3) TRUCK_CAP * 3 / 2 else TRUCK_CAP

/** XP bonus in the concrete town. */
fun World.xpMult() = if (state.villageTier >= 3) 1.1 else 1.0

// ------------------------------------------------------------------ the Seed shop
/** Seeds come in packs of ten, 20% under the planting price, once the Seed shop is open. */
const val SEED_PACK = 10

fun World.seedPackPrice(crop: String) = maxOf(1, (seedPrice(crop) * SEED_PACK * 0.8).roundToInt())

fun World.buySeedPack(crop: String): Boolean {
    val c = data.crops[crop] ?: return false
    when {
        state.villageTier < 1 -> events += WorldEvent.Blocked("The Seed shop opens with the stone village")
        !cropUnlocked(crop) -> events += WorldEvent.Blocked("Unlocks at level ${c.unlockLevel}")
        state.coins < seedPackPrice(crop) -> events += WorldEvent.Blocked("Not enough coins")
        else -> {
            state.coins -= seedPackPrice(crop)
            add(c.seedItem, SEED_PACK)
            events += WorldEvent.Bought(c.seedItem, SEED_PACK, seedPackPrice(crop))
            return true
        }
    }
    return false
}
