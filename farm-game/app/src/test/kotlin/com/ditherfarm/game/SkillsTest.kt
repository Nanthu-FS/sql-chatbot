package com.ditherfarm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillsTest {
    private val data = WorldTest.loadData()
    private fun fresh(level: Int = 10) = World.newGame(data, 41).apply {
        gainXp(data.level(level).totalXp - state.xp)
        state.coins = 1_000_000
        events.clear()
    }

    @Test fun treeHasFiveBranchesOfFourWithIcons() {
        assertEquals(5, BRANCHES.size)
        assertTrue(BRANCHES.all { it.skills.size == 4 })
        assertEquals(20, SKILLS.size)
    }

    @Test fun pointsComeFromLevelsAndSkillsUnlockInOrder() {
        val w = fresh(4)
        assertEquals(3, w.skillPoints)
        assertNotNull("needs Quick Sprout first", w.skillBlocker("bountiful"))
        assertTrue(w.learn("quick_sprout"))
        assertTrue(w.learn("bountiful"))
        assertTrue(w.learn("bountiful"))
        assertEquals(0, w.skillPoints)
        assertFalse(w.learn("bountiful"))
        assertEquals(2, w.rank("bountiful"))
    }

    @Test fun ranksStopAtTheMax() {
        val w = fresh(20)
        repeat(3) { assertTrue(w.learn("overclock")) }
        assertEquals("Already mastered", w.skillBlocker("overclock"))
    }

    @Test fun respecReturnsPointsForCoins() {
        val w = fresh(10)
        w.learn("haggler"); w.learn("bulk_buyer")
        val coins = w.state.coins
        assertTrue(w.respec())
        assertEquals(coins - w.respecCost(), w.state.coins)
        assertEquals(9, w.skillPoints)
        assertFalse("nothing to reset", w.respec())
    }

    @Test fun traderSkillsChangePrices() {
        val w = fresh(10)
        w.add("wheat", 10)
        val sell = w.unitPrice("wheat") * 10
        val seed = w.seedPrice("pumpkin")
        repeat(3) { w.learn("haggler") }
        repeat(3) { w.learn("bulk_buyer") }
        assertTrue(w.quoteSale("wheat", 10) >= sell)
        assertTrue(w.unitPrice("corn") > 0)
        assertTrue(w.seedPrice("pumpkin") < seed)
    }

    @Test fun quickSproutGrowsFaster() {
        val a = fresh(10)
        val b = fresh(10)
        repeat(3) { b.learn("quick_sprout") }
        for (w in listOf(a, b)) w.plot(0).apply { clear(); crop = "potato"; growth = 0.0; watered = true }
        a.advance(600.0); b.advance(600.0)
        assertTrue(b.plot(0).growth > a.plot(0).growth)
    }

    @Test fun tinkererSkillsChangeMachines() {
        val w = fresh(20)
        val r = data.recipes.getValue("wb_plank")
        val minutes = w.jobMinutes(r)
        val queue = w.queueSize("workbench")
        repeat(3) { w.learn("overclock") }
        repeat(3) { w.learn("fuel_saver") }
        repeat(2) { w.learn("extra_slot") }
        assertTrue(w.jobMinutes(r) < minutes)
        assertEquals(queue + 2, w.queueSize("workbench"))
    }

    @Test fun skillsSurviveSave() {
        val w = fresh(10)
        w.learn("sharp_edge"); w.learn("timber")
        w.state.luckUntil = 123.0
        val back = GameState.fromJson(w.state.toJson())
        assertEquals(1, back.skills["timber"])
        assertEquals(123.0, back.luckUntil, 0.0)
    }

    @Test fun tonicsAreCraftedAndUsed() {
        val w = fresh(12)
        for (id in TONICS) assertTrue("$id has a recipe", data.recipes.values.any { r -> r.building == "workbench" && id in r.outputs })
        // Growth Tonic: every growing crop jumps two hours.
        w.plot(0).apply { clear(); crop = "pumpkin"; growth = 0.0 }
        w.add("growth_tonic", 1)
        assertTrue(w.useTonic("growth_tonic"))
        assertEquals(TONIC_GROWTH_MINUTES, w.plot(0).growth, 0.001)
        assertEquals(0, w.count("growth_tonic"))
        // Rain Totem waters everything planted.
        w.plot(1).apply { clear(); crop = "wheat"; growth = 0.0; watered = false }
        w.add("rain_totem", 1)
        assertTrue(w.useTonic("rain_totem"))
        assertTrue(w.plot(1).watered)
        // Energy Brew needs room for energy.
        w.state.energy = 0.0
        w.add("energy_brew", 1)
        assertTrue(w.useTonic("energy_brew"))
        assertEquals(TONIC_ENERGY.toDouble(), w.state.energy, 0.001)
        w.state.energy = w.maxEnergy.toDouble()
        w.add("energy_brew", 1)
        assertNotNull(w.useBlocker("energy_brew"))
        // Lucky Charm lasts ten minutes.
        w.add("lucky_charm", 1)
        assertTrue(w.useTonic("lucky_charm"))
        assertTrue(w.lucky)
        w.advance(LUCK_SECONDS + 5)
        assertFalse(w.lucky)
        assertNull(w.skillBlocker("quick_sprout"))
    }
}
