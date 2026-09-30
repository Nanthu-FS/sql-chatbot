package com.spendlens.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendlens.app.data.SettingsRepository
import com.spendlens.app.data.TransactionRepository
import com.spendlens.app.domain.Analytics
import com.spendlens.app.domain.Anomaly
import com.spendlens.app.domain.AnomalyDetector
import com.spendlens.app.domain.Category
import com.spendlens.app.domain.CategoryTrend
import com.spendlens.app.domain.CurrencyOption
import com.spendlens.app.domain.Dashboard
import com.spendlens.app.domain.Goal
import com.spendlens.app.domain.GoalPlan
import com.spendlens.app.domain.GoalPlanner
import com.spendlens.app.domain.PatternFinder
import com.spendlens.app.domain.Period
import com.spendlens.app.domain.PeriodType
import com.spendlens.app.domain.SampleData
import com.spendlens.app.domain.SpendPatterns
import com.spendlens.app.domain.StreakCalculator
import com.spendlens.app.domain.Streaks
import com.spendlens.app.domain.TrendCalculator
import com.spendlens.app.domain.Txn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

data class HomeUiState(
    val loading: Boolean = true,
    val dashboard: Dashboard? = null,
    val streaks: Streaks? = null,
    val patterns: SpendPatterns? = null,
    val anomalies: List<Anomaly> = emptyList(),
    val trends: Map<Category, CategoryTrend> = emptyMap(),
    val goals: List<GoalPlan> = emptyList(),
    val hasIncome: Boolean = false,
)

/** Everything the dashboard shows, from the raw payments. Pure so screenshot tests can use it too. */
fun buildHome(
    txns: List<Txn>,
    period: Period,
    now: LocalDateTime,
    currency: CurrencyOption,
    budget: Long?,
    income: Long?,
    goals: List<Goal>,
    dismissed: Set<String>,
): HomeUiState {
    val today = now.toLocalDate()
    val month = Period(PeriodType.MONTH, today)
    val monthSpent = txns.filter { it.dateTime in month }.sumOf { it.amountMinor }
    val monthForecast = Analytics.forecast(txns, month, monthSpent, now)
    val lastDay = minOf(period.endExclusive.minusDays(1), today)
    return HomeUiState(
        loading = false,
        dashboard = Analytics.build(txns, period, now, currency, budget),
        streaks = StreakCalculator.of(txns, today),
        patterns = PatternFinder.of(txns, today.minusDays(89), today),
        anomalies = AnomalyDetector.detect(txns, now, currency).filter { it.key !in dismissed },
        trends = TrendCalculator.of(txns, YearMonth.from(lastDay), today),
        goals = goals.map { GoalPlanner.plan(it, income, monthSpent, monthForecast, today) },
        hasIncome = income != null,
    )
}

class HomeViewModel(private val repository: TransactionRepository, private val settings: SettingsRepository) : ViewModel() {

    private val period = MutableStateFlow(Period(PeriodType.MONTH, LocalDate.now()))

    val state: StateFlow<HomeUiState> = combine(repository.transactions, settings.settings, period, repository.goals) { txns, prefs, p, goals ->
        buildHome(txns, p, LocalDateTime.now(), prefs.currency, prefs.monthlyBudgetMinor, prefs.monthlyIncomeMinor, goals, prefs.dismissedAlerts)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun selectType(type: PeriodType) {
        period.update { current ->
            val today = LocalDate.now()
            Period(type, if (current.isCurrent(today)) today else current.anchor)
        }
    }

    fun shift(steps: Long) = period.update { it.shift(steps) }

    fun openDay(date: LocalDate) {
        period.value = Period(PeriodType.DAY, date)
    }

    fun openMonth(date: LocalDate) {
        period.value = Period(PeriodType.MONTH, date)
    }

    fun backToToday() = period.update { Period(it.type, LocalDate.now()) }

    fun dismissAlert(key: String) {
        viewModelScope.launch { settings.dismissAlert(key) }
    }

    fun putAside(goalId: Long, amount: Long) {
        viewModelScope.launch { repository.addToGoal(goalId, amount) }
    }

    fun loadSamples() {
        viewModelScope.launch { repository.addSamples(SampleData.generate(LocalDate.now(), now = LocalDateTime.now())) }
    }
}
