package app.monoworkspace.ui.theme.fx

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import app.monoworkspace.ui.theme.LocalAmbientMotion
import app.monoworkspace.ui.theme.LocalReduceMotion
import app.monoworkspace.ui.theme.LocalSurface
import app.monoworkspace.ui.theme.LocalTheme

/** Full-window overlay for bursts and the pointer trail. It never takes input. */
@Composable
fun FxLayer() {
    val theme = LocalTheme.current
    val surface = LocalSurface.current
    if (LocalReduceMotion.current) {
        LaunchedEffect(FxBus.active.size) { FxBus.active.clear() }
        return
    }
    Box(Modifier.fillMaxSize()) {
        if (LocalAmbientMotion.current) theme.fx.PointerTrail({ FxBus.pointer }, surface)
        for (f in FxBus.active.toList()) {
            key(f.id) { theme.fx.Burst(f.kind, f.origin, surface) { FxBus.done(f) } }
        }
    }
}
