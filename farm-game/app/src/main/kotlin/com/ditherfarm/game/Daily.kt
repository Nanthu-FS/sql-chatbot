package com.ditherfarm.game

import org.json.JSONObject
import kotlin.math.min
import kotlin.math.roundToInt

// ------------------------------------------------------------------ daily chores
/** Chores start once the first notebook steps are behind the player. */
const val CHORES_LEVEL = 3

/** Week number of a device day (epoch days), weeks starting on Monday. */
fun weekOf(day: Long) = Math.floorDiv(day + 3, 7L)

/** One of today's chores: [n] more of [stat] counted from [base]. [icon] is an item id or a sprite path. */
class Chore(val stat: String, val n: Long, val base: Long, val text: String, val icon: String) {
    var announced = false
    var claimed = false

    fun toJson() = JSONObject().put("stat", stat).put("n", n).put("base", base).put("text", text).put("icon", icon)
        .put("announced", announced).put("claimed", claimed)

    companion object {
        fun fromJson(o: JSONObject) = Chore(o.getString("stat"), o.getLong("n"), o.getLong("base"), o.getString("text"), o.optString("icon")).apply {
            announced = o.optBoolean("announced"); claimed = o.optBoolean("claimed")
        }
    }
}

private fun World.chore(stat: String, n: Int, text: String, icon: String) = Chore(stat, n.toLong(), state.stat(stat), text, icon)

/** Farm work: harvest a quick crop in season, or water, or plant. */
private fun World.farmChore(rng: Rng): Chore {
    val crops = data.crops.values.filter { cropUnlocked(it.id) && it.growHours <= 12 && (time.season in it.seasons || greenhouseOpen) }.sortedBy { it.unlockLevel }
    val roll = rng.nextDouble()
    return when {
        roll < 0.6 && crops.isNotEmpty() -> {
            val c = crops[rng.nextInt(crops.size)]
            val n = maxOf(10, (24 - c.growHours.toInt()) / 5 * 5)
            chore("harvest_${c.id}", n, "Harvest $n ${data.name(c.id)}", c.id)
        }
        roll < 0.8 -> chore("watered", 15, "Water 15 crops", "icons/water")
        else -> chore("planted", 20, "Plant 20 crops", "icons/seeds")
    }
}

/** A few batches of something a built machine makes quickly from what can be had now; selling crops before any machine. */
private fun World.craftChore(rng: Rng): Chore {
    val got = obtainableNow()
    val options = data.recipes.values.filter { r ->
        isBuilt(r.building) && !r.id.startsWith("tool_") && r.unlockLevel <= state.level && r.minutes <= 6.0 && r.fuel <= 0.0 &&
            r.outputs.keys.none { it.startsWith("@") } && r.inputs.keys.all { it == "#crop" || it in got }
    }.sortedBy { it.id }
    if (options.isEmpty()) return chore("crops_sold", 15, "Sell 15 crops", "icons/sell")
    val r = options[rng.nextInt(options.size)]
    val (item, qty) = r.outputs.entries.first()
    val n = qty * if (r.minutes <= 2.0) 3 else 2
    return chore("made_$item", n, "Make $n ${data.name(item)}", item)
}

/** Out and about: orders, the forest or the quarry. */
private fun World.roamChore(rng: Rng): Chore {
    val options = arrayListOf(
        { chore("orders", 2, "Fill 2 orders", "icons/orders") },
        { chore("fells", 5, "Chop down 5 trees", "icons/tool_axe") },
    )
    if (quarryOpen) options += { chore("rocks_broken", 8, "Break 8 rocks", "icons/tool_pickaxe") }
    return options[rng.nextInt(options.size)]()
}

fun World.choreProgress(c: Chore) = min(state.stat(c.stat) - c.base, c.n)
fun World.choreDone(c: Chore) = choreProgress(c) >= c.n

/** Coins and XP for one chore. */
fun World.choreReward() = (20 + 8 * state.level) to (10 + 2 * state.level)

/** Coins for finishing all three on streak day [day] (capped at 7). */
fun World.streakBonus(day: Int) = (30 + 6 * state.level) * minOf(day, 7)

fun World.claimChore(i: Int): Boolean {
    val c = state.chores.getOrNull(i) ?: return false
    if (c.claimed || !choreDone(c)) return false
    c.claimed = true
    val (coins, xp) = choreReward()
    state.coins += coins
    events += WorldEvent.ChoreClaimed(i, coins)
    gainXp(xp)
    if (state.chores.all { it.claimed }) finishChores(state.choreDay)
    return true
}

/** All three done on [day]: the streak grows (one missed day a week is forgiven) and pays its bonus. */
private fun World.finishChores(day: Long) {
    val missed = day - state.choreDoneDay - 1
    state.choreStreak = when {
        state.choreDoneDay < 0 -> 1
        missed <= 0 -> state.choreStreak + 1
        missed == 1L && state.choreFreeWeek != weekOf(day - 1) -> { state.choreFreeWeek = weekOf(day - 1); state.choreStreak + 1 }
        else -> 1
    }
    state.choreDoneDay = day
    val coins = streakBonus(state.choreStreak)
    state.coins += coins
    if (state.choreStreak % 7 == 0) add(if (state.choreStreak / 7 % 2 == 1) "growth_tonic" else "lucky_charm", 1)
    events += WorldEvent.ChoresFinished(state.choreStreak, coins)
    gainXp(10 * minOf(state.choreStreak, 7))
}

/** The streak as it stands [today]: 0 once more days were missed than the free one allows. */
fun World.choreStreakNow(today: Long): Int {
    if (state.choreDoneDay < 0) return 0
    val missed = today - state.choreDoneDay - 1
    return when {
        missed <= 0 -> state.choreStreak
        missed == 1L && state.choreFreeWeek != weekOf(today - 1) -> state.choreStreak
        else -> 0
    }
}

// ------------------------------------------------------------------ weekly truck contract
/** Ship [crops] by truck during [week]; counted from the per-crop truck totals in [base]. */
class Contract(val week: Long, val crops: Map<String, Int>, val base: Map<String, Long>) {
    var announced = false
    var claimed = false

    fun toJson() = JSONObject().put("week", week).put("crops", JSONObject(crops as Map<*, *>)).put("base", JSONObject(base as Map<*, *>))
        .put("announced", announced).put("claimed", claimed)

    companion object {
        fun fromJson(o: JSONObject) = Contract(
            o.getLong("week"), o.getJSONObject("crops").toIntMap(),
            o.getJSONObject("base").let { b -> b.keys().asSequence().associateWith { b.getLong(it) } },
        ).apply { announced = o.optBoolean("announced"); claimed = o.optBoolean("claimed") }
    }
}

/** Contracts begin once a farmhand can fill the truck. */
val World.contractOpen get() = state.workers.isNotEmpty() || state.stat("truck_trips") > 0

private fun World.newContract(week: Long): Contract {
    val rng = Rng.forDay(state.seed, week.toInt(), 53)
    val pool = data.crops.values.filter { cropUnlocked(it.id) }.sortedBy { it.unlockLevel }.toMutableList()
    val crops = LinkedHashMap<String, Int>()
    while (crops.size < 3 && pool.isNotEmpty()) {
        val c = pool.removeAt(rng.nextInt(pool.size))
        crops[c.id] = maxOf(5, (900 / (c.growHours + 3) / 5).roundToInt() * 5)
    }
    return Contract(week, crops, crops.keys.associateWith { state.stat("trucked_$it") })
}

fun World.contractShipped(c: Contract, crop: String) = min(state.stat("trucked_$crop") - (c.base[crop] ?: 0), c.crops.getValue(crop).toLong())
fun World.contractDone(c: Contract) = c.crops.keys.all { contractShipped(c, it) >= c.crops.getValue(it) }

/** Coins, XP and the rare extra: gold bars and lucky charms on alternate weeks. */
fun World.contractReward(c: Contract): Triple<Int, Int, Map<String, Int>> =
    Triple(1500 + 150 * state.level, 100 + 15 * state.level, if (c.week % 2 == 0L) mapOf("gold_bar" to 3) else mapOf("lucky_charm" to 2))

fun World.claimContract(): Boolean {
    val c = state.contract ?: return false
    if (c.claimed || !contractDone(c)) return false
    c.claimed = true
    val (coins, xp, items) = contractReward(c)
    state.coins += coins
    items.forEach { (k, v) -> add(k, v) }
    events += WorldEvent.ContractClaimed(coins)
    gainXp(xp)
    return true
}

/** Days left on this week's contract, counting [today]. */
fun contractDaysLeft(today: Long) = 7 - Math.floorMod(today + 3, 7L).toInt()

// ------------------------------------------------------------------ rolling over
/** New chores each device day (finished ones are paid first) and a new contract each week. */
fun World.rollDaily(today: Long) {
    if (state.level >= CHORES_LEVEL && state.choreDay != today) {
        state.chores.indices.forEach { claimChore(it) }
        state.choreDay = today
        state.chores.clear()
        val rng = Rng.forDay(state.seed, today.toInt(), 31)
        state.chores += listOf(farmChore(rng), craftChore(rng), roamChore(rng))
        events += WorldEvent.ChoresNew
    }
    val week = weekOf(today)
    if (contractOpen && state.contract?.week != week) {
        state.contract?.let { claimContract() }
        state.contract = newContract(week)
        events += WorldEvent.ContractNew
    }
}

/** Announces chores and the contract the moment they are done; drops a pin whose goal is finished. */
internal fun World.checkDaily() {
    state.chores.forEachIndexed { i, c ->
        if (!c.announced && choreDone(c)) { c.announced = true; events += WorldEvent.ChoreDone(i) }
    }
    state.contract?.let { c -> if (!c.announced && contractDone(c)) { c.announced = true; events += WorldEvent.ContractDone } }
    if (state.pinned.isNotEmpty() && goalLine(state.pinned) == null) state.pinned = ""
}

fun World.dailyClaimable() = state.chores.count { !it.claimed && choreDone(it) } + if (state.contract?.let { !it.claimed && contractDone(it) } == true) 1 else 0
