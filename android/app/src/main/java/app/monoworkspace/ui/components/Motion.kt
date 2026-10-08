package app.monoworkspace.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.monoworkspace.ui.theme.LocalReduceMotion
import app.monoworkspace.ui.theme.MonoColors
import app.monoworkspace.ui.theme.MonoIcons
import app.monoworkspace.ui.theme.MonoType
import app.monoworkspace.ui.theme.Motion
import app.monoworkspace.ui.theme.monoTween
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class IconExit { Down, Up }

/**
 * A round button that morphs into a square-cornered label pill when the S Pen
 * (or a mouse) hovers it: the width grows, the corners sharpen, the icon flies
 * out and the label flies in. With [confirm], the first touch tap arms the
 * button (same morph) and a second tap within three seconds runs the action;
 * a hovering pen already shows the label, so its tap runs the action directly.
 */
@Composable
fun ExpandingActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    idleColor: Color = MonoColors.Ink,
    activeColor: Color = MonoColors.Ink,
    contentColor: Color = MonoColors.White,
    iconExit: IconExit = IconExit.Down,
    confirm: Boolean = false,
    size: Dp = 48.dp,
    expandedWidth: Dp = 144.dp,
    ring: Boolean = false,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    var armed by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val reduce = LocalReduceMotion.current
    LaunchedEffect(armed) {
        if (armed) {
            delay(3000)
            armed = false
        }
    }
    val expanded = enabled && (hovered || armed || (pressed && !confirm))
    val transition = updateTransition(expanded, label = "expand")
    val ms = if (reduce) 0 else Motion.MEDIUM
    val width by transition.animateDp({ tween(ms, easing = FastOutSlowInEasing) }, label = "w") { if (it) expandedWidth else size }
    val corner by transition.animateFloat({ tween(ms, easing = FastOutSlowInEasing) }, label = "c") { if (it) 0f else 0.5f }
    val bg by transition.animateColor({ tween(ms) }, label = "bg") { if (it) activeColor else idleColor }
    val iconShift by transition.animateFloat({ tween(ms, easing = FastOutSlowInEasing) }, label = "i") { if (it) 1f else 0f }
    val labelIn by transition.animateFloat({ tween(ms, delayMillis = if (reduce) 0 else 60, easing = FastOutSlowInEasing) }, label = "l") { if (it) 1f else 0f }

    val shape = RoundedCornerShape(size * corner)
    val dir = if (iconExit == IconExit.Down) 1f else -1f
    Box(
        modifier
            .then(if (ring) Modifier.border(4.dp, MonoColors.Hairline, shape).padding(4.dp) else Modifier)
            .height(size)
            .width(width)
            .clip(shape)
            .background(if (enabled) bg else MonoColors.Hairline)
            .semantics { contentDescription = label }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button) {
                when {
                    !confirm -> onClick()
                    hovered || armed -> {
                        armed = false
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    }
                    else -> {
                        armed = true
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, null,
            Modifier.size(20.dp).graphicsLayer {
                translationY = dir * iconShift * this.size.height * 1.6f
                alpha = 1f - iconShift
            },
            tint = contentColor,
        )
        Text(
            if (armed && !hovered) "Tap to ${label.lowercase()}" else label,
            Modifier.graphicsLayer {
                alpha = labelIn
                // Mirrors the CSS sample: the label grows from a dot and drops in.
                val s = 0.25f + 0.75f * labelIn
                scaleX = s
                scaleY = s
                translationY = -dir * (1f - labelIn) * 24.dp.toPx()
            },
            style = MonoType.label.copy(color = contentColor),
            maxLines = 1,
        )
    }
}

/** Delete: black circle that turns into a red "Delete" bar. Always confirmed. */
@Composable
fun DeleteButton(onDelete: () -> Unit, modifier: Modifier = Modifier, label: String = "Delete", size: Dp = 48.dp, expandedWidth: Dp = 148.dp) {
    ExpandingActionButton(
        icon = MonoIcons.Trash,
        label = label,
        onClick = onDelete,
        modifier = modifier,
        idleColor = MonoColors.Ink,
        activeColor = MonoColors.Destructive,
        iconExit = IconExit.Down,
        confirm = true,
        size = size,
        expandedWidth = expandedWidth,
    )
}

/** Back to top: appears after scrolling, morphs into a labelled bar on hover. */
@Composable
fun BackToTopButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(if (reduce) 0 else Motion.FAST)) + slideInVertically(tween(if (reduce) 0 else Motion.MEDIUM)) { it },
        exit = fadeOut(tween(if (reduce) 0 else Motion.FAST)) + slideOutVertically(tween(if (reduce) 0 else Motion.FAST)) { it },
    ) {
        ExpandingActionButton(
            icon = MonoIcons.ArrowUp,
            label = "Back to top",
            onClick = onClick,
            iconExit = IconExit.Up,
            ring = true,
            expandedWidth = 152.dp,
        )
    }
}

@Composable
fun rememberShowBackToTop(state: LazyListState, threshold: Int = 6): Boolean {
    val show by remember(state) { derivedStateOf { state.firstVisibleItemIndex >= threshold } }
    return show
}

@Composable
fun rememberShowBackToTop(state: LazyGridState, threshold: Int = 6): Boolean {
    val show by remember(state) { derivedStateOf { state.firstVisibleItemIndex >= threshold } }
    return show
}

@Composable
fun BackToTopFor(state: LazyListState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    BackToTopButton(rememberShowBackToTop(state), { scope.launch { state.animateScrollToItem(0) } }, modifier)
}

/** Shared state for a group of cards: which one the pen is over. */
@Stable
class HoverFocusState {
    var focused by mutableStateOf<Any?>(null)
}

@Composable
fun rememberHoverFocusState(): HoverFocusState = remember { HoverFocusState() }

/**
 * Hover-to-focus cards: the hovered card lifts (scale up), every other card in
 * the group recedes (scale down, blur, fade). Blur needs Android 12+; older
 * versions still get the scale and fade.
 */
fun Modifier.hoverFocus(state: HoverFocusState, key: Any, interaction: MutableInteractionSource): Modifier = composed {
    val hovered by interaction.collectIsHoveredAsState()
    LaunchedEffect(hovered) {
        if (hovered) state.focused = key else if (state.focused == key) state.focused = null
    }
    val isFocused = state.focused == key
    val dimmed = state.focused != null && !isFocused
    val scale by animateFloatAsState(
        when {
            isFocused -> 1.05f
            dimmed -> 0.95f
            else -> 1f
        },
        monoTween(Motion.SLOW), label = "hfScale",
    )
    val blurRadius by animateDpAsState(if (dimmed) 6.dp else 0.dp, monoTween(Motion.SLOW), label = "hfBlur")
    val alpha by animateFloatAsState(if (dimmed) 0.55f else 1f, monoTween(Motion.SLOW), label = "hfAlpha")
    this
        .zIndex(if (isFocused) 1f else 0f)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
        .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius, BlurredEdgeTreatment.Unbounded) else Modifier)
}

/** Small floating offset helper for drag overlays. */
fun Modifier.offsetPx(x: Float, y: Float): Modifier = this.offset { androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt()) }
