package com.spendlens.app.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------------------------------------------------------------- no-spend streaks

data class Streaks(
    /** Consecutive days without a payment, ending today (0 if you've paid for something today). */
    val current: Int,
    val longest: Int,
    val noSpendDaysThisMonth: Int,
    val trackedSince: LocalDate?,
)

object StreakCalculator {
    fun of(txns: List<Txn>, today: LocalDate): Streaks {
        val first = txns.minOfOrNull { it.dateTime.toLocalDate() } ?: return Streaks(0, 0, 0, null)
        val spendDays = txns.map { it.dateTime.toLocalDate() }.toHashSet()

        var current = 0
        var day = today
        while (!day.isBefore(first) && day !in spendDays) {
            current++
            day = day.minusDays(1)
        }

        var longest = 0
        var run = 0
        day = first
        while (!day.isAfter(today)) {
            if (day in spendDays) run = 0 else longest = maxOf(longest, ++run)
            day = day.plusDays(1)
        }

        val monthStart = maxOf(today.withDayOfMonth(1), first)
        var noSpend = 0
        day = monthStart
        while (!day.isAfter(today)) {
            if (day !in spendDays) noSpend++
            day = day.plusDays(1)
        }
        return Streaks(current, longest, noSpend, first)
    }
}

// ---------------------------------------------------------------- when you spend

enum class DayPart(val label: String, val short: String) {
    MORNING("mornings", "Morn"),
    AFTERNOON("afternoons", "Aftn"),
    EVENING("evenings", "Eve"),
    NIGHT("nights", "Night");

    companion object {
        fun of(hour: Int): DayPart = when (hour) {
            in 5..11 -> MORNING
            in 12..16 -> AFTERNOON
            in 17..20 -> EVENING
            else -> NIGHT
        }
    }
}

data class SpendPatterns(
    /** [dayOfWeek.ordinal (Mon=0)][DayPart.ordinal] → total spent. */
    val grid: List<List<Long>>,
    val max: Long,
    val topDay: DayOfWeek?,
    val topPart: DayPart?,
    val busiestHour: Int?,
    val hourTotals: List<Long>,
    val weekdayTotals: List<Long>,
    val sampleSize: Int,
) {
    /** "You spend most on Friday nights." — only when there's enough to say it. */
    val statement: String?
        get() {
            val day = topDay ?: return null
            val part = topPart ?: return null
            if (sampleSize < 8) return null
            return "You spend most on ${day.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${part.label}."
        }
}

object PatternFinder {
    /** Payments after midnight count toward the previous evening — 1 AM Saturday is still "Friday night". */
    private fun attribute(at: LocalDateTime): Pair<DayOfWeek, DayPart> {
        val part = DayPart.of(at.hour)
        val day = if (part == DayPart.NIGHT && at.hour < 5) at.minusDays(1).dayOfWeek else at.dayOfWeek
        return day to part
    }

    fun of(txns: List<Txn>, from: LocalDate, until: LocalDate): SpendPatterns {
        val window = txns.filter { val d = it.dateTime.toLocalDate(); !d.isBefore(from) && !d.isAfter(until) }
        val grid = List(7) { LongArray(4) }
        val hours = LongArray(24)
        val weekdays = LongArray(7)
        window.forEach { t ->
            val (day, part) = attribute(t.dateTime)
            grid[day.value - 1][part.ordinal] += t.amountMinor
            hours[t.dateTime.hour] += t.amountMinor
            weekdays[day.value - 1] += t.amountMinor
        }
        var best = -1L
        var bestDay: DayOfWeek? = null
        var bestPart: DayPart? = null
        grid.forEachIndexed { d, parts ->
            parts.forEachIndexed { p, v ->
                if (v > best) {
                    best = v
                    bestDay = DayOfWeek.of(d + 1)
                    bestPart = DayPart.entries[p]
                }
            }
        }
        val busiest = hours.indices.maxByOrNull { hours[it] }?.takeIf { hours[it] > 0 }
        return SpendPatterns(
            grid = grid.map { it.toList() },
            max = best.coerceAtLeast(0),
            topDay = bestDay.takeIf { best > 0 },
            topPart = bestPart.takeIf { best > 0 },
            busiestHour = busiest,
            hourTotals = hours.toList(),
            weekdayTotals = weekdays.toList(),
            sampleSize = window.size,
        )
    }
}

// ---------------------------------------------------------------- anomalies

enum class AnomalyKind { UNUSUAL_AMOUNT, POSSIBLE_DUPLICATE }

data class Anomaly(
    val txn: Txn,
    val kind: AnomalyKind,
    val title: String,
    val detail: String,
    val relatedId: Long? = null,
) {
    /** Stable key used to remember that the user dismissed it. */
    val key: String get() = "${kind.name}:${txn.id}"
}

object AnomalyDetector {

    private const val MIN_SAMPLES = 5
    private const val MIN_AMOUNT = 200_00L

    fun detect(all: List<Txn>, now: LocalDateTime, currency: CurrencyOption, lookbackDays: Long = 45): List<Anomaly> {
        val since = now.minusDays(lookbackDays)
        val recent = all.filter { !it.dateTime.isBefore(since) && !it.dateTime.isAfter(now) }
        val out = mutableListOf<Anomaly>()

        // Unusually large for its category: well above both the typical and the upper-normal payment.
        recent.forEach { t ->
            if (t.amountMinor < MIN_AMOUNT) return@forEach
            val history = all.filter {
                it.id != t.id && it.category == t.category &&
                    it.dateTime.isBefore(t.dateTime) && !it.dateTime.isBefore(t.dateTime.minusDays(180))
            }.map { it.amountMinor }.sorted()
            if (history.size < MIN_SAMPLES) return@forEach
            val median = percentile(history, 0.5)
            val p75 = percentile(history, 0.75)
            if (median > 0 && t.amountMinor >= 3 * median && t.amountMinor >= 2 * p75) {
                val times = t.amountMinor.toDouble() / median
                out += Anomaly(
                    txn = t,
                    kind = AnomalyKind.UNUSUAL_AMOUNT,
                    title = "Unusually large",
                    detail = "${"%.1f".format(Locale.US, times)}× your usual ${t.category.label} payment (${currency.format(median)})",
                )
            }
        }

        // Same payee, same amount, minutes apart — or the same UPI reference twice.
        val sorted = recent.sortedBy { it.dateTime }
        sorted.forEachIndexed { i, t ->
            for (j in i - 1 downTo 0) {
                val o = sorted[j]
                val gap = Duration.between(o.dateTime, t.dateTime).toMinutes()
                if (gap > 15) break
                val sameRef = t.reference != null && t.reference == o.reference
                val samePay = t.amountMinor == o.amountMinor && normalize(t.merchant) == normalize(o.merchant)
                if (sameRef || samePay) {
                    out += Anomaly(
                        txn = t,
                        kind = AnomalyKind.POSSIBLE_DUPLICATE,
                        title = "Possible double charge",
                        detail = "${currency.format(t.amountMinor)} to ${t.merchant} twice" +
                            if (gap <= 0) " at the same time" else " within $gap min",
                        relatedId = o.id,
                    )
                    break
                }
            }
        }
        return out.distinctBy { it.key }.sortedByDescending { it.txn.dateTime }
    }

    private fun percentile(sorted: List<Long>, q: Double): Long {
        if (sorted.isEmpty()) return 0
        val pos = q * (sorted.size - 1)
        val lo = sorted[pos.toInt()]
        val hi = sorted[minOf(pos.toInt() + 1, sorted.lastIndex)]
        return (lo + (hi - lo) * (pos - pos.toInt())).roundToInt().toLong()
    }

    fun normalize(name: String): String = MerchantNames.key(name)
}

/** "SWIGGY", "Swiggy Ltd" and "swiggy@icici" are the same place. */
object MerchantNames {
    private val noise = Regex("\\b(ltd|limited|pvt|private|llp|inc|india|technologies|services|payments?|online|store)\\b")
    fun key(name: String): String = name.lowercase()
        .substringBefore('@')
        .replace(noise, " ")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}

// ---------------------------------------------------------------- category trends

data class CategoryTrend(
    val category: Category,
    /** Oldest → newest month totals. */
    val monthly: List<Long>,
    /** Latest month (projected to a full month if it's still running) vs the 3 before it. */
    val change: Float?,
)

object TrendCalculator {
    fun of(txns: List<Txn>, endMonth: YearMonth, today: LocalDate, months: Int = 6): Map<Category, CategoryTrend> {
        val range = (months - 1 downTo 0).map { endMonth.minusMonths(it.toLong()) }
        val byCategory = txns.groupBy { it.category }
        return byCategory.mapValues { (category, list) ->
            val totals = range.map { ym -> list.filter { YearMonth.from(it.dateTime) == ym }.sumOf { it.amountMinor } }
            val latest = totals.last().let { v ->
                if (endMonth == YearMonth.from(today) && today.dayOfMonth < endMonth.lengthOfMonth()) {
                    v * endMonth.lengthOfMonth() / today.dayOfMonth.coerceAtLeast(1)
                } else {
                    v
                }
            }
            val before = totals.dropLast(1).takeLast(3).filter { it > 0 }
            val base = if (before.isEmpty()) 0.0 else before.average()
            CategoryTrend(category, totals, if (base > 0) ((latest - base) / base).toFloat() else null)
        }
    }
}

// ---------------------------------------------------------------- compare two periods

data class PeriodSummary(
    val period: Period,
    val title: String,
    val total: Long,
    val count: Int,
    val avgPerDay: Long,
    val largest: Txn?,
    val byCategory: Map<Category, Long>,
    /** Running total by day of the period (stops at today for the current period). */
    val cumulative: List<Long>,
)

data class CategoryDelta(val category: Category, val a: Long, val b: Long) {
    val change: Float? get() = if (a > 0) (b - a).toFloat() / a else null
}

data class Comparison(val a: PeriodSummary, val b: PeriodSummary, val rows: List<CategoryDelta>) {
    val change: Float? get() = if (a.total > 0) (b.total - a.total).toFloat() / a.total else null
}

object Comparer {
    fun summarize(txns: List<Txn>, period: Period, today: LocalDate): PeriodSummary {
        val inside = txns.filter { it.dateTime in period }
        val total = inside.sumOf { it.amountMinor }
        val lastDay = if (period.isCurrent(today)) today else period.endExclusive.minusDays(1)
        val days = (ChronoUnit.DAYS.between(period.start, lastDay).toInt() + 1).coerceAtLeast(1)
        val byDay = inside.groupBy { it.dateTime.toLocalDate() }.mapValues { (_, v) -> v.sumOf { it.amountMinor } }
        var running = 0L
        val cumulative = (0 until days).map { i -> running += byDay[period.start.plusDays(i.toLong())] ?: 0L; running }
        return PeriodSummary(
            period = period,
            title = period.label(today),
            total = total,
            count = inside.size,
            avgPerDay = total / days,
            largest = inside.maxByOrNull { it.amountMinor },
            byCategory = inside.groupBy { it.category }.mapValues { (_, v) -> v.sumOf { it.amountMinor } },
            cumulative = cumulative,
        )
    }

    fun compare(txns: List<Txn>, a: Period, b: Period, today: LocalDate): Comparison {
        val sa = summarize(txns, a, today)
        val sb = summarize(txns, b, today)
        val rows = (sa.byCategory.keys + sb.byCategory.keys)
            .map { CategoryDelta(it, sa.byCategory[it] ?: 0, sb.byCategory[it] ?: 0) }
            .sortedByDescending { maxOf(it.a, it.b) }
        return Comparison(sa, sb, rows)
    }
}

// ---------------------------------------------------------------- recap ("wrapped")

data class Wrap(
    val period: Period,
    val title: String,
    val total: Long,
    val count: Int,
    val previousTotal: Long,
    val change: Float?,
    val avgPerDay: Long,
    val topPlaces: List<MerchantStat>,
    val topCategory: CategorySlice?,
    val biggestDay: Pair<LocalDate, Long>?,
    val busiestHour: Int?,
    val busiestHourShare: Float,
    val topDay: DayOfWeek?,
    val topPart: DayPart?,
    val noSpendDays: Int,
    val longestStreak: Int,
    val largest: Txn?,
)

object WrapBuilder {
    fun of(txns: List<Txn>, period: Period, today: LocalDate): Wrap {
        val inside = txns.filter { it.dateTime in period }
        val total = inside.sumOf { it.amountMinor }
        val previous = period.shift(-1)
        val previousTotal = txns.filter { it.dateTime in previous }.sumOf { it.amountMinor }
        val lastDay = if (period.isCurrent(today)) today else period.endExclusive.minusDays(1)
        val days = (ChronoUnit.DAYS.between(period.start, lastDay).toInt() + 1).coerceAtLeast(1)

        val places = inside.groupBy { MerchantNames.key(it.merchant) }.values.map { g ->
            val sum = g.sumOf { it.amountMinor }
            MerchantStat(
                name = g.first().merchant,
                category = g.groupingBy { it.category }.eachCount().maxByOrNull { it.value }!!.key,
                amountMinor = sum,
                count = g.size,
                fraction = if (total > 0) sum.toFloat() / total else 0f,
            )
        }.sortedByDescending { it.amountMinor }.take(3)

        val topCategory = inside.groupBy { it.category }.map { (c, g) ->
            val sum = g.sumOf { it.amountMinor }
            CategorySlice(c, sum, if (total > 0) sum.toFloat() / total else 0f, g.size)
        }.maxByOrNull { it.amountMinor }

        val byDay = inside.groupBy { it.dateTime.toLocalDate() }.mapValues { (_, v) -> v.sumOf { it.amountMinor } }
        val biggestDay = byDay.maxByOrNull { it.value }?.toPair()
        val patterns = PatternFinder.of(inside, period.start, lastDay)
        val hourShare = patterns.busiestHour?.let { h -> if (total > 0) patterns.hourTotals[h].toFloat() / total else 0f } ?: 0f

        // No-spend days and the longest run of them inside the period (from the first tracked day on).
        val trackedFrom = txns.minOfOrNull { it.dateTime.toLocalDate() }
        var noSpend = 0
        var longest = 0
        var run = 0
        var day = period.start
        while (!day.isAfter(lastDay)) {
            if (trackedFrom != null && !day.isBefore(trackedFrom)) {
                if (byDay.containsKey(day)) run = 0 else { noSpend++; longest = maxOf(longest, ++run) }
            }
            day = day.plusDays(1)
        }

        return Wrap(
            period = period,
            title = period.label(today),
            total = total,
            count = inside.size,
            previousTotal = previousTotal,
            change = if (previousTotal > 0) (total - previousTotal).toFloat() / previousTotal else null,
            avgPerDay = total / days,
            topPlaces = places,
            topCategory = topCategory,
            biggestDay = biggestDay,
            busiestHour = patterns.busiestHour,
            busiestHourShare = hourShare,
            topDay = patterns.topDay,
            topPart = patterns.topPart,
            noSpendDays = noSpend,
            longestStreak = longest,
            largest = inside.maxByOrNull { it.amountMinor },
        )
    }
}

// ---------------------------------------------------------------- savings goals

data class Goal(
    val id: Long,
    val name: String,
    val targetMinor: Long,
    val savedMinor: Long,
    val deadline: LocalDate?,
)

data class GoalPlan(
    val goal: Goal,
    val remaining: Long,
    val progress: Float,
    val monthsLeft: Int?,
    /** What needs to go aside each month to hit the deadline. */
    val neededPerMonth: Long?,
    /** Income minus where this month's spending is heading; null without an income. */
    val projectedLeftover: Long?,
    /** A sensible amount to put aside now. */
    val suggestion: Long,
    val onTrack: Boolean?,
) {
    val done: Boolean get() = remaining <= 0
}

object GoalPlanner {
    fun plan(goal: Goal, monthlyIncome: Long?, spentThisMonth: Long, forecastThisMonth: Long?, today: LocalDate): GoalPlan {
        val remaining = (goal.targetMinor - goal.savedMinor).coerceAtLeast(0)
        val monthsLeft = goal.deadline?.let {
            (ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(it)) + 1).toInt().coerceAtLeast(1)
        }
        val needed = monthsLeft?.let { ceilDiv(remaining, it.toLong()) }
        val leftover = monthlyIncome?.let { it - (forecastThisMonth ?: spentThisMonth) }
        val suggestion = when {
            remaining == 0L -> 0L
            leftover != null && needed != null -> minOf(needed, leftover.coerceAtLeast(0))
            leftover != null -> minOf(remaining, leftover.coerceAtLeast(0))
            needed != null -> needed
            else -> 0L
        }
        return GoalPlan(
            goal = goal,
            remaining = remaining,
            progress = if (goal.targetMinor > 0) (goal.savedMinor.toFloat() / goal.targetMinor).coerceIn(0f, 1f) else 0f,
            monthsLeft = monthsLeft,
            neededPerMonth = needed,
            projectedLeftover = leftover,
            suggestion = suggestion,
            onTrack = if (leftover != null && needed != null) leftover >= needed else null,
        )
    }

    private fun ceilDiv(a: Long, b: Long): Long = if (b <= 0) a else (a + b - 1) / b
}

/** Signed percentage like "+24%" / "−8%". */
fun percentLabel(change: Float): String = (if (change >= 0) "+" else "−") + (abs(change) * 100).roundToInt() + "%"
