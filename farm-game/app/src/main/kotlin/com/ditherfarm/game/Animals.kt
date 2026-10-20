package com.ditherfarm.game

import org.json.JSONArray
import org.json.JSONObject

/**
 * Farm animals (round 6, owner picks): hens, sheep and cows bought for the pasture from
 * [ANIMAL_LEVEL]. Each dawn every animal eats from the trough (filled with wheat or corn); a fed
 * animal makes its product (an egg or milk every day, wool every other day), which waits on it
 * until tapped. Petting once a day and keeping it fed raise its hearts; more hearts, better
 * products (silver at 3, gold at 5). A hungry dawn costs a heart. The Ranch hand upgrade (barn)
 * refills the trough from the Silo and collects the products into storage each morning.
 */
const val ANIMAL_LEVEL = 7
const val TROUGH_MAX = 60
const val MAX_HEARTS = 5
const val RANCH_HAND_LEVEL = 15
val RANCH_HAND_COST = mapOf("coins" to 4000, "plank" to 20, "iron_bar" to 5)

/** Feed each crop puts in the trough. */
val FEED = mapOf("wheat" to 1, "corn" to 2)

enum class AnimalKind(val label: String, val product: String, val price: Int, val unlockLevel: Int, val eats: Int, val everyDays: Int, val max: Int) {
    HEN("Hen", "egg", 250, ANIMAL_LEVEL, 1, 1, 4),
    SHEEP("Sheep", "wool", 800, 11, 2, 2, 2),
    COW("Cow", "milk", 1200, 13, 3, 1, 2);
    val id get() = name.lowercase()
}

class Animal(val kind: AnimalKind, var lastProductDay: Int) {
    var hearts = 0
    var product = false
    var fedDay = -1
    var pettedDay = -1

    fun toJson(): JSONObject = JSONObject().apply {
        put("k", kind.name); put("h", hearts); put("p", product); put("f", fedDay); put("pet", pettedDay); put("last", lastProductDay)
    }

    companion object {
        fun fromJson(o: JSONObject) = runCatching { AnimalKind.valueOf(o.getString("k")) }.getOrNull()?.let { k ->
            Animal(k, o.optInt("last")).apply {
                hearts = o.optInt("h"); product = o.optBoolean("p"); fedDay = o.optInt("f", -1); pettedDay = o.optInt("pet", -1)
            }
        }
    }
}

class RanchState {
    val animals = ArrayList<Animal>()
    var feed = 0
    var ranchHand = false

    fun toJson(): JSONObject = JSONObject().apply {
        put("feed", feed); put("hand", ranchHand)
        put("animals", JSONArray().apply { animals.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject) = RanchState().apply {
            feed = o.optInt("feed"); ranchHand = o.optBoolean("hand")
            o.optJSONArray("animals")?.let { a -> for (k in 0 until a.length()) a.optJSONObject(k)?.let { Animal.fromJson(it) }?.let { animals += it } }
        }
    }
}

val World.ranch: RanchState get() = state.ranch
private val World.today get() = time.dayIndex

fun World.animalCount(kind: AnimalKind) = ranch.animals.count { it.kind == kind }

/** Feed the animals eat each morning. */
fun World.dailyFeed() = ranch.animals.sumOf { it.kind.eats }

fun World.animalBlocker(kind: AnimalKind): String? = when {
    level < ANIMAL_LEVEL -> "Animals come at level $ANIMAL_LEVEL"
    level < kind.unlockLevel -> "Unlocks at level ${kind.unlockLevel}"
    animalCount(kind) >= kind.max -> "The pasture holds ${kind.max} ${kind.label.lowercase()}${if (kind == AnimalKind.SHEEP) "" else "s"}"
    state.coins < kind.price -> "Needs ${kind.price}c"
    else -> null
}

fun World.buyAnimal(kind: AnimalKind): Boolean {
    animalBlocker(kind)?.let { events += WorldEvent.Blocked(it); return false }
    state.coins -= kind.price
    // Its first product comes the morning after it arrives (if it is fed).
    ranch.animals += Animal(kind, today + 1 - kind.everyDays)
    state.bump("animals_bought")
    events += WorldEvent.AnimalBought(ranch.animals.size - 1, kind)
    return true
}

/** Takes up to [n] of [item] from storage, plain quality first; returns how many it took. */
private fun World.takeAny(item: String, n: Int): Int {
    var left = n
    for (q in Quality.entries) {
        val k = q.key(item)
        val take = minOf(left, count(k))
        if (take > 0) { remove(k, take); left -= take }
    }
    return n - left
}

fun World.troughBlocker(crop: String): String? = when {
    crop !in FEED -> "Animals eat wheat or corn"
    ranch.feed >= TROUGH_MAX -> "The trough is full"
    count(crop, anyQuality = true) == 0 -> "No ${data.name(crop).lowercase()} in the Silo"
    else -> null
}

/** Puts up to [n] of [crop] in the trough (no more than it holds); returns the feed added. */
fun World.fillTrough(crop: String, n: Int): Int {
    troughBlocker(crop)?.let { events += WorldEvent.Blocked(it); return 0 }
    val per = FEED.getValue(crop)
    val want = minOf(n, (TROUGH_MAX - ranch.feed + per - 1) / per)
    val took = takeAny(crop, want)
    ranch.feed = minOf(TROUGH_MAX, ranch.feed + took * per)
    events += WorldEvent.TroughFilled(took * per)
    return took * per
}

/** Each dawn the animals eat; fed ones make their product, hungry ones lose a heart. */
internal fun World.ranchDawn(day: Int) {
    if (ranch.animals.isEmpty()) return
    if (ranch.ranchHand) {
        // The hand tops the trough up from the Silo: corn first (more feed per crop), then wheat.
        for (crop in listOf("corn", "wheat")) {
            while (ranch.feed < dailyFeed() * 2 && ranch.feed < TROUGH_MAX && takeAny(crop, 1) == 1) ranch.feed += FEED.getValue(crop)
        }
        ranch.feed = minOf(ranch.feed, TROUGH_MAX)
    }
    var hungry = 0
    for (a in ranch.animals) {
        if (ranch.feed >= a.kind.eats) {
            ranch.feed -= a.kind.eats
            a.fedDay = day
            if (!a.product && day - a.lastProductDay >= a.kind.everyDays) a.product = true
        } else {
            a.hearts = maxOf(0, a.hearts - 1)
            hungry++
        }
    }
    if (ranch.ranchHand) ranch.animals.indices.forEach { if (ranch.animals[it].product) collectProduct(it, byHand = true, day = day) }
    events += WorldEvent.AnimalsFed(ranch.animals.size - hungry, hungry)
}

/** The quality an animal's product has now: silver at 3 hearts, gold at [MAX_HEARTS]. */
fun Animal.quality() = when { hearts >= MAX_HEARTS -> Quality.GOLD; hearts >= 3 -> Quality.SILVER; else -> Quality.NORMAL }

/** Collects animal [i]'s product (a tap also pets it); [byHand] is the Ranch hand at dawn. */
fun World.collectProduct(i: Int, byHand: Boolean = false, day: Int = today): Boolean {
    val a = ranch.animals.getOrNull(i) ?: return false
    if (!a.product) return false
    val item = a.kind.product
    if (!hasRoom(item)) { if (!byHand) events += WorldEvent.Blocked("Barn full"); return false }
    val q = a.quality()
    add(q.key(item), 1)
    a.product = false
    a.lastProductDay = day
    state.bump("animal_products")
    state.bump("product_$item")
    val xp = when (a.kind) { AnimalKind.HEN -> 2; AnimalKind.SHEEP -> 6; AnimalKind.COW -> 4 }
    events += WorldEvent.ProductCollected(i, item, q, xp, byHand)
    gainXp(xp)
    if (!byHand) petAnimal(i)
    return true
}

/** Pets animal [i] once a day: a heart, up to [MAX_HEARTS]. */
fun World.petAnimal(i: Int): Boolean {
    val a = ranch.animals.getOrNull(i) ?: return false
    if (a.pettedDay == today) return false
    a.pettedDay = today
    a.hearts = minOf(MAX_HEARTS, a.hearts + 1)
    events += WorldEvent.AnimalPetted(i, a.hearts)
    return true
}

fun World.pettedToday(a: Animal) = a.pettedDay == today

fun World.ranchHandBlocker(): String? = when {
    ranch.ranchHand -> "Already hired"
    level < RANCH_HAND_LEVEL -> "Unlocks at level $RANCH_HAND_LEVEL"
    !canAfford(RANCH_HAND_COST) -> "Needs " + RANCH_HAND_COST.entries.joinToString(", ") { (k, v) -> if (k == "coins") "${v}c" else "$v ${data.name(k)}" }
    else -> null
}

fun World.hireRanchHand(): Boolean {
    ranchHandBlocker()?.let { events += WorldEvent.Blocked(it); return false }
    pay(RANCH_HAND_COST)
    ranch.ranchHand = true
    events += WorldEvent.RanchHandHired
    return true
}

/** Products the animals give (for orders). */
fun World.animalProductsNow(): Set<String> = ranch.animals.map { it.kind.product }.toSet()
