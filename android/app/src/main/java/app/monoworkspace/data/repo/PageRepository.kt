package app.monoworkspace.data.repo

import androidx.room.withTransaction
import app.monoworkspace.core.Clock
import app.monoworkspace.core.FractionalIndex
import app.monoworkspace.core.Ids
import app.monoworkspace.data.db.MonoDatabase
import app.monoworkspace.data.db.PageEntity
import app.monoworkspace.data.db.RecentEntity
import app.monoworkspace.data.decodeProps
import app.monoworkspace.data.decodeSpans
import app.monoworkspace.data.decodeValue
import app.monoworkspace.data.toModel
import app.monoworkspace.model.Page
import app.monoworkspace.model.PropertyValue
import app.monoworkspace.model.SpanKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class Backlinks(val mentionedIn: List<Page> = emptyList(), val relatedRows: List<Page> = emptyList()) {
    val isEmpty: Boolean get() = mentionedIn.isEmpty() && relatedRows.isEmpty()
}

class CircularNestingException : IllegalArgumentException("A page cannot be moved inside itself")

class PageRepository(
    private val db: MonoDatabase,
    private val search: SearchRepository,
    private val media: MediaRepository,
    private val clock: Clock = Clock.System,
) {
    private val pages get() = db.pages()

    fun observe(id: String): Flow<Page?> = pages.observe(id).map { it?.toModel() }
    fun observeTree(): Flow<List<Page>> = pages.observeTree().map { list -> list.map { it.toModel() } }
    fun observeFavorites(): Flow<List<Page>> = pages.observeFavorites().map { list -> list.map { it.toModel() } }
    fun observeRecents(): Flow<List<Page>> = db.recents().observe().map { list -> list.map { it.toModel() } }
    fun observeTrash(): Flow<List<Page>> = pages.observeTrash().map { list -> list.map { it.toModel() } }
    fun observeChildren(id: String): Flow<List<Page>> = pages.observeChildren(id).map { list -> list.map { it.toModel() } }
    fun observeAll(): Flow<List<Page>> = pages.observeAllLive().map { list -> list.map { it.toModel() } }

    suspend fun get(id: String): Page? = pages.get(id)?.toModel()

    suspend fun createPage(parentId: String?, title: String = "", icon: String? = null): Page = withContext(Dispatchers.IO) {
        db.withTransaction {
            val now = clock.now()
            val key = FractionalIndex.between(pages.lastChildKey(parentId), null)
            val entity = PageEntity(
                id = Ids.new(), parentId = parentId, databaseId = null, isDatabase = false, title = title, icon = icon,
                cover = null, orderKey = key, isFavorite = false, favoriteOrder = null, isTrashed = false,
                trashRoot = false, trashedAt = null, createdAt = now, editedAt = now,
            )
            pages.insert(entity)
            search.indexPage(entity)
            entity.toModel()
        }
    }

    suspend fun rename(id: String, title: String) = withContext(Dispatchers.IO) {
        pages.setTitle(id, title.take(500), clock.now())
        search.reindexPage(id)
    }

    suspend fun setIcon(id: String, icon: String?) = withContext(Dispatchers.IO) { pages.setIcon(id, icon, clock.now()) }
    suspend fun setCover(id: String, cover: String?) = withContext(Dispatchers.IO) { pages.setCover(id, cover, clock.now()) }
    suspend fun touch(id: String) = withContext(Dispatchers.IO) { pages.touch(id, clock.now()) }

    suspend fun setFavorite(id: String, favorite: Boolean) = withContext(Dispatchers.IO) {
        val key = if (favorite) FractionalIndex.between(pages.lastFavoriteKey(), null) else null
        pages.setFavorite(id, favorite, key)
    }

    /** Moves a favorite one step up or down in the favorites list. */
    suspend fun moveFavorite(favorites: List<Page>, id: String, up: Boolean) = withContext(Dispatchers.IO) {
        val list = favorites.filter { it.favoriteOrder != null }
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return@withContext
        val key = if (up) {
            if (i == 0) return@withContext
            FractionalIndex.between(list.getOrNull(i - 2)?.favoriteOrder, list[i - 1].favoriteOrder)
        } else {
            if (i == list.lastIndex) return@withContext
            FractionalIndex.between(list[i + 1].favoriteOrder, list.getOrNull(i + 2)?.favoriteOrder)
        }
        pages.setFavoriteOrder(id, key)
    }

    suspend fun visit(id: String) = withContext(Dispatchers.IO) {
        db.recents().upsert(RecentEntity(id, clock.now()))
        db.recents().prune(10)
    }

    suspend fun breadcrumb(id: String): List<Page> = withContext(Dispatchers.IO) {
        val out = ArrayList<Page>()
        var cur = pages.get(id)
        val seen = HashSet<String>()
        while (cur != null && seen.add(cur.id)) {
            out.add(0, cur.toModel())
            cur = cur.parentId?.let { pages.get(it) }
        }
        out
    }

    /** True if [ancestorId] is [id] or one of its ancestors. */
    private suspend fun isAncestorOrSelf(ancestorId: String, id: String?): Boolean {
        var cur = id
        val seen = HashSet<String>()
        while (cur != null && seen.add(cur)) {
            if (cur == ancestorId) return true
            cur = pages.get(cur)?.parentId
        }
        return false
    }

    /** Moves a page under a new parent. Rejects circular nesting. */
    suspend fun move(id: String, newParentId: String?) = withContext(Dispatchers.IO) {
        if (newParentId != null && isAncestorOrSelf(id, newParentId)) throw CircularNestingException()
        val page = pages.get(id) ?: return@withContext
        if (page.databaseId != null) throw IllegalArgumentException("Rows stay inside their database")
        val key = FractionalIndex.between(pages.lastChildKey(newParentId), null)
        pages.move(id, newParentId, key, clock.now())
    }

    /** Collects a page and everything under it: subpages, databases and their rows. */
    suspend fun subtree(id: String): List<PageEntity> {
        val out = ArrayList<PageEntity>()
        val root = pages.get(id) ?: return out
        val stack = ArrayDeque<PageEntity>()
        stack.add(root)
        val seen = HashSet<String>()
        while (stack.isNotEmpty()) {
            val p = stack.removeLast()
            if (!seen.add(p.id)) continue
            out.add(p)
            pages.children(p.id).forEach { stack.add(it) }
            if (p.isDatabase) {
                db.databases().byPage(p.id)?.let { d -> pages.allRows(d.id).forEach { stack.add(it) } }
            }
        }
        return out
    }

    suspend fun trash(id: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val now = clock.now()
            val tree = subtree(id)
            tree.forEach { p ->
                // Items trashed earlier on their own keep their own trash entry.
                if (p.id == id || !p.isTrashed) {
                    pages.setTrashed(p.id, true, p.id == id, now)
                }
                search.removePage(p.id)
            }
        }
    }

    suspend fun restore(id: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val root = pages.get(id) ?: return@withTransaction
            val at = root.trashedAt
            subtree(id).forEach { p ->
                if (p.id == id || (p.isTrashed && !p.trashRoot && p.trashedAt == at)) {
                    pages.setTrashed(p.id, false, false, null)
                }
            }
            // Return to the original parent if it is still there, otherwise to the root.
            val parent = root.parentId?.let { pages.get(it) }
            if (root.databaseId != null) {
                val database = db.databases().get(root.databaseId)
                val dbPage = database?.let { pages.get(it.pageId) }
                if (dbPage == null || dbPage.isTrashed) {
                    pages.setDatabase(id, null, null)
                    pages.move(id, null, FractionalIndex.between(pages.lastChildKey(null), null), clock.now())
                }
            } else if (root.parentId != null && (parent == null || parent.isTrashed)) {
                pages.move(id, null, FractionalIndex.between(pages.lastChildKey(null), null), clock.now())
            }
        }
        reindexSubtree(id)
    }

    private suspend fun reindexSubtree(id: String) {
        for (p in subtree(id)) {
            if (p.isTrashed) continue
            search.indexPage(p)
            db.blocks().byPage(p.id).forEach { search.indexBlock(it, p.databaseId) }
        }
    }

    /** Permanently deletes a page subtree and any media only it referenced. */
    suspend fun deleteForever(id: String) = withContext(Dispatchers.IO) {
        val doomedMedia = HashSet<String>()
        db.withTransaction {
            val tree = subtree(id)
            val ids = tree.map { it.id }
            for (p in tree) {
                db.blocks().byPage(p.id).forEach { b ->
                    val props = decodeProps(b.propsJson)
                    props.mediaPath?.let(doomedMedia::add)
                }
                db.values().forRow(p.id).forEach { v ->
                    (decodeValue(v.valueJson) as? PropertyValue.Files)?.files?.forEach { doomedMedia.add(it.path) }
                }
                p.cover?.takeIf { it.startsWith(app.monoworkspace.model.Covers.IMAGE_PREFIX) }
                    ?.let { doomedMedia.add(it.removePrefix(app.monoworkspace.model.Covers.IMAGE_PREFIX)) }
                search.removePage(p.id)
            }
            ids.chunked(500).forEach { pages.delete(it) }
        }
        // Keep files another page still uses (for example after duplication).
        val stillUsed = referencedMedia()
        media.delete(doomedMedia - stillUsed)
    }

    suspend fun referencedMedia(): Set<String> {
        val out = HashSet<String>()
        db.blocks().mediaRefs().forEach { r -> decodeProps(r.propsJson).mediaPath?.let(out::add) }
        db.values().all().forEach { v -> (decodeValue(v.valueJson) as? PropertyValue.Files)?.files?.forEach { out.add(it.path) } }
        pages.all().forEach { p -> p.cover?.takeIf { it.startsWith(app.monoworkspace.model.Covers.IMAGE_PREFIX) }?.let { out.add(it.removePrefix(app.monoworkspace.model.Covers.IMAGE_PREFIX)) } }
        return out
    }

    /** Deletes trash older than 30 days. Returns how many trash entries were purged. */
    suspend fun purgeExpired(now: Long = clock.now()): Int = withContext(Dispatchers.IO) {
        val cutoff = now - TRASH_RETENTION_MS
        val expired = pages.trashRootsBefore(cutoff)
        expired.forEach { deleteForever(it.id) }
        expired.size
    }

    suspend fun backlinks(id: String): Backlinks = withContext(Dispatchers.IO) {
        val mentioning = LinkedHashSet<String>()
        for (b in db.blocks().referencing(id)) {
            if (b.pageId == id) continue
            val spans = decodeSpans(b.contentJson)
            val props = decodeProps(b.propsJson)
            val mentions = spans.any { it.kind == SpanKind.PAGE_MENTION && it.data?.pageId == id }
            if (mentions || props.pageId == id) mentioning.add(b.pageId)
        }
        val related = LinkedHashSet<String>()
        for (v in db.values().referencing(id)) {
            val rel = decodeValue(v.valueJson) as? PropertyValue.Relation ?: continue
            if (id in rel.rowIds && v.rowPageId != id) related.add(v.rowPageId)
        }
        val all = pages.getMany((mentioning + related).toList()).filter { !it.isTrashed }.associateBy { it.id }
        Backlinks(
            mentionedIn = mentioning.mapNotNull { all[it]?.toModel() },
            relatedRows = related.mapNotNull { all[it]?.toModel() },
        )
    }

    companion object {
        const val TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}
