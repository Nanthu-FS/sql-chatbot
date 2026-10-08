package app.monoworkspace.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.KeyEvent

/** Window-level key routing: the window forwards every key event here before focus handling. */
class KeyRouter {
    var handler: ((KeyEvent) -> Boolean)? = null
    fun handle(e: KeyEvent): Boolean = handler?.invoke(e) ?: false
}

val LocalKeyRouter = staticCompositionLocalOf { KeyRouter() }
