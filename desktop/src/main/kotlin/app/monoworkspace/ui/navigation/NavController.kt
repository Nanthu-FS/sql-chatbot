package app.monoworkspace.ui.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.monoworkspace.ui.common.BackStackEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Browser-style history for the desktop window: back (Alt+Left, mouse back,
 * the back button) and forward (Alt+Right, mouse forward). A popped entry's
 * models are cleared after its exit animation so pending saves still finish.
 */
@Stable
class NavController(private val scope: CoroutineScope) {
    private var nextId = 0L
    val stack = mutableStateListOf(BackStackEntry(HomeRoute, nextId++))
    private val forward = ArrayList<Any>()

    /** Direction of the last change, for the transition. */
    var lastWasPop by mutableStateOf(false)
        private set
    var canGoForward by mutableStateOf(false)
        private set

    val current: BackStackEntry get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun navigate(route: Any) {
        if (current.route == route) return
        stack.add(BackStackEntry(route, nextId++))
        forward.clear()
        canGoForward = false
        lastWasPop = false
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        val e = stack.removeAt(stack.lastIndex)
        forward.add(e.route)
        canGoForward = true
        lastWasPop = true
        retire(e)
        return true
    }

    fun forward(): Boolean {
        val route = forward.removeLastOrNull() ?: return false
        stack.add(BackStackEntry(route, nextId++))
        canGoForward = forward.isNotEmpty()
        lastWasPop = false
        return true
    }

    /** Pops back to the newest entry matching [match]; false when none exists. */
    fun popTo(match: (Any) -> Boolean): Boolean {
        val idx = stack.indexOfLast { match(it.route) }
        if (idx < 0) return false
        if (idx == stack.lastIndex) return true
        while (stack.lastIndex > idx) {
            val e = stack.removeAt(stack.lastIndex)
            retire(e)
        }
        forward.clear()
        canGoForward = false
        lastWasPop = true
        return true
    }

    /** Drops every entry showing [pageId] (for example after it was trashed elsewhere). */
    fun dropPage(pageId: String) {
        val doomed = stack.filter { e -> stack.size > 1 && pageIdOf(e.route) == pageId }
        if (doomed.isEmpty()) return
        val wasCurrent = doomed.contains(current)
        doomed.forEach { e ->
            if (stack.size > 1) {
                stack.remove(e)
                retire(e)
            }
        }
        forward.removeAll { pageIdOf(it) == pageId }
        canGoForward = forward.isNotEmpty()
        if (wasCurrent) lastWasPop = true
    }

    private fun retire(e: BackStackEntry) {
        scope.launch {
            delay(1_500)
            e.clear()
        }
    }

    companion object {
        fun pageIdOf(route: Any): String? = when (route) {
            is PageRoute -> route.pageId
            is DatabaseRoute -> route.pageId
            else -> null
        }
    }
}
