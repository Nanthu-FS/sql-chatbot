package com.ditherfarm.ui

import com.ditherfarm.Game
import com.ditherfarm.engine.Gfx
import com.ditherfarm.engine.Iso
import com.ditherfarm.engine.Lighting
import com.ditherfarm.engine.Noise
import com.ditherfarm.engine.Terrain
import kotlin.math.sin

/**
 * Engine and art smoke test on top of [IsoMapScreen]: a painted island with a pond,
 * path, raised plots, fences, lanterns, swaying decorations and a sample HUD.
 * Replaced by the real Farm screen.
 */
class GridDemoScreen(game: Game) : IsoMapScreen(game, groundSeed = 21) {
    override val cols = 16
    override val rows = 16
    var hourOverride = 10f
    var season = "spring"
    private var selC = -1
    private var selR = -1

    private class Prop(val sprite: String, val x: Float, val y: Float, val sway: Boolean = false)
    private val props = ArrayList<Prop>()
    private val lanterns = listOf(4 to 3, 9 to 8)

    private fun isPlot(c: Int, r: Int) = c in 5..8 && r in 4..7

    override fun terrain(c: Int, r: Int): Terrain {
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

    override fun seasonIndex() = when (season) { "summer" -> 1; "fall" -> 2; "winter" -> 3; else -> 0 }
    override fun hour() = hourOverride

    override fun enter() {
        super.enter()
        centerOnTile(7, 6)
        weather.onThunder = { game.sfx.play("thunder") }
        props.clear()
        for (r in 0 until rows) for (c in 0 until cols) {
            if (isPlot(c, r) || (c to r) in lanterns) continue
            val roll = Noise.rand(c, r, 77)
            val x = Iso.x(c, r) + (Noise.rand(c, r, 78) - 0.5f) * 14f
            val y = Iso.centerY(c, r) + (Noise.rand(c, r, 79) - 0.5f) * 6f
            val sprite = when (terrain(c, r)) {
                Terrain.GRASS -> when {
                    roll < 0.16f -> "deco/tallgrass_a"
                    roll < 0.22f -> listOf("red", "white", "pink", "blue", "purple").let { "deco/flowers_" + it[(roll * 1000).toInt() % 5] }
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
            props.add(Prop(sprite, x, y, sway = sprite == "deco/tallgrass_a"))
        }
        for ((c, r) in lanterns) props.add(Prop("deco/lantern", Iso.x(c, r).toFloat(), Iso.centerY(c, r).toFloat()))
        for (c in 4..9) props.add(Prop("deco/fence_r", Iso.x(c, 3).toFloat(), Iso.y(c, 3).toFloat()))
        for (r in 3..8) props.add(Prop("deco/fence_l", Iso.x(4, r).toFloat(), Iso.y(4, r).toFloat()))
    }

    override fun drawGroundLayer(g: Gfx) {
        for (r in 0 until rows) for (c in 0 until cols) {
            if (terrain(c, r) == Terrain.WATER) {
                val phase = (time * 2f + c * 0.7f + r * 1.3f) % 3f
                if (phase < 0.6f) g.rect(Iso.x(c, r) - 3 + (phase * 8).toInt(), Iso.centerY(c, r), Iso.x(c, r) + (phase * 8).toInt(), Iso.centerY(c, r) + 1, Gfx.WHITE)
            }
            if (isPlot(c, r)) g.sprite("tiles/shadow", Iso.x(c, r) + 2f, Iso.y(c, r) + 3f)
        }
    }

    override fun collect(list: DrawList) {
        val winter = season == "winter"
        for (p in props) {
            if (winter && (p.sprite.startsWith("deco/flowers") || p.sprite == "deco/mushrooms")) continue
            var sprite = if (p.sway && sin(time * 2.2f + p.x * 0.15f) > 0.3f) "deco/tallgrass_b" else p.sprite
            if (winter && game.assets.has("${sprite}_snow")) sprite = "${sprite}_snow"
            list.add(p.y + p.x * 0.001f) { game.gfx.sprite(sprite, p.x, p.y) }
        }
        val crops = listOf("wheat", "carrot", "potato", "tomato", "corn", "strawberry", "pumpkin", "sunflower", "cabbage", "eggplant", "melon", "grapes")
        for (row in 0 until rows) for (col in 0 until cols) {
            if (!isPlot(col, row)) continue
            val x = Iso.x(col, row).toFloat()
            val y = Iso.y(col, row).toFloat()
            val soil = when {
                winter -> "tiles/soil_snow"
                (col + row) % 3 == 0 -> "tiles/soil_wet"
                else -> "tiles/soil_dry"
            }
            val crop = "crops/${crops[(col * 4 + row) % crops.size]}_${(col * 3 + row) % 4}"
            list.add(y + 0.5f) {
                game.gfx.sprite(soil, x, y)
                if (col == selC && row == selR) game.gfx.sprite("tiles/cursor_ok", x, y)
                if (!winter && game.assets.has(crop)) game.gfx.sprite(crop, x, Iso.centerY(col, row).toFloat())
            }
        }
        if (!winter) for (i in 0 until 3) {
            val bx = Iso.x(11, 10) + sin(time * 0.7f + i * 2f) * 40f + i * 10
            val by = Iso.y(11, 10) + sin(time * 1.1f + i) * 14f - 10 - i * 6
            list.add(by + 40f) { game.gfx.sprite(if (sin(time * 18f + i) > 0) "deco/butterfly_a" else "deco/butterfly_b", bx, by) }
        }
    }

    override fun worldLights(out: MutableList<Lighting.Light>) {
        for ((c, r) in lanterns) out.add(Lighting.Light(Iso.x(c, r).toFloat(), Iso.centerY(c, r).toFloat(), 26))
    }

    override fun drawHud(g: Gfx) {
        val w = game.engine.w
        val top = game.engine.safeTop + 3
        ui.pill(3, top, 66)
        g.sprite(if (game.assets.has("icons/coin")) "icons/coin" else "ui/dot", 12f, top + 6f)
        g.text("1,250", 21, top + 2, Gfx.GOLD)
        ui.pill(w - 92, top, 89)
        g.text("${season.uppercase()} 3", w - 86, top + 2, Gfx.WHITE)
        g.text("%02d:00".format(hourOverride.toInt()), w - 6, top + 2, Gfx.WHITE, align = 1)
        val by = game.engine.h - game.engine.safeBottom - 40
        ui.panel(2, by, w - 4, 38)
        val labels = listOf("FARM", "WOODS", "MINE", "BAG")
        labels.forEachIndexed { i, label ->
            val bw = (w - 16) / labels.size
            if (ui.button("nav$i", 8 + i * bw, by + 9, bw - 4, 20, label, style = if (i == 0) "gold" else "green")) game.sfx.play("swap")
        }
    }

    override fun onTileTap(c: Int, r: Int) {
        selC = c; selR = r
        game.sfx.play("click")
        game.haptics.tick()
        fx.burst("deco/butterfly_a", Iso.x(c, r).toFloat(), Iso.centerY(c, r).toFloat(), 0)
        fx.floatText("+1", Iso.x(c, r).toFloat(), Iso.y(c, r).toFloat() - 10, Gfx.GOLD)
    }
}
