package com.ditherfarm.game

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Treasure rooms and relics in the deep mine. */
class RelicsTest {
    private val data = WorldTest.loadData()

    private fun deep() = World.newGame(data, 12).apply {
        knowEverything(); gainXp(data.level(30).totalXp - state.xp)
        state.tools["pickaxe"] = 4; state.mine.repaired = true; state.energy = 5000.0; events.clear()
    }

    /** Visits deep floors until one has a treasure room; returns the chest cell. */
    private fun World.findRoom(): Int {
        repeat(200) { k ->
            enterFloor(41 + k % 39)
            state.mine.cells.indexOfFirst { it.node == "treasure_chest" }.takeIf { it >= 0 }?.let { return it }
        }
        error("no treasure room in 200 floors")
    }

    @Test fun aTreasureRoomIsAChestInSealedWalls() {
        val w = deep()
        val c = w.findRoom()
        val n = w.state.mine.grid
        for (dr in -1..1) for (dc in -1..1) if (dr != 0 || dc != 0) assertEquals("sealed_wall", w.state.mine.cells[c + dr * n + dc].node)
        repeat(20) { w.mineFloor(c - 1) }
        val coins = w.state.coins
        w.mineFloor(c)
        assertEquals(null, w.state.mine.cells[c].node)
        assertTrue(w.state.coins >= coins + 300)
        assertTrue(w.events.any { it is WorldEvent.TreasureOpened })
    }

    @Test fun relicRocksGiveRelicsButNeverTheGolemHeart() {
        val w = deep()
        repeat(12) { w.findRelic(Rng(it.toLong())) }
        assertEquals(RELICS.size - 1, w.relicsFound)
        assertFalse(GOLEM_RELIC in w.state.relics)
        assertFalse(w.findRelic(Rng(99)))
        assertEquals("Find all ${RELICS.size} relics first", w.relicRewardBlocker())
    }

    @Test fun theFullSetPaysOnce() {
        val w = deep()
        RELICS.keys.forEach { w.giveRelic(it) }
        val coins = w.state.coins
        assertTrue(w.claimRelicReward())
        assertEquals(coins + RELIC_REWARD.getValue("coins"), w.state.coins)
        assertFalse(w.claimRelicReward())
        val back = GameState.fromJson(JSONObject(w.state.toJson().toString()))
        assertEquals(RELICS.size, back.relics.size)
        assertTrue(back.relicsClaimed)
    }

    @Test fun shallowFloorsHaveNoTreasure() {
        val w = deep()
        for (f in 1..40) {
            w.state.mine.gatesOpened += f
            w.enterFloor(f)
            assertTrue(w.state.mine.cells.none { it.node == "treasure_chest" || it.node == "relic_rock" })
        }
    }
}
