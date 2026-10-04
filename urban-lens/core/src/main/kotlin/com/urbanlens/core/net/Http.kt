package com.urbanlens.core.net

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.io.IOException
import java.util.Locale
import kotlin.math.roundToInt

/** Minimal HTTP abstraction so the core logic stays platform-free and testable. */
interface Http {
    suspend fun get(url: String): String
    suspend fun postForm(url: String, form: Map<String, String>): String
}

class HttpException(val code: Int, message: String) : IOException(message)

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
internal fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
internal fun JsonObject.int(key: String): Int? = double(key)?.roundToInt()
internal fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonElement.asObjectList(): List<JsonObject> = when (this) {
    is JsonArray -> filterIsInstance<JsonObject>()
    is JsonObject -> listOf(this)
    else -> emptyList()
}

/** Coordinates in URLs: fixed precision, never locale-dependent decimal commas. */
internal fun coord(value: Double): String = String.format(Locale.ROOT, "%.5f", value)
