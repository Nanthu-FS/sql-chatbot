package com.ditherfarm.game

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Things that happened inside the simulation; the UI drains them for sounds, FX and toasts. */
sealed class WorldEvent {
    data class Harvested(val plot: Int, val crop: String, val qty: Int, val quality: Quality, val xp: Int) : WorldEvent()
    data class Planted(val plot: Int, val crop: String) : WorldEvent()
    data class Watered(val plot: Int) : WorldEvent()
    data class Ripened(val plot: Int) : WorldEvent()
    data class Cleared(val plot: Int) : WorldEvent()
    data class BlockBought(val block: Int, val price: Int) : WorldEvent()
    data class XpGained(val amount: Int) : WorldEvent()
    data class LevelUp(val level: Int, val coins: Int) : WorldEvent()
    data class Blocked(val reason: String, val plot: Int = -1) : WorldEvent()
    data class DayStarted(val day: Int, val weather: Weather) : WorldEvent()
    data class SeasonChanged(val season: Season, val withered: Int) : WorldEvent()
    data class Storm(val scorched: List<Int>, val battered: List<Int>) : WorldEvent()
    data class OrderArrived(val slot: Int) : WorldEvent()
    data class OrderDelivered(val slot: Int, val coins: Int, val xp: Int) : WorldEvent()
    data class Sold(val key: String, val qty: Int, val coins: Int) : WorldEvent()
    data class Ate(val key: String, val energy: Int) : WorldEvent()
    data class Discovered(val item: String) : WorldEvent()
    data class Built(val building: String) : WorldEvent()
    data class JobQueued(val building: String, val recipe: String) : WorldEvent()
    data class JobDone(val building: String, val recipe: String) : WorldEvent()
    data class Collected(val building: String, val qty: Int) : WorldEvent()
    data class ToolUpgraded(val tool: String, val tier: Int) : WorldEvent()
    data class Chopped(val spot: Int, val damage: Int, val crit: Boolean, val hpLeft: Int) : WorldEvent()
    data class Felled(val spot: Int, val tree: String, val drops: Map<String, Int>, val xp: Int) : WorldEvent()
    data class StumpCleared(val spot: Int) : WorldEvent()
    data class Foraged(val spot: Int, val item: String, val qty: Int) : WorldEvent()
    data class TreePlanted(val spot: Int, val tree: String) : WorldEvent()
    data class ZoneUnlocked(val zone: String) : WorldEvent()
    data class BirdNest(val spot: Int, val item: String, val qty: Int) : WorldEvent()
    data class Mined(val cell: Int, val quarry: Boolean, val node: String, val crit: Boolean, val hpLeft: Int) : WorldEvent()
    data class RockBroken(val cell: Int, val quarry: Boolean, val node: String, val drops: Map<String, Int>, val xp: Int) : WorldEvent()
    data class HollowKnock(val cell: Int, val onLadder: Boolean) : WorldEvent()
    data class LadderFound(val cell: Int) : WorldEvent()
    data class FloorEntered(val floor: Int) : WorldEvent()
    data class StopUnlocked(val floor: Int) : WorldEvent()
    data class GateOpened(val floor: Int, val chest: Map<String, Int>) : WorldEvent()
    data class GeodeCracked(val geode: String, val out: Map<String, Int>) : WorldEvent()
    data class FixturePlaced(val vertex: Int, val item: String) : WorldEvent()
    data class FixtureRemoved(val vertex: Int, val item: String) : WorldEvent()
    data class WorkerHired(val worker: String) : WorldEvent()
    data class SkillLearned(val skill: String, val rank: Int) : WorldEvent()
    data class FeatDone(val key: String) : WorldEvent()
    data class Donated(val bundle: String, val units: Int) : WorldEvent()
    data class VillageRebuilt(val tier: Int) : WorldEvent()
    data class QuestDone(val quest: String) : WorldEvent()
    object ChoresNew : WorldEvent()
    data class ChoreDone(val chore: Int) : WorldEvent()
    data class ChoreClaimed(val chore: Int, val coins: Int) : WorldEvent()
    data class ChoresFinished(val streak: Int, val coins: Int) : WorldEvent()
    object ContractNew : WorldEvent()
    object ContractDone : WorldEvent()
    data class ContractClaimed(val coins: Int) : WorldEvent()
    data class Respecced(val coins: Int) : WorldEvent()
    data class TonicUsed(val item: String) : WorldEvent()
    data class WorkerLevelUp(val worker: String, val level: Int) : WorldEvent()
    data class WorkerActed(val worker: String, val plot: Int, val kind: WorkKind) : WorldEvent()
    data class WorkerHungry(val worker: String) : WorldEvent()
    data class HarvesterRan(val units: Int) : WorldEvent()
    data class TruckSent(val units: Int, val coins: Int) : WorldEvent()
    data class TruckReturned(val coins: Int) : WorldEvent()
    data class AchievementReady(val id: String) : WorldEvent()
    data class AchievementClaimed(val id: String, val tier: Int, val coins: Int) : WorldEvent()
    data class PageClaimed(val id: String, val coins: Int) : WorldEvent()
    data class GiftClaimed(val day: Int, val coins: Int) : WorldEvent()
    data class Bought(val item: String, val qty: Int, val price: Int) : WorldEvent()
    data class FestivalStarted(val name: String) : WorldEvent()
    data class FestivalWon(val name: String) : WorldEvent()
    data class CartSold(val units: Int, val coins: Int) : WorldEvent()
    data class Rumble(val cell: Int) : WorldEvent()
    data class Braced(val cell: Int) : WorldEvent()
    data class CaveIn(val cell: Int) : WorldEvent()
    data class HiveSmoked(val spot: Int, val seed: Boolean) : WorldEvent()
    data class Stung(val spot: Int) : WorldEvent()
}

/**
 * The whole simulation behind one facade. All time is driven by [advance] (real seconds),
 * which steps the game clock in small slices and fires the 06:00 day roll, the 14:00 storm,
 * season changes and order timers in order — the same path offline catch-up uses.
 */
class World(val data: GameData, val state: GameState) {
    val events = ArrayList<WorldEvent>()
    val time get() = GameTime(state.minutes)
    val level get() = state.level

    init {
        val from = state.version
        migrateLayout()
        // Saves from before the notebook: players past level 4 skip it; feats already met stay quiet.
        if (from < 3) {
            if (state.level >= 4) state.questIndex = QUESTS.size
            for ((k, f) in FEATS + BLUEPRINTS.mapKeys { "bp_" + it.key }) if (met(f)) state.featsSeen += k
            state.version = GameState.VERSION
        }
        retireSheds()
        syncGreenhouse()
        while (state.fields.size < FieldLayout.BLOCKS) state.fields.add(FieldState())
        autoOpenZones() // the Edge Grove is open from the start
    }

    // ------------------------------------------------------------------ time
    /** Advances real time; returns false if nothing ran (dt <= 0). */
    fun advance(realSeconds: Double): Boolean {
        if (realSeconds <= 0) return false
        regenEnergy(realSeconds)
        var remaining = Time.realSecondsToGameMinutes(realSeconds)
        while (remaining > 0) {
            val step = min(remaining, STEP_MINUTES)
            stepGame(step)
            remaining -= step
        }
        return true
    }

    /** Offline catch-up after the app was closed; capped at 24 real hours. */
    fun catchUp(realSeconds: Double) = advance(min(realSeconds, OFFLINE_CAP_SECONDS))

    private fun stepGame(dt: Double) {
        val before = GameTime(state.minutes)
        val after = before.plusMinutes(dt)
        // Day roll at 06:00 of every day crossed (including the first day of a new game).
        var t = before
        while (true) {
            val dawn = if (t.minuteOfDay < 360 && state.lastDayRolled < t.dayIndex) t.startOfDay().plusHours(6.0) else t.next(6.0)
            if (dawn > after) break
            grow(dawn.minutes - state.minutes)
            state.minutes = dawn.minutes
            dayRoll(dawn.dayIndex)
            t = dawn
        }
        // 14:00 storm strike.
        val storm = GameTime(state.minutes).next(14.0)
        if (storm <= after && state.weather.stormy && state.lastStormDay == storm.dayIndex) {
            grow(storm.minutes - state.minutes)
            state.minutes = storm.minutes
            stormStrike()
        }
        grow(after.minutes - state.minutes)
        state.minutes = after.minutes
        tickOrders()
        tickMachines()
        tickQuarry()
        tickMine()
        tickWorkers()
        tickForestAuto()
        checkAchievements()
        checkFeats()
        checkDaily()
    }

    private fun grow(dt: Double) {
        if (dt <= 0) return
        for (p in state.plots) {
            val id = p.crop ?: continue
            if (p.withered || p.scorched) continue
            val def = data.crop(id)
            if (p.growth >= def.growMinutes) continue
            p.growth += dt * growRate(p.watered)
            if (p.watered) p.cared = true
            if (p.growth >= def.growMinutes) events += WorldEvent.Ripened(p.index)
        }
    }

    private fun dayRoll(day: Int) {
        state.lastDayRolled = day
        val t = GameTime(day.toDouble() * Time.MINUTES_PER_DAY + 360)
        // Season change: outdoor crops still growing that the new season does not allow wither.
        if (day > 0 && day % Time.DAYS_PER_SEASON == 0) {
            var withered = 0
            for (p in state.plots) {
                val id = p.crop ?: continue
                if (p.withered || p.scorched || FieldLayout.isGreenhouse(p.index)) continue
                val def = data.crop(id)
                val mature = p.growth >= def.growMinutes
                if (!mature && t.season !in def.seasons) { p.withered = true; withered++ }
            }
            events += WorldEvent.SeasonChanged(t.season, withered)
        }
        state.weather = if (day == 0) Weather.SUNNY else state.forecast
        if (state.weather.stormy) state.lastStormDay = day
        state.forecast = rollWeather(day + 1)
        // Rain waters the field (not the greenhouse); sprinklers water what they cover.
        for (p in state.plots) p.watered = p.crop != null && ((state.weather.waters && !FieldLayout.isGreenhouse(p.index)) || covered(p.index, FixtureKind.WATER))
        for (k in state.glut.keys.toList()) state.glut[k] = state.glut[k]!! * 0.5
        forestDawn(day)
        workersDawn()
        festivalDawn()
        runMarketCart()
        events += WorldEvent.DayStarted(day, state.weather)
    }

    /** Weather for a day, seeded so reloading never re-rolls (GAME_DESIGN §2). */
    fun rollWeather(day: Int): Weather {
        if (day < 3 || isFestivalDay(GameTime(day.toDouble() * Time.MINUTES_PER_DAY))) return Weather.SUNNY
        val season = GameTime(day.toDouble() * Time.MINUTES_PER_DAY).season
        val r = Rng.forDay(state.seed, day, 11).nextDouble()
        val (pWet, pStorm) = when (season) {
            Season.SPRING -> 0.35 to 0.07
            Season.SUMMER -> 0.22 to 0.10
            Season.FALL -> 0.37 to 0.09
            Season.WINTER -> 0.42 to 0.06
        }
        val stormAllowed = day >= 6 && state.level >= 5 && state.lastStormDay < day - 1
        return when {
            r < pStorm && stormAllowed -> if (season == Season.WINTER) Weather.BLIZZARD else Weather.STORM
            r < pStorm + pWet -> if (season == Season.WINTER) Weather.SNOW else Weather.RAIN
            else -> Weather.SUNNY
        }
    }

    private fun stormStrike() {
        val rng = rng()
        val planted = state.plots.filter { it.owned && it.crop != null && !it.withered && !it.scorched && !FieldLayout.isGreenhouse(it.index) }
        val strikes = min(3, 1 + planted.size / 16)
        // Lightning rods protect what they cover; scarecrows keep the wind off.
        val exposed = planted.filter { !covered(it.index, FixtureKind.LIGHTNING) }
        val scorched = exposed.shuffled(kotlin.random.Random(rng.nextLong())).take(if (exposed.isEmpty()) 0 else strikes)
        scorched.forEach { it.scorched = true }
        val battered = ArrayList<Int>()
        for (p in planted) {
            if (p.scorched || covered(p.index, FixtureKind.WIND)) continue
            val def = data.crop(p.crop!!)
            if (p.growth < def.growMinutes && rng.chance(0.15)) { p.battered = true; battered += p.index }
        }
        state.bump("storms")
        if (planted.size >= 12 && scorched.isEmpty() && battered.isEmpty()) state.bump("storm_safe")
        events += WorldEvent.Storm(scorched.map { it.index }, battered)
    }

    internal fun rng(): Rng = Rng(state.seed xor (state.rngCounter++ * 0x9E3779B97F4A7C15uL.toLong()))

    // ---------------------------------------------------------------- energy
    val maxEnergy get() = data.level(state.level).maxEnergy + if (hasBonus("max_energy")) 10 else 0

    private fun regenEnergy(realSeconds: Double) {
        val max = maxEnergy.toDouble()
        if (state.energy < max) state.energy = min(max, state.energy + realSeconds / 60.0 * max / 90.0)
    }

    fun spendEnergy(n: Int): Boolean {
        if (state.energy < n) { events += WorldEvent.Blocked("Too tired"); return false }
        state.energy -= n
        return true
    }

    /** Energy an item restores when eaten (0 = not edible). */
    fun foodEnergy(key: String): Int {
        val crop = data.crops[baseId(key)] ?: return 0
        return (crop.energy * Quality.of(key).mult * if (hasBonus("dish_energy")) 1.1 else 1.0).roundToInt()
    }

    /** Eats the cheapest food (coins per energy) that fits under the energy cap; returns its key. */
    fun quickEat(): String? {
        val room = maxEnergy - state.energy
        val key = state.inventory.keys
            .filter { count(it) > 0 && foodEnergy(it) in 1..room.toInt() }
            .minByOrNull { unitPrice(it).toDouble() / foodEnergy(it) } ?: return null
        return if (eat(key)) key else null
    }

    fun eat(key: String): Boolean {
        val e = foodEnergy(key)
        if (e <= 0 || count(key) <= 0) return false
        if (state.energy + e > maxEnergy) { events += WorldEvent.Blocked("Not hungry"); return false }
        remove(key, 1)
        state.energy += e
        state.bump("eaten")
        events += WorldEvent.Ate(key, e)
        return true
    }

    // -------------------------------------------------------------- progress
    fun gainXp(amount: Int) {
        if (amount <= 0) return
        val n = (amount * xpMult()).roundToInt()
        state.xp += n
        events += WorldEvent.XpGained(n)
        while (state.level < data.maxLevel && state.xp >= data.level(state.level + 1).totalXp) {
            state.level++
            val reward = data.level(state.level).levelUpCoins
            state.coins += reward
            state.energy = maxEnergy.toDouble()
            ensureOrderSlots()
            autoOpenZones()
            events += WorldEvent.LevelUp(state.level, reward)
        }
    }

    /** 0..1 progress through the current level. */
    fun levelProgress(): Float {
        if (state.level >= data.maxLevel) return 1f
        val from = data.level(state.level).totalXp
        val to = data.level(state.level + 1).totalXp
        return ((state.xp - from).toFloat() / (to - from)).coerceIn(0f, 1f)
    }

    fun cropUnlocked(id: String) = data.crop(id).unlockLevel <= state.level

    // --------------------------------------------------------------- storage
    fun capacity(s: Storage): Int = when (s) {
        Storage.BARN -> data.barn[state.barnLevel - 1].capacity
        Storage.SILO -> data.silo[state.siloLevel - 1].capacity
        Storage.NONE -> Int.MAX_VALUE
    }

    fun used(s: Storage): Int = state.inventory.entries.sumOf { (k, v) -> if (data.item(baseId(k)).storage == s) v else 0 }

    fun hasRoom(item: String): Boolean {
        val s = data.item(baseId(item)).storage
        return s == Storage.NONE || used(s) < capacity(s)
    }

    /** Count of an exact key, or of every quality when [key] has no suffix and [anyQuality]. */
    fun count(key: String, anyQuality: Boolean = false): Int =
        if (anyQuality) Quality.entries.sumOf { state.inventory[it.key(baseId(key))] ?: 0 } else state.inventory[key] ?: 0

    fun add(key: String, qty: Int) {
        if (qty <= 0) return
        state.inventory[key] = (state.inventory[key] ?: 0) + qty
        val id = baseId(key)
        if (state.discovered.add(id)) events += WorldEvent.Discovered(id)
    }

    fun remove(key: String, qty: Int): Boolean {
        val have = state.inventory[key] ?: 0
        if (have < qty) return false
        if (have == qty) state.inventory.remove(key) else state.inventory[key] = have - qty
        return true
    }

    /** Removes [qty] of an item across qualities, lowest quality first. */
    fun removeAny(id: String, qty: Int): Boolean {
        if (count(id, anyQuality = true) < qty) return false
        var left = qty
        for (q in Quality.entries) {
            val k = q.key(id)
            val take = min(left, state.inventory[k] ?: 0)
            if (take > 0) { remove(k, take); left -= take }
        }
        return true
    }

    fun upgradeCost(s: Storage): StorageTier? {
        val tiers = if (s == Storage.BARN) data.barn else data.silo
        val lvl = if (s == Storage.BARN) state.barnLevel else state.siloLevel
        return tiers.getOrNull(lvl)
    }

    fun canUpgrade(s: Storage): Boolean {
        val t = upgradeCost(s) ?: return false
        return t.unlockLevel <= state.level && t.cost.all { (k, v) -> if (k == "coins") state.coins >= v else count(k, anyQuality = true) >= v }
    }

    fun upgrade(s: Storage): Boolean {
        if (!canUpgrade(s)) { events += WorldEvent.Blocked("Can't upgrade yet"); return false }
        val t = upgradeCost(s)!!
        t.cost.forEach { (k, v) -> if (k == "coins") state.coins -= v else removeAny(k, v) }
        if (s == Storage.BARN) state.barnLevel++ else state.siloLevel++
        gainXp(20 * t.level)
        return true
    }

    // ------------------------------------------------------------------ farm
    fun plot(i: Int) = state.plots[i]
    /** Owned field plots (the greenhouse is not counted). */
    val ownedPlots get() = (0 until FieldLayout.FIELD).count { state.plots[it].owned }

    fun isReady(p: PlotState): Boolean {
        val id = p.crop ?: return false
        return !p.withered && !p.scorched && p.growth >= data.crop(id).growMinutes
    }

    /** Remaining real seconds until a plot is ready at its current watering. */
    fun secondsToReady(p: PlotState): Double {
        val id = p.crop ?: return 0.0
        val left = (data.crop(id).growMinutes - p.growth).coerceAtLeast(0.0)
        return Time.gameMinutesToRealSeconds(left / (if (p.watered) 1.0 else DRY_SPEED))
    }

    /** Fraction grown, 0..1. */
    fun growthFraction(p: PlotState): Float {
        val id = p.crop ?: return 0f
        return (p.growth / data.crop(id).growMinutes).toFloat().coerceIn(0f, 1f)
    }

    /** Sprite stage 0..3 for a growing crop. */
    fun stage(p: PlotState): Int {
        if (isReady(p)) return 3
        val f = growthFraction(p)
        return when { f < 0.25f -> 0; f < 0.6f -> 1; else -> 2 }
    }

    fun outOfSeason(cropId: String) = time.season !in data.crop(cropId).seasons

    /** Why planting [cropId] on plot [i] would fail, or null if it can be planted. */
    fun plantBlocker(i: Int, cropId: String): String? {
        val p = plot(i)
        val def = data.crop(cropId)
        return when {
            !p.owned -> "Buy this plot first"
            !p.empty -> "Plot is busy"
            def.unlockLevel > state.level -> "Unlocks at level ${def.unlockLevel}"
            time.season !in def.seasons && !FieldLayout.isGreenhouse(i) -> "Not in season"
            count(def.seedItem) <= 0 && state.coins < seedPrice(cropId) -> "Not enough coins"
            else -> null
        }
    }

    /** Coins for one seed bought at planting time (the Crops page takes 10% off). */
    fun seedPrice(cropId: String): Int {
        val base = data.crop(cropId).seedPrice * (1 - skill("bulk_buyer"))
        return max(1, (if (hasBonus("seed_discount")) base * 0.9 else base).toInt())
    }

    fun plant(i: Int, cropId: String): Boolean {
        plantBlocker(i, cropId)?.let { events += WorldEvent.Blocked(it, i); return false }
        val def = data.crop(cropId)
        if (count(def.seedItem) > 0) remove(def.seedItem, 1) else state.coins -= seedPrice(cropId)
        val p = plot(i)
        p.clear()
        p.crop = cropId
        p.lastCrop = cropId
        p.watered = p.watered || (state.weather.waters && !FieldLayout.isGreenhouse(i)) || covered(i, FixtureKind.WATER)
        state.bump("planted")
        events += WorldEvent.Planted(i, cropId)
        return true
    }

    fun water(i: Int): Boolean {
        val p = plot(i)
        if (p.crop == null || p.needsClearing || p.watered || isReady(p)) return false
        p.watered = true
        p.cared = true
        state.bump("watered")
        events += WorldEvent.Watered(i)
        return true
    }

    fun fertilize(i: Int, key: String): Boolean {
        val p = plot(i)
        if (p.crop == null || p.needsClearing || p.fertSilver > 0 || count(key) <= 0) return false
        when (key) {
            "basic_fertilizer" -> { p.fertSilver = 0.20; p.fertGold = 0.05 }
            "quality_fertilizer" -> { p.fertSilver = 0.25; p.fertGold = 0.15 }
            else -> return false
        }
        remove(key, 1)
        return true
    }

    /** Harvests a ready plot. [auto] harvests (sheds) give half XP. */
    fun harvest(i: Int, auto: Boolean = false, into: MutableMap<String, Int>? = null): Boolean {
        val p = plot(i)
        if (!isReady(p)) return false
        val def = data.crop(p.crop!!)
        if (into == null && !hasRoom(def.id)) { events += WorldEvent.Blocked("Silo full", i); return false }
        val quality = rollQuality(p)
        var qty = def.yield
        if (roll(skill("bountiful") + if (lucky) 0.10 else 0.0)) qty++
        var q = quality
        if (p.battered) { if (q == Quality.NORMAL) qty = max(1, qty - 1) else q = Quality.entries[q.ordinal - 1] }
        val first = def.id !in state.discovered
        if (into != null) {
            // A farmhand's crate at the field edge; the harvester takes it on from there.
            into[q.key(def.id)] = (into[q.key(def.id)] ?: 0) + qty
            if (state.discovered.add(def.id)) events += WorldEvent.Discovered(def.id)
        } else {
            add(q.key(def.id), qty)
            // Leaves and stalks for the Compost Bin.
            if (rng().chance(0.15) && hasRoom("plant_scraps")) add("plant_scraps", 1)
        }
        if (roll(skill("seed_saver"))) add(def.seedItem, 1)
        val xp = (ceil(def.xp * q.mult).toInt() + if (first) 10 else 0).let { if (auto) (it + 1) / 2 else it }
        p.harvests++
        state.bump("harvested", qty.toLong())
        state.bump("harvest_${def.id}", qty.toLong())
        if (q == Quality.GOLD) state.bump("harvest_gold", qty.toLong())
        if (def.regrows) {
            p.growth = (def.growHours - def.regrowHours) * 60
            p.cared = false; p.battered = false
        } else {
            p.clear()
        }
        events += WorldEvent.Harvested(i, def.id, qty, q, xp)
        gainXp(xp)
        return true
    }

    private fun rollQuality(p: PlotState): Quality {
        val care = if (p.cared) 1 else 0
        val l = state.level
        val luck = if (lucky) 0.10 else 0.0
        val gold = min(0.50, 0.01 + 0.004 * l + 0.02 * care + p.fertGold + skill("star_grower") + luck)
        val silver = min(0.90 - gold, 0.10 + 0.008 * l + 0.06 * care + p.fertSilver + 1.5 * skill("star_grower"))
        val r = rng().nextDouble()
        return when { r < gold -> Quality.GOLD; r < gold + silver -> Quality.SILVER; else -> Quality.NORMAL }
    }

    /** Clears a withered or scorched plot (scorched gives 1 coal, withered gives a withered crop). */
    fun clearPlot(i: Int): Boolean {
        val p = plot(i)
        if (!p.needsClearing) return false
        if (p.scorched) add("coal", 1) else add("withered_crop", 1)
        p.clear()
        events += WorldEvent.Cleared(i)
        return true
    }

    // ---------------------------------------------------------------- market
    /** Deterministic ±30% daily swing per item (GAME_DESIGN §13). */
    fun dailyMult(item: String, day: Int): Double {
        val h = item.hashCode().toLong()
        val knot = Math.floorDiv(day, 4)
        val f = (day - knot * 4) / 4.0
        val a = hash01(h, knot.toLong(), 1) * 2 - 1
        val b = hash01(h, knot + 1L, 1) * 2 - 1
        val s = (1 - cos(PI * f)) / 2
        val trend = 0.20 * (a + (b - a) * s)
        val jitter = 0.10 * (hash01(h, day.toLong(), 7) * 2 - 1)
        return (1 + trend + jitter).coerceIn(0.70, 1.30)
    }

    private fun hash01(a: Long, b: Long, c: Long): Double = Rng(state.seed xor (a * 1_000_003L) xor (b * 7919L) xor (c shl 40)).nextDouble()

    private fun seasonMult(id: String): Double = if (data.crops[id] != null && id != "wheat" && outOfSeason(id)) 1.25 else 1.0

    private fun supplyMult(id: String, extra: Double = 0.0): Double {
        val t = 200.0 + 100.0 * state.level
        val glut = (state.glut[id] ?: 0.0) + extra
        return max(0.50, 1 - 0.25 * max(0.0, glut - t) / t)
    }

    /** Collection page bonus: ores sell for 5% more once the Mine page is complete. */
    private fun bonusMult(id: String) = sellSkillMult() * cafeMult(id) * if (id.endsWith("_ore") && hasBonus("ore_price")) 1.05 else 1.0

    /** Current sell price of one unit of [key] (quality included). */
    fun unitPrice(key: String): Int {
        val id = baseId(key)
        val base = data.item(id).price
        if (base <= 0) return 0
        return max(1, (base * dailyMult(id, time.dayIndex) * seasonMult(id) * Quality.of(key).mult * supplyMult(id) * bonusMult(id)).roundToInt())
    }

    /** -1 falling, 0 flat, 1 rising versus yesterday. */
    fun trend(id: String): Int {
        val d = time.dayIndex
        val delta = dailyMult(id, d) - dailyMult(id, d - 1)
        return when { delta >= 0.03 -> 1; delta <= -0.03 -> -1; else -> 0 }
    }

    /** Percent of base price today, e.g. 112 for +12%. */
    fun percentOfBase(id: String) = (dailyMult(id, time.dayIndex) * seasonMult(id) * supplyMult(id) * 100).roundToInt()

    /** Coins for selling [qty] of [key] now, accounting for saturation as units are sold. */
    fun quoteSale(key: String, qty: Int): Int {
        val id = baseId(key)
        val base = data.item(id).price
        if (base <= 0) return 0
        var total = 0
        for (n in 0 until qty) {
            total += max(1, (base * dailyMult(id, time.dayIndex) * seasonMult(id) * Quality.of(key).mult * supplyMult(id, n * base.toDouble()) * bonusMult(id)).roundToInt())
        }
        return total
    }

    fun sell(key: String, qty: Int): Int {
        val n = min(qty, count(key))
        if (n <= 0) return 0
        val coins = quoteSale(key, n)
        if (coins <= 0) return 0
        remove(key, n)
        val id = baseId(key)
        state.glut[id] = (state.glut[id] ?: 0.0) + n * data.item(id).price
        if (n >= 20 && percentOfBase(id) >= 120) state.bump("wizard_sales")
        if (data.crops[id]?.let { time.season !in it.seasons } == true) state.bump("out_season_sales")
        if (data.crops.containsKey(id)) state.bump("crops_sold", n.toLong())
        state.coins += coins
        state.bump("coins_earned", coins.toLong())
        events += WorldEvent.Sold(key, n, coins)
        gainXp(coins / 50)
        return coins
    }

    // ---------------------------------------------------------------- orders
    fun ensureOrderSlots() {
        val want = data.level(state.level).orderSlots + villageOrderSlots
        while (state.orders.size < want) state.orders.add(OrderSlot().apply { nextAt = state.minutes })
    }

    private fun orderDelayMinutes() = Time.realSecondsToGameMinutes((if (state.level < 5) 120.0 else 300.0) - if (hasBonus("order_speed")) 60.0 else 0.0)

    private fun tickOrders() {
        ensureOrderSlots()
        state.orders.forEachIndexed { i, s ->
            if (s.order == null && state.minutes >= s.nextAt) {
                s.order = generateOrder() ?: return@forEachIndexed
                events += WorldEvent.OrderArrived(i)
            }
        }
    }

    fun villagersUnlocked() = data.villagers.filter { it.unlockLevel <= state.level && it.tier <= state.villageTier }

    /** Builds a random order from items the player has obtained (GAME_DESIGN §14). */
    fun generateOrder(): Order? {
        val rng = rng()
        val busy = state.orders.mapNotNull { it.order?.villager }.toSet()
        val villager = villagersUnlocked().filter { it.id !in busy }.let { if (it.isEmpty()) return null else it[rng.nextInt(it.size)] }
        // Only what can be had right now (Docs/DECISIONS.md, round 2).
        val can = obtainableNow()
        val pool = can.filter { id ->
            val item = data.items[id] ?: return@filter false
            item.price > 0 && item.storage != Storage.NONE && id !in NOT_ORDERED && !id.startsWith("mineral_") &&
                item.category !in setOf("fixture", "rare", "seed", "mineral") && (data.crops[id]?.let { it.unlockLevel <= state.level } ?: true)
        }
        if (pool.isEmpty()) return null
        val l = state.level
        val lines = when {
            l < 5 -> if (rng.chance(0.6)) 1 else 2
            l < 15 -> rng.nextDouble().let { if (it < 0.3) 1 else if (it < 0.8) 2 else 3 }
            else -> if (rng.chance(0.5)) 2 else 3
        }.coerceAtMost(pool.size)
        val value = (20 + 10.0 * l + 1.6 * l * l) * rng.range(0.8, 1.2)
        val picked = ArrayList<String>()
        repeat(lines) {
            val weights = pool.filter { it !in picked }.map { id ->
                var w = 1.0
                if (id in villager.favorites) w *= 3
                if (data.crops[id]?.let { time.season in it.seasons } == true) w *= 1.5
                if (count(id, anyQuality = true) > 0) w *= 1.5
                if (id in state.recentOrderItems) w *= 0.3
                id to w
            }
            if (weights.isEmpty()) return@repeat
            var r = rng.nextDouble() * weights.sumOf { it.second }
            picked += (weights.firstOrNull { (_, w) -> r -= w; r <= 0 } ?: weights.last()).first
        }
        val orderLines = picked.map { id ->
            val base = max(1, data.item(id).price)
            OrderLine(id, ((value / picked.size) / base).roundToInt().coerceIn(1, 40))
        }
        val raw = orderLines.sumOf { it.qty * data.item(it.item).price }.toDouble()
        val hearts = hearts(villager.id)
        val coins = niceRound(raw * 1.35 * (1 + (if (hearts >= 3) 0.10 else if (hearts >= 1) 0.05 else 0.0)))
        val xp = max(10, (0.30 * raw).roundToInt() + 5 * orderLines.size)
        state.recentOrderItems.addAll(picked)
        while (state.recentOrderItems.size > 8) state.recentOrderItems.removeAt(0)
        return Order(++state.orderCounter, villager.id, orderLines, coins, xp)
    }

    fun canDeliver(slot: Int): Boolean {
        val o = state.orders.getOrNull(slot)?.order ?: return false
        return o.lines.all { count(it.item, anyQuality = true) >= it.qty }
    }

    fun deliver(slot: Int): Boolean {
        val s = state.orders.getOrNull(slot) ?: return false
        val o = s.order ?: return false
        if (!canDeliver(slot)) { events += WorldEvent.Blocked("Missing items"); return false }
        o.lines.forEach { removeAny(it.item, it.qty) }
        val coins = (o.coins * (1 + skill("charmer")) * orderPayMult()).roundToInt()
        state.coins += coins
        val v = data.villagers.firstOrNull { it.id == o.villager }
        val fav = o.lines.count { v != null && it.item in v.favorites }
        state.friendship[o.villager] = (state.friendship[o.villager] ?: 0) + 10 + 5 * o.lines.size + 5 * fav + if (o.big) 20 else 0
        s.order = null
        s.nextAt = state.minutes + orderDelayMinutes()
        state.bump("orders")
        state.bump("coins_earned", coins.toLong())
        events += WorldEvent.OrderDelivered(slot, coins, o.xp)
        gainXp(o.xp)
        return true
    }

    fun trashOrder(slot: Int) {
        val s = state.orders.getOrNull(slot) ?: return
        s.order = null
        s.nextAt = state.minutes + Time.realSecondsToGameMinutes(900.0)
    }

    fun hearts(villager: String): Int {
        val f = state.friendship[villager] ?: 0
        return HEARTS.count { f >= it }
    }

    // ----------------------------------------------------------- notifications
    /** Soonest moment (real seconds from now) a planted crop becomes ready, or null. */
    fun secondsUntilNextCrop(): Double? = state.plots
        .filter { it.owned && it.crop != null && !it.needsClearing && !isReady(it) }
        .minOfOrNull { secondsToReady(it) }

    companion object {
        const val DRY_SPEED = 0.6
        const val STEP_MINUTES = 15.0
        const val OFFLINE_CAP_SECONDS = 24 * 3600.0
        const val PLOT_COUNT = FieldLayout.FIELD
        val HEARTS = intArrayOf(100, 250, 500, 850, 1300)
        /** Rare finds and junk that villagers never ask for. */
        val NOT_ORDERED = setOf("prismatic_shard", "golden_feather", "golden_acorn", "heartwood", "old_coin", "withered_crop", "plant_scraps", "diamond", "bird_nest")

        /** A fresh save with the starting kit (GAME_DESIGN §17). */
        fun newGame(data: GameData, seed: Long): World {
            val s = GameState(seed)
            s.coins = 250
            s.energy = 100.0
            repeat(FieldLayout.TOTAL) { s.plots.add(PlotState(it)) }
            for (i in FieldLayout.plotsOf(0)) s.plots[i].owned = true
            for (i in 0 until 2) s.plots[i].apply { crop = "wheat"; growth = data.crop("wheat").growMinutes; cared = true }
            val w = World(data, s)
            mapOf(
                "seed_wheat" to 12, "seed_carrot" to 6, "carrot" to 3, "wood" to 20, "stone" to 15, "fibre" to 6,
                "plank" to 2, "basic_fertilizer" to 2,
            ).forEach { (k, v) -> s.inventory[k] = v }
            s.discovered.addAll(listOf("wheat", "carrot", "wood", "stone", "fibre", "plank", "basic_fertilizer"))
            s.forecast = Weather.SUNNY
            s.lastDayRolled = 0
            w.ensureOrderSlots()
            val scripted = listOf(
                Order(1, "mara", listOf(OrderLine("wheat", 4)), 20, 12),
                Order(2, "tobias", listOf(OrderLine("wood", 8)), 35, 12),
                Order(3, "mara", listOf(OrderLine("carrot", 3)), 35, 12),
            )
            scripted.forEachIndexed { i, o -> s.orders[i].order = o }
            s.orderCounter = scripted.size
            w.events.clear()
            return w
        }
    }
}
