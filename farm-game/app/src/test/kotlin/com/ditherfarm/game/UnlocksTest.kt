package com.ditherfarm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlocksTest {
    private val data = WorldTest.loadData()
    private fun fresh() = World.newGame(data, 51).apply { state.coins = 100_000; events.clear() }

    @Test fun stoneAxeComesFromChoppingNotLevels() {
        val w = fresh()
        val r = data.recipes.getValue("tool_axe_1")
        assertEquals(1, r.unlockLevel)
        w.building("workbench").built = true
        w.add("wood", 20); w.add("stone", 20)
        assertTrue(w.recipeBlocker(r)!!.startsWith("Chop down 5 trees"))
        w.state.stats["fells"] = 5
        assertNull(w.recipeBlocker(r))
    }

    @Test fun forestRocksOpenTheQuarry() {
        val w = fresh()
        w.advance(1.0) // first dawn opens the Edge Grove
        assertFalse(w.quarryOpen)
        val rocks = w.forestSpots.filter { it.kind == SpotKind.ROCK && w.zoneOpen(it.zone) }
        assertTrue("zone A has loose rocks", rocks.size >= 4)
        for (sp in rocks) assertTrue(w.forage(sp.index))
        assertTrue(w.count("stone") >= 8)
        assertTrue(w.quarryOpen)
        w.advance(1.0)
        assertTrue("the quarry fills once open", w.mine.quarry.isNotEmpty())
        assertTrue(w.events.any { it is WorldEvent.FeatDone && it.key == "quarry" })
    }

    @Test fun machinesNeedTheirBlueprintAndLevel() {
        val w = fresh()
        w.gainXp(data.level(3).totalXp)
        w.add("wood", 50); w.add("wheat", 20)
        assertTrue(w.buildBlocker("windmill")!!.startsWith("Blueprint"))
        w.state.stats["harvest_wheat"] = 10
        assertNull(w.buildBlocker("windmill"))
        w.state.stats["made_flour"] = 3
        assertEquals("the bakery still needs its level", "Unlocks at level 4", w.buildBlocker("bakery_oven"))
    }

    @Test fun notebookWalksThroughQuests() {
        val w = fresh()
        assertEquals("plant", w.quest!!.id)
        assertFalse(w.questDone)
        for (i in 0 until 6) { w.plot(i).clear(); w.plant(i, "wheat") }
        assertTrue(w.questDone)
        val coins = w.state.coins
        assertTrue(w.claimQuest())
        assertTrue(w.state.coins > coins)
        assertEquals("water", w.quest!!.id)
        assertEquals("counts from when the quest starts", 0L, w.questProgress().first)
        assertEquals(Pointer.FIELD, w.pointer)
    }

    @Test fun ordersOnlyAskForWhatCanBeHadNow() {
        val w = fresh()
        // A lucky find (hardwood from a nest, say) must not turn into orders before the axe can fell oaks.
        w.add("hardwood", 3); w.add("iron_ore", 2)
        val can = w.obtainableNow()
        assertTrue("wheat" in can)
        assertFalse("hardwood" in can)
        assertFalse("iron_ore" in can)
        repeat(30) {
            val o = w.generateOrder() ?: return@repeat
            assertTrue(o.lines.all { it.item in can })
        }
    }

    @Test fun oldSavesSkipTheNotebook() {
        val w = fresh()
        w.gainXp(data.level(8).totalXp)
        w.state.version = 2
        val back = World(data, GameState.fromJson(w.state.toJson()))
        assertNull(back.quest)
    }
}
