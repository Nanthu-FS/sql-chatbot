package com.smartnotes.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.smartnotes.ui.theme.SkinTheme
import com.smartnotes.ui.theme.Skins
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private val openNote = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent {
            val skin by vm.skin.collectAsState()
            LaunchedEffect(skin) {
                val dark = Skins.tokens(skin).dark
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            SkinTheme(skin) {
                AppNav(vm, openNote)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshContext()
    }

    // Android 10+ only allows clipboard reads while the app has focus.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) vm.captureClipboard()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_NOTE_ID, -1L) ?: -1L
        if (id > 0) openNote.value = id
    }

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
    }
}
