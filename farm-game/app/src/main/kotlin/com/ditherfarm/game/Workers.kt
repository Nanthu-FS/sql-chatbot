package com.ditherfarm.game

import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

// ------------------------------------------------------------------ farmhands
/** A farmhand who can be hired: fixed look (sprite id), default name, unlock level and hiring fee. */
class FarmhandDef(val id: String, val name: String, val unlockLevel: Int, val hireCost: Int)

val FARMHANDS = listOf(
    FarmhandDef("rosa", "Rosa", 5, 800),
    FarmhandDef("milo", "Milo", 8, 2_500),
    FarmhandDef("hana", "Hana", 12, 6_000),
    FarmhandDef("otis", "Otis", 16, 12_000),
    FarmhandDef("wren", "Wren", 20, 20_000),
)

const val WORKER_MAX_LEVEL = 25
/** Crops a field's crate holds before its farmhand stops harvesting and waits for the harvester. */
const val CRATE_CAP = 40
const val TRUCK_CAP = 240
const val TRUCK_TRIP_SECONDS = 120.0
const val HARVESTER_SECONDS = 60.0

class WorkerState(val id: String) {
    var name = ""
    var level = 1
    /** Field block worked (-1 = none). */
    var field = -1
    var hungry = false
    /** Game minute of the next action. */
    var nextAt = 0.0
    /** Last plot touched and what was done (for the screen's animation). */
    var lastPlot = -1

    fun toJson() = JSONObject().put("id", id).put("name", name).put("level", level).put("field", field).put("hungry", hungry).put("next", nextAt)

    companion object {
        fun fromJson(o: JSONObject) = WorkerState(o.getString("id")).apply {
            name = o.optString("name"); level = o.optInt("level", 1); field = o.optInt("field", -1)
            hungry = o.optBoolean("hungry"); nextAt = o.optDouble("next", 0.0)
        }
    }
}

/** The pickup truck: its load, and while it is away, when it returns and what it earned. */
class TruckState {
    val load = LinkedHashMap<String, Int>()
    var away = false
    var returnAt = 0.0
    var pendingCoins = 0
    var lastTrip = 0

    fun toJson() = JSONObject().put("load", JSONObject(load as Map<*, *>)).put("away", away).put("returnAt", returnAt)
        .put("pending", pendingCoins).put("last", lastTrip)

    companion object {
        fun fromJson(o: JSONObject) = TruckState().apply {
            o.optJSONObject("load")?.toIntMap()?.forEach { (k, v) -> load[k] = v }
            away = o.optBoolean("away"); returnAt = o.optDouble("returnAt", 0.0)
            pendingCoins = o.optInt("pending"); lastTrip = o.optInt("last")
        }
    }
}

enum class WorkKind { HARVEST, CLEAR, PLANT, WATER }

/** Real seconds between actions: 12 s at level 1, about 10% faster each level, never under 1 s. */
fun workerSeconds(level: Int) = max(1.0, 12.0 * 0.9.pow(level - 1))

/** Crops eaten each game day: 1 at levels 1-9, 2 at 10-19, 3 at 20-25. */
fun workerAppetite(level: Int) = when { level >= 20 -> 3; level >= 10 -> 2; else -> 1 }

fun workerLevelCost(level: Int) = niceRound(150 * level.toDouble().pow(1.6))

fun World.farmhand(id: String) = FARMHANDS.first { it.id == id }
fun World.worker(id: String): WorkerState? = state.workers[id]
fun World.workerOn(field: Int): WorkerState? = state.workers.values.firstOrNull { it.field == field }
fun World.crate(field: Int): MutableMap<String, Int> = state.fields[field].crate

fun World.hireBlocker(id: String): String? {
    val d = FARMHANDS.firstOrNull { it.id == id } ?: return "Nobody by that name"
    return when {
        state.workers.containsKey(id) -> "Already working for you"
        state.level < d.unlockLevel -> "${d.name} is looking for work at level ${d.unlockLevel}"
        state.coins < d.hireCost -> "Needs ${d.hireCost} coins"
        else -> null
    }
}

fun World.hire(id: String): Boolean {
    hireBlocker(id)?.let { events += WorldEvent.Blocked(it); return false }
    val d = farmhand(id)
    state.coins -= d.hireCost
    state.workers[id] = WorkerState(id).apply { name = d.name; nextAt = state.minutes }
    state.bump("hired")
    events += WorldEvent.WorkerHired(id)
    return true
}

fun World.levelUpWorker(id: String): Boolean {
    val w = worker(id) ?: return false
    if (w.level >= WORKER_MAX_LEVEL) return false
    val cost = workerLevelCost(w.level)
    if (state.coins < cost) { events += WorldEvent.Blocked("Needs $cost coins"); return false }
    state.coins -= cost
    w.level++
    events += WorldEvent.WorkerLevelUp(id, w.level)
    return true
}

/** Puts a farmhand on a field (the field's previous farmhand is freed); -1 takes them off. */
fun World.assign(id: String, field: Int): Boolean {
    val w = worker(id) ?: return false
    if (field >= 0 && !blockOwned(field)) { events += WorldEvent.Blocked("Buy that field first"); return false }
    if (field >= 0) workerOn(field)?.takeIf { it !== w }?.field = -1
    w.field = field
    w.nextAt = state.minutes
    return true
}

// ------------------------------------------------------------------ simulation
private fun World.workStep(w: WorkerState): Boolean {
    val field = w.field
    val crate = crate(field)
    val crateFull = crate.values.sum() >= CRATE_CAP
    val plots = FieldLayout.plotsOf(field)
    val crop = state.fields[field].crop
    // Priority: harvest ripe crops, clear dead plots, replant, then water.
    val target = plots.firstOrNull { !crateFull && isReady(plot(it)) }?.let { it to WorkKind.HARVEST }
        ?: plots.firstOrNull { plot(it).needsClearing }?.let { it to WorkKind.CLEAR }
        ?: crop?.let { c -> plots.firstOrNull { plot(it).empty && canReplant(it, c) }?.let { it to WorkKind.PLANT } }
        ?: plots.firstOrNull { val p = plot(it); p.crop != null && !p.needsClearing && !p.watered && !isReady(p) }?.let { it to WorkKind.WATER }
        ?: return false
    val (i, kind) = target
    val done = when (kind) {
        WorkKind.HARVEST -> harvest(i, auto = true, into = crate)
        WorkKind.CLEAR -> clearPlot(i)
        WorkKind.PLANT -> plant(i, crop!!)
        WorkKind.WATER -> water(i)
    }
    if (done) { w.lastPlot = i; state.bump("worker_actions"); events += WorldEvent.WorkerActed(w.id, i, kind) }
    return done
}

/** Seeds come from the Silo first, then are bought; outdoor fields only take in-season crops. */
private fun World.canReplant(i: Int, crop: String): Boolean {
    val def = data.crops[crop] ?: return false
    if (plantBlocker(i, crop) != null) return false
    return count(def.seedItem) > 0 || state.coins >= seedPrice(crop)
}

/** Eats the cheapest crops in the Silo; false if there are not enough. */
private fun World.feed(w: WorkerState): Boolean {
    var need = workerAppetite(w.level)
    val foods = state.inventory.keys.filter { data.crops.containsKey(baseId(it)) && count(it) > 0 }.sortedBy { unitPrice(it) }
    if (foods.sumOf { count(it) } < need) return false
    for (k in foods) {
        val n = min(need, count(k))
        remove(k, n); need -= n
        if (need == 0) break
    }
    return true
}

/** At dawn every farmhand eats; with nothing to eat they rest until fed. */
internal fun World.workersDawn() {
    for (w in state.workers.values) {
        val fed = feed(w)
        if (!fed && !w.hungry) events += WorldEvent.WorkerHungry(w.id)
        w.hungry = !fed
    }
}

private fun period(seconds: Double) = Time.realSecondsToGameMinutes(seconds)

/** Farmhands work their fields, the harvester moves crates, the truck comes home. Offline-safe. */
internal fun World.tickWorkers() {
    for (w in state.workers.values) {
        if (w.field < 0 || !blockOwned(w.field)) { w.nextAt = state.minutes; continue }
        if (w.hungry) {
            if (feed(w)) w.hungry = false else { w.nextAt = state.minutes; continue }
        }
        val step = period(workerSeconds(w.level))
        var at = max(w.nextAt, state.minutes - Time.MINUTES_PER_DAY.toDouble())
        var runs = 0
        while (state.minutes >= at && runs < 2000) {
            if (!workStep(w)) { at = state.minutes + step; break }
            at += step
            runs++
        }
        w.nextAt = at
    }
    // Harvester: every minute, crates go on the truck (Truck fields) or into the Silo.
    var at = state.harvesterNext.takeIf { it > 0 } ?: state.minutes
    var guard = 0
    while (state.minutes >= at && guard++ < 1500) { runHarvester(); at += period(HARVESTER_SECONDS) }
    state.harvesterNext = at
    val t = state.truck
    if (t.away && state.minutes >= t.returnAt) {
        t.away = false
        state.coins += t.pendingCoins
        state.bump("coins_earned", t.pendingCoins.toLong())
        t.lastTrip = t.pendingCoins
        events += WorldEvent.TruckReturned(t.pendingCoins)
        gainXp(t.pendingCoins / 50)
        t.pendingCoins = 0
    }
}

fun World.truckLoad() = state.truck.load.values.sum()

internal fun World.runHarvester(): Int {
    var moved = 0
    for (b in 0 until FieldLayout.BLOCKS) {
        val crate = state.fields[b].crate
        if (crate.isEmpty()) continue
        val toTruck = state.fields[b].toTruck
        for (k in crate.keys.toList()) {
            var n = crate[k] ?: 0
            while (n > 0) {
                val ok = if (toTruck) !state.truck.away && truckLoad() < TRUCK_CAP else hasRoom(k)
                if (!ok) break
                if (toTruck) state.truck.load[k] = (state.truck.load[k] ?: 0) + 1 else add(k, 1)
                n--; moved++
            }
            if (n == 0) crate.remove(k) else crate[k] = n
        }
    }
    if (moved > 0) events += WorldEvent.HarvesterRan(moved)
    return moved
}

/** Coins the truck's load would fetch at today's prices. */
fun World.truckValue(): Int = state.truck.load.entries.sumOf { (k, n) -> quoteSale(k, n) }

/** Sends the truck off to sell its load; it comes back with the coins after a short trip. */
fun World.sendTruck(): Boolean {
    val t = state.truck
    if (t.away || t.load.isEmpty()) return false
    var coins = 0
    for ((k, n) in t.load) {
        val got = quoteSale(k, n)
        coins += got
        val id = baseId(k)
        state.glut[id] = (state.glut[id] ?: 0.0) + n * data.item(id).price
        if (data.crops.containsKey(id)) state.bump("crops_sold", n.toLong())
    }
    val units = t.load.values.sum()
    t.load.clear()
    t.away = true
    t.returnAt = state.minutes + period(TRUCK_TRIP_SECONDS)
    t.pendingCoins = coins
    state.bump("truck_trips")
    events += WorldEvent.TruckSent(units, coins)
    return true
}

fun World.truckSecondsLeft(): Double = Time.gameMinutesToRealSeconds(max(0.0, state.truck.returnAt - state.minutes))

/** Rough share of a field a farmhand can keep up with, for the panel ("plots per minute"). */
fun workerPlotsPerMinute(level: Int) = (60 / workerSeconds(level)).roundToInt()
