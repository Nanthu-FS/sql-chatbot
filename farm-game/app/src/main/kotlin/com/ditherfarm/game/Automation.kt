package com.ditherfarm.game

import kotlin.math.max

// ------------------------------------------------------------------ field geometry
/**
 * Land comes in eight fenced field blocks (A to H) of 4x4 plots, bought whole. Plot
 * indices 0..127 are field plots (16 per block, row by row inside a block); 128..139 are
 * greenhouse plots. Fixtures stand on the 5x5 corners of a block and only cover that block.
 */
object FieldLayout {
    const val BLOCKS = 8
    const val SIDE = 4
    const val PER_BLOCK = SIDE * SIDE
    const val VSIDE = SIDE + 1
    const val VERTS_PER_BLOCK = VSIDE * VSIDE
    const val GH_COLS = 4
    const val GH_ROWS = 3
    const val GREENHOUSE = GH_COLS * GH_ROWS
    const val FIELD = BLOCKS * PER_BLOCK
    const val TOTAL = FIELD + GREENHOUSE

    /** Level each block unlocks at, and its price in coins (block A is free). */
    val UNLOCK = intArrayOf(1, 3, 5, 7, 10, 13, 16, 20)
    val PRICE = intArrayOf(0, 500, 1500, 3000, 6000, 12000, 20000, 35000)

    fun name(block: Int) = ('A' + block).toString()
    fun blockOf(plot: Int) = plot / PER_BLOCK
    /** Cell (column, row) of a field plot inside its block. */
    fun cell(plot: Int) = (plot % PER_BLOCK) % SIDE to (plot % PER_BLOCK) / SIDE
    fun plotAt(block: Int, c: Int, r: Int) = block * PER_BLOCK + r * SIDE + c
    fun plotsOf(block: Int) = block * PER_BLOCK until (block + 1) * PER_BLOCK

    fun isGreenhouse(plot: Int) = plot >= FIELD

    /** Greenhouse cell of plot 128..139. */
    fun ghCell(plot: Int) = (plot - FIELD) % GH_COLS to (plot - FIELD) / GH_COLS

    fun vertex(block: Int, vc: Int, vr: Int) = block * VERTS_PER_BLOCK + vr * VSIDE + vc
    fun vertexBlock(v: Int) = v / VERTS_PER_BLOCK
    fun vertexCell(v: Int) = (v % VERTS_PER_BLOCK) % VSIDE to (v % VERTS_PER_BLOCK) / VSIDE

    /** True if a fixture of [radius] at [vertex] covers field plot [plot] (same block only). */
    fun covers(vertex: Int, radius: Int, plot: Int): Boolean {
        if (plot >= FIELD || blockOf(plot) != vertexBlock(vertex)) return false
        val (vc, vr) = vertexCell(vertex)
        val (c, r) = cell(plot)
        return c in vc - radius until vc + radius && r in vr - radius until vr + radius
    }
}

/** Per-block settings: the crop workers replant and where the harvest goes. */
class FieldState {
    var crop: String? = null
    /** True: crates go on the truck to be sold. False: into the Silo. */
    var toTruck = true
    /** The farmhand's crate at the field edge, waiting for the harvester. */
    val crate = LinkedHashMap<String, Int>()

    fun toJson() = org.json.JSONObject().apply {
        crop?.let { put("crop", it) }; put("truck", toTruck); put("crate", org.json.JSONObject(crate as Map<*, *>))
    }

    companion object {
        fun fromJson(o: org.json.JSONObject) = FieldState().apply {
            crop = if (o.has("crop")) o.getString("crop") else null
            toTruck = o.optBoolean("truck", true)
            o.optJSONObject("crate")?.toIntMap()?.forEach { (k, v) -> crate[k] = v }
        }
    }
}

fun World.blockOwned(block: Int) = state.plots[block * FieldLayout.PER_BLOCK].owned

fun World.blockBlocker(block: Int): String? = when {
    block !in 0 until FieldLayout.BLOCKS -> "No such field"
    blockOwned(block) -> "Already yours"
    state.level < FieldLayout.UNLOCK[block] -> "Field ${FieldLayout.name(block)} unlocks at level ${FieldLayout.UNLOCK[block]}"
    state.coins < blockPrice(block) -> "Needs ${blockPrice(block)} coins"
    else -> null
}

fun World.buyBlock(block: Int): Boolean {
    blockBlocker(block)?.let { events += WorldEvent.Blocked(it); return false }
    val price = blockPrice(block)
    state.coins -= price
    for (i in FieldLayout.plotsOf(block)) state.plots[i].owned = true
    gainXp(maxOf(10, price / 50))
    events += WorldEvent.BlockBought(block, price)
    return true
}

/** Version 1 saves had an 8x6 field of single plots: move them into blocks, refund fixtures. */
internal fun World.migrateLayout() {
    if (state.version >= 2) return
    val old = ArrayList(state.plots)
    val oldField = minOf(48, old.size)
    val owned = old.take(oldField).count { it.owned }
    state.plots.clear()
    repeat(FieldLayout.TOTAL) { state.plots.add(PlotState(it)) }
    for (i in 0 until oldField) copyPlot(old[i], state.plots[i])
    for (k in 0 until FieldLayout.GREENHOUSE) old.getOrNull(48 + k)?.let { copyPlot(it, state.plots[FieldLayout.FIELD + k]) }
    val blocks = maxOf(1, (owned + FieldLayout.PER_BLOCK - 1) / FieldLayout.PER_BLOCK)
    for (b in 0 until blocks) for (i in FieldLayout.plotsOf(b)) state.plots[i].owned = true
    state.fixtures.values.forEach { add(it, 1) }
    state.fixtures.clear()
    state.version = GameState.VERSION
}

private fun copyPlot(from: PlotState, to: PlotState) {
    to.owned = from.owned; to.crop = from.crop; to.growth = from.growth; to.watered = from.watered; to.cared = from.cared
    to.withered = from.withered; to.scorched = from.scorched; to.battered = from.battered
    to.fertSilver = from.fertSilver; to.fertGold = from.fertGold; to.harvests = from.harvests; to.lastCrop = from.lastCrop
}

enum class FixtureKind { WATER, WIND, LIGHTNING }

enum class Fixture(val item: String, val kind: FixtureKind, val radius: Int) {
    COPPER_SPRINKLER("copper_sprinkler", FixtureKind.WATER, 1),
    IRON_SPRINKLER("iron_sprinkler", FixtureKind.WATER, 2),
    GOLD_SPRINKLER("gold_sprinkler", FixtureKind.WATER, 3),
    SCARECROW("scarecrow", FixtureKind.WIND, 3),
    LIGHTNING_ROD("lightning_rod", FixtureKind.LIGHTNING, 5);

    companion object {
        fun of(item: String?) = entries.firstOrNull { it.item == item }
    }
}

// ------------------------------------------------------------------ fixtures
fun World.fixtureAt(vertex: Int): Fixture? = Fixture.of(state.fixtures[vertex])

fun World.covered(plot: Int, kind: FixtureKind): Boolean =
    state.fixtures.any { (v, item) -> Fixture.of(item)?.let { it.kind == kind && FieldLayout.covers(v, it.radius, plot) } == true }

fun World.placeBlocker(vertex: Int, item: String): String? = when {
    Fixture.of(item) == null -> "Can't place that"
    vertex !in 0 until FieldLayout.BLOCKS * FieldLayout.VERTS_PER_BLOCK -> "Place it between plots"
    !blockOwned(FieldLayout.vertexBlock(vertex)) -> "Buy this field first"
    state.fixtures.containsKey(vertex) -> "Something is already there"
    count(item) <= 0 -> "You have no ${data.name(item)}"
    else -> null
}

fun World.placeFixture(vertex: Int, item: String): Boolean {
    placeBlocker(vertex, item)?.let { events += WorldEvent.Blocked(it); return false }
    remove(item, 1)
    state.fixtures[vertex] = item
    // A new sprinkler waters its plots right away.
    if (Fixture.of(item)?.kind == FixtureKind.WATER) for (p in state.plots) if (p.crop != null && !p.needsClearing && covered(p.index, FixtureKind.WATER)) p.watered = true
    events += WorldEvent.FixturePlaced(vertex, item)
    return true
}

fun World.pickUpFixture(vertex: Int): Boolean {
    val item = state.fixtures[vertex] ?: return false
    if (used(Storage.BARN) >= capacity(Storage.BARN)) { events += WorldEvent.Blocked("Barn full"); return false }
    state.fixtures.remove(vertex)
    add(item, 1)
    events += WorldEvent.FixtureRemoved(vertex, item)
    return true
}

// ------------------------------------------------------------------ greenhouse
val World.greenhouseOpen get() = isBuilt("greenhouse")

/** Owns the greenhouse plots once it is built (and on older saves). */
internal fun World.syncGreenhouse() {
    while (state.plots.size < FieldLayout.TOTAL) state.plots.add(PlotState(state.plots.size))
    if (greenhouseOpen) for (i in FieldLayout.FIELD until FieldLayout.TOTAL) state.plots[i].owned = true
}

// ------------------------------------------------------------------ timers
/** Real seconds until a timed building (Logging Hut, Tree Nursery) runs again. */
fun World.shedSecondsLeft(id: String): Double =
    Time.gameMinutesToRealSeconds(max(0.0, (state.shedNext[id] ?: state.minutes) - state.minutes))

/** The Harvest and Planter Sheds were replaced by farmhands: refund any that were built. */
internal fun World.retireSheds() {
    for ((id, coins) in listOf("harvest_shed" to 3000, "planter_shed" to 3500)) {
        val b = state.buildings[id] ?: continue
        if (b.built) state.coins += coins
        state.buildings.remove(id)
    }
}
