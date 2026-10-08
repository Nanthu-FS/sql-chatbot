package app.monoworkspace.ui.components

import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.monoworkspace.ui.theme.MonoColors
import app.monoworkspace.ui.theme.MonoIcons
import app.monoworkspace.ui.theme.MonoType
import app.monoworkspace.ui.theme.Motion
import app.monoworkspace.ui.theme.Space
import app.monoworkspace.ui.theme.monoTween

/** Underline-only text field; the rule turns black when focused, red on error. */
@Composable
fun MonoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    error: String? = null,
    warning: String? = null,
    textStyle: TextStyle = MonoType.body,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeDone: (() -> Unit)? = null,
    minLines: Int = 1,
    maxLength: Int = Int.MAX_VALUE,
    password: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ruleColor by animateColorAsState(
        when {
            error != null -> MonoColors.Destructive
            focused -> MonoColors.Ink
            else -> MonoColors.Hairline
        },
        monoTween(Motion.FAST), label = "rule",
    )
    val ruleWidth by animateDpAsState(if (focused) 1.5.dp else 1.dp, monoTween(Motion.FAST), label = "ruleW")
    // Focus draws an ink rule across the hairline from the left.
    val draw by animateFloatAsState(if (focused || error != null) 1f else 0f, monoTween(Motion.MEDIUM), label = "ruleDraw")
    Column(modifier) {
        if (label != null) Text(label, style = MonoType.caption)
        BasicTextField(
            value = value,
            onValueChange = { if (it.length <= maxLength) onValueChange(it) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(top = Space.xs).onPreviewKeyEvent { e ->
                if (singleLine && imeDone != null && e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) {
                    imeDone()
                    true
                } else false
            },
            visualTransformation = if (password) PasswordVisualTransformation('•') else VisualTransformation.None,
            textStyle = textStyle,
            singleLine = singleLine,
            minLines = minLines,
            interactionSource = interaction,
            cursorBrush = SolidColor(MonoColors.Ink),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (imeDone != null) androidx.compose.ui.text.input.ImeAction.Done else androidx.compose.ui.text.input.ImeAction.Default),
            keyboardActions = KeyboardActions(onDone = { imeDone?.invoke() }),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
                    if (value.isEmpty() && placeholder != null) Text(placeholder, style = textStyle.copy(color = MonoColors.Tertiary), maxLines = 1)
                    inner()
                }
            },
        )
        Box(Modifier.fillMaxWidth().height(ruleWidth).background(MonoColors.Hairline)) {
            Box(Modifier.fillMaxWidth(draw).fillMaxHeight().background(ruleColor))
        }
        if (error != null) Text(error, Modifier.padding(top = Space.xs), style = MonoType.caption.copy(color = MonoColors.Destructive))
        else if (warning != null) WarningChip(warning, Modifier.padding(top = Space.xs))
    }
}

/** Non-blocking validation warning. */
@Composable
fun WarningChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.border(1.dp, MonoColors.Destructive).padding(horizontal = 6.dp, vertical = 1.dp),
        style = MonoType.caption.copy(color = MonoColors.Destructive),
        maxLines = 1,
    )
}

/** Square checkbox; the check stroke draws itself in. */
@Composable
fun MonoCheckbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, size: Dp = 20.dp, label: String? = null) {
    val center = remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    val progress by animateFloatAsState(if (checked) 1f else 0f, monoTween(Motion.MEDIUM), label = "check")
    val fill by animateColorAsState(if (checked) MonoColors.Ink else Color.Transparent, monoTween(Motion.FAST), label = "checkFill")
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier.onGloballyPositioned { c -> val p = c.positionInWindow(); center.value = androidx.compose.ui.geometry.Offset(p.x + c.size.width / 2f, p.y + c.size.height / 2f) }
            .then(
                if (onCheckedChange != null) Modifier.minimumInteractiveComponentSize().clickable(interaction, null, role = Role.Checkbox) {
                    // Celebrate at the box itself, not wherever the pointer drifted.
                    if (!checked) app.monoworkspace.ui.theme.fx.FxBus.fire(app.monoworkspace.ui.theme.fx.BurstKind.Complete, center.value)
                    onCheckedChange(!checked)
                }
                else Modifier,
            )
            .semantics {
                stateDescription = if (checked) "Checked" else "Not checked"
                if (label != null) contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        val tint = MonoColors.Tint
        val ink = MonoColors.Ink
        val onInk = MonoColors.OnInk
        Canvas(Modifier.size(size)) {
            val stroke = 1.dp.toPx()
            drawRect(fill)
            if (hovered && !checked) drawRect(tint)
            drawRect(ink, style = Stroke(stroke))
            if (progress > 0f) {
                val path = Path().apply {
                    moveTo(this@Canvas.size.width * 0.22f, this@Canvas.size.height * 0.52f)
                    lineTo(this@Canvas.size.width * 0.42f, this@Canvas.size.height * 0.72f)
                    lineTo(this@Canvas.size.width * 0.80f, this@Canvas.size.height * 0.30f)
                }
                val measure = PathMeasure().apply { setPath(path, false) }
                val partial = Path()
                measure.getSegment(0f, measure.length * progress, partial, true)
                drawPath(partial, onInk, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Square))
            }
        }
    }
}

/** Rectangular switch: a square knob slides across a 1dp outlined track. */
@Composable
fun MonoSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, label: String? = null, enabled: Boolean = true) {
    val knob by animateDpAsState(if (checked) 22.dp else 2.dp, monoTween(Motion.MEDIUM), label = "knob")
    val track by animateColorAsState(if (checked) MonoColors.Ink else MonoColors.Background, monoTween(Motion.FAST), label = "track")
    val knobColor by animateColorAsState(if (checked) MonoColors.OnInk else MonoColors.Ink, monoTween(Motion.FAST), label = "knobColor")
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .semantics {
                stateDescription = if (checked) "On" else "Off"
                if (label != null) contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(44.dp)
                .height(24.dp)
                .background(if (enabled) track else MonoColors.Hairline)
                .border(1.dp, if (enabled) MonoColors.Ink else MonoColors.Tertiary),
        ) {
            Box(Modifier.offset(x = knob, y = 2.dp).size(20.dp).background(knobColor))
        }
    }
}

/** 1dp outlined chip that inverts when selected. */
@Composable
fun MonoChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
    onTrailingClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(
        when {
            selected -> MonoColors.Ink
            hovered -> MonoColors.Tint
            else -> MonoColors.Background
        },
        monoTween(Motion.FAST), label = "chipBg",
    )
    val fg by animateColorAsState(if (selected) MonoColors.OnInk else if (enabled) MonoColors.Ink else MonoColors.Tertiary, monoTween(Motion.FAST), label = "chipFg")
    Row(
        modifier
            .heightIn(min = 36.dp)
            .background(bg)
            .border(1.dp, if (enabled) MonoColors.Ink else MonoColors.Hairline)
            .clickable(interaction, null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, null, Modifier.size(16.dp), tint = fg)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MonoType.bodySmall.copy(color = fg), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) {
            Spacer(Modifier.width(4.dp))
            Icon(
                trailing, "Remove",
                Modifier.size(16.dp).then(if (onTrailingClick != null) Modifier.clickable(onClick = onTrailingClick) else Modifier),
                tint = fg,
            )
        }
    }
}

/** Dropdown field: underline style value plus a flat menu. */
@Composable
fun <T> MonoDropdown(
    value: T?,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select",
    caption: String? = null,
    menuWidth: Dp = 260.dp,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        if (caption != null) Text(caption, style = MonoType.caption)
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .clickable(enabled = enabled) { open = true }
                    .padding(vertical = Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    value?.let(label) ?: placeholder,
                    Modifier.weight(1f),
                    style = MonoType.bodySmall.copy(color = if (value == null || !enabled) MonoColors.Tertiary else MonoColors.Ink),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Icon(MonoIcons.ChevronDown, null, Modifier.size(16.dp), tint = MonoColors.Secondary)
            }
            MonoMenu(open, { open = false }, options.map { o -> MenuItem(label(o), checked = o == value) { onSelect(o) } }, width = menuWidth)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(if (enabled) MonoColors.Ink else MonoColors.Hairline))
    }
}

/** Two or three mutually exclusive options in an outlined strip (e.g. AND / OR). */
@Composable
fun <T> MonoSegmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.border(1.dp, MonoColors.Ink)) {
        options.forEach { o ->
            val on = o == selected
            val bg by animateColorAsState(if (on) MonoColors.Ink else MonoColors.Background, monoTween(Motion.FAST), label = "seg")
            Text(
                label(o),
                Modifier
                    .background(bg)
                    .clickable(role = Role.RadioButton) { onSelect(o) }
                    .padding(horizontal = Space.m, vertical = 6.dp),
                style = MonoType.label.copy(color = if (on) MonoColors.OnInk else MonoColors.Ink),
            )
        }
    }
}

/** A labelled row used in forms and settings. */
@Composable
fun FormRow(title: String, modifier: Modifier = Modifier, caption: String? = null, trailing: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MonoType.body)
                if (caption != null) Text(caption, style = MonoType.caption)
            }
            trailing()
        }
        Hairline()
    }
}
