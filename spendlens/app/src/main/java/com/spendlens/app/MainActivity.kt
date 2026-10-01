package com.spendlens.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
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
import com.spendlens.app.ui.theme.Style
import com.spendlens.app.notify.Notifier
import com.spendlens.app.work.RefreshWorker
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.fragment.app.FragmentActivity
import com.spendlens.app.lock.AppLock
import com.spendlens.app.lock.Biometrics
import com.spendlens.app.lock.LocalBiometricUnlock
import com.spendlens.app.ui.components.Screen
import com.spendlens.app.ui.screens.lock.LockScreen

/** FragmentActivity so the system fingerprint prompt can attach to it. */
class MainActivity : FragmentActivity() {

    private val container get() = (application as SpendLensApplication).container
    private lateinit var biometricPrompt: BiometricPrompt

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        runCatching { RefreshWorker.schedule(this) }
        biometricPrompt = Biometrics.create(this) { container.lock.unlockWithBiometrics() }

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
            val lockState by container.lock.state.collectAsStateWithLifecycle()
            val canUseBiometrics = settings.biometricUnlock && Biometrics.available(this)
            val unlockWithBiometrics: (() -> Unit)? = if (canUseBiometrics) ({ Biometrics.prompt(biometricPrompt) }) else null
            CompositionLocalProvider(LocalAppContainer provides container, LocalBiometricUnlock provides unlockWithBiometrics) {
                SpendLensTheme(darkTheme = dark, style = Style.from(settings.style), accent = settings.accent?.let { androidx.compose.ui.graphics.Color(it.toInt()) }) {
                    when (lockState) {
                        // Nothing until we know whether to lock, so no totals flash on screen.
                        AppLock.State.UNKNOWN -> Screen {}
                        AppLock.State.LOCKED -> Box {
                            Box(Modifier.clearAndSetSemantics { }) { SpendLensRoot(settings = settings) }
                            LockScreen(container.lock, unlockWithBiometrics)
                        }
                        AppLock.State.OPEN -> SpendLensRoot(settings = settings)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.lock.onForeground()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) container.lock.onBackground()
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
        if (uris.isNotEmpty()) {
            container.sharedImages.value = uris
        } else if (intent.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { container.sharedText.value = it }
        }
    }

}
