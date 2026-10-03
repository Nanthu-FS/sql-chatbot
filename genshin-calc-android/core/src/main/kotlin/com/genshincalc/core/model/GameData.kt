package com.genshincalc.core.model

import kotlinx.serialization.Serializable

// JSON schema of the bundled game data (core/src/main/resources/gamedata), produced by
// tools/datagen/generate.py.

@Serializable
data class CharactersFile(val gameVersion: String, val characters: List<CharacterData>)

@Serializable
data class CharacterData(
    val id: String,
    val name: String,
    val title: String? = null,
    val element: Element,
    val weapon: WeaponType,
    val rarity: Int,
    val region: String? = null,
    val affiliation: String? = null,
    val constellation: String? = null,
    val birthday: String? = null,
    val description: String? = null,
    val release: String? = null,
    val stats: CharacterStats,
    val talents: List<TalentData>,
    val passives: List<TextEntry> = emptyList(),
    val constellations: List<TextEntry> = emptyList(),
    /** Talent boosted by +3 levels at C3 / C5. */
    val c3: TalentType? = null,
    val c5: TalentType? = null,
    val hits: List<HitData> = emptyList(),
) {
    fun talent(type: TalentType): TalentData? = talents.firstOrNull { it.type == type }
}

@Serializable
data class CharacterStats(
    val hp: Double,
    val atk: Double,
    val def: Double,
    val curveHp: String,
    val curveAtk: String,
    val curveDef: String,
    /** Cumulative ascension bonuses, index = ascension phase (0..6). */
    val ascHp: List<Double>,
    val ascAtk: List<Double>,
    val ascDef: List<Double>,
    val ascStat: Stat? = null,
    val ascStatValues: List<Double> = emptyList(),
)

@Serializable
data class TalentData(
    val type: TalentType,
    val name: String? = null,
    val description: String? = null,
    val attributes: List<TalentAttribute> = emptyList(),
    /** Scaling tables: "param1" -> values for talent levels 1..15. */
    val params: Map<String, List<Double>> = emptyMap(),
)

@Serializable
data class TalentAttribute(val label: String, val value: String)

@Serializable
data class TextEntry(val name: String, val description: String? = null)

/** One row of the damage table, derived from a talent label. */
@Serializable
data class HitData(
    val id: String,
    val talent: TalentType,
    val name: String,
    val kind: HitKind,
    val category: AttackCategory,
    /** null = Physical unless the attack is infused. */
    val element: Element? = null,
    val special: SpecialDamage? = null,
    /** Parts are separate instances whose damage is summed (e.g. "45% + 45%", "30% x 3"). */
    val parts: List<HitPart>,
)

@Serializable
data class HitPart(
    val terms: List<HitTerm>,
    /** Flat addition (healing/shields), e.g. "+1,234". */
    val flat: String? = null,
    val count: Int = 1,
    val element: Element? = null,
)

@Serializable
data class HitTerm(val stat: ScalingStat, val param: String)

@Serializable
data class WeaponsFile(val weapons: List<WeaponData>)

@Serializable
data class WeaponData(
    val id: String,
    val name: String,
    val type: WeaponType,
    val rarity: Int,
    val description: String? = null,
    val baseAtk: Double,
    val curveAtk: String,
    val substat: Stat? = null,
    val substatBase: Double = 0.0,
    val curveSub: String? = null,
    val ascAtk: List<Double> = emptyList(),
    val effectName: String? = null,
    /** Passive text with {0}, {1}... placeholders, filled from [refinements]. */
    val effectTemplate: String? = null,
    /** Passive values per refinement (R1..R5) as shown in game, e.g. ["20%", "0.8%"]. */
    val refinements: List<List<String>> = emptyList(),
    val release: String? = null,
) {
    val maxLevel: Int get() = if (rarity <= 2) 70 else 90

    fun passiveText(refinement: Int): String? {
        val template = effectTemplate ?: return null
        val values = refinements.getOrNull(refinement - 1) ?: return template
        var text: String = template
        values.forEachIndexed { i, v -> text = text.replace("{$i}", v) }
        return text
    }
}

@Serializable
data class ArtifactsFile(val artifacts: List<ArtifactSetData>)

@Serializable
data class ArtifactSetData(
    val id: String,
    val name: String,
    val rarities: List<Int> = emptyList(),
    val onePiece: String? = null,
    val twoPiece: String? = null,
    val fourPiece: String? = null,
    val pieces: Map<String, String> = emptyMap(),
    val release: String? = null,
) {
    val maxRarity: Int get() = rarities.maxOrNull() ?: 5
}

@Serializable
data class EnemiesFile(val enemies: List<EnemyData>)

@Serializable
data class EnemyData(
    val id: String,
    val name: String,
    val type: String? = null,
    val category: String? = null,
    val res: Map<Element, Double> = emptyMap(),
)

@Serializable
data class CurvesFile(
    /** Growth curves indexed by level (index 0 unused). */
    val character: Map<String, List<Double>>,
    val weapon: Map<String, List<Double>>,
    /** Artifact main stat value by rarity ("1".."5") -> stat -> value per level (+0..+20). */
    val artifactMain: Map<String, Map<Stat, List<Double>>>,
    /** Artifact substat roll values by rarity -> stat -> the 4 roll tiers. */
    val artifactSub: Map<String, Map<Stat, List<Double>>>,
)
