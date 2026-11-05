package com.pulse.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.tracker.data.AppData
import com.pulse.tracker.data.Meal
import com.pulse.tracker.kcalLeft
import com.pulse.tracker.streak
import com.pulse.tracker.summarize
import com.pulse.tracker.ui.components.IconBadge
import com.pulse.tracker.ui.components.PBar
import com.pulse.tracker.ui.components.PIconButton
import com.pulse.tracker.ui.components.PIcons
import com.pulse.tracker.ui.components.PLabel
import com.pulse.tracker.ui.components.PText
import com.pulse.tracker.ui.components.PulseCard
import com.pulse.tracker.ui.components.SectionTitle
import com.pulse.tracker.ui.components.Seg
import com.pulse.tracker.ui.components.formatInt
import com.pulse.tracker.ui.components.sticker
import com.pulse.tracker.ui.theme.CardTint
import com.pulse.tracker.ui.theme.LocalMuted
import com.pulse.tracker.ui.theme.ThemeId
import com.pulse.tracker.ui.theme.pulse
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TodayScreen(
    data: AppData,
    date: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onLog: (Meal) -> Unit,
    onSettings: () -> Unit,
    onWater: () -> Unit,
    onHabits: () -> Unit,
) {
    val t = pulse
    val s = summarize(data, date)
    val g = data.goals
    val budget = g.kcal + if (data.addBurnedBack) s.burned else 0
    val macroKcal = s.protein * 4 + s.carbs * 4 + s.fat * 9
    val other = (s.eaten - macroKcal).coerceAtLeast(0.0)
    val segs = buildList {
        add(Seg((s.protein * 4 / budget).toFloat(), t.c.protein))
        add(Seg((s.carbs * 4 / budget).toFloat(), t.c.carbs))
        add(Seg((s.fat * 9 / budget).toFloat(), t.c.fat))
        if (other > 1) add(Seg((other / budget).toFloat(), t.c.muted))
    }
    val hero = HeroData(
        left = kcalLeft(data, s), eaten = s.eaten.roundToInt(), burned = s.burned, goal = g.kcal,
        segments = segs, usedFraction = (s.eaten / budget).toFloat().coerceIn(0f, 1f),
        proteinFraction = (s.protein / g.protein).toFloat(),
        carbsFraction = (s.carbs / g.carbs).toFloat(),
        fatFraction = (s.fat / g.fat).toFloat(),
    )
    val macros = listOf(
        MacroItem("Protein", "P", s.protein, g.protein, t.c.protein, CardTint.PROTEIN),
        MacroItem("Carbs", "C", s.carbs, g.carbs, t.c.carbs, CardTint.CARBS),
        MacroItem("Fat", "F", s.fat, g.fat, t.c.fat, CardTint.FAT),
    )
    val loggedDays = data.foods.map { it.date }.toSet()
    val foodStreak = streak(LocalDate.now()) { it in loggedDays }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
            .padding(horizontal = 20.dp).padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (t.id == ThemeId.BRUTAL) 20.dp else 16.dp),
    ) {
        TodayHeader(data, date, s.eaten, budget, foodStreak, onSettings)
        WeekStrip(date, loggedDays, onSelectDate)
        CalorieHero(hero)
        MacroSection(macros)
        Row(Modifier.fillMaxWidth().padding(end = if (t.id == ThemeId.BRUTAL) 5.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(if (t.id == ThemeId.BRUTAL) 12.dp else 10.dp)) {
            WaterMini(s.waterMl, g.waterMl, onWater, Modifier.weight(1f))
            StepsMini(s.metrics.steps, g.steps, data.stepsFromHealthConnect, onHabits, Modifier.weight(1f))
        }
        MealsSection(data, s, onLog)
        Spacer(Modifier.height(130.dp))
    }
}

private fun greeting(name: String): String {
    val h = LocalTime.now().hour
    val base = when { h < 12 -> "Good morning"; h < 17 -> "Good afternoon"; else -> "Good evening" }
    return if (name.isBlank()) base else "$base, ${name.trim()}"
}

@Composable
private fun TodayHeader(data: AppData, date: LocalDate, eaten: Double, budget: Int, streak: Int, onSettings: () -> Unit) {
    val t = pulse
    val isToday = date == LocalDate.now()
    val dateLong = date.format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault()))
    when (t.id) {
        ThemeId.PAPER -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PText("PULSE", size = 12.sp, weight = FontWeight.Bold, tracking = 0.32f, modifier = Modifier.weight(1f))
                PText("No. ${date.dayOfYear} · ${date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))}", size = 12.sp, color = t.c.muted)
                Spacer(Modifier.width(6.dp))
                PIconButton(PIcons.Palette, "Themes and settings", onSettings, size = 36.dp)
            }
            Box(
                Modifier.fillMaxWidth().height(5.dp).drawBehind {
                    drawLine(t.c.text, Offset(0f, 1.dp.toPx()), Offset(size.width, 1.dp.toPx()), 2.dp.toPx())
                    drawLine(t.c.text, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx())
                },
            )
            Spacer(Modifier.height(10.dp))
            PText(greeting(data.name) + ",", size = 36.sp, display = true, italic = true, lineHeight = 38.sp)
            val pct = if (budget > 0) (eaten / budget * 100).roundToInt() else 0
            PText(if (isToday) "You are $pct% through today’s fuel." else "Looking back at $dateLong.", size = 14.sp, color = t.c.muted)
        }

        ThemeId.SWISS -> Row(
            Modifier.fillMaxWidth().drawBehind {
                drawLine(t.c.text, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx())
            }.padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PText("Pulse", size = 18.sp, weight = FontWeight.Black, tracking = -0.02f)
            PText(".", size = 18.sp, weight = FontWeight.Black, color = t.c.accent, modifier = Modifier.weight(1f))
            PText(date.format(DateTimeFormatter.ofPattern("EEE dd.MM.yyyy", Locale.getDefault())), size = 13.sp, weight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            PIconButton(PIcons.Palette, "Themes and settings", onSettings, size = 36.dp)
        }

        ThemeId.NEON -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PText("> pulse --${if (isToday) "today" else "history"}", size = 12.sp, color = t.c.carbs)
                    Box(Modifier.padding(start = 3.dp).width(7.dp).height(13.dp).background(t.c.carbs))
                }
                PText(date.format(DateTimeFormatter.ofPattern("EEE_dd.MM", Locale.US)).uppercase(), size = 24.sp, display = true)
            }
            Row(
                Modifier.height(36.dp).border(1.dp, t.c.accent).clickable(onClick = onSettings).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(6.dp).background(t.c.accent))
                PText("THEME", size = 12.sp, weight = FontWeight.Bold, color = t.c.accent, tracking = 0.08f)
            }
        }

        ThemeId.BRUTAL -> Box(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.sticker(-2f).background(t.c.text).padding(horizontal = 10.dp, vertical = 5.dp)) {
                        PText(date.format(DateTimeFormatter.ofPattern("EEE · d MMM", Locale.getDefault())).uppercase(), size = 12.sp, weight = FontWeight.ExtraBold, color = t.c.bg, tracking = 0.08f)
                    }
                    PText(if (isToday) "Let’s eat." else "Rewind.", size = 44.sp, display = true, lineHeight = 44.sp)
                }
                PIconButton(PIcons.Palette, "Themes and settings", onSettings, size = 50.dp, filled = false)
            }
            if (streak > 0) {
                Row(
                    Modifier.align(Alignment.TopCenter).padding(start = 60.dp, top = 4.dp).sticker(7f)
                        .clip(RoundedCornerShape(15.dp)).background(t.c.fat).border(2.5.dp, t.c.border, RoundedCornerShape(15.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(PIcons.Flame, null, Modifier.size(13.dp), tint = t.c.text)
                    PText("$streak DAY${if (streak == 1) "" else "S"}", size = 12.sp, weight = FontWeight.ExtraBold)
                }
            }
        }

        ThemeId.MATCHA -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).background(t.c.surfaceAlt), contentAlignment = Alignment.Center) {
                Icon(PIcons.Leaf, null, Modifier.size(22.dp), tint = t.c.accentText)
            }
            Column(Modifier.weight(1f)) {
                PText(greeting(data.name), size = 24.sp, display = true, lineHeight = 28.sp)
                PText(dateLong, size = 14.sp, weight = FontWeight.SemiBold, color = t.c.muted)
            }
            PIconButton(PIcons.Palette, "Themes and settings", onSettings, size = 46.dp)
        }

        else -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PText(dateLong, size = 13.sp, color = t.c.muted)
                PText(greeting(data.name), size = 24.sp, display = true, weight = FontWeight.SemiBold, lineHeight = 28.sp)
            }
            if (streak > 0) {
                Row(
                    Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(t.c.surface)
                        .border(1.dp, t.c.border, RoundedCornerShape(17.dp)).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(PIcons.Flame, null, Modifier.size(14.dp), tint = t.c.warm)
                    PText("$streak", size = 13.sp, weight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(10.dp))
            }
            PIconButton(PIcons.Palette, "Themes and settings", onSettings)
        }
    }
}

@Composable
private fun WeekStrip(selected: LocalDate, logged: Set<LocalDate>, onSelect: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val monday = selected.with(DayOfWeek.MONDAY)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..6).forEach { i ->
            val d = monday.plusDays(i.toLong())
            DayPill(
                weekday = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                day = d.dayOfMonth,
                selected = d == selected,
                logged = d in logged,
                enabled = !d.isAfter(today),
                onClick = { onSelect(d) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WaterMini(ml: Int, goal: Int, onClick: () -> Unit, modifier: Modifier) {
    val t = pulse
    val pills = 12
    val filled = ((ml.toFloat() / goal) * pills).roundToInt().coerceIn(0, pills)
    PulseCard(modifier, tint = CardTint.WATER, onClick = onClick, padding = 15.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(PIcons.Drop, t.c.water, 28.dp)
            Spacer(Modifier.width(8.dp))
            PLabel("Water", Modifier.weight(1f))
            Icon(PIcons.Plus, "Add water", Modifier.size(18.dp), tint = LocalMuted.current)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            PText("%.2f".format(ml / 1000f), size = 26.sp, display = true, weight = FontWeight.SemiBold)
            PText(" / %.1f L".format(goal / 1000f), size = 13.sp, color = LocalMuted.current, modifier = Modifier.padding(bottom = 4.dp))
        }
        Spacer(Modifier.height(10.dp))
        val onFill = if (t.tints.containsKey(CardTint.WATER)) Color.White else t.c.water
        val off = if (t.tints.containsKey(CardTint.WATER)) Color.White.copy(alpha = 0.25f) else t.c.track
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(pills) { i ->
                Box(
                    Modifier.weight(1f).height(14.dp)
                        .clip(RoundedCornerShape(if (t.radius > 0.dp) 4.dp else 0.dp))
                        .background(if (i < filled) onFill else off),
                )
            }
        }
    }
}

@Composable
private fun StepsMini(steps: Int, goal: Int, synced: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val t = pulse
    PulseCard(modifier, tint = CardTint.STEPS, onClick = onClick, padding = 15.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(PIcons.Activity, t.c.text, 28.dp)
            Spacer(Modifier.width(8.dp))
            PLabel("Steps", Modifier.weight(1f))
            if (synced) {
                Icon(PIcons.Refresh, "Synced from Health Connect", Modifier.size(12.dp), tint = LocalMuted.current)
                Spacer(Modifier.width(4.dp))
            }
            PText("${(steps * 100f / goal).roundToInt()}%", size = 12.sp, color = LocalMuted.current)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            PText(formatInt(steps), size = 26.sp, display = true, weight = FontWeight.SemiBold)
            PText(" / ${goal / 1000}k", size = 13.sp, color = LocalMuted.current, modifier = Modifier.padding(bottom = 4.dp))
        }
        Spacer(Modifier.height(10.dp))
        PBar(steps.toFloat() / goal, if (t.tints.containsKey(CardTint.STEPS)) t.c.text else t.c.text, Modifier.fillMaxWidth(), height = 14.dp)
    }
}

@Composable
private fun MealsSection(data: AppData, s: com.pulse.tracker.data.DaySummary, onLog: (Meal) -> Unit) {
    val t = pulse
    Column(verticalArrangement = Arrangement.spacedBy(if (t.id == ThemeId.BRUTAL) 14.dp else 10.dp)) {
        SectionTitle(if (t.id == ThemeId.SWISS) "03 — Meals" else "Meals")
        Meal.entries.forEach { meal ->
            val items = s.entries.filter { it.meal == meal }
            val kcal = items.sumOf { it.kcal }.roundToInt()
            PulseCard(Modifier.fillMaxWidth().padding(end = if (t.id == ThemeId.BRUTAL) 5.dp else 0.dp), onClick = { onLog(meal) }, padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        PText(meal.label, size = 15.sp, weight = FontWeight.SemiBold, display = t.id == ThemeId.PAPER)
                        PText(
                            if (items.isEmpty()) "Nothing logged yet" else items.joinToString(" · ") { it.name },
                            size = 12.sp, color = LocalMuted.current, maxLines = 1,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    if (kcal > 0) {
                        PText(formatInt(kcal), size = 17.sp, display = true, weight = FontWeight.SemiBold)
                        PText(" kcal", size = 12.sp, color = LocalMuted.current)
                        Spacer(Modifier.width(10.dp))
                    }
                    PIconButton(PIcons.Plus, "Add to ${meal.label}", { onLog(meal) }, size = 36.dp)
                }
            }
        }
    }
}
