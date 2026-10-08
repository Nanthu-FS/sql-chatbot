package app.monoworkspace.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.monoworkspace.ui.theme.MonoColors
import app.monoworkspace.ui.theme.MonoIcons
import app.monoworkspace.ui.theme.MonoType
import app.monoworkspace.ui.theme.Motion
import app.monoworkspace.ui.theme.Space
import app.monoworkspace.ui.theme.motionMs
import app.monoworkspace.ui.theme.tnum
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Month grid; selected days invert. Range mode highlights days between start and end. */
@Composable
fun MonthCalendar(
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    rangeEnd: LocalDate? = null,
    marks: Set<LocalDate> = emptySet(),
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), Modifier.weight(1f), style = MonoType.h3)
            MonoIconButton(MonoIcons.ChevronLeft, "Previous month", { onMonthChange(month.minusMonths(1)) })
            MonoIconButton(MonoIcons.ChevronRight, "Next month", { onMonthChange(month.plusMonths(1)) })
        }
        Row(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
            DayOfWeek.entries.forEach { d ->
                Text(
                    d.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
                    Modifier.weight(1f),
                    style = MonoType.label.copy(color = MonoColors.Secondary),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        SectionRule()
        val slow = motionMs(Motion.MEDIUM)
        val fast = motionMs(Motion.FAST)
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(slow)) { it / 4 * dir } + fadeIn(tween(slow)))
                    .togetherWith(slideOutHorizontally(tween(slow)) { -it / 4 * dir } + fadeOut(tween(fast)))
            },
            label = "month",
        ) { m ->
            val first = m.atDay(1)
            val lead = first.dayOfWeek.value - 1
            val days = m.lengthOfMonth()
            val cells = ((lead + days + 6) / 7) * 7
            val today = LocalDate.now()
            Column {
                for (week in 0 until cells / 7) {
                    Row(Modifier.fillMaxWidth()) {
                        for (dow in 0 until 7) {
                            val idx = week * 7 + dow
                            val dayNum = idx - lead + 1
                            Box(Modifier.weight(1f).aspectRatio(1.15f), contentAlignment = Alignment.Center) {
                                if (dayNum in 1..days) {
                                    val date = m.atDay(dayNum)
                                    val isSel = date == selected || date == rangeEnd
                                    val inRange = selected != null && rangeEnd != null && date.isAfter(minOf(selected, rangeEnd)) && date.isBefore(maxOf(selected, rangeEnd))
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1.15f)
                                            .background(
                                                when {
                                                    isSel -> MonoColors.Ink
                                                    inRange -> MonoColors.Tint
                                                    else -> MonoColors.Background
                                                },
                                            )
                                            .inkClickable(onClick = { onSelect(date) }, showBar = false),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                "$dayNum",
                                                style = MonoType.bodySmall.tnum().copy(
                                                    color = if (isSel) MonoColors.White else if (dow >= 5) MonoColors.Secondary else MonoColors.Ink,
                                                    fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                                                ),
                                            )
                                            if (date == today && !isSel) Box(Modifier.width(12.dp).height(1.dp).background(MonoColors.Ink))
                                            else if (date in marks) Box(Modifier.width(4.dp).height(4.dp).background(if (isSel) MonoColors.White else MonoColors.Ink))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class PickedDate(val start: LocalDate, val end: LocalDate?, val includeTime: Boolean, val time: LocalTime?)

/** Calendar picker plus optional end date and time. */
@Composable
fun DatePickerSheet(
    initial: PickedDate?,
    onDismiss: () -> Unit,
    onSave: (PickedDate?) -> Unit,
    allowRange: Boolean = true,
    allowTime: Boolean = true,
    title: String = "Date",
) {
    var start by remember { mutableStateOf(initial?.start ?: LocalDate.now()) }
    var end by remember { mutableStateOf(initial?.end) }
    var hasEnd by remember { mutableStateOf(initial?.end != null) }
    var includeTime by remember { mutableStateOf(initial?.includeTime ?: false) }
    var timeText by remember { mutableStateOf((initial?.time ?: LocalTime.of(9, 0)).format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var month by remember { mutableStateOf(YearMonth.from(start)) }
    var editingEnd by remember { mutableStateOf(false) }
    val time = runCatching { LocalTime.parse(timeText, DateTimeFormatter.ofPattern("H:mm")) }.getOrNull()

    MonoBottomSheet(onDismiss = onDismiss, title = title) {
        Column(Modifier.padding(horizontal = Space.l)) {
            Row(Modifier.fillMaxWidth().padding(vertical = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                MonoChip(start.format(DateTimeFormatter.ofPattern("MMM d, yyyy")), selected = !editingEnd, onClick = { editingEnd = false })
                if (hasEnd) MonoChip(end?.format(DateTimeFormatter.ofPattern("MMM d, yyyy")) ?: "End date", selected = editingEnd, onClick = { editingEnd = true })
            }
            MonthCalendar(
                month = month,
                onMonthChange = { month = it },
                selected = start,
                rangeEnd = if (hasEnd) end else null,
                onSelect = { d ->
                    if (editingEnd) {
                        if (d.isBefore(start)) { end = start; start = d } else end = d
                    } else {
                        start = d
                        if (hasEnd && end != null && end!!.isBefore(d)) end = d
                        if (hasEnd) editingEnd = true
                    }
                },
            )
            Spacer(Modifier.height(Space.s))
            if (allowRange) FormRow("End date") {
                MonoSwitch(hasEnd, { hasEnd = it; if (it && end == null) end = start; editingEnd = it }, label = "End date")
            }
            if (allowTime) {
                FormRow("Include time") { MonoSwitch(includeTime, { includeTime = it }, label = "Include time") }
                if (includeTime) {
                    MonoTextField(
                        timeText, { timeText = it.take(5) }, label = "Time (24h, HH:mm)",
                        error = if (time == null) "Use HH:mm" else null,
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    )
                }
            }
            Spacer(Modifier.height(Space.l))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                MonoButton("Clear", { onSave(null) }, style = MonoButtonStyle.Text)
                Spacer(Modifier.weight(1f))
                MonoButton("Today", { start = LocalDate.now(); month = YearMonth.now() }, style = MonoButtonStyle.Outlined)
                MonoButton(
                    "Done",
                    { onSave(PickedDate(start, if (hasEnd) end else null, includeTime && time != null, if (includeTime) time else null)) },
                    style = MonoButtonStyle.Filled,
                    enabled = !includeTime || time != null,
                )
            }
        }
    }
}

@Composable
fun BorderBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.border(1.dp, MonoColors.Ink)) { content() }
}
