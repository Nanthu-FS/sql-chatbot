package com.ditherfarm.game

/**
 * Married life (round 7, Parts 4 and 5, owner picks). The spouse lives at the farmhouse: each
 * morning they bring a gift and do one chore (water the crops, top up the trough, play with the
 * pet), and their trade sells for more. Their hearts go on to 14, with a scene at 12 (they ask
 * about a family) and 14. Divorce is filed at the bank for [DIVORCE_COST]: they move back home and
 * their friendship drops to 4 hearts. The kid arrives a few days after the 12-heart scene; the player
 * names them; they grow a stage each season (baby, toddler, child) and play with the pet.
 */
val SPOUSE_EVENTS = listOf(12, 14)
const val KID_ASK_HEARTS = 12
const val KID_DAYS = 3
const val DIVORCE_HEARTS = 4

/** The spouse's morning gifts. */
val SPOUSE_GIFTS = mapOf(
    "ivy" to listOf("sunflower", "strawberry", "honey"), "sela" to listOf("grapes_juice", "grapes", "cooking_oil"),
    "willa" to listOf("plank", "nails", "beam"), "coco" to listOf("bread", "pancakes", "pumpkin_pie"),
    "rook" to listOf("iron_bar", "copper_bar", "coal"), "finn" to listOf("trout", "salmon", "perch"),
    "jasper" to listOf("honey", "apple_juice", "maple_syrup"), "marco" to listOf("quartz", "amethyst", "maple_syrup"),
)

/** The spouse's trade: one kind of thing sells for more. */
class SpousePerk(val categories: Set<String>, val mult: Double, val text: String)

val SPOUSE_PERKS = mapOf(
    "ivy" to SpousePerk(setOf("crop"), 1.10, "Crops sell for 10% more"),
    "sela" to SpousePerk(setOf("goods"), 1.10, "Jams, juices and pickles sell for 10% more"),
    "willa" to SpousePerk(setOf("material"), 1.10, "Planks, bars and bricks sell for 10% more"),
    "coco" to SpousePerk(setOf("dish"), 1.15, "Dishes sell for 15% more"),
    "rook" to SpousePerk(setOf("mine", "mineral"), 1.10, "Ores and minerals sell for 10% more"),
    "finn" to SpousePerk(setOf("fish"), 1.15, "Fish sell for 15% more"),
    "jasper" to SpousePerk(setOf("forest"), 1.10, "Forest finds sell for 10% more"),
    "marco" to SpousePerk(setOf("gem", "rare"), 1.15, "Gems and rare finds sell for 15% more"),
)

fun World.spousePerk(): SpousePerk? = romance.spouse?.let { SPOUSE_PERKS[it] }

/** The sell-price bonus from the spouse's trade for item [id]. */
fun World.spouseMult(id: String): Double = spousePerk()?.takeIf { data.items[id]?.category in it.categories }?.mult ?: 1.0

/** Each dawn: the spouse's gift and chore; the baby arrives on its day. */
internal fun World.familyDawn(day: Int) {
    val r = romance
    if (r.kidDue >= 0 && r.kidName == null && day >= r.kidDue) {
        r.kidFemale = Rng.forDay(state.seed, day, 77).chance(0.5)
        events += WorldEvent.KidArrived
    }
    val id = r.spouse ?: return
    val gifts = SPOUSE_GIFTS[id] ?: return
    val gift = gifts[Math.floorMod(day, gifts.size)]
    add(gift, 1)
    r.lastGift = gift
    // One chore a morning, the first that's any use, starting from a different one each day.
    val chores = listOf("water", "trough", "pet")
    for (k in chores.indices) {
        val done = when (chores[Math.floorMod(day + k, chores.size)]) {
            "water" -> state.plots.filter { it.owned && it.crop != null && !it.watered && !it.withered }.let { dry -> dry.forEach { it.watered = true }; if (dry.isEmpty()) null else "watered the crops" }
            "trough" -> if (ranch.animals.isNotEmpty() && ranch.feed < TROUGH_MAX) {
                val before = ranch.feed
                for (crop in listOf("corn", "wheat")) while (ranch.feed < TROUGH_MAX && takeAny(crop, 1) == 1) ranch.feed += FEED.getValue(crop)
                ranch.feed = minOf(ranch.feed, TROUGH_MAX)
                if (ranch.feed > before) "filled the trough" else null
            } else null
            else -> if (hasPet) { state.pet.love += 1; "played with ${state.pet.name}" } else null
        }
        if (done != null) { r.lastChore = done; break }
    }
    events += WorldEvent.SpouseMorning(id, gift, r.lastChore)
}

/** The 12-heart scene asks about a family; only while there is none. */
fun World.kidAskOpen() = romance.kidDue < 0 && romance.kidName == null

internal fun World.familySceneDone(id: String) {
    val s = romance.spouse ?: return
    if (id == heartScene(s, KID_ASK_HEARTS) && kidAskOpen()) {
        romance.kidDue = time.dayIndex + KID_DAYS.toLong()
        events += WorldEvent.KidOnTheWay(KID_DAYS)
    }
}

/** True while the baby has arrived and is waiting for a name. */
fun World.kidNeedsName() = romance.kidDue in 0..time.dayIndex.toLong() && romance.kidName == null

/** Names the baby (a boy or a girl, by chance, decided the morning they arrive). */
fun World.nameKid(name: String) {
    if (!kidNeedsName()) return
    val n = name.filter { it.isLetter() || it == ' ' }.trim().take(NAME_MAX).ifBlank { "Elias" }
    romance.kidName = n
    romance.kidDue = time.dayIndex.toLong()
    state.bump("kids")
    events += WorldEvent.KidNamed(n)
}

/** 0 baby, 1 toddler, 2 child: a stage a season. */
fun World.kidStage(): Int = if (romance.kidName == null) -1 else ((time.dayIndex - romance.kidDue) / Time.DAYS_PER_SEASON).toInt().coerceIn(0, 2)

fun World.kidAgeLabel() = when (kidStage()) { 0 -> "BABY"; 1 -> "TODDLER"; 2 -> "LITTLE ONE"; else -> "" }

// ------------------------------------------------------------------ divorce

fun World.divorceBlocker(): String? = when {
    romance.spouse == null -> "You're not married"
    state.coins < DIVORCE_COST -> "The bank wants $DIVORCE_COST coins to file the papers"
    else -> null
}

/** Files for divorce at the bank: the ex moves home, their hearts drop to [DIVORCE_HEARTS]. */
fun World.divorce(): Boolean {
    divorceBlocker()?.let { events += WorldEvent.Blocked(it); return false }
    val ex = romance.spouse ?: return false
    state.coins -= DIVORCE_COST
    romance.spouse = null
    romance.lastGift = null
    romance.lastChore = null
    state.friendship[ex] = World.HEARTS[DIVORCE_HEARTS - 1]
    // Courting them again starts over (the scenes play again); the married ones stay seen.
    state.storySeen.removeAll(setOf(bouquetScene(ex), dateScene(ex), proposalScene(ex), weddingScene(ex)))
    state.bump("divorces")
    events += WorldEvent.Divorced(ex)
    return true
}
