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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** Talk while you walk. Say "todo …" to add a checklist item. */
@Composable
fun VoiceScreen(vm: MainViewModel, nav: NavController) {
    val t = LocalSkin.current
    val context = LocalContext.current
    val speech = remember { SpeechCapture(context, continuous = true) }
    DisposableEffect(Unit) { onDispose { speech.destroy() } }
    val transcript by speech.transcript.collectAsState()
    val partial by speech.partial.collectAsState()
    val listening by speech.listening.collectAsState()
    val error by speech.error.collectAsState()

    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) speech.start() }
    val toggle = {
        when {
            listening -> speech.stop()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> speech.start()
            else -> mic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    SkinScaffold(
        title = "Voice note",
        subtitle = if (listening) "● Listening" else "Tap record and talk",
        onBack = { nav.popBackStack() },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SkinButton(if (listening) "Stop" else "● Record", toggle, Modifier.weight(1f), primary = !listening)
                SkinButton(
                    "Save note",
                    { speech.stop(); vm.createFromVoice(transcript) { nav.openNoteReplacing(it) } },
                    Modifier.weight(1f), primary = listening, enabled = transcript.isNotBlank(),
                )
            }
        },
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            error?.let { Text(it, color = t.accent) }
            SkinCard(title = "Transcript") {
                if (transcript.isEmpty() && partial.isEmpty()) SkinLabel("Your words will appear here")
                Text(transcript, fontSize = 17.sp)
                if (partial.isNotEmpty()) Text(partial, color = t.muted, fontSize = 17.sp)
            }
            SkinLabel("Say \"todo\" before an action item to make it a checkbox.")
        }
    }
}
