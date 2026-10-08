package app.monoworkspace.engine

import app.monoworkspace.engine.formula.CompiledFormula
import app.monoworkspace.engine.formula.FormulaContext
import app.monoworkspace.model.DatabaseSchema
import app.monoworkspace.model.PropertyDef
import app.monoworkspace.model.PropertyType
import app.monoworkspace.model.PropertyValue
import app.monoworkspace.model.RollupFunction
import java.time.ZoneId
import java.time.ZonedDateTime

/** Raw row as stored: title lives on the page, other values in property values. */
data class RowInput(
    val id: String,
    val title: String,
    val icon: String? = null,
    val createdAt: Long,
    val editedAt: Long,
    val values: Map<String, PropertyValue> = emptyMap(),
    val firstImagePath: String? = null,
    val orderKey: String = "",
)

/** Rows of another database, needed to resolve relations and rollups. */
data class RelatedDatabase(val schema: DatabaseSchema, val rows: Map<String, RowInput>)

/**
 * Resolves stored and computed values for the rows of one database.
 * Rollups and formulas are evaluated at read time and memoised per row.
 */
class RowResolver(
    val schema: DatabaseSchema,
    rows: List<RowInput>,
    private val related: Map<String, RelatedDatabase> = emptyMap(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: ZonedDateTime = ZonedDateTime.now(zone),
    private val depth: Int = 0,
    private val ownDatabaseId: String? = null,
) {
    private val rowsById = rows.associateBy { it.id }
    private val formulas = HashMap<String, CompiledFormula>()
    private val cache = HashMap<Pair<String, String>, CellValue>()
    private val evaluating = HashSet<Pair<String, String>>()
    private val relatedResolvers = HashMap<String, RowResolver?>()

    fun formula(prop: PropertyDef): CompiledFormula =
        formulas.getOrPut(prop.id + "\u0000" + prop.config.expression) { CompiledFormula(prop.config.expression) }

    fun cell(row: RowInput, prop: PropertyDef): CellValue {
        val key = row.id to prop.id
        cache[key]?.let { return it }
        if (!evaluating.add(key)) return CellValue.Error("Circular reference to \"${prop.name}\"")
        try {
            val v = compute(row, prop)
            cache[key] = v
            return v
        } finally {
            evaluating.remove(key)
        }
    }

    fun cell(rowId: String, propertyId: String): CellValue {
        val row = rowsById[rowId] ?: return CellValue.Empty
        val prop = schema.property(propertyId) ?: return CellValue.Empty
        return cell(row, prop)
    }

    private fun compute(row: RowInput, prop: PropertyDef): CellValue {
        val stored = row.values[prop.id]
        return when (prop.type) {
            PropertyType.TITLE -> CellValue.Text(row.title)
            PropertyType.TEXT, PropertyType.URL, PropertyType.EMAIL, PropertyType.PHONE ->
                (stored as? PropertyValue.Text)?.value?.takeIf { it.isNotEmpty() }?.let { CellValue.Text(it) } ?: CellValue.Empty
            PropertyType.NUMBER -> (stored as? PropertyValue.Number)?.let { CellValue.Num(it.value) } ?: CellValue.Empty
            PropertyType.SELECT, PropertyType.STATUS -> {
                val id = (stored as? PropertyValue.Select)?.optionId
                val opt = prop.option(id)
                if (opt == null) CellValue.Empty else CellValue.Options(listOf(opt))
            }
            PropertyType.MULTI_SELECT -> {
                val ids = (stored as? PropertyValue.Multi)?.optionIds.orEmpty()
                val opts = ids.mapNotNull { prop.option(it) }
                if (opts.isEmpty()) CellValue.Empty else CellValue.Options(opts)
            }
            PropertyType.DATE -> (stored as? PropertyValue.DateValue)?.let { Dates.parse(it, zone) } ?: CellValue.Empty
            PropertyType.CHECKBOX -> CellValue.Bool((stored as? PropertyValue.Checkbox)?.value ?: false)
            PropertyType.FILES -> (stored as? PropertyValue.Files)?.files?.takeIf { it.isNotEmpty() }?.let { CellValue.Files(it) } ?: CellValue.Empty
            PropertyType.RELATION -> {
                val ids = (stored as? PropertyValue.Relation)?.rowIds.orEmpty()
                if (ids.isEmpty()) CellValue.Empty else CellValue.Rows(ids.map { relatedRef(prop, it) })
            }
            PropertyType.ROLLUP -> rollup(row, prop)
            PropertyType.FORMULA -> formula(prop).evaluate(object : FormulaContext {
                override fun prop(name: String): CellValue {
                    val target = schema.byName(name) ?: return CellValue.Error("No property named \"$name\"")
                    return cell(row, target)
                }

                override fun now(): ZonedDateTime = now
            })
            PropertyType.CREATED_TIME -> CellValue.DateTime(Dates.fromEpoch(row.createdAt, zone), includeTime = true)
            PropertyType.EDITED_TIME -> CellValue.DateTime(Dates.fromEpoch(row.editedAt, zone), includeTime = true)
            PropertyType.CREATED_BY -> CellValue.Text("You")
        }
    }

    private fun relatedRows(prop: PropertyDef): RelatedDatabase? {
        val dbId = prop.config.relationDatabaseId ?: return null
        if (dbId == ownDatabaseId) return RelatedDatabase(schema, rowsById)
        return related[dbId]
    }

    private fun relatedRef(prop: PropertyDef, rowId: String): RowRef {
        val target = relatedRows(prop)?.rows?.get(rowId)
        return if (target == null) RowRef(rowId, "Deleted row", deleted = true) else RowRef(rowId, target.title.ifBlank { "Untitled" })
    }

    private fun resolverFor(dbId: String): RowResolver? {
        if (dbId == ownDatabaseId) return this
        return relatedResolvers.getOrPut(dbId) {
            val rel = related[dbId] ?: return@getOrPut null
            // Rollups of rollups stop one level deep to keep evaluation bounded.
            if (depth >= 1) null else RowResolver(rel.schema, rel.rows.values.toList(), related, zone, now, depth + 1, dbId)
        }
    }

    private fun rollup(row: RowInput, prop: PropertyDef): CellValue {
        val relProp = schema.property(prop.config.rollupRelationPropertyId)
            ?: return CellValue.Error("Pick a relation for this rollup")
        if (relProp.type != PropertyType.RELATION) return CellValue.Error("Rollup source is not a relation")
        val dbId = relProp.config.relationDatabaseId ?: return CellValue.Error("Relation has no target")
        val ids = (row.values[relProp.id] as? PropertyValue.Relation)?.rowIds.orEmpty()
        val resolver = resolverFor(dbId) ?: return CellValue.Error("Rollup is too deep")
        val targetRows = ids.mapNotNull { resolver.rowsById[it] }
        val fn = prop.config.rollupFunction
        if (fn == RollupFunction.COUNT) return CellValue.Num(targetRows.size.toDouble())
        val targetProp = resolver.schema.property(prop.config.rollupTargetPropertyId)
            ?: return CellValue.Error("Pick a property to roll up")
        val values = targetRows.map { resolver.cell(it, targetProp) }
        return Rollups.apply(fn, values)
    }
}

object Rollups {
    fun apply(fn: RollupFunction, values: List<CellValue>): CellValue {
        return when (fn) {
            RollupFunction.COUNT -> CellValue.Num(values.size.toDouble())
            RollupFunction.COUNT_VALUES -> CellValue.Num(
                values.sumOf { v ->
                    when (v) {
                        is CellValue.Options -> v.options.size
                        is CellValue.Rows -> v.rows.count { !it.deleted }
                        is CellValue.Files -> v.files.size
                        is CellValue.Bool -> if (v.value) 1 else 0
                        else -> if (v.isEmpty || v is CellValue.Error) 0 else 1
                    }
                }.toDouble(),
            )
            RollupFunction.SUM, RollupFunction.AVERAGE, RollupFunction.MIN, RollupFunction.MAX -> {
                val nums = values.mapNotNull { (it as? CellValue.Num)?.value }
                if (nums.isEmpty()) return CellValue.Empty
                CellValue.Num(
                    when (fn) {
                        RollupFunction.SUM -> nums.sum()
                        RollupFunction.AVERAGE -> nums.average()
                        RollupFunction.MIN -> nums.min()
                        else -> nums.max()
                    },
                )
            }
            RollupFunction.EARLIEST, RollupFunction.LATEST -> {
                val dates = values.mapNotNull { it as? CellValue.DateTime }
                if (dates.isEmpty()) return CellValue.Empty
                if (fn == RollupFunction.EARLIEST) dates.minBy { it.start.toInstant() } else dates.maxBy { it.start.toInstant() }
            }
        }
    }
}
