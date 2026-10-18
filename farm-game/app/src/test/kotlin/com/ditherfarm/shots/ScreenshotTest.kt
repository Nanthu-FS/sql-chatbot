package com.ditherfarm.shots

import android.graphics.Bitmap
import android.graphics.Canvas
import com.ditherfarm.Game
import com.ditherfarm.Host
import com.ditherfarm.engine.AssetSource
import com.ditherfarm.engine.FileAssets
import com.ditherfarm.game.GameTime
import com.ditherfarm.game.Season
import com.ditherfarm.game.Weather
import com.ditherfarm.game.build
import com.ditherfarm.game.chop
import com.ditherfarm.game.enqueue
import com.ditherfarm.game.forestSpots
import com.ditherfarm.ui.Nav
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files

/**
 * Renders real game frames on the JVM (Robolectric native graphics). Doubles as a
 * smoke test in CI; set -Dshots.out=<dir> to also write 3x PNGs for review.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {
    private class TestHost : Host {
        override val assetSource: AssetSource = FileAssets(assetRoot())
        override val saveDir: File = Files.createTempDirectory("df-save").toFile()
        override val context: android.content.Context? = null
        override val density = 3f
    }

    /** Builds a game, lets [setup] arrange it, runs frames for [seconds] and saves a PNG. */
    private fun render(name: String, seconds: Float = 0.6f, setup: (Game) -> Unit = {}, drive: (Game, Float) -> Unit = { _, _ -> }): Game {
        val game = Game(TestHost())
        val w = 270
        val h = 585
        game.engine.resize(w, h)
        game.engine.screen?.enter()
        setup(game)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val dt = 1f / 30f
        var t = 0f
        var now = 1_000L
        while (t < seconds) {
            drive(game, t)
            canvas.drawColor(0xFF181425.toInt())
            game.engine.frame(dt, canvas, now)
            t += dt
            now += (dt * 1000).toLong()
        }
        System.getProperty("shots.out")?.let { out ->
            File(out).mkdirs()
            val big = Bitmap.createScaledBitmap(bmp, w * 3, h * 3, false)
            File(out, "$name.png").outputStream().use { big.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return game
    }

    @Test fun farmStart() { render("farm_start") }

    @Test fun farmHarvestAndPlant() {
        val game = render("farm_harvest", seconds = 0.4f, setup = { g ->
            val (c, r) = g.farm.plotTile(0)
            g.farm.onTileTap(c, r)
            val (c2, r2) = g.farm.plotTile(2)
            g.farm.onTileTap(c2, r2) // opens the seed tray for this plot
        })
        assertTrue(game.world.count("wheat", anyQuality = true) >= 3)
    }

    @Test fun farmSeedTray() { render("farm_tray", setup = { it.farm.showPanel("tray") }) }
    @Test fun farmBarn() { render("panel_barn", setup = { it.world.harvest(0); it.world.harvest(1); it.farm.showPanel("barn") }) }
    @Test fun farmMarket() { render("panel_market", setup = { it.world.harvest(0); it.farm.showPanel("market") }) }
    @Test fun farmOrders() { render("panel_orders", setup = { it.world.harvest(0); it.world.harvest(1); it.farm.showPanel("orders") }) }
    @Test fun farmSettings() { render("panel_settings", setup = { it.farm.showPanel("settings") }) }

    @Test fun farmRainyNight() {
        render("farm_night_rain", seconds = 1f, setup = { g ->
            g.world.state.minutes = GameTime.of(1, Season.SPRING, 2, 22.5).minutes
            g.world.state.lastDayRolled = g.world.time.dayIndex
            g.world.state.weather = Weather.RAIN
        })
    }

    @Test fun farmGrown() {
        render("farm_grown", seconds = 1f, setup = { g ->
            val w = g.world
            val s = w.state
            s.level = 22; s.xp = 61_000; s.coins = 48_250
            s.minutes = GameTime.of(1, Season.SUMMER, 6, 10.0).minutes
            s.lastDayRolled = w.time.dayIndex
            val crops = listOf("wheat", "corn", "tomato", "sunflower", "eggplant", "melon", "grapes", "potato")
            for (i in 0 until 40) {
                val p = s.plots[i]
                p.owned = true
                val id = crops[(i * 7) % crops.size]
                if (!w.data.crop(id).seasons.contains(Season.SUMMER)) continue
                p.crop = id
                p.growth = w.data.crop(id).growMinutes * listOf(0.1, 0.4, 0.8, 1.0, 1.0)[i % 5]
                p.watered = i % 3 != 0
            }
        })
    }

    @Test fun forest() {
        render("forest", seconds = 1f, setup = { g ->
            g.world.gainXp(130) // level 3: zone A open
            g.world.state.tools["axe"] = 1
            g.goTo(Nav.FOREST)
            g.engine.screen?.enter()
            val spots = g.world.forestSpots.filter { it.zone == "A" && it.native == "pine" }
            g.world.chop(spots[0].index); g.world.chop(spots[0].index)
            repeat(5) { g.world.chop(spots[1].index) }
        })
    }

    @Test fun workbenchPanel() { render("panel_workbench", setup = { g -> g.world.enqueue("wb_plank"); g.farm.showPanel("workbench") }) }

    @Test fun buildPanel() { render("panel_build", setup = { g -> g.world.gainXp(40); g.farm.showPanel("build:windmill") }) }

    @Test fun machinePanel() {
        render("panel_machine", setup = { g ->
            val w = g.world
            w.gainXp(130); w.add("wheat", 40); w.add("wood", 30); w.build("windmill")
            w.enqueue("flour"); w.enqueue("flour")
            g.farm.showPanel("machine:windmill")
        })
    }

    companion object {
        /** app/src/main/assets, whether Gradle runs from the module dir or elsewhere. */
        fun assetRoot(): File = listOfNotNull(
            System.getProperty("assets.dir")?.let(::File),
            File("src/main/assets"),
            File("app/src/main/assets"),
        ).first { File(it, "art").exists() }
    }
}
