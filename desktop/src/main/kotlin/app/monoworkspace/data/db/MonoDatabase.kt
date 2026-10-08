package app.monoworkspace.data.db

import app.monoworkspace.data.MonoJson
import app.monoworkspace.data.repo.BackupDump
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Every table as an immutable snapshot; each write swaps in a new one. */
data class Tables(
    val pages: Map<String, PageEntity> = emptyMap(),
    val blocks: Map<String, BlockEntity> = emptyMap(),
    val databases: Map<String, DatabaseEntity> = emptyMap(),
    val views: Map<String, ViewEntity> = emptyMap(),
    val values: Map<Pair<String, String>, PropertyValueEntity> = emptyMap(),
    val versions: Map<String, VersionEntity> = emptyMap(),
    val templates: Map<String, TemplateEntity> = emptyMap(),
    val recents: Map<String, RecentEntity> = emptyMap(),
    val search: List<SearchFts> = emptyList(),
)

/**
 * Desktop storage: the whole workspace lives in memory and is written to
 * `db.json` (the same shape as a backup's db.json) half a second after the
 * last change, atomically. The DAO surface mirrors the Android Room DAOs so
 * the shared repositories run unchanged.
 */
class MonoDatabase(private val dir: File, scope: CoroutineScope) {
    val state = MutableStateFlow(Tables())
    private val file = File(dir, NAME)

    init {
        dir.mkdirs()
        load()
        state.drop(1)
            .debounce(500)
            .onEach { save(it) }
            .launchIn(CoroutineScope(scope.coroutineContext + Dispatchers.IO))
    }

    private fun load() {
        if (!file.exists()) return
        val dump = runCatching { MonoJson.decodeFromString(BackupDump.serializer(), file.readText()) }.getOrNull()
            ?: run {
                // Keep a corrupt file aside instead of overwriting it.
                file.copyTo(File(dir, "db.corrupt-${System.currentTimeMillis()}.json"), overwrite = true)
                return
            }
        state.value = Tables(
            pages = dump.pages.associateBy { it.id },
            blocks = dump.blocks.associateBy { it.id },
            databases = dump.databases.associateBy { it.id },
            views = dump.views.associateBy { it.id },
            values = dump.values.associateBy { it.rowPageId to it.propertyId },
            versions = dump.versions.associateBy { it.id },
            templates = dump.templates.associateBy { it.id },
            recents = dump.recents.associateBy { it.pageId },
        )
    }

    @Synchronized
    fun save(t: Tables = state.value) {
        val dump = BackupDump(
            exportedAt = System.currentTimeMillis(),
            workspaceName = "",
            pages = t.pages.values.toList(),
            blocks = t.blocks.values.toList(),
            databases = t.databases.values.toList(),
            views = t.views.values.toList(),
            values = t.values.values.toList(),
            versions = t.versions.values.toList(),
            templates = t.templates.values.toList(),
            recents = t.recents.values.toList(),
        )
        val tmp = File(dir, "$NAME.tmp")
        tmp.writeText(MonoJson.encodeToString(BackupDump.serializer(), dump))
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun sizeBytes(): Long = if (file.exists()) file.length() else 0L

    private val pageDao = PageDao(state)
    private val blockDao = BlockDao(state)
    private val databaseDao = DatabaseDao(state)
    private val viewDao = ViewDao(state)
    private val valueDao = ValueDao(state)
    private val versionDao = VersionDao(state)
    private val templateDao = TemplateDao(state)
    private val recentDao = RecentDao(state)
    private val searchDao = SearchDao(state)

    fun pages() = pageDao
    fun blocks() = blockDao
    fun databases() = databaseDao
    fun views() = viewDao
    fun values() = valueDao
    fun versions() = versionDao
    fun templates() = templateDao
    fun recents() = recentDao
    fun search() = searchDao

    companion object {
        const val NAME = "db.json"
        const val VERSION = 1
    }
}

private fun <T> MutableStateFlow<Tables>.watch(select: (Tables) -> T): Flow<T> = map(select).distinctUntilChanged()

class PageDao(private val s: MutableStateFlow<Tables>) {
    private val pages get() = s.value.pages.values
    private fun edit(id: String, f: (PageEntity) -> PageEntity) = s.update { t ->
        val p = t.pages[id] ?: return@update t
        t.copy(pages = t.pages + (id to f(p)))
    }

    suspend fun get(id: String): PageEntity? = s.value.pages[id]
    fun observe(id: String): Flow<PageEntity?> = s.watch { it.pages[id] }
    suspend fun getMany(ids: List<String>): List<PageEntity> = ids.mapNotNull { s.value.pages[it] }
    fun observeTree(): Flow<List<PageEntity>> = s.watch { t -> t.pages.values.filter { !it.isTrashed && it.databaseId == null }.sortedBy { it.orderKey } }
    fun observeAllLive(): Flow<List<PageEntity>> = s.watch { t -> t.pages.values.filter { !it.isTrashed } }
    fun observeFavorites(): Flow<List<PageEntity>> = s.watch { t -> t.pages.values.filter { it.isFavorite && !it.isTrashed }.sortedBy { it.favoriteOrder } }
    fun observeTrash(): Flow<List<PageEntity>> = s.watch { t -> t.pages.values.filter { it.isTrashed && it.trashRoot }.sortedByDescending { it.trashedAt } }
    suspend fun children(parentId: String): List<PageEntity> = pages.filter { it.parentId == parentId }
    fun observeChildren(parentId: String): Flow<List<PageEntity>> =
        s.watch { t -> t.pages.values.filter { it.parentId == parentId && !it.isTrashed && it.databaseId == null }.sortedBy { it.orderKey } }
    fun observeRows(databaseId: String): Flow<List<PageEntity>> =
        s.watch { t -> t.pages.values.filter { it.databaseId == databaseId && !it.isTrashed }.sortedBy { it.orderKey } }
    suspend fun rows(databaseId: String): List<PageEntity> = pages.filter { it.databaseId == databaseId && !it.isTrashed }.sortedBy { it.orderKey }
    suspend fun allRows(databaseId: String): List<PageEntity> = pages.filter { it.databaseId == databaseId }
    suspend fun all(): List<PageEntity> = pages.toList()
    suspend fun lastChildKey(parentId: String?): String? = pages.filter { it.parentId == parentId && it.databaseId == null }.maxOfOrNull { it.orderKey }
    suspend fun firstChildKey(parentId: String?): String? = pages.filter { it.parentId == parentId && it.databaseId == null }.minOfOrNull { it.orderKey }
    suspend fun lastRowKey(databaseId: String): String? = pages.filter { it.databaseId == databaseId }.maxOfOrNull { it.orderKey }
    suspend fun lastFavoriteKey(): String? = pages.filter { it.isFavorite }.mapNotNull { it.favoriteOrder }.maxOrNull()
    suspend fun trashRootsBefore(cutoff: Long): List<PageEntity> = pages.filter { it.isTrashed && it.trashRoot && (it.trashedAt ?: 0) < cutoff }
    suspend fun count(): Int = s.value.pages.size
    suspend fun insert(page: PageEntity) = s.update { it.copy(pages = it.pages + (page.id to page)) }
    suspend fun insertAll(list: List<PageEntity>) = s.update { t -> t.copy(pages = t.pages + list.associateBy { it.id }) }
    suspend fun update(page: PageEntity) = insert(page)
    suspend fun setTitle(id: String, title: String, at: Long) = edit(id) { it.copy(title = title, editedAt = at) }
    suspend fun setIcon(id: String, icon: String?, at: Long) = edit(id) { it.copy(icon = icon, editedAt = at) }
    suspend fun setCover(id: String, cover: String?, at: Long) = edit(id) { it.copy(cover = cover, editedAt = at) }
    suspend fun setFavorite(id: String, favorite: Boolean, order: String?) = edit(id) { it.copy(isFavorite = favorite, favoriteOrder = order) }
    suspend fun setFavoriteOrder(id: String, order: String) = edit(id) { it.copy(favoriteOrder = order) }
    suspend fun touch(id: String, at: Long) = edit(id) { it.copy(editedAt = at) }
    suspend fun move(id: String, parentId: String?, orderKey: String, at: Long) = edit(id) { it.copy(parentId = parentId, orderKey = orderKey, editedAt = at) }
    suspend fun setOrder(id: String, orderKey: String) = edit(id) { it.copy(orderKey = orderKey) }
    suspend fun setTrashed(id: String, trashed: Boolean, root: Boolean, at: Long?) = edit(id) { it.copy(isTrashed = trashed, trashRoot = root, trashedAt = at) }
    suspend fun setDatabase(id: String, databaseId: String?, parentId: String?) = edit(id) { it.copy(databaseId = databaseId, parentId = parentId) }

    /** Deleting pages cascades to their blocks, values, versions, databases and recents. */
    suspend fun delete(ids: List<String>) = s.update { t ->
        val doomed = ids.toSet()
        val dbIds = t.databases.values.filter { it.pageId in doomed }.map { it.id }.toSet()
        t.copy(
            pages = t.pages - doomed,
            blocks = t.blocks.filterValues { it.pageId !in doomed },
            values = t.values.filterValues { it.rowPageId !in doomed },
            versions = t.versions.filterValues { it.pageId !in doomed },
            databases = t.databases.filterValues { it.id !in dbIds },
            views = t.views.filterValues { it.databaseId !in dbIds },
            recents = t.recents - doomed,
            search = t.search.filter { it.pageId !in doomed },
        )
    }

    suspend fun clear() = s.update { it.copy(pages = emptyMap()) }
}

class BlockDao(private val s: MutableStateFlow<Tables>) {
    private val blocks get() = s.value.blocks.values
    suspend fun byPage(pageId: String): List<BlockEntity> = blocks.filter { it.pageId == pageId }.sortedBy { it.orderKey }
    fun observeByPage(pageId: String): Flow<List<BlockEntity>> = s.watch { t -> t.blocks.values.filter { it.pageId == pageId }.sortedBy { it.orderKey } }
    suspend fun get(id: String): BlockEntity? = s.value.blocks[id]
    suspend fun all(): List<BlockEntity> = blocks.toList()
    suspend fun insert(block: BlockEntity) = s.update { it.copy(blocks = it.blocks + (block.id to block)) }
    suspend fun insertAll(list: List<BlockEntity>) = s.update { t -> t.copy(blocks = t.blocks + list.associateBy { it.id }) }
    suspend fun setContent(id: String, contentJson: String, at: Long) = s.update { t ->
        val b = t.blocks[id] ?: return@update t
        t.copy(blocks = t.blocks + (id to b.copy(contentJson = contentJson, updatedAt = at)))
    }
    suspend fun move(id: String, parentBlockId: String?, orderKey: String, at: Long) = s.update { t ->
        val b = t.blocks[id] ?: return@update t
        t.copy(blocks = t.blocks + (id to b.copy(parentBlockId = parentBlockId, orderKey = orderKey, updatedAt = at)))
    }
    suspend fun delete(ids: List<String>) = s.update { it.copy(blocks = it.blocks - ids.toSet()) }
    suspend fun deleteByPage(pageId: String) = s.update { t -> t.copy(blocks = t.blocks.filterValues { it.pageId != pageId }) }
    suspend fun referencing(pageId: String): List<BlockEntity> = blocks.filter { it.contentJson.contains(pageId) || it.propsJson.contains(pageId) }
    suspend fun imagesFor(pageIds: List<String>): List<BlockPropsRow> {
        val ids = pageIds.toSet()
        return blocks.filter { it.type == "IMAGE" && it.pageId in ids }.sortedBy { it.orderKey }.map { BlockPropsRow(it.pageId, it.propsJson) }
    }
    suspend fun mediaRefs(): List<BlockPropsRow> = blocks.filter { it.propsJson.contains("mediaPath") }.map { BlockPropsRow(it.pageId, it.propsJson) }
    suspend fun count(): Int = s.value.blocks.size
    suspend fun clear() = s.update { it.copy(blocks = emptyMap()) }
}

class DatabaseDao(private val s: MutableStateFlow<Tables>) {
    suspend fun get(id: String): DatabaseEntity? = s.value.databases[id]
    fun observe(id: String): Flow<DatabaseEntity?> = s.watch { it.databases[id] }
    suspend fun byPage(pageId: String): DatabaseEntity? = s.value.databases.values.firstOrNull { it.pageId == pageId }
    fun observeByPage(pageId: String): Flow<DatabaseEntity?> = s.watch { t -> t.databases.values.firstOrNull { it.pageId == pageId } }
    fun observeAllLive(): Flow<List<DatabaseEntity>> = s.watch { t -> t.databases.values.filter { d -> t.pages[d.pageId]?.isTrashed == false } }
    suspend fun all(): List<DatabaseEntity> = s.value.databases.values.toList()
    suspend fun insert(db: DatabaseEntity) = s.update { it.copy(databases = it.databases + (db.id to db)) }
    suspend fun insertAll(dbs: List<DatabaseEntity>) = s.update { t -> t.copy(databases = t.databases + dbs.associateBy { it.id }) }
    suspend fun setSchema(id: String, schemaJson: String) = s.update { t ->
        val d = t.databases[id] ?: return@update t
        t.copy(databases = t.databases + (id to d.copy(schemaJson = schemaJson)))
    }
    suspend fun clear() = s.update { it.copy(databases = emptyMap()) }
}

class ViewDao(private val s: MutableStateFlow<Tables>) {
    fun observe(databaseId: String): Flow<List<ViewEntity>> = s.watch { t -> t.views.values.filter { it.databaseId == databaseId }.sortedBy { it.orderKey } }
    suspend fun byDatabase(databaseId: String): List<ViewEntity> = s.value.views.values.filter { it.databaseId == databaseId }.sortedBy { it.orderKey }
    suspend fun all(): List<ViewEntity> = s.value.views.values.toList()
    suspend fun insert(view: ViewEntity) = s.update { it.copy(views = it.views + (view.id to view)) }
    suspend fun insertAll(list: List<ViewEntity>) = s.update { t -> t.copy(views = t.views + list.associateBy { it.id }) }
    suspend fun delete(view: ViewEntity) = s.update { it.copy(views = it.views - view.id) }
    suspend fun clear() = s.update { it.copy(views = emptyMap()) }
}

class ValueDao(private val s: MutableStateFlow<Tables>) {
    private fun rowsOf(t: Tables, databaseId: String): Set<String> = t.pages.values.filter { it.databaseId == databaseId }.map { it.id }.toSet()
    fun observeForDatabase(databaseId: String): Flow<List<PropertyValueEntity>> = s.watch { t ->
        val rows = rowsOf(t, databaseId)
        t.values.values.filter { it.rowPageId in rows }
    }
    suspend fun forDatabase(databaseId: String): List<PropertyValueEntity> {
        val t = s.value
        val rows = rowsOf(t, databaseId)
        return t.values.values.filter { it.rowPageId in rows }
    }
    fun observeForRow(rowId: String): Flow<List<PropertyValueEntity>> = s.watch { t -> t.values.values.filter { it.rowPageId == rowId } }
    suspend fun forRow(rowId: String): List<PropertyValueEntity> = s.value.values.values.filter { it.rowPageId == rowId }
    suspend fun referencing(id: String): List<PropertyValueEntity> = s.value.values.values.filter { it.valueJson.contains(id) }
    suspend fun all(): List<PropertyValueEntity> = s.value.values.values.toList()
    suspend fun upsert(value: PropertyValueEntity) = s.update { it.copy(values = it.values + ((value.rowPageId to value.propertyId) to value)) }
    suspend fun upsertAll(list: List<PropertyValueEntity>) = s.update { t -> t.copy(values = t.values + list.associateBy { it.rowPageId to it.propertyId }) }
    suspend fun delete(rowId: String, propertyId: String) = s.update { it.copy(values = it.values - (rowId to propertyId)) }
    suspend fun deleteProperty(databaseId: String, propertyId: String) = s.update { t ->
        val rows = rowsOf(t, databaseId)
        t.copy(values = t.values.filterValues { !(it.propertyId == propertyId && it.rowPageId in rows) })
    }
    suspend fun clear() = s.update { it.copy(values = emptyMap()) }
}

class VersionDao(private val s: MutableStateFlow<Tables>) {
    fun observe(pageId: String): Flow<List<VersionEntity>> = s.watch { t -> t.versions.values.filter { it.pageId == pageId }.sortedByDescending { it.createdAt } }
    suspend fun get(id: String): VersionEntity? = s.value.versions[id]
    suspend fun latest(pageId: String): VersionEntity? = s.value.versions.values.filter { it.pageId == pageId }.maxByOrNull { it.createdAt }
    suspend fun all(): List<VersionEntity> = s.value.versions.values.toList()
    suspend fun insert(version: VersionEntity) = s.update { it.copy(versions = it.versions + (version.id to version)) }
    suspend fun insertAll(list: List<VersionEntity>) = s.update { t -> t.copy(versions = t.versions + list.associateBy { it.id }) }
    suspend fun prune(pageId: String, keep: Int) = s.update { t ->
        val drop = t.versions.values.filter { it.pageId == pageId }.sortedByDescending { it.createdAt }.drop(keep).map { it.id }.toSet()
        if (drop.isEmpty()) t else t.copy(versions = t.versions - drop)
    }
    suspend fun clear() = s.update { it.copy(versions = emptyMap()) }
}

class TemplateDao(private val s: MutableStateFlow<Tables>) {
    fun observe(): Flow<List<TemplateEntity>> = s.watch { t -> t.templates.values.sortedByDescending { it.createdAt } }
    suspend fun get(id: String): TemplateEntity? = s.value.templates[id]
    suspend fun all(): List<TemplateEntity> = s.value.templates.values.toList()
    suspend fun insert(template: TemplateEntity) = s.update { it.copy(templates = it.templates + (template.id to template)) }
    suspend fun insertAll(list: List<TemplateEntity>) = s.update { t -> t.copy(templates = t.templates + list.associateBy { it.id }) }
    suspend fun delete(id: String) = s.update { it.copy(templates = it.templates - id) }
    suspend fun clear() = s.update { it.copy(templates = emptyMap()) }
}

class RecentDao(private val s: MutableStateFlow<Tables>) {
    fun observe(): Flow<List<PageEntity>> = s.watch { t ->
        t.recents.values.sortedByDescending { it.visitedAt }.mapNotNull { r -> t.pages[r.pageId]?.takeIf { !it.isTrashed } }.take(10)
    }
    suspend fun upsert(recent: RecentEntity) = s.update { it.copy(recents = it.recents + (recent.pageId to recent)) }
    suspend fun prune(keep: Int) = s.update { t ->
        val keepIds = t.recents.values.sortedByDescending { it.visitedAt }.take(keep).map { it.pageId }.toSet()
        t.copy(recents = t.recents.filterKeys { it in keepIds })
    }
    suspend fun all(): List<RecentEntity> = s.value.recents.values.toList()
    suspend fun insertAll(list: List<RecentEntity>) = s.update { t -> t.copy(recents = t.recents + list.associateBy { it.pageId }) }
    suspend fun clear() = s.update { it.copy(recents = emptyMap()) }
}

/** Token search with FTS4-style syntax: "exact" tokens and a trailing prefix*. */
class SearchDao(private val s: MutableStateFlow<Tables>) {
    suspend fun insert(doc: SearchFts) = s.update { it.copy(search = it.search + doc) }
    suspend fun insertAll(docs: List<SearchFts>) = s.update { it.copy(search = it.search + docs) }
    suspend fun deleteRef(refId: String) = s.update { t -> t.copy(search = t.search.filter { it.refId != refId }) }
    suspend fun deletePage(pageId: String) = s.update { t -> t.copy(search = t.search.filter { it.pageId != pageId }) }
    suspend fun clear() = s.update { it.copy(search = emptyList()) }

    suspend fun match(query: String): List<SearchFts> {
        val terms = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.map { raw ->
            val prefix = raw.endsWith("*")
            raw.trim('"').trimEnd('*').lowercase() to prefix
        }.filter { it.first.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()
        val splitter = Regex("[^\\p{L}\\p{N}]+")
        return s.value.search.asSequence().filter { doc ->
            val tokens = (doc.title + " " + doc.body).lowercase().split(splitter)
            terms.all { (term, prefix) -> tokens.any { if (prefix) it.startsWith(term) else it == term } }
        }.take(300).toList()
    }
}
