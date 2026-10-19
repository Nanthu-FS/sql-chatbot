package com.ditherfarm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValleyTest {
    private val data = WorldTest.loadData()
    private fun fresh(level: Int = 6) = World.newGame(data, 61).apply {
        knowEverything()
        gainXp(data.level(level).totalXp - state.xp)
        state.coins = 1_000_000
        state.inventory.clear()
        events.clear()
    }

    private fun World.fill(t: TierDef) { for (b in t.bundles) for ((k, n) in b.items) add(k, n) }

    @Test fun threeTiersAfterTheHuts() {
        assertEquals(listOf(1, 2, 3), data.valley.map { it.tier })
        assertEquals(4, VILLAGE_TIERS.size)
    }

    @Test fun donationsAddUpAndRebuildTheVillage() {
        val w = fresh()
        val t = w.nextTier!!
        assertNotNull(w.rebuildBlocker())
        // A little at a time.
        val b = t.bundles[0]
        val (k, n) = b.items.entries.first()
        w.add(k, n / 2)
        assertEquals(n / 2, w.donate(b.id))
        assertFalse(w.bundleDone(b))
        w.fill(t)
        for (bd in t.bundles) w.donate(bd.id)
        assertTrue(t.bundles.all { w.bundleDone(it) })
        assertEquals("Needs ${t.coins} more coins", w.rebuildBlocker())
        w.donateCoins()
        assertNull(w.rebuildBlocker())
        val slots = w.state.orders.size
        assertTrue(w.rebuild())
        assertEquals(1, w.villageTier)
        assertTrue("the Blacksmith opens", w.isBuilt("blacksmith"))
        assertEquals("one more order slot", slots + 1, w.state.orders.size)
        assertTrue(w.state.donated.isEmpty())
        assertTrue(w.villagersUnlocked().any { it.id == "rook" })
    }

    @Test fun tiersWaitForTheirLevel() {
        val w = fresh(3)
        assertTrue(w.donateBlocker()!!.contains("level 5"))
        assertEquals(0, w.donate(w.nextTier!!.bundles[0].id))
    }

    @Test fun ironToolsAtTheBlacksmithGoldAfterBrick() {
        val w = fresh(12)
        val iron = data.recipes.getValue("tool_axe_2")
        val gold = data.recipes.getValue("tool_axe_3")
        assertEquals("blacksmith", iron.building)
        assertTrue(w.recipeBlocker(iron)!!.contains("Blacksmith"))
        w.state.villageTier = 1; w.building("blacksmith").built = true
        w.state.tools["axe"] = 1
        for ((k, v) in iron.inputs) w.add(k, v)
        assertNull(w.recipeBlocker(iron))
        w.gainXp(data.level(18).totalXp - w.state.xp)
        w.state.tools["axe"] = 2
        for ((k, v) in gold.inputs) w.add(k, v)
        assertEquals("Gold work comes with the brick village", w.recipeBlocker(gold))
        w.state.villageTier = 2
        assertNull(w.recipeBlocker(gold))
    }

    @Test fun perksFromTheTiers() {
        val w = fresh(16)
        val price = w.blockPrice(3)
        val seed = w.seedPrice("wheat")
        w.state.villageTier = 1
        val had = w.count(data.crop("wheat").seedItem)
        assertTrue(w.buySeedPack("wheat"))
        assertEquals(had + SEED_PACK, w.count(data.crop("wheat").seedItem))
        assertTrue(w.seedPackPrice("wheat") < seed * SEED_PACK)
        w.state.villageTier = 2
        assertTrue("the Carpenter takes 15% off", w.blockPrice(3) < price)
        w.state.villageTier = 3
        assertEquals(TRUCK_CAP * 3 / 2, w.truckCap)
        w.add("bread", 1)
        val withCafe = w.unitPrice("bread")
        w.state.villageTier = 2
        assertTrue("the Cafe pays more for dishes", withCafe > w.unitPrice("bread"))
    }

    @Test fun villageSurvivesSave() {
        val w = fresh()
        w.add("stone", 10); w.donate("stone")
        w.state.villageTier = 1
        val back = GameState.fromJson(w.state.toJson())
        assertEquals(1, back.villageTier)
        assertEquals(10, back.donated["stone:stone"])
    }
}
