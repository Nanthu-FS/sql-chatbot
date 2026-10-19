package com.ditherfarm.game

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MachinesForestTest {
    private val data = WorldTest.loadData()
    private fun fresh() = World.newGame(data, 99).apply { knowEverything() }
    private fun World.realMinutes(m: Double) = advance(m * 60)

    @Test fun everyReferencedItemExists() {
        val missing = HashSet<String>()
        fun check(id: String) { if (!id.startsWith("@") && !id.startsWith("#") && id != "coins" && id !in data.items) missing += id }
        data.recipes.values.forEach { r -> (r.inputs.keys + r.outputs.keys).forEach(::check) }
        data.buildings.values.forEach { b -> b.cost.keys.forEach(::check); b.recipes.forEach { assertNotNull("recipe $it", data.recipes[it]) } }
        data.trees.values.forEach { t -> check(t.seed); t.extras.forEach { check(it.first) } }
        data.zones.forEach { z -> z.cost.keys.forEach(::check) }
        data.villagers.forEach { v -> v.favorites.forEach(::check) }
        assertTrue("missing items: $missing", missing.isEmpty())
    }

    @Test fun workbenchIsPrebuiltAndCraftsPlanks() {
        val w = fresh()
        assertTrue(w.isBuilt("workbench"))
        val wood = w.count("wood")
        assertTrue(w.enqueue("wb_plank"))
        assertEquals(wood - data.recipes.getValue("wb_plank").inputs.getValue("wood"), w.count("wood"))
        assertFalse("queue of 1 is full", w.enqueue("wb_plank"))
        w.realMinutes(0.6)
        assertEquals(1, w.trayCount("workbench"))
        val planks = w.count("plank")
        assertEquals(1, w.collect("workbench"))
        assertEquals(planks + 1, w.count("plank"))
    }

    @Test fun buildingNeedsLevelAndMaterialsThenQueuesBackToBack() {
        val w = fresh()
        assertNotNull(w.buildBlocker("windmill")) // level 2
        w.gainXp(40)
        w.add("wheat", 30); w.add("wood", 20)
        assertTrue(w.build("windmill"))
        assertTrue(w.enqueue("flour"))
        assertTrue(w.enqueue("flour"))
        w.realMinutes(4.1)
        assertEquals(1, w.trayCount("windmill"))
        w.realMinutes(4.1)
        assertEquals(2, w.trayCount("windmill"))
    }

    @Test fun machinesFinishWhileOffline() {
        val w = fresh()
        w.gainXp(40); w.add("wheat", 30); w.add("wood", 20); w.build("windmill")
        w.enqueue("flour"); w.enqueue("flour"); w.enqueue("flour")
        w.catchUp(3600.0)
        assertEquals(3, w.trayCount("windmill"))
    }

    @Test fun furnaceBurnsCoal() {
        val w = fresh()
        w.state.level = 6
        w.add("stone", 50); w.add("clay", 20); w.add("plank", 10); w.add("coal", 20); w.add("copper_ore", 9); w.state.coins = 5000
        assertTrue(w.build("furnace"))
        assertFalse("no fuel yet", w.enqueue("copper_bar"))
        assertEquals(10, w.addFuel("furnace", 10))
        assertTrue(w.enqueue("copper_bar"))
        assertEquals(9.5, w.building("furnace").fuel, 1e-9)
    }

    @Test fun toolUpgradeAppliesWhenDone() {
        val w = fresh()
        w.state.level = 4
        w.add("wood", 20); w.add("stone", 30)
        assertEquals(0, w.toolTier(Tool.AXE))
        assertTrue(w.enqueue("tool_axe_1"))
        w.realMinutes(2.1)
        assertEquals(1, w.toolTier(Tool.AXE))
        assertEquals("tool_axe_2", w.nextToolRecipe(Tool.AXE)?.id)
    }

    @Test fun forestIsOpenFromTheStartWithMatureTrees() {
        val w = fresh()
        assertTrue(w.zoneOpen("A"))
        assertFalse("the next grove waits for chopping", w.zoneOpen("B"))
        val pine = w.forestSpots.first { it.native == "pine" }
        assertEquals(TreeStage.MATURE, w.treeStage(pine.index))
    }

    @Test fun choppingAPineCostsEnergyAndFellsIt() {
        val w = fresh()
        w.gainXp(40)
        val pine = w.forestSpots.first { it.native == "pine" }.index
        val energy = w.state.energy
        var swings = 0
        while (w.treeStage(pine) == TreeStage.MATURE && swings < 10) { assertTrue(w.chop(pine)); swings++ }
        assertEquals(TreeStage.STUMP, w.treeStage(pine))
        assertTrue("swings: $swings", swings in 2..3)
        assertEquals(energy - SWING_ENERGY * swings, w.state.energy, 0.5)
        assertTrue(w.count("wood") >= 22)
        assertNotNull("stump needs a stone axe", w.chopBlocker(pine))
    }

    @Test fun stumpSproutsItsTreeAgainWithinMinutes() {
        val w = fresh()
        w.gainXp(40)
        val pine = w.forestSpots.first { it.native == "pine" }.index
        while (w.treeStage(pine) == TreeStage.MATURE) assertTrue(w.chop(pine))
        assertEquals(TreeStage.STUMP, w.treeStage(pine))
        w.realMinutes(STUMP_SPROUT_MINUTES - 1)
        assertEquals("still a stump", TreeStage.STUMP, w.treeStage(pine))
        w.realMinutes(1.5)
        assertEquals(TreeStage.SAPLING, w.treeStage(pine))
        w.realMinutes(data.trees.getValue("pine").growMinutes + 0.5)
        assertEquals("a pine is back in about 17 real minutes", TreeStage.MATURE, w.treeStage(pine))
    }

    @Test fun oakNeedsStoneAxe() {
        val w = fresh()
        w.gainXp(40)
        val oak = w.forestSpots.first { it.native == "oak" && it.zone == "A" }.index
        assertEquals("Needs Stone axe", w.chopBlocker(oak))
    }

    @Test fun plantedTreeGrowsUp() {
        val w = fresh()
        w.gainXp(40)
        val pine = w.forestSpots.first { it.native == "pine" }.index
        w.state.tools["axe"] = 1
        while (w.treeStage(pine) != TreeStage.EMPTY) w.chop(pine)
        w.add("pinecone", 1)
        assertTrue(w.plantTree(pine, "pinecone"))
        assertEquals(TreeStage.SAPLING, w.treeStage(pine))
        w.realMinutes(data.trees.getValue("pine").growMinutes * 0.8 + 0.5) // spring x0.8
        assertEquals(TreeStage.MATURE, w.treeStage(pine))
    }

    @Test fun forestAndMachinesSurviveSaveRoundTrip() {
        val w = fresh()
        w.gainXp(40)
        w.chop(w.forestSpots.first { it.native == "pine" }.index)
        w.enqueue("wb_plank")
        val json = w.state.toJson().toString()
        assertEquals(json, GameState.fromJson(JSONObject(json)).toJson().toString())
    }

    private fun World.toLevel(l: Int) { gainXp(data.level(l).totalXp - state.xp); state.energy = 1000.0; state.coins += 100_000 }

    @Test fun loggingHutFellsWhileAway() {
        val w = fresh(); w.toLevel(9)
        w.add("plank", 30); w.add("stone", 10)
        assertTrue(w.build("logging_hut"))
        w.catchUp(3 * 3600.0)
        val wood = w.building("logging_hut").tray["wood"] ?: 0
        assertTrue("hut wood $wood", wood > 0)
        assertTrue("stops near its cap", w.trayCount("logging_hut") < w.hutCap() + 10)
        assertTrue(w.state.stat("fells") > 0)
        assertTrue(w.collect("logging_hut") > 0)
    }

    @Test fun nurseryReplantsFromBarnSeeds() {
        val w = fresh(); w.toLevel(14)
        w.add("plank", 25); w.add("gear", 6)
        assertTrue(w.build("tree_nursery"))
        val empty = w.forestSpots.first { it.kind == SpotKind.TREE && it.native == "pine" && w.zoneOpen(it.zone) }
        w.spot(empty.index).apply { tree = null; stage = TreeStage.EMPTY; since = w.state.minutes }
        w.forestSpots.filter { it.kind == SpotKind.TREE && w.treeStage(it.index) == TreeStage.STUMP }.forEach { w.spot(it.index).apply { tree = null; stage = TreeStage.EMPTY } }
        w.add("pinecone", 1)
        w.advance(11 * 60.0)
        assertEquals(0, w.count("pinecone"))
        assertTrue(w.state.stat("trees_planted") >= 1)
    }

    @Test fun beehiveSmokeOrSting() {
        val w = fresh(); w.toLevel(6)
        val host = w.forestSpots.first { it.kind == SpotKind.TREE && w.zoneOpen(it.zone) && w.treeStage(it.index) == TreeStage.MATURE }
        w.state.forest.hive = host.index; w.state.forest.hiveUntil = w.time.dayIndex + HIVE_DAYS
        w.add("torch", 1)
        assertTrue(w.smokeHive())
        assertEquals(0, w.count("torch"))
        assertTrue(w.count("honey") >= 3)
        assertEquals(-1, w.state.forest.hive)
        // A second hive, chopped without smoke: stings.
        w.state.forest.hive = host.index
        w.state.tools["axe"] = 3
        val energy = w.state.energy
        w.chop(host.index)
        assertEquals(energy - SWING_ENERGY - 10, w.state.energy, 0.01)
        assertEquals(-1, w.state.forest.hive)
    }
}
