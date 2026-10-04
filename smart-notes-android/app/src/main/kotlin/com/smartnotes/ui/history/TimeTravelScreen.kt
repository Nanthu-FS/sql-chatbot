package com.smartnotes.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.smartnotes.core.LineChange
import com.smartnotes.core.TimeTravel
import com.smartnotes.ui.MainViewModel
import com.smartnotes.ui.theme.LocalSkin
import com.smartnotes.ui.theme.SkinButton
import com.smartnotes.ui.theme.SkinLabel
import com.smartnotes.ui.theme.SkinScaffold
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/** Scrub through every saved version of a note; Play animates the history like a video. */
@Composable
fun TimeTravelScreen(vm: MainViewModel, nav: NavController, noteId: Long) {
    val t = LocalSkin.current
    val noteFlow = remember(noteId) { vm.repo.note(noteId) }
    val versionsFlow = remember(noteId) { vm.repo.versions(noteId) }
    val note by noteFlow.collectAsState(initial = null)
    val versions by versionsFlow.collectAsState(initial = emptyList())
    var position by remember { mutableFloatStateOf(1f) }
    var playing by remember { mutableStateOf(false) }

    LaunchedEffect(playing, versions.size) {
        if (!playing || versions.size < 2) { playing = false; return@LaunchedEffect }
        var i = 0
        while (playing && i < versions.size) {
            position = i.toFloat() / (versions.size - 1)
            delay(700)
            i++
        }
        playing = false
    }

    SkinScaffold(title = "Time travel", subtitle = "${versions.size} versions", onBack = { nav.popBackStack() }) {
        if (versions.isEmpty()) {
            SkinLabel("No history yet. Versions are saved as you edit.", Modifier.padding(20.dp))
            return@SkinScaffold
        }
        val idx = TimeTravel.indexAt(position, versions.size)
        val v = versions[idx]
        val prev = versions.getOrNull(idx - 1)
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkinLabel("Version ${idx + 1} of ${versions.size} · ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(v.savedAt))}")
            Slider(
                value = position, onValueChange = { playing = false; position = it },
                steps = (versions.size - 2).coerceAtLeast(0),
                colors = SliderDefaults.colors(thumbColor = t.accent, activeTrackColor = t.accent, inactiveTrackColor = t.muted.copy(alpha = 0.4f)),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SkinButton(if (playing) "Pause" else "Play history", { if (!playing) position = 0f; playing = !playing }, primary = false)
                SkinButton("Restore", {
                    note?.let { vm.save(it.copy(title = v.title, body = v.body)) }
                    nav.popBackStack()
                }, enabled = idx != versions.lastIndex)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(v.title, fontFamily = t.display, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            TimeTravel.diff(prev?.body.orEmpty(), v.body).forEach { line ->
                when (line.change) {
                    LineChange.SAME -> Text(line.text, fontFamily = t.cardBody)
                    LineChange.ADDED -> Text("+ " + line.text, fontFamily = t.cardBody, modifier = Modifier.fillMaxWidth().background(t.accent.copy(alpha = 0.18f)))
                    LineChange.REMOVED -> Text("− " + line.text, fontFamily = t.cardBody, color = t.muted, textDecoration = TextDecoration.LineThrough)
                }
            }
        }
    }
}
