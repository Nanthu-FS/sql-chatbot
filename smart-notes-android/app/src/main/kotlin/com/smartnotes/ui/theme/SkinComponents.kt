package com.smartnotes.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- Modifiers ----------

fun SkinTokens.shape(): Shape = if (corner == 0.dp) RectangleShape else RoundedCornerShape(corner)

/** Solid offset shadow (Brutalist / Retro). No-op for skins without one. */
fun Modifier.hardShadow(t: SkinTokens): Modifier =
    if (t.shadowOffset == 0.dp) this
    else drawBehind {
        val o = t.shadowOffset.toPx()
        val r = t.corner.toPx()
        drawRoundRect(t.shadowColor, topLeft = Offset(o, o), size = size, cornerRadius = CornerRadius(r, r))
    }

fun Modifier.skinBorder(t: SkinTokens, color: Color = t.border): Modifier =
    if (t.borderWidth == 0.dp) this else border(t.borderWidth, color, t.shape())

/**
 * Background plus pattern. The pattern is rendered once into a small tile and repeated
 * by a shader, so it costs one draw call per frame instead of hundreds of dots or lines.
 */
fun Modifier.skinBackdrop(t: SkinTokens): Modifier = background(t.background).drawWithCache {
    val brush = when (t.backdrop) {
        Backdrop.PLAIN -> null
        Backdrop.DOTS -> tileBrush(20.dp.toPx()) { s ->
            drawCircle(Color.Black.copy(alpha = 0.18f), 1.3.dp.toPx(), Offset(s / 2, s / 2))
        }
        Backdrop.SCANLINES -> tileBrush(3.dp.toPx()) { s ->
            drawLine(Color.White.copy(alpha = 0.025f), Offset(0f, 0f), Offset(s, 0f))
        }
    }
    onDrawBehind { if (brush != null) drawRect(brush) }
}

private fun androidx.compose.ui.draw.CacheDrawScope.tileBrush(
    sizePx: Float,
    draw: androidx.compose.ui.graphics.drawscope.DrawScope.(Float) -> Unit,
): ShaderBrush {
    val px = sizePx.toInt().coerceAtLeast(1)
    val tile = ImageBitmap(px, px)
    CanvasDrawScope().draw(this, layoutDirection, Canvas(tile), Size(px.toFloat(), px.toFloat())) { draw(px.toFloat()) }
    return ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
}

private fun String.titleCase(t: SkinTokens) = if (t.upperCaseTitles) uppercase() else this

// ---------- Scaffold ----------

@Composable
fun SkinScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalSkin.current
    CompositionLocalProvider(
        LocalContentColor provides t.onBackground,
        LocalTextStyle provides TextStyle(fontFamily = t.body, fontSize = 15.sp, color = t.onBackground),
    ) {
        Column(modifier.fillMaxSize().skinBackdrop(t).systemBarsPadding().imePadding()) {
            SkinHeader(title, subtitle, onBack, actions)
            Column(Modifier.weight(1f).fillMaxWidth(), content = content)
            bottomBar()
        }
    }
}

@Composable
private fun SkinHeader(title: String, subtitle: String?, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit) {
    val t = LocalSkin.current
    val back: @Composable () -> Unit = {
        if (onBack != null) SkinIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
    }
    when (t.skin) {
        Skin.SWISS -> Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
                back()
                Column(Modifier.weight(1f).padding(start = if (onBack != null) 8.dp else 0.dp)) {
                    if (subtitle != null) SkinLabel(subtitle)
                    Text(title, fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(t.border))
        }
        Skin.RETRO -> Row(
            Modifier.fillMaxWidth().background(t.bar).drawBehind {
                drawLine(t.border, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx())
            }.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            back()
            Text("◆ $title", color = t.onBar, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = t.onBar, fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), content = actions)
        }
        Skin.TERMINAL -> Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                back()
                Column(Modifier.weight(1f).padding(start = 6.dp)) {
                    Text("smartnotes@phone:~/${title.lowercase().replace(' ', '_')}", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) Text(subtitle, color = t.muted, fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), content = actions)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(t.muted.copy(alpha = 0.4f)))
        }
        else -> Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            back()
            Column(Modifier.weight(1f).padding(start = if (onBack != null) 8.dp else 4.dp)) {
                Text(title.titleCase(t), fontFamily = t.display, fontSize = 28.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, color = t.muted, fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
}

// ---------- Text ----------

@Composable
fun SkinLabel(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    val t = LocalSkin.current
    val shown = if (t.skin == Skin.TERMINAL) "# $text" else text.uppercase()
    Text(shown, modifier = modifier, fontFamily = t.label, fontSize = 11.sp, letterSpacing = 1.sp, color = color ?: t.muted, fontWeight = FontWeight.Medium)
}

// ---------- Cards ----------

@Composable
fun SkinCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    onClick: (() -> Unit)? = null,
    fill: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalSkin.current
    val press = rememberPress()
    val click = if (onClick != null) Modifier.pressClick(press, role = null, onClick = onClick) else Modifier
    val contentColor = when (t.cardStyle) {
        CardStyle.RULED, CardStyle.PROMPT -> t.onBackground
        else -> t.onSurface
    }
    CompositionLocalProvider(
        LocalContentColor provides contentColor,
        LocalTextStyle provides TextStyle(fontFamily = t.cardBody, fontSize = 15.sp, color = contentColor),
    ) {
        val modifier = modifier.pressFeedback(press, 0.dp)
        when (t.cardStyle) {
            CardStyle.RULED -> Column(
                modifier.fillMaxWidth()
                    .then(if (fill != null) Modifier.background(fill) else Modifier)
                    .drawBehind { drawLine(Color(0xFFDDDDDD), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                    .then(click).padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (title != null) Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                content()
            }
            CardStyle.BOXED -> Column(
                modifier.fillMaxWidth().hardShadow(t).background(fill ?: t.surface).skinBorder(t).then(click).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (title != null) Text(title.uppercase(), fontWeight = FontWeight.Black, fontSize = 16.sp)
                content()
            }
            CardStyle.WINDOW -> Column(modifier.fillMaxWidth().hardShadow(t).background(fill ?: t.surface).skinBorder(t).then(click)) {
                if (title != null) WindowTitleBar(title)
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
            }
            CardStyle.INDEX -> Column(
                modifier.fillMaxWidth().shadow(8.dp, t.shape()).background(fill ?: t.surface, t.shape())
                    .drawBehind {
                        val step = 28.dp.toPx()
                        var y = step + 8.dp.toPx()
                        while (y < size.height) {
                            drawLine(Color(0xFFD7CDB3), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                            y += step
                        }
                    }
                    .clip(t.shape()).then(click).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (title != null) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Box(Modifier.fillMaxWidth().height(2.dp).background(t.accent))
                }
                content()
            }
            CardStyle.PROMPT -> Column(
                modifier.fillMaxWidth()
                    .drawBehind { drawLine(t.accent, Offset(0f, 0f), Offset(0f, size.height), 2.dp.toPx()) }
                    .then(click).padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (title != null) Text("> $title", fontWeight = FontWeight.Bold)
                content()
            }
        }
    }
}

@Composable
private fun WindowTitleBar(title: String) {
    val t = LocalSkin.current
    Box(
        Modifier.fillMaxWidth().height(26.dp).drawBehind {
            val step = 3.dp.toPx()
            var y = 4.dp.toPx()
            while (y < size.height - 3.dp.toPx()) {
                drawLine(Color.Black, Offset(4.dp.toPx(), y), Offset(size.width - 4.dp.toPx(), y), 1.dp.toPx())
                y += step
            }
            drawLine(t.border, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx())
        },
        contentAlignment = Alignment.Center,
    ) {
        Text(title, Modifier.background(t.surface).padding(horizontal = 8.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Emphasised card for resurfaced notes, meeting prompts and errors. */
@Composable
fun SkinBanner(label: String, title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val t = LocalSkin.current
    when (t.skin) {
        Skin.SWISS -> Column(
            modifier.fillMaxWidth().background(t.accent).then(if (onClick != null) Modifier.clickable(interactionSource = null, indication = null, onClick = onClick) else Modifier).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SkinLabel(label, color = Color.White)
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            if (subtitle != null) Text(subtitle, color = Color.White, fontSize = 13.sp)
        }
        Skin.TERMINAL -> Column(modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(interactionSource = null, indication = null, onClick = onClick) else Modifier).padding(vertical = 4.dp)) {
            Text("! $title" + (subtitle?.let { "  ($it)" } ?: ""), color = Color(0xFFFF7A59))
        }
        Skin.RETRO -> SkinCard(modifier, title = label.lowercase().replaceFirstChar { it.uppercase() }, onClick = onClick, fill = t.highlight) {
            Text(title, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp)
        }
        Skin.ROLODEX -> SkinCard(modifier, onClick = onClick) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold)
                    if (subtitle != null) Text(subtitle, fontSize = 13.sp)
                }
                Text(
                    label.uppercase(), color = t.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.border(2.dp, t.accent).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Skin.BRUTAL -> SkinCard(modifier, onClick = onClick, fill = t.highlight) {
            SkinLabel(label, color = Color.Black)
            Text(title.uppercase(), fontWeight = FontWeight.Black, fontSize = 17.sp)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp)
        }
    }
}

// ---------- Controls ----------

@Composable
fun SkinButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true) {
    val t = LocalSkin.current
    val shape = when (t.skin) {
        Skin.ROLODEX -> RoundedCornerShape(12.dp)
        Skin.RETRO -> if (primary) RoundedCornerShape(8.dp) else RectangleShape
        else -> t.shape()
    }
    val bg = when {
        !primary -> if (t.skin == Skin.ROLODEX || t.skin == Skin.TERMINAL) Color.Transparent else t.surface
        else -> if (t.skin == Skin.RETRO) t.surface else t.accent
    }
    val fg = when {
        !primary -> if (t.skin == Skin.ROLODEX) t.onBackground else if (t.skin == Skin.TERMINAL) t.accent else t.onSurface
        else -> if (t.skin == Skin.RETRO) t.onSurface else t.onAccent
    }
    val borderColor = if (t.skin == Skin.ROLODEX) t.onBackground else t.border
    val borderWidth = when {
        t.skin == Skin.ROLODEX -> if (primary) 0.dp else 2.dp
        t.skin == Skin.RETRO && primary -> 3.dp
        t.skin == Skin.SWISS && primary -> 0.dp
        else -> t.borderWidth
    }
    val press = rememberPress()
    val shadow = if (enabled) t.shadowOffset else 0.dp
    Box(
        modifier.heightIn(min = 48.dp)
            .pressFeedback(press, shadow)
            .then(
                if (shadow > 0.dp && !press.pressed) Modifier.hardShadow(t.copy(corner = if (shape is RoundedCornerShape) 8.dp else 0.dp))
                else Modifier,
            )
            .background(bg, shape)
            .then(if (borderWidth > 0.dp) Modifier.border(borderWidth, borderColor, shape) else Modifier)
            .clip(shape)
            .pressClick(press, enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (t.upperCaseTitles || t.skin == Skin.SWISS) text.uppercase() else text,
            color = if (enabled) fg else fg.copy(alpha = 0.4f),
            fontFamily = t.label, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.5.sp,
        )
    }
}

@Composable
fun SkinIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    val t = LocalSkin.current
    val shape = when (t.skin) {
        Skin.ROLODEX -> RoundedCornerShape(12.dp)
        else -> t.shape()
    }
    val bg = if (primary) t.accent else when (t.skin) {
        Skin.BRUTAL, Skin.SWISS -> t.surface
        Skin.RETRO -> t.bar
        else -> Color.Transparent
    }
    val fg = if (primary) t.onAccent else when (t.skin) {
        Skin.RETRO -> t.onBar
        Skin.BRUTAL, Skin.SWISS -> t.onSurface
        else -> t.onBackground
    }
    val bordered = !primary && (t.skin == Skin.SWISS || t.skin == Skin.BRUTAL || t.skin == Skin.TERMINAL)
    val press = rememberPress()
    val shadow = if (t.skin == Skin.BRUTAL) 3.dp else 0.dp
    Box(
        modifier.size(48.dp)
            .pressFeedback(press, shadow)
            .then(if (shadow > 0.dp && !press.pressed) Modifier.hardShadow(t.copy(shadowOffset = shadow)) else Modifier)
            .background(bg, shape)
            .then(if (bordered) Modifier.border(t.borderWidth, t.border, shape) else Modifier)
            .clip(shape)
            .pressClick(press, label = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = fg, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun SkinTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    prefix: String? = null,
    minHeight: androidx.compose.ui.unit.Dp = 52.dp,
    textStyle: TextStyle? = null,
    bare: Boolean = false,
    password: Boolean = false,
    onSubmit: (() -> Unit)? = null,
) {
    val t = LocalSkin.current
    val fieldBg = when (t.skin) {
        Skin.TERMINAL, Skin.ROLODEX -> Color.Transparent
        else -> t.surface
    }
    val fg = if (fieldBg == Color.Transparent) t.onBackground else t.onSurface
    val border = if (t.skin == Skin.ROLODEX) t.onBackground else t.border
    val shape = if (t.skin == Skin.ROLODEX) RoundedCornerShape(12.dp) else t.shape()
    val shownPrefix = prefix ?: if (t.skin == Skin.TERMINAL) "$" else null
    val style = (textStyle ?: TextStyle(fontSize = 15.sp)).merge(TextStyle(fontFamily = textStyle?.fontFamily ?: t.body, color = fg))
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = style,
        cursorBrush = SolidColor(t.accent),
        modifier = modifier,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            imeAction = if (onSubmit != null) ImeAction.Send else ImeAction.Default,
            keyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
        ),
        keyboardActions = KeyboardActions(onSend = { onSubmit?.invoke() }),
        decorationBox = { inner ->
            Row(
                (if (bare) Modifier else Modifier.heightIn(min = minHeight)
                    .then(if (t.skin == Skin.BRUTAL) Modifier.hardShadow(t.copy(shadowOffset = 4.dp)) else Modifier)
                    .background(fieldBg, shape)
                    .border(if (t.borderWidth == 0.dp) 2.dp else t.borderWidth, border, shape)
                    .padding(horizontal = 14.dp, vertical = 10.dp)),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            ) {
                if (shownPrefix != null) {
                    Text(shownPrefix, color = if (t.skin == Skin.TERMINAL) t.muted else t.accent, fontFamily = t.label, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = style.copy(color = fg.copy(alpha = 0.45f)))
                    inner()
                }
            }
        },
    )
}

@Composable
fun SkinChip(text: String, onClick: () -> Unit, selected: Boolean = false) {
    val t = LocalSkin.current
    val shape = when (t.skin) {
        Skin.ROLODEX -> RoundedCornerShape(8.dp, 8.dp, 0.dp, 0.dp)
        Skin.SWISS, Skin.BRUTAL, Skin.RETRO, Skin.TERMINAL -> t.shape()
    }
    val bg = when {
        selected -> t.accent
        t.skin == Skin.ROLODEX -> t.highlight
        t.skin == Skin.TERMINAL -> Color.Transparent
        else -> t.surface
    }
    val fg = when {
        selected -> t.onAccent
        t.skin == Skin.TERMINAL -> t.accent
        t.skin == Skin.ROLODEX -> t.onSurface
        else -> t.onSurface
    }
    val press = rememberPress()
    Box(
        Modifier.heightIn(min = 40.dp).pressFeedback(press, 0.dp).background(bg, shape)
            .then(if (t.borderWidth > 0.dp) Modifier.border(if (t.skin == Skin.BRUTAL) 2.dp else 1.dp, t.border, shape) else Modifier)
            .clip(shape).pressClick(press, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontFamily = t.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SkinSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val t = LocalSkin.current
    val shape = if (t.skin == Skin.ROLODEX) RoundedCornerShape(12.dp) else t.shape()
    Row(
        modifier.fillMaxWidth().hardShadow(t.copy(shadowOffset = if (t.shadowOffset > 0.dp) 4.dp else 0.dp))
            .background(if (t.skin == Skin.TERMINAL || t.skin == Skin.ROLODEX) Color.Transparent else t.surface, shape)
            .border(if (t.borderWidth == 0.dp) 2.dp else t.borderWidth, if (t.skin == Skin.ROLODEX) t.onBackground else t.border, shape)
            .clip(shape),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp)
                    .background(if (on) t.accent else Color.Transparent)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                    ) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label.uppercase(), fontFamily = t.label, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    color = if (on) t.onAccent else if (t.skin == Skin.TERMINAL || t.skin == Skin.ROLODEX) t.onBackground else t.onSurface,
                )
            }
        }
    }
}
