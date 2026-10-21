package com.ditherfarm.game

import org.json.JSONArray
import org.json.JSONObject

/**
 * Romance (round 7, owner picks). The farmer can court the villagers of the other gender marked
 * romance in villagers.json. Their friendship stops at 8 hearts until a bouquet starts dating, which
 * opens hearts 9 and 10. Several can be dated at once, but a bouquet or a loved gift to one costs the
 * others [JEALOUSY] points. At 10 hearts, with the bigger farmhouse built, a crafted ring proposes; the
 * wedding is [WEDDING_DAYS] days later in the village square. A spouse's hearts go on to
 * [SPOUSE_HEARTS]. Divorce (Part 4) is at the bank.
 */
const val COURT_HEARTS = 8
const val SPOUSE_HEARTS = 14
const val WEDDING_DAYS = 3
const val JEALOUSY = 120
const val DIVORCE_COST = 20_000
const val BOUQUET = "bouquet"
const val RING = "ring"

/** Points for hearts 11 to 14 (a spouse only), after [World.HEARTS]. */
val SPOUSE_EXTRA = intArrayOf(3850, 4500, 5200, 6000)

class RomanceState {
    val dating = LinkedHashSet<String>()
    var engaged: String? = null
    var weddingDay = -1L
    var spouse: String? = null
    /** 0: Grandpa's old farmhouse; 1: the family farmhouse (needed to propose). */
    var farmhouse = 0
    /** Part 5: the kid. [kidDue] is the day the baby arrives (-1: none on the way); the player names it. */
    var kidDue = -1L
    var kidName: String? = null
    var kidFemale = false
    /** Part 4: what the spouse did this morning (for the farmhouse page). */
    var lastGift: String? = null
    var lastChore: String? = null

    fun toJson(): JSONObject = JSONObject().apply {
        put("dating", JSONArray(dating.toList()))
        engaged?.let { put("engaged", it) }
        put("weddingDay", weddingDay)
        spouse?.let { put("spouse", it) }
        put("farmhouse", farmhouse)
        put("kidDue", kidDue)
        kidName?.let { put("kidName", it) }
        put("kidFemale", kidFemale)
        lastGift?.let { put("lastGift", it) }
        lastChore?.let { put("lastChore", it) }
    }

    companion object {
        fun fromJson(o: JSONObject) = RomanceState().apply {
            o.optJSONArray("dating")?.let { a -> for (i in 0 until a.length()) dating += a.getString(i) }
            engaged = o.optString("engaged").ifEmpty { null }
            weddingDay = o.optLong("weddingDay", -1)
            spouse = o.optString("spouse").ifEmpty { null }
            farmhouse = o.optInt("farmhouse")
            kidDue = o.optLong("kidDue", -1)
            kidName = o.optString("kidName").ifEmpty { null }
            kidFemale = o.optBoolean("kidFemale")
            lastGift = o.optString("lastGift").ifEmpty { null }
            lastChore = o.optString("lastChore").ifEmpty { null }
        }
    }
}

val World.romance: RomanceState get() = state.romance

/** True if the farmer can court [id]: a romance candidate of the other gender. */
fun World.canCourt(id: String): Boolean {
    val v = villager(id) ?: return false
    val farmerFemale = state.playerGender == Gender.FEMALE
    return v.romance && v.female != farmerFemale
}

fun World.isDating(id: String) = id in romance.dating
fun World.isSpouse(id: String) = romance.spouse == id

/** The most hearts [id] can reach now. */
fun World.maxHearts(id: String): Int = when {
    isSpouse(id) -> SPOUSE_HEARTS
    canCourt(id) && !isDating(id) && romance.engaged != id -> COURT_HEARTS
    else -> FRIEND_HEARTS
}

/** Friendship points at heart [n] (1-based), counting a spouse's extra hearts. */
fun heartPoints(n: Int): Int = if (n <= World.HEARTS.size) World.HEARTS[n - 1] else SPOUSE_EXTRA[n - World.HEARTS.size - 1]

/** The cap on [id]'s friendship points (they never drop to it, only stop there). */
fun World.friendshipCap(id: String): Int = heartPoints(maxHearts(id))

fun World.bouquetBlocker(id: String): String? = when {
    !canCourt(id) -> "Just good friends"
    isDating(id) -> "Already dating"
    isSpouse(id) -> "Already married"
    hearts(id) < COURT_HEARTS -> "Needs $COURT_HEARTS hearts"
    count(BOUQUET) <= 0 -> "Make a bouquet at the Workbench"
    else -> null
}

/** Gives a bouquet: [id] and the farmer are dating (the scene plays in the panel). */
fun World.giveBouquet(id: String): Boolean {
    bouquetBlocker(id)?.let { events += WorldEvent.Blocked(it); return false }
    remove(BOUQUET, 1)
    romance.dating += id
    jealous(id)
    state.bump("bouquets")
    events += WorldEvent.Dating(id)
    return true
}

/** The others being dated feel it when [id] gets a bouquet or a loved gift. */
internal fun World.jealous(id: String) {
    for (other in romance.dating) if (other != id && romance.spouse == null) {
        state.friendship[other] = (friendship(other) - JEALOUSY).coerceAtLeast(0)
        events += WorldEvent.Jealous(other, id)
    }
}

fun World.proposeBlocker(id: String): String? = when {
    romance.spouse != null -> "Already married"
    romance.engaged != null -> "Already engaged"
    !isDating(id) -> "You're not dating"
    hearts(id) < FRIEND_HEARTS -> "Needs $FRIEND_HEARTS hearts"
    romance.farmhouse < 1 -> "Where would you live? Build the family farmhouse first"
    count(RING) <= 0 -> "Forge a ring at the Workbench (a gold bar and an amethyst)"
    else -> null
}

/** Proposes with the ring: engaged, the wedding in [WEDDING_DAYS] days. */
fun World.propose(id: String): Boolean {
    proposeBlocker(id)?.let { events += WorldEvent.Blocked(it); return false }
    remove(RING, 1)
    romance.engaged = id
    romance.weddingDay = time.dayIndex + WEDDING_DAYS.toLong()
    events += WorldEvent.Engaged(id, WEDDING_DAYS)
    return true
}

/** The wedding scene has played: married, the others dated go back to being friends. */
internal fun World.marry(id: String) {
    if (romance.engaged != id) return
    romance.engaged = null
    romance.weddingDay = -1
    romance.spouse = id
    romance.dating.clear()
    state.bump("weddings")
    events += WorldEvent.Married(id)
}

/** True on (or after) the wedding day. */
fun World.weddingDue(): Boolean = romance.engaged != null && time.dayIndex >= romance.weddingDay

/** The wedding scene, on the day (Chrome plays it on the farm). */
fun World.pendingWedding(): Scene? = romance.engaged?.takeIf { weddingDue() }?.let { sceneById(weddingScene(it)) }

/** The date on the hill, once dating reaches ten hearts (played from their panel). */
fun World.pendingDate(id: String) = isDating(id) && hearts(id) >= FRIEND_HEARTS && dateScene(id) !in state.storySeen

/** Shown under their hearts: where the two of you stand. */
fun World.romanceLabel(id: String): String? = when {
    isSpouse(id) -> if (state.playerGender == Gender.FEMALE) "HUSBAND" else "WIFE"
    romance.engaged == id -> "ENGAGED"
    isDating(id) -> "DATING"
    else -> null
}

// ------------------------------------------------------------------ the family farmhouse

val FARMHOUSE_COST = mapOf("coins" to 15_000, "plank" to 60, "brick" to 40, "glass" to 10)
const val FARMHOUSE_LEVEL = 12

fun World.farmhouseBlocker(): String? = when {
    romance.farmhouse >= 1 -> "Already built"
    level < FARMHOUSE_LEVEL -> "Unlocks at level $FARMHOUSE_LEVEL"
    else -> FARMHOUSE_COST.entries.firstOrNull { (k, v) -> if (k == "coins") state.coins < v else count(k) < v }
        ?.let { (k, v) -> if (k == "coins") "Needs $v coins" else "Needs $v ${data.name(k)}" }
}

fun World.buildFarmhouse(): Boolean {
    farmhouseBlocker()?.let { events += WorldEvent.Blocked(it); return false }
    for ((k, v) in FARMHOUSE_COST) if (k == "coins") state.coins -= v else remove(k, v)
    romance.farmhouse = 1
    gainXp(300)
    events += WorldEvent.FarmhouseBuilt
    return true
}
