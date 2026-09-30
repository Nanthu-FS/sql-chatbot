package com.spendlens.app.ui.screens.activity

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendlens.app.domain.Category
import com.spendlens.app.domain.Txn
import com.spendlens.app.ui.Format
import com.spendlens.app.ui.appViewModel
import com.spendlens.app.ui.components.Hairline
import com.spendlens.app.ui.components.Label
import com.spendlens.app.ui.components.LocalCurrency
import com.spendlens.app.ui.components.Statement
import com.spendlens.app.ui.components.TextChip
import com.spendlens.app.ui.components.TransactionRow
import com.spendlens.app.ui.components.rememberHaptics
import com.spendlens.app.ui.components.reveal
import com.spendlens.app.ui.components.short
import com.spendlens.app.ui.theme.Spend
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ActivityScreen(onOpenTransaction: (Long) -> Unit, onDelete: (Long) -> Unit) {
    val vm = appViewModel { ActivityViewModel(it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    ActivityContent(
        state = state,
        query = query,
        onQuery = {
            query = it
            vm.search(it)
        },
        onFilter = vm::filterBy,
        onOpen = onOpenTransaction,
        onDelete = onDelete,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActivityContent(
    state: ActivityUiState,
    query: String,
    onQuery: (String) -> Unit,
    onFilter: (Category?) -> Unit,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val colors = Spend.ink
    val currency = LocalCurrency.current
    val haptics = rememberHaptics()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
        item(key = "title") {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 20.dp).padding(top = 22.dp)) {
                Statement("Activity ", "(${state.count})", Modifier.reveal(0), MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(6.dp))
                Label("${currency.format(state.total)} in total", color = colors.muted, modifier = Modifier.reveal(1))
                Spacer(Modifier.height(24.dp))
                Box(Modifier.reveal(2)) {
                    BasicTextField(
                        value = query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(color = colors.text),
                        cursorBrush = SolidColor(colors.text),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        decorationBox = { inner ->
                            Box {
                                if (query.isEmpty()) Label("Search payee, category, app", color = colors.faint, style = MaterialTheme.typography.labelLarge)
                                inner()
                            }
                        },
                    )
                }
                Hairline(color = if (query.isEmpty()) colors.line else colors.text)
            }
        }
        if (state.categories.size > 1) {
            item(key = "filters") {
                LazyRow(
                    Modifier.reveal(3),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item { TextChip("All", state.filter == null, { haptics.tick(); onFilter(null) }) }
                    items(state.categories, key = { it.key }) { c ->
                        TextChip(c.short, state.filter == c, {
                            haptics.tick()
                            onFilter(if (state.filter == c) null else c)
                        })
                    }
                }
            }
        }
        if (!state.loading && state.groups.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.padding(20.dp).padding(top = 40.dp)) {
                    Statement(if (state.hasAny) "Nothing matches. " else "No payments yet. ", if (state.hasAny) "Try another word." else "Tap + to add screenshots.", style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
        state.groups.forEachIndexed { g, group ->
            stickyHeader(key = "h-${group.date}") {
                Row(
                    Modifier.fillMaxWidth().background(colors.canvas).padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Label(Format.dayHeader(group.date), color = colors.text, modifier = Modifier.weight(1f))
                    Label(currency.format(group.total), color = colors.muted)
                }
            }
            items(group.items, key = { it.id }) { txn ->
                SwipeRow(txn, { onOpen(txn.id) }, { onDelete(txn.id) }, Modifier.animateItem().padding(horizontal = 20.dp).reveal(4 + g))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(txn: Txn, onOpen: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Spend.ink
    val currency = LocalCurrency.current
    val haptics = rememberHaptics()
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                haptics.confirm()
                onDelete()
                true
            } else {
                false
            }
        },
    )
    // A tick the moment the swipe passes the point of no return (and when it springs back).
    LaunchedEffect(state) {
        snapshotFlow { state.targetValue }.distinctUntilChanged().collect { if (it != state.currentValue) haptics.tick() }
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(colors.alert).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                Text("[ DELETE ]", style = MaterialTheme.typography.labelLarge, color = colors.inverse)
            }
        },
    ) {
        Box(Modifier.background(colors.canvas)) { TransactionRow(txn, currency, onOpen) }
    }
}
