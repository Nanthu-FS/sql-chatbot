package com.ditherfarm.game

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Decorations: bought with coins, one per tile, beauty adds to order coins, half back on pick-up. */
class DecorTest {
    private val data = WorldTest.loadData()
    private fun fresh() = World.newGame(data, 3).apply { state.coins = 100_000; gainXp(data.level(20).totalXp - state.xp); events.clear() }

    @Test fun placeAndPickUp() {
        val w = fresh()
        val coins = w.state.coins
        assertTrue(w.placeDecor(5, 6, "bench"))
        assertEquals(coins - 300, w.state.coins)
        assertFalse("one per tile", w.placeDecor(5, 6, "gnome"))
        assertTrue(w.pickUpDecor(5, 6))
        assertEquals(coins - 150, w.state.coins)
        assertTrue(w.state.decor.isEmpty())
    }

    @Test fun levelAndCoinsGate() {
        val w = World.newGame(data, 3)
        assertNotNull(w.decorBlocker("sheaf"))
        w.state.coins = 10
        assertNotNull(w.decorBlocker("flowers_red"))
        assertFalse(w.placeDecor(1, 1, "flowers_red"))
    }

    @Test fun beautyAddsToOrderCoinsUpToTenPercent() {
        val w = fresh()
        assertEquals(0.0, w.decorBonus(), 1e-9)
        w.placeDecor(1, 1, "pond")          // 12 beauty: +2%
        assertEquals(0.02, w.decorBonus(), 1e-9)
        w.placeDecor(2, 1, "sheaf")         // +30: 42 beauty, +8%
        w.placeDecor(3, 1, "arch")          // +15: 57, capped at +10%
        assertEquals(0.10, w.decorBonus(), 1e-9)
    }

    @Test fun decorSurvivesASave() {
        val w = fresh()
        w.placeDecor(7, 8, "gnome")
        val back = GameState.fromJson(JSONObject(w.state.toJson().toString()))
        assertEquals("gnome", back.decor[decorKey(7, 8)])
    }
}
