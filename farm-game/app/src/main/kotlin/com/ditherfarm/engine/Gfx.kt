package com.ditherfarm.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/**
 * Drawing helpers for the low-res buffer. Everything is nearest-neighbour and
 * snapped to whole pixels so the art stays crisp.
 */
class Gfx(val assets: Assets) {
    val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
    private val text = Paint().apply { isAntiAlias = false; isFilterBitmap = false; textSize = 8f }
    private val src = Rect()
    private val dst = Rect()
    private val dstF = RectF()
    lateinit var canvas: Canvas

    fun bind(c: Canvas) { canvas = c }

    fun sprite(name: String, x: Float, y: Float, alpha: Int = 255, flipX: Boolean = false) =
        sprite(assets[name], x, y, alpha, flipX)

    fun sprite(s: Sprite, x: Float, y: Float, alpha: Int = 255, flipX: Boolean = false) {
        val px = Math.round(x) - (if (flipX) s.w - s.ax else s.ax)
        val py = Math.round(y) - s.ay
        paint.alpha = alpha
        if (flipX) {
            canvas.save()
            canvas.scale(-1f, 1f, px + s.w / 2f, 0f)
            canvas.drawBitmap(s.bmp, px.toFloat(), py.toFloat(), paint)
            canvas.restore()
        } else {
            canvas.drawBitmap(s.bmp, px.toFloat(), py.toFloat(), paint)
        }
        paint.alpha = 255
    }

    /** Draws a sprite scaled around its pivot (for squash/pop effects). */
    /** A sprite drawn through a colour filter (e.g. darkened scenery). */
    fun spriteFiltered(name: String, x: Float, y: Float, filter: android.graphics.ColorFilter) {
        paint.colorFilter = filter
        sprite(assets[name], x, y)
        paint.colorFilter = null
    }

    fun spriteScaled(name: String, x: Float, y: Float, sx: Float, sy: Float, alpha: Int = 255) {
        val s = assets[name]
        if (sx == 1f && sy == 1f) return sprite(s, x, y, alpha)
        dstF.set(x - s.ax * sx, y - s.ay * sy, x + (s.w - s.ax) * sx, y + (s.h - s.ay) * sy)
        dst.set(Math.round(dstF.left), Math.round(dstF.top), Math.round(dstF.right), Math.round(dstF.bottom))
        src.set(0, 0, s.w, s.h)
        paint.alpha = alpha
        canvas.drawBitmap(s.bmp, src, dst, paint)
        paint.alpha = 255
    }

    fun rect(l: Int, t: Int, r: Int, b: Int, color: Int) {
        paint.color = color
        canvas.drawRect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat(), paint)
    }

    /**
     * 9-slice draw of a pixel-art frame: corners stay 1:1, edges and center stretch.
     * [b] is the border width in pixels on every side.
     */
    fun slice9(name: String, l: Int, t: Int, r: Int, bottom: Int, b: Int, alpha: Int = 255) {
        val bmp = assets[name].bmp
        val w = bmp.width
        val h = bmp.height
        val xs = intArrayOf(0, b, w - b, w)
        val ys = intArrayOf(0, b, h - b, h)
        val dx = intArrayOf(l, l + b, r - b, r)
        val dy = intArrayOf(t, t + b, bottom - b, bottom)
        paint.alpha = alpha
        for (j in 0..2) for (i in 0..2) {
            if (dx[i + 1] <= dx[i] || dy[j + 1] <= dy[j]) continue
            src.set(xs[i], ys[j], xs[i + 1], ys[j + 1])
            dst.set(dx[i], dy[j], dx[i + 1], dy[j + 1])
            canvas.drawBitmap(bmp, src, dst, paint)
        }
        paint.alpha = 255
    }

    fun textWidth(s: String): Int {
        text.typeface = assets.font
        return Math.round(text.measureText(s))
    }

    /**
     * Pixel text with a 1px dark outline. [align] -1 left, 0 center, 1 right.
     * Silkscreen is drawn at 8px so glyphs land on the pixel grid.
     */
    fun text(s: String, x: Int, y: Int, color: Int = WHITE, align: Int = -1, outline: Int = INK, shadow: Boolean = true) {
        text.typeface = assets.font
        val w = textWidth(s)
        val left = when (align) { 0 -> x - w / 2; 1 -> x - w; else -> x }.toFloat()
        val base = (y + 7).toFloat()
        if (shadow) {
            text.color = outline
            canvas.drawText(s, left, base + 2, text)
            canvas.drawText(s, left + 1, base + 2, text)
        }
        if (outline != 0) {
            text.color = outline
            for (oy in -1..1) for (ox in -1..1) if (ox != 0 || oy != 0) canvas.drawText(s, left + ox, base + oy, text)
        }
        text.color = color
        canvas.drawText(s, left, base, text)
    }

    companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val INK = 0xFF181425.toInt()
        const val GOLD = 0xFFFEE761.toInt()
        const val RED = 0xFFFF0044.toInt()
        const val GREEN = 0xFF63C74D.toInt()
        const val BLUE = 0xFF2CE8F5.toInt()
        const val GREY = 0xFF8B9BB4.toInt()

        fun newBuffer(w: Int, h: Int): Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    }
}
