package com.spendlens.app.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class BarEntry(
    val axisLabel: String,
    val tooltipLabel: String,
    val value: Long,
    val isCurrent: Boolean,
    val date: LocalDate? = null,
)

data class CategorySlice(val category: Category, val amountMinor: Long, val fraction: Float, val count: Int)

data class MerchantStat(val name: String, val category: Category, val amountMinor: Long, val count: Int, val fraction: Float)

data class HeatDay(val date: LocalDate, val amountMinor: Long, val intensity: Float)

enum class InsightIcon { TREND_UP, TREND_DOWN, CATEGORY, CALENDAR, STAR, REPEAT, WEEKEND }

data class Insight(val icon: InsightIcon, val title: String, val body: String, val category: Category? = null)

data class BudgetStatus(
    val label: String,
    val limit: Long,
    val spent: Long,
    val fraction: Float,
    val remaining: Long,
    val dailyAllowance: Long?,
    val daysLeft: Int?,
)

data class Dashboard(
    val period: Period,
    val title: String,
    val total: Long,
    val previousTotal: Long,
    val change: Float?,
    val comparisonLabel: String,
    val count: Int,
    val averageLabel: String,
    val average: Long,
    val largest: Txn?,
    val barsTitle: String,
    val bars: List<BarEntry>,
    val cumulative: List<Long>,
    val heatmap: List<HeatDay>,
    val categories: List<CategorySlice>,
    val merchants: List<MerchantStat>,
    val transactions: List<Txn>,
    val budget: BudgetStatus?,
    val insights: List<Insight>,
    val isCurrent: Boolean,
    val canGoForward: Boolean,
    val hasAnyData: Boolean,
)

object Analytics {

    private val tooltipDay = DateTimeFormatter.ofPattern("EEE, d MMM")
    private val hourFormat = DateTimeFormatter.ofPattern("h a")

    fun build(
        all: List<Txn>,
        period: Period,
        now: LocalDateTime,
        currency: CurrencyOption,
        monthlyBudget: Long? = null,
    ): Dashboard {
        val today = now.toLocalDate()
        val inPeriod = all.filter { it.dateTime in period }.sortedByDescending { it.dateTime }
        val total = inPeriod.sumOf { it.amountMinor }
        val isCurrent = period.isCurrent(today)

        // Compare like with like: a half-finished month against the same slice of last month.
        val previous = period.shift(-1)
        val prevStart = previous.start.atStartOfDay()
        val prevEnd = if (isCurrent) {
            minOf(prevStart.plus(Duration.between(period.start.atStartOfDay(), now)), previous.endExclusive.atStartOfDay())
        } else {
            previous.endExclusive.atStartOfDay()
        }
        val previousTotal = all.filter { !it.dateTime.isBefore(prevStart) && it.dateTime.isBefore(prevEnd) }.sumOf { it.amountMinor }
        val change = if (previousTotal > 0) (total - previousTotal).toFloat() / previousTotal else null
        val comparisonLabel = when {
            isCurrent -> when (period.type) {
                PeriodType.DAY -> "vs this time yesterday"
                PeriodType.MONTH -> "vs same time last month"
                PeriodType.YEAR -> "vs same time last year"
            }
            else -> when (period.type) {
                PeriodType.DAY -> "vs previous day"
                PeriodType.MONTH -> "vs " + previous.anchor.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
                PeriodType.YEAR -> "vs ${previous.anchor.year}"
            }
        }

        val (averageLabel, average) = when (period.type) {
            PeriodType.DAY -> "Avg / payment" to if (inPeriod.isEmpty()) 0L else total / inPeriod.size
            PeriodType.MONTH -> {
                val days = if (isCurrent) today.dayOfMonth else period.start.lengthOfMonth()
                "Avg / day" to total / days.coerceAtLeast(1)
            }
            PeriodType.YEAR -> {
                val months = if (isCurrent) today.monthValue else 12
                "Avg / month" to total / months.coerceAtLeast(1)
            }
        }

        val bars = bars(period, inPeriod, now)
        val lastIndex = when {
            !isCurrent -> bars.lastIndex
            else -> bars.indexOfFirst { it.isCurrent }.takeIf { it >= 0 } ?: bars.lastIndex
        }
        var running = 0L
        val cumulative = bars.take(lastIndex + 1).map { running += it.value; running }

        val categories = inPeriod.groupBy { it.category }
            .map { (category, txns) ->
                val sum = txns.sumOf { it.amountMinor }
                CategorySlice(category, sum, if (total > 0) sum.toFloat() / total else 0f, txns.size)
            }
            .sortedByDescending { it.amountMinor }

        val merchants = inPeriod.groupBy { it.merchant.trim().lowercase() }
            .map { (_, txns) ->
                val sum = txns.sumOf { it.amountMinor }
                MerchantStat(
                    name = txns.first().merchant.trim(),
                    category = txns.groupingBy { it.category }.eachCount().maxByOrNull { it.value }!!.key,
                    amountMinor = sum,
                    count = txns.size,
                    fraction = if (total > 0) sum.toFloat() / total else 0f,
                )
            }
            .sortedByDescending { it.amountMinor }
            .take(5)

        val budget = monthlyBudget?.takeIf { it > 0 }?.let { budgetStatus(it, period, total, today) }

        return Dashboard(
            period = period,
            title = period.title(today),
            total = total,
            previousTotal = previousTotal,
            change = change,
            comparisonLabel = comparisonLabel,
            count = inPeriod.size,
            averageLabel = averageLabel,
            average = average,
            largest = inPeriod.maxByOrNull { it.amountMinor },
            barsTitle = when (period.type) {
                PeriodType.DAY -> "Spending by hour"
                PeriodType.MONTH -> "Spending by day"
                PeriodType.YEAR -> "Spending by month"
            },
            bars = bars,
            cumulative = cumulative,
            heatmap = heatmap(period, inPeriod),
            categories = categories,
            merchants = merchants,
            transactions = inPeriod,
            budget = budget,
            insights = insights(period, inPeriod, total, change, comparisonLabel, bars, categories, currency),
            isCurrent = isCurrent,
            canGoForward = !period.shift(1).isFuture(today),
            hasAnyData = all.isNotEmpty(),
        )
    }

    private fun bars(period: Period, txns: List<Txn>, now: LocalDateTime): List<BarEntry> {
        val today = now.toLocalDate()
        return when (period.type) {
            PeriodType.DAY -> {
                val byHour = LongArray(24)
                txns.forEach { byHour[it.dateTime.hour] += it.amountMinor }
                (0 until 24).map { hour ->
                    val time = period.start.atTime(hour, 0)
                    BarEntry(
                        axisLabel = when (hour) { 0 -> "12a"; 6 -> "6a"; 12 -> "12p"; 18 -> "6p"; else -> "" },
                        tooltipLabel = time.format(hourFormat) + " – " + time.plusHours(1).format(hourFormat),
                        value = byHour[hour],
                        isCurrent = period.anchor == today && hour == now.hour,
                    )
                }
            }
            PeriodType.MONTH -> {
                val length = period.start.lengthOfMonth()
                val byDay = LongArray(length + 1)
                txns.forEach { byDay[it.dateTime.dayOfMonth] += it.amountMinor }
                (1..length).map { day ->
                    val date = period.start.withDayOfMonth(day)
                    BarEntry(
                        axisLabel = if (day == 1 || day % 7 == 1) day.toString() else "",
                        tooltipLabel = date.format(tooltipDay),
                        value = byDay[day],
                        isCurrent = date == today,
                        date = date,
                    )
                }
            }
            PeriodType.YEAR -> {
                val byMonth = LongArray(13)
                txns.forEach { byMonth[it.dateTime.monthValue] += it.amountMinor }
                (1..12).map { month ->
                    val date = period.start.withMonth(month)
                    val name = date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    BarEntry(
                        axisLabel = name.take(1),
                        tooltipLabel = date.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + date.year,
                        value = byMonth[month],
                        isCurrent = today.year == date.year && today.monthValue == month,
                        date = date,
                    )
                }
            }
        }
    }

    private fun heatmap(period: Period, txns: List<Txn>): List<HeatDay> {
        if (period.type == PeriodType.DAY) return emptyList()
        val byDate = txns.groupBy { it.dateTime.toLocalDate() }.mapValues { (_, v) -> v.sumOf { it.amountMinor } }
        val max = byDate.values.maxOrNull() ?: 0L
        val days = mutableListOf<HeatDay>()
        var date = period.start
        while (date.isBefore(period.endExclusive)) {
            val value = byDate[date] ?: 0L
            days += HeatDay(date, value, if (max > 0) sqrt(value.toDouble() / max).toFloat() else 0f)
            date = date.plusDays(1)
        }
        return days
    }

    private fun budgetStatus(monthly: Long, period: Period, spent: Long, today: LocalDate): BudgetStatus {
        val limit = when (period.type) {
            PeriodType.DAY -> monthly / period.anchor.lengthOfMonth()
            PeriodType.MONTH -> monthly
            PeriodType.YEAR -> monthly * 12
        }
        val remaining = limit - spent
        val daysLeft = if (period.type == PeriodType.MONTH && period.isCurrent(today)) {
            period.start.lengthOfMonth() - today.dayOfMonth + 1
        } else {
            null
        }
        return BudgetStatus(
            label = when (period.type) {
                PeriodType.DAY -> "Daily budget"
                PeriodType.MONTH -> "Monthly budget"
                PeriodType.YEAR -> "Yearly budget"
            },
            limit = limit,
            spent = spent,
            fraction = if (limit > 0) spent.toFloat() / limit else 0f,
            remaining = remaining,
            dailyAllowance = daysLeft?.let { remaining.coerceAtLeast(0) / it },
            daysLeft = daysLeft,
        )
    }

    private fun insights(
        period: Period,
        txns: List<Txn>,
        total: Long,
        change: Float?,
        comparisonLabel: String,
        bars: List<BarEntry>,
        categories: List<CategorySlice>,
        currency: CurrencyOption,
    ): List<Insight> {
        if (txns.isEmpty()) return emptyList()
        val out = mutableListOf<Insight>()

        if (change != null && abs(change) >= 0.05f) {
            val pct = (abs(change) * 100).roundToInt()
            out += if (change < 0) {
                Insight(InsightIcon.TREND_DOWN, "$pct% less", "You've spent less $comparisonLabel. Nice!")
            } else {
                Insight(InsightIcon.TREND_UP, "$pct% more", "Spending is up $comparisonLabel.")
            }
        }

        categories.firstOrNull()?.takeIf { categories.size > 1 || txns.size > 1 }?.let { top ->
            val pct = (top.fraction * 100).roundToInt()
            out += Insight(InsightIcon.CATEGORY, top.category.label, "$pct% of your spending goes here.", top.category)
        }

        if (period.type != PeriodType.DAY) {
            bars.filter { it.value > 0 }.maxByOrNull { it.value }?.takeIf { bars.count { b -> b.value > 0 } > 1 }?.let { peak ->
                val what = if (period.type == PeriodType.MONTH) "Biggest day" else "Biggest month"
                out += Insight(InsightIcon.CALENDAR, what, "${peak.tooltipLabel} · ${currency.format(peak.value)}")
            }
        }

        if (period.type != PeriodType.DAY && txns.size >= 5) {
            val weekend = txns.filter { it.dateTime.dayOfWeek == DayOfWeek.SATURDAY || it.dateTime.dayOfWeek == DayOfWeek.SUNDAY }
            val weekday = txns - weekend.toSet()
            val weekendDays = weekend.map { it.dateTime.toLocalDate() }.distinct().size
            val weekdayDays = weekday.map { it.dateTime.toLocalDate() }.distinct().size
            if (weekendDays > 0 && weekdayDays > 0) {
                val weekendAvg = weekend.sumOf { it.amountMinor }.toDouble() / weekendDays
                val weekdayAvg = weekday.sumOf { it.amountMinor }.toDouble() / weekdayDays
                if (weekendAvg > weekdayAvg * 1.3) {
                    val ratio = ((weekendAvg / weekdayAvg) * 10).roundToInt() / 10.0
                    out += Insight(InsightIcon.WEEKEND, "Weekend spender", "You spend ${ratio}× more per day on weekends.")
                }
            }
        }

        txns.groupBy { it.merchant.trim().lowercase() }.values
            .maxByOrNull { it.size }
            ?.takeIf { it.size >= 3 }
            ?.let { group ->
                val name = group.first().merchant.trim()
                out += Insight(InsightIcon.REPEAT, "Regular: $name", "${group.size} payments · ${currency.format(group.sumOf { it.amountMinor })}", group.first().category)
            }

        txns.maxByOrNull { it.amountMinor }?.takeIf { txns.size > 1 }?.let { big ->
            val share = if (total > 0) (big.amountMinor * 100 / total) else 0
            out += Insight(InsightIcon.STAR, "Largest payment", "${currency.format(big.amountMinor)} at ${big.merchant} · $share% of total", big.category)
        }

        return out.take(4)
    }
}
