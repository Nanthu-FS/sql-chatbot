package com.ditherfarm.game

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryTest {
    private val data = WorldTest.loadData()

    @Test fun aNewFarmOpensAtTheBank() {
        val w = World.newGame(data, 5)
        assertEquals("opening", w.pendingScene()?.id)
        assertTrue(w.pendingScene()!!.bank)
        w.finishScene("opening")
        assertNull("nothing until the village is rebuilt", w.pendingScene())
    }

    @Test fun theOfficerVisitsAfterEachRebuild() {
        val w = World.newGame(data, 5)
        w.finishScene("opening")
        w.state.villageTier = 1
        assertEquals("tier_1", w.pendingScene()?.id)
        w.finishScene("tier_1")
        w.state.villageTier = 3
        assertEquals("tier_2", w.pendingScene()?.id)
        w.finishScene("tier_2")
        assertEquals("finale", w.pendingScene()?.id)
    }

    @Test fun theFinalePaysTheLoanOnce() {
        val w = World.newGame(data, 5)
        w.state.villageTier = 3
        val coins = w.state.coins
        w.finishScene("finale")
        assertEquals(coins + 50_000, w.state.coins)
        assertTrue(w.events.any { it is WorldEvent.LoanApproved })
        w.finishScene("finale")
        assertEquals("only once", coins + 50_000, w.state.coins)
    }

    @Test fun namesAreCleanedAndFillTheLines() {
        val w = World.newGame(data, 5)
        assertEquals("Farmer", w.playerName)
        w.setPlayer(7, "  Rosa-Lee 123 Montgomery ")
        assertEquals(FARMER_LOOKS - 1, w.state.playerLook)
        assertTrue(w.state.playerName.length <= NAME_MAX)
        assertTrue(w.state.playerName.startsWith("RosaLee"))
        val line = SCENES.first { it.id == "tier_1" }.lines.first { "{name}" in it.text }
        assertTrue(w.storyText(line).contains(w.playerName))
    }

    @Test fun olderSavesSkipTheScenesBehindThem() {
        val w = World.newGame(data, 5)
        w.state.villageTier = 2
        val json = w.state.toJson().put("version", 3)
        json.remove("story")
        val back = World(data, GameState.fromJson(JSONObject(json.toString())))
        assertEquals(setOf("opening", "tier_1", "tier_2"), back.state.storySeen)
        assertNull(back.pendingScene())
    }

    @Test fun storySurvivesSaving() {
        val w = World.newGame(data, 5)
        w.setPlayer(2, "Sam")
        w.finishScene("opening")
        val back = GameState.fromJson(JSONObject(w.state.toJson().toString()))
        assertEquals("Sam", back.playerName)
        assertEquals(2, back.playerLook)
        assertTrue("opening" in back.storySeen)
    }
}
