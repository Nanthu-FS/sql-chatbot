package com.ditherfarm.ui

import android.graphics.Bitmap
import com.ditherfarm.Game
import com.ditherfarm.engine.Camera
import com.ditherfarm.engine.Gfx
import com.ditherfarm.engine.GroundPainter
import com.ditherfarm.engine.Iso
import com.ditherfarm.engine.Lighting
import com.ditherfarm.engine.Noise
import com.ditherfarm.engine.Screen
import com.ditherfarm.engine.Terrain
import com.ditherfarm.engine.Weather
import kotlin.math.sin

/**
 * Engine and art smoke test: a painted island with a pond, path, raised plots,
 * fences, lanterns and swaying decorations, plus lighting and weather.
 * Replaced by the real Farm screen.
 */
class GridDemoScreen(private val game: Game) : Screen() {
    private val cols = 16
    private val rows = 16
    private val cam = Camera()
    private val pick = IntArray(2)
    private var selC = -1
    private var selR = -1
    private val lighting = Lighting()
    private val painter = GroundPainter(seed = 21)
    private var ground: Bitmap? = null
    private var groundSeason = -1
    val weather = Weather()
    var hour = 10f
    var season = "spring"
    private var time = 0f
    private val lights = ArrayList<Lighting.Light>()

    private class Prop(val sprite: String, val x: Float, val y: Float, val sway: Boolean = false, val alt: String? = null)
    private val props = ArrayList<Prop>()
    private val lanterns = listOf(4 to 3, 9 to 8)

    private fun isPlot(c: Int, r: Int) = c in 5..8 && r in 4..7

    private fun terrain(c: Int, r: Int): Terrain {
        val dx = c - 12; val dy = r - 3
        return when {
            dx * dx + dy * dy <= 5 -> Terrain.WATER
            dx * dx + dy * dy <= 9 -> Terrain.SAND
            c + r < 4 -> Terrain.FOREST
            r == 11 || (c == 6 && r > 8) -> Terrain.PATH
            c in 4..9 && r in 3..8 -> Terrain.DIRT
            else -> Terrain.GRASS
        }
    }

    private fun seasonIndex() = when (season) { "summer" -> 1; "fall" -> 2; "winter" -> 3; else -> 0 }

    override fun enter() {
        cam.setBounds(Iso.bounds(cols, rows))
        cam.setView(game.engine.w, game.engine.h, 20, 0)
        val b = Iso.bounds(cols, rows)
        cam.centerOn(Iso.x(7, 6).toFloat(), Iso.centerY(7, 6).toFloat())
        weather.onThunder = { game.sfx.play("thunder") }
        scatter()
    }

    private fun scatter() {
        props.clear()
        for (r in 0 until rows) for (c in 0 until cols) {
            val t = terrain(c, r)
            if (isPlot(c, r) || (c to r) in lanterns) continue
            val roll = Noise.rand(c, r, 77)
            val jx = (Noise.rand(c, r, 78) - 0.5f) * 14f
            val jy = (Noise.rand(c, r, 79) - 0.5f) * 6f
            val x = Iso.x(c, r) + jx
            val y = Iso.centerY(c, r) + jy
            val sprite = when (t) {
                Terrain.GRASS -> when {
                    roll < 0.16f -> "deco/tallgrass_a"
                    roll < 0.22f -> listOf("deco/flowers_red", "deco/flowers_white", "deco/flowers_pink", "deco/flowers_blue", "deco/flowers_purple")[(roll * 1000).toInt() % 5]
                    roll < 0.25f -> if (roll < 0.235f) "deco/bush_berry" else "deco/bush"
                    roll < 0.27f -> "deco/rock"
                    roll < 0.28f -> "deco/rock_big"
                    else -> null
                }
                Terrain.FOREST -> when {
                    roll < 0.25f -> "deco/bush"
                    roll < 0.35f -> "deco/mushrooms"
                    roll < 0.45f -> "deco/stump"
                    roll < 0.5f -> "deco/log"
                    else -> "deco/tallgrass_a"
                }
                Terrain.SAND -> if (roll < 0.35f) "deco/reeds" else null
                else -> null
            } ?: continue
            props.add(Prop(sprite, x, y, sway = sprite == "deco/tallgrass_a", alt = "deco/tallgrass_b"))
        }
        for ((c, r) in lanterns) props.add(Prop("deco/lantern", Iso.x(c, r).toFloat(), Iso.centerY(c, r).toFloat()))
        // Fence along the plot yard's back edges.
        for (c in 4..9) props.add(Prop("deco/fence_r", Iso.x(c, 3).toFloat(), Iso.y(c, 3).toFloat()))
        for (r in 3..8) props.add(Prop("deco/fence_l", Iso.x(4, r).toFloat() , Iso.y(4, r).toFloat()))
    }

    override fun update(dt: Float) {
        time += dt
        cam.setView(game.engine.w, game.engine.h, 20 + game.engine.safeTop, 0)
        cam.update(dt)
        weather.update(dt, game.engine.w, game.engine.h)
    }

    override fun draw(g: Gfx) {
        val c = g.canvas
        val si = seasonIndex()
        if (ground == null || groundSeason != si) {
            ground?.recycle()
            ground = painter.paint(cols, rows, si) { cc, rr -> terrain(cc, rr) }
            groundSeason = si
        }
        c.save()
        c.translate(-cam.snapX().toFloat(), -cam.snapY().toFloat())
        c.scale(cam.zoom.toFloat(), cam.zoom.toFloat())
        c.drawBitmap(ground!!, -painter.originX.toFloat(), 0f, g.paint)

        // Water glints.
        for (r in 0 until rows) for (cc in 0 until cols) if (terrain(cc, r) == Terrain.WATER) {
            val phase = ((time * 2f + cc * 0.7f + r * 1.3f) % 3f)
            if (phase < 0.6f) g.rect(Iso.x(cc, r) - 3 + (phase * 8).toInt(), Iso.centerY(cc, r), Iso.x(cc, r) + (phase * 8).toInt(), Iso.centerY(cc, r) + 1, 0xFFFFFFFF.toInt())
        }

        // Plots first (raised beds), with a soft shadow on their lower-right.
        val crops = listOf("wheat", "carrot", "tomato", "pumpkin")
        for (d in 0 until cols + rows - 1) for (col in 0 until cols) {
            val row = d - col
            if (row !in 0 until rows || !isPlot(col, row)) continue
            val x = Iso.x(col, row).toFloat()
            val y = Iso.y(col, row).toFloat()
            g.sprite("tiles/shadow", x + 2, y + 3)
        }
        // Everything standing, sorted by ground depth.
        data class D(val key: Float, val draw: () -> Unit)
        val list = ArrayList<D>(props.size + 32)
        for (p in props) {
            val sprite = if (p.sway && sin(time * 2.2f + p.x * 0.15f) > 0.3f) p.alt!! else p.sprite
            list.add(D(p.y + p.x * 0.001f) { g.sprite(sprite, p.x, p.y) })
        }
        for (row in 0 until rows) for (col in 0 until cols) {
            if (!isPlot(col, row)) continue
            val x = Iso.x(col, row).toFloat()
            val y = Iso.y(col, row).toFloat()
            val soil = when {
                season == "winter" -> "tiles/soil_snow"
                (col + row) % 3 == 0 -> "tiles/soil_wet"
                else -> "tiles/soil_dry"
            }
            list.add(D(Iso.y(col, row).toFloat() + 0.5f) {
                g.sprite(soil, x, y)
                if (col == selC && row == selR) g.sprite("tiles/cursor_ok", x, y)
                val crop = "crops/${crops[(col + row) % 4]}_${(col * 3 + row) % 4}"
                if (season != "winter" && game.assets.has(crop)) g.sprite(crop, x, Iso.centerY(col, row).toFloat())
            })
        }
        list.sortBy { it.key }
        for (d in list) d.draw()

        // Butterflies over the meadow.
        if (season != "winter") for (i in 0 until 3) {
            val bx = Iso.x(11, 10) + sin(time * 0.7f + i * 2f) * 40f + i * 10
            val by = Iso.y(11, 10) + sin(time * 1.1f + i) * 14f - 10 - i * 6
            g.sprite(if (sin(time * 18f + i) > 0) "deco/butterfly_a" else "deco/butterfly_b", bx, by)
        }
        c.restore()

        lights.clear()
        for ((lc, lr) in lanterns) {
            val bx = (Iso.x(lc, lr) - cam.x) * cam.zoom
            val by = (Iso.centerY(lc, lr) - cam.y) * cam.zoom
            lights.add(Lighting.Light(bx, by, 26 * cam.zoom))
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
