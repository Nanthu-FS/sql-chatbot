package com.spendlens.app.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendlens.app.domain.Anomaly
import com.spendlens.app.domain.BudgetStatus
import com.spendlens.app.domain.Category
import com.spendlens.app.domain.CategoryTrend
import com.spendlens.app.domain.DayPart
import com.spendlens.app.domain.GoalPlan
import com.spendlens.app.domain.Insight
import com.spendlens.app.domain.MerchantStat
import com.spendlens.app.domain.Period
import com.spendlens.app.domain.PeriodType
import com.spendlens.app.domain.SpendPatterns
import com.spendlens.app.domain.percentLabel
import com.spendlens.app.ui.Format
import com.spendlens.app.ui.appViewModel
import com.spendlens.app.ui.components.caps
import com.spendlens.app.ui.components.BarChart
import com.spendlens.app.ui.components.BracketButton
import com.spendlens.app.ui.components.Dots
import com.spendlens.app.ui.components.Emphasized
import com.spendlens.app.ui.components.Hairline
import com.spendlens.app.ui.components.Label
import com.spendlens.app.ui.components.LocalCurrency
import com.spendlens.app.ui.components.LocalGlass
import com.spendlens.app.ui.components.MiniSpark
import com.spendlens.app.ui.components.MonthHeatmap
import com.spendlens.app.ui.components.Ribbon
import com.spendlens.app.ui.components.RollingAmount
import com.spendlens.app.ui.components.Screen
import com.spendlens.app.ui.components.Section
import com.spendlens.app.ui.components.Sparkline
import com.spendlens.app.ui.components.SplitBar
import com.spendlens.app.ui.components.Statement
import com.spendlens.app.ui.components.TransactionRow
import com.spendlens.app.ui.components.UnderlineTabs
import com.spendlens.app.ui.components.YearHeatmap
import com.spendlens.app.ui.components.bouncy
import com.spendlens.app.ui.components.controlShape
import com.spendlens.app.ui.components.sectionFrame
import com.spendlens.app.ui.components.glass
import com.spendlens.app.ui.components.index
import com.spendlens.app.ui.components.pressable
import com.spendlens.app.ui.components.rememberHaptics
import com.spendlens.app.ui.components.reveal
import com.spendlens.app.ui.components.short
import com.spendlens.app.ui.theme.CardStyle
import com.spendlens.app.ui.theme.Spend
import com.spendlens.app.ui.theme.Style
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
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
    val onDismissAlert: (String) -> Unit = {},
    val onPutAside: (Long, Long) -> Unit = { _, _ -> },
    val onOpenGoals: () -> Unit = {},
    val onWrap: (Period) -> Unit = {},
    val onCompare: (Period) -> Unit = {},
)

@Composable
fun HomeScreen(
    onOpenTransaction: (Long) -> Unit,
    onScan: () -> Unit,
    onAutoFind: () -> Unit,
    onAddManually: () -> Unit,
    onSeeAll: () -> Unit,
    onSetBudget: () -> Unit,
    onOpenGoals: () -> Unit,
    onWrap: (Period) -> Unit,
    onCompare: (Period) -> Unit,
) {
    val vm = appViewModel { HomeViewModel(it.repository, it.settings) }
    val state by vm.state.collectAsStateWithLifecycle()
    val dashboard = state.dashboard
    when {
        state.loading || dashboard == null -> Screen {}
        !dashboard.hasAnyData -> Welcome(onScan, onAutoFind, onAddManually, onTrySamples = vm::loadSamples)
        else -> DashboardContent(
            state,
            HomeActions(
                onSelectType = vm::selectType,
                onShift = vm::shift,
                onToday = vm::backToToday,
                onOpenDay = vm::openDay,
                onOpenMonth = vm::openMonth,
                onOpenTransaction = onOpenTransaction,
                onSeeAll = onSeeAll,
                onSetBudget = onSetBudget,
                onDismissAlert = vm::dismissAlert,
                onPutAside = vm::putAside,
                onOpenGoals = onOpenGoals,
                onWrap = onWrap,
                onCompare = onCompare,
            ),
        )
    }
}

@Composable
fun DashboardContent(state: HomeUiState, actions: HomeActions) {
    val dashboard = state.dashboard ?: return
    val colors = Spend.ink
    val types = PeriodType.entries
    val list = rememberLazyListState()
    LaunchedEffect(dashboard.period.type) { list.animateScrollToItem(0) }
    val isDay = dashboard.period.type == PeriodType.DAY
    val look = Spend.look
    // The calendar look leads with the month grid.
    val calendarFirst = look.style == Style.CALENDAR

    Screen {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 110.dp)) {
            // Section numbers follow whichever sections are present; counted afresh on every pass.
            var n = 0
            item(key = "top") {
                Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 20.dp).padding(top = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("spendlens", style = MaterialTheme.typography.titleLarge, color = colors.text, modifier = Modifier.weight(1f))
                        val streak = state.streaks?.current ?: 0
                        if (streak >= 2) {
                            StreakBadge(streak)
                            Spacer(Modifier.width(10.dp))
                        }
                        Dots(color = colors.muted)
                        Spacer(Modifier.width(10.dp))
                        Label(LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yy")), color = colors.muted)
                    }
                    Spacer(Modifier.height(18.dp))
                    UnderlineTabs(types.map { it.label }, types.indexOf(dashboard.period.type), { actions.onSelectType(types[it]) })
                }
            }
            item(key = "hero") { Hero(state, actions, Modifier.reveal(0)) }
            if (!look.ribbon) item(key = "gap") { Spacer(Modifier.height(28.dp)) }
            if (look.ribbon) item(key = "ribbon") {
                val currency = LocalCurrency.current
                val top = dashboard.categories.firstOrNull()?.category?.label
                Ribbon(
                    listOfNotNull(
                        dashboard.title,
                        currency.format(dashboard.total) + " spent",
                        "${dashboard.count} payments",
                        top?.let { "most on $it" },
                        state.streaks?.longest?.takeIf { it >= 2 }?.let { "longest quiet run $it days" },
                    ).joinToString("     •  •     "),
                    Modifier.padding(vertical = 26.dp).reveal(1),
                )
            }
            fun heat() {
                if (dashboard.heatmap.isNotEmpty() && dashboard.total > 0) {
                    val num = ++n
                    item(key = "heat") {
                        Pad(4) {
                            Section(num, if (dashboard.period.type == PeriodType.MONTH) "Calendar" else "Year at a glance") {
                                if (dashboard.period.type == PeriodType.MONTH) {
                                    MonthHeatmap(dashboard.heatmap, LocalDate.now(), actions.onOpenDay)
                                } else {
                                    YearHeatmap(dashboard.heatmap, LocalDate.now(), actions.onOpenDay)
                                }
                            }
                        }
                    }
                }
            }
            if (calendarFirst) heat()
            if (state.anomalies.isNotEmpty()) {
                val num = ++n
                item(key = "alerts") { Pad(2) { Section(num, "Worth a look") { Alerts(state.anomalies, actions) } } }
            }
            dashboard.budget?.let { budget ->
                val num = ++n
                item(key = "budget") { Pad(2) { Section(num, "Budget") { BudgetBlock(budget) } } }
            }
            if (dashboard.budget == null && dashboard.period.type == PeriodType.MONTH) {
                val num = ++n
                item(key = "budget-cta") {
                    Pad(2) {
                        Section(num, "Budget", trailing = { BracketButton("Set", actions.onSetBudget) }) {
                            Text("No monthly budget yet. Set one to see what you can spend each day.", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                        }
                    }
                }
            }
            val chartNum = ++n
            item(key = "chart") {
                Pad(3) {
                    var selected by remember(dashboard.period) { mutableStateOf<Int?>(null) }
                    val currency = LocalCurrency.current
                    Section(chartNum, if (isDay) "By hour" else if (dashboard.period.type == PeriodType.YEAR) "By month" else "By day") {
                        BarChart(
                            bars = dashboard.bars,
                            selectedIndex = selected,
                            onSelect = { selected = it },
                            formatValue = { currency.format(it) },
                            formatAxis = { currency.compact(it) },
                            average = if (isDay) null else dashboard.average,
                        )
                        val date = selected?.let { dashboard.bars.getOrNull(it)?.date }
                        AnimatedVisibility(visible = date != null && !isDay) {
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                                BracketButton("Open " + (selected?.let { dashboard.bars[it].tooltipLabel } ?: ""), onClick = {
                                    date?.let { if (dashboard.period.type == PeriodType.YEAR) actions.onOpenMonth(it) else actions.onOpenDay(it) }
                                })
                            }
                        }
                    }
                }
            }
            if (!calendarFirst) heat()
            if (dashboard.categories.isNotEmpty()) {
                val num = ++n
                item(key = "categories") { Pad(5) { Section(num, "Where it went") { Categories(state) } } }
            }
            state.patterns?.takeIf { it.sampleSize >= 5 }?.let { patterns ->
                val num = ++n
                item(key = "patterns") {
                    Pad(6) { Section(num, "When you spend", trailing = { Label("Last 90 days", color = colors.faint) }) { Patterns(patterns) } }
                }
            }
            val goalsNum = ++n
            item(key = "goals") {
                Pad(7) {
                    Section(goalsNum, "Goals", trailing = { BracketButton(if (state.goals.isEmpty()) "New" else "Manage", actions.onOpenGoals) }) {
                        Goals(state.goals, state.hasIncome, actions)
                    }
                }
            }
            if (dashboard.insights.isNotEmpty()) {
                val num = ++n
                item(key = "notes") { Pad(8) { Section(num, "Notes") { Notes(dashboard.insights) } } }
            }
            if (dashboard.merchants.size > 1) {
                val num = ++n
                item(key = "places") { Pad(9) { Section(num, "Places") { Places(dashboard.merchants) } } }
            }
            val paymentsNum = ++n
            item(key = "payments") {
                Pad(10) {
                    val currency = LocalCurrency.current
                    Section(paymentsNum, "Payments", trailing = { BracketButton("All", actions.onSeeAll) }) {
                        if (dashboard.transactions.isEmpty()) {
                            Text("Nothing here yet.", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                        }
                        dashboard.transactions.take(6).forEach { txn ->
                            TransactionRow(txn, currency, { actions.onOpenTransaction(txn.id) }, showDate = !isDay)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Pad(order: Int, content: @Composable () -> Unit) {
    val look = Spend.look
    val glassy = LocalGlass.current > 0.01f
    val (side, gap) = when (look.card) {
        CardStyle.PRINT -> if (glassy) 12.dp to 16.dp else 20.dp to 44.dp
        CardStyle.PAPER, CardStyle.RULED -> 20.dp to 32.dp
        CardStyle.BRUTAL -> 20.dp to 24.dp
        else -> 20.dp to 14.dp
    }
    Box(Modifier.padding(horizontal = side).padding(bottom = gap).reveal(order)) { content() }
}

@Composable
private fun StreakBadge(days: Int) {
    val colors = Spend.ink
    Row(Modifier.border(1.dp, colors.accent, controlShape()).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).background(colors.accent))
        Spacer(Modifier.width(6.dp))
        Label("$days-day no-spend streak", color = colors.text)
    }
}

@Composable
private fun Hero(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    val dashboard = state.dashboard ?: return
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
            if (!dashboard.isCurrent) BracketButton("Today", onClick = actions.onToday)
            Icon(
                Icons.Rounded.ChevronRight, "Next",
                tint = if (dashboard.canGoForward) colors.muted else colors.ghost,
                modifier = Modifier.size(28.dp).pressable(pressedScale = 0.8f, haptic = dashboard.canGoForward) {
                    if (dashboard.canGoForward) actions.onShift(1) else haptics.reject()
                },
            )
        }
        Spacer(Modifier.height(28.dp))
        when (Spend.look.style) {
            Style.BENTO -> BentoHero(state)
            Style.BRUTAL -> Column(Modifier.fillMaxWidth().sectionFrame()) { ClassicHero(state) }
            else -> ClassicHero(state)
        }
        if (dashboard.period.type != PeriodType.DAY) {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BracketButton("${dashboard.period.type.label} wrapped", onClick = { actions.onWrap(dashboard.period) })
                BracketButton("Compare", onClick = { actions.onCompare(dashboard.period) })
            }
        }
    }
}

/** Total, change, forecast, a chart and three stats — shaped by the current look. */
@Composable
private fun ClassicHero(state: HomeUiState) {
    val dashboard = state.dashboard ?: return
    val colors = Spend.ink
    val look = Spend.look
    val currency = LocalCurrency.current
    val noun = when (dashboard.period.type) {
        PeriodType.DAY -> if (dashboard.isCurrent) "today" else "that day"
        PeriodType.WEEK -> if (dashboard.isCurrent) "this week" else "that week"
        PeriodType.MONTH -> if (dashboard.isCurrent) "this month" else "that month"
        PeriodType.YEAR -> if (dashboard.isCurrent) "this year" else "that year"
    }
    Statement("Spent ", noun, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(6.dp))
    val big = when (look.style) {
        Style.SWISS -> MaterialTheme.typography.displayLarge.copy(fontSize = 84.sp, lineHeight = 84.sp)
        Style.DOT -> MaterialTheme.typography.displayLarge.copy(fontSize = 76.sp, lineHeight = 76.sp)
        Style.RECEIPT -> MaterialTheme.typography.displayLarge.copy(fontSize = 50.sp, lineHeight = 54.sp)
        else -> MaterialTheme.typography.displayLarge
    }
    RollingAmount(dashboard.total, currency, big)
    Spacer(Modifier.height(10.dp))
    val change = dashboard.change
    Label(
        if (change == null) dashboard.noComparisonLabel else "${percentLabel(change)}  ${dashboard.comparisonLabel}",
        color = if (change != null && change > 0.1f) colors.text else colors.muted,
    )
    dashboard.forecast?.let { f ->
        Spacer(Modifier.height(6.dp))
        val end = dashboard.period.endExclusive.minusDays(1).format(DateTimeFormatter.ofPattern("d MMM"))
        Label("At this pace ≈ ${currency.compact(f)} by $end", color = colors.text)
    }
    Spacer(Modifier.height(20.dp))
    when (look.style) {
        Style.DOT -> DotDays(dashboard.bars)
        Style.RECEIPT -> Barcode(dashboard.bars)
        else -> Sparkline(dashboard.cumulative, dashboard.bars.size, Modifier.fillMaxWidth().height(56.dp), forecast = dashboard.forecast)
    }
    Spacer(Modifier.height(18.dp))
    val largest = dashboard.largest?.let { currency.compact(it.amountMinor) } ?: "—"
    if (look.style == Style.SWISS) {
        StatCells(listOf("Payments" to dashboard.count.toString(), dashboard.averageLabel to currency.compact(dashboard.average), "Largest" to largest))
    } else {
        Row(Modifier.fillMaxWidth()) {
            Stat("Payments", dashboard.count.toString(), Modifier.weight(1f))
            Stat(dashboard.averageLabel, currency.compact(dashboard.average), Modifier.weight(1f))
            Stat("Largest", largest, Modifier.weight(1f))
        }
    }
}

/** Bento: a total tile with a budget ring, then four small tiles. */
@Composable
private fun BentoHero(state: HomeUiState) {
    val dashboard = state.dashboard ?: return
    val colors = Spend.ink
    val currency = LocalCurrency.current
    val look = Spend.look
    Row(
        Modifier.fillMaxWidth().background(colors.surface, RoundedCornerShape(look.radius)).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val noun = when (dashboard.period.type) {
                PeriodType.DAY -> if (dashboard.isCurrent) "today" else "that day"
                PeriodType.WEEK -> if (dashboard.isCurrent) "this week" else "that week"
                PeriodType.MONTH -> if (dashboard.isCurrent) "this month" else "that month"
                PeriodType.YEAR -> if (dashboard.isCurrent) "this year" else "that year"
            }
            Label("Spent $noun", color = colors.muted, style = MaterialTheme.typography.labelLarge)
            RollingAmount(dashboard.total, currency, MaterialTheme.typography.displaySmall)
            val change = dashboard.change
            Text(
                if (change == null) dashboard.noComparisonLabel else "${percentLabel(change)} ${dashboard.comparisonLabel}",
                style = MaterialTheme.typography.bodyMedium, color = colors.muted,
            )
            dashboard.forecast?.let { Text("Pace ≈ ${currency.compact(it)}", style = MaterialTheme.typography.bodyMedium, color = colors.muted) }
            val budget = dashboard.budget
            if (budget != null && budget.remaining < 0) {
                Text(
                    "${currency.format(-budget.remaining)} over budget",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.alert,
                    modifier = Modifier.padding(top = 6.dp).background(colors.alert.copy(alpha = 0.14f), RoundedCornerShape(99.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        val budget = dashboard.budget
        val f = dashboard.forecast
        when {
            budget != null -> BudgetRing(budget.fraction, "${(budget.fraction * 100).roundToInt()}%", "of ${currency.compact(budget.limit)}")
            f != null && f > 0 -> BudgetRing(dashboard.total.toFloat() / f, currency.compact(dashboard.total), "of ≈${currency.compact(f)}")
        }
    }
    Spacer(Modifier.height(12.dp))
    val streak = state.streaks?.current ?: 0
    val top = dashboard.categories.firstOrNull()
    val patterns = state.patterns
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BentoTile("No-spend streak", if (streak == 1) "1 day" else "$streak days", "Longest ${state.streaks?.longest ?: 0}", Modifier.weight(1f), accent = true)
            BentoTile(
                "Worth a look", state.anomalies.size.toString(),
                state.anomalies.take(2).joinToString(" · ") { it.txn.merchant }.ifEmpty { "All clear" },
                Modifier.weight(1f),
                footColor = if (state.anomalies.isNotEmpty()) colors.alert else null,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BentoTile("Most on", top?.category?.label ?: "—", top?.let { "${(it.fraction * 100).roundToInt()}% · ${currency.compact(it.amountMinor)}" } ?: "", Modifier.weight(1f))
            val day = patterns?.topDay
            val part = patterns?.topPart
            BentoTile(
                "You spend most",
                if (day != null && part != null) dayPartLabel(day, part) else "—",
                patterns?.busiestHour?.let { "Busiest hour · ${Format.hour(it)}" } ?: "",
                Modifier.weight(1f),
            )
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
private fun Alerts(anomalies: List<Anomaly>, actions: HomeActions) {
    val colors = Spend.ink
    val currency = LocalCurrency.current
    anomalies.take(4).forEachIndexed { i, a ->
        if (i > 0) Hairline()
        Column(Modifier.fillMaxWidth().pressable(pressedScale = 0.98f) { actions.onOpenTransaction(a.txn.id) }.padding(vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(colors.alert))
                Spacer(Modifier.width(8.dp))
                Label(a.title, color = colors.alert, modifier = Modifier.weight(1f))
                Text(currency.format(a.txn.amountMinor), style = MaterialTheme.typography.titleSmall, color = colors.text)
            }
            Spacer(Modifier.height(6.dp))
            Text("${a.txn.merchant} · ${Format.relative(a.txn.dateTime)}", style = MaterialTheme.typography.titleMedium, color = colors.text)
            Text(a.detail, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                BracketButton("Looks fine", onClick = { actions.onDismissAlert(a.key) }, color = colors.muted)
            }
        }
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
        Box(Modifier.fillMaxWidth(fill).height(3.dp).background(if (over) colors.alert else colors.accent))
    }
    Spacer(Modifier.height(10.dp))
    Row {
        Label("of ${currency.format(budget.limit)} · ${budget.label}", color = colors.faint, modifier = Modifier.weight(1f))
        val allowance = budget.dailyAllowance
        if (allowance != null && budget.daysLeft != null && !over) {
            Label("≈ ${currency.format(allowance)} / day · ${budget.daysLeft}d", color = colors.muted)
        }
    }
    budget.projectedOver?.let { overBy ->
        if (!over) {
            Spacer(Modifier.height(10.dp))
            Label("At this pace you'll go ${currency.format(overBy)} over", color = colors.alert)
        }
    }
}

@Composable
private fun Categories(state: HomeUiState) {
    val dashboard = state.dashboard ?: return
    val colors = Spend.ink
    val currency = LocalCurrency.current
    val haptics = rememberHaptics()
    val slices = dashboard.categories
    when (Spend.look.style) {
        Style.RECEIPT -> return ReceiptLines(slices, currency)
        Style.DOT -> return DotShares(slices, currency)
        Style.BRUTAL -> return BoxedBars(slices)
        Style.WALLET -> return WalletStack(slices, state.trends, currency)
        Style.SWISS -> {
            SplitBar(slices.map { it.fraction }, null, Modifier.height(28.dp))
            Spacer(Modifier.height(14.dp))
            return SplitLegend(slices)
        }
        else -> Unit
    }
    var selected by remember(dashboard.period, slices.size) { mutableStateOf<Int?>(null) }
    SplitBar(slices.map { it.fraction }, selected)
    Spacer(Modifier.height(12.dp))
    slices.forEachIndexed { i, slice ->
        val dim = selected != null && selected != i
        val trend: CategoryTrend? = state.trends[slice.category]
        Column(
            Modifier
                .alpha(if (dim) 0.35f else 1f)
                .pressable(pressedScale = 0.98f, haptic = false) {
                    haptics.tick()
                    selected = if (selected == i) null else i
                },
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (Spend.look.numbered) Label(index(i + 1), color = colors.faint, modifier = Modifier.width(34.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        caps(slice.category.label),
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (i == 0 || selected == i) colors.text else colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    trend?.change?.let { c ->
                        Label("${percentLabel(c)} vs usual", color = if (c > 0.15f) colors.text else colors.faint)
                    }
                }
                if (trend != null && trend.monthly.count { it > 0 } >= 2) {
                    MiniSpark(trend.monthly, Modifier.width(44.dp).height(20.dp))
                    Spacer(Modifier.width(12.dp))
                }
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
private fun Patterns(p: SpendPatterns) {
    val colors = Spend.ink
    val day = p.topDay
    val part = p.topPart
    if (day != null && part != null) {
        Statement("You spend most on ", "${day.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${part.label}.", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(18.dp))
    }
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(26.dp))
        DayPart.entries.forEach { dp ->
            Label(dp.short, color = colors.faint, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
        }
    }
    Spacer(Modifier.height(6.dp))
    DayOfWeek.entries.forEachIndexed { d, dow ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Label(dow.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2), color = colors.faint, modifier = Modifier.width(26.dp))
            DayPart.entries.forEachIndexed { pi, _ ->
                val v = p.grid[d][pi]
                val t = if (p.max > 0 && v > 0) 0.15f + 0.85f * v / p.max else 0f
                val top = d == (day?.value?.minus(1)) && pi == part?.ordinal
                Box(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .height(18.dp)
                        .background(colors.heat(t))
                        .then(if (top) Modifier.border(1.dp, colors.text) else Modifier),
                )
            }
        }
    }
    p.busiestHour?.let { h ->
        Spacer(Modifier.height(12.dp))
        Label("Busiest hour · ${Format.hour(h)}", color = colors.muted)
    }
}

@Composable
private fun Goals(goals: List<GoalPlan>, hasIncome: Boolean, actions: HomeActions) {
    val colors = Spend.ink
    val currency = LocalCurrency.current
    if (goals.isEmpty()) {
        Text("Saving for something? Set a goal and see how much to put aside each month.", style = MaterialTheme.typography.bodyMedium, color = colors.muted)
        return
    }
    goals.forEachIndexed { i, plan ->
        if (i > 0) Hairline(Modifier.padding(vertical = 4.dp))
        GoalBlock(plan, currency, onPutAside = { actions.onPutAside(plan.goal.id, it) })
    }
    if (!hasIncome) {
        Spacer(Modifier.height(10.dp))
        Label("Add your monthly income in Settings for suggestions", color = colors.faint)
    }
}

@Composable
fun GoalBlock(plan: GoalPlan, currency: com.spendlens.app.domain.CurrencyOption, onPutAside: (Long) -> Unit) {
    val colors = Spend.ink
    val fill by animateFloatAsState(plan.progress, bouncy(), label = "goal")
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(caps(plan.goal.name), style = MaterialTheme.typography.headlineSmall, color = colors.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Label("${(plan.progress * 100).roundToInt()}%", color = colors.text, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).background(colors.line)) {
            Box(Modifier.fillMaxWidth(fill).height(3.dp).background(colors.accent))
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label("${currency.format(plan.goal.savedMinor)} of ${currency.format(plan.goal.targetMinor)}", color = colors.faint)
            when {
                plan.done -> Label("Done — well saved", color = colors.text)
                else -> {
                    val deadline = plan.goal.deadline
                    if (plan.neededPerMonth != null && deadline != null) {
                        Label("${currency.format(plan.neededPerMonth)} / month to hit ${deadline.format(DateTimeFormatter.ofPattern("MMM yyyy"))}", color = colors.muted)
                    }
                    if (plan.onTrack == false && plan.neededPerMonth != null && plan.projectedLeftover != null) {
                        Label("This month's leftover looks ${currency.format((plan.neededPerMonth - plan.projectedLeftover).coerceAtLeast(0))} short", color = colors.alert)
                    }
                    if (plan.suggestion > 0) {
                        Spacer(Modifier.height(2.dp))
                        BracketButton("Put aside ${currency.format(plan.suggestion)}", onClick = { onPutAside(plan.suggestion) })
                    }
                }
            }
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
fun Welcome(onScan: () -> Unit, onAutoFind: () -> Unit, onAddManually: () -> Unit, onTrySamples: () -> Unit = {}) {
    val colors = Spend.ink
    Screen {
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
                "Add screenshots of your UPI, card or wallet payments — or let it read your bank SMS. SpendLens works it out on this phone and shows where your money goes.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted,
                modifier = Modifier.reveal(1),
            )
            Spacer(Modifier.height(32.dp))
            listOf("Add screenshots or bank SMS", "Check what was read", "See where it goes").forEachIndexed { i, step ->
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
            com.spendlens.app.ui.components.BracketButton("Add screenshots", onClick = onScan, filled = true, modifier = Modifier.fillMaxWidth().reveal(5))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().reveal(6), horizontalArrangement = Arrangement.SpaceBetween) {
                BracketButton("Auto-find", onClick = onAutoFind)
                BracketButton("Add manually", onClick = onAddManually)
            }
            Row(Modifier.fillMaxWidth().reveal(7), horizontalArrangement = Arrangement.Center) {
                BracketButton("Try with sample data", onClick = onTrySamples, color = colors.muted)
            }
        }
    }
}
