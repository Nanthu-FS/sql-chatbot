package com.ditherfarm.game

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The farm pet: offered at level 4, one kept and named, a gift a day that waits to be collected. */
class PetTest {
    private val data = WorldTest.loadData()
    private fun World.toLevel(l: Int) = gainXp(data.level(l).totalXp - state.xp)

    @Test fun offeredAtLevelFourAndAdoptedOnce() {
        val w = World.newGame(data, 7)
        w.toLevel(3)
        assertFalse(w.petOffered)
        assertFalse(w.adoptPet(PetKind.DOG, "Rex"))
        w.toLevel(PET_LEVEL)
        assertTrue(w.petOffered)
        assertTrue(w.adoptPet(PetKind.CAT, "  whiskerstoolong "))
        assertEquals(PetKind.CAT, w.pet.kind)
        assertEquals("WHISKERSTO", w.pet.name)
        assertFalse("only one pet", w.adoptPet(PetKind.DOG, "Rex"))
        assertTrue("a welcome gift", w.pet.gift.isNotEmpty())
    }

    @Test fun blankNameTakesTheSuggestion() {
        val w = World.newGame(data, 7).apply { toLevel(PET_LEVEL) }
        assertTrue(w.adoptPet(PetKind.DOG, " "))
        assertEquals(PetKind.DOG.defaultName, w.pet.name)
    }

    @Test fun oneGiftADayThatWaits() {
        val w = World.newGame(data, 11).apply { toLevel(PET_LEVEL) }
        w.adoptPet(PetKind.DOG, "Biscuit")
        val coins = w.state.coins
        val got = w.collectPetGift()
        assertNotNull(got)
        assertTrue(got!!.isNotEmpty())
        assertTrue(w.pet.gift.isEmpty())
        assertNull("nothing until tomorrow", w.collectPetGift())
        got["coins"]?.let { assertEquals(coins + it, w.state.coins) }
        // Two dawns pass without collecting: still just one gift waiting.
        w.advance(2 * Time.REAL_SECONDS_PER_DAY + 60)
        val waiting = LinkedHashMap(w.pet.gift)
        assertTrue(waiting.isNotEmpty())
        w.advance(Time.REAL_SECONDS_PER_DAY)
        assertEquals("the gift does not stack or change", waiting, w.pet.gift)
    }

    @Test fun petSurvivesASave() {
        val w = World.newGame(data, 5).apply { toLevel(PET_LEVEL) }
        w.adoptPet(PetKind.CAT, "Miso")
        val back = GameState.fromJson(JSONObject(w.state.toJson().toString()))
        assertEquals(PetKind.CAT, back.pet.kind)
        assertEquals("MISO", back.pet.name)
        assertEquals(w.pet.gift, back.pet.gift)
        assertTrue(back.pet.offered || !w.pet.offered)
    }

    @Test fun loveGrowsByDayAndLevelsUp() {
        val w = World.newGame(data, 9).apply { toLevel(PET_LEVEL) }
        w.adoptPet(PetKind.DOG, "Rex")
        assertEquals(1, w.petLevel)
        assertTrue(w.petPetted())
        assertFalse("once a day", w.petPetted())
        w.collectPetGift()                     // a collected gift counts too
        assertEquals(2, w.pet.love)
        repeat(3) { w.petFetched(true) }
        assertEquals(5, w.pet.love)
        assertEquals(2, w.petLevel)
        assertTrue(w.events.any { it is WorldEvent.PetLevelUp })
    }

    @Test fun fetchBonusOnceADayAndOnlyForGreatCatches() {
        val w = World.newGame(data, 9).apply { toLevel(PET_LEVEL) }
        w.adoptPet(PetKind.CAT, "Miso")
        assertTrue(w.petFetched(false).isEmpty())
        val coins = w.state.coins
        val bonus = w.petFetched(true)
        assertEquals(coins + bonus.getValue("coins"), w.state.coins)
        assertTrue("once a day", w.petFetched(true).isEmpty())
    }

    @Test fun accessoriesBuyOnceAndWear() {
        val w = World.newGame(data, 9).apply { toLevel(PET_LEVEL); state.coins = 10_000 }
        w.adoptPet(PetKind.DOG, "Rex")
        assertNotNull("pet level 3 for the hat", w.accessoryBlocker("straw_hat"))
        assertTrue(w.wearAccessory("bandana"))
        val coins = w.state.coins
        assertTrue(w.wearAccessory(null))
        assertTrue("owned: free to wear again", w.wearAccessory("bandana"))
        assertEquals(coins, w.state.coins)
        assertEquals("bandana", GameState.fromJson(JSONObject(w.state.toJson().toString())).pet.accessory)
    }
}
