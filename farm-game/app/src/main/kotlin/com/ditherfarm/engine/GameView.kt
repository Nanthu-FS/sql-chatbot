package com.ditherfarm.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Renders the game into a small bitmap (about 280 px wide) and blits it to the
 * screen at a whole-number scale with no filtering, so every art pixel is a crisp square.
 */
@SuppressLint("ViewConstructor")
class GameView(ctx: Context, private val engine: Engine, private val onResized: (Int, Int) -> Unit) : View(ctx) {
    private var buffer: Bitmap? = null
    private var bufferCanvas: Canvas? = null
    private var scale = 4
    private val dst = Rect()
    private val blit = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private var lastNs = 0L
    private var cutoutTop = 0
    private var cutoutBottom = 0

    val frame: Bitmap? get() = buffer

    init {
        isFocusable = true
        keepScreenOn = false
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val cut = insets.displayCutout
        val bars = insets.getInsets(WindowInsets.Type.systemBars())
        cutoutTop = maxOf(cut?.safeInsetTop ?: 0, bars.top)
        cutoutBottom = maxOf(cut?.safeInsetBottom ?: 0, bars.bottom)
        updateSafeArea()
        return super.onApplyWindowInsets(insets)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w == 0 || h == 0) return
        scale = (w / 280f).roundToInt().coerceAtLeast(2)
        val bw = ceil(w / scale.toFloat()).toInt()
        val bh = ceil(h / scale.toFloat()).toInt()
        buffer?.recycle()
        val b = Gfx.newBuffer(bw, bh)
        buffer = b
        bufferCanvas = Canvas(b)
        dst.set(0, 0, bw * scale, bh * scale)
        engine.router.scale = scale.toFloat()
        engine.resize(bw, bh)
        engine.frameBitmap = b
        updateSafeArea()
        onResized(bw, bh)
    }

    private fun updateSafeArea() {
        engine.safeTop = ceil(cutoutTop / scale.toFloat()).toInt()
        engine.safeBottom = ceil(cutoutBottom / scale.toFloat()).toInt()
    }

    override fun onDraw(canvas: Canvas) {
        val b = buffer ?: return
        val c = bufferCanvas ?: return
        val now = System.nanoTime()
        val dt = if (lastNs == 0L) 1f / 60f else ((now - lastNs) / 1e9f).coerceIn(0f, 0.1f)
        lastNs = now
        c.drawColor(0xFF181425.toInt())
        engine.frame(dt, c, SystemClock.uptimeMillis())
        canvas.drawBitmap(b, null, dst, blit)
        postInvalidateOnAnimation()
    }

    /** Called when the app returns from the background so dt does not jump. */
    fun resetClock() { lastNs = 0L }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = engine.router.onTouch(event)
}
