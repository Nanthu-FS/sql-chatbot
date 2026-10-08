package app.monoworkspace.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.monoworkspace.ui.common.BackHandler
import app.monoworkspace.ui.theme.LocalReduceMotion
import app.monoworkspace.ui.theme.MonoColors
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicLong

enum class OverlayPlacement { Center, Right }

/**
 * Desktop overlays (sheets, dialogs, palette) render in one layer above the
 * app so the content underneath can be pushed back and blurred while they
 * are open. A caller composes [Overlay] like a dialog; leaving composition
 * plays the exit animation before the entry is dropped.
 */
@Stable
class OverlayHost {
    internal class Entry(
        val id: Long,
        val placement: OverlayPlacement,
        content: @Composable () -> Unit,
        onDismiss: () -> Unit,
        locals: CompositionLocalContext,
    ) {
        var content by mutableStateOf(content)
        var onDismiss by mutableStateOf(onDismiss)
        var locals by mutableStateOf(locals)
        val visible = MutableTransitionState(false).apply { targetState = true }
    }

    internal val entries = mutableStateListOf<Entry>()

    /** True while any overlay is showing or animating in. */
    val active: Boolean get() = entries.any { it.visible.targetState }

    internal companion object {
        val ids = AtomicLong()
    }
}

val LocalOverlayHost = staticCompositionLocalOf<OverlayHost?> { null }

@Composable
fun Overlay(onDismiss: () -> Unit, placement: OverlayPlacement = OverlayPlacement.Center, content: @Composable () -> Unit) {
    val host = LocalOverlayHost.current ?: error("OverlayHost not provided")
    val locals = currentCompositionLocalContext
    val entry = remember { OverlayHost.Entry(OverlayHost.ids.incrementAndGet(), placement, content, onDismiss, locals) }
    SideEffect {
        entry.content = content
        entry.onDismiss = onDismiss
        entry.locals = locals
    }
    BackHandler { entry.onDismiss() }
    DisposableEffect(entry) {
        host.entries.add(entry)
        onDispose { entry.visible.targetState = false }
    }
}

/** Wraps the app: blurs and recedes [content] while overlays are open, then draws them on top. */
@Composable
fun OverlayLayer(host: OverlayHost, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduce = LocalReduceMotion.current
    val active = host.active
    val blur by animateDpAsState(if (active && !reduce) 10.dp else 0.dp, tween(if (reduce) 0 else 260), label = "overlayBlur")
    val depth by animateFloatAsState(if (active && !reduce) 0.985f else 1f, spring(dampingRatio = 0.85f, stiffness = 380f), label = "overlayDepth")
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = depth; scaleY = depth }
                .then(if (blur > 0.dp) Modifier.blur(blur) else Modifier),
        ) { content() }
        host.entries.forEach { e ->
            key(e.id) {
                LaunchedEffect(e) {
                    snapshotFlow { e.visible.isIdle && !e.visible.currentState && !e.visible.targetState }.first { it }
                    host.entries.remove(e)
                }
                OverlayFrame(e, reduce)
            }
        }
    }
}

@Composable
private fun OverlayFrame(e: OverlayHost.Entry, reduce: Boolean) {
    val ms = if (reduce) 0 else 180
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(e.visible, enter = fadeIn(tween(ms)), exit = fadeOut(tween(ms))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MonoColors.Scrim)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { e.onDismiss() },
            )
        }
        val enter = when {
            reduce -> fadeIn(tween(0))
            e.placement == OverlayPlacement.Right -> slideInHorizontallyFromEdge() + fadeIn(tween(160))
            else -> scaleIn(spring(dampingRatio = 0.72f, stiffness = 520f), initialScale = 0.92f, transformOrigin = TransformOrigin(0.5f, 0.3f)) +
                slideInVertically(spring(dampingRatio = 0.8f, stiffness = 520f)) { it / 14 } + fadeIn(tween(140))
        }
        val exit = when {
            reduce -> fadeOut(tween(0))
            e.placement == OverlayPlacement.Right -> slideOutHorizontallyToEdge() + fadeOut(tween(140))
            else -> scaleOut(tween(140), targetScale = 0.96f) + slideOutVertically(tween(140)) { it / 24 } + fadeOut(tween(120))
        }
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(if (e.placement == OverlayPlacement.Right) 0.dp else 24.dp),
            contentAlignment = if (e.placement == OverlayPlacement.Right) Alignment.CenterEnd else Alignment.Center,
        ) {
            val maxH: Dp = maxHeight
            AnimatedVisibility(e.visible, enter = enter, exit = exit) {
                CompositionLocalProvider(e.locals) {
                    CompositionLocalProvider(LocalOverlayMaxHeight provides maxH) { e.content() }
                }
            }
        }
    }
}

private fun slideInHorizontallyFromEdge() = androidx.compose.animation.slideInHorizontally(spring(dampingRatio = 0.86f, stiffness = 420f)) { it }
private fun slideOutHorizontallyToEdge() = androidx.compose.animation.slideOutHorizontally(tween(180)) { it }

/** Height available to an overlay panel inside the window padding. */
val LocalOverlayMaxHeight = staticCompositionLocalOf { 800.dp }
