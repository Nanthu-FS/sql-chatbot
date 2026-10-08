package app.monoworkspace.data.repo

import androidx.room.withTransaction
import app.monoworkspace.core.Clock
import app.monoworkspace.core.FractionalIndex
import app.monoworkspace.core.Ids
import app.monoworkspace.data.db.DatabaseEntity
import app.monoworkspace.data.db.MonoDatabase
import app.monoworkspace.data.db.PageEntity
import app.monoworkspace.data.db.PropertyValueEntity
import app.monoworkspace.data.decodeProps
import app.monoworkspace.data.decodeValue
import app.monoworkspace.data.encodeSchema
import app.monoworkspace.data.encodeValue
import app.monoworkspace.data.toEntity
import app.monoworkspace.data.toModel
import app.monoworkspace.data.toValueMap
import app.monoworkspace.engine.PropertyConversion
import app.monoworkspace.engine.RelatedDatabase
import app.monoworkspace.engine.RowInput
import app.monoworkspace.model.Database
import app.monoworkspace.model.DatabaseSchema
import app.monoworkspace.model.DatabaseView
import app.monoworkspace.model.DefaultSchemas
import app.monoworkspace.model.Page
import app.monoworkspace.model.PropertyConfig
import app.monoworkspace.model.PropertyDef
import app.monoworkspace.model.PropertyType
import app.monoworkspace.model.PropertyValue
import app.monoworkspace.model.SelectOption
import app.monoworkspace.model.StatusGroup
import app.monoworkspace.model.ViewConfig
import app.monoworkspace.model.ViewType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext

data class DatabaseSnapshot(
    val page: Page,
    val database: Database,
    val views: List<DatabaseView>,
    val rows: List<RowInput>,
    val rowPages: Map<String, Page>,
    val related: Map<String, RelatedDatabase>,
    val relatedTitles: Map<String, String>,
)

data class DatabaseRef(val database: Database, val page: Page)

sealed interface CsvTarget {
    data object Skip : CsvTarget
    data object Title : CsvTarget
    data class Existing(val propertyId: String) : CsvTarget
    data class NewProperty(val name: String, val type: PropertyType = PropertyType.TEXT) : CsvTarget
}

@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseRepository(
    private val db: MonoDatabase,
    private val search: SearchRepository,
    private val pagesRepo: PageRepository,
    private val clock: Clock = Clock.System,
) {
    private val pages get() = db.pages()

    // ---------- Reading ----------

    suspend fun byPage(pageId: String): Database? = withContext(Dispatchers.IO) { db.databases().byPage(pageId)?.toModel() }
    suspend fun get(id: String): Database? = withContext(Dispatchers.IO) { db.databases().get(id)?.toModel() }

    fun observeDatabases(): Flow<List<DatabaseRef>> = combine(db.databases().observeAllLive(), pages.observeAllLive()) { dbs, ps ->
        val byId = ps.associateBy { it.id }
        dbs.mapNotNull { d -> byId[d.pageId]?.let { DatabaseRef(d.toModel(), it.toModel()) } }
            .sortedBy { it.page.title.lowercase() }
    }

    private suspend fun rowInputs(rows: List<PageEntity>, values: Map<String, Map<String, PropertyValue>>): List<RowInput> {
        val images = if (rows.isEmpty()) emptyMap() else rows.map { it.id }.chunked(500).flatMap { db.blocks().imagesFor(it) }
            .groupBy { it.pageId }
            .mapValues { (_, list) -> list.firstNotNullOfOrNull { decodeProps(it.propsJson).mediaPath } }
        return rows.map { p ->
            RowInput(
                id = p.id, title = p.title, icon = p.icon, createdAt = p.createdAt, editedAt = p.editedAt,
                values = values[p.id].orEmpty(), firstImagePath = images[p.id], orderKey = p.orderKey,
            )
        }
    }

    private fun observeRelated(databaseId: String): Flow<Triple<String, RelatedDatabase, String>?> =
        db.databases().observe(databaseId).flatMapLatest { d ->
            if (d == null) flowOf(null) else combine(pages.observe(d.pageId), pages.observeRows(d.id), db.values().observeForDatabase(d.id)) { page, rows, values ->
                val vmap = values.toValueMap()
                val inputs = rows.map { p -> RowInput(p.id, p.title, p.icon, p.createdAt, p.editedAt, vmap[p.id].orEmpty(), orderKey = p.orderKey) }
                Triple(d.id, RelatedDatabase(d.toModel().schema, inputs.associateBy { it.id }), page?.title?.ifBlank { "Untitled" } ?: "Untitled")
            }
        }

    fun observeSnapshot(pageId: String): Flow<DatabaseSnapshot?> = db.databases().observeByPage(pageId).flatMapLatest { dbe ->
        if (dbe == null) return@flatMapLatest flowOf(null)
        val database = dbe.toModel()
        val own = combine(
            pages.observe(pageId),
            db.views().observe(database.id),
            pages.observeRows(database.id),
            db.values().observeForDatabase(database.id),
        ) { page, views, rows, values -> Own(page, views, rows, values) }
            .mapLatest { own ->
                val inputs = rowInputs(own.rows, own.values.toValueMap())
                own to inputs
            }
        val targets = database.schema.properties
            .filter { it.type == PropertyType.RELATION }
            .mapNotNull { it.config.relationDatabaseId }
            .filter { it != database.id }
            .distinct()
        val relatedFlow: Flow<List<Triple<String, RelatedDatabase, String>>> =
            if (targets.isEmpty()) flowOf(emptyList())
            else combine(targets.map { observeRelated(it) }) { arr -> arr.filterNotNull() }
        combine(own, relatedFlow) { (o, inputs), related ->
            val page = o.page ?: return@combine null
            val relMap = related.associate { it.first to it.second }.toMutableMap()
            relMap[database.id] = RelatedDatabase(database.schema, inputs.associateBy { it.id })
            DatabaseSnapshot(
                page = page.toModel(),
                database = database,
                views = o.views.map { it.toModel() },
                rows = inputs,
                rowPages = o.rows.associate { it.id to it.toModel() },
                related = relMap,
                relatedTitles = related.associate { it.first to it.third } + (database.id to page.title.ifBlank { "Untitled" }),
            )
        }
    }

    private data class Own(
        val page: PageEntity?,
        val views: List<app.monoworkspace.data.db.ViewEntity>,
        val rows: List<PageEntity>,
        val values: List<PropertyValueEntity>,
    )

    /** Values and schema for one row, kept live for the row screen. */
    fun observeRow(rowId: String): Flow<Pair<Database, Map<String, PropertyValue>>?> =
        pages.observe(rowId).flatMapLatest { p ->
            val dbId = p?.databaseId ?: return@flatMapLatest flowOf(null)
            combine(db.databases().observe(dbId), db.values().observeForRow(rowId)) { d, values ->
                if (d == null) null else d.toModel() to values.toValueMap()[rowId].orEmpty()
            }
        }

    suspend fun rowsOf(databaseId: String): List<Page> = withContext(Dispatchers.IO) { pages.rows(databaseId).map { it.toModel() } }

    suspend fun loadRelated(schema: DatabaseSchema, ownId: String): Map<String, RelatedDatabase> = withContext(Dispatchers.IO) {
        val out = HashMap<String, RelatedDatabase>()
        val targets = schema.properties.filter { it.type == PropertyType.RELATION }.mapNotNull { it.config.relationDatabaseId }.toSet() + ownId
        for (t in targets) {
            val d = db.databases().get(t) ?: continue
            val rows = pages.rows(t)
            val values = db.values().forDatabase(t).toValueMap()
            out[t] = RelatedDatabase(d.toModel().schema, rowInputs(rows, values).associateBy { it.id })
        }
        out
    }

    // ---------- Databases ----------

    suspend fun createDatabase(parentId: String?, title: String = "", icon: String? = null, schema: DatabaseSchema = DefaultSchemas.newDatabase()): Page =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val now = clock.now()
                val pageId = Ids.new()
                val dbId = Ids.new()
                val page = PageEntity(
                    id = pageId, parentId = parentId, databaseId = null, isDatabase = true, title = title, icon = icon, cover = null,
                    orderKey = FractionalIndex.between(pages.lastChildKey(parentId), null), isFavorite = false, favoriteOrder = null,
                    isTrashed = false, trashRoot = false, trashedAt = null, createdAt = now, editedAt = now,
                )
                pages.insert(page)
                db.databases().insert(DatabaseEntity(dbId, pageId, encodeSchema(schema)))
                db.views().insert(DatabaseView(Ids.new(), dbId, "Table", ViewType.TABLE, ViewConfig(), FractionalIndex.first()).toEntity())
                search.indexPage(page)
                page.toModel()
            }
        }

    suspend fun saveSchema(databaseId: String, schema: DatabaseSchema) = withContext(Dispatchers.IO) {
        db.databases().setSchema(databaseId, encodeSchema(schema))
        db.databases().get(databaseId)?.let { pages.touch(it.pageId, clock.now()) }
    }

    // ---------- Rows ----------

    suspend fun addRow(databaseId: String, title: String = "", values: Map<String, PropertyValue> = emptyMap(), icon: String? = null): Page =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val d = db.databases().get(databaseId) ?: throw IllegalStateException("Database not found")
                val now = clock.now()
                val row = PageEntity(
                    id = Ids.new(), parentId = d.pageId, databaseId = databaseId, isDatabase = false, title = title, icon = icon, cover = null,
                    orderKey = FractionalIndex.between(pages.lastRowKey(databaseId), null), isFavorite = false, favoriteOrder = null,
                    isTrashed = false, trashRoot = false, trashedAt = null, createdAt = now, editedAt = now,
                )
                pages.insert(row)
                if (values.isNotEmpty()) {
                    db.values().upsertAll(values.map { (k, v) -> PropertyValueEntity(row.id, k, encodeValue(v)) })
                }
                val schema = d.toModel().schema
                for ((propId, v) in values) {
                    val prop = schema.property(propId) ?: continue
                    if (prop.type == PropertyType.RELATION && v is PropertyValue.Relation) syncReverse(prop, row.id, emptySet(), v.rowIds.toSet())
                }
                search.indexPage(row)
                row.toModel()
            }
        }

    suspend fun setValue(rowId: String, propertyId: String, value: PropertyValue?) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val row = pages.get(rowId) ?: return@withTransaction
            val d = row.databaseId?.let { db.databases().get(it) } ?: return@withTransaction
            val prop = d.toModel().schema.property(propertyId) ?: return@withTransaction
            val old = db.values().forRow(rowId).firstOrNull { it.propertyId == propertyId }?.let { decodeValue(it.valueJson) }
            writeValue(rowId, propertyId, value)
            if (prop.type == PropertyType.RELATION) {
                val before = (old as? PropertyValue.Relation)?.rowIds.orEmpty().toSet()
                val after = (value as? PropertyValue.Relation)?.rowIds.orEmpty().toSet()
                syncReverse(prop, rowId, before, after)
            }
            pages.touch(rowId, clock.now())
        }
        search.reindexPage(rowId)
    }

    suspend fun setValueForRows(rowIds: Collection<String>, propertyId: String, value: PropertyValue?) {
        rowIds.forEach { setValue(it, propertyId, value) }
    }

    private suspend fun writeValue(rowId: String, propertyId: String, value: PropertyValue?) {
        val empty = value == null ||
            (value is PropertyValue.Text && value.value.isEmpty()) ||
            (value is PropertyValue.Multi && value.optionIds.isEmpty()) ||
            (value is PropertyValue.Relation && value.rowIds.isEmpty()) ||
            (value is PropertyValue.Files && value.files.isEmpty())
        if (empty) db.values().delete(rowId, propertyId)
        else db.values().upsert(PropertyValueEntity(rowId, propertyId, encodeValue(value!!)))
    }

    /** Keeps the reverse side of a two-way relation in sync. */
    private suspend fun syncReverse(prop: PropertyDef, rowId: String, before: Set<String>, after: Set<String>) {
        val reverseId = prop.config.relationReversePropertyId ?: return
        if (!prop.config.relationTwoWay) return
        for (target in after - before) {
            val cur = db.values().forRow(target).firstOrNull { it.propertyId == reverseId }?.let { decodeValue(it.valueJson) as? PropertyValue.Relation }
            val ids = cur?.rowIds.orEmpty()
            if (rowId !in ids) writeValue(target, reverseId, PropertyValue.Relation(ids + rowId))
        }
        for (target in before - after) {
            val cur = db.values().forRow(target).firstOrNull { it.propertyId == reverseId }?.let { decodeValue(it.valueJson) as? PropertyValue.Relation } ?: continue
            writeValue(target, reverseId, PropertyValue.Relation(cur.rowIds - rowId))
        }
    }

    suspend fun duplicateRow(rowId: String): Page? = withContext(Dispatchers.IO) {
        db.withTransaction {
            val row = pages.get(rowId) ?: return@withTransaction null
            val dbId = row.databaseId ?: return@withTransaction null
            val now = clock.now()
            val copy = row.copy(
                id = Ids.new(), title = row.title + " (copy)", orderKey = FractionalIndex.between(pages.lastRowKey(dbId), null),
                isFavorite = false, favoriteOrder = null, createdAt = now, editedAt = now,
            )
            pages.insert(copy)
            val values = db.values().forRow(rowId)
            db.values().upsertAll(values.map { it.copy(rowPageId = copy.id) })
            val blocks = db.blocks().byPage(rowId)
            val idMap = blocks.associate { it.id to Ids.new() }
            db.blocks().insertAll(blocks.map { b -> b.copy(id = idMap.getValue(b.id), pageId = copy.id, parentBlockId = b.parentBlockId?.let { idMap[it] }) })
            val schema = db.databases().get(dbId)?.toModel()?.schema
            values.forEach { v ->
                val prop = schema?.property(v.propertyId) ?: return@forEach
                val rel = decodeValue(v.valueJson) as? PropertyValue.Relation ?: return@forEach
                if (prop.type == PropertyType.RELATION) syncReverse(prop, copy.id, emptySet(), rel.rowIds.toSet())
            }
            search.indexPage(copy)
            db.blocks().byPage(copy.id).forEach { search.indexBlock(it, dbId) }
            copy.toModel()
        }
    }

    suspend fun trashRows(rowIds: Collection<String>) {
        rowIds.forEach { pagesRepo.trash(it) }
    }

    suspend fun moveRow(rowId: String, beforeKey: String?, afterKey: String?) = withContext(Dispatchers.IO) {
        pages.setOrder(rowId, FractionalIndex.between(beforeKey, afterKey))
    }

    // ---------- Properties ----------

    private suspend fun schemaOf(databaseId: String): DatabaseSchema =
        db.databases().get(databaseId)?.toModel()?.schema ?: throw IllegalStateException("Database not found")

    suspend fun addProperty(databaseId: String, name: String, type: PropertyType): PropertyDef = withContext(Dispatchers.IO) {
        val schema = schemaOf(databaseId)
        val unique = uniqueName(schema, name)
        val def = PropertyDef(
            id = Ids.new(), name = unique, type = type,
            options = if (type == PropertyType.STATUS) DefaultSchemas.statusOptions() else emptyList(),
            config = if (type == PropertyType.RELATION) PropertyConfig(relationDatabaseId = databaseId) else PropertyConfig(),
        )
        saveSchema(databaseId, schema.copy(properties = schema.properties + def))
        def
    }

    private fun uniqueName(schema: DatabaseSchema, name: String, exceptId: String? = null): String {
        val base = name.trim().ifEmpty { "Property" }
        var candidate = base
        var n = 2
        while (schema.properties.any { it.id != exceptId && it.name.equals(candidate, ignoreCase = true) }) candidate = "$base $n".also { n++ }
        return candidate
    }

    /** Saves name, options and config. Type changes go through [changeType]. */
    suspend fun updateProperty(databaseId: String, def: PropertyDef) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val schema = schemaOf(databaseId)
            val old = schema.property(def.id) ?: return@withTransaction
            val fixed = def.copy(type = old.type, name = uniqueName(schema, def.name, def.id), isTitle = old.isTitle)
            saveSchema(databaseId, schema.copy(properties = schema.properties.map { if (it.id == def.id) fixed else it }))
            // Options that were removed leave no dangling values.
            if (old.type.hasOptions) {
                val removed = old.options.map { it.id }.toSet() - fixed.options.map { it.id }.toSet()
                if (removed.isNotEmpty()) {
                    for (v in db.values().forDatabase(databaseId).filter { it.propertyId == def.id }) {
                        when (val value = decodeValue(v.valueJson)) {
                            is PropertyValue.Select -> if (value.optionId in removed) db.values().delete(v.rowPageId, def.id)
                            is PropertyValue.Multi -> writeValue(v.rowPageId, def.id, PropertyValue.Multi(value.optionIds - removed))
                            else -> Unit
                        }
                    }
                }
            }
            // Relation turned two-way: create and fill the reverse property.
            if (fixed.type == PropertyType.RELATION && fixed.config.relationTwoWay && fixed.config.relationReversePropertyId == null) {
                enableTwoWay(databaseId, fixed)
            }
            if (fixed.type == PropertyType.RELATION && !fixed.config.relationTwoWay && old.config.relationTwoWay) {
                old.config.relationReversePropertyId?.let { rid ->
                    old.config.relationDatabaseId?.let { target -> unlinkReverse(target, rid) }
                }
                val s = schemaOf(databaseId)
                saveSchema(databaseId, s.copy(properties = s.properties.map { if (it.id == def.id) it.copy(config = it.config.copy(relationReversePropertyId = null)) else it }))
            }
            // Retargeting a relation clears values that point at the old database.
            if (fixed.type == PropertyType.RELATION && old.config.relationDatabaseId != fixed.config.relationDatabaseId) {
                for (v in db.values().forDatabase(databaseId).filter { it.propertyId == def.id }) db.values().delete(v.rowPageId, def.id)
            }
        }
    }

    private suspend fun unlinkReverse(targetDb: String, reversePropId: String) {
        val target = schemaOf(targetDb)
        saveSchema(
            targetDb,
            target.copy(properties = target.properties.map {
                if (it.id == reversePropId) it.copy(config = it.config.copy(relationTwoWay = false, relationReversePropertyId = null)) else it
            }),
        )
    }

    private suspend fun enableTwoWay(databaseId: String, prop: PropertyDef) {
        val targetDb = prop.config.relationDatabaseId ?: return
        val ownTitle = db.databases().get(databaseId)?.let { pages.get(it.pageId)?.title }?.ifBlank { "Untitled" } ?: "Untitled"
        val reverseId = Ids.new()
        val target = schemaOf(targetDb)
        val reverse = PropertyDef(
            id = reverseId, name = uniqueName(target, "Related to $ownTitle"), type = PropertyType.RELATION,
            config = PropertyConfig(relationDatabaseId = databaseId, relationTwoWay = true, relationReversePropertyId = prop.id),
        )
        if (targetDb == databaseId) {
            val s = schemaOf(databaseId)
            saveSchema(databaseId, s.copy(properties = s.properties.map {
                if (it.id == prop.id) it.copy(config = it.config.copy(relationReversePropertyId = reverseId)) else it
            } + reverse))
        } else {
            saveSchema(targetDb, target.copy(properties = target.properties + reverse))
            val s = schemaOf(databaseId)
            saveSchema(databaseId, s.copy(properties = s.properties.map {
                if (it.id == prop.id) it.copy(config = it.config.copy(relationReversePropertyId = reverseId)) else it
            }))
        }
        // Fill the reverse side from existing relations.
        val linked = schemaOf(databaseId).property(prop.id) ?: return
        for (v in db.values().forDatabase(databaseId).filter { it.propertyId == prop.id }) {
            val rel = decodeValue(v.valueJson) as? PropertyValue.Relation ?: continue
            syncReverse(linked, v.rowPageId, emptySet(), rel.rowIds.toSet())
        }
    }

    suspend fun deleteProperty(databaseId: String, propertyId: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val schema = schemaOf(databaseId)
            val prop = schema.property(propertyId) ?: return@withTransaction
            if (prop.isTitle) return@withTransaction
            saveSchema(databaseId, schema.copy(properties = schema.properties.filter { it.id != propertyId }))
            db.values().deleteProperty(databaseId, propertyId)
            if (prop.type == PropertyType.RELATION && prop.config.relationTwoWay) {
                val target = prop.config.relationDatabaseId
                val rid = prop.config.relationReversePropertyId
                if (target != null && rid != null) unlinkReverse(target, rid)
            }
            // Drop the property from every view's configuration.
            for (v in db.views().byDatabase(databaseId).map { it.toModel() }) {
                val c = v.config
                val cleaned = c.copy(
                    sorts = c.sorts.filter { it.propertyId != propertyId },
                    groupBy = c.groupBy?.takeIf { it != propertyId },
                    visibleProperties = c.visibleProperties?.filter { it != propertyId },
                    propertyOrder = c.propertyOrder.filter { it != propertyId },
                    calendarPropertyId = c.calendarPropertyId?.takeIf { it != propertyId },
                    filter = removeRules(c.filter, propertyId),
                )
                if (cleaned != c) db.views().insert(v.copy(config = cleaned).toEntity())
            }
        }
    }

    private fun removeRules(g: app.monoworkspace.model.FilterGroup, propertyId: String): app.monoworkspace.model.FilterGroup =
        g.copy(rules = g.rules.filter { it.propertyId != propertyId }, groups = g.groups.map { removeRules(it, propertyId) })

    /** Changes a property's type, converting values. Returns how many values were cleared. */
    suspend fun changeType(databaseId: String, propertyId: String, newType: PropertyType): Int = withContext(Dispatchers.IO) {
        db.withTransaction {
            val schema = schemaOf(databaseId)
            val prop = schema.property(propertyId) ?: return@withTransaction 0
            if (prop.isTitle || prop.type == newType) return@withTransaction 0
            val stored = db.values().forDatabase(databaseId).filter { it.propertyId == propertyId }
                .mapNotNull { e -> decodeValue(e.valueJson)?.let { e.rowPageId to it } }.toMap()
            val result = PropertyConversion.convert(prop, newType, stored)
            var newProp = result.property
            if (newType == PropertyType.RELATION) newProp = newProp.copy(config = newProp.config.copy(relationDatabaseId = databaseId))
            if (newType == PropertyType.FORMULA) newProp = newProp.copy(config = newProp.config.copy(expression = ""))
            saveSchema(databaseId, schema.copy(properties = schema.properties.map { if (it.id == propertyId) newProp else it }))
            for ((rowId, v) in result.values) writeValue(rowId, propertyId, v)
            result.cleared
        }
    }

    suspend fun reorderProperties(databaseId: String, orderedIds: List<String>) = withContext(Dispatchers.IO) {
        val schema = schemaOf(databaseId)
        val byId = schema.properties.associateBy { it.id }
        val ordered = orderedIds.mapNotNull { byId[it] } + schema.properties.filter { it.id !in orderedIds }
        saveSchema(databaseId, schema.copy(properties = ordered))
    }

    /** Adds an option to a select-like property and returns it. */
    suspend fun addOption(databaseId: String, propertyId: String, name: String, group: StatusGroup? = null): SelectOption = withContext(Dispatchers.IO) {
        val schema = schemaOf(databaseId)
        val prop = schema.property(propertyId) ?: throw IllegalStateException("Property not found")
        prop.options.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }?.let { return@withContext it }
        val opt = SelectOption(
            Ids.new(), name.trim().take(100), (prop.options.maxOfOrNull { it.order } ?: -1) + 1,
            if (prop.type == PropertyType.STATUS) group ?: StatusGroup.TODO else null,
        )
        saveSchema(databaseId, schema.copy(properties = schema.properties.map { if (it.id == propertyId) it.copy(options = it.options + opt) else it }))
        opt
    }

    // ---------- Views ----------

    suspend fun addView(databaseId: String, type: ViewType, name: String = type.label): DatabaseView = withContext(Dispatchers.IO) {
        val existing = db.views().byDatabase(databaseId)
        val schema = schemaOf(databaseId)
        val config = when (type) {
            ViewType.BOARD -> ViewConfig(groupBy = schema.properties.firstOrNull { it.type == PropertyType.STATUS || it.type == PropertyType.SELECT }?.id)
            ViewType.CALENDAR -> ViewConfig(calendarPropertyId = schema.properties.firstOrNull { it.type == PropertyType.DATE }?.id)
            else -> ViewConfig()
        }
        val view = DatabaseView(Ids.new(), databaseId, name, type, config, FractionalIndex.between(existing.lastOrNull()?.orderKey, null))
        db.views().insert(view.toEntity())
        view
    }

    suspend fun updateView(view: DatabaseView) = withContext(Dispatchers.IO) { db.views().insert(view.toEntity()) }

    suspend fun deleteView(view: DatabaseView): Boolean = withContext(Dispatchers.IO) {
        if (db.views().byDatabase(view.databaseId).size <= 1) return@withContext false
        db.views().delete(view.toEntity())
        true
    }

    // ---------- CSV import ----------

    suspend fun importCsv(databaseId: String, rows: List<List<String>>, mapping: List<CsvTarget>): Int = withContext(Dispatchers.IO) {
        // Create new properties first.
        val created = HashMap<Int, String>()
        mapping.forEachIndexed { i, t ->
            if (t is CsvTarget.NewProperty) created[i] = addProperty(databaseId, t.name, t.type).id
        }
        var count = 0
        for (r in rows) {
            var title = ""
            val values = LinkedHashMap<String, PropertyValue>()
            for ((i, target) in mapping.withIndex()) {
                val cell = r.getOrNull(i)?.trim().orEmpty()
                when (target) {
                    CsvTarget.Skip -> Unit
                    CsvTarget.Title -> title = cell
                    is CsvTarget.Existing -> parseCell(databaseId, target.propertyId, cell)?.let { values[target.propertyId] = it }
                    is CsvTarget.NewProperty -> created[i]?.let { pid -> parseCell(databaseId, pid, cell)?.let { values[pid] = it } }
                }
            }
            if (title.isEmpty() && values.isEmpty()) continue
            addRow(databaseId, title, values)
            count++
        }
        count
    }

    private suspend fun parseCell(databaseId: String, propertyId: String, cell: String): PropertyValue? {
        if (cell.isEmpty()) return null
        val prop = schemaOf(databaseId).property(propertyId) ?: return null
        return when (prop.type) {
            PropertyType.TEXT, PropertyType.URL, PropertyType.EMAIL, PropertyType.PHONE -> PropertyValue.Text(cell.take(2000))
            PropertyType.NUMBER -> cell.replace(",", "").removeSuffix("%").trim().toDoubleOrNull()?.let { PropertyValue.Number(it) }
            PropertyType.SELECT, PropertyType.STATUS -> PropertyValue.Select(addOption(databaseId, propertyId, cell).id)
            PropertyType.MULTI_SELECT -> PropertyValue.Multi(
                cell.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { addOption(databaseId, propertyId, it).id }.distinct(),
            )
            PropertyType.DATE -> PropertyConversion.parseDate(cell)
            PropertyType.CHECKBOX -> PropertyValue.Checkbox(cell.lowercase() in setOf("yes", "true", "1", "x", "checked", "y"))
            else -> null
        }
    }
}
