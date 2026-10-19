package com.ditherfarm.game

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

/** A building that can be built in a screen slot and runs recipes (GAME_DESIGN §11). */
class BuildingDef(
    val id: String,
    val name: String,
    val screen: String,
    val unlockLevel: Int,
    val cost: Map<String, Int>,
    val recipes: List<String>,
    val queue: Int,
    val prebuilt: Boolean,
    val sprite: String,
    /** Coal tank size for fuelled machines (Furnace); 0 = no fuel. */
    val fuelTank: Int,
    /** A producer (no-input recipe) stops restarting once its tray holds this many items. */
    val trayCap: Int = 3,
)

class RecipeDef(
    val id: String,
    val building: String,
    /** Item id → qty; the key "#crop" accepts any crops. */
    val inputs: Map<String, Int>,
    /** Item id → qty; "@tool:<tool>" sets that tool's tier. */
    val outputs: Map<String, Int>,
    /** Real minutes per batch. */
    val minutes: Double,
    val unlockLevel: Int,
    val coins: Int,
    val fuel: Double,
    val name: String?,
) {
    val gameMinutes get() = Time.realSecondsToGameMinutes(minutes * 60)
}

/**
 * One queued job with absolute start/end times on the game clock (offline-safe): [count] batches
 * of its recipe made back to back in one queue slot (bulk crafting), each taking the usual time.
 */
class Job(val recipe: String, val start: Double, val end: Double, val count: Int = 1) {
    fun toJson() = JSONObject().put("r", recipe).put("s", start).put("e", end).also { if (count > 1) it.put("n", count) }
    companion object { fun fromJson(o: JSONObject) = Job(o.getString("r"), o.getDouble("s"), o.getDouble("e"), o.optInt("n", 1).coerceAtLeast(1)) }
}

/** Most batches one bulk job can hold. */
const val BULK_MAX = 99

class BuildingState(val id: String) {
    var built = false
    val jobs = ArrayList<Job>()
    /** Finished outputs waiting to be collected. */
    val tray = LinkedHashMap<String, Int>()
    /** Coal units in the tank. */
    var fuel = 0.0
    var batches = 0L

    fun toJson() = JSONObject().apply {
        put("id", id); put("built", built); put("fuel", fuel); put("batches", batches)
        put("jobs", JSONArray().apply { jobs.forEach { put(it.toJson()) } })
        put("tray", JSONObject(tray as Map<*, *>))
    }

    companion object {
        fun fromJson(o: JSONObject) = BuildingState(o.getString("id")).apply {
            built = o.optBoolean("built"); fuel = o.optDouble("fuel", 0.0); batches = o.optLong("batches")
            o.optJSONArray("jobs")?.objects()?.forEach { jobs.add(Job.fromJson(it)) }
            o.optJSONObject("tray")?.toIntMap()?.forEach { (k, v) -> tray[k] = v }
        }
    }
}

enum class Tool(val label: String) { AXE("Axe"), PICKAXE("Pickaxe"), HOE("Hoe"), CAN("Watering Can"), SICKLE("Sickle") }

// ---------------------------------------------------------------- World API
fun World.building(id: String): BuildingState = state.buildings.getOrPut(id) {
    BuildingState(id).also { it.built = data.buildings[id]?.prebuilt == true }
}

fun World.isBuilt(id: String) = state.buildings[id]?.built ?: (data.buildings[id]?.prebuilt == true)

fun World.buildingsOn(screen: String) = data.buildings.values.filter { it.screen == screen }.sortedBy { it.unlockLevel }

fun World.canAfford(cost: Map<String, Int>): Boolean =
    cost.all { (k, v) -> if (k == "coins") state.coins >= v else count(k, anyQuality = true) >= v }

/** Removes a cost map (coins and items, any quality). Caller checks [canAfford] first. */
fun World.pay(cost: Map<String, Int>) {
    cost.forEach { (k, v) -> if (k == "coins") state.coins -= v else removeAny(k, v) }
}

fun World.buildBlocker(id: String): String? {
    val def = data.buildings[id] ?: return "Unknown building"
    return when {
        isBuilt(id) -> "Already built"
        def.unlockLevel > state.level -> "Unlocks at level ${def.unlockLevel}"
        featBlocker(id) != null -> "Blueprint: " + featBlocker(id)
        !canAfford(buildCost(id)) -> "Not enough materials"
        else -> null
    }
}

fun World.build(id: String): Boolean {
    buildBlocker(id)?.let { events += WorldEvent.Blocked(it); return false }
    val def = data.buildings.getValue(id)
    pay(buildCost(id))
    building(id).built = true
    if (id == "greenhouse") syncGreenhouse()
    state.bump("built")
    events += WorldEvent.Built(id)
    gainXp(max(5, (def.cost["coins"] ?: 0) / 50))
    return true
}

fun World.recipesFor(id: String): List<RecipeDef> = data.buildings[id]?.recipes?.mapNotNull { data.recipes[it] } ?: emptyList()

/** Units of a recipe input available ("#crop" counts every crop in the Silo). */
fun World.available(input: String): Int =
    if (input == "#crop") state.inventory.entries.sumOf { (k, v) -> if (data.crops.containsKey(baseId(k))) v else 0 } else count(input, anyQuality = true)

/** True if [r] can be made in bulk: anything but tool upgrades and the Drill Rig. */
fun World.canBulk(r: RecipeDef) = toolTarget(r) == null && r.id != "drill" && r.inputs.isNotEmpty()

/** How many batches of [r] the coins, ingredients and fuel on hand pay for (at most [BULK_MAX]). */
fun World.maxBatches(r: RecipeDef): Int {
    var n = BULK_MAX
    if (r.coins > 0) n = minOf(n, state.coins / r.coins)
    for ((k, v) in r.inputs) if (v > 0) n = minOf(n, available(k) / v)
    val fuel = fuelUse(r)
    if (fuel > 0) n = minOf(n, (building(r.building).fuel / fuel).toInt())
    return n.coerceAtLeast(0)
}

/** Why [count] batches of [r] cannot be queued now, or null. */
fun World.recipeBlocker(r: RecipeDef, count: Int = 1): String? {
    val b = building(r.building)
    val def = data.buildings[r.building]
    toolTarget(r)?.let { (tool, tier) -> if (toolTier(tool) >= tier) return "Already have it" }
    return when {
        !b.built -> "Build the ${def?.name ?: r.building} first"
        r.unlockLevel > state.level -> "Unlocks at level ${r.unlockLevel}"
        featBlocker(r.id) != null -> featBlocker(r.id)
        r.building == "blacksmith" && r.id.endsWith("_3") && state.villageTier < 2 -> "Gold work comes with the brick village"
        b.jobs.size >= queueSize(r.building) -> "Queue is full"
        state.coins < r.coins * count -> "Not enough coins"
        r.inputs.any { (k, v) -> available(k) < v * count } -> "Missing ingredients"
        r.fuel > 0 && b.fuel < fuelUse(r) * count -> "Needs coal"
        else -> null
    }
}

/** Game minutes a batch takes (the Goods page makes machines 5% faster). */
fun World.jobMinutes(r: RecipeDef) = r.gameMinutes * (1 - skill("overclock")) * if (hasBonus("machine_speed")) 0.95 else 1.0

/**
 * Queues [count] batches as one job (one queue slot): takes all their inputs (and fuel) now and
 * schedules them after the last queued job, each batch taking the usual time.
 */
fun World.enqueue(recipeId: String, count: Int = 1): Boolean {
    val r = data.recipes[recipeId] ?: return false
    val n = if (canBulk(r)) count.coerceIn(1, BULK_MAX) else 1
    recipeBlocker(r, n)?.let { events += WorldEvent.Blocked(it); return false }
    val b = building(r.building)
    state.coins -= r.coins * n
    r.inputs.forEach { (k, v) -> if (k == "#crop") takeAnyCrops(v * n) else removeAny(k, v * n) }
    b.fuel -= fuelUse(r) * n
    val start = max(state.minutes, b.jobs.lastOrNull()?.end ?: 0.0)
    b.jobs += Job(r.id, start, start + jobMinutes(r) * n, n)
    events += WorldEvent.JobQueued(r.building, r.id)
    return true
}

private fun World.takeAnyCrops(n: Int) {
    var left = n
    for (k in state.inventory.keys.toList().sortedBy { data.item(baseId(it)).price }) {
        if (left == 0) break
        if (!data.crops.containsKey(baseId(k))) continue
        val take = minOf(left, state.inventory[k] ?: 0)
        remove(k, take); left -= take
    }
}

/** Cancels the last queued job of a building and refunds its inputs. */
fun World.cancelLast(id: String): Boolean {
    val b = building(id)
    val job = b.jobs.lastOrNull() ?: return false
    if (job.end <= state.minutes) return false
    val r = data.recipes.getValue(job.recipe)
    b.jobs.removeAt(b.jobs.size - 1)
    val n = job.count
    state.coins += r.coins * n
    r.inputs.forEach { (k, v) -> add(if (k == "#crop") "wheat" else k, v * n) }
    b.fuel += fuelUse(r) * n
    return true
}

/** Moves finished jobs into trays; tool upgrades apply immediately. Called from the time step. */
internal fun World.tickMachines() {
    for (b in state.buildings.values) {
        val def = data.buildings[b.id] ?: continue
        var lastEnd = -1.0
        var guard = 0
        while (guard++ < 1000) {
            while (b.jobs.isNotEmpty() && b.jobs[0].end <= state.minutes) {
                val job = b.jobs.removeAt(0)
                lastEnd = job.end
                val r = data.recipes[job.recipe] ?: continue
                b.batches += job.count
                for ((k, v) in r.outputs) {
                    val tool = k.removePrefix("@tool:")
                    if (tool != k) {
                        state.tools[tool] = maxOf(state.tools[tool] ?: 0, v)
                        events += WorldEvent.ToolUpgraded(tool, v)
                    } else {
                        b.tray[k] = (b.tray[k] ?: 0) + v * job.count
                    }
                }
                if (r.id == "drill") drillBonus(b)
                events += WorldEvent.JobDone(b.id, r.id)
            }
            // Producers (no inputs, e.g. the Beehive or Drill Rig) restart themselves, back to back
            // from the end of the last batch so offline time is not lost.
            if (!b.built || b.jobs.isNotEmpty() || b.tray.values.sum() >= trayCap(b.id)) break
            val auto = def.recipes.mapNotNull { data.recipes[it] }.firstOrNull { it.inputs.isEmpty() && it.unlockLevel <= state.level } ?: break
            if (b.fuel < fuelUse(auto)) break
            b.fuel -= fuelUse(auto)
            val start = if (lastEnd >= 0) lastEnd else state.minutes
            b.jobs += Job(auto.id, start, start + jobMinutes(auto))
            if (b.jobs[0].end > state.minutes) break
        }
    }
}

/** The Drill Rig also turns up one quarry rock's worth of ore per batch. */
private fun World.drillBonus(b: BuildingState) {
    val q = data.mine.quarry.getOrNull(state.mine.quarryLevel - 1) ?: return
    val w = q.weights
    var r = rng().nextInt(w.values.sum().coerceAtLeast(1))
    val node = w.entries.firstOrNull { r -= it.value; r < 0 }?.key ?: return
    val drop = data.mine.nodes[node]?.drops?.keys?.firstOrNull() ?: return
    b.tray[drop] = (b.tray[drop] ?: 0) + 1
}

fun World.trayCount(id: String) = building(id).tray.values.sum()

/** Collects a building's tray into storage (as much as fits); returns units collected. */
fun World.collect(id: String): Int {
    val b = building(id)
    var got = 0
    var value = 0
    for (k in b.tray.keys.toList()) {
        var n = b.tray[k] ?: 0
        while (n > 0 && hasRoom(k)) { add(k, 1); n--; got++; value += data.item(baseId(k)).price; state.bump("made_${baseId(k)}") }
        if (n == 0) b.tray.remove(k) else b.tray[k] = n
    }
    if (got == 0 && b.tray.isNotEmpty()) { events += WorldEvent.Blocked("Barn full"); return 0 }
    if (got > 0) {
        events += WorldEvent.Collected(id, got)
        gainXp(max(1, value / 10))
    }
    return got
}

fun World.addFuel(id: String, coal: Int): Int {
    val b = building(id)
    val tank = data.buildings[id]?.fuelTank ?: 0
    val n = minOf(coal, count("coal", anyQuality = true), (tank - b.fuel).toInt())
    if (n <= 0) return 0
    removeAny("coal", n)
    b.fuel += n
    return n
}

/** Seconds of real time until the building's current job finishes, or null if idle. */
fun World.secondsLeft(id: String): Double? {
    val j = building(id).jobs.firstOrNull() ?: return null
    return Time.gameMinutesToRealSeconds((j.end - state.minutes).coerceAtLeast(0.0))
}

fun World.jobProgress(id: String): Float {
    val j = building(id).jobs.firstOrNull() ?: return 0f
    if (state.minutes < j.start) return 0f
    return ((state.minutes - j.start) / (j.end - j.start)).toFloat().coerceIn(0f, 1f)
}

// ---------------------------------------------------------------- tools
fun World.toolTier(tool: String): Int = state.tools[tool] ?: 0
fun World.toolTier(tool: Tool): Int = toolTier(tool.name.lowercase())

private fun toolTarget(r: RecipeDef): Pair<String, Int>? =
    r.outputs.entries.firstOrNull { it.key.startsWith("@tool:") }?.let { it.key.removePrefix("@tool:") to it.value }

/** The workbench recipe that upgrades [tool] to the next tier, if any. */
fun World.nextToolRecipe(tool: Tool): RecipeDef? = data.recipes["tool_${tool.name.lowercase()}_${toolTier(tool) + 1}"]
