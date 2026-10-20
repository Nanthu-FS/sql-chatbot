package com.ditherfarm.game

import org.json.JSONObject

/**
 * The farm pet (round 5, owner picks): at level [PET_LEVEL] a stray dog and cat turn up; the player
 * keeps one and names it. It follows the player between the farm, forest and mine, and brings one
 * small gift a day (a welcome gift on adoption), waiting until the player taps it.
 */
const val PET_LEVEL = 4
const val PET_NAME_MAX = 10

enum class PetKind(val defaultName: String, val happy: String) {
    DOG("BISCUIT", "wags happily"), CAT("MISO", "purrs");
    val id get() = name.lowercase()
}

/** Love needed for each pet level (level 1 at 0); round 6. */
val PET_LEVELS = listOf(0, 5, 15, 30, 50)

/** Things a pet can wear (round 6): bought in its page, one worn at a time. */
class PetAccessory(val id: String, val name: String, val price: Int, val petLevel: Int)

val PET_ACCESSORIES = listOf(
    PetAccessory("bandana", "Blue bandana", 300, 1),
    PetAccessory("bow", "Pink bow", 500, 2),
    PetAccessory("straw_hat", "Straw hat", 800, 3),
    PetAccessory("flower_crown", "Flower crown", 1200, 4),
).associateBy { it.id }

class PetState {
    var kind: PetKind? = null
    var name = ""
    /** Grows by petting (once a day), great catches at fetch and collected gifts. */
    var love = 0
    var pettedDay = -1
    var fetchBonusDay = -1
    var accessory: String? = null
    val accessories = LinkedHashSet<String>()
    /** Today's gift, kept until collected (one at a time; it does not stack). */
    val gift = LinkedHashMap<String, Int>()
    /** The adoption panel opened by itself once already. */
    var offered = false

    fun toJson(): JSONObject = JSONObject().apply {
        put("kind", kind?.name ?: ""); put("name", name); put("offered", offered)
        put("gift", JSONObject(gift as Map<*, *>))
        put("love", love); put("petted", pettedDay); put("fetchDay", fetchBonusDay)
        accessory?.let { put("wear", it) }
        put("acc", org.json.JSONArray(accessories.toList()))
    }

    companion object {
        fun fromJson(o: JSONObject) = PetState().apply {
            kind = o.optString("kind").takeIf { it.isNotEmpty() }?.let { PetKind.valueOf(it) }
            name = o.optString("name"); offered = o.optBoolean("offered")
            o.optJSONObject("gift")?.toIntMap()?.let { gift.putAll(it) }
            love = o.optInt("love"); pettedDay = o.optInt("petted", -1); fetchBonusDay = o.optInt("fetchDay", -1)
            accessory = o.optString("wear").takeIf { it.isNotEmpty() && PET_ACCESSORIES.containsKey(it) }
            o.optJSONArray("acc")?.let { a -> for (k in 0 until a.length()) a.getString(k).takeIf { PET_ACCESSORIES.containsKey(it) }?.let { accessories += it } }
        }
    }
}

val World.pet: PetState get() = state.pet
val World.hasPet get() = state.pet.kind != null
val World.petOffered get() = state.pet.kind == null && level >= PET_LEVEL

fun World.adoptPet(kind: PetKind, name: String): Boolean {
    if (!petOffered) return false
    val p = state.pet
    p.kind = kind
    p.name = name.trim().uppercase().take(PET_NAME_MAX).ifEmpty { kind.defaultName }
    p.gift.clear(); p.gift.putAll(rollPetGift(rng()))
    state.bump("pets")
    events += WorldEvent.PetAdopted(kind, p.name)
    return true
}

/** At dawn a pet with empty paws finds something new. */
internal fun World.petDawn(day: Int) {
    if (!hasPet || state.pet.gift.isNotEmpty()) return
    state.pet.gift.putAll(rollPetGift(Rng.forDay(state.seed, day, 41)))
}

val World.petLevel get() = PET_LEVELS.count { state.pet.love >= it }

/** Love toward the next level: (have, need), or null at the top level. */
fun World.petLoveToNext(): Pair<Int, Int>? = PET_LEVELS.getOrNull(petLevel)?.let { state.pet.love to it }

private fun World.addLove(n: Int) {
    val before = petLevel
    state.pet.love += n
    if (petLevel > before) events += WorldEvent.PetLevelUp(state.pet.name, petLevel)
}

/** A pat: love once a day. Returns true if it counted. */
fun World.petPetted(): Boolean {
    if (!hasPet || state.pet.pettedDay == time.dayIndex) return false
    state.pet.pettedDay = time.dayIndex
    addLove(1)
    return true
}

/** A game of fetch ended; a great catch gives love and, once a day, a small bonus find. */
fun World.petFetched(great: Boolean): Map<String, Int> {
    if (!hasPet || !great) return emptyMap()
    state.bump("pet_great_catches")
    addLove(1)
    if (state.pet.fetchBonusDay == time.dayIndex) return emptyMap()
    state.pet.fetchBonusDay = time.dayIndex
    val bonus = mapOf("coins" to 20 + 10 * petLevel)
    state.coins += bonus.getValue("coins")
    return bonus
}

fun World.accessoryBlocker(id: String): String? {
    val a = PET_ACCESSORIES[id] ?: return "Unknown"
    return when {
        id in state.pet.accessories -> null
        petLevel < a.petLevel -> "${state.pet.name} needs level ${a.petLevel}"
        state.coins < a.price -> "Needs ${a.price} coins"
        else -> null
    }
}

/** Buys (if needed) and puts on accessory [id]; null takes it off. */
fun World.wearAccessory(id: String?): Boolean {
    if (id == null) { state.pet.accessory = null; return true }
    accessoryBlocker(id)?.let { events += WorldEvent.Blocked(it); return false }
    if (id !in state.pet.accessories) { state.coins -= PET_ACCESSORIES.getValue(id).price; state.pet.accessories += id }
    state.pet.accessory = id
    return true
}

/** Small finds that grow with the pet's level: more of them, and rarer gems. */
internal fun World.rollPetGift(rng: Rng): Map<String, Int> {
    val out = LinkedHashMap<String, Int>()
    val seeds = data.crops.values.filter { cropUnlocked(it.id) }.map { it.seedItem }.ifEmpty { listOf("seed_wheat") }
    val lv = if (hasPet) petLevel else 1
    repeat(1 + rng.nextInt(3) + (lv - 1) / 2) {
        val (item, n) = when (rng.nextInt(10)) {
            0, 1, 2 -> seeds[rng.nextInt(seeds.size)] to 2 + rng.nextInt(3)
            3, 4 -> "stone" to 2 + rng.nextInt(3)
            5, 6 -> "wood" to 2 + rng.nextInt(3)
            7 -> "fibre" to 1 + rng.nextInt(3)
            else -> "coins" to 20 + 5 * level + rng.nextInt(20)
        }
        out[item] = (out[item] ?: 0) + n
    }
    if (rng.chance(0.08 + 0.03 * (lv - 1))) out[listOf("quartz", "amethyst", "old_coin")[rng.nextInt(3)]] = 1
    return out
}

/** Takes the pet's gift; null if there is none, or (with a toast reason) if storage is full. */
fun World.collectPetGift(): Map<String, Int>? {
    val gift = state.pet.gift
    if (gift.isEmpty()) return null
    if (gift.keys.any { it != "coins" && !hasRoom(it) }) { events += WorldEvent.Blocked("Storage is full"); return null }
    val got = LinkedHashMap(gift)
    for ((k, n) in got) if (k == "coins") state.coins += n else add(k, n)
    gift.clear()
    state.bump("pet_gifts")
    addLove(1)
    events += WorldEvent.PetGift(got)
    return got
}
