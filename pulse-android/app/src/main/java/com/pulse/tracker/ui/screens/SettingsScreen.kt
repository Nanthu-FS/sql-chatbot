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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.tracker.PulseViewModel
import com.pulse.tracker.data.AppData
import com.pulse.tracker.data.Goals
import com.pulse.tracker.ui.components.PIconButton
import com.pulse.tracker.ui.components.PIcons
import com.pulse.tracker.ui.components.PText
import com.pulse.tracker.ui.components.PTextField
import com.pulse.tracker.ui.components.PulseCard
import com.pulse.tracker.ui.components.Ring
import com.pulse.tracker.ui.components.RingStyle
import com.pulse.tracker.ui.components.SectionTitle
import com.pulse.tracker.ui.components.Seg
import com.pulse.tracker.ui.components.formatInt
import com.pulse.tracker.ui.theme.AllThemes
import com.pulse.tracker.ui.theme.CardStyle
import com.pulse.tracker.ui.theme.LocalMuted
import com.pulse.tracker.ui.theme.PulseProvider
import com.pulse.tracker.ui.theme.PulseTheme
import com.pulse.tracker.ui.theme.pulse

@Composable
fun SettingsScreen(vm: PulseViewModel, data: AppData, onBack: () -> Unit) {
    val t = pulse
    var name by remember { mutableStateOf(data.name) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
            .padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PIconButton(PIcons.Back, "Back", onBack)
            PText("Appearance & goals", size = 20.sp, display = true, italic = t.id == com.pulse.tracker.ui.theme.ThemeId.PAPER, modifier = Modifier.weight(1f).padding(start = 14.dp))
        }

        SectionTitle("Theme")
        AllThemes.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { th ->
                    ThemeCard(th, selected = th.id == t.id, modifier = Modifier.weight(1f)) { vm.setTheme(th.id.name) }
                }
            }
        }

        SectionTitle("You")
        PTextField(name, { name = it; vm.setName(it) }, "Your first name (for greetings)", Modifier.fillMaxWidth())

        SectionTitle("Daily goals")
        GoalsEditor(data.goals) { vm.setGoals(it) }

        PulseCard(Modifier.fillMaxWidth().padding(end = if (t.cardStyle == CardStyle.BRUTAL) 5.dp else 0.dp), padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    PText("Add burned calories back", size = 14.sp, weight = FontWeight.SemiBold)
                    PText("Steps raise today’s calorie budget (≈0.04 kcal per step).", size = 12.sp, color = LocalMuted.current)
                }
                Switch(
                    checked = data.addBurnedBack,
                    onCheckedChange = { vm.setAddBurnedBack(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = t.c.onAccent, checkedTrackColor = t.c.accent,
                        uncheckedThumbColor = t.c.muted, uncheckedTrackColor = t.c.track, uncheckedBorderColor = t.c.border,
                    ),
                )
            }
        }

        PText(
            "Your data stays on this phone. Barcode lookups use the free Open Food Facts database.",
            size = 12.sp, color = t.c.muted,
        )
    }
}

@Composable
private fun ThemeCard(theme: PulseTheme, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val outer = pulse
    val shape = RoundedCornerShape(if (outer.radius > 0.dp) 22.dp else 0.dp)
    Column(
        modifier.clip(shape)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) outer.c.accentText else outer.c.border, shape)
            .clickable(onClick = onClick).padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The preview renders in the theme it represents.
        PulseProvider(theme) {
            val c = theme.c
            Box(
                Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(if (theme.radius > 0.dp) 16.dp else 0.dp)).background(c.bg)
                    .border(if (theme.id == com.pulse.tracker.ui.theme.ThemeId.BRUTAL) 2.dp else 0.dp, c.border, RoundedCornerShape(if (theme.radius > 0.dp) 16.dp else 0.dp))
                    .padding(12.dp),
            ) {
                PText("Aa", size = 30.sp, display = true, color = c.text, modifier = Modifier.align(Alignment.BottomStart))
                when (theme.heroStyle) {
                    com.pulse.tracker.ui.theme.HeroStyle.SEGMENT_RING, com.pulse.tracker.ui.theme.HeroStyle.LED_RING, com.pulse.tracker.ui.theme.HeroStyle.SOFT_RING ->
                        Ring(
                            listOf(Seg(0.18f, c.protein), Seg(0.27f, c.carbs), Seg(0.16f, c.fat)),
                            Modifier.size(52.dp).align(Alignment.TopEnd), stroke = 6.dp,
                            style = when (theme.heroStyle) {
                                com.pulse.tracker.ui.theme.HeroStyle.SOFT_RING -> RingStyle.ROUND
                                com.pulse.tracker.ui.theme.HeroStyle.LED_RING -> RingStyle.LED
                                else -> RingStyle.BUTT
                            },
                            cutColor = c.bg,
                        )
                    else -> Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(c.protein, c.carbs, c.fat).forEach { col ->
                            Box(Modifier.size(12.dp).clip(RoundedCornerShape(if (theme.radius > 0.dp) 6.dp else 0.dp)).background(col))
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                PText(theme.name, size = 14.sp, weight = FontWeight.SemiBold)
                PText(theme.tagline, size = 12.sp, color = outer.c.muted)
            }
            if (selected) Icon(PIcons.Check, "Selected", Modifier.size(18.dp), tint = outer.c.accentText)
        }
    }
}

@Composable
private fun GoalsEditor(goals: Goals, onChange: (Goals) -> Unit) {
    val t = pulse
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        GoalRow("Calories", "${formatInt(goals.kcal)} kcal", { onChange(goals.copy(kcal = (goals.kcal - 50).coerceAtLeast(1000))) }) { onChange(goals.copy(kcal = (goals.kcal + 50).coerceAtMost(6000))) }
        GoalRow("Protein", "${goals.protein} g", { onChange(goals.copy(protein = (goals.protein - 5).coerceAtLeast(20))) }) { onChange(goals.copy(protein = goals.protein + 5)) }
        GoalRow("Carbs", "${goals.carbs} g", { onChange(goals.copy(carbs = (goals.carbs - 5).coerceAtLeast(20))) }) { onChange(goals.copy(carbs = goals.carbs + 5)) }
        GoalRow("Fat", "${goals.fat} g", { onChange(goals.copy(fat = (goals.fat - 5).coerceAtLeast(10))) }) { onChange(goals.copy(fat = goals.fat + 5)) }
        GoalRow("Water", "%.2f L".format(goals.waterMl / 1000f), { onChange(goals.copy(waterMl = (goals.waterMl - 250).coerceAtLeast(500))) }) { onChange(goals.copy(waterMl = goals.waterMl + 250)) }
        GoalRow("Steps", formatInt(goals.steps), { onChange(goals.copy(steps = (goals.steps - 500).coerceAtLeast(1000))) }) { onChange(goals.copy(steps = goals.steps + 500)) }
        GoalRow("Sleep", "${goals.sleepMinutes / 60}h ${goals.sleepMinutes % 60}m", { onChange(goals.copy(sleepMinutes = (goals.sleepMinutes - 15).coerceAtLeast(240))) }) { onChange(goals.copy(sleepMinutes = (goals.sleepMinutes + 15).coerceAtMost(720))) }
        GoalRow("Mindful", "${goals.mindfulMinutes} min", { onChange(goals.copy(mindfulMinutes = (goals.mindfulMinutes - 5).coerceAtLeast(5))) }) { onChange(goals.copy(mindfulMinutes = goals.mindfulMinutes + 5)) }
        Spacer(Modifier.height(4.dp))
        PText("Macro calories: ${formatInt(goals.protein * 4 + goals.carbs * 4 + goals.fat * 9)} of ${formatInt(goals.kcal)} kcal", size = 12.sp, color = t.c.muted)
    }
}

@Composable
private fun GoalRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val t = pulse
    Row(
        Modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PText(label, size = 15.sp, weight = FontWeight.Medium, modifier = Modifier.weight(1f))
        PIconButton(PIcons.Minus, "Decrease $label", onMinus, size = 38.dp)
        PText(value, size = 16.sp, display = true, weight = FontWeight.SemiBold, modifier = Modifier.width(100.dp), align = androidx.compose.ui.text.style.TextAlign.Center)
        PIconButton(PIcons.Plus, "Increase $label", onPlus, size = 38.dp)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(t.c.border.copy(alpha = if (t.cardStyle == CardStyle.SWISS || t.cardStyle == CardStyle.BRUTAL) 0.25f else 1f)))
}
