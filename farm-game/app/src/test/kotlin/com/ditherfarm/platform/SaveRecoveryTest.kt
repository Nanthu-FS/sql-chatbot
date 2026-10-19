package com.ditherfarm.platform

import com.ditherfarm.Game
import com.ditherfarm.Host
import com.ditherfarm.engine.AssetSource
import com.ditherfarm.engine.FileAssets
import com.ditherfarm.game.GameData
import com.ditherfarm.game.World
import com.ditherfarm.game.WorldTest
import com.ditherfarm.shots.ScreenshotTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Start-up loading through [Game]: a damaged main save gives way to the backup, and nothing stops the game from starting. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SaveRecoveryTest {
    @get:Rule val folder = TemporaryFolder()
    private val now = 1_700_000_000_000L
    private val data: GameData by lazy { WorldTest.loadData() }

    private inner class TestHost(override val saveDir: File) : Host {
        override val assetSource: AssetSource = FileAssets(ScreenshotTest.assetRoot())
        override val context: android.content.Context? = null
        override val density = 3f
        override fun nowMs() = now
    }

    /** A farm with [coins] in it, as the game saves it; lastSavedMs is now, so no offline catch-up runs on load. */
    private fun farmText(coins: Int): String {
        val w = World.newGame(data, 42)
        w.state.coins = coins
        w.state.lastSavedMs = now
        return w.state.toJson().toString()
    }

    /** A backup with 1111 coins and a main file with 2222 coins, then [damage] applied to the main file. */
    private fun dirWithDamagedMain(damage: (File) -> Unit): File {
        val d = folder.newFolder()
        val s = SaveStore(d)
        s.write(farmText(1111))
        s.write(farmText(2222))
        damage(File(d, "save.json"))
        return d
    }

    @Test fun startsFromBackupWhenMainIsHalfWritten() {
        val d = dirWithDamagedMain { f -> f.writeText(f.readText().substring(0, f.length().toInt() / 2)) }
        assertEquals(1111, Game(TestHost(d)).world.state.coins)
    }

    @Test fun startsFromBackupWhenMainIsEmpty() {
        val d = dirWithDamagedMain { f -> f.writeText("") }
        assertEquals(1111, Game(TestHost(d)).world.state.coins)
    }

    @Test fun startsFromBackupWhenMainIsJsonButNotAFarm() {
        val d = dirWithDamagedMain { f -> f.writeText("""{"version":4,"coins":5}""") }
        assertEquals(1111, Game(TestHost(d)).world.state.coins)
    }

    @Test fun startsFromAFreshFarmWhenNoSaveIsUsable() {
        val d = folder.newFolder()
        File(d, "save.json").writeText("\u0000\u0000")
        File(d, "save.bak.json").writeText("{")
        val game = Game(TestHost(d))
        assertEquals(250, game.world.state.coins)
        assertTrue(game.world.state.plots.isNotEmpty())
    }

    @Test fun startsFromTheMainSaveWhenItIsIntact() {
        val d = dirWithDamagedMain { }
        assertEquals(2222, Game(TestHost(d)).world.state.coins)
    }
}
