package com.ditherfarm.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import com.ditherfarm.Game
import com.ditherfarm.Host
import com.ditherfarm.engine.AssetSource
import com.ditherfarm.engine.FileAssets
import com.ditherfarm.engine.Iso
import com.ditherfarm.game.FieldLayout
import com.ditherfarm.game.SCENES
import com.ditherfarm.game.SpotKind
import com.ditherfarm.game.chopBlocker
import com.ditherfarm.game.TreeStage
import com.ditherfarm.game.forestSpots
import com.ditherfarm.game.spot
import com.ditherfarm.game.treeStage
import com.ditherfarm.game.zoneOpen
import com.ditherfarm.game.quarryOpen
import com.ditherfarm.game.befriend
import com.ditherfarm.game.setPlayer
import com.ditherfarm.game.isDating
import com.ditherfarm.shots.ScreenshotTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files

/**
 * Real touches (MotionEvents through the GestureRouter) on the farm and in the forest, from the
 * playtest's findings: a slide harvests a row of ripe crops, and a tap on the tall part of a crop
 * or a tree reaches it rather than the ground tile behind it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TouchPlayTest {
    private class TestHost : Host {
        override val assetSource: AssetSource = FileAssets(ScreenshotTest.assetRoot())
        override val saveDir: File = Files.createTempDirectory("df-touch").toFile()
        override val context: android.content.Context? = null
        override val density = 3f
    }

    private val canvas = Canvas(Bitmap.createBitmap(270, 480, Bitmap.Config.ARGB_8888))

    /** A farm past its opening, with nothing open. */
    private fun start(): Pair<Game, TestTouch> {
        val g = Game(TestHost())
        g.engine.resize(270, 480)
        g.world.state.storySeen.addAll(SCENES.map { it.id } + com.ditherfarm.game.ACT2_SCENES.keys)
        g.world.state.tips.addAll(listOf("harvest", "plant", "water"))
        g.engine.screen?.enter()
        val t = TestTouch(g, canvas)
        t.frames(3)
        return g to t
    }

    /** Field A's plots on one iso row (same r), left to right in c. */
    private fun rowOfFieldA(g: Game): List<Int> {
        val plots = FieldLayout.plotsOf(0).toList()
        val byRow = plots.groupBy { g.farm.plotTile(it).second }
        return byRow.values.first { it.size >= 4 }.sortedBy { g.farm.plotTile(it).first }
    }

    private fun ripen(g: Game, i: Int) {
        val p = g.world.plot(i)
        p.owned = true; p.crop = "wheat"; p.growth = g.world.data.crop("wheat").growMinutes * 1.0; p.watered = true
    }

    private fun bufX(s: IsoMapScreen, wx: Float) = s.toBufferX(wx)
    private fun bufY(s: IsoMapScreen, wy: Float) = s.toBufferY(wy)

    @Test fun slideHarvestsARowOfRipeCrops() {
        val (g, t) = start()
        val row = rowOfFieldA(g).take(4)
        FieldLayout.plotsOf(0).forEach { g.world.plot(it).apply { clear() } }
        row.forEach { ripen(g, it) }
        val (c0, r0) = g.farm.plotTile(row.first())
        val (c3, r3) = g.farm.plotTile(row.last())
        g.farm.centerOnTile((c0 + c3) / 2, r0)
        t.frames(3)
        val f = g.farm
        t.swipe(bufX(f, Iso.x(c0, r0).toFloat()), bufY(f, Iso.centerY(c0, r0).toFloat()), bufX(f, Iso.x(c3, r3).toFloat()), bufY(f, Iso.centerY(c3, r3).toFloat()))
        assertEquals("all four ripe crops on the slide are harvested", 0, row.count { g.world.isReady(g.world.plot(it)) })
    }

    @Test fun orchardSignAndMeadowOpenTheOrchard() {
        val (g, t) = start()
        val f = g.farm
        g.world.gainXp(g.world.data.level(13).totalXp - g.world.state.xp)
        g.world.state.pet.offered = true
        g.world.events.clear()
        t.frames(3)
        repeat(5) { if (f.chrome.isOpen) { f.chrome.close(); t.frames(2) } }
        for (tile in listOf(FarmMap.ORCHARD_SIGN, 19 to 33, 18 to 30)) {
            f.centerOnTile(tile.first, tile.second)
            t.frames(3)
            t.tap(bufX(f, Iso.x(tile.first, tile.second).toFloat()), bufY(f, Iso.centerY(tile.first, tile.second).toFloat()))
            t.frames(3)
            assertEquals("tap on $tile", Chrome.Panel.ORCHARD, f.chrome.panel)
            f.chrome.close(); t.frames(2)
        }
    }

    @Test fun lockedMineButtonStaysOnTheFarm() {
        val (g, t) = start()
        assertFalse("quarry still closed on a new farm", g.world.quarryOpen)
        val n = Nav.entries.size
        val gap = (270 - 8 - n * 34) / (n - 1)
        val x = 4 + Nav.MINE.ordinal * (34 + gap) + 17f
        val y = 480f - g.engine.safeBottom - 46 + 6 + 17                  // Hud.bottomHeight 46
        t.tap(x, y)
        t.frames(3)
        assertTrue("the Mine stays shut until the quarry opens, on ${g.engine.screen?.javaClass?.simpleName}", g.engine.screen === g.farm)
    }

    @Test fun tapOnTheTopOfACropHarvestsIt() {
        val (g, t) = start()
        val f = g.farm
        FieldLayout.plotsOf(0).forEach { g.world.plot(it).apply { clear() } }
        // A ripe crop in the second row of Field A, with an empty plot right behind it.
        val i = rowOfFieldA(g)[1]
        ripen(g, i)
        val (c, r) = f.plotTile(i)
        f.centerOnTile(c, r)
        t.frames(3)
        // The topmost solid pixel of the crop's art, two pixels down.
        val s = g.assets["crops/wheat_${g.world.stage(g.world.plot(i))}"]
        val col = s.ax
        val top = (0 until s.h).first { (s.bmp.getPixel(col, it) ushr 24) >= 200 }
        val wx = Iso.x(c, r).toFloat()
        val wy = Iso.centerY(c, r).toFloat() - s.ay + top + 2
        val tile = IntArray(2).also { Iso.pick(wx, wy, it) }
        assertNotEquals("the tap point lies over another tile", c to r, tile[0] to tile[1])
        t.tap(bufX(f, wx), bufY(f, wy))
        assertFalse("the crop under the finger is harvested", g.world.isReady(g.world.plot(i)))
        assertFalse("no seed tray for the plot behind", f.chrome.isOpen)
    }

    @Test fun tapOnATreeCrownChopsIt() {
        val (g, t) = start()
        val w = g.world
        // A tree the starting axe can fell.
        val sp = w.forestSpots.first { it.kind == SpotKind.TREE && w.zoneOpen(it.zone) && w.treeStage(it.index) == TreeStage.MATURE && w.chopBlocker(it.index) == null }
        g.goTo(Nav.FOREST)
        g.engine.screen?.enter()
        val fs = g.forest
        fs.centerOnTile(sp.c, sp.r)
        t.frames(3)
        val art = g.assets["trees/${w.spot(sp.index).tree}"]
        // A point in the crown: the middle column, a third of the way down the art.
        val wx = Iso.x(sp.c, sp.r).toFloat()
        val ly = (art.h / 3 until art.h).first { (art.bmp.getPixel(art.ax, it) ushr 24) >= 200 }
        val wy = Iso.centerY(sp.c, sp.r).toFloat() - art.ay + ly
        val tile = IntArray(2).also { Iso.pick(wx, wy, it) }
        assertNotEquals("the crown lies over another tile", sp.c to sp.r, tile[0] to tile[1])
        assertTrue("that tile is not a tree", w.forestSpots.none { it.c == tile[0] && it.r == tile[1] && it.kind == SpotKind.TREE })
        // The ground tile under the finger holds no tree: without sprite picking the tap chopped nothing.
        val swings = w.state.stat("swings")
        t.tap(bufX(fs, wx), bufY(fs, wy))
        assertEquals("a tap on a crown swings at the frontmost tree there", swings + 1, w.state.stat("swings"))
    }

    /** Playtest round 7: the bouquet button on a villager's panel starts dating and plays the scene. */
    @Test fun bouquetButtonPlaysTheScene() {
        val (g, t) = start()
        val w = g.world
        w.setPlayer(0, "Sam")
        w.gainXp(w.data.level(16).totalXp - w.state.xp)
        w.befriend("ivy", 2_600)
        w.add(com.ditherfarm.game.BOUQUET, 2)
        w.state.minutes = w.time.dayIndex * 1440.0 + 600
        t.frames(3)
        g.farm.chrome.open(Chrome.Panel.VILLAGER, "ivy")
        t.frames(3)
        t.tap(135f, 203f)
        assertTrue("dating after the bouquet", w.isDating("ivy"))
        assertEquals(Chrome.Panel.STORY, g.farm.chrome.panel)
    }
}
