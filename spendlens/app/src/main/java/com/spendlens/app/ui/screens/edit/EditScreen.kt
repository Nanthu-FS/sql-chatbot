package com.spendlens.app.ui.screens.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
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
import com.spendlens.app.domain.TxnSource
import com.spendlens.app.ui.Format
import com.spendlens.app.ui.appViewModel
import com.spendlens.app.ui.components.BracketButton
import com.spendlens.app.ui.components.DatePickerPopup
import com.spendlens.app.ui.components.FieldChip
import com.spendlens.app.ui.components.Hairline
import com.spendlens.app.ui.components.Label
import com.spendlens.app.ui.components.LocalCurrency
import com.spendlens.app.ui.components.Statement
import com.spendlens.app.ui.components.TextChip
import com.spendlens.app.ui.components.TimePickerPopup
import com.spendlens.app.ui.components.rememberHaptics
import com.spendlens.app.ui.components.reveal
import com.spendlens.app.ui.components.short
import com.spendlens.app.ui.screens.review.UnderlineField
import com.spendlens.app.ui.theme.Spend
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
    val canSave: Boolean get() = loaded && Money.parseInput(form.amountText) != null

    init {
        if (id >= 0) {
            viewModelScope.launch {
                repository.get(id)?.let { e ->
                    original = e
                    form = EditForm(
                        Money.toInput(e.amountMinor), e.merchant, Category.fromKey(e.category), true,
                        e.timestamp.toLocalDateTime(), e.paymentApp.orEmpty(), e.note.orEmpty(),
                    )
                }
                loaded = true
            }
        }
    }

    fun update(transform: (EditForm) -> EditForm) {
        val next = transform(form)
        // Suggest a category from the payee until the user picks one.
        form = if (!next.categoryTouched && next.merchant != form.merchant) next.copy(category = CategoryClassifier.classify(next.merchant)) else next
    }

    fun save(onDone: () -> Unit) {
        val amount = Money.parseInput(form.amountText) ?: return
        viewModelScope.launch {
            val base = original ?: TransactionEntity(amountMinor = 0, merchant = "", category = "", timestamp = 0, source = TxnSource.MANUAL.key)
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

@Composable
fun EditScreen(id: Long, onDone: () -> Unit) {
    val vm = appViewModel(key = "edit-$id") { EditViewModel(it.repository, id) }
    EditContent(vm.form, vm.isNew, vm.canSave, vm::update, onSave = { vm.save(onDone) }, onClose = onDone)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditContent(
    form: EditForm,
    isNew: Boolean,
    canSave: Boolean,
    onUpdate: ((EditForm) -> EditForm) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = Spend.ink
    val currency = LocalCurrency.current
    val haptics = rememberHaptics()
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(colors.canvas)) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp, bottom = 120.dp),
        ) {
            Row { BracketButton("Close", onClick = onClose, color = colors.muted) }
            Spacer(Modifier.height(20.dp))
            Statement(if (isNew) "New " else "Edit ", "payment", Modifier.reveal(0), MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(36.dp))

            Column(Modifier.reveal(1)) {
                Label("(01)  Amount", color = colors.faint)
                UnderlineField(form.amountText, { t -> onUpdate { it.copy(amountText = t.filter { c -> c.isDigit() || c == '.' }.take(12)) } }, "0", MaterialTheme.typography.displayMedium, prefix = currency.symbol.trim(), keyboard = KeyboardType.Decimal)
            }
            Spacer(Modifier.height(28.dp))
            Column(Modifier.reveal(2)) {
                Label("(02)  Paid to", color = colors.faint)
                UnderlineField(form.merchant, { v -> onUpdate { it.copy(merchant = v) } }, "Swiggy, Priya, Airtel…", MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.height(28.dp))
            Column(Modifier.reveal(3)) {
                Label("(03)  When", color = colors.faint)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldChip("Date", Format.dayHeader(form.dateTime.toLocalDate()), { pickDate = true })
                    FieldChip("Time", Format.time(form.dateTime), { pickTime = true })
                }
            }
            Spacer(Modifier.height(28.dp))
            Column(Modifier.reveal(4)) {
                Label("(04)  Category", color = colors.faint)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Category.entries.forEach { c ->
                        TextChip(c.short, c == form.category, {
                            if (c != form.category) haptics.tick()
                            onUpdate { it.copy(category = c, categoryTouched = true) }
                        })
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Column(Modifier.reveal(5)) {
                Label("(05)  Paid with — optional", color = colors.faint)
                UnderlineField(form.paymentApp, { v -> onUpdate { it.copy(paymentApp = v) } }, "Cash, card, GPay…", MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(28.dp))
                Label("(06)  Note — optional", color = colors.faint)
                UnderlineField(form.note, { v -> onUpdate { it.copy(note = v) } }, "Anything to remember", MaterialTheme.typography.titleMedium, singleLine = false)
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(colors.canvas).navigationBarsPadding().imePadding(),
        ) {
            Hairline()
            BracketButton(
                if (isNew) "Add payment" else "Save changes",
                onClick = {
                    haptics.confirm()
                    onSave()
                },
                filled = true,
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }

    if (pickDate) DatePickerPopup(form.dateTime.toLocalDate(), { d -> onUpdate { it.copy(dateTime = d.atTime(it.dateTime.toLocalTime())) } }, { pickDate = false })
    if (pickTime) TimePickerPopup(form.dateTime.toLocalTime(), { t -> onUpdate { it.copy(dateTime = it.dateTime.toLocalDate().atTime(t)) } }, { pickTime = false })
}
