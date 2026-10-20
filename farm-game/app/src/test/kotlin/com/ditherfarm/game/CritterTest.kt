package com.ditherfarm.game

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deep-mine critters: they roam, bite when close, fall to a few taps, and can knock the player out. */
class CritterTest {
    private val data = WorldTest.loadData()

    private fun deep() = World.newGame(data, 8).apply {
        knowEverything(); gainXp(data.level(30).totalXp - state.xp)
        state.tools["pickaxe"] = 4; state.mine.repaired = true; state.energy = 300.0; events.clear()
    }

    @Test fun onlyDeepFloorsHaveCritters() {
        val w = deep()
        for (f in listOf(5, 25, 39)) { w.enterFloor(f); assertEquals(0, w.state.mine.critters.size) }
        val counts = (41..79).map { w.enterFloor(it); w.state.mine.critters.size }
        assertTrue(counts.all { it >= 1 })
        assertTrue("more critters deeper", counts.takeLast(9).average() > counts.take(9).average())
    }

    @Test fun aCritterNextToYouBites() {
        val w = deep()
        w.enterFloor(45)
        val m = w.state.mine
        val n = m.grid
        val entry = entryCell(n)
        m.critters.clear()
        val spot = entry - n
        m.cells[spot].node = null
        m.critters += Critter(CritterKind.SLIME, spot)
        val energy = w.state.energy
        repeat(80) { w.tickCritters(0.1) }
        assertTrue(w.state.energy < energy)
        assertTrue(w.events.any { it is WorldEvent.CritterBit })
    }

    @Test fun tapsBeatACritterForLoot() {
        val w = deep()
        w.enterFloor(45)
        val m = w.state.mine
        m.critters.clear()
        m.critters += Critter(CritterKind.BEETLE, entryCell(m.grid) - 3 * m.grid)
        val xp = w.state.xp
        var swings = 0
        while (m.critters.isNotEmpty() && swings < 10) { assertTrue(w.hitCritter(0)); swings++ }
        assertTrue("a crystal pick beats a beetle in two or fewer swings", swings <= 2)
        assertTrue(w.state.xp > xp)
        assertTrue(w.events.any { it is WorldEvent.CritterDefeated && it.loot.isNotEmpty() })
    }

    @Test fun knockedOutLosesHalfThisTrip() {
        val w = deep()
        w.state.mine.deepestStop = 45
        w.add("diamond", 3)
        w.enterFloor(45)
        w.enterFloor(47)
        w.add("diamond", 10); w.add("gold_ore", 5)
        w.state.energy = 0.0
        w.knockOut()
        assertEquals(45, w.state.mine.floor)
        assertEquals("kept the 3 from home and half of the 10", 3 + 5, w.count("diamond"))
        assertEquals(3, w.count("gold_ore"))
        assertTrue(w.state.energy >= w.maxEnergy * 0.25 - 1e-6)
    }

    @Test fun crittersAreSaved() {
        val w = deep()
        w.enterFloor(66)
        val back = GameState.fromJson(JSONObject(w.state.toJson().toString())).mine
        assertEquals(w.state.mine.critters.map { it.kind to it.cell }, back.critters.map { it.kind to it.cell })
    }
}
