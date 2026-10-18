package com.ditherfarm.game

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MachinesForestTest {
    private val data = WorldTest.loadData()
    private fun fresh() = World.newGame(data, 99)
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
        assertEquals(wood - 3, w.count("wood"))
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

    @Test fun forestOpensAtLevelTwoWithMatureTrees() {
        val w = fresh()
        assertFalse(w.zoneOpen("A"))
        w.gainXp(40)
        assertTrue(w.zoneOpen("A"))
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
        assertTrue(swings in 3..5)
        assertEquals(energy - SWING_ENERGY * swings, w.state.energy, 0.5)
        assertTrue(w.count("wood") >= 22)
        assertNotNull("stump needs a stone axe", w.chopBlocker(pine))
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
        w.realMinutes(30.0) // spring x0.8 → 24 minutes to mature
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
}
