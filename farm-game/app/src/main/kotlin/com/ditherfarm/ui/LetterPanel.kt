package com.ditherfarm.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import com.ditherfarm.Game
import com.ditherfarm.engine.Gfx
import com.ditherfarm.game.LETTERS
import com.ditherfarm.game.letterById
import com.ditherfarm.game.openLetter
import com.ditherfarm.game.storyText
import kotlin.math.cos
import kotlin.math.sin

/**
 * One of Grandpa's letters (round 7). A new one arrives as a sealed envelope: it drops in, the wax
 * seal cracks, the flap opens and the letter slides out, then his words write themselves onto the
 * old paper, signed, with his photo taped in the corner. A letter read again opens straight away.
 * The list in the menu holds every letter opened so far.
 */
class LetterPanel(private val game: Game, private val hud: Hud) {
    private val ui get() = game.ui
    private val g get() = game.gfx
    private val world get() = game.world

    private var id = ""
    private var t = 0f
    private var shown = 0f
    private var sealCracked = false

    fun start(letter: String, reread: Boolean) {
        id = letter
        t = if (reread) PAPER_IN else 0f
        shown = if (reread) 0f else 0f
        sealCracked = reread
        world.openLetter(letter)
        game.sfx.play("open")
    }

    fun update(dt: Float) {
        t += dt
        if (t >= PAPER_IN) shown += dt * CPS
    }

    /** Draws the letter; false once it is put away. */
    fun draw(): Boolean {
        val def = letterById(id) ?: return false
        val w = game.engine.w
        val h = game.engine.h
        ui.modal(0, 0, w, h)
        g.rect(0, 0, w, h, 0xB0181425.toInt())
        val cx = w / 2f
        val cy = h / 2f
        // The envelope: drops in, wobbles, the seal cracks, the flap opens.
        if (t < PAPER_IN + 0.3f) {
            val drop = (t / 0.38f).coerceIn(0f, 1f)
            val ey = cy + 40f - (1f - easeOutBack(drop)) * 220f + if (t > PAPER_IN) (t - PAPER_IN) * 260f else 0f
            val wobble = if (t in 0.38f..0.58f) sin(t * 70f) * 2f else 0f
            val alpha = if (t > PAPER_IN) ((1f - (t - PAPER_IN) / 0.3f) * 255).toInt().coerceIn(0, 255) else 255
            if (t >= SEAL_AT && !sealCracked) {
                sealCracked = true
                game.sfx.play("swap", 0.8f)
                game.haptics.tick()
            }
            // The letter rising out from behind the envelope.
            if (t > FLAP_AT) {
                val rise = ((t - FLAP_AT) / (PAPER_IN - FLAP_AT)).coerceIn(0f, 1f)
                val py = ey - 10f - rise * (ey - cy + 10f)
                g.spriteScaled("scenes/letter", cx, py, 0.3f + 0.7f * rise, 0.3f + 0.7f * rise)
            }
            g.spriteScaled(if (t >= FLAP_AT) "scenes/envelope_open" else "scenes/envelope", cx + wobble, ey, 2f, 2f, alpha = alpha)
            if (t in SEAL_AT..(SEAL_AT + 0.4f)) {
                val k = (t - SEAL_AT) / 0.4f
                for (p in 0 until 8) {
                    val a = p * 0.785f
                    val d = 6f + k * 30f
                    g.rect((cx + cos(a) * d).toInt(), (ey - 2f + sin(a) * d * 0.7f + k * k * 20f).toInt(), (cx + cos(a) * d).toInt() + 2, (ey - 2f + sin(a) * d * 0.7f + k * k * 20f).toInt() + 2,
                        (((1f - k) * 255).toInt() shl 24) or 0xE43B44)
                }
            }
        }
        if (t >= PAPER_IN) drawPaper(def, w, h, cx, cy)
        val typed = t >= PAPER_IN && shown >= world.storyText(def.text).length + 20
        if (ui.clickable("letter-next", 0, 0, w, h)) {
            when {
                t < PAPER_IN -> { t = PAPER_IN; sealCracked = true }
                !typed -> shown = 9999f
                else -> { game.sfx.play("close"); return false }
            }
        }
        return true
    }

    private fun drawPaper(def: com.ditherfarm.game.LetterDef, w: Int, h: Int, cx: Float, cy: Float) {
        val paper = game.assets["scenes/letter"]
        g.sprite(paper, cx, cy)
        val left = (cx - paper.w / 2f).toInt() + 16
        val top = (cy - paper.h / 2f).toInt() + 14
        g.text(def.title, cx.toInt(), top + 4, 0xFFA22633.toInt(), align = 0, outline = 0, shadow = false)
        // Grandpa's hand: brown ink, each line a touch off true, written out a letter at a time.
        val body = world.storyText(def.text).uppercase()
        var left2 = shown.toInt()
        var y = top + 24
        for ((pi, para) in body.split("\n").withIndex()) {
            if (para.isEmpty()) { y += 6; continue }
            for ((li, line) in hud.wrap(para, paper.w - 36).withIndex()) {
                if (left2 <= 0) break
                val jx = ((pi * 7 + li * 3) % 3) - 1
                g.text(line.take(left2), left + jx, y, 0xFF3E2731.toInt(), outline = 0, shadow = false)
                left2 -= line.length
                y += 13
            }
        }
        // Signed, with his photo taped in the corner.
        if (left2 > 0) {
            val sy = (cy + paper.h / 2f).toInt() - 46
            val a = ((left2 / 20f).coerceIn(0f, 1f) * 255).toInt()
            g.text("- GRANDPA ELIAS", (cx + paper.w / 2f).toInt() - 18, sy + 8, (a shl 24) or 0x733E39, align = 1, outline = 0, shadow = false)
            val px = (cx - paper.w / 2f) + 30f
            val py = (cy + paper.h / 2f) - 26f
            g.rect(px.toInt() - 14, py.toInt() - 30, px.toInt() + 14, py.toInt() + 2, (a shl 24) or 0xFFFFFF)
            g.spriteScaled("portraits/elias_old", px, py - 14f, 0.8f, 0.8f, alpha = a, filter = SEPIA)
            g.rect(px.toInt() - 6, py.toInt() - 33, px.toInt() + 6, py.toInt() - 28, (a / 2 shl 24) or 0xEAD4AA)
            if (a >= 255 && (t * 2).toInt() % 2 == 0) g.text("TAP TO FOLD IT AWAY", cx.toInt(), (cy + paper.h / 2f).toInt() + 8, Gfx.WHITE, align = 0)
        }
    }

    private fun easeOutBack(x: Float): Float {
        val c1 = 1.70158f; val c3 = c1 + 1f
        return 1f + c3 * (x - 1f) * (x - 1f) * (x - 1f) + c1 * (x - 1f) * (x - 1f)
    }

    /** The menu's list of letters opened so far; [onRead] opens one again. */
    fun list(onRead: (String) -> Unit): Boolean {
        val w = game.engine.w
        val read = LETTERS.filter { it.id in world.state.letters }
        val ph = 60 + LETTERS.size * 22
        val y = (game.engine.h - ph) / 2
        ui.modal(8, y, w - 16, ph)
        ui.panel(8, y, w - 16, ph)
        g.text("GRANDPA'S LETTERS", 18, y + 8, Gfx.GOLD)
        if (ui.button("letters-x", w - 30, y + 6, 14, 14, null, "ui/x", "red")) return false
        LETTERS.forEachIndexed { i, def ->
            val ry = y + 28 + i * 22
            val have = def in read
            g.slice9(if (have) "ui/slot_sel" else "ui/slot", 16, ry, w - 16, ry + 20, 4)
            g.sprite("scenes/envelope_icon", 30f, ry + 10f, if (have) 255 else 90)
            g.text(if (have) def.title else "SEALED", 46, ry + 6, if (have) Gfx.INK else Gfx.MUTED, outline = 0, shadow = false)
            if (have && ui.clickable("letter-$i", 16, ry, w - 32, 20)) { onRead(def.id); return true }
        }
        if (read.isEmpty()) g.text("THEY OPEN AS THE FARM GROWS", w / 2, y + ph - 18, Gfx.MUTED, align = 0, outline = 0, shadow = false)
        return true
    }

    companion object {
        const val SEAL_AT = 0.62f
        const val FLAP_AT = 0.78f
        const val PAPER_IN = 1.3f
        const val CPS = 55f
        val SEPIA = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 6f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, -10f,
            0f, 0f, 0f, 1f, 0f,
        )))
    }
}
