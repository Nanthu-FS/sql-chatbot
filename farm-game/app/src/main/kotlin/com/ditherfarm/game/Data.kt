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

class TreeDef(
    val id: String,
    val minAxe: Int,
    val hp: Int,
    val wood: IntRange,
    val hardwood: IntRange,
    /** Real minutes from sapling to mature (before the season multiplier). */
    val growMinutes: Double,
    val xp: Int,
    val seed: String,
    val seedChance: Double,
    val extras: List<Pair<String, Double>>,
)

class ZoneDef(
    val id: String,
    val name: String,
    val unlockLevel: Int,
    val cost: Map<String, Int>,
    val trees: Map<String, Int>,
    val bushes: Int,
    val apples: Int,
    val mushrooms: Int,
    val slots: Int,
)

class ToolTiers(val damage: List<Int>, val crit: List<Double>, val names: List<String>)

class GameData(
    val crops: Map<String, CropDef>,
    val items: Map<String, ItemDef>,
    val levels: List<LevelDef>,
    val barn: List<StorageTier>,
    val silo: List<StorageTier>,
    val villagers: List<VillagerDef>,
    val buildings: Map<String, BuildingDef> = emptyMap(),
    val recipes: Map<String, RecipeDef> = emptyMap(),
    val trees: Map<String, TreeDef> = emptyMap(),
    val zones: List<ZoneDef> = emptyList(),
    val tools: ToolTiers = ToolTiers(listOf(1, 2, 3, 6), listOf(0.05, 0.08, 0.12, 0.15), listOf("Wood", "Stone", "Iron", "Gold")),
    val mine: MineData = MineData.EMPTY,
    val goals: GoalsData = GoalsData.EMPTY,
) {
    val maxLevel get() = levels.size
    val forestLayout: List<ForestSpot> by lazy { ForestLayout.build(zones) }
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
            val buildings = JSONObject(read("buildings.json")).getJSONArray("buildings").objects().map { o ->
                BuildingDef(
                    o.getString("id"), o.getString("name"), o.getString("screen"), o.getInt("unlock"),
                    o.getJSONObject("cost").toIntMap(), o.getJSONArray("recipes").strings(), o.optInt("queue", 3),
                    o.optBoolean("prebuilt"), o.optString("sprite", "buildings/" + o.getString("id")),
                    o.optJSONObject("fuel")?.optInt("tank", 0) ?: 0,
                    o.optInt("trayCap", 3),
                )
            }.associateBy { it.id }
            val recipes = JSONObject(read("recipes.json")).getJSONArray("recipes").objects().map { o ->
                RecipeDef(
                    o.getString("id"), o.getString("building"), o.getJSONObject("inputs").toIntMap(), o.getJSONObject("outputs").toIntMap(),
                    o.getDouble("minutes"), o.getInt("unlock"), o.optInt("coins", 0), o.optDouble("fuel", 0.0),
                    if (o.has("name")) o.getString("name") else null,
                )
            }.associateBy { it.id }
            val forest = JSONObject(read("forest.json"))
            fun range(a: JSONArray) = a.getInt(0)..a.getInt(1)
            val trees = forest.getJSONArray("trees").objects().map { o ->
                TreeDef(
                    o.getString("id"), o.getInt("minAxe"), o.getInt("hp"), range(o.getJSONArray("wood")), range(o.getJSONArray("hardwood")),
                    o.getDouble("growMinutes"), o.getInt("xp"), o.getString("seed"), o.getDouble("seedChance"),
                    o.getJSONArray("extras").objects().map { it.getString("item") to it.getDouble("chance") },
                )
            }.associateBy { it.id }
            val zones = forest.getJSONArray("zones").objects().map { o ->
                ZoneDef(
                    o.getString("id"), o.getString("name"), o.getInt("unlock"), o.getJSONObject("cost").toIntMap(),
                    o.getJSONObject("trees").toIntMap(), o.getInt("bushes"), o.getInt("apples"), o.getInt("mushrooms"), o.getInt("slots"),
                )
            }
            val t = forest.getJSONObject("tools")
            val tools = ToolTiers(
                (0 until t.getJSONArray("damage").length()).map { t.getJSONArray("damage").getInt(it) },
                (0 until t.getJSONArray("crit").length()).map { t.getJSONArray("crit").getDouble(it) },
                t.getJSONArray("names").strings(),
            )
            return GameData(crops, items, levels, tiers("barn"), tiers("silo"), villagers, buildings, recipes, trees, zones, tools, MineData.parse(read("mine.json")), GoalsData.parse(read("goals.json")))
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
