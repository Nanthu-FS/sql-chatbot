package com.spendlens.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendlens.app.data.SettingsRepository
import com.spendlens.app.data.TransactionRepository
import com.spendlens.app.domain.Analytics
import com.spendlens.app.domain.Dashboard
import com.spendlens.app.domain.Period
import com.spendlens.app.domain.PeriodType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalDateTime

data class HomeUiState(val loading: Boolean = true, val dashboard: Dashboard? = null)

class HomeViewModel(repository: TransactionRepository, settings: SettingsRepository) : ViewModel() {

    private val period = MutableStateFlow(Period(PeriodType.MONTH, LocalDate.now()))

    val state: StateFlow<HomeUiState> = combine(repository.transactions, settings.settings, period) { txns, prefs, p ->
        HomeUiState(
            loading = false,
            dashboard = Analytics.build(txns, p, LocalDateTime.now(), prefs.currency, prefs.monthlyBudgetMinor),
        )
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
}
