package com.ditherfarm.ui

import android.graphics.Canvas
import android.view.MotionEvent
import com.ditherfarm.Game

/**
 * A finger for tests: real MotionEvents through the engine's GestureRouter, with frames run in
 * between on a test clock, so taps, long presses, slides and drags reach the game exactly the way
 * a phone delivers them (HUD first, long press after 320 ms, then paint). Coordinates are game pixels.
 */
class TestTouch(private val game: Game, private val canvas: Canvas, var clock: Long = 1_000L) {
    fun frame(dt: Float = 1f / 30f) {
        canvas.drawColor(0xFF181425.toInt())
        game.engine.frame(dt, canvas, clock)
        clock += (dt * 1000).toLong()
    }

    fun frames(n: Int) = repeat(n) { frame() }

    private var downAt = 0L

    private fun send(action: Int, x: Float, y: Float) {
        if (action == MotionEvent.ACTION_DOWN) downAt = clock
        val e = MotionEvent.obtain(downAt, clock, action, x * game.engine.router.scale, y * game.engine.router.scale, 0)
        game.engine.router.onTouch(e)
        e.recycle()
    }

    fun tap(x: Float, y: Float) {
        send(MotionEvent.ACTION_DOWN, x, y); frame()
        send(MotionEvent.ACTION_UP, x, y); frames(4)
    }

    /** Presses until the long press fires, then lifts. */
    fun hold(x: Float, y: Float) {
        send(MotionEvent.ACTION_DOWN, x, y); frames(14)
        send(MotionEvent.ACTION_UP, x, y); frames(4)
    }

    /** Long press at (x1, y1), slide to (x2, y2) in [steps] moves, lift: a harvest or water slide. */
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, steps: Int = 16) {
        send(MotionEvent.ACTION_DOWN, x1, y1); frames(14)
        for (i in 1..steps) { send(MotionEvent.ACTION_MOVE, x1 + (x2 - x1) * i / steps, y1 + (y2 - y1) * i / steps); frame() }
        send(MotionEvent.ACTION_UP, x2, y2); frames(4)
    }

    /** Drags the map by (dx, dy) right away (no long press). */
    fun drag(dx: Float, dy: Float, x: Float = 135f, y: Float = 240f, steps: Int = 8) {
        send(MotionEvent.ACTION_DOWN, x, y); frame()
        for (i in 1..steps) { send(MotionEvent.ACTION_MOVE, x + dx * i / steps, y + dy * i / steps); frame() }
        send(MotionEvent.ACTION_UP, x + dx, y + dy); frames(3)
    }
}
