package com.ditherfarm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The deep mine (round 6): floors 41-80, crystalite and the Crystal tool tier. */
class DeepMineTest {
    private val data = WorldTest.loadData()

    private fun deep(level: Int = 30) = World.newGame(data, 4).apply {
        knowEverything(); gainXp(data.level(level).totalXp - state.xp)
        state.tools["pickaxe"] = 4; state.mine.repaired = true; events.clear()
    }

    @Test fun theMineGoesDownToEighty() {
        val w = deep()
        assertTrue(w.enterFloor(41))
        assertEquals(5, data.mine.band(41).band)
        assertFalse(w.enterFloor(MINE_BOTTOM + 1))
        assertTrue(w.enterFloor(MINE_BOTTOM))
        assertEquals(-1, w.state.mine.ladder)
        w.state.mine.deepestStop = MINE_BOTTOM
        assertEquals(MINE_BOTTOM, w.elevatorStops().last())
    }

    @Test fun deepGatesNeedLevelsAndTheCrystalPick() {
        val w = deep(level = 25)
        assertEquals("Gate opens at level 26", w.gateBlocker(70))
        val v = deep(level = 27)
        v.state.tools["pickaxe"] = 3
        assertEquals("Needs Crystal pickaxe", v.gateBlocker(70))
        assertNotNull(v.gateBlocker(80))
    }

    @Test fun crystaliteOnlyInTheDeep() {
        val w = deep()
        fun crystalite(floor: Int) = run { w.enterFloor(floor); w.state.mine.cells.count { it.node == "crystalite_vein" } }
        assertEquals(0, (31..39).sumOf { crystalite(it) })
        assertTrue((41..49).sumOf { crystalite(it) } > 0)
    }

    @Test fun crystalToolsAreTierFour() {
        assertEquals("Crystal", data.tools.names[4])
        assertTrue(data.tools.damage[4] > data.tools.damage[3])
        for (t in listOf("pickaxe", "axe", "hoe")) assertEquals(4, data.recipes.getValue("tool_${t}_4").outputs["@tool:$t"])
        assertEquals(1, data.recipes.getValue("crystal_bar").outputs["crystal_bar"])
    }
}
