package app.monoworkspace.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.monoworkspace.ui.theme.MonoColors
import app.monoworkspace.ui.theme.MonoType
import app.monoworkspace.ui.theme.Motion
import app.monoworkspace.ui.theme.Space
import app.monoworkspace.ui.theme.monoTween

/** 1dp black rule: the only section separator. */
@Composable
fun SectionRule(modifier: Modifier = Modifier, color: Color = MonoColors.Rule, thickness: Dp = 1.dp) {
    Box(modifier.fillMaxWidth().height(thickness).background(color))
}

@Composable
fun Hairline(modifier: Modifier = Modifier) = SectionRule(modifier, MonoColors.Hairline)

@Composable
fun VerticalRule(modifier: Modifier = Modifier, color: Color = MonoColors.Rule) {
    Box(modifier.fillMaxHeight().width(1.dp).background(color))
}

/** Uppercase label text. */
@Composable
fun LabelText(text: String, modifier: Modifier = Modifier, color: Color = MonoColors.Ink, maxLines: Int = 1) {
    Text(text.uppercase(), modifier, style = MonoType.label.copy(color = color), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
fun MonoIcon(icon: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = MonoColors.Ink, size: Dp = 24.dp) {
    Icon(icon, contentDescription, modifier.size(size), tint = tint)
}

/**
 * Clickable with the house hover treatment: a tint fill plus a 2dp ink bar that
 * grows from the left edge while hovered (S Pen or mouse) or pressed.
 */
@Composable
fun Modifier.inkClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    showBar: Boolean = true,
    role: Role = Role.Button,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val active = hovered || pressed
    val tint by animateColorAsState(if (active || selected) MonoColors.Tint else Color.Transparent, monoTween(Motion.FAST), label = "tint")
    val bar by animateFloatAsState(if (showBar && (active || selected)) 1f else 0f, monoTween(Motion.MEDIUM), label = "bar")
    return this
        .drawBehind {
            drawRect(tint)
            if (bar > 0f) drawRect(MonoColors.Ink, Offset.Zero, Size(2.dp.toPx(), size.height * bar))
        }
        .combinedClickable(
            interactionSource = interaction,
            indication = androidx.compose.material3.ripple(color = MonoColors.Ink),
            enabled = enabled,
            role = role,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

@Composable
fun MonoIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MonoColors.Ink,
    enabled: Boolean = true,
    selected: Boolean = false,
    iconSize: Dp = 24.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(
        when {
            selected -> MonoColors.Ink
            hovered -> MonoColors.Tint
            else -> Color.Transparent
        },
        monoTween(Motion.FAST), label = "iconbg",
    )
    val scale by animateFloatAsState(if (hovered) 1.08f else 1f, monoTween(Motion.FAST), label = "iconscale")
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .size(48.dp)
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(bounded = false, radius = 24.dp, color = MonoColors.Ink),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, contentDescription,
            Modifier.size(iconSize).graphicsLayer { scaleX = scale; scaleY = scale },
            tint = when {
                !enabled -> MonoColors.Tertiary
                selected -> MonoColors.White
                else -> tint
            },
        )
    }
}

enum class MonoButtonStyle { Filled, Outlined, Text }

/**
 * Buttons invert on hover with a left-to-right ink wipe: outlined fills black,
 * filled turns white. Destructive buttons use the red only for their text/fill.
 */
@Composable
fun MonoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: MonoButtonStyle = MonoButtonStyle.Outlined,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    height: Dp = 44.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val wipe by animateFloatAsState(if ((hovered || pressed) && enabled) 1f else 0f, monoTween(Motion.MEDIUM), label = "wipe")
    val accent = if (destructive) MonoColors.Destructive else MonoColors.Ink
    val (base, fill, baseText, fillText) = when (style) {
        MonoButtonStyle.Filled -> Quad(accent, MonoColors.White, MonoColors.White, accent)
        MonoButtonStyle.Outlined -> Quad(Color.Transparent, accent, accent, MonoColors.White)
        MonoButtonStyle.Text -> Quad(Color.Transparent, MonoColors.Tint, accent, accent)
    }
    val contentColor = if (!enabled) MonoColors.Tertiary else lerp(baseText, fillText, wipe)
    val borderColor = if (!enabled) MonoColors.Hairline else accent
    Row(
        modifier
            .heightIn(min = height)
            .drawBehind {
                drawRect(if (enabled) base else if (style == MonoButtonStyle.Filled) MonoColors.Hairline else Color.Transparent)
                if (wipe > 0f) drawRect(fill, Offset.Zero, Size(size.width * wipe, size.height))
                if (style != MonoButtonStyle.Text) {
                    val w = 1.dp.toPx()
                    drawRect(borderColor, Offset.Zero, Size(size.width, w))
                    drawRect(borderColor, Offset(0f, size.height - w), Size(size.width, w))
                    drawRect(borderColor, Offset.Zero, Size(w, size.height))
                    drawRect(borderColor, Offset(size.width - w, 0f), Size(w, size.height))
                }
            }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = contentColor)
            Spacer(Modifier.width(Space.s))
        }
        Text(text.uppercase(), style = MonoType.label.copy(color = contentColor), maxLines = 1)
    }
}

private data class Quad(val a: Color, val b: Color, val c: Color, val d: Color)

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(bottom = Space.xs),
            verticalAlignment = Alignment.Bottom,
        ) {
            LabelText(title, Modifier.weight(1f).padding(bottom = Space.s))
            trailing()
        }
        SectionRule()
    }
}

/** One-line explanation plus a single action, used by every empty view. */
@Composable
fun EmptyState(message: String, actionLabel: String?, onAction: (() -> Unit)?, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = Space.xxl), horizontalAlignment = Alignment.Start) {
        if (icon != null) {
            MonoIcon(icon, null, tint = MonoColors.Tertiary, size = 32.dp)
            Spacer(Modifier.height(Space.l))
        }
        Text(message, style = MonoType.bodySmall.copy(color = MonoColors.Secondary))
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Space.l))
            MonoButton(actionLabel, onAction)
        }
    }
}

/** A list row: optional leading glyph, title, caption and trailing content, with ink hover. */
@Composable
fun InkRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    caption: String? = null,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    titleStyle: TextStyle = MonoType.body,
    minHeight: Dp = 48.dp,
    divider: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .inkClickable(onClick = onClick, onLongClick = onLongClick, selected = selected)
                .padding(horizontal = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { leading() }
                Spacer(Modifier.width(Space.m))
            }
            Column(Modifier.weight(1f).padding(vertical = Space.s)) {
                Text(title, style = titleStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (caption != null) Text(caption, style = MonoType.caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            trailing()
        }
        if (divider) Hairline()
    }
}

/** Page icon glyph (emoji or geometric character), or a fallback page icon. */
@Composable
fun PageGlyph(icon: String?, isDatabase: Boolean = false, modifier: Modifier = Modifier, size: Dp = 20.dp, tint: Color = MonoColors.Ink) {
    if (!icon.isNullOrBlank()) {
        Text(icon, modifier, style = MonoType.body.copy(fontSize = (size.value * 0.9f).sp, lineHeight = size.value.sp, color = tint), maxLines = 1)
    } else {
        Icon(if (isDatabase) app.monoworkspace.ui.theme.MonoIcons.Table else app.monoworkspace.ui.theme.MonoIcons.Page, null, modifier.size(size), tint = tint)
    }
}

/** Animated left inset for nested content. */
@Composable
fun indentFor(depth: Int, step: Dp = 24.dp): Dp {
    val target = step * depth
    val anim by animateDpAsState(target, monoTween(Motion.FAST), label = "indent")
    return anim
}
