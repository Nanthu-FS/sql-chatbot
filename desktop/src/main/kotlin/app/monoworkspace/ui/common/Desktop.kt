package app.monoworkspace.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.awt.ComposeWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.awt.Desktop
import java.awt.FileDialog
import java.io.File

/** Base for screen state holders; the navigator clears it when its entry leaves the back stack. */
abstract class ViewModel {
    val viewModelScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    protected open fun onCleared() {}
    fun clear() {
        onCleared()
        viewModelScope.cancel()
    }
}

/** Per-destination saved state: the route plus values that survive leaving and coming back. */
class SavedStateHandle(val route: Any) {
    private val flows = HashMap<String, MutableStateFlow<Any?>>()

    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: String): T? = flows[key]?.value as T?

    operator fun <T> set(key: String, value: T?) {
        val f = flows[key]
        if (f == null) flows[key] = MutableStateFlow(value) else f.value = value
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getStateFlow(key: String, initial: T): StateFlow<T> =
        flows.getOrPut(key) { MutableStateFlow(initial) } as StateFlow<T>
}

inline fun <reified T> SavedStateHandle.toRoute(): T = route as T

/** One back stack entry: its route, saved state and the models created for it. */
class BackStackEntry(val route: Any, val id: Long) {
    val handle = SavedStateHandle(route)
    val models = HashMap<String, ViewModel>()
    fun clear() {
        models.values.forEach { it.clear() }
        models.clear()
    }
}

val LocalBackStackEntry = staticCompositionLocalOf<BackStackEntry> { error("No back stack entry") }

/**
 * Back handling for Esc, the mouse back button and Alt+Left. The most recently
 * composed enabled handler wins, like Android's OnBackPressedDispatcher.
 */
class BackDispatcher {
    internal class Handler(var enabled: Boolean, var onBack: () -> Unit)
    internal val handlers = mutableStateListOf<Handler>()

    /** Returns true when a screen-level handler consumed the event. */
    fun dispatch(): Boolean {
        val h = handlers.lastOrNull { it.enabled } ?: return false
        h.onBack()
        return true
    }
}

val LocalBackDispatcher = staticCompositionLocalOf { BackDispatcher() }

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val dispatcher = LocalBackDispatcher.current
    val current = rememberUpdatedState(onBack)
    val handler = remember { BackDispatcher.Handler(enabled) { current.value() } }
    SideEffect { handler.enabled = enabled }
    DisposableEffect(dispatcher) {
        dispatcher.handlers.add(handler)
        onDispose { dispatcher.handlers.remove(handler) }
    }
}

val LocalComposeWindow = staticCompositionLocalOf<ComposeWindow?> { null }

/** Native Windows file dialogs (java.awt.FileDialog maps to the Win32 common dialogs). */
object NativeFiles {
    val IMAGES = listOf("png", "jpg", "jpeg", "gif", "bmp", "webp")

    fun open(window: ComposeWindow?, title: String, extensions: List<String> = emptyList()): File? {
        val d = FileDialog(window, title, FileDialog.LOAD)
        if (extensions.isNotEmpty()) {
            // Windows honours a "*.a;*.b" file pattern; other platforms use the filter.
            d.file = extensions.joinToString(";") { "*.$it" }
            d.setFilenameFilter { _, name -> extensions.any { name.lowercase().endsWith(".$it") } }
        }
        d.isVisible = true
        val name = d.file ?: return null
        return File(d.directory, name).takeIf { it.isFile }
    }

    fun save(window: ComposeWindow?, title: String, fileName: String): File? {
        val d = FileDialog(window, title, FileDialog.SAVE)
        d.file = fileName
        d.isVisible = true
        val name = d.file ?: return null
        var f = File(d.directory, name)
        val ext = fileName.substringAfterLast('.', "")
        if (ext.isNotEmpty() && !f.name.contains('.')) f = File(f.parentFile, f.name + "." + ext)
        return f
    }

    /** Opens a file with its default Windows application. */
    fun launch(file: File): Boolean = runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(file)
            true
        } else false
    }.getOrDefault(false)

    /** Shows a folder in Explorer. */
    fun reveal(dir: File): Boolean = runCatching {
        if (System.getProperty("os.name").lowercase().contains("win")) {
            ProcessBuilder("explorer.exe", dir.absolutePath).start()
            true
        } else launch(dir)
    }.getOrDefault(false)
}
