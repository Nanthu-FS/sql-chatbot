package com.spendlens.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {

    private val store = context.dataStore

    private object Keys {
        val currency = stringPreferencesKey("currency")
        val budget = longPreferencesKey("monthly_budget")
        val theme = stringPreferencesKey("theme")
        val autoFindDays = intPreferencesKey("auto_find_days")
    }

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            currency = CurrencyOption.fromCode(prefs[Keys.currency]),
            monthlyBudgetMinor = prefs[Keys.budget]?.takeIf { it > 0 },
            theme = ThemeMode.entries.firstOrNull { it.name == prefs[Keys.theme] } ?: ThemeMode.DARK,
            autoFindDays = prefs[Keys.autoFindDays] ?: 30,
        )
    }

    suspend fun setCurrency(currency: CurrencyOption) {
        store.edit { it[Keys.currency] = currency.code }
    }

    suspend fun setMonthlyBudget(amountMinor: Long?) {
        store.edit { prefs ->
            if (amountMinor == null || amountMinor <= 0) prefs.remove(Keys.budget) else prefs[Keys.budget] = amountMinor
        }
    }

    suspend fun setTheme(theme: ThemeMode) {
        store.edit { it[Keys.theme] = theme.name }
    }

    suspend fun setAutoFindDays(days: Int) {
        store.edit { it[Keys.autoFindDays] = days }
    }
}
