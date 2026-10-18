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
import com.ditherfarm.game.addCartRule
import com.ditherfarm.game.build
import com.ditherfarm.game.buyBlock
import com.ditherfarm.game.chop
import com.ditherfarm.game.enqueue
import com.ditherfarm.game.enterFloor
import com.ditherfarm.game.forestSpots
import com.ditherfarm.game.treeStage
import com.ditherfarm.game.zoneOpen
import com.ditherfarm.game.mine
import com.ditherfarm.game.mineFloor
import com.ditherfarm.game.mineQuarry
import com.ditherfarm.game.placeFixture
import com.ditherfarm.game.placeTorch
import com.ditherfarm.game.repairMine
import com.ditherfarm.ui.Chrome
import com.ditherfarm.ui.FarmScreen
import com.ditherfarm.ui.MineScreen
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
    private fun render(
        name: String, seconds: Float = 0.6f, setup: (Game) -> Unit = {}, drive: (Game, Float) -> Unit = { _, _ -> },
        size: Pair<Int, Int> = 270 to 585, zoom: Int = 0,
    ): Game {
        val game = Game(TestHost())
        val (w, h) = size
        game.engine.resize(w, h)
        game.engine.screen?.enter()
        setup(game)
        if (zoom > 0) (game.engine.screen as? com.ditherfarm.ui.IsoMapScreen)?.cam?.let { cam ->
            cam.minZoom = zoom
            cam.zoomAt(zoom - cam.zoom, w / 2f, h / 2f)
        }
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
            val k = if (w > 600) 1 else 3
            val big = Bitmap.createScaledBitmap(bmp, w * k, h * k, false)
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
            g.world.events.clear()
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

    private fun toLevel(g: Game, l: Int) { g.world.gainXp(g.world.data.level(l).totalXp - g.world.state.xp); g.world.state.energy = 500.0; g.world.events.clear() }

    @Test fun mineSurface() {
        render("mine_surface", seconds = 1f, setup = { g ->
            toLevel(g, 8)
            g.world.state.coins += 2000; g.world.add("wood", 30); g.world.add("stone", 40); g.world.add("copper_bar", 4)
            g.world.repairMine(); g.world.build("geode_anvil")
            g.goTo(Nav.MINE)
            g.engine.screen?.enter()
            g.world.advance(1.0)
            g.world.mineQuarry(0); g.world.mineQuarry(2)
        })
    }

    @Test fun mineFloor() {
        render("mine_floor", seconds = 1f, setup = { g ->
            toLevel(g, 6)
            g.world.state.coins += 200; g.world.add("wood", 30)
            g.world.repairMine(); g.world.enterFloor(3)
            g.goTo(Nav.MINE)
            g.engine.screen?.enter()
            val m = g.world.mine
            val rocks = m.cells.indices.filter { m.cells[it].node != null && m.cells[it].node != "glow_mushroom" }
            g.world.mineFloor(rocks[0]); g.world.mineFloor(rocks[1])
        })
    }

    @Test fun mineDark() {
        render("mine_dark", seconds = 1f, setup = { g ->
            toLevel(g, 14)
            g.world.state.coins += 200; g.world.add("wood", 30); g.world.add("torch", 3)
            g.world.state.tools["pickaxe"] = 2
            g.world.mine.gatesOpened += listOf(10, 20)
            g.world.repairMine(); g.world.enterFloor(23)
            g.goTo(Nav.MINE)
            g.engine.screen?.enter()
            val m = g.world.mine
            val n = m.grid
            m.cells.indices.firstOrNull { m.cells[it].node == null && it % n in 3..5 && it / n in 3..5 }?.let { g.world.placeTorch(it) }
        })
    }

    @Test fun geodePanel() {
        render("panel_geodes", setup = { g ->
            toLevel(g, 7)
            g.world.state.coins += 500; g.world.add("stone", 30); g.world.add("copper_bar", 2); g.world.add("geode", 4); g.world.add("ember_geode", 1)
            g.world.build("geode_anvil")
            g.goTo(Nav.MINE)
            g.engine.screen?.enter()
            (g.engine.screen as MineScreen).chrome.open(Chrome.Panel.GEODES)
        })
    }

    @Test fun elevatorPanel() {
        render("panel_elevator", setup = { g ->
            toLevel(g, 8)
            g.world.state.coins += 200; g.world.add("wood", 30)
            g.world.repairMine(); g.world.enterFloor(5); g.world.enterFloor(7)
            g.goTo(Nav.MINE)
            g.engine.screen?.enter()
            (g.engine.screen as MineScreen).chrome.open(Chrome.Panel.ELEVATOR)
        })
    }

    @Test fun farmAutomation() {
        render("farm_automation", seconds = 1f, setup = { g ->
            toLevel(g, 18)
            val w = g.world
            w.state.coins += 100_000
            w.add("plank", 200); w.add("stone", 100); w.add("iron_bar", 60); w.add("glass", 30); w.add("copper_bar", 10); w.add("cooking_oil", 10); w.add("brick", 10)
            w.build("greenhouse"); w.build("harvest_shed"); w.build("planter_shed")
            for (i in 0 until 20) w.plot(i).owned = true
            val gh = com.ditherfarm.game.FieldLayout.FIELD
            listOf("pumpkin", "tomato", "strawberry", "corn", "melon", "carrot").forEachIndexed { k, c ->
                if (w.data.crops.containsKey(c)) w.plot(gh + k).apply { clear(); crop = c; growth = w.data.crop(c).growMinutes * (0.3 + k * 0.15) }
            }
            w.add("copper_sprinkler", 2); w.add("scarecrow", 1); w.add("lightning_rod", 1)
            w.placeFixture(com.ditherfarm.game.FieldLayout.vertex(0, 1, 1), "copper_sprinkler")
            w.placeFixture(com.ditherfarm.game.FieldLayout.vertex(0, 3, 3), "copper_sprinkler")
            w.placeFixture(com.ditherfarm.game.FieldLayout.vertex(0, 2, 0), "scarecrow")
            w.placeFixture(com.ditherfarm.game.FieldLayout.vertex(0, 4, 2), "lightning_rod")
            w.events.clear()
            g.farm.centerOnTile(com.ditherfarm.ui.FarmMap.GREENHOUSE.first + 2, com.ditherfarm.ui.FarmMap.GREENHOUSE.second + 1)
        })
    }

    @Test fun farmPlacing() {
        render("farm_placing", seconds = 0.6f, setup = { g ->
            toLevel(g, 6)
            g.world.add("copper_sprinkler", 2)
            g.farm.startPlacing("copper_sprinkler")
            val (bc, br) = com.ditherfarm.ui.FarmMap.BLOCKS[0]
            val x = com.ditherfarm.engine.Iso.x(bc + 2, br + 2).toFloat()
            val y = com.ditherfarm.engine.Iso.y(bc + 2, br + 2).toFloat()
            g.farm.onWorldTap(x, y)
        })
    }

    @Test fun shedPanel() {
        render("panel_shed", setup = { g ->
            toLevel(g, 18)
            val w = g.world
            w.state.coins += 10_000
            w.add("plank", 50); w.add("iron_bar", 30); w.add("brick", 10); w.add("cooking_oil", 5); w.add("seed_carrot", 12)
            w.build("planter_shed")
            w.state.planterMode = com.ditherfarm.game.PlanterMode.FILL; w.state.planterSeed = "carrot"
            w.events.clear()
            g.farm.chrome.open(Chrome.Panel.SHED, "planter_shed")
        })
    }

    @Test fun goalsPanel() {
        render("panel_goals", setup = { g ->
            g.world.harvest(0); g.world.harvest(1)
            g.world.advance(1.0); g.world.events.clear()
            g.farm.showPanel("goals")
        })
    }

    @Test fun bookPanel() {
        render("panel_book", setup = { g ->
            listOf("wheat", "carrot", "potato", "tomato").forEach { g.world.add(it, 1) }
            g.world.events.clear()
            g.farm.showPanel("goals")
            g.farm.chrome.panels.goalsTab = 1
        })
    }

    @Test fun eventsPanel() {
        render("panel_events", setup = { g ->
            toLevel(g, 10)
            g.world.advance(com.ditherfarm.game.Time.gameMinutesToRealSeconds(7 * 1440.0 + 60))
            g.world.events.clear()
            g.farm.showPanel("goals")
            g.farm.chrome.panels.goalsTab = 2
        })
    }

    @Test fun giftPanel() { render("panel_gift", setup = { g -> g.world.state.giftDay = g.today() - 1; g.world.state.giftStreak = 2 }) }

    @Test fun merchantPanel() {
        render("panel_merchant", setup = { g ->
            toLevel(g, 10)
            val day = 6
            g.world.advance(com.ditherfarm.game.Time.gameMinutesToRealSeconds(day * 1440.0 + 60))
            g.world.events.clear()
            g.farm.showPanel("merchant")
        })
    }

    @Test fun cartPanel() {
        render("panel_cart", setup = { g ->
            toLevel(g, 13)
            val w = g.world
            w.state.coins += 5000; w.add("plank", 20); w.add("iron_bar", 6); w.add("copper_bar", 4)
            w.build("market_cart")
            w.add("carrot", 30); w.add("wood", 40)
            w.addCartRule("carrot"); w.addCartRule("wood"); w.state.cartRules[1].cond = 3
            w.events.clear()
            g.farm.showPanel("cart")
        })
    }

    @Test fun mineCaveIn() {
        render("mine_cavein", seconds = 0.5f, setup = { g ->
            toLevel(g, 14)
            val w = g.world
            w.state.coins += 200; w.add("wood", 30); w.add("rope_ladder", 2)
            w.state.tools["pickaxe"] = 2
            w.mine.gatesOpened += 10
            w.repairMine()
            var tries = 0
            while (tries++ < 200 && !(w.enterFloor(12) && w.mine.markers.isNotEmpty() && !w.mine.dark)) {}
            g.goTo(Nav.MINE)
            g.engine.screen?.enter()
            val m = w.mine
            m.rumble = m.markers.first(); m.rumbleEnd = w.state.minutes + 1000; m.braceTaps = 1
            w.events.clear()
        })
    }

    @Test fun forestHive() {
        render("forest_hive", seconds = 1f, setup = { g ->
            toLevel(g, 6)
            g.goTo(Nav.FOREST)
            g.engine.screen?.enter()
            val w = g.world
            val host = w.forestSpots.filter { it.kind == com.ditherfarm.game.SpotKind.TREE && w.zoneOpen(it.zone) && w.treeStage(it.index) == com.ditherfarm.game.TreeStage.MATURE }
                .minBy { kotlin.math.abs(it.c - 17) + kotlin.math.abs(it.r - 17) }
            w.state.forest.hive = host.index
            (g.engine.screen as com.ditherfarm.ui.ForestScreen).centerOnTile(host.c, host.r)
            w.events.clear()
        })
    }

    @Test fun farmOverview() {
        render("farm_overview", seconds = 0.3f, setup = { g ->
            g.world.gainXp(g.world.data.level(12).totalXp); g.world.events.clear()
            g.world.state.coins += 100_000
            for (b in 1..4) g.world.buyBlock(b)
            g.world.events.clear()
        }, size = 1500 to 820, zoom = 1)
    }
}
