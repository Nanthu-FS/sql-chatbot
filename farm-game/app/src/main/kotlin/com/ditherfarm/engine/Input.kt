package com.ditherfarm.engine

import android.view.MotionEvent
import kotlin.math.hypot

/** What the current screen wants from raw touch input. Coordinates are buffer pixels. */
interface TouchTarget {
    /** HUD gets first look at every press; return true to own the whole gesture. */
    fun hudDown(x: Float, y: Float): Boolean = false
    fun hudMove(x: Float, y: Float) {}
    fun hudUp(x: Float, y: Float) {}

    fun tap(x: Float, y: Float) {}
    fun longPress(x: Float, y: Float) {}
    /** After a long press the finger "paints" actions across tiles until release. */
    fun paint(x: Float, y: Float) {}
    fun paintEnd() {}
    fun pan(dx: Float, dy: Float) {}
    fun fling(vx: Float, vy: Float) {}
    fun panStart() {}
    fun pinch(steps: Int, fx: Float, fy: Float) {}
}

/**
 * Turns MotionEvents into taps, pans (with fling), long-press painting and
 * whole-step pinch zoom. [scale] converts screen pixels to buffer pixels.
 */
class GestureRouter(private val slopPx: Float, private val longPressMs: Long = 320L) {
    var scale = 1f
    var target: TouchTarget? = null

    private enum class Mode { NONE, HUD, PENDING, PAN, PAINT, PINCH }
    private var mode = Mode.NONE
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downTime = 0L
    private var pinchStart = 0f
    private var pinchApplied = 0
    // Recent motion for fling velocity (buffer px).
    private val hx = FloatArray(6)
    private val hy = FloatArray(6)
    private val ht = LongArray(6)
    private var hn = 0

    /** Call every frame so a still finger becomes a long press. */
    fun tick(nowMs: Long) {
        if (mode == Mode.PENDING && nowMs - downTime >= longPressMs) {
            mode = Mode.PAINT
            target?.longPress(downX, downY)
            target?.paint(downX, downY)
        }
    }

    fun onTouch(e: MotionEvent): Boolean {
        val t = target ?: return false
        val x = e.getX(0) / scale
        val y = e.getY(0) / scale
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x; downY = y; lastX = x; lastY = y; downTime = e.eventTime; hn = 0
                record(x, y, e.eventTime)
                mode = if (t.hudDown(x, y)) Mode.HUD else Mode.PENDING
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (mode == Mode.PAINT) t.paintEnd()
                if (mode != Mode.HUD && e.pointerCount >= 2) {
                    mode = Mode.PINCH
                    pinchStart = spread(e)
                    pinchApplied = 0
                }
            }
            MotionEvent.ACTION_MOVE -> when (mode) {
                Mode.HUD -> t.hudMove(x, y)
                Mode.PENDING -> if (hypot(x - downX, y - downY) * scale > slopPx) {
                    mode = Mode.PAN
                    t.panStart()
                    t.pan(x - lastX, y - lastY)
                }
                Mode.PAN -> t.pan(x - lastX, y - lastY)
                Mode.PAINT -> t.paint(x, y)
                Mode.PINCH -> if (e.pointerCount >= 2) {
                    val ratio = spread(e) / pinchStart
                    // One zoom step per ~45% change in finger spread.
                    val want = when {
                        ratio > 1f -> ((ratio - 1f) / 0.45f).toInt()
                        else -> -((1f / ratio - 1f) / 0.45f).toInt()
                    }
                    if (want != pinchApplied) {
                        val fx = (e.getX(0) + e.getX(1)) / 2f / scale
                        val fy = (e.getY(0) + e.getY(1)) / 2f / scale
                        t.pinch(want - pinchApplied, fx, fy)
                        pinchApplied = want
                    }
                }
                Mode.NONE -> {}
            }
            MotionEvent.ACTION_UP -> {
                when (mode) {
                    Mode.HUD -> t.hudUp(x, y)
                    Mode.PENDING -> t.tap(x, y)
                    Mode.PAN -> {
                        record(x, y, e.eventTime)
                        val (vx, vy) = velocity()
                        t.fling(vx, vy)
                    }
                    Mode.PAINT -> t.paintEnd()
                    else -> {}
                }
                mode = Mode.NONE
            }
            MotionEvent.ACTION_CANCEL -> {
                if (mode == Mode.PAINT) t.paintEnd()
                if (mode == Mode.HUD) t.hudUp(-999f, -999f)
                mode = Mode.NONE
            }
        }
        if (e.actionMasked == MotionEvent.ACTION_MOVE) record(x, y, e.eventTime)
        lastX = x; lastY = y
        return true
    }

    private fun spread(e: MotionEvent) =
        hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1)).coerceAtLeast(1f)

    private fun record(x: Float, y: Float, t: Long) {
        val i = hn % hx.size
        hx[i] = x; hy[i] = y; ht[i] = t; hn++
    }

    private fun velocity(): Pair<Float, Float> {
        if (hn < 2) return 0f to 0f
        val last = (hn - 1) % hx.size
        var first = last
        val count = minOf(hn, hx.size)
        for (k in 1 until count) {
            val i = (hn - 1 - k) % hx.size
            if (ht[last] - ht[i] > 100) break
            first = i
        }
        val dt = (ht[last] - ht[first]) / 1000f
        if (dt <= 0.005f) return 0f to 0f
        return (hx[last] - hx[first]) / dt to (hy[last] - hy[first]) / dt
    }
}
