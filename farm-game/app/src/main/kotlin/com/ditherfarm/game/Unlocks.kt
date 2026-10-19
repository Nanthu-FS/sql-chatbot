package com.ditherfarm.game

// ------------------------------------------------------------------ unlocks by doing
/** Something learned by doing: a lifetime stat reaching [n]. */
class Feat(val stat: String, val n: Long, val text: String)

/**
 * Stone tools, the quarry and forest zones open by doing (Docs/DECISIONS.md, round 2). Keys are
 * tool recipe ids, "quarry" and "zone_<id>".
 */
val FEATS: Map<String, Feat> = mapOf(
    "tool_axe_1" to Feat("fells", 5, "Chop down 5 trees"),
    "tool_pickaxe_1" to Feat("rocks_broken", 5, "Break 5 rocks in the quarry"),
    "tool_hoe_1" to Feat("planted", 30, "Plant 30 crops"),
    "tool_sickle_1" to Feat("harvested", 40, "Harvest 40 crops"),
    "tool_can_1" to Feat("watered", 40, "Water 40 crops"),
    "quarry" to Feat("forest_stone", 8, "Pick up 8 stones in the forest"),
    "zone_B" to Feat("fells_zone_A", 12, "Chop 12 trees in the Edge Grove"),
    "zone_C" to Feat("fells_zone_B", 25, "Chop 25 trees in the second grove"),
    "zone_D" to Feat("fells_zone_C", 35, "Chop 35 trees in the third grove"),
    "zone_E" to Feat("fells_zone_D", 45, "Chop 45 trees in the fourth grove"),
)

/** Machine blueprints, found by doing; building one also needs its level. */
val BLUEPRINTS: Map<String, Feat> = mapOf(
    "windmill" to Feat("harvest_wheat", 10, "Harvest 10 wheat"),
    "compost_bin" to Feat("harvested", 40, "Harvest 40 crops"),
    "sawmill" to Feat("fells", 8, "Chop down 8 trees"),
    "bakery_oven" to Feat("made_flour", 3, "Make 3 flour at the Windmill"),
    "furnace" to Feat("rocks_broken", 15, "Break 15 quarry rocks"),
    "preserves_jar" to Feat("harvested", 200, "Harvest 200 crops"),
    "wood_chipper" to Feat("fells", 40, "Chop down 40 trees"),
    "geode_anvil" to Feat("rocks_broken", 60, "Break 60 rocks"),
    "beehive" to Feat("harvested", 300, "Harvest 300 crops"),
    "seed_maker" to Feat("planted", 300, "Plant 300 crops"),
    "charcoal_kiln" to Feat("fells", 80, "Chop down 80 trees"),
    "logging_hut" to Feat("fells", 100, "Chop down 100 trees"),
    "fruit_press" to Feat("harvest_strawberry", 20, "Harvest 20 strawberries"),
    "greenhouse" to Feat("harvested", 800, "Harvest 800 crops"),
    "syrup_kettle" to Feat("fell_maple", 5, "Fell 5 maple trees"),
    "market_cart" to Feat("crops_sold", 500, "Sell 500 crops"),
    "oil_press" to Feat("harvest_sunflower", 20, "Harvest 20 sunflowers"),
    "tree_nursery" to Feat("trees_planted", 10, "Plant 10 trees"),
    "drill_rig" to Feat("rocks_broken", 400, "Break 400 rocks"),
)

fun World.met(f: Feat) = state.stat(f.stat) >= f.n
fun World.progress(f: Feat) = "${minOf(state.stat(f.stat), f.n)}/${f.n}"

/** "Needs: Chop down 5 trees (3/5)" for a locked feat, or null. */
fun World.featBlocker(key: String): String? =
    (FEATS[key] ?: BLUEPRINTS[key])?.takeIf { !met(it) }?.let { "${it.text} (${progress(it)})" }

/** The quarry opens after picking up stones in the forest (or once it has been mined). */
val World.quarryOpen get() = met(FEATS.getValue("quarry")) || state.stat("rocks_broken") > 0

/** Announces feats and blueprints the moment they are met. */
internal fun World.checkFeats() {
    for ((key, f) in FEATS + BLUEPRINTS.mapKeys { "bp_" + it.key }) {
        if (key in state.featsSeen || !met(f)) continue
        state.featsSeen += key
        events += WorldEvent.FeatDone(key)
    }
}

// ------------------------------------------------------------------ Grandpa's notebook
/** Where a quest's pointer points. */
enum class Pointer { NONE, FIELD, MARKET, ORDERS, WORKBENCH, FOREST, TREE, ROCK, MINE, WINDMILL, FIELD_B, SAWMILL, BUNKHOUSE, TRUCK, VILLAGE }

/**
 * One guided step. A [stat] quest counts that stat from when the quest started (needs [n] more);
 * otherwise [check] reports (have, need).
 */
class Quest(
    val id: String, val text: String, val pointer: Pointer, val reward: Map<String, Int>,
    val stat: String? = null, val n: Long = 1, val check: (World.() -> Pair<Long, Long>)? = null,
)

private fun yes(b: Boolean) = (if (b) 1L else 0L) to 1L

val QUESTS = listOf(
    Quest("plant", "Plant wheat on 6 plots", Pointer.FIELD, mapOf("coins" to 30, "xp" to 10), "planted", 6),
    Quest("water", "Water the crops you planted", Pointer.FIELD, mapOf("coins" to 20, "xp" to 10), "watered", 6),
    Quest("harvest", "Harvest 6 crops", Pointer.FIELD, mapOf("coins" to 40, "xp" to 15), "harvested", 6),
    Quest("sell", "Sell 5 crops at the Market", Pointer.MARKET, mapOf("coins" to 30, "xp" to 10), "crops_sold", 5),
    Quest("chop", "Go to the forest and chop down 2 trees", Pointer.TREE, mapOf("coins" to 30, "xp" to 15), "fells", 2),
    Quest("stones", "Pick up 4 stones from forest rocks", Pointer.ROCK, mapOf("coins" to 30, "xp" to 10), "forest_stone", 4),
    Quest("trees5", "Chop down 5 trees in all", Pointer.TREE, mapOf("wood" to 5, "xp" to 15), check = { minOf(state.stat("fells"), 5L) to 5L }),
    Quest("axe", "Craft a Stone Axe at the Workbench", Pointer.WORKBENCH, mapOf("coins" to 50, "xp" to 25), check = { yes(toolTier(Tool.AXE) >= 1) }),
    Quest("order", "Deliver an order from the board", Pointer.ORDERS, mapOf("coins" to 40, "xp" to 20), "orders", 1),
    Quest("quarry", "Pick up 8 stones in the forest to open the quarry", Pointer.ROCK, mapOf("coins" to 30, "xp" to 15), check = { yes(quarryOpen) }),
    Quest("mine", "Break 5 rocks in the quarry", Pointer.MINE, mapOf("coins" to 50, "xp" to 20), "rocks_broken", 5),
    Quest("pickaxe", "Craft a Stone Pickaxe", Pointer.WORKBENCH, mapOf("coins" to 60, "xp" to 25), check = { yes(toolTier(Tool.PICKAXE) >= 1) }),
    Quest("windmill", "Build the Windmill", Pointer.WINDMILL, mapOf("coins" to 80, "xp" to 30), check = { yes(isBuilt("windmill")) }),
    Quest("flour", "Make flour at the Windmill", Pointer.WINDMILL, mapOf("coins" to 40, "xp" to 20), "made_flour", 1),
    Quest("field_b", "Buy Field B", Pointer.FIELD_B, mapOf("coins" to 100, "xp" to 30), check = { yes(blockOwned(1)) }),
    Quest("sawmill", "Build the Sawmill in the forest", Pointer.SAWMILL, mapOf("coins" to 100, "xp" to 30), check = { yes(isBuilt("sawmill")) }),
    Quest("level5", "Reach level 5", Pointer.NONE, mapOf("coins" to 150), check = { minOf(state.level, 5).toLong() to 5L }),
    Quest("hire", "Hire a farmhand at the bunkhouse", Pointer.BUNKHOUSE, mapOf("coins" to 100, "xp" to 30), check = { yes(state.workers.isNotEmpty()) }),
    Quest("assign", "Give your farmhand a field to work", Pointer.BUNKHOUSE, mapOf("coins" to 50, "xp" to 20), check = { yes(state.workers.values.any { it.field >= 0 }) }),
    Quest("truck", "Send the truck to town", Pointer.TRUCK, mapOf("coins" to 100, "xp" to 30), "truck_trips", 1),
    Quest("donate", "Give goods to restore the village (tap a house)", Pointer.VILLAGE, mapOf("coins" to 100, "xp" to 30), "donated", 1),
    Quest("stone_village", "Rebuild the village in stone", Pointer.VILLAGE, mapOf("coins" to 500, "xp" to 100), check = { yes(state.villageTier >= 1) }),
)

val World.quest: Quest? get() = QUESTS.getOrNull(state.questIndex)

/** (have, need) for the current quest. */
fun World.questProgress(): Pair<Long, Long> {
    val q = quest ?: return 0L to 0L
    q.check?.let { return it(this) }
    return minOf(state.stat(q.stat!!) - state.questBase, q.n) to q.n
}

val World.questDone get() = quest != null && questProgress().let { (h, n) -> h >= n }

fun World.claimQuest(): Boolean {
    val q = quest ?: return false
    if (!questDone) return false
    for ((k, v) in q.reward) when (k) {
        "coins" -> state.coins += v
        "xp" -> {}
        else -> add(k, v)
    }
    events += WorldEvent.QuestDone(q.id)
    gainXp(q.reward["xp"] ?: 0)
    startQuest(state.questIndex + 1)
    return true
}

internal fun World.startQuest(i: Int) {
    state.questIndex = i
    state.questBase = QUESTS.getOrNull(i)?.stat?.let { state.stat(it) } ?: 0L
}

/** The current quest's pointer while it is unfinished. */
val World.pointer get() = quest?.takeIf { !questDone }?.pointer ?: Pointer.NONE

// ------------------------------------------------------------------ what can be had right now
/**
 * Items the player can get today with their current crops, zones, tools and machines: what
 * orders may ask for. Rare extras (under 10%) and tools are left out.
 */
fun World.obtainableNow(): Set<String> {
    val got = HashSet<String>()
    val season = time.season
    for (c in data.crops.values) if (cropUnlocked(c.id) && (season in c.seasons || greenhouseOpen)) got += c.id
    // Forest: trees the axe can fell in open zones, forage, loose rocks and branches.
    val axe = toolTier(Tool.AXE)
    for (sp in forestSpots) {
        if (!zoneOpen(sp.zone)) continue
        when (sp.kind) {
            SpotKind.TREE -> data.trees[sp.native]?.takeIf { it.minAxe <= axe }?.let { t ->
                got += "wood"
                if (t.hardwood.last > 0) got += "hardwood"
                t.extras.filter { it.second >= 0.1 }.forEach { got += it.first }
            }
            SpotKind.BUSH -> if (season == Season.SUMMER) got += "blackberry"
            SpotKind.APPLE -> if (season == Season.SUMMER || season == Season.FALL) got += "apple"
            SpotKind.MUSHROOM -> if (season != Season.WINTER) got += "wild_mushroom"
            SpotKind.ROCK -> got += "stone"
            SpotKind.SLOT -> {}
        }
    }
    // Quarry and the floors already reached, with nodes the pickaxe can break.
    val pick = toolTier(Tool.PICKAXE)
    val nodes = HashSet<String>()
    if (quarryOpen) data.mine.quarry.getOrNull(state.mine.quarryLevel - 1)?.weights?.keys?.let { nodes += it }
    for (b in data.mine.bands) if (b.floors.first <= state.mine.deepestFloor) nodes += b.weights.keys.filter { (data.mine.minFloor[it] ?: 0) <= state.mine.deepestFloor }
    for (n in nodes) data.mine.nodes[n]?.takeIf { it.minPick <= pick }?.let { got += it.drops.keys }
    // Machines that are built, closed over their inputs.
    var grew = true
    while (grew) {
        grew = false
        for (b in data.buildings.values) {
            if (!isBuilt(b.id)) continue
            for (r in recipesFor(b.id)) {
                if (r.unlockLevel > state.level || r.id.startsWith("tool_")) continue
                if (!r.inputs.keys.all { it == "#crop" || it in got }) continue
                for (o in r.outputs.keys) if (!o.startsWith("@") && got.add(o)) grew = true
            }
        }
    }
    return got
}
