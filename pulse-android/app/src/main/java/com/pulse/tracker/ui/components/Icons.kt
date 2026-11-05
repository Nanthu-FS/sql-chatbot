package com.pulse.tracker.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Stroke icons drawn on a 24×24 grid (tinted at use site). */
object PIcons {
    val Home by lazy { icon("M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z") }
    val Chart by lazy { icon("M6 20V10M12 20V4M18 20v-7") }
    val Plus by lazy { icon("M12 5v14M5 12h14", stroke = 2.4f) }
    val Minus by lazy { icon("M5 12h14", stroke = 2.4f) }
    val Drop by lazy { icon("M12 22a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5s-3.5-4-4-6.5c-.5 2.5-2 4.9-4 6.5C6 11.1 5 13 5 15a7 7 0 0 0 7 7z") }
    val Habits by lazy { icon(rect(3f, 3f, 7f, 7f, 2f), rect(14f, 3f, 7f, 7f, 2f), rect(3f, 14f, 7f, 7f, 2f), "m14.5 17.5 2 2 4-4") }
    val Bell by lazy { icon("M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.94 1.94 0 0 0 3.4 0") }
    val User by lazy { icon(circle(12f, 8f, 4f), "M4 21a8 8 0 0 1 16 0") }
    val Sliders by lazy { icon("M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6") }
    val Back by lazy { icon("m15 18-6-6 6-6") }
    val ChevronRight by lazy { icon("m9 18 6-6-6-6") }
    val Close by lazy { icon("M18 6 6 18M6 6l12 12") }
    val Check by lazy { icon("M20 6 9 17l-5-5", stroke = 2.6f) }
    val Flame by lazy { icon("M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.07-2.14-.22-4.05 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.15.43-2.29 1-3a2.5 2.5 0 0 0 2.5 2.5z") }
    val Utensils by lazy { icon("M3 2v7c0 1.1.9 2 2 2h4a2 2 0 0 0 2-2V2", "M7 2v20", "M21 15V2a5 5 0 0 0-5 5v6c0 1.1.9 2 2 2h3Zm0 0v7") }
    val Target by lazy { icon(circle(12f, 12f, 9f), circle(12f, 12f, 5f), circle(12f, 12f, 1f)) }
    val Activity by lazy { icon("M22 12h-4l-3 9L9 3l-3 9H2") }
    val Moon by lazy { icon("M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9z") }
    val Leaf by lazy { icon("M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.48 19 2c1 2 2 4.18 2 8 0 5.5-4.78 10-10 10Z", "M2 21c0-3 1.85-5.36 5.08-6C9.5 14.52 12 13 13 12") }
    val Pill by lazy { icon("m10.5 20.5 10-10a4.95 4.95 0 1 0-7-7l-10 10a4.95 4.95 0 1 0 7 7Z", "m8.5 8.5 7 7") }
    val Timer by lazy { icon(circle(12f, 13f, 8f), "M12 9v4l2 2M10 2h4") }
    val Scan by lazy { icon("M3 7V5a2 2 0 0 1 2-2h2M17 3h2a2 2 0 0 1 2 2v2M21 17v2a2 2 0 0 1-2 2h-2M7 21H5a2 2 0 0 1-2-2v-2", "M7 8v8M10 8v8M14 8v8M17 8v8") }
    val Bolt by lazy { icon("M13 2 3 14h9l-1 8 10-12h-9l1-8z") }
    val Search by lazy { icon(circle(11f, 11f, 7f), "m20 20-3.5-3.5") }
    val Trash by lazy { icon("M3 6h18M8 6V4h8v2M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6") }
    val Cup by lazy { icon("M17 8h1a4 4 0 1 1 0 8h-1", "M3 8h14v9a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4Z") }
    val Glass by lazy { icon("M5 3h14l-1.5 17a1 1 0 0 1-1 1h-9a1 1 0 0 1-1-1z", "M6 9h12") }
    val Bottle by lazy { icon("M10 2h4v3l2 3v12a2 2 0 0 1-2 2h-4a2 2 0 0 1-2-2V8l2-3z", "M8 12h8") }
    val Refresh by lazy { icon("M21 12a9 9 0 1 1-2.64-6.36L21 8", "M21 3v5h-5") }
    val Undo by lazy { icon("M3 7v6h6", "M21 17a9 9 0 0 0-9-9 9 9 0 0 0-6 2.3L3 13") }
    val Calendar by lazy { icon(rect(3f, 4f, 18f, 18f, 3f), "M16 2v4M8 2v4M3 10h18") }
    val TrendDown by lazy { icon("m22 17-8.5-8.5-5 5L2 7", "M16 17h6v-6") }
    val TrendUp by lazy { icon("m22 7-8.5 8.5-5-5L2 17", "M16 7h6v6") }
    val Scale by lazy { icon(rect(3f, 3f, 18f, 18f, 5f), "M8 9a5 5 0 0 1 8 0", "m12 9 1.5-2") }
    val Edit by lazy { icon("M12 20h9", "M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z") }
    val Palette by lazy { icon(circle(12f, 12f, 9f), circle(8f, 10f, 1f), circle(12f, 7.5f, 1f), circle(16f, 10f, 1f), "M12 21a3 3 0 0 1 0-6h2a3 3 0 0 0 3-3") }

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"

    private fun icon(vararg paths: String, stroke: Float = 2f): ImageVector =
        ImageVector.Builder(
            defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = stroke,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
}
