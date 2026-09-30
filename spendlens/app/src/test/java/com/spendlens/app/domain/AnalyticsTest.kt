package com.spendlens.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class AnalyticsTest {

    private val now = LocalDateTime.of(2026, 9, 15, 18, 30)
    private var nextId = 1L

    private fun txn(amount: Long, at: LocalDateTime, merchant: String = "Swiggy", category: Category = Category.FOOD) =
        Txn(nextId++, amount * 100, merchant, category, at)

    private val data = listOf(
        txn(500, LocalDateTime.of(2026, 9, 15, 9, 0)),
        txn(1500, LocalDateTime.of(2026, 9, 15, 13, 0), "Uber", Category.TRANSPORT),
        txn(2000, LocalDateTime.of(2026, 9, 12, 20, 0), "Myntra", Category.SHOPPING),
        txn(300, LocalDateTime.of(2026, 9, 1, 8, 0)),
        txn(1000, LocalDateTime.of(2026, 8, 10, 8, 0)), // inside "same time last month"
        txn(9000, LocalDateTime.of(2026, 8, 25, 8, 0)), // after it
        txn(700, LocalDateTime.of(2025, 12, 31, 23, 59)),
    )

    @Test
    fun monthTotalsAndComparison() {
        val d = Analytics.build(data, Period(PeriodType.MONTH, now.toLocalDate()), now, CurrencyOption.INR, monthlyBudget = 10_000_00)
        assertEquals(4300_00L, d.total)
        assertEquals(4, d.count)
        assertEquals(1000_00L, d.previousTotal)
        assertEquals(3.3f, d.change!!, 0.001f)
        assertEquals(30, d.bars.size)
        assertEquals(2000_00L, d.bars[14].value)
        assertTrue(d.bars[14].isCurrent)
        assertEquals(15, d.cumulative.size)
        assertEquals(4300_00L, d.cumulative.last())
        assertEquals(Category.SHOPPING, d.categories.first().category)
        assertEquals(30, d.heatmap.size)
        assertEquals(1f, d.heatmap.first { it.date == LocalDate.of(2026, 9, 15) }.intensity, 0.0001f)
        val budget = d.budget!!
        assertEquals(5700_00L, budget.remaining)
        assertEquals(16, budget.daysLeft)
        assertEquals(356_00L, budget.dailyAllowance) // ₹5,700 over 16 days, in whole rupees
        assertFalse(d.canGoForward)
        assertEquals("Avg / day", d.averageLabel)
        assertEquals(4300_00L / 15, d.average)
    }

    @Test
    fun dayAndYearViews() {
        val day = Analytics.build(data, Period(PeriodType.DAY, now.toLocalDate()), now, CurrencyOption.INR)
        assertEquals(2000_00L, day.total)
        assertEquals(24, day.bars.size)
        assertEquals(1500_00L, day.bars[13].value)
        assertEquals("Today", day.title)
        assertTrue(day.heatmap.isEmpty())

        val year = Analytics.build(data, Period(PeriodType.YEAR, now.toLocalDate()), now, CurrencyOption.INR)
        assertEquals(14300_00L, year.total)
        assertEquals(12, year.bars.size)
        assertEquals(10000_00L, year.bars[7].value)
        assertEquals(365, year.heatmap.size)
        assertEquals(0L, year.previousTotal) // Dec 2025 is after "same time last year"

        val past = Analytics.build(data, Period(PeriodType.MONTH, LocalDate.of(2026, 8, 3)), now, CurrencyOption.INR)
        assertTrue(past.canGoForward)
        assertEquals(10000_00L, past.total)
        assertEquals(31, past.cumulative.size)
    }

    @Test
    fun emptyPeriod() {
        val d = Analytics.build(data, Period(PeriodType.MONTH, LocalDate.of(2026, 3, 1)), now, CurrencyOption.INR)
        assertEquals(0L, d.total)
        assertTrue(d.insights.isEmpty())
        assertTrue(d.hasAnyData)
    }
}
