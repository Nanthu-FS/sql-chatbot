package com.genshincalc.core

import com.genshincalc.core.calc.TeamCalculator
import com.genshincalc.core.effects.GameEffects
import com.genshincalc.core.model.ArtifactBuild
import com.genshincalc.core.model.GameDataSet
import com.genshincalc.core.model.MemberBuild
import com.genshincalc.core.model.Reaction
import com.genshincalc.core.model.Stat
import com.genshincalc.core.model.TalentLevels
import com.genshincalc.core.model.TalentType
import com.genshincalc.core.model.Team
import com.genshincalc.core.model.WeaponBuild
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Direct Lunar/Stellar talent damage: Talent% x Stat x Reaction multiplier x (1 + 6 EM / (EM + 2000) + Bonus)
 * x (1 + Base DMG Bonus) x CRIT x RES, no DEF. Builds use weapons without passives and no artifact sets, and
 * reach 2,000 ATK so the "Benediction/Jubilee" Base DMG bonuses are capped at 14%.
 */
class LunarStellarTest {
    private val data = GameDataSet.load()
    private val calc = TeamCalculator(data, GameEffects)

    private fun build(id: String, weapon: String) = MemberBuild(
        characterId = id,
        talents = TalentLevels(10, 10, 10),
        weapon = WeaponBuild(weapon, 90, refinement = 1),
        artifacts = ArtifactBuild(
            sands = Stat.ATK_PCT, goblet = Stat.ATK_PCT, circlet = Stat.CRIT_RATE,
            substats = mapOf(Stat.EM to 100.0, Stat.CRIT_DMG to 0.50),
        ),
    )

    private fun emBonus(em: Double) = 6 * em / (em + 2000)

    private fun talent(id: String, type: TalentType, param: String) =
        data.character(id).talents.first { it.type == type }.params.getValue(param)[9]

    @Test
    fun flinsLunarChargedSoloAndAscendant() {
        val solo = calc.calculate(Team(listOf(build("flins", "favoniuslance")))).members[0]
        assertTrue(solo.finalStats.atk > 2000)
        val hit = solo.hits.first { it.hit.id == "burst/middle-phase-lunar-charged-dmg" }
        val p = talent("flins", TalentType.BURST, "param2")
        val atk = solo.finalStats.atk
        val em = solo.finalStats.em
        // Nascent Gleam: no Ascendant bonus. Electro RES 10% -> x0.9.
        val expected = p * atk * 3 * (1 + emBonus(em)) * 1.14 * 0.9
        assertEquals(expected, hit.numbers.nonCrit, 1e-6)
        assertEquals(expected * (1 + solo.finalStats.critDmg), hit.numbers.crit, 1e-6)

        // With Ineffa the party reaches Ascendant Gleam: Flins A1 +20% Lunar-Charged DMG and Ineffa's
        // Benediction adds another capped 14% Base DMG.
        val duo = calc.calculate(Team(listOf(build("flins", "favoniuslance"), build("ineffa", "favoniuslance")))).members[0]
        val duoHit = duo.hits.first { it.hit.id == "burst/middle-phase-lunar-charged-dmg" }
        val expectedDuo = p * duo.finalStats.atk * 3 * (1 + emBonus(duo.finalStats.em) + 0.20) * 1.28 * 0.9
        assertEquals(expectedDuo, duoHit.numbers.nonCrit, 1e-6)
        assertTrue(duo.finalStats.em > em, "Ineffa's Parameter Permutation adds EM")
    }

    @Test
    fun sandroneStellarConductUsesCryoRes() {
        val m = calc.calculate(Team(listOf(build("sandrone", "prototypearchaic")))).members[0]
        assertTrue(m.finalStats.atk > 2000)
        val hit = m.hits.first { it.hit.id == "skill/prism-shot-stellar-conduct-dmg" }
        val p = talent("sandrone", TalentType.SKILL, "param2")
        val expected = p * m.finalStats.atk * (1 + emBonus(m.finalStats.em)) * 1.14 * 0.9
        assertEquals(expected, hit.numbers.nonCrit, 1e-6)
    }

    @Test
    fun anemoMembersListStellarSwirl() {
        val m = calc.calculate(Team(listOf(build("sucrose", "favoniuscodex")))).members[0]
        val swirl = m.transformative.first { it.reaction == Reaction.STELLAR_SWIRL }
        assertTrue(swirl.canCrit)
        assertTrue(swirl.numbers.nonCrit > 0)
    }
}
