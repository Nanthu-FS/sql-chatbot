package com.spendlens.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.spendlens.app.domain.CurrencyOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

data class AppSettings(
    val currency: CurrencyOption = CurrencyOption.INR,
    val monthlyBudgetMinor: Long? = null,
    val theme: ThemeMode = ThemeMode.DARK,
    val autoFindDays: Int = 30,
    /** Take-home pay per month; lets goals work out what can be put aside. */
    val monthlyIncomeMinor: Long? = null,
    /** 0 = flat editorial, 1 = full frosted glass. */
    val glass: Float = 0.55f,
    /** Ongoing notification with today's and this month's spend (also shows on the lock screen). */
    val summaryNotification: Boolean = false,
    /** Add bank debit SMS automatically as they arrive. */
    val smsAutoImport: Boolean = false,
    val alertNotifications: Boolean = true,
    val dismissedAlerts: Set<String> = emptySet(),
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {

    private val store = context.dataStore

    private object Keys {
        val currency = stringPreferencesKey("currency")
        val budget = longPreferencesKey("monthly_budget")
        val theme = stringPreferencesKey("theme")
        val autoFindDays = intPreferencesKey("auto_find_days")
        val income = longPreferencesKey("monthly_income")
        val glass = floatPreferencesKey("glass")
        val summary = booleanPreferencesKey("summary_notification")
        val smsAuto = booleanPreferencesKey("sms_auto_import")
        val alerts = booleanPreferencesKey("alert_notifications")
        val dismissed = stringSetPreferencesKey("dismissed_alerts")
    }

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            currency = CurrencyOption.fromCode(prefs[Keys.currency]),
            monthlyBudgetMinor = prefs[Keys.budget]?.takeIf { it > 0 },
            theme = ThemeMode.entries.firstOrNull { it.name == prefs[Keys.theme] } ?: ThemeMode.DARK,
            autoFindDays = prefs[Keys.autoFindDays] ?: 30,
            monthlyIncomeMinor = prefs[Keys.income]?.takeIf { it > 0 },
            glass = (prefs[Keys.glass] ?: 0.55f).coerceIn(0f, 1f),
            summaryNotification = prefs[Keys.summary] ?: false,
            smsAutoImport = prefs[Keys.smsAuto] ?: false,
            alertNotifications = prefs[Keys.alerts] ?: true,
            dismissedAlerts = prefs[Keys.dismissed].orEmpty(),
        )
    }

    suspend fun setCurrency(currency: CurrencyOption) {
        store.edit { it[Keys.currency] = currency.code }
    }

    suspend fun setMonthlyBudget(amountMinor: Long?) = setOptionalLong(Keys.budget, amountMinor)

    suspend fun setMonthlyIncome(amountMinor: Long?) = setOptionalLong(Keys.income, amountMinor)

    private suspend fun setOptionalLong(key: Preferences.Key<Long>, value: Long?) {
        store.edit { prefs -> if (value == null || value <= 0) prefs.remove(key) else prefs[key] = value }
    }

    suspend fun setTheme(theme: ThemeMode) {
        store.edit { it[Keys.theme] = theme.name }
    }

    suspend fun setAutoFindDays(days: Int) {
        store.edit { it[Keys.autoFindDays] = days }
    }

    suspend fun setGlass(level: Float) {
        store.edit { it[Keys.glass] = level.coerceIn(0f, 1f) }
    }

    suspend fun setSummaryNotification(on: Boolean) {
        store.edit { it[Keys.summary] = on }
    }

    suspend fun setSmsAutoImport(on: Boolean) {
        store.edit { it[Keys.smsAuto] = on }
    }

    suspend fun setAlertNotifications(on: Boolean) {
        store.edit { it[Keys.alerts] = on }
    }

    suspend fun dismissAlert(key: String) {
        store.edit { it[Keys.dismissed] = it[Keys.dismissed].orEmpty() + key }
    }
}
