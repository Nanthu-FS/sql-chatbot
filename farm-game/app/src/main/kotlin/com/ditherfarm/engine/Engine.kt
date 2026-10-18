package com.ditherfarm.engine

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader

/** One full-screen state (Farm, Forest, Mine, title...). Coordinates are buffer pixels. */
abstract class Screen : TouchTarget {
    open fun enter() {}
    open fun exit() {}
    abstract fun update(dt: Float)
    abstract fun draw(g: Gfx)
}

/**
 * Owns the low-res buffer size, the active screen and screen transitions.
 * Switching screens plays a Bayer-dither dissolve from the old frame.
 */
class Engine(val gfx: Gfx, val router: GestureRouter) {
    var w = 270
        private set
    var h = 480
        private set
    /** Rows of buffer pixels hidden by the status bar / camera cutout. */
    var safeTop = 0
    var safeBottom = 0
    var time = 0f
        private set

    var screen: Screen? = null
        private set
    /** Runs every frame before the screen updates (the game simulation hooks in here). */
    var onTick: ((Float) -> Unit)? = null
    private var next: Screen? = null
    private var snapshot: Bitmap? = null
    private var transition = 0f
    private val transitionTime = 0.38f
    private val masks: Array<Paint> = Array(17) { k -> ditherMask(k) }

    fun resize(bw: Int, bh: Int) { w = bw; h = bh }

    fun start(first: Screen) {
        screen = first
        router.target = first
        first.enter()
    }

    /** Switch to [to] with a dither dissolve. [current] is the frame now on screen. */
    fun go(to: Screen, current: Bitmap) {
        if (to === screen || next != null) return
        snapshot = current.copy(Bitmap.Config.ARGB_8888, false)
        screen?.exit()
        screen = to
        router.target = to
        to.enter()
        transition = transitionTime
    }

    fun frame(dt: Float, canvas: Canvas, nowMs: Long) {
        time += dt
        router.tick(nowMs)
        val s = screen ?: return
        onTick?.invoke(dt)
        s.update(dt)
        gfx.bind(canvas)
        s.draw(gfx)
        val snap = snapshot
        if (snap != null) {
            transition -= dt
            if (transition <= 0f) {
                snap.recycle(); snapshot = null
            } else {
                // Keep fewer and fewer old-frame pixels in Bayer order.
                val keep = ((transition / transitionTime) * 16f).toInt().coerceIn(0, 16)
                val layer = canvas.saveLayer(null, null)
                canvas.drawBitmap(snap, 0f, 0f, null)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), masks[keep])
                canvas.restoreToCount(layer)
            }
        }
    }

    private fun ditherMask(keep: Int): Paint {
        val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
        val tile = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        for (i in 0 until 16) tile.setPixel(i % 4, i / 4, if (bayer[i] < keep) 0xFF000000.toInt() else 0)
        return Paint().apply {
            shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            isFilterBitmap = false
        }
    }
}
