package com.genshincalc.core

import com.genshincalc.core.calc.BaseStats
import com.genshincalc.core.calc.Formulas
import com.genshincalc.core.calc.TeamCalculator
import com.genshincalc.core.effects.GameEffects
import com.genshincalc.core.model.ArtifactBuild
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.EnemyConfig
import com.genshincalc.core.model.GameDataSet
import com.genshincalc.core.model.ManualStats
import com.genshincalc.core.model.MemberBuild
import com.genshincalc.core.model.Reaction
import com.genshincalc.core.model.Stat
import com.genshincalc.core.model.StatMode
import com.genshincalc.core.model.TalentLevels
import com.genshincalc.core.model.Team
import com.genshincalc.core.model.WeaponBuild
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end checks: each expected value is computed by hand from the game formulas, independent
 * of the engine's effect plumbing.
 */
class EngineTest {
    private val data = GameDataSet.load()
    private val calc = TeamCalculator(data, GameEffects)

    private fun huTao(effectStates: Map<String, Int> = emptyMap()) = MemberBuild(
        characterId = "hutao",
        talents = TalentLevels(10, 10, 10),
        weapon = WeaponBuild("staffofhoma", 90, refinement = 1),
        artifacts = ArtifactBuild(
            set4 = "crimsonwitchofflames",
            sands = Stat.HP_PCT, goblet = Stat.PYRO_DMG, circlet = Stat.CRIT_DMG,
            substats = mapOf(Stat.CRIT_RATE to 0.40, Stat.CRIT_DMG to 0.30, Stat.HP_PCT to 0.10, Stat.EM to 80.0),
        ),
        effectStates = effectStates,
    )

    @Test
    fun huTaoSoloChargedAttack() {
        val result = calc.calculate(Team(listOf(huTao()), enemy = EnemyConfig(level = 100))).members[0]

        val c = BaseStats.character(data, data.character("hutao"), 90, true)
        val w = BaseStats.weapon(data, data.weapon("staffofhoma"), WeaponBuild("staffofhoma", 90))
        val baseAtk = c.atk + w.atk
        val hp = c.hp * (1 + 0.466 + 0.20 + 0.10) + 4780
        assertEquals(hp, result.finalStats.hp, 1e-6)
        // Homa 0.8% + 1% (HP < 50%) of Max HP, Paramita: 6.256% of Max HP (max 400% Base ATK).
        val atk = baseAtk + 311 + 0.018 * hp + minOf(0.06256 * hp, 4 * baseAtk)
        assertEquals(atk, result.finalStats.atk, 1e-6)
        // The character screen shows only Homa's unconditional 0.8%.
        assertEquals(baseAtk + 311 + 0.008 * hp, result.screenStats.atk, 1e-6)

        val critRate = 0.05 + 0.40
        val critDmg = 0.5 + 0.384 + w.substatValue + 0.622 + 0.30
        // Goblet + CW 2pc + 1 CW stack + Sanguine Rouge.
        val pyro = 0.466 + 0.15 + 0.075 + 0.33
        assertEquals(pyro, result.finalStats.dmgBonus(Element.PYRO), 1e-9)

        val ca = result.hits.first { it.hit.id == "normal/charged-attack" }
        assertEquals(Element.PYRO, ca.element, "Paramita Papilio infuses Pyro")
        val defMult = 190.0 / 390.0
        val nonCrit = 2.42565 * atk * (1 + pyro) * defMult * 0.9
        assertEquals(nonCrit, ca.numbers.nonCrit, 1e-6)
        assertEquals(nonCrit * (1 + critDmg), ca.numbers.crit, 1e-6)
        assertEquals(nonCrit * (1 + critRate * critDmg), ca.numbers.average, 1e-6)

        val vape = ca.reactions.first { it.reaction == Reaction.VAPORIZE }
        val amp = 1.5 * (1 + 25.0 / 9.0 * 80 / (80 + 1400) + 0.15)
        assertEquals(nonCrit * amp, vape.numbers.nonCrit, 1e-6)
        val melt = ca.reactions.first { it.reaction == Reaction.MELT }
        assertEquals(nonCrit * 2.0 * (1 + 25.0 / 9.0 * 80 / 1480 + 0.15), melt.numbers.nonCrit, 1e-6)
    }

    @Test
    fun togglesChangeTheResult() {
        val on = calc.calculate(Team(listOf(huTao()))).members[0]
        val off = calc.calculate(Team(listOf(huTao(mapOf("char.hutao.skill" to 0))))).members[0]
        assertTrue(on.finalStats.atk > off.finalStats.atk)
        val naOff = off.hits.first { it.hit.id == "normal/1-hit-dmg" }
        assertEquals(Element.PHYSICAL, naOff.element, "No infusion without Paramita Papilio")
    }

    private fun kazuha() = MemberBuild(
        characterId = "kaedeharakazuha",
        talents = TalentLevels(1, 9, 9),
        weapon = WeaponBuild("staffofhoma"), // weapon type does not matter for the engine
        artifacts = ArtifactBuild(
            set4 = "viridescentvenerer",
            sands = Stat.EM, goblet = Stat.EM, circlet = Stat.EM,
            substats = mapOf(Stat.EM to 100.0),
        ),
    )

    @Test
    fun partyBuffsAndResonance() {
        val bennett = MemberBuild(
            characterId = "bennett", constellation = 1, talents = TalentLevels(1, 9, 10),
            weapon = WeaponBuild("staffofhoma"),
            artifacts = ArtifactBuild(set4 = "noblesseoblige"),
        )
        val xingqiu = MemberBuild(characterId = "xingqiu", constellation = 2)
        val team = Team(listOf(huTao(), bennett, xingqiu, kazuha()), enemy = EnemyConfig(level = 90))
        val res = calc.calculate(team)
        val ht = res.members[0]

        val bennettBaseAtk = res.members[1].screenStats[Stat.BASE_ATK]
        // Bennett Q (Lv10 = 100.8%) + C1 20% of his Base ATK.
        val bennettBuff = bennettBaseAtk * (1.008 + 0.2)
        val htBase = ht.screenStats[Stat.BASE_ATK]
        val hp = ht.finalStats.hp
        val expectedAtk = htBase * (1 + 0.25 + 0.20) + 311 + bennettBuff + 0.018 * hp + minOf(0.06256 * hp, 4 * htBase)
        assertEquals(expectedAtk, ht.finalStats.atk, 1e-6)

        // Kazuha's A4 uses his own EM: 115.2 (ascension) + 3 x 186.5 + 100.
        val kazuhaEm = 115.2 + 3 * 186.5 + 100
        assertEquals(kazuhaEm, res.members[3].finalStats.em, 1e-6)
        val pyro = 0.466 + 0.15 + 0.075 + 0.33 + 0.0004 * kazuhaEm
        assertEquals(pyro, ht.finalStats.dmgBonus(Element.PYRO), 1e-9)

        // VV shreds Pyro and Hydro (the swirlable party elements) by 40%.
        assertEquals(0.40, res.resShred[Element.PYRO] ?: 0.0, 1e-9)
        // Xingqiu C2 adds 15% Hydro shred on top.
        assertEquals(0.55, res.resShred[Element.HYDRO] ?: 0.0, 1e-9)
        assertEquals(null, res.resShred[Element.ELECTRO])

        val ca = ht.hits.first { it.hit.id == "normal/charged-attack" }
        val nonCrit = 2.42565 * ht.finalStats.atk * (1 + pyro) * 0.5 * Formulas.resMultiplier(0.1 - 0.4)
        assertEquals(nonCrit, ca.numbers.nonCrit, 1e-6)
    }

    @Test
    fun manualStatsSkipStaticBonuses() {
        val manual = ManualStats(
            baseHp = 15552.0, baseAtk = 714.0, baseDef = 876.0,
            hp = 34000.0, atk = 1300.0, def = 1000.0, em = 80.0,
            critRate = 0.45, critDmg = 2.468, er = 1.0,
            dmgBonus = mapOf(Element.PYRO to 0.616),
        )
        val build = huTao().copy(statMode = StatMode.MANUAL, manualStats = manual)
        val m = calc.calculate(Team(listOf(build))).members[0]
        assertEquals(34000.0, m.screenStats.hp, 1e-9)
        assertEquals(1300.0, m.screenStats.atk, 1e-9)
        // Dynamic bonuses still apply: 1% Homa (HP < 50%), Paramita, 1 CW stack, Sanguine Rouge.
        val expectedAtk = 1300.0 + 0.01 * 34000 + minOf(0.06256 * 34000, 4 * 714.0)
        assertEquals(expectedAtk, m.finalStats.atk, 1e-6)
        assertEquals(0.616 + 0.075 + 0.33, m.finalStats.dmgBonus(Element.PYRO), 1e-9)
    }

    @Test
    fun swirlDamage() {
        // Solo Kazuha: pick Pyro for the VV shred explicitly (the default only shreds party elements).
        val k = kazuha().copy(effectStates = mapOf("set.viridescentvenerer.4shred" to 1))
        val res = calc.calculate(Team(listOf(k), enemy = EnemyConfig(level = 90))).members[0]
        val em = 115.2 + 3 * 186.5 + 100
        val pyroSwirl = res.transformative.first { it.reaction == Reaction.SWIRL && it.element == Element.PYRO }
        val expected = 1446.8535 * 0.6 * (1 + 16 * em / (em + 2000) + 0.6) * Formulas.resMultiplier(0.1 - 0.4)
        assertEquals(expected, pyroSwirl.numbers.nonCrit, 1e-6)
    }
}
