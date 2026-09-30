package com.spendlens.app.ui

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object Format {
    private val time = DateTimeFormatter.ofPattern("h:mm a")
    private val dayThisYear = DateTimeFormatter.ofPattern("EEE, d MMM")
    private val dayOtherYear = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
    private val fullDate = DateTimeFormatter.ofPattern("d MMM yyyy")
    private val shortDate = DateTimeFormatter.ofPattern("d MMM")

    fun time(dateTime: LocalDateTime): String = dateTime.format(time)

    /** 20 → "8 PM", 0 → "12 AM". */
    fun hour(h: Int): String = LocalTime.of(h, 0).format(hourFormat)
    private val hourFormat = DateTimeFormatter.ofPattern("h a")

    fun date(date: LocalDate): String = date.format(fullDate)

    fun shortDate(date: LocalDate): String = date.format(shortDate)

    fun dayHeader(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(if (date.year == today.year) dayThisYear else dayOtherYear)
    }

    fun relative(dateTime: LocalDateTime, today: LocalDate = LocalDate.now()): String {
        val date = dateTime.toLocalDate()
        return when (date) {
            today -> "Today, ${time(dateTime)}"
            today.minusDays(1) -> "Yesterday, ${time(dateTime)}"
            else -> "${date.format(if (date.year == today.year) shortDate else fullDate)}, ${time(dateTime)}"
        }
    }
}
