package com.ditherfarm.game

import kotlin.math.abs
import kotlin.math.max

// ------------------------------------------------------------------ field geometry
/**
 * The 8x6 field and the 4x3 greenhouse. Plot indices 0..47 are field plots, unlocked from
 * the centre outward; 48..59 are greenhouse plots. Fixtures stand on field vertices (the
 * 9x7 corners between plots) and never use a plot.
 */
object FieldLayout {
    const val COLS = 8
    const val ROWS = 6
    const val VCOLS = COLS + 1
    const val VROWS = ROWS + 1
    const val GH_COLS = 4
    const val GH_ROWS = 3
    const val GREENHOUSE = GH_COLS * GH_ROWS
    const val FIELD = COLS * ROWS
    const val TOTAL = FIELD + GREENHOUSE

    /** Field cell (column, row) of each field plot index. */
    val cells: List<Pair<Int, Int>> = run {
        val all = ArrayList<Pair<Int, Int>>()
        for (r in 0 until ROWS) for (c in 0 until COLS) all += c to r
        val cc = (COLS - 1) / 2f
        val cr = (ROWS - 1) / 2f
        all.sortedWith(compareBy({ (c, r) -> abs(c - cc) * 0.9f + abs(r - cr) }, { it.second }, { it.first }))
    }

    fun isGreenhouse(plot: Int) = plot >= FIELD

    /** Greenhouse cell of plot 48..59. */
    fun ghCell(plot: Int) = (plot - FIELD) % GH_COLS to (plot - FIELD) / GH_COLS

    fun vertex(vc: Int, vr: Int) = vr * VCOLS + vc

    /** True if a fixture of [radius] at [vertex] covers field plot [plot]. */
    fun covers(vertex: Int, radius: Int, plot: Int): Boolean {
        if (plot >= FIELD) return false
        val vc = vertex % VCOLS; val vr = vertex / VCOLS
        val (c, r) = cells[plot]
        return c in vc - radius until vc + radius && r in vr - radius until vr + radius
    }
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
    vertex !in 0 until FieldLayout.VCOLS * FieldLayout.VROWS -> "Place it between plots"
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

// ------------------------------------------------------------------ sheds
enum class PlanterMode { REPLANT, FILL }

const val SHED_PLOTS = 8
const val SHED_REAL_MINUTES = 20.0

private fun shedPeriod() = Time.realSecondsToGameMinutes(SHED_REAL_MINUTES * 60)

/** Runs the Harvest and Planter sheds every 20 real minutes (catches up offline). */
internal fun World.tickSheds() {
    for (id in listOf("harvest_shed", "planter_shed")) {
        if (!isBuilt(id)) continue
        // A newly built shed runs straight away.
        var at = state.shedNext[id] ?: state.minutes
        var runs = 0
        while (state.minutes >= at && runs < 80) {
            if (id == "harvest_shed") runHarvestShed() else runPlanterShed()
            at += shedPeriod()
            runs++
        }
        state.shedNext[id] = at
    }
}

fun World.shedSecondsLeft(id: String): Double =
    Time.gameMinutesToRealSeconds(max(0.0, (state.shedNext[id] ?: state.minutes) - state.minutes))

/** Harvests up to 8 ready plots: ones that will wither soonest first, then the longest ripe. */
internal fun World.runHarvestShed(): Int {
    val seasonLeft = Time.DAYS_PER_SEASON - time.dayOfSeason
    val ready = state.plots.filter { it.owned && isReady(it) }
        .sortedWith(compareBy<PlotState>({ p -> if (!FieldLayout.isGreenhouse(p.index) && seasonLeft <= 1 && time.season !in data.crop(p.crop!!).seasons) 0 else 1 }, { -it.growth }))
    var n = 0
    for (p in ready) {
        if (n >= SHED_PLOTS) break
        if (!hasRoom(p.crop!!)) { state.shedBlocked["harvest_shed"] = true; break }
        if (harvest(p.index, auto = true)) n++
    }
    if (n > 0) { state.shedBlocked["harvest_shed"] = false; state.bump("auto_harvests", n.toLong()) }
    state.shedLast["harvest_shed"] = n
    if (n > 0) events += WorldEvent.ShedRan("harvest_shed", n)
    return n
}

/** Game minutes left in the current season. */
private fun World.seasonMinutesLeft(): Double {
    val dayEnd = Time.MINUTES_PER_DAY
    return (Time.DAYS_PER_SEASON - time.dayOfSeason) * dayEnd + (dayEnd - state.minutes % dayEnd)
}

/** Seed the Planter Shed would put on plot [p], or null. Season-safe outdoors. */
fun World.planterChoice(p: PlotState): String? {
    val crop = when (state.planterMode) {
        PlanterMode.REPLANT -> p.lastCrop
        PlanterMode.FILL -> state.planterSeed
    } ?: return null
    val def = data.crops[crop] ?: return null
    if (count(def.seedItem) <= 0 || plantBlocker(p.index, crop) != null) return null
    if (!FieldLayout.isGreenhouse(p.index) && def.growMinutes > seasonMinutesLeft()) return null
    return crop
}

/** Plants up to 8 empty owned plots from seeds in the Silo (never buys seeds). */
internal fun World.runPlanterShed(): Int {
    var n = 0
    for (p in state.plots) {
        if (n >= SHED_PLOTS) break
        if (!p.owned || !p.empty) continue
        val crop = planterChoice(p) ?: continue
        if (plant(p.index, crop)) n++
    }
    if (n > 0) state.bump("auto_plants", n.toLong())
    state.shedLast["planter_shed"] = n
    if (n > 0) events += WorldEvent.ShedRan("planter_shed", n)
    return n
}
