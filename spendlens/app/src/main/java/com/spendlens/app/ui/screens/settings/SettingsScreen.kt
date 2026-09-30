package com.spendlens.app.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.spendlens.app.ui.Format
import com.spendlens.app.ui.appViewModel
import com.spendlens.app.ui.components.IconTile
import com.spendlens.app.ui.components.SegmentedTabs
import com.spendlens.app.ui.theme.SpendTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUi(val settings: AppSettings = AppSettings(), val txns: List<Txn> = emptyList())

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val repository: TransactionRepository,
    private val images: ImageStore,
) : ViewModel() {

    val state: StateFlow<SettingsUi> = combine(settings.settings, repository.transactions) { s, t -> SettingsUi(s, t) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUi())

    fun setCurrency(currency: CurrencyOption) = viewModelScope.launch { settings.setCurrency(currency) }

    fun setBudget(amountMinor: Long?) = viewModelScope.launch { settings.setMonthlyBudget(amountMinor) }

    fun setTheme(theme: ThemeMode) = viewModelScope.launch { settings.setTheme(theme) }

    fun deleteAll() = viewModelScope.launch { repository.deleteEverything(images.imagesDir) }
}

@Composable
fun SettingsScreen(onMessage: (String) -> Unit) {
    val vm = appViewModel { SettingsViewModel(it.settings, it.repository, it.images) }
    val ui by vm.state.collectAsStateWithLifecycle()
    val colors = SpendTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editCurrency by remember { mutableStateOf(false) }
    var editBudget by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    val currency = ui.settings.currency

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge)

        // Summary card
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(colors.heroBrush)
                .drawBehind { drawCircle(Color.White.copy(alpha = 0.1f), size.width * 0.4f, Offset(size.width, size.height)) }
                .padding(22.dp),
        ) {
            Text("SpendLens", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Spacer(Modifier.height(4.dp))
            val since = ui.txns.minOfOrNull { it.dateTime }?.toLocalDate()
            Text(
                if (ui.txns.isEmpty()) {
                    "No payments tracked yet"
                } else {
                    "${ui.txns.size} payments · ${currency.format(ui.txns.sumOf { it.amountMinor })} tracked" +
                        (since?.let { " since ${Format.date(it)}" } ?: "")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
        }

        Group("Preferences") {
            SettingRow(Icons.Rounded.CurrencyExchange, "Currency", "${currency.symbol.trim()} · ${currency.label}", colors.brand[0]) { editCurrency = true }
            SettingRow(
                Icons.Rounded.Savings,
                "Monthly budget",
                ui.settings.monthlyBudgetMinor?.let { currency.format(it) } ?: "Not set",
                colors.positive,
            ) { editBudget = true }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Palette, size = 40.dp, brush = Brush.linearGradient(listOf(colors.brand[1], colors.brand[2])))
                    Spacer(Modifier.width(14.dp))
                    Text("Appearance", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(12.dp))
                SegmentedTabs(
                    options = ThemeMode.entries.map { it.label },
                    selectedIndex = ThemeMode.entries.indexOf(ui.settings.theme),
                    onSelect = { vm.setTheme(ThemeMode.entries[it]) },
                )
            }
        }

        Group("Your data") {
            SettingRow(Icons.Rounded.Download, "Export to CSV", "Open in Sheets or Excel", colors.brand[2]) {
                if (ui.txns.isEmpty()) {
                    onMessage("Nothing to export yet")
                } else {
                    scope.launch {
                        val uri = CsvExporter.export(context, ui.txns, currency)
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/csv")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(send, "Export payments"))
                    }
                }
            }
            SettingRow(Icons.Rounded.DeleteOutline, "Delete all data", "Payments and saved screenshots", colors.negative) { confirmWipe = true }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.positive.copy(alpha = 0.1f))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Shield, null, tint = colors.positive)
            Spacer(Modifier.width(12.dp))
            Text(
                "Private by design. Screenshots are read on your phone with on-device text recognition — nothing is uploaded anywhere.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            "SpendLens ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textFaint,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }

    if (editCurrency) {
        AlertDialog(
            onDismissRequest = { editCurrency = false },
            title = { Text("Currency") },
            text = {
                Column {
                    CurrencyOption.entries.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    vm.setCurrency(option)
                                    editCurrency = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == currency, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text("${option.symbol.trim()}  ${option.label}", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editCurrency = false }) { Text("Close") } },
        )
    }

    if (editBudget) {
        var text by remember { mutableStateOf(ui.settings.monthlyBudgetMinor?.let { Money.toInput(it) }.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editBudget = false },
            title = { Text("Monthly budget") },
            text = {
                Column {
                    Text("We'll show how much you can spend per day to stay on track.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' }.take(12) },
                        prefix = { Text(currency.symbol) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(14.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setBudget(Money.parseInput(text))
                    editBudget = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.setBudget(null)
                    editBudget = false
                }) { Text("Remove") }
            },
        )
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Delete everything?") },
            text = { Text("All ${ui.txns.size} payments and their screenshots will be permanently deleted from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteAll()
                    confirmWipe = false
                    onMessage("All data deleted")
                }) { Text("Delete", color = colors.negative) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    val colors = SpendTheme.colors
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = colors.textMuted, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(colors.card)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(28.dp))
                .padding(vertical = 6.dp),
        ) { content() }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, value: String, accent: Color, onClick: () -> Unit) {
    val colors = SpendTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, size = 40.dp, brush = Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.65f))))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(value, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = colors.textFaint, modifier = Modifier.size(20.dp))
    }
}
