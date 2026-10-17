package com.ditherfarm.ui

import com.ditherfarm.Game
import com.ditherfarm.engine.Camera
import com.ditherfarm.engine.Gfx
import com.ditherfarm.engine.Iso
import com.ditherfarm.engine.Lighting
import com.ditherfarm.engine.Screen
import com.ditherfarm.engine.Weather

/**
 * Engine and art smoke test: an isometric field with plots, crops, day/night
 * lighting and weather. Replaced by the real Farm screen.
 */
class GridDemoScreen(private val game: Game) : Screen() {
    private val cols = 10
    private val rows = 10
    private val cam = Camera()
    private val pick = IntArray(2)
    private var selC = -1
    private var selR = -1
    private val lighting = Lighting()
    val weather = Weather()
    var hour = 10f
    var season = "spring"
    private val lights = ArrayList<Lighting.Light>()

    override fun enter() {
        cam.setBounds(Iso.bounds(cols, rows))
        cam.setView(game.engine.w, game.engine.h, 20, 0)
        val b = Iso.bounds(cols, rows)
        cam.centerOn((b[0] + b[2]) / 2f, (b[1] + b[3]) / 2f)
        weather.onThunder = { game.sfx.play("thunder") }
    }

    override fun update(dt: Float) {
        cam.setView(game.engine.w, game.engine.h, 20 + game.engine.safeTop, 0)
        cam.update(dt)
        weather.update(dt, game.engine.w, game.engine.h)
    }

    private fun ground(c: Int, r: Int): String {
        val v = "abcd"[(c * 7 + r * 13) % 4]
        return when (season) {
            "spring" -> "tiles/grass_$v"
            else -> "tiles/${season}_$v"
        }
    }

    private fun isPlot(c: Int, r: Int) = c in 3..6 && r in 3..6

    override fun draw(g: Gfx) {
        val c = g.canvas
        c.save()
        c.translate(-cam.snapX().toFloat(), -cam.snapY().toFloat())
        c.scale(cam.zoom.toFloat(), cam.zoom.toFloat())
        // Ground pass, then raised plots and standing objects in depth order.
        for (d in 0 until cols + rows - 1) for (col in 0 until cols) {
            val row = d - col
            if (row !in 0 until rows) continue
            g.sprite(ground(col, row), Iso.x(col, row).toFloat(), Iso.y(col, row).toFloat())
        }
        val crops = listOf("wheat", "carrot", "tomato", "pumpkin")
        for (d in 0 until cols + rows - 1) for (col in 0 until cols) {
            val row = d - col
            if (row !in 0 until rows || !isPlot(col, row)) continue
            val x = Iso.x(col, row).toFloat()
            val y = Iso.y(col, row).toFloat()
            val soil = when {
                season == "winter" -> "tiles/soil_snow"
                (col + row) % 3 == 0 -> "tiles/soil_wet"
                else -> "tiles/soil_dry"
            }
            g.sprite(soil, x, y)
            if (col == selC && row == selR) g.sprite("tiles/cursor_ok", x, y)
            if (season != "winter") g.sprite("crops/${crops[(col + row) % 4]}_${(col * 3 + row) % 4}", x, (Iso.centerY(col, row)).toFloat())
        }
        c.restore()

        // Lamps at two plot corners light up at night.
        lights.clear()
        for ((lc, lr) in listOf(3 to 3, 6 to 6)) {
            val bx = (Iso.x(lc, lr) - cam.x) * cam.zoom
            val by = (Iso.centerY(lc, lr) - cam.y) * cam.zoom
            lights.add(Lighting.Light(bx, by, 22 * cam.zoom))
        }
        lighting.apply(c, game.engine.w, game.engine.h, lighting.ambient(hour), lights, weather.flash)
        weather.draw(g, game.engine.w, game.engine.h)

        val top = game.engine.safeTop + 4
        g.text("DITHER FARM", game.engine.w / 2, top, Gfx.GOLD, align = 0)
        g.text("${season.uppercase()}  %02d:00".format(hour.toInt()), game.engine.w / 2, top + 11, Gfx.WHITE, align = 0)
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
}
