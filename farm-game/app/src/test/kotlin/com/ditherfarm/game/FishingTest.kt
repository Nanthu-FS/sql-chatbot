package com.ditherfarm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fishing: a rod from the Workbench, casts that cost energy, fish by season, hour and weather. */
class FishingTest {
    private val data = WorldTest.loadData()

    private fun World.toLevel(l: Int) = gainXp(data.level(l).totalXp - state.xp)

    private fun angler() = World.newGame(data, 3).apply {
        knowEverything(); toLevel(FISHING_LEVEL); state.coins = 5000; add("wood", 20); add("fibre", 10)
        assertTrue(enqueue("tool_rod_1")); advance(120.0); events.clear()
    }

    @Test fun theRodComesFromTheWorkbench() {
        val w = World.newGame(data, 3)
        assertEquals("Fishing opens at level $FISHING_LEVEL", w.fishBlocker())
        w.toLevel(FISHING_LEVEL)
        assertEquals("Craft a fishing rod at the Workbench", w.fishBlocker())
        assertTrue(angler().hasRod)
        assertNull(angler().fishBlocker())
    }

    @Test fun castSpendsEnergyAndLandingStoresTheFish() {
        val w = angler()
        val energy = w.state.energy
        val f = w.cast()
        assertNotNull(f)
        assertEquals(energy - CAST_ENERGY, w.state.energy, 1e-6)
        val xp = w.state.xp
        assertTrue(w.landFish(f!!))
        assertEquals(1, w.count(f.id))
        assertEquals(xp + f.xp, w.state.xp)
        w.state.energy = 1.0
        assertNull("too tired", w.cast())
    }

    @Test fun fishFollowTheSeasons() {
        val w = angler()
        assertTrue(w.fishWeights().none { it.first.id == "pike" || it.first.id == "salmon" })
        w.state.minutes += Time.MINUTES_PER_DAY * Time.DAYS_PER_SEASON * 3.0
        assertEquals(Season.WINTER, w.time.season)
        val ids = w.fishWeights().map { it.first.id }
        assertTrue("pike" in ids)
        assertFalse("bass" in ids)
        assertTrue("pike" in w.fishNow())
        assertFalse("koi is never ordered", "golden_koi" in w.fishNow())
    }

    @Test fun catfishBiteAtNight() {
        val w = angler()
        fun cat() = w.fishWeights().first { it.first.id == "catfish" }.second
        w.state.minutes = Time.MINUTES_PER_DAY * 4.0 + 12 * 60
        w.state.weather = Weather.SUNNY
        val day = cat()
        w.state.minutes += 10 * 60
        assertEquals(day * 4, cat(), 1e-9)
    }

    @Test fun harderFishHaveSmallerFasterBars() {
        val easy = FISH.getValue("minnow"); val hard = FISH.getValue("golden_koi")
        assertTrue(hard.zone < easy.zone && hard.speed > easy.speed && hard.zone > 0.1f)
    }
}
