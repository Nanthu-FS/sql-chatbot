package com.spendlens.app.lock

import android.os.SystemClock
import com.spendlens.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * PIN / fingerprint lock. Locked at launch when a PIN is set, and again after the app has been in
 * the background for [RELOCK_AFTER_MS]. The PIN is stored only as a salted hash.
 */
class AppLock(private val settings: SettingsRepository, private val scope: CoroutineScope) {

    enum class State { UNKNOWN, LOCKED, OPEN }

    private val _state = MutableStateFlow(State.UNKNOWN)
    val state: StateFlow<State> = _state.asStateFlow()

    @Volatile private var pinHash: String? = null
    @Volatile private var backgroundAt = 0L
    private var failures = 0
    private var blockedUntil = 0L

    init {
        scope.launch {
            settings.settings.collect { s ->
                if (!s.loaded) return@collect
                pinHash = s.pinHash
                when {
                    s.pinHash == null -> _state.value = State.OPEN
                    _state.value == State.UNKNOWN -> _state.value = State.LOCKED
                }
            }
        }
    }

    val enabled: Boolean get() = pinHash != null

    fun onBackground() {
        backgroundAt = SystemClock.elapsedRealtime()
    }

    fun onForeground() {
        if (enabled && backgroundAt > 0 && SystemClock.elapsedRealtime() - backgroundAt > RELOCK_AFTER_MS) {
            _state.value = State.LOCKED
        }
        backgroundAt = 0
    }

    /** Seconds left before another PIN attempt is allowed, 0 when free. */
    fun waitSeconds(): Int = ((blockedUntil - SystemClock.elapsedRealtime()).coerceAtLeast(0) / 1000).toInt()

    /** Checks the PIN; unlocks on success. Five misses in a row block tries for 30 seconds. */
    fun tryPin(pin: String): Boolean {
        if (waitSeconds() > 0) return false
        val ok = matches(pin, pinHash)
        if (ok) {
            failures = 0
            _state.value = State.OPEN
        } else if (++failures >= MAX_FAILURES) {
            failures = 0
            blockedUntil = SystemClock.elapsedRealtime() + BLOCK_MS
        }
        return ok
    }

    fun unlockWithBiometrics() {
        failures = 0
        _state.value = State.OPEN
    }

    fun setPin(pin: String) {
        val hash = hash(pin)
        pinHash = hash
        scope.launch { settings.setPinHash(hash) }
    }

    fun clearPin() {
        pinHash = null
        _state.value = State.OPEN
        scope.launch { settings.setPinHash(null) }
    }

    fun check(pin: String): Boolean = matches(pin, pinHash)

    companion object {
        const val PIN_LENGTH = 4
        const val RELOCK_AFTER_MS = 30_000L
        private const val MAX_FAILURES = 5
        private const val BLOCK_MS = 30_000L

        fun hash(pin: String, salt: String = newSalt()): String = "$salt:${digest(salt, pin)}"

        fun matches(pin: String, stored: String?): Boolean {
            val salt = stored?.substringBefore(':', "") ?: return false
            if (salt.isEmpty()) return false
            return MessageDigest.isEqual(stored.toByteArray(), "$salt:${digest(salt, pin)}".toByteArray())
        }

        private fun newSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

        private fun digest(salt: String, pin: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            var bytes = "$salt:$pin".toByteArray()
            repeat(20_000) { bytes = md.digest(bytes) }
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
