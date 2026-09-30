package com.spendlens.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class SignalsTest {

    private var id = 1L
    private fun t(amount: Long, at: LocalDateTime, merchant: String = "Swiggy", category: Category = Category.FOOD, ref: String? = null) =
        Txn(id++, amount * 100, merchant, category, at, reference = ref)

    private val today = LocalDate.of(2026, 9, 30) // a Wednesday

    @Test
    fun weekPeriod() {
        val week = Period(PeriodType.WEEK, today)
        assertEquals(LocalDate.of(2026, 9, 28), week.start)
        assertEquals(LocalDate.of(2026, 10, 5), week.endExclusive)
        assertEquals("This week", week.title(today))
        assertEquals("Last week", week.shift(-1).title(today))
        assertEquals("14 – 20 Sep", week.shift(-2).title(today))
        assertEquals("31 Aug – 6 Sep", week.shift(-4).title(today))
    }

    @Test
    fun weekDashboard() {
        val now = today.atTime(20, 0)
        val data = listOf(t(100, now.minusDays(2)), t(300, now.minusHours(1)), t(999, now.minusDays(9)))
        val d = Analytics.build(data, Period(PeriodType.WEEK, today), now, CurrencyOption.INR, monthlyBudget = 30_000_00)
        assertEquals(400_00L, d.total)
        assertEquals(7, d.bars.size)
        assertEquals(300_00L, d.bars[2].value)
        assertTrue(d.bars[2].isCurrent)
        assertEquals("Weekly budget", d.budget!!.label)
        assertEquals(30_000_00L * 7 / 30, d.budget!!.limit)
        assertEquals(5, d.budget!!.daysLeft)
        assertTrue(d.heatmap.isEmpty())
    }

    @Test
    fun forecastBlendsPaceAndHistory() {
        val now = LocalDateTime.of(2026, 9, 15, 12, 0)
        // History: ₹100/day for the 60 days before September.
        val history = (1..60).map { t(100, LocalDate.of(2026, 9, 1).minusDays(it.toLong()).atTime(13, 0)) }
        // This month so far: ₹200/day for 14.5 days.
        val current = (1..14).map { t(200, LocalDate.of(2026, 9, it).atTime(13, 0)) } + t(100, now.minusHours(1))
        val all = history + current
        val spent = current.sumOf { it.amountMinor }
        val f = Analytics.forecast(all, Period(PeriodType.MONTH, now.toLocalDate()), spent, now)!!
        // pace ≈ 200/day, history ≈ 100/day → blended ≈ 165/day over the remaining 15.5 days
        assertTrue("forecast $f", f in (spent + 15_5 * 150_00L / 10)..(spent + 15_5 * 185_00L / 10))
        assertNull(Analytics.forecast(all, Period(PeriodType.MONTH, LocalDate.of(2026, 8, 1)), 0, now))
        assertNull(Analytics.forecast(all, Period(PeriodType.DAY, now.toLocalDate()), spent, now))
    }

    @Test
    fun budgetShowsProjectedOverrun() {
        val now = LocalDateTime.of(2026, 9, 10, 12, 0)
        val data = (1..9).map { t(1000, LocalDate.of(2026, 9, it).atTime(12, 0)) }
        val d = Analytics.build(data, Period(PeriodType.MONTH, now.toLocalDate()), now, CurrencyOption.INR, monthlyBudget = 20_000_00)
        assertNotNull(d.forecast)
        assertTrue(d.budget!!.projectedOver!! > 0)
    }

    @Test
    fun streaks() {
        val data = listOf(
            t(100, LocalDateTime.of(2026, 9, 20, 10, 0)),
            t(100, LocalDateTime.of(2026, 9, 21, 10, 0)),
            t(100, LocalDateTime.of(2026, 9, 26, 10, 0)),
        )
        val s = StreakCalculator.of(data, today)
        assertEquals(4, s.current) // 27, 28, 29, 30
        assertEquals(4, s.longest) // 22–25 and 27–30 are both 4
        assertEquals(8, s.noSpendDaysThisMonth) // 22,23,24,25,27,28,29,30 (tracking began on the 20th)
        assertEquals(0, StreakCalculator.of(data + t(50, today.atTime(9, 0)), today).current)
    }

    @Test
    fun patternsCountLateNightsAsThePreviousEvening() {
        val fri = LocalDate.of(2026, 9, 25)
        val data = listOf(
            t(2000, fri.atTime(22, 30)),
            t(1500, fri.plusDays(1).atTime(1, 10)), // Saturday 1 AM → Friday night
            t(300, fri.plusDays(1).atTime(10, 0)),
        ) + (1..8).map { t(50, LocalDate.of(2026, 9, it).atTime(9, 0)) }
        val p = PatternFinder.of(data, LocalDate.of(2026, 9, 1), today)
        assertEquals(DayOfWeek.FRIDAY, p.topDay)
        assertEquals(DayPart.NIGHT, p.topPart)
        assertEquals("You spend most on Friday nights.", p.statement)
        assertEquals(22, p.busiestHour)
    }

    @Test
    fun anomalies() {
        val base = LocalDateTime.of(2026, 9, 1, 13, 0)
        val usual = (0 until 10).map { t(300 + it * 10L, base.plusDays(it.toLong())) }
        val big = t(2400, base.plusDays(20))
        val first = t(486, base.plusDays(25).withHour(21))
        val second = t(486, first.dateTime.plusMinutes(2))
        val sameRef = listOf(
            t(120, base.plusDays(26), merchant = "Uber", category = Category.TRANSPORT, ref = "426500000001"),
            t(120, base.plusDays(26).plusMinutes(5), merchant = "UBER INDIA", category = Category.TRANSPORT, ref = "426500000001"),
        )
        val found = AnomalyDetector.detect(usual + big + first + second + sameRef, LocalDateTime.of(2026, 9, 30, 12, 0), CurrencyOption.INR)
        assertTrue(found.any { it.kind == AnomalyKind.UNUSUAL_AMOUNT && it.txn.id == big.id })
        assertTrue(found.any { it.kind == AnomalyKind.POSSIBLE_DUPLICATE && it.txn.id == second.id && it.relatedId == first.id })
        assertTrue(found.any { it.kind == AnomalyKind.POSSIBLE_DUPLICATE && it.txn.id == sameRef[1].id })
        assertTrue(found.none { it.txn.id in usual.map(Txn::id) })
    }

    @Test
    fun merchantKeys() {
        assertEquals(MerchantNames.key("SWIGGY"), MerchantNames.key("Swiggy Ltd"))
        assertEquals("swiggy", MerchantNames.key("swiggy@icici"))
    }

    @Test
    fun trends() {
        val data = (4..9).flatMap { m -> listOf(t(100L * m, LocalDate.of(2026, m, 10).atTime(12, 0))) }
        val trend = TrendCalculator.of(data, YearMonth.of(2026, 9), today)[Category.FOOD]!!
        assertEquals(listOf(40000L, 50000L, 60000L, 70000L, 80000L, 90000L), trend.monthly)
        // September is complete enough (30th): 900 vs avg(600,700,800) = +28.6%
        assertEquals(0.2857f, trend.change!!, 0.01f)
    }

    @Test
    fun compare() {
        val data = listOf(
            t(1000, LocalDateTime.of(2026, 8, 2, 10, 0)),
            t(500, LocalDateTime.of(2026, 8, 20, 10, 0), merchant = "Uber", category = Category.TRANSPORT),
            t(3000, LocalDateTime.of(2026, 9, 3, 10, 0)),
        )
        val c = Comparer.compare(data, Period(PeriodType.MONTH, LocalDate.of(2026, 8, 1)), Period(PeriodType.MONTH, today), today)
        assertEquals(1500_00L, c.a.total)
        assertEquals(3000_00L, c.b.total)
        assertEquals(1f, c.change!!, 0.001f)
        assertEquals(31, c.a.cumulative.size)
        assertEquals(30, c.b.cumulative.size)
        assertEquals(Category.FOOD, c.rows.first().category)
        assertEquals(-1f, c.rows.first { it.category == Category.TRANSPORT }.change!!, 0.001f)
    }

    @Test
    fun wrap() {
        val data = listOf(
            t(1000, LocalDateTime.of(2026, 9, 2, 20, 10)),
            t(200, LocalDateTime.of(2026, 9, 2, 20, 40), merchant = "Chai Point"),
            t(4000, LocalDateTime.of(2026, 9, 12, 21, 5), merchant = "Myntra", category = Category.SHOPPING),
            t(900, LocalDateTime.of(2026, 8, 5, 12, 0)),
        )
        val w = WrapBuilder.of(data, Period(PeriodType.MONTH, today), today)
        assertEquals(5200_00L, w.total)
        assertEquals(3, w.count)
        assertEquals("Myntra", w.topPlaces.first().name)
        assertEquals(Category.SHOPPING, w.topCategory!!.category)
        assertEquals(LocalDate.of(2026, 9, 12), w.biggestDay!!.first)
        assertEquals(21, w.busiestHour)
        assertEquals(28, w.noSpendDays)
        assertEquals(18, w.longestStreak) // 13th–30th
        assertEquals(4.777f, w.change!!, 0.01f)
    }

    @Test
    fun goals() {
        val goal = Goal(1, "Goa trip", targetMinor = 60_000_00, savedMinor = 12_000_00, deadline = LocalDate.of(2026, 12, 31))
        val plan = GoalPlanner.plan(goal, monthlyIncome = 80_000_00, spentThisMonth = 40_000_00, forecastThisMonth = 55_000_00, today = today)
        assertEquals(4, plan.monthsLeft) // Sep, Oct, Nov, Dec
        assertEquals(12_000_00L, plan.neededPerMonth)
        assertEquals(25_000_00L, plan.projectedLeftover)
        assertEquals(12_000_00L, plan.suggestion)
        assertEquals(true, plan.onTrack)
        assertEquals(0.2f, plan.progress, 0.001f)

        val tight = GoalPlanner.plan(goal, 60_000_00, 50_000_00, 58_000_00, today)
        assertEquals(2_000_00L, tight.suggestion)
        assertEquals(false, tight.onTrack)

        val noIncome = GoalPlanner.plan(goal.copy(deadline = null), null, 0, null, today)
        assertEquals(0L, noIncome.suggestion)
        assertNull(noIncome.onTrack)
    }

    @Test
    fun sampleDataShowcasesEverything() {
        val samples = SampleData.generate(today)
        val txns = samples.mapIndexed { i, s -> Txn(i + 1L, s.amountMinor, s.merchant, s.category, s.dateTime, s.app) }
        assertTrue(samples.size > 300)
        assertTrue(samples.all { !it.dateTime.toLocalDate().isAfter(today) })
        assertTrue(samples.any { it.dateTime.toLocalDate() == today })
        val found = AnomalyDetector.detect(txns, today.atTime(23, 0), CurrencyOption.INR)
        assertTrue(found.any { it.kind == AnomalyKind.UNUSUAL_AMOUNT && it.txn.merchant == "Croma" })
        assertTrue(found.any { it.kind == AnomalyKind.POSSIBLE_DUPLICATE && it.txn.merchant == "Swiggy" })
        assertEquals(samples, SampleData.generate(today)) // deterministic
        assertNotNull(PatternFinder.of(txns, today.minusDays(90), today).statement)
    }

    @Test
    fun sampleDataStaysInThePast() {
        val now = today.atTime(7, 30)
        val samples = SampleData.generate(today, now = now)
        assertTrue(samples.none { it.dateTime.isAfter(now) })
        assertTrue(samples.any { it.dateTime.toLocalDate() == today && it.merchant == "Blue Tokai Coffee" })
    }

    @Test
    fun changeLabels() {
        assertEquals("+24%", percentLabel(0.24f))
        assertEquals("−8%", percentLabel(-0.08f))
        assertEquals("3.4×", percentLabel(2.4f))
        assertEquals("17×", percentLabel(16.44f))
    }

    @Test
    fun wholeUnits() {
        assertEquals(1_380_00L, 1_379_72L.roundToWhole())
        assertEquals(1_601_00L, 1_601_20L.floorToWhole())
        assertEquals(1_602_00L, 1_601_20L.ceilToWhole())
        assertEquals(-15_695_00L, (-15_694_86L).floorToWhole())
    }
}
