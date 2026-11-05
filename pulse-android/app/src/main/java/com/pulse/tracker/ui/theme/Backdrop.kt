package com.pulse.tracker.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/** Paints the theme's backdrop behind every screen. */
fun DrawScope.drawBackdrop(theme: PulseTheme) {
    val w = size.width
    val h = size.height
    when (theme.backdrop) {
        Backdrop.NONE -> Unit

        Backdrop.AURORA -> {
            fun glow(color: Color, alpha: Float, cx: Float, cy: Float, r: Float) {
                drawCircle(
                    Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = Offset(cx, cy), radius = r),
                    radius = r, center = Offset(cx, cy),
                )
            }
            glow(Color(0xFF2EE6C5), 0.30f, w * 0.05f, h * 0.06f, w * 0.85f)
            glow(Color(0xFF8B5CF6), 0.32f, w * 1.0f, h * 0.30f, w * 0.80f)
            glow(Color(0xFFFF5DA2), 0.16f, w * 0.20f, h * 0.78f, w * 0.75f)
        }

        Backdrop.SUNSET -> {
            // A striped 70s sun peeking in from the top-right corner.
            val c = Offset(w * 0.92f, h * 0.04f)
            val r = w * 0.42f
            drawCircle(theme.c.warm.copy(alpha = 0.22f), r, c)
            drawCircle(theme.c.accent.copy(alpha = 0.14f), r * 0.68f, c)
            val stripe = 7.dp.toPx()
            var y = c.y + r * 0.15f
            var gap = 5.dp.toPx()
            while (y < c.y + r) {
                drawRect(theme.c.bg, Offset(c.x - r, y), androidx.compose.ui.geometry.Size(r * 2, gap))
                y += stripe + gap
                gap += 1.5.dp.toPx()
            }
        }

        Backdrop.GRID -> {
            val minor = 16.dp.toPx()
            val line = Color.White
            var x = 0f
            var i = 0
            while (x < w) {
                drawLine(line.copy(alpha = if (i % 5 == 0) 0.11f else 0.045f), Offset(x, 0f), Offset(x, h), 1f)
                x += minor; i++
            }
            var y = 0f
            i = 0
            while (y < h) {
                drawLine(line.copy(alpha = if (i % 5 == 0) 0.11f else 0.045f), Offset(0f, y), Offset(w, y), 1f)
                y += minor; i++
            }
        }

        Backdrop.DOTS -> {
            val step = 30.dp.toPx()
            val r = 3.dp.toPx()
            var row = 0
            var y = step / 2
            while (y < h) {
                var x = if (row % 2 == 0) step / 2 else step
                while (x < w) {
                    drawCircle(theme.c.accent.copy(alpha = 0.10f), r, Offset(x, y))
                    x += step
                }
                y += step * 0.866f
                row++
            }
        }
    }
}
