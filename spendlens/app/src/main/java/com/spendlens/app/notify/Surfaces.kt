package com.spendlens.app.notify

import android.content.Context
import com.spendlens.app.data.SettingsRepository
import com.spendlens.app.data.TransactionRepository
import com.spendlens.app.domain.Analytics
import com.spendlens.app.domain.AnomalyDetector
import com.spendlens.app.domain.CurrencyOption
import com.spendlens.app.domain.Period
import com.spendlens.app.domain.PeriodType
import com.spendlens.app.domain.StreakCalculator
import com.spendlens.app.domain.TxnSource
import com.spendlens.app.widget.SpendWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/** The numbers shown outside the app: widget, lock-screen notification. */
data class SpendSummary(
    val currency: CurrencyOption,
    val today: Long,
    val month: Long,
    val monthName: String,
    val budgetLeft: Long?,
    val forecast: Long?,
    /** Oldest → today. */
    val last7: List<Long>,
    val streak: Int,
    val hasData: Boolean,
)

/** Keeps the widget, the ongoing notification and alert notifications in sync with the data. */
class Surfaces(
    private val context: Context,
    private val repository: TransactionRepository,
    private val settings: SettingsRepository,
    private val notifier: Notifier,
) {
    private val alerted = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)

    suspend fun summary(now: LocalDateTime = LocalDateTime.now()): SpendSummary {
        val prefs = settings.settings.first()
        val all = repository.all()
        val today = now.toLocalDate()
        val month = Period(PeriodType.MONTH, today)
        val dash = Analytics.build(all, month, now, prefs.currency, prefs.monthlyBudgetMinor)
        return SpendSummary(
            currency = prefs.currency,
            today = all.filter { it.dateTime.toLocalDate() == today }.sumOf { it.amountMinor },
            month = dash.total,
            monthName = today.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
            budgetLeft = dash.budget?.remaining,
            forecast = dash.forecast,
            last7 = (6 downTo 0).map { back -> val d: LocalDate = today.minusDays(back.toLong()); all.filter { it.dateTime.toLocalDate() == d }.sumOf { it.amountMinor } },
            streak = StreakCalculator.of(all, today).current,
            hasData = all.isNotEmpty(),
        )
    }

    suspend fun refresh() {
        val prefs = settings.settings.first()
        val s = summary()
        if (prefs.summaryNotification) {
            val c = s.currency
            notifier.showSummary(
                title = "${c.format(s.today)} today",
                text = "${c.format(s.month)} in ${s.monthName}" + (s.budgetLeft?.let { " · ${c.format(it)} ${if (it >= 0) "left" else "over"}" } ?: ""),
                detail = s.forecast?.let { "At this pace ≈ ${c.compact(it)} by month end" } ?: "SpendLens",
            )
        } else {
            notifier.hideSummary()
        }
        runCatching { SpendWidget().updateAll(context) }
        if (prefs.alertNotifications) notifyNewAnomalies(prefs.currency, prefs.dismissedAlerts)
    }

    private suspend fun notifyNewAnomalies(currency: CurrencyOption, dismissed: Set<String>) {
        val seen = alerted.getStringSet(KEY, emptySet()).orEmpty()
        val fresh = AnomalyDetector.detect(repository.all(), LocalDateTime.now(), currency, lookbackDays = 3)
            .filter { it.txn.source != TxnSource.SAMPLE && it.key !in seen && it.key !in dismissed }
        fresh.take(3).forEach { a -> notifier.alert(a.key, "${a.title} · ${currency.format(a.txn.amountMinor)}", a.detail, a.txn.id) }
        if (fresh.isNotEmpty()) alerted.edit().putStringSet(KEY, (seen + fresh.map { it.key }).takeLast(500).toSet()).apply()
    }

    private companion object {
        const val KEY = "notified"
    }
}
