package com.genshincalc.core.model

import kotlinx.serialization.Serializable

/**
 * Character attributes. Percent stats are stored as fractions (46.6% -> 0.466).
 *
 * Totals follow the game: Max HP = Base HP x (1 + HP%) + flat HP, same for ATK and DEF.
 */
@Serializable
enum class Stat(val display: String, val short: String, val percent: Boolean) {
    BASE_HP("Base HP", "Base HP", false),
    BASE_ATK("Base ATK", "Base ATK", false),
    BASE_DEF("Base DEF", "Base DEF", false),
    HP("HP", "HP", false),
    HP_PCT("HP%", "HP%", true),
    ATK("ATK", "ATK", false),
    ATK_PCT("ATK%", "ATK%", true),
    DEF("DEF", "DEF", false),
    DEF_PCT("DEF%", "DEF%", true),
    EM("Elemental Mastery", "EM", false),
    ER("Energy Recharge", "ER", true),
    CRIT_RATE("CRIT Rate", "CR", true),
    CRIT_DMG("CRIT DMG", "CD", true),
    HEALING_BONUS("Healing Bonus", "Heal", true),
    INCOMING_HEALING("Incoming Healing Bonus", "Inc. Heal", true),
    SHIELD_STRENGTH("Shield Strength", "Shield", true),
    PYRO_DMG("Pyro DMG Bonus", "Pyro%", true),
    HYDRO_DMG("Hydro DMG Bonus", "Hydro%", true),
    ANEMO_DMG("Anemo DMG Bonus", "Anemo%", true),
    ELECTRO_DMG("Electro DMG Bonus", "Electro%", true),
    DENDRO_DMG("Dendro DMG Bonus", "Dendro%", true),
    CRYO_DMG("Cryo DMG Bonus", "Cryo%", true),
    GEO_DMG("Geo DMG Bonus", "Geo%", true),
    PHYSICAL_DMG("Physical DMG Bonus", "Phys%", true),

    /** "All DMG Bonus" - not shown on the character screen. */
    ALL_DMG("DMG Bonus (all)", "DMG%", true);

    val element: Element?
        get() = when (this) {
            PYRO_DMG -> Element.PYRO
            HYDRO_DMG -> Element.HYDRO
            ANEMO_DMG -> Element.ANEMO
            ELECTRO_DMG -> Element.ELECTRO
            DENDRO_DMG -> Element.DENDRO
            CRYO_DMG -> Element.CRYO
            GEO_DMG -> Element.GEO
            PHYSICAL_DMG -> Element.PHYSICAL
            else -> null
        }

    companion object {
        val elementalDmg: List<Stat> = Element.entries.map { it.dmgBonusStat }

        /** Substats that can roll on artifacts. */
        val substats: List<Stat> = listOf(CRIT_RATE, CRIT_DMG, ATK_PCT, ATK, HP_PCT, HP, DEF_PCT, DEF, EM, ER)

        val sandsMain: List<Stat> = listOf(ATK_PCT, HP_PCT, DEF_PCT, EM, ER)
        val gobletMain: List<Stat> = listOf(
            PYRO_DMG, HYDRO_DMG, ANEMO_DMG, ELECTRO_DMG, DENDRO_DMG, CRYO_DMG, GEO_DMG, PHYSICAL_DMG,
            ATK_PCT, HP_PCT, DEF_PCT, EM,
        )
        val circletMain: List<Stat> = listOf(CRIT_RATE, CRIT_DMG, ATK_PCT, HP_PCT, DEF_PCT, EM, HEALING_BONUS)
    }
}
