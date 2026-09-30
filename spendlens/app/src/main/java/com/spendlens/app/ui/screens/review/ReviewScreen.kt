package com.spendlens.app.ui.screens.review

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import com.spendlens.app.ui.components.CategoryChips
import com.spendlens.app.ui.components.DatePickerPopup
import com.spendlens.app.ui.components.EmptyIllustration
import com.spendlens.app.ui.components.FieldChip
import com.spendlens.app.ui.components.GradientButton
import com.spendlens.app.ui.components.ImageViewer
import com.spendlens.app.ui.components.LocalCurrency
import com.spendlens.app.ui.components.Pill
import com.spendlens.app.ui.components.ScanOverlay
import com.spendlens.app.ui.components.TimePickerPopup
import com.spendlens.app.ui.theme.SpendTheme
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ReviewScreen(onClose: () -> Unit, onSaved: (Int) -> Unit) {
    val manager = LocalAppContainer.current.importManager
    val state by manager.state.collectAsStateWithLifecycle()
    val currency = LocalCurrency.current
    val scope = rememberCoroutineScope()
    var confirmDiscard by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Any?>(null) }
    var saving by remember { mutableStateOf(false) }

    val close: () -> Unit = {
        if (state.drafts.any { it.state == DraftState.READY }) {
            confirmDiscard = true
        } else {
            manager.discard()
            onClose()
        }
    }
    BackHandler(onBack = close)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "top") {
                Row(
                    Modifier
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = close) { Icon(Icons.Rounded.Close, "Close") }
                    Column(Modifier.weight(1f)) {
                        Text("Review payments", style = MaterialTheme.typography.titleLarge)
                        Text(subtitle(state), style = MaterialTheme.typography.bodySmall, color = SpendTheme.colors.textMuted)
                    }
                }
            }
            item(key = "progress") {
                AnimatedVisibility(visible = state.isWorking, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    ProgressCard(state)
                }
            }
            if (state.phase == ImportPhase.DONE && state.drafts.isEmpty()) {
                item(key = "empty") { NothingFound(state, onClose = { manager.discard(); onClose() }) }
            }
            items(state.drafts, key = { it.id }) { draft ->
                DraftCard(
                    draft = draft,
                    currency = currency,
                    onChange = { transform -> manager.update(draft.id, transform) },
                    onRemove = { manager.remove(draft.id) },
                    onPreview = { preview = draft.stagedPath?.let(::File) ?: draft.sourceUri },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        val count = state.selected.size
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            GradientButton(
                text = when {
                    state.isWorking -> "Reading screenshots…"
                    count == 0 -> "Nothing selected"
                    count == 1 -> "Save 1 payment"
                    else -> "Save $count payments"
                },
                onClick = {
                    saving = true
                    scope.launch {
                        val saved = manager.save()
                        saving = false
                        onSaved(saved)
                    }
                },
                enabled = !state.isWorking && count > 0,
                loading = saving,
                icon = Icons.Rounded.CheckCircle,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard these payments?") },
            text = { Text("Nothing from this import will be saved.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    manager.discard()
                    onClose()
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep reviewing") } },
        )
    }
    preview?.let { model -> ImageViewer(model = model, onDismiss = { preview = null }) }
}

private fun subtitle(state: ImportState): String = when (state.phase) {
    ImportPhase.FINDING -> "Looking through your Screenshots folder…"
    ImportPhase.SCANNING -> "Reading ${state.processed + 1} of ${state.total}"
    ImportPhase.SAVING -> "Saving…"
    else -> buildList {
        add("${state.drafts.size} found")
        if (state.skipped > 0) add("${state.skipped} not payments")
        if (state.alreadyImported > 0) add("${state.alreadyImported} already added")
    }.joinToString(" · ")
}

@Composable
private fun ProgressCard(state: ImportState) {
    val colors = SpendTheme.colors
    val fraction = if (state.total > 0) state.processed.toFloat() / state.total else 0f
    val progress by animateFloatAsState(fraction, label = "importProgress")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.card)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(24.dp))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = colors.brand[1])
            Spacer(Modifier.width(10.dp))
            Text(
                if (state.phase == ImportPhase.FINDING) "Finding screenshots" else "Reading on-device · ${state.processed}/${state.total}",
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(colors.chartTrack),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(if (state.phase == ImportPhase.FINDING) 0.08f else progress.coerceAtLeast(0.04f))
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(colors.brandBrush),
            )
        }
        if (state.mode == ImportMode.AUTO_FIND && state.phase == ImportPhase.SCANNING) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Only screenshots that look like payments will show up here.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
    }
}

@Composable
private fun NothingFound(state: ImportState, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyIllustration(Modifier.size(220.dp))
        Text("No new payment screenshots", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            if (state.mode == ImportMode.AUTO_FIND) {
                "Checked ${state.total} recent screenshots from the last 30 days. Try picking screenshots manually instead."
            } else {
                "We couldn't open those images."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = SpendTheme.colors.textMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onClose) { Text("Back") }
    }
}

@Composable
private fun DraftCard(
    draft: ImportDraft,
    currency: CurrencyOption,
    onChange: ((ImportDraft) -> ImportDraft) -> Unit,
    onRemove: () -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SpendTheme.colors
    val shape = RoundedCornerShape(28.dp)
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val dimmed by animateFloatAsState(if (draft.include || draft.state == DraftState.SCANNING) 1f else 0.55f, label = "dim")

    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, if (draft.include && draft.isValid) colors.brand[0].copy(alpha = 0.5f) else colors.cardBorder, shape)
            .padding(14.dp),
    ) {
        Row {
            Box(
                Modifier
                    .width(96.dp)
                    .height(176.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.subtle)
                    .clickable(onClick = onPreview),
            ) {
                AsyncImage(
                    model = draft.stagedPath?.let(::File) ?: draft.sourceUri,
                    contentDescription = "Screenshot",
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
                if (draft.state == DraftState.SCANNING) {
                    ScanOverlay()
                } else {
                    Icon(
                        Icons.Rounded.ZoomIn,
                        null,
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(4.dp)
                            .size(16.dp),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).alpha(dimmed)) {
                StatusRow(draft)
                Spacer(Modifier.height(8.dp))
                when (draft.state) {
                    DraftState.SCANNING -> {
                        Text("Reading screenshot…", style = MaterialTheme.typography.titleMedium, color = colors.textMuted)
                        Text("Amount, payee and date appear here.", style = MaterialTheme.typography.bodySmall, color = colors.textFaint)
                    }
                    DraftState.ERROR -> {
                        Text("Couldn't open this image", style = MaterialTheme.typography.titleMedium, color = colors.negative)
                    }
                    DraftState.READY -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(currency.symbol.trim(), style = MaterialTheme.typography.headlineMedium, color = colors.textMuted)
                            Spacer(Modifier.width(4.dp))
                            BasicTextField(
                                value = draft.amountText,
                                onValueChange = { text ->
                                    val clean = text.filter { it.isDigit() || it == '.' || it == ',' }.take(12)
                                    onChange { it.copy(amountText = clean, flags = it.flags - DraftFlag.NO_AMOUNT) }
                                },
                                textStyle = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                cursorBrush = SolidColor(colors.brand[1]),
                                modifier = Modifier.weight(1f),
                                decorationBox = { inner ->
                                    Box {
                                        if (draft.amountText.isEmpty()) {
                                            Text("0", style = MaterialTheme.typography.headlineMedium, color = colors.textFaint)
                                        }
                                        inner()
                                    }
                                },
                            )
                        }
                        BasicTextField(
                            value = draft.merchant,
                            onValueChange = { text -> onChange { it.copy(merchant = text.take(60)) } },
                            textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            singleLine = true,
                            cursorBrush = SolidColor(colors.brand[1]),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                Box {
                                    if (draft.merchant.isEmpty()) {
                                        Text("Paid to…", style = MaterialTheme.typography.titleMedium, color = colors.textFaint)
                                    }
                                    inner()
                                }
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FieldChip(Format.shortDate(draft.dateTime.toLocalDate()), Icons.Rounded.CalendarMonth, { pickDate = true })
                            FieldChip(Format.time(draft.dateTime), Icons.Rounded.Schedule, { pickTime = true })
                        }
                    }
                }
            }
        }
        if (draft.state == DraftState.READY) {
            Spacer(Modifier.height(12.dp))
            CategoryChips(selected = draft.category, onSelect = { category -> onChange { it.copy(category = category) } })
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = draft.include,
                    onCheckedChange = { checked -> onChange { it.copy(include = checked) } },
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.brand[0]),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (draft.include) "Will be saved" else "Skipped",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (draft.include) MaterialTheme.colorScheme.onSurface else colors.textMuted,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove) { Icon(Icons.Rounded.DeleteOutline, "Remove", tint = colors.textMuted) }
            }
        } else if (draft.state == DraftState.ERROR) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onRemove) { Text("Remove") }
            }
        }
    }

    if (pickDate) {
        DatePickerPopup(
            initial = draft.dateTime.toLocalDate(),
            onPick = { date -> onChange { it.copy(dateTime = date.atTime(it.dateTime.toLocalTime())) } },
            onDismiss = { pickDate = false },
        )
    }
    if (pickTime) {
        TimePickerPopup(
            initial = draft.dateTime.toLocalTime(),
            onPick = { time -> onChange { it.copy(dateTime = it.dateTime.toLocalDate().atTime(time)) } },
            onDismiss = { pickTime = false },
        )
    }
}

@Composable
private fun StatusRow(draft: ImportDraft) {
    val colors = SpendTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            draft.state == DraftState.SCANNING -> Pill("Scanning", colors.brand[0], icon = Icons.Rounded.AutoAwesome)
            draft.state == DraftState.ERROR -> Pill("Error", colors.negative, icon = Icons.Rounded.ErrorOutline)
            draft.flags.isNotEmpty() -> {
                val flag = draft.flags.first()
                Pill(flag.label, if (flag.excludeByDefault) colors.negative else colors.warning, icon = Icons.Rounded.ErrorOutline)
            }
            else -> Pill("Detected", colors.positive, icon = Icons.Rounded.CheckCircle)
        }
        draft.paymentApp?.let { Pill(it, colors.textMuted) }
    }
}
