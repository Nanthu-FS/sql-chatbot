package com.spendlens.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import com.spendlens.app.domain.Category
import com.spendlens.app.domain.CurrencyOption
import com.spendlens.app.ui.theme.CardStyle
import com.spendlens.app.ui.theme.ControlStyle
import com.spendlens.app.ui.theme.Look
import com.spendlens.app.ui.theme.Spend
import com.spendlens.app.ui.theme.contentOn

/** Currency chosen in Settings, available everywhere below the root. */
val LocalCurrency = staticCompositionLocalOf { CurrencyOption.INR }

/** Screenshots are shown in black & white so they sit quietly in the layout. */
val Grayscale: ColorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** "(01)" style index used by numbered lists and section headers. */
fun index(n: Int): String = "(" + n.toString().padStart(2, '0') + ")"

val Category.short: String
    get() = when (this) {
        Category.FOOD -> "Food"
        Category.BILLS -> "Bills"
        Category.HOUSING -> "Home"
        else -> label
    }

/** Capitals in the looks that use them, as written in the others. */
@Composable
fun caps(text: String): String = Spend.look.caps(text)

/** Shape for buttons and chips in the current look. */
@Composable
fun controlShape(): Shape = RoundedCornerShape(Spend.look.controlRadius.coerceAtMost(100.dp))

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = Spend.ink.muted, style: TextStyle = MaterialTheme.typography.labelMedium) {
    Text(caps(text), modifier = modifier, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Small helper line that wraps instead of cutting off. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(caps(text), modifier = modifier, style = MaterialTheme.typography.labelMedium, color = Spend.ink.faint)
}

/** Two small squares — the reference's "• •" marker. */
@Composable
fun Dots(modifier: Modifier = Modifier, color: Color = Spend.ink.text) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(2) { Box(Modifier.size(3.dp).background(color)) }
    }
}

/** One-pixel rule; dashed in the receipt look. */
@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Spend.ink.line) {
    if (Spend.look.dashed) {
        Canvas(modifier.fillMaxWidth().height(1.dp)) {
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
        }
    } else {
        Box(modifier.fillMaxWidth().height(1.dp).background(color))
    }
}

/** Solid offset block behind the element: the neo-brutal shadow. */
fun Modifier.hardShadow(color: Color, offset: Dp): Modifier = drawBehind {
    val o = offset.toPx()
    drawRect(color, topLeft = Offset(o, o), size = Size(size.width, size.height))
}

/** Tiny crop marks at the four corners, like a print frame. */
fun Modifier.cornerMarks(color: Color, length: Dp = 6.dp, inset: Dp = 0.dp): Modifier = drawWithContent {
    drawContent()
    val l = length.toPx()
    val i = inset.toPx()
    val s = 1.dp.toPx()
    val w = size.width
    val h = size.height
    listOf(Offset(i, i) to Offset(1f, 1f), Offset(w - i, i) to Offset(-1f, 1f), Offset(i, h - i) to Offset(1f, -1f), Offset(w - i, h - i) to Offset(-1f, -1f))
        .forEach { (c, d) ->
            drawLine(color, c, Offset(c.x + l * d.x, c.y), s)
            drawLine(color, c, Offset(c.x, c.y + l * d.y), s)
        }
}

/** Statement with an ink part and a grey tail: "SPENT" + " THIS MONTH". */
@Composable
fun Statement(ink: String, tail: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.headlineLarge) {
    val colors = Spend.ink
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = colors.text)) { append(caps(ink)) }
            withStyle(SpanStyle(color = colors.faint)) { append(caps(tail)) }
        },
        modifier = modifier,
        style = style,
    )
}

/** Big figures use the look's number face (dot matrix, typewriter, grotesk…). */
@Composable
fun numberStyle(style: TextStyle): TextStyle {
    val look = Spend.look
    return style.merge(TextStyle(fontFamily = look.numbers, fontWeight = look.numberWeight, letterSpacing = look.numberTracking.em))
}

/** Amount with a quiet currency sign and decimals: ₹ 12,400 .50 */
@Composable
fun AmountText(amountMinor: Long, currency: CurrencyOption, style: TextStyle, modifier: Modifier = Modifier, color: Color = Spend.ink.text) {
    val formatted = currency.format(amountMinor).removePrefix(currency.symbol)
    val whole = formatted.substringBefore('.')
    val decimals = if ('.' in formatted) "." + formatted.substringAfter('.') else ""
    val colors = Spend.ink
    val numbers = numberStyle(style)
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = colors.faint, fontSize = 0.55.em, fontFamily = style.fontFamily, fontWeight = FontWeight.Normal, letterSpacing = 0.em)) {
            append(currency.symbol.trim())
        }
        // No-break space: the figure must never wrap away from its symbol.
        append("\u00A0")
        withStyle(SpanStyle(color = color)) { append(whole) }
        withStyle(SpanStyle(color = colors.faint)) { append(decimals) }
    }
    // Wide display faces overflow narrow screens on big totals, so shrink the whole figure to fit.
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer()
        val maxWidth = constraints.maxWidth
        val bounded = constraints.hasBoundedWidth
        val scale = remember(text, numbers, maxWidth, bounded) {
            if (!bounded) {
                1f
            } else {
                val width = measurer.measure(text, numbers, softWrap = false, maxLines = 1).size.width
                if (width <= maxWidth) 1f else (maxWidth.toFloat() / width * 0.98f).coerceAtLeast(0.3f)
            }
        }
        Text(
            text,
            style = if (scale == 1f) numbers else numbers.copy(fontSize = numbers.fontSize * scale, lineHeight = if (numbers.lineHeight.isSpecified) numbers.lineHeight * scale else numbers.lineHeight),
            softWrap = false,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

/**
 * Section header: "(02)  RHYTHM ........... ACTION" with a rule under it in the print looks;
 * just the title in the card looks.
 */
@Composable
fun SectionHeader(number: Int, title: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    val colors = Spend.ink
    val look = Spend.look
    val ruled = look.card == CardStyle.PRINT || look.card == CardStyle.PAPER
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (look.numbered) {
                Label(index(number), color = colors.faint)
                Spacer(Modifier.width(10.dp))
            }
            Label(
                if (look.card == CardStyle.TERMINAL) "> " + title.lowercase() else title,
                color = colors.text,
                modifier = Modifier.weight(1f),
                style = if (look.upper) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )
            trailing?.invoke(this)
        }
        if (ruled) {
            Spacer(Modifier.height(10.dp))
            Hairline()
        }
    }
}

/** The frame a dashboard section sits in, per look. */
fun Modifier.sectionFrame(): Modifier = composed {
    val look: Look = Spend.look
    val ink = Spend.ink
    val glass = LocalGlass.current
    when (look.card) {
        CardStyle.PRINT -> if (glass > 0.01f) glass().padding(16.dp) else this
        CardStyle.PAPER -> this
            .drawBehind {
                drawLine(ink.lineStrong, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
            }
            .padding(top = 18.dp)
        CardStyle.TILE -> background(ink.surface, RoundedCornerShape(look.radius)).padding(18.dp)
        CardStyle.RULED -> this
            .drawBehind { drawRect(ink.lineStrong, size = Size(size.width, 2.dp.toPx())) }
            .padding(top = 14.dp)
        CardStyle.OUTLINE -> border(1.dp, ink.line, RoundedCornerShape(look.radius)).padding(16.dp)
        CardStyle.SOFT -> background(ink.raised, RoundedCornerShape(look.radius)).padding(16.dp)
        CardStyle.BRUTAL -> hardShadow(ink.text, 6.dp).background(ink.raised).border(3.dp, ink.text).padding(16.dp)
        CardStyle.TERMINAL -> this
            .drawBehind {
                drawLine(ink.lineStrong, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
            }
            .padding(top = 14.dp)
        CardStyle.GLASS -> {
            val shape = RoundedCornerShape(look.radius)
            val fill = if (ink.isDark) Color.White.copy(alpha = 0.05f + 0.06f * glass) else Color.White.copy(alpha = 0.45f + 0.3f * glass)
            val edge = if (ink.isDark) Color.White.copy(alpha = 0.18f) else ink.line
            background(fill, shape).border(1.dp, edge, shape).padding(18.dp)
        }
        CardStyle.RISO -> hardShadow(ink.accent, 5.dp).background(ink.raised).border(2.dp, ink.lineStrong).padding(16.dp)
        CardStyle.BEVEL -> bevel().padding(12.dp)
        CardStyle.BLUEPRINT -> border(1.5.dp, ink.lineStrong).padding(14.dp)
    }
}

/** Win95-style bevel: light top-left edge, dark bottom-right; [pressed] flips it (sunken fields, held buttons). */
fun Modifier.bevel(pressed: Boolean = false, fill: Color? = null): Modifier = composed {
    val ink = Spend.ink
    val light = if (ink.isDark) androidx.compose.ui.graphics.lerp(ink.surface, Color.White, 0.25f) else Color.White
    val dark = ink.lineStrong
    val (topLeft, bottomRight) = if (pressed) dark to light else light to dark
    val bg = fill ?: ink.surface
    drawBehind {
        val s = 2.dp.toPx()
        drawRect(bg)
        drawRect(topLeft, size = Size(size.width, s))
        drawRect(topLeft, size = Size(s, size.height))
        drawRect(bottomRight, Offset(0f, size.height - s), Size(size.width, s))
        drawRect(bottomRight, Offset(size.width - s, 0f), Size(s, size.height))
    }
}

/** Navy-to-blue title bar of a retro window, with the minimise/close boxes. */
@Composable
fun TitleBar(title: String, modifier: Modifier = Modifier) {
    val ink = Spend.ink
    val start = ink.accent
    val end = androidx.compose.ui.graphics.lerp(ink.accent, Color(0xFF1084D0), 0.7f)
    Row(
        modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(start, end)))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = contentOn(start), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        listOf("_", "x").forEach {
            Spacer(Modifier.width(3.dp))
            Box(Modifier.size(width = 16.dp, height = 14.dp).bevel(), contentAlignment = Alignment.Center) {
                Text(it, style = MaterialTheme.typography.labelSmall, color = ink.text)
            }
        }
    }
}

/** A bevelled window with a title bar; the body gets the usual padding. */
@Composable
fun RetroWindow(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().bevel().padding(3.dp)) {
        TitleBar(title)
        Column(Modifier.fillMaxWidth().padding(12.dp), content = content)
    }
}

/** Blueprint frame: a ruled box with its label set into the top line ("FIG. 02 — RHYTHM"). */
@Composable
fun NotchFrame(label: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val ink = Spend.ink
    Box(modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.fillMaxWidth().border(1.5.dp, ink.lineStrong).padding(start = 14.dp, end = 14.dp, top = 20.dp, bottom = 14.dp), content = content)
        Label(
            label,
            color = ink.text,
            modifier = Modifier.padding(start = 12.dp).offset(y = (-7).dp).background(ink.canvas).padding(horizontal = 6.dp),
        )
    }
}

/** A titled block of the dashboard, framed the way the current look frames things. */
@Composable
fun Section(number: Int, title: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    when (Spend.look.card) {
        CardStyle.BEVEL -> return RetroWindow(title.lowercase().replace(' ', '_') + ".txt", modifier) {
            if (trailing != null) Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.End, content = trailing)
            content()
        }
        CardStyle.BLUEPRINT -> return NotchFrame("FIG. ${index(number).trim('(', ')')} — ${title.uppercase()}", modifier) {
            if (trailing != null) Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.End, content = trailing)
            content()
        }
        else -> Unit
    }
    Column(modifier.fillMaxWidth().sectionFrame()) {
        SectionHeader(number, title, trailing = trailing)
        Spacer(Modifier.height(18.dp))
        content()
    }
}

/**
 * The look's button: "[ TEXT ]" in print looks, a pill, a bordered block with a hard shadow, or bold
 * type. Filled = the primary action, in the accent colour.
 */
@Composable
fun BracketButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    color: Color = Color.Unspecified,
) {
    val colors = Spend.ink
    val look = Spend.look
    val shape = controlShape()
    val fill = if (color.isSpecified()) color else colors.accent
    val content = when {
        filled -> if (color.isSpecified()) contentOn(color) else colors.onAccent
        color.isSpecified() -> color
        else -> colors.text
    }
    val frame = when {
        filled && look.control == ControlStyle.BLOCK -> Modifier.hardShadow(colors.text, 4.dp).background(fill).border(3.dp, colors.text)
        look.control == ControlStyle.BEVEL -> if (filled) Modifier.border(1.dp, colors.text).bevel(fill = fill) else Modifier.bevel()
        filled -> Modifier.background(fill, shape)
        look.control == ControlStyle.PILL -> Modifier.background(colors.ghost, shape)
        look.control == ControlStyle.BLOCK -> Modifier.hardShadow(colors.text, 3.dp).background(colors.raised).border(2.dp, colors.text)
        else -> Modifier
    }
    val padH = when {
        filled -> 20.dp
        look.control == ControlStyle.PILL || look.control == ControlStyle.BLOCK || look.control == ControlStyle.BEVEL -> 14.dp
        else -> 4.dp
    }
    val padV = if (filled) 18.dp else 10.dp
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.35f)
            .pressable(enabled = enabled && !loading, onClick = onClick)
            .then(frame)
            .padding(horizontal = padH, vertical = padV),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = content, strokeWidth = 1.5.dp, modifier = Modifier.size(16.dp))
        } else {
            val label = when (look.control) {
                ControlStyle.BRACKET -> "[ ${text.uppercase()} ]"
                else -> caps(text)
            }
            val style = if (look.control == ControlStyle.SOLID) MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.labelLarge
            Text(label, style = style, color = content, maxLines = 1)
        }
    }
}

private fun Color.isSpecified(): Boolean = this != Color.Unspecified

/** Chip for pickers. Selected: outlined ink in print looks, accent fill in the others. */
@Composable
fun TextChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Spend.ink
    val look = Spend.look
    val shape = controlShape()
    val filledStyle = look.control == ControlStyle.PILL || look.control == ControlStyle.BLOCK
    val color by animateColorAsState(
        when {
            selected && filledStyle -> colors.onAccent
            selected -> colors.text
            else -> if (filledStyle || look.control == ControlStyle.BEVEL) colors.muted else colors.faint
        },
        label = "chip",
    )
    val border by animateColorAsState(if (selected) colors.text else colors.line, label = "chipBorder")
    val bg by animateColorAsState(if (selected && filledStyle) colors.accent else if (filledStyle) colors.ghost else Color.Transparent, label = "chipBg")
    val frame = when (look.control) {
        ControlStyle.BLOCK -> Modifier.background(bg).border(2.dp, colors.text)
        ControlStyle.PILL -> Modifier.background(bg, shape)
        ControlStyle.BEVEL -> Modifier.bevel(pressed = selected)
        else -> Modifier.border(1.dp, border, shape)
    }
    Box(
        modifier
            .pressable(onClick = onClick)
            .then(frame)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(caps(text), style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
    }
}
