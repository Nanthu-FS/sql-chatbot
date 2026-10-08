package app.monoworkspace.data

import app.monoworkspace.data.db.BlockEntity
import app.monoworkspace.data.db.DatabaseEntity
import app.monoworkspace.data.db.PageEntity
import app.monoworkspace.data.db.PropertyValueEntity
import app.monoworkspace.data.db.ViewEntity
import app.monoworkspace.model.Block
import app.monoworkspace.model.BlockProps
import app.monoworkspace.model.BlockType
import app.monoworkspace.model.Database
import app.monoworkspace.model.DatabaseSchema
import app.monoworkspace.model.DatabaseView
import app.monoworkspace.model.Page
import app.monoworkspace.model.PropertyValue
import app.monoworkspace.model.Span
import app.monoworkspace.model.ViewConfig
import app.monoworkspace.model.ViewType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

val MonoJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
    coerceInputValues = true
}

private val spanList = ListSerializer(Span.serializer())

fun encodeSpans(spans: List<Span>): String = MonoJson.encodeToString(spanList, spans)
fun decodeSpans(json: String): List<Span> = runCatching { MonoJson.decodeFromString(spanList, json) }.getOrDefault(emptyList())

fun encodeProps(props: BlockProps): String = MonoJson.encodeToString(BlockProps.serializer(), props)
fun decodeProps(json: String): BlockProps = runCatching { MonoJson.decodeFromString(BlockProps.serializer(), json) }.getOrDefault(BlockProps())

fun encodeValue(v: PropertyValue): String = MonoJson.encodeToString(PropertyValue.serializer(), v)
fun decodeValue(json: String): PropertyValue? = runCatching { MonoJson.decodeFromString(PropertyValue.serializer(), json) }.getOrNull()

fun PageEntity.toModel() = Page(
    id, parentId, databaseId, isDatabase, title, icon, cover, orderKey, isFavorite, favoriteOrder,
    isTrashed, trashRoot, trashedAt, createdAt, editedAt,
)

fun Page.toEntity() = PageEntity(
    id, parentId, databaseId, isDatabase, title, icon, cover, orderKey, isFavorite, favoriteOrder,
    isTrashed, trashRoot, trashedAt, createdAt, editedAt,
)

fun BlockEntity.toModel() = Block(
    id = id,
    pageId = pageId,
    parentBlockId = parentBlockId,
    orderKey = orderKey,
    type = runCatching { BlockType.valueOf(type) }.getOrDefault(BlockType.TEXT),
    content = decodeSpans(contentJson),
    props = decodeProps(propsJson),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Block.toEntity() = BlockEntity(
    id, pageId, parentBlockId, orderKey, type.name, encodeSpans(content), encodeProps(props), createdAt, updatedAt,
)

fun DatabaseEntity.toModel() = Database(
    id, pageId,
    runCatching { MonoJson.decodeFromString(DatabaseSchema.serializer(), schemaJson) }.getOrDefault(DatabaseSchema()),
)

fun encodeSchema(schema: DatabaseSchema): String = MonoJson.encodeToString(DatabaseSchema.serializer(), schema)

fun ViewEntity.toModel() = DatabaseView(
    id, databaseId, name,
    runCatching { ViewType.valueOf(type) }.getOrDefault(ViewType.TABLE),
    runCatching { MonoJson.decodeFromString(ViewConfig.serializer(), configJson) }.getOrDefault(ViewConfig()),
    orderKey,
)

fun DatabaseView.toEntity() = ViewEntity(
    id, databaseId, name, type.name, MonoJson.encodeToString(ViewConfig.serializer(), config), orderKey,
)

fun List<PropertyValueEntity>.toValueMap(): Map<String, Map<String, PropertyValue>> =
    groupBy { it.rowPageId }.mapValues { (_, list) ->
        list.mapNotNull { e -> decodeValue(e.valueJson)?.let { e.propertyId to it } }.toMap()
    }
