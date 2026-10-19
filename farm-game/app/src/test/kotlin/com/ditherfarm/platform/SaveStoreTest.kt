package com.ditherfarm.platform

import com.ditherfarm.game.GameState
import com.ditherfarm.game.Time
import com.ditherfarm.game.World
import com.ditherfarm.game.WorldTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** What a write leaves on disk, what a load picks from it, and that a whole farm survives a save and load. */
class SaveStoreTest {
    @get:Rule val folder = TemporaryFolder()

    /** A save text that tells saves apart by [marker]; [version] is what the store reads. */
    private fun save(marker: Int, version: Int = GameState.VERSION) =
        JSONObject().put("version", version).put("marker", marker).toString()

    private fun markerOf(text: String?): Int? = text?.let { JSONObject(it).getInt("marker") }

    /** What a load does: the first candidate that parses. */
    private fun loaded(dir: File): String? =
        SaveStore(dir).candidates().firstOrNull { runCatching { JSONObject(it) }.isSuccess }

    @Test fun writeKeepsPreviousSaveAsBackupAndLeavesNoTempFiles() {
        val d = folder.newFolder()
        val s = SaveStore(d)
        s.write(save(1))
        s.write(save(2))
        assertEquals(save(2), File(d, "save.json").readText())
        assertEquals(save(1), File(d, "save.bak.json").readText())
        assertEquals(setOf("save.json", "save.bak.json"), d.list()!!.toSet())
        assertEquals(listOf(save(2), save(1)), SaveStore(d).candidates())
    }

    @Test fun corruptMainFallsBackToBackup() {
        val damages = mapOf<String, (String) -> String>(
            "half written" to { it.substring(0, it.length / 2) },
            "not json" to { "\u0000garbage\u0000" },
            "zero filled" to { "\u0000".repeat(it.length) },
        )
        for ((name, damage) in damages) {
            val d = folder.newFolder(name)
            val s = SaveStore(d)
            s.write(save(1))
            s.write(save(2))
            File(d, "save.json").writeText(damage(save(2)))
            assertEquals("$name main loads the backup", 1, markerOf(loaded(d)))
        }
    }

    @Test fun damagedMainNeverReplacesTheBackup() {
        val d = folder.newFolder()
        val s = SaveStore(d)
        s.write(save(1))
        s.write(save(2)) // backup: 1, main: 2
        val damaged = save(2).substring(0, 10)
        File(d, "save.json").writeText(damaged)
        s.write(save(3))
        assertEquals(save(1), File(d, "save.bak.json").readText())
        assertEquals(damaged, File(d, "save.corrupt.json").readText())
        assertEquals(save(3), File(d, "save.json").readText())
    }

    @Test fun emptyMainIsIgnored() {
        val d = folder.newFolder()
        val s = SaveStore(d)
        s.write(save(1))
        s.write(save(2))
        File(d, "save.json").writeText("")
        assertEquals(listOf(save(1)), SaveStore(d).candidates())
        assertEquals(1, markerOf(loaded(d)))
        // Nothing worth keeping in an empty file, and the backup is still the good save.
        s.write(save(3))
        assertFalse(File(d, "save.corrupt.json").exists())
        assertEquals(save(1), File(d, "save.bak.json").readText())
    }

    @Test fun halfWrittenTempFileFromACrashIsSkipped() {
        val d = folder.newFolder()
        val s = SaveStore(d)
        s.write(save(1))
        File(d, "save.json.tmp").writeText(save(2).substring(0, 12)) // crashed mid-write
        assertEquals(1, markerOf(loaded(d)))
        s.write(save(3))
        assertEquals(3, markerOf(loaded(d)))
        assertFalse(File(d, "save.json.tmp").exists())
        assertEquals(save(1), File(d, "save.bak.json").readText())
    }

    @Test fun wholeTempFileFromACrashIsTheNewestSave() {
        val d = folder.newFolder()
        SaveStore(d).write(save(1))
        // Crashed after the new save was written, before the rename: it is the newest save.
        File(d, "save.json.tmp").writeText(save(2))
        assertEquals(2, markerOf(loaded(d)))
        // Crashed during the rename: the main file is gone and the temp file is still whole.
        File(d, "save.json").delete()
        assertEquals(2, markerOf(loaded(d)))
        SaveStore(d).write(save(3))
        assertEquals(3, markerOf(loaded(d)))
        assertFalse(File(d, "save.json.tmp").exists())
    }

    @Test fun saveFromNewerVersionIsKeptBeforeBeingReplaced() {
        val d = folder.newFolder()
        val newerVersion = GameState.VERSION + 1
        val newer = save(7, version = newerVersion)
        SaveStore(d, newerVersion).write(newer)
        val mine = save(8)
        val s = SaveStore(d)
        s.write(mine)
        assertEquals(newer, File(d, "save.v$newerVersion.json").readText())
        assertEquals(mine, File(d, "save.json").readText())
        // The first copy is the one kept.
        s.write(save(9))
        assertEquals(newer, File(d, "save.v$newerVersion.json").readText())
        assertFalse(File(d, "save.v${GameState.VERSION}.json").exists())
    }

    @Test fun fullGameStateSurvivesSaveAndLoad() {
        val data = WorldTest.loadData()
        val d = folder.newFolder()
        val w = World.newGame(data, 42)
        assertTrue(w.plant(2, "carrot"))
        assertTrue(w.water(2))
        w.advance(Time.gameMinutesToRealSeconds(90.0))
        w.state.lastSavedMs = 1_700_000_000_000L
        val text = w.state.toJson().toString()
        SaveStore(d).write(text)

        val text2 = loaded(d)!!
        assertEquals(text, text2)
        val back = GameState.fromJson(JSONObject(text2))
        val s = w.state
        assertEquals("carrot", s.plots[2].crop)
        assertTrue(s.minutes > Time.START_MINUTE)
        assertEquals(s.seed, back.seed)
        assertEquals(s.minutes, back.minutes, 0.0)
        assertEquals(s.lastSavedMs, back.lastSavedMs)
        assertEquals(s.coins, back.coins)
        assertEquals(s.xp, back.xp)
        assertEquals(s.level, back.level)
        assertEquals(s.inventory, back.inventory)
        assertEquals(s.weather, back.weather)
        assertEquals(s.plots.size, back.plots.size)
        for (i in s.plots.indices) {
            assertEquals("plot $i crop", s.plots[i].crop, back.plots[i].crop)
            assertEquals("plot $i growth", s.plots[i].growth, back.plots[i].growth, 0.0)
            assertEquals("plot $i watered", s.plots[i].watered, back.plots[i].watered)
            assertEquals("plot $i owned", s.plots[i].owned, back.plots[i].owned)
        }
        assertEquals(text, back.toJson().toString())
    }
}
