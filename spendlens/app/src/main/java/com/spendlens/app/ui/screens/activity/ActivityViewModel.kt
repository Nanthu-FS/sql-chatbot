package com.spendlens.app.ui.screens.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendlens.app.data.TransactionRepository
import com.spendlens.app.domain.Category
import com.spendlens.app.domain.Txn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class DayGroup(val date: LocalDate, val total: Long, val items: List<Txn>)

data class ActivityUiState(
    val loading: Boolean = true,
    val filter: Category? = null,
    val groups: List<DayGroup> = emptyList(),
    val categories: List<Category> = emptyList(),
    val total: Long = 0,
    val count: Int = 0,
    val hasAny: Boolean = false,
)

class ActivityViewModel(repository: TransactionRepository) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow<Category?>(null)

    val state: StateFlow<ActivityUiState> = combine(repository.transactions, query, filter) { txns, q, f ->
        val needle = q.trim()
        val filtered = txns.filter { t ->
            (f == null || t.category == f) && (
                needle.isEmpty() ||
                    t.merchant.contains(needle, ignoreCase = true) ||
                    t.category.label.contains(needle, ignoreCase = true) ||
                    t.note?.contains(needle, ignoreCase = true) == true ||
                    t.paymentApp?.contains(needle, ignoreCase = true) == true ||
                    t.reference?.contains(needle, ignoreCase = true) == true
                )
        }
        ActivityUiState(
            loading = false,
            filter = f,
            groups = filtered.groupBy { it.dateTime.toLocalDate() }
                .map { (date, items) -> DayGroup(date, items.sumOf { it.amountMinor }, items) },
            categories = txns.map { it.category }.distinct().sortedBy { it.ordinal },
            total = filtered.sumOf { it.amountMinor },
            count = filtered.size,
            hasAny = txns.isNotEmpty(),
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityUiState())

    fun search(text: String) {
        query.value = text
    }

    fun filterBy(category: Category?) {
        filter.value = category
    }
}
