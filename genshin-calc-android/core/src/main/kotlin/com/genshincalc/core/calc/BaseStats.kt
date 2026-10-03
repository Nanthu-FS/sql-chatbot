package com.genshincalc.core.calc

import com.genshincalc.core.model.ArtifactBuild
import com.genshincalc.core.model.CharacterData
import com.genshincalc.core.model.GameDataSet
import com.genshincalc.core.model.ManualStats
import com.genshincalc.core.model.Stat
import com.genshincalc.core.model.WeaponBuild
import com.genshincalc.core.model.WeaponData

/** Level / ascension / weapon / artifact stat math. */
object BaseStats {

    /** Max level of each ascension phase: A0 = 20, A1 = 40 ... A6 = 90. */
    private val ASCENSION_CAPS = intArrayOf(20, 40, 50, 60, 70, 80, 90)

    const val MAX_CHARACTER_LEVEL = 100

    /** Ascension phase for [level]; [ascended] decides "80/80" (false) vs "80/90" (true) at a cap. */
    fun ascensionFor(level: Int, ascended: Boolean): Int {
        var phase = 0
        for ((i, cap) in ASCENSION_CAPS.withIndex()) {
            if (level > cap || (level == cap && ascended && i < ASCENSION_CAPS.size - 1)) phase = i + 1
        }
        return phase.coerceAtMost(6)
    }

    /** True if [level] is an ascension cap where the user can choose ascended or not. */
    fun isAscensionCap(level: Int, maxLevel: Int = 90): Boolean = level in ASCENSION_CAPS && level < maxLevel

    /** Character base HP/ATK/DEF (ATK without the weapon) and the ascension stat bonus. */
    data class CharacterBase(val hp: Double, val atk: Double, val def: Double, val ascensionStat: Stat?, val ascensionValue: Double)

    fun character(data: GameDataSet, char: CharacterData, level: Int, ascended: Boolean): CharacterBase {
        val lvl = level.coerceIn(1, MAX_CHARACTER_LEVEL)
        val asc = ascensionFor(lvl, ascended)
        val s = char.stats
        fun grow(base: Double, curve: String, bonus: List<Double>) =
            base * data.characterCurve(curve)[lvl] + (bonus.getOrNull(asc) ?: bonus.lastOrNull() ?: 0.0)
        return CharacterBase(
            hp = grow(s.hp, s.curveHp, s.ascHp),
            atk = grow(s.atk, s.curveAtk, s.ascAtk),
            def = grow(s.def, s.curveDef, s.ascDef),
            ascensionStat = s.ascStat,
            ascensionValue = s.ascStatValues.getOrNull(asc) ?: 0.0,
        )
    }

    data class WeaponBase(val atk: Double, val substat: Stat?, val substatValue: Double)

    fun weapon(data: GameDataSet, weapon: WeaponData, build: WeaponBuild): WeaponBase {
        val lvl = build.level.coerceIn(1, weapon.maxLevel)
        val asc = ascensionFor(lvl, build.ascended).coerceAtMost(weapon.ascAtk.size - 1).coerceAtLeast(0)
        val atk = weapon.baseAtk * data.weaponCurve(weapon.curveAtk)[lvl] + (weapon.ascAtk.getOrNull(asc) ?: 0.0)
        val sub = weapon.curveSub?.let { weapon.substatBase * data.weaponCurve(it)[lvl] } ?: 0.0
        return WeaponBase(atk, weapon.substat, sub)
    }

    /** Main stat value of an artifact piece. */
    fun artifactMain(data: GameDataSet, stat: Stat, rarity: Int, level: Int): Double {
        val table = data.curves.artifactMain[rarity.toString()]?.get(stat) ?: return 0.0
        return table[level.coerceIn(0, table.size - 1)]
    }

    /** Max artifact level for a rarity (+20 for 4-5 stars). */
    fun artifactMaxLevel(rarity: Int): Int = when (rarity) {
        5 -> 20
        4 -> 16
        3 -> 12
        else -> 4
    }

    /** Substat value of one maximum roll. */
    fun maxSubstatRoll(data: GameDataSet, stat: Stat, rarity: Int = 5): Double =
        data.curves.artifactSub[rarity.toString()]?.get(stat)?.lastOrNull() ?: 0.0

    /** Adds the five main stats and the substats of [build] to [sheet]. */
    fun addArtifacts(data: GameDataSet, build: ArtifactBuild, sheet: StatSheet) {
        val r = build.rarity
        val lvl = build.mainLevel.coerceIn(0, artifactMaxLevel(r))
        sheet.add(Stat.HP, artifactMain(data, Stat.HP, r, lvl))
        sheet.add(Stat.ATK, artifactMain(data, Stat.ATK, r, lvl))
        for (main in listOf(build.sands, build.goblet, build.circlet)) {
            sheet.add(main, artifactMain(data, main, r, lvl))
        }
        for ((stat, value) in build.substats) sheet.add(stat, value)
    }

    /**
     * Turns the attribute screen numbers into a [StatSheet]. Bonus HP/ATK/DEF are stored as flat
     * values so that totals match the screen exactly.
     */
    fun fromManual(manual: ManualStats): StatSheet = StatSheet().apply {
        this[Stat.BASE_HP] = manual.baseHp
        this[Stat.BASE_ATK] = manual.baseAtk
        this[Stat.BASE_DEF] = manual.baseDef
        this[Stat.HP] = manual.hp - manual.baseHp
        this[Stat.ATK] = manual.atk - manual.baseAtk
        this[Stat.DEF] = manual.def - manual.baseDef
        this[Stat.EM] = manual.em
        this[Stat.CRIT_RATE] = manual.critRate
        this[Stat.CRIT_DMG] = manual.critDmg
        this[Stat.ER] = manual.er
        this[Stat.HEALING_BONUS] = manual.healingBonus
        for ((element, bonus) in manual.dmgBonus) this[element.dmgBonusStat] = bonus
    }
}
