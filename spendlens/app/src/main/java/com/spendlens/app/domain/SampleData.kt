package com.spendlens.app.domain

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

data class SamplePayment(
    val merchant: String,
    val category: Category,
    val amountMinor: Long,
    val dateTime: LocalDateTime,
    val app: String,
)

/**
 * Six months of believable city spending so every screen has something to show: daily coffee and food,
 * weekly groceries, monthly bills, busy weekend nights, a few quiet days, one unusually big purchase and
 * one double charge (to exercise the alerts).
 */
object SampleData {

    private data class Habit(val merchant: String, val category: Category, val min: Int, val max: Int, val chance: Double, val hours: IntRange)

    private val daily = listOf(
        Habit("Blue Tokai Coffee", Category.FOOD, 180, 420, 0.35, 8..11),
        Habit("Swiggy", Category.FOOD, 220, 780, 0.35, 12..22),
        Habit("Zomato", Category.FOOD, 250, 900, 0.18, 19..23),
        Habit("Uber", Category.TRANSPORT, 140, 620, 0.3, 8..21),
        Habit("Rapido", Category.TRANSPORT, 60, 180, 0.2, 8..20),
        Habit("Namma Metro", Category.TRANSPORT, 40, 90, 0.25, 8..19),
        Habit("Chai Point", Category.FOOD, 60, 160, 0.2, 15..18),
        Habit("Blinkit", Category.GROCERIES, 150, 900, 0.18, 9..22),
    )
    private val weekend = listOf(
        Habit("PVR Cinemas", Category.ENTERTAINMENT, 380, 1100, 0.3, 17..22),
        Habit("Toit Brewpub", Category.FOOD, 1200, 3400, 0.35, 20..23),
        Habit("Myntra", Category.SHOPPING, 699, 2999, 0.12, 11..22),
        Habit("Decathlon", Category.SHOPPING, 499, 2499, 0.06, 11..19),
        Habit("Priya Sharma", Category.TRANSFERS, 300, 2000, 0.15, 12..22),
    )
    private val apps = listOf("Google Pay", "PhonePe", "Paytm", "Google Pay")

    fun generate(today: LocalDate, days: Int = 180, seed: Int = 42): List<SamplePayment> {
        val rnd = Random(seed)
        val out = mutableListOf<SamplePayment>()
        fun at(date: LocalDate, hours: IntRange) = date.atTime(rnd.nextInt(hours.first, hours.last + 1), rnd.nextInt(60))
        fun add(h: Habit, date: LocalDate) {
            out += SamplePayment(h.merchant, h.category, rnd.nextInt(h.min, h.max + 1) * 100L, at(date, h.hours), apps[rnd.nextInt(apps.size)])
        }

        for (offset in days downTo 0) {
            val date = today.minusDays(offset.toLong())
            // Roughly one quiet day a week, plus a short run of them mid-way through.
            val quiet = offset != 0 && (rnd.nextDouble() < 0.12 || offset in 40..43)
            if (!quiet) {
                daily.forEach { if (rnd.nextDouble() < it.chance) add(it, date) }
                if (date.dayOfWeek.value >= 5) weekend.forEach { if (rnd.nextDouble() < it.chance) add(it, date) }
                if (date.dayOfWeek.value == 6) add(Habit("BigBasket", Category.GROCERIES, 900, 2600, 1.0, 10..13), date)
            }
            when (date.dayOfMonth) {
                1 -> out += SamplePayment("NoBroker Rent", Category.HOUSING, 18_000_00, date.atTime(10, 5), "PhonePe")
                5 -> out += SamplePayment("Airtel Postpaid", Category.BILLS, 599_00, date.atTime(9, 30), "Google Pay")
                9 -> out += SamplePayment("BESCOM Electricity", Category.BILLS, rnd.nextInt(900, 1900) * 100L, date.atTime(18, 10), "Paytm")
                12 -> out += SamplePayment("Netflix", Category.ENTERTAINMENT, 649_00, date.atTime(7, 0), "Google Pay")
                20 -> if (rnd.nextBoolean()) out += SamplePayment("Apollo Pharmacy", Category.HEALTH, rnd.nextInt(180, 1400) * 100L, date.atTime(19, 40), "PhonePe")
            }
        }
        // Make sure today has a little activity for the Day view.
        out += SamplePayment("Blue Tokai Coffee", Category.FOOD, 310_00, today.atTime(9, 12), "Google Pay")
        // An unusually large purchase a few days ago…
        out += SamplePayment("Croma", Category.SHOPPING, 38_999_00, today.minusDays(4).atTime(18, 25), "Paytm")
        // …and the same Swiggy order paid twice, two minutes apart.
        val twice = today.minusDays(1).atTime(21, 3)
        out += SamplePayment("Swiggy", Category.FOOD, 486_00, twice, "PhonePe")
        out += SamplePayment("Swiggy", Category.FOOD, 486_00, twice.plusMinutes(2), "PhonePe")
        return out.sortedBy { it.dateTime }
    }
}
