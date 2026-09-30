package com.spendlens.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spendlens.app.domain.Category
import com.spendlens.app.domain.CurrencyOption
import com.spendlens.app.ui.theme.Spend

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

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = Spend.ink.muted, style: TextStyle = MaterialTheme.typography.labelMedium) {
    Text(text.uppercase(), modifier = modifier, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Small uppercase helper line that wraps instead of cutting off. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier, style = MaterialTheme.typography.labelMedium, color = Spend.ink.faint)
}

/** Two small squares — the reference's "• •" marker. */
@Composable
fun Dots(modifier: Modifier = Modifier, color: Color = Spend.ink.text) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(2) { Box(Modifier.size(3.dp).background(color)) }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Spend.ink.line) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
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

/** Uppercase statement with an ink part and a grey tail: "SPENT" + " THIS MONTH". */
@Composable
fun Statement(ink: String, tail: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.headlineLarge) {
    val colors = Spend.ink
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = colors.text)) { append(ink.uppercase()) }
            withStyle(SpanStyle(color = colors.faint)) { append(tail.uppercase()) }
        },
        modifier = modifier,
        style = style,
    )
}

/** Amount with a quiet currency sign and decimals: ₹ 12,400 .50 */
@Composable
fun AmountText(amountMinor: Long, currency: CurrencyOption, style: TextStyle, modifier: Modifier = Modifier, color: Color = Spend.ink.text) {
    val formatted = currency.format(amountMinor).removePrefix(currency.symbol)
    val whole = formatted.substringBefore('.')
    val decimals = if ('.' in formatted) "." + formatted.substringAfter('.') else ""
    val colors = Spend.ink
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = colors.faint, fontSize = style.fontSize * 0.55f)) { append(currency.symbol.trim()) }
            append(" ")
            withStyle(SpanStyle(color = color)) { append(whole) }
            withStyle(SpanStyle(color = colors.faint)) { append(decimals) }
        },
        modifier = modifier,
        style = style,
        maxLines = 1,
    )
}

/**
 * Section header: "(02)  RHYTHM ........... ACTION", hairline under it.
 */
@Composable
fun SectionHeader(number: Int, title: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    val colors = Spend.ink
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(index(number), color = colors.faint)
            Spacer(Modifier.width(10.dp))
            Label(title, color = colors.text, modifier = Modifier.weight(1f))
            trailing?.invoke(this)
        }
        Spacer(Modifier.height(10.dp))
        Hairline()
    }
}

/** A titled block of the dashboard. With glass on, it becomes a frosted panel. */
@Composable
fun Section(number: Int, title: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val glassy = LocalGlass.current > 0.01f
    Column(
        modifier
            .fillMaxWidth()
            .then(if (glassy) Modifier.glass().padding(16.dp) else Modifier),
    ) {
        SectionHeader(number, title, trailing = trailing)
        Spacer(Modifier.height(18.dp))
        content()
    }
}

/**
 * "[ TEXT ]" button. Filled = solid ink block (primary action); otherwise just the bracketed label.
 */
@Composable
fun BracketButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    color: Color = Spend.ink.text,
) {
    val colors = Spend.ink
    val content = if (filled) colors.inverse else color
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.35f)
            .then(if (filled) Modifier.background(color, RoundedCornerShape(2.dp)) else Modifier)
            .pressable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = if (filled) 20.dp else 4.dp, vertical = if (filled) 18.dp else 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = content, strokeWidth = 1.5.dp, modifier = Modifier.size(16.dp))
        } else {
            Text("[ ${text.uppercase()} ]", style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
        }
    }
}

/** Text chip for pickers: selected is bracketed ink, others grey. */
@Composable
fun TextChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Spend.ink
    val color by animateColorAsState(if (selected) colors.text else colors.faint, label = "chip")
    val border by animateColorAsState(if (selected) colors.text else colors.line, label = "chipBorder")
    Box(
        modifier
            .border(1.dp, border, RoundedCornerShape(2.dp))
            .pressable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
    }
}
