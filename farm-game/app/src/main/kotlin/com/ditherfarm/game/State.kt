package com.ditherfarm.game

import org.json.JSONArray
import org.json.JSONObject

enum class Weather(val icon: String, val waters: Boolean, val stormy: Boolean) {
    SUNNY("icons/weather_sunny", false, false),
    RAIN("icons/weather_rain", true, false),
    STORM("icons/weather_storm", true, true),
    SNOW("icons/weather_snow", true, false),
    BLIZZARD("icons/weather_snow", true, true),
}

class PlotState(val index: Int) {
    var owned = false
    var crop: String? = null
    /** Effective game minutes of growth (watered = 1/min, dry = 0.6/min). */
    var growth = 0.0
    var watered = false
    /** Watered at some point since planting or the last regrow; counts as care for quality. */
    var cared = false
    var withered = false
    /** Struck by lightning: cleared like a withered plot. */
    var scorched = false
    var battered = false
    var fertSilver = 0.0
    var fertGold = 0.0
    var harvests = 0
    /** Last crop planted here; the Planter Shed replants it. */
    var lastCrop: String? = null

    val empty get() = crop == null && !withered && !scorched
    val needsClearing get() = withered || scorched

    fun clear() {
        crop = null; growth = 0.0; cared = false; withered = false; scorched = false
        battered = false; fertSilver = 0.0; fertGold = 0.0; harvests = 0
    }

    fun toJson() = JSONObject().apply {
        put("i", index); put("owned", owned); crop?.let { put("crop", it) }; put("growth", growth)
        put("watered", watered); put("cared", cared); put("withered", withered); put("scorched", scorched)
        put("battered", battered); put("fs", fertSilver); put("fg", fertGold); put("h", harvests)
        lastCrop?.let { put("lc", it) }
    }

    companion object {
        fun fromJson(o: JSONObject) = PlotState(o.getInt("i")).apply {
            owned = o.optBoolean("owned"); crop = if (o.has("crop")) o.getString("crop") else null
            growth = o.optDouble("growth", 0.0); watered = o.optBoolean("watered"); cared = o.optBoolean("cared")
            withered = o.optBoolean("withered"); scorched = o.optBoolean("scorched"); battered = o.optBoolean("battered")
            fertSilver = o.optDouble("fs", 0.0); fertGold = o.optDouble("fg", 0.0); harvests = o.optInt("h")
            lastCrop = if (o.has("lc")) o.getString("lc") else null
        }
    }
}

class OrderLine(val item: String, val qty: Int)

class Order(
    val id: Int,
    val villager: String,
    val lines: List<OrderLine>,
    val coins: Int,
    val xp: Int,
    val big: Boolean = false,
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("v", villager); put("coins", coins); put("xp", xp); put("big", big)
        put("lines", JSONArray().apply { lines.forEach { put(JSONObject().put("item", it.item).put("qty", it.qty)) } })
    }

    companion object {
        fun fromJson(o: JSONObject) = Order(
            o.getInt("id"), o.getString("v"),
            o.getJSONArray("lines").objects().map { OrderLine(it.getString("item"), it.getInt("qty")) },
            o.getInt("coins"), o.getInt("xp"), o.optBoolean("big"),
        )
    }
}

class OrderSlot {
    var order: Order? = null
    /** Game minute at which a new order fills this empty slot. */
    var nextAt = 0.0
}

/** Everything that is saved. Static definitions live in [GameData]. */
class GameState(var seed: Long) {
    var version = VERSION
    var minutes = Time.START_MINUTE
    var lastSavedMs = 0L
    /** dayIndex of the last 06:00 roll that ran. */
    var lastDayRolled = -1
    var lastStormDay = -10

    var coins = 0
    var xp = 0
    var level = 1
    var energy = 100.0

    /** Item key (with optional quality suffix) → count. */
    val inventory = LinkedHashMap<String, Int>()
    var barnLevel = 1
    var siloLevel = 1

    val plots = ArrayList<PlotState>()

    val orders = ArrayList<OrderSlot>()
    var orderCounter = 0
    val friendship = HashMap<String, Int>()
    val recentOrderItems = ArrayList<String>()

    var weather = Weather.SUNNY
    var forecast = Weather.SUNNY

    /** Market saturation per item (base coins sold recently). */
    val glut = HashMap<String, Double>()
    /** Items obtained at least once (collection book, order pool). */
    val discovered = LinkedHashSet<String>()
    val stats = HashMap<String, Long>()
    /** Tutorial tips already shown. */
    val tips = HashSet<String>()
    var rngCounter = 0L

    var sfx = true
    var haptics = true
    var notifications = true
    var music = true
    var ambience = true

    val buildings = LinkedHashMap<String, BuildingState>()
    /** Tool id (axe, pickaxe, hoe, can, sickle) → tier 0 wood .. 3 gold. */
    val tools = HashMap<String, Int>()
    var forest = ForestState()
    var mine = MineState()
    /** Field vertex → fixture item (sprinklers, scarecrows, lightning rods). */
    val fixtures = HashMap<Int, String>()
    /** Per field block (A..H): crop to replant and harvest destination. */
    val fields = ArrayList<FieldState>()
    /** Hired farmhands by id, the truck and when the harvester next does its round. */
    val workers = LinkedHashMap<String, WorkerState>()
    var truck = TruckState()
    var harvesterNext = 0.0
    /** Timed building id → game minute of its next run, and whether it is blocked (tray full). */
    val shedNext = HashMap<String, Double>()
    val shedBlocked = HashMap<String, Boolean>()
    /** Achievement id → tiers claimed; "id#tier" keys already announced; claimed collection pages. */
    val achievements = HashMap<String, Int>()
    val achievementsSeen = HashSet<String>()
    val pagesClaimed = LinkedHashSet<String>()
    /** Login gift: device day last claimed and the running streak. */
    var giftDay = -1L
    var giftStreak = 0
    /** Merchant purchases (offer slots) on [merchantDay]. */
    var merchantDay = -1
    val merchantBought = HashSet<Int>()
    /** Current festival ("year-SEASON"), its hand-in count and starting counter, and festivals won. */
    var festivalKey = ""
    var festivalProgress = 0
    var festivalBase = 0L
    val festivalsClaimed = HashSet<String>()
    /** Market Cart rules in priority order and the last 7 days of sales. */
    val cartRules = ArrayList<CartRule>()
    val cartLog = ArrayList<CartDay>()
    /** Skill id → rank, and the game minute the Lucky Charm wears off. */
    val skills = LinkedHashMap<String, Int>()
    var luckUntil = 0.0
    /** Grandpa's notebook: current quest and its stat when it started; feats and blueprints announced. */
    var questIndex = 0
    var questBase = 0L
    val featsSeen = LinkedHashSet<String>()

    fun stat(key: String) = stats[key] ?: 0L
    fun bump(key: String, by: Long = 1) { stats[key] = stat(key) + by }

    fun toJson(): JSONObject = JSONObject().apply {
        put("version", version); put("seed", seed); put("minutes", minutes); put("lastSavedMs", lastSavedMs)
        put("lastDayRolled", lastDayRolled); put("lastStormDay", lastStormDay)
        put("coins", coins); put("xp", xp); put("level", level); put("energy", energy)
        put("inventory", JSONObject(inventory as Map<*, *>))
        put("barnLevel", barnLevel); put("siloLevel", siloLevel)
        put("plots", JSONArray().apply { plots.forEach { put(it.toJson()) } })
        put("orders", JSONArray().apply {
            orders.forEach { s -> put(JSONObject().apply { put("nextAt", s.nextAt); s.order?.let { put("order", it.toJson()) } }) }
        })
        put("orderCounter", orderCounter)
        put("friendship", JSONObject(friendship as Map<*, *>))
        put("recentOrderItems", JSONArray(recentOrderItems))
        put("weather", weather.name); put("forecast", forecast.name)
        put("glut", JSONObject(glut as Map<*, *>))
        put("discovered", JSONArray(discovered.toList()))
        put("stats", JSONObject(stats as Map<*, *>))
        put("tips", JSONArray(tips.toList()))
        put("rngCounter", rngCounter)
        put("settings", JSONObject().put("sfx", sfx).put("haptics", haptics).put("notifications", notifications).put("music", music).put("ambience", ambience))
        put("buildings", JSONArray().apply { buildings.values.forEach { put(it.toJson()) } })
        put("tools", JSONObject(tools as Map<*, *>))
        put("forest", forest.toJson())
        put("mine", mine.toJson())
        put("fixtures", JSONObject().apply { fixtures.forEach { (k, v) -> put(k.toString(), v) } })
        put("fields", JSONArray().apply { fields.forEach { put(it.toJson()) } })
        put("workers", JSONArray().apply { workers.values.forEach { put(it.toJson()) } })
        put("truck", truck.toJson())
        put("harvesterNext", harvesterNext)
        put("shedNext", JSONObject(shedNext as Map<*, *>))
        put("achievements", JSONObject(achievements as Map<*, *>))
        put("achievementsSeen", JSONArray(achievementsSeen.toList()))
        put("pagesClaimed", JSONArray(pagesClaimed.toList()))
        put("cartRules", cartRulesToJson(cartRules))
        put("skills", JSONObject(skills as Map<*, *>)); put("luck", luckUntil)
        put("quest", questIndex); put("questBase", questBase); put("feats", JSONArray(featsSeen.toList()))
        put("cartLog", JSONArray().apply { cartLog.forEach { put(it.toJson()) } })
        put("events", JSONObject().apply {
            put("giftDay", giftDay); put("giftStreak", giftStreak); put("merchantDay", merchantDay)
            put("merchantBought", JSONArray(merchantBought.toList()))
            put("festivalKey", festivalKey); put("festivalProgress", festivalProgress); put("festivalBase", festivalBase)
            put("festivalsClaimed", JSONArray(festivalsClaimed.toList()))
        })
    }

    companion object {
        const val VERSION = 3

        fun fromJson(o: JSONObject): GameState = GameState(o.getLong("seed")).apply {
            version = o.optInt("version", 1)
            minutes = o.getDouble("minutes"); lastSavedMs = o.optLong("lastSavedMs")
            lastDayRolled = o.optInt("lastDayRolled", -1); lastStormDay = o.optInt("lastStormDay", -10)
            coins = o.getInt("coins"); xp = o.getInt("xp"); level = o.getInt("level"); energy = o.getDouble("energy")
            o.getJSONObject("inventory").toIntMap().forEach { (k, v) -> inventory[k] = v }
            barnLevel = o.optInt("barnLevel", 1); siloLevel = o.optInt("siloLevel", 1)
            o.getJSONArray("plots").objects().forEach { plots.add(PlotState.fromJson(it)) }
            o.getJSONArray("orders").objects().forEach { so ->
                orders.add(OrderSlot().apply {
                    nextAt = so.optDouble("nextAt", 0.0)
                    if (so.has("order")) order = Order.fromJson(so.getJSONObject("order"))
                })
            }
            orderCounter = o.optInt("orderCounter")
            o.optJSONObject("friendship")?.toIntMap()?.forEach { (k, v) -> friendship[k] = v }
            o.optJSONArray("recentOrderItems")?.strings()?.let { recentOrderItems.addAll(it) }
            weather = Weather.valueOf(o.optString("weather", "SUNNY")); forecast = Weather.valueOf(o.optString("forecast", "SUNNY"))
            o.optJSONObject("glut")?.let { g -> g.keys().forEach { glut[it] = g.getDouble(it) } }
            o.optJSONArray("discovered")?.strings()?.let { discovered.addAll(it) }
            o.optJSONObject("stats")?.let { s -> s.keys().forEach { stats[it] = s.getLong(it) } }
            o.optJSONArray("tips")?.strings()?.let { tips.addAll(it) }
            rngCounter = o.optLong("rngCounter")
            o.optJSONObject("settings")?.let {
                sfx = it.optBoolean("sfx", true); haptics = it.optBoolean("haptics", true); notifications = it.optBoolean("notifications", true)
                music = it.optBoolean("music", true); ambience = it.optBoolean("ambience", true)
            }
            o.optJSONArray("buildings")?.objects()?.forEach { val b = BuildingState.fromJson(it); buildings[b.id] = b }
            o.optJSONObject("tools")?.toIntMap()?.forEach { (k, v) -> tools[k] = v }
            o.optJSONObject("forest")?.let { forest = ForestState.fromJson(it) }
            o.optJSONObject("mine")?.let { mine = MineState.fromJson(it) }
            o.optJSONObject("fixtures")?.let { f -> f.keys().forEach { fixtures[it.toInt()] = f.getString(it) } }
            o.optJSONArray("fields")?.objects()?.forEach { fields += FieldState.fromJson(it) }
            o.optJSONArray("workers")?.objects()?.forEach { val w = WorkerState.fromJson(it); workers[w.id] = w }
            o.optJSONObject("truck")?.let { truck = TruckState.fromJson(it) }
            harvesterNext = o.optDouble("harvesterNext", 0.0)
            o.optJSONObject("shedNext")?.let { m -> m.keys().forEach { shedNext[it] = m.getDouble(it) } }
            o.optJSONObject("achievements")?.toIntMap()?.forEach { (k, v) -> achievements[k] = v }
            o.optJSONArray("achievementsSeen")?.strings()?.let { achievementsSeen.addAll(it) }
            o.optJSONArray("pagesClaimed")?.strings()?.let { pagesClaimed.addAll(it) }
            o.optJSONArray("cartRules")?.objects()?.forEach { cartRules += CartRule.fromJson(it) }
            o.optJSONObject("skills")?.toIntMap()?.forEach { (k, v) -> skills[k] = v }
            luckUntil = o.optDouble("luck", 0.0)
            questIndex = o.optInt("quest", 0); questBase = o.optLong("questBase", 0L)
            o.optJSONArray("feats")?.let { a -> for (k in 0 until a.length()) featsSeen += a.getString(k) }
            o.optJSONArray("cartLog")?.objects()?.forEach { cartLog += CartDay(it.getInt("day"), it.getInt("units"), it.getInt("coins")) }
            o.optJSONObject("events")?.let { e ->
                giftDay = e.optLong("giftDay", -1); giftStreak = e.optInt("giftStreak"); merchantDay = e.optInt("merchantDay", -1)
                e.optJSONArray("merchantBought")?.let { a -> (0 until a.length()).forEach { merchantBought += a.getInt(it) } }
                festivalKey = e.optString("festivalKey"); festivalProgress = e.optInt("festivalProgress"); festivalBase = e.optLong("festivalBase")
                e.optJSONArray("festivalsClaimed")?.strings()?.let { festivalsClaimed.addAll(it) }
            }
        }
    }
}
