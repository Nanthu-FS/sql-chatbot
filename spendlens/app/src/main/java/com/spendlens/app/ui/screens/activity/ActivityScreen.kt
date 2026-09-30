package com.spendlens.app.ui.screens.activity

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendlens.app.domain.Category
import com.spendlens.app.domain.Txn
import com.spendlens.app.ui.Format
import com.spendlens.app.ui.appViewModel
import com.spendlens.app.ui.components.EmptyIllustration
import com.spendlens.app.ui.components.LocalCurrency
import com.spendlens.app.ui.components.TransactionRow
import com.spendlens.app.ui.components.bounceClick
import com.spendlens.app.ui.components.color
import com.spendlens.app.ui.components.icon
import com.spendlens.app.ui.theme.SpendTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActivityScreen(onOpenTransaction: (Long) -> Unit, onDelete: (Long) -> Unit) {
    val vm = appViewModel { ActivityViewModel(it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = SpendTheme.colors
    val currency = LocalCurrency.current
    var query by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 130.dp),
    ) {
        item(key = "title") {
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp),
            ) {
                Text("Activity", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "${state.count} ${if (state.count == 1) "payment" else "payments"} · ${currency.format(state.total)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )
                Spacer(Modifier.height(16.dp))
                TextField(
                    value = query,
                    onValueChange = {
                        query = it
                        vm.search(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search payee, category, app…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                query = ""
                                vm.search("")
                            }) { Icon(Icons.Rounded.Close, "Clear") }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.subtle,
                        unfocusedContainerColor = colors.subtle,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            }
        }
        if (state.categories.size > 1) {
            item(key = "filters") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { FilterChip("All", null, state.filter == null) { vm.filterBy(null) } }
                    items(state.categories, key = { it.key }) { category ->
                        FilterChip(category.label, category, state.filter == category) {
                            vm.filterBy(if (state.filter == category) null else category)
                        }
                    }
                }
            }
        }
        if (!state.loading && state.groups.isEmpty()) {
            item(key = "empty") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EmptyIllustration(Modifier.size(220.dp))
                    Text(
                        if (state.hasAny) "No payments match" else "No payments yet",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        if (state.hasAny) "Try another search or filter." else "Tap the scan button to add payment screenshots.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        state.groups.forEach { group ->
            stickyHeader(key = "h-${group.date}") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(Format.dayHeader(group.date), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(currency.format(group.total), style = MaterialTheme.typography.labelLarge, color = colors.textMuted)
                }
            }
            items(group.items, key = { it.id }) { txn ->
                SwipeRow(
                    txn = txn,
                    onOpen = { onOpenTransaction(txn.id) },
                    onDelete = { onDelete(txn.id) },
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = 12.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(txn: Txn, onOpen: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SpendTheme.colors
    val currency = LocalCurrency.current
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val bg by animateColorAsState(
                if (state.targetValue == SwipeToDismissBoxValue.EndToStart) colors.negative else colors.negative.copy(alpha = 0.5f),
                label = "swipeBg",
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(bg)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color.White)
            }
        },
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            TransactionRow(txn = txn, currency = currency, onClick = onOpen)
        }
    }
}

@Composable
private fun FilterChip(label: String, category: Category?, selected: Boolean, onClick: () -> Unit) {
    val colors = SpendTheme.colors
    val accent = category?.color ?: MaterialTheme.colorScheme.primary
    val background by animateColorAsState(if (selected) accent else colors.subtle, label = "chipBg")
    val content by animateColorAsState(if (selected) Color.White else colors.textMuted, label = "chipFg")
    Row(
        Modifier
            .clip(CircleShape)
            .background(background)
            .bounceClick(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (category != null) {
            Icon(category.icon, null, tint = if (selected) Color.White else accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = content)
    }
}
