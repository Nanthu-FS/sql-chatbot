package com.spendlens.app.ui.screens.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.spendlens.app.data.SettingsRepository
import com.spendlens.app.data.TransactionRepository
import com.spendlens.app.domain.Analytics
import com.spendlens.app.domain.Goal
import com.spendlens.app.domain.GoalPlan
import com.spendlens.app.domain.GoalPlanner
import com.spendlens.app.domain.Money
import com.spendlens.app.domain.Period
import com.spendlens.app.domain.PeriodType
import com.spendlens.app.domain.floorToWhole
import com.spendlens.app.domain.roundToWhole
import com.spendlens.app.ui.appViewModel
import com.spendlens.app.ui.components.BracketButton
import com.spendlens.app.ui.components.DatePickerPopup
import com.spendlens.app.ui.components.FieldChip
import com.spendlens.app.ui.components.Label
import com.spendlens.app.ui.components.LocalCurrency
import com.spendlens.app.ui.components.Screen
import com.spendlens.app.ui.components.Section
import com.spendlens.app.ui.components.Statement
import com.spendlens.app.ui.components.reveal
import com.spendlens.app.ui.screens.home.GoalBlock
import com.spendlens.app.ui.screens.review.UnderlineField
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextOverflow
import com.spendlens.app.domain.CurrencyOption
import com.spendlens.app.domain.WishCatalog
import com.spendlens.app.domain.WishKind
import com.spendlens.app.ui.components.Hint
import com.spendlens.app.ui.components.UnderlineTabs
import com.spendlens.app.ui.components.bevel
import com.spendlens.app.ui.components.numberStyle
import com.spendlens.app.ui.components.pressable
import com.spendlens.app.ui.theme.ControlStyle
import com.spendlens.app.ui.theme.Spend
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class GoalsUi(
    val plans: List<GoalPlan> = emptyList(),
    val income: Long? = null,
    val monthSpent: Long = 0,
    val monthForecast: Long? = null,
)

class GoalsViewModel(private val repository: TransactionRepository, private val settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<GoalsUi> = combine(repository.goals, repository.transactions, settings.settings) { goals, txns, prefs ->
        val now = LocalDateTime.now()
        val month = Period(PeriodType.MONTH, now.toLocalDate())
        val spent = txns.filter { it.dateTime in month }.sumOf { it.amountMinor }
        val forecast = Analytics.forecast(txns, month, spent, now)
        GoalsUi(goals.map { GoalPlanner.plan(it, prefs.monthlyIncomeMinor, spent, forecast, now.toLocalDate()) }, prefs.monthlyIncomeMinor, spent, forecast)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsUi())

    fun save(goal: Goal) { viewModelScope.launch { repository.saveGoal(goal) } }
    fun delete(id: Long) { viewModelScope.launch { repository.deleteGoal(id) } }
    fun putAside(id: Long, amount: Long) { viewModelScope.launch { repository.addToGoal(id, amount) } }
    fun setIncome(amount: Long?) { viewModelScope.launch { settings.setMonthlyIncome(amount) } }
}

class GoalsActions(
    val onBack: () -> Unit = {},
    val onSave: (Goal) -> Unit = {},
    val onDelete: (Long) -> Unit = {},
    val onPutAside: (Long, Long) -> Unit = { _, _ -> },
    val onIncome: (Long?) -> Unit = {},
)

@Composable
fun GoalsScreen(onBack: () -> Unit) {
    val vm = appViewModel { GoalsViewModel(it.repository, it.settings) }
    val ui by vm.state.collectAsStateWithLifecycle()
    GoalsContent(ui, GoalsActions(onBack, vm::save, vm::delete, vm::putAside, vm::setIncome))
}

@Composable
fun GoalsContent(ui: GoalsUi, actions: GoalsActions) {
    val colors = Spend.ink
    val currency = LocalCurrency.current
    var editing by remember { mutableStateOf<Goal?>(null) }
    var incomeDialog by remember { mutableStateOf(false) }

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
            Statement("Savings ", "goals", Modifier.reveal(0), MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(24.dp))

            Section(1, "This month", Modifier.reveal(1), trailing = { BracketButton(if (ui.income == null) "Add income" else "Edit income", { incomeDialog = true }) }) {
                val income = ui.income
                if (income == null) {
                    Text("Tell SpendLens what you take home each month and it will suggest how much to put aside.", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                } else {
                    val heading = ui.monthForecast ?: ui.monthSpent
                    val leftover = (income - heading).floorToWhole()
                    if (leftover >= 0) {
                        Statement(currency.format(leftover), " likely left over", style = MaterialTheme.typography.headlineMedium)
                    } else {
                        Statement(currency.format(-leftover), " past your income", style = MaterialTheme.typography.headlineMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                    Label(
                        "Income ${currency.format(income)} − spending heading to ${currency.format(heading.roundToWhole())}",
                        color = if (leftover < 0) colors.alert else colors.faint,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            Section(2, "Save for something", Modifier.reveal(2)) {
                WishPicker(onPick = { editing = it })
            }
            Spacer(Modifier.height(20.dp))

            ui.plans.forEachIndexed { i, plan ->
                Section(3 + i, plan.goal.deadline?.let { "By " + it.format(DateTimeFormatter.ofPattern("MMM yyyy")) } ?: "No deadline", Modifier.reveal(3 + i), trailing = {
                    BracketButton("Edit", { editing = plan.goal }, color = colors.muted)
                }) {
                    GoalBlock(plan, currency, onPutAside = { actions.onPutAside(plan.goal.id, it) })
                }
                Spacer(Modifier.height(20.dp))
            }
            BracketButton("New goal", filled = true, modifier = Modifier.fillMaxWidth(), onClick = {
                editing = Goal(0, "", 0, 0, LocalDate.now().plusMonths(6).withDayOfMonth(1).minusDays(1))
            })
        }
    }

    editing?.let { goal ->
        GoalEditor(goal, onDismiss = { editing = null }, onSave = { actions.onSave(it); editing = null }, onDelete = if (goal.id != 0L) ({ actions.onDelete(goal.id); editing = null }) else null)
    }
    if (incomeDialog) {
        var text by remember { mutableStateOf(ui.income?.let { Money.toInput(it) }.orEmpty()) }
        AlertDialog(
            onDismissRequest = { incomeDialog = false },
            containerColor = colors.raised,
            title = { Text("MONTHLY INCOME", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    Text("Only used on this phone to suggest savings.", style = MaterialTheme.typography.bodySmall, color = colors.muted)
                    UnderlineField(text, { v -> text = v.filter { it.isDigit() || it == '.' }.take(12) }, "0", MaterialTheme.typography.headlineLarge, prefix = currency.symbol.trim(), keyboard = KeyboardType.Decimal)
                }
            },
            confirmButton = { BracketButton("Save", { actions.onIncome(Money.parseInput(text)); incomeDialog = false }) },
            dismissButton = { BracketButton("Clear", { actions.onIncome(null); incomeDialog = false }, color = colors.muted) },
        )
    }
}

/** Phones, bikes and cars with their prices; tapping one opens a goal pre-filled with it. */
@Composable
private fun WishPicker(onPick: (Goal) -> Unit) {
    val colors = Spend.ink
    val look = Spend.look
    val currency = LocalCurrency.current
    val kinds = WishKind.entries
    var kind by rememberSaveable { mutableStateOf(WishKind.PHONE) }
    UnderlineTabs(kinds.map { it.label }, kinds.indexOf(kind), { kind = kinds[it] })
    Spacer(Modifier.height(14.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WishCatalog.of(kind).forEach { item ->
            val shape = RoundedCornerShape(look.radius.coerceAtMost(16.dp))
            Column(
                Modifier
                    .width(158.dp)
                    .then(if (look.control == ControlStyle.BEVEL) Modifier.bevel() else Modifier.border(1.dp, colors.line, shape))
                    .pressable(pressedScale = 0.96f) { onPick(WishCatalog.goalFor(item, currency, LocalDate.now())) }
                    .padding(14.dp),
            ) {
                Icon(
                    when (item.kind) {
                        WishKind.PHONE -> Icons.Rounded.Smartphone
                        WishKind.BIKE -> Icons.Rounded.TwoWheeler
                        WishKind.CAR -> Icons.Rounded.DirectionsCar
                    },
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(item.name, style = MaterialTheme.typography.titleSmall, color = colors.text, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Text(currency.format(WishCatalog.price(item, currency)), style = numberStyle(MaterialTheme.typography.titleLarge), color = colors.text, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Label("≈ ${currency.compact(WishCatalog.perMonth(item, currency))}/mo · ${item.kind.months} mo", color = colors.faint)
                Spacer(Modifier.height(10.dp))
                Label("Save for this →", color = colors.accent)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Hint(if (currency == CurrencyOption.INR) "Approximate starting prices in India (ex-showroom for vehicles). Edit the amount to match your quote." else "Rough conversions of Indian prices. Edit the amount to match your quote.")
}

@Composable
private fun GoalEditor(goal: Goal, onDismiss: () -> Unit, onSave: (Goal) -> Unit, onDelete: (() -> Unit)?) {
    val colors = Spend.ink
    val currency = LocalCurrency.current
    var name by remember { mutableStateOf(goal.name) }
    var target by remember { mutableStateOf(if (goal.targetMinor > 0) Money.toInput(goal.targetMinor) else "") }
    var saved by remember { mutableStateOf(if (goal.savedMinor > 0) Money.toInput(goal.savedMinor) else "") }
    var deadline by remember { mutableStateOf(goal.deadline) }
    var pickDate by remember { mutableStateOf(false) }
    val targetMinor = Money.parseInput(target)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.raised,
        title = { Text(if (goal.id == 0L) "NEW GOAL" else "EDIT GOAL", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column { Label("Name", color = colors.faint); UnderlineField(name, { name = it.take(40) }, "Goa trip, new phone…", MaterialTheme.typography.titleLarge) }
                Column { Label("Target", color = colors.faint); UnderlineField(target, { v -> target = v.filter { it.isDigit() || it == '.' }.take(12) }, "0", MaterialTheme.typography.headlineMedium, prefix = currency.symbol.trim(), keyboard = KeyboardType.Decimal) }
                Column { Label("Saved so far", color = colors.faint); UnderlineField(saved, { v -> saved = v.filter { it.isDigit() || it == '.' }.take(12) }, "0", MaterialTheme.typography.titleLarge, prefix = currency.symbol.trim(), keyboard = KeyboardType.Decimal) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldChip("By", deadline?.format(DateTimeFormatter.ofPattern("d MMM yyyy")) ?: "No date", { pickDate = true })
                    if (deadline != null) BracketButton("No date", { deadline = null }, color = colors.muted)
                }
                if (onDelete != null) BracketButton("Delete goal", onDelete, color = colors.alert)
            }
        },
        confirmButton = {
            BracketButton("Save", enabled = name.isNotBlank() && targetMinor != null, onClick = {
                onSave(goal.copy(name = name.trim(), targetMinor = targetMinor ?: 0, savedMinor = Money.parseInput(saved) ?: 0, deadline = deadline))
            })
        },
        dismissButton = { BracketButton("Cancel", onDismiss, color = colors.muted) },
    )
    if (pickDate) {
        DatePickerPopup(deadline ?: LocalDate.now().plusMonths(3), { deadline = it }, { pickDate = false }, allowFuture = true)
    }
}
