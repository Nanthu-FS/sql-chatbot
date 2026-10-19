package com.ditherfarm.shots

import android.graphics.Bitmap
import android.graphics.Canvas
import com.ditherfarm.Game
import com.ditherfarm.Host
import com.ditherfarm.engine.AssetSource
import com.ditherfarm.engine.FileAssets
import com.ditherfarm.game.FieldLayout
import com.ditherfarm.game.GameTime
import com.ditherfarm.game.SCENES
import com.ditherfarm.game.Season
import com.ditherfarm.game.Weather
import com.ditherfarm.game.assign
import com.ditherfarm.game.buyBlock
import com.ditherfarm.game.claimChore
import com.ditherfarm.game.crate
import com.ditherfarm.game.hire
import com.ditherfarm.game.knowEverything
import com.ditherfarm.game.mine
import com.ditherfarm.game.levelUpWorker
import com.ditherfarm.game.rollDaily
import com.ditherfarm.game.repairMine
import com.ditherfarm.game.enterFloor
import com.ditherfarm.game.togglePin
import com.ditherfarm.ui.ForestScreen
import com.ditherfarm.ui.IsoMapScreen
import com.ditherfarm.ui.MineScreen
import com.ditherfarm.ui.Nav
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files

/**
 * Marketing scenes for the store listing: six 270x480 frames, saved 4x (1080x1920) to Docs/store/
 * when the system property shots.out is set (gradle test -Pshots --tests '*StoreShotsTest*').
 * Without it the scenes still render, so CI keeps them honest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreShotsTest {
    private class TestHost : Host {
        override val assetSource: AssetSource = FileAssets(ScreenshotTest.assetRoot())
        override val saveDir: File = Files.createTempDirectory("df-store").toFile()
        override val context: android.content.Context? = null
        override val density = 3f
    }

    /** Builds a game, lets [setup] arrange it, runs frames for [seconds] and saves the 4x PNG. */
    private fun shot(
        name: String,
        seconds: Float = 0.6f,
        story: Boolean = false,
        zoom: Int = 0,
        setup: (Game) -> Unit,
        drive: (Game, Float) -> Unit = { _, _ -> },
    ): Game {
        val game = Game(TestHost())
        val w = 270
        val h = 480
        // The scenes show the game, not its tutorial hints (they are marked as seen up front).
        game.world.state.tips.addAll(TUTORIAL_TIPS)
        game.engine.resize(w, h)
        game.engine.screen?.enter()
        // Scenes are of the game, not the story, unless asked for.
        val seen = SCENES.map { it.id }
        if (!story) game.world.state.storySeen.addAll(seen)
        setup(game)
        if (!story) game.world.state.storySeen.addAll(seen)
        // Today's chores and this week's contract are already rolled, so no "new" toast pops up.
        game.world.rollDaily(game.today())
        game.world.events.clear()
        if (zoom > 0) (game.engine.screen as? IsoMapScreen)?.cam?.let { cam ->
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
        System.getProperty("shots.out")?.let {
            val big = Bitmap.createScaledBitmap(bmp, w * 4, h * 4, false)
            File(storeDir(), "$name.png").outputStream().use { out -> big.compress(Bitmap.CompressFormat.PNG, 100, out) }
        }
        return game
    }

    /** farm-game/Docs/store, found by walking up from the test's working directory. */
    private fun storeDir(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            if (File(dir, "Docs/GAME_DESIGN.md").isFile) return File(dir, "Docs/store").also { it.mkdirs() }
            dir = dir.parentFile
        }
        error("Docs/GAME_DESIGN.md not found above ${File("").absolutePath}")
    }

    companion object {
        /** Tutorial tip keys (Hud.tip) to mark as already shown. */
        private val TUTORIAL_TIPS = listOf(
            "axe", "barn-full", "chores", "collect", "energy", "fixtures", "forest", "harvest", "hive", "knock", "land",
            "market", "mine", "mine-dark", "mine-floor", "orders", "plant", "sawmill", "skills", "storm", "stump",
            "village", "water", "workbench", "workers",
        )
    }

    private fun toLevel(g: Game, l: Int) {
        g.world.knowEverything()
        g.world.gainXp(g.world.data.level(l).totalXp - g.world.state.xp)
        g.world.state.energy = 500.0
        g.world.events.clear()
    }

    /** A lush summer farm: every plot planted with summer crops, and three farmhands at work. */
    @Test fun storeFarmSummer() {
        shot("store_farm_summer", seconds = 3f, zoom = 2, setup = { g ->
            toLevel(g, 13)
            val w = g.world
            val s = w.state
            s.coins += 100_000
            s.minutes = GameTime.of(1, Season.SUMMER, 6, 10.0).minutes
            s.lastDayRolled = w.time.dayIndex
            s.weather = Weather.SUNNY
            val summer = listOf("wheat", "corn", "tomato", "sunflower", "eggplant", "melon", "grapes", "potato", "carrot")
                .filter { w.data.crops.containsKey(it) && w.data.crop(it).seasons.contains(Season.SUMMER) }
            for (i in 0 until 40) {
                val p = s.plots[i]
                p.owned = true
                val id = summer[(i * 7) % summer.size]
                p.crop = id
                p.growth = w.data.crop(id).growMinutes * listOf(0.1, 0.4, 0.8, 1.0, 1.0)[i % 5]
                p.watered = i % 3 != 0
            }
            w.buyBlock(1)
            w.hire("rosa"); w.hire("milo"); w.hire("hana")
            repeat(15) { w.levelUpWorker("rosa") }
            w.assign("rosa", 0); w.assign("milo", 1)
            s.fields[0].crop = "wheat"; s.fields[1].crop = "carrot"
            w.add("seed_wheat", 20); w.add("carrot", 20)
            for (i in FieldLayout.plotsOf(0)) w.plot(i).apply { clear(); crop = "wheat"; growth = w.data.crop("wheat").growMinutes * if (i % 3 == 0) 1.0 else 0.5 }
            w.crate(0)["wheat"] = 12
            w.events.clear()
            g.farm.centerOnTile(16, 20)
        })
    }

    /** The village at the brick tier: brick houses, the carpenter's workshop and the seed shop. */
    @Test fun storeVillageBrick() {
        shot("store_village_brick", seconds = 0.4f, zoom = 2, setup = { g ->
            toLevel(g, 16)
            g.world.state.villageTier = 2
            g.farm.enter()
            g.farm.centerOnTile(11, 28)
        })
    }

    /** The forest grove: tall pines, birches and a chopped clearing. */
    @Test fun storeForestGrove() {
        shot("store_forest_grove", seconds = 1f, setup = { g ->
            toLevel(g, 12)
            listOf("B", "C").forEach { g.world.state.forest.zones += it }
            g.world.events.clear()
            g.goTo(Nav.FOREST)
            g.engine.screen?.enter()
            (g.engine.screen as ForestScreen).centerOnTile(33, 30)
        })
    }

    /** A lit mine floor with a cluster of veins: copper, iron, gold, emerald, ruby, amethyst. */
    @Test fun storeMineOres() {
        shot("store_mine_ores", seconds = 1f, setup = { g ->
            toLevel(g, 14)
            g.world.state.coins += 200; g.world.add("wood", 30); g.world.add("torch", 6)
            g.world.state.tools["pickaxe"] = 3
            g.world.mine.gatesOpened += listOf(10, 20)
            g.world.repairMine(); g.world.enterFloor(12)
            g.world.mine.dark = false
            g.goTo(Nav.MINE)
            g.engine.screen?.enter()
            val m = g.world.mine
            val n = m.grid
            val c0 = n / 2
            val r0 = n / 2
            val veins = listOf(
                -3 to -1 to "copper_vein", -2 to -2 to "iron_vein", -1 to -2 to "gold_vein",
                0 to -2 to "emerald_node", 1 to -2 to "ruby_node", -1 to 0 to "amethyst_node",
            )
            for ((pos, node) in veins) {
                val (dc, dr) = pos
                m.cells[(r0 + dr) * n + (c0 + dc)].node = node
            }
            (g.engine.screen as MineScreen).centerOnTile(c0 - 1, r0 - 1)
        })
    }

    /** The bank's loan office: the officer's line over the portrait dialogue. */
    @Test fun storeStoryBank() {
        shot("store_story_bank", story = true, seconds = 0.3f, setup = { g ->
            g.world.state.playerName = "Rosa"; g.world.state.playerLook = 2
        }, drive = { g, t -> if (t < 0.05f) g.farm.chrome.story.showLine(6) })
    }

    /** Grandpa's goals: the daily chores with a streak, and the truck contract. */
    @Test fun storeDailyGoals() {
        shot("store_daily_goals", setup = { g ->
            toLevel(g, 8)
            val w = g.world
            w.state.bump("truck_trips")
            w.rollDaily(g.today())
            w.state.chores[0].let { w.state.bump(it.stat, it.n / 2) }
            w.state.chores[1].let { w.state.bump(it.stat, it.n) }
            w.state.chores[2].let { w.state.bump(it.stat, it.n) }
            w.claimChore(2)
            w.state.choreStreak = 3; w.state.choreDoneDay = g.today() - 1
            w.state.contract!!.crops.keys.first().let { w.state.stats["trucked_$it"] = (w.state.stats["trucked_$it"] ?: 0) + 40 }
            w.togglePin("chore:${w.state.choreDay}:0")
            w.events.clear()
            g.farm.showPanel("goals")
            g.farm.chrome.panels.goalsTab = 0
        })
    }
}
