package app.monoworkspace.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.monoworkspace.model.Covers
import app.monoworkspace.ui.common.LocalAppContainer
import app.monoworkspace.ui.theme.MonoColors
import app.monoworkspace.ui.theme.MonoIcons
import kotlin.math.hypot
import kotlin.math.sqrt

/** Loads an image from app-private media storage. */
@Composable
fun MediaImage(
    name: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    maxEdge: Int = 1600,
) {
    val media = LocalAppContainer.current.media
    val bitmap by produceState<ImageBitmap?>(null, name, maxEdge) { value = media.loadImage(name, maxEdge) }
    Box(modifier.background(MonoColors.Tint), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b != null) Image(b, contentDescription, Modifier.fillMaxSize(), contentScale = contentScale)
        else MonoIcon(MonoIcons.Image, null, tint = MonoColors.Tertiary)
    }
}

/** Page cover: a generative mono pattern or an imported image. */
@Composable
fun CoverArt(cover: String, modifier: Modifier = Modifier) {
    if (cover.startsWith(Covers.IMAGE_PREFIX)) {
        MediaImage(cover.removePrefix(Covers.IMAGE_PREFIX), "Cover image", modifier)
        return
    }
    val pattern = cover.removePrefix(Covers.PATTERN_PREFIX)
    val ink = MonoColors.Ink
    Canvas(modifier.background(MonoColors.Background)) { drawPattern(pattern, ink) }
}

fun DrawScope.drawPattern(pattern: String, ink: androidx.compose.ui.graphics.Color) {
    val w = size.width
    val h = size.height
    val unit = 12.dp.toPx()
    clipRect {
        when (pattern) {
            "solid" -> drawRect(ink)
            "stripes" -> {
                var x = 0f
                while (x < w) { drawRect(ink, Offset(x, 0f), Size(unit, h)); x += unit * 2 }
            }
            "grid" -> {
                val step = unit * 2
                val line = 1.dp.toPx()
                var x = 0f
                while (x < w) { drawRect(ink, Offset(x, 0f), Size(line, h)); x += step }
                var y = 0f
                while (y < h) { drawRect(ink, Offset(0f, y), Size(w, line)); y += step }
            }
            "dots" -> {
                val step = unit * 1.5f
                val r = 1.6.dp.toPx()
                var y = step / 2
                while (y < h) {
                    var x = step / 2
                    while (x < w) { drawCircle(ink, r, Offset(x, y)); x += step }
                    y += step
                }
            }
            "diagonal" -> {
                val step = unit * 1.4f
                val stroke = 2.dp.toPx()
                var d = -h
                while (d < w) {
                    drawLine(ink, Offset(d, h), Offset(d + h, 0f), stroke)
                    d += step
                }
            }
            "checker" -> {
                val step = unit * 2
                var row = 0
                var y = 0f
                while (y < h) {
                    var x = if (row % 2 == 0) 0f else step
                    while (x < w) { drawRect(ink, Offset(x, y), Size(step, step)); x += step * 2 }
                    y += step
                    row++
                }
            }
            "rules" -> {
                // Swiss poster rules: varying weights on a baseline grid.
                val weights = floatArrayOf(10f, 1f, 1f, 4f, 1f, 18f, 1f, 2f, 1f, 6f)
                var y = 0f
                var i = 0
                val gap = 8.dp.toPx()
                while (y < h) {
                    val t = weights[i % weights.size].dp.toPx() * 0.6f
                    drawRect(ink, Offset(0f, y), Size(w, t))
                    y += t + gap
                    i++
                }
            }
            "halftone" -> {
                val step = unit * 1.3f
                val maxR = step * 0.48f
                val diag = hypot(w, h)
                var y = step / 2
                while (y < h + step) {
                    var x = step / 2
                    while (x < w + step) {
                        val t = (x + y) / diag
                        val r = maxR * sqrt(t.coerceIn(0f, 1f))
                        if (r > 0.4f) drawCircle(ink, r, Offset(x, y))
                        x += step
                    }
                    y += step
                }
            }
            else -> drawRect(ink)
        }
    }
}
