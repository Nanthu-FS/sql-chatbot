package com.ditherfarm.game

import kotlin.math.max
import kotlin.math.roundToInt

// ------------------------------------------------------------------ daily gift
/** True if today's login gift (by the device's calendar day) has not been claimed. */
fun World.giftReady(today: Long) = today > state.giftDay

/** Day in the 7-day streak the next gift would be (1..7). */
fun World.giftStreakDay(today: Long): Int {
    val streak = if (today == state.giftDay + 1) state.giftStreak + 1 else 1
    return (streak - 1) % 7 + 1
}

/** Coins, XP and items of a gift on streak day [day] (GAME_DESIGN §15.3). */
fun World.giftFor(day: Int): Triple<Int, Int, Map<String, Int>> =
    Triple(30 + 20 * state.level + 10 * day, 5 * day, if (day == 7) mapOf("plank" to 5, "coal" to 5) else emptyMap())

fun World.claimGift(today: Long): Boolean {
    if (!giftReady(today)) return false
    val streak = if (today == state.giftDay + 1) state.giftStreak + 1 else 1
    val day = (streak - 1) % 7 + 1
    val (coins, xp, items) = giftFor(day)
    state.giftDay = today
    state.giftStreak = streak
    state.coins += coins
    items.forEach { (k, v) -> add(k, v) }
    events += WorldEvent.GiftClaimed(day, coins)
    gainXp(xp)
    return true
}

// ------------------------------------------------------------------ traveling merchant
class MerchantOffer(val item: String, val qty: Int, val price: Int)

const val MERCHANT_LEVEL = 10

/** The merchant visits from level 10 on every 7th game day. */
fun World.merchantHere() = state.level >= MERCHANT_LEVEL && time.dayIndex % 7 == 6

/** Today's six offers, seeded by the day so they never re-roll. */
fun World.merchantStock(): List<MerchantOffer> {
    if (!merchantHere()) return emptyList()
    val pool = ArrayList<MerchantOffer>()
    for (c in data.crops.values) if (c.unlockLevel <= state.level + 3) pool += MerchantOffer(c.seedItem, 5, c.seedPrice * 5 * 3)
    fun item(id: String, qty: Int, mult: Double, minLevel: Int = 0) {
        val base = data.items[id]?.price ?: return
        if (state.level >= minLevel) pool += MerchantOffer(id, qty, max(10, (base * qty * mult).roundToInt()))
    }
    item("rope_ladder", 1, 5.0)
    item("torch", 10, 3.0)
    item("quality_fertilizer", 3, 4.0)
    item("gold_ore", 5, 3.0, 14)
    item("hardwood", 10, 3.0)
    item("geode", 3, 4.0)
    if (state.level >= 20) pool += MerchantOffer("golden_acorn", 1, 600)
    pool += MerchantOffer("apple_sapling", 1, 250)
    val rng = Rng.forDay(state.seed, time.dayIndex, 77)
    val out = ArrayList<MerchantOffer>()
    while (out.size < 6 && pool.isNotEmpty()) out += pool.removeAt(rng.nextInt(pool.size))
    return out
}

fun World.merchantBought(slot: Int) = state.merchantDay == time.dayIndex && slot in state.merchantBought

fun World.buyFromMerchant(slot: Int): Boolean {
    val offer = merchantStock().getOrNull(slot) ?: return false
    if (merchantBought(slot)) return false
    val price = merchantPrice(offer)
    if (state.coins < price) { events += WorldEvent.Blocked("Not enough coins"); return false }
    if (!hasRoom(offer.item)) { events += WorldEvent.Blocked("No room"); return false }
    if (state.merchantDay != time.dayIndex) { state.merchantDay = time.dayIndex; state.merchantBought.clear() }
    state.coins -= price
    state.merchantBought += slot
    add(offer.item, offer.qty)
    events += WorldEvent.Bought(offer.item, offer.qty, price)
    return true
}

// ------------------------------------------------------------------ festivals
class FestivalDef(val season: Season, val name: String, val goal: String, val target: Int, val reward: Map<String, Int>)

val FESTIVALS = listOf(
    FestivalDef(Season.SPRING, "Arbor Day", "Plant 20 crops or saplings", 20, mapOf("coins" to 500, "golden_acorn" to 1)),
    FestivalDef(Season.SUMMER, "Jam Fair", "Hand in 10 jams", 10, mapOf("coins" to 800)),
    FestivalDef(Season.FALL, "Pie Contest", "Hand in 5 Pumpkin Pies", 5, mapOf("coins" to 1500)),
    FestivalDef(Season.WINTER, "Frost Fair", "Sell 50 crops", 50, mapOf("coins" to 1000)),
)

/** Festival days 8 to 10 of every season (always sunny). */
val FESTIVAL_DAYS = 8..10

fun isFestivalDay(t: GameTime) = t.dayOfSeason in FESTIVAL_DAYS

fun World.festival(): FestivalDef? = if (isFestivalDay(time)) FESTIVALS.first { it.season == time.season } else null

private fun World.festivalKey() = "${time.year}-${time.season.name}"

/** Opens the festival window at its first dawn: remembers the counters it measures from. */
internal fun World.festivalDawn() {
    val f = festival() ?: return
    if (state.festivalKey == festivalKey()) return
    state.festivalKey = festivalKey()
    state.festivalProgress = 0
    state.festivalBase = when (f.season) {
        Season.SPRING -> state.stat("planted") + state.stat("trees_planted")
        Season.WINTER -> state.stat("crops_sold")
        else -> 0
    }
    events += WorldEvent.FestivalStarted(f.name)
}

fun World.festivalProgress(): Int {
    val f = festival() ?: return 0
    if (state.festivalKey != festivalKey()) return 0
    return when (f.season) {
        Season.SPRING -> (state.stat("planted") + state.stat("trees_planted") - state.festivalBase).toInt()
        Season.WINTER -> (state.stat("crops_sold") - state.festivalBase).toInt()
        else -> state.festivalProgress
    }.coerceIn(0, f.target)
}

/** Items the Jam Fair or Pie Contest accepts. */
fun World.festivalItems(): List<String> = when (festival()?.season) {
    Season.SUMMER -> state.inventory.keys.filter { baseId(it).endsWith("_jam") }
    Season.FALL -> state.inventory.keys.filter { baseId(it) == "pumpkin_pie" }
    else -> emptyList()
}

fun World.festivalHandIn(): Int {
    val f = festival() ?: return 0
    var need = f.target - festivalProgress()
    var given = 0
    for (k in festivalItems()) {
        val n = minOf(need, count(k))
        if (n <= 0) continue
        remove(k, n); need -= n; given += n
    }
    state.festivalProgress += given
    return given
}

fun World.festivalClaimable(): Boolean {
    val f = festival() ?: return false
    return festivalKey() !in state.festivalsClaimed && festivalProgress() >= f.target
}

fun World.claimFestival(): Boolean {
    val f = festival() ?: return false
    if (!festivalClaimable()) return false
    state.festivalsClaimed += festivalKey()
    f.reward.forEach { (k, v) -> if (k == "coins") state.coins += v else add(k, v) }
    events += WorldEvent.FestivalWon(f.name)
    gainXp(50 + 10 * state.level)
    return true
}
