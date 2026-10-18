package com.ditherfarm.engine

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * World camera for one screen. Position is the world pixel at the view's top-left,
 * zoom is a whole number so art pixels stay square. Supports fling inertia and
 * clamping to the map bounds (with a little slack so edges can be centred).
 */
class Camera {
    var x = 0f
        private set
    var y = 0f
        private set
    var zoom = 2
        private set
    var minZoom = 1
    var maxZoom = 3

    private var vx = 0f
    private var vy = 0f
    private var bounds = intArrayOf(-1000, -1000, 1000, 1000)
    private var viewW = 1
    private var viewH = 1
    private var topInset = 0
    private var bottomInset = 0

    /** View size in buffer pixels, and HUD heights that should not hide the map. */
    fun setView(w: Int, h: Int, top: Int, bottom: Int) {
        viewW = w; viewH = h; topInset = top; bottomInset = bottom
        clamp()
    }

    fun setBounds(b: IntArray) { bounds = b; clamp() }

    /** When set, the whole view (centre cx, cy and half extents in world px) stays inside this diamond. */
    private var diamond: FloatArray? = null

    fun setDiamond(cx: Float, cy: Float, halfW: Float, halfH: Float) { diamond = floatArrayOf(cx, cy, halfW, halfH); clamp() }

    /** Visible world rectangle size at the current zoom. */
    val viewWidth get() = viewW / zoom.toFloat()
    val viewHeight get() = viewH / zoom.toFloat()

    fun centerOn(wx: Float, wy: Float) {
        x = wx - viewW / (2f * zoom)
        y = wy - (viewH + topInset - bottomInset) / (2f * zoom)
        clamp()
    }

    fun panBy(dxBuffer: Float, dyBuffer: Float) {
        x -= dxBuffer / zoom
        y -= dyBuffer / zoom
        clamp()
    }

    fun fling(vxBufferPerSec: Float, vyBufferPerSec: Float) {
        vx = -vxBufferPerSec / zoom
        vy = -vyBufferPerSec / zoom
    }

    fun stop() { vx = 0f; vy = 0f }

    /** Changes zoom by [steps], keeping the world point under (fx, fy) fixed. */
    fun zoomAt(steps: Int, fx: Float, fy: Float): Boolean {
        val nz = (zoom + steps).coerceIn(minZoom, maxZoom)
        if (nz == zoom) return false
        val wx = x + fx / zoom
        val wy = y + fy / zoom
        zoom = nz
        x = wx - fx / zoom
        y = wy - fy / zoom
        clamp()
        return true
    }

    fun update(dt: Float) {
        if (vx == 0f && vy == 0f) return
        x += vx * dt
        y += vy * dt
        val k = Math.pow(0.04, dt.toDouble()).toFloat()
        vx *= k; vy *= k
        if (abs(vx) < 4f) vx = 0f
        if (abs(vy) < 4f) vy = 0f
        clamp()
    }

    /** Screen (buffer) pixel to world pixel. */
    fun toWorldX(bx: Float) = x + bx / zoom
    fun toWorldY(by: Float) = y + by / zoom

    /** Whole-pixel translation for drawing (prevents shimmering). */
    fun snapX() = (x * zoom).roundToInt()
    fun snapY() = (y * zoom).roundToInt()

    private fun clamp() {
        diamond?.let { d -> clampDiamond(d); return }
        val visW = viewW / zoom.toFloat()
        val visH = (viewH - topInset - bottomInset) / zoom.toFloat()
        val slack = 48f
        val minX = bounds[0] - slack
        val maxX = bounds[2] + slack - visW
        val minY = bounds[1] - slack - topInset / zoom.toFloat()
        val maxY = bounds[3] + slack - visH - topInset / zoom.toFloat()
        x = if (maxX < minX) (bounds[0] + bounds[2]) / 2f - visW / 2f else x.coerceIn(minX, maxX)
        y = if (maxY < minY) (bounds[1] + bounds[3]) / 2f - visH / 2f - topInset / zoom.toFloat() else y.coerceIn(minY, maxY)
    }

    /**
     * Keeps every corner of the view inside the diamond: the view centre must lie in a
     * smaller diamond shrunk by the view's half size, so it is pulled back toward the middle.
     */
    private fun clampDiamond(d: FloatArray) {
        val a = viewWidth / 2f
        val b = viewHeight / 2f
        val k = 1f - a / d[2] - b / d[3]
        if (k <= 0f) { x = d[0] - a; y = d[1] - b; return }
        var dx = x + a - d[0]
        var dy = y + b - d[1]
        val s = abs(dx) / (k * d[2]) + abs(dy) / (k * d[3])
        if (s > 1f) { dx /= s; dy /= s }
        x = d[0] + dx - a
        y = d[1] + dy - b
    }
}
