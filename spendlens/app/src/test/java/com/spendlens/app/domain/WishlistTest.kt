package com.spendlens.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WishlistTest {

    @Test
    fun everyKindHasChoicesCheapestFirst() {
        WishKind.entries.forEach { kind ->
            val items = WishCatalog.of(kind)
            assertTrue("$kind has few items", items.size >= 8)
            assertEquals(items.map { it.priceInr }.sorted(), items.map { it.priceInr })
        }
    }

    @Test
    fun rupeePricesAreExactAndOtherCurrenciesTidy() {
        val classic = WishCatalog.items.first { it.name == "Royal Enfield Classic 350" }
        assertEquals(1_97_000_00L, WishCatalog.price(classic, CurrencyOption.INR))
        val usd = WishCatalog.price(classic, CurrencyOption.USD)
        assertEquals(0L, usd % 1_000)
        assertTrue(usd in 2_000_00L..2_500_00L)
    }

    @Test
    fun goalIsDueAtMonthEndAfterTheUsualTime() {
        val phone = WishCatalog.items.first { it.name == "iPhone 17" }
        val goal = WishCatalog.goalFor(phone, CurrencyOption.INR, LocalDate.of(2026, 10, 1))
        assertEquals("iPhone 17", goal.name)
        assertEquals(82_900_00L, goal.targetMinor)
        assertEquals(0L, goal.savedMinor)
        assertEquals(LocalDate.of(2027, 4, 30), goal.deadline)
        // ₹82,900 over 6 months, rounded up to whole rupees.
        assertEquals(13_817_00L, WishCatalog.perMonth(phone, CurrencyOption.INR))
    }
}
