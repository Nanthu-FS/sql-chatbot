package com.ditherfarm.ui

import com.ditherfarm.Game
import com.ditherfarm.engine.Gfx
import com.ditherfarm.game.FARMER_LOOKS
import com.ditherfarm.game.NAME_MAX
import com.ditherfarm.game.OFFICER_NAME
import com.ditherfarm.game.Scene
import com.ditherfarm.game.Who
import com.ditherfarm.game.finishScene
import com.ditherfarm.game.playerName
import com.ditherfarm.game.setPlayer
import com.ditherfarm.game.storyText

/**
 * Plays a story scene as portrait dialogue: the bank's loan office (or the village, dimmed, for
 * the officer's visits), the two speakers as big busts with the one talking lit, and a dialogue
 * box that types its line out. Tap to finish the line, tap again for the next; SKIP ends it. The
 * opening first asks who you are: one of four farmer looks and a name typed on the key grid.
 */
class StoryPanel(private val game: Game, private val hud: Hud) {
    private val ui get() = game.ui
    private val g get() = game.gfx
    private val world get() = game.world

    private var scene: Scene? = null
    private var line = 0
    private var shown = 0f
    private var picking = false
    private var look = 0
    private val name = StringBuilder()
    private var time = 0f

    val playing get() = scene != null

    /** Ends the scene without marking it played (a new farm starts over). */
    fun stop() { scene = null; picking = false }

    /** Jumps to line [i] fully typed (screenshots). */
    internal fun showLine(i: Int) { line = i; shown = 999f; picking = false }

    /** Starts [s]; the opening asks for a look and a name when none is set yet, or on a replay. */
    fun start(s: Scene, replay: Boolean = false) {
        scene = s
        line = 0
        shown = 0f
        picking = s.id == "opening" && (replay || world.state.playerName.isBlank())
        look = world.state.playerLook
        name.clear()
        name.append(world.state.playerName.uppercase())
    }

    fun update(dt: Float) {
        time += dt
        if (!picking) shown += dt * CHARS_PER_SECOND
    }

    /** Draws the scene; false once it is over (and marked played). */
    fun draw(): Boolean {
        val s = scene ?: return false
        val w = game.engine.w
        val h = game.engine.h
        ui.modal(0, 0, w, h, dim = false)
        // Backdrop: the bank, bottom-aligned on any screen height, or the village dimmed.
        val top = h - BANK_H
        if (s.bank) {
            g.rect(0, 0, w, h, WALLPAPER)
            g.sprite("scenes/bank", 0f, top.toFloat())
        } else {
            g.rect(0, 0, w, h, 0x99181425.toInt())
        }
        if (picking) return pick(top)
        val cur = s.lines[line]
        val boxTop = h - 92
        // The officer sits behind his desk in the bank; in the village both stand above the box.
        val officerLit = cur.who == Who.OFFICER || cur.who == Who.VILLAGER || cur.who == Who.RIVAL
        val rival = s.lines.any { it.who == Who.RIVAL }
        val playerLit = cur.who == Who.PLAYER
        // A heart event puts the villager (round 6) where the officer stands.
        val officer = s.villager?.let { "portraits/$it" }
            ?: if (rival) (if (cur.who == Who.RIVAL && cur.mood == 1) "portraits/kessler_shock_bust" else "portraits/kessler_bust")
            else if (officerLit && cur.mood == 1) "portraits/officer_shock_bust" else "portraits/officer_bust"
        val farmer = "portraits/farmer_${world.state.playerLook}" + (if (playerLit && cur.mood == 1) "_det" else "") + "_bust"
        // Villager portraits pivot on their middle, busts on their base: lift them clear of the box.
        val officerBase = (if (s.bank) top + DESK_TOP + 4 else boxTop + 4) - if (s.villager != null) 46 else 0
        g.spriteScaled(officer, (w - 64).toFloat(), officerBase.toFloat(), 3f, 3f, filter = if (officerLit) null else SHADE)
        if (s.bank) g.sprite("scenes/bank_desk", 0f, top.toFloat())
        g.spriteScaled(farmer, 62f, (boxTop + 8).toFloat(), 3f, 3f, filter = if (playerLit) null else SHADE)
        // Dialogue box with the speaker's name tag and the line typed out.
        ui.panel(6, boxTop, w - 12, 84)
        val who = when (cur.who) {
            Who.PLAYER -> world.playerName.uppercase(); Who.OFFICER -> OFFICER_NAME.uppercase(); Who.NARRATOR -> null
            Who.VILLAGER -> s.villager?.let { id -> world.data.villagers.firstOrNull { it.id == id }?.name?.uppercase() }
            Who.RIVAL -> "MS. KESSLER"
        }
        if (who != null) {
            val tw = g.textWidth(who) + 14
            val tx = if (cur.who == Who.PLAYER) 12 else w - 12 - tw
            ui.pill(tx, boxTop - 10, tw, 14)
            g.text(who, tx + tw / 2, boxTop - 7, Gfx.GOLD, align = 0)
        }
        val full = world.storyText(cur).uppercase()
        val lines = hud.wrap(full, w - 36)
        var left = shown.toInt()
        lines.forEachIndexed { i, l ->
            if (left <= 0) return@forEachIndexed
            g.text(l.take(left), 18, boxTop + 12 + i * 12, if (cur.who == Who.NARRATOR) 0xFF68386C.toInt() else Gfx.INK, outline = 0, shadow = false)
            left -= l.length
        }
        val done = shown >= full.length
        if (done && (time * 3).toInt() % 2 == 0) g.text("v", w - 22, boxTop + 66, 0xFF733E39.toInt(), outline = 0, shadow = false)
        if (ui.button("story-skip", w - 54, game.engine.safeTop + 6, 48, 14, "SKIP", style = "wood")) return end(s)
        if (ui.clickable("story-next", 0, 0, w, h)) {
            if (!done) shown = full.length.toFloat()
            else if (++line >= s.lines.size) return end(s)
            else shown = 0f
        }
        return true
    }

    private fun end(s: Scene): Boolean {
        world.finishScene(s.id)
        scene = null
        return false
    }

    /** Who are you? Four looks to tap, a name typed on the key grid, then START. */
    private fun pick(top: Int): Boolean {
        val w = game.engine.w
        val ph = 318
        val py = (game.engine.h - ph) / 2
        g.rect(0, 0, w, game.engine.h, 0x99181425.toInt())
        ui.panel(8, py, w - 16, ph)
        g.text("WHO ARE YOU?", w / 2, py + 8, Gfx.GOLD, align = 0)
        // Looks: a 2x2 grid of portraits at 2x.
        for (i in 0 until FARMER_LOOKS) {
            val cx = w / 2 + (if (i % 2 == 0) -38 else 38)
            val cy = py + 58 + (i / 2) * 72
            if (i == look) g.rect(cx - 36, cy - 36, cx + 36, cy + 36, Gfx.GOLD)
            g.spriteScaled("portraits/farmer_$i", cx.toFloat(), cy.toFloat(), 2f, 2f)
            if (ui.clickable("look-$i", cx - 36, cy - 36, 72, 72)) look = i
        }
        // Name field.
        val ny = py + 172
        ui.pill(w / 2 - 70, ny, 140, 16)
        val cursor = if ((time * 2).toInt() % 2 == 0 && name.length < NAME_MAX) "_" else " "
        g.text(if (name.isEmpty()) "TYPE YOUR NAME$cursor" else "$name$cursor", w / 2, ny + 4, if (name.isEmpty()) Gfx.GREY else Gfx.WHITE, align = 0)
        // Key grid.
        val kw = 22
        KEYS.forEachIndexed { r, row ->
            val rowW = row.length * (kw + 2) + if (r == 2) 2 * (kw + 2) else 0
            var kx = (w - rowW) / 2
            val ky = ny + 24 + r * 22
            for (ch in row) {
                if (ui.button("key-$ch", kx, ky, kw, 20, ch.toString(), style = "wood") && name.length < NAME_MAX) name.append(ch)
                kx += kw + 2
            }
            if (r == 2 && ui.button("key-del", kx, ky, kw * 2 + 2, 20, "DEL", style = "red") && name.isNotEmpty()) name.setLength(name.length - 1)
        }
        val ok = name.isNotBlank()
        if (ui.button("story-start", w / 2 - 50, py + ph - 28, 100, 18, "START", style = if (ok) "gold" else "grey") && ok) {
            world.setPlayer(look, name.toString().lowercase().replaceFirstChar { it.uppercase() })
            picking = false
            shown = 0f
        }
        return true
    }

    companion object {
        const val CHARS_PER_SECOND = 40f
        /** The bank backdrop's height and its desk top (scenes.py). */
        const val BANK_H = 480
        const val DESK_TOP = 380
        val WALLPAPER = 0xFF265C42.toInt()
        /** The one not talking is shaded back. */
        val SHADE = android.graphics.LightingColorFilter(0x6A6A80, 0)
        val KEYS = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
    }
}
