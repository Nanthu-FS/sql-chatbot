package app.monoworkspace.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "pages",
    indices = [Index("parentId"), Index("databaseId"), Index("isTrashed")],
)
data class PageEntity(
    @PrimaryKey val id: String,
    val parentId: String?,
    val databaseId: String?,
    val isDatabase: Boolean,
    val title: String,
    val icon: String?,
    val cover: String?,
    val orderKey: String,
    val isFavorite: Boolean,
    val favoriteOrder: String?,
    val isTrashed: Boolean,
    val trashRoot: Boolean,
    val trashedAt: Long?,
    val createdAt: Long,
    val editedAt: Long,
)

@Serializable
@Entity(
    tableName = "blocks",
    foreignKeys = [
        ForeignKey(entity = PageEntity::class, parentColumns = ["id"], childColumns = ["pageId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("pageId"), Index("parentBlockId")],
)
data class BlockEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    val parentBlockId: String?,
    val orderKey: String,
    val type: String,
    val contentJson: String,
    val propsJson: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
@Entity(
    tableName = "databases",
    foreignKeys = [
        ForeignKey(entity = PageEntity::class, parentColumns = ["id"], childColumns = ["pageId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["pageId"], unique = true)],
)
data class DatabaseEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    val schemaJson: String,
)

@Serializable
@Entity(
    tableName = "views",
    foreignKeys = [
        ForeignKey(entity = DatabaseEntity::class, parentColumns = ["id"], childColumns = ["databaseId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("databaseId")],
)
data class ViewEntity(
    @PrimaryKey val id: String,
    val databaseId: String,
    val name: String,
    val type: String,
    val configJson: String,
    val orderKey: String,
)

@Serializable
@Entity(
    tableName = "row_property_values",
    primaryKeys = ["rowPageId", "propertyId"],
    foreignKeys = [
        ForeignKey(entity = PageEntity::class, parentColumns = ["id"], childColumns = ["rowPageId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("propertyId")],
)
data class PropertyValueEntity(
    val rowPageId: String,
    val propertyId: String,
    val valueJson: String,
)

@Serializable
@Entity(
    tableName = "versions",
    foreignKeys = [
        ForeignKey(entity = PageEntity::class, parentColumns = ["id"], childColumns = ["pageId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("pageId")],
)
data class VersionEntity(
    @PrimaryKey val id: String,
    val pageId: String,
    val createdAt: Long,
    val title: String,
    val blocksJson: String,
)

@Serializable
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val payloadJson: String,
    val createdAt: Long,
)

@Serializable
@Entity(
    tableName = "recents",
    foreignKeys = [
        ForeignKey(entity = PageEntity::class, parentColumns = ["id"], childColumns = ["pageId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class RecentEntity(
    @PrimaryKey val pageId: String,
    val visitedAt: Long,
)

/** Full-text index over page titles, block text and row values. */
@Fts4(notIndexed = ["refId", "kind", "pageId", "databaseId", "editedAt"])
@Entity(tableName = "search_fts")
data class SearchFts(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid") val rowId: Int = 0,
    val refId: String,
    val kind: String,
    val pageId: String,
    val databaseId: String,
    val title: String,
    val body: String,
    val editedAt: String,
)

data class BlockPropsRow(val pageId: String, val propsJson: String)
