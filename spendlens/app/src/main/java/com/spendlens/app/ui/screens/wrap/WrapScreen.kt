package com.spendlens.app.ui.screens.wrap

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.spendlens.app.data.SettingsRepository
import com.spendlens.app.data.TransactionRepository
import com.spendlens.app.domain.CurrencyOption
import com.spendlens.app.domain.Period
import com.spendlens.app.domain.Wrap
import com.spendlens.app.domain.WrapBuilder
import com.spendlens.app.domain.percentLabel
import com.spendlens.app.ui.Format
import com.spendlens.app.ui.appViewModel
import com.spendlens.app.ui.components.AmountText
import com.spendlens.app.ui.components.BracketButton
import com.spendlens.app.ui.components.Hairline
import com.spendlens.app.ui.components.Label
import com.spendlens.app.ui.components.LocalCurrency
import com.spendlens.app.ui.components.Screen
import com.spendlens.app.ui.components.Statement
import com.spendlens.app.ui.components.glass
import com.spendlens.app.ui.components.index
import com.spendlens.app.ui.components.rememberHaptics
import com.spendlens.app.ui.components.reveal
import com.spendlens.app.ui.theme.Spend
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

class WrapViewModel(repository: TransactionRepository, settings: SettingsRepository, period: Period) : ViewModel() {
    val state: StateFlow<Pair<Wrap, CurrencyOption>?> = combine(repository.transactions, settings.settings) { txns, prefs ->
        WrapBuilder.of(txns, period, LocalDate.now()) to prefs.currency
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun WrapScreen(period: Period, onClose: () -> Unit) {
    val vm = appViewModel(key = "wrap-${period.type}-${period.start}") { WrapViewModel(it.repository, it.settings, period) }
    val state by vm.state.collectAsStateWithLifecycle()
    val wrap = state?.first
    if (wrap == null) {
        Screen {}
        return
    }
    WrapContent(wrap, onClose)
}

private const val PAGE_MS = 5_000

/** Story-style recap: tap right/left to move, hold to pause, pages advance on their own. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun WrapContent(wrap: Wrap, onClose: () -> Unit, autoAdvance: Boolean = true, initialPage: Int = 0) {
    val colors = Spend.ink
    val pages = remember(wrap) { pagesFor(wrap) }
    val pager = rememberPagerState(initialPage = initialPage.coerceIn(0, pages.lastIndex)) { pages.size }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val progress = remember { Animatable(0f) }
    var paused by remember { mutableStateOf(false) }

    LaunchedEffect(pager.currentPage) { haptics.tick() }
    LaunchedEffect(pager.currentPage, paused) {
        if (!autoAdvance || paused) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(PAGE_MS, easing = LinearEasing))
        if (pager.currentPage < pages.lastIndex) pager.animateScrollToPage(pager.currentPage + 1)
    }

    Screen {
        HorizontalPager(
            state = pager,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(pages.size) {
                    detectTapGestures(
                        onPress = {
                            paused = true
                            tryAwaitRelease()
                            paused = false
                        },
                        onTap = { o ->
                            scope.launch {
                                val next = if (o.x < size.width * 0.3f) pager.currentPage - 1 else pager.currentPage + 1
                                if (next in pages.indices) pager.animateScrollToPage(next) else if (next > pages.lastIndex) onClose()
                            }
                        },
                    )
                },
        ) { page ->
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp).padding(top = 64.dp, bottom = 28.dp)) {
                pages[page](this@Box)
            }
        }
        // Progress segments + close
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                pages.indices.forEach { i ->
                    val fill = when {
                        i < pager.currentPage -> 1f
                        i == pager.currentPage -> if (autoAdvance) progress.value else 1f
                        else -> 0f
                    }
                    Box(Modifier.weight(1f).height(2.dp).background(colors.line)) {
                        Box(Modifier.fillMaxWidth(fill).height(2.dp).background(colors.text))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Label("${wrap.title} · wrapped", color = colors.muted, modifier = Modifier.weight(1f))
                BracketButton("Close", onClick = onClose, color = colors.muted)
            }
        }
    }
}

private typealias Page = @Composable (androidx.compose.foundation.layout.BoxScope) -> Unit

private fun pagesFor(w: Wrap): List<Page> = buildList<Page> {
    add { _ -> Intro(w) }
    if (w.topPlaces.isNotEmpty()) add { _ -> TopPlaces(w) }
    w.biggestDay?.let { add { _ -> BiggestDay(w) } }
    w.busiestHour?.let { add { _ -> Hours(w) } }
    w.topCategory?.let { add { _ -> TopCategory(w) } }
    add { _ -> Quiet(w) }
    add { _ -> Summary(w) }
}

@Composable
private fun PageColumn(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) { content() }
}

@Composable
private fun Intro(w: Wrap) {
    val colors = Spend.ink
    val currency = LocalCurrency.current
    PageColumn {
        Statement(w.title.substringBefore(' ') + " ", "wrapped.", Modifier.reveal(0), MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(28.dp))
        Label("You spent", color = colors.muted, modifier = Modifier.reveal(1))
        AmountText(w.total, currency, MaterialTheme.typography.displayLarge, Modifier.reveal(2))
        Spacer(Modifier.height(8.dp))
        Label(
            buildString {
                append("${w.count} payments")
                w.change?.let { append(" · ${percentLabel(it)} vs before") }
            },
            color = colors.text,
            modifier = Modifier.reveal(3),
        )
    }
}

@Composable
private fun TopPlaces(w: Wrap) {
    val colors = Spend.ink
    val currency = LocalCurrency.current
    PageColumn {
        Statement("Top ", "places.", Modifier.reveal(0), MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(28.dp))
        w.topPlaces.forEachIndexed { i, p ->
            Column(Modifier.reveal(1 + i)) {
                Hairline()
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.Bottom) {
                    Label(index(i + 1), color = colors.faint, modifier = Modifier.width(40.dp).padding(bottom = 6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name.uppercase(), style = MaterialTheme.typography.headlineLarge, color = if (i == 0) colors.text else colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Label("${p.count}× · ${(p.fraction * 100).roundToInt()}% of everything", color = colors.faint)
                    }
                    Text(currency.format(p.amountMinor), style = MaterialTheme.typography.titleMedium, color = colors.text)
                }
            }
        }
        Hairline()
    }
}

@Composable
private fun BiggestDay(w: Wrap) {
    val colors = Spend.ink
    val currency = LocalCurrency.current
    val (day, amount) = w.biggestDay ?: return
    PageColumn {
        Label("Biggest day", color = colors.muted, modifier = Modifier.reveal(0))
        Spacer(Modifier.height(8.dp))
        Text(day.format(DateTimeFormatter.ofPattern("EEE d MMM")).uppercase(), style = MaterialTheme.typography.displayMedium, color = colors.text, modifier = Modifier.reveal(1))
        Spacer(Modifier.height(10.dp))
        AmountText(amount, currency, MaterialTheme.typography.headlineLarge, Modifier.reveal(2))
        Spacer(Modifier.height(10.dp))
        Label("${((amount.toFloat() / w.total.coerceAtLeast(1)) * 100).roundToInt()}% of the ${w.period.type.label.lowercase()} in one day", color = colors.faint, modifier = Modifier.reveal(3))
    }
}

@Composable
private fun Hours(w: Wrap) {
    val colors = Spend.ink
    val hour = w.busiestHour ?: return
    PageColumn {
        Label("Busiest hour", color = colors.muted, modifier = Modifier.reveal(0))
        Text(Format.hour(hour).uppercase(), style = MaterialTheme.typography.displayLarge, color = colors.text, modifier = Modifier.reveal(1))
        Spacer(Modifier.height(10.dp))
        Statement(
            "${(w.busiestHourShare * 100).roundToInt()}% ",
            "of your spending happened around then." +
                (if (w.topDay != null && w.topPart != null) " Mostly ${w.topDay.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${w.topPart.label}." else ""),
            Modifier.reveal(2),
            MaterialTheme.typography.headlineMedium,
        )
    }
}

@Composable
private fun TopCategory(w: Wrap) {
    val colors = Spend.ink
    val currency = LocalCurrency.current
    val top = w.topCategory ?: return
    PageColumn {
        Label("Most went to", color = colors.muted, modifier = Modifier.reveal(0))
        Text(top.category.label.uppercase(), style = MaterialTheme.typography.displayMedium, color = colors.text, modifier = Modifier.reveal(1))
        Spacer(Modifier.height(10.dp))
        Statement("${(top.fraction * 100).roundToInt()}% ", "of the total — ${currency.format(top.amountMinor)} across ${top.count} payments.", Modifier.reveal(2), MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun Quiet(w: Wrap) {
    val colors = Spend.ink
    PageColumn {
        Label("Quiet days", color = colors.muted, modifier = Modifier.reveal(0))
        Text("${w.noSpendDays}", style = MaterialTheme.typography.displayLarge, color = colors.text, modifier = Modifier.reveal(1))
        Statement("days without spending. ", if (w.longestStreak >= 2) "Longest run: ${w.longestStreak} days in a row." else "Try for two in a row next time.", Modifier.reveal(2), MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun Summary(w: Wrap) {
    val colors = Spend.ink
    val currency = LocalCurrency.current
    val context = LocalContext.current
    PageColumn {
        Statement("${w.title}. ", "At a glance.", Modifier.reveal(0), MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth().glass().padding(16.dp).reveal(1)) {
            listOfNotNull(
                "Total" to currency.format(w.total),
                "Payments" to w.count.toString(),
                "Per day" to currency.format(w.avgPerDay),
                w.largest?.let { "Largest" to "${currency.format(it.amountMinor)} · ${it.merchant}" },
                w.topPlaces.firstOrNull()?.let { "Top place" to it.name },
                "Quiet days" to w.noSpendDays.toString(),
            ).forEachIndexed { i, (k, v) ->
                if (i > 0) Hairline()
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Label(k, color = colors.faint, modifier = Modifier.width(96.dp))
                    Text(v, style = MaterialTheme.typography.titleSmall, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        BracketButton("Share", filled = true, modifier = Modifier.fillMaxWidth().reveal(2), onClick = {
            val text = "My ${w.title} on SpendLens: ${currency.format(w.total)} over ${w.count} payments" +
                (w.topPlaces.firstOrNull()?.let { ", mostly at ${it.name}" } ?: "") + "."
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share"))
        })
    }
}
