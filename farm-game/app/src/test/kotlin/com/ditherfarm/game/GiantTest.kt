package com.ditherfarm.game

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Giant crops: a ripe 3x3 of pumpkins, melons or cabbages may merge at dawn; one tap harvests it. */
class GiantTest {
    private val data = WorldTest.loadData()

    private fun ripeSquare(w: World, crop: String) {
        for (i in FieldLayout.plotsOf(0)) w.plot(i).apply { clear(); this.crop = crop; growth = data.crop(crop).growMinutes; watered = true }
    }

    /** Dawns until a giant grows (the chance is per dawn), with the field kept ripe. */
    private fun growGiant(w: World, crop: String): Int {
        repeat(80) {
            ripeSquare(w, crop)
            w.giantDawn(it)
            FieldLayout.plotsOf(0).firstOrNull { i -> w.plot(i).giant >= 0 }?.let { i -> return w.plot(i).giant }
        }
        error("no giant in 80 dawns")
    }

    @Test fun ripeSquareGrowsAGiantThatHarvestsGold() {
        val w = World.newGame(data, 21).apply { state.coins = 10_000 }
        val a = growGiant(w, "pumpkin")
        assertEquals(9, giantPlots(a).count { w.plot(it).giant == a })
        val before = w.count(Quality.GOLD.key("pumpkin"))
        assertTrue(w.harvest(giantPlots(a)[4]))
        assertEquals(before + data.crop("pumpkin").yield * 18, w.count(Quality.GOLD.key("pumpkin")))
        assertTrue(giantPlots(a).all { w.plot(it).empty && w.plot(it).giant < 0 })
    }

    @Test fun onlyBigCropsGrowGiants() {
        val w = World.newGame(data, 21)
        repeat(60) { ripeSquare(w, "wheat"); w.giantDawn(it) }
        assertTrue(FieldLayout.plotsOf(0).none { w.plot(it).giant >= 0 })
    }

    @Test fun farmhandsLeaveGiants() {
        val w = World.newGame(data, 21)
        val a = growGiant(w, "melon")
        assertFalse("a farmhand's harvest skips it", w.harvest(giantPlots(a)[0], auto = true, into = HashMap()))
        assertTrue(giantPlots(a).all { w.plot(it).crop == "melon" })
    }

    @Test fun giantSurvivesASave() {
        val w = World.newGame(data, 21)
        val a = growGiant(w, "cabbage")
        val back = GameState.fromJson(JSONObject(w.state.toJson().toString()))
        assertTrue(giantPlots(a).all { back.plots[it].giant == a })
    }
}
