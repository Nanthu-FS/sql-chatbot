package app.monoworkspace

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.monoworkspace.ui.common.LayoutMode
import app.monoworkspace.ui.common.LocalAppContainer
import app.monoworkspace.ui.common.LocalWindowLayout
import app.monoworkspace.ui.common.WindowLayout
import app.monoworkspace.ui.lock.LockScreen
import app.monoworkspace.ui.navigation.AppShell
import app.monoworkspace.ui.theme.MonoColors
import app.monoworkspace.ui.theme.MonoTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Single activity. A FragmentActivity so BiometricPrompt can attach. */
class MainActivity : FragmentActivity() {

    private val container get() = (application as MonoApp).container

    /** Locked state survives rotation; process death starts locked again when enabled. */
    private var locked by mutableStateOf(false)
    private var stoppedAt = 0L
    private var promptShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.WHITE, android.graphics.Color.WHITE),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.WHITE, android.graphics.Color.WHITE),
        )
        super.onCreate(savedInstanceState)
        val restoredLock = savedInstanceState?.getBoolean(KEY_LOCKED)
        locked = restoredLock ?: runBlocking { container.settings.settings.first().appLock }

        setContent {
            val windowSize = calculateWindowSizeClass(this)
            val config = LocalConfiguration.current
            val mode = when {
                windowSize.widthSizeClass == WindowWidthSizeClass.Compact -> LayoutMode.Phone
                windowSize.heightSizeClass == WindowHeightSizeClass.Compact -> LayoutMode.Landscape
                else -> LayoutMode.Tablet
            }
            val layout = WindowLayout(mode, config.screenWidthDp, config.screenHeightDp)
            val initialPage by produceState<String?>(null) {
                // Seed first, then reopen the tour (first run) or the last page.
                val seeded = container.seeder.seedIfNeeded()
                if (savedInstanceState != null) return@produceState
                value = seeded ?: container.settings.current().lastOpenedPageId?.let { id ->
                    container.pages.get(id)?.takeIf { !it.isTrashed }?.id
                }
            }
            MonoTheme {
                CompositionLocalProvider(LocalAppContainer provides container, LocalWindowLayout provides layout) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MonoColors.Background)
                            .windowInsetsPadding(WindowInsets.safeDrawing),
                    ) {
                        AppShell(initialPage)
                        if (locked) {
                            LockScreen(onUnlock = ::authenticate)
                            LaunchedEffect(Unit) { authenticate() }
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LOCKED, locked)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt == 0L) return
        val away = SystemClock.elapsedRealtime() - stoppedAt
        stoppedAt = 0L
        if (away >= LOCK_AFTER_MS && runBlocking { container.settings.settings.first().appLock }) locked = true
    }

    private fun authenticate() {
        if (!locked || promptShowing) return
        val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            // No screen lock on the device: nothing to verify against.
            locked = false
            return
        }
        promptShowing = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    promptShowing = false
                    locked = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    promptShowing = false
                }

                override fun onAuthenticationFailed() = Unit
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.app_name))
                .setSubtitle("Unlock to continue")
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }

    companion object {
        private const val KEY_LOCKED = "locked"
        private const val LOCK_AFTER_MS = 60_000L
    }
}
