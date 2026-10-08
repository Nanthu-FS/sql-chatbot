package app.monoworkspace.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import app.monoworkspace.AppContainer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

enum class LayoutMode { Phone, Landscape, Tablet }

/** Window-size driven layout decisions shared by every screen. */
@Immutable
data class WindowLayout(
    val mode: LayoutMode,
    val widthDp: Int,
    val heightDp: Int,
) {
    val usesSidebar: Boolean get() = mode != LayoutMode.Phone
    val margin: Dp get() = if (mode == LayoutMode.Phone) 16.dp else 32.dp
    val topBarHeight: Dp get() = if (mode == LayoutMode.Landscape) 48.dp else 56.dp
    val isWide: Boolean get() = widthDp >= 600
    /** Readable line length for the editor on wide screens. */
    val contentMaxWidth: Dp get() = if (mode == LayoutMode.Phone) Dp.Unspecified else 760.dp
}

val LocalWindowLayout = staticCompositionLocalOf { WindowLayout(LayoutMode.Phone, 400, 800) }

/** Navigation actions available to every screen. */
interface Navigator {
    fun openPage(pageId: String, blockId: String? = null)
    fun openHome()
    fun openSearch()
    fun openTrash()
    fun openSettings()
    fun back()
    fun openDrawer()
    fun showNewSheet(parentId: String? = null)
    fun showTemplates(parentId: String? = null)
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Navigator not provided") }

/** Builds a ViewModel from the app container and the destination's saved state; it lives as long as the back stack entry. */
@Composable
inline fun <reified VM : ViewModel> monoViewModel(key: String? = null, crossinline create: (AppContainer, SavedStateHandle) -> VM): VM {
    val container = LocalAppContainer.current
    val entry = LocalBackStackEntry.current
    val k = (key ?: "") + ":" + VM::class.qualifiedName
    return remember(entry, k) { entry.models.getOrPut(k) { create(container, entry.handle) } as VM }
}

object Formats {
    private val day = DateTimeFormatter.ofPattern("MMM d, yyyy")
    private val dayTime = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
    private val longDay = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")

    fun relative(ms: Long, now: Long = System.currentTimeMillis()): String {
        val diff = (now - ms) / 1000
        return when {
            diff < 45 -> "just now"
            diff < 3600 -> "${diff / 60} min ago"
            diff < 86_400 -> "${diff / 3600} h ago"
            diff < 7 * 86_400 -> "${diff / 86_400} d ago"
            else -> date(ms)
        }
    }

    fun date(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(day)
    fun dateTime(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(dayTime)
    fun longToday(): String = LocalDate.now().format(longDay)

    fun bytes(n: Long): String = when {
        n < 1024 -> "$n B"
        n < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", n / 1024.0)
        n < 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", n / (1024.0 * 1024))
        else -> String.format(java.util.Locale.US, "%.2f GB", n / (1024.0 * 1024 * 1024))
    }
}
