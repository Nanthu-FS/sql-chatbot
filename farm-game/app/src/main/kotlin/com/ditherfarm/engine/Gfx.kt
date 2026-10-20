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

    fun spriteScaled(name: String, x: Float, y: Float, sx: Float, sy: Float, alpha: Int = 255, filter: android.graphics.ColorFilter? = null) {
        val s = assets[name]
        if (sx == 1f && sy == 1f && filter == null) return sprite(s, x, y, alpha)
        dstF.set(x - s.ax * sx, y - s.ay * sy, x + (s.w - s.ax) * sx, y + (s.h - s.ay) * sy)
        dst.set(Math.round(dstF.left), Math.round(dstF.top), Math.round(dstF.right), Math.round(dstF.bottom))
        src.set(0, 0, s.w, s.h)
        paint.alpha = alpha
        paint.colorFilter = filter
        canvas.drawBitmap(s.bmp, src, dst, paint)
        paint.colorFilter = null
        paint.alpha = 255
    }

    fun rect(l: Int, t: Int, r: Int, b: Int, color: Int) {
        paint.color = color
        canvas.drawRect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat(), paint)
    }

    /**
     * 9-slice draw of a pixel-art frame: corners stay 1:1, edges and center stretch.
     * [b] is the border width in pixels on every side. Small opaque frames whose pieces do not
     * overlap are drawn once into an image and blitted after: the same pixels in one draw, not nine.
     */
    fun slice9(name: String, l: Int, t: Int, r: Int, bottom: Int, b: Int, alpha: Int = 255) {
        val w = r - l
        val h = bottom - t
        if (alpha == 255 && !inWorld && b > 0 && w >= 2 * b && h >= 2 * b && w * h <= FRAME_MAX_AREA) {
            val img = frameImages.getOrPut(FrameKey(name, w, h, b)) {
                val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                nineSlice(Canvas(out), name, 0, 0, w, h, b, 255)
                out
            }
            canvas.drawBitmap(img, l.toFloat(), t.toFloat(), blit)
        } else nineSlice(canvas, name, l, t, r, bottom, b, alpha)
    }

    private fun nineSlice(c: Canvas, name: String, l: Int, t: Int, r: Int, bottom: Int, b: Int, alpha: Int) {
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
            c.drawBitmap(bmp, src, dst, paint)
        }
        paint.alpha = 255
    }

    private data class FrameKey(val name: String, val w: Int, val h: Int, val b: Int)

    /** Rendered small frames, least recently used out first; the oldest is recycled when the cache is full. */
    private val frameImages = object : LinkedHashMap<FrameKey, Bitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<FrameKey, Bitmap>?): Boolean {
            if (size <= FRAME_IMAGES) return false
            eldest?.value?.recycle()
            return true
        }
    }

    fun textWidth(s: String): Int {
        text.typeface = assets.font
        return Math.round(text.measureText(s))
    }

    /**
     * True while world objects are drawn under the map's zoom. Text there is drawn glyph by glyph,
     * scaled with the world; text on the screen itself comes from [text]'s image cache.
     */
    var inWorld = false

    private val blit = Paint().apply { isFilterBitmap = false; isAntiAlias = false }

    /**
     * Pixel text with a 1px dark outline. [align] -1 left, 0 center, 1 right.
     * Silkscreen is drawn at 8px so glyphs land on the pixel grid.
     */
    fun text(s: String, x: Int, y: Int, color: Int = WHITE, align: Int = -1, outline: Int = INK, shadow: Boolean = true) {
        text.typeface = assets.font
        val w = textWidth(s)
        val left = when (align) { 0 -> x - w / 2; 1 -> x - w; else -> x }
        val base = y + 7
        if (!inWorld && isOpaque(color) && (outline == 0 || isOpaque(outline))) {
            // Opaque text on the screen is drawn once into an image and blitted after: the same pixels, one draw.
            val img = textImage(s, color, outline, shadow)
            canvas.drawBitmap(img, (left - TEXT_PAD_X).toFloat(), (base - TEXT_PAD_Y).toFloat(), blit)
        } else drawTextLines(canvas, s, left.toFloat(), base.toFloat(), color, outline, shadow)
    }

    /** The glyph draws of one line anchored at ([left], [base]): shadow, outline ring, then the text. */
    private fun drawTextLines(c: Canvas, s: String, left: Float, base: Float, color: Int, outline: Int, shadow: Boolean) {
        text.typeface = assets.font
        if (shadow) {
            text.color = outline
            c.drawText(s, left, base + 2, text)
            c.drawText(s, left + 1, base + 2, text)
        }
        if (outline != 0) {
            text.color = outline
            for (oy in -1..1) for (ox in -1..1) if (ox != 0 || oy != 0) c.drawText(s, left + ox, base + oy, text)
        }
        text.color = color
        c.drawText(s, left, base, text)
    }

    private data class TextKey(val s: String, val color: Int, val outline: Int, val shadow: Boolean)

    /** Rendered text images, least recently used first out; the oldest is recycled when the cache is full. */
    private val textImages = object : LinkedHashMap<TextKey, Bitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TextKey, Bitmap>?): Boolean {
            if (size <= TEXT_IMAGES) return false
            eldest?.value?.recycle()
            return true
        }
    }

    /** The line of text drawn at the origin with a [TEXT_PAD_X] x [TEXT_PAD_Y] margin, made once per text and style. */
    private fun textImage(s: String, color: Int, outline: Int, shadow: Boolean): Bitmap =
        textImages.getOrPut(TextKey(s, color, outline, shadow)) {
            val bmp = Bitmap.createBitmap(textWidth(s) + 2 * TEXT_PAD_X, 2 * TEXT_PAD_Y, Bitmap.Config.ARGB_8888)
            drawTextLines(Canvas(bmp), s, TEXT_PAD_X.toFloat(), TEXT_PAD_Y.toFloat(), color, outline, shadow)
            bmp
        }

    private fun isOpaque(color: Int) = color ushr 24 == 0xFF

    companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val INK = 0xFF181425.toInt()
        const val GOLD = 0xFFFEE761.toInt()
        const val RED = 0xFFFF0044.toInt()
        const val GREEN = 0xFF63C74D.toInt()
        const val BLUE = 0xFF2CE8F5.toInt()
        const val GREY = 0xFF8B9BB4.toInt()
        /** Grey that reads on the cream panels (GREY is for dark backgrounds). */
        const val MUTED = 0xFF5A6988.toInt()

        /** Margin round a cached text image: the outline reaches 1 px out, the shadow 2 px down, ink sits within 12 px of the baseline. */
        const val TEXT_PAD_X = 4
        const val TEXT_PAD_Y = 12
        /** Most text images kept at once (a few KB each). */
        const val TEXT_IMAGES = 256
        /** Frames up to 64 x 64 px are cached as images (at most 16 KB each); bigger ones are drawn piece by piece. */
        const val FRAME_MAX_AREA = 64 * 64
        const val FRAME_IMAGES = 256

        fun newBuffer(w: Int, h: Int): Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    }
}
