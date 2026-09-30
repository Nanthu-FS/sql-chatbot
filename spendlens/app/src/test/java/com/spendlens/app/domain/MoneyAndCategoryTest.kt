package com.spendlens.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyAndCategoryTest {

    @Test
    fun formatsWithRegionalGrouping() {
        assertEquals("₹1,23,456.78", CurrencyOption.INR.format(12_345_678))
        assertEquals("₹1,00,000", CurrencyOption.INR.format(10_000_000))
        assertEquals("₹999", CurrencyOption.INR.format(99_900))
        assertEquals("$1,234,567.89", CurrencyOption.USD.format(123_456_789))
        assertEquals("€0.50", CurrencyOption.EUR.format(50))
    }

    @Test
    fun compactAmounts() {
        assertEquals("₹1.5K", CurrencyOption.INR.compact(150_000))
        assertEquals("₹2.5L", CurrencyOption.INR.compact(25_000_000))
        assertEquals("₹1Cr", CurrencyOption.INR.compact(1_000_000_000))
        assertEquals("$3M", CurrencyOption.USD.compact(300_000_000))
        assertEquals("₹450", CurrencyOption.INR.compact(45_000))
    }

    @Test
    fun parsesUserInput() {
        assertEquals(125_050L, Money.parseInput("1,250.5"))
        assertEquals(9_900L, Money.parseInput("₹ 99"))
        assertNull(Money.parseInput(""))
        assertNull(Money.parseInput("1.2.3"))
        assertEquals("1250.50", Money.toInput(125_050))
        assertEquals("99", Money.toInput(9_900))
    }

    @Test
    fun classifiesMerchants() {
        assertEquals(Category.FOOD, CategoryClassifier.classify("Swiggy"))
        assertEquals(Category.SHOPPING, CategoryClassifier.classify("Decathlon Sports"))
        assertEquals(Category.HEALTH, CategoryClassifier.classify("Apollo Pharmacy"))
        assertEquals(Category.BILLS, CategoryClassifier.classify("Airtel Postpaid"))
        // "vi" (Vodafone Idea) must not match inside "Ravi".
        assertEquals(Category.TRANSFERS, CategoryClassifier.classify("Ravi Kumar", "UPI transaction ID 1234"))
        assertEquals(Category.FOOD, CategoryClassifier.classify("Some Name Pvt", "zomato-order@ptybl"))
        assertEquals(Category.OTHER, CategoryClassifier.classify("Acme Pvt Ltd"))
    }
}
