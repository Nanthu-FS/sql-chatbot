package com.spendlens.app.ui.screens.settings

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.spendlens.app.ui.components.caps
import com.spendlens.app.ui.components.numberStyle
import com.spendlens.app.ui.theme.AccentChoices
import com.spendlens.app.ui.theme.SpendLensTheme
import com.spendlens.app.ui.theme.Style
import com.spendlens.app.ui.theme.contentOn
import com.spendlens.app.ui.theme.inkFor
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.spendlens.app.domain.SampleData
import com.spendlens.app.domain.Txn
import com.spendlens.app.ocr.ImageStore
import com.spendlens.app.ui.LocalAppContainer
import com.spendlens.app.ui.appViewModel
import com.spendlens.app.ui.components.BracketButton
import com.spendlens.app.ui.components.BracketToggle
import com.spendlens.app.ui.components.Hairline
import com.spendlens.app.ui.components.Hint
import com.spendlens.app.ui.components.Label
import com.spendlens.app.ui.components.LineSlider
import com.spendlens.app.ui.components.Screen
import com.spendlens.app.ui.components.Section
import com.spendlens.app.ui.components.Statement
import com.spendlens.app.ui.components.TextChip
import com.spendlens.app.ui.components.UnderlineTabs
import com.spendlens.app.ui.components.pressable
import com.spendlens.app.ui.components.rememberHaptics
import com.spendlens.app.ui.components.reveal
import com.spendlens.app.ui.screens.review.UnderlineField
import com.spendlens.app.ui.theme.Spend
import com.spendlens.app.widget.SpendWidgetReceiver
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class SettingsUi(val settings: AppSettings = AppSettings(), val txns: List<Txn> = emptyList(), val sampleCount: Int = 0)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val repository: TransactionRepository,
    private val images: ImageStore,
) : ViewModel() {
    val state: StateFlow<SettingsUi> = combine(settings.settings, repository.transactions, repository.sampleCount) { s, t, n -> SettingsUi(s, t, n) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUi())

    fun setCurrency(c: CurrencyOption) { viewModelScope.launch { settings.setCurrency(c) } }
    fun setBudget(minor: Long?) { viewModelScope.launch { settings.setMonthlyBudget(minor) } }
    fun setIncome(minor: Long?) { viewModelScope.launch { settings.setMonthlyIncome(minor) } }
    fun setTheme(t: ThemeMode) { viewModelScope.launch { settings.setTheme(t) } }
    fun setGlass(level: Float) { viewModelScope.launch { settings.setGlass(level) } }
    fun setStyle(key: String) { viewModelScope.launch { settings.setStyle(key) } }
    fun setAccent(argb: Long?) { viewModelScope.launch { settings.setAccent(argb) } }
    fun setAutoFindDays(d: Int) { viewModelScope.launch { settings.setAutoFindDays(d) } }
    fun setSummary(on: Boolean) { viewModelScope.launch { settings.setSummaryNotification(on) } }
    fun setSmsAuto(on: Boolean) { viewModelScope.launch { settings.setSmsAutoImport(on) } }
    fun setAlerts(on: Boolean) { viewModelScope.launch { settings.setAlertNotifications(on) } }
    fun addSamples() { viewModelScope.launch { repository.addSamples(SampleData.generate(LocalDate.now(), now = java.time.LocalDateTime.now())) } }
    fun removeSamples() { viewModelScope.launch { repository.removeSamples() } }
    fun deleteAll() { viewModelScope.launch { repository.deleteEverything(images.imagesDir) } }
}

class SettingsActions(
    val onCurrency: (CurrencyOption) -> Unit = {},
    val onBudget: (Long?) -> Unit = {},
    val onIncome: (Long?) -> Unit = {},
    val onTheme: (ThemeMode) -> Unit = {},
    val onGlass: (Float) -> Unit = {},
    val onStyle: (String) -> Unit = {},
    val onAccent: (Long?) -> Unit = {},
    val onAutoFindDays: (Int) -> Unit = {},
    val onSmsImport: () -> Unit = {},
    val onSmsAuto: (Boolean) -> Unit = {},
    val onSummary: (Boolean) -> Unit = {},
    val onAlerts: (Boolean) -> Unit = {},
    val onPinWidget: () -> Unit = {},
    val onAddSamples: () -> Unit = {},
    val onRemoveSamples: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onDeleteAll: () -> Unit = {},
)

/** Slider range for money amounts, in major units. */
private fun moneyMax(c: CurrencyOption): Float = when (c) {
    CurrencyOption.INR -> 200_000f
    CurrencyOption.JPY -> 1_000_000f
    else -> 10_000f
}

@Composable
fun SettingsScreen(onMessage: (String) -> Unit, onOpenReview: () -> Unit) {
    val vm = appViewModel { SettingsViewModel(it.settings, it.repository, it.images) }
    val ui by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()

    val readSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            container.importManager.startSms(ui.settings.autoFindDays)
            onOpenReview()
        } else {
            onMessage("SMS access is needed to read bank messages")
        }
    }
    val receiveSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setSmsAuto(true) else onMessage("Allow SMS access to add bank messages automatically")
    }
    var pendingNotify by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingNotify?.invoke() else onMessage("Allow notifications to use this")
        pendingNotify = null
    }
    fun withNotifications(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !container.notifier.canPost()) {
            pendingNotify = action
            notify.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    SettingsContent(
        ui,
        SettingsActions(
            onCurrency = vm::setCurrency,
            onBudget = vm::setBudget,
            onIncome = vm::setIncome,
            onTheme = vm::setTheme,
            onGlass = vm::setGlass,
            onStyle = vm::setStyle,
            onAccent = vm::setAccent,
            onAutoFindDays = vm::setAutoFindDays,
            onSmsImport = {
                if (com.spendlens.app.sms.SmsReader.hasReadPermission(context)) {
                    container.importManager.startSms(ui.settings.autoFindDays)
                    onOpenReview()
                } else {
                    readSms.launch(Manifest.permission.READ_SMS)
                }
            },
            onSmsAuto = { on ->
                when {
                    !on -> vm.setSmsAuto(false)
                    com.spendlens.app.sms.SmsReader.hasReceivePermission(context) -> withNotifications { vm.setSmsAuto(true) }
                    else -> receiveSms.launch(Manifest.permission.RECEIVE_SMS)
                }
            },
            onSummary = { on -> if (on) withNotifications { vm.setSummary(true) } else vm.setSummary(false) },
            onAlerts = { on -> if (on) withNotifications { vm.setAlerts(true) } else vm.setAlerts(false) },
            onPinWidget = {
                val manager = AppWidgetManager.getInstance(context)
                if (manager.isRequestPinAppWidgetSupported) {
                    manager.requestPinAppWidget(ComponentName(context, SpendWidgetReceiver::class.java), null, null)
                } else {
                    onMessage("Long-press your home screen → Widgets → SpendLens")
                }
            },
            onAddSamples = {
                vm.addSamples()
                onMessage("Added sample payments — remove them any time here")
            },
            onRemoveSamples = {
                vm.removeSamples()
                onMessage("Sample payments removed")
            },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsContent(ui: SettingsUi, actions: SettingsActions) {
    val colors = Spend.ink
    val haptics = rememberHaptics()
    val currency = ui.settings.currency
    var moneyDialog by remember { mutableStateOf<String?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }

    // Sliders keep local state so dragging is instant; money persists when the finger lifts.
    var budget by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(ui.settings.monthlyBudgetMinor) { budget = (ui.settings.monthlyBudgetMinor ?: 0L) / 100f }
    var income by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(ui.settings.monthlyIncomeMinor) { income = (ui.settings.monthlyIncomeMinor ?: 0L) / 100f }
    var days by remember { mutableFloatStateOf(30f) }
    LaunchedEffect(ui.settings.autoFindDays) { days = ui.settings.autoFindDays.toFloat() }
    var glass by remember { mutableFloatStateOf(ui.settings.glass) }
    LaunchedEffect(ui.settings.glass) { glass = ui.settings.glass }
    var n = 0

    Screen {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 22.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column {
                Statement("Settings ", "& data", Modifier.reveal(0), MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(8.dp))
                Label(
                    if (ui.txns.isEmpty()) "No payments tracked yet" else "${ui.txns.size} payments · ${currency.format(ui.txns.sumOf { it.amountMinor })} tracked",
                    color = colors.muted,
                    modifier = Modifier.reveal(1),
                )
            }

            Section(++n, "Theme", Modifier.reveal(2), trailing = {
                Text(Style.from(ui.settings.style).label, style = MaterialTheme.typography.titleMedium, color = colors.text)
            }) {
                ThemePicker(ui.settings.style, ui.settings.accent, colors.isDark, actions.onStyle)
                Spacer(Modifier.height(18.dp))
                Label("Accent", color = colors.muted)
                Spacer(Modifier.height(10.dp))
                AccentPicker(ui.settings.accent, actions.onAccent)
            }

            if (Spend.look.glassy) {
                Section(++n, "Glass", Modifier.reveal(3), trailing = {
                    Text(if (glass < 0.01f) "OFF" else "${(glass * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium, color = colors.text)
                }) {
                    LineSlider(value = glass, onValueChange = { glass = it; actions.onGlass(it) }, range = 0f..1f, steps = 20)
                    Hint("Frosted panels and a see-through bar. 0 keeps it flat.")
                }
            }

            Section(++n, "Monthly budget", Modifier.reveal(3), trailing = {
                Text(
                    if (budget <= 0f) "OFF" else currency.format((budget * 100).roundToLong()),
                    style = MaterialTheme.typography.titleMedium, color = colors.text,
                    modifier = Modifier.pressable { moneyDialog = "budget" },
                )
            }) {
                LineSlider(budget, { budget = it }, 0f..moneyMax(currency), 200, onFinished = { actions.onBudget(if (budget <= 0f) null else (budget * 100).roundToLong()) })
                Hint("Drag, or tap the amount to type it")
            }

            Section(++n, "Monthly income", Modifier.reveal(4), trailing = {
                Text(
                    if (income <= 0f) "NOT SET" else currency.format((income * 100).roundToLong()),
                    style = MaterialTheme.typography.titleMedium, color = colors.text,
                    modifier = Modifier.pressable { moneyDialog = "income" },
                )
            }) {
                LineSlider(income, { income = it }, 0f..moneyMax(currency) * 2, 200, onFinished = { actions.onIncome(if (income <= 0f) null else (income * 100).roundToLong()) })
                Hint("Used for savings goals — what you could put aside")
            }

            Section(++n, "Bank SMS", Modifier.reveal(5)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Read debit alerts from the last ${days.toInt()} days", style = MaterialTheme.typography.bodyMedium, color = colors.muted, modifier = Modifier.weight(1f))
                    BracketButton("Import", onClick = actions.onSmsImport)
                }
                Hairline(Modifier.padding(vertical = 10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Add new bank SMS automatically", style = MaterialTheme.typography.bodyMedium, color = colors.muted, modifier = Modifier.weight(1f))
                    BracketToggle(ui.settings.smsAutoImport, actions.onSmsAuto, on = "On", off = "Off")
                }
                Spacer(Modifier.height(8.dp))
                Hint("Messages are read on this phone. Only debits are kept.")
            }

            Section(++n, "Lock screen & widget", Modifier.reveal(6)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Today's spend on the lock screen", style = MaterialTheme.typography.bodyMedium, color = colors.muted, modifier = Modifier.weight(1f))
                    BracketToggle(ui.settings.summaryNotification, actions.onSummary, on = "On", off = "Off")
                }
                Hairline(Modifier.padding(vertical = 10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Alerts for unusual payments & double charges", style = MaterialTheme.typography.bodyMedium, color = colors.muted, modifier = Modifier.weight(1f))
                    BracketToggle(ui.settings.alertNotifications, actions.onAlerts, on = "On", off = "Off")
                }
                Hairline(Modifier.padding(vertical = 10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Home-screen widget: today, month, last 7 days", style = MaterialTheme.typography.bodyMedium, color = colors.muted, modifier = Modifier.weight(1f))
                    BracketButton("Add", onClick = actions.onPinWidget)
                }
            }

            Section(++n, "Auto-find window", Modifier.reveal(7), trailing = {
                Text("${days.toInt()} DAYS", style = MaterialTheme.typography.titleMedium, color = colors.text)
            }) {
                LineSlider(days, { days = it }, 7f..90f, 83, onFinished = { actions.onAutoFindDays(days.toInt()) })
                Hint("How far back auto-find and SMS import look")
            }

            Section(++n, "Currency", Modifier.reveal(8)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CurrencyOption.entries.forEach { c ->
                        TextChip(if (c.symbol.trim() == c.code) c.code else "${c.symbol.trim()} ${c.code}", c == currency, {
                            if (c != currency) haptics.tick()
                            actions.onCurrency(c)
                        })
                    }
                }
            }

            Section(++n, "Appearance", Modifier.reveal(9)) {
                UnderlineTabs(ThemeMode.entries.map { it.label }, ThemeMode.entries.indexOf(ui.settings.theme), { actions.onTheme(ThemeMode.entries[it]) })
            }

            Section(++n, "Sample data", Modifier.reveal(10)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (ui.sampleCount > 0) "${ui.sampleCount} sample payments are mixed in" else "Six months of example payments to explore every screen",
                        style = MaterialTheme.typography.bodyMedium, color = colors.muted, modifier = Modifier.weight(1f),
                    )
                    if (ui.sampleCount > 0) {
                        BracketButton("Remove", onClick = actions.onRemoveSamples, color = colors.alert)
                    } else {
                        BracketButton("Add", onClick = actions.onAddSamples)
                    }
                }
            }

            Section(++n, "Your data", Modifier.reveal(11)) {
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

            Column {
                Statement("Private by design. ", "Screenshots and messages are read on this phone. Nothing is uploaded.", Modifier.reveal(12), MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(20.dp))
                Label("SpendLens ${BuildConfig.VERSION_NAME}", color = colors.faint)
            }
        }
    }

    moneyDialog?.let { which ->
        val isBudget = which == "budget"
        val current = if (isBudget) budget else income
        var text by remember(which) { mutableStateOf(if (current > 0) Money.toInput((current * 100).roundToLong()) else "") }
        AlertDialog(
            onDismissRequest = { moneyDialog = null },
            containerColor = colors.raised,
            title = { Text(if (isBudget) "MONTHLY BUDGET" else "MONTHLY INCOME", style = MaterialTheme.typography.titleMedium) },
            text = {
                UnderlineField(text, { v -> text = v.filter { it.isDigit() || it == '.' }.take(12) }, "0", MaterialTheme.typography.headlineLarge, prefix = currency.symbol.trim(), keyboard = KeyboardType.Decimal)
            },
            confirmButton = {
                BracketButton("Save", onClick = {
                    val v = Money.parseInput(text)
                    if (isBudget) actions.onBudget(v) else actions.onIncome(v)
                    moneyDialog = null
                })
            },
            dismissButton = {
                BracketButton(if (isBudget) "Turn off" else "Clear", onClick = {
                    if (isBudget) actions.onBudget(null) else actions.onIncome(null)
                    moneyDialog = null
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

/** One small card per look, each drawn in that look, so the choice is visible before it's made. */
@Composable
private fun ThemePicker(current: String, accent: Long?, dark: Boolean, onPick: (String) -> Unit) {
    val haptics = rememberHaptics()
    val selectedStyle = Style.from(current)
    val active = Spend.ink
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Style.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { style ->
                    val selected = style == selectedStyle
                    Box(
                        Modifier
                            .weight(1f)
                            .border(if (selected) 2.dp else 1.dp, if (selected) active.accent else active.line, RoundedCornerShape(14.dp))
                            .padding(4.dp)
                            .pressable(pressedScale = 0.96f, haptic = false) {
                                if (!selected) {
                                    haptics.confirm()
                                    onPick(style.key)
                                }
                            },
                    ) {
                        SpendLensTheme(darkTheme = dark, style = style, accent = accent?.let { Color(it.toInt()) }) {
                            StylePreview(style)
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StylePreview(style: Style) {
    val ink = Spend.ink
    Column(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(ink.canvas, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Text(caps(style.label), style = MaterialTheme.typography.labelLarge, color = ink.text, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Text("₹95,633", style = numberStyle(MaterialTheme.typography.headlineMedium), color = ink.text, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 22.dp, height = 6.dp).background(ink.accent, RoundedCornerShape(3.dp)))
            Box(Modifier.size(width = 14.dp, height = 6.dp).background(ink.muted, RoundedCornerShape(3.dp)))
            Box(Modifier.size(width = 8.dp, height = 6.dp).background(ink.line, RoundedCornerShape(3.dp)))
        }
    }
}

/** "Style colour" plus a row of swatches. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker(current: Long?, onPick: (Long?) -> Unit) {
    val colors = Spend.ink
    val haptics = rememberHaptics()
    val look = Spend.look
    val lookDefault = inkFor(look.style, colors.isDark).accent
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val options: List<Pair<Long?, Color>> = listOf<Pair<Long?, Color>>(null to lookDefault) + AccentChoices.map { it.toArgb().toLong() to it }
        options.forEach { (value, swatch) ->
            val selected = value == current
            Box(
                Modifier
                    .size(40.dp)
                    .border(2.dp, if (selected) colors.text else Color.Transparent, CircleShape)
                    .padding(4.dp)
                    .background(swatch, CircleShape)
                    .pressable(pressedScale = 0.85f, haptic = false) {
                        if (!selected) {
                            haptics.tick()
                            onPick(value)
                        }
                    }
                    .semantics { contentDescription = if (value == null) "Theme's own accent" else "Accent colour" },
                contentAlignment = Alignment.Center,
            ) {
                if (value == null) Text("A", style = MaterialTheme.typography.labelLarge, color = contentOn(swatch))
            }
        }
    }
}

