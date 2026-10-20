package com.ditherfarm.game

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The orchard: bought at level 10, eight trees that fruit every few days in their seasons. */
class OrchardTest {
    private val data = WorldTest.loadData()

    private fun World.toLevel(l: Int) = gainXp(data.level(l).totalXp - state.xp)
    private fun World.days(n: Int) = repeat(n) { advance(Time.REAL_SECONDS_PER_DAY) }

    private fun opened(level: Int = 16) = World.newGame(data, 5).apply {
        knowEverything(); toLevel(level); state.coins = 20_000; add("plank", 20)
        assertTrue(buyOrchard()); events.clear()
    }

    @Test fun buyingNeedsLevelAndMaterials() {
        val w = World.newGame(data, 5)
        assertEquals("Unlocks at level $ORCHARD_LEVEL", w.orchardBlocker())
        w.toLevel(ORCHARD_LEVEL); w.state.coins = 0
        assertNotNull(w.orchardBlocker())
        w.state.coins = 2000; w.add("plank", 10)
        val planks = w.count("plank")
        assertTrue(w.buyOrchard())
        assertEquals(500, w.state.coins)
        assertEquals(planks - 10, w.count("plank"))
        assertEquals("Already yours", w.orchardBlocker())
    }

    @Test fun cherryFruitsAfterADayThenEveryTwoDays() {
        val w = opened()
        assertEquals(Season.SPRING, w.time.season)
        val coins = w.state.coins
        assertTrue(w.plantOrchardTree(0, "cherry"))
        assertEquals(coins - 300, w.state.coins)
        assertFalse("spot taken", w.plantOrchardTree(0, "apple"))
        val t = w.orchard.trees[0]!!
        assertEquals(0, t.fruit)
        w.days(1)
        assertEquals(6, t.fruit)
        assertEquals("Fruit ready", w.treeStatus(t))
        val before = w.count("cherry")
        assertTrue(w.pickFruit(0))
        assertEquals(before + 6, w.count("cherry"))
        assertEquals("Fruit in 2 days", w.treeStatus(t))
        w.days(1)
        assertEquals("fruit waits two days", 0, t.fruit)
        w.days(1)
        assertEquals(6, t.fruit)
        w.days(3)
        assertEquals("fruit does not stack", 6, t.fruit)
    }

    @Test fun treesRestOutOfSeasonAndUnlockByLevel() {
        val w = opened(level = 12)
        assertEquals("Unlocks at level 14", w.treeBlocker(1, "peach"))
        assertTrue(w.plantOrchardTree(1, "apple"))
        val t = w.orchard.trees[1]!!
        w.days(3)
        assertEquals("apples rest in spring", 0, t.fruit)
        assertEquals("Rests until Summer", w.treeStatus(t))
        assertFalse("apple" in w.orchardFruitNow())
        w.days(Time.DAYS_PER_SEASON)
        assertEquals(Season.SUMMER, w.time.season)
        assertEquals(8, t.fruit)
        assertTrue("apple" in w.orchardFruitNow())
    }

    @Test fun digUpFreesTheSpot() {
        val w = opened()
        assertTrue(w.plantOrchardTree(2, "orange"))
        assertTrue(w.digUpTree(2))
        assertEquals(null, w.orchard.trees[2])
        assertTrue(w.plantOrchardTree(2, "peach"))
    }

    @Test fun savesTheOrchard() {
        val w = opened()
        w.plantOrchardTree(3, "peach"); w.plantOrchardTree(7, "cherry")
        w.days(2)
        val back = GameState.fromJson(JSONObject(w.state.toJson().toString())).orchard
        assertTrue(back.open)
        assertEquals("peach", back.trees[3]!!.kind)
        assertEquals(w.orchard.trees[7]!!.fruit, back.trees[7]!!.fruit)
        assertEquals(w.orchard.trees[7]!!.nextDay, back.trees[7]!!.nextDay)
        assertEquals(null, back.trees[0])
    }

    @Test fun fruitGoesIntoJuiceAndJam() {
        for (r in listOf("apple_juice", "orange_juice", "cherry_jam", "peach_jam")) {
            val rec = data.recipes.getValue(r)
            assertTrue(r, data.buildings.getValue(rec.building).recipes.contains(r))
            assertTrue(r, data.item(r).price > rec.inputs.entries.sumOf { (k, v) -> data.item(k).price * v })
        }
    }
}
