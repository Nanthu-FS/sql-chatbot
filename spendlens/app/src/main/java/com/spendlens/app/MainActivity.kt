package com.spendlens.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendlens.app.data.AppSettings
import com.spendlens.app.data.ThemeMode
import com.spendlens.app.ui.LocalAppContainer
import com.spendlens.app.ui.SpendLensRoot
import com.spendlens.app.ui.theme.SpendLensTheme
import com.spendlens.app.notify.Notifier
import com.spendlens.app.work.RefreshWorker

class MainActivity : ComponentActivity() {

    private val container get() = (application as SpendLensApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        runCatching { RefreshWorker.schedule(this) }

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            CompositionLocalProvider(LocalAppContainer provides container) {
                SpendLensTheme(darkTheme = dark) {
                    SpendLensRoot(settings = settings)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        intent ?: return
        if (intent.getStringExtra(Notifier.EXTRA_ACTION) == Notifier.ACTION_SCAN) container.pendingScan.value = true
        intent.getLongExtra(Notifier.EXTRA_OPEN_TXN, -1L).takeIf { it > 0 }?.let { container.pendingOpen.value = it }
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) container.sharedImages.value = uris
    }

}
