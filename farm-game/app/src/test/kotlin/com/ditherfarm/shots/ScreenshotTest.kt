package com.ditherfarm.shots

import android.graphics.Bitmap
import android.graphics.Canvas
import com.ditherfarm.Game
import com.ditherfarm.Host
import com.ditherfarm.engine.AssetSource
import com.ditherfarm.engine.FileAssets
import com.ditherfarm.engine.Weather
import com.ditherfarm.ui.GridDemoScreen
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

    private fun render(name: String, w: Int = 270, h: Int = 585, seconds: Float = 0.5f, drive: (Game, Float) -> Unit = { _, _ -> }) {
        val game = Game(TestHost())
        game.engine.resize(w, h)
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
        val out = System.getProperty("shots.out") ?: return
        File(out).mkdirs()
        val big = Bitmap.createScaledBitmap(bmp, w * 3, h * 3, false)
        File(out, "$name.png").outputStream().use { big.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun boot() = render("boot")

    private fun demo(name: String, hour: Float, season: String, weather: Weather.Kind) = render(name, seconds = 1f) { game, _ ->
        (game.engine.screen as? GridDemoScreen)?.let { it.hour = hour; it.season = season; it.weather.kind = weather }
    }

    @Test fun demoDay() = demo("demo_day", 11f, "spring", Weather.Kind.CLEAR)

    @Test fun demoOverview() = render("demo_overview", seconds = 1f) { game, t ->
        if (t == 0f) game.engine.screen?.pinch(-1, 135f, 290f)
    }
    @Test fun demoRainDusk() = demo("demo_dusk_rain", 18.6f, "summer", Weather.Kind.RAIN)
    @Test fun demoNight() = demo("demo_night", 23f, "fall", Weather.Kind.LEAVES)
    @Test fun demoWinter() = demo("demo_winter", 7f, "winter", Weather.Kind.SNOW)

    companion object {
        /** app/src/main/assets, whether Gradle runs from the module dir or elsewhere. */
        fun assetRoot(): File = listOfNotNull(
            System.getProperty("assets.dir")?.let(::File),
            File("src/main/assets"),
            File("app/src/main/assets"),
        ).first { File(it, "art").exists() }
    }
}
