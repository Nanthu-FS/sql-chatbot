package com.spendlens.app.ui.screens.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.spendlens.app.data.SettingsRepository
import com.spendlens.app.data.TransactionRepository
import com.spendlens.app.domain.Anomaly
import com.spendlens.app.domain.AnomalyDetector
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import androidx.compose.foundation.border
import com.spendlens.app.domain.Txn
import com.spendlens.app.ui.Format
import com.spendlens.app.ui.appViewModel
import com.spendlens.app.ui.components.caps
import com.spendlens.app.ui.components.AmountText
import com.spendlens.app.ui.components.BracketButton
import com.spendlens.app.ui.components.Grayscale
import com.spendlens.app.ui.components.Hairline
import com.spendlens.app.ui.components.ImageViewer
import com.spendlens.app.ui.components.Label
import com.spendlens.app.ui.components.LocalCurrency
import com.spendlens.app.ui.components.cornerMarks
import com.spendlens.app.ui.components.index
import com.spendlens.app.ui.components.pressable
import com.spendlens.app.ui.components.rememberHaptics
import com.spendlens.app.ui.components.reveal
import com.spendlens.app.ui.theme.Spend
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File

sealed interface DetailState {
    data object Loading : DetailState
    data object Missing : DetailState
    data class Loaded(val txn: Txn, val anomaly: Anomaly? = null) : DetailState
}

class DetailViewModel(repository: TransactionRepository, private val settings: SettingsRepository, id: Long) : ViewModel() {
    val state: StateFlow<DetailState> = combine(repository.observe(id), repository.transactions, settings.settings) { txn, all, prefs ->
        if (txn == null) {
            DetailState.Missing
        } else {
            val anomaly = AnomalyDetector.detect(all, LocalDateTime.now(), prefs.currency, lookbackDays = 400)
                .firstOrNull { it.txn.id == txn.id && it.key !in prefs.dismissedAlerts }
            DetailState.Loaded(txn, anomaly)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState.Loading)

    fun dismiss(key: String) {
        viewModelScope.launch { settings.dismissAlert(key) }
    }
}

@Composable
fun DetailScreen(id: Long, onBack: () -> Unit, onEdit: (Long) -> Unit, onDelete: (Long) -> Unit, onOpen: (Long) -> Unit = {}) {
    val vm = appViewModel(key = "detail-$id") { DetailViewModel(it.repository, it.settings, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    when (val s = state) {
        DetailState.Loading -> Box(Modifier.fillMaxSize())
        DetailState.Missing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Label("Deleted") }
        is DetailState.Loaded -> DetailContent(s.txn, onBack, onEdit, onDelete, s.anomaly, onOpen, vm::dismiss)
    }
}

@Composable
fun DetailContent(
    txn: Txn,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    anomaly: Anomaly? = null,
    onOpen: (Long) -> Unit = {},
    onDismissAnomaly: (String) -> Unit = {},
) {
    val colors = Spend.ink
    val currency = LocalCurrency.current
    val clipboard = LocalClipboardManager.current
    val haptics = rememberHaptics()
    var confirmDelete by remember { mutableStateOf(false) }
    var viewImage by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }

    com.spendlens.app.ui.components.Screen {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp, bottom = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BracketButton("Back", onClick = onBack, color = colors.muted)
            Spacer(Modifier.weight(1f))
            BracketButton("Edit", onClick = { onEdit(txn.id) })
            BracketButton("Delete", onClick = { confirmDelete = true }, color = colors.alert)
        }
        Spacer(Modifier.height(24.dp))
        anomaly?.let { a ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.alert)
                    .padding(14.dp)
                    .reveal(0),
            ) {
                Label(a.title, color = colors.alert)
                Spacer(Modifier.height(4.dp))
                Text(a.detail, style = MaterialTheme.typography.bodyMedium, color = colors.text)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                    a.relatedId?.let { other -> BracketButton("Open the other", onClick = { onOpen(other) }) }
                    BracketButton("Looks fine", onClick = { onDismissAnomaly(a.key) }, color = colors.muted)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        Label(txn.category.label, color = colors.muted, modifier = Modifier.reveal(0))
        Spacer(Modifier.height(8.dp))
        Text(caps(txn.merchant), style = MaterialTheme.typography.headlineLarge, color = colors.text, modifier = Modifier.reveal(1))
        Spacer(Modifier.height(4.dp))
        AmountText(txn.amountMinor, currency, MaterialTheme.typography.displayLarge, Modifier.reveal(2))
        Spacer(Modifier.height(28.dp))

        val path = txn.imagePath
        if (path != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .reveal(3)
                    .padding(6.dp)
                    .cornerMarks(colors.text, length = 10.dp, inset = (-6).dp)
                    .background(colors.surface)
                    .pressable(pressedScale = 0.98f) { viewImage = true },
            ) {
                AsyncImage(
                    model = File(path),
                    contentDescription = "Payment screenshot",
                    contentScale = ContentScale.Fit,
                    colorFilter = Grayscale,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(8.dp))
            Label("Tap to view in colour", color = colors.faint)
            Spacer(Modifier.height(28.dp))
        }

        Column(Modifier.reveal(4)) {
            val rows = buildList {
                add("Date" to Format.dayHeader(txn.dateTime.toLocalDate()) + " · " + Format.date(txn.dateTime.toLocalDate()))
                add("Time" to Format.time(txn.dateTime))
                add("Category" to txn.category.label)
                txn.paymentApp?.let { add("Paid via" to it) }
                add("Source" to txn.source.label)
                txn.reference?.let { add("Reference" to it) }
                txn.note?.let { add("Note" to it) }
            }
            rows.forEachIndexed { i, (label, value) ->
                Hairline()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (label == "Reference") {
                                Modifier.pressable(pressedScale = 0.98f) {
                                    clipboard.setText(AnnotatedString(value))
                                    haptics.confirm()
                                }
                            } else {
                                Modifier
                            },
                        )
                        .padding(vertical = 14.dp),
                ) {
                    Label(index(i + 1), color = colors.faint, modifier = Modifier.width(38.dp))
                    Label(label, color = colors.muted, modifier = Modifier.width(92.dp))
                    Text(value, style = MaterialTheme.typography.titleSmall, color = colors.text, modifier = Modifier.weight(1f))
                    if (label == "Reference") Label("Copy", color = colors.faint)
                }
            }
            Hairline()
        }

        val raw = txn.rawText
        if (!raw.isNullOrBlank()) {
            Spacer(Modifier.height(20.dp))
            BracketButton(if (showRaw) "Hide text read" else "Show text read", onClick = { showRaw = !showRaw }, color = colors.muted)
            AnimatedVisibility(showRaw) {
                Text(
                    raw,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.muted,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }

    }
    val image = txn.imagePath
    if (viewImage && image != null) ImageViewer(File(image)) { viewImage = false }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = colors.raised,
            title = { Text("DELETE THIS PAYMENT?", style = MaterialTheme.typography.titleMedium) },
            text = { Text("${currency.format(txn.amountMinor)} to ${txn.merchant} will be removed from your totals.", color = colors.muted) },
            confirmButton = {
                BracketButton("Delete", onClick = {
                    confirmDelete = false
                    onDelete(txn.id)
                }, color = colors.alert)
            },
            dismissButton = { BracketButton("Cancel", onClick = { confirmDelete = false }) },
        )
    }
}
