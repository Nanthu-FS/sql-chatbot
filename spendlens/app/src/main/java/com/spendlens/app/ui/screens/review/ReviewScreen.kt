package com.spendlens.app.ui.screens.review

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.spendlens.app.domain.CurrencyOption
import com.spendlens.app.ocr.DraftFlag
import com.spendlens.app.ocr.DraftState
import com.spendlens.app.ocr.ImportDraft
import com.spendlens.app.ocr.ImportMode
import com.spendlens.app.ocr.ImportPhase
import com.spendlens.app.ocr.ImportState
import com.spendlens.app.ui.Format
import com.spendlens.app.ui.LocalAppContainer
import com.spendlens.app.ui.components.BracketButton
import com.spendlens.app.ui.components.BracketToggle
import com.spendlens.app.ui.components.CategoryChips
import com.spendlens.app.ui.components.DatePickerPopup
import com.spendlens.app.ui.components.FieldChip
import com.spendlens.app.ui.components.Grayscale
import com.spendlens.app.ui.components.Hairline
import com.spendlens.app.ui.components.ImageViewer
import com.spendlens.app.ui.components.Label
import com.spendlens.app.ui.components.LocalCurrency
import com.spendlens.app.ui.components.ScanOverlay
import com.spendlens.app.ui.components.Statement
import com.spendlens.app.ui.components.TimePickerPopup
import com.spendlens.app.ui.components.cornerMarks
import com.spendlens.app.ui.components.index
import com.spendlens.app.ui.components.pressable
import com.spendlens.app.ui.components.rememberHaptics
import com.spendlens.app.ui.components.reveal
import com.spendlens.app.ui.theme.Spend
import kotlinx.coroutines.launch
import java.io.File

class ReviewActions(
    val onClose: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onChange: (String, (ImportDraft) -> ImportDraft) -> Unit = { _, _ -> },
    val onRemove: (String) -> Unit = {},
)

@Composable
fun ReviewScreen(onClose: () -> Unit, onSaved: (Int) -> Unit) {
    val manager = LocalAppContainer.current.importManager
    val state by manager.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var confirmDiscard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val colors = Spend.ink

    val close: () -> Unit = {
        if (state.drafts.any { it.state == DraftState.READY }) {
            confirmDiscard = true
        } else {
            manager.discard()
            onClose()
        }
    }
    BackHandler(onBack = close)

    ReviewContent(
        state = state,
        saving = saving,
        actions = ReviewActions(
            onClose = close,
            onSave = {
                saving = true
                scope.launch {
                    val count = manager.save()
                    haptics.confirm()
                    saving = false
                    onSaved(count)
                }
            },
            onChange = manager::update,
            onRemove = manager::remove,
        ),
    )

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            containerColor = colors.raised,
            title = { Text("DISCARD THIS IMPORT?", style = MaterialTheme.typography.titleMedium) },
            text = { Text("Nothing from these screenshots will be saved.", color = colors.muted) },
            confirmButton = {
                BracketButton("Discard", onClick = {
                    confirmDiscard = false
                    manager.discard()
                    onClose()
                }, color = colors.alert)
            },
            dismissButton = { BracketButton("Keep", onClick = { confirmDiscard = false }) },
        )
    }
}

@Composable
fun ReviewContent(state: ImportState, saving: Boolean, actions: ReviewActions) {
    val colors = Spend.ink
    val currency = LocalCurrency.current
    var preview by remember { mutableStateOf<Any?>(null) }
    val count = state.selected.size

    Box(Modifier.fillMaxSize().background(colors.canvas)) {
        LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(bottom = 120.dp)) {
            item(key = "top") {
                Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 20.dp).padding(top = 14.dp)) {
                    Row {
                        BracketButton("Close", onClick = actions.onClose, color = colors.muted)
                    }
                    Spacer(Modifier.height(20.dp))
                    Statement("Review ", "(${state.drafts.size})", Modifier.reveal(0), MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(6.dp))
                    Label(subtitle(state), color = colors.muted, modifier = Modifier.reveal(1))
                    Spacer(Modifier.height(16.dp))
                    AnimatedVisibility(state.isWorking, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Progress(state)
                    }
                }
            }
            if (state.phase == ImportPhase.DONE && state.drafts.isEmpty()) {
                item(key = "none") {
                    Column(Modifier.padding(20.dp)) {
                        Statement("Nothing found. ", if (state.mode == ImportMode.AUTO_FIND) "Checked ${state.total} recent screenshots — try picking them yourself." else "Those images couldn't be opened.", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
            itemsIndexed(state.drafts, key = { _, d -> d.id }) { i, draft ->
                DraftBlock(
                    number = i + 1,
                    draft = draft,
                    currency = currency,
                    onChange = { actions.onChange(draft.id, it) },
                    onRemove = { actions.onRemove(draft.id) },
                    onPreview = { preview = draft.stagedPath?.let(::File) ?: draft.sourceUri },
                    modifier = Modifier.animateItem().reveal(2 + i),
                )
            }
        }
        Column(
            Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .fillMaxWidth()
                .background(colors.canvas)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Hairline()
            BracketButton(
                text = when {
                    state.isWorking -> "Reading…"
                    count == 0 -> "Nothing selected"
                    count == 1 -> "Save 1 payment"
                    else -> "Save $count payments"
                },
                onClick = actions.onSave,
                filled = true,
                enabled = !state.isWorking && count > 0,
                loading = saving,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
    preview?.let { ImageViewer(it) { preview = null } }
}

private fun subtitle(state: ImportState): String = when (state.phase) {
    ImportPhase.FINDING -> "Looking through Screenshots"
    ImportPhase.SCANNING -> "Reading ${minOf(state.processed + 1, state.total)} of ${state.total} · on this phone"
    ImportPhase.SAVING -> "Saving"
    else -> buildList {
        add("${state.selected.size} selected")
        if (state.skipped > 0) add("${state.skipped} not payments")
        if (state.alreadyImported > 0) add("${state.alreadyImported} already added")
    }.joinToString(" · ")
}

@Composable
private fun Progress(state: ImportState) {
    val colors = Spend.ink
    val fraction = if (state.total > 0) state.processed.toFloat() / state.total else 0.06f
    val p by animateFloatAsState(fraction, label = "progress")
    Column(Modifier.padding(bottom = 16.dp)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(colors.line)) {
            Box(Modifier.fillMaxWidth(p.coerceAtLeast(0.04f)).height(2.dp).background(colors.text))
        }
    }
}

@Composable
private fun DraftBlock(
    number: Int,
    draft: ImportDraft,
    currency: CurrencyOption,
    onChange: ((ImportDraft) -> ImportDraft) -> Unit,
    onRemove: () -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Spend.ink
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val dim by animateFloatAsState(if (draft.include || draft.state == DraftState.SCANNING) 1f else 0.45f, label = "dim")

    Column(modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        Hairline()
        Row(Modifier.padding(top = 10.dp)) {
            Label(index(number), color = colors.faint, modifier = Modifier.width(38.dp))
            Label(status(draft), color = if (draft.flags.any { it.excludeByDefault } || draft.state == DraftState.ERROR) colors.alert else colors.muted, modifier = Modifier.weight(1f))
            draft.paymentApp?.let { Label(it, color = colors.faint) }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.alpha(dim)) {
            Box(
                Modifier
                    .width(92.dp)
                    .height(168.dp)
                    .background(colors.surface)
                    .cornerMarks(colors.text, inset = (-4).dp)
                    .pressable(pressedScale = 0.96f, onClick = onPreview),
            ) {
                AsyncImage(
                    model = draft.stagedPath?.let(::File) ?: draft.sourceUri,
                    contentDescription = "Screenshot",
                    contentScale = ContentScale.Crop,
                    alignment = androidx.compose.ui.Alignment.TopCenter,
                    colorFilter = Grayscale,
                    modifier = Modifier.fillMaxSize(),
                )
                if (draft.state == DraftState.SCANNING) ScanOverlay()
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                when (draft.state) {
                    DraftState.SCANNING -> Statement("Reading ", "amount, payee and date…", style = MaterialTheme.typography.titleLarge)
                    DraftState.ERROR -> Statement("Couldn't open ", "this image.", style = MaterialTheme.typography.titleLarge)
                    DraftState.READY -> {
                        Label("Amount", color = colors.faint)
                        UnderlineField(
                            value = draft.amountText,
                            onValue = { t -> onChange { it.copy(amountText = t.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(12), flags = it.flags - DraftFlag.NO_AMOUNT) } },
                            placeholder = "0",
                            prefix = currency.symbol.trim(),
                            style = MaterialTheme.typography.headlineLarge,
                            keyboard = KeyboardType.Decimal,
                        )
                        Spacer(Modifier.height(12.dp))
                        Label("Paid to", color = colors.faint)
                        UnderlineField(
                            value = draft.merchant,
                            onValue = { t -> onChange { it.copy(merchant = t.take(60)) } },
                            placeholder = "Payee",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FieldChip("Date", Format.shortDate(draft.dateTime.toLocalDate()), { pickDate = true })
                            FieldChip("Time", Format.time(draft.dateTime), { pickTime = true })
                        }
                    }
                }
            }
        }
        if (draft.state == DraftState.READY) {
            Spacer(Modifier.height(16.dp))
            CategoryChips(draft.category, { c -> onChange { it.copy(category = c) } })
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                BracketToggle(draft.include, { v -> onChange { it.copy(include = v) } }, on = "Include", off = "Skipped")
                Spacer(Modifier.weight(1f))
                BracketButton("Remove", onClick = onRemove, color = colors.muted)
            }
        } else if (draft.state == DraftState.ERROR) {
            BracketButton("Remove", onClick = onRemove, color = colors.muted)
        }
    }

    if (pickDate) {
        DatePickerPopup(draft.dateTime.toLocalDate(), { d -> onChange { it.copy(dateTime = d.atTime(it.dateTime.toLocalTime())) } }, { pickDate = false })
    }
    if (pickTime) {
        TimePickerPopup(draft.dateTime.toLocalTime(), { t -> onChange { it.copy(dateTime = it.dateTime.toLocalDate().atTime(t)) } }, { pickTime = false })
    }
}

private fun status(d: ImportDraft): String = when {
    d.state == DraftState.SCANNING -> "Scanning"
    d.state == DraftState.ERROR -> "Unreadable"
    d.flags.isNotEmpty() -> d.flags.first().label
    else -> "Detected"
}

@Composable
fun UnderlineField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
) {
    val colors = Spend.ink
    Column(modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValue,
            textStyle = style.copy(color = colors.text),
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            cursorBrush = SolidColor(colors.text),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            decorationBox = { inner ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    if (prefix != null) {
                        Text(prefix, style = style, color = colors.faint)
                        Spacer(Modifier.width(6.dp))
                    }
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) Text(placeholder, style = style, color = colors.ghost)
                        inner()
                    }
                }
            },
        )
        Hairline(color = if (value.isEmpty()) colors.line else colors.lineStrong)
    }
}
