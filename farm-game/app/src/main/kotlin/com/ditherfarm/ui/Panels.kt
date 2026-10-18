package com.ditherfarm.ui

import com.ditherfarm.Game
import com.ditherfarm.engine.Gfx
import com.ditherfarm.game.Quality
import com.ditherfarm.game.Storage
import com.ditherfarm.game.Time
import com.ditherfarm.game.RecipeDef
import com.ditherfarm.game.Tool
import com.ditherfarm.game.addFuel
import com.ditherfarm.game.available
import com.ditherfarm.game.build
import com.ditherfarm.game.buildBlocker
import com.ditherfarm.game.building
import com.ditherfarm.game.cancelLast
import com.ditherfarm.game.collect
import com.ditherfarm.game.enqueue
import com.ditherfarm.game.jobProgress
import com.ditherfarm.game.nextToolRecipe
import com.ditherfarm.game.recipeBlocker
import com.ditherfarm.game.recipesFor
import com.ditherfarm.game.secondsLeft
import com.ditherfarm.game.toolTier
import com.ditherfarm.game.trayCount
import com.ditherfarm.game.baseId
import com.ditherfarm.game.crackBlocker
import com.ditherfarm.game.crackGeode
import com.ditherfarm.game.elevatorStops
import com.ditherfarm.game.enterFloor
import com.ditherfarm.game.gateBlocker

/** Modal panels opened from the HUD. Each draw call returns false when the panel should close. */
class Panels(private val game: Game, private val hud: Hud) {
    private val ui get() = game.ui
    private val g get() = game.gfx
    private val world get() = game.world

    private var barnTab = 0
    private var benchTab = 0
    private var selected: String? = null
    private var confirmNewGame = 0

    fun reset() { selected = null; confirmNewGame = 0 }

    /** Sprite for an inventory key: crop/item icon, falling back to the generic ui dot. */
    fun icon(key: String, big: Boolean = false): String {
        val id = baseId(key)
        val dir = if (big) "items_big" else "items"
        return when {
            game.assets.has("$dir/$id") -> "$dir/$id"
            game.assets.has("icons${if (big) "_big" else ""}/$id") -> "icons${if (big) "_big" else ""}/$id"
            else -> "ui/dot"
        }
    }

    private fun frame(title: String, ph: Int, pw: Int = game.engine.w - 16): IntArray {
        val w = game.engine.w
        val x = (w - pw) / 2
        val y = game.engine.safeTop + hud.topHeight + 4
        ui.modal(x, y, pw, ph)
        ui.panel(x, y, pw, ph)
        g.text(title, x + 10, y + 8, Gfx.GOLD)
        return intArrayOf(x, y, pw, ph)
    }

    private fun closeButton(x: Int, y: Int, pw: Int): Boolean =
        ui.button("close", x + pw - 22, y + 5, 16, 14, null, "ui/x", "red")

    // ------------------------------------------------------------------ barn
    fun barn(): Boolean {
        val maxH = game.engine.h - game.engine.safeTop - game.engine.safeBottom - hud.topHeight - hud.bottomHeight - 12
        val (x, y, pw, ph) = frame("STORAGE", maxH).toList()
        if (closeButton(x, y, pw)) return false
        val storage = if (barnTab == 0) Storage.SILO else Storage.BARN
        val newTab = ui.tabs("barn-tabs", x + 8, y + 22, pw - 16, listOf("SILO", "BARN"), barnTab)
        if (newTab != barnTab) { barnTab = newTab; selected = null }
        val used = world.used(storage)
        val cap = world.capacity(storage)
        ui.bar(x + 10, y + 42, pw - 90, used.toFloat() / cap, if (used >= cap * 0.9) "red" else "gold")
        g.text("$used/$cap", x + pw - 76, y + 41, Gfx.INK, outline = 0, shadow = false)
        if (world.upgradeCost(storage) != null && ui.button("upgrade", x + pw - 40, y + 38, 32, 14, null, "icons/arrow_up", if (world.canUpgrade(storage)) "green" else "grey")) {
            if (world.upgrade(storage)) { game.sfx.play("levelup"); hud.toast("${if (storage == Storage.BARN) "Barn" else "Silo"} upgraded!") }
            else hud.toast(upgradeText(storage))
        }
        val keys = world.state.inventory.keys.filter { world.data.item(baseId(it)).storage == storage }.sorted()
        val size = 34
        val cols = (pw - 16) / (size + 2)
        val gridY = y + 56
        val gridH = ph - 56 - 64
        val rows = (keys.size + cols - 1) / cols
        val off = ui.scroll("barn-grid", x + 6, gridY, pw - 12, gridH, rows * (size + 2))
        val c = g.canvas
        c.save()
        c.clipRect(x + 6, gridY, x + pw - 6, gridY + gridH)
        keys.forEachIndexed { i, k ->
            val sx = x + 8 + (i % cols) * (size + 2)
            val sy = gridY + (i / cols) * (size + 2) - off
            if (sy + size < gridY || sy > gridY + gridH) return@forEachIndexed
            if (ui.slot("inv-$k", sx, sy, icon(k, big = true), world.count(k), k == selected, size)) selected = k
            Quality.of(k).icon?.let { g.sprite(it, (sx + 7).toFloat(), (sy + 7).toFloat()) }
        }
        c.restore()
        if (keys.isEmpty()) g.text("EMPTY", x + pw / 2, gridY + 20, Gfx.GREY, align = 0, outline = 0, shadow = false)
        selected?.let { k -> if (world.count(k) > 0) itemActions(k, x, y + ph - 60, pw) else selected = null }
        return true
    }

    private fun upgradeText(s: Storage): String {
        val t = world.upgradeCost(s) ?: return "Max level"
        if (t.unlockLevel > world.level) return "Upgrade unlocks at level ${t.unlockLevel}"
        return "Needs " + t.cost.entries.joinToString(", ") { (k, v) -> if (k == "coins") "${v}c" else "$v ${world.data.name(k)}" }
    }

    /** Sell / eat strip for the selected item. */
    private fun itemActions(key: String, x: Int, y: Int, pw: Int) {
        g.rect(x + 8, y, x + pw - 8, y + 1, 0xFFC28569.toInt())
        g.sprite(icon(key, big = true), (x + 26).toFloat(), (y + 22).toFloat())
        Quality.of(key).icon?.let { g.sprite(it, (x + 16).toFloat(), (y + 12).toFloat()) }
        val name = world.data.name(key).uppercase()
        g.text(name, x + 46, y + 6, Gfx.INK, outline = 0, shadow = false)
        val unit = world.unitPrice(key)
        if (unit > 0) {
            g.sprite("icons/coin", (x + 52).toFloat(), (y + 22).toFloat())
            g.text("$unit", x + 60, y + 18, Gfx.INK, outline = 0, shadow = false)
            val trend = world.trend(baseId(key))
            if (trend != 0) g.sprite(if (trend > 0) "ui/arrow_up" else "ui/arrow_down", (x + 64 + g.textWidth("$unit")).toFloat(), (y + 22).toFloat())
            g.text("${world.percentOfBase(baseId(key))}%", x + 74 + g.textWidth("$unit"), y + 18, Gfx.GREY, outline = 0, shadow = false)
        }
        var bx = x + pw - 10
        val have = world.count(key)
        if (unit > 0) {
            bx -= 60
            if (ui.button("sell-all", bx, y + 34, 56, 18, "SELL ALL", style = "gold")) {
                val coins = world.sell(key, have)
                if (coins > 0) { game.sfx.play("sell"); game.haptics.click() }
            }
            bx -= 48
            if (ui.button("sell-1", bx, y + 34, 44, 18, "SELL 1", style = "gold")) {
                if (world.sell(key, 1) > 0) game.sfx.play("coin")
            }
        }
        if (world.foodEnergy(key) > 0) {
            bx -= 44
            if (ui.button("eat", bx, y + 34, 40, 18, "EAT", style = "green")) {
                if (world.eat(key)) { game.sfx.play("eat"); game.haptics.tick() } else game.sfx.play("error")
            }
            g.sprite("icons/energy", (x + 12).toFloat(), (y + 44).toFloat())
            g.text("+${world.foodEnergy(key)}", x + 18, y + 40, Gfx.INK, outline = 0, shadow = false)
        }
    }

    // ---------------------------------------------------------------- market
    fun market(): Boolean {
        val maxH = game.engine.h - game.engine.safeTop - game.engine.safeBottom - hud.topHeight - hud.bottomHeight - 12
        val (x, y, pw, ph) = frame("MARKET", maxH).toList()
        if (closeButton(x, y, pw)) return false
        g.text("PRICES CHANGE DAILY", x + 10, y + 20, Gfx.GREY, outline = 0, shadow = false)
        val keys = world.state.inventory.keys.filter { world.unitPrice(it) > 0 }.sortedByDescending { world.unitPrice(it) * world.count(it) }
        val rowH = 24
        val listY = y + 34
        val listH = ph - 34 - 30
        val off = ui.scroll("market", x + 6, listY, pw - 12, listH, keys.size * rowH)
        val c = g.canvas
        c.save()
        c.clipRect(x + 6, listY, x + pw - 6, listY + listH)
        keys.forEachIndexed { i, k ->
            val ry = listY + i * rowH - off
            if (ry + rowH < listY || ry > listY + listH) return@forEachIndexed
            if (i % 2 == 0) g.rect(x + 8, ry, x + pw - 8, ry + rowH - 1, 0x22B86F50)
            g.sprite(icon(k), (x + 18).toFloat(), (ry + rowH / 2).toFloat())
            Quality.of(k).icon?.let { g.sprite(it, (x + 12).toFloat(), (ry + 6).toFloat()) }
            g.text("${world.count(k)}x ${world.data.name(k).uppercase()}".take(18), x + 30, ry + 4, Gfx.INK, outline = 0, shadow = false)
            val unit = world.unitPrice(k)
            g.text("${unit}c", x + 30, ry + 13, Gfx.INK, outline = 0, shadow = false)
            val trend = world.trend(baseId(k))
            g.sprite(if (trend > 0) "ui/arrow_up" else if (trend < 0) "ui/arrow_down" else "ui/dot", (x + 38 + g.textWidth("${unit}c")).toFloat(), (ry + 17).toFloat())
            if (ui.button("m-$k", x + pw - 66, ry + 3, 56, 18, "SELL", style = "gold")) {
                if (world.sell(k, world.count(k)) > 0) { game.sfx.play("sell"); game.haptics.click() }
            }
        }
        c.restore()
        if (keys.isEmpty()) g.text("NOTHING TO SELL", x + pw / 2, listY + 20, Gfx.GREY, align = 0, outline = 0, shadow = false)
        val total = keys.filter { world.data.item(baseId(it)).storage == Storage.SILO }.sumOf { world.quoteSale(it, world.count(it)) }
        if (total > 0 && ui.button("sell-crops", x + 10, y + ph - 24, pw - 20, 18, "SELL ALL CROPS  ${total}c", style = "green")) {
            keys.filter { world.data.item(baseId(it)).storage == Storage.SILO }.forEach { world.sell(it, world.count(it)) }
            game.sfx.play("sell"); game.haptics.heavy()
        }
        return true
    }

    // ---------------------------------------------------------------- orders
    fun orders(): Boolean {
        val slots = world.state.orders
        val cardH = 50
        val ph = 30 + slots.size * (cardH + 4)
        val (x, y, pw, _) = frame("ORDERS", ph).toList()
        if (closeButton(x, y, pw)) return false
        slots.forEachIndexed { i, s ->
            val cy = y + 26 + i * (cardH + 4)
            g.slice9("ui/slot", x + 8, cy, x + pw - 8, cy + cardH, 4)
            val o = s.order
            if (o == null) {
                val left = Time.gameMinutesToRealSeconds((s.nextAt - world.state.minutes).coerceAtLeast(0.0)).toInt()
                g.text(if (left > 0) "NEXT ORDER IN %d:%02d".format(left / 60, left % 60) else "WAITING FOR A VILLAGER", x + pw / 2, cy + cardH / 2 - 4, Gfx.GREY, align = 0, outline = 0, shadow = false)
                return@forEachIndexed
            }
            val v = world.data.villagers.firstOrNull { it.id == o.villager }
            g.text((v?.name ?: o.villager).uppercase(), x + 14, cy + 5, Gfx.INK, outline = 0, shadow = false)
            val hearts = world.hearts(o.villager)
            for (h in 0 until hearts) g.sprite("icons/heart", (x + 18 + g.textWidth(v?.name ?: "") + h * 8).toFloat(), (cy + 9).toFloat())
            o.lines.forEachIndexed { li, line ->
                val lx = x + 14 + li * 58
                val have = world.count(line.item, anyQuality = true)
                g.sprite(icon(line.item), (lx + 7).toFloat(), (cy + 28).toFloat())
                g.text("$have/${line.qty}", lx + 16, cy + 24, if (have >= line.qty) 0xFF3E8948.toInt() else 0xFFA22633.toInt(), outline = 0, shadow = false)
            }
            g.sprite("icons/coin", (x + 16).toFloat(), (cy + 42).toFloat())
            g.text("${o.coins}", x + 24, cy + 38, Gfx.INK, outline = 0, shadow = false)
            g.sprite("icons/xp", (x + 34 + g.textWidth("${o.coins}")).toFloat(), (cy + 42).toFloat())
            g.text("${o.xp}", x + 42 + g.textWidth("${o.coins}"), cy + 38, Gfx.INK, outline = 0, shadow = false)
            val can = world.canDeliver(i)
            if (ui.button("deliver-$i", x + pw - 76, cy + 28, 50, 18, "SEND", style = if (can) "green" else "grey")) {
                if (world.deliver(i)) { game.sfx.play("sell"); game.haptics.heavy() } else game.sfx.play("error")
            }
            if (ui.button("trash-$i", x + pw - 24, cy + 28, 14, 18, null, "ui/x", "red")) world.trashOrder(i)
        }
        return true
    }

    // -------------------------------------------------------------- settings
    fun settings(): Boolean {
        val (x, y, pw, ph) = frame("SETTINGS", 186).toList()
        if (closeButton(x, y, pw)) return false
        val s = world.state
        fun row(i: Int, icon: String, label: String, on: Boolean): Boolean {
            val ry = y + 28 + i * 20
            g.sprite(icon, (x + 18).toFloat(), (ry + 6).toFloat())
            g.text(label, x + 30, ry + 2, Gfx.INK, outline = 0, shadow = false)
            return ui.toggle("t-$label", x + pw - 34, ry + 1, on)
        }
        if (row(0, "icons/sound_on", "SOUND", s.sfx)) { s.sfx = !s.sfx; game.applySettings() }
        if (row(1, "icons/vibrate", "VIBRATION", s.haptics)) { s.haptics = !s.haptics; game.applySettings() }
        if (row(2, "icons/bell", "NOTIFICATIONS", s.notifications)) s.notifications = !s.notifications
        val by = y + 96
        if (ui.button("export", x + 10, by, (pw - 26) / 2, 18, "EXPORT", "icons/export", "blue")) game.host.requestExport()
        if (ui.button("import", x + 16 + (pw - 26) / 2, by, (pw - 26) / 2, 18, "IMPORT", "icons/import", "blue")) game.host.requestImport()
        val label = when (confirmNewGame) { 0 -> "NEW FARM"; 1 -> "SURE? TAP AGAIN"; else -> "REALLY? LAST TAP" }
        if (ui.button("newgame", x + 10, by + 24, pw - 20, 18, label, style = "red")) {
            confirmNewGame++
            if (confirmNewGame >= 3) { game.newGame(); confirmNewGame = 0; hud.toast("A fresh start!"); return false }
        }
        g.text("DITHER FARM 0.1", x + pw / 2, y + ph - 30, Gfx.GREY, align = 0, outline = 0, shadow = false)
        g.text("FONT SILKSCREEN (OFL) - ENDESGA 32", x + pw / 2, y + ph - 19, Gfx.GREY, align = 0, outline = 0, shadow = false)
        return true
    }

    // -------------------------------------------------------------- level up
    fun levelUp(level: Int, coins: Int): Boolean {
        val unlocks = world.data.crops.values.filter { it.unlockLevel == level }.map { it.id }
        val ph = 92 + if (unlocks.isEmpty()) 0 else 24
        val pw = 200
        val w = game.engine.w
        val x = (w - pw) / 2
        val y = game.engine.h / 2 - ph / 2
        ui.modal(x, y, pw, ph)
        ui.panel(x, y, pw, ph)
        g.sprite("icons_big/level", (w / 2).toFloat(), (y + 22).toFloat())
        g.text("LEVEL $level!", w / 2, y + 40, Gfx.GOLD, align = 0)
        g.sprite("icons/coin", (w / 2 - 14).toFloat(), (y + 58).toFloat())
        g.text("+$coins", w / 2 - 6, y + 54, Gfx.INK, outline = 0, shadow = false)
        unlocks.forEachIndexed { i, id ->
            g.sprite("items/seed_$id", (w / 2 - unlocks.size * 10 + i * 20 + 10).toFloat(), (y + 76).toFloat())
        }
        return !ui.button("lvl-ok", w / 2 - 24, y + ph - 22, 48, 16, "YAY!", style = "gold")
    }


    // ------------------------------------------------------------ machines
    /** Sprite for a building: its own art, else a fitting icon. */
    fun buildingSprite(id: String): String {
        val own = "buildings/$id"
        if (game.assets.has(own)) return own
        return when (id) {
            "workbench" -> "icons_big/craft"; "windmill" -> "icons_big/settings"; "sawmill" -> "icons_big/tool_axe"
            "furnace" -> "icons_big/warning"; "beehive" -> "icons_big/gift"; "compost_bin" -> "icons_big/fertilizer"
            else -> "icons_big/build"
        }
    }

    private fun outputIcon(r: RecipeDef): String {
        val out = r.outputs.keys.first()
        if (out.startsWith("@tool:")) return toolIcon(out.removePrefix("@tool:"))
        return icon(out)
    }

    private fun toolIcon(tool: String) = "icons/" + when (tool) { "can" -> "tool_water_can"; else -> "tool_$tool" }

    private fun recipeName(r: RecipeDef): String {
        r.name?.let { return it.uppercase() }
        val out = r.outputs.keys.first()
        if (out.startsWith("@tool:")) return "${world.data.tools.names[r.outputs.values.first()]} ${out.removePrefix("@tool:")}".uppercase()
        val n = r.outputs.values.first()
        return (if (n > 1) "${n}x " else "") + world.data.name(out).uppercase()
    }

    /** One recipe row: output, name, inputs with have/need, time and a MAKE button. */
    private fun recipeRow(r: RecipeDef, x: Int, y: Int, w: Int, label: String = "MAKE"): Boolean {
        val locked = r.unlockLevel > world.level
        g.slice9("ui/slot", x, y, x + w, y + 34, 4)
        g.sprite(if (locked) "icons/lock" else outputIcon(r), (x + 12).toFloat(), (y + 12).toFloat())
        g.text(if (locked) "LEVEL ${r.unlockLevel}" else recipeName(r).take(20), x + 24, y + 4, Gfx.INK, outline = 0, shadow = false)
        var ix = x + 24
        for ((k, v) in r.inputs) {
            val have = world.available(k)
            g.sprite(if (k == "#crop") "icons/harvest" else icon(k), (ix + 6).toFloat(), (y + 22).toFloat())
            val t = "$have/$v"
            g.text(t, ix + 13, y + 18, if (have >= v) 0xFF3E8948.toInt() else 0xFFA22633.toInt(), outline = 0, shadow = false)
            ix += 16 + g.textWidth(t)
        }
        if (r.coins > 0) {
            g.sprite("icons/coin", (ix + 6).toFloat(), (y + 22).toFloat())
            g.text("${r.coins}", ix + 13, y + 18, if (world.state.coins >= r.coins) 0xFF3E8948.toInt() else 0xFFA22633.toInt(), outline = 0, shadow = false)
        }
        val mins = r.minutes
        g.text(if (mins < 1) "${(mins * 60).toInt()}S" else "${mins.toInt()}M", x + w - 62, y + 4, Gfx.GREY, outline = 0, shadow = false)
        val blocker = world.recipeBlocker(r)
        val clicked = ui.button("make-${r.id}", x + w - 48, y + 15, 42, 15, label, style = if (blocker == null) "green" else "grey")
        if (clicked) {
            if (blocker != null) { hud.toast(blocker); game.sfx.play("error") }
            else if (world.enqueue(r.id)) { game.sfx.play("craft"); game.haptics.click() }
        }
        return clicked
    }

    private fun queueStrip(id: String, x: Int, y: Int, w: Int) {
        val b = world.building(id)
        val def = world.data.buildings[id]
        val q = def?.queue ?: 1
        for (i in 0 until q) {
            val job = b.jobs.getOrNull(i)
            val sx = x + i * 24
            g.slice9("ui/slot", sx, y, sx + 22, y + 22, 4)
            job?.let { j -> world.data.recipes[j.recipe]?.let { g.sprite(outputIcon(it), (sx + 11).toFloat(), (y + 11).toFloat()) } }
        }
        val qx = x + q * 24 + 4
        if (b.jobs.isNotEmpty()) {
            ui.bar(qx, y + 3, w - (qx - x) - 64, world.jobProgress(id), "blue")
            val s = world.secondsLeft(id)?.toInt() ?: 0
            g.text("%d:%02d".format(s / 60, s % 60), qx, y + 12, Gfx.INK, outline = 0, shadow = false)
            if (ui.button("cancel", x + w - 60, y + 3, 16, 16, null, "ui/x", "red")) { if (world.cancelLast(id)) game.sfx.play("close") }
        } else {
            g.text("IDLE", qx, y + 7, Gfx.GREY, outline = 0, shadow = false)
        }
        val ready = world.trayCount(id)
        if (ui.button("collect", x + w - 40, y + 3, 40, 16, if (ready > 0) "x$ready" else "-", style = if (ready > 0) "gold" else "grey")) {
            if (ready > 0) world.collect(id)
        }
    }

    fun machine(id: String): Boolean {
        val def = world.data.buildings[id] ?: return false
        val recipes = world.recipesFor(id)
        val maxH = game.engine.h - game.engine.safeTop - game.engine.safeBottom - hud.topHeight - hud.bottomHeight - 12
        val fuel = def.fuelTank > 0
        val (x, y, pw, ph) = frame(def.name.uppercase(), minOf(maxH, 66 + (if (fuel) 20 else 0) + recipes.size * 36)).toList()
        if (closeButton(x, y, pw)) return false
        queueStrip(id, x + 8, y + 22, pw - 16)
        var top = y + 50
        if (fuel) {
            val b = world.building(id)
            g.sprite("items/coal", (x + 16).toFloat(), (top + 6).toFloat())
            ui.bar(x + 26, top + 3, pw - 100, (b.fuel / def.fuelTank).toFloat(), "red")
            g.text("%.1f".format(b.fuel), x + pw - 70, top + 2, Gfx.INK, outline = 0, shadow = false)
            if (ui.button("fuel", x + pw - 46, top, 38, 14, "+COAL", style = "wood")) {
                if (world.addFuel(id, 5) > 0) game.sfx.play("drip") else hud.toast("No coal")
            }
            top += 20
        }
        val listH = y + ph - 8 - top
        val off = ui.scroll("recipes-$id", x + 6, top, pw - 12, listH, recipes.size * 36)
        val c = g.canvas
        c.save(); c.clipRect(x + 6, top, x + pw - 6, top + listH)
        recipes.forEachIndexed { i, r ->
            val ry = top + i * 36 - off
            if (ry + 34 < top || ry > top + listH) return@forEachIndexed
            recipeRow(r, x + 8, ry, pw - 20)
        }
        c.restore()
        return true
    }

    fun build(id: String): Boolean {
        val def = world.data.buildings[id] ?: return false
        val rows = def.cost.size
        val (x, y, pw, ph) = frame("BUILD ${def.name.uppercase()}", 92 + rows * 14, pw = 210).toList()
        if (closeButton(x, y, pw)) return false
        g.sprite(buildingSprite(id), (x + 30).toFloat(), (y + 46).toFloat())
        def.cost.entries.forEachIndexed { i, (k, v) ->
            val ry = y + 26 + i * 14
            val have = if (k == "coins") world.state.coins else world.count(k, anyQuality = true)
            g.sprite(if (k == "coins") "icons/coin" else icon(k), (x + 62).toFloat(), (ry + 5).toFloat())
            g.text("$v ${if (k == "coins") "COINS" else world.data.name(k).uppercase()}".take(20), x + 72, ry + 1, if (have >= v) 0xFF3E8948.toInt() else 0xFFA22633.toInt(), outline = 0, shadow = false)
        }
        val blocker = world.buildBlocker(id)
        if (blocker != null) g.text(blocker.uppercase(), x + pw / 2, y + ph - 36, Gfx.RED, align = 0, outline = 0, shadow = false)
        if (ui.button("build-$id", x + pw / 2 - 40, y + ph - 22, 80, 16, "BUILD", style = if (blocker == null) "green" else "grey")) {
            if (world.build(id)) return false
        }
        return true
    }

    fun workbench(): Boolean {
        val maxH = game.engine.h - game.engine.safeTop - game.engine.safeBottom - hud.topHeight - hud.bottomHeight - 12
        val (x, y, pw, ph) = frame("WORKBENCH", maxH).toList()
        if (closeButton(x, y, pw)) return false
        queueStrip("workbench", x + 8, y + 22, pw - 16)
        benchTab = ui.tabs("bench-tabs", x + 8, y + 48, pw - 16, listOf("PARTS", "FARM", "TOOLS"), benchTab)
        val top = y + 66
        val listH = y + ph - 8 - top
        val farmItems = setOf("wb_quality_fertilizer", "wb_scarecrow", "wb_lightning_rod", "wb_copper_sprinkler", "wb_iron_sprinkler", "wb_gold_sprinkler")
        val all = world.recipesFor("workbench")
        val c = g.canvas
        if (benchTab == 2) {
            Tool.entries.forEachIndexed { i, tool ->
                val ry = top + i * 36
                val next = world.nextToolRecipe(tool)
                if (next == null) {
                    g.slice9("ui/slot", x + 8, ry, x + pw - 12, ry + 34, 4)
                    g.sprite(toolIcon(tool.name.lowercase()), (x + 20).toFloat(), (ry + 12).toFloat())
                    g.text("GOLD ${tool.label.uppercase()} - MAX", x + 32, ry + 12, Gfx.INK, outline = 0, shadow = false)
                } else {
                    recipeRow(next, x + 8, ry, pw - 20, "UPGRADE")
                    g.text(world.data.tools.names[world.toolTier(tool)].uppercase(), x + 8 + pw - 20 - 104, ry + 4, Gfx.GREY, outline = 0, shadow = false)
                }
            }
            return true
        }
        val list = all.filter { !it.id.startsWith("tool_") && ((benchTab == 1) == (it.id in farmItems)) }
        val off = ui.scroll("bench-$benchTab", x + 6, top, pw - 12, listH, list.size * 36)
        c.save(); c.clipRect(x + 6, top, x + pw - 6, top + listH)
        list.forEachIndexed { i, r ->
            val ry = top + i * 36 - off
            if (ry + 34 < top || ry > top + listH) return@forEachIndexed
            recipeRow(r, x + 8, ry, pw - 20)
        }
        c.restore()
        return true
    }

    // ------------------------------------------------------------------ mine
    private var lastGeode: Map<String, Int> = emptyMap()

    /** Geode Anvil: crack geodes for a small fee. */
    fun geodes(): Boolean {
        val types = world.data.mine.geodes.keys.toList()
        val (x, y, pw, ph) = frame("GEODE ANVIL", 58 + types.size * 30, pw = 220).toList()
        if (closeButton(x, y, pw)) { lastGeode = emptyMap(); return false }
        types.forEachIndexed { i, id ->
            val ry = y + 24 + i * 30
            val have = world.count(id)
            ui.slot("geode-$id", x + 10, ry, icon(id), have)
            g.text(world.data.name(id).uppercase(), x + 36, ry + 2, Gfx.INK, outline = 0, shadow = false)
            g.sprite("icons/coin", (x + 40).toFloat(), (ry + 15).toFloat())
            g.text("${world.data.mine.geodes.getValue(id).fee}", x + 47, ry + 12, Gfx.INK, outline = 0, shadow = false)
            val ok = world.crackBlocker(id) == null
            if (ui.button("crack-$id", x + pw - 64, ry + 2, 54, 16, "CRACK", style = if (ok) "green" else "grey")) {
                val out = world.crackGeode(id)
                if (out.isNotEmpty()) lastGeode = out
            }
        }
        val ry = y + ph - 18
        if (lastGeode.isNotEmpty()) {
            val (k, n) = lastGeode.entries.first()
            g.sprite(icon(k), (x + 16).toFloat(), (ry + 4).toFloat())
            g.text("GOT $n ${world.data.name(k).uppercase()}".take(28), x + 26, ry, 0xFF3E8948.toInt(), outline = 0, shadow = false)
        } else g.text("FEE PER CRACK. GEMS ARE RARE!", x + pw / 2, ry, Gfx.INK, align = 0, outline = 0, shadow = false)
        return true
    }

    /** Mine elevator: ride to the surface or any stop already reached (every 5 floors). */
    fun elevator(): Boolean {
        val stops = world.elevatorStops()
        val perRow = 5
        val rows = (stops.size + perRow - 1) / perRow
        val next = (stops.last() + 5).takeIf { it <= 40 }
        val (x, y, pw, ph) = frame("ELEVATOR", 62 + rows * 22, pw = 220).toList()
        if (closeButton(x, y, pw)) return false
        val bw = (pw - 20 - (perRow - 1) * 4) / perRow
        stops.forEachIndexed { i, f ->
            val bx = x + 10 + (i % perRow) * (bw + 4)
            val by = y + 24 + (i / perRow) * 22
            val here = world.state.mine.floor == f
            if (ui.button("stop-$f", bx, by, bw, 18, if (f == 0) "TOP" else "B$f", style = if (here) "grey" else if (f == 0) "wood" else "green")) {
                if (!here && world.enterFloor(f)) return false
            }
        }
        val note = when {
            stops.size == 1 -> "REACH FLOOR 5 TO ADD A STOP"
            next != null -> world.gateBlocker(next)?.uppercase() ?: "NEXT STOP: FLOOR $next"
            else -> "YOU REACHED THE BOTTOM!"
        }
        g.text(note.take(34), x + pw / 2, y + ph - 16, Gfx.INK, align = 0, outline = 0, shadow = false)
        return true
    }
}
