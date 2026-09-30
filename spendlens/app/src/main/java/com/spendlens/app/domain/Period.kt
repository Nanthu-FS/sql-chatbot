package com.spendlens.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class PeriodType(val label: String) { DAY("Day"), WEEK("Week"), MONTH("Month"), YEAR("Year") }

/** Weeks run Monday → Sunday everywhere in the app. */
val WEEK_START: DayOfWeek = DayOfWeek.MONDAY

data class Period(val type: PeriodType, val anchor: LocalDate) {

    val start: LocalDate
        get() = when (type) {
            PeriodType.DAY -> anchor
            PeriodType.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(WEEK_START))
            PeriodType.MONTH -> anchor.withDayOfMonth(1)
            PeriodType.YEAR -> anchor.withDayOfYear(1)
        }

    val endExclusive: LocalDate
        get() = when (type) {
            PeriodType.DAY -> start.plusDays(1)
            PeriodType.WEEK -> start.plusDays(7)
            PeriodType.MONTH -> start.plusMonths(1)
            PeriodType.YEAR -> start.plusYears(1)
        }

    val lengthInDays: Int get() = ChronoUnit.DAYS.between(start, endExclusive).toInt()

    fun shift(steps: Long): Period = copy(
        anchor = when (type) {
            PeriodType.DAY -> anchor.plusDays(steps)
            PeriodType.WEEK -> anchor.plusWeeks(steps)
            PeriodType.MONTH -> anchor.plusMonths(steps)
            PeriodType.YEAR -> anchor.plusYears(steps)
        },
    )

    operator fun contains(dateTime: LocalDateTime): Boolean = contains(dateTime.toLocalDate())

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && date.isBefore(endExclusive)

    fun isCurrent(today: LocalDate): Boolean = today in this

    fun isFuture(today: LocalDate): Boolean = start.isAfter(today)

    /** "Today" / "This week", "12 – 18 Aug" / "September 2026" / "2026". */
    fun title(today: LocalDate): String = when (type) {
        PeriodType.DAY -> when (anchor) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> anchor.format(if (anchor.year == today.year) DAY_FORMAT else DAY_YEAR_FORMAT)
        }
        PeriodType.WEEK -> when {
            isCurrent(today) -> "This week"
            shift(1).isCurrent(today) -> "Last week"
            else -> {
                val end = endExclusive.minusDays(1)
                val left = if (start.month == end.month) start.dayOfMonth.toString() else start.format(SHORT_FORMAT)
                "$left – ${end.format(if (end.year == today.year) SHORT_FORMAT else SHORT_YEAR_FORMAT)}"
            }
        }
        PeriodType.MONTH -> anchor.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + anchor.year
        PeriodType.YEAR -> anchor.year.toString()
    }

    /** Title that never says "This week"/"Today" — for comparisons and recaps. */
    fun label(today: LocalDate): String = when (type) {
        PeriodType.DAY -> anchor.format(if (anchor.year == today.year) DAY_FORMAT else DAY_YEAR_FORMAT)
        PeriodType.WEEK -> {
            val end = endExclusive.minusDays(1)
            val left = if (start.month == end.month) start.dayOfMonth.toString() else start.format(SHORT_FORMAT)
            "$left – ${end.format(if (end.year == today.year) SHORT_FORMAT else SHORT_YEAR_FORMAT)}"
        }
        PeriodType.MONTH -> anchor.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + anchor.year
        PeriodType.YEAR -> anchor.year.toString()
    }

    fun previousNoun(): String = when (type) {
        PeriodType.DAY -> "yesterday"
        PeriodType.WEEK -> "last week"
        PeriodType.MONTH -> "last month"
        PeriodType.YEAR -> "last year"
    }

    companion object {
        private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE, d MMM")
        private val DAY_YEAR_FORMAT = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
        private val SHORT_FORMAT = DateTimeFormatter.ofPattern("d MMM")
        private val SHORT_YEAR_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy")
    }
}
