package com.ditherfarm.game

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

/** What stands on a forest spot. */
enum class SpotKind { TREE, BUSH, APPLE, MUSHROOM, SLOT, ROCK }

enum class TreeStage { EMPTY, SAPLING, YOUNG, MATURE, STUMP }

/** Fixed forest layout: every spot's zone, kind, native tree and map tile. */
class ForestSpot(val index: Int, val zone: String, val kind: SpotKind, val native: String?, val c: Int, val r: Int)

class SpotState {
    var tree: String? = null
    var stage = TreeStage.EMPTY
    /** Damage taken by the current tree or stump. */
    var damage = 0
    /** Game minute the sapling was planted (growth is measured from here). */
    var plantedAt = 0.0
    /** Game minutes needed to reach maturity (season multiplier baked in at planting). */
    var growMinutes = 0.0
    /** Game minute the spot became empty or a stump (for reseeding and rotting). */
    var since = 0.0
    /** Forage stock on bushes, apple trees and mushroom spots. */
    var stock = 0

    fun toJson() = JSONObject().apply {
        tree?.let { put("t", it) }; put("s", stage.name); put("d", damage); put("p", plantedAt)
        put("g", growMinutes); put("since", since); put("k", stock)
    }

    companion object {
        fun fromJson(o: JSONObject) = SpotState().apply {
            tree = if (o.has("t")) o.getString("t") else null
            stage = TreeStage.valueOf(o.optString("s", "EMPTY")); damage = o.optInt("d")
            plantedAt = o.optDouble("p", 0.0); growMinutes = o.optDouble("g", 0.0)
            since = o.optDouble("since", 0.0); stock = o.optInt("k")
        }
    }
}

class ForestState {
    var seeded = false
    val spots = ArrayList<SpotState>()
    val zones = LinkedHashSet<String>()
    /** Fallen branches lying on map tiles, packed as c * 1000 + r. */
    val branches = LinkedHashSet<Int>()
    /** Spot with a wild beehive (-1 = none) and the day it leaves. */
    var hive = -1
    var hiveUntil = 0

    fun toJson() = JSONObject().apply {
        put("seeded", seeded)
        put("hive", hive); put("hiveUntil", hiveUntil)
        put("spots", JSONArray().apply { spots.forEach { put(it.toJson()) } })
        put("zones", JSONArray(zones.toList()))
        put("branches", JSONArray(branches.toList()))
    }

    companion object {
        fun fromJson(o: JSONObject) = ForestState().apply {
            seeded = o.optBoolean("seeded")
            hive = o.optInt("hive", -1); hiveUntil = o.optInt("hiveUntil")
            o.optJSONArray("spots")?.objects()?.forEach { spots.add(SpotState.fromJson(it)) }
            o.optJSONArray("zones")?.strings()?.let { zones.addAll(it) }
            // Branches from the old small map that now fall in the forest wall are dropped.
            o.optJSONArray("branches")?.let { a -> (0 until a.length()).map { a.getInt(it) }.filter { ForestLayout.zoneOf(it / 1000, it % 1000) != null }.forEach { branches.add(it) } }
        }
    }
}

/**
 * Forest layout: a 44x44 map like the farm. The five zones are clearings joined by a trail that
 * winds up from the entrance (bottom corner, by the farm) to the Ancient Heart near the top; the
 * rest is forest wall (the screen adds rock cliffs). u = c - r runs left to
 * right and v = c + r top to bottom. Spot indices keep the zone order, so saves stay valid.
 */
object ForestLayout {
    const val SIZE = 44
    /** Tiles from the map edge that are wall. */
    const val WALL = 4

    /** A zone's clearing: an ellipse centred at (u, v) with half-axes [a] (u) and [b] (v). */
    class Clearing(val zone: String, val u: Float, val v: Float, val a: Float, val b: Float)

    val CLEARINGS = listOf(
        Clearing("A", 0f, 64f, 10f, 7f),
        Clearing("B", -14f, 48f, 9.5f, 6.5f),
        Clearing("C", 14f, 40f, 9.5f, 6.5f),
        Clearing("D", -11f, 29f, 8.5f, 6f),
        Clearing("E", 2f, 18.5f, 8f, 5.5f),
    )

    /** The trail's bends (u, v): the entrance, then each clearing's centre. */
    private val BENDS = listOf(0f to 2f * SIZE - 2f) + CLEARINGS.map { it.u to it.v }

    private fun inside(c: Int, r: Int) = c >= WALL && r >= WALL && c < SIZE - WALL && r < SIZE - WALL

    /** Ellipse test with a little noise so clearings are not perfect ovals. */
    private fun inClearing(k: Clearing, c: Int, r: Int): Boolean {
        val du = (c - r - k.u) / k.a
        val dv = (c + r - k.v) / k.b
        val wobble = ((c * 92821 + r * 68917) and 0xFF) / 255f * 0.18f
        return du * du + dv * dv <= 1f + wobble - 0.09f
    }

    /** Distance in tiles from (c, r) to trail leg [i] (from bend i to bend i + 1). */
    private fun legDistance(i: Int, c: Int, r: Int): Float {
        val (u0, v0) = BENDS[i]; val (u1, v1) = BENDS[i + 1]
        // Work in (c, r): c = (v + u) / 2, r = (v - u) / 2.
        val ax = (v0 + u0) / 2f; val ay = (v0 - u0) / 2f
        val bx = (v1 + u1) / 2f; val by = (v1 - u1) / 2f
        val dx = bx - ax; val dy = by - ay
        val t = (((c - ax) * dx + (r - ay) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        val px = ax + dx * t - c; val py = ay + dy * t - r
        return kotlin.math.sqrt(px * px + py * py)
    }

    private val zoneGrid: Array<String?> = Array(SIZE * SIZE) { i ->
        val c = i % SIZE; val r = i / SIZE
        CLEARINGS.firstOrNull { inside(c, r) && inClearing(it, c, r) }?.zone
    }
    private val trailGrid: Array<String?> = Array(SIZE * SIZE) { i ->
        val c = i % SIZE; val r = i / SIZE
        val leg = (0 until BENDS.size - 1).firstOrNull { legDistance(it, c, r) <= 0.75f }
        if (leg == null || (!inside(c, r) && c + r < SIZE)) null else CLEARINGS[leg].zone
    }

    /** Zone of a clearing or trail tile; null for forest wall. */
    fun zoneOf(c: Int, r: Int): String? =
        if (c !in 0 until SIZE || r !in 0 until SIZE) null else zoneGrid[r * SIZE + c] ?: trailGrid[r * SIZE + c]

    /** The trail from the entrance through every clearing; nothing is placed on it. */
    fun isTrail(c: Int, r: Int): Boolean = c in 0 until SIZE && r in 0 until SIZE && trailGrid[r * SIZE + c] != null

    fun isWall(c: Int, r: Int) = zoneOf(c, r) == null

    /** Open ground of a zone where branches may fall (clearings, not spots). */
    val clearingTiles: List<Pair<Int, Int>> = (0 until SIZE * SIZE).map { it % SIZE to it / SIZE }.filter { (c, r) -> zoneGrid[r * SIZE + c] != null }

    fun build(zones: List<ZoneDef>): List<ForestSpot> {
        val spots = ArrayList<ForestSpot>()
        val rockTiles = HashMap<String, List<Pair<Int, Int>>>()
        for (z in zones) {
            val tiles = ArrayList<Pair<Int, Int>>()
            for (r in 1 until SIZE - 1) for (c in 1 until SIZE - 1) {
                if (zoneGrid[r * SIZE + c] == z.id && !isTrail(c, r) && (c + r) % 2 == 0) tiles += c to r
            }
            val rng = Rng(z.id.hashCode().toLong() * 7919)
            for (i in tiles.indices.reversed()) { val j = rng.nextInt(i + 1); val t = tiles[i]; tiles[i] = tiles[j]; tiles[j] = t }
            var k = 0
            fun take(kind: SpotKind, native: String? = null) {
                if (k >= tiles.size) return
                val (c, r) = tiles[k++]
                spots += ForestSpot(spots.size, z.id, kind, native, c, r)
            }
            repeat(z.slots) { take(SpotKind.SLOT) }
            for ((tree, n) in z.trees) repeat(n) { take(SpotKind.TREE, tree) }
            repeat(z.bushes) { take(SpotKind.BUSH) }
            repeat(z.apples) { take(SpotKind.APPLE) }
            repeat(z.mushrooms) { take(SpotKind.MUSHROOM) }
            // Rocks sit on the odd tiles between the spots.
            rockTiles[z.id] = (1 until SIZE - 1).flatMap { r -> (1 until SIZE - 1).map { c -> c to r } }
                .filter { (c, r) -> zoneGrid[r * SIZE + c] == z.id && !isTrail(c, r) && (c + r) % 2 == 1 }
                .let { odd -> odd.indices.map { odd[(it * 7 + z.id.hashCode().mod(odd.size)) % odd.size] }.distinct() }
        }
        // Loose rocks come last so spots in saves made before they existed keep their indices.
        for (z in zones) for ((c, r) in rockTiles[z.id].orEmpty().take(z.rocks)) spots += ForestSpot(spots.size, z.id, SpotKind.ROCK, null, c, r)
        return spots
    }
}

// ---------------------------------------------------------------- World API
val World.forestSpots: List<ForestSpot> get() = data.forestLayout

private fun World.ensureForest() {
    val f = state.forest
    if (f.seeded && f.spots.size == forestSpots.size) return
    while (f.spots.size < forestSpots.size) f.spots.add(SpotState())
    if (!f.seeded) {
        // Every tree spot starts with a mature tree of its native type; forage spots start stocked.
        for (s in forestSpots) {
            val st = f.spots[s.index]
            when (s.kind) {
                SpotKind.TREE -> { st.tree = s.native; st.stage = TreeStage.MATURE }
                SpotKind.BUSH, SpotKind.APPLE -> st.stock = 2
                SpotKind.MUSHROOM -> st.stock = 1
                SpotKind.SLOT -> {}
                SpotKind.ROCK -> st.stock = 2
            }
        }
        f.seeded = true
    }
}

fun World.spot(i: Int): SpotState { ensureForest(); return state.forest.spots[i] }

fun World.zoneOpen(id: String): Boolean = id in state.forest.zones

fun World.zoneBlocker(id: String): String? {
    val z = data.zones.firstOrNull { it.id == id } ?: return "Unknown zone"
    return when {
        zoneOpen(id) -> "Already open"
        z.unlockLevel > state.level -> "Unlocks at level ${z.unlockLevel}"
        featBlocker("zone_$id") != null -> featBlocker("zone_$id")
        !canAfford(z.cost) -> "Needs " + z.cost.entries.joinToString(", ") { (k, v) -> if (k == "coins") "${v}c" else "$v ${data.name(k)}" }
        else -> null
    }
}

fun World.unlockZone(id: String): Boolean {
    zoneBlocker(id)?.let { events += WorldEvent.Blocked(it); return false }
    pay(data.zones.first { it.id == id }.cost)
    state.forest.zones += id
    events += WorldEvent.ZoneUnlocked(id)
    gainXp(mapOf("B" to 50, "C" to 100, "D" to 200, "E" to 400)[id] ?: 0)
    return true
}

/** Zone A opens for free at level 2. */
internal fun World.autoOpenZones() {
    data.zones.firstOrNull()?.let { if (it.unlockLevel <= state.level && it.cost.isEmpty()) state.forest.zones += it.id }
}

/** Grows saplings into young and mature trees as time passes; a stump on a tree's own spot sprouts
 *  a sapling of it [STUMP_SPROUT_MINUTES] real minutes after the fell (playtest round 4). */
fun World.treeStage(i: Int): TreeStage {
    val s = spot(i)
    val native = forestSpots[i].native
    if (s.stage == TreeStage.STUMP && native != null && state.minutes - s.since >= Time.realSecondsToGameMinutes(STUMP_SPROUT_MINUTES * 60.0)) {
        val def = data.trees.getValue(native)
        s.tree = def.id; s.stage = TreeStage.SAPLING; s.damage = 0; s.plantedAt = state.minutes; s.since = state.minutes
        s.growMinutes = Time.realSecondsToGameMinutes(def.growMinutes * 60)
    }
    if (s.stage == TreeStage.SAPLING || s.stage == TreeStage.YOUNG) {
        val f = (state.minutes - s.plantedAt) / s.growMinutes
        s.stage = when { f >= 1 -> TreeStage.MATURE; f >= 0.4 -> TreeStage.YOUNG; else -> TreeStage.SAPLING }
        if (s.stage == TreeStage.MATURE) s.damage = 0
    }
    return s.stage
}

fun World.treeHp(i: Int): Int {
    val s = spot(i)
    val def = s.tree?.let { data.trees[it] }
    return when (treeStage(i)) {
        TreeStage.SAPLING -> 1
        TreeStage.YOUNG -> 3
        TreeStage.MATURE -> def?.hp ?: 5
        TreeStage.STUMP -> if (s.tree == "ancient_oak") 10 else 2
        TreeStage.EMPTY -> 0
    }
}

/** Why a swing at spot [i] would not land (costs nothing), or null. */
fun World.chopBlocker(i: Int): String? {
    val sp = forestSpots[i]
    val s = spot(i)
    val tier = toolTier(Tool.AXE)
    val def = s.tree?.let { data.trees[it] }
    return when {
        sp.kind != SpotKind.TREE -> "Nothing to chop"
        !zoneOpen(sp.zone) -> "Zone locked"
        treeStage(i) == TreeStage.EMPTY -> "Nothing to chop"
        treeStage(i) == TreeStage.MATURE && def != null && tier < def.minAxe -> "Needs ${data.tools.names[def.minAxe]} axe"
        treeStage(i) == TreeStage.STUMP && tier < 1 -> "Needs Stone axe"
        used(Storage.BARN) >= capacity(Storage.BARN) -> "Barn full"
        state.energy < SWING_ENERGY -> "Too tired"
        else -> null
    }
}

/** One axe swing. Returns true if it landed. */
fun World.chop(i: Int): Boolean {
    chopBlocker(i)?.let { events += WorldEvent.Blocked(it); return false }
    if (!roll(skill("light_swing"))) spendEnergy(SWING_ENERGY)
    stingIfHive(i)
    val s = spot(i)
    val tier = toolTier(Tool.AXE)
    val rng = rng()
    val crit = rng.chance(data.tools.crit[tier] + skill("sharp_edge"))
    val dmg = data.tools.damage[tier] * if (crit) 2 else 1
    s.damage += dmg
    val hp = treeHp(i)
    state.bump("swings")
    events += WorldEvent.Chopped(i, dmg, crit, max(0, hp - s.damage))
    if (s.damage >= hp) fell(i, rng)
    return true
}

private fun World.fell(i: Int, rng: Rng) {
    val s = spot(i)
    val def = s.tree?.let { data.trees[it] }
    when (s.stage) {
        TreeStage.MATURE -> {
            val drops = LinkedHashMap<String, Int>()
            fun drop(k: String, n: Int) { if (n > 0) drops[k] = (drops[k] ?: 0) + n }
            if (def != null) {
                drop("wood", def.wood.first + rng.nextInt(def.wood.last - def.wood.first + 1))
                if (def.hardwood.last > 0) drop("hardwood", def.hardwood.first + rng.nextInt(def.hardwood.last - def.hardwood.first + 1))
                def.extras.forEach { (item, p) -> if (rng.chance(p)) drop(item, 1) }
                if (rng.chance(def.seedChance)) drop(def.seed, 1)
                if (toolTier(Tool.AXE) >= 2 && rng.chance(if (toolTier(Tool.AXE) >= 3) 0.2 else 0.1)) drop("wood", 1)
                if (roll(skill("timber"))) drop("wood", 1)
            }
            drops.forEach { (k, n) -> add(k, n) }
            s.stage = TreeStage.STUMP
            s.damage = 0
            s.since = state.minutes
            state.bump("fells")
            state.bump("fell_${s.tree}")
            state.bump("fells_zone_${forestSpots[i].zone}")
            val xp = def?.xp ?: 1
            events += WorldEvent.Felled(i, s.tree ?: "", drops, xp)
            gainXp(xp)
            if (rng.chance(birdNestChance(s.tree))) openBirdNest(i, rng)
        }
        TreeStage.STUMP -> {
            add("wood", 2)
            if (rng.chance(0.01)) add("old_coin", 1)
            if ((s.tree == "oak" || s.tree == "birch") && rng.chance(0.05)) add("wild_mushroom", 1)
            s.tree = null; s.stage = TreeStage.EMPTY; s.damage = 0; s.since = state.minutes
            events += WorldEvent.StumpCleared(i)
            gainXp(1)
        }
        TreeStage.SAPLING, TreeStage.YOUNG -> {
            if (s.stage == TreeStage.YOUNG) add("wood", 1)
            s.tree = null; s.stage = TreeStage.EMPTY; s.damage = 0; s.since = state.minutes
            events += WorldEvent.StumpCleared(i)
        }
        TreeStage.EMPTY -> {}
    }
}

private fun birdNestChance(tree: String?) = when (tree) { "pine" -> 0.01; "birch" -> 0.03; "oak" -> 0.05; "maple" -> 0.04; "ancient_oak" -> 0.10; else -> 0.0 }

private fun World.openBirdNest(i: Int, rng: Rng) {
    val r = rng.nextDouble()
    val (item, qty) = when {
        r < 0.40 -> listOf("pinecone", "birch_seed", "acorn")[rng.nextInt(3)] to 1 + rng.nextInt(2)
        r < 0.65 -> "coins" to 15 + rng.nextInt(26)
        r < 0.85 -> "seed_" + data.crops.keys.filter { cropUnlocked(it) }.let { it[rng.nextInt(it.size)] } to 1
        r < 0.95 -> "feather" to 1
        r < 0.99 -> "golden_feather" to 1
        else -> "amethyst" to 1
    }
    if (item == "coins") state.coins += qty else add(item, qty)
    state.bump("bird_nests")
    events += WorldEvent.BirdNest(i, item, qty)
}

/** Plants a tree seed (pinecone, acorn, ...) on an empty tree spot. */
fun World.plantTree(i: Int, seedItem: String): Boolean {
    val sp = forestSpots[i]
    val s = spot(i)
    val def = data.trees.values.firstOrNull { it.seed == seedItem } ?: return false
    if (sp.kind != SpotKind.TREE || treeStage(i) != TreeStage.EMPTY || !zoneOpen(sp.zone)) return false
    if ((def.id == "ancient_oak") != (sp.native == "ancient_oak")) { events += WorldEvent.Blocked("Wrong soil for that seed"); return false }
    if (count(seedItem) <= 0) { events += WorldEvent.Blocked("No ${data.name(seedItem)}"); return false }
    remove(seedItem, 1)
    val mult = when (time.season) { Season.SPRING -> 0.8; Season.WINTER -> 1.33; else -> 1.0 }
    s.tree = def.id; s.stage = TreeStage.SAPLING; s.damage = 0; s.plantedAt = state.minutes
    s.growMinutes = Time.realSecondsToGameMinutes(def.growMinutes * 60 * mult)
    state.bump("trees_planted")
    events += WorldEvent.TreePlanted(i, def.id)
    gainXp(1)
    return true
}

/** Taps a bush, apple tree or mushroom spot: collects its stock (0 energy). */
fun World.forage(i: Int): Boolean {
    val sp = forestSpots[i]
    val s = spot(i)
    if (!zoneOpen(sp.zone) || s.stock <= 0) return false
    val item = when (sp.kind) {
        SpotKind.BUSH -> "blackberry"
        SpotKind.APPLE -> "apple"
        SpotKind.MUSHROOM -> if (time.season == Season.FALL && rng().chance(0.4)) "chanterelle" else if (time.hour >= 20 && rng().chance(0.05)) "glowcap" else "wild_mushroom"
        SpotKind.ROCK -> "stone"
        else -> return false
    }
    if (!hasRoom(item)) { events += WorldEvent.Blocked("Barn full"); return false }
    val n = s.stock
    add(item, n)
    if (sp.kind == SpotKind.ROCK) state.bump("forest_stone", n.toLong())
    s.stock = 0
    events += WorldEvent.Foraged(i, item, n)
    gainXp(n)
    return true
}

fun World.collectBranch(c: Int, r: Int): Boolean {
    if (!state.forest.branches.remove(c * 1000 + r)) return false
    add("wood", 1)
    if (rng().chance(0.10)) add("resin", 1)
    events += WorldEvent.Foraged(-1, "wood", 1)
    return true
}

/** 06:00 forest update: branches, forage regrowth, reseeding and rotting stumps. */
internal fun World.forestDawn(day: Int) {
    ensureForest()
    autoOpenZones()
    val f = state.forest
    val rng = Rng.forDay(state.seed, day, 23)
    val season = time.season
    for (sp in forestSpots) {
        val s = f.spots[sp.index]
        when (sp.kind) {
            SpotKind.TREE -> {
                treeStage(sp.index)
                val idleDays = (state.minutes - s.since) / Time.MINUTES_PER_DAY
                if (s.stage == TreeStage.STUMP && idleDays >= 1) { s.stage = TreeStage.EMPTY; s.tree = null; s.since = state.minutes }
                else if (s.stage == TreeStage.EMPTY && sp.native != null) {
                    val def = data.trees.getValue(sp.native)
                    s.tree = def.id; s.stage = TreeStage.SAPLING; s.plantedAt = state.minutes
                    s.growMinutes = Time.realSecondsToGameMinutes(def.growMinutes * 60)
                }
            }
            SpotKind.BUSH -> if (season == Season.SUMMER && day % 2 == 0) s.stock = minOf(3, s.stock + 1)
            SpotKind.APPLE -> if ((season == Season.SUMMER || season == Season.FALL) && day % 2 == 0) s.stock = minOf(3, s.stock + 1)
            SpotKind.MUSHROOM -> if (season != Season.WINTER) s.stock = minOf(if (state.weather.waters) 2 else 1, s.stock + 1)
            SpotKind.ROCK -> s.stock = minOf(2, s.stock + 1)
            SpotKind.SLOT -> {}
        }
    }
    hiveDawn(day, rng)
    // Fallen branches on free tiles of open zones.
    val spawn = if (state.weather.stormy) 5 else 3
    f.branches.removeAll { ForestLayout.zoneOf(it / 1000, it % 1000) == null }
    val tiles = ForestLayout.clearingTiles
    repeat(spawn) {
        if (f.branches.size >= 8) return@repeat
        val (c, r) = tiles[rng.nextInt(tiles.size)]
        if (ForestLayout.zoneOf(c, r) in f.zones && forestSpots.none { it.c == c && it.r == r }) f.branches += c * 1000 + r
    }
}

const val SWING_ENERGY = 2
const val STUMP_SPROUT_MINUTES = 5.0
