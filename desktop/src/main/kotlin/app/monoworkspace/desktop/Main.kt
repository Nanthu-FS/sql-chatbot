package app.monoworkspace.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.awt.ComposeWindow
import app.monoworkspace.ui.common.Navigator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.monoworkspace.AppContainer
import app.monoworkspace.ui.common.KeyRouter
import app.monoworkspace.ui.common.LayoutMode
import app.monoworkspace.ui.common.LocalAppContainer
import app.monoworkspace.ui.common.LocalComposeWindow
import app.monoworkspace.ui.common.LocalKeyRouter
import app.monoworkspace.ui.common.LocalWindowLayout
import app.monoworkspace.ui.common.WindowLayout
import app.monoworkspace.ui.lock.LockScreen
import app.monoworkspace.ui.navigation.AppShell
import app.monoworkspace.ui.theme.MonoColors
import app.monoworkspace.ui.theme.MonoTheme
import app.monoworkspace.ui.theme.ThemeRevealHost
import app.monoworkspace.ui.theme.ThemeSpec
import app.monoworkspace.ui.theme.WindowsAppearance
import app.monoworkspace.ui.theme.fx.FxLayer
import app.monoworkspace.ui.theme.resolveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Dimension
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import javax.imageio.ImageIO
import javax.swing.JOptionPane
import kotlin.system.exitProcess

private const val LOCK_AFTER_MS = 5 * 60_000L
private const val PURGE_EVERY_MS = 6 * 60 * 60_000L

fun main() {
    val dataDir = AppContainer.defaultDataDir()
    val instanceLock = acquireSingleInstance(dataDir) ?: run {
        JOptionPane.showMessageDialog(null, "Mono Workspace is already open.", AppInfo.NAME, JOptionPane.INFORMATION_MESSAGE)
        exitProcess(0)
    }
    val container = AppContainer(dataDir)
    val windowsReducedMotion = windowsAnimationsOff()
    val windowsDarkAtStart = WindowsAppearance.isDark()

    // The search index lives in memory on desktop: build it, then keep the trash tidy.
    container.appScope.launch {
        container.search.rebuild()
        while (isActive) {
            runCatching { container.pages.purgeExpired() }
            delay(PURGE_EVERY_MS)
        }
    }

    val icon = BitmapPainter(ImageIO.read(AppContainer::class.java.classLoader.getResource("app_icon.png")).toComposeImageBitmap())

    application {
        val start = remember { container.settings.now() }
        val windowState = rememberWindowState(
            placement = if (start.windowMaximized) WindowPlacement.Maximized else WindowPlacement.Floating,
            position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center),
            size = DpSize(start.windowWidth.dp, start.windowHeight.dp),
        )
        val keys = remember { KeyRouter() }
        var title by remember { mutableStateOf(AppInfo.NAME) }
        var locked by remember { mutableStateOf(start.appLock) }
        val settings by container.settings.settings.collectAsState(start)

        Window(
            onCloseRequest = {
                container.database.save()
                instanceLock.release()
                exitApplication()
            },
            state = windowState,
            title = if (title == AppInfo.NAME) title else "$title — ${AppInfo.NAME}",
            icon = icon,
            onPreviewKeyEvent = { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.F11) {
                    windowState.placement = if (windowState.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
                    true
                } else if (locked) false else keys.handle(e)
            },
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(720, 520) }

            // Remember size and maximized state for the next launch.
            LaunchedEffect(windowState) {
                snapshotFlow { windowState.size to windowState.placement }
                    .distinctUntilChanged()
                    .debounce(800)
                    .collect { (size, placement) ->
                        if (placement != WindowPlacement.Fullscreen) {
                            container.settings.setWindow(size.width.value.toInt(), size.height.value.toInt(), placement == WindowPlacement.Maximized)
                        }
                    }
            }

            // Lock again after five minutes in the background.
            val focused = LocalWindowInfo.current.isWindowFocused
            var blurredAt by remember { mutableStateOf(0L) }
            LaunchedEffect(focused) {
                if (!focused) {
                    blurredAt = System.currentTimeMillis()
                    withContext(Dispatchers.IO) { container.database.save() }
                } else if (blurredAt > 0 && System.currentTimeMillis() - blurredAt >= LOCK_AFTER_MS && container.settings.now().appLock) {
                    locked = true
                }
            }

            val initialPage by produceState<String?>(null) {
                // Seed first, then reopen the tour (first run) or the last page.
                val seeded = container.seeder.seedIfNeeded()
                value = seeded ?: container.settings.current().lastOpenedPageId?.let { id ->
                    container.pages.get(id)?.takeIf { !it.isTrashed }?.id
                }
            }

            val scope = rememberCoroutineScope()
            // Follow Windows' light/dark app mode: checked at start, on focus and every minute.
            var systemDark by remember { mutableStateOf(windowsDarkAtStart) }
            LaunchedEffect(focused) {
                while (true) {
                    systemDark = withContext(Dispatchers.IO) { WindowsAppearance.isDark() }
                    delay(60_000)
                }
            }
            val theme = resolveTheme(settings, systemDark)
            // The native frame shows its own background while resizing; match the theme.
            LaunchedEffect(theme.id) {
                val bg = theme.main.background
                window.background = java.awt.Color(bg.red, bg.green, bg.blue)
            }
            DesktopRoot(
                container = container,
                keys = keys,
                window = window,
                initialPageId = initialPage,
                theme = theme,
                ambient = settings.ambientEffects && focused,
                reduceMotion = settings.reduceMotion || windowsReducedMotion,
                locked = locked,
                onUnlocked = { locked = false },
                onLock = if (settings.appLock) ({ scope.launch { container.database.save() }; locked = true }) else null,
                onTitle = { title = it },
            )
        }
    }
}

/** Everything inside the window; shared with the headless screenshot renderer. */
@Composable
fun DesktopRoot(
    container: AppContainer,
    keys: KeyRouter,
    window: ComposeWindow?,
    initialPageId: String?,
    theme: ThemeSpec,
    reduceMotion: Boolean,
    ambient: Boolean,
    locked: Boolean,
    onUnlocked: () -> Unit,
    onLock: (() -> Unit)?,
    onTitle: (String) -> Unit,
    onNavigator: (Navigator) -> Unit = {},
) {
    ThemeRevealHost(theme, reduceMotion) { shown ->
        MonoTheme(shown, reduceMotion = reduceMotion, ambient = ambient) {
            BoxWithConstraints(Modifier.fillMaxSize().background(MonoColors.Background)) {
                val w = maxWidth.value.toInt()
                val layout = WindowLayout(if (w < 1000) LayoutMode.Landscape else LayoutMode.Tablet, w, maxHeight.value.toInt())
                CompositionLocalProvider(
                    LocalAppContainer provides container,
                    LocalWindowLayout provides layout,
                    LocalKeyRouter provides keys,
                    LocalComposeWindow provides window,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        AppShell(initialPageId = initialPageId, onTitle = onTitle, onLock = onLock, onNavigator = onNavigator)
                        FxLayer()
                        if (locked) LockScreen(check = container.settings::checkPin, onUnlocked = onUnlocked)
                    }
                }
            }
        }
    }
}

/** Two copies writing the same workspace file would lose edits; the second one exits. */
private fun acquireSingleInstance(dir: File): FileLock? = runCatching {
    val channel = RandomAccessFile(File(dir, ".lock"), "rw").channel
    channel.tryLock()
}.getOrNull()

/** Windows' "Show animations" setting (Accessibility › Visual effects) mirrored into MinAnimate. */
private fun windowsAnimationsOff(): Boolean = runCatching {
    if (!System.getProperty("os.name").lowercase().contains("win")) return@runCatching false
    val p = ProcessBuilder("reg", "query", "HKCU\\Control Panel\\Desktop\\WindowMetrics", "/v", "MinAnimate").redirectErrorStream(true).start()
    val out = p.inputStream.bufferedReader().readText()
    p.waitFor()
    Regex("MinAnimate\\s+REG_SZ\\s+(\\d)").find(out)?.groupValues?.get(1) == "0"
}.getOrDefault(false)
