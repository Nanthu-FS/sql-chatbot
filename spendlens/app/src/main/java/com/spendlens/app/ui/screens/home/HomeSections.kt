package com.spendlens.app.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spendlens.app.domain.BudgetStatus
import com.spendlens.app.domain.Dashboard
import com.spendlens.app.domain.Insight
import com.spendlens.app.domain.InsightIcon
import com.spendlens.app.domain.MerchantStat
import com.spendlens.app.domain.PeriodType
import com.spendlens.app.ui.components.AnimatedAmount
import com.spendlens.app.ui.components.BarChart
import com.spendlens.app.ui.components.CategoryBadge
import com.spendlens.app.ui.components.DonutChart
import com.spendlens.app.ui.components.IconTile
import com.spendlens.app.ui.components.LocalCurrency
import com.spendlens.app.ui.components.MerchantAvatar
import com.spendlens.app.ui.components.MonthHeatmap
import com.spendlens.app.ui.components.SectionCard
import com.spendlens.app.ui.components.Sparkline
import com.spendlens.app.ui.components.TransactionRow
import com.spendlens.app.ui.components.YearHeatmap
import com.spendlens.app.ui.components.bounceClick
import com.spendlens.app.ui.components.color
import com.spendlens.app.ui.theme.SpendTheme
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun HeroCard(dashboard: Dashboard) {
    val colors = SpendTheme.colors
    val currency = LocalCurrency.current
    val shape = RoundedCornerShape(32.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.heroBrush)
            .drawBehind {
                // Soft light blobs for depth.
                drawCircle(Color.White.copy(alpha = 0.10f), radius = size.width * 0.45f, center = Offset(size.width * 0.95f, 0f))
                drawCircle(Color.White.copy(alpha = 0.07f), radius = size.width * 0.3f, center = Offset(size.width * 0.05f, size.height))
            }
            .padding(24.dp),
    ) {
        val label = when (dashboard.period.type) {
            PeriodType.DAY -> if (dashboard.isCurrent) "Spent today" else "Spent on this day"
            PeriodType.MONTH -> if (dashboard.isCurrent) "Spent this month" else "Spent in ${dashboard.title}"
            PeriodType.YEAR -> if (dashboard.isCurrent) "Spent this year" else "Spent in ${dashboard.title}"
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
        Spacer(Modifier.height(4.dp))
        AnimatedAmount(
            amountMinor = dashboard.total,
            currency = currency,
            style = MaterialTheme.typography.displayMedium,
            color = Color.White,
        )
        Spacer(Modifier.height(8.dp))
        ChangeChip(dashboard)
        if (dashboard.cumulative.size > 1) {
            Spacer(Modifier.height(12.dp))
            Sparkline(
                values = dashboard.cumulative,
                totalPoints = dashboard.bars.size,
                modifier = Modifier.fillMaxWidth().height(64.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.14f))
                .padding(vertical = 12.dp),
        ) {
            HeroStat("Payments", dashboard.count.toString(), Modifier.weight(1f))
            HeroDivider()
            HeroStat(dashboard.averageLabel, currency.compact(dashboard.average), Modifier.weight(1f))
            HeroDivider()
            HeroStat("Largest", dashboard.largest?.let { currency.compact(it.amountMinor) } ?: "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun ChangeChip(dashboard: Dashboard) {
    val change = dashboard.change
    val text = when {
        change == null && dashboard.total == 0L -> "No payments yet"
        change == null -> "Nothing to compare ${dashboard.comparisonLabel.removePrefix("vs ")}"
        else -> "${(abs(change) * 100).roundToInt()}% ${if (change >= 0) "more" else "less"} ${dashboard.comparisonLabel}"
    }
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (change != null) {
            Icon(
                if (change >= 0) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
    }
}

@Composable
private fun HeroDivider() {
    Box(
        Modifier
            .padding(vertical = 4.dp)
            .width(1.dp)
            .height(32.dp)
            .background(Color.White.copy(alpha = 0.25f)),
    )
}

@Composable
fun BudgetCard(budget: BudgetStatus) {
    val colors = SpendTheme.colors
    val currency = LocalCurrency.current
    val over = budget.remaining < 0
    val progress by animateFloatAsState(budget.fraction.coerceIn(0f, 1f), tween(1000, easing = FastOutSlowInEasing), label = "budget")
    val barBrush = when {
        over -> Brush.horizontalGradient(listOf(colors.negative, colors.negative.copy(alpha = 0.7f)))
        budget.fraction > 0.85f -> Brush.horizontalGradient(listOf(colors.warning, colors.negative))
        else -> Brush.horizontalGradient(listOf(colors.positive, colors.brand[0]))
    }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(
                Icons.Rounded.Savings,
                size = 42.dp,
                brush = Brush.linearGradient(listOf(colors.positive, colors.brand[0])),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(budget.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (over) "Over by ${currency.format(-budget.remaining)}" else "${currency.format(budget.remaining)} left of ${currency.format(budget.limit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (over) colors.negative else colors.textMuted,
                )
            }
            Text(
                "${(budget.fraction * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = if (over) colors.negative else MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape)
                .background(colors.chartTrack),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(barBrush),
            )
        }
        val allowance = budget.dailyAllowance
        if (allowance != null && budget.daysLeft != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                if (over) "You've gone past this month's budget." else "You can spend about ${currency.format(allowance)}/day for the next ${budget.daysLeft} days.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
    }
}

@Composable
fun BudgetPrompt(onSetBudget: () -> Unit) {
    val colors = SpendTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.subtle)
            .bounceClick(onClick = onSetBudget)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(Icons.Rounded.Savings, size = 40.dp, brush = Brush.linearGradient(listOf(colors.positive, colors.brand[0])))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Set a monthly budget", style = MaterialTheme.typography.titleSmall)
            Text("Get a daily spending allowance and alerts.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = colors.textMuted)
    }
}

@Composable
fun InsightsRow(insights: List<Insight>) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 0.dp),
    ) {
        items(insights, key = { it.icon.name + it.title }) { insight -> InsightCard(insight) }
    }
}

@Composable
private fun InsightCard(insight: Insight) {
    val colors = SpendTheme.colors
    val accent = insight.category?.color ?: when (insight.icon) {
        InsightIcon.TREND_DOWN -> colors.positive
        InsightIcon.TREND_UP -> colors.negative
        else -> colors.brand[0]
    }
    val icon: ImageVector = when (insight.icon) {
        InsightIcon.TREND_UP -> Icons.AutoMirrored.Rounded.TrendingUp
        InsightIcon.TREND_DOWN -> Icons.AutoMirrored.Rounded.TrendingDown
        InsightIcon.CATEGORY -> Icons.Rounded.Category
        InsightIcon.CALENDAR -> Icons.Rounded.CalendarMonth
        InsightIcon.STAR -> Icons.Rounded.Star
        InsightIcon.REPEAT -> Icons.Rounded.Repeat
        InsightIcon.WEEKEND -> Icons.Rounded.Weekend
    }
    Column(
        Modifier
            .width(230.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.card)
            .drawBehind {
                drawCircle(accent.copy(alpha = 0.12f), radius = size.width * 0.35f, center = Offset(size.width, 0f))
            }
            .padding(16.dp),
    ) {
        IconTile(icon, size = 36.dp, brush = Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.6f))))
        Spacer(Modifier.height(12.dp))
        Text(insight.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(insight.body, style = MaterialTheme.typography.bodySmall, color = colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SpendChartCard(dashboard: Dashboard, onDrillDown: (LocalDate) -> Unit) {
    val currency = LocalCurrency.current
    val colors = SpendTheme.colors
    var selected by remember(dashboard.period) { mutableStateOf<Int?>(null) }
    SectionCard(
        title = dashboard.barsTitle,
        subtitle = if (dashboard.total > 0) "Tap or drag across the bars" else "No payments in ${dashboard.title.lowercase()}",
    ) {
        BarChart(
            bars = dashboard.bars,
            selectedIndex = selected,
            onSelect = { selected = it },
            formatValue = { currency.format(it) },
            formatAxis = { currency.compact(it) },
            average = if (dashboard.period.type == PeriodType.DAY) null else dashboard.average,
        )
        val bar = selected?.let { dashboard.bars.getOrNull(it) }
        AnimatedVisibility(visible = bar?.date != null && dashboard.period.type != PeriodType.DAY) {
            val date = bar?.date
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { if (date != null) onDrillDown(date) }) {
                    Text(
                        "Open ${bar?.tooltipLabel.orEmpty()}",
                        color = colors.brand[0],
                    )
                    Icon(Icons.Rounded.ChevronRight, null, tint = colors.brand[0])
                }
            }
        }
    }
}

@Composable
fun HeatmapCard(dashboard: Dashboard, onDayClick: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    if (dashboard.period.type == PeriodType.MONTH) {
        SectionCard(title = "Spending calendar", subtitle = "Tap a day to see its payments") {
            MonthHeatmap(dashboard.heatmap, today, onDayClick)
        }
    } else {
        SectionCard(title = "Year at a glance", subtitle = "Every day of ${dashboard.title}") {
            YearHeatmap(dashboard.heatmap, today, onDayClick)
        }
    }
}

@Composable
fun CategoriesCard(dashboard: Dashboard) {
    val colors = SpendTheme.colors
    val currency = LocalCurrency.current
    val slices = dashboard.categories
    var selected by remember(dashboard.period, slices.size) { mutableStateOf<Int?>(null) }
    SectionCard(title = "Where it went", subtitle = "${slices.size} ${if (slices.size == 1) "category" else "categories"}") {
        DonutChart(
            slices = slices,
            selectedIndex = selected,
            onSelect = { selected = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        ) {
            val focus = selected?.let { slices.getOrNull(it) }
            AnimatedContent(targetState = focus, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "donutCenter") { slice ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        slice?.category?.label ?: "Total",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textMuted,
                    )
                    Text(
                        currency.format(slice?.amountMinor ?: dashboard.total),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (slice != null) {
                        Text("${(slice.fraction * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = slice.category.color)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        slices.forEachIndexed { index, slice ->
            val appear = remember(slice.category, dashboard.period) { Animatable(0f) }
            LaunchedEffect(slice.category, dashboard.period) {
                appear.animateTo(slice.fraction, tween(900, delayMillis = 80 * index, easing = FastOutSlowInEasing))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected == index) slice.category.color.copy(alpha = 0.08f) else Color.Transparent)
                    .bounceClick { selected = if (selected == index) null else index }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryBadge(slice.category, size = 38.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(slice.category.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(currency.format(slice.amountMinor), style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(colors.chartTrack),
                        ) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawRoundRect(
                                    color = slice.category.color,
                                    size = size.copy(width = size.width * appear.value),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "${(slice.fraction * 100).roundToInt()}% · ${slice.count}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MerchantsCard(merchants: List<MerchantStat>) {
    val colors = SpendTheme.colors
    val currency = LocalCurrency.current
    SectionCard(title = "Top places", subtitle = "Where you pay the most") {
        merchants.forEachIndexed { index, merchant ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.TopStart) {
                    MerchantAvatar(merchant.name, merchant.category, size = 42.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(merchant.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "#${index + 1} · ${merchant.count} ${if (merchant.count == 1) "payment" else "payments"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(currency.format(merchant.amountMinor), style = MaterialTheme.typography.titleSmall)
                    Text("${(merchant.fraction * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = merchant.category.color)
                }
            }
        }
    }
}

@Composable
fun RecentCard(dashboard: Dashboard, onOpenTransaction: (Long) -> Unit, onSeeAll: () -> Unit) {
    val currency = LocalCurrency.current
    val colors = SpendTheme.colors
    SectionCard(
        title = "Payments",
        subtitle = dashboard.title,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 12.dp),
        action = {
            TextButton(onClick = onSeeAll) { Text("See all") }
        },
    ) {
        if (dashboard.transactions.isEmpty()) {
            Text(
                "No payments ${if (dashboard.period.type == PeriodType.DAY) "on this day" else "in this period"}. Add screenshots with the scan button.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                modifier = Modifier.padding(8.dp),
            )
        } else {
            dashboard.transactions.take(6).forEach { txn ->
                TransactionRow(
                    txn = txn,
                    currency = currency,
                    onClick = { onOpenTransaction(txn.id) },
                    showDate = dashboard.period.type != PeriodType.DAY,
                )
            }
            if (dashboard.transactions.size > 6) {
                Text(
                    "+${dashboard.transactions.size - 6} more",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}
