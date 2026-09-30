package com.spendlens.app.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Opens the system fingerprint / face prompt; null when the device can't do it. */
val LocalBiometricUnlock = staticCompositionLocalOf<(() -> Unit)?> { null }

object Biometrics {
    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun available(context: Context): Boolean =
        runCatching { BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS }.getOrDefault(false)

    /** Build once in onCreate; [prompt] then shows it. */
    fun create(activity: FragmentActivity, onSuccess: () -> Unit): BiometricPrompt =
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            },
        )

    fun prompt(prompt: BiometricPrompt) {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock SpendLens")
            .setSubtitle("Use your fingerprint")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        runCatching { prompt.authenticate(info) }
    }
}
