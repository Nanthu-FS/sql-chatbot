package com.ditherfarm.ui

import android.graphics.Bitmap
import com.ditherfarm.Game
import com.ditherfarm.engine.Camera
import com.ditherfarm.engine.Gfx
import com.ditherfarm.engine.Iso
import com.ditherfarm.engine.Screen

/**
 * Engine smoke test: a procedurally dithered isometric field with pan, fling,
 * pinch zoom and tap highlight. Replaced by the real Farm screen.
 */
class GridDemoScreen(private val game: Game) : Screen() {
    private val cols = 10
    private val rows = 10
    private val cam = Camera()
    private val pick = IntArray(2)
    private var selC = -1
    private var selR = -1
    private val grass = diamond(0xFF63C74D.toInt(), 0xFF3E8948.toInt())
    private val soil = diamond(0xFFB86F50.toInt(), 0xFF733E39.toInt())
    private val hilite = diamond(0x66FFFFFF, 0x33FFFFFF)

    override fun enter() {
        cam.setBounds(Iso.bounds(cols, rows))
        cam.setView(game.engine.w, game.engine.h, 20, 0)
        val b = Iso.bounds(cols, rows)
        cam.centerOn((b[0] + b[2]) / 2f, (b[1] + b[3]) / 2f)
    }

    override fun update(dt: Float) {
        cam.setView(game.engine.w, game.engine.h, 20 + game.engine.safeTop, 0)
        cam.update(dt)
    }

    override fun draw(g: Gfx) {
        val c = g.canvas
        c.save()
        c.translate(-cam.snapX().toFloat(), -cam.snapY().toFloat())
        c.scale(cam.zoom.toFloat(), cam.zoom.toFloat())
        for (d in 0 until cols + rows - 1) for (col in 0 until cols) {
            val row = d - col
            if (row !in 0 until rows) continue
            val bmp = if ((col in 3..6) && (row in 3..6)) soil else grass
            c.drawBitmap(bmp, (Iso.x(col, row) - Iso.HW).toFloat(), Iso.y(col, row).toFloat(), g.paint)
            if (col == selC && row == selR) c.drawBitmap(hilite, (Iso.x(col, row) - Iso.HW).toFloat(), Iso.y(col, row).toFloat(), g.paint)
        }
        c.restore()
        val top = game.engine.safeTop + 4
        g.text("DITHER FARM", game.engine.w / 2, top, Gfx.GOLD, align = 0)
        g.text("DRAG  PINCH  TAP", game.engine.w / 2, top + 11, Gfx.WHITE, align = 0)
    }

    override fun tap(x: Float, y: Float) {
        Iso.pick(cam.toWorldX(x), cam.toWorldY(y), pick)
        if (pick[0] in 0 until cols && pick[1] in 0 until rows) {
            selC = pick[0]; selR = pick[1]
            game.sfx.play("click")
            game.haptics.tick()
        }
    }

    override fun panStart() = cam.stop()
    override fun pan(dx: Float, dy: Float) = cam.panBy(dx, dy)
    override fun fling(vx: Float, vy: Float) = cam.fling(vx, vy)
    override fun pinch(steps: Int, fx: Float, fy: Float) { cam.zoomAt(steps, fx, fy) }

    /** A 32x16 diamond with a 2-colour checker dither in the lower half. */
    private fun diamond(light: Int, dark: Int): Bitmap {
        val b = Bitmap.createBitmap(Iso.TW, Iso.TH, Bitmap.Config.ARGB_8888)
        for (y in 0 until Iso.TH) {
            val half = if (y < Iso.HH) (y + 1) * 2 else (Iso.TH - y) * 2
            for (x in Iso.HW - half until Iso.HW + half) {
                val shadeDark = y >= Iso.HH && (x + y) % 2 == 0
                val edge = x == Iso.HW - half || x == Iso.HW + half - 1
                b.setPixel(x, y, if (edge) 0xFF181425.toInt() else if (shadeDark) dark else light)
            }
        }
        return b
    }
}
