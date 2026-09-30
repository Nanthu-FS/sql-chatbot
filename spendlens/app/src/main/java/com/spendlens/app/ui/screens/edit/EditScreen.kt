package com.spendlens.app.ui.screens.edit

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendlens.app.data.TransactionEntity
import com.spendlens.app.data.TransactionRepository
import com.spendlens.app.data.toEpochMillis
import com.spendlens.app.data.toLocalDateTime
import com.spendlens.app.domain.Category
import com.spendlens.app.domain.CategoryClassifier
import com.spendlens.app.domain.Money
import com.spendlens.app.ui.Format
import com.spendlens.app.ui.appViewModel
import com.spendlens.app.ui.components.DatePickerPopup
import com.spendlens.app.ui.components.FieldChip
import com.spendlens.app.ui.components.GradientButton
import com.spendlens.app.ui.components.LocalCurrency
import com.spendlens.app.ui.components.TimePickerPopup
import com.spendlens.app.ui.components.bounceClick
import com.spendlens.app.ui.components.color
import com.spendlens.app.ui.components.icon
import com.spendlens.app.ui.theme.SpendTheme
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class EditForm(
    val amountText: String = "",
    val merchant: String = "",
    val category: Category = Category.OTHER,
    val categoryTouched: Boolean = false,
    val dateTime: LocalDateTime = LocalDateTime.now().withSecond(0).withNano(0),
    val paymentApp: String = "",
    val note: String = "",
)

class EditViewModel(private val repository: TransactionRepository, private val id: Long) : ViewModel() {

    var form by mutableStateOf(EditForm())
        private set
    var loaded by mutableStateOf(id < 0)
        private set
    private var original: TransactionEntity? = null

    val isNew: Boolean get() = id < 0
    val canSave: Boolean get() = Money.parseInput(form.amountText) != null

    init {
        if (id >= 0) {
            viewModelScope.launch {
                repository.get(id)?.let { e ->
                    original = e
                    form = EditForm(
                        amountText = Money.toInput(e.amountMinor),
                        merchant = e.merchant,
                        category = Category.fromKey(e.category),
                        categoryTouched = true,
                        dateTime = e.timestamp.toLocalDateTime(),
                        paymentApp = e.paymentApp.orEmpty(),
                        note = e.note.orEmpty(),
                    )
                }
                loaded = true
            }
        }
    }

    fun update(transform: (EditForm) -> EditForm) {
        val next = transform(form)
        // Suggest a category from the payee until the user picks one themselves.
        form = if (!next.categoryTouched && next.merchant != form.merchant) {
            next.copy(category = CategoryClassifier.classify(next.merchant))
        } else {
            next
        }
    }

    fun save(onDone: () -> Unit) {
        val amount = Money.parseInput(form.amountText) ?: return
        viewModelScope.launch {
            val base = original ?: TransactionEntity(amountMinor = 0, merchant = "", category = "", timestamp = 0)
            val entity = base.copy(
                amountMinor = amount,
                merchant = form.merchant.trim().ifBlank { "Unknown payee" },
                category = form.category.key,
                timestamp = form.dateTime.toEpochMillis(),
                paymentApp = form.paymentApp.trim().ifBlank { null },
                note = form.note.trim().ifBlank { null },
            )
            if (original == null) repository.save(entity) else repository.update(entity)
            onDone()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditScreen(id: Long, onDone: () -> Unit) {
    val vm = appViewModel(key = "edit-$id") { EditViewModel(it.repository, id) }
    val form = vm.form
    val colors = SpendTheme.colors
    val currency = LocalCurrency.current
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (vm.isNew) amountFocus.requestFocus()
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 120.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDone) { Icon(Icons.Rounded.Close, "Close") }
                Text(
                    if (vm.isNew) "Add payment" else "Edit payment",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(20.dp))

            // Big amount input
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(colors.heroBrush)
                    .padding(vertical = 28.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Amount", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(currency.symbol.trim(), style = MaterialTheme.typography.displaySmall, color = Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.width(6.dp))
                    BasicTextField(
                        value = form.amountText,
                        onValueChange = { text ->
                            vm.update { it.copy(amountText = text.filter { c -> c.isDigit() || c == '.' }.take(12)) }
                        },
                        textStyle = MaterialTheme.typography.displayMedium.copy(color = Color.White, textAlign = TextAlign.Start),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier
                            .width(amountFieldWidth(form.amountText))
                            .focusRequester(amountFocus),
                        decorationBox = { inner ->
                            Box {
                                if (form.amountText.isEmpty()) {
                                    Text("0", style = MaterialTheme.typography.displayMedium, color = Color.White.copy(alpha = 0.5f))
                                }
                                inner()
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            Label("Paid to")
            FormField(form.merchant, { v -> vm.update { it.copy(merchant = v) } }, "Swiggy, Priya, Airtel…", KeyboardCapitalization.Words)
            Spacer(Modifier.height(18.dp))

            Label("When")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldChip(Format.dayHeader(form.dateTime.toLocalDate()), Icons.Rounded.CalendarMonth, { pickDate = true })
                FieldChip(Format.time(form.dateTime), Icons.Rounded.Schedule, { pickTime = true })
            }
            Spacer(Modifier.height(18.dp))

            Label("Category")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.entries.forEach { category ->
                    val selected = category == form.category
                    val bg by animateColorAsState(if (selected) category.color else category.color.copy(alpha = 0.12f), label = "cat")
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(bg)
                            .bounceClick { vm.update { it.copy(category = category, categoryTouched = true) } }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(category.icon, null, tint = if (selected) Color.White else category.color, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            category.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                        if (selected) {
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            Label("Paid with (optional)")
            FormField(form.paymentApp, { v -> vm.update { it.copy(paymentApp = v) } }, "Cash, Card, Google Pay…", KeyboardCapitalization.Words)
            Spacer(Modifier.height(18.dp))

            Label("Note (optional)")
            FormField(form.note, { v -> vm.update { it.copy(note = v) } }, "Anything to remember", KeyboardCapitalization.Sentences, singleLine = false)
        }

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
                text = if (vm.isNew) "Add payment" else "Save changes",
                onClick = { vm.save(onDone) },
                enabled = vm.canSave && vm.loaded,
                icon = Icons.Rounded.Check,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (pickDate) {
        DatePickerPopup(
            initial = form.dateTime.toLocalDate(),
            onPick = { date -> vm.update { it.copy(dateTime = date.atTime(it.dateTime.toLocalTime())) } },
            onDismiss = { pickDate = false },
        )
    }
    if (pickTime) {
        TimePickerPopup(
            initial = form.dateTime.toLocalTime(),
            onPick = { time -> vm.update { it.copy(dateTime = it.dateTime.toLocalDate().atTime(time)) } },
            onDismiss = { pickTime = false },
        )
    }
}

/** Grows the amount field with its content so the currency sign hugs the number. */
private fun amountFieldWidth(text: String) = (40 + 30 * text.length.coerceAtLeast(1)).dp

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = SpendTheme.colors.textMuted,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
    )
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    capitalization: KeyboardCapitalization,
    singleLine: Boolean = true,
) {
    val colors = SpendTheme.colors
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(capitalization = capitalization),
        shape = RoundedCornerShape(18.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.subtle,
            unfocusedContainerColor = colors.subtle,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}
