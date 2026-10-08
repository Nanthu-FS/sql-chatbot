package app.monoworkspace.data.repo

import androidx.room.withTransaction
import app.monoworkspace.data.MonoJson
import app.monoworkspace.data.db.BlockEntity
import app.monoworkspace.data.db.DatabaseEntity
import app.monoworkspace.data.db.MonoDatabase
import app.monoworkspace.data.db.PageEntity
import app.monoworkspace.data.db.PropertyValueEntity
import app.monoworkspace.data.db.RecentEntity
import app.monoworkspace.data.db.TemplateEntity
import app.monoworkspace.data.db.VersionEntity
import app.monoworkspace.data.db.ViewEntity
import app.monoworkspace.data.toModel
import app.monoworkspace.data.toValueMap
import app.monoworkspace.engine.Csv
import app.monoworkspace.engine.MarkdownExporter
import app.monoworkspace.engine.RelatedDatabase
import app.monoworkspace.engine.RowInput
import app.monoworkspace.engine.RowResolver
import app.monoworkspace.engine.ValueFormat
import app.monoworkspace.model.BlockType
import app.monoworkspace.model.DatabaseView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Everything in the database, as written to db.json inside a .monobackup. */
@Serializable
data class BackupDump(
    val format: Int = FORMAT,
    val schemaVersion: Int = MonoDatabase.VERSION,
    val exportedAt: Long,
    val workspaceName: String,
    val pages: List<PageEntity>,
    val blocks: List<BlockEntity>,
    val databases: List<DatabaseEntity>,
    val views: List<ViewEntity>,
    val values: List<PropertyValueEntity>,
    val versions: List<VersionEntity>,
    val templates: List<TemplateEntity>,
    val recents: List<RecentEntity>,
) {
    companion object {
        const val FORMAT = 1
    }
}

class InvalidBackupException(message: String) : IOException(message)

class ExportRepository(
    private val db: MonoDatabase,
    private val pages: PageRepository,
    private val blocks: BlockRepository,
    private val databases: DatabaseRepository,
    private val search: SearchRepository,
    private val media: MediaRepository,
    private val settings: SettingsRepository,
) {

    private suspend fun dump(): BackupDump = BackupDump(
        exportedAt = System.currentTimeMillis(),
        workspaceName = settings.current().workspaceName,
        pages = db.pages().all(),
        blocks = db.blocks().all(),
        databases = db.databases().all(),
        views = db.views().all(),
        values = db.values().all(),
        versions = db.versions().all(),
        templates = db.templates().all(),
        recents = db.recents().all(),
    )

    // ---------- Backup ----------

    suspend fun exportBackup(out: OutputStream) = withContext(Dispatchers.IO) {
        val data = dump()
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("db.json"))
            zip.write(MonoJson.encodeToString(BackupDump.serializer(), data).toByteArray())
            zip.closeEntry()
            for (f in media.list()) {
                zip.putNextEntry(ZipEntry("media/" + f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Replaces all data with a backup. Nothing changes unless the file parses completely. */
    suspend fun restoreBackup(input: InputStream, cacheDir: File) = withContext(Dispatchers.IO) {
        val staging = File(cacheDir, "restore-" + System.nanoTime()).apply { mkdirs() }
        try {
            var dumpJson: String? = null
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    when {
                        name == "db.json" -> dumpJson = zip.readBytes().toString(Charsets.UTF_8)
                        name.startsWith("media/") && !entry.isDirectory -> {
                            val fileName = name.removePrefix("media/")
                            // Guard against path traversal in hostile archives.
                            if (fileName.isNotEmpty() && !fileName.contains('/') && !fileName.contains("..")) {
                                File(staging, fileName).outputStream().use { zip.copyTo(it) }
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            val json = dumpJson ?: throw InvalidBackupException("This file is not a Mono Workspace backup")
            val data = runCatching { MonoJson.decodeFromString(BackupDump.serializer(), json) }
                .getOrElse { throw InvalidBackupException("The backup could not be read") }
            if (data.format > BackupDump.FORMAT) throw InvalidBackupException("This backup was made by a newer version")

            db.withTransaction {
                db.recents().clear()
                db.versions().clear()
                db.values().clear()
                db.views().clear()
                db.blocks().clear()
                db.databases().clear()
                db.templates().clear()
                db.search().clear()
                db.pages().clear()
                data.pages.chunked(500).forEach { db.pages().insertAll(it) }
                db.databases().insertAll(data.databases)
                db.views().insertAll(data.views)
                data.blocks.chunked(500).forEach { db.blocks().insertAll(it) }
                data.values.chunked(500).forEach { db.values().upsertAll(it) }
                data.versions.chunked(200).forEach { db.versions().insertAll(it) }
                db.templates().insertAll(data.templates)
                db.recents().insertAll(data.recents.filter { r -> data.pages.any { it.id == r.pageId } })
            }
            media.clear()
            staging.listFiles()?.forEach { f -> f.copyTo(media.file(f.name), overwrite = true) }
            settings.setWorkspaceName(data.workspaceName)
            settings.setSeeded(true)
            search.rebuild()
        } finally {
            staging.deleteRecursively()
        }
    }

    // ---------- JSON ----------

    suspend fun exportJson(out: OutputStream) = withContext(Dispatchers.IO) {
        val all = db.pages().all().filter { !it.isTrashed }
        val dbs = db.databases().all()
        val rowsByDb = all.filter { it.databaseId != null }.groupBy { it.databaseId!! }
        val root = buildJsonObject {
            put("workspace", settings.current().workspaceName)
            put("exportedAt", System.currentTimeMillis())
            putJsonArray("pages") {
                for (p in all.filter { it.databaseId == null && !it.isDatabase }) {
                    addJsonObject {
                        put("id", p.id)
                        put("title", p.title)
                        p.icon?.let { put("icon", it) }
                        p.parentId?.let { put("parentId", it) }
                        put("createdAt", p.createdAt)
                        put("editedAt", p.editedAt)
                        put("blocks", blocksJson(p.id))
                    }
                }
            }
            putJsonArray("databases") {
                for (d in dbs) {
                    val page = all.firstOrNull { it.id == d.pageId } ?: continue
                    val model = d.toModel()
                    val values = db.values().forDatabase(d.id).toValueMap()
                    val rows = rowsByDb[d.id].orEmpty()
                    val inputs = rows.map { RowInput(it.id, it.title, it.icon, it.createdAt, it.editedAt, values[it.id].orEmpty(), orderKey = it.orderKey) }
                    val resolver = RowResolver(model.schema, inputs, related = relatedFor(model.schema, d.id), ownDatabaseId = d.id)
                    addJsonObject {
                        put("id", d.id)
                        put("pageId", page.id)
                        put("title", page.title)
                        page.parentId?.let { put("parentId", it) }
                        put("schema", MonoJson.encodeToJsonElement(app.monoworkspace.model.DatabaseSchema.serializer(), model.schema))
                        putJsonArray("views") {
                            db.views().byDatabase(d.id).forEach { v ->
                                addJsonObject {
                                    put("id", v.id)
                                    put("name", v.name)
                                    put("type", v.type)
                                }
                            }
                        }
                        putJsonArray("rows") {
                            for (r in inputs) {
                                addJsonObject {
                                    put("id", r.id)
                                    putJsonObject("values") {
                                        for (prop in model.schema.properties) {
                                            put(prop.name, ValueFormat.export(resolver.cell(r, prop), prop))
                                        }
                                    }
                                    put("blocks", blocksJson(r.id))
                                }
                            }
                        }
                    }
                }
            }
        }
        out.bufferedWriter().use { it.write(MonoJson.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), root)) }
    }

    private suspend fun relatedFor(schema: app.monoworkspace.model.DatabaseSchema, ownId: String): Map<String, RelatedDatabase> =
        databases.loadRelated(schema, ownId)

    private suspend fun blocksJson(pageId: String): JsonArray {
        val list = blocks.load(pageId)
        return buildJsonArray {
            for (b in list) {
                addJsonObject {
                    put("id", b.id)
                    b.parentBlockId?.let { put("parentId", it) }
                    put("type", b.type.name)
                    put("text", b.text)
                    put("content", MonoJson.encodeToJsonElement(kotlinx.serialization.builtins.ListSerializer(app.monoworkspace.model.Span.serializer()), b.content))
                    put("props", MonoJson.encodeToJsonElement(app.monoworkspace.model.BlockProps.serializer(), b.props))
                }
            }
        }
    }

    // ---------- Markdown ----------

    data class MarkdownExport(val fileName: String, val markdown: String, val media: List<String>) {
        val needsZip: Boolean get() = media.isNotEmpty()
    }

    suspend fun pageMarkdown(pageId: String): MarkdownExport = withContext(Dispatchers.IO) {
        val page = pages.get(pageId) ?: throw IOException("Page not found")
        val titles = HashMap<String, String>()
        val list = blocks.load(pageId)
        val refs = list.mapNotNull { it.props.pageId } + list.flatMap { b -> b.content.mapNotNull { it.data?.pageId } }
        db.pages().getMany(refs.distinct()).forEach { titles[it.id] = it.title.ifBlank { "Untitled" } }
        val exporter = MarkdownExporter(pageTitle = { titles[it] ?: "Untitled" })
        val result = exporter.export(page.title, list)
        MarkdownExport(safeName(page.displayTitle), result.markdown, result.media.distinct())
    }

    /** Writes either a bare .md or, when the page has media, a zip with ./media. */
    suspend fun writeMarkdown(export: MarkdownExport, out: OutputStream) = withContext(Dispatchers.IO) {
        if (!export.needsZip) {
            out.bufferedWriter().use { it.write(export.markdown) }
            return@withContext
        }
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(export.fileName + ".md"))
            zip.write(export.markdown.toByteArray())
            zip.closeEntry()
            for (name in export.media) {
                val f = media.file(name)
                if (!f.exists()) continue
                zip.putNextEntry(ZipEntry("media/$name"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    // ---------- Databases ----------

    fun viewCsv(snapshot: DatabaseSnapshot, view: DatabaseView): String {
        val result = DatabaseQuery.run(snapshot, view)
        val props = result.visibleProperties
        val rows = listOf(props.map { it.name }) + result.rows.map { r -> props.map { ValueFormat.export(result.resolver.cell(r, it), it) } }
        return Csv.write(rows)
    }

    /** Zip with the view's CSV plus a Markdown index. */
    suspend fun writeDatabaseExport(snapshot: DatabaseSnapshot, view: DatabaseView, out: OutputStream) = withContext(Dispatchers.IO) {
        val result = DatabaseQuery.run(snapshot, view)
        val props = result.visibleProperties
        val header = props.map { it.name }
        val body = result.rows.map { r -> props.map { ValueFormat.export(result.resolver.cell(r, it), it) } }
        val base = safeName(snapshot.page.displayTitle)
        val csvName = "$base.csv"
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(csvName))
            zip.write(Csv.write(listOf(header) + body).toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("index.md"))
            zip.write(MarkdownExporter.databaseIndex(snapshot.page.displayTitle, csvName, header, body).toByteArray())
            zip.closeEntry()
            // Each row page with content gets its own Markdown file.
            for (r in result.rows) {
                val list = blocks.load(r.id)
                if (list.none { it.type != BlockType.DIVIDER }) continue
                val md = MarkdownExporter().export(r.title, list)
                zip.putNextEntry(ZipEntry("rows/" + safeName(r.title.ifBlank { "Untitled" }) + "-" + r.id.take(8) + ".md"))
                zip.write(md.markdown.toByteArray())
                zip.closeEntry()
            }
        }
    }

    fun safeName(title: String): String =
        title.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ").trim().take(80).ifEmpty { "Untitled" }

    fun mediaBytes(): Long = media.totalBytes()

    fun databaseBytes(dbFile: File): Long = dbFile.length() + File(dbFile.path + "-wal").length() + File(dbFile.path + "-shm").length()
}
