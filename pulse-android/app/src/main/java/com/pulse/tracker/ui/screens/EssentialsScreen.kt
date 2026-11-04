package com.pulse.tracker.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulse.tracker.PulseViewModel
import com.pulse.tracker.data.AppData
import com.pulse.tracker.summarize
import com.pulse.tracker.ui.components.IconBadge
import com.pulse.tracker.ui.components.PBar
import com.pulse.tracker.ui.components.PButton
import com.pulse.tracker.ui.components.PChip
import com.pulse.tracker.ui.components.PDialog
import com.pulse.tracker.ui.components.PIconButton
import com.pulse.tracker.ui.components.PIcons
import com.pulse.tracker.ui.components.PLabel
import com.pulse.tracker.ui.components.PText
import com.pulse.tracker.ui.components.PTextField
import com.pulse.tracker.ui.components.PulseCard
import com.pulse.tracker.ui.components.SectionTitle
import com.pulse.tracker.ui.components.formatInt
import com.pulse.tracker.ui.theme.CardTint
import com.pulse.tracker.ui.theme.LocalMuted
import com.pulse.tracker.ui.theme.ThemeId
import com.pulse.tracker.ui.theme.pulse
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private enum class EssentialsDialog { NONE, SLEEP, STEPS, WEIGHT, SUPPLEMENTS }

@Composable
fun EssentialsScreen(vm: PulseViewModel, data: AppData, date: LocalDate) {
    val t = pulse
    val haptics = LocalHapticFeedback.current
    val s = summarize(data, date)
    val g = data.goals
    val checks = data.supplementChecks[date] ?: emptySet()
    val done = data.supplements.count { it.id in checks }
    var dialog by remember { mutableStateOf(EssentialsDialog.NONE) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
            .padding(horizontal = 20.dp).padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (t.id == ThemeId.BRUTAL) 16.dp else 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PLabel("Sleep, fasting & supplements")
                PText(if (t.id == ThemeId.NEON) "HABITS.SYS" else "Essentials", size = if (t.id == ThemeId.SWISS) 30.sp else 24.sp, display = true, italic = t.id == ThemeId.PAPER)
            }
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                val frac = if (data.supplements.isEmpty()) 0f else done.toFloat() / data.supplements.size
                Canvas(Modifier.fillMaxSize()) {
                    val sw = 5.dp.toPx()
                    drawArc(t.c.track, 0f, 360f, false, style = Stroke(sw), topLeft = androidx.compose.ui.geometry.Offset(sw / 2, sw / 2), size = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw))
                    drawArc(t.c.accentText, -90f, 360f * frac, false, style = Stroke(sw, cap = if (t.radius > 0.dp) StrokeCap.Round else StrokeCap.Butt), topLeft = androidx.compose.ui.geometry.Offset(sw / 2, sw / 2), size = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw))
                }
                PText("$done/${data.supplements.size}", size = 13.sp, display = true, weight = FontWeight.Bold)
            }
        }

        FastingCard(vm, data)

        Row(Modifier.fillMaxWidth().padding(end = if (t.id == ThemeId.BRUTAL) 5.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val sleep = s.metrics.sleepMinutes
            MetricCard(
                PIcons.Moon, t.c.calm, "Sleep",
                if (sleep > 0) "${sleep / 60}h ${sleep % 60}m" else "—",
                sleep.toFloat() / g.sleepMinutes, CardTint.SLEEP, Modifier.weight(1f),
            ) { dialog = EssentialsDialog.SLEEP }
            MetricCard(
                PIcons.Activity, t.c.text, "Steps", formatInt(s.metrics.steps),
                s.metrics.steps.toFloat() / g.steps, CardTint.STEPS, Modifier.weight(1f),
            ) { dialog = EssentialsDialog.STEPS }
        }
        Row(Modifier.fillMaxWidth().padding(end = if (t.id == ThemeId.BRUTAL) 5.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                PIcons.Leaf, t.c.protein, "Mindful · tap +5",
                "${s.metrics.mindfulMinutes} / ${g.mindfulMinutes} min",
                s.metrics.mindfulMinutes.toFloat() / g.mindfulMinutes, CardTint.PROTEIN, Modifier.weight(1f),
            ) {
                vm.addMindful(5, date)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            val weight = data.weights.firstOrNull { it.date == date } ?: data.weights.maxByOrNull { it.date }
            MetricCard(
                PIcons.Scale, t.c.fat, "Weight",
                weight?.let { "%.1f kg".format(it.kg) } ?: "—",
                null, CardTint.FAT, Modifier.weight(1f),
                footnote = weight?.let { if (it.date == date) "Logged today" else "Last: ${it.date.format(DateTimeFormatter.ofPattern("d MMM"))}" } ?: "Tap to log",
            ) { dialog = EssentialsDialog.WEIGHT }
        }

        PulseCard(Modifier.fillMaxWidth().padding(end = if (t.id == ThemeId.BRUTAL) 5.dp else 0.dp), padding = 14.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(PIcons.Pill, null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                PText(if (t.upperLabels) "SUPPLEMENTS" else "Supplements", size = 15.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                PText("Edit", size = 13.sp, color = LocalMuted.current, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { dialog = EssentialsDialog.SUPPLEMENTS }.padding(8.dp))
            }
            if (data.supplements.isEmpty()) {
                PText("Add vitamins or anything you take daily.", size = 13.sp, color = LocalMuted.current, modifier = Modifier.padding(vertical = 8.dp))
            }
            data.supplements.forEach { sup ->
                val on = sup.id in checks
                Row(
                    Modifier.fillMaxWidth().height(50.dp).clickable {
                        vm.toggleSupplement(sup.id, date)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CheckDot(on)
                    PText(sup.name, size = 14.sp, weight = FontWeight.SemiBold, color = if (on) LocalMuted.current else androidx.compose.material3.LocalContentColor.current, modifier = Modifier.weight(1f))
                    Box(Modifier.clip(RoundedCornerShape(if (t.radius > 0.dp) 12.dp else 0.dp)).background(t.c.surfaceAlt).padding(horizontal = 9.dp, vertical = 4.dp)) {
                        PText(sup.timing, size = 11.sp, color = t.c.muted)
                    }
                }
            }
        }
        Spacer(Modifier.height(130.dp))
    }

    when (dialog) {
        EssentialsDialog.SLEEP -> SleepDialog(s.metrics.sleepMinutes, { dialog = EssentialsDialog.NONE }) { vm.setSleep(it, date); dialog = EssentialsDialog.NONE }
        EssentialsDialog.STEPS -> StepsDialog(s.metrics.steps, { dialog = EssentialsDialog.NONE }) { vm.setSteps(it, date); dialog = EssentialsDialog.NONE }
        EssentialsDialog.WEIGHT -> WeightDialog(data.weights.maxByOrNull { it.date }?.kg, { dialog = EssentialsDialog.NONE }) { vm.logWeight(it, date); dialog = EssentialsDialog.NONE }
        EssentialsDialog.SUPPLEMENTS -> SupplementsDialog(vm, data) { dialog = EssentialsDialog.NONE }
        EssentialsDialog.NONE -> Unit
    }
}

@Composable
private fun CheckDot(on: Boolean) {
    val t = pulse
    val shape = RoundedCornerShape(if (t.radius > 0.dp) 12.dp else 0.dp)
    Box(
        Modifier.size(24.dp).clip(shape)
            .background(if (on) t.c.accent else Color.Transparent)
            .border(2.dp, if (on) (if (t.id == ThemeId.BRUTAL) t.c.border else t.c.accent) else t.c.muted.copy(alpha = 0.6f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Icon(PIcons.Check, null, Modifier.size(14.dp), tint = t.c.onAccent)
    }
}

@Composable
private fun MetricCard(
    icon: ImageVector,
    color: Color,
    label: String,
    value: String,
    fraction: Float?,
    tint: CardTint,
    modifier: Modifier,
    footnote: String? = null,
    onClick: () -> Unit,
) {
    val t = pulse
    PulseCard(modifier.height(112.dp), tint = tint, onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(16.dp), tint = if (t.tints.containsKey(tint)) androidx.compose.material3.LocalContentColor.current else color)
            PText(label, size = 13.sp, color = LocalMuted.current, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        PText(value, size = 22.sp, display = true, weight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        if (fraction != null) {
            PBar(fraction, if (t.tints.containsKey(tint)) androidx.compose.material3.LocalContentColor.current else color, Modifier.fillMaxWidth(), height = 8.dp,
                track = if (t.tints.containsKey(tint)) Color.White.copy(alpha = 0.5f) else t.c.track)
        } else if (footnote != null) {
            PText(footnote, size = 12.sp, color = LocalMuted.current, maxLines = 1)
        }
    }
}

@Composable
private fun FastingCard(vm: PulseViewModel, data: AppData) {
    val t = pulse
    val started = data.fasting.startedAt
    val target = data.goals.fastingHours
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(started) {
        while (started != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    PulseCard(Modifier.fillMaxWidth().padding(end = if (t.id == ThemeId.BRUTAL) 5.dp else 0.dp), padding = 16.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(PIcons.Timer, t.c.calm, 28.dp)
            Spacer(Modifier.width(8.dp))
            PLabel("Fasting · $target:${24 - target}", Modifier.weight(1f))
            if (started != null) {
                PButton("End fast", { vm.endFast() }, height = 34.dp, container = t.c.text, content = t.c.bg)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (started != null) {
            val elapsed = (now - started).coerceAtLeast(0)
            val h = elapsed / 3_600_000
            val m = (elapsed / 60_000) % 60
            val sec = (elapsed / 1000) % 60
            val leftMs = target * 3_600_000L - elapsed
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                PText("%d:%02d".format(h, m), size = 40.sp, display = true, tracking = -0.03f)
                PText(":%02d".format(sec), size = 18.sp, display = true, color = LocalMuted.current, modifier = Modifier.padding(bottom = 5.dp))
                Spacer(Modifier.weight(1f))
                PText(
                    if (leftMs > 0) "${leftMs / 3_600_000}h ${(leftMs / 60_000) % 60}m to go" else "Goal reached",
                    size = 13.sp, weight = FontWeight.Bold, color = t.c.accentText, modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            val hoursDone = elapsed / 3_600_000f
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(target) { i ->
                    val fill = (hoursDone - i).coerceIn(0f, 1f)
                    Box(
                        Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(if (t.radius > 0.dp) 3.dp else 0.dp))
                            .background(if (fill >= 1f) t.c.calm else if (fill > 0f) t.c.calm.copy(alpha = 0.5f) else t.c.track),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            val start = Instant.ofEpochMilli(started).atZone(ZoneId.systemDefault())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PText("Started ${start.format(timeFmt)}", size = 12.sp, color = LocalMuted.current)
                PText("Eat from ${start.plusHours(target.toLong()).format(timeFmt)}", size = 12.sp, color = LocalMuted.current)
            }
        } else {
            PText("Not fasting", size = 26.sp, display = true)
            data.fasting.lastCompletedHours?.let {
                PText("Last fast: %.1f h".format(it), size = 13.sp, color = LocalMuted.current)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(12, 14, 16, 18, 20).forEach { hrs -> PChip("${hrs}h", hrs == target, { vm.setFastingHours(hrs) }, Modifier.weight(1f), height = 36.dp) }
            }
            Spacer(Modifier.height(12.dp))
            PButton("Start fast", { vm.startFast() }, Modifier.fillMaxWidth(), icon = PIcons.Timer, height = 48.dp)
        }
    }
}

@Composable
private fun SleepDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    val t = pulse
    var minutes by remember { mutableFloatStateOf(if (current > 0) current.toFloat() else 450f) }
    PDialog("Sleep last night", onDismiss, onConfirm = { onSave(minutes.roundToInt()) }) {
        PText("${minutes.roundToInt() / 60}h ${minutes.roundToInt() % 60}m", size = 40.sp, display = true)
        Slider(
            value = minutes, onValueChange = { minutes = (it / 15).roundToInt() * 15f }, valueRange = 0f..720f,
            colors = SliderDefaults.colors(thumbColor = t.c.accentText, activeTrackColor = t.c.accentText, inactiveTrackColor = t.c.track),
        )
    }
}

@Composable
private fun StepsDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(if (current > 0) current.toString() else "") }
    val value = text.filter { it.isDigit() }.toIntOrNull()
    PDialog("Steps today", onDismiss, onConfirm = { value?.let(onSave) }, confirmEnabled = value != null) {
        PText("Enter the count from your phone or watch.", size = 13.sp, color = pulse.c.muted)
        PTextField(text, { text = it }, "e.g. 8500", Modifier.fillMaxWidth(), number = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1000, 2500, 5000).forEach { add ->
                PChip("+${formatInt(add)}", false, { text = ((value ?: 0) + add).toString() })
            }
        }
    }
}

@Composable
private fun SupplementsDialog(vm: PulseViewModel, data: AppData, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var timing by remember { mutableStateOf("") }
    PDialog("Supplements", onDismiss, confirmText = "Done", onConfirm = {
        if (name.isNotBlank()) vm.addSupplement(name, timing)
        onDismiss()
    }) {
        data.supplements.forEach { sup ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    PText(sup.name, size = 14.sp, weight = FontWeight.SemiBold)
                    PText(sup.timing, size = 12.sp, color = pulse.c.muted)
                }
                PIconButton(PIcons.Trash, "Remove ${sup.name}", { vm.removeSupplement(sup.id) }, size = 36.dp)
            }
        }
        PTextField(name, { name = it }, "Add: name (e.g. Iron)", Modifier.fillMaxWidth())
        PTextField(timing, { timing = it }, "When (e.g. Morning)", Modifier.fillMaxWidth())
        if (name.isNotBlank()) {
            PButton("Add ${name.trim()}", {
                vm.addSupplement(name, timing); name = ""; timing = ""
            }, Modifier.fillMaxWidth(), height = 44.dp, icon = PIcons.Plus)
        }
    }
}
