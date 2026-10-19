package com.ditherfarm.game

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WorldTest {
    private val data = loadData()
    private fun fresh(seed: Long = 42) = World.newGame(data, seed)

    /** Advances whole game hours (1 game hour = 50 real seconds). */
    private fun World.hours(h: Double) = advance(Time.gameMinutesToRealSeconds(h * 60))

    @Test fun newGameMatchesStartingKit() {
        val w = fresh()
        assertEquals(250, w.state.coins)
        assertEquals(FieldLayout.PER_BLOCK, w.ownedPlots)
        assertTrue(w.isReady(w.plot(0)) && w.isReady(w.plot(1)))
        assertEquals(12, w.count("seed_wheat"))
        assertEquals(3, w.state.orders.count { it.order != null })
        assertEquals(Season.SPRING, w.time.season)
    }

    @Test fun harvestGivesYieldAndXp() {
        val w = fresh()
        assertTrue(w.harvest(0))
        assertEquals(3, w.count("wheat", anyQuality = true))
        assertTrue(w.state.xp >= 2)
        assertTrue(w.plot(0).empty)
        assertFalse(w.harvest(0))
    }

    @Test fun wateredCarrotGrowsInSixGameHoursDryTakesLonger() {
        val w = fresh()
        assertTrue(w.plant(2, "carrot"))
        assertTrue(w.plant(3, "carrot"))
        assertTrue(w.water(2))
        w.hours(6.0)
        assertTrue(w.isReady(w.plot(2)))
        assertFalse(w.isReady(w.plot(3)))
        w.hours(4.1)
        assertTrue(w.isReady(w.plot(3)))
    }

    @Test fun plantingUsesSeedsThenCoins() {
        val w = fresh()
        repeat(6) { w.state.inventory.remove("seed_carrot") }
        val before = w.state.coins
        assertTrue(w.plant(2, "carrot"))
        assertEquals(before - data.crop("carrot").seedPrice, w.state.coins)
        assertNotNull(w.plantBlocker(4, "corn")) // level 2 crop
        assertEquals("Plot is busy", w.plantBlocker(2, "carrot"))
    }

    @Test fun dawnResetsWateringUnlessItRains() {
        val w = fresh()
        w.plant(2, "potato".let { "carrot" })
        w.water(2)
        assertTrue(w.plot(2).watered)
        // Jump to just after the next 06:00.
        val toDawn = w.time.next(6.0).minutes - w.state.minutes + 1
        w.advance(Time.gameMinutesToRealSeconds(toDawn))
        assertEquals(w.state.weather.waters, w.plot(2).watered)
    }

    @Test fun seasonChangeWithersOutOfSeasonCrops() {
        val w = fresh()
        w.state.level = 5
        // Late spring: plant cabbage (spring/winter) and carrot (spring/fall/winter), then roll into summer.
        w.state.minutes = GameTime.of(1, Season.SPRING, 14, 23.0).minutes
        w.state.lastDayRolled = w.time.dayIndex
        assertTrue(w.plant(2, "carrot"))
        assertTrue(w.plant(3, "cabbage"))
        w.hours(12.0)
        assertEquals(Season.SUMMER, w.time.season)
        assertTrue(w.plot(2).withered)
        assertTrue(w.plot(3).withered)
        assertTrue(w.clearPlot(2))
        assertEquals(1, w.count("withered_crop"))
    }

    @Test fun matureCropsNeverWither() {
        val w = fresh()
        w.state.minutes = GameTime.of(1, Season.SPRING, 14, 12.0).minutes
        w.state.lastDayRolled = w.time.dayIndex
        w.hours(18.0)
        assertEquals(Season.SUMMER, w.time.season)
        assertFalse(w.plot(0).withered) // starting wheat was ripe
        assertTrue(w.isReady(w.plot(0)))
    }

    @Test fun levelUpPaysCoinsAndRefillsEnergy() {
        val w = fresh()
        w.state.energy = 10.0
        val coins = w.state.coins
        w.gainXp(40)
        assertEquals(2, w.level)
        assertEquals(coins + 120, w.state.coins)
        assertEquals(w.data.level(2).maxEnergy.toDouble(), w.state.energy, 0.001)
    }

    @Test fun deliveringAScriptedOrderPays() {
        val w = fresh()
        w.harvest(0); w.harvest(1)
        assertTrue(w.canDeliver(0))
        val coins = w.state.coins
        assertTrue(w.deliver(0))
        assertEquals(coins + 20, w.state.coins)
        assertNull(w.state.orders[0].order)
        // Mara still has her carrot order, so nobody is free yet; deliver it too.
        assertTrue(w.deliver(2))
        // A new order arrives after the delay (2 real minutes at L1-4).
        w.advance(125.0)
        assertTrue(w.state.orders.count { it.order != null } >= 2)
    }

    @Test fun marketSwingStaysWithinThirtyPercentAndSellingFloods() {
        val w = fresh()
        for (d in 0 until 200) {
            val m = w.dailyMult("carrot", d)
            assertTrue(m in 0.70..1.30)
        }
        w.add("pumpkin", 60)
        val first = w.quoteSale("pumpkin", 1)
        val bulk = w.quoteSale("pumpkin", 60)
        assertTrue("bulk sale should be saturated", bulk < first * 60)
        val earned = w.sell("pumpkin", 60)
        assertEquals(bulk, earned)
        assertTrue(w.unitPrice("pumpkin") < first)
    }

    @Test fun fieldBlocksUnlockByLevelAndCost() {
        val w = fresh()
        assertTrue(w.blockOwned(0))
        assertNotNull(w.blockBlocker(1)) // level 3
        w.gainXp(w.data.level(3).totalXp - w.state.xp)
        w.state.coins = 0
        assertNotNull(w.blockBlocker(1)) // coins
        w.state.coins = 10_000
        assertTrue(w.buyBlock(1))
        assertEquals(10_000 - FieldLayout.PRICE[1], w.state.coins)
        assertEquals(2 * FieldLayout.PER_BLOCK, w.ownedPlots)
        assertFalse(w.buyBlock(1))
        assertNotNull(w.blockBlocker(2)) // level 5
    }

    @Test fun oldSavesMoveIntoBlocks() {
        val w = fresh()
        val json = w.state.toJson()
        // Fake a version 1 save: 48 field plots + 12 greenhouse, 20 owned, a crop on old plot 17.
        val plots = org.json.JSONArray()
        for (i in 0 until 60) plots.put(PlotState(i).apply { owned = i < 20; if (i == 17) { crop = "carrot"; growth = 30.0 } }.toJson())
        json.put("plots", plots).put("version", 1)
        json.put("fixtures", org.json.JSONObject().put("5", "scarecrow"))
        val back = World(w.data, GameState.fromJson(json))
        assertEquals(FieldLayout.TOTAL, back.state.plots.size)
        assertEquals(2 * FieldLayout.PER_BLOCK, back.ownedPlots)
        assertEquals("carrot", back.plot(17).crop)
        assertTrue(back.state.fixtures.isEmpty())
        assertEquals(1, back.count("scarecrow"))
        assertEquals(GameState.VERSION, back.state.version)
    }

    @Test fun energyRefillsInFortyFiveMinutes() {
        val w = fresh()
        val max = w.maxEnergy.toDouble()
        assertEquals("level 1 bar", 150.0, max, 0.001)
        w.state.energy = 0.0
        w.advance(ENERGY_REFILL_MINUTES / 2 * 60.0)
        assertEquals(max / 2, w.state.energy, 0.5)
        w.advance(ENERGY_REFILL_MINUTES * 60.0)
        assertEquals(max, w.state.energy, 0.001)
    }

    @Test fun offlineCatchUpIsCapped() {
        val w = fresh()
        val start = w.state.minutes
        w.catchUp(10 * 24 * 3600.0)
        assertEquals(Time.realSecondsToGameMinutes(24 * 3600.0), w.state.minutes - start, 1e-6)
    }

    @Test fun weatherIsDeterministicAndStormsNeedLevelFive() {
        val a = fresh(7)
        val b = fresh(7)
        for (d in 0 until 120) assertEquals(a.rollWeather(d), b.rollWeather(d))
        assertTrue((0 until 300).none { a.rollWeather(it).stormy })
        a.state.level = 5
        assertTrue((0 until 300).any { a.rollWeather(it).stormy })
    }

    @Test fun saveRoundTrip() {
        val w = fresh()
        w.harvest(0)
        w.plant(2, "carrot")
        w.hours(30.0)
        val json = w.state.toJson().toString()
        val back = GameState.fromJson(JSONObject(json))
        assertEquals(json, back.toJson().toString())
    }

    @Test fun storageLimitsBlockHarvest() {
        val w = fresh()
        w.add("carrot", w.capacity(Storage.SILO))
        assertFalse(w.harvest(0))
        assertTrue(w.events.any { it is WorldEvent.Blocked && it.reason == "Silo full" })
    }

    companion object {
        fun loadData(): GameData {
            val dir = listOf(File("src/main/assets/data"), File("app/src/main/assets/data"), File(System.getProperty("assets.dir") ?: "", "data"))
                .first { File(it, "crops.json").exists() }
            return GameData.load { File(dir, it).readText() }
        }
    }
}
