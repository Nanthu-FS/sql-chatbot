package com.ditherfarm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MineTest {
    private val data = WorldTest.loadData()
    private fun fresh(level: Int = 1) = World.newGame(data, 7).apply {
        if (level > 1) gainXp(data.level(level).totalXp - state.xp)
        state.energy = 10_000.0
    }

    @Test fun mineItemsExist() {
        val missing = HashSet<String>()
        fun check(id: String) { if (id != "coins" && id !in data.items) missing += id }
        data.mine.nodes.values.forEach { n -> (n.drops.keys + n.bonus.keys).forEach(::check) }
        data.mine.gates.forEach { g -> g.chest.keys.forEach(::check); g.item?.let(::check) }
        data.mine.geodes.forEach { (k, g) -> check(k); g.ores.forEach { check(it.first) }; g.gems.forEach { check(it.first) } }
        data.mine.minerals.values.flatten().forEach(::check)
        data.mine.bands.forEach { b -> b.weights.keys.forEach { assertNotNull(it, data.mine.nodes[it]) } }
        data.mine.quarry.forEach { q -> q.weights.keys.forEach { assertNotNull(it, data.mine.nodes[it]) } }
        assertTrue("missing items: $missing", missing.isEmpty())
    }

    @Test fun quarryFillsAtLevel3AndRegrowsAfterBreaking() {
        val w = fresh(3)
        w.advance(1.0)
        assertEquals(12, w.mine.quarry.size)
        assertTrue(w.mine.quarry.all { it.node != null })
        val i = w.mine.quarry.indexOfFirst { it.node == "stone" }
        val stone = w.count("stone")
        while (w.mine.quarry[i].node != null) assertTrue(w.mineQuarry(i))
        assertTrue(w.count("stone") > stone)
        w.advance(10 * 60.0)
        assertNull("still regrowing", w.mine.quarry[i].node)
        w.advance(20 * 60.0)
        assertNotNull(w.mine.quarry[i].node)
    }

    @Test fun ironNeedsStonePickaxe() {
        val w = fresh(14)
        assertNotNull(w.mineBlocker("iron_vein"))
        w.state.tools[Tool.PICKAXE.name.lowercase()] = 1
        assertEquals(1, w.pickTier())
        assertNull(w.mineBlocker("iron_vein"))
    }

    @Test fun repairThenDescendWithLadder() {
        val w = fresh(4)
        assertFalse(w.enterFloor(1))
        w.add("wood", 25); w.state.coins += 100
        assertTrue(w.repairMine())
        assertTrue(w.enterFloor(1))
        val m = w.mine
        assertEquals(9, m.grid)
        assertTrue(m.ladder >= 0)
        assertFalse(w.descend())
        // Break rocks until the ladder shows (the pity rule guarantees it by 10 breaks).
        var guard = 0
        while (!m.ladderFound && guard++ < 500) {
            val i = m.cells.indexOfFirst { it.node != null && it.node != "glow_mushroom" && w.mineBlocker(it.node) == null }
            assertTrue(i >= 0)
            w.mineFloor(i)
        }
        assertTrue(m.ladderFound)
        assertTrue(m.breaks <= 10)
        assertTrue(w.descend())
        assertEquals(2, m.floor)
        assertEquals(2, m.deepestFloor)
    }

    @Test fun elevatorStopsEveryFiveAndGateNeedsLantern() {
        val w = fresh(8)
        w.add("wood", 25); w.state.coins += 100; w.repairMine()
        assertTrue(w.enterFloor(5))
        assertEquals(listOf(0, 5), w.elevatorStops())
        assertNotNull(w.gateBlocker(10)) // wood pickaxe, no lantern
        w.state.tools[Tool.PICKAXE.name.lowercase()] = 1
        assertEquals("Needs a Lantern", w.gateBlocker(10))
        w.add("lantern", 1)
        val coins = w.state.coins
        assertTrue(w.enterFloor(10))
        assertEquals(coins + 300, w.state.coins)
        assertEquals(listOf(0, 5, 10), w.elevatorStops())
    }

    @Test fun darkFloorsNeedLight() {
        val w = fresh(14)
        w.add("wood", 25); w.state.coins += 100; w.repairMine()
        w.state.tools[Tool.PICKAXE.name.lowercase()] = 2
        w.mine.gatesOpened += listOf(10, 20)
        assertTrue(w.enterFloor(25))
        val m = w.mine
        assertTrue(m.dark)
        val far = 0
        assertFalse(w.lit(far))
        w.add("torch", 1)
        val spot = m.cells.indices.first { m.cells[it].node == null && it != 0 && it % m.grid != 0 && it / m.grid != 0 && it % m.grid < 4 && it / m.grid < 4 }
        assertTrue(w.placeTorch(spot))
        assertTrue(w.lit(far))
    }

    @Test fun geodesCrackAtTheAnvil() {
        val w = fresh(7)
        w.add("geode", 20)
        assertNotNull(w.crackBlocker("geode"))
        w.state.coins += 1000; w.add("stone", 30); w.add("copper_bar", 2)
        assertTrue(w.build("geode_anvil"))
        var got = 0
        repeat(20) { got += w.crackGeode("geode").values.sum() }
        assertEquals(0, w.count("geode"))
        assertTrue(got >= 20)
    }

    @Test fun mineStateSurvivesSave() {
        val w = fresh(4)
        w.add("wood", 25); w.state.coins += 100; w.repairMine(); w.enterFloor(3)
        val back = GameState.fromJson(w.state.toJson())
        assertTrue(back.mine.repaired)
        assertEquals(3, back.mine.floor)
        assertEquals(w.mine.cells.map { it.node }, back.mine.cells.map { it.node })
        assertEquals(w.mine.ladder, back.mine.ladder)
    }
}
