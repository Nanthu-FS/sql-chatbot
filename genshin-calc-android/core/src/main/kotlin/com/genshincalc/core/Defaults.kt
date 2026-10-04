package com.genshincalc.core

import com.genshincalc.core.calc.BaseStats
import com.genshincalc.core.model.ArtifactBuild
import com.genshincalc.core.model.CharacterData
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.EnemyConfig
import com.genshincalc.core.model.GameDataSet
import com.genshincalc.core.model.HitKind
import com.genshincalc.core.model.MemberBuild
import com.genshincalc.core.model.ScalingStat
import com.genshincalc.core.model.Stat
import com.genshincalc.core.model.Team
import com.genshincalc.core.model.TalentLevels
import com.genshincalc.core.model.WeaponBuild
import com.genshincalc.core.model.WeaponType

/** Sensible starting builds so a freshly added character shows realistic numbers. */
object Defaults {

    private val defaultWeapons = mapOf(
        WeaponType.SWORD to "theblacksword",
        WeaponType.CLAYMORE to "serpentspine",
        WeaponType.POLEARM to "deathmatch",
        WeaponType.BOW to "theviridescenthunt",
        WeaponType.CATALYST to "solarpearl",
    )

    private val defaultSets = mapOf(
        Element.PYRO to "crimsonwitchofflames",
        Element.HYDRO to "heartofdepth",
        Element.ELECTRO to "thunderingfury",
        Element.CRYO to "blizzardstrayer",
        Element.ANEMO to "viridescentvenerer",
        Element.GEO to "huskofopulentdreams",
        Element.DENDRO to "deepwoodmemories",
    )

    /** The stat most of the character's damage scales with. */
    fun primaryScaling(c: CharacterData): ScalingStat {
        val counts = mutableMapOf<ScalingStat, Int>()
        for (h in c.hits) {
            if (h.kind != HitKind.DMG) continue
            // Skill/Burst hits say more about a character's build than default Normal Attacks.
            val weight = if (h.talent == com.genshincalc.core.model.TalentType.NORMAL) 1 else 3
            for (p in h.parts) for (t in p.terms) counts.merge(t.stat, weight, Int::plus)
        }
        return counts.maxByOrNull { it.value }?.key ?: ScalingStat.ATK
    }

    fun build(data: GameDataSet, characterId: String): MemberBuild {
        val c = data.character(characterId)
        val scaling = primaryScaling(c)
        val (sands, mainSub) = when (scaling) {
            ScalingStat.HP -> Stat.HP_PCT to Stat.HP_PCT
            ScalingStat.DEF -> Stat.DEF_PCT to Stat.DEF_PCT
            ScalingStat.EM -> Stat.EM to Stat.EM
            ScalingStat.ATK -> Stat.ATK_PCT to Stat.ATK_PCT
        }
        val weaponId = defaultWeapons[c.weapon]?.takeIf { data.weaponOrNull(it) != null }
            ?: data.weaponsOfType(c.weapon).first().id
        // CRIT DMG circlet when the ascension stat and weapon already give plenty of CRIT Rate.
        val asc = BaseStats.character(data, c, 90, true)
        val weaponBase = BaseStats.weapon(data, data.weapon(weaponId), WeaponBuild(weaponId, 90))
        val baseCrit = 0.05 + 0.25 +
            (if (asc.ascensionStat == Stat.CRIT_RATE) asc.ascensionValue else 0.0) +
            (if (weaponBase.substat == Stat.CRIT_RATE) weaponBase.substatValue else 0.0)
        val circlet = if (baseCrit + 0.311 > 0.80) Stat.CRIT_DMG else Stat.CRIT_RATE
        val substats = buildMap {
            put(Stat.CRIT_RATE, 0.25)
            put(Stat.CRIT_DMG, 0.50)
            put(Stat.ER, 0.10)
            put(Stat.ATK, 33.0)
            if (mainSub == Stat.EM) put(Stat.EM, 60.0) else put(mainSub, 0.10)
        }
        return MemberBuild(
            characterId = c.id,
            level = 90,
            ascended = true,
            constellation = 0,
            talents = TalentLevels(9, 9, 9),
            weapon = WeaponBuild(weaponId, 90, refinement = 1),
            artifacts = ArtifactBuild(
                set4 = defaultSets[c.element]?.takeIf { data.artifactSetOrNull(it) != null },
                sands = sands,
                goblet = c.element.dmgBonusStat,
                circlet = circlet,
                substats = substats,
            ),
        )
    }

    /** A classic team that shows off vaporize, team buffs and shields. */
    fun sampleTeam(data: GameDataSet): Team {
        val ids = listOf("hutao", "xingqiu", "yelan", "zhongli").filter { data.characterOrNull(it) != null }
        val members = ids.map { id ->
            val b = build(data, id)
            when (id) {
                "hutao" -> b.copy(
                    talents = TalentLevels(10, 10, 10),
                    weapon = WeaponBuild("staffofhoma", 90, refinement = 1),
                    artifacts = b.artifacts.copy(
                        set4 = "crimsonwitchofflames", sands = Stat.HP_PCT, goblet = Stat.PYRO_DMG, circlet = Stat.CRIT_DMG,
                        substats = mapOf(Stat.CRIT_RATE to 0.42, Stat.CRIT_DMG to 0.40, Stat.HP_PCT to 0.15, Stat.EM to 80.0, Stat.ATK to 33.0),
                    ),
                )
                "zhongli" -> b.copy(
                    weapon = WeaponBuild("favoniuslance", 90, refinement = 3),
                    artifacts = b.artifacts.copy(set4 = "tenacityofthemillelith", sands = Stat.HP_PCT, goblet = Stat.HP_PCT, circlet = Stat.HP_PCT),
                )
                else -> b
            }
        }
        return Team(members = members, activeIndex = 0, enemy = EnemyConfig(level = 100))
    }
}
