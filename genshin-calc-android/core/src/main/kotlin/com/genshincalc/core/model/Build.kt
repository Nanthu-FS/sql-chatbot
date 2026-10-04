package com.genshincalc.core.model

import kotlinx.serialization.Serializable

/** Everything the user enters for one party member. */
@Serializable
data class MemberBuild(
    val characterId: String,
    val level: Int = 90,
    /** Only matters at ascension caps (20/40/50/60/70/80): true = "80/90", false = "80/80". */
    val ascended: Boolean = true,
    val constellation: Int = 0,
    /** Talent levels as shown before constellation bonuses (1..10). */
    val talents: TalentLevels = TalentLevels(),
    val weapon: WeaponBuild? = null,
    val artifacts: ArtifactBuild = ArtifactBuild(),
    /** BUILD = stats computed from weapon/artifacts, MANUAL = copied from the in-game attribute screen. */
    val statMode: StatMode = StatMode.BUILD,
    val manualStats: ManualStats = ManualStats(),
    /** State of toggles/stacks/choices of effects provided by this member, keyed by effect id. */
    val effectStates: Map<String, Int> = emptyMap(),
    /** Extra buffs the user wants to add to this member (anything not modelled by the app). */
    val customBuffs: CustomBuffs = CustomBuffs(),
)

@Serializable
data class TalentLevels(val normal: Int = 9, val skill: Int = 9, val burst: Int = 9) {
    fun of(type: TalentType): Int = when (type) {
        TalentType.NORMAL -> normal
        TalentType.SKILL -> skill
        TalentType.BURST -> burst
        TalentType.SPECIAL -> 1
    }

    fun with(type: TalentType, level: Int): TalentLevels = when (type) {
        TalentType.NORMAL -> copy(normal = level)
        TalentType.SKILL -> copy(skill = level)
        TalentType.BURST -> copy(burst = level)
        TalentType.SPECIAL -> this
    }
}

@Serializable
enum class StatMode { BUILD, MANUAL }

@Serializable
data class WeaponBuild(
    val weaponId: String,
    val level: Int = 90,
    val ascended: Boolean = true,
    val refinement: Int = 1,
)

@Serializable
data class ArtifactBuild(
    /** Four-piece set, or null for 2+2 / no set. */
    val set4: String? = null,
    val set2a: String? = null,
    val set2b: String? = null,
    val sands: Stat = Stat.ATK_PCT,
    val goblet: Stat = Stat.ATK_PCT,
    val circlet: Stat = Stat.CRIT_RATE,
    val rarity: Int = 5,
    val mainLevel: Int = 20,
    /** Substat totals across all five pieces (fractions for percent stats). */
    val substats: Map<Stat, Double> = emptyMap(),
    /** SUMMARY uses the fields above; PIECES uses [pieces] instead. */
    val mode: ArtifactMode = ArtifactMode.SUMMARY,
    /** Individually entered artifacts by slot (used in [ArtifactMode.PIECES]). */
    val pieces: Map<ArtifactSlot, ArtifactPiece> = emptyMap(),
) {
    /** Set id -> number of pieces equipped. */
    fun pieceCounts(): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        if (mode == ArtifactMode.PIECES) {
            pieces.values.mapNotNull { it.setId }.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        } else if (set4 != null) {
            counts[set4] = 4
        } else {
            listOfNotNull(set2a, set2b).forEach { counts[it] = (counts[it] ?: 0) + 2 }
        }
        return counts
    }
}

/** Values copied from the in-game character attribute screen (percent values as fractions). */
@Serializable
data class ManualStats(
    val baseHp: Double = 0.0,
    val baseAtk: Double = 0.0,
    val baseDef: Double = 0.0,
    val hp: Double = 0.0,
    val atk: Double = 0.0,
    val def: Double = 0.0,
    val em: Double = 0.0,
    val critRate: Double = 0.05,
    val critDmg: Double = 0.5,
    val er: Double = 1.0,
    val healingBonus: Double = 0.0,
    val dmgBonus: Map<Element, Double> = emptyMap(),
)

@Serializable
data class CustomBuffs(
    val atkPct: Double = 0.0,
    val atk: Double = 0.0,
    val hpPct: Double = 0.0,
    val defPct: Double = 0.0,
    val em: Double = 0.0,
    val critRate: Double = 0.0,
    val critDmg: Double = 0.0,
    val dmgBonus: Double = 0.0,
    val flatDmg: Double = 0.0,
    val resShred: Double = 0.0,
    val defReduction: Double = 0.0,
) {
    val isEmpty: Boolean get() = this == CustomBuffs()
}

@Serializable
data class EnemyConfig(
    val level: Int = 100,
    val enemyId: String? = null,
    /** Base resistance per element (fractions); missing elements default to 10%. */
    val res: Map<Element, Double> = emptyMap(),
) {
    fun baseRes(element: Element): Double = res[element] ?: DEFAULT_RES

    companion object {
        const val DEFAULT_RES = 0.10
    }
}

@Serializable
data class Team(
    val members: List<MemberBuild> = emptyList(),
    /** Index of the member whose damage is shown. */
    val activeIndex: Int = 0,
    val enemy: EnemyConfig = EnemyConfig(),
    /** Toggles of party-wide effects that belong to no single member (elemental resonance). */
    val teamEffectStates: Map<String, Int> = emptyMap(),
) {
    val active: MemberBuild? get() = members.getOrNull(activeIndex)

    companion object {
        const val MAX_SIZE = 4
    }
}
