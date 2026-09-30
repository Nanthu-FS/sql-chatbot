package com.spendlens.app.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendlens.app.domain.BudgetStatus
import com.spendlens.app.domain.Dashboard
import com.spendlens.app.domain.Insight
import com.spendlens.app.domain.MerchantStat
import com.spendlens.app.domain.PeriodType
import com.spendlens.app.ui.appViewModel
import com.spendlens.app.ui.components.BarChart
import com.spendlens.app.ui.components.BracketButton
import com.spendlens.app.ui.components.Dots
import com.spendlens.app.ui.components.Emphasized
import com.spendlens.app.ui.components.Hairline
import com.spendlens.app.ui.components.Label
import com.spendlens.app.ui.components.LocalCurrency
import com.spendlens.app.ui.components.MonthHeatmap
import com.spendlens.app.ui.components.Ribbon
import com.spendlens.app.ui.components.RollingAmount
import com.spendlens.app.ui.components.Section
import com.spendlens.app.ui.components.Sparkline
import com.spendlens.app.ui.components.SplitBar
import com.spendlens.app.ui.components.Statement
import com.spendlens.app.ui.components.TransactionRow
import com.spendlens.app.ui.components.UnderlineTabs
import com.spendlens.app.ui.components.YearHeatmap
import com.spendlens.app.ui.components.bouncy
import com.spendlens.app.ui.components.index
import com.spendlens.app.ui.components.pressable
import com.spendlens.app.ui.components.rememberHaptics
import com.spendlens.app.ui.components.reveal
import com.spendlens.app.ui.components.short
import com.spendlens.app.ui.theme.Spend
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

class HomeActions(
    val onSelectType: (PeriodType) -> Unit = {},
    val onShift: (Long) -> Unit = {},
    val onToday: () -> Unit = {},
    val onOpenDay: (LocalDate) -> Unit = {},
    val onOpenMonth: (LocalDate) -> Unit = {},
    val onOpenTransaction: (Long) -> Unit = {},
    val onSeeAll: () -> Unit = {},
    val onSetBudget: () -> Unit = {},
)

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
    when {
        state.loading || dashboard == null -> Box(Modifier.fillMaxSize())
        !dashboard.hasAnyData -> Welcome(onScan, onAutoFind, onAddManually)
        else -> DashboardContent(
            dashboard,
            HomeActions(
                onSelectType = vm::selectType,
                onShift = vm::shift,
                onToday = vm::backToToday,
                onOpenDay = vm::openDay,
                onOpenMonth = vm::openMonth,
                onOpenTransaction = onOpenTransaction,
                onSeeAll = onSeeAll,
                onSetBudget = onSetBudget,
            ),
        )
    }
}

@Composable
fun DashboardContent(dashboard: Dashboard, actions: HomeActions) {
    val colors = Spend.ink
    val types = PeriodType.entries
    val list = rememberLazyListState()
    LaunchedEffect(dashboard.period.type) { list.animateScrollToItem(0) }
    var n = 0

    LazyColumn(
        Modifier
            .fillMaxSize()
            .drawBehind {
                // A soft grey haze behind the header, like light falling on the page.
                drawRect(
                    Brush.radialGradient(
                        listOf(colors.raised, colors.canvas),
                        center = Offset(size.width * 0.15f, 0f),
                        radius = size.width * 1.1f,
                    ),
                )
            },
        state = list,
        contentPadding = PaddingValues(bottom = 110.dp),
    ) {
        item(key = "top") {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 20.dp).padding(top = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("spendlens", style = MaterialTheme.typography.titleLarge, color = colors.text, modifier = Modifier.weight(1f))
                    Dots(color = colors.muted)
                    Spacer(Modifier.width(10.dp))
                    Label(LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yy")), color = colors.muted)
                }
                Spacer(Modifier.height(18.dp))
                UnderlineTabs(types.map { it.label }, types.indexOf(dashboard.period.type), { actions.onSelectType(types[it]) })
            }
        }
        item(key = "hero") { Hero(dashboard, actions, Modifier.reveal(0)) }
        item(key = "ribbon") {
            val currency = LocalCurrency.current
            val top = dashboard.categories.firstOrNull()?.category?.label
            Ribbon(
                listOfNotNull(
                    dashboard.title,
                    currency.format(dashboard.total) + " spent",
                    "${dashboard.count} payments",
                    top?.let { "most on $it" },
                ).joinToString("     •  •     "),
                Modifier.padding(vertical = 26.dp).reveal(1),
            )
        }
        dashboard.budget?.let { budget ->
            val numBudget = ++n
            item(key = "budget") { Pad(2) { Section(numBudget, "Budget") { BudgetBlock(budget) } } }
        }
        if (dashboard.budget == null && dashboard.period.type == PeriodType.MONTH) {
            val numBudgetCta = ++n
            item(key = "budget-cta") {
                Pad(2) {
                    Section(numBudgetCta, "Budget", trailing = { BracketButton("Set", actions.onSetBudget) }) {
                        Text("No monthly budget yet. Set one to see what you can spend each day.", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                    }
                }
            }
        }
        val numChart = ++n
        item(key = "chart") {
            Pad(3) {
                var selected by remember(dashboard.period) { mutableStateOf<Int?>(null) }
                val currency = LocalCurrency.current
                Section(numChart, when (dashboard.period.type) { PeriodType.DAY -> "By hour"; PeriodType.WEEK, PeriodType.MONTH -> "By day"; PeriodType.YEAR -> "By month" }) {
                    BarChart(
                        bars = dashboard.bars,
                        selectedIndex = selected,
                        onSelect = { selected = it },
                        formatValue = { currency.format(it) },
                        formatAxis = { currency.compact(it) },
                        average = if (dashboard.period.type == PeriodType.DAY) null else dashboard.average,
                    )
                    val date = selected?.let { dashboard.bars.getOrNull(it)?.date }
                    AnimatedVisibility(visible = date != null && dashboard.period.type != PeriodType.DAY) {
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                            BracketButton("Open " + (selected?.let { dashboard.bars[it].tooltipLabel } ?: ""), onClick = {
                                date?.let { if (dashboard.period.type == PeriodType.YEAR) actions.onOpenMonth(it) else actions.onOpenDay(it) }
                            })
                        }
                    }
                }
            }
        }
        if (dashboard.heatmap.isNotEmpty() && dashboard.total > 0) {
            val numHeat = ++n
            item(key = "heat") {
                Pad(4) {
                    Section(numHeat, if (dashboard.period.type == PeriodType.MONTH) "Calendar" else "Year at a glance") {
                        if (dashboard.period.type == PeriodType.MONTH) {
                            MonthHeatmap(dashboard.heatmap, LocalDate.now(), actions.onOpenDay)
                        } else {
                            YearHeatmap(dashboard.heatmap, LocalDate.now(), actions.onOpenDay)
                        }
                    }
                }
            }
        }
        if (dashboard.categories.isNotEmpty()) {
            val numCategories = ++n
            item(key = "categories") { Pad(5) { Section(numCategories, "Where it went") { Categories(dashboard) } } }
        }
        if (dashboard.insights.isNotEmpty()) {
            val numNotes = ++n
            item(key = "notes") { Pad(6) { Section(numNotes, "Notes") { Notes(dashboard.insights) } } }
        }
        if (dashboard.merchants.size > 1) {
            val numPlaces = ++n
            item(key = "places") { Pad(7) { Section(numPlaces, "Places") { Places(dashboard.merchants) } } }
        }
        val numPayments = ++n
        item(key = "payments") {
            Pad(8) {
                val currency = LocalCurrency.current
                Section(numPayments, "Payments", trailing = { BracketButton("All", actions.onSeeAll) }) {
                    if (dashboard.transactions.isEmpty()) {
                        Text("Nothing here yet.", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                    }
                    dashboard.transactions.take(6).forEach { txn ->
                        TransactionRow(txn, currency, { actions.onOpenTransaction(txn.id) }, showDate = dashboard.period.type != PeriodType.DAY)
                    }
                }
            }
        }
    }
}

@Composable
private fun Pad(order: Int, content: @Composable () -> Unit) {
    Box(Modifier.padding(horizontal = 20.dp).padding(bottom = 44.dp).reveal(order)) { content() }
}

@Composable
private fun Hero(dashboard: Dashboard, actions: HomeActions, modifier: Modifier = Modifier) {
    val colors = Spend.ink
    val currency = LocalCurrency.current
    val haptics = rememberHaptics()
    Column(modifier.padding(horizontal = 20.dp).padding(top = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.ChevronLeft, "Previous", tint = colors.muted,
                modifier = Modifier.size(28.dp).pressable(pressedScale = 0.8f) { actions.onShift(-1) },
            )
            AnimatedContent(
                targetState = dashboard.title,
                transitionSpec = {
                    (slideInVertically(tween(420, easing = Emphasized)) { it / 2 } + fadeIn(tween(300))) togetherWith
                        (slideOutVertically(tween(300)) { -it / 2 } + fadeOut(tween(200)))
                },
                modifier = Modifier.weight(1f),
                label = "title",
            ) { title ->
                Label(title, color = colors.text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp))
            }
            if (!dashboard.isCurrent) {
                BracketButton("Today", onClick = actions.onToday)
            }
            Icon(
                Icons.Rounded.ChevronRight, "Next",
                tint = if (dashboard.canGoForward) colors.muted else colors.ghost,
                modifier = Modifier.size(28.dp).pressable(pressedScale = 0.8f, haptic = dashboard.canGoForward) {
                    if (dashboard.canGoForward) actions.onShift(1) else haptics.reject()
                },
            )
        }
        Spacer(Modifier.height(28.dp))
        val (ink, tail) = when (dashboard.period.type) {
            PeriodType.DAY -> "Spent " to if (dashboard.isCurrent) "today" else "that day"
            PeriodType.WEEK -> "Spent " to if (dashboard.isCurrent) "this week" else "that week"
            PeriodType.MONTH -> "Spent " to if (dashboard.isCurrent) "this month" else "that month"
            PeriodType.YEAR -> "Spent " to if (dashboard.isCurrent) "this year" else "that year"
        }
        Statement(ink, tail, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        RollingAmount(dashboard.total, currency, MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(10.dp))
        val change = dashboard.change
        Label(
            when {
                change == null -> "No earlier data to compare"
                else -> "${if (change >= 0) "+" else "−"}${(abs(change) * 100).roundToInt()}%  ${dashboard.comparisonLabel}"
            },
            color = if (change != null && change > 0.1f) colors.text else colors.muted,
        )
        Spacer(Modifier.height(20.dp))
        Sparkline(dashboard.cumulative, dashboard.bars.size, Modifier.fillMaxWidth().height(56.dp))
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth()) {
            Stat("Payments", dashboard.count.toString(), Modifier.weight(1f))
            Stat(dashboard.averageLabel, currency.compact(dashboard.average), Modifier.weight(1f))
            Stat("Largest", dashboard.largest?.let { currency.compact(it.amountMinor) } ?: "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    val colors = Spend.ink
    Column(modifier) {
        Hairline()
        Spacer(Modifier.height(8.dp))
        Label(label, color = colors.faint)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = colors.text, maxLines = 1)
    }
}

@Composable
private fun BudgetBlock(budget: BudgetStatus) {
    val colors = Spend.ink
    val currency = LocalCurrency.current
    val over = budget.remaining < 0
    val fill by animateFloatAsState(budget.fraction.coerceIn(0f, 1f), bouncy(), label = "budget")
    Row(verticalAlignment = Alignment.Bottom) {
        Statement(
            if (over) currency.format(-budget.remaining) else currency.format(budget.remaining),
            if (over) " over" else " left",
            Modifier.weight(1f),
            MaterialTheme.typography.headlineLarge,
        )
        Label("${(budget.fraction * 100).roundToInt()}%", color = if (over) colors.alert else colors.text, style = MaterialTheme.typography.labelLarge)
    }
    Spacer(Modifier.height(14.dp))
    Box(Modifier.fillMaxWidth().height(3.dp).background(colors.line)) {
        Box(Modifier.fillMaxWidth(fill).height(3.dp).background(if (over) colors.alert else colors.text))
    }
    Spacer(Modifier.height(10.dp))
    Row {
        Label("of ${currency.format(budget.limit)} · ${budget.label}", color = colors.faint, modifier = Modifier.weight(1f))
        val allowance = budget.dailyAllowance
        if (allowance != null && budget.daysLeft != null && !over) {
            Label("≈ ${currency.format(allowance)} / day · ${budget.daysLeft}d", color = colors.muted)
        }
    }
}

@Composable
private fun Categories(dashboard: Dashboard) {
    val colors = Spend.ink
    val currency = LocalCurrency.current
    val haptics = rememberHaptics()
    val slices = dashboard.categories
    var selected by remember(dashboard.period, slices.size) { mutableStateOf<Int?>(null) }
    SplitBar(slices.map { it.fraction }, selected)
    Spacer(Modifier.height(12.dp))
    slices.forEachIndexed { i, slice ->
        val dim = selected != null && selected != i
        Column(
            Modifier
                .alpha(if (dim) 0.35f else 1f)
                .pressable(pressedScale = 0.98f, haptic = false) {
                    haptics.tick()
                    selected = if (selected == i) null else i
                },
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Bottom) {
                Label(index(i + 1), color = colors.faint, modifier = Modifier.width(38.dp).padding(bottom = 4.dp))
                Text(
                    slice.category.label.uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (i == 0 || selected == i) colors.text else colors.muted,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(currency.format(slice.amountMinor), style = MaterialTheme.typography.titleSmall, color = colors.text)
                    Label("${(slice.fraction * 100).roundToInt()}% · ${slice.count}", color = colors.faint)
                }
            }
            Hairline()
        }
    }
}

@Composable
private fun Notes(insights: List<Insight>) {
    val colors = Spend.ink
    val lead = insights.first()
    Statement(lead.title + ". ", lead.body, style = MaterialTheme.typography.headlineMedium)
    insights.drop(1).forEachIndexed { i, note ->
        Spacer(Modifier.height(if (i == 0) 20.dp else 0.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Label(index(i + 2), color = colors.faint, modifier = Modifier.width(38.dp))
            Column(Modifier.weight(1f)) {
                Label(note.title, color = colors.text)
                Spacer(Modifier.height(3.dp))
                Text(note.body, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            }
        }
        Hairline()
    }
}

@Composable
private fun Places(merchants: List<MerchantStat>) {
    val colors = Spend.ink
    val currency = LocalCurrency.current
    merchants.forEachIndexed { i, m ->
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Label(index(i + 1), color = colors.faint, modifier = Modifier.width(38.dp))
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.titleMedium, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Label("${m.category.short} · ${m.count}×", color = colors.faint)
            }
            Text(currency.format(m.amountMinor), style = MaterialTheme.typography.titleSmall, color = colors.text)
        }
        Hairline()
    }
}

@Composable
fun Welcome(onScan: () -> Unit, onAutoFind: () -> Unit, onAddManually: () -> Unit) {
    val colors = Spend.ink
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp, bottom = 96.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("spendlens", style = MaterialTheme.typography.titleLarge, color = colors.text, modifier = Modifier.weight(1f))
            Dots(color = colors.muted)
        }
        Spacer(Modifier.weight(1f))
        Statement("Screenshots in. ", "Clarity out.", Modifier.reveal(0), MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(16.dp))
        Text(
            "Add screenshots of your UPI, card or wallet payments. SpendLens reads them on this phone and shows where your money goes — by day, month and year.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.muted,
            modifier = Modifier.reveal(1),
        )
        Spacer(Modifier.height(32.dp))
        listOf("Add screenshots", "Check what was read", "See where it goes").forEachIndexed { i, step ->
            Column(Modifier.reveal(2 + i)) {
                Hairline()
                Row(Modifier.padding(vertical = 12.dp)) {
                    Label(index(i + 1), color = colors.faint, modifier = Modifier.width(38.dp))
                    Label(step, color = colors.text)
                }
            }
        }
        Hairline()
        Spacer(Modifier.height(28.dp))
        BracketButton("Add screenshots", onClick = onScan, filled = true, modifier = Modifier.fillMaxWidth().reveal(5))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().reveal(6), horizontalArrangement = Arrangement.SpaceBetween) {
            BracketButton("Auto-find", onClick = onAutoFind)
            BracketButton("Add manually", onClick = onAddManually)
        }
    }
}
