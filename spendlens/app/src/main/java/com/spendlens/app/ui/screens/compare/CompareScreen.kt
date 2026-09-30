package com.spendlens.app.ui.screens.compare

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.spendlens.app.data.TransactionRepository
import com.spendlens.app.domain.Comparer
import com.spendlens.app.domain.Comparison
import com.spendlens.app.domain.Period
import com.spendlens.app.domain.PeriodType
import com.spendlens.app.domain.percentLabel
import com.spendlens.app.ui.appViewModel
import com.spendlens.app.ui.components.caps
import com.spendlens.app.ui.components.AmountText
import com.spendlens.app.ui.components.BracketButton
import com.spendlens.app.ui.components.Hairline
import com.spendlens.app.ui.components.Label
import com.spendlens.app.ui.components.LocalCurrency
import com.spendlens.app.ui.components.PaceChart
import com.spendlens.app.ui.components.Screen
import com.spendlens.app.ui.components.Section
import com.spendlens.app.ui.components.Statement
import com.spendlens.app.ui.components.UnderlineTabs
import com.spendlens.app.ui.components.bouncy
import com.spendlens.app.ui.components.pressable
import com.spendlens.app.ui.components.reveal
import com.spendlens.app.ui.theme.Spend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

class CompareViewModel(repository: TransactionRepository, initial: Period) : ViewModel() {
    private val base = if (initial.type == PeriodType.DAY) Period(PeriodType.MONTH, initial.anchor) else initial
    private val periods = MutableStateFlow(base.shift(-1) to base)

    val state: StateFlow<Comparison?> = combine(repository.transactions, periods) { txns, (a, b) ->
        Comparer.compare(txns, a, b, LocalDate.now())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setType(type: PeriodType) {
        val today = LocalDate.now()
        val b = Period(type, today)
        periods.value = b.shift(-1) to b
    }

    fun shiftA(steps: Long) = periods.update { (a, b) -> a.shift(steps) to b }
    fun shiftB(steps: Long) = periods.update { (a, b) -> a to b.shift(steps).let { if (it.isFuture(LocalDate.now())) b else it } }
}

class CompareActions(
    val onBack: () -> Unit = {},
    val onType: (PeriodType) -> Unit = {},
    val onShiftA: (Long) -> Unit = {},
    val onShiftB: (Long) -> Unit = {},
)

@Composable
fun CompareScreen(initial: Period, onBack: () -> Unit) {
    val vm = appViewModel(key = "compare") { CompareViewModel(it.repository, initial) }
    val state by vm.state.collectAsStateWithLifecycle()
    val c = state
    if (c == null) {
        Screen {}
        return
    }
    CompareContent(c, CompareActions(onBack, vm::setType, vm::shiftA, vm::shiftB))
}

private val TYPES = listOf(PeriodType.WEEK, PeriodType.MONTH, PeriodType.YEAR)

@Composable
fun CompareContent(c: Comparison, actions: CompareActions) {
    val colors = Spend.ink
    val currency = LocalCurrency.current
    Screen {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp, bottom = 40.dp),
        ) {
            Row { BracketButton("Back", onClick = actions.onBack, color = colors.muted) }
            Spacer(Modifier.height(20.dp))
            Statement("Compare ", "two ${c.b.period.type.label.lowercase()}s", Modifier.reveal(0), MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(20.dp))
            UnderlineTabs(TYPES.map { it.label }, TYPES.indexOf(c.b.period.type).coerceAtLeast(0), { actions.onType(TYPES[it]) })
            Spacer(Modifier.height(20.dp))

            PeriodPicker("A", c.a.title, actions.onShiftA, dashed = true)
            PeriodPicker("B", c.b.title, actions.onShiftB, dashed = false)
            Spacer(Modifier.height(24.dp))

            Row(Modifier.fillMaxWidth().reveal(1)) {
                Column(Modifier.weight(1f)) {
                    Label("A", color = colors.faint)
                    AmountText(c.a.total, currency, MaterialTheme.typography.headlineLarge, color = colors.muted)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Label("B", color = colors.faint)
                    AmountText(c.b.total, currency, MaterialTheme.typography.headlineLarge)
                }
            }
            Spacer(Modifier.height(8.dp))
            Label(
                c.change?.let { "B is ${percentLabel(it)} vs A" } ?: "Nothing in A to compare with",
                color = if ((c.change ?: 0f) > 0.1f) colors.text else colors.muted,
                modifier = Modifier.reveal(2),
            )
            Spacer(Modifier.height(28.dp))

            Section(1, "Pace", Modifier.reveal(3), trailing = { Label("A - - -   B ——", color = colors.faint) }) {
                PaceChart(c.a.cumulative, c.b.cumulative, maxOf(c.a.period.lengthInDays, c.b.period.lengthInDays))
                Spacer(Modifier.height(8.dp))
                Label("Running total by day of the ${c.b.period.type.label.lowercase()}", color = colors.faint)
            }
            Spacer(Modifier.height(24.dp))

            Section(2, "Numbers", Modifier.reveal(4)) {
                StatRow("Payments", c.a.count.toString(), c.b.count.toString())
                StatRow("Per day", currency.format(c.a.avgPerDay), currency.format(c.b.avgPerDay))
                StatRow("Largest", c.a.largest?.let { currency.format(it.amountMinor) } ?: "—", c.b.largest?.let { currency.format(it.amountMinor) } ?: "—")
            }
            Spacer(Modifier.height(24.dp))

            Section(3, "By category", Modifier.reveal(5)) {
                val max = c.rows.maxOfOrNull { maxOf(it.a, it.b) }?.coerceAtLeast(1) ?: 1
                if (c.rows.isEmpty()) Text("No payments in either.", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                c.rows.forEach { row ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(caps(row.category.label), style = MaterialTheme.typography.titleMedium, color = colors.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Label(row.change?.let { percentLabel(it) } ?: "new", color = if ((row.change ?: 1f) > 0.1f) colors.text else colors.faint)
                        }
                        Spacer(Modifier.height(6.dp))
                        Bar(row.a.toFloat() / max, colors.muted, currency.format(row.a))
                        Spacer(Modifier.height(3.dp))
                        Bar(row.b.toFloat() / max, colors.text, currency.format(row.b))
                    }
                    Hairline()
                }
            }
        }
    }
}

@Composable
private fun PeriodPicker(tag: String, title: String, onShift: (Long) -> Unit, dashed: Boolean) {
    val colors = Spend.ink
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 18.dp, height = 2.dp).background(if (dashed) colors.muted else colors.accent))
        Spacer(Modifier.width(10.dp))
        Label(tag, color = colors.faint, modifier = Modifier.width(18.dp))
        Icon(Icons.Rounded.ChevronLeft, "Earlier", tint = colors.muted, modifier = Modifier.size(28.dp).pressable(pressedScale = 0.8f) { onShift(-1) })
        Label(title, color = colors.text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f).padding(horizontal = 6.dp))
        Icon(Icons.Rounded.ChevronRight, "Later", tint = colors.muted, modifier = Modifier.size(28.dp).pressable(pressedScale = 0.8f) { onShift(1) })
    }
}

@Composable
private fun StatRow(label: String, a: String, b: String) {
    val colors = Spend.ink
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Label(label, color = colors.faint, modifier = Modifier.weight(1f))
        Text(a, style = MaterialTheme.typography.titleSmall, color = colors.muted, modifier = Modifier.weight(1f))
        Text(b, style = MaterialTheme.typography.titleSmall, color = colors.text, modifier = Modifier.weight(1f))
    }
    Hairline()
}

@Composable
private fun Bar(fraction: Float, color: Color, value: String) {
    val colors = Spend.ink
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), bouncy(), label = "bar")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(6.dp).background(colors.ghost)) {
            Box(Modifier.fillMaxWidth(f).height(6.dp).background(color))
        }
        Spacer(Modifier.width(10.dp))
        Text(value, style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.width(78.dp), maxLines = 1)
    }
}

