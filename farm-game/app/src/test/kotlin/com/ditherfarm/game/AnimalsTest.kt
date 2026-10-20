package com.ditherfarm.game

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Farm animals: bought for the pasture, fed from the trough at dawn, products waiting to be tapped. */
class AnimalsTest {
    private val data = WorldTest.loadData()

    private fun World.toLevel(l: Int) = gainXp(data.level(l).totalXp - state.xp)
    private fun World.days(n: Int) = repeat(n) { advance(Time.REAL_SECONDS_PER_DAY) }

    private fun ranch(level: Int = 16) = World.newGame(data, 9).apply {
        knowEverything(); toLevel(level); state.coins = 50_000; add("wheat", 200); add("corn", 100); events.clear()
    }

    @Test fun animalsNeedLevelsAndRoom() {
        val w = World.newGame(data, 9).apply { state.coins = 10_000 }
        assertEquals("Animals come at level $ANIMAL_LEVEL", w.animalBlocker(AnimalKind.HEN))
        w.toLevel(ANIMAL_LEVEL)
        assertEquals("Unlocks at level 11", w.animalBlocker(AnimalKind.SHEEP))
        val coins = w.state.coins
        repeat(4) { assertTrue(w.buyAnimal(AnimalKind.HEN)) }
        assertNotNull("pasture holds 4 hens", w.animalBlocker(AnimalKind.HEN))
        assertEquals(coins - 4 * 250, w.state.coins)
    }

    @Test fun fedHenLaysEachMorningAndHungryOneLosesAHeart() {
        val w = ranch()
        w.buyAnimal(AnimalKind.HEN)
        assertEquals(10, w.fillTrough("wheat", 10))
        val hen = w.ranch.animals[0]
        assertFalse(hen.product)
        w.days(1)
        assertTrue("a fed hen lays", hen.product)
        assertEquals(9, w.ranch.feed)
        val eggs = w.count("egg", anyQuality = true)
        assertTrue(w.collectProduct(0))
        assertEquals(eggs + 1, w.count("egg", anyQuality = true))
        assertEquals("collecting pets it", 1, hen.hearts)
        assertFalse("once a day", w.petAnimal(0))
        w.ranch.feed = 0
        w.days(1)
        assertFalse("no feed, no egg", hen.product)
        assertEquals(0, hen.hearts)
    }

    @Test fun sheepGrowWoolEveryOtherDayAndCowsEatMore() {
        val w = ranch()
        w.buyAnimal(AnimalKind.SHEEP); w.buyAnimal(AnimalKind.COW)
        w.fillTrough("corn", 30)
        assertEquals(TROUGH_MAX, w.ranch.feed)
        w.days(1)
        assertEquals(TROUGH_MAX - 5, w.ranch.feed)
        assertTrue(w.ranch.animals.all { it.product })
        w.collectProduct(0); w.collectProduct(1)
        w.days(1)
        assertFalse("wool takes two days", w.ranch.animals[0].product)
        assertTrue("milk every day", w.ranch.animals[1].product)
        w.days(1)
        assertTrue(w.ranch.animals[0].product)
    }

    @Test fun heartsMakeBetterProducts() {
        val w = ranch()
        w.buyAnimal(AnimalKind.HEN)
        val hen = w.ranch.animals[0]
        hen.hearts = MAX_HEARTS
        w.fillTrough("wheat", 5); w.days(1)
        val gold = w.count(Quality.GOLD.key("egg"))
        w.collectProduct(0)
        assertEquals(gold + 1, w.count(Quality.GOLD.key("egg")))
    }

    @Test fun ranchHandFeedsAndCollects() {
        val w = ranch()
        repeat(2) { w.buyAnimal(AnimalKind.HEN) }
        w.add("plank", 20); w.add("iron_bar", 5)
        assertTrue(w.hireRanchHand())
        val eggs = w.count("egg", anyQuality = true)
        w.days(3)
        assertEquals(eggs + 6, w.count("egg", anyQuality = true))
        assertTrue(w.ranch.animals.none { it.product })
    }

    @Test fun savesTheRanch() {
        val w = ranch()
        w.buyAnimal(AnimalKind.COW); w.buyAnimal(AnimalKind.HEN)
        w.fillTrough("wheat", 7); w.days(1); w.petAnimal(1)
        val back = GameState.fromJson(JSONObject(w.state.toJson().toString())).ranch
        assertEquals(listOf(AnimalKind.COW, AnimalKind.HEN), back.animals.map { it.kind })
        assertEquals(w.ranch.feed, back.feed)
        assertEquals(w.ranch.animals[1].hearts, back.animals[1].hearts)
        assertEquals(w.ranch.animals[0].product, back.animals[0].product)
    }
}
