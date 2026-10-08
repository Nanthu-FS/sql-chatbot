package app.monoworkspace.data.repo

import androidx.room.withTransaction
import app.monoworkspace.data.db.BlockEntity
import app.monoworkspace.data.db.MonoDatabase
import app.monoworkspace.data.db.PageEntity
import app.monoworkspace.data.db.SearchFts
import app.monoworkspace.data.decodeProps
import app.monoworkspace.data.decodeSpans
import app.monoworkspace.data.decodeValue
import app.monoworkspace.data.toModel
import app.monoworkspace.model.BlockType
import app.monoworkspace.model.PropertyType
import app.monoworkspace.model.PropertyValue
import app.monoworkspace.model.RichText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class SearchKind(val label: String) { PAGE("Pages"), ROW("Rows"), DATABASE("Databases"), BLOCK("Blocks") }

data class SearchFilters(
    val kinds: Set<SearchKind> = emptySet(),
    /** Minimum editedAt, epoch millis. */
    val modifiedAfter: Long? = null,
    val modifiedBefore: Long? = null,
    val databaseId: String? = null,
)

data class SearchHit(
    val kind: SearchKind,
    val refId: String,
    val pageId: String,
    val pageTitle: String,
    val pageIcon: String?,
    val snippet: String,
    val editedAt: Long,
    val titleMatch: Boolean,
)

/** Keeps the FTS4 index in step with writes and answers queries. */
class SearchRepository(private val db: MonoDatabase) {
    private val dao get() = db.search()

    suspend fun indexPage(page: PageEntity) {
        dao.deleteRef(page.id)
        if (page.isTrashed) return
        val kind = when {
            page.isDatabase -> SearchKind.DATABASE
            page.databaseId != null -> SearchKind.ROW
            else -> SearchKind.PAGE
        }
        val body = if (kind == SearchKind.ROW) rowValuesText(page) else ""
        dao.insert(SearchFts(refId = page.id, kind = kind.name, pageId = page.id, databaseId = page.databaseId.orEmpty(), title = page.title, body = body, editedAt = page.editedAt.toString()))
    }

    suspend fun reindexPage(pageId: String) {
        val page = db.pages().get(pageId) ?: run { dao.deleteRef(pageId); return }
        indexPage(page)
    }

    private suspend fun rowValuesText(page: PageEntity): String {
        val dbId = page.databaseId ?: return ""
        val schema = db.databases().get(dbId)?.toModel()?.schema ?: return ""
        val values = db.values().forRow(page.id)
        return values.mapNotNull { e ->
            val prop = schema.property(e.propertyId) ?: return@mapNotNull null
            when (val v = decodeValue(e.valueJson)) {
                is PropertyValue.Text -> if (prop.type == PropertyType.TEXT || prop.type == PropertyType.URL) v.value else null
                is PropertyValue.Select -> prop.option(v.optionId)?.name
                is PropertyValue.Multi -> v.optionIds.mapNotNull { prop.option(it)?.name }.joinToString(" ")
                else -> null
            }
        }.joinToString(" • ")
    }

    suspend fun indexBlock(block: BlockEntity, databaseId: String?) {
        dao.deleteRef(block.id)
        val text = blockText(block)
        if (text.isBlank()) return
        dao.insert(SearchFts(refId = block.id, kind = SearchKind.BLOCK.name, pageId = block.pageId, databaseId = databaseId.orEmpty(), title = "", body = text, editedAt = block.updatedAt.toString()))
    }

    suspend fun removeRef(id: String) = dao.deleteRef(id)

    suspend fun removePage(pageId: String) = dao.deletePage(pageId)

    fun blockText(block: BlockEntity): String {
        val type = runCatching { BlockType.valueOf(block.type) }.getOrDefault(BlockType.TEXT)
        val props = decodeProps(block.propsJson)
        val parts = ArrayList<String>()
        parts.add(RichText.plain(decodeSpans(block.contentJson)))
        when (type) {
            BlockType.IMAGE -> parts.add(RichText.plain(props.caption))
            BlockType.FILE -> props.fileName?.let(parts::add)
            BlockType.LINK_PREVIEW -> listOfNotNull(props.title, props.description, props.url).forEach(parts::add)
            BlockType.TABLE -> props.table?.let { t ->
                parts.add(t.columns.joinToString(" ") { it.name })
                t.rows.forEach { parts.add(it.joinToString(" ")) }
            }
            else -> Unit
        }
        return parts.filter { it.isNotBlank() }.joinToString("\n")
    }

    suspend fun rebuild() = withContext(Dispatchers.IO) {
        db.withTransaction {
            dao.clear()
            val pages = db.pages().all().filter { !it.isTrashed }
            val byId = pages.associateBy { it.id }
            pages.forEach { indexPage(it) }
            db.blocks().all().forEach { b ->
                val page = byId[b.pageId] ?: return@forEach
                indexBlock(b, page.databaseId)
            }
        }
    }

    /** Builds an FTS4 MATCH expression: exact tokens, prefix match on the last one. */
    fun matchExpression(query: String): String? {
        val tokens = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        return tokens.mapIndexed { i, t -> if (i == tokens.lastIndex) "$t*" else "\"$t\"" }.joinToString(" ")
    }

    suspend fun search(query: String, filters: SearchFilters): List<SearchHit> = withContext(Dispatchers.IO) {
        val match = matchExpression(query) ?: return@withContext emptyList()
        val docs = runCatching { dao.match(match) }.getOrDefault(emptyList())
        if (docs.isEmpty()) return@withContext emptyList()
        val pages = db.pages().getMany(docs.map { it.pageId }.distinct()).associateBy { it.id }
        val tokens = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
        docs.mapNotNull { d ->
            val page = pages[d.pageId] ?: return@mapNotNull null
            if (page.isTrashed) return@mapNotNull null
            val kind = runCatching { SearchKind.valueOf(d.kind) }.getOrNull() ?: return@mapNotNull null
            val edited = d.editedAt.toLongOrNull() ?: page.editedAt
            if (filters.kinds.isNotEmpty() && kind !in filters.kinds) return@mapNotNull null
            if (filters.modifiedAfter != null && edited < filters.modifiedAfter) return@mapNotNull null
            if (filters.modifiedBefore != null && edited > filters.modifiedBefore) return@mapNotNull null
            if (filters.databaseId != null) {
                val inDb = page.databaseId == filters.databaseId ||
                    (page.isDatabase && db.databases().byPage(page.id)?.id == filters.databaseId)
                if (!inDb) return@mapNotNull null
            }
            val titleLower = page.title.lowercase()
            val titleMatch = kind != SearchKind.BLOCK && tokens.all { t -> titleLower.split(Regex("[^\\p{L}\\p{N}]+")).any { it.startsWith(t) } }
            SearchHit(
                kind = kind,
                refId = d.refId,
                pageId = d.pageId,
                pageTitle = page.title.ifBlank { "Untitled" },
                pageIcon = page.icon,
                snippet = snippet(if (kind == SearchKind.BLOCK || kind == SearchKind.ROW) d.body else "", tokens),
                editedAt = edited,
                titleMatch = titleMatch,
            )
        }.sortedWith(compareByDescending<SearchHit> { it.titleMatch }.thenByDescending { it.editedAt })
    }

    private fun snippet(body: String, tokens: List<String>): String {
        if (body.isEmpty()) return ""
        val lower = body.lowercase()
        val idx = tokens.mapNotNull { t -> lower.indexOf(t).takeIf { it >= 0 } }.minOrNull() ?: 0
        val start = (idx - 40).coerceAtLeast(0)
        val end = (idx + 100).coerceAtMost(body.length)
        return (if (start > 0) "…" else "") + body.substring(start, end).replace('\n', ' ') + if (end < body.length) "…" else ""
    }
}
