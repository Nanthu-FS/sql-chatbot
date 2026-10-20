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

    @Test fun genderFollowsTheLookAndFillsTheLines() {
        val w = World.newGame(data, 5)
        assertNull("a new farm hasn't picked", w.state.playerGender)
        w.setPlayer(1, "Ada")
        assertEquals(Gender.FEMALE, w.state.playerGender)
        assertEquals("Ada's granddaughter", w.storyText("{name}'s {grandchild}"))
        w.setPlayer(3, "Bo")
        assertEquals(Gender.MALE, w.state.playerGender)
        assertEquals("my boy", w.storyText("{kid}"))
        assertTrue(Gender.MALE.looks.intersect(Gender.FEMALE.looks.toSet()).isEmpty())
        assertEquals(FARMER_LOOKS, (Gender.MALE.looks + Gender.FEMALE.looks).toSet().size)
    }

    @Test fun theOpeningIsGrandpasStory() {
        val lines = SCENES.first { it.id == "opening" }.lines
        assertEquals("three flashbacks end in a stamp, and the bank today too", 4, lines.count { it.cue == Cue.STAMP })
        assertEquals(1, lines.count { it.cue == Cue.PICK })
        assertTrue("the picker comes before the player first speaks", lines.indexOfFirst { it.cue == Cue.PICK } < lines.indexOfFirst { it.who == Who.PLAYER })
        assertTrue(lines.any { it.bg == "bedroom" } && lines.any { it.bg == "porch" } && lines.any { it.bg == "bank_old" })
        assertTrue(SCENES.first { it.id == "finale" }.lines.any { it.cue == Cue.APPROVE })
    }

    @Test fun lettersOpenAtMilestonesOneAtATime() {
        val w = World.newGame(data, 5)
        w.state.bump("harvested")
        assertNull("nothing before the opening has played", w.pendingLetter())
        w.finishScene("opening")
        assertEquals("harvest", w.pendingLetter()?.id)
        w.openLetter("harvest")
        assertNull(w.pendingLetter())
        w.state.villageTier = 1
        assertEquals("stone", w.pendingLetter()?.id)
        w.openLetter("stone")
        val back = GameState.fromJson(JSONObject(w.state.toJson().toString()))
        assertEquals(setOf("harvest", "stone"), back.letters)
    }

    @Test fun storySurvivesSaving() {
        val w = World.newGame(data, 5)
        w.setPlayer(2, "Sam")
        w.finishScene("opening")
        val back = GameState.fromJson(JSONObject(w.state.toJson().toString()))
        assertEquals("Sam", back.playerName)
        assertEquals(2, back.playerLook)
        assertEquals(Gender.FEMALE, back.playerGender)
        assertTrue("opening" in back.storySeen)
        // A save from before round 7 has no gender until the player picks.
        val old = w.state.toJson()
        old.getJSONObject("story").remove("gender")
        assertNull(GameState.fromJson(JSONObject(old.toString())).playerGender)
    }
}
