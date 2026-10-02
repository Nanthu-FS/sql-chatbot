package com.smartnotes.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.smartnotes.features.SpeechCapture
import com.smartnotes.ui.MainViewModel
import com.smartnotes.ui.openNoteReplacing
import com.smartnotes.ui.theme.LocalSkin
import com.smartnotes.ui.theme.SkinButton
import com.smartnotes.ui.theme.SkinCard
import com.smartnotes.ui.theme.SkinLabel
import com.smartnotes.ui.theme.SkinScaffold
import com.smartnotes.ui.theme.SkinTextField

/**
 * Meeting mode: picks up the current calendar event, transcribes and splits turns on pauses,
 * then saves the transcript under a meeting template.
 */
@Composable
fun MeetingScreen(vm: MainViewModel, nav: NavController) {
    val t = LocalSkin.current
    val context = LocalContext.current
    val event by vm.currentMeeting.collectAsState()
    var title by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(event) { if (title.isEmpty()) event?.let { title = it.title } }

    val speech = remember { SpeechCapture(context, continuous = true, markTurns = true) }
    DisposableEffect(Unit) { onDispose { speech.destroy() } }
    val transcript by speech.transcript.collectAsState()
    val partial by speech.partial.collectAsState()
    val listening by speech.listening.collectAsState()

    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) speech.start() }

    SkinScaffold(
        title = "Meeting",
        subtitle = if (listening) "● Recording" else event?.let { "Now: ${it.title}" } ?: "No event right now",
        onBack = { nav.popBackStack() },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SkinButton(if (listening) "Stop" else "● Transcribe", {
                    when {
                        listening -> speech.stop()
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> speech.start()
                        else -> mic.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }, Modifier.weight(1f), primary = !listening)
                SkinButton(if (transcript.isBlank()) "Use template" else "Save notes", {
                    speech.stop()
                    vm.saveMeeting(title.ifBlank { "Meeting" }, transcript) { nav.openNoteReplacing(it) }
                }, Modifier.weight(1f), primary = listening)
            }
        },
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SkinTextField(title, { title = it }, "Meeting title", Modifier.fillMaxWidth())
            SkinCard(title = "Live transcript") {
                if (transcript.isEmpty() && partial.isEmpty()) SkinLabel("Phone on the table, then tap Transcribe")
                Text(transcript)
                if (partial.isNotEmpty()) Text(partial, color = t.muted)
            }
            SkinLabel("Saved with an agenda / decisions / action-items template above the transcript.")
        }
    }
}
