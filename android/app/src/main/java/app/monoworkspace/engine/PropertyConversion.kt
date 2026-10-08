package app.monoworkspace.engine

import app.monoworkspace.core.Ids
import app.monoworkspace.model.DefaultSchemas
import app.monoworkspace.model.PropertyDef
import app.monoworkspace.model.PropertyType
import app.monoworkspace.model.PropertyValue
import app.monoworkspace.model.SelectOption
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Converts stored values when a property changes type. Values that cannot be
 * converted are dropped and counted so the UI can tell the user once.
 */
object PropertyConversion {

    data class Result(
        val property: PropertyDef,
        val values: Map<String, PropertyValue?>,
        val cleared: Int,
    )

    fun convert(old: PropertyDef, newType: PropertyType, values: Map<String, PropertyValue>): Result {
        if (old.type == newType) return Result(old, values, 0)
        val asText: Map<String, String> = values.mapValues { (_, v) -> textOf(v, old) }

        var options: List<SelectOption> = emptyList()
        if (newType.hasOptions) {
            options = when {
                old.type.hasOptions -> old.options.mapIndexed { i, o ->
                    o.copy(order = i, group = if (newType == PropertyType.STATUS) o.group ?: app.monoworkspace.model.StatusGroup.TODO else null)
                }
                else -> {
                    val distinct = asText.values
                        .flatMap { if (newType == PropertyType.MULTI_SELECT) it.split(",") else listOf(it) }
                        .map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                    distinct.mapIndexed { i, name ->
                        SelectOption(Ids.new(), name, i, if (newType == PropertyType.STATUS) app.monoworkspace.model.StatusGroup.TODO else null)
                    }
                }
            }
            if (newType == PropertyType.STATUS && options.isEmpty()) options = DefaultSchemas.statusOptions()
        }
        val newProp = old.copy(type = newType, options = options)
        val byName = options.associateBy { it.name.lowercase() }

        var cleared = 0
        val out = LinkedHashMap<String, PropertyValue?>()
        for ((rowId, v) in values) {
            val text = asText[rowId].orEmpty()
            val converted: PropertyValue? = when (newType) {
                PropertyType.TEXT, PropertyType.URL, PropertyType.EMAIL, PropertyType.PHONE ->
                    text.takeIf { it.isNotEmpty() }?.let { PropertyValue.Text(it.take(2000)) }
                PropertyType.NUMBER -> when (v) {
                    is PropertyValue.Number -> v
                    is PropertyValue.Checkbox -> PropertyValue.Number(if (v.value) 1.0 else 0.0)
                    else -> text.replace(",", "").trim().removeSuffix("%").toDoubleOrNull()?.let { PropertyValue.Number(it) }
                }
                PropertyType.SELECT, PropertyType.STATUS -> when (v) {
                    is PropertyValue.Select -> if (options.any { it.id == v.optionId }) v else null
                    is PropertyValue.Multi -> v.optionIds.firstOrNull { id -> options.any { it.id == id } }?.let { PropertyValue.Select(it) }
                    else -> byName[text.trim().lowercase()]?.let { PropertyValue.Select(it.id) }
                }
                PropertyType.MULTI_SELECT -> when (v) {
                    is PropertyValue.Multi -> v
                    is PropertyValue.Select -> PropertyValue.Multi(listOf(v.optionId))
                    else -> text.split(",").mapNotNull { byName[it.trim().lowercase()]?.id }.distinct()
                        .takeIf { it.isNotEmpty() }?.let { PropertyValue.Multi(it) }
                }
                PropertyType.DATE -> when (v) {
                    is PropertyValue.DateValue -> v
                    else -> parseDate(text)
                }
                PropertyType.CHECKBOX -> when (v) {
                    is PropertyValue.Checkbox -> v
                    is PropertyValue.Number -> PropertyValue.Checkbox(v.value != 0.0)
                    else -> when (text.trim().lowercase()) {
                        "true", "yes", "y", "1", "checked", "x" -> PropertyValue.Checkbox(true)
                        "false", "no", "n", "0", "" -> PropertyValue.Checkbox(false)
                        else -> null
                    }
                }
                PropertyType.RELATION -> v as? PropertyValue.Relation
                PropertyType.FILES -> v as? PropertyValue.Files
                // Computed or fixed types never store values.
                else -> null
            }
            val hadContent = text.isNotEmpty() || v is PropertyValue.Checkbox && v.value
            if (converted == null && hadContent && !newType.computed) cleared++
            out[rowId] = converted
        }
        return Result(newProp, out, cleared)
    }

    fun textOf(v: PropertyValue, prop: PropertyDef): String = when (v) {
        is PropertyValue.Text -> v.value
        is PropertyValue.Number -> app.monoworkspace.engine.formula.Evaluator.formatNumber(v.value)
        is PropertyValue.Select -> prop.option(v.optionId)?.name.orEmpty()
        is PropertyValue.Multi -> v.optionIds.mapNotNull { prop.option(it)?.name }.joinToString(", ")
        is PropertyValue.DateValue -> v.start
        is PropertyValue.Checkbox -> if (v.value) "Yes" else ""
        is PropertyValue.Relation -> ""
        is PropertyValue.Files -> v.files.joinToString(", ") { it.name }
    }

    fun parseDate(text: String): PropertyValue.DateValue? {
        val t = text.trim()
        if (t.isEmpty()) return null
        runCatching { LocalDate.parse(t) }.getOrNull()?.let { return PropertyValue.DateValue(it.toString()) }
        runCatching { OffsetDateTime.parse(t) }.getOrNull()?.let { return PropertyValue.DateValue(it.toString(), includeTime = true) }
        val formats = listOf("M/d/yyyy", "d.M.yyyy", "MMM d, yyyy", "MMMM d, yyyy", "yyyy/M/d", "d MMM yyyy")
        for (f in formats) {
            val parsed = runCatching {
                LocalDate.parse(t, java.time.format.DateTimeFormatter.ofPattern(f, java.util.Locale.US))
            }.getOrNull()
            if (parsed != null) return PropertyValue.DateValue(parsed.toString())
        }
        return null
    }
}
