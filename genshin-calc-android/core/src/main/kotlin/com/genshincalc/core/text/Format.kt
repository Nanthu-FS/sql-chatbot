package com.genshincalc.core.text

import com.genshincalc.core.model.Stat
import com.genshincalc.core.model.TalentAttribute
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Number formatting shared by the app screens. */
object Format {
    /** 12345.6 -> "12,346". */
    fun int(value: Double): String = String.format(Locale.US, "%,d", value.roundToLong())

    /** 0.466 -> "46.6%". */
    fun pct(value: Double, decimals: Int = 1): String = String.format(Locale.US, "%.${decimals}f%%", value * 100)

    /** Compact damage numbers: 1234567 -> "1.23M", 45678 -> "45,678". */
    fun damage(value: Double): String = when {
        abs(value) >= 10_000_000 -> String.format(Locale.US, "%.2fM", value / 1_000_000)
        else -> int(value)
    }

    fun stat(stat: Stat, value: Double): String = if (stat.percent) pct(value) else int(value)

    /** Number typed by the user: "46.6" -> 46.6, "1,234" -> 1234. */
    fun parse(text: String): Double? = text.trim().replace(",", "").toDoubleOrNull()

    /** Value shown in an edit field for a stat: percents as "46.6", flats as "311". */
    fun editable(stat: Stat, value: Double): String = if (stat.percent) trim(value * 100) else trim(value)

    fun trim(value: Double): String {
        val s = String.format(Locale.US, "%.2f", value)
        return s.trimEnd('0').trimEnd('.')
    }
}

/** Renders talent attribute templates such as "{param1:F1P}+{param2:F1P}" at a talent level. */
object TalentText {
    // Braces are escaped: Android's ICU regex engine rejects a bare '}'.
    private val placeholder = Regex("""\{(param\d+|x_[A-Za-z0-9_-]+):([A-Z0-9]+)\}""")

    fun format(attribute: TalentAttribute, params: Map<String, List<Double>>, level: Int): String =
        format(attribute.value, params, level)

    fun format(template: String, params: Map<String, List<Double>>, level: Int): String =
        placeholder.replace(template) { m ->
            val values = params[m.groupValues[1]] ?: return@replace "?"
            val v = values[(level - 1).coerceIn(0, values.size - 1)]
            formatValue(v, m.groupValues[2])
        }

    fun formatValue(v: Double, code: String): String = when (code) {
        "F1P" -> String.format(Locale.US, "%.1f%%", v * 100)
        "F2P" -> String.format(Locale.US, "%.2f%%", v * 100)
        "P" -> String.format(Locale.US, "%.0f%%", v * 100)
        "F1" -> String.format(Locale.US, "%.1f", v)
        "F2" -> String.format(Locale.US, "%.2f", v)
        "I" -> String.format(Locale.US, "%,d", v.roundToLong())
        else -> Format.trim(v)
    }
}
