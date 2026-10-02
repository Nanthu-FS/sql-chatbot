package com.smartnotes.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.smartnotes.core.Gesture
import com.smartnotes.core.GestureRecognizer
import com.smartnotes.core.Pt
import com.smartnotes.ui.theme.LocalSkin

/** A finished stroke, in root coordinates so it can be matched against rendered lines. */
data class GestureStroke(val gesture: Gesture, val points: List<Offset>)

/** Transparent drawing surface. ✓ makes a todo, ○ searches, — strikes a todo through. */
@Composable
fun GestureLayer(onStroke: (GestureStroke) -> Unit, modifier: Modifier = Modifier) {
    val t = LocalSkin.current
    val points = remember { mutableStateListOf<Offset>() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier.fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { points.clear(); points.add(it) },
                    onDrag = { change, _ -> points.add(change.position); change.consume() },
                    onDragEnd = {
                        val gesture = GestureRecognizer.classify(points.map { Pt(it.x, it.y) })
                        onStroke(GestureStroke(gesture, points.map { it + origin }))
                        points.clear()
                    },
                    onDragCancel = { points.clear() },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (points.size > 1) {
                val path = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                drawPath(path, t.accent, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}
