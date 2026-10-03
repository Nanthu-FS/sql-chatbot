package com.genshincalc.core.calc

import com.genshincalc.core.model.AttackCategory
import com.genshincalc.core.model.CharacterData
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.HitData
import com.genshincalc.core.model.HitKind
import com.genshincalc.core.model.MemberBuild
import com.genshincalc.core.model.Reaction
import com.genshincalc.core.model.SpecialDamage
import com.genshincalc.core.model.TalentType

data class DamageNumbers(val nonCrit: Double, val crit: Double, val average: Double) {
    operator fun plus(o: DamageNumbers) = DamageNumbers(nonCrit + o.nonCrit, crit + o.crit, average + o.average)
    operator fun times(k: Double) = DamageNumbers(nonCrit * k, crit * k, average * k)

    companion object {
        val ZERO = DamageNumbers(0.0, 0.0, 0.0)
        fun flat(value: Double) = DamageNumbers(value, value, value)
    }
}

data class ReactionDamage(val reaction: Reaction, val numbers: DamageNumbers)

/** Every factor of the damage formula for one hit, for the detail view. */
data class HitBreakdown(
    val talentMultiplier: String,
    val baseDamage: Double,
    val flatDamage: Double,
    val dmgBonus: Double,
    val critRate: Double,
    val critDmg: Double,
    val defMultiplier: Double,
    val enemyRes: Double,
    val resMultiplier: Double,
)

data class HitResult(
    val hit: HitData,
    val talent: TalentType,
    val talentLevel: Int,
    val kind: HitKind,
    val category: AttackCategory,
    val element: Element,
    val special: SpecialDamage?,
    /** e.g. "93.2% ATK" or "(23.1% ATK + 46.2% EM) x3". */
    val scaling: String,
    /** DMG: without reaction. HEAL/SHIELD: the amount (same in all three fields). */
    val numbers: DamageNumbers,
    val reactions: List<ReactionDamage>,
    val breakdown: HitBreakdown?,
)

data class TransformativeResult(
    val reaction: Reaction,
    /** Swirled element for Swirl, element of the RES used otherwise. */
    val element: Element?,
    val numbers: DamageNumbers,
    val canCrit: Boolean,
    val note: String? = null,
)

data class MemberResult(
    val index: Int,
    val build: MemberBuild,
    val character: CharacterData,
    val talentLevels: Map<TalentType, Int>,
    /** What the in-game attribute screen should show. */
    val screenStats: StatSheet,
    /** Stats with every buff applied. */
    val finalStats: StatSheet,
    val hits: List<HitResult>,
    val transformative: List<TransformativeResult>,
    /** Effects provided by this member (character, weapon, artifacts, custom). */
    val ownEffects: List<AppliedEffect>,
    /** Effects currently boosting this member. */
    val receivedEffects: List<AppliedEffect>,
    val infusion: Element?,
) {
    /** Hit modifiers applied to this member (for tests and debugging). */
    internal var debugHitMods: List<HitMod> = emptyList()
    internal var debugElementOverrides: Map<String, Element> = emptyMap()
}

data class TeamResult(
    val members: List<MemberResult>,
    val enemyLevel: Int,
    val enemyRes: Map<Element, Double>,
    val resShred: Map<Element, Double>,
    val defReduction: Double,
    val teamEffects: List<AppliedEffect>,
) {
    fun member(index: Int): MemberResult? = members.getOrNull(index)
}
