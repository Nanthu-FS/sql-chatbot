package com.spendlens.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.spendlens.app.domain.Category
import com.spendlens.app.ui.theme.Spend
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun CategoryChips(selected: Category, onSelect: (Category) -> Unit, modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(0.dp)) {
    val haptics = rememberHaptics()
    LazyRow(modifier, contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(Category.entries, key = { it.key }) { category ->
            TextChip(category.short, category == selected, onClick = {
                if (category != selected) haptics.tick()
                onSelect(category)
            })
        }
    }
}

/** Bracketed field value, e.g. "[ 30 SEP ]". */
@Composable
fun FieldChip(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Spend.ink
    Column(
        modifier
            .border(1.dp, colors.line, RoundedCornerShape(2.dp))
            .pressable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Label(label, color = colors.faint, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(2.dp))
        Text(caps(value), style = MaterialTheme.typography.labelLarge, color = colors.text)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerPopup(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit, allowFuture: Boolean = false) {
    val colors = Spend.ink
    val limit = remember { LocalDate.now().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = allowFuture || utcTimeMillis < limit
        },
    )
    val pickerColors = DatePickerDefaults.colors(containerColor = colors.raised)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(4.dp),
        colors = pickerColors,
        confirmButton = {
            BracketButton("Done", onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            })
        },
        dismissButton = { BracketButton("Cancel", onClick = onDismiss, color = colors.muted) },
    ) { DatePicker(state = state, colors = pickerColors) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerPopup(initial: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val colors = Spend.ink
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = false)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(4.dp), color = colors.raised) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Label("Payment time", Modifier.fillMaxWidth())
                Spacer(Modifier.height(20.dp))
                TimePicker(state = state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    BracketButton("Cancel", onClick = onDismiss, color = colors.muted)
                    BracketButton("Done", onClick = {
                        onPick(LocalTime.of(state.hour, state.minute))
                        onDismiss()
                    })
                }
            }
        }
    }
}
