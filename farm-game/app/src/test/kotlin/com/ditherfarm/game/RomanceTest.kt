package com.ditherfarm.game

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Romance (round 7): courting the other gender, dating, jealousy, the ring, the farmhouse, the wedding. */
class RomanceTest {
    private val data = WorldTest.loadData()

    private fun farmer(look: Int) = World.newGame(data, 9).apply {
        knowEverything(); setPlayer(look, "Sam"); gainXp(data.level(16).totalXp - state.xp); state.coins += 100_000; events.clear()
    }

    @Test fun courtingIsByTheOtherGender() {
        val man = farmer(0)
        assertTrue(man.canCourt("ivy"))
        assertFalse(man.canCourt("rook"))
        assertFalse("not every villager", man.canCourt("mara"))
        val woman = farmer(2)
        assertTrue(woman.canCourt("finn"))
        assertFalse(woman.canCourt("sela"))
    }

    @Test fun eightHeartsUntilABouquet() {
        val w = farmer(0)
        w.befriend("ivy", 9_000)
        assertEquals(COURT_HEARTS, w.hearts("ivy"))
        assertEquals("Make a bouquet at the Workbench", w.bouquetBlocker("ivy"))
        w.add(BOUQUET, 1)
        assertTrue(w.giveBouquet("ivy"))
        assertTrue(w.isDating("ivy"))
        w.befriend("ivy", 9_000)
        assertEquals(FRIEND_HEARTS, w.hearts("ivy"))
        // A friend of the same gender can still be a best friend.
        w.befriend("rook", 9_000)
        assertEquals(FRIEND_HEARTS, w.hearts("rook"))
    }

    @Test fun datingTwoMakesOneJealous() {
        val w = farmer(0)
        for (id in listOf("ivy", "sela")) { w.befriend(id, 2_500); w.add(BOUQUET, 1); assertTrue(w.giveBouquet(id)) }
        val before = w.friendship("ivy")
        w.add("grapes", 1)
        assertTrue(w.giveGift("sela", "grapes"))
        assertEquals(before - JEALOUSY, w.friendship("ivy"))
        assertTrue(w.events.any { it is WorldEvent.Jealous && it.villager == "ivy" && it.rival == "sela" })
    }

    @Test fun proposingNeedsTenHeartsTheFarmhouseAndARing() {
        val w = farmer(0)
        w.befriend("ivy", 2_500); w.add(BOUQUET, 1); w.giveBouquet("ivy")
        assertEquals("Needs $FRIEND_HEARTS hearts", w.proposeBlocker("ivy"))
        w.befriend("ivy", 2_000)
        assertTrue(w.proposeBlocker("ivy")!!.startsWith("Where would you live"))
        for ((k, v) in FARMHOUSE_COST) if (k != "coins") w.add(k, v)
        assertTrue(w.buildFarmhouse())
        assertEquals(1, w.romance.farmhouse)
        assertTrue(w.proposeBlocker("ivy")!!.startsWith("Forge a ring"))
        w.add(RING, 1)
        assertTrue(w.propose("ivy"))
        assertEquals("ivy", w.romance.engaged)
        assertFalse(w.weddingDue())
        w.advance(Time.REAL_SECONDS_PER_DAY * WEDDING_DAYS)
        assertTrue(w.weddingDue())
        w.marry("ivy")
        assertEquals("ivy", w.romance.spouse)
        assertTrue(w.romance.dating.isEmpty())
        w.befriend("ivy", 9_000)
        assertEquals(SPOUSE_HEARTS, w.hearts("ivy"))
        val back = GameState.fromJson(JSONObject(w.state.toJson().toString()))
        assertEquals("ivy", back.romance.spouse)
        assertEquals(1, back.romance.farmhouse)
        assertNull(back.romance.engaged)
    }

    private fun marriedTo(id: String, look: Int = 0) = farmer(look).apply {
        befriend(id, 2_500); add(BOUQUET, 1); giveBouquet(id)
        befriend(id, 2_000); romance.farmhouse = 1; add(RING, 1); propose(id)
        advance(Time.REAL_SECONDS_PER_DAY * WEDDING_DAYS)
        finishScene(weddingScene(id))
        for (n in HEART_EVENTS) state.storySeen += heartScene(id, n)
        events.clear()
    }

    @Test fun theWeddingPlaysOnItsDay() {
        val w = farmer(0)
        w.befriend("ivy", 2_500); w.add(BOUQUET, 1); w.giveBouquet("ivy")
        w.befriend("ivy", 2_000)
        assertTrue(w.pendingDate("ivy"))
        w.finishScene(dateScene("ivy"))
        w.romance.farmhouse = 1; w.add(RING, 1); w.propose("ivy")
        assertNull(w.pendingWedding())
        w.advance(Time.REAL_SECONDS_PER_DAY * WEDDING_DAYS)
        assertEquals(weddingScene("ivy"), w.pendingWedding()?.id)
        w.finishScene(weddingScene("ivy"))
        assertEquals("ivy", w.romance.spouse)
        assertNull(w.pendingWedding())
        assertEquals("WIFE", w.romanceLabel("ivy"))
    }

    @Test fun theSpouseBringsAGiftDoesAChoreAndSellsTheirTrade() {
        val w = marriedTo("finn", look = 2)
        val before = w.unitPrice("trout")
        val price = w.data.item("trout").price
        w.add("wheat", 2)
        w.advance(Time.REAL_SECONDS_PER_DAY)
        val morning = w.events.filterIsInstance<WorldEvent.SpouseMorning>().single()
        assertEquals("finn", morning.villager)
        assertTrue(morning.gift in SPOUSE_GIFTS.getValue("finn"))
        assertTrue(w.count(morning.gift) >= 1)
        assertEquals(1.15, w.spouseMult("trout"), 1e-9)
        assertEquals(1.0, w.spouseMult("wheat"), 1e-9)
        assertTrue(price > 0 && before > 0)
    }

    @Test fun twelveHeartsAsksForAFamilyAndTheKidGrows() {
        val w = marriedTo("ivy")
        assertNull("no married scene before 12 hearts", w.pendingHeartEvent("ivy"))
        w.befriend("ivy", 4_500)
        assertEquals(12, w.pendingHeartEvent("ivy"))
        w.finishScene(heartScene("ivy", 12))
        assertTrue(w.events.any { it is WorldEvent.KidOnTheWay })
        assertFalse(w.kidNeedsName())
        w.advance(Time.REAL_SECONDS_PER_DAY * KID_DAYS)
        assertTrue(w.kidNeedsName())
        w.nameKid("rosie")
        assertEquals("rosie", w.romance.kidName)
        assertEquals(0, w.kidStage())
        w.advance(Time.REAL_SECONDS_PER_DAY * Time.DAYS_PER_SEASON)
        assertEquals(1, w.kidStage())
        w.advance(Time.REAL_SECONDS_PER_DAY * Time.DAYS_PER_SEASON * 3)
        assertEquals(2, w.kidStage())
        // No second ask while there's a kid; the 14-heart scene still comes.
        w.befriend("ivy", 9_000)
        assertEquals(14, w.pendingHeartEvent("ivy"))
        val back = GameState.fromJson(JSONObject(w.state.toJson().toString()))
        assertEquals("rosie", back.romance.kidName)
    }

    @Test fun divorceCostsTheBankFeeAndHearts() {
        val w = marriedTo("ivy")
        w.state.coins = DIVORCE_COST - 1
        assertFalse(w.divorce())
        w.state.coins = DIVORCE_COST + 5
        assertTrue(w.divorce())
        assertEquals(5, w.state.coins)
        assertNull(w.romance.spouse)
        assertEquals(DIVORCE_HEARTS, w.hearts("ivy"))
        assertFalse(bouquetScene("ivy") in w.state.storySeen)
    }
}
