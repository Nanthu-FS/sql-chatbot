package com.ditherfarm.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import com.ditherfarm.Game
import com.ditherfarm.Host
import com.ditherfarm.engine.AssetSource
import com.ditherfarm.engine.FileAssets
import com.ditherfarm.game.setPlayer
import com.ditherfarm.shots.ScreenshotTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files

/**
 * While a story scene plays nothing under it can be tapped: not the nav bar (which used to take
 * the player to the Forest or Mine mid-scene), not the HUD. A new farm drops a scene that was
 * playing and starts its own opening once, on the farm.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoryInputTest {
    private class TestHost : Host {
        override val assetSource: AssetSource = FileAssets(ScreenshotTest.assetRoot())
        override val saveDir: File = Files.createTempDirectory("df-story").toFile()
        override val context: android.content.Context? = null
        override val density = 3f
    }

    private val w = 270
    private val h = 480
    private val canvas = Canvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888))
    private var now = 1_000L

    private fun newGame(): Game = Game(TestHost()).also {
        it.engine.resize(w, h)
        it.engine.screen?.enter()
        frames(it, 3)
    }

    private fun frames(g: Game, n: Int) = repeat(n) {
        g.engine.frame(1f / 30f, canvas, now)
        now += 33
    }

    /** A tap on the HUD layer at (x, y), the way Input routes it. */
    private fun tap(g: Game, x: Float, y: Float) {
        val s = g.engine.screen as IsoMapScreen
        if (s.hudDown(x, y)) {
            frames(g, 1)
            s.hudUp(x, y)
        }
        frames(g, 1)
    }

    private val navXs = listOf(20f, 58f, 96f, 135f, 174f, 212f, 250f)

    @Test fun navBarAndHudIgnoreTapsDuringTheOpening() {
        val g = newGame()
        assertEquals("the opening plays on a new farm", Chrome.Panel.STORY, g.farm.chrome.panel)
        // The look-and-name picker: the whole nav bar and the HUD's goals pill.
        for (x in navXs) tap(g, x, h - 15f)
        tap(g, 220f, g.engine.safeTop + 25f)
        assertSame(g.farm, g.engine.screen)
        assertEquals(Chrome.Panel.STORY, g.farm.chrome.panel)
        // The dialogue: a tap on the nav bar only moves the scene on.
        g.world.setPlayer(0, "Ada")
        g.farm.chrome.story.showLine(0)
        for (x in navXs) tap(g, x, h - 15f)
        assertSame(g.farm, g.engine.screen)
        assertEquals(Chrome.Panel.STORY, g.farm.chrome.panel)
    }

    @Test fun newFarmDropsTheOldSceneAndPlaysOneOpening() {
        val g = newGame()
        assertTrue(g.farm.chrome.story.playing)
        // Old saves could leave the farm mid-scene; a new farm started from the Forest...
        g.goTo(Nav.FOREST)
        frames(g, 2)
        g.newGame()
        frames(g, 3)
        // ...goes back to the farm, where only the new farm's opening plays.
        assertSame(g.farm, g.engine.screen)
        assertEquals(Chrome.Panel.STORY, g.farm.chrome.panel)
        assertTrue(g.farm.chrome.story.playing)
        assertEquals(Chrome.Panel.NONE, g.forest.chrome.panel)
        assertFalse(g.forest.chrome.story.playing)
    }
}
