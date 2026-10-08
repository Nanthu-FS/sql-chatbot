package app.monoworkspace.data.repo

import app.monoworkspace.engine.FilterEngine
import app.monoworkspace.engine.Grouping
import app.monoworkspace.engine.RowGroup
import app.monoworkspace.engine.RowInput
import app.monoworkspace.engine.RowResolver
import app.monoworkspace.engine.SortEngine
import app.monoworkspace.model.DatabaseView
import app.monoworkspace.model.PropertyDef
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** The rows a view shows, in order, plus the resolver used to read their values. */
data class ViewResult(
    val resolver: RowResolver,
    val rows: List<RowInput>,
    val groups: List<RowGroup>?,
    val visibleProperties: List<PropertyDef>,
    val total: Int,
)

object DatabaseQuery {

    fun run(snapshot: DatabaseSnapshot, view: DatabaseView, zone: ZoneId = ZoneId.systemDefault()): ViewResult {
        val now = ZonedDateTime.now(zone)
        val schema = snapshot.database.schema
        val resolver = RowResolver(schema, snapshot.rows, snapshot.related, zone, now, ownDatabaseId = snapshot.database.id)
        val today: LocalDate = now.toLocalDate()
        val filtered = snapshot.rows.filter { FilterEngine.matches(it, view.config.filter, resolver, today) }
        val sorted = SortEngine.sort(filtered, view.config.sorts, resolver)
        val groupProp = schema.property(view.config.groupBy)
        val groups = groupProp?.let { Grouping.group(sorted, it, resolver) }
        return ViewResult(resolver, sorted, groups, visibleProperties(snapshot, view), snapshot.rows.size)
    }

    /** Properties in view order; the title always comes first. */
    fun orderedProperties(snapshot: DatabaseSnapshot, view: DatabaseView): List<PropertyDef> {
        val props = snapshot.database.schema.properties
        val order = view.config.propertyOrder
        val ordered = if (order.isEmpty()) props else {
            val byId = props.associateBy { it.id }
            order.mapNotNull { byId[it] } + props.filter { it.id !in order }
        }
        return ordered.sortedByDescending { it.isTitle }
    }

    fun visibleProperties(snapshot: DatabaseSnapshot, view: DatabaseView): List<PropertyDef> {
        val visible = view.config.visibleProperties
        return orderedProperties(snapshot, view).filter { it.isTitle || visible == null || it.id in visible }
    }
}
