package com.ditherfarm.game

import kotlin.math.max

/**
 * The Crystal Golem (round 6, owner picks "break its armor"): the boss on the bottom floor. Each
 * of three phases it wears more armour crystals and charges its slam faster. Break every crystal
 * before the charge ring fills; when it fills the Golem winds up ("BACK OFF!": a tap now hurts),
 * slams, and if its armour is gone it is left stunned with its heart bare ("STRIKE!"): three hits
 * end the phase. A slam with armour still on costs energy and regrows a crystal. Like critters it
 * runs on real seconds from the mine screen; out of energy, the player is knocked out.
 */
const val GOLEM_PHASES = 3
const val GOLEM_CRYSTAL_HP = 3
const val GOLEM_CORE_HITS = 3
const val GOLEM_WINDUP = 1.2
const val GOLEM_STUN = 3.5
const val GOLEM_SLAM = 8
const val GOLEM_SMACK = 6
val GOLEM_REWARD = mapOf("coins" to 10_000, "crystal_bar" to 5, "diamond" to 3)

enum class GolemStage { ARMORED, WINDUP, STUNNED, DEFEATED }

class GolemFight {
    var phase = 1
    var stage = GolemStage.ARMORED
    /** Hit points left on each armour crystal (0 = broken). */
    var crystals = IntArray(0)
    var charge = 0.0
    var timer = 0.0
    var coreHits = 0
    /** Tapped during the wind-up: the slam lands on the player. */
    var smacked = false

    val armourLeft get() = crystals.count { it > 0 }
    val chargeSeconds get() = 10.0 - 1.5 * (phase - 1)

    fun arm() { crystals = IntArray(2 + phase) { GOLEM_CRYSTAL_HP }; charge = 0.0; stage = GolemStage.ARMORED }
}

/** True on the bottom floor while the Golem still stands. */
val World.golemHere get() = state.mine.floor == MINE_BOTTOM && !state.mine.golemDefeated

/** The fight on screen, made fresh each time the player comes down to it. */
val World.golem: GolemFight get() = state.mine.golemFight ?: GolemFight().also { it.arm(); state.mine.golemFight = it }

/** Advances the fight by [dt] real seconds (the mine screen calls it each frame). */
fun World.tickGolem(dt: Double) {
    if (!golemHere || dt <= 0) return
    val f = golem
    when (f.stage) {
        GolemStage.ARMORED -> {
            f.charge += dt / f.chargeSeconds
            if (f.charge >= 1.0) { f.stage = GolemStage.WINDUP; f.timer = GOLEM_WINDUP; f.smacked = false; events += WorldEvent.GolemWindup }
        }
        GolemStage.WINDUP -> {
            f.timer -= dt
            if (f.timer > 0) return
            val armoured = f.armourLeft > 0
            val hurt = (if (armoured) GOLEM_SLAM else 0) + (if (f.smacked) GOLEM_SMACK else 0)
            state.energy = max(0.0, state.energy - hurt)
            events += WorldEvent.GolemSlam(hurt, stunned = !armoured)
            if (state.energy <= 0.0) { knockOut(); return }
            if (armoured) {
                // The slam shakes a broken crystal back into place.
                f.crystals.indexOfFirst { it <= 0 }.takeIf { it >= 0 }?.let { f.crystals[it] = GOLEM_CRYSTAL_HP }
                f.charge = 0.0; f.stage = GolemStage.ARMORED
            } else { f.stage = GolemStage.STUNNED; f.timer = GOLEM_STUN }
        }
        GolemStage.STUNNED -> {
            f.timer -= dt
            if (f.timer <= 0) { f.arm(); events += WorldEvent.GolemRecovered }
        }
        GolemStage.DEFEATED -> {}
    }
}

/** A tap on armour crystal [k] (1 energy; 1 damage, 2 with a Crystal pick). */
fun World.hitGolemCrystal(k: Int): Boolean {
    val f = golem
    if (!golemHere || f.stage == GolemStage.DEFEATED) return false
    if (f.stage == GolemStage.WINDUP) { f.smacked = true; events += WorldEvent.Blocked("Back off!"); return false }
    if (f.stage != GolemStage.ARMORED || f.crystals.getOrNull(k)?.let { it > 0 } != true) return false
    if (!spendEnergy(1)) return false
    f.crystals[k] = max(0, f.crystals[k] - if (pickTier() >= 4) 2 else 1)
    events += WorldEvent.GolemCrystalHit(k, f.crystals[k] == 0)
    if (state.energy <= 0.0) knockOut()
    return true
}

/** A tap on the Golem's heart: only lands while it is stunned. */
fun World.hitGolemCore(): Boolean {
    val f = golem
    if (!golemHere) return false
    if (f.stage == GolemStage.WINDUP) { f.smacked = true; events += WorldEvent.Blocked("Back off!"); return false }
    if (f.stage != GolemStage.STUNNED) return false
    if (!spendEnergy(1)) return false
    f.coreHits++
    if (f.coreHits < GOLEM_CORE_HITS) { events += WorldEvent.GolemCoreHit(f.phase, f.coreHits); return true }
    f.coreHits = 0
    if (f.phase < GOLEM_PHASES) {
        f.phase++
        f.arm()
        events += WorldEvent.GolemPhase(f.phase)
        return true
    }
    f.stage = GolemStage.DEFEATED
    state.mine.golemDefeated = true
    state.mine.golemFight = null
    GOLEM_REWARD.forEach { (k, v) -> if (k == "coins") state.coins += v else add(k, v) }
    giveRelic(GOLEM_RELIC)
    state.bump("golem")
    events += WorldEvent.GolemDefeated(GOLEM_REWARD)
    gainXp(1000)
    return true
}
