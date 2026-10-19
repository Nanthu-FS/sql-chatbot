package com.ditherfarm.play

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.ditherfarm.Game
import com.ditherfarm.Host
import com.ditherfarm.engine.AssetSource
import com.ditherfarm.engine.FileAssets
import com.ditherfarm.shots.ScreenshotTest
import com.ditherfarm.ui.Chrome
import com.ditherfarm.ui.FarmScreen
import com.ditherfarm.ui.ForestScreen
import com.ditherfarm.ui.Hud
import com.ditherfarm.ui.IsoMapScreen
import com.ditherfarm.ui.MineScreen
import com.ditherfarm.ui.Nav
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Lets a tester (a person or an agent) play the real game one turn at a time without a phone.
 * Skipped unless gradle runs with -Pplay=<dir>. Each run is one turn:
 *  1. loads the farm saved in <dir>/save (a new farm the first time), the clock, the screen and the camera;
 *  2. runs the commands in <dir>/actions.txt, one per line (coordinates are game pixels, 270 x 480):
 *       tap X Y              a finger tap
 *       hold X Y             a long press (on a ripe crop it starts a harvest slide)
 *       swipe X1 Y1 X2 Y2    long press at the start, slide to the end, lift (harvest / water many)
 *       pan DX DY            drag the map by DX, DY (positive DX shows more of the left side)
 *       wait SECONDS         let time pass (crops grow in real seconds; up to 3600)
 *       back                 the phone's back button
 *       shot                 an extra screenshot now
 *     Lines starting with # are ignored.
 *  3. saves the farm and writes <dir>/shots/turn_NNN.png (2x), turn_NNN_grid.png (the same with a
 *     30-pixel coordinate grid) and <dir>/state.txt (what is on screen and in the farm).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayTest {
    private class PlayHost(dir: File, var clock: Long) : Host {
        override val assetSource: AssetSource = FileAssets(ScreenshotTest.assetRoot())
        override val saveDir: File = File(dir, "save").also { it.mkdirs() }
        override val context: android.content.Context? = null
        override val density = 3f
        override fun nowMs(): Long = clock
    }

    private val w = 270
    private val h = 480
    private val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bmp)
    private lateinit var host: PlayHost
    private lateinit var game: Game
    private val log = ArrayList<String>()
    private var shotN = 0
    private lateinit var shotsDir: File
    private var turn = 0

    private fun frame(dt: Float = 1f / 30f) {
        canvas.drawColor(0xFF181425.toInt())
        game.engine.frame(dt, canvas, host.clock)
        host.clock += (dt * 1000).toLong()
    }

    private fun frames(n: Int) = repeat(n) { frame() }
    private val screen get() = game.engine.screen as IsoMapScreen

    private fun tap(x: Float, y: Float) {
        val s = screen
        if (s.hudDown(x, y)) { frame(); s.hudUp(x, y) } else s.tap(x, y)
        frames(4)
    }

    @Test fun play() {
        val dirName = System.getProperty("play.dir")
        assumeTrue("run with -Pplay=<dir>", dirName != null)
        val dir = File(dirName!!).also { it.mkdirs() }
        shotsDir = File(dir, "shots").also { it.mkdirs() }
        val meta = File(dir, "meta.txt").takeIf { it.isFile }?.readLines()?.associate { it.substringBefore('=') to it.substringAfter('=') } ?: emptyMap()
        turn = (meta["turn"]?.toInt() ?: 0) + 1
        host = PlayHost(dir, meta["clock"]?.toLong() ?: 1_700_000_000_000L)
        game = Game(host)
        game.engine.resize(w, h)
        game.engine.screen?.enter()
        when (meta["screen"]) {
            "FOREST" -> { game.goTo(Nav.FOREST); game.engine.screen?.enter() }
            "MINE" -> { game.goTo(Nav.MINE); game.engine.screen?.enter() }
        }
        frames(2)
        restoreCamera(meta)
        frames(2)

        val actions = File(dir, "actions.txt").takeIf { it.isFile }?.readLines() ?: emptyList()
        for (raw in actions) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val p = line.split(Regex("\\s+"))
            val n = p.drop(1).mapNotNull { it.toFloatOrNull() }
            try {
                when (p[0].lowercase()) {
                    "tap" -> tap(n[0], n[1])
                    "hold" -> { screen.longPress(n[0], n[1]); frames(2); screen.paintEnd(); frames(3) }
                    "swipe" -> {
                        screen.longPress(n[0], n[1]); frames(2)
                        for (i in 1..12) { screen.paint(n[0] + (n[2] - n[0]) * i / 12, n[1] + (n[3] - n[1]) * i / 12); frame() }
                        screen.paintEnd(); frames(3)
                    }
                    "pan" -> { screen.cam.panBy(n[0], n[1]); frames(2) }
                    "wait" -> {
                        var left = n[0].coerceIn(0f, 3600f)
                        while (left > 2f) { frame(1f); left -= 1f }
                        while (left > 0f) { frame(); left -= 1f / 30f }
                    }
                    "back" -> { game.onBack(); frames(3) }
                    "shot" -> shot("mid")
                    else -> log += "unknown command: $line"
                }
                log += "ok: $line"
            } catch (e: Exception) {
                log += "ERROR on '$line': $e"
            }
        }
        frames(3)
        game.save()
        shot("turn")
        File(dir, "state.txt").writeText(state())
        val sc = when (game.engine.screen) { is ForestScreen -> "FOREST"; is MineScreen -> "MINE"; else -> "FARM" }
        val c = screen.cam
        File(dir, "meta.txt").writeText("turn=$turn\nclock=${host.clock}\nscreen=$sc\ncamx=${c.x}\ncamy=${c.y}\nzoom=${c.zoom}\n")
    }

    private fun restoreCamera(meta: Map<String, String>) {
        val c = screen.cam
        val zoom = meta["zoom"]?.toIntOrNull() ?: return
        c.zoomAt(zoom - c.zoom, w / 2f, h / 2f)
        val x = meta["camx"]?.toFloatOrNull() ?: return
        val y = meta["camy"]?.toFloatOrNull() ?: return
        c.panBy((c.x - x) * c.zoom, (c.y - y) * c.zoom)
    }

    private fun shot(kind: String) {
        val name = if (kind == "turn") "turn_%03d".format(turn) else "turn_%03d_mid%d".format(turn, ++shotN)
        val big = Bitmap.createScaledBitmap(bmp, w * 2, h * 2, false)
        File(shotsDir, "$name.png").outputStream().use { big.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (kind != "turn") return
        // The same frame with a grid every 30 game pixels, labelled, to read tap coordinates off.
        val grid = big.copy(Bitmap.Config.ARGB_8888, true)
        val gc = Canvas(grid)
        val line = Paint().apply { color = Color.argb(110, 255, 0, 255); strokeWidth = 1f }
        val text = Paint().apply { color = Color.MAGENTA; textSize = 14f; isAntiAlias = true }
        for (gx in 0..w step 30) { gc.drawLine(gx * 2f, 0f, gx * 2f, h * 2f, line); gc.drawText("$gx", gx * 2f + 2, 14f, text) }
        for (gy in 0..h step 30) { gc.drawLine(0f, gy * 2f, w * 2f, gy * 2f, line); gc.drawText("$gy", 2f, gy * 2f - 2, text) }
        File(shotsDir, "${name}_grid.png").outputStream().use { grid.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun state(): String {
        val wd = game.world
        val s = wd.state
        val t = wd.time
        val sb = StringBuilder()
        val chrome: Chrome = when (val sc = game.engine.screen) { is ForestScreen -> sc.chrome; is MineScreen -> sc.chrome; is FarmScreen -> sc.chrome; else -> game.farm.chrome }
        sb.appendLine("TURN $turn")
        sb.appendLine("screen: ${game.engine.screen?.javaClass?.simpleName}  open panel: ${chrome.panel}  story scene playing: ${chrome.story.playing}")
        val toast = runCatching { Hud::class.java.getDeclaredField("toastText").apply { isAccessible = true }.get(chrome.hud) as String? }.getOrNull()
        if (toast != null) sb.appendLine("toast on screen: $toast")
        game.awaySummary?.let { sb.appendLine("welcome-back popup: $it") }
        sb.appendLine("player: ${s.playerName.ifBlank { "(no name yet)" }}  level ${wd.level}  xp ${s.xp}  coins ${s.coins}  energy ${s.energy.toInt()}")
        sb.appendLine("time: day ${t.dayOfSeason} of ${t.season}, ${"%.1f".format(t.hour)}h, weather ${s.weather}")
        sb.appendLine("village tier ${s.villageTier}, story seen: ${s.storySeen}")
        val owned = s.plots.withIndex().filter { it.value.owned }
        val planted = owned.count { it.value.crop != null }
        val ready = owned.count { wd.isReady(it.value) }
        sb.appendLine("plots: ${owned.size} owned, $planted planted, $ready ready to harvest")
        sb.appendLine("inventory: " + s.inventory.filter { it.value > 0 }.entries.sortedByDescending { it.value }.joinToString { "${it.key} x${it.value}" })
        sb.appendLine("mine: repaired=${s.mine.repaired} floor=${s.mine.floor}")
        sb.appendLine("--- actions this turn")
        log.forEach { sb.appendLine(it) }
        return sb.toString()
    }
}
