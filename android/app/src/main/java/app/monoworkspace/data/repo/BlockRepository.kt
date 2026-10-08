package app.monoworkspace.data.repo

import androidx.room.withTransaction
import app.monoworkspace.core.Clock
import app.monoworkspace.core.Ids
import app.monoworkspace.data.MonoJson
import app.monoworkspace.data.db.MonoDatabase
import app.monoworkspace.data.db.VersionEntity
import app.monoworkspace.data.encodeSpans
import app.monoworkspace.data.toEntity
import app.monoworkspace.data.toModel
import app.monoworkspace.model.Block
import app.monoworkspace.model.BlockProps
import app.monoworkspace.model.BlockType
import app.monoworkspace.model.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** Serialized form of a block inside a version snapshot. */
@Serializable
data class SnapshotBlock(
    val id: String,
    val parentBlockId: String? = null,
    val orderKey: String,
    val type: BlockType,
    val content: List<Span> = emptyList(),
    val props: BlockProps = BlockProps(),
    val createdAt: Long = 0,
)

data class PageVersion(val id: String, val pageId: String, val createdAt: Long, val title: String, val blockCount: Int)

class BlockRepository(
    private val db: MonoDatabase,
    private val search: SearchRepository,
    private val clock: Clock = Clock.System,
) {
    private val blocks get() = db.blocks()
    private val snapshotSerializer = ListSerializer(SnapshotBlock.serializer())

    suspend fun load(pageId: String): List<Block> = withContext(Dispatchers.IO) { blocks.byPage(pageId).map { it.toModel() } }

    fun observe(pageId: String): Flow<List<Block>> = blocks.observeByPage(pageId).map { list -> list.map { it.toModel() } }

    private suspend fun databaseIdOf(pageId: String): String? = db.pages().get(pageId)?.databaseId

    suspend fun insert(list: List<Block>) = withContext(Dispatchers.IO) {
        if (list.isEmpty()) return@withContext
        db.withTransaction {
            val entities = list.map { it.toEntity() }
            blocks.insertAll(entities)
            val pageId = list.first().pageId
            val dbId = databaseIdOf(pageId)
            entities.forEach { search.indexBlock(it, dbId) }
            db.pages().touch(pageId, clock.now())
        }
    }

    /** Writes text content only; used by the debounced autosave. */
    suspend fun saveContent(updates: Map<String, List<Span>>, pageId: String) = withContext(Dispatchers.IO) {
        if (updates.isEmpty()) return@withContext
        db.withTransaction {
            val now = clock.now()
            val dbId = databaseIdOf(pageId)
            for ((id, spans) in updates) {
                blocks.setContent(id, encodeSpans(spans), now)
                blocks.get(id)?.let { search.indexBlock(it, dbId) }
            }
            db.pages().touch(pageId, now)
        }
    }

    suspend fun update(list: List<Block>) = insert(list)

    suspend fun move(id: String, parentBlockId: String?, orderKey: String, pageId: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val now = clock.now()
            blocks.move(id, parentBlockId, orderKey, now)
            db.pages().touch(pageId, now)
        }
    }

    suspend fun delete(ids: Collection<String>, pageId: String) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        db.withTransaction {
            ids.toList().chunked(500).forEach { blocks.delete(it) }
            ids.forEach { search.removeRef(it) }
            db.pages().touch(pageId, clock.now())
        }
    }

    /** Applies a structural edit (inserts, updates, deletes) atomically. */
    suspend fun apply(pageId: String, upserts: List<Block>, deletes: Collection<String>) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val now = clock.now()
            val dbId = databaseIdOf(pageId)
            if (deletes.isNotEmpty()) {
                deletes.toList().chunked(500).forEach { blocks.delete(it) }
                deletes.forEach { search.removeRef(it) }
            }
            if (upserts.isNotEmpty()) {
                val entities = upserts.map { it.toEntity() }
                blocks.insertAll(entities)
                entities.forEach { search.indexBlock(it, dbId) }
            }
            db.pages().touch(pageId, now)
        }
    }

    // ---------- Versions ----------

    fun observeVersions(pageId: String): Flow<List<PageVersion>> = db.versions().observe(pageId).map { list ->
        list.map { v ->
            val count = runCatching { MonoJson.decodeFromString(snapshotSerializer, v.blocksJson).size }.getOrDefault(0)
            PageVersion(v.id, v.pageId, v.createdAt, v.title, count)
        }
    }

    private fun encodeSnapshot(list: List<Block>): String = MonoJson.encodeToString(
        snapshotSerializer,
        list.map { SnapshotBlock(it.id, it.parentBlockId, it.orderKey, it.type, it.content, it.props, it.createdAt) },
    )

    /** Stores a snapshot unless it is identical to the latest one. Keeps the last 50. */
    suspend fun snapshot(pageId: String, title: String, list: List<Block>): Boolean = withContext(Dispatchers.IO) {
        val json = encodeSnapshot(list)
        val latest = db.versions().latest(pageId)
        if (latest != null && latest.blocksJson == json && latest.title == title) return@withContext false
        db.withTransaction {
            db.versions().insert(VersionEntity(Ids.new(), pageId, clock.now(), title, json))
            db.versions().prune(pageId, MAX_VERSIONS)
        }
        true
    }

    /** Replaces the page's blocks with a snapshot, snapshotting the current state first. */
    suspend fun restoreVersion(versionId: String): String? = withContext(Dispatchers.IO) {
        val version = db.versions().get(versionId) ?: return@withContext null
        val pageId = version.pageId
        val page = db.pages().get(pageId) ?: return@withContext null
        val current = blocks.byPage(pageId).map { it.toModel() }
        snapshot(pageId, page.title, current)
        val restored = runCatching { MonoJson.decodeFromString(snapshotSerializer, version.blocksJson) }.getOrDefault(emptyList())
        val now = clock.now()
        db.withTransaction {
            current.forEach { search.removeRef(it.id) }
            blocks.deleteByPage(pageId)
            val entities = restored.map {
                Block(it.id, pageId, it.parentBlockId, it.orderKey, it.type, it.content, it.props, it.createdAt, now).toEntity()
            }
            blocks.insertAll(entities)
            entities.forEach { search.indexBlock(it, page.databaseId) }
            db.pages().setTitle(pageId, version.title, now)
        }
        search.reindexPage(pageId)
        pageId
    }

    companion object {
        const val MAX_VERSIONS = 50
    }
}
