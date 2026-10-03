package com.genshincalc.core

import com.genshincalc.core.calc.BaseStats
import com.genshincalc.core.model.GameDataSet
import com.genshincalc.core.model.Stat
import com.genshincalc.core.model.WeaponBuild
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Base stats checked against the values the game shows on the character/weapon screens. */
class BaseStatsTest {
    private val data = GameDataSet.load()

    private fun base(id: String, level: Int = 90, ascended: Boolean = true) =
        BaseStats.character(data, data.character(id), level, ascended)

    @Test
    fun loadsAllData() {
        assertTrue(data.characters.size >= 120, "characters: ${data.characters.size}")
        assertTrue(data.weapons.size >= 200, "weapons: ${data.weapons.size}")
        assertTrue(data.artifactSets.size >= 50, "sets: ${data.artifactSets.size}")
        assertTrue(data.enemies.isNotEmpty())
        // Every damage row must reference talent params that exist.
        for (c in data.characters) for (h in c.hits) for (p in h.parts) for (t in p.terms) {
            val talent = c.talent(h.talent)
            assertTrue(talent?.params?.containsKey(t.param) == true, "${c.id} ${h.id} ${t.param}")
        }
    }

    @Test
    fun huTaoLevel90() {
        val b = base("hutao")
        assertEquals(15552, b.hp.roundToInt())
        assertEquals(106, b.atk.roundToInt())
        assertEquals(876, b.def.roundToInt())
        assertEquals(Stat.CRIT_DMG, b.ascensionStat)
        assertEquals(0.384, b.ascensionValue, 1e-9)
    }

    @Test
    fun otherCharactersLevel90() {
        base("raidenshogun").let {
            assertEquals(12907, it.hp.roundToInt()); assertEquals(337, it.atk.roundToInt()); assertEquals(789, it.def.roundToInt())
            assertEquals(Stat.ER, it.ascensionStat); assertEquals(0.32, it.ascensionValue, 1e-9)
        }
        base("bennett").let {
            assertEquals(12397, it.hp.roundToInt()); assertEquals(191, it.atk.roundToInt()); assertEquals(771, it.def.roundToInt())
        }
        base("kaedeharakazuha").let {
            assertEquals(13348, it.hp.roundToInt()); assertEquals(297, it.atk.roundToInt()); assertEquals(807, it.def.roundToInt())
            assertEquals(Stat.EM, it.ascensionStat); assertEquals(115.2, it.ascensionValue, 1e-6)
        }
    }

    @Test
    fun ascensionPhases() {
        assertEquals(0, BaseStats.ascensionFor(1, false))
        assertEquals(0, BaseStats.ascensionFor(20, false))
        assertEquals(1, BaseStats.ascensionFor(20, true))
        assertEquals(5, BaseStats.ascensionFor(80, false))
        assertEquals(6, BaseStats.ascensionFor(80, true))
        assertEquals(6, BaseStats.ascensionFor(90, true))
        assertEquals(6, BaseStats.ascensionFor(100, true))
        // Hu Tao 80/80 vs 80/90: same level, different ascension bonus.
        val a = base("hutao", 80, ascended = false)
        val b = base("hutao", 80, ascended = true)
        assertTrue(b.hp > a.hp)
    }

    @Test
    fun weaponsLevel90() {
        fun w(id: String) = BaseStats.weapon(data, data.weapon(id), WeaponBuild(id, 90))
        w("staffofhoma").let {
            assertEquals(608, it.atk.roundToInt()); assertEquals(Stat.CRIT_DMG, it.substat)
            assertEquals(0.662, it.substatValue, 0.0005)
        }
        w("mistsplitterreforged").let { assertEquals(674, it.atk.roundToInt()); assertEquals(0.441, it.substatValue, 0.0005) }
        w("thecatch").let { assertEquals(510, it.atk.roundToInt()); assertEquals(0.459, it.substatValue, 0.0005) }
        w("aquilafavonia").let { assertEquals(674, it.atk.roundToInt()); assertEquals(0.413, it.substatValue, 0.0005) }
    }

    @Test
    fun artifactMainStats() {
        assertEquals(4780.0, BaseStats.artifactMain(data, Stat.HP, 5, 20))
        assertEquals(311.0, BaseStats.artifactMain(data, Stat.ATK, 5, 20))
        assertEquals(0.466, BaseStats.artifactMain(data, Stat.ATK_PCT, 5, 20))
        assertEquals(0.311, BaseStats.artifactMain(data, Stat.CRIT_RATE, 5, 20))
        assertEquals(0.622, BaseStats.artifactMain(data, Stat.CRIT_DMG, 5, 20))
        assertEquals(186.5, BaseStats.artifactMain(data, Stat.EM, 5, 20))
        assertEquals(0.583, BaseStats.artifactMain(data, Stat.PHYSICAL_DMG, 5, 20))
        assertEquals(0.0389, BaseStats.maxSubstatRoll(data, Stat.CRIT_RATE), 1e-9)
    }
}
