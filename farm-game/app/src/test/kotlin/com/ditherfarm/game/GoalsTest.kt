package com.ditherfarm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalsTest {
    private val data = WorldTest.loadData()
    private fun fresh(level: Int = 1) = World.newGame(data, 21).apply {
        if (level > 1) gainXp(data.level(level).totalXp - state.xp)
        events.clear()
    }

    /** Advances to 07:00 of absolute game day [day]. */
    private fun World.toDay(day: Int) {
        val target = day * Time.MINUTES_PER_DAY + 7 * 60.0
        advance(Time.gameMinutesToRealSeconds(target - state.minutes))
    }

    @Test fun goalItemsExist() {
        val missing = data.goals.pages.flatMap { it.entries }.filter { !it.startsWith("#") && it !in data.items }
        assertTrue("missing: $missing", missing.isEmpty())
        val ids = data.goals.achievements.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun firstSproutIsClaimableAfterOneHarvest() {
        val w = fresh()
        val a = data.goals.achievements.first { it.id == "first_sprout" }
        assertFalse(w.claimable(a))
        assertTrue(w.harvest(0))
        w.advance(1.0)
        assertTrue(w.events.any { it is WorldEvent.AchievementReady && it.id == "first_sprout" })
        val coins = w.state.coins
        assertTrue(w.claimAchievement("first_sprout"))
        assertEquals(coins + 50, w.state.coins)
        assertFalse("tiers count once", w.claimAchievement("first_sprout"))
    }

    @Test fun collectionPageGivesBonus() {
        val w = fresh(20)
        val crops = data.goals.pages.first { it.id == "crops" }
        val price = w.seedPrice("carrot")
        assertFalse(w.pageClaimable(crops))
        data.crops.keys.forEach { w.add(it, 1) }
        assertTrue(w.pageClaimable(crops))
        assertTrue(w.claimPage("crops"))
        assertTrue(w.seedPrice("carrot") < price)
        assertFalse(w.claimPage("crops"))
    }

    @Test fun dailyGiftStreak() {
        val w = fresh()
        assertTrue(w.giftReady(100))
        assertEquals(1, w.giftStreakDay(100))
        assertTrue(w.claimGift(100))
        assertFalse(w.giftReady(100))
        assertEquals(2, w.giftStreakDay(101))
        w.claimGift(101)
        assertEquals("missed a day resets", 1, w.giftStreakDay(103))
        repeat(5) { w.claimGift(102L + it) }
        assertEquals(7, w.state.giftStreak)
        assertEquals(1, w.giftStreakDay(107))
    }

    @Test fun merchantVisitsEverySeventhDay() {
        val w = fresh(MERCHANT_LEVEL)
        w.toDay(5)
        assertFalse(w.merchantHere())
        w.toDay(6)
        assertTrue(w.merchantHere())
        val stock = w.merchantStock()
        assertEquals(6, stock.size)
        assertEquals("stock never re-rolls", stock.map { it.item }, w.merchantStock().map { it.item })
        w.state.coins += 100_000
        assertTrue(w.buyFromMerchant(0))
        assertFalse(w.buyFromMerchant(0))
    }

    @Test fun springFestivalCountsPlanting() {
        val w = fresh(3)
        w.toDay(7) // day 8 of spring
        val f = w.festival()
        assertNotNull(f)
        assertEquals(Weather.SUNNY, w.state.weather)
        for (i in 0 until 6) w.plot(i).clear()
        w.add("seed_wheat", 40)
        var planted = 0
        while (planted < 20) {
            for (i in 0 until 6) if (w.plot(i).empty && w.plant(i, "wheat")) planted++
            for (i in 0 until 6) w.plot(i).clear()
        }
        assertTrue(w.festivalClaimable())
        assertTrue(w.claimFestival())
        assertEquals(1, w.count("golden_acorn"))
        assertFalse(w.claimFestival())
        w.toDay(11)
        assertNull(w.festival())
    }

    @Test fun goalsSurviveSave() {
        val w = fresh()
        w.state.achievements["first_sprout"] = 1
        w.state.pagesClaimed += "crops"
        w.claimGift(50)
        val back = GameState.fromJson(w.state.toJson())
        assertEquals(1, back.achievements["first_sprout"])
        assertTrue("crops" in back.pagesClaimed)
        assertEquals(50L, back.giftDay)
    }
}
