package com.ditherfarm.game

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ------------------------------------------------------------------ skill tree
/** One skill: up to [ranks] ranks, each adding [per] (a fraction, or a count) to its effect. */
class SkillDef(val id: String, val name: String, val ranks: Int, val per: Double, val desc: String)

/** A branch of four skills that unlock in order (a skill needs one rank of the one before). */
class BranchDef(val id: String, val name: String, val short: String, val skills: List<SkillDef>)

val BRANCHES = listOf(
    BranchDef("green_thumb", "Green Thumb", "CROPS", listOf(
        SkillDef("quick_sprout", "Quick Sprout", 3, 0.04, "Crops grow 4% faster"),
        SkillDef("bountiful", "Bountiful", 3, 0.06, "6% chance of an extra crop"),
        SkillDef("star_grower", "Star Grower", 3, 0.02, "More silver and gold crops"),
        SkillDef("deep_roots", "Deep Roots", 3, 0.10, "Dry crops grow 10% faster"),
    )),
    BranchDef("woodsman", "Woodsman", "WOOD", listOf(
        SkillDef("sharp_edge", "Sharp Edge", 3, 0.05, "+5% critical axe swings"),
        SkillDef("timber", "Timber", 3, 0.10, "10% chance of extra wood"),
        SkillDef("seed_saver", "Seed Saver", 3, 0.08, "8% chance a harvest returns a seed"),
        SkillDef("light_swing", "Light Swing", 3, 0.10, "10% of axe swings cost no energy"),
    )),
    BranchDef("miner", "Miner", "MINE", listOf(
        SkillDef("prospector", "Prospector", 3, 0.06, "6% chance of extra ore"),
        SkillDef("ladder_sense", "Ladder Sense", 3, 2.0, "The ladder turns up 2 rocks sooner"),
        SkillDef("geologist", "Geologist", 3, 0.04, "4% chance a rock holds a geode"),
        SkillDef("steady_hands", "Steady Hands", 3, 0.10, "10% of pick swings cost no energy"),
    )),
    BranchDef("trader", "Trader", "TRADE", listOf(
        SkillDef("haggler", "Haggler", 3, 0.03, "Everything sells for 3% more"),
        SkillDef("bulk_buyer", "Bulk Buyer", 3, 0.05, "Seeds cost 5% less"),
        SkillDef("charmer", "Charmer", 3, 0.05, "Orders pay 5% more"),
        SkillDef("merchants_pal", "Merchant's Pal", 3, 0.05, "The merchant charges 5% less"),
    )),
    BranchDef("tinkerer", "Tinkerer", "TINKER", listOf(
        SkillDef("overclock", "Overclock", 3, 0.04, "Machines work 4% faster"),
        SkillDef("fuel_saver", "Fuel Saver", 3, 0.10, "Machines burn 10% less coal"),
        SkillDef("extra_slot", "Extra Slot", 2, 1.0, "+1 queue slot on every machine"),
        SkillDef("big_trays", "Big Trays", 3, 2.0, "Producers hold 2 more batches"),
    )),
)

val SKILLS: Map<String, SkillDef> = BRANCHES.flatMap { it.skills }.associateBy { it.id }

fun World.rank(id: String) = state.skills[id] ?: 0

/** Total effect of a skill: ranks x per-rank amount. */
fun World.skill(id: String) = rank(id) * (SKILLS[id]?.per ?: 0.0)

/** One point per level after the first. */
val World.skillPoints get() = max(0, state.level - 1 - state.skills.values.sum())

fun World.skillBlocker(id: String): String? {
    val def = SKILLS[id] ?: return "No such skill"
    val branch = BRANCHES.first { b -> b.skills.any { it.id == id } }
    val before = branch.skills.getOrNull(branch.skills.indexOf(def) - 1)
    return when {
        rank(id) >= def.ranks -> "Already mastered"
        before != null && rank(before.id) == 0 -> "Learn ${before.name} first"
        skillPoints <= 0 -> "No skill points: level up to earn more"
        else -> null
    }
}

fun World.learn(id: String): Boolean {
    skillBlocker(id)?.let { events += WorldEvent.Blocked(it); return false }
    state.skills[id] = rank(id) + 1
    events += WorldEvent.SkillLearned(id, rank(id))
    return true
}

/** Coins to take back every point (at the Workbench). */
fun World.respecCost() = 100 * state.level

fun World.respec(): Boolean {
    if (state.skills.isEmpty()) return false
    val cost = respecCost()
    if (state.coins < cost) { events += WorldEvent.Blocked("Needs $cost coins"); return false }
    state.coins -= cost
    state.skills.clear()
    events += WorldEvent.Respecced(cost)
    return true
}

/** A chance roll that draws no random number when the chance is zero, so unskilled play stays as it was. */
internal fun World.roll(p: Double) = p > 0 && rng().chance(min(1.0, p))

// ------------------------------------------------------------------ tonics
/** One-shot drinks and charms made at the Workbench and used from the Barn. */
val TONICS = setOf("growth_tonic", "lucky_charm", "energy_brew", "rain_totem")

/** Growth Tonic: every growing crop jumps ahead this many game minutes. */
const val TONIC_GROWTH_MINUTES = 120.0
const val TONIC_ENERGY = 40
/** Lucky Charm: real seconds of better quality and extra-crop odds. */
const val LUCK_SECONDS = 600.0

val World.lucky get() = state.luckUntil > state.minutes
fun World.luckSecondsLeft() = Time.gameMinutesToRealSeconds(max(0.0, state.luckUntil - state.minutes))

fun World.useBlocker(item: String): String? = when {
    item !in TONICS -> "Can't use that"
    count(item) <= 0 -> "You have no ${data.name(item)}"
    item == "energy_brew" && state.energy >= maxEnergy -> "Energy is full"
    item == "growth_tonic" && state.plots.none { growing(it) } -> "Nothing is growing"
    item == "rain_totem" && state.plots.none { it.owned && it.crop != null && !it.needsClearing && !it.watered } -> "Every crop is watered"
    else -> null
}

private fun World.growing(p: PlotState) = p.owned && p.crop != null && !p.needsClearing && !isReady(p)

fun World.useTonic(item: String): Boolean {
    useBlocker(item)?.let { events += WorldEvent.Blocked(it); return false }
    remove(item, 1)
    when (item) {
        "growth_tonic" -> for (p in state.plots) if (growing(p)) {
            p.growth += TONIC_GROWTH_MINUTES
            if (isReady(p)) events += WorldEvent.Ripened(p.index)
        }
        "energy_brew" -> state.energy = min(maxEnergy.toDouble(), state.energy + TONIC_ENERGY)
        "rain_totem" -> for (p in state.plots) if (p.owned && p.crop != null && !p.needsClearing) { p.watered = true; p.cared = true }
        "lucky_charm" -> state.luckUntil = max(state.luckUntil, state.minutes) + Time.realSecondsToGameMinutes(LUCK_SECONDS)
    }
    state.bump("tonics_used")
    events += WorldEvent.TonicUsed(item)
    return true
}

// ------------------------------------------------------------------ effects used across the game
/** Growth speed of a plot: watered crops grow at 1, dry ones slower (Quick Sprout, Deep Roots). */
fun World.growRate(watered: Boolean) =
    (1 + skill("quick_sprout")) * if (watered) 1.0 else min(1.0, World.DRY_SPEED + skill("deep_roots"))

/** Extra sell price (Haggler). */
fun World.sellSkillMult() = 1 + skill("haggler")

fun World.merchantPrice(offer: MerchantOffer) = max(1, (offer.price * (1 - skill("merchants_pal"))).roundToInt())

/** Coal a batch burns (Fuel Saver). */
fun World.fuelUse(r: RecipeDef) = r.fuel * (1 - skill("fuel_saver"))

fun World.queueSize(id: String) = (data.buildings[id]?.queue ?: 1) + skill("extra_slot").toInt()

fun World.trayCap(id: String) = (data.buildings[id]?.trayCap ?: 3) + skill("big_trays").toInt()
