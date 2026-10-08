package app.monoworkspace.ui.components

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.monoworkspace.ui.theme.MonoColors
import app.monoworkspace.ui.theme.MonoIcons
import app.monoworkspace.ui.theme.MonoType
import app.monoworkspace.ui.theme.Motion
import app.monoworkspace.ui.theme.Space
import app.monoworkspace.ui.theme.motionMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Desktop panel that replaces the phone bottom sheet: a floating square card
 * with a 1dp ink border and a hard offset shadow, springing in over the
 * blurred app. Esc or a click outside closes it.
 */
@Composable
fun MonoBottomSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    trailing: @Composable () -> Unit = {},
    width: Dp = 560.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Overlay(onDismiss) {
        PanelCard(modifier.widthIn(max = width).fillMaxWidth()) {
            if (title != null) {
                Row(
                    Modifier.fillMaxWidth().padding(start = Space.l, end = Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, Modifier.weight(1f).padding(vertical = Space.m), style = MonoType.h3, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    trailing()
                    MonoIconButton(MonoIcons.X, "Close (Esc)", onDismiss)
                }
                SectionRule()
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(bottom = Space.l, top = if (title == null) Space.s else 0.dp),
                content = content,
            )
        }
    }
}

/** The shared overlay card: white, square, ink border, hard 6dp offset shadow. */
@Composable
fun PanelCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val maxH = LocalOverlayMaxHeight.current
    val shape = app.monoworkspace.ui.theme.MonoShapes.card
    val square = app.monoworkspace.ui.theme.MonoShapes.radius == 0.dp
    Box(modifier.heightIn(max = maxH)) {
        if (square) Box(Modifier.matchParentSize().padding(start = 6.dp, top = 6.dp).offset(6.dp, 6.dp).background(MonoColors.Ink))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxH - 6.dp)
                .then(if (square) Modifier else Modifier.shadow(24.dp, shape, ambientColor = MonoColors.Scrim, spotColor = MonoColors.Scrim))
                .clip(shape)
                .background(MonoColors.Background)
                .border(1.dp, if (square) MonoColors.Ink else MonoColors.Rule, shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            content = content,
        )
    }
}

/** Confirmation panel bounded by rules. */
@Composable
fun MonoDialog(
    onDismiss: () -> Unit,
    title: String,
    body: String? = null,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String = "Cancel",
    destructive: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Overlay(onDismiss) {
        PanelCard(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
            Text(title, Modifier.padding(Space.l), style = MonoType.h3)
            SectionRule()
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(Space.l)) {
                if (body != null) Text(body, style = MonoType.bodySmall.copy(color = MonoColors.Secondary))
                if (content != null) {
                    if (body != null) Spacer(Modifier.height(Space.m))
                    content()
                }
            }
            SectionRule()
            Row(Modifier.fillMaxWidth().padding(Space.m), horizontalArrangement = Arrangement.End) {
                MonoButton(dismissLabel, onDismiss, style = MonoButtonStyle.Text)
                Spacer(Modifier.width(Space.s))
                MonoButton(confirmLabel, onConfirm, style = MonoButtonStyle.Filled, destructive = destructive)
            }
        }
    }
}

/** App-wide message channel backed by a SnackbarHostState. */
@Stable
class Messenger(val host: SnackbarHostState, private val scope: CoroutineScope) {
    fun show(message: String, action: String? = null, long: Boolean = false, onAction: () -> Unit = {}) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val r = host.showSnackbar(message, action, withDismissAction = false, duration = if (long || action != null) SnackbarDuration.Long else SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) onAction()
        }
    }
}

val LocalMessenger = staticCompositionLocalOf<Messenger> { error("Messenger not provided") }

@Composable
fun MonoSnackbarHost(host: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(host, modifier) { data -> MonoSnackbar(data) }
}

@Composable
fun MonoSnackbar(data: SnackbarData) {
    Row(
        Modifier
            .padding(Space.l)
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .heightIn(min = 48.dp)
            .background(MonoColors.Ink)
            .padding(start = Space.l, end = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(data.visuals.message, Modifier.weight(1f).padding(vertical = Space.m), style = MonoType.bodySmall.copy(color = MonoColors.OnInk))
        val action = data.visuals.actionLabel
        if (action != null) {
            Text(
                action.uppercase(),
                Modifier.clickable { data.performAction() }.padding(Space.m),
                style = MonoType.label.copy(color = MonoColors.OnInk, textDecoration = TextDecoration.Underline),
            )
        }
    }
}

data class MenuItem(
    val label: String,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val checked: Boolean = false,
    val onClick: () -> Unit,
)

private class BelowAnchor(private val offset: IntOffset) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        var x = anchorBounds.left + offset.x
        if (x + popupContentSize.width > windowSize.width) x = (anchorBounds.right - popupContentSize.width).coerceAtLeast(0)
        var y = anchorBounds.bottom + offset.y
        if (y + popupContentSize.height > windowSize.height) y = (anchorBounds.top - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x.coerceAtLeast(0), y)
    }
}

/** Flat dropdown: 1dp rule border, square, no shadow. Place it inside the anchor's Box. */
@Composable
fun MonoMenu(expanded: Boolean, onDismiss: () -> Unit, items: List<MenuItem>, width: Dp = 240.dp, offset: IntOffset = IntOffset.Zero) {
    if (!expanded) return
    Popup(popupPositionProvider = BelowAnchor(offset), onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        val appear = androidx.compose.runtime.remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(
            visibleState = appear,
            enter = fadeIn(tween(motionMs(Motion.FAST))) + expandVertically(tween(motionMs(Motion.FAST))),
            exit = fadeOut(tween(motionMs(Motion.FAST))) + shrinkVertically(tween(motionMs(Motion.FAST))),
        ) {
            val shape = app.monoworkspace.ui.theme.MonoShapes.card
            Column(
                Modifier
                    .width(width)
                    .clip(shape)
                    .background(MonoColors.Background)
                    .border(1.dp, MonoColors.Rule, shape)
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                items.forEachIndexed { i, item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .inkClickable(onClick = { onDismiss(); item.onClick() }, enabled = item.enabled)
                            .padding(horizontal = Space.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val color = when {
                            !item.enabled -> MonoColors.Tertiary
                            item.destructive -> MonoColors.Destructive
                            else -> MonoColors.Ink
                        }
                        if (item.icon != null) {
                            Icon(item.icon, null, Modifier.size(20.dp), tint = color)
                            Spacer(Modifier.width(Space.m))
                        }
                        Text(item.label, Modifier.weight(1f), style = MonoType.bodySmall.copy(color = color), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (item.checked) Icon(MonoIcons.Check, null, Modifier.size(18.dp), tint = MonoColors.Ink)
                    }
                    if (i < items.lastIndex) Hairline()
                }
            }
        }
    }
}

/** Top bar: 56dp (48dp in landscape), 1dp rule below. */
@Composable
fun MonoTopBar(
    modifier: Modifier = Modifier,
    navigation: (@Composable () -> Unit)? = null,
    height: Dp = 56.dp,
    title: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
) {
    Column(modifier.fillMaxWidth().background(MonoColors.Background)) {
        Row(
            Modifier.fillMaxWidth().height(height).padding(horizontal = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (navigation != null) navigation()
            Box(Modifier.weight(1f).padding(horizontal = Space.xs)) { title() }
            actions()
        }
        SectionRule()
    }
}

@Composable
fun Breadcrumb(parts: List<String>, modifier: Modifier = Modifier, onClick: ((Int) -> Unit)? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        parts.forEachIndexed { i, p ->
            val last = i == parts.lastIndex
            Text(
                p,
                Modifier
                    .weight(1f, fill = false)
                    .then(if (onClick != null && !last) Modifier.clickable { onClick(i) } else Modifier),
                style = MonoType.caption.copy(color = if (last) MonoColors.Ink else MonoColors.Secondary, fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!last) Text("  /  ", style = MonoType.caption.copy(color = MonoColors.Tertiary))
        }
    }
}

@Composable
fun ToolbarDivider() {
    Box(Modifier.width(1.dp).height(24.dp).background(MonoColors.Hairline))
}

@Composable
fun ChipText(text: String, modifier: Modifier = Modifier, inverted: Boolean = false, color: Color = MonoColors.Ink) {
    Text(
        text,
        modifier
            .background(if (inverted) MonoColors.Ink else Color.Transparent)
            .border(1.dp, if (inverted) MonoColors.Ink else color)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        style = MonoType.caption.copy(color = if (inverted) MonoColors.OnInk else color),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
