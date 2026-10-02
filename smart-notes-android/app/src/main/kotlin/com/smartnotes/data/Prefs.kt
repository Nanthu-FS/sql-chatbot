package com.smartnotes.data

import android.content.Context
import android.content.SharedPreferences
import com.smartnotes.ui.theme.Skin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class Prefs(context: Context) {

    private val plain: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _skin = MutableStateFlow(
        runCatching { Skin.valueOf(plain.getString(KEY_SKIN, null) ?: "") }.getOrDefault(Skin.SWISS),
    )
    val skin: StateFlow<Skin> = _skin

    fun setSkin(skin: Skin) {
        plain.edit().putString(KEY_SKIN, skin.name).apply()
        _skin.value = skin
    }

    private companion object {
        const val KEY_SKIN = "skin"
    }
}
