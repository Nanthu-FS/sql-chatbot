package com.ditherfarm.game

/**
 * Villager friendships (round 6, owner picks: gifts and orders, cutscenes at 2, 5 and 8 hearts, a
 * unique gift at 10, villagers walking the village). Friendship points come from delivered orders
 * (World.deliver), a chat once a day and one gift a day: loved gifts (their favourites) count most,
 * liked ones (the same kind of thing) less, scraps barely; better quality counts a little more.
 * Hearts are thresholds on the points ([World.HEARTS]), ten at most.
 */
const val FRIEND_HEARTS = 10
const val TALK_POINTS = 10
const val GIFT_LOVED = 80
const val GIFT_LIKED = 40
const val GIFT_PLAIN = 20
const val GIFT_POOR = 5
val HEART_EVENTS = listOf(2, 5, 8)

enum class GiftReaction(val label: String) { LOVED("loves it"), LIKED("likes it"), PLAIN("says thanks"), POOR("looks puzzled") }

private val World.today get() = time.dayIndex

fun World.villager(id: String) = data.villagers.firstOrNull { it.id == id }

fun World.friendship(id: String) = state.friendship[id] ?: 0

/** Points this villager's next heart needs, as (have, need), or null at the most they can reach now. */
fun World.nextHeart(id: String): Pair<Int, Int>? = (1..maxHearts(id)).map { heartPoints(it) }.firstOrNull { friendship(id) < it }?.let { friendship(id) to it }

/** Adds friendship and announces any new heart. */
fun World.befriend(id: String, points: Int) {
    val before = hearts(id)
    // Round 7: points stop at the hearts this villager can reach for now (8 for a sweetheart until a
    // bouquet; 14 for a spouse); a friendship already past the cap is never cut back.
    val now = friendship(id)
    state.friendship[id] = if (points <= 0) now + points else maxOf(now, minOf(now + points, friendshipCap(id)))
    val after = hearts(id)
    if (after > before) events += WorldEvent.HeartUp(id, after)
    // Best friends: their keepsake, once.
    if (after >= FRIEND_HEARTS && before < FRIEND_HEARTS) {
        DECOR.values.firstOrNull { it.keepsake == id }?.let { d ->
            if (state.keepsakes.add(d.id)) events += WorldEvent.KeepsakeGiven(id, d.id)
        }
    }
}

fun World.talkedToday(id: String) = state.talked[id] == today
fun World.giftedToday(id: String) = state.gifted[id] == today

/** A chat, once a day: a few points and a line of their talk. */
fun World.talk(id: String): Boolean {
    val v = villager(id) ?: return false
    if (talkedToday(id)) return false
    state.talked[id] = today
    befriend(id, TALK_POINTS)
    val lines = TALK[id] ?: listOf("Nice day for it.")
    events += WorldEvent.Talked(id, lines[Math.floorMod(today * 7 + id.length, lines.size)].replace("{name}", playerName), v.name)
    return true
}

/** Things that cannot be given: seeds, tools, placeable fixtures and the like. */
fun World.giftable(key: String): Boolean {
    val item = data.items[baseId(key)] ?: return false
    return item.price > 0 && item.storage != Storage.NONE && item.category !in setOf("seed", "fixture")
}

fun World.giftReaction(id: String, key: String): GiftReaction {
    val v = villager(id) ?: return GiftReaction.PLAIN
    val base = baseId(key)
    val item = data.item(base)
    return when {
        base in v.favorites -> GiftReaction.LOVED
        v.favorites.any { data.items[it]?.category == item.category } -> GiftReaction.LIKED
        item.price <= 4 -> GiftReaction.POOR
        else -> GiftReaction.PLAIN
    }
}

fun World.giftBlocker(id: String, key: String): String? = when {
    villager(id) == null -> "Nobody's there"
    giftedToday(id) -> "One gift a day"
    !giftable(key) -> "They can't use that"
    count(key) <= 0 -> "You have none"
    else -> null
}

/** Gives one of [key] (exact quality) to villager [id]. */
fun World.giveGift(id: String, key: String): Boolean {
    giftBlocker(id, key)?.let { events += WorldEvent.Blocked(it); return false }
    remove(key, 1)
    state.gifted[id] = today
    val reaction = giftReaction(id, key)
    val base = when (reaction) { GiftReaction.LOVED -> GIFT_LOVED; GiftReaction.LIKED -> GIFT_LIKED; GiftReaction.PLAIN -> GIFT_PLAIN; GiftReaction.POOR -> GIFT_POOR }
    val points = (base * Quality.of(key).mult).toInt()
    state.bump("gifts")
    events += WorldEvent.GiftGiven(id, baseId(key), reaction, points)
    befriend(id, points)
    if (reaction == GiftReaction.LOVED && isDating(id)) jealous(id)
    return true
}

/** The heart event (2, 5 or 8; 12 and 14 for a spouse) this villager has waiting, or null. */
fun World.pendingHeartEvent(id: String): Int? = (HEART_EVENTS + if (isSpouse(id)) SPOUSE_EVENTS else emptyList()).firstOrNull {
    hearts(id) >= it && heartScene(id, it) !in state.storySeen && sceneById(heartScene(id, it)) != null && (it != KID_ASK_HEARTS || kidAskOpen())
}

fun heartScene(id: String, hearts: Int) = "heart_${id}_$hearts"

/** A few lines of everyday talk per villager. */
val TALK: Map<String, List<String>> = mapOf(
    "mara" to listOf("Fresh loaves at dawn, every day. Flour's the secret. Well, flour and stubbornness.", "If you've got wheat to spare, my oven's always hungry.", "You look tired, {name}. Take a roll for the road."),
    "tobias" to listOf("Measure twice, cut once. Grandpa taught you that too, I bet.", "Planks don't grow on trees. Well. They do. You know what I mean.", "That old barn of yours has good bones."),
    "ivy" to listOf("Sunflowers follow the sun all day. I like that about them.", "Bees have been busy near your fields. Good sign!", "Bring me something that blooms and I'll be your friend forever."),
    "bram" to listOf("Iron's honest. It does what you hammer it to do.", "Hot forge, cold ale, good day.", "Your pickaxe sounds dull from here. Come see me."),
    "juniper" to listOf("The inn's full of travellers since the village came back to life.", "A warm stew fixes most things. The rest, it improves.", "Stay out of the rain, dear. Or don't, the crops need it."),
    "pip" to listOf("I found a shiny rock! It's probably a diamond. Probably.", "Can your dog come play? Or your cat? Both?", "When I grow up I'm going to dig to the bottom of the mine!"),
    "sela" to listOf("Grapes need patience. So do people.", "The valley's air is good for wine. And for gossip.", "A little sun, a little rain, a lot of waiting."),
    "fennel" to listOf("Cabbage, honey, a pinch of amethyst dust. Cures nearly anything.", "Mind the mushrooms in the forest. Some are friends, some are not.", "You've colour in your cheeks. Farm life suits you."),
    "rook" to listOf("Bram taught me the anvil. I'm teaching it to listen.", "The Mine's deeper than anyone thought. Bring me what you find.", "Good steel takes a lot of hitting."),
    "willa" to listOf("Every house in this village has a story nailed into it.", "Hand me a beam and I'll hand you a home.", "I like the sound of a busy village. Saws and laughter."),
    "coco" to listOf("Coffee's on! Always.", "A pumpkin pie in the window brings in half the valley.", "Sit, sit. The cafe's quieter before the lunch rush."),
    "finn" to listOf("River's low today. The fish are sulking.", "Best bait's patience. Second best is worms.", "Saw a heron this morning. Bigger than Pip."),
    "jasper" to listOf("Got a request? I know three hundred songs, and half of them are about cows.", "Juniper says I can stay at the inn as long as I play at supper.", "Every town sounds different, {name}. This one sounds like hammers."),
    "marco" to listOf("Your top field is exactly one point six acres. Sorry. Old habits.", "Ms. Kessler still writes to me. I use the letters to light the stove.", "Finn's teaching me to fish. He says I talk too much."),
)
