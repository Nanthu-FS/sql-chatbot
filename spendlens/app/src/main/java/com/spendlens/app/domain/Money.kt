package com.spendlens.app.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * Private mode: every formatted amount reads "₹•••••" — on screen, in notifications and on the
 * widget. Snapshot state, so anything showing an amount redraws when it flips.
 */
object Privacy {
    var hidden by mutableStateOf(false)
    const val MASK = "•••••"
}

enum class CurrencyOption(val code: String, val symbol: String, val label: String, private val indianGrouping: Boolean = false) {
    INR("INR", "₹", "Indian Rupee", indianGrouping = true),
    USD("USD", "$", "US Dollar"),
    EUR("EUR", "€", "Euro"),
    GBP("GBP", "£", "British Pound"),
    AED("AED", "AED ", "UAE Dirham"),
    SGD("SGD", "S$", "Singapore Dollar"),
    JPY("JPY", "¥", "Japanese Yen");

    /** Whole amounts drop the decimals ("₹1,250"); anything else shows two ("₹1,250.50"). */
    fun format(amountMinor: Long, forceDecimals: Boolean = false): String {
        if (Privacy.hidden) return symbol + Privacy.MASK
        val value = abs(amountMinor)
        val grouped = groupDigits((value / 100).toString())
        val fraction = if (value % 100 == 0L && !forceDecimals) "" else "." + (value % 100).toString().padStart(2, '0')
        return (if (amountMinor < 0) "-" else "") + symbol + grouped + fraction
    }

    /** Western grouping (1,234,567) or Indian lakh/crore grouping (12,34,567). */
    private fun groupDigits(digits: String): String {
        if (digits.length <= 3) return digits
        val size = if (indianGrouping) 2 else 3
        val groups = ArrayDeque<String>()
        groups.addFirst(digits.takeLast(3))
        var rest = digits.dropLast(3)
        while (rest.length > size) {
            groups.addFirst(rest.takeLast(size))
            rest = rest.dropLast(size)
        }
        if (rest.isNotEmpty()) groups.addFirst(rest)
        return groups.joinToString(",")
    }

    /** Axis-friendly short form: ₹1.2K, ₹3.4L, ₹1.1Cr / $1.2K, $3.4M. */
    fun compact(amountMinor: Long): String {
        if (Privacy.hidden) return symbol + "•••"
        val major = abs(amountMinor) / 100.0
        val (divisor, suffix) = when {
            indianGrouping && major >= 1e7 -> 1e7 to "Cr"
            indianGrouping && major >= 1e5 -> 1e5 to "L"
            !indianGrouping && major >= 1e9 -> 1e9 to "B"
            !indianGrouping && major >= 1e6 -> 1e6 to "M"
            major >= 1e3 -> 1e3 to "K"
            else -> 1.0 to ""
        }
        val scaled = major / divisor
        val number = when {
            suffix.isEmpty() -> scaled.toLong().toString()
            scaled >= 100 -> scaled.toLong().toString()
            else -> BigDecimal(scaled).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        }
        return (if (amountMinor < 0) "-" else "") + symbol + number + suffix
    }

    companion object {
        fun fromCode(code: String?): CurrencyOption = entries.firstOrNull { it.code == code } ?: INR
    }
}

object Money {
    /** Parses what the user typed ("1,250.5", "₹ 99") into minor units, or null. */
    fun parseInput(text: String): Long? {
        val cleaned = text.filter { it.isDigit() || it == '.' }
        if (cleaned.isEmpty() || cleaned.count { it == '.' } > 1) return null
        return runCatching {
            BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        }.getOrNull()?.takeIf { it > 0 }
    }

    /** Minor units back to an editable string ("1250.5" → "1250.50", "1250.00" → "1250"). */
    fun toInput(amountMinor: Long): String {
        val value = BigDecimal.valueOf(amountMinor).movePointLeft(2)
        return if (amountMinor % 100 == 0L) value.setScale(0).toPlainString() else value.setScale(2).toPlainString()
    }
}

/** Derived amounts (averages, allowances, suggestions) are shown in whole currency units. */
fun Long.roundToWhole(): Long = Math.floorDiv(this + 50, 100L) * 100

fun Long.floorToWhole(): Long = Math.floorDiv(this, 100L) * 100

fun Long.ceilToWhole(): Long = -Math.floorDiv(-this, 100L) * 100
