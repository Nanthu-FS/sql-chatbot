package com.genshincalc.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Element(val display: String) {
    PYRO("Pyro"),
    HYDRO("Hydro"),
    ANEMO("Anemo"),
    ELECTRO("Electro"),
    DENDRO("Dendro"),
    CRYO("Cryo"),
    GEO("Geo"),
    PHYSICAL("Physical");

    val dmgBonusStat: Stat
        get() = when (this) {
            PYRO -> Stat.PYRO_DMG
            HYDRO -> Stat.HYDRO_DMG
            ANEMO -> Stat.ANEMO_DMG
            ELECTRO -> Stat.ELECTRO_DMG
            DENDRO -> Stat.DENDRO_DMG
            CRYO -> Stat.CRYO_DMG
            GEO -> Stat.GEO_DMG
            PHYSICAL -> Stat.PHYSICAL_DMG
        }

    companion object {
        /** The seven elements, without Physical. */
        val elemental: List<Element> = entries.filter { it != PHYSICAL }
    }
}

@Serializable
enum class WeaponType(val display: String) {
    SWORD("Sword"),
    CLAYMORE("Claymore"),
    POLEARM("Polearm"),
    BOW("Bow"),
    CATALYST("Catalyst");

    /** Weapons whose attacks can be infused by party-wide infusions (Chongyun, Candace, Bennett C6). */
    val isMelee: Boolean get() = this == SWORD || this == CLAYMORE || this == POLEARM
}

@Serializable
enum class TalentType(val display: String, val short: String) {
    NORMAL("Normal Attack", "NA"),
    SKILL("Elemental Skill", "E"),
    BURST("Elemental Burst", "Q"),
    SPECIAL("Special", "SP"),
}

/** The attack type a hit counts as, which decides which "X DMG Bonus" buffs apply. */
@Serializable
enum class AttackCategory(val display: String) {
    NORMAL("Normal Attack"),
    CHARGED("Charged Attack"),
    PLUNGE("Plunging Attack"),
    SKILL("Elemental Skill"),
    BURST("Elemental Burst"),
    NONE("Other"),
}

@Serializable
enum class HitKind { DMG, HEAL, SHIELD }

/** Hits that are calculated with the Lunar/Stellar reaction formula instead of the regular one. */
@Serializable
enum class SpecialDamage(val display: String) {
    LUNAR_CHARGED("Lunar-Charged"),
    LUNAR_BLOOM("Lunar-Bloom"),
    LUNAR_CRYSTALLIZE("Lunar-Crystallize"),
    STELLAR_CONDUCT("Stellar-Conduct"),
    STELLAR_SWIRL("Stellar Swirl"),
    STELLAR("Stellar-Conduct / Stellar Swirl");

    val reaction: Reaction?
        get() = when (this) {
            LUNAR_CHARGED -> Reaction.LUNAR_CHARGED
            LUNAR_BLOOM -> Reaction.LUNAR_BLOOM
            LUNAR_CRYSTALLIZE -> Reaction.LUNAR_CRYSTALLIZE
            STELLAR_CONDUCT -> Reaction.STELLAR_CONDUCT
            STELLAR_SWIRL -> Reaction.STELLAR_SWIRL
            STELLAR -> null
        }
}

@Serializable
enum class ScalingStat(val display: String) { ATK("ATK"), HP("Max HP"), DEF("DEF"), EM("Elemental Mastery") }

enum class ReactionType { AMPLIFYING, ADDITIVE, TRANSFORMATIVE, LUNAR, STELLAR, SHIELD }

@Serializable
enum class Reaction(val display: String, val type: ReactionType) {
    VAPORIZE("Vaporize", ReactionType.AMPLIFYING),
    MELT("Melt", ReactionType.AMPLIFYING),
    AGGRAVATE("Aggravate", ReactionType.ADDITIVE),
    SPREAD("Spread", ReactionType.ADDITIVE),
    OVERLOADED("Overloaded", ReactionType.TRANSFORMATIVE),
    SUPERCONDUCT("Superconduct", ReactionType.TRANSFORMATIVE),
    ELECTRO_CHARGED("Electro-Charged", ReactionType.TRANSFORMATIVE),
    SWIRL("Swirl", ReactionType.TRANSFORMATIVE),
    SHATTERED("Shattered", ReactionType.TRANSFORMATIVE),
    BURNING("Burning", ReactionType.TRANSFORMATIVE),
    BLOOM("Bloom", ReactionType.TRANSFORMATIVE),
    HYPERBLOOM("Hyperbloom", ReactionType.TRANSFORMATIVE),
    BURGEON("Burgeon", ReactionType.TRANSFORMATIVE),
    LUNAR_CHARGED("Lunar-Charged", ReactionType.LUNAR),
    LUNAR_BLOOM("Lunar-Bloom", ReactionType.LUNAR),
    LUNAR_CRYSTALLIZE("Lunar-Crystallize", ReactionType.LUNAR),
    STELLAR_CONDUCT("Stellar-Conduct", ReactionType.STELLAR),
    STELLAR_SWIRL("Stellar Swirl", ReactionType.STELLAR),
    CRYSTALLIZE("Crystallize", ReactionType.SHIELD),
}
