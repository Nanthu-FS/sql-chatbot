package com.ditherfarm.ui

import com.ditherfarm.Game
import com.ditherfarm.engine.Gfx
import com.ditherfarm.game.Season

/** Bottom-bar destinations. */
enum class Nav(val icon: String, val unlockLevel: Int) {
    FARM("icons_big/nav_farm", 1), FOREST("icons_big/nav_forest", 2), MINE("icons_big/nav_mine", 3),
    BARN("icons_big/bag", 1), ORDERS("icons_big/orders", 1), MARKET("icons_big/market", 1), MENU("icons_big/menu", 1),
}

/**
 * Shared chrome for every map screen: the status bar on top, the navigation bar at the
 * bottom, toasts, tutorial tips and the "while you were away" note.
 */
class Hud(private val game: Game) {
    private val ui get() = game.ui
    private val g get() = game.gfx
    private val world get() = game.world

    private var toastText: String? = null
    private var toastTime = 0f
    private var tipText: String? = null
    private var tipKey: String? = null
    private var displayedCoins = -1f

    val topHeight = 34
    val bottomHeight = 46

    fun toast(text: String) { toastText = text; toastTime = 2.2f }

    /** Shows a tutorial tip once per save. */
    fun tip(key: String, text: String) {
        if (key in world.state.tips || tipKey != null) return
        tipKey = key; tipText = text
    }

    fun update(dt: Float) {
        if (toastTime > 0) toastTime -= dt
        val target = world.state.coins.toFloat()
        displayedCoins = if (displayedCoins < 0) target else displayedCoins + (target - displayedCoins) * minOf(1f, dt * 8f)
        if (kotlin.math.abs(target - displayedCoins) < 0.5f) displayedCoins = target
    }

    fun drawTop() {
        val w = game.engine.w
        val y = game.engine.safeTop + 2
        val s = world.state
        // Level badge + XP.
        ui.pill(2, y, 92, 14)
        g.sprite("icons/level", 10f, y + 7f)
        g.text("${s.level}", 18, y + 3, Gfx.GOLD)
        ui.bar(32, y + 4, 58, world.levelProgress(), "purple", 6)
        // Coins.
        ui.pill(96, y, 76, 14)
        g.sprite("icons/coin", 104f, y + 7f)
        g.text(Ui.compact(displayedCoins.toInt()), 113, y + 3, Gfx.GOLD)
        // Energy.
        ui.pill(174, y, w - 176, 14)
        g.sprite("icons/energy", 182f, y + 7f)
        val maxE = world.maxEnergy
        ui.bar(190, y + 4, w - 218, (s.energy / maxE).toFloat(), if (s.energy < 20) "red" else "green", 6)
        g.text("${s.energy.toInt()}", w - 6, y + 3, Gfx.WHITE, align = 1)
        // Calendar, clock and weather.
        val t = world.time
        val y2 = y + 16
        ui.pill(2, y2, 140, 14)
        g.sprite(seasonIcon(t.season), 10f, y2 + 7f)
        g.text("${t.season.label.take(3).uppercase()} ${t.dayOfSeason}  ${t.clockText()}", 18, y2 + 3, Gfx.WHITE)
        g.sprite(s.weather.icon, 126f, y2 + 7f)
        ui.pill(144, y2, 44, 14)
        g.text(">", 149, y2 + 3, Gfx.GREY)
        g.sprite(s.forecast.icon, 165f, y2 + 7f)
        if (ui.clickable("hud-forecast", 144, y2, 44, 14)) toast("Tomorrow: ${s.forecast.name.lowercase()}")
    }

    /** Draws the nav bar; returns the destination tapped this frame, if any. */
    fun drawBottom(current: Nav, badges: Map<Nav, Int> = emptyMap()): Nav? {
        val w = game.engine.w
        val h = game.engine.h
        val y = h - game.engine.safeBottom - bottomHeight
        ui.panel(0, y, w, bottomHeight + game.engine.safeBottom)
        val n = Nav.entries.size
        val size = 34
        val gap = (w - 8 - n * size) / (n - 1)
        var picked: Nav? = null
        Nav.entries.forEachIndexed { i, nav ->
            val x = 4 + i * (size + gap)
            val locked = world.level < nav.unlockLevel
            if (ui.slot("nav-$nav", x, y + 6, if (locked) null else nav.icon, -1, nav == current, size)) picked = nav
            if (locked) {
                g.sprite("icons/lock", (x + size / 2).toFloat(), (y + 6 + size / 2).toFloat())
                g.text("L${nav.unlockLevel}", x + size / 2, y + 6 + size - 9, Gfx.WHITE, align = 0)
            }
            val b = badges[nav] ?: 0
            if (b > 0) {
                g.sprite("ui/badge", (x + size - 3).toFloat(), (y + 9).toFloat())
            }
        }
        return picked?.let { nav ->
            if (world.level < nav.unlockLevel) { toast("Unlocks at level ${nav.unlockLevel}"); game.sfx.play("error"); null } else nav
        }
    }

    /** Toasts, tips and the away summary, drawn last. */
    fun drawOverlays(tipVisible: Boolean = true, tipLift: Int = 0) {
        val w = game.engine.w
        val h = game.engine.h
        toastText?.let { text ->
            if (toastTime > 0) {
                val tw = g.textWidth(text) + 14
                val y = game.engine.safeTop + topHeight + 6
                ui.pill(w / 2 - tw / 2, y, tw, 14)
                g.text(text, w / 2, y + 3, Gfx.WHITE, align = 0)
            }
        }
        val tip = tipText
        if (tip != null && tipVisible) {
            val lines = wrap(tip, w - 40)
            val bh = 12 + lines.size * 10
            val y = h - game.engine.safeBottom - bottomHeight - bh - 6 - tipLift
            g.slice9("ui/bubble", 10, y, w - 10, y + bh, 5)
            lines.forEachIndexed { i, l -> g.text(l, 18, y + 6 + i * 10, Gfx.INK, outline = 0, shadow = false) }
            if (ui.clickable("tip", 10, y, w - 20, bh)) {
                tipKey?.let { world.state.tips += it }
                tipKey = null; tipText = null
            }
        }
        game.awaySummary?.let { text ->
            val lines = listOf("WHILE YOU WERE AWAY") + wrap(text, 200)
            val pw = 220
            val ph = 30 + lines.size * 11
            val x = (w - pw) / 2
            val y = h / 2 - ph / 2
            val outside = ui.modal(x, y, pw, ph)
            ui.panel(x, y, pw, ph)
            lines.forEachIndexed { i, l -> g.text(l, w / 2, y + 10 + i * 11, if (i == 0) Gfx.GOLD else Gfx.INK, align = 0, outline = if (i == 0) Gfx.INK else 0, shadow = i == 0) }
            if (ui.button("away-ok", w / 2 - 24, y + ph - 22, 48, 16, "OK") || outside) game.awaySummary = null
        }
    }

    fun wrap(text: String, maxW: Int): List<String> {
        val out = ArrayList<String>()
        var line = ""
        for (word in text.split(' ')) {
            val next = if (line.isEmpty()) word else "$line $word"
            if (g.textWidth(next) > maxW && line.isNotEmpty()) { out += line; line = word } else line = next
        }
        if (line.isNotEmpty()) out += line
        return out
    }

    companion object {
        fun seasonIcon(s: Season) = when (s) {
            Season.SPRING -> "icons/season_spring"
            Season.SUMMER -> "icons/season_summer"
            Season.FALL -> "icons/season_fall"
            Season.WINTER -> "icons/season_winter"
        }
    }
}
