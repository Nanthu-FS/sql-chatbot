package com.ditherfarm.game

import org.json.JSONArray
import org.json.JSONObject

/** Static definitions loaded from the JSON tables in assets/data (see Docs/GAME_DESIGN.md). */
class CropDef(
    val id: String,
    val unlockLevel: Int,
    val seasons: Set<Season>,
    val growHours: Double,
    /** 0 = single harvest; otherwise hours until the next harvest after picking. */
    val regrowHours: Double,
    val seedPrice: Int,
    val yield: Int,
    val price: Int,
    val xp: Int,
    val energy: Int,
) {
    val growMinutes get() = growHours * 60
    val regrows get() = regrowHours > 0
    val seedItem get() = "seed_$id"
}

enum class Storage { SILO, BARN, NONE }

class ItemDef(val id: String, val name: String, val category: String, val price: Int, val storage: Storage)

class LevelDef(
    val level: Int,
    val xpToNext: Int,
    val totalXp: Int,
    val maxEnergy: Int,
    val plotCap: Int,
    val orderSlots: Int,
    val levelUpCoins: Int,
)

class StorageTier(val level: Int, val capacity: Int, val unlockLevel: Int, val cost: Map<String, Int>)

class VillagerDef(val id: String, val name: String, val role: String, val unlockLevel: Int, val favorites: Set<String>)

class GameData(
    val crops: Map<String, CropDef>,
    val items: Map<String, ItemDef>,
    val levels: List<LevelDef>,
    val barn: List<StorageTier>,
    val silo: List<StorageTier>,
    val villagers: List<VillagerDef>,
) {
    val maxLevel get() = levels.size
    fun level(l: Int): LevelDef = levels[(l - 1).coerceIn(0, levels.size - 1)]
    fun item(id: String): ItemDef = items[id] ?: ItemDef(id, id, "unknown", 0, Storage.BARN)
    fun crop(id: String): CropDef = crops[id] ?: error("unknown crop $id")
    fun name(id: String) = items[baseId(id)]?.name ?: baseId(id)

    companion object {
        /** Loads every table; [read] returns the text of a file under assets/data. */
        fun load(read: (String) -> String): GameData {
            val crops = JSONObject(read("crops.json")).getJSONArray("crops").objects().map { o ->
                CropDef(
                    id = o.getString("id"),
                    unlockLevel = o.getInt("unlock"),
                    seasons = o.getJSONArray("seasons").strings().map { Season.valueOf(it.uppercase()) }.toSet(),
                    growHours = o.getDouble("growHours"),
                    regrowHours = o.getDouble("regrowHours"),
                    seedPrice = o.getInt("seed"),
                    yield = o.getInt("yield"),
                    price = o.getInt("price"),
                    xp = o.getInt("xp"),
                    energy = o.getInt("energy"),
                )
            }.associateBy { it.id }
            val items = JSONObject(read("items.json")).getJSONArray("items").objects().map { o ->
                ItemDef(o.getString("id"), o.getString("name"), o.getString("category"), o.getInt("price"), Storage.valueOf(o.getString("storage").uppercase()))
            }.associateBy { it.id }
            val levels = JSONObject(read("levels.json")).getJSONArray("levels").objects().map { o ->
                LevelDef(o.getInt("level"), o.getInt("xpToNext"), o.getInt("totalXp"), o.getInt("maxEnergy"), o.getInt("plotCap"), o.getInt("orderSlots"), o.getInt("levelUpCoins"))
            }
            val storage = JSONObject(read("storage.json"))
            fun tiers(key: String) = storage.getJSONArray(key).objects().map { o ->
                StorageTier(o.getInt("level"), o.getInt("capacity"), o.getInt("unlock"), o.getJSONObject("cost").toIntMap())
            }
            val villagers = JSONObject(read("villagers.json")).getJSONArray("villagers").objects().map { o ->
                VillagerDef(o.getString("id"), o.getString("name"), o.getString("role"), o.getInt("unlock"), o.getJSONArray("favorites").strings().toSet())
            }
            return GameData(crops, items, levels, tiers("barn"), tiers("silo"), villagers)
        }
    }
}

/** Inventory keys carry quality as a suffix: "carrot", "carrot:s" (silver), "carrot:g" (gold). */
enum class Quality(val suffix: String, val mult: Double, val icon: String?) {
    NORMAL("", 1.0, null), SILVER(":s", 1.25, "icons/quality_silver"), GOLD(":g", 1.5, "icons/quality_gold");

    fun key(id: String) = id + suffix

    companion object {
        fun of(key: String): Quality = when {
            key.endsWith(":g") -> GOLD
            key.endsWith(":s") -> SILVER
            else -> NORMAL
        }
    }
}

fun baseId(key: String): String = key.substringBefore(':')

/** Rounds to friendly numbers: 1s under 100, 10s under 1,000, 50s under 10,000, then 100s. */
fun niceRound(v: Double): Int = when {
    v < 100 -> Math.round(v).toInt()
    v < 1_000 -> (Math.round(v / 10) * 10).toInt()
    v < 10_000 -> (Math.round(v / 50) * 50).toInt()
    else -> (Math.round(v / 100) * 100).toInt()
}

internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
internal fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
internal fun JSONObject.toIntMap(): Map<String, Int> = keys().asSequence().associateWith { getInt(it) }
