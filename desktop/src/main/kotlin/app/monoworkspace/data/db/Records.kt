package app.monoworkspace.data.db

import kotlinx.serialization.Serializable

// Same records as the Android Room entities, so the shared repositories and
// the backup format (.monobackup) are identical on both platforms.

@Serializable
data class PageEntity(
    val id: String,
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
data class BlockEntity(
    val id: String,
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
data class DatabaseEntity(
    val id: String,
    val pageId: String,
    val schemaJson: String,
)

@Serializable
data class ViewEntity(
    val id: String,
    val databaseId: String,
    val name: String,
    val type: String,
    val configJson: String,
    val orderKey: String,
)

@Serializable
data class PropertyValueEntity(
    val rowPageId: String,
    val propertyId: String,
    val valueJson: String,
)

@Serializable
data class VersionEntity(
    val id: String,
    val pageId: String,
    val createdAt: Long,
    val title: String,
    val blocksJson: String,
)

@Serializable
data class TemplateEntity(
    val id: String,
    val name: String,
    val payloadJson: String,
    val createdAt: Long,
)

@Serializable
data class RecentEntity(
    val pageId: String,
    val visitedAt: Long,
)

/** In-memory full-text document; rebuilt from content on start. */
data class SearchFts(
    val rowId: Int = 0,
    val refId: String,
    val kind: String,
    val pageId: String,
    val databaseId: String,
    val title: String,
    val body: String,
    val editedAt: String,
)

data class BlockPropsRow(val pageId: String, val propsJson: String)
