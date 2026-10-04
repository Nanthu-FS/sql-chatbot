package com.genshincalc.core

import com.genshincalc.core.calc.TeamCalculator
import com.genshincalc.core.effects.GameEffects
import com.genshincalc.core.model.ArtifactBuild
import com.genshincalc.core.model.ArtifactMode
import com.genshincalc.core.model.ArtifactPiece
import com.genshincalc.core.model.ArtifactSlot
import com.genshincalc.core.model.GameDataSet
import com.genshincalc.core.model.MemberBuild
import com.genshincalc.core.model.Stat
import com.genshincalc.core.model.Team
import com.genshincalc.core.model.WeaponBuild
import com.genshincalc.core.text.ArtifactScanParser
import com.genshincalc.core.text.OcrLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArtifactScanTest {
    private val data = GameDataSet.load()

    private fun plain(text: String) = text.trimIndent().lines().map { OcrLine(it) }

    @Test
    fun cleanFlowerCard() {
        val scan = ArtifactScanParser.parse(
            plain(
                """
                Gladiator's Nostalgia
                Flower of Life
                HP
                4,780
                +20
                • CRIT Rate+10.5%
                • CRIT DMG+14.0%
                • ATK+5.8%
                • Elemental Mastery+23
                Gladiator's Finale:
                2-Piece Set: ATK +18%.
                4-Piece Set: If the wielder of this artifact set uses a Sword, Claymore or Polearm, increases their Normal Attack DMG by 35%.
                """,
            ),
            data,
        )
        assertEquals("gladiatorsfinale", scan.setId)
        assertEquals(ArtifactSlot.FLOWER, scan.slot)
        assertEquals(Stat.HP, scan.mainStat)
        assertEquals(4780.0, scan.mainValue)
        assertEquals(20, scan.level)
        assertEquals(5, scan.rarity)
        assertEquals(
            mapOf(Stat.CRIT_RATE to 0.105, Stat.CRIT_DMG to 0.14, Stat.ATK_PCT to 0.058, Stat.EM to 23.0),
            scan.substats.mapValues { Math.round(it.value * 1000) / 1000.0 },
        )
        assertTrue(scan.warnings.isEmpty(), scan.warnings.toString())
    }

    /** Inventory screenshot: the item grid on the left also has "+20" labels; the card is on the right. */
    @Test
    fun inventoryScreenshotWithGridNoise() {
        fun l(text: String, x: Int, y: Int, w: Int = 300) = OcrLine(text, x, y, x + w, y + 40)
        val lines = listOf(
            l("Artifacts", 80, 40), l("1764/2100", 900, 40),
            l("+16", 120, 300, 60), l("+20", 330, 300, 60), l("+4", 540, 300, 60),
            l("+20", 120, 520, 60), l("+0", 330, 520, 60), l("+12", 540, 520, 60),
            l("Witch's Heart Flames", 1300, 160, 500),
            l("Goblet of Eonothem", 1300, 215, 360),
            l("Pyro DMG Bonus", 1300, 330, 280),
            l("46.6%", 1300, 380, 160),
            l("+20", 1310, 520, 60),
            l("• CRIT DMG+21.0%", 1300, 600, 340),
            l("• ATK+16", 1300, 650, 200),
            l("• CRIT Rate+7.0%", 1300, 700, 320),
            l("• HP+5.3%", 1300, 750, 200),
            l("Crimson Witch of Flames:(4)", 1300, 820, 480),
            l("Sort by Level", 600, 1000, 200),
        )
        val scan = ArtifactScanParser.parse(lines.shuffled(java.util.Random(7)), data)
        assertEquals("crimsonwitchofflames", scan.setId)
        assertEquals(ArtifactSlot.GOBLET, scan.slot)
        assertEquals(Stat.PYRO_DMG, scan.mainStat)
        assertEquals(20, scan.level)
        assertEquals(5, scan.rarity)
        assertEquals(setOf(Stat.CRIT_DMG, Stat.ATK, Stat.CRIT_RATE, Stat.HP_PCT), scan.substats.keys)
        assertEquals(16.0, scan.substats[Stat.ATK])
        val piece = scan.toPiece("x")!!
        assertEquals(Stat.PYRO_DMG, piece.mainStat)
    }

    /** OCR mistakes: "CRlT", spaces around "+", a set line without the artifact name, an unactivated substat. */
    @Test
    fun noisyOcrAndLevelZero() {
        val scan = ArtifactScanParser.parse(
            plain(
                """
                Sands of Eon
                ATK
                7.0%
                +0
                · CRlT Rate +3.9%
                · Energy Recharge+5.8%
                · DEF+19
                · CRIT DMG+7.8% (unactivated)
                Emblem of Severed Fate:2
                """,
            ),
            data,
        )
        assertEquals("emblemofseveredfate", scan.setId)
        assertEquals(ArtifactSlot.SANDS, scan.slot)
        assertEquals(Stat.ATK_PCT, scan.mainStat)
        assertEquals(0, scan.level)
        assertEquals(5, scan.rarity)
        assertEquals(setOf(Stat.CRIT_RATE, Stat.ER, Stat.DEF), scan.substats.keys)
    }

    @Test
    fun fourStarPlumeInfersRarity() {
        val scan = ArtifactScanParser.parse(
            plain(
                """
                Gambler's Feather Accessory
                Plume of Death
                ATK
                232
                +16
                • HP+4.1%
                """,
            ),
            data,
        )
        assertEquals("gambler", scan.setId)
        assertEquals(ArtifactSlot.PLUME, scan.slot)
        assertEquals(Stat.ATK, scan.mainStat)
        assertEquals(4, scan.rarity)
        assertEquals(16, scan.level)
    }

    @Test
    fun levelInferredFromMainValueAndSlotFromMainStat() {
        val scan = ArtifactScanParser.parse(plain("CRIT Rate\n31.1%\n• ATK+19"), data)
        assertEquals(ArtifactSlot.CIRCLET, scan.slot)
        assertEquals(Stat.CRIT_RATE, scan.mainStat)
        assertEquals(20, scan.level)
        assertEquals(5, scan.rarity)
        assertNull(scan.setId)
    }

    @Test
    fun unrelatedImageIsNotRecognized() {
        val scan = ArtifactScanParser.parse(plain("Paimon\nEmergency food\nLv. 90"), data)
        assertTrue(!scan.recognized)
        assertNull(scan.toPiece("x"))
    }

    @Test
    fun piecesModeStatsAndSets() {
        fun piece(slot: ArtifactSlot, main: Stat, subs: Map<Stat, Double>, set: String = "gladiatorsfinale") =
            ArtifactPiece(id = slot.key, setId = set, slot = slot, mainStat = main, substats = subs)
        val pieces = listOf(
            piece(ArtifactSlot.FLOWER, Stat.HP, mapOf(Stat.CRIT_RATE to 0.1)),
            piece(ArtifactSlot.PLUME, Stat.ATK, mapOf(Stat.CRIT_DMG to 0.2)),
            piece(ArtifactSlot.SANDS, Stat.ATK_PCT, mapOf(Stat.EM to 40.0)),
            piece(ArtifactSlot.GOBLET, Stat.ATK_PCT, mapOf(Stat.ATK to 30.0), set = "crimsonwitchofflames"),
            piece(ArtifactSlot.CIRCLET, Stat.CRIT_DMG, mapOf(Stat.CRIT_RATE to 0.05)),
        ).associateBy { it.slot }
        val build = MemberBuild(
            characterId = "keqing",
            weapon = WeaponBuild("favoniussword", 90),
            artifacts = ArtifactBuild(mode = ArtifactMode.PIECES, pieces = pieces),
        )
        assertEquals(mapOf("gladiatorsfinale" to 4, "crimsonwitchofflames" to 1), build.artifacts.pieceCounts())

        val m = TeamCalculator(data, GameEffects).calculate(Team(listOf(build))).members[0]
        val s = m.screenStats
        // Keqing: CRIT DMG ascension 38.4%; Gladiator 2pc ATK +18%.
        assertEquals(0.05 + 0.10 + 0.05, s.critRate, 1e-9)
        assertEquals(0.5 + 0.384 + 0.20 + 0.622, s.critDmg, 1e-9)
        assertEquals(40.0, s.em, 1e-9)
        val baseAtk = s[Stat.BASE_ATK]
        assertEquals(baseAtk * (1 + 0.466 + 0.466 + 0.18) + 311 + 30, s.atk, 1e-6)
        assertTrue(m.ownEffects.any { it.effect.id == "set.gladiatorsfinale.2" && it.unlocked })
    }
}
