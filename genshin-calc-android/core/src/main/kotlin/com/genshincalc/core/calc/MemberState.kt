package com.genshincalc.core.calc

import com.genshincalc.core.model.CharacterData
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.GameDataSet
import com.genshincalc.core.model.MemberBuild
import com.genshincalc.core.model.Stat
import com.genshincalc.core.model.StatMode
import com.genshincalc.core.model.TalentType
import com.genshincalc.core.model.Team
import com.genshincalc.core.model.WeaponData
import com.genshincalc.core.model.WeaponType

/** Supplies the effects of characters, weapons, artifact sets and the party. */
interface EffectProvider {
    fun characterEffects(characterId: String): List<Effect>
    fun weaponEffects(weaponId: String): List<Effect>
    /** Effects of a set; each effect's requirement checks the number of pieces worn. */
    fun setEffects(setId: String): List<Effect>
    /** Party-wide effects with no owner (elemental resonance). */
    fun teamEffects(): List<Effect>
}

internal class TeamInfoImpl(team: Team, data: GameDataSet) : TeamInfo {
    private val chars = team.members.map { data.character(it.characterId) }
    override val size: Int = chars.size
    override val elements: List<Element> = chars.map { it.element }
    override val regions: List<String?> = chars.map { it.region }
    override val characterIds: List<String> = chars.map { it.id }
}

/** Mutable working state of one party member during a calculation. */
class MemberState internal constructor(
    val index: Int,
    val build: MemberBuild,
    val character: CharacterData,
    val weapon: WeaponData?,
    private val data: GameDataSet,
    override val team: TeamInfo,
) : EffectOwner {
    override val characterId: String get() = character.id
    override val element: Element get() = character.element
    val weaponType: WeaponType get() = character.weapon
    override val level: Int get() = build.level
    override val ascension: Int = BaseStats.ascensionFor(build.level, build.ascended)
    override val constellation: Int get() = build.constellation.coerceIn(0, 6)
    override val refinement: Int get() = (build.weapon?.refinement ?: 1).coerceIn(1, 5)

    private val pieces = build.artifacts.pieceCounts()
    override fun setPieces(setId: String): Int = pieces[setId] ?: 0

    val manual: Boolean get() = build.statMode == StatMode.MANUAL

    /** Buffs received by this member. */
    val mods = Modifiers()

    /** Character screen stats (what the game shows out of combat). */
    var sheet: StatSheet = StatSheet.withDefaults()
        internal set

    /** Own stats incl. constant buffs, used when this member's stats scale someone else's buff. */
    var selfStats: StatSheet = StatSheet.withDefaults()
        internal set

    /** All buffs except stat conversions. */
    var total: StatSheet = StatSheet.withDefaults()
        internal set

    /** Final stats used for damage. */
    var final: StatSheet = StatSheet.withDefaults()
        internal set

    private val talentLevels = mutableMapOf<TalentType, Int>()

    /** Effective talent level (base + C3/C5 + other bonuses), max 15. */
    fun talentLevel(type: TalentType): Int = talentLevels[type] ?: build.talents.of(type)

    internal fun finalizeTalentLevels() {
        for (type in listOf(TalentType.NORMAL, TalentType.SKILL, TalentType.BURST)) {
            var lvl = build.talents.of(type).coerceIn(1, 10)
            if (constellation >= 3 && character.c3 == type) lvl += 3
            if (constellation >= 5 && character.c5 == type) lvl += 3
            lvl += mods.talentBonus[type] ?: 0
            talentLevels[type] = lvl.coerceIn(1, 15)
        }
    }

    fun talentParam(type: TalentType, param: String): Double {
        val values = character.talent(type)?.params?.get(param) ?: return 0.0
        return values[(talentLevel(type) - 1).coerceIn(0, values.size - 1)]
    }

    fun weaponRefinementValue(index: Int): Double {
        val raw = weapon?.refinements?.getOrNull(refinement - 1)?.getOrNull(index) ?: return 0.0
        return parseGameNumber(raw)
    }

    /** Builds the character-screen sheet (without static effects, which the engine adds). */
    internal fun baseSheet(): StatSheet {
        if (manual) return BaseStats.fromManual(build.manualStats)
        val sheet = StatSheet.withDefaults()
        val base = BaseStats.character(data, character, build.level, build.ascended)
        sheet[Stat.BASE_HP] = base.hp
        sheet[Stat.BASE_DEF] = base.def
        var baseAtk = base.atk
        base.ascensionStat?.let { sheet.add(it, base.ascensionValue) }
        val weaponBuild = build.weapon
        if (weapon != null && weaponBuild != null) {
            val w = BaseStats.weapon(data, weapon, weaponBuild)
            baseAtk += w.atk
            w.substat?.let { sheet.add(it, w.substatValue) }
        }
        sheet[Stat.BASE_ATK] = baseAtk
        BaseStats.addArtifacts(data, build.artifacts, sheet)
        return sheet
    }

    fun effectValue(effect: Effect, overrides: Map<String, Int> = build.effectStates): Int {
        val raw = overrides[effect.id] ?: effect.control.defaultValue
        return when (val c = effect.control) {
            EffectControl.Always -> 1
            is EffectControl.Toggle -> raw.coerceIn(0, 1)
            is EffectControl.Stacks -> raw.coerceIn(0, c.max)
            is EffectControl.Choice -> raw.coerceIn(0, c.options.size - 1)
        }
    }

    companion object {
        /** "20%" -> 0.2, "0.8%" -> 0.008, "12" -> 12.0, "1,000" -> 1000.0. */
        fun parseGameNumber(raw: String): Double {
            val text = raw.trim().replace(",", "")
            val number = Regex("""-?\d+(\.\d+)?""").find(text)?.value?.toDoubleOrNull() ?: return 0.0
            return if (text.contains('%')) number / 100 else number
        }
    }
}
