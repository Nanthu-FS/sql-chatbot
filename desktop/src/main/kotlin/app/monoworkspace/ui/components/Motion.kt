package app.monoworkspace.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay

import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import app.monoworkspace.ui.theme.LocalReduceMotion

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
 * Hover-to-focus cards: the hovered card lifts, tilts toward the cursor in
 * 3D and carries a soft spotlight that follows the pointer; every other card
 * in the group recedes (scale down, blur, fade).
 */
fun Modifier.hoverFocus(state: HoverFocusState, key: Any, interaction: MutableInteractionSource): Modifier = composed {
    val hovered by interaction.collectIsHoveredAsState()
    val reduce = LocalReduceMotion.current
    LaunchedEffect(hovered) {
        if (hovered) state.focused = key else if (state.focused == key) state.focused = null
    }
    val isFocused = state.focused == key
    val dimmed = state.focused != null && !isFocused
    val scale by animateFloatAsState(
        when {
            isFocused -> 1.04f
            dimmed -> 0.96f
            else -> 1f
        },
        if (reduce) tween(0) else spring(dampingRatio = 0.62f, stiffness = 260f), label = "hfScale",
    )
    val blurRadius by animateDpAsState(if (dimmed && !reduce) 5.dp else 0.dp, monoTween(Motion.SLOW), label = "hfBlur")
    val alpha by animateFloatAsState(if (dimmed) 0.5f else 1f, monoTween(Motion.SLOW), label = "hfAlpha")

    var pointer by remember { mutableStateOf(Offset.Unspecified) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val nx = if (pointer.isSpecified && box.width > 0) pointer.x / box.width - 0.5f else 0f
    val ny = if (pointer.isSpecified && box.height > 0) pointer.y / box.height - 0.5f else 0f
    val tiltSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 180f)
    val tiltX by animateFloatAsState(if (isFocused && !reduce) -ny * 9f else 0f, tiltSpring, label = "tiltX")
    val tiltY by animateFloatAsState(if (isFocused && !reduce) nx * 9f else 0f, tiltSpring, label = "tiltY")
    val glow by animateFloatAsState(if (isFocused && !reduce && pointer.isSpecified) 1f else 0f, monoTween(Motion.MEDIUM), label = "glow")
    this
        .zIndex(if (isFocused) 1f else 0f)
        .onSizeChanged { box = it }
        .onPointerEvent(PointerEventType.Move) { e -> pointer = e.changes.first().position }
        .onPointerEvent(PointerEventType.Exit) { pointer = Offset.Unspecified }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            rotationX = tiltX
            rotationY = tiltY
            cameraDistance = 16f * density
            this.alpha = alpha
        }
        .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius, BlurredEdgeTreatment.Unbounded) else Modifier)
        .drawWithContent {
            drawContent()
            if (glow > 0f && pointer.isSpecified) {
                drawRect(
                    Brush.radialGradient(
                        listOf(MonoColors.Ink.copy(alpha = 0.09f * glow), Color.Transparent),
                        center = pointer,
                        radius = size.maxDimension * 0.55f,
                    ),
                )
                // A 2dp ink edge sweeps in along the bottom while focused.
                drawRect(MonoColors.Ink, Offset(0f, size.height - 2.dp.toPx()), Size(size.width * glow, 2.dp.toPx()))
            }
        }
}

/** Small floating offset helper for drag overlays. */
fun Modifier.offsetPx(x: Float, y: Float): Modifier = this.offset { androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt()) }

/**
 * Entrance choreography for a screen: items composed within the first moments
 * after the screen appears rise and fade in one after another; anything that
 * scrolls into view later just appears, so scrolling never replays it.
 */
@Stable
class Entrance internal constructor(private val startedAt: Long, internal val reduce: Boolean) {
    internal fun active(): Boolean = !reduce && System.currentTimeMillis() - startedAt < 900
}

@Composable
fun rememberEntrance(): Entrance {
    val reduce = LocalReduceMotion.current
    return remember { Entrance(System.currentTimeMillis(), reduce) }
}

fun Modifier.staggerIn(entrance: Entrance, index: Int): Modifier = composed {
    val progress = remember { Animatable(if (entrance.active()) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(index.coerceIn(0, 14) * 38L)
            progress.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 240f))
        }
    }
    graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * 28.dp.toPx()
    }
}

/**
 * Display type that sets itself: each letter rises out of a baseline mask
 * with a springy overshoot, left to right.
 */
@Composable
fun KineticText(text: String, style: TextStyle, modifier: Modifier = Modifier, staggerMs: Long = 26) {
    val reduce = LocalReduceMotion.current
    Row(modifier) {
        text.forEachIndexed { i, ch ->
            val p = remember(text) { Animatable(if (reduce) 1f else 0f) }
            LaunchedEffect(text) {
                if (!reduce) {
                    delay(i * staggerMs)
                    p.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 260f))
                }
            }
            Box(Modifier.clipToBounds()) {
                Text(
                    ch.toString(),
                    Modifier.graphicsLayer {
                        translationY = (1f - p.value) * size.height
                        rotationZ = (1f - p.value) * -10f
                        transformOrigin = TransformOrigin(0f, 1f)
                    },
                    style = style,
                )
            }
        }
    }
}
