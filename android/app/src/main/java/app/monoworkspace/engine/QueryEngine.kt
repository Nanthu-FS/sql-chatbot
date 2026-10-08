package app.monoworkspace.engine

import app.monoworkspace.engine.formula.Evaluator
import app.monoworkspace.model.Conjunction
import app.monoworkspace.model.DatabaseSchema
import app.monoworkspace.model.FilterGroup
import app.monoworkspace.model.FilterOperator
import app.monoworkspace.model.FilterRule
import app.monoworkspace.model.PropertyDef
import app.monoworkspace.model.PropertyType
import app.monoworkspace.model.SelectOption
import app.monoworkspace.model.SortDirection
import app.monoworkspace.model.SortRule
import app.monoworkspace.model.StatusGroup
import java.time.LocalDate
import java.time.ZonedDateTime

object FilterEngine {

    fun matches(row: RowInput, group: FilterGroup, resolver: RowResolver, today: LocalDate): Boolean {
        if (group.isEmpty) return true
        val results = sequence {
            for (r in group.rules) yield { matchesRule(row, r, resolver, today) }
            for (g in group.groups) if (!g.isEmpty) yield { matches(row, g, resolver, today) }
        }
        return when (group.conjunction) {
            Conjunction.AND -> results.all { it() }
            Conjunction.OR -> results.any { it() }
        }
    }

    fun matchesRule(row: RowInput, rule: FilterRule, resolver: RowResolver, today: LocalDate): Boolean {
        val prop = resolver.schema.property(rule.propertyId) ?: return true
        val cell = resolver.cell(row, prop)
        return evaluate(cell, prop, rule, today)
    }

    fun evaluate(cell: CellValue, prop: PropertyDef, rule: FilterRule, today: LocalDate): Boolean {
        val v = rule.value
        if (cell is CellValue.Error) return rule.operator == FilterOperator.IS_EMPTY
        return when (rule.operator) {
            FilterOperator.IS_EMPTY -> cell.isEmpty
            FilterOperator.IS_NOT_EMPTY -> !cell.isEmpty
            FilterOperator.CONTAINS -> when (cell) {
                is CellValue.Options -> v.optionIds.isEmpty() && v.text != null && cell.options.any { it.name.contains(v.text, true) } ||
                    v.optionIds.isNotEmpty() && v.optionIds.all { id -> cell.options.any { it.id == id } }
                else -> asText(cell).contains(v.text.orEmpty(), ignoreCase = true)
            }
            FilterOperator.NOT_CONTAINS -> when (cell) {
                is CellValue.Options -> if (v.optionIds.isNotEmpty()) v.optionIds.none { id -> cell.options.any { it.id == id } }
                else cell.options.none { it.name.contains(v.text.orEmpty(), true) }
                CellValue.Empty -> true
                else -> !asText(cell).contains(v.text.orEmpty(), ignoreCase = true)
            }
            FilterOperator.IS -> when {
                prop.type == PropertyType.SELECT || prop.type == PropertyType.STATUS -> {
                    val id = (cell as? CellValue.Options)?.options?.firstOrNull()?.id
                    id != null && id == v.optionIds.firstOrNull()
                }
                cell is CellValue.DateTime -> v.date?.let { cell.start.toLocalDate() == LocalDate.parse(it) } ?: false
                else -> asText(cell).equals(v.text.orEmpty(), ignoreCase = true)
            }
            FilterOperator.IS_NOT -> when {
                prop.type == PropertyType.SELECT || prop.type == PropertyType.STATUS -> {
                    val id = (cell as? CellValue.Options)?.options?.firstOrNull()?.id
                    id != v.optionIds.firstOrNull()
                }
                else -> !asText(cell).equals(v.text.orEmpty(), ignoreCase = true)
            }
            FilterOperator.IS_ANY_OF -> {
                val ids = (cell as? CellValue.Options)?.options?.map { it.id }.orEmpty()
                ids.any { it in v.optionIds }
            }
            FilterOperator.EQ -> number(cell)?.let { it == v.number } ?: false
            FilterOperator.NEQ -> number(cell)?.let { it != v.number } ?: (v.number != null)
            FilterOperator.LT -> number(cell)?.let { n -> v.number?.let { n < it } } ?: false
            FilterOperator.GT -> number(cell)?.let { n -> v.number?.let { n > it } } ?: false
            FilterOperator.BETWEEN -> number(cell)?.let { n ->
                val lo = v.number ?: Double.NEGATIVE_INFINITY
                val hi = v.number2 ?: Double.POSITIVE_INFINITY
                n >= minOf(lo, hi) && n <= maxOf(lo, hi)
            } ?: false
            FilterOperator.BEFORE -> date(cell)?.let { d -> v.date?.let { d.isBefore(LocalDate.parse(it)) } } ?: false
            FilterOperator.AFTER -> date(cell)?.let { d -> v.date?.let { d.isAfter(LocalDate.parse(it)) } } ?: false
            FilterOperator.DATE_BETWEEN -> date(cell)?.let { d ->
                val a = v.date?.let { LocalDate.parse(it) } ?: LocalDate.MIN
                val b = v.date2?.let { LocalDate.parse(it) } ?: LocalDate.MAX
                val lo = if (a.isBefore(b)) a else b
                val hi = if (a.isBefore(b)) b else a
                !d.isBefore(lo) && !d.isAfter(hi)
            } ?: false
            FilterOperator.PAST_WEEK -> date(cell)?.let { !it.isAfter(today) && !it.isBefore(today.minusWeeks(1)) } ?: false
            FilterOperator.PAST_MONTH -> date(cell)?.let { !it.isAfter(today) && !it.isBefore(today.minusMonths(1)) } ?: false
            FilterOperator.NEXT_WEEK -> date(cell)?.let { !it.isBefore(today) && !it.isAfter(today.plusWeeks(1)) } ?: false
            FilterOperator.NEXT_MONTH -> date(cell)?.let { !it.isBefore(today) && !it.isAfter(today.plusMonths(1)) } ?: false
            FilterOperator.CHECKED -> (cell as? CellValue.Bool)?.value == true
            FilterOperator.NOT_CHECKED -> (cell as? CellValue.Bool)?.value != true
            FilterOperator.RELATION_CONTAINS -> (cell as? CellValue.Rows)?.rows?.any { it.id == v.rowId && !it.deleted } ?: false
        }
    }

    private fun asText(cell: CellValue): String = runCatching { Evaluator.text(cell) }.getOrDefault("")

    private fun number(cell: CellValue): Double? = (cell as? CellValue.Num)?.value

    private fun date(cell: CellValue): LocalDate? = (cell as? CellValue.DateTime)?.start?.toLocalDate()

    /** Values a new row should get so that it shows up in a filtered view. */
    fun defaultsFor(group: FilterGroup, schema: DatabaseSchema): Map<String, app.monoworkspace.model.PropertyValue> {
        if (group.conjunction != Conjunction.AND) return emptyMap()
        val out = LinkedHashMap<String, app.monoworkspace.model.PropertyValue>()
        for (rule in group.rules) {
            val prop = schema.property(rule.propertyId) ?: continue
            val v = rule.value
            when (prop.type) {
                PropertyType.SELECT, PropertyType.STATUS -> when (rule.operator) {
                    FilterOperator.IS, FilterOperator.IS_ANY_OF ->
                        v.optionIds.firstOrNull()?.let { out[prop.id] = app.monoworkspace.model.PropertyValue.Select(it) }
                    else -> Unit
                }
                PropertyType.MULTI_SELECT -> if (rule.operator == FilterOperator.CONTAINS || rule.operator == FilterOperator.IS_ANY_OF) {
                    if (v.optionIds.isNotEmpty()) out[prop.id] = app.monoworkspace.model.PropertyValue.Multi(v.optionIds.take(1))
                }
                PropertyType.CHECKBOX -> if (rule.operator == FilterOperator.CHECKED) out[prop.id] = app.monoworkspace.model.PropertyValue.Checkbox(true)
                PropertyType.TEXT, PropertyType.URL, PropertyType.EMAIL, PropertyType.PHONE ->
                    if ((rule.operator == FilterOperator.IS || rule.operator == FilterOperator.CONTAINS) && !v.text.isNullOrEmpty()) {
                        out[prop.id] = app.monoworkspace.model.PropertyValue.Text(v.text)
                    }
                PropertyType.NUMBER -> if (rule.operator == FilterOperator.EQ && v.number != null) {
                    out[prop.id] = app.monoworkspace.model.PropertyValue.Number(v.number)
                }
                PropertyType.DATE -> if (rule.operator == FilterOperator.IS && v.date != null) {
                    out[prop.id] = app.monoworkspace.model.PropertyValue.DateValue(v.date)
                }
                PropertyType.RELATION -> if (rule.operator == FilterOperator.RELATION_CONTAINS && v.rowId != null) {
                    out[prop.id] = app.monoworkspace.model.PropertyValue.Relation(listOf(v.rowId))
                }
                else -> Unit
            }
        }
        return out
    }
}

object SortEngine {

    /** Stable multi-level sort. Empty values always sort last, whatever the direction. */
    fun sort(rows: List<RowInput>, sorts: List<SortRule>, resolver: RowResolver): List<RowInput> {
        val active = sorts.take(3).mapNotNull { s -> resolver.schema.property(s.propertyId)?.let { it to s.direction } }
        if (active.isEmpty()) return rows.sortedBy { it.orderKey }
        val comparator = Comparator<RowInput> { a, b ->
            for ((prop, dir) in active) {
                val c = compareCells(resolver.cell(a, prop), resolver.cell(b, prop), prop, dir)
                if (c != 0) return@Comparator c
            }
            a.orderKey.compareTo(b.orderKey)
        }
        return rows.sortedWith(comparator)
    }

    fun compareCells(a: CellValue, b: CellValue, prop: PropertyDef?, dir: SortDirection): Int {
        val aEmpty = a.isEmpty || a is CellValue.Error
        val bEmpty = b.isEmpty || b is CellValue.Error
        if (aEmpty && bEmpty) return 0
        if (aEmpty) return 1
        if (bEmpty) return -1
        val c = compareNonEmpty(a, b, prop)
        return if (dir == SortDirection.ASC) c else -c
    }

    private fun compareNonEmpty(a: CellValue, b: CellValue, prop: PropertyDef?): Int = when {
        a is CellValue.Num && b is CellValue.Num -> a.value.compareTo(b.value)
        a is CellValue.Text && b is CellValue.Text -> a.value.compareTo(b.value, ignoreCase = true)
        a is CellValue.Bool && b is CellValue.Bool -> a.value.compareTo(b.value)
        a is CellValue.DateTime && b is CellValue.DateTime -> a.start.toInstant().compareTo(b.start.toInstant())
        a is CellValue.Options && b is CellValue.Options -> {
            val ao = a.options.first()
            val bo = b.options.first()
            val c = optionRank(ao, prop).compareTo(optionRank(bo, prop))
            if (c != 0) c else ao.name.compareTo(bo.name, ignoreCase = true)
        }
        a is CellValue.Rows && b is CellValue.Rows -> a.rows.size.compareTo(b.rows.size)
        a is CellValue.Files && b is CellValue.Files -> a.files.size.compareTo(b.files.size)
        else -> rank(a).compareTo(rank(b))
    }

    private fun optionRank(o: SelectOption, prop: PropertyDef?): Int {
        val groupRank = o.group?.ordinal ?: 0
        return groupRank * 10_000 + o.order
    }

    private fun rank(v: CellValue): Int = when (v) {
        is CellValue.Num -> 0
        is CellValue.Text -> 1
        is CellValue.DateTime -> 2
        is CellValue.Bool -> 3
        is CellValue.Options -> 4
        is CellValue.Rows -> 5
        is CellValue.Files -> 6
        CellValue.Empty -> 7
        is CellValue.Error -> 8
    }
}

data class RowGroup(val key: String?, val label: String, val option: SelectOption?, val rows: List<RowInput>)

object Grouping {
    const val NONE_KEY = "__none__"

    /** Groups rows by a property; select-like properties produce one group per option in order. */
    fun group(rows: List<RowInput>, prop: PropertyDef, resolver: RowResolver): List<RowGroup> {
        return when (prop.type) {
            PropertyType.SELECT, PropertyType.STATUS, PropertyType.MULTI_SELECT -> {
                val options = if (prop.type == PropertyType.STATUS) {
                    prop.options.sortedWith(compareBy({ it.group?.ordinal ?: 0 }, { it.order }))
                } else prop.sortedOptions
                val buckets = LinkedHashMap<String, MutableList<RowInput>>()
                options.forEach { buckets[it.id] = ArrayList() }
                val none = ArrayList<RowInput>()
                for (r in rows) {
                    val cell = resolver.cell(r, prop)
                    val ids = (cell as? CellValue.Options)?.options?.map { it.id }.orEmpty()
                    if (ids.isEmpty()) none.add(r)
                    else if (prop.type == PropertyType.MULTI_SELECT) ids.forEach { buckets[it]?.add(r) }
                    else buckets[ids.first()]?.add(r) ?: none.add(r)
                }
                options.map { RowGroup(it.id, it.name, it, buckets.getValue(it.id)) } +
                    RowGroup(null, "No ${prop.name.lowercase()}", null, none)
            }
            PropertyType.CHECKBOX -> {
                val (on, off) = rows.partition { (resolver.cell(it, prop) as? CellValue.Bool)?.value == true }
                listOf(RowGroup("true", "Checked", null, on), RowGroup("false", "Unchecked", null, off))
            }
            else -> {
                val map = LinkedHashMap<String, MutableList<RowInput>>()
                val none = ArrayList<RowInput>()
                for (r in rows) {
                    val label = ValueFormat.display(resolver.cell(r, prop), prop)
                    if (label.isEmpty()) none.add(r) else map.getOrPut(label) { ArrayList() }.add(r)
                }
                map.entries.sortedBy { it.key.lowercase() }.map { RowGroup(it.key, it.key, null, it.value) } +
                    RowGroup(null, "No ${prop.name.lowercase()}", null, none)
            }
        }
    }

    fun statusGroupOf(prop: PropertyDef, optionId: String?): StatusGroup? = prop.option(optionId)?.group
}

object ValueFormat {
    private val dateFmt = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy")
    private val timeFmt = java.time.format.DateTimeFormatter.ofPattern("h:mm a")

    fun date(d: ZonedDateTime, includeTime: Boolean): String =
        if (includeTime) "${d.format(dateFmt)} ${d.format(timeFmt)}" else d.format(dateFmt)

    fun number(value: Double, prop: PropertyDef?): String {
        val cfg = prop?.config
        val decimals = (cfg?.decimals ?: 0).coerceIn(0, 4)
        val fmt = cfg?.numberFormat ?: app.monoworkspace.model.NumberFormat.PLAIN
        val scaled = if (fmt == app.monoworkspace.model.NumberFormat.PERCENT) value * 100 else value
        val nf = java.text.NumberFormat.getNumberInstance(java.util.Locale.US).apply {
            isGroupingUsed = fmt != app.monoworkspace.model.NumberFormat.PLAIN
            minimumFractionDigits = decimals
            // Plain numbers with no fixed decimals show what was typed, up to 4 places.
            maximumFractionDigits = if (fmt == app.monoworkspace.model.NumberFormat.PLAIN && decimals == 0) 4 else decimals
        }
        val body = nf.format(scaled)
        return when (fmt) {
            app.monoworkspace.model.NumberFormat.PERCENT -> "$body%"
            app.monoworkspace.model.NumberFormat.CURRENCY -> (cfg?.currencySymbol ?: "$") + body
            else -> body
        }
    }

    fun display(cell: CellValue, prop: PropertyDef?): String = when (cell) {
        CellValue.Empty -> ""
        is CellValue.Text -> cell.value
        is CellValue.Num -> number(cell.value, prop)
        is CellValue.Bool -> if (cell.value) "Checked" else ""
        is CellValue.DateTime -> buildString {
            append(date(cell.start, cell.includeTime))
            cell.end?.let { append(" → ").append(date(it, cell.includeTime)) }
        }
        is CellValue.Options -> cell.options.joinToString(", ") { it.name }
        is CellValue.Rows -> cell.rows.joinToString(", ") { it.title }
        is CellValue.Files -> cell.files.joinToString(", ") { it.name }
        is CellValue.Error -> "#ERR"
    }

    /** Plain export text (CSV, markdown, search). */
    fun export(cell: CellValue, prop: PropertyDef?): String = when (cell) {
        is CellValue.Bool -> if (cell.value) "Yes" else "No"
        is CellValue.DateTime -> buildString {
            append(if (cell.includeTime) cell.start.toOffsetDateTime().toString() else cell.start.toLocalDate().toString())
            cell.end?.let { append("/").append(if (cell.includeTime) it.toOffsetDateTime().toString() else it.toLocalDate().toString()) }
        }
        is CellValue.Rows -> cell.rows.filter { !it.deleted }.joinToString(", ") { it.title }
        is CellValue.Error -> "#ERR"
        else -> display(cell, prop)
    }
}

object Validation {
    private val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    private val urlRegex = Regex("^(https?://)?([\\w-]+\\.)+[\\w-]{2,}(:\\d+)?(/\\S*)?$", RegexOption.IGNORE_CASE)
    private val phoneRegex = Regex("^\\+?[0-9 ()\\-.]{5,20}$")

    /** Returns a short warning, or null when the value is fine or empty. */
    fun warning(type: PropertyType, value: String): String? {
        if (value.isBlank()) return null
        return when (type) {
            PropertyType.EMAIL -> if (emailRegex.matches(value.trim())) null else "Not a valid email"
            PropertyType.URL -> if (urlRegex.matches(value.trim())) null else "Not a valid URL"
            PropertyType.PHONE -> if (phoneRegex.matches(value.trim())) null else "Not a valid phone number"
            else -> null
        }
    }
}
