package com.spendlens.app.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.spendlens.app.BuildConfig
import com.spendlens.app.data.AppSettings
import com.spendlens.app.data.CsvExporter
import com.spendlens.app.data.SettingsRepository
import com.spendlens.app.data.ThemeMode
import com.spendlens.app.data.TransactionRepository
import com.spendlens.app.domain.CurrencyOption
import com.spendlens.app.domain.Money
import com.spendlens.app.domain.Txn
import com.spendlens.app.ocr.ImageStore
import com.spendlens.app.ui.appViewModel
import com.spendlens.app.ui.components.BracketButton
import com.spendlens.app.ui.components.Hairline
import com.spendlens.app.ui.components.Label
import com.spendlens.app.ui.components.LineSlider
import com.spendlens.app.ui.components.Section
import com.spendlens.app.ui.components.Statement
import com.spendlens.app.ui.components.TextChip
import com.spendlens.app.ui.components.UnderlineTabs
import com.spendlens.app.ui.components.pressable
import com.spendlens.app.ui.components.rememberHaptics
import com.spendlens.app.ui.components.reveal
import com.spendlens.app.ui.screens.review.UnderlineField
import com.spendlens.app.ui.theme.Spend
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

data class SettingsUi(val settings: AppSettings = AppSettings(), val txns: List<Txn> = emptyList())

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val repository: TransactionRepository,
    private val images: ImageStore,
) : ViewModel() {
    val state: StateFlow<SettingsUi> = combine(settings.settings, repository.transactions) { s, t -> SettingsUi(s, t) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUi())

    fun setCurrency(c: CurrencyOption) { viewModelScope.launch { settings.setCurrency(c) } }
    fun setBudget(minor: Long?) { viewModelScope.launch { settings.setMonthlyBudget(minor) } }
    fun setTheme(t: ThemeMode) { viewModelScope.launch { settings.setTheme(t) } }
    fun setAutoFindDays(d: Int) { viewModelScope.launch { settings.setAutoFindDays(d) } }
    fun deleteAll() { viewModelScope.launch { repository.deleteEverything(images.imagesDir) } }
}

class SettingsActions(
    val onCurrency: (CurrencyOption) -> Unit = {},
    val onBudget: (Long?) -> Unit = {},
    val onTheme: (ThemeMode) -> Unit = {},
    val onAutoFindDays: (Int) -> Unit = {},
    val onExport: () -> Unit = {},
    val onDeleteAll: () -> Unit = {},
)

/** Slider range for the monthly budget, in major units. */
private fun budgetMax(c: CurrencyOption): Float = when (c) {
    CurrencyOption.INR -> 200_000f
    CurrencyOption.JPY -> 1_000_000f
    else -> 10_000f
}

@Composable
fun SettingsScreen(onMessage: (String) -> Unit) {
    val vm = appViewModel { SettingsViewModel(it.settings, it.repository, it.images) }
    val ui by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    SettingsContent(
        ui,
        SettingsActions(
            onCurrency = vm::setCurrency,
            onBudget = vm::setBudget,
            onTheme = vm::setTheme,
            onAutoFindDays = vm::setAutoFindDays,
            onExport = {
                if (ui.txns.isEmpty()) {
                    onMessage("Nothing to export yet")
                } else {
                    scope.launch {
                        val uri = CsvExporter.export(context, ui.txns, ui.settings.currency)
                        val send = Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(send, "Export payments"))
                    }
                }
            },
            onDeleteAll = {
                vm.deleteAll()
                onMessage("All data deleted")
            },
        ),
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsContent(ui: SettingsUi, actions: SettingsActions) {
    val colors = Spend.ink
    val haptics = rememberHaptics()
    val currency = ui.settings.currency
    var budgetDialog by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }

    // Local slider state so dragging is instant; persisted when the finger lifts.
    var budget by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(ui.settings.monthlyBudgetMinor, currency) { budget = (ui.settings.monthlyBudgetMinor ?: 0L) / 100f }
    var days by remember { mutableFloatStateOf(30f) }
    LaunchedEffect(ui.settings.autoFindDays) { days = ui.settings.autoFindDays.toFloat() }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 22.dp, bottom = 120.dp),
    ) {
        Statement("Settings ", "& data", Modifier.reveal(0), MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Label(
            if (ui.txns.isEmpty()) "No payments tracked yet" else "${ui.txns.size} payments · ${currency.format(ui.txns.sumOf { it.amountMinor })} tracked",
            color = colors.muted,
            modifier = Modifier.reveal(1),
        )
        Spacer(Modifier.height(40.dp))

        Section(1, "Monthly budget", Modifier.reveal(2), trailing = {
            Text(
                if (budget <= 0f) "OFF" else currency.format((budget * 100).roundToLong()),
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
                modifier = Modifier.pressable { budgetDialog = true },
            )
        }) {
            LineSlider(
                value = budget,
                onValueChange = { budget = it },
                range = 0f..budgetMax(currency),
                steps = 200,
                onFinished = { actions.onBudget(if (budget <= 0f) null else (budget * 100).roundToLong()) },
            )
            Label("Drag, or tap the amount to type it", color = colors.faint)
        }
        Spacer(Modifier.height(40.dp))

        Section(2, "Auto-find window", Modifier.reveal(3), trailing = {
            Text("${days.toInt()} DAYS", style = MaterialTheme.typography.titleMedium, color = colors.text)
        }) {
            LineSlider(
                value = days,
                onValueChange = { days = it },
                range = 7f..90f,
                steps = 83,
                onFinished = { actions.onAutoFindDays(days.toInt()) },
            )
            Label("How far back auto-find looks in Screenshots", color = colors.faint)
        }
        Spacer(Modifier.height(40.dp))

        Section(3, "Currency", Modifier.reveal(4)) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
            ) {
                CurrencyOption.entries.forEach { c ->
                    TextChip("${c.symbol.trim()} ${c.code}", c == currency, {
                        if (c != currency) haptics.tick()
                        actions.onCurrency(c)
                    })
                }
            }
        }
        Spacer(Modifier.height(40.dp))

        Section(4, "Appearance", Modifier.reveal(5)) {
            UnderlineTabs(ThemeMode.entries.map { it.label }, ThemeMode.entries.indexOf(ui.settings.theme), { actions.onTheme(ThemeMode.entries[it]) })
        }
        Spacer(Modifier.height(40.dp))

        Section(5, "Your data", Modifier.reveal(6)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Export every payment as CSV", style = MaterialTheme.typography.bodyMedium, color = colors.muted, modifier = Modifier.weight(1f))
                BracketButton("Export", onClick = actions.onExport)
            }
            Hairline(Modifier.padding(vertical = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Delete payments and saved screenshots", style = MaterialTheme.typography.bodyMedium, color = colors.muted, modifier = Modifier.weight(1f))
                BracketButton("Delete", onClick = { confirmWipe = true }, color = colors.alert)
            }
        }
        Spacer(Modifier.height(48.dp))
        Statement("Private by design. ", "Screenshots are read on this phone. Nothing is uploaded.", Modifier.reveal(7), MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(20.dp))
        Label("SpendLens ${BuildConfig.VERSION_NAME}", color = colors.faint)
    }

    if (budgetDialog) {
        var text by remember { mutableStateOf(if (budget > 0) Money.toInput((budget * 100).roundToLong()) else "") }
        AlertDialog(
            onDismissRequest = { budgetDialog = false },
            containerColor = colors.raised,
            title = { Text("MONTHLY BUDGET", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    UnderlineField(text, { v -> text = v.filter { it.isDigit() || it == '.' }.take(12) }, "0", MaterialTheme.typography.headlineLarge, prefix = currency.symbol.trim(), keyboard = KeyboardType.Decimal)
                    Spacer(Modifier.width(1.dp))
                }
            },
            confirmButton = {
                BracketButton("Save", onClick = {
                    actions.onBudget(Money.parseInput(text))
                    budgetDialog = false
                })
            },
            dismissButton = {
                BracketButton("Turn off", onClick = {
                    actions.onBudget(null)
                    budgetDialog = false
                }, color = colors.muted)
            },
        )
    }
    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            containerColor = colors.raised,
            title = { Text("DELETE EVERYTHING?", style = MaterialTheme.typography.titleMedium) },
            text = { Text("All ${ui.txns.size} payments and their screenshots will be removed from this phone.", color = colors.muted) },
            confirmButton = {
                BracketButton("Delete", onClick = {
                    confirmWipe = false
                    actions.onDeleteAll()
                }, color = colors.alert)
            },
            dismissButton = { BracketButton("Cancel", onClick = { confirmWipe = false }) },
        )
    }
}
