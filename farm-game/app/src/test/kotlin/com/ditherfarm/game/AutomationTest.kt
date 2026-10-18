package com.ditherfarm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationTest {
    private val data = WorldTest.loadData()
    private fun fresh(level: Int = 1) = World.newGame(data, 11).apply {
        if (level > 1) gainXp(data.level(level).totalXp - state.xp)
        state.coins = 1_000_000
        events.clear()
    }

    /** Vertex at the top-left corner of field plot [plot]. */
    private fun cornerOf(plot: Int): Int { val (c, r) = FieldLayout.cells[plot]; return FieldLayout.vertex(c, r) }

    @Test fun layoutHasSixtyPlotsAndUniqueCells() {
        val w = fresh()
        assertEquals(FieldLayout.TOTAL, w.state.plots.size)
        assertEquals(FieldLayout.FIELD, FieldLayout.cells.toSet().size)
        assertEquals(6, w.ownedPlots)
    }

    @Test fun copperSprinklerCoversFourPlots() {
        val v = FieldLayout.vertex(4, 3)
        val covered = (0 until FieldLayout.FIELD).filter { FieldLayout.covers(v, 1, it) }
        assertEquals(4, covered.size)
        assertEquals(16, (0 until FieldLayout.FIELD).count { FieldLayout.covers(v, 2, it) })
    }

    @Test fun sprinklerWatersAtPlantingAndAtDawn() {
        val w = fresh(6)
        w.add("copper_sprinkler", 1)
        val plot = 2
        assertTrue(w.placeFixture(cornerOf(plot), "copper_sprinkler"))
        assertEquals(0, w.count("copper_sprinkler"))
        assertTrue(w.covered(plot, FixtureKind.WATER))
        w.plot(plot).clear()
        assertTrue(w.plant(plot, "wheat"))
        assertTrue(w.plot(plot).watered)
        w.advance(Time.REAL_SECONDS_PER_DAY.toDouble())
        assertTrue(w.plot(plot).watered || w.isReady(w.plot(plot)))
        assertFalse("vertex is taken", w.placeFixture(cornerOf(plot), "copper_sprinkler"))
        assertTrue(w.pickUpFixture(cornerOf(plot)))
        assertEquals(1, w.count("copper_sprinkler"))
    }

    @Test fun rodsAndScarecrowsProtectFromStorms() {
        val w = fresh(10)
        for (i in 0 until FieldLayout.FIELD) w.plot(i).owned = true
        // Rods and scarecrows over the whole field.
        w.add("lightning_rod", 2); w.add("scarecrow", 4)
        w.placeFixture(FieldLayout.vertex(2, 3), "lightning_rod"); w.placeFixture(FieldLayout.vertex(7, 3), "lightning_rod")
        listOf(FieldLayout.vertex(2, 2), FieldLayout.vertex(6, 2), FieldLayout.vertex(2, 5), FieldLayout.vertex(6, 5)).forEach { w.placeFixture(it, "scarecrow") }
        for (i in 0 until FieldLayout.FIELD) {
            assertTrue("rod $i", w.covered(i, FixtureKind.LIGHTNING))
            w.plot(i).apply { clear(); crop = "wheat"; growth = 10.0 }
        }
        repeat(5) {
            w.state.weather = Weather.STORM
            w.state.lastStormDay = w.time.dayIndex
            val before = w.time
            w.advance(Time.gameMinutesToRealSeconds(before.next(14.0).minutes - before.minutes + 1))
        }
        assertTrue(w.state.plots.none { it.scorched })
    }

    @Test fun greenhouseIgnoresSeasons() {
        val w = fresh(12)
        val gh = FieldLayout.FIELD
        assertFalse(w.plot(gh).owned)
        w.add("plank", 100); w.add("stone", 60); w.add("iron_bar", 15); w.add("glass", 20)
        assertTrue(w.build("greenhouse"))
        assertTrue(w.plot(gh).owned)
        val offSeason = data.crops.values.first { w.time.season !in it.seasons && it.unlockLevel <= 12 }
        assertNotNull(w.plantBlocker(0, offSeason.id))
        assertTrue(w.plant(gh, offSeason.id))
        assertEquals(6, w.ownedPlots)
    }

    @Test fun shedsHarvestAndReplant() {
        val w = fresh(18)
        w.add("plank", 50); w.add("iron_bar", 30); w.add("copper_bar", 10); w.add("cooking_oil", 10); w.add("brick", 10)
        assertTrue(w.build("harvest_shed"))
        assertTrue(w.build("planter_shed"))
        w.add("seed_wheat", 20)
        for (i in 0 until 6) w.plot(i).apply { clear(); crop = "wheat"; lastCrop = "wheat"; growth = data.crop("wheat").growMinutes }
        val wheat = w.count("wheat")
        w.advance(SHED_REAL_MINUTES * 60 + 5)
        assertTrue(w.count("wheat") > wheat)
        // Whichever shed ran first, after two cycles everything is replanted and growing.
        w.advance(SHED_REAL_MINUTES * 60 + 5)
        assertTrue(w.state.plots.take(6).all { it.crop == "wheat" })
    }

    @Test fun automationSurvivesSave() {
        val w = fresh(6)
        w.add("copper_sprinkler", 1)
        w.placeFixture(FieldLayout.vertex(3, 3), "copper_sprinkler")
        w.state.planterMode = PlanterMode.FILL; w.state.planterSeed = "carrot"
        w.plot(0).lastCrop = "wheat"
        val back = GameState.fromJson(w.state.toJson())
        assertEquals("copper_sprinkler", back.fixtures[FieldLayout.vertex(3, 3)])
        assertEquals(PlanterMode.FILL, back.planterMode)
        assertEquals("carrot", back.planterSeed)
        assertEquals("wheat", back.plots[0].lastCrop)
    }
}
