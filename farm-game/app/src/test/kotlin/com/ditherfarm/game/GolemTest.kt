package com.ditherfarm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Crystal Golem: break its armour, back off from the slam, strike its heart; three phases. */
class GolemTest {
    private val data = WorldTest.loadData()

    private fun hall() = World.newGame(data, 21).apply {
        knowEverything(); gainXp(data.level(30).totalXp - state.xp)
        state.tools["pickaxe"] = 4; state.mine.repaired = true; state.energy = 5000.0
        state.mine.gatesOpened += (10..80 step 10)
        assertTrue(enterFloor(MINE_BOTTOM)); events.clear()
    }

    private fun World.run(seconds: Double) = repeat((seconds / 0.1).toInt()) { tickGolem(0.1) }
    private fun World.breakArmour() { val f = golem; f.crystals.indices.forEach { k -> while (f.crystals[k] > 0) hitGolemCrystal(k) } }
    /** Breaks the armour, waits out the slam without tapping, and strikes the heart three times. */
    private fun World.winPhase() {
        breakArmour()
        run(golem.chargeSeconds + GOLEM_WINDUP + 0.3)
        assertEquals(GolemStage.STUNNED, golem.stage)
        repeat(GOLEM_CORE_HITS) { hitGolemCore() }
    }

    @Test fun theHallIsOpenAndLit() {
        val w = hall()
        assertTrue(w.golemHere)
        assertFalse(w.state.mine.dark)
        assertEquals(null, w.state.mine.cells[golemCell(w.state.mine.grid)].node)
        assertEquals(3, w.golem.crystals.size)
        assertTrue(w.state.mine.critters.isEmpty())
    }

    @Test fun aSlamWithArmourOnHurtsAndRegrows() {
        val w = hall()
        val f = w.golem
        while (f.crystals[0] > 0) w.hitGolemCrystal(0)
        val energy = w.state.energy
        w.run(f.chargeSeconds + GOLEM_WINDUP + 0.3)
        assertEquals(energy - GOLEM_SLAM, w.state.energy, 1e-6)
        assertEquals(GolemStage.ARMORED, f.stage)
        assertEquals(3, f.armourLeft)
    }

    @Test fun tappingDuringTheWindupHurts() {
        val w = hall()
        w.breakArmour()
        w.run(w.golem.chargeSeconds + 0.2)
        assertEquals(GolemStage.WINDUP, w.golem.stage)
        assertFalse(w.hitGolemCore())
        val energy = w.state.energy
        w.run(GOLEM_WINDUP + 0.2)
        assertEquals(energy - GOLEM_SMACK, w.state.energy, 1e-6)
        assertEquals(GolemStage.STUNNED, w.golem.stage)
    }

    @Test fun threePhasesBeatTheGolem() {
        val w = hall()
        w.winPhase()
        assertEquals(2, w.golem.phase)
        assertEquals(4, w.golem.crystals.size)
        w.winPhase()
        assertEquals(5, w.golem.crystals.size)
        val coins = w.state.coins
        w.winPhase()
        assertTrue(w.state.mine.golemDefeated)
        assertFalse(w.golemHere)
        assertTrue(GOLEM_RELIC in w.state.relics)
        assertEquals(coins + GOLEM_REWARD.getValue("coins"), w.state.coins)
        assertTrue(w.enterFloor(MINE_BOTTOM - 5) && w.enterFloor(MINE_BOTTOM))
        assertFalse("no rematch", w.golemHere)
    }

    @Test fun theStunWearsOffAndArmourComesBack() {
        val w = hall()
        w.breakArmour()
        w.run(w.golem.chargeSeconds + GOLEM_WINDUP + 0.3)
        w.hitGolemCore()
        w.run(GOLEM_STUN + 0.2)
        assertEquals(GolemStage.ARMORED, w.golem.stage)
        assertEquals(3, w.golem.armourLeft)
        assertEquals(1, w.golem.phase)
    }
}
