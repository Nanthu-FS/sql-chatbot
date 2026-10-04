package com.urbanlens.core.alerts

enum class HealthProfile(val label: String, val description: String, val defaultThreshold: Int) {
    GENERAL("General", "Alert when the air is unhealthy for everyone", 151),
    SENSITIVE("Sensitive", "Asthma, heart or lung conditions, older adults, kids", 101),
}

data class AlertState(val alerting: Boolean = false, val lastNotifiedAtMillis: Long = 0L)

enum class AlertKind { WORSENED, RECOVERED }

data class AlertDecision(val notify: AlertKind?, val state: AlertState)

/**
 * Decides when to notify about AQI at a saved place. Uses hysteresis so a reading that hovers
 * around the threshold doesn't spam, and a cooldown between "air got worse" alerts.
 */
object AqiAlertPolicy {
    const val RECOVERY_MARGIN = 10
    const val COOLDOWN_MILLIS = 3 * 3_600_000L

    fun evaluate(aqi: Int, threshold: Int, state: AlertState, nowMillis: Long): AlertDecision {
        if (!state.alerting) {
            if (aqi < threshold) return AlertDecision(null, state)
            val cooledDown = nowMillis - state.lastNotifiedAtMillis >= COOLDOWN_MILLIS
            return if (cooledDown) {
                AlertDecision(AlertKind.WORSENED, AlertState(alerting = true, lastNotifiedAtMillis = nowMillis))
            } else {
                AlertDecision(null, state.copy(alerting = true))
            }
        }
        return if (aqi < threshold - RECOVERY_MARGIN) {
            AlertDecision(AlertKind.RECOVERED, state.copy(alerting = false))
        } else {
            AlertDecision(null, state)
        }
    }
}
