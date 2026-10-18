package com.ditherfarm.game

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max

// ------------------------------------------------------------------ definitions
class NodeDef(val id: String, val hp: Int, val minPick: Int, val drops: Map<String, IntRange>, val xp: Int, val bonus: Map<String, Double>)
class QuarryLevel(val level: Int, val unlockLevel: Int, val cost: Map<String, Int>, val spots: Int, val respawnMinutes: Double, val weights: Map<String, Int>)
class BandDef(val band: Int, val floors: IntRange, val grid: Int, val density: Double, val dark: Double, val geode: String, val weights: Map<String, Int>)
class GateDef(val floor: Int, val level: Int, val pick: Int, val item: String?, val chest: Map<String, Int>)
class GeodeDef(val fee: Int, val oreChance: Double, val ores: List<Triple<String, Int, Int>>, val mineral: Double, val gems: List<Triple<String, Double, Int>>, val prismatic: Double)

class MineData(
    val nodes: Map<String, NodeDef>,
    val quarry: List<QuarryLevel>,
    val bands: List<BandDef>,
    val minFloor: Map<String, Int>,
    val gates: List<GateDef>,
    val minerals: Map<String, List<String>>,
    val geodes: Map<String, GeodeDef>,
    val repairLevel: Int,
    val repairCost: Map<String, Int>,
) {
    fun band(floor: Int): BandDef = bands.firstOrNull { floor in it.floors } ?: bands.last()

    companion object {
        val EMPTY = MineData(emptyMap(), emptyList(), emptyList(), emptyMap(), emptyList(), emptyMap(), emptyMap(), 4, emptyMap())

        fun parse(text: String): MineData {
            val o = JSONObject(text)
            fun range(a: JSONArray) = a.getInt(0)..a.getInt(1)
            val nodes = o.getJSONArray("nodes").objects().map { n ->
                val drops = n.getJSONObject("drops").let { d -> d.keys().asSequence().associateWith { range(d.getJSONArray(it)) } }
                val bonus = n.getJSONObject("bonus").let { b -> b.keys().asSequence().associateWith { b.getDouble(it) } }
                NodeDef(n.getString("id"), n.getInt("hp"), n.getInt("minPick"), drops, n.getInt("xp"), bonus)
            }.associateBy { it.id }
            val quarry = o.getJSONArray("quarry").objects().map { q ->
                QuarryLevel(q.getInt("level"), q.getInt("unlock"), q.getJSONObject("cost").toIntMap(), q.getInt("spots"), q.getDouble("respawnMinutes"), q.getJSONObject("weights").toIntMap())
            }
            val bands = o.getJSONArray("bands").objects().map { b ->
                BandDef(b.getInt("band"), range(b.getJSONArray("floors")), b.getInt("grid"), b.getDouble("density"), b.getDouble("dark"), b.getString("geode"), b.getJSONObject("weights").toIntMap())
            }
            val gates = o.getJSONArray("gates").objects().map { g ->
                GateDef(g.getInt("floor"), g.getInt("level"), g.getInt("pick"), if (g.has("item")) g.getString("item") else null, g.getJSONObject("chest").toIntMap())
            }
            val minerals = o.getJSONObject("minerals").let { m -> m.keys().asSequence().associateWith { m.getJSONArray(it).strings().map { s -> "mineral_$s" } } }
            val geodes = o.getJSONObject("geodes").let { gs ->
                gs.keys().asSequence().associateWith { k ->
                    val g = gs.getJSONObject(k)
                    val ore = g.getJSONArray("ore")
                    GeodeDef(
                        g.getInt("fee"), ore.getDouble(0),
                        ore.getJSONArray(1).let { a -> (0 until a.length()).map { val e = a.getJSONArray(it); Triple(e.getString(0), e.getInt(1), e.getInt(2)) } },
                        g.getDouble("mineral"),
                        g.getJSONArray("gems").let { a -> (0 until a.length()).map { val e = a.getJSONArray(it); Triple(e.getString(0), e.getDouble(1), e.getInt(2)) } },
                        g.getDouble("prismatic"),
                    )
                }
            }
            val repair = o.getJSONObject("repair")
            return MineData(nodes, quarry, bands, o.getJSONObject("minFloor").toIntMap(), gates, minerals, geodes, repair.getInt("level"), repair.getJSONObject("cost").toIntMap())
        }
    }
}

// ------------------------------------------------------------------ state
class Cell(var node: String?, var damage: Int = 0)

class MineState {
    var repaired = false
    var quarryLevel = 1
    /** Quarry spot nodes (null = empty) and the game minute an empty spot regrows. */
    val quarry = ArrayList<Cell>()
    val respawnAt = ArrayList<Double>()
    /** 0 = surface. */
    var floor = 0
    var deepestStop = 0
    var deepestFloor = 0
    var visits = 0L
    var grid = 0
    val cells = ArrayList<Cell>()
    var ladder = -1
    var ladderFound = false
    var breaks = 0
    var dark = false
    val torches = LinkedHashSet<Int>()
    /** Cells opened this visit (entry + broken rocks); the lantern lights around them. */
    val opened = LinkedHashSet<Int>()
    val gatesOpened = LinkedHashSet<Int>()
    /** Cave-in markers (cracked beams) on this floor, and the one rumbling right now. */
    val markers = LinkedHashSet<Int>()
    var rumble = -1
    var rumbleEnd = 0.0
    var braceTaps = 0

    fun toJson() = JSONObject().apply {
        put("repaired", repaired); put("quarryLevel", quarryLevel); put("floor", floor); put("deepestStop", deepestStop)
        put("deepestFloor", deepestFloor); put("visits", visits); put("grid", grid); put("ladder", ladder)
        put("ladderFound", ladderFound); put("breaks", breaks); put("dark", dark)
        put("quarry", JSONArray().apply { quarry.forEachIndexed { i, c -> put(JSONObject().put("n", c.node ?: "").put("d", c.damage).put("at", respawnAt.getOrElse(i) { 0.0 })) } })
        put("cells", JSONArray().apply { cells.forEach { put(JSONObject().put("n", it.node ?: "").put("d", it.damage)) } })
        put("torches", JSONArray(torches.toList()))
        put("opened", JSONArray(opened.toList()))
        put("markers", JSONArray(markers.toList()))
        put("gates", JSONArray(gatesOpened.toList()))
    }

    companion object {
        fun fromJson(o: JSONObject) = MineState().apply {
            repaired = o.optBoolean("repaired"); quarryLevel = o.optInt("quarryLevel", 1); floor = o.optInt("floor")
            deepestStop = o.optInt("deepestStop"); deepestFloor = o.optInt("deepestFloor"); visits = o.optLong("visits")
            grid = o.optInt("grid"); ladder = o.optInt("ladder", -1); ladderFound = o.optBoolean("ladderFound")
            breaks = o.optInt("breaks"); dark = o.optBoolean("dark")
            o.optJSONArray("quarry")?.objects()?.forEach { q ->
                quarry += Cell(q.getString("n").ifEmpty { null }, q.optInt("d")); respawnAt += q.optDouble("at", 0.0)
            }
            o.optJSONArray("cells")?.objects()?.forEach { c -> cells += Cell(c.getString("n").ifEmpty { null }, c.optInt("d")) }
            o.optJSONArray("torches")?.let { a -> (0 until a.length()).forEach { torches += a.getInt(it) } }
            o.optJSONArray("opened")?.let { a -> (0 until a.length()).forEach { opened += a.getInt(it) } }
            o.optJSONArray("markers")?.let { a -> (0 until a.length()).forEach { markers += a.getInt(it) } }
            o.optJSONArray("gates")?.let { a -> (0 until a.length()).forEach { gatesOpened += a.getInt(it) } }
        }
    }
}

/** Fixed quarry spot tiles on the 14x14 surface map (first 12 at Lv1, 16 at Lv2, 20 at Lv3). */
object MineLayout {
    const val SURFACE = 14
    val quarryTiles: List<Pair<Int, Int>> = listOf(
        5 to 7, 6 to 8, 7 to 7, 8 to 8, 6 to 6, 8 to 6, 5 to 9, 7 to 9, 9 to 7, 4 to 8, 9 to 9, 6 to 10,
        4 to 6, 10 to 8, 8 to 10, 5 to 11, 10 to 6, 3 to 7, 7 to 11, 11 to 9,
    )
    val mouth = 3 to 3
    val slots = listOf(10 to 3, 12 to 6, 2 to 11, 4 to 12)
}

// ------------------------------------------------------------------ World API
val World.mine: MineState get() = state.mine

private fun World.ensureQuarry() {
    val m = state.mine
    val q = data.mine.quarry.getOrNull(m.quarryLevel - 1) ?: return
    while (m.quarry.size < q.spots) { m.quarry += Cell(null); m.respawnAt += 0.0 }
}

private fun rollWeighted(rng: Rng, weights: Map<String, Int>, filter: (String) -> Boolean = { true }): String? {
    val w = weights.filter { filter(it.key) }
    val total = w.values.sum()
    if (total <= 0) return null
    var r = rng.nextInt(total)
    for ((k, v) in w) { r -= v; if (r < 0) return k }
    return w.keys.last()
}

/** Regrows quarry spots whose timer has passed (also runs during offline catch-up). */
internal fun World.tickQuarry() {
    if (state.level < (data.mine.quarry.firstOrNull()?.unlockLevel ?: 3)) return
    ensureQuarry()
    val m = state.mine
    val q = data.mine.quarry[m.quarryLevel - 1]
    m.quarry.forEachIndexed { i, c ->
        if (c.node == null && state.minutes >= m.respawnAt[i]) {
            c.node = rollWeighted(rng(), q.weights); c.damage = 0
        }
    }
}

fun World.quarryNode(i: Int): Cell? { ensureQuarry(); return state.mine.quarry.getOrNull(i) }

fun World.pickTier() = toolTier(Tool.PICKAXE)

/** Why a pickaxe swing at [node] would not land (costs nothing), or null. */
fun World.mineBlocker(node: String?): String? {
    val def = node?.let { data.mine.nodes[it] } ?: return "Nothing to mine"
    return when {
        pickTier() < def.minPick -> "Needs ${data.tools.names[def.minPick]} pickaxe"
        used(Storage.BARN) >= capacity(Storage.BARN) -> "Barn full"
        state.energy < SWING_ENERGY -> "Too tired"
        else -> null
    }
}

/** Swing at a cell the caller already checked with [mineBlocker]; returns drops when it breaks. */
private fun World.hit(cell: Cell, depth: Int, where: Int, quarry: Boolean): Map<String, Int>? {
    val def = data.mine.nodes.getValue(cell.node!!)
    spendEnergy(SWING_ENERGY)
    val rng = rng()
    val tier = pickTier()
    val crit = rng.chance(data.tools.crit[tier])
    cell.damage += data.tools.damage[tier] * if (crit) 2 else 1
    state.bump("pick_swings")
    events += WorldEvent.Mined(where, quarry, cell.node!!, crit, max(0, def.hp - cell.damage))
    if (cell.damage < def.hp) return null
    val drops = LinkedHashMap<String, Int>()
    fun drop(k: String, n: Int) { if (n > 0) drops[k] = (drops[k] ?: 0) + n }
    for ((k, r) in def.drops) drop(k, r.first + rng.nextInt(r.last - r.first + 1))
    def.bonus.forEach { (k, p) -> if (rng.chance(p)) drop(k, 1) }
    if (def.id == "geode_rock") { drops.remove("geode"); drop(data.mine.band(max(1, depth)).geode, 1) }
    if (depth > 0 && def.id != "stone" && def.id != "rubble" && rng.chance(depth / 100.0)) drops.keys.firstOrNull()?.let { drop(it, 1) }
    if (def.id == "crate") {
        when (rng.nextInt(3)) {
            0 -> state.coins += 20 + rng.nextInt(41)
            1 -> drop("torch", 2 + rng.nextInt(3))
            else -> drop("seed_" + data.crops.values.filter { time.season in it.seasons && cropUnlocked(it.id) }.let { if (it.isEmpty()) "wheat" else it[rng.nextInt(it.size)].id }, 2)
        }
    }
    drops.forEach { (k, n) -> add(k, n) }
    cell.node = null
    cell.damage = 0
    state.bump("rocks_broken")
    events += WorldEvent.RockBroken(where, quarry, def.id, drops, def.xp)
    gainXp(def.xp)
    return drops
}

/** One pickaxe swing at quarry spot [i]. Returns true if it landed. */
fun World.mineQuarry(i: Int): Boolean {
    val c = quarryNode(i)?.takeIf { it.node != null } ?: return false
    mineBlocker(c.node)?.let { events += WorldEvent.Blocked(it); return false }
    if (hit(c, 0, i, quarry = true) != null) {
        val q = data.mine.quarry[state.mine.quarryLevel - 1]
        val jitter = 0.8 + rng().nextDouble() * 0.4
        state.mine.respawnAt[i] = state.minutes + Time.realSecondsToGameMinutes(q.respawnMinutes * 60 * jitter)
    }
    return true
}

fun World.quarryUpgrade(): QuarryLevel? = data.mine.quarry.getOrNull(state.mine.quarryLevel)

fun World.upgradeQuarry(): Boolean {
    val next = quarryUpgrade() ?: return false
    if (next.unlockLevel > state.level) { events += WorldEvent.Blocked("Unlocks at level ${next.unlockLevel}"); return false }
    if (!canAfford(next.cost)) { events += WorldEvent.Blocked("Not enough materials"); return false }
    pay(next.cost)
    state.mine.quarryLevel = next.level
    ensureQuarry()
    gainXp(30)
    return true
}

fun World.repairBlocker(): String? = when {
    state.mine.repaired -> "Already repaired"
    state.level < data.mine.repairLevel -> "Repair at level ${data.mine.repairLevel}"
    !canAfford(data.mine.repairCost) -> "Needs 25 Wood and 100 coins"
    else -> null
}

fun World.repairMine(): Boolean {
    repairBlocker()?.let { events += WorldEvent.Blocked(it); return false }
    pay(data.mine.repairCost)
    state.mine.repaired = true
    gainXp(25)
    return true
}

/** Why the gate on [floor] stays shut, or null if there is none or it opens. */
fun World.gateBlocker(floor: Int): String? {
    val gate = data.mine.gates.firstOrNull { it.floor == floor } ?: return null
    if (floor in state.mine.gatesOpened) return null
    return when {
        state.level < gate.level -> "Gate opens at level ${gate.level}"
        pickTier() < gate.pick -> "Needs ${data.tools.names[gate.pick]} pickaxe"
        gate.item != null && count(gate.item) <= 0 -> "Needs a ${data.name(gate.item)}"
        else -> null
    }
}

/** Elevator stops the player can ride to (0 = surface). */
fun World.elevatorStops(): List<Int> = listOf(0) + (5..state.mine.deepestStop step 5).toList()

/** Rides the elevator or takes a ladder to [floor] (generates a fresh floor). */
fun World.enterFloor(floor: Int): Boolean {
    val m = state.mine
    if (floor <= 0) { m.floor = 0; events += WorldEvent.FloorEntered(0); return true }
    if (!m.repaired) { events += WorldEvent.Blocked("Repair the mine first"); return false }
    if (floor > 40) return false
    gateBlocker(floor)?.let { events += WorldEvent.Blocked(it); return false }
    m.floor = floor
    m.visits++
    generateFloor(floor)
    val firstTime = floor > m.deepestFloor
    if (firstTime) {
        m.deepestFloor = floor
        gainXp(5 * data.mine.band(floor).band)
    }
    if (floor % 5 == 0 && floor > m.deepestStop) { m.deepestStop = floor; gainXp(25); events += WorldEvent.StopUnlocked(floor) }
    data.mine.gates.firstOrNull { it.floor == floor }?.let { g ->
        if (m.gatesOpened.add(floor)) {
            g.chest.forEach { (k, v) -> if (k == "coins") state.coins += v else add(k, v) }
            events += WorldEvent.GateOpened(floor, g.chest)
        }
    }
    events += WorldEvent.FloorEntered(floor)
    return true
}

private fun World.generateFloor(floor: Int) {
    val m = state.mine
    val band = data.mine.band(floor)
    val rng = Rng(state.seed * 31 + floor * 7919L + m.visits * 104729L)
    val n = band.grid
    m.grid = n
    m.cells.clear()
    m.torches.clear()
    m.opened.clear()
    m.markers.clear()
    m.rumble = -1
    m.ladderFound = false
    m.breaks = 0
    m.dark = rng.chance(band.dark)
    val entry = entryCell(n)
    m.opened += entry
    for (i in 0 until n * n) {
        val c = i % n; val r = i / n
        val border = c == 0 || r == 0 || c == n - 1 || r == n - 1
        val node = when {
            border -> null
            i == entry -> null
            rng.chance(band.density) -> rollWeighted(rng, band.weights) { (data.mine.minFloor[it] ?: 0) <= floor }
            else -> null
        }
        m.cells += Cell(node)
    }
    // A couple of extras: mushrooms and sometimes a crate.
    val empties = m.cells.indices.filter { m.cells[it].node == null && it != entry && !isBorder(it, n) }.toMutableList()
    repeat((if (band.band == 1) 0 else 1) + rng.nextInt(3)) {
        if (empties.isNotEmpty()) m.cells[empties.removeAt(rng.nextInt(empties.size))].node = "glow_mushroom"
    }
    if (rng.chance(0.2) && empties.isNotEmpty()) m.cells[empties.removeAt(rng.nextInt(empties.size))].node = "crate"
    // Ladder: a breakable rock at least 4 tiles from the entry (no ladder on floor 40).
    val ec = entry % n; val er = entry / n
    val candidates = m.cells.indices.filter { i ->
        val node = m.cells[i].node ?: return@filter false
        val def = data.mine.nodes[node] ?: return@filter false
        node != "glow_mushroom" && node != "crate" && def.minPick <= pickTier() && abs(i % n - ec) + abs(i / n - er) >= 4
    }
    m.ladder = if (floor >= 40 || candidates.isEmpty()) -1 else candidates[rng.nextInt(candidates.size)]
    // Cave-in markers from floor 8: a quarter of floors get one or two cracked beams.
    if (floor >= 8 && rng.chance(0.25)) {
        repeat(1 + rng.nextInt(2)) { if (empties.isNotEmpty()) m.markers += empties.removeAt(rng.nextInt(empties.size)) }
    }
}

/** The cell the ladder or elevator drops the player onto. */
fun entryCell(n: Int) = (n - 2) * n + (n / 2)

private fun isBorder(i: Int, n: Int) = i % n == 0 || i / n == 0 || i % n == n - 1 || i / n == n - 1

/** True when cell [i] can be seen: always on normal floors; on dark floors near a torch (3) or, with a lantern, near opened ground (2). */
fun World.lit(i: Int): Boolean {
    val m = state.mine
    if (!m.dark) return true
    val n = m.grid
    fun near(set: Set<Int>, r: Int) = set.any { t -> max(abs(t % n - i % n), abs(t / n - i / n)) <= r }
    return near(m.torches, 3) || (count("lantern") > 0 && near(m.opened, 2))
}

fun World.placeTorch(i: Int): Boolean {
    val m = state.mine
    if (m.floor == 0 || i !in m.cells.indices || m.cells[i].node != null || i in m.torches) return false
    if (!removeAny("torch", 1)) { events += WorldEvent.Blocked("No torches"); return false }
    m.torches += i
    return true
}

/** Swing at floor cell [i]. Mushrooms are picked for free. */
fun World.mineFloor(i: Int): Boolean {
    val m = state.mine
    val cell = m.cells.getOrNull(i) ?: return false
    val node = cell.node ?: return false
    if (node == "glow_mushroom") {
        add("cave_mushroom", 1); cell.node = null; m.opened += i
        events += WorldEvent.RockBroken(i, false, node, mapOf("cave_mushroom" to 1), 1); gainXp(1)
        return true
    }
    mineBlocker(node)?.let { events += WorldEvent.Blocked(it); return false }
    val n = m.grid
    val near = m.ladder >= 0 && !m.ladderFound && max(abs(m.ladder % n - i % n), abs(m.ladder / n - i / n)) <= 1
    val broke = hit(cell, m.floor, i, quarry = false)
    if (near && broke == null) events += WorldEvent.HollowKnock(i, i == m.ladder)
    if (broke != null) {
        m.breaks++
        m.opened += i
        maybeRumble(i)
        if (i == m.ladder) { m.ladderFound = true; events += WorldEvent.LadderFound(i) }
        else if (!m.ladderFound && m.breaks >= 10 && m.ladder >= 0) {
            // Pity rule: the ladder moves under the rock just broken.
            m.ladder = i; m.ladderFound = true; events += WorldEvent.LadderFound(i)
        }
    }
    return true
}

// ------------------------------------------------------------------ cave-ins
private const val RUMBLE_SECONDS = 2.5
const val BRACE_TAPS = 3

/** Breaking a rock within 2 tiles of a cracked beam has a 30% chance to start a rumble. */
private fun World.maybeRumble(i: Int) {
    val m = state.mine
    if (m.rumble >= 0) return
    val n = m.grid
    val marker = m.markers.firstOrNull { max(abs(it % n - i % n), abs(it / n - i / n)) <= 2 } ?: return
    if (!rng().chance(0.30)) return
    m.rumble = marker
    m.braceTaps = 0
    m.rumbleEnd = state.minutes + Time.realSecondsToGameMinutes(RUMBLE_SECONDS)
    events += WorldEvent.Rumble(marker)
}

/** Seconds left to brace the rumbling beam (0 when none). */
fun World.rumbleSecondsLeft(): Double {
    val m = state.mine
    return if (m.rumble < 0) 0.0 else Time.gameMinutesToRealSeconds(max(0.0, m.rumbleEnd - state.minutes))
}

/** One tap on a rumbling beam; three taps brace it (+5 XP). */
fun World.brace(cell: Int): Boolean {
    val m = state.mine
    if (cell != m.rumble) return false
    m.braceTaps++
    if (m.braceTaps < BRACE_TAPS) return true
    m.markers.remove(cell)
    m.rumble = -1
    state.bump("braced")
    events += WorldEvent.Braced(cell)
    gainXp(5)
    return true
}

/** A Support Beam makes a cracked beam safe for this visit. */
fun World.placeBeam(cell: Int): Boolean {
    val m = state.mine
    if (cell !in m.markers || cell == m.rumble) return false
    if (!removeAny("support_beam", 1)) { events += WorldEvent.Blocked("Needs a Support Beam"); return false }
    m.markers.remove(cell)
    events += WorldEvent.Braced(cell)
    return true
}

/** An unbraced rumble collapses: rubble fills 3x3 around the beam and costs 5 energy. */
internal fun World.tickMine() {
    val m = state.mine
    if (m.rumble < 0 || state.minutes < m.rumbleEnd) return
    val marker = m.rumble
    val n = m.grid
    m.rumble = -1
    m.markers.remove(marker)
    val entry = entryCell(n)
    for (dr in -1..1) for (dc in -1..1) {
        val c = marker % n + dc; val r = marker / n + dr
        if (c <= 0 || r <= 0 || c >= n - 1 || r >= n - 1) continue
        val i = r * n + c
        if (i == entry || (m.ladderFound && i == m.ladder) || i in m.torches) continue
        if (m.cells[i].node == null) { m.cells[i].node = "rubble"; m.cells[i].damage = 0 }
    }
    state.energy = max(0.0, state.energy - 5)
    events += WorldEvent.CaveIn(marker)
}

/** Rope Ladder: drop to the next floor from any empty tile (not past a gate floor). */
fun World.useRopeLadder(): Boolean {
    val m = state.mine
    if (m.floor <= 0) return false
    if (data.mine.gates.any { it.floor == m.floor + 1 } || m.floor % 10 == 0) { events += WorldEvent.Blocked("Too deep for a rope here"); return false }
    if (!removeAny("rope_ladder", 1)) { events += WorldEvent.Blocked("No rope ladder"); return false }
    return enterFloor(m.floor + 1)
}

fun World.descend(): Boolean {
    val m = state.mine
    if (!m.ladderFound) return false
    return enterFloor(m.floor + 1)
}

// ------------------------------------------------------------------ geodes
fun World.crackBlocker(geode: String): String? {
    val g = data.mine.geodes[geode] ?: return "Not a geode"
    return when {
        !isBuilt("geode_anvil") -> "Build the Geode Anvil first"
        count(geode) <= 0 -> "No ${data.name(geode)}"
        state.coins < g.fee -> "Needs ${g.fee} coins"
        used(Storage.BARN) >= capacity(Storage.BARN) -> "Barn full"
        else -> null
    }
}

/** Cracks one geode at the Anvil for its fee; returns what was inside. */
fun World.crackGeode(geode: String): Map<String, Int> {
    crackBlocker(geode)?.let { events += WorldEvent.Blocked(it); return emptyMap() }
    val g = data.mine.geodes.getValue(geode)
    remove(geode, 1)
    state.coins -= g.fee
    val rng = rng()
    val out = LinkedHashMap<String, Int>()
    if (rng.chance(g.prismatic)) out["prismatic_shard"] = 1
    else {
        var r = rng.nextDouble()
        var gem: Triple<String, Double, Int>? = null
        for (t in g.gems) { if (r < t.second) { gem = t; break }; r -= t.second }
        when {
            gem != null -> out[gem.first] = gem.third
            rng.chance(g.oreChance / (1 - g.gems.sumOf { it.second })) -> {
                val (item, lo, hi) = g.ores[rng.nextInt(g.ores.size)]
                out[item] = lo + rng.nextInt(hi - lo + 1)
            }
            else -> data.mine.minerals[geode]?.let { out[it[rng.nextInt(it.size)]] = 1 }
        }
    }
    out.forEach { (k, n) -> add(k, n) }
    state.bump("geodes")
    events += WorldEvent.GeodeCracked(geode, out)
    gainXp(5)
    return out
}
