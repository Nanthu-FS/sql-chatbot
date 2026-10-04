package com.smartnotes.core

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

data class Pt(val x: Float, val y: Float)

enum class Gesture {
    /** ✓ — turn the line under the stroke into a todo. */
    CHECKMARK,
    /** ○ — search for the circled text. */
    CIRCLE,
    /** — — strike the line through (mark todo done). */
    STRIKE,
    UNKNOWN,
}

/** Classifies a single finger/stylus stroke. Screen coordinates: y grows downward. */
object GestureRecognizer {

    fun classify(points: List<Pt>): Gesture {
        if (points.size < 5) return Gesture.UNKNOWN
        val minX = points.minOf { it.x }; val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }; val maxY = points.maxOf { it.y }
        val w = maxX - minX; val h = maxY - minY
        val diag = hypot(w, h)
        if (diag < 40f) return Gesture.UNKNOWN

        val pathLength = points.zipWithNext { a, b -> hypot(b.x - a.x, b.y - a.y) }.sum()
        val first = points.first(); val last = points.last()
        val closeGap = hypot(last.x - first.x, last.y - first.y)

        if (h < 0.2f * w && w > 80f) return Gesture.STRIKE

        val aspect = if (h == 0f) Float.MAX_VALUE else w / h
        if (closeGap < 0.3f * diag && pathLength > 2.2f * diag && aspect in 0.4f..2.5f) return Gesture.CIRCLE

        // Checkmark: the stroke bottoms out early or midway, then rises to the right and ends
        // well above that low point. Real finger ticks vary a lot ("✓", "_/", a lazy "v"), so
        // the opening drop may be tiny.
        val lowIdx = points.indices.maxBy { points[it].y }
        val low = points[lowIdx]
        val fraction = lowIdx.toFloat() / points.lastIndex
        val rise = low.y - last.y
        val drop = low.y - first.y
        if (fraction in 0.05f..0.75f && rise > 0.45f * h && rise >= drop * 0.8f &&
            last.x > low.x && last.x - first.x > -0.2f * w
        ) return Gesture.CHECKMARK

        return Gesture.UNKNOWN
    }
}
