package com.ditherfarm.ui

import com.ditherfarm.Game
import com.ditherfarm.engine.Gfx
import com.ditherfarm.engine.Iso
import com.ditherfarm.game.FishDef
import com.ditherfarm.game.cast
import com.ditherfarm.game.fishBlocker
import com.ditherfarm.game.landFish
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fishing on the farm river (round 6): a tap on the water casts the float; after a wait it dips
 * ("TAP NOW!") and a tap hooks the fish; then a marker sweeps a bar and a tap while it is in the
 * green lands it. A slow tap at the bite, a tap outside the green, or three sweeps without a tap
 * and it gets away. Which fish it is was decided at the cast (game/Fishing.kt).
 */
class Fishing(private val game: Game, private val toast: (String) -> Unit) {
    enum class State { IDLE, CAST, WAIT, BITE, REEL }

    var state = State.IDLE
        private set
    val active get() = state != State.IDLE
    private var fish: FishDef? = null
    private var t = 0f
    private var waitFor = 0f
    private var meter = 0f
    private var zoneAt = 0.5f
    private var c = 0
    private var r = 0
    private val world get() = game.world

    /** Casts at water tile (c, r); false (with a toast) if fishing is not possible now. */
    fun start(c: Int, r: Int): Boolean {
        if (active) return false
        world.fishBlocker()?.let { toast(it); game.sfx.play("error"); return false }
        fish = world.cast() ?: return false
        this.c = c; this.r = r
        state = State.CAST; t = 0f
        waitFor = 1.5f + Random.nextFloat() * 2.5f
        game.sfx.play("swap")
        return true
    }

    fun cancel() { state = State.IDLE; fish = null }

    private fun lose(why: String) { toast(why); game.sfx.play("error"); cancel() }

    fun update(dt: Float) {
        if (!active) return
        t += dt
        when (state) {
            State.CAST -> if (t >= 0.4f) { state = State.WAIT; t = 0f }
            State.WAIT -> if (t >= waitFor) { state = State.BITE; t = 0f; game.sfx.play("order"); game.haptics.heavy() }
            State.BITE -> if (t >= BITE_WINDOW) lose("Too slow: it got away")
            State.REEL -> { meter += dt * (fish?.speed ?: 1f); if (meter >= 6f) lose("It got away!") }
            State.IDLE -> {}
        }
    }

    private val marker get() = (meter % 2f).let { if (it < 1f) it else 2f - it }
    private fun inZone(): Boolean = fish?.let { f -> kotlin.math.abs(marker - zoneAt) <= f.zone / 2f } ?: false

    /** Any tap on the farm while fishing comes here. */
    fun tap() {
        when (state) {
            State.CAST, State.WAIT -> toast("Wait for the float to dip")
            State.BITE -> {
                state = State.REEL; meter = 0f; t = 0f
                zoneAt = 0.3f + Random.nextFloat() * 0.4f
                game.haptics.tick()
            }
            State.REEL -> {
                val f = fish ?: return cancel()
                if (inZone()) { world.landFish(f); cancel() } else lose("It got away!")
            }
            State.IDLE -> {}
        }
    }

    /** The float on the water (and its ring), sorted with the world. */
    fun collect(list: DrawList) {
        if (!active) return
        val g = game.gfx
        val x = Iso.x(c, r).toFloat()
        val y = Iso.centerY(c, r).toFloat()
        list.add(y) {
            when (state) {
                State.CAST -> {
                    // The float flies in from below the view and lands with a ring.
                    val k = (t / 0.4f).coerceIn(0f, 1f)
                    g.sprite("fishing/float", x, y + (1f - k) * 40f - sin(k * Math.PI.toFloat()) * 24f)
                }
                State.WAIT -> {
                    g.sprite("fishing/ring", x, y + 1f, (120 + sin(t * 2f) * 60).toInt())
                    g.sprite("fishing/float", x, y + sin(t * 3f) * 0.8f)
                }
                State.BITE, State.REEL -> {
                    g.sprite("fishing/ring", x, y + 1f)
                    g.sprite("fishing/float_bite", x, y + 1f)
                    if (state == State.BITE) g.text("!", x.toInt(), (y - 16 + sin(t * 20f)).toInt(), Gfx.GOLD, align = 0)
                }
                State.IDLE -> {}
            }
        }
    }

    /** The banner above the nav bar: what to do now, and the bar while reeling. */
    fun drawUi(g: Gfx, ui: Ui, bottom: Int) {
        if (!active) return
        val w = game.engine.w
        val y = bottom - 34
        ui.panel(2, y, w - 4, 30, dark = true)
        g.sprite("icons/tool_rod", 16f, y + 15f)
        when (state) {
            State.CAST, State.WAIT -> {
                g.text("FISHING...", 32, y + 5, Gfx.GOLD)
                g.text("WAIT FOR THE FLOAT TO DIP", 32, y + 17, Gfx.WHITE)
            }
            State.BITE -> {
                g.text("A BITE!", 32, y + 5, Gfx.GOLD)
                g.text("TAP NOW!", 32, y + 17, if ((t * 8).toInt() % 2 == 0) Gfx.WHITE else Gfx.GOLD)
            }
            State.REEL -> {
                val f = fish ?: return
                g.text("TAP IN THE GREEN!", 32, y + 4, Gfx.GOLD)
                val bx = 32; val bw = w - 64; val by = y + 17
                g.rect(bx - 1, by - 1, bx + bw + 1, by + 7, Gfx.INK)
                g.rect(bx, by, bx + bw, by + 6, 0xFF3A4466.toInt())
                val z0 = bx + ((zoneAt - f.zone / 2f) * bw).toInt()
                val z1 = bx + ((zoneAt + f.zone / 2f) * bw).toInt()
                g.rect(z0, by, z1, by + 6, 0xFF63C74D.toInt())
                val mx = bx + (marker * bw).toInt()
                g.rect(mx - 1, by - 3, mx + 1, by + 9, Gfx.WHITE)
            }
            State.IDLE -> {}
        }
        if (ui.button("fish-cancel", w - 22, y + 4, 16, 12, null, "ui/x", "red")) cancel()
    }

    companion object {
        /** Seconds to tap after the float dips. */
        const val BITE_WINDOW = 1.2f
    }
}
