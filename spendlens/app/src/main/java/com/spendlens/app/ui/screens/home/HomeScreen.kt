package com.spendlens.app.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendlens.app.domain.Dashboard
import com.spendlens.app.domain.PeriodType
import com.spendlens.app.ui.appViewModel
import com.spendlens.app.ui.components.EmptyIllustration
import com.spendlens.app.ui.components.GradientButton
import com.spendlens.app.ui.components.IconTile
import com.spendlens.app.ui.components.Pill
import com.spendlens.app.ui.components.SegmentedTabs
import com.spendlens.app.ui.theme.SpendTheme
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun HomeScreen(
    onOpenTransaction: (Long) -> Unit,
    onScan: () -> Unit,
    onAutoFind: () -> Unit,
    onAddManually: () -> Unit,
    onSeeAll: () -> Unit,
    onSetBudget: () -> Unit,
) {
    val vm = appViewModel { HomeViewModel(it.repository, it.settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    val dashboard = state.dashboard

    Box(Modifier.fillMaxSize()) {
        when {
            state.loading || dashboard == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            !dashboard.hasAnyData -> Welcome(onScan, onAutoFind, onAddManually)
            else -> DashboardContent(
                dashboard = dashboard,
                vm = vm,
                onOpenTransaction = onOpenTransaction,
                onSeeAll = onSeeAll,
                onSetBudget = onSetBudget,
            )
        }
    }
}

@Composable
private fun DashboardContent(
    dashboard: Dashboard,
    vm: HomeViewModel,
    onOpenTransaction: (Long) -> Unit,
    onSeeAll: () -> Unit,
    onSetBudget: () -> Unit,
) {
    val types = PeriodType.entries
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") {
            Header(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = 12.dp))
        }
        item(key = "tabs") {
            SegmentedTabs(
                options = types.map { it.label },
                selectedIndex = types.indexOf(dashboard.period.type),
                onSelect = { vm.selectType(types[it]) },
            )
        }
        item(key = "navigator") {
            PeriodNavigator(
                dashboard = dashboard,
                onPrevious = { vm.shift(-1) },
                onNext = { vm.shift(1) },
                onToday = vm::backToToday,
            )
        }
        item(key = "hero") { HeroCard(dashboard) }
        dashboard.budget?.let { budget ->
            item(key = "budget") { BudgetCard(budget) }
        }
        if (dashboard.budget == null && dashboard.period.type == PeriodType.MONTH) {
            item(key = "budget-cta") { BudgetPrompt(onSetBudget) }
        }
        if (dashboard.insights.isNotEmpty()) {
            item(key = "insights") { InsightsRow(dashboard.insights) }
        }
        item(key = "chart") {
            SpendChartCard(dashboard, onDrillDown = { date ->
                if (dashboard.period.type == PeriodType.YEAR) vm.openMonth(date) else vm.openDay(date)
            })
        }
        if (dashboard.heatmap.isNotEmpty() && dashboard.total > 0) {
            item(key = "heatmap") { HeatmapCard(dashboard, onDayClick = vm::openDay) }
        }
        if (dashboard.categories.isNotEmpty()) {
            item(key = "categories") { CategoriesCard(dashboard) }
        }
        if (dashboard.merchants.size > 1) {
            item(key = "merchants") { MerchantsCard(dashboard.merchants) }
        }
        item(key = "recent") { RecentCard(dashboard, onOpenTransaction, onSeeAll) }
    }
}

@Composable
private fun Header(modifier: Modifier = Modifier) {
    val colors = SpendTheme.colors
    val hour = LocalTime.now().hour
    val greeting = when {
        hour < 5 -> "Up late"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconTile(Icons.Rounded.DocumentScanner, size = 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(greeting, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            Text("Your spending", style = MaterialTheme.typography.headlineSmall)
        }
        Pill(
            text = com.spendlens.app.ui.Format.shortDate(LocalDate.now()),
            color = MaterialTheme.colorScheme.primary,
            icon = Icons.Rounded.Today,
        )
    }
}

@Composable
private fun PeriodNavigator(dashboard: Dashboard, onPrevious: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    val colors = SpendTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous", tint = colors.textMuted)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = dashboard.title,
                transitionSpec = {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
                },
                label = "periodTitle",
            ) { title ->
                Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            }
            AnimatedVisibility(visible = !dashboard.isCurrent) {
                TextButton(onClick = onToday, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                    Text("Back to today", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        IconButton(onClick = onNext, enabled = dashboard.canGoForward) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = "Next",
                tint = if (dashboard.canGoForward) colors.textMuted else colors.textFaint.copy(alpha = 0.4f),
            )
        }
    }
}

@Composable
private fun Welcome(onScan: () -> Unit, onAutoFind: () -> Unit, onAddManually: () -> Unit) {
    val colors = SpendTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 28.dp)
            .padding(bottom = 110.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyIllustration(Modifier.fillMaxWidth().height(280.dp))
        Spacer(Modifier.height(24.dp))
        Text(
            "Screenshots in,\ninsights out.",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Add screenshots of your UPI, card or wallet payments. SpendLens reads them on your phone and charts where your money goes — by day, month and year.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        GradientButton("Add payment screenshots", onClick = onScan, icon = Icons.Rounded.DocumentScanner, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onAutoFind) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Auto-find")
            }
            TextButton(onClick = onAddManually) {
                Icon(Icons.Rounded.EditNote, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add manually")
            }
        }
    }
}
