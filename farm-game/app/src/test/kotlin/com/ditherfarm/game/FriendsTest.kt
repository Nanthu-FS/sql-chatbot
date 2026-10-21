package com.ditherfarm.game

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Villager friendships: a chat and a gift a day, ten hearts, heart events at 2, 5 and 8. */
class FriendsTest {
    private val data = WorldTest.loadData()

    private fun village() = World.newGame(data, 31).apply {
        knowEverything(); add("wheat", 20); add("stone", 20); add("carrot", 5); add(Quality.GOLD.key("bread"), 2); events.clear()
    }

    @Test fun everyVillagerHasTalkAndThreeHeartScenesAndAKeepsake() {
        for (v in data.villagers) {
            assertTrue(v.id, TALK[v.id]?.isNotEmpty() == true)
            for (h in HEART_EVENTS) assertNotNull("${v.id} at $h", HEART_SCENES[heartScene(v.id, h)])
            assertTrue(v.id, DECOR.values.any { it.keepsake == v.id })
            for (f in v.favorites) assertNotNull("${v.id} loves $f", data.items[f])
        }
    }

    @Test fun theNewcomersArriveOnCue() {
        val w = World.newGame(data, 31)
        fun here(id: String) = w.villagersUnlocked().any { it.id == id }
        assertFalse(here("finn"))
        w.gainXp(w.data.level(8).totalXp - w.state.xp)
        assertTrue("Finn comes with fishing", here("finn"))
        assertFalse("Jasper waits for the stone village", here("jasper"))
        w.state.villageTier = 1
        assertTrue(here("jasper"))
        assertFalse("Marco is still with Vexmoor", here("marco"))
        w.state.act2.chapter = 3
        assertTrue(here("marco"))
        // Courting is by the other gender: four brides, four grooms.
        val rom = data.villagers.filter { it.romance }
        assertEquals(4, rom.count { it.female })
        assertEquals(4, rom.count { !it.female })
        assertFalse("Pip is a child", data.villagers.first { it.id == "pip" }.romance)
    }

    @Test fun aChatOnceADay() {
        val w = village()
        assertTrue(w.talk("mara"))
        assertFalse(w.talk("mara"))
        assertEquals(TALK_POINTS, w.friendship("mara"))
        assertTrue(w.events.any { it is WorldEvent.Talked && it.line.isNotBlank() })
        w.advance(Time.REAL_SECONDS_PER_DAY)
        assertTrue(w.talk("mara"))
    }

    @Test fun giftsCountByTaste() {
        val w = village()
        assertEquals(GiftReaction.LOVED, w.giftReaction("mara", "wheat"))
        assertEquals(GiftReaction.LIKED, w.giftReaction("mara", "carrot"))
        assertEquals(GiftReaction.POOR, w.giftReaction("mara", "stone"))
        assertTrue(w.giveGift("mara", "wheat"))
        assertEquals(GIFT_LOVED, w.friendship("mara"))
        assertEquals("One gift a day", w.giftBlocker("mara", "wheat"))
        assertTrue("other villagers still take gifts", w.giveGift("tobias", "stone"))
        assertEquals(19, w.count("wheat"))
        assertEquals("seeds can't be given", "They can't use that", w.giftBlocker("ivy", "seed_wheat"))
    }

    @Test fun goldGiftsCountMore() {
        val w = village()
        assertTrue(w.giveGift("mara", Quality.GOLD.key("bread")))
        assertEquals((GIFT_LOVED * Quality.GOLD.mult).toInt(), w.friendship("mara"))
    }

    @Test fun tenHeartsAndHeartEvents() {
        // Mara can't be courted, so her friendship goes all the way to ten (round 7).
        val w = village()
        assertEquals(0, w.hearts("mara"))
        w.befriend("mara", 250)
        assertEquals(2, w.hearts("mara"))
        assertEquals(2, w.pendingHeartEvent("mara"))
        assertTrue(w.events.any { it is WorldEvent.HeartUp && it.villager == "mara" && it.hearts == 2 })
        w.finishScene(heartScene("mara", 2))
        assertNull(w.pendingHeartEvent("mara"))
        assertTrue(w.friendship("mara") > 250)
        w.befriend("mara", 5000)
        assertEquals(FRIEND_HEARTS, w.hearts("mara"))
        assertNull(w.nextHeart("mara"))
        assertEquals(5, w.pendingHeartEvent("mara"))
    }

    @Test fun bestFriendsGiveAKeepsakeOnce() {
        val w = village()
        assertEquals("A gift from a best friend", w.decorBlocker("ks_bram"))
        w.befriend("bram", 4000)
        assertTrue("ks_bram" in w.state.keepsakes)
        assertTrue(w.events.any { it is WorldEvent.KeepsakeGiven && it.decor == "ks_bram" })
        assertEquals(listOf("ks_bram"), w.keepsakesToPlace().map { it.id })
        val coins = w.state.coins
        assertTrue(w.placeDecor(20, 6, "ks_bram"))
        assertEquals("keepsakes are free", coins, w.state.coins)
        assertEquals("Already on the farm", w.decorBlocker("ks_bram"))
        assertTrue(w.keepsakesToPlace().isEmpty())
        assertTrue(w.pickUpDecor(20, 6))
        assertEquals(listOf("ks_bram"), w.keepsakesToPlace().map { it.id })
        for (v in data.villagers) assertTrue(v.id, DECOR.values.any { it.keepsake == v.id })
    }

    @Test fun everyVillagerHasTalkAndThreeHeartEvents() {
        for (v in data.villagers) {
            assertNotNull(v.id, TALK[v.id])
            for (h in HEART_EVENTS) assertNotNull("${v.id} $h", HEART_SCENES[heartScene(v.id, h)])
        }
    }

    @Test fun chatsAndGiftsAreSaved() {
        val w = village()
        w.talk("pip"); w.giveGift("pip", "carrot")
        val back = GameState.fromJson(JSONObject(w.state.toJson().toString()))
        assertEquals(w.state.talked["pip"], back.talked["pip"])
        assertEquals(w.state.gifted["pip"], back.gifted["pip"])
        assertEquals(w.friendship("pip"), back.friendship["pip"])
    }
}
