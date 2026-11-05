package com.pulse.tracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pulse.tracker.ui.theme.CardStyle
import com.pulse.tracker.ui.theme.CardTint
import com.pulse.tracker.ui.theme.LocalMuted
import com.pulse.tracker.ui.theme.ThemeId
import com.pulse.tracker.ui.theme.pulse
import kotlin.math.PI
import kotlin.math.max

// ---------------------------------------------------------------- text

@Composable
fun PText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 14.sp,
    weight: FontWeight? = null,
    color: Color = Color.Unspecified,
    display: Boolean = false,
    italic: Boolean = false,
    align: TextAlign? = null,
    tracking: Float? = null,
    maxLines: Int = Int.MAX_VALUE,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    val t = pulse
    Text(
        text = text,
        modifier = modifier,
        color = if (color == Color.Unspecified) LocalContentColor.current else color,
        fontSize = size,
        fontWeight = weight ?: if (display) t.displayWeight else FontWeight.Normal,
        fontFamily = if (display) t.display else t.body,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        letterSpacing = (tracking ?: if (display) t.displayTracking else 0f).em,
        textAlign = align,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        lineHeight = lineHeight,
    )
}

/** Small caption. Upper-cased and tracked in themes that use editorial labels. */
@Composable
fun PLabel(text: String, modifier: Modifier = Modifier, color: Color = LocalMuted.current, size: TextUnit = 12.sp) {
    val t = pulse
    PText(
        text = if (t.upperLabels) text.uppercase() else text,
        modifier = modifier,
        size = if (t.upperLabels) (size.value - 1f).sp else size,
        weight = if (t.upperLabels) FontWeight.Bold else FontWeight.Medium,
        color = color,
        tracking = t.labelTracking,
        maxLines = 1,
    )
}

/** Section heading in the theme's voice. */
@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val t = pulse
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        when (t.id) {
            ThemeId.PAPER, ThemeId.NOIR -> PText(title, size = 24.sp, display = true, italic = true, modifier = Modifier.weight(1f))
            ThemeId.SWISS -> Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(t.c.accent))
                Spacer(Modifier.width(8.dp))
                PText(title.uppercase(), size = 11.sp, weight = FontWeight.Bold, tracking = 0.12f)
            }
            ThemeId.NEON, ThemeId.BLUEPRINT -> PText("// " + title.uppercase(), size = 12.sp, color = t.c.muted, tracking = 0.1f, modifier = Modifier.weight(1f))
            ThemeId.BRUTAL -> PText(title.uppercase(), size = 15.sp, weight = FontWeight.ExtraBold, tracking = 0.04f, modifier = Modifier.weight(1f))
            ThemeId.PIXEL -> PText("▶ " + title.uppercase(), size = 15.sp, weight = FontWeight.Bold, tracking = 0.04f, modifier = Modifier.weight(1f))
            ThemeId.RETRO -> PText(title, size = 22.sp, display = true, modifier = Modifier.weight(1f))
            else -> PText(title, size = 16.sp, weight = if (t.id == ThemeId.MATCHA) FontWeight.ExtraBold else FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
        if (action != null && onAction != null) {
            PText(
                if (t.upperLabels) action.uppercase() else action,
                size = 13.sp, color = t.c.muted, weight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 6.dp, vertical = 8.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- containers

@Composable
fun PulseCard(
    modifier: Modifier = Modifier,
    tint: CardTint = CardTint.DEFAULT,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = pulse
    val (bg, fg) = t.tint(tint)
    val coloured = t.tints.containsKey(tint)
    val shape = RoundedCornerShape(t.radius)
    val base = when (t.cardStyle) {
        CardStyle.OUTLINED -> modifier.clip(shape).background(bg).border(t.borderWidth, t.c.border, shape)
        CardStyle.RULED -> modifier.drawBehind {
            drawLine(t.c.text, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
        }
        CardStyle.BRUTAL -> modifier
            .drawBehind {
                drawRoundRect(
                    color = t.c.border,
                    topLeft = Offset(5.dp.toPx(), 5.dp.toPx()),
                    size = size,
                    cornerRadius = CornerRadius(t.radius.toPx()),
                )
            }
            .clip(shape).background(bg).border(t.borderWidth, t.c.border, shape)
        CardStyle.BRACKETS -> modifier.background(bg).border(1.dp, t.c.border).drawWithContent {
            drawContent()
            drawBrackets(t.c.accent)
        }
        CardStyle.SOFT -> modifier
            .shadow(14.dp, shape, ambientColor = t.c.text.copy(alpha = 0.10f), spotColor = t.c.text.copy(alpha = 0.16f))
            .clip(shape).background(bg)
        CardStyle.SWISS -> modifier.drawBehind {
            drawLine(t.c.text, Offset(0f, 1.dp.toPx()), Offset(size.width, 1.dp.toPx()), 2.dp.toPx())
        }
        CardStyle.GLASS -> modifier.clip(shape).background(bg).border(
            1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.05f))), shape,
        )
        CardStyle.FRAME -> modifier.background(bg).border(1.dp, t.c.border).drawWithContent {
            drawContent()
            val i = 4.dp.toPx()
            drawRect(t.c.accent.copy(alpha = 0.35f), Offset(i, i), Size(size.width - 2 * i, size.height - 2 * i), style = Stroke(0.7.dp.toPx()))
        }
        CardStyle.RETRO -> modifier
            .drawBehind {
                drawRoundRect(t.c.warm, Offset(0f, 5.dp.toPx()), size, CornerRadius(t.radius.toPx()))
            }
            .clip(shape).background(bg).border(2.dp, t.c.border, shape)
        CardStyle.PIXEL -> {
            val cut = CutCornerShape(6.dp)
            modifier
                .drawBehind { drawRect(t.c.carbs, Offset(4.dp.toPx(), 4.dp.toPx()), size) }
                .clip(cut).background(bg).border(t.borderWidth, t.c.border, cut)
        }
        CardStyle.CANDY -> modifier
            .shadow(12.dp, shape, ambientColor = t.c.accent.copy(alpha = 0.30f), spotColor = t.c.accent.copy(alpha = 0.40f))
            .clip(shape).background(bg).border(t.borderWidth, t.c.border, shape)
        CardStyle.BLUEPRINT -> modifier.drawWithContent {
            drawContent()
            drawBlueprintFrame(t.c.border)
        }
    }
    val withClick = if (onClick != null) base.clickable(onClick = onClick) else base
    val pad = when (t.cardStyle) {
        CardStyle.RULED, CardStyle.SWISS -> PaddingValues(top = 14.dp, bottom = 6.dp)
        CardStyle.SOFT -> PaddingValues(padding + 2.dp)
        else -> PaddingValues(padding)
    }
    CompositionLocalProvider(
        LocalContentColor provides fg,
        LocalMuted provides if (coloured) fg.copy(alpha = 0.78f) else t.c.muted,
    ) {
        Column(withClick.padding(pad), content = content)
    }
}

/** Dashed outline with corner lines that run past the edges, like a technical drawing. */
fun DrawScope.drawBlueprintFrame(color: Color) {
    val w = 1.dp.toPx()
    val e = 7.dp.toPx()
    drawRect(color.copy(alpha = 0.75f), style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
    val W = size.width
    val H = size.height
    val c = color.copy(alpha = 0.9f)
    drawLine(c, Offset(-e, 0f), Offset(0f, 0f), w); drawLine(c, Offset(0f, -e), Offset(0f, 0f), w)
    drawLine(c, Offset(W, 0f), Offset(W + e, 0f), w); drawLine(c, Offset(W, -e), Offset(W, 0f), w)
    drawLine(c, Offset(-e, H), Offset(0f, H), w); drawLine(c, Offset(0f, H), Offset(0f, H + e), w)
    drawLine(c, Offset(W, H), Offset(W + e, H), w); drawLine(c, Offset(W, H), Offset(W, H + e), w)
}

fun DrawScope.drawBrackets(color: Color, len: Float = 14.dp.toPx(), w: Float = 2.dp.toPx()) {
    val W = size.width
    val H = size.height
    drawLine(color, Offset(0f, w / 2), Offset(len, w / 2), w)
    drawLine(color, Offset(w / 2, 0f), Offset(w / 2, len), w)
    drawLine(color, Offset(W - len, w / 2), Offset(W, w / 2), w)
    drawLine(color, Offset(W - w / 2, 0f), Offset(W - w / 2, len), w)
    drawLine(color, Offset(0f, H - w / 2), Offset(len, H - w / 2), w)
    drawLine(color, Offset(w / 2, H - len), Offset(w / 2, H), w)
    drawLine(color, Offset(W - len, H - w / 2), Offset(W, H - w / 2), w)
    drawLine(color, Offset(W - w / 2, H - len), Offset(W - w / 2, H), w)
}

/** Square icon badge used in card headers. */
@Composable
fun IconBadge(icon: ImageVector, color: Color, size: Dp = 30.dp) {
    val t = pulse
    val shape = when (t.id) {
        ThemeId.MATCHA, ThemeId.CANDY, ThemeId.RETRO -> RoundedCornerShape(50)
        ThemeId.NEON, ThemeId.SWISS, ThemeId.PAPER, ThemeId.PIXEL, ThemeId.NOIR, ThemeId.BLUEPRINT -> RoundedCornerShape(0.dp)
        else -> RoundedCornerShape(10.dp)
    }
    val bg = when (t.id) {
        ThemeId.BRUTAL -> Color.White
        ThemeId.PAPER, ThemeId.SWISS, ThemeId.NOIR, ThemeId.BLUEPRINT -> Color.Transparent
        else -> color.copy(alpha = if (t.isDark) 0.16f else 0.22f)
    }
    Box(
        Modifier.size(size).clip(shape).background(bg)
            .then(if (t.id == ThemeId.BRUTAL) Modifier.border(2.dp, t.c.border, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.52f), tint = if (t.id == ThemeId.BRUTAL) t.c.text else if (t.isDark) color else darken(color))
    }
}

private fun darken(c: Color, f: Float = 0.62f) = Color(c.red * f, c.green * f, c.blue * f, c.alpha)

// ---------------------------------------------------------------- controls

@Composable
fun PChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 40.dp) {
    val t = pulse
    val c = t.c
    val shape = RoundedCornerShape(t.chipRadius)
    val bg: Color
    val fg: Color
    val bd: Color
    when (t.id) {
        ThemeId.NEON -> { bg = if (selected) c.accent.copy(alpha = 0.12f) else Color.Transparent; fg = if (selected) c.accent else c.muted; bd = if (selected) c.accent else c.border }
        ThemeId.PAPER, ThemeId.SWISS -> { bg = if (selected) c.text else Color.Transparent; fg = if (selected) c.bg else c.text; bd = c.text }
        ThemeId.BRUTAL -> { bg = if (selected) c.accent else c.surface; fg = c.text; bd = c.border }
        ThemeId.MATCHA -> { bg = if (selected) c.accent else c.surface; fg = if (selected) c.onAccent else c.text; bd = Color.Transparent }
        else -> { bg = if (selected) c.accent else c.surface; fg = if (selected) c.onAccent else c.text; bd = if (selected) c.accent else c.border }
    }
    Box(
        modifier.height(height).clip(shape).background(bg)
            .border(if (t.id == ThemeId.BRUTAL) 2.dp else 1.dp, bd, shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        PText(
            if (t.upperLabels) text.uppercase() else text,
            size = if (t.upperLabels) 12.sp else 13.sp,
            weight = if (t.id == ThemeId.BRUTAL) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = fg, tracking = if (t.upperLabels) 0.06f else 0f, maxLines = 1,
        )
    }
}

/** Primary call-to-action. */
@Composable
fun PButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    container: Color? = null,
    content: Color? = null,
    height: Dp = 54.dp,
    enabled: Boolean = true,
) {
    val t = pulse
    val c = t.c
    val bg = container ?: c.accent
    val fg = content ?: c.onAccent
    val radius = when (t.id) {
        ThemeId.MATCHA, ThemeId.CANDY, ThemeId.RETRO -> height / 2
        ThemeId.BRUTAL -> 14.dp
        ThemeId.VOLT, ThemeId.AURORA -> 18.dp
        else -> 0.dp
    }
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .height(height)
            .then(
                if (t.id == ThemeId.BRUTAL || t.id == ThemeId.PIXEL) Modifier.drawBehind {
                    drawRoundRect(if (t.id == ThemeId.PIXEL) c.carbs else c.border, Offset(4.dp.toPx(), 4.dp.toPx()), size, CornerRadius(radius.toPx()))
                } else if (t.id == ThemeId.NEON) Modifier.shadow(16.dp, shape, ambientColor = bg, spotColor = bg) else Modifier,
            )
            .clip(shape)
            .background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .then(
                when (t.id) {
                    ThemeId.BRUTAL, ThemeId.PIXEL -> Modifier.border(2.5.dp, c.border, shape)
                    ThemeId.RETRO -> Modifier.border(2.dp, c.border, shape)
                    else -> Modifier
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = fg)
            PText(
                if (t.upperLabels) text.uppercase() else text,
                size = if (t.upperLabels) 14.sp else 16.sp,
                weight = if (t.id == ThemeId.BRUTAL || t.id == ThemeId.SWISS) FontWeight.ExtraBold else FontWeight.Bold,
                color = fg, tracking = if (t.upperLabels) 0.08f else 0f, maxLines = 1,
            )
        }
    }
}

/** Square or round icon button. */
@Composable
fun PIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    filled: Boolean = false,
) {
    val t = pulse
    val c = t.c
    val shape = when (t.id) {
        ThemeId.MATCHA, ThemeId.CANDY, ThemeId.RETRO -> RoundedCornerShape(50)
        ThemeId.NEON, ThemeId.SWISS, ThemeId.PAPER, ThemeId.NOIR, ThemeId.BLUEPRINT -> RoundedCornerShape(0.dp)
        ThemeId.PIXEL -> CutCornerShape(4.dp)
        else -> RoundedCornerShape(14.dp)
    }
    val bg = when {
        filled -> c.accent
        t.id in setOf(ThemeId.PAPER, ThemeId.SWISS, ThemeId.NEON, ThemeId.NOIR, ThemeId.BLUEPRINT) -> Color.Transparent
        else -> c.surface
    }
    val border = when (t.id) {
        ThemeId.BRUTAL, ThemeId.PIXEL -> Modifier.border(2.5.dp, c.border, shape)
        ThemeId.RETRO, ThemeId.CANDY -> Modifier.border(2.dp, c.border, shape)
        ThemeId.MATCHA -> Modifier
        else -> Modifier.border(1.dp, if (filled) c.accent else c.border, shape)
    }
    Box(
        modifier.size(size)
            .then(if (t.id == ThemeId.MATCHA) Modifier.shadow(8.dp, shape, ambientColor = c.text.copy(alpha = 0.1f), spotColor = c.text.copy(alpha = 0.1f)) else Modifier)
            .clip(shape).background(bg).then(border).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, Modifier.size(20.dp), tint = if (filled) c.onAccent else c.text)
    }
}

@Composable
fun PBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    track: Color = pulse.c.track,
) {
    val t = pulse
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(800, easing = FastOutSlowInEasing), label = "bar")
    val r = when (t.cardStyle) {
        CardStyle.RULED, CardStyle.SWISS, CardStyle.BRACKETS, CardStyle.FRAME, CardStyle.PIXEL, CardStyle.BLUEPRINT -> 0.dp
        CardStyle.BRUTAL -> 5.dp
        else -> height / 2
    }
    val shape = RoundedCornerShape(r)
    Box(
        modifier.height(height).clip(shape).background(track)
            .then(if (t.id == ThemeId.BRUTAL || t.id == ThemeId.PIXEL) Modifier.border(2.dp, t.c.border, shape) else Modifier),
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(anim).clip(shape).background(color))
        if (t.id == ThemeId.PIXEL) {
            // Cut the fill into chunky blocks.
            Box(
                Modifier.matchParentSize().drawBehind {
                    val step = 8.dp.toPx()
                    var x = step
                    while (x < size.width) {
                        drawLine(track, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                        x += step
                    }
                },
            )
        }
    }
}

// ---------------------------------------------------------------- rings

data class Seg(val fraction: Float, val color: Color)

enum class RingStyle { BUTT, ROUND, LED }

@Composable
fun Ring(
    segments: List<Seg>,
    modifier: Modifier = Modifier,
    stroke: Dp = 16.dp,
    track: Color = pulse.c.track,
    style: RingStyle = RingStyle.BUTT,
    cutColor: Color = pulse.c.surface,
    glow: Boolean = false,
    inner: Seg? = null,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    val animSegs = segments.map { it.copy(fraction = it.fraction.coerceAtLeast(0f)) }
    val total = animSegs.sumOf { it.fraction.toDouble() }.toFloat()
    val scale = if (total > 1f) 1f / total else 1f
    Canvas(modifier) {
        val sw = stroke.toPx()
        val pad = sw / 2 + if (glow) sw * 0.6f else 0f
        val arc = Size(size.width - pad * 2, size.height - pad * 2)
        val tl = Offset(pad, pad)
        val radius = arc.width / 2
        drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(sw))
        var start = -90f
        val p = progress.value
        animSegs.forEach { s ->
            val sweep = 360f * s.fraction * scale * p
            if (sweep > 0.2f) {
                when (style) {
                    RingStyle.BUTT, RingStyle.LED -> {
                        val gap = if (style == RingStyle.BUTT && animSegs.size > 1) 1.6f else 0f
                        if (glow) {
                            drawArc(s.color.copy(alpha = 0.10f), start, sweep, false, tl, arc, style = Stroke(sw * 2.4f))
                            drawArc(s.color.copy(alpha = 0.18f), start, sweep, false, tl, arc, style = Stroke(sw * 1.6f))
                        }
                        drawArc(s.color, start + gap / 2, max(0.1f, sweep - gap), false, tl, arc, style = Stroke(sw, cap = StrokeCap.Butt))
                    }
                    RingStyle.ROUND -> {
                        val capDeg = ((sw / 2) / radius * 180f / PI.toFloat())
                        val gap = 3f
                        val visible = sweep - 2 * capDeg - gap
                        drawArc(
                            s.color, start + capDeg + gap / 2, max(0.1f, visible), false, tl, arc,
                            style = Stroke(sw, cap = StrokeCap.Round),
                        )
                    }
                }
            }
            start += sweep
        }
        if (style == RingStyle.LED) {
            val on = 2.6.dp.toPx()
            val circumference = (2 * PI * radius).toFloat()
            val count = (circumference / (on + 5.4.dp.toPx())).toInt().coerceAtLeast(1)
            val step = circumference / count
            drawCircle(
                cutColor, radius, center,
                style = Stroke(sw + 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(on, step - on))),
            )
        }
        if (inner != null) {
            val isw = 4.dp.toPx()
            val ipad = pad + sw / 2 + 9.dp.toPx() + isw / 2
            val iarc = Size(size.width - ipad * 2, size.height - ipad * 2)
            val itl = Offset(ipad, ipad)
            drawArc(track, 0f, 360f, false, itl, iarc, style = Stroke(isw))
            drawArc(inner.color, -90f, 360f * inner.fraction.coerceIn(0f, 1f) * p, false, itl, iarc, style = Stroke(isw))
        }
    }
}

// ---------------------------------------------------------------- inputs

@Composable
fun PTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    number: Boolean = false,
    height: Dp = 52.dp,
) {
    val t = pulse
    val c = t.c
    val shape = RoundedCornerShape(
        when (t.id) {
            ThemeId.MATCHA, ThemeId.CANDY, ThemeId.RETRO -> 26.dp
            ThemeId.VOLT, ThemeId.AURORA -> 18.dp
            ThemeId.BRUTAL -> 14.dp
            else -> 0.dp
        },
    )
    val underline = t.id in setOf(ThemeId.PAPER, ThemeId.SWISS, ThemeId.NOIR, ThemeId.BLUEPRINT)
    val bg = if (underline) Color.Transparent else c.surface
    val border = when (t.id) {
        ThemeId.BRUTAL, ThemeId.PIXEL -> Modifier.border(2.5.dp, c.border, shape)
        ThemeId.RETRO, ThemeId.CANDY -> Modifier.border(2.dp, c.border, shape)
        ThemeId.MATCHA -> Modifier
        ThemeId.PAPER, ThemeId.SWISS, ThemeId.NOIR, ThemeId.BLUEPRINT -> Modifier.drawBehind {
            drawLine(c.text, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 1.5.dp.toPx())
        }
        else -> Modifier.border(1.dp, c.border, shape)
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(fontFamily = t.body, fontSize = 16.sp, color = c.text),
        cursorBrush = SolidColor(c.accentText),
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().height(height)
                    .then(if (t.id == ThemeId.MATCHA) Modifier.shadow(8.dp, shape, ambientColor = c.text.copy(alpha = 0.08f), spotColor = c.text.copy(alpha = 0.1f)) else Modifier)
                    .clip(shape).background(bg).then(border)
                    .padding(horizontal = if (underline) 2.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    Icon(leading, null, Modifier.size(20.dp), tint = c.muted)
                    Spacer(Modifier.width(10.dp))
                }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) PText(placeholder, size = 15.sp, color = c.muted, maxLines = 1)
                    inner()
                }
            }
        },
    )
}

/** Themed dialog wrapper. */
@Composable
fun PDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String = "Save",
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = pulse
    val c = t.c
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (t.cardStyle == CardStyle.RULED || t.cardStyle == CardStyle.SWISS) c.bg else t.solidSurface,
        shape = RoundedCornerShape(if (t.radius > 0.dp) 28.dp else 0.dp),
        title = { PText(title, size = 22.sp, display = true, color = c.text) },
        text = {
            CompositionLocalProvider(LocalContentColor provides c.text) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            }
        },
        confirmButton = {
            PButton(confirmText, onConfirm, height = 44.dp, enabled = confirmEnabled)
        },
        dismissButton = {
            PText(
                "Cancel", size = 15.sp, weight = FontWeight.SemiBold, color = c.muted,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDismiss).padding(horizontal = 12.dp, vertical = 12.dp),
            )
        },
    )
}

/** Slight tilt used by the Brutal theme's stickers; no-op elsewhere. */
@Composable
fun Modifier.sticker(deg: Float): Modifier = if (pulse.id == ThemeId.BRUTAL) this.rotate(deg) else this

fun formatInt(v: Int): String = "%,d".format(v)
fun formatInt(v: Double): String = formatInt(v.toInt())
