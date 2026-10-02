package com.smartnotes.features.capture

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.smartnotes.app
import com.smartnotes.data.NoteSource
import com.smartnotes.features.SpeechCapture
import com.smartnotes.ui.theme.SkinButton
import com.smartnotes.ui.theme.SkinIconButton
import com.smartnotes.ui.theme.SkinLabel
import com.smartnotes.ui.theme.SkinScaffold
import com.smartnotes.ui.theme.SkinTextField
import com.smartnotes.ui.theme.SkinTheme
import kotlinx.coroutines.launch

/**
 * Capture-only screen that opens over the lock screen. It never shows existing notes,
 * so nothing private is exposed without unlocking.
 */
class QuickCaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true)
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)

        val startVoice = intent.getBooleanExtra(EXTRA_VOICE, false)
        setContent {
            val skin by app.prefs.skin.collectAsState()
            SkinTheme(skin) {
                val context = LocalContext.current
                val speech = remember { SpeechCapture(context, continuous = false) }
                DisposableEffect(Unit) { onDispose { speech.destroy() } }
                val transcript by speech.transcript.collectAsState()
                val partial by speech.partial.collectAsState()
                val listening by speech.listening.collectAsState()
                var text by remember { mutableStateOf("") }
                LaunchedEffect(transcript) { if (transcript.isNotBlank()) text = (text + " " + transcript).trim() }
                LaunchedEffect(Unit) { if (startVoice) speech.start() }

                SkinScaffold(title = "Quick note", subtitle = "Saved without unlocking", onBack = { finish() }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SkinTextField(text, { text = it }, "Type or tap the mic…", Modifier.fillMaxWidth(), singleLine = false, minHeight = 160.dp)
                        if (partial.isNotBlank()) SkinLabel(partial)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SkinIconButton(
                                if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                                if (listening) "Stop dictation" else "Dictate",
                                { if (listening) speech.stop() else speech.start() },
                                primary = listening,
                            )
                            SkinButton("Save", onClick = {
                                val body = text.trim()
                                if (body.isNotEmpty()) {
                                    val title = body.lineSequence().first().take(48)
                                    val repo = app.repo
                                    lifecycleScope.launch {
                                        repo.create(title, body, NoteSource.TEXT)
                                        finish()
                                    }
                                } else finish()
                            }, modifier = Modifier.weight(1f))
                        }
                        Text("Unlock and open Smart Notes to see your notes.", color = com.smartnotes.ui.theme.LocalSkin.current.muted)
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_VOICE = "voice"
    }
}
