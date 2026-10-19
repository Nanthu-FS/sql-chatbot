package com.ditherfarm.perf

import android.graphics.Bitmap
import android.graphics.Canvas
import com.ditherfarm.Game
import com.ditherfarm.Host
import com.ditherfarm.engine.AssetSource
import com.ditherfarm.engine.FileAssets
import com.ditherfarm.game.Season
import com.ditherfarm.game.SCENES
import com.ditherfarm.game.build
import com.ditherfarm.game.buyBlock
import com.ditherfarm.game.chop
import com.ditherfarm.game.enterFloor
import com.ditherfarm.game.forestSpots
import com.ditherfarm.game.hire
import com.ditherfarm.game.knowEverything
import com.ditherfarm.game.mine
import com.ditherfarm.game.mineFloor
import com.ditherfarm.game.mineQuarry
import com.ditherfarm.game.repairMine
import com.ditherfarm.game.assign
import com.ditherfarm.game.FieldLayout
import com.ditherfarm.game.GameTime
import com.ditherfarm.ui.Nav
import com.ditherfarm.shots.ScreenshotTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files

/**
 * Frame-time benchmark: renders the farm, the forest and a mine floor (plus the surface) through
 * the real engine loop (simulation tick, screen update, draw) on the JVM with native graphics,
 * then prints the average milliseconds per frame. Only a generous ceiling is asserted, so a
 * slow CI machine does not fail it; compare the printed numbers across runs instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FrameTimeTest {
    private class TestHost : Host {
        override val assetSource: AssetSource = FileAssets(ScreenshotTest.assetRoot())
        override val saveDir: File = Files.createTempDirectory("df-bench").toFile()
        override val context: android.content.Context? = null
        override val density = 3f
    }

    /** Frames drawn before timing starts, so caches (ground paint, lighting masks) are warm. */
    private val warmup = 30
    /** Timed frames per round, and rounds (the reported figure is the mean of the round averages). */
    private val frames = 120
    private val rounds = 3
    /** Phone-sized buffer, as the shots use. */
    private val w = 270
    private val h = 585

    /** Generous: only a runaway regression should trip it. */
    private val ceilingMs = 400.0

    private fun seen(g: Game) = g.world.state.storySeen.addAll(SCENES.map { it.id })

    /** Builds the scene with [setup], then times [frames] frames per round. Returns the mean ms/frame. */
    private fun bench(name: String, setup: (Game) -> Unit): Double {
        val game = Game(TestHost())
        game.engine.resize(w, h)
        game.engine.screen?.enter()
        seen(game)
        setup(game)
        seen(game)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val dt = 1f / 30f
        var now = 1_000L
        fun run(n: Int) {
            repeat(n) {
                canvas.drawColor(0xFF181425.toInt())
                game.engine.frame(dt, canvas, now)
                now += (dt * 1000).toLong()
            }
        }
        run(warmup)
        val roundMs = DoubleArray(rounds) {
            val t0 = System.nanoTime()
            run(frames)
            (System.nanoTime() - t0) / 1e6 / frames
        }
        val mean = roundMs.average()
        println("FRAMEBENCH $name avg_ms_per_frame=%.3f rounds=%s frames=%d".format(mean, roundMs.joinToString(",") { "%.3f".format(it) }, frames))
        assertTrue("$name: ${"%.1f".format(mean)} ms/frame is above the ceiling of $ceilingMs", mean < ceilingMs)
        return mean
    }

    /** A mid-game farm: four fields bought, two farmhands hired, every owned plot planted. */
    @Test fun farm() {
        bench("farm") { g ->
            g.world.knowEverything()
            g.world.gainXp(g.world.data.level(14).totalXp - g.world.state.xp)
            g.world.state.energy = 500.0
            g.world.state.coins += 100_000
            for (b in 1..3) g.world.buyBlock(b)
            g.world.hire("rosa"); g.world.hire("milo")
            g.world.assign("rosa", 0); g.world.assign("milo", 1)
            val crops = listOf("wheat", "corn", "tomato", "sunflower", "eggplant", "melon", "grapes", "potato")
            val s = g.world.state
            s.minutes = GameTime.of(1, Season.SUMMER, 6, 10.0).minutes
            s.lastDayRolled = g.world.time.dayIndex
            for (i in 0 until FieldLayout.FIELD) {
                val p = s.plots[i]
                if (!p.owned) continue
                val id = crops[(i * 7) % crops.size]
                if (!g.world.data.crop(id).seasons.contains(Season.SUMMER)) continue
                p.crop = id
                p.growth = g.world.data.crop(id).growMinutes * listOf(0.1, 0.4, 0.8, 1.0, 1.0)[i % 5]
                p.watered = i % 3 != 0
            }
            g.world.events.clear()
            g.farm.enter()
        }
    }

    /** The forest with its first three clearings open and a few trees felled. */
    @Test fun forest() {
        bench("forest") { g ->
            g.world.knowEverything()
            g.world.gainXp(g.world.data.level(12).totalXp - g.world.state.xp)
            g.world.state.energy = 500.0
            listOf("B", "C").forEach { g.world.state.forest.zones += it }
            g.world.state.tools["axe"] = 3
            g.world.events.clear()
            g.goTo(Nav.FOREST)
            g.engine.screen?.enter()
            val spots = g.world.forestSpots.filter { it.zone == "A" && it.native == "pine" }
            repeat(3) { g.world.chop(spots[0].index) }
            g.world.events.clear()
        }
    }

    /** One rock floor underground, with its ladder hunt in progress. */
    @Test fun mineFloor() {
        bench("mine_floor") { g ->
            g.world.knowEverything()
            g.world.gainXp(g.world.data.level(6).totalXp - g.world.state.xp)
            g.world.state.energy = 500.0
            g.world.state.coins += 200; g.world.add("wood", 30)
            g.world.repairMine(); g.world.enterFloor(3)
            g.goTo(Nav.MINE)
            g.engine.screen?.enter()
            val m = g.world.mine
            val rocks = m.cells.indices.filter { m.cells[it].node != null && m.cells[it].node != "glow_mushroom" }
            g.world.mineFloor(rocks[0]); g.world.mineFloor(rocks[1])
            g.world.events.clear()
        }
    }

    /** The quarry and Mine Mouth on the surface, for comparison. */
    @Test fun mineSurface() {
        bench("mine_surface") { g ->
            g.world.knowEverything()
            g.world.gainXp(g.world.data.level(8).totalXp - g.world.state.xp)
            g.world.state.energy = 500.0
            g.world.state.coins += 2000; g.world.add("wood", 30); g.world.add("stone", 40); g.world.add("copper_bar", 4)
            g.world.repairMine(); g.world.build("geode_anvil")
            g.goTo(Nav.MINE)
            g.engine.screen?.enter()
            g.world.mineQuarry(0); g.world.mineQuarry(2)
            g.world.events.clear()
        }
    }
}
