package com.ditherfarm.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import com.ditherfarm.Game
import com.ditherfarm.Host
import com.ditherfarm.engine.AssetSource
import com.ditherfarm.engine.FileAssets
import com.ditherfarm.game.knowEverything
import com.ditherfarm.shots.ScreenshotTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files
import kotlin.random.Random

/**
 * Thousands of random taps (and a few back presses) over every screen, from a new farm through
 * its opening and on: nothing crashes, and after every tap the screens agree with each other.
 *  - A story scene only ever plays on the farm, and only while the farm is the screen.
 *  - A screen that is not showing has no panel open behind it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TapFuzzTest {
    private class TestHost : Host {
        override val assetSource: AssetSource = FileAssets(ScreenshotTest.assetRoot())
        override val saveDir: File = Files.createTempDirectory("df-fuzz").toFile()
        override val context: android.content.Context? = null
        override val density = 3f
    }

    private val w = 270
    private val h = 480
    private val canvas = Canvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888))
    private var now = 1_000L

    private fun frames(g: Game, n: Int) = repeat(n) {
        g.engine.frame(1f / 30f, canvas, now)
        now += 33
    }

    private fun tap(g: Game, x: Float, y: Float) {
        val s = g.engine.screen as IsoMapScreen
        if (s.hudDown(x, y)) {
            frames(g, 1)
            s.hudUp(x, y)
        } else {
            s.tap(x, y)
        }
        frames(g, 2)
    }

    /** The screens that exist so far, without creating the lazy ones. */
    private fun screens(g: Game): List<Pair<IsoMapScreen, Chrome>> {
        val out = mutableListOf<Pair<IsoMapScreen, Chrome>>(g.farm to g.farm.chrome)
        runCatching { g.javaClass.getDeclaredField("forestScreen").apply { isAccessible = true }.get(g) as Lazy<*> }
            .getOrNull()?.takeIf { it.isInitialized() }?.let { out += g.forest to g.forest.chrome }
        runCatching { g.javaClass.getDeclaredField("mineScreen").apply { isAccessible = true }.get(g) as Lazy<*> }
            .getOrNull()?.takeIf { it.isInitialized() }?.let { out += g.mine to g.mine.chrome }
        return out
    }

    private fun check(g: Game, step: Int, seed: Int) {
        val current = g.engine.screen
        for ((screen, chrome) in screens(g)) {
            val where = "seed $seed, step $step, ${screen.javaClass.simpleName}"
            if (screen !== current) assertFalse("$where: panel ${chrome.panel} left open behind", chrome.isOpen)
            if (chrome.story.playing) {
                assertTrue("$where: a scene plays off the farm", screen === g.farm)
                assertTrue("$where: a scene plays while the farm is not showing", current === g.farm)
            }
        }
    }

    private fun run(seed: Int, taps: Int, setup: (Game) -> Unit = {}) {
        val rnd = Random(seed)
        val g = Game(TestHost())
        g.engine.resize(w, h)
        g.engine.screen?.enter()
        setup(g)
        frames(g, 3)
        for (step in 0 until taps) {
            when (rnd.nextInt(40)) {
                0 -> (g.engine.screen as? FarmScreen)?.closePanel() ?: screens(g).firstOrNull { it.first === g.engine.screen }?.second?.close()
                // The nav bar and the top bar get extra taps: that is where the bug lived.
                in 1..10 -> tap(g, rnd.nextFloat() * w, h - 1f - rnd.nextFloat() * 44f)
                in 11..14 -> tap(g, rnd.nextFloat() * w, g.engine.safeTop + rnd.nextFloat() * 50f)
                else -> tap(g, rnd.nextFloat() * w, rnd.nextFloat() * h)
            }
            check(g, step, seed)
        }
    }

    @Test fun newFarmThroughTheOpening() {
        for (seed in 1..3) run(seed, 700)
    }

    @Test fun grownFarmWithEverythingUnlocked() {
        for (seed in 11..13) run(seed, 700) { g ->
            g.world.knowEverything()
            g.world.gainXp(g.world.data.level(20).totalXp - g.world.state.xp)
            g.world.state.coins += 50_000
            g.world.state.energy = 500.0
            g.world.events.clear()
        }
    }
}
