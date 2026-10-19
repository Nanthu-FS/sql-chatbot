package com.ditherfarm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkersTest {
    private val data = WorldTest.loadData()
    private fun fresh(level: Int = 5) = World.newGame(data, 31).apply {
        gainXp(data.level(level).totalXp - state.xp)
        state.coins = 1_000_000
        events.clear()
    }

    @Test fun hiringNeedsLevelAndCoins() {
        val w = fresh(4)
        assertNotNull(w.hireBlocker("rosa"))
        w.gainXp(w.data.level(5).totalXp - w.state.xp)
        assertTrue(w.hire("rosa"))
        assertFalse(w.hire("rosa"))
        assertNotNull(w.hireBlocker("milo")) // level 8
        assertTrue(w.levelUpWorker("rosa"))
        assertEquals(2, w.worker("rosa")!!.level)
    }

    @Test fun farmhandHarvestsReplantsAndWaters() {
        val w = fresh()
        w.hire("rosa")
        w.state.fields[0].crop = "wheat"
        w.add("seed_wheat", 40)
        for (i in FieldLayout.plotsOf(0)) w.plot(i).apply { clear(); crop = "wheat"; growth = data.crop("wheat").growMinutes }
        w.add("carrot", 10) // food
        assertTrue(w.assign("rosa", 0))
        repeat(14) { w.levelUpWorker("rosa") } // L15: about 2.8 s a job
        // 48 jobs (harvest, replant, water) fit before the new wheat ripens (150 s).
        w.advance(140.0)
        // Every plot was harvested into the crate (or truck) and replanted.
        assertTrue(FieldLayout.plotsOf(0).all { w.plot(it).crop == "wheat" })
        assertTrue(FieldLayout.plotsOf(0).count { w.isReady(w.plot(it)) } < 16)
        val moved = w.truckLoad() + w.crate(0).values.sum()
        assertTrue("harvest went to crate/truck: $moved", moved >= 16)
    }

    @Test fun levelOneFarmhandTendsAFieldInUnderTwoMinutes() {
        val w = fresh()
        w.hire("rosa")
        w.state.fields[0].crop = "wheat"
        w.add("seed_wheat", 40)
        w.add("carrot", 10) // food
        val plots = FieldLayout.plotsOf(0).toList()
        for (i in plots) w.plot(i).apply { clear(); crop = "wheat"; growth = data.crop("wheat").growMinutes }
        assertTrue(w.assign("rosa", 0))
        // One visit harvests, replants and waters a plot, every 6 s at level 1.
        w.advance(plots.size * workerSeconds(1) + 5)
        assertTrue("all replanted", plots.all { w.plot(it).crop == "wheat" && !w.isReady(w.plot(it)) })
        assertTrue("all watered", plots.all { w.plot(it).watered })
    }

    @Test fun harvesterRoutesSiloFieldsAndTruckSells() {
        val w = fresh()
        w.crate(0)["carrot"] = 5
        w.state.fields[0].toTruck = false
        val carrots = w.count("carrot")
        w.advance(65.0)
        assertEquals(carrots + 5, w.count("carrot"))
        w.crate(0)["wheat"] = 8
        w.state.fields[0].toTruck = true
        w.advance(65.0)
        assertEquals(8, w.truckLoad())
        val coins = w.state.coins
        assertTrue(w.sendTruck())
        assertFalse("already away", w.sendTruck())
        assertEquals(0, w.truckLoad())
        w.advance(TRUCK_TRIP_SECONDS + 5)
        assertTrue(w.state.coins > coins)
        assertFalse(w.state.truck.away)
    }

    @Test fun hungryFarmhandsRest() {
        val w = fresh()
        w.hire("rosa")
        w.assign("rosa", 0)
        w.state.inventory.keys.filter { data.crops.containsKey(baseId(it)) }.forEach { w.state.inventory.remove(it) }
        w.state.fields[0].crop = "wheat"
        // Next dawn: nothing to eat.
        w.advance(Time.REAL_SECONDS_PER_DAY)
        assertTrue(w.worker("rosa")!!.hungry)
        w.add("wheat", 3)
        w.advance(2.0)
        assertFalse(w.worker("rosa")!!.hungry)
    }

    @Test fun workersSurviveSave() {
        val w = fresh()
        w.hire("rosa"); w.assign("rosa", 0); w.levelUpWorker("rosa")
        w.state.truck.load["wheat"] = 4
        w.crate(0)["carrot"] = 2
        val back = GameState.fromJson(w.state.toJson())
        assertEquals(2, back.workers["rosa"]!!.level)
        assertEquals(0, back.workers["rosa"]!!.field)
        assertEquals(4, back.truck.load["wheat"])
        assertEquals(2, back.fields[0].crate["carrot"])
    }
}
