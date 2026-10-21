package com.ditherfarm.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.ditherfarm.Game
import com.ditherfarm.engine.Gfx
import com.ditherfarm.game.Cue
import com.ditherfarm.game.FARMER_LOOKS
import com.ditherfarm.game.Gender
import com.ditherfarm.game.Line
import com.ditherfarm.game.NAME_MAX
import com.ditherfarm.game.OFFICER_NAME
import com.ditherfarm.game.Scene
import com.ditherfarm.game.Who
import com.ditherfarm.game.finishScene
import com.ditherfarm.game.lookGender
import com.ditherfarm.game.playerName
import com.ditherfarm.game.setPlayer
import com.ditherfarm.game.storyText
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Plays a story scene as portrait dialogue: the two speakers as big busts with the one talking lit
 * (their mouth moving while the line types out, both blinking now and then), and a dialogue box.
 * Tap to finish the line, tap again for the next; SKIP ends it.
 *
 * Round 7 (the new opening, "go all out"): a line can change the faces on either side and the
 * backdrop (the bank, the bank forty years ago, Grandpa's bedroom, the porch, the village), with a
 * fade through black between backdrops. The old bank plays as old film: sepia, grain, scratches, a
 * flicker. The bank's stamp slams down with a shake and a puff of dust. In the bedroom the lamp glows
 * and dust drifts in the window's light while the dusk deepens line by line, until the lamp gutters
 * out. On the porch fireflies drift round the lantern. Title cards show a line alone on a dark screen.
 * The picker asks who you are: male or female, one of two looks each, and a name.
 */
class StoryPanel(private val game: Game, private val hud: Hud) {
    private val ui get() = game.ui
    private val g get() = game.gfx
    private val world get() = game.world

    private var scene: Scene? = null
    private var line = 0
    private var shown = 0f
    private var picking = false
    /** Only asking who you are (an old save choosing a gender), no scene. */
    private var pickOnly = false
    private var look = 0
    private var gender = Gender.MALE
    private val name = StringBuilder()
    private var time = 0f
    /** Seconds since the current line began (stamps, fades, the lamp going out). */
    private var lineTime = 0f
    /** 0..1 fade to black before moving on to a line with another backdrop. */
    private var leaving = -1f
    private val blinkAt = floatArrayOf(2f, 3.3f)
    private val rnd = Random(7)

    val playing get() = scene != null || pickOnly

    /** The music the scene wants (the opening's sad theme until the bank today), or null for the usual. */
    val music: String? get() {
        val s = scene ?: return null
        if (s.id != "opening") return null
        return if (bgAt(line) == "bank" && line > 0) null else "elegy"
    }

    /** Ends the scene without marking it played (a new farm starts over). */
    fun stop() { scene = null; picking = false; pickOnly = false }

    /** Jumps to line [i] fully typed, [t] seconds into it (screenshots). */
    internal fun showLine(i: Int, t: Float = 5f) { line = i; shown = 999f; picking = false; lineTime = t; leaving = -1f }

    /** Jumps to the opening's "who are you?" (screenshots). */
    internal fun showPicker() {
        val s = scene ?: return
        line = s.lines.indexOfFirst { it.cue == Cue.PICK }.coerceAtLeast(0); picking = true; resetPicker()
    }

    /** Starts [s]; the opening asks for a look and a name when none is set yet, or on a replay. */
    fun start(s: Scene, replay: Boolean = false) {
        scene = s
        line = 0
        shown = 0f
        lineTime = 0f
        leaving = -1f
        this.replay = replay
        skipAfterPick = false
        picking = s.lines.none { it.cue == Cue.PICK } && s.id == "opening" && (replay || world.state.playerName.isBlank())
        resetPicker()
        onLine()
    }

    private var replay = false
    private var skipAfterPick = false

    /** Asks an old save's farmer who they are (round 7 adds male and female), keeping the name. */
    fun startPick() {
        scene = null
        pickOnly = true
        picking = true
        resetPicker()
    }

    private fun resetPicker() {
        look = world.state.playerLook
        gender = world.state.playerGender ?: lookGender(look)
        if (look !in gender.looks) look = gender.looks[0]
        name.clear()
        name.append(world.state.playerName.uppercase())
    }

    fun update(dt: Float) {
        time += dt
        lineTime += dt
        if (leaving >= 0f) leaving += dt / FADE_OUT
        val s = scene ?: return
        if (!picking) shown += dt * (if (s.lines.getOrNull(line)?.slow == true) SLOW_CPS else CHARS_PER_SECOND)
        motes.update(dt)
    }

    // ------------------------------------------------------------ what the scene shows at a line

    private fun bgAt(i: Int): String {
        val s = scene ?: return "village"
        for (k in i downTo 0) s.lines.getOrNull(k)?.bg?.let { return it }
        return if (s.bank) "bank" else "village"
    }

    private fun leftAt(i: Int): String? {
        val s = scene ?: return null
        for (k in i downTo 0) s.lines.getOrNull(k)?.left?.let { return if (it == "player") null else it }
        return null
    }

    private fun rightAt(i: Int): String? {
        val s = scene ?: return null
        for (k in i downTo 0) s.lines.getOrNull(k)?.right?.let { return it }
        return null
    }

    /** The first and last bedroom lines, for the dusk deepening across them. */
    private fun bedroomSpan(): IntRange? {
        val s = scene ?: return null
        val idx = s.lines.indices.filter { bgAt(it) == "bedroom" }
        return if (idx.isEmpty()) null else idx.first()..idx.last()
    }

    private fun onLine() {
        lineTime = 0f
        val cur = scene?.lines?.getOrNull(line) ?: return
        when (cur.cue) {
            Cue.STAMP, Cue.APPROVE -> {}
            Cue.PICK -> if (replay || world.state.playerName.isBlank()) { picking = true; resetPicker() } else advance()
            else -> {}
        }
    }

    private fun advance(): Boolean {
        val s = scene ?: return false
        if (++line >= s.lines.size) return end(s)
        shown = 0f
        onLine()
        return true
    }

    // ------------------------------------------------------------ drawing

    /** Draws the scene; false once it is over (and marked played). */
    fun draw(): Boolean {
        val w = game.engine.w
        val h = game.engine.h
        ui.modal(0, 0, w, h, dim = false)
        val top = h - BANK_H
        if (pickOnly) {
            g.rect(0, 0, w, h, 0xE6181425.toInt())
            return pick(top)
        }
        val s = scene ?: return false
        val cur = s.lines[line]
        val bg = bgAt(line)
        val film = bg == "bank_old"
        // Stamp shake.
        val shakeT = lineTime - STAMP_DROP
        val stamping = (cur.cue == Cue.STAMP || cur.cue == Cue.APPROVE)
        val shake = if (stamping && shakeT in 0f..0.35f) ((1f - shakeT / 0.35f) * 3f * sin(shakeT * 70f)).toInt() else 0
        val c = g.canvas
        c.save()
        c.translate(shake.toFloat(), (shake / 2).toFloat())
        drawBackdrop(bg, w, h, top, film)
        if (cur.cue == Cue.CARD || (cur.cue == Cue.PICK && picking)) {
            c.restore()
            g.rect(0, 0, w, h, if (film) 0xF02B1E1A.toInt() else 0xF0181425.toInt())
            if (film) filmOverlay(w, h, strong = true)
            if (picking) return pick(top)
            return card(s, cur, w, h, film)
        }
        val boxTop = h - 92
        val rightLit = cur.who == Who.OFFICER || cur.who == Who.VILLAGER || cur.who == Who.RIVAL || (cur.who == Who.OTHER && cur.side == 1)
        val leftLit = cur.who == Who.PLAYER || (cur.who == Who.OTHER && cur.side == 0)
        val typing = shown < world.storyText(cur).length && leaving < 0f
        // The right side: who the line put there, else the scene's villager, rival or the officer.
        val rival = s.lines.any { it.who == Who.RIVAL }
        val rightFace = rightAt(line)?.let { if (it == "officer" && cur.who == Who.OFFICER && cur.mood == 1) "officer_shock" else it }
            ?: s.villager
            ?: if (rival) (if (cur.who == Who.RIVAL && cur.mood == 1) "kessler_shock" else "kessler")
            else if (rightLit && cur.mood == 1) "officer_shock" else "officer"
        val leftFace = leftAt(line) ?: ("farmer_${world.state.playerLook}" + if (leftLit && cur.mood == 1) "_det" else "")
        val behindDesk = bg == "bank" || bg == "bank_old"
        val rightBase = if (behindDesk) top + DESK_TOP + 4 else boxTop + 4
        val sepia = if (film) SEPIA else null
        drawBust(rightFace, 1, (w - 64).toFloat(), rightBase.toFloat(), rightLit, rightLit && typing, film)
        if (behindDesk) {
            if (film) g.spriteFiltered("scenes/bank_desk", 0f, top.toFloat(), sepia!!) else g.sprite("scenes/bank_desk", 0f, top.toFloat())
        }
        drawBust(leftFace, 0, 62f, (boxTop + 8).toFloat(), leftLit, leftLit && typing, film)
        drawLights(bg, w, h, top)
        if (stamping) drawStamp(cur, w, top)
        c.restore()
        if (film) filmOverlay(w, h, strong = false)
        drawDusk(bg, w, h)
        if (cur.cue == Cue.LAMP_OUT) {
            val f = ((lineTime - 1.6f) / 1.6f).coerceIn(0f, 1f)
            if (f > 0f) g.rect(0, 0, w, h, (((f * 255).toInt()) shl 24) or 0x181425)
        }
        drawBox(s, cur, w, boxTop)
        if (leaving >= 0f) {
            g.rect(0, 0, w, h, ((leaving.coerceIn(0f, 1f) * 255).toInt() shl 24) or 0x181425)
            if (leaving >= 1f) { leaving = -1f; return advance() }
        } else if (lineTime < FADE_IN && (line == 0 || bgAt(line - 1) != bg || s.lines[line - 1].cue == Cue.CARD)) {
            g.rect(0, 0, w, h, (((1f - lineTime / FADE_IN) * 255).toInt().coerceIn(0, 255) shl 24) or 0x181425)
        }
        return input(s, cur)
    }

    private fun input(s: Scene, cur: Line): Boolean {
        val w = game.engine.w
        val h = game.engine.h
        if (ui.button("story-skip", w - 54, game.engine.safeTop + 6, 48, 14, "SKIP", style = "wood")) {
            // A new farmer still says who they are before the scene is skipped.
            val pickAt = s.lines.indexOfFirst { it.cue == Cue.PICK }
            if (pickAt > line && (replay || world.state.playerName.isBlank())) {
                line = pickAt; shown = 0f; leaving = -1f; skipAfterPick = true; picking = true; resetPicker()
                return true
            }
            return end(s)
        }
        if (leaving >= 0f) return true
        if (ui.clickable("story-next", 0, 0, w, h)) {
            val full = world.storyText(cur).length
            when {
                shown < full -> shown = full.toFloat()
                cur.cue == Cue.LAMP_OUT && lineTime < 3.2f -> {}
                line + 1 < s.lines.size && bgAt(line + 1) != bgAt(line) -> leaving = 0f
                else -> return advance()
            }
        }
        return true
    }

    private fun drawBackdrop(bg: String, w: Int, h: Int, top: Int, film: Boolean) {
        when (bg) {
            "village" -> g.rect(0, 0, w, h, 0x99181425.toInt())
            else -> {
                g.rect(0, 0, w, h, when (bg) { "bedroom" -> 0xFF3E2731.toInt(); "porch" -> 0xFF181425.toInt(); "bank_old" -> 0xFF5E4E36.toInt(); else -> WALLPAPER })
                val name = "scenes/$bg"
                if (film) g.spriteFiltered(name, 0f, top.toFloat(), SEPIA) else g.sprite(name, 0f, top.toFloat())
            }
        }
    }

    /** A bust at 2x: talking (mouth open on every other beat of the typing), blinking, breathing. A face
     *  "<id>:blush" is the romance candidate blushing. */
    private fun drawBust(face: String, side: Int, x: Float, base: Float, lit: Boolean, talking: Boolean, film: Boolean) {
        if (time > blinkAt[side] + BLINK) blinkAt[side] = time + 2.2f + rnd.nextFloat() * 3f
        val blink = time in blinkAt[side]..(blinkAt[side] + BLINK)
        val mouth = talking && ((shown * 0.55f).toInt() % 2 == 0)
        val id = face.substringBefore(':')
        val mood = if (face.endsWith(":blush") && game.assets.has("portraits/${id}_bust_blush")) "_blush" else ""
        // A face without a bust falls back to its tile, which pivots on its middle.
        val hasBust = game.assets.has("portraits/${id}_bust")
        val baseName = if (hasBust) "portraits/${id}_bust$mood" else "portraits/$id"
        val lift = if (hasBust) 0f else 50f
        val art = when {
            mouth && game.assets.has("${baseName}_talk") -> "${baseName}_talk"
            blink && game.assets.has("${baseName}_blink") -> "${baseName}_blink"
            else -> baseName
        }
        val breathe = if (lit) (sin(time * 2.4f + side) * 0.6f) else 0f
        val filter = when {
            film && lit -> SEPIA
            film -> SEPIA_SHADE
            lit -> null
            else -> SHADE
        }
        g.spriteScaled(art, x, base + breathe - lift, 2f, 2f, filter = filter)
    }

    /** The lamp and the lantern: a warm flickering glow; the bedroom's window beam with its dust. */
    private fun drawLights(bg: String, w: Int, h: Int, top: Int) {
        val c = g.canvas
        when (bg) {
            "bedroom" -> {
                val cur = scene?.lines?.getOrNull(line)
                val out = if (cur?.cue == Cue.LAMP_OUT) (1f - (lineTime / 1.6f)).coerceIn(0f, 1f) else 1f
                // The window's beam, fading as the sun goes.
                val dusk = duskLevel()
                val beamA = ((1f - dusk) * 34).toInt()
                if (beamA > 2) {
                    beamPaint.color = (beamA shl 24) or 0xFEAE34
                    beamPath.reset()
                    beamPath.moveTo(80f, top + 140f); beamPath.lineTo(122f, top + 140f)
                    beamPath.lineTo(190f, top + 388f); beamPath.lineTo(110f, top + 388f); beamPath.close()
                    c.drawPath(beamPath, beamPaint)
                    motes.draw(g, top, (1f - dusk))
                }
                val flick = if (out < 1f) (rnd.nextFloat() * 0.6f + 0.4f) * out else 0.85f + 0.15f * sin(time * 9f) * sin(time * 5.3f)
                glow(124f, top + 298f, 34f * flick, 0xFEAE34, (130 * flick).toInt())
                glow(124f, top + 298f, 14f * flick, 0xFEE761, (140 * flick).toInt())
            }
            "porch" -> {
                val flick = 0.85f + 0.15f * sin(time * 7f) * sin(time * 3.1f)
                glow(40f, top + 64f, 46f * flick, 0xFEAE34, (120 * flick).toInt())
                glow(40f, top + 64f, 16f * flick, 0xFEE761, (140 * flick).toInt())
                for (k in 0 until 14) {
                    val fx = 30f + ((k * 53) % 220) + sin(time * (0.4f + k * 0.07f) + k) * 18f
                    val fy = top + 150f + ((k * 37) % 170) + cos(time * (0.5f + k * 0.05f) + k * 2) * 12f
                    val on = sin(time * (1.3f + k * 0.17f) + k * 1.7f)
                    if (on > 0.2f) {
                        val a = ((on - 0.2f) / 0.8f * 255).toInt()
                        glow(fx, fy, 4f, 0xFEE761, a / 2)
                        g.rect(fx.toInt(), fy.toInt(), fx.toInt() + 1, fy.toInt() + 1, (a shl 24) or 0xFEE761)
                    }
                }
            }
        }
    }

    private val glowPaint = Paint().apply { isAntiAlias = false }
    private val beamPaint = Paint().apply { isAntiAlias = false }
    private val beamPath = android.graphics.Path()

    /** A soft glow: rings that add up toward the middle. */
    private fun glow(x: Float, y: Float, r: Float, rgb: Int, a: Int) {
        if (r < 1f || a <= 0) return
        for (k in 0 until 5) {
            glowPaint.color = ((a / 5).coerceIn(1, 255) shl 24) or rgb
            g.canvas.drawCircle(x, y, r * (1f - k * 0.18f), glowPaint)
        }
    }

    /** 0 at the first bedroom line, 1 at the last: the dusk deepens while Grandpa talks. */
    private fun duskLevel(): Float {
        val span = bedroomSpan() ?: return 0f
        if (line !in span) return 0f
        return ((line - span.first).toFloat() / (span.last - span.first).coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    private fun drawDusk(bg: String, w: Int, h: Int) {
        if (bg != "bedroom") return
        val a = (duskLevel() * 120).toInt()
        if (a > 0) g.rect(0, 0, w, h - 92, (a shl 24) or 0x181425)
    }

    /** Old film: grain, a scratch now and then, a soft flicker and dark corners. */
    private fun filmOverlay(w: Int, h: Int, strong: Boolean) {
        val n = if (strong) 120 else 220
        for (k in 0 until n) {
            val x = rnd.nextInt(w); val y = rnd.nextInt(h)
            g.rect(x, y, x + 1, y + 1, if (k % 2 == 0) 0x50F6E8C8 else 0x502B1E1A)
        }
        if (rnd.nextFloat() < 0.35f) {
            val x = rnd.nextInt(w)
            g.rect(x, 0, x + 1, h, 0x40F6E8C8)
        }
        val flick = rnd.nextInt(18)
        g.rect(0, 0, w, h, (flick shl 24) or 0x2B1E1A)
        for (k in 0 until 6) {
            val a = (0x22 + k * 0x10) shl 24
            val inset = 18 - k * 3
            g.rect(0, 0, w, inset, a or 0x1A120E)
            g.rect(0, h - inset, w, h, a or 0x1A120E)
            g.rect(0, inset, inset, h - inset, a or 0x1A120E)
            g.rect(w - inset, inset, w, h - inset, a or 0x1A120E)
        }
    }

    /** The stamp drops from big to its mark, bites in (shake, a puff of dust, the thud) and stays. */
    private fun drawStamp(cur: Line, w: Int, top: Int) {
        val art = if (cur.cue == Cue.APPROVE) "scenes/stamp_approved" else "scenes/stamp_refused"
        val t = (lineTime / STAMP_DROP).coerceIn(0f, 1f)
        val k = 1f + 2.4f * (1f - t) * (1f - t)
        val cx = w / 2f + 26f
        val cy = top + 262f
        if (lineTime >= STAMP_DROP && !stampHit) {
            stampHit = true
            game.sfx.play("stamp", 1f, 0f)
            game.haptics.heavy()
        }
        if (lineTime < STAMP_DROP) stampHit = false
        // A shadow that shrinks as it lands.
        g.spriteScaled(art, cx + 4f * k, cy + 6f * k, k, k, alpha = (60 * t).toInt(), filter = INK)
        g.spriteScaled(art, cx, cy, k, k, alpha = (120 + 135 * t).toInt())
        val dt = lineTime - STAMP_DROP
        if (dt in 0f..0.5f) {
            for (p in 0 until 14) {
                val a = p * 0.45f
                val d = 24f + dt * 90f
                val px = cx + cos(a) * d * 1.7f
                val py = cy + sin(a) * d * 0.6f - dt * 20f
                val alpha = ((1f - dt / 0.5f) * 200).toInt()
                g.rect(px.toInt(), py.toInt(), px.toInt() + 2, py.toInt() + 2, (alpha shl 24) or 0xC0CBDC)
            }
        }
    }

    private var stampHit = false

    private fun drawBox(s: Scene, cur: Line, w: Int, boxTop: Int) {
        ui.panel(6, boxTop, w - 12, 84)
        val who = when (cur.who) {
            Who.PLAYER -> world.playerName.uppercase(); Who.OFFICER -> OFFICER_NAME.uppercase(); Who.NARRATOR -> null
            Who.VILLAGER -> s.villager?.let { id -> world.data.villagers.firstOrNull { it.id == id }?.name?.uppercase() }
            Who.RIVAL -> "MS. KESSLER"
            Who.OTHER -> cur.name
        }
        if (who != null) {
            val tw = g.textWidth(who) + 14
            val left = cur.who == Who.PLAYER || (cur.who == Who.OTHER && cur.side == 0)
            val tx = if (left) 12 else w - 12 - tw
            ui.pill(tx, boxTop - 10, tw, 14)
            g.text(who, tx + tw / 2, boxTop - 7, Gfx.GOLD, align = 0)
        }
        val full = world.storyText(cur).uppercase()
        val lines = hud.wrap(full, w - 36)
        var left = shown.toInt()
        val color = if (cur.who == Who.NARRATOR) 0xFF68386C.toInt() else Gfx.INK
        lines.forEachIndexed { i, l ->
            if (left <= 0) return@forEachIndexed
            g.text(l.take(left), 18, boxTop + 12 + i * 12, color, outline = 0, shadow = false)
            left -= l.length
        }
        val done = shown >= full.length
        if (done && (time * 3).toInt() % 2 == 0) g.text("v", w - 22, boxTop + 66, 0xFF733E39.toInt(), outline = 0, shadow = false)
    }

    /** A title card: the line large and centred, letter by letter, with a little rule under it. */
    private fun card(s: Scene, cur: Line, w: Int, h: Int, film: Boolean): Boolean {
        val full = world.storyText(cur).uppercase()
        val rows = full.split("\n")
        val c = g.canvas
        val big = rows.all { it.length <= 16 }
        val scale = if (big) 2f else 1f
        val color = if (film) 0xFFEAD4AA.toInt() else Gfx.GOLD
        var left = shown.toInt()
        val lineH = if (big) 13 else 12
        val y0 = h / 2 - (rows.size * lineH * scale).toInt() / 2 - 10
        rows.forEachIndexed { i, r ->
            if (left <= 0) return@forEachIndexed
            c.save()
            c.translate(w / 2f, y0 + i * lineH * scale)
            c.scale(scale, scale)
            g.text(r.take(left), 0, 0, color, align = 0)
            c.restore()
            left -= r.length + 1
        }
        if (shown >= full.length) {
            val ry = y0 + (rows.size * lineH * scale).toInt() + 8
            val a = ((lineTime - full.length / CHARS_PER_SECOND) * 2f).coerceIn(0f, 1f)
            val half = (40 * a).toInt()
            g.rect(w / 2 - half, ry, w / 2 + half, ry + 1, color)
            g.rect(w / 2 - 2, ry - 2, w / 2 + 2, ry + 3, color)
        }
        if (lineTime < FADE_IN) g.rect(0, 0, w, h, (((1f - lineTime / FADE_IN) * 255).toInt().coerceIn(0, 255) shl 24) or 0x181425)
        if (leaving >= 0f) {
            g.rect(0, 0, w, h, ((leaving.coerceIn(0f, 1f) * 255).toInt() shl 24) or 0x181425)
            if (leaving >= 1f) { leaving = -1f; return advance() }
        }
        return input(s, cur)
    }

    private fun end(s: Scene): Boolean {
        world.finishScene(s.id)
        scene = null
        return false
    }

    /** Who are you? Male or female, one of that gender's two looks, a name typed on the key grid, then START. */
    private fun pick(top: Int): Boolean {
        val w = game.engine.w
        val ph = 312
        val py = (game.engine.h - ph) / 2
        ui.panel(8, py, w - 16, ph)
        g.text("WHO ARE YOU?", w / 2, py + 8, Gfx.GOLD, align = 0)
        g.text("ELIAS'S GRANDCHILD", w / 2, py + 20, Gfx.MUTED, align = 0, outline = 0, shadow = false)
        // Male or female: switching keeps the same slot (first or second look).
        Gender.entries.forEachIndexed { i, gd ->
            val bx = w / 2 + (if (i == 0) -94 else 4)
            val on = gender == gd
            if (ui.button("gender-$i", bx, py + 34, 90, 18, gd.name, style = if (on) "gold" else "wood") && !on) {
                val slot = gender.looks.indexOf(look).coerceAtLeast(0)
                gender = gd
                look = gd.looks[slot]
            }
        }
        // The two looks, gently bobbing; the chosen one framed in gold.
        gender.looks.forEachIndexed { k, i ->
            val cx = w / 2 + (if (k == 0) -46 else 46)
            val cy = py + 100
            val on = i == look
            if (on) g.rect(cx - 36, cy - 36, cx + 36, cy + 36, Gfx.GOLD)
            val bob = if (on) sin(time * 3f) * 1.5f else 0f
            val blink = on && (time % 3.4f) < 0.12f
            val art = "portraits/farmer_$i" + if (blink && game.assets.has("portraits/farmer_${i}_blink")) "_blink" else ""
            g.spriteScaled(art, cx.toFloat(), cy + bob, 1.36f, 1.36f, filter = if (on) null else SHADE)
            if (ui.clickable("look-$i", cx - 36, cy - 36, 72, 72)) look = i
        }
        // Name field.
        val ny = py + 148
        ui.pill(w / 2 - 70, ny, 140, 16)
        val cursor = if ((time * 2).toInt() % 2 == 0 && name.length < NAME_MAX) "_" else " "
        g.text(if (name.isEmpty()) "TYPE YOUR NAME$cursor" else "$name$cursor", w / 2, ny + 4, if (name.isEmpty()) Gfx.GREY else Gfx.WHITE, align = 0)
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
            if (pickOnly) {
                pickOnly = false
                hud.toast("Settings has the new opening, if you'd like to see it")
                return false
            }
            if (skipAfterPick) return end(scene ?: return false)
            return advance()
        }
        return true
    }

    /** Dust drifting in the bedroom window's light. */
    private class Motes {
        private val xs = FloatArray(26) { Random(it).nextFloat() }
        private val ys = FloatArray(26) { Random(it + 99).nextFloat() }
        private var t = 0f
        fun update(dt: Float) { t += dt }
        fun draw(g: Gfx, top: Int, a: Float) {
            for (k in xs.indices) {
                val v = (ys[k] + t * (0.01f + k % 5 * 0.004f)) % 1f
                val y = top + 150f + v * 230f
                val x = 84f + v * 90f + xs[k] * 34f + sin(t * 0.8f + k) * 4f
                val tw = (sin(t * 2f + k * 1.3f) * 0.5f + 0.5f) * a
                val alpha = (tw * 200).toInt()
                if (alpha > 10) g.rect(x.toInt(), y.toInt(), x.toInt() + 1, y.toInt() + 1, (alpha shl 24) or 0xFEE761)
            }
        }
    }

    private val motes = Motes()

    companion object {
        const val CHARS_PER_SECOND = 40f
        /** Grandpa's last words type out slowly. */
        const val SLOW_CPS = 17f
        const val BLINK = 0.13f
        const val FADE_IN = 0.55f
        const val FADE_OUT = 0.4f
        const val STAMP_DROP = 0.16f
        /** The bank backdrop's height and its desk top (scenes.py). */
        const val BANK_H = 480
        const val DESK_TOP = 380
        val WALLPAPER = 0xFF265C42.toInt()
        /** The one not talking is shaded back. */
        val SHADE = android.graphics.LightingColorFilter(0x6A6A80, 0)
        /** The flashbacks: old warm sepia film, and the one not talking shaded within it. */
        val SEPIA = ColorMatrixColorFilter(sepia(1f))
        val SEPIA_SHADE = ColorMatrixColorFilter(sepia(0.6f))
        /** Shadows cast by the stamp. */
        val INK = android.graphics.PorterDuffColorFilter(0xFF181425.toInt(), android.graphics.PorterDuff.Mode.SRC_IN)
        val KEYS = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")

        private fun sepia(bright: Float) = ColorMatrix(floatArrayOf(
            0.393f * bright, 0.769f * bright, 0.189f * bright, 0f, 6f,
            0.349f * bright, 0.686f * bright, 0.168f * bright, 0f, 0f,
            0.272f * bright, 0.534f * bright, 0.131f * bright, 0f, -10f,
            0f, 0f, 0f, 1f, 0f,
        ))
    }
}
