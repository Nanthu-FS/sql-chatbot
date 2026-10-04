package com.genshincalc.core.model

import kotlinx.serialization.Serializable

/** The five artifact slots, with the names the game shows on an artifact's detail card. */
@Serializable
enum class ArtifactSlot(val display: String, val gameName: String, val key: String) {
    FLOWER("Flower", "Flower of Life", "flower"),
    PLUME("Plume", "Plume of Death", "plume"),
    SANDS("Sands", "Sands of Eon", "sands"),
    GOBLET("Goblet", "Goblet of Eonothem", "goblet"),
    CIRCLET("Circlet", "Circlet of Logos", "circlet");

    /** Main stats that can appear on this slot. */
    val mainStats: List<Stat>
        get() = when (this) {
            FLOWER -> listOf(Stat.HP)
            PLUME -> listOf(Stat.ATK)
            SANDS -> Stat.sandsMain
            GOBLET -> Stat.gobletMain
            CIRCLET -> Stat.circletMain
        }

    companion object {
        fun ofKey(key: String): ArtifactSlot? = entries.firstOrNull { it.key == key }
    }
}

/** How a member's artifacts are entered. */
@Serializable
enum class ArtifactMode {
    /** Sets, three main stats and substat totals (quick entry). */
    SUMMARY,

    /** Five individual pieces, e.g. scanned from screenshots. */
    PIECES,
}

/** One artifact: what the game's artifact detail card shows. */
@Serializable
data class ArtifactPiece(
    /** Stable id, shared by the copy in "My artifacts" and the copies equipped on characters. */
    val id: String,
    val setId: String? = null,
    val slot: ArtifactSlot,
    val mainStat: Stat = slot.mainStats.first(),
    val rarity: Int = 5,
    val level: Int = 20,
    /** Up to four substats; percent stats as fractions (CRIT Rate 3.9% = 0.039). */
    val substats: Map<Stat, Double> = emptyMap(),
) {
    /** Clamps rarity/level and drops a main stat that the slot can't have. */
    fun normalized(): ArtifactPiece {
        val r = rarity.coerceIn(1, 5)
        val maxLevel = when (r) {
            5 -> 20
            4 -> 16
            3 -> 12
            else -> 4
        }
        return copy(
            rarity = r,
            level = level.coerceIn(0, maxLevel),
            mainStat = mainStat.takeIf { it in slot.mainStats } ?: slot.mainStats.first(),
            substats = substats.filter { (s, v) -> s in Stat.substats && v > 0 && s != mainStat }.entries.take(4).associate { it.toPair() },
        )
    }
}
