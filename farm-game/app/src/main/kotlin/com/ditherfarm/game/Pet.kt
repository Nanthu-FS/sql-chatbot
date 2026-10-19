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

class PetState {
    var kind: PetKind? = null
    var name = ""
    /** Today's gift, kept until collected (one at a time; it does not stack). */
    val gift = LinkedHashMap<String, Int>()
    /** The adoption panel opened by itself once already. */
    var offered = false

    fun toJson(): JSONObject = JSONObject().apply {
        put("kind", kind?.name ?: ""); put("name", name); put("offered", offered)
        put("gift", JSONObject(gift as Map<*, *>))
    }

    companion object {
        fun fromJson(o: JSONObject) = PetState().apply {
            kind = o.optString("kind").takeIf { it.isNotEmpty() }?.let { PetKind.valueOf(it) }
            name = o.optString("name"); offered = o.optBoolean("offered")
            o.optJSONObject("gift")?.toIntMap()?.let { gift.putAll(it) }
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

/** One to three small finds: seeds you can grow, stone, wood, fibre or coins; now and then a gem. */
internal fun World.rollPetGift(rng: Rng): Map<String, Int> {
    val out = LinkedHashMap<String, Int>()
    val seeds = data.crops.values.filter { cropUnlocked(it.id) }.map { it.seedItem }.ifEmpty { listOf("seed_wheat") }
    repeat(1 + rng.nextInt(3)) {
        val (item, n) = when (rng.nextInt(10)) {
            0, 1, 2 -> seeds[rng.nextInt(seeds.size)] to 2 + rng.nextInt(3)
            3, 4 -> "stone" to 2 + rng.nextInt(3)
            5, 6 -> "wood" to 2 + rng.nextInt(3)
            7 -> "fibre" to 1 + rng.nextInt(3)
            else -> "coins" to 20 + 5 * level + rng.nextInt(20)
        }
        out[item] = (out[item] ?: 0) + n
    }
    if (rng.chance(0.08)) out[listOf("quartz", "amethyst", "old_coin")[rng.nextInt(3)]] = 1
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
    events += WorldEvent.PetGift(got)
    return got
}
