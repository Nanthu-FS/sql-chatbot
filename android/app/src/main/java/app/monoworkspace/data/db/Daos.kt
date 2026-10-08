package app.monoworkspace.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PageDao {
    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun get(id: String): PageEntity?

    @Query("SELECT * FROM pages WHERE id = :id")
    fun observe(id: String): Flow<PageEntity?>

    @Query("SELECT * FROM pages WHERE id IN (:ids)")
    suspend fun getMany(ids: List<String>): List<PageEntity>

    @Query("SELECT * FROM pages WHERE isTrashed = 0 AND databaseId IS NULL ORDER BY orderKey")
    fun observeTree(): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE isTrashed = 0")
    fun observeAllLive(): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE isFavorite = 1 AND isTrashed = 0 ORDER BY favoriteOrder")
    fun observeFavorites(): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE isTrashed = 1 AND trashRoot = 1 ORDER BY trashedAt DESC")
    fun observeTrash(): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE parentId = :parentId")
    suspend fun children(parentId: String): List<PageEntity>

    @Query("SELECT * FROM pages WHERE parentId = :parentId AND isTrashed = 0 AND databaseId IS NULL ORDER BY orderKey")
    fun observeChildren(parentId: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE databaseId = :databaseId AND isTrashed = 0 ORDER BY orderKey")
    fun observeRows(databaseId: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE databaseId = :databaseId AND isTrashed = 0 ORDER BY orderKey")
    suspend fun rows(databaseId: String): List<PageEntity>

    @Query("SELECT * FROM pages WHERE databaseId = :databaseId")
    suspend fun allRows(databaseId: String): List<PageEntity>

    @Query("SELECT * FROM pages")
    suspend fun all(): List<PageEntity>

    @Query("SELECT MAX(orderKey) FROM pages WHERE parentId IS :parentId AND databaseId IS NULL")
    suspend fun lastChildKey(parentId: String?): String?

    @Query("SELECT MIN(orderKey) FROM pages WHERE parentId IS :parentId AND databaseId IS NULL")
    suspend fun firstChildKey(parentId: String?): String?

    @Query("SELECT MAX(orderKey) FROM pages WHERE databaseId = :databaseId")
    suspend fun lastRowKey(databaseId: String): String?

    @Query("SELECT MAX(favoriteOrder) FROM pages WHERE isFavorite = 1")
    suspend fun lastFavoriteKey(): String?

    @Query("SELECT * FROM pages WHERE isTrashed = 1 AND trashRoot = 1 AND trashedAt < :cutoff")
    suspend fun trashRootsBefore(cutoff: Long): List<PageEntity>

    @Query("SELECT COUNT(*) FROM pages")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(page: PageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pages: List<PageEntity>)

    @Update
    suspend fun update(page: PageEntity)

    @Query("UPDATE pages SET title = :title, editedAt = :at WHERE id = :id")
    suspend fun setTitle(id: String, title: String, at: Long)

    @Query("UPDATE pages SET icon = :icon, editedAt = :at WHERE id = :id")
    suspend fun setIcon(id: String, icon: String?, at: Long)

    @Query("UPDATE pages SET cover = :cover, editedAt = :at WHERE id = :id")
    suspend fun setCover(id: String, cover: String?, at: Long)

    @Query("UPDATE pages SET isFavorite = :favorite, favoriteOrder = :order WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, order: String?)

    @Query("UPDATE pages SET favoriteOrder = :order WHERE id = :id")
    suspend fun setFavoriteOrder(id: String, order: String)

    @Query("UPDATE pages SET editedAt = :at WHERE id = :id")
    suspend fun touch(id: String, at: Long)

    @Query("UPDATE pages SET parentId = :parentId, orderKey = :orderKey, editedAt = :at WHERE id = :id")
    suspend fun move(id: String, parentId: String?, orderKey: String, at: Long)

    @Query("UPDATE pages SET orderKey = :orderKey WHERE id = :id")
    suspend fun setOrder(id: String, orderKey: String)

    @Query("UPDATE pages SET isTrashed = :trashed, trashRoot = :root, trashedAt = :at WHERE id = :id")
    suspend fun setTrashed(id: String, trashed: Boolean, root: Boolean, at: Long?)

    @Query("UPDATE pages SET databaseId = :databaseId, parentId = :parentId WHERE id = :id")
    suspend fun setDatabase(id: String, databaseId: String?, parentId: String?)

    @Query("DELETE FROM pages WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("DELETE FROM pages")
    suspend fun clear()
}

@Dao
interface BlockDao {
    @Query("SELECT * FROM blocks WHERE pageId = :pageId ORDER BY orderKey")
    suspend fun byPage(pageId: String): List<BlockEntity>

    @Query("SELECT * FROM blocks WHERE pageId = :pageId ORDER BY orderKey")
    fun observeByPage(pageId: String): Flow<List<BlockEntity>>

    @Query("SELECT * FROM blocks WHERE id = :id")
    suspend fun get(id: String): BlockEntity?

    @Query("SELECT * FROM blocks")
    suspend fun all(): List<BlockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(block: BlockEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(blocks: List<BlockEntity>)

    @Query("UPDATE blocks SET contentJson = :contentJson, updatedAt = :at WHERE id = :id")
    suspend fun setContent(id: String, contentJson: String, at: Long)

    @Query("UPDATE blocks SET parentBlockId = :parentBlockId, orderKey = :orderKey, updatedAt = :at WHERE id = :id")
    suspend fun move(id: String, parentBlockId: String?, orderKey: String, at: Long)

    @Query("DELETE FROM blocks WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("DELETE FROM blocks WHERE pageId = :pageId")
    suspend fun deleteByPage(pageId: String)

    @Query("SELECT * FROM blocks WHERE contentJson LIKE '%' || :pageId || '%' OR propsJson LIKE '%' || :pageId || '%'")
    suspend fun referencing(pageId: String): List<BlockEntity>

    @Query("SELECT pageId, propsJson FROM blocks WHERE type = 'IMAGE' AND pageId IN (:pageIds) ORDER BY orderKey")
    suspend fun imagesFor(pageIds: List<String>): List<BlockPropsRow>

    @Query("SELECT pageId, propsJson FROM blocks WHERE propsJson LIKE '%mediaPath%'")
    suspend fun mediaRefs(): List<BlockPropsRow>

    @Query("SELECT COUNT(*) FROM blocks")
    suspend fun count(): Int

    @Query("DELETE FROM blocks")
    suspend fun clear()
}

@Dao
interface DatabaseDao {
    @Query("SELECT * FROM databases WHERE id = :id")
    suspend fun get(id: String): DatabaseEntity?

    @Query("SELECT * FROM databases WHERE id = :id")
    fun observe(id: String): Flow<DatabaseEntity?>

    @Query("SELECT * FROM databases WHERE pageId = :pageId")
    suspend fun byPage(pageId: String): DatabaseEntity?

    @Query("SELECT * FROM databases WHERE pageId = :pageId")
    fun observeByPage(pageId: String): Flow<DatabaseEntity?>

    @Query("SELECT d.* FROM databases d JOIN pages p ON p.id = d.pageId WHERE p.isTrashed = 0")
    fun observeAllLive(): Flow<List<DatabaseEntity>>

    @Query("SELECT * FROM databases")
    suspend fun all(): List<DatabaseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(db: DatabaseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(dbs: List<DatabaseEntity>)

    @Query("UPDATE databases SET schemaJson = :schemaJson WHERE id = :id")
    suspend fun setSchema(id: String, schemaJson: String)

    @Query("DELETE FROM databases")
    suspend fun clear()
}

@Dao
interface ViewDao {
    @Query("SELECT * FROM views WHERE databaseId = :databaseId ORDER BY orderKey")
    fun observe(databaseId: String): Flow<List<ViewEntity>>

    @Query("SELECT * FROM views WHERE databaseId = :databaseId ORDER BY orderKey")
    suspend fun byDatabase(databaseId: String): List<ViewEntity>

    @Query("SELECT * FROM views")
    suspend fun all(): List<ViewEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(view: ViewEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(views: List<ViewEntity>)

    @Delete
    suspend fun delete(view: ViewEntity)

    @Query("DELETE FROM views")
    suspend fun clear()
}

@Dao
interface ValueDao {
    @Query("SELECT v.* FROM row_property_values v JOIN pages p ON p.id = v.rowPageId WHERE p.databaseId = :databaseId")
    fun observeForDatabase(databaseId: String): Flow<List<PropertyValueEntity>>

    @Query("SELECT v.* FROM row_property_values v JOIN pages p ON p.id = v.rowPageId WHERE p.databaseId = :databaseId")
    suspend fun forDatabase(databaseId: String): List<PropertyValueEntity>

    @Query("SELECT * FROM row_property_values WHERE rowPageId = :rowId")
    fun observeForRow(rowId: String): Flow<List<PropertyValueEntity>>

    @Query("SELECT * FROM row_property_values WHERE rowPageId = :rowId")
    suspend fun forRow(rowId: String): List<PropertyValueEntity>

    @Query("SELECT * FROM row_property_values WHERE valueJson LIKE '%' || :id || '%'")
    suspend fun referencing(id: String): List<PropertyValueEntity>

    @Query("SELECT * FROM row_property_values")
    suspend fun all(): List<PropertyValueEntity>

    @Upsert
    suspend fun upsert(value: PropertyValueEntity)

    @Upsert
    suspend fun upsertAll(values: List<PropertyValueEntity>)

    @Query("DELETE FROM row_property_values WHERE rowPageId = :rowId AND propertyId = :propertyId")
    suspend fun delete(rowId: String, propertyId: String)

    @Query("DELETE FROM row_property_values WHERE propertyId = :propertyId AND rowPageId IN (SELECT id FROM pages WHERE databaseId = :databaseId)")
    suspend fun deleteProperty(databaseId: String, propertyId: String)

    @Query("DELETE FROM row_property_values")
    suspend fun clear()
}

@Dao
interface VersionDao {
    @Query("SELECT * FROM versions WHERE pageId = :pageId ORDER BY createdAt DESC")
    fun observe(pageId: String): Flow<List<VersionEntity>>

    @Query("SELECT * FROM versions WHERE id = :id")
    suspend fun get(id: String): VersionEntity?

    @Query("SELECT * FROM versions WHERE pageId = :pageId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latest(pageId: String): VersionEntity?

    @Query("SELECT * FROM versions")
    suspend fun all(): List<VersionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(version: VersionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(versions: List<VersionEntity>)

    @Query("DELETE FROM versions WHERE pageId = :pageId AND id NOT IN (SELECT id FROM versions WHERE pageId = :pageId ORDER BY createdAt DESC LIMIT :keep)")
    suspend fun prune(pageId: String, keep: Int)

    @Query("DELETE FROM versions")
    suspend fun clear()
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY createdAt DESC")
    fun observe(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun get(id: String): TemplateEntity?

    @Query("SELECT * FROM templates")
    suspend fun all(): List<TemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: TemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(templates: List<TemplateEntity>)

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM templates")
    suspend fun clear()
}

@Dao
interface RecentDao {
    @Query("SELECT p.* FROM recents r JOIN pages p ON p.id = r.pageId WHERE p.isTrashed = 0 ORDER BY r.visitedAt DESC LIMIT 10")
    fun observe(): Flow<List<PageEntity>>

    @Upsert
    suspend fun upsert(recent: RecentEntity)

    @Query("DELETE FROM recents WHERE pageId NOT IN (SELECT pageId FROM recents ORDER BY visitedAt DESC LIMIT :keep)")
    suspend fun prune(keep: Int)

    @Query("SELECT * FROM recents")
    suspend fun all(): List<RecentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(recents: List<RecentEntity>)

    @Query("DELETE FROM recents")
    suspend fun clear()
}

@Dao
interface SearchDao {
    @Insert
    suspend fun insert(doc: SearchFts)

    @Insert
    suspend fun insertAll(docs: List<SearchFts>)

    @Query("DELETE FROM search_fts WHERE refId = :refId")
    suspend fun deleteRef(refId: String)

    @Query("DELETE FROM search_fts WHERE pageId = :pageId")
    suspend fun deletePage(pageId: String)

    @Query("DELETE FROM search_fts")
    suspend fun clear()

    @Query("SELECT * FROM search_fts WHERE search_fts MATCH :query LIMIT 300")
    suspend fun match(query: String): List<SearchFts>
}
