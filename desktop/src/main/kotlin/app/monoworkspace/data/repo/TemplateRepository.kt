package app.monoworkspace.data.repo

import app.monoworkspace.core.Clock
import app.monoworkspace.core.FractionalIndex
import app.monoworkspace.core.Ids
import app.monoworkspace.data.MonoJson
import app.monoworkspace.data.db.MonoDatabase
import app.monoworkspace.data.db.TemplateEntity
import app.monoworkspace.data.toEntity
import app.monoworkspace.data.toModel
import app.monoworkspace.data.toValueMap
import app.monoworkspace.engine.BlockTree
import app.monoworkspace.model.Block
import app.monoworkspace.model.BlockType
import app.monoworkspace.model.DatabaseSchema
import app.monoworkspace.model.DatabaseView
import app.monoworkspace.model.Page
import app.monoworkspace.model.PropertyValue
import app.monoworkspace.model.Span
import app.monoworkspace.model.SpanKind
import app.monoworkspace.model.TemplateBlock
import app.monoworkspace.model.TemplateDatabase
import app.monoworkspace.model.TemplatePage
import app.monoworkspace.model.TemplatePayload
import app.monoworkspace.model.TemplateRow
import app.monoworkspace.model.TemplateView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class UserTemplate(val id: String, val payload: TemplatePayload, val createdAt: Long)

class TemplateRepository(
    private val db: MonoDatabase,
    private val pages: PageRepository,
    private val blocks: BlockRepository,
    private val databases: DatabaseRepository,
    private val clock: Clock = Clock.System,
) {
    @Volatile private var builtinCache: List<TemplatePayload>? = null

    suspend fun builtins(): List<TemplatePayload> = builtinCache ?: withContext(Dispatchers.IO) {
        val names = Resources.list("templates").filter { it.endsWith(".json") }.sorted()
        names.mapNotNull { readAsset("templates/$it") }.also { builtinCache = it }
    }

    suspend fun readAsset(path: String): TemplatePayload? = withContext(Dispatchers.IO) {
        runCatching {
            Resources.open(path).bufferedReader().use { MonoJson.decodeFromString(TemplatePayload.serializer(), it.readText()) }
        }.getOrNull()
    }

    fun observeUserTemplates(): Flow<List<UserTemplate>> = db.templates().observe().map { list ->
        list.mapNotNull { e ->
            runCatching { UserTemplate(e.id, MonoJson.decodeFromString(TemplatePayload.serializer(), e.payloadJson), e.createdAt) }.getOrNull()
        }
    }

    suspend fun deleteUserTemplate(id: String) = withContext(Dispatchers.IO) { db.templates().delete(id) }

    /** Creates real pages, databases, rows and blocks from a template. Returns the new top page. */
    suspend fun instantiate(payload: TemplatePayload, parentId: String?): Page = instantiate(payload.page, parentId)

    suspend fun instantiate(tp: TemplatePage, parentId: String?): Page = withContext(Dispatchers.IO) {
        val database = tp.database
        val page = if (database != null) {
            val dbPage = databases.createDatabase(parentId, tp.title, tp.icon, DatabaseSchema(database.properties))
            val d = databases.byPage(dbPage.id) ?: throw IllegalStateException("Database not created")
            // Swap the default view for the template's views.
            val defaults = db.views().byDatabase(d.id)
            val keys = FractionalIndex.nBetween(null, null, database.views.size)
            database.views.forEachIndexed { i, v ->
                db.views().insert(DatabaseView(Ids.new(), d.id, v.name, v.type, v.config, keys[i]).toEntity())
            }
            if (database.views.isNotEmpty()) defaults.forEach { db.views().delete(it) }
            for (row in database.rows) {
                val rowPage = databases.addRow(d.id, row.title, row.values, row.icon)
                createBlocks(rowPage.id, row.blocks)
            }
            dbPage
        } else {
            val p = pages.createPage(parentId, tp.title, tp.icon)
            createBlocks(p.id, tp.blocks)
            p
        }
        tp.cover?.let { pages.setCover(page.id, it) }
        pages.get(page.id) ?: page
    }

    private suspend fun createBlocks(pageId: String, list: List<TemplateBlock>) {
        if (list.isEmpty()) return
        val out = ArrayList<Block>()
        val now = clock.now()
        suspend fun build(items: List<TemplateBlock>, parent: String?) {
            val keys = FractionalIndex.nBetween(null, null, items.size)
            items.forEachIndexed { i, tb ->
                val id = Ids.new()
                var props = tb.props
                val childTemplate = tb.page
                if ((tb.type == BlockType.CHILD_PAGE || tb.type == BlockType.CHILD_DATABASE) && childTemplate != null) {
                    val child = instantiate(childTemplate, pageId)
                    val dbId = if (child.isDatabase) databases.byPage(child.id)?.id else null
                    props = props.copy(pageId = child.id, databaseId = dbId)
                }
                if (tb.type == BlockType.TABLE && props.table == null) props = props.copy(table = app.monoworkspace.model.TableData())
                val content = tb.spans().map { s ->
                    // A date mention with start 0 means "the moment the template is used".
                    val data = s.data
                    if (s.kind == SpanKind.DATE_MENTION && data != null && data.start == 0L) {
                        s.copy(text = "@" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy")), data = data.copy(start = now))
                    } else s
                }
                out.add(Block(id, pageId, parent, keys[i], tb.type, content, props, now, now))
                build(tb.children, id)
            }
        }
        build(list, null)
        blocks.insert(out)
    }

    /** Saves a page (and its subpages, up to three levels) as a reusable template. */
    suspend fun saveAsTemplate(pageId: String, name: String): Boolean = withContext(Dispatchers.IO) {
        val page = pages.get(pageId) ?: return@withContext false
        val tp = snapshotPage(page, 0) ?: return@withContext false
        val payload = TemplatePayload(name = name.ifBlank { page.displayTitle }, description = "Saved from ${page.displayTitle}", glyph = page.icon ?: "◆", page = tp)
        db.templates().insert(TemplateEntity(Ids.new(), payload.name, MonoJson.encodeToString(TemplatePayload.serializer(), payload), clock.now()))
        true
    }

    private suspend fun snapshotPage(page: Page, depth: Int): TemplatePage? {
        if (depth > 3) return null
        if (page.isDatabase) {
            val d = databases.byPage(page.id) ?: return null
            val views = db.views().byDatabase(d.id).map { it.toModel() }
            val rows = databases.rowsOf(d.id)
            val values = db.values().forDatabase(d.id).toValueMap()
            val keepTypes = d.schema.properties.filter { it.type != app.monoworkspace.model.PropertyType.RELATION && it.type != app.monoworkspace.model.PropertyType.ROLLUP }
            val schema = keepTypes
            val keepIds = schema.map { it.id }.toSet()
            return TemplatePage(
                title = page.title, icon = page.icon, cover = page.cover,
                database = TemplateDatabase(
                    properties = schema,
                    views = views.map { TemplateView(it.name, it.type, it.config) },
                    rows = rows.map { r ->
                        TemplateRow(
                            title = r.title, icon = r.icon,
                            values = values[r.id].orEmpty().filter { (k, v) -> k in keepIds && v !is PropertyValue.Relation },
                            blocks = blockTree(blocks.load(r.id), depth + 1),
                        )
                    },
                ),
            )
        }
        return TemplatePage(page.title, page.icon, page.cover, blockTree(blocks.load(page.id), depth))
    }

    private suspend fun blockTree(list: List<Block>, depth: Int): List<TemplateBlock> {
        val map = BlockTree.childrenMap(list)
        suspend fun build(parent: String?): List<TemplateBlock> = map[parent].orEmpty().map { b ->
            var child: TemplatePage? = null
            val childId = b.props.pageId
            if ((b.type == BlockType.CHILD_PAGE || b.type == BlockType.CHILD_DATABASE) && childId != null) {
                child = pages.get(childId)?.takeIf { !it.isTrashed }?.let { snapshotPage(it, depth + 1) }
            }
            TemplateBlock(
                type = b.type,
                content = b.content.map { s: Span -> if (s.kind == SpanKind.PAGE_MENTION) s.copy(kind = SpanKind.TEXT, data = null) else s },
                props = b.props.copy(pageId = null, databaseId = null),
                children = build(b.id),
                page = child,
            )
        }.filter { !((it.type == BlockType.CHILD_PAGE || it.type == BlockType.CHILD_DATABASE) && it.page == null) }
        return build(null)
    }
}

/** Classpath assets (templates, seed) bundled from the Android project's assets folder. */
object Resources {
    fun open(path: String): java.io.InputStream =
        Resources::class.java.classLoader.getResourceAsStream(path) ?: throw java.io.FileNotFoundException(path)

    /** File names directly inside a resource folder, from a jar or an exploded classes directory. */
    fun list(folder: String): List<String> {
        val url = Resources::class.java.classLoader.getResource(folder) ?: return emptyList()
        return when (url.protocol) {
            "file" -> java.io.File(url.toURI()).listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
            "jar" -> {
                val conn = url.openConnection() as java.net.JarURLConnection
                conn.useCaches = false
                conn.jarFile.use { jar ->
                    jar.entries().asSequence().map { it.name }
                        .filter { it.startsWith("$folder/") && !it.endsWith("/") && it.count { c -> c == '/' } == 1 }
                        .map { it.substringAfter('/') }.toList()
                }
            }
            else -> emptyList()
        }
    }
}
