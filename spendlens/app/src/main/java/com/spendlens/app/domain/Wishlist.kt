package com.spendlens.app.domain

import java.time.LocalDate
import kotlin.math.roundToLong

/** What a saving goal can be picked from, with how long people usually save for each. */
enum class WishKind(val label: String, val months: Int) {
    PHONE("Phones", 6),
    BIKE("Bikes", 12),
    CAR("Cars", 36),
}

/** A thing to save for, with its approximate starting price in India (whole rupees). */
data class WishItem(val name: String, val kind: WishKind, val priceInr: Long)

/**
 * Popular phones, bikes and cars with approximate Indian starting prices (ex-showroom for
 * vehicles). Prices move, so the goal editor opens pre-filled and the amount stays editable.
 */
object WishCatalog {

    val items: List<WishItem> = listOf(
        WishItem("iPhone 17", WishKind.PHONE, 82_900),
        WishItem("iPhone 17 Pro", WishKind.PHONE, 1_34_900),
        WishItem("iPhone 17 Pro Max", WishKind.PHONE, 1_49_900),
        WishItem("iPhone Air", WishKind.PHONE, 1_19_900),
        WishItem("Samsung Galaxy S25 Ultra", WishKind.PHONE, 1_29_999),
        WishItem("Samsung Galaxy Z Fold7", WishKind.PHONE, 1_74_999),
        WishItem("Google Pixel 10 Pro", WishKind.PHONE, 1_09_999),
        WishItem("Google Pixel 10", WishKind.PHONE, 79_999),
        WishItem("OnePlus 13", WishKind.PHONE, 69_999),
        WishItem("Nothing Phone (3)", WishKind.PHONE, 79_999),
        WishItem("Samsung Galaxy A56", WishKind.PHONE, 41_999),

        WishItem("Royal Enfield Classic 350", WishKind.BIKE, 1_97_000),
        WishItem("Royal Enfield Hunter 350", WishKind.BIKE, 1_50_000),
        WishItem("Royal Enfield Himalayan 450", WishKind.BIKE, 2_85_000),
        WishItem("KTM 390 Duke", WishKind.BIKE, 2_97_000),
        WishItem("Yamaha R15 V4", WishKind.BIKE, 1_82_000),
        WishItem("Kawasaki Ninja 300", WishKind.BIKE, 3_17_000),
        WishItem("Bajaj Pulsar NS200", WishKind.BIKE, 1_57_000),
        WishItem("TVS Apache RTR 160 4V", WishKind.BIKE, 1_24_000),
        WishItem("Ather 450X", WishKind.BIKE, 1_47_000),
        WishItem("Ola S1 Pro", WishKind.BIKE, 1_35_000),
        WishItem("Honda Activa 6G", WishKind.BIKE, 78_000),
        WishItem("Hero Splendor+", WishKind.BIKE, 74_000),

        WishItem("Maruti Suzuki Swift", WishKind.CAR, 6_49_000),
        WishItem("Tata Nexon", WishKind.CAR, 8_00_000),
        WishItem("Mahindra XUV 3XO", WishKind.CAR, 7_99_000),
        WishItem("Tata Punch EV", WishKind.CAR, 9_99_000),
        WishItem("Hyundai Creta", WishKind.CAR, 11_00_000),
        WishItem("Kia Seltos", WishKind.CAR, 11_19_000),
        WishItem("Mahindra Thar Roxx", WishKind.CAR, 12_99_000),
        WishItem("Mahindra XUV700", WishKind.CAR, 13_99_000),
        WishItem("MG Windsor EV", WishKind.CAR, 13_99_000),
        WishItem("Toyota Innova Hycross", WishKind.CAR, 19_94_000),
        WishItem("Toyota Fortuner", WishKind.CAR, 33_78_000),
        WishItem("BMW 3 Series LWB", WishKind.CAR, 62_60_000),
    )

    fun of(kind: WishKind): List<WishItem> = items.filter { it.kind == kind }.sortedBy { it.priceInr }

    /** Rupees per unit of each currency — rough, only so other currencies see a ballpark. */
    private fun inrPer(currency: CurrencyOption): Double = when (currency) {
        CurrencyOption.INR -> 1.0
        CurrencyOption.USD -> 88.0
        CurrencyOption.EUR -> 102.0
        CurrencyOption.GBP -> 118.0
        CurrencyOption.AED -> 24.0
        CurrencyOption.SGD -> 68.0
        CurrencyOption.JPY -> 0.59
    }

    /** Price in [currency], minor units; converted prices are rounded to a tidy figure. */
    fun price(item: WishItem, currency: CurrencyOption): Long {
        if (currency == CurrencyOption.INR) return item.priceInr * 100
        val major = item.priceInr / inrPer(currency)
        val step = if (currency == CurrencyOption.JPY) 1_000.0 else 10.0
        return ((major / step).roundToLong() * step).toLong() * 100
    }

    /** What to put aside each month to get there in the kind's usual time. */
    fun perMonth(item: WishItem, currency: CurrencyOption): Long = (price(item, currency) / item.kind.months).ceilToWhole()

    /** A new goal for [item], due at the end of the month [WishKind.months] from now. */
    fun goalFor(item: WishItem, currency: CurrencyOption, today: LocalDate): Goal {
        val due = today.plusMonths(item.kind.months.toLong())
        return Goal(0, item.name, price(item, currency), 0, due.withDayOfMonth(due.lengthOfMonth()))
    }
}
