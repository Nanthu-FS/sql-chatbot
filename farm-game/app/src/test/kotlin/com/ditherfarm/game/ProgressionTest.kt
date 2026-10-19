package com.ditherfarm.game

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Static progression audit: works out the earliest player level at which every item can
 * be obtained (crops, forest, quarry, mine floors, geodes, machines, merchant, chests),
 * then checks that everything the game asks for (building costs, recipe inputs, tool
 * upgrades, gate items) is obtainable by the level it unlocks.
 */
class ProgressionTest {
    private val data = WorldTest.loadData()
    private val earliest = HashMap<String, Int>()
    private var changed = false

    private fun offer(id: String, level: Int) {
        val l = maxOf(1, level)
        if (l < (earliest[id] ?: Int.MAX_VALUE)) { earliest[id] = l; changed = true }
    }

    private fun at(id: String) = earliest[id] ?: Int.MAX_VALUE
    private fun tool(tool: String, tier: Int) = if (tier <= 0) 1 else at("@tool:$tool:$tier")
    private fun need(items: Collection<String>) = items.filter { it != "coins" && it != "#crop" }.maxOfOrNull { at(it) } ?: 1

    private fun solve() {
        // Starting kit and crops.
        listOf("wheat", "carrot", "wood", "stone", "fibre", "plank", "basic_fertilizer").forEach { offer(it, 1) }
        // Side drops: harvest scraps, withered plots, wild beehives (L5), stumps.
        offer("plant_scraps", 1); offer("withered_crop", 1); offer("wildflower_seed", 5); offer("old_coin", 2)
        offer("#crop", 1)
        for (c in data.crops.values) { offer(c.id, c.unlockLevel); offer(c.seedItem, c.unlockLevel) }
        val zoneLevel = data.zones.associate { it.id to it.unlockLevel }
        val mine = data.mine
        do {
            changed = false
            // Forest: trees by zone and axe tier, foraging, branches, bird nests.
            for (z in data.zones) {
                val zl = maxOf(z.unlockLevel, need(z.cost.keys))
                for (treeId in z.trees.keys) {
                    val t = data.trees[treeId] ?: continue
                    val l = maxOf(zl, tool("axe", t.minAxe))
                    offer("wood", l)
                    if (t.hardwood.last > 0) offer("hardwood", l)
                    t.extras.forEach { (item, _) -> offer(item, l) }
                    offer(t.seed, l)
                    offer("feather", l); offer("golden_feather", l)
                }
                if (z.bushes > 0) offer("blackberry", zl)
                if (z.apples > 0) offer("apple", zl)
                if (z.mushrooms > 0) { offer("wild_mushroom", zl); offer("chanterelle", zl); offer("glowcap", zl) }
                offer("resin", zl)
            }
            // Quarry levels (need their upgrade cost).
            mine.quarry.forEach { q ->
                val l = maxOf(q.unlockLevel, need(q.cost.keys))
                q.weights.keys.forEach { node -> mine.nodes[node]?.let { n -> val nl = maxOf(l, tool("pickaxe", n.minPick)); (n.drops.keys + n.bonus.keys).forEach { offer(it, nl) } } }
            }
            // Mine floors: repair, gates, bands, minimum floors.
            val repair = maxOf(mine.repairLevel, need(mine.repairCost.keys))
            for (b in mine.bands) {
                var l = repair
                for (g in mine.gates) if (g.floor <= b.floors.first && g.floor > 0) {
                    l = maxOf(l, g.level, tool("pickaxe", g.pick), g.item?.let { at(it) } ?: 1)
                    g.chest.keys.filter { it != "coins" }.forEach { offer(it, l) }
                }
                for (node in b.weights.keys + listOf("glow_mushroom", "crate")) {
                    val n = mine.nodes[node] ?: continue
                    val nl = maxOf(l, tool("pickaxe", n.minPick))
                    (n.drops.keys + n.bonus.keys).forEach { offer(it, nl) }
                    if (node == "geode_rock") offer(b.geode, nl)
                    if (node == "crate") offer("torch", nl)
                }
            }
            offer("cave_mushroom", repair)
            // Geodes at the Anvil.
            val anvil = data.buildings["geode_anvil"]?.let { maxOf(it.unlockLevel, need(it.cost.keys)) } ?: Int.MAX_VALUE
            for ((geode, g) in mine.geodes) {
                val l = maxOf(anvil, at(geode))
                g.ores.forEach { offer(it.first, l) }; g.gems.forEach { offer(it.first, l) }
                mine.minerals[geode]?.forEach { offer(it, l) }
                if (g.prismatic > 0) offer("prismatic_shard", l)
            }
            // Machines and the Workbench.
            for (r in data.recipes.values) {
                val b = data.buildings[r.building] ?: continue
                val bl = if (b.prebuilt) b.unlockLevel else maxOf(b.unlockLevel, need(b.cost.keys))
                val l = maxOf(bl, r.unlockLevel, need(r.inputs.keys))
                for ((k, v) in r.outputs) {
                    if (k.startsWith("@tool:")) offer("@tool:${k.removePrefix("@tool:")}:$v", l) else offer(k, l)
                }
            }
            // Traveling merchant (level 10).
            listOf("rope_ladder", "torch", "quality_fertilizer", "hardwood", "geode", "apple_sapling").forEach { offer(it, MERCHANT_LEVEL) }
            offer("golden_acorn", 20)
        } while (changed)
        // Never a source: nothing to fix if unused.
        zoneLevel.size
    }

    @Test fun everythingNeededIsObtainableInTime() {
        solve()
        val problems = ArrayList<String>()
        val slack = 2
        fun check(what: String, items: Collection<String>, level: Int) {
            for (it in items) {
                if (it == "coins" || it == "#crop") continue
                val e = at(it)
                if (e == Int.MAX_VALUE) problems += "$what needs $it which can never be obtained"
                else if (e > level + slack) problems += "$what (L$level) needs $it, first obtainable at L$e"
            }
        }
        for (b in data.buildings.values) if (!b.prebuilt) check("building ${b.id}", b.cost.keys, b.unlockLevel)
        for (r in data.recipes.values) {
            val b = data.buildings[r.building] ?: continue
            check("recipe ${r.id}", r.inputs.keys, maxOf(r.unlockLevel, b.unlockLevel))
        }
        for (z in data.zones) check("zone ${z.id}", z.cost.keys, z.unlockLevel)
        for (t in data.valley) for (b in t.bundles) check("village tier ${t.tier} bundle ${b.id}", b.items.keys, t.level)
        for (q in data.mine.quarry) check("quarry ${q.level}", q.cost.keys, q.unlockLevel)
        for (g in data.mine.gates) g.item?.let { check("gate ${g.floor}", listOf(it), g.level) }
        for (t in Tool.entries) for (tier in 1..3) {
            val r = data.recipes["tool_${t.name.lowercase()}_$tier"] ?: continue
            check("tool ${t.name} $tier", r.inputs.keys, r.unlockLevel)
        }
        // Items no source can ever produce (for the collection book).
        // Bird nests open on the spot and are never stored.
        val unobtainable = data.items.keys.filter { at(it) == Int.MAX_VALUE && it != "bird_nest" }
        if (unobtainable.isNotEmpty()) problems += "never obtainable: $unobtainable"
        println("PROGRESSION: earliest levels\n" + earliest.entries.filter { !it.key.startsWith("#") }.sortedBy { it.value }.joinToString("\n") { "  L${it.value} ${it.key}" })
        println("PROGRESSION: never obtainable: $unobtainable")
        println("PROGRESSION: problems:\n" + problems.joinToString("\n") { "  $it" })
        assertTrue("progression problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}
