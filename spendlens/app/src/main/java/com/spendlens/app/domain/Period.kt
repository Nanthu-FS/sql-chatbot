package com.spendlens.app.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

enum class PeriodType(val label: String) { DAY("Day"), MONTH("Month"), YEAR("Year") }

data class Period(val type: PeriodType, val anchor: LocalDate) {

    val start: LocalDate
        get() = when (type) {
            PeriodType.DAY -> anchor
            PeriodType.MONTH -> anchor.withDayOfMonth(1)
            PeriodType.YEAR -> anchor.withDayOfYear(1)
        }

    val endExclusive: LocalDate
        get() = when (type) {
            PeriodType.DAY -> start.plusDays(1)
            PeriodType.MONTH -> start.plusMonths(1)
            PeriodType.YEAR -> start.plusYears(1)
        }

    fun shift(steps: Long): Period = copy(
        anchor = when (type) {
            PeriodType.DAY -> anchor.plusDays(steps)
            PeriodType.MONTH -> anchor.plusMonths(steps)
            PeriodType.YEAR -> anchor.plusYears(steps)
        },
    )

    operator fun contains(dateTime: LocalDateTime): Boolean {
        val date = dateTime.toLocalDate()
        return !date.isBefore(start) && date.isBefore(endExclusive)
    }

    fun isCurrent(today: LocalDate): Boolean = !today.isBefore(start) && today.isBefore(endExclusive)

    fun isFuture(today: LocalDate): Boolean = start.isAfter(today)

    /** "Today", "Yesterday", "Mon, 28 Sep" / "September 2026" / "2026". */
    fun title(today: LocalDate): String = when (type) {
        PeriodType.DAY -> when (anchor) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> anchor.format(if (anchor.year == today.year) DAY_FORMAT else DAY_YEAR_FORMAT)
        }
        PeriodType.MONTH -> anchor.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + anchor.year
        PeriodType.YEAR -> anchor.year.toString()
    }

    fun previousNoun(): String = when (type) {
        PeriodType.DAY -> "yesterday"
        PeriodType.MONTH -> "last month"
        PeriodType.YEAR -> "last year"
    }

    companion object {
        private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE, d MMM")
        private val DAY_YEAR_FORMAT = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
    }
}
