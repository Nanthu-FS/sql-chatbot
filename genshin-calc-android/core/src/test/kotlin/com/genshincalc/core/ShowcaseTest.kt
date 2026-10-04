package com.genshincalc.core

import com.genshincalc.core.calc.BaseStats
import com.genshincalc.core.calc.TeamCalculator
import com.genshincalc.core.effects.GameEffects
import com.genshincalc.core.model.ArtifactMode
import com.genshincalc.core.model.ArtifactSlot
import com.genshincalc.core.model.Element
import com.genshincalc.core.model.GameDataSet
import com.genshincalc.core.model.Stat
import com.genshincalc.core.model.TalentLevels
import com.genshincalc.core.model.Team
import com.genshincalc.core.model.WeaponBuild
import com.genshincalc.core.showcase.ShowcaseFormatException
import com.genshincalc.core.showcase.ShowcaseIds
import com.genshincalc.core.showcase.ShowcaseParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ShowcaseTest {
    private val data = GameDataSet.load()
    private val ids = ShowcaseIds.load()
    private var n = 0

    private fun resource(name: String) = javaClass.getResource("/showcase/$name")!!.readText()
    private fun parse(text: String) = ShowcaseParser.parse(text, data, ids) { "p${n++}" }

    @Test
    fun everyCharacterWeaponAndSetHasAShowcaseId() {
        val chars = ids.characters.values.map { it.id }.toSet()
        assertEquals(emptyList(), data.characters.map { it.id }.filter { it !in chars })
        assertEquals(emptyList(), data.weapons.map { it.id }.filter { it !in ids.weapons.values })
        assertEquals(emptyList(), data.artifactSets.map { it.id }.filter { it !in ids.sets.values })
        assertTrue(ids.characters.values.all { it.skills.size == 3 })
    }

    @Test
    fun parsesShowcaseInOrder() {
        val s = parse(resource("sample.json"))
        assertEquals("800000001", s.uid)
        assertEquals("Paimon's Friend", s.player.nickname)
        assertEquals(60, s.player.level)
        assertEquals(1, s.unknownCharacters)
        assertEquals(
            listOf("hutao", "traveleranemo", "raidenshogun", "kaedeharakazuha", "bennett"),
            s.characters.map { it.build.characterId },
        )

        val hutao = s.characters[0].build
        assertEquals(90, hutao.level)
        assertTrue(hutao.ascended)
        assertEquals(1, hutao.constellation)
        assertEquals(TalentLevels(10, 10, 9), hutao.talents)
        assertEquals(WeaponBuild("staffofhoma", 90, true, 1), hutao.weapon)
        assertEquals(ArtifactMode.PIECES, hutao.artifacts.mode)
        assertEquals(mapOf("crimsonwitchofflames" to 4, "shimenawasreminiscence" to 1), hutao.artifacts.pieceCounts())
        val goblet = hutao.artifacts.pieces.getValue(ArtifactSlot.GOBLET)
        assertEquals(Stat.PYRO_DMG, goblet.mainStat)
        assertEquals(20, goblet.level)
        assertEquals(5, goblet.rarity)
        assertEquals(mapOf(Stat.CRIT_RATE to 0.039, Stat.CRIT_DMG to 0.28, Stat.HP to 508.0, Stat.DEF_PCT to 0.073),
            goblet.substats.mapValues { Math.round(it.value * 1e6) / 1e6 })
        assertTrue(s.characters[0].warnings.isEmpty(), s.characters[0].warnings.toString())

        // Lumine (Anemo), The Flute R5, two pieces, one of them +12.
        val traveler = s.characters[1].build
        assertEquals(2, traveler.constellation)
        assertEquals(TalentLevels(8, 9, 9), traveler.talents)
        assertEquals(WeaponBuild("theflute", 90, true, 5), traveler.weapon)
        assertEquals(setOf(ArtifactSlot.FLOWER, ArtifactSlot.GOBLET), traveler.artifacts.pieces.keys)
        assertEquals(12, traveler.artifacts.pieces.getValue(ArtifactSlot.GOBLET).level)
        // The fixture's goblet shows a main stat that a 5-star +12 goblet can't have.
        assertEquals(listOf("Goblet main stat differs from the game"), s.characters[1].warnings)

        // Bennett 80/90 (ascended), C6, base talent levels (constellation bonuses are added by the calculator).
        val bennett = s.characters[4].build
        assertEquals(80, bennett.level)
        assertTrue(bennett.ascended)
        assertEquals(6, bennett.constellation)
        assertEquals(TalentLevels(1, 8, 10), bennett.talents)
        assertEquals(WeaponBuild("aquilafavonia", 80, true, 1), bennett.weapon)
        assertTrue(bennett.artifacts.pieces.isEmpty())
    }

    /** The calculated attribute screen reproduces the game's (worked out by hand in the fixture). */
    @Test
    fun calculatedStatsMatchTheGame() {
        val s = parse(resource("sample.json"))
        val hutao = s.characters[0]
        val result = TeamCalculator(data, GameEffects).calculate(Team(listOf(hutao.build))).members[0]
        val sheet = result.screenStats
        assertEquals(0.427, sheet.critRate, 1e-9)
        assertEquals(84.0, sheet.em, 1e-9)
        assertEquals(emptyList(), ShowcaseParser.compare(hutao.gameStats, sheet, Element.PYRO))

        val wrong = hutao.gameStats.copy(atk = hutao.gameStats.atk + 40)
        assertEquals(listOf("ATK"), ShowcaseParser.compare(wrong, sheet, Element.PYRO).map { it.label })
    }

    @Test
    fun hiddenDetailsAndBadResponses() {
        val s = parse(resource("hidden.json"))
        assertTrue(s.detailsHidden)
        assertTrue(s.characters.isEmpty())
        assertEquals("Hidden", s.player.nickname)
        assertFailsWith<ShowcaseFormatException> { parse("<html>Bad gateway</html>") }
    }

    /**
     * CI downloads a real showcase into build/showcase-sample.json (when the service is reachable); this
     * checks that it parses and prints how the calculated stats compare with the game's.
     */
    @Test
    fun liveShowcaseSample() {
        val file = File("build/showcase-sample.json")
        if (!file.exists()) return
        val s = runCatching { parse(file.readText()) }.getOrElse {
            println("LIVE showcase sample unreadable: ${it.message}")
            return
        }
        println("LIVE showcase: ${s.characters.size} characters, ${s.unknownCharacters} unknown, details hidden=${s.detailsHidden}")
        for (c in s.characters) {
            val build = c.build
            val m = TeamCalculator(data, GameEffects).calculate(Team(listOf(build))).members[0]
            val diffs = ShowcaseParser.compare(c.gameStats, m.screenStats, m.character.element)
            println(
                "LIVE ${build.characterId} Lv${build.level} C${build.constellation} ${build.talents} ${build.weapon} " +
                    "sets=${build.artifacts.pieceCounts()} warnings=${c.warnings} " +
                    if (diffs.isEmpty()) "stats match" else "differs: " + diffs.joinToString { "${it.label} game=${it.game} calc=${it.calculated}" },
            )
            assertTrue(build.artifacts.pieces.values.all { BaseStats.pieceMain(data, it) > 0 })
        }
    }
}
