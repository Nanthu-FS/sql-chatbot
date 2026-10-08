package app.monoworkspace.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import app.monoworkspace.AppContainer
import app.monoworkspace.ui.common.KeyRouter
import app.monoworkspace.ui.common.Navigator
import app.monoworkspace.ui.theme.Themes
import app.monoworkspace.ui.theme.fx.BurstKind
import app.monoworkspace.ui.theme.fx.FxBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.nio.file.Files

/**
 * Renders the real app offscreen (no window, no GPU) through a scripted tour
 * and writes PNGs, so CI can show what the build looks like mid-animation
 * and at rest. Run with `./gradlew renderScreenshots`.
 */
@OptIn(androidx.compose.ui.InternalComposeUiApi::class)
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/screenshots").apply { mkdirs() }
    val data = Files.createTempDirectory("mono-shots").toFile()
    runBlocking(Dispatchers.Main) {
        val container = AppContainer(data)
        val tour = container.seeder.seedIfNeeded()
        val tracker = container.templates.builtins().first { it.name.contains("Task", ignoreCase = true) }
        val trackerPage = container.templates.instantiate(tracker, null)
        container.pages.setFavorite(trackerPage.id, true)
        container.templates.builtins().firstOrNull { it.name.contains("Reading", ignoreCase = true) }?.let { container.templates.instantiate(it, null) }
        container.search.rebuild()
        tour?.let { container.pages.visit(it) }
        container.pages.visit(trackerPage.id)

        val keys = KeyRouter()
        var navigator: Navigator? = null
        var locked by mutableStateOf(false)
        var theme by mutableStateOf(Themes.Mono)
        val w = 1440
        val h = 900
        val scene = ImageComposeScene(w, h, Density(1f), coroutineContext = coroutineContext) {
            DesktopRoot(
                container = container,
                keys = keys,
                window = null,
                initialPageId = null,
                theme = theme,
                ambient = true,
                reduceMotion = false,
                locked = locked,
                onUnlocked = { locked = false },
                onLock = null,
                onTitle = {},
                onNavigator = { navigator = it },
            )
        }
        val start = System.nanoTime()
        suspend fun frames(ms: Long) {
            val end = System.currentTimeMillis() + ms
            while (System.currentTimeMillis() < end) {
                scene.render(System.nanoTime() - start)
                delay(16)
            }
        }
        fun shot(name: String) {
            val img = scene.render(System.nanoTime() - start)
            val bytes = img.encodeToData(EncodedImageFormat.PNG)!!.bytes
            File(out, "$name.png").writeBytes(bytes)
            println("wrote $name.png")
        }
        fun key(k: Key, ctrl: Boolean = false) {
            keys.handle(KeyEvent(k, KeyEventType.KeyDown, isCtrlPressed = ctrl))
        }

        frames(120)
        shot("01-home-entering")
        frames(1600)
        shot("02-home")
        // Hover the first "Jump back in" card to show the tilt and spotlight.
        scene.sendPointerEvent(PointerEventType.Move, Offset(430f, 520f))
        frames(80)
        scene.sendPointerEvent(PointerEventType.Move, Offset(470f, 540f))
        frames(700)
        shot("03-home-hover")
        scene.sendPointerEvent(PointerEventType.Exit, Offset(-1f, -1f))

        tour?.let { navigator?.openPage(it) }
        frames(140)
        shot("04-page-transition")
        frames(1400)
        shot("05-page")

        navigator?.openPage(trackerPage.id)
        frames(1600)
        shot("06-database")

        key(Key.P, ctrl = true)
        frames(110)
        shot("07-palette-opening")
        frames(900)
        shot("08-palette")
        key(Key.Escape)
        frames(500)

        navigator?.openSettings()
        frames(1400)
        shot("09-settings")

        navigator?.openSearch()
        frames(1400)
        shot("10-search")

        locked = true
        frames(900)
        shot("11-lock")
        locked = false
        frames(400)

        // Every theme: Home at rest, a page, and a burst in flight.
        for (t in Themes.all) {
            theme = t
            frames(900)
            navigator?.openHome()
            frames(1500)
            shot("theme-${t.id}-home")
            FxBus.fire(BurstKind.Create, Offset(720f, 450f))
            frames(260)
            shot("theme-${t.id}-burst")
            frames(900)
            tour?.let { navigator?.openPage(it) }
            frames(1500)
            shot("theme-${t.id}-page")
        }
        // A theme switch caught halfway through its reveal.
        scene.sendPointerEvent(PointerEventType.Move, Offset(1100f, 300f))
        theme = Themes.Citrus
        frames(320)
        shot("theme-reveal-mid")
        frames(900)
        navigator?.openSettings()
        frames(1500)
        shot("theme-settings")

        scene.close()
        container.database.save()
    }
    data.deleteRecursively()
    kotlin.system.exitProcess(0)
}
