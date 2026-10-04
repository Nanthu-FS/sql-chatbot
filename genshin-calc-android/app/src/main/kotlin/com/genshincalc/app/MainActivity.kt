package com.genshincalc.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import com.genshincalc.app.ui.App
import com.genshincalc.app.ui.GenshinCalcTheme

class MainActivity : ComponentActivity() {
    private val viewModel: CalcViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) importSharedImages(intent)
        setContent {
            GenshinCalcTheme {
                App(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        importSharedImages(intent)
    }

    /** Screenshots shared to the app ("Share" → Teyvat DMG Calc) are scanned for artifacts. */
    fun importSharedImages(intent: Intent?) {
        if (intent == null || intent.type?.startsWith("image/") != true) return
        // Reopened from recents: the shared images were already imported (and their read grant is gone).
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        viewModel.importScreenshots(uris)
    }
}
