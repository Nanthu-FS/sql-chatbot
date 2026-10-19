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
import com.ditherfarm.game.BULK_MAX
import com.ditherfarm.game.canBulk
import com.ditherfarm.game.enqueue
import com.ditherfarm.game.maxBatches
import com.ditherfarm.game.jobProgress
import com.ditherfarm.game.nextToolRecipe
import com.ditherfarm.game.recipeBlocker
import com.ditherfarm.game.recipesFor
import com.ditherfarm.game.secondsLeft
import com.ditherfarm.game.toolTier
import com.ditherfarm.game.trayCount
import com.ditherfarm.game.baseId
import com.ditherfarm.game.Fixture
import com.ditherfarm.game.shedSecondsLeft
import com.ditherfarm.game.AchievementDef
import com.ditherfarm.game.FESTIVALS
import com.ditherfarm.game.FESTIVAL_DAYS
import com.ditherfarm.game.MERCHANT_LEVEL
import com.ditherfarm.game.Season
import com.ditherfarm.game.buyFromMerchant
import com.ditherfarm.game.claimAchievement
import com.ditherfarm.game.claimFestival
import com.ditherfarm.game.claimGift
import com.ditherfarm.game.claimPage
import com.ditherfarm.game.claimable
import com.ditherfarm.game.claimedTiers
import com.ditherfarm.game.entryFound
import com.ditherfarm.game.festival
import com.ditherfarm.game.festivalClaimable
import com.ditherfarm.game.festivalHandIn
import com.ditherfarm.game.festivalItems
import com.ditherfarm.game.festivalProgress
import com.ditherfarm.game.giftFor
import com.ditherfarm.game.giftStreakDay
import com.ditherfarm.game.merchantBought
import com.ditherfarm.game.merchantHere
import com.ditherfarm.game.merchantStock
import com.ditherfarm.game.metric
import com.ditherfarm.game.pageClaimable
import com.ditherfarm.game.pageEntries
import com.ditherfarm.game.pageProgress
import com.ditherfarm.game.target
import com.ditherfarm.game.hutCap
import com.ditherfarm.game.hutTier
import com.ditherfarm.game.CART_DAILY_CAP
import com.ditherfarm.game.CART_MAX_RULES
import com.ditherfarm.game.CartRule
import com.ditherfarm.game.addCartRule
import com.ditherfarm.game.crackBlocker
import com.ditherfarm.game.crackGeode
import com.ditherfarm.game.elevatorStops
import com.ditherfarm.game.enterFloor
import com.ditherfarm.game.gateBlocker
import com.ditherfarm.game.CRATE_CAP
import com.ditherfarm.game.FARMHANDS
import com.ditherfarm.game.FieldLayout
import com.ditherfarm.game.truckCap
import com.ditherfarm.game.SEED_PACK
import com.ditherfarm.game.VILLAGE_TIERS
import com.ditherfarm.game.buildCost
import com.ditherfarm.game.buySeedPack
import com.ditherfarm.game.bundleDone
import com.ditherfarm.game.coinsDonated
import com.ditherfarm.game.donate
import com.ditherfarm.game.donateCoins
import com.ditherfarm.game.donated
import com.ditherfarm.game.nextTier
import com.ditherfarm.game.rebuild
import com.ditherfarm.game.rebuildBlocker
import com.ditherfarm.game.seedPackPrice
import com.ditherfarm.game.tierProgress
import com.ditherfarm.game.villageTier
import com.ditherfarm.game.WORKER_MAX_LEVEL
import com.ditherfarm.game.assign
import com.ditherfarm.game.blockOwned
import com.ditherfarm.game.hire
import com.ditherfarm.game.levelUpWorker
import com.ditherfarm.game.truckLoad
import com.ditherfarm.game.truckSecondsLeft
import com.ditherfarm.game.workerAppetite
import com.ditherfarm.game.workerLevelCost
import com.ditherfarm.game.workerOn
import com.ditherfarm.game.workerPlotsPerMinute
import com.ditherfarm.game.PET_NAME_MAX
import com.ditherfarm.game.PetKind
import com.ditherfarm.game.DECOR
import com.ditherfarm.game.beauty
import com.ditherfarm.game.decorBlocker
import com.ditherfarm.game.decorBonus
import com.ditherfarm.game.adoptPet
import com.ditherfarm.game.petOffered
import com.ditherfarm.game.BRANCHES
import com.ditherfarm.game.TONICS
import com.ditherfarm.game.learn
import com.ditherfarm.game.merchantPrice
import com.ditherfarm.game.queueSize
import com.ditherfarm.game.rank
import com.ditherfarm.game.respec
import com.ditherfarm.game.respecCost
import com.ditherfarm.game.skillBlocker
import com.ditherfarm.game.skillPoints
import com.ditherfarm.game.useBlocker
import com.ditherfarm.game.useTonic
import com.ditherfarm.game.CHORES_LEVEL
import com.ditherfarm.game.choreDone
import com.ditherfarm.game.choreProgress
import com.ditherfarm.game.choreReward
import com.ditherfarm.game.choreStreakNow
import com.ditherfarm.game.claimChore
import com.ditherfarm.game.claimContract
import com.ditherfarm.game.contractDaysLeft
import com.ditherfarm.game.contractDone
import com.ditherfarm.game.contractReward
import com.ditherfarm.game.contractShipped
import com.ditherfarm.game.streakBonus
import com.ditherfarm.game.togglePin

/** Modal panels opened from the HUD. Each draw call returns false when the panel should close. */
class Panels(private val game: Game, private val hud: Hud) {
    private val ui get() = game.ui
    private val g get() = game.gfx
    private val world get() = game.world

    private var barnTab = 0
    internal var benchTab = 0
    /** Called with a fixture item when the player taps PLACE in the Barn. */
    var onPlace: ((String) -> Unit)? = null
    /** Called when Settings' STORY button is tapped (replays the opening). */
    var onStory: (() -> Unit)? = null
    /** Called with a decoration id when BUY is tapped in the decor shop, and to open the shop. */
    var onDecor: ((String) -> Unit)? = null
    var onDecorShop: (() -> Unit)? = null
    /** Called when the Workbench's SKILLS button is tapped. */
    var onSkills: (() -> Unit)? = null
    private var skillTab = 0
    private var selected: String? = null
    private var confirmNewGame = 0

    fun reset() { selected = null; confirmNewGame = 0 }

    companion object {
        /** [craftAmount] value for MAX: as many batches as you can afford. */
        const val AMOUNT_MAX = 0

        /** Tabs of the goals panel. */
        const val TAB_DAILY = 0
        const val TAB_GOALS = 1
    }

    /** Sprite for a goal icon: a sprite path as is, an item id through [icon]. */
    fun goalIcon(key: String) = if ('/' in key) key else icon(key)

    /** A pin centred at ([cx], [cy]) that pins [key] to the goal chip; faded while unpinned. */
    fun pinToggle(key: String, cx: Int, cy: Int) {
        val on = world.state.pinned == key
        g.sprite("icons/pin", cx.toFloat(), cy.toFloat(), if (on) 255 else 90)
        if (ui.clickable("pin-$key", cx - 8, cy - 8, 16, 16)) {
            game.sfx.play("click")
            hud.toast(if (world.togglePin(key)) "Pinned to the goal chip" else "Unpinned")
        }
    }

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

    /** [s] if it fits in [maxW] pixels, else cut at the last whole letter that fits with a dot. */
    private fun fit(s: String, maxW: Int): String {
        if (g.textWidth(s) <= maxW) return s
        var n = s.length
        while (n > 1 && g.textWidth(s.take(n) + ".") > maxW) n--
        return s.take(n).trimEnd() + "."
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
        if (baseId(key) in TONICS) {
            bx -= 44
            val why = world.useBlocker(baseId(key))
            if (ui.button("use", bx, y + 34, 40, 18, "USE", style = if (why == null) "green" else "grey")) {
                if (why != null) hud.toast(why) else world.useTonic(baseId(key))
            }
        }
        if (Fixture.of(baseId(key)) != null) {
            bx -= 52
            if (ui.button("place", bx, y + 34, 48, 18, "PLACE", style = "green")) onPlace?.invoke(baseId(key))
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
            g.text(fit("${world.count(k)}x ${world.data.name(k).uppercase()}", pw - 104), x + 30, ry + 4, Gfx.INK, outline = 0, shadow = false)
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
            val portrait = "portraits/${o.villager}"
            if (game.assets.has(portrait)) g.sprite(portrait, (x + 27).toFloat(), (cy + cardH / 2).toFloat())
            val tx = x + 48
            g.text((v?.name ?: o.villager).uppercase(), tx, cy + 5, Gfx.INK, outline = 0, shadow = false)
            val hearts = world.hearts(o.villager)
            for (h in 0 until hearts) g.sprite("icons/heart", (tx + 4 + g.textWidth((v?.name ?: "").uppercase()) + h * 8).toFloat(), (cy + 9).toFloat())
            o.lines.forEachIndexed { li, line ->
                val lx = tx + li * 50
                val have = world.count(line.item, anyQuality = true)
                g.sprite(icon(line.item), (lx + 7).toFloat(), (cy + 26).toFloat())
                g.text("$have/${line.qty}", lx + 16, cy + 22, if (have >= line.qty) 0xFF3E8948.toInt() else 0xFFA22633.toInt(), outline = 0, shadow = false)
            }
            g.sprite("icons/coin", (tx + 2).toFloat(), (cy + 42).toFloat())
            g.text("${o.coins}", tx + 10, cy + 38, Gfx.INK, outline = 0, shadow = false)
            g.sprite("icons/xp", (tx + 20 + g.textWidth("${o.coins}")).toFloat(), (cy + 42).toFloat())
            g.text("${o.xp}", tx + 28 + g.textWidth("${o.coins}"), cy + 38, Gfx.INK, outline = 0, shadow = false)
            val can = world.canDeliver(i)
            if (ui.button("deliver-$i", x + pw - 76, cy + 30, 50, 16, "SEND", style = if (can) "green" else "grey")) {
                if (world.deliver(i)) { game.sfx.play("sell"); game.haptics.heavy() } else game.sfx.play("error")
            }
            if (ui.button("trash-$i", x + pw - 24, cy + 30, 14, 16, null, "ui/x", "red")) world.trashOrder(i)
        }
        return true
    }

    // -------------------------------------------------------------- settings
    fun settings(): Boolean {
        val (x, y, pw, ph) = frame("SETTINGS", 274).toList()
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
        if (row(3, "icons/sound_on", "MUSIC", s.music)) { s.music = !s.music; game.applySettings() }
        if (row(4, "icons/weather_cloudy", "AMBIENCE", s.ambience)) { s.ambience = !s.ambience; game.applySettings() }
        val by = y + 136
        if (ui.button("export", x + 10, by, (pw - 26) / 2, 18, "EXPORT", "icons/export", "blue")) game.host.requestExport()
        if (ui.button("import", x + 16 + (pw - 26) / 2, by, (pw - 26) / 2, 18, "IMPORT", "icons/import", "blue")) game.host.requestImport()
        val label = when (confirmNewGame) { 0 -> "NEW FARM"; 1 -> "SURE? TAP AGAIN"; else -> "REALLY? LAST TAP" }
        if (ui.button("newgame", x + 10, by + 24, pw - 20, 18, label, style = "red")) {
            confirmNewGame++
            if (confirmNewGame >= 3) { game.newGame(); confirmNewGame = 0; hud.toast("A fresh start!"); return false }
        }
        if (ui.button("story", x + 10, by + 48, pw - 20, 18, "STORY: WATCH THE OPENING", "icons/book", "wood")) { onStory?.invoke(); return true }
        if (ui.button("decorate", x + 10, by + 72, pw - 20, 18, "DECORATE YOUR FARM", "icons/heart", "gold")) { onDecorShop?.invoke(); return true }
        g.text("DITHER FARM 0.1", x + pw / 2, y + ph - 30, Gfx.GREY, align = 0, outline = 0, shadow = false)
        g.text("FONT SILKSCREEN (OFL) - ENDESGA 32", x + pw / 2, y + ph - 19, Gfx.GREY, align = 0, outline = 0, shadow = false)
        return true
    }

    // -------------------------------------------------------------- level up
    fun levelUp(level: Int, coins: Int): Boolean {
        val unlocks = world.data.crops.values.filter { it.unlockLevel == level }.map { it.id }
        val ph = 102 + if (unlocks.isEmpty()) 0 else 24
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
        g.text("+1 SKILL POINT", w / 2, y + 66, 0xFF68386C.toInt(), align = 0, outline = 0, shadow = false)
        unlocks.forEachIndexed { i, id ->
            g.sprite("items/seed_$id", (w / 2 - unlocks.size * 10 + i * 20 + 10).toFloat(), (y + 86).toFloat())
        }
        return !ui.button("lvl-ok", w / 2 - 24, y + ph - 22, 48, 16, "YAY!", style = "gold")
    }


    // ------------------------------------------------------------ machines
    /** Draws a machine on its tile: its own art standing on the tile centre, else an icon on a shadow. */
    fun drawBuilding(id: String, x: Float, y: Float, winter: Boolean) {
        val own = "buildings/$id"
        if (game.assets.has(own)) {
            g.sprite(if (winter && game.assets.has("${own}_snow")) "${own}_snow" else own, x, y)
        } else {
            g.sprite("tiles/shadow", x, y - 6)
            g.sprite(buildingSprite(id), x, y - 12)
        }
    }

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

    /** How many batches each MAKE queues: 1, 5, 10, or [AMOUNT_MAX] for as many as you can afford. */
    var craftAmount = 1

    /** The x1 / x5 / x10 / MAX chips over a recipe list; returns the y below them. */
    private fun amountChips(x: Int, y: Int, w: Int): Int {
        g.text("MAKE AT ONCE", x + 2, y + 4, Gfx.INK, outline = 0, shadow = false)
        val opts = listOf(1, 5, 10, AMOUNT_MAX)
        val bw = 34
        var bx = x + w - opts.size * (bw + 3) + 3
        for (o in opts) {
            if (ui.button("amount-$o", bx, y, bw, 14, if (o == AMOUNT_MAX) "MAX" else "x$o", style = if (craftAmount == o) "gold" else "wood")) craftAmount = o
            bx += bw + 3
        }
        return y + 18
    }

    /** Batches a MAKE on [r] queues now: the chosen amount, or all you can afford for MAX; tools are one at a time. */
    private fun batchCount(r: RecipeDef): Int = when {
        !world.canBulk(r) -> 1
        craftAmount == AMOUNT_MAX -> world.maxBatches(r).coerceIn(1, BULK_MAX)
        else -> craftAmount
    }

    /** One recipe row: output, name, inputs with have/need, time and a MAKE button, for [batchCount] batches. */
    private fun recipeRow(r: RecipeDef, x: Int, y: Int, w: Int, label: String = "MAKE"): Boolean {
        val n = batchCount(r)
        val locked = r.unlockLevel > world.level
        g.slice9("ui/slot", x, y, x + w, y + 34, 4)
        g.sprite(if (locked) "icons/lock" else outputIcon(r), (x + 12).toFloat(), (y + 12).toFloat())
        // Locked recipes still say what they make, greyed, so there is something to look forward to.
        g.text(fit(recipeName(r), w - 24 - 66), x + 24, y + 4, if (locked) Gfx.GREY else Gfx.INK, outline = 0, shadow = false)
        var ix = x + 24
        for ((k, v) in r.inputs) {
            val have = world.available(k)
            g.sprite(if (k == "#crop") "icons/harvest" else icon(k), (ix + 6).toFloat(), (y + 22).toFloat())
            val t = "$have/${v * n}"
            g.text(t, ix + 13, y + 18, if (have >= v * n) 0xFF3E8948.toInt() else 0xFFA22633.toInt(), outline = 0, shadow = false)
            ix += 16 + g.textWidth(t)
        }
        if (r.coins > 0) {
            g.sprite("icons/coin", (ix + 6).toFloat(), (y + 22).toFloat())
            g.text("${r.coins * n}", ix + 13, y + 18, if (world.state.coins >= r.coins * n) 0xFF3E8948.toInt() else 0xFFA22633.toInt(), outline = 0, shadow = false)
        }
        val mins = r.minutes * n
        val time = when { mins < 1 -> "${(mins * 60).toInt()}S"; mins < 60 -> "${mins.toInt()}M"; else -> "${(mins / 60).toInt()}H${(mins % 60).toInt()}M" }
        g.text(time, x + w - 62, y + 4, Gfx.GREY, outline = 0, shadow = false)
        val blocker = world.recipeBlocker(r, n)
        val text = when { locked -> "LV ${r.unlockLevel}"; n > 1 -> "$label x$n"; else -> label }
        val clicked = ui.button("make-${r.id}", x + w - 56, y + 15, 50, 15, text, style = if (blocker == null) "green" else "grey")
        if (clicked) {
            if (blocker != null) { hud.toast(blocker); game.sfx.play("error") }
            else if (world.enqueue(r.id, n)) { game.sfx.play("craft"); game.haptics.click() }
        }
        return clicked
    }

    private fun queueStrip(id: String, x: Int, y: Int, w: Int) {
        val b = world.building(id)
        val def = world.data.buildings[id]
        val q = world.queueSize(id)
        for (i in 0 until q) {
            val job = b.jobs.getOrNull(i)
            val sx = x + i * 24
            g.slice9("ui/slot", sx, y, sx + 22, y + 22, 4)
            job?.let { j -> world.data.recipes[j.recipe]?.let { g.sprite(outputIcon(it), (sx + 11).toFloat(), (y + 11).toFloat()) } }
            // A bulk job shows how many batches it holds.
            job?.takeIf { it.count > 1 }?.let { g.text("x${it.count}", sx + 21, y + 13, Gfx.WHITE, align = 1) }
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
        val chips = if (recipes.any { world.canBulk(it) }) 18 else 0
        val (x, y, pw, ph) = frame(def.name.uppercase(), minOf(maxH, 66 + (if (fuel) 20 else 0) + chips + recipes.size * 36)).toList()
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
        if (recipes.any { world.canBulk(it) }) top = amountChips(x + 8, top, pw - 16)
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
        // The art stands below the title; tall buildings (Windmill, Silo) make the panel taller.
        val art = game.assets[buildingSprite(id)]
        val (x, y, pw, ph) = frame("BUILD ${def.name.uppercase()}", maxOf(92 + rows * 14, 24 + art.bmp.height + 46), pw = 210).toList()
        if (closeButton(x, y, pw)) return false
        g.sprite(art, (x + 30).toFloat(), (y + 22 + art.ay).toFloat())
        world.buildCost(id).entries.forEachIndexed { i, (k, v) ->
            val ry = y + 26 + i * 14
            val have = if (k == "coins") world.state.coins else world.count(k, anyQuality = true)
            g.sprite(if (k == "coins") "icons/coin" else icon(k), (x + 62).toFloat(), (ry + 5).toFloat())
            g.text(fit("$v ${if (k == "coins") "COINS" else world.data.name(k).uppercase()}", pw - 82), x + 72, ry + 1, if (have >= v) 0xFF3E8948.toInt() else 0xFFA22633.toInt(), outline = 0, shadow = false)
        }
        val blocker = world.buildBlocker(id)
        if (blocker != null) g.text(blocker.uppercase(), x + pw / 2, y + ph - 36, Gfx.RED, align = 0, outline = 0, shadow = false)
        pinToggle("build:$id", x + pw - 18, y + ph - 14)
        if (ui.button("build-$id", x + pw / 2 - 40, y + ph - 22, 80, 16, "BUILD", style = if (blocker == null) "green" else "grey")) {
            if (world.build(id)) return false
        }
        return true
    }

    fun workbench(): Boolean {
        val maxH = game.engine.h - game.engine.safeTop - game.engine.safeBottom - hud.topHeight - hud.bottomHeight - 12
        val (x, y, pw, ph) = frame("WORKBENCH", maxH).toList()
        if (closeButton(x, y, pw)) return false
        if (ui.button("bench-skills", x + pw - 84, y + 4, 58, 14, "SKILLS", style = "wood")) onSkills?.invoke()
        queueStrip("workbench", x + 8, y + 22, pw - 16)
        benchTab = ui.tabs("bench-tabs", x + 8, y + 48, pw - 16, listOf("PARTS", "FARM", "TONICS", "TOOLS"), benchTab)
        val top = y + 66
        val listH = y + ph - 8 - top
        val farmItems = setOf("wb_quality_fertilizer", "wb_scarecrow", "wb_lightning_rod", "wb_copper_sprinkler", "wb_iron_sprinkler", "wb_gold_sprinkler")
        val all = world.recipesFor("workbench")
        val c = g.canvas
        if (benchTab == 3) {
            Tool.entries.forEachIndexed { i, tool ->
                val ry = top + i * 36
                val next = world.nextToolRecipe(tool)
                if (next == null) {
                    g.slice9("ui/slot", x + 8, ry, x + pw - 12, ry + 34, 4)
                    g.sprite(toolIcon(tool.name.lowercase()), (x + 20).toFloat(), (ry + 12).toFloat())
                    g.text("GOLD ${tool.label.uppercase()} - MAX", x + 32, ry + 12, Gfx.INK, outline = 0, shadow = false)
                } else if (next.building != "workbench") {
                    // Iron and gold tools are forged at the village Blacksmith.
                    g.slice9("ui/slot", x + 8, ry, x + pw - 12, ry + 34, 4)
                    g.sprite(toolIcon(tool.name.lowercase()), (x + 20).toFloat(), (ry + 12).toFloat())
                    g.text("NEXT ${tool.label.uppercase()}: AT THE BLACKSMITH", x + 32, ry + 6, Gfx.INK, outline = 0, shadow = false)
                    g.text(if (world.villageTier >= 1) "IN THE VILLAGE" else "OPENS WITH THE STONE VILLAGE", x + 32, ry + 18, Gfx.GREY, outline = 0, shadow = false)
                } else {
                    recipeRow(next, x + 8, ry, pw - 20, "UPGRADE")
                    g.text(world.data.tools.names[world.toolTier(tool)].uppercase(), x + 8 + pw - 20 - 104, ry + 4, Gfx.GREY, outline = 0, shadow = false)
                }
            }
            return true
        }
        val tab = { r: RecipeDef -> when { r.outputs.keys.any { it in TONICS } -> 2; r.id in farmItems -> 1; else -> 0 } }
        val list = all.filter { !it.id.startsWith("tool_") && tab(it) == benchTab }
        val listTop = amountChips(x + 8, top, pw - 16)
        val listH2 = y + ph - 8 - listTop
        val off = ui.scroll("bench-$benchTab", x + 6, listTop, pw - 12, listH2, list.size * 36)
        c.save(); c.clipRect(x + 6, listTop, x + pw - 6, listTop + listH2)
        list.forEachIndexed { i, r ->
            val ry = listTop + i * 36 - off
            if (ry + 34 < listTop || ry > listTop + listH2) return@forEachIndexed
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
            g.text(fit("GOT $n ${world.data.name(k).uppercase()}", pw - 36), x + 26, ry, 0xFF3E8948.toInt(), outline = 0, shadow = false)
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

    // ------------------------------------------------------------------ sheds
    /** Harvest or Planter Shed status; the Planter Shed also picks Replant or Fill. */
    fun shed(id: String): Boolean {
        val def = world.data.buildings[id] ?: return false
        val hut = id == "logging_hut"
        val (x, y, pw, _) = frame(def.name.uppercase(), 82, pw = 220).toList()
        if (closeButton(x, y, pw)) return false
        val secs = world.shedSecondsLeft(id).toInt()
        g.text("NEXT RUN IN %d:%02d".format(secs / 60, secs % 60), x + 10, y + 24, Gfx.INK, outline = 0, shadow = false)
        g.text(if (hut) "TRAY: ${world.trayCount(id)}/${world.hutCap()}" else "TRAY: ${world.trayCount(id)}", x + 10, y + 35, Gfx.INK, outline = 0, shadow = false)
        val trees = when (world.hutTier()) { 1 -> "PINE, BIRCH"; 2 -> "PINE, BIRCH, OAK"; else -> "PINE, BIRCH, OAK, MAPLE" }
        g.text(if (hut) "FELLS $trees" else "CLEARS STUMPS, PLANTS SEEDS", x + 10, y + 48, Gfx.GREY, outline = 0, shadow = false)
        g.text(if (hut) "NEVER TOUCHES ANCIENT OR HIVE TREES" else "USES TREE SEEDS FROM THE BARN", x + 10, y + 60, Gfx.GREY, outline = 0, shadow = false)
        if (world.trayCount(id) > 0 && ui.button("shed-collect", x + pw - 70, y + 30, 60, 16, "COLLECT", style = "gold")) world.collect(id)
        return true
    }

    // ------------------------------------------------------------------ goals
    /** Open tab of the goals panel: 0 daily, 1 goals, 2 book, 3 events. */
    var goalsTab = 0
    private var bookPage = 0

    private val maxPanelH get() = game.engine.h - game.engine.safeTop - game.engine.safeBottom - hud.topHeight - hud.bottomHeight - 12

    /** Achievements, the collection book and events (festival, merchant, login streak). */
    fun goals(): Boolean {
        val (x, y, pw, ph) = frame("GOALS", maxPanelH).toList()
        if (closeButton(x, y, pw)) return false
        goalsTab = ui.tabs("goal-tabs", x + 8, y + 22, pw - 16, listOf("DAILY", "GOALS", "BOOK", "EVENTS"), goalsTab)
        val top = y + 42
        when (goalsTab) {
            TAB_DAILY -> daily(x, top, pw)
            TAB_GOALS -> achievementList(x, top, pw, y + ph - 8 - top)
            2 -> book(x, top, pw, y + ph - 8 - top)
            else -> eventsTab(x, top, pw)
        }
        return true
    }

    private fun roman(n: Int) = listOf("", "I", "II", "III", "IV", "V").getOrElse(n) { "$n" }

    private fun achievementList(x: Int, top: Int, pw: Int, listH: Int) {
        val list = world.data.goals.achievements.sortedWith(compareBy<AchievementDef>(
            { if (world.claimable(it)) 0 else if (world.claimedTiers(it.id) < it.tiers.size) 1 else 2 }, { it.name },
        ))
        val rowH = 34
        val off = ui.scroll("ach-list", x + 6, top, pw - 12, listH, list.size * rowH)
        val c = g.canvas
        c.save(); c.clipRect(x + 6, top, x + pw - 6, top + listH)
        list.forEachIndexed { i, a ->
            val ry = top + i * rowH - off
            if (ry + rowH < top || ry > top + listH) return@forEachIndexed
            val tier = world.claimedTiers(a.id)
            val done = tier >= a.tiers.size
            val shown = minOf(tier, a.tiers.size - 1)
            g.rect(x + 8, ry + rowH - 1, x + pw - 8, ry + rowH, 0xFFC28569.toInt())
            g.sprite("icons/trophy", (x + 18).toFloat(), (ry + 12).toFloat(), if (done || world.claimable(a)) 255 else 110)
            val title = a.name.uppercase() + if (a.tiers.size > 1) " " + roman(shown + 1) else ""
            g.text(title, x + 30, ry + 3, Gfx.INK, outline = 0, shadow = false)
            g.text(a.desc.uppercase().take(30), x + 30, ry + 13, Gfx.GREY, outline = 0, shadow = false)
            val target = world.target(a, shown)
            val have = minOf(world.metric(a.metric), target)
            val bw = pw - 110
            ui.bar(x + 30, ry + 23, bw, if (done) 1f else have.toFloat() / target, if (done) "gold" else "green", 6)
            g.text("${Ui.compact(have.toInt())}/${Ui.compact(target.toInt())}", x + 34 + bw, ry + 22, Gfx.INK, outline = 0, shadow = false)
            val t = a.tiers[shown]
            when {
                done -> g.sprite("icons/check", (x + pw - 22).toFloat(), (ry + 14).toFloat())
                world.claimable(a) -> if (ui.button("claim-${a.id}", x + pw - 58, ry + 4, 48, 16, "CLAIM", style = "gold")) world.claimAchievement(a.id)
                else -> pinToggle("ach:${a.id}:$tier", x + pw - 18, ry + 10)
            }
            if (!done) {
                val reward = if (t.coins > 0) "${Ui.compact(t.coins)}c" else if (t.xp > 0) "${t.xp}XP" else "GIFT"
                g.text(reward, x + pw - 12, ry + 22, 0xFF3E8948.toInt(), align = 1, outline = 0, shadow = false)
            }
        }
        c.restore()
    }

    private fun entryIcon(entry: String): String =
        if (entry.startsWith("villager:")) entry.removePrefix("villager:").let { if (game.assets.has("portraits/$it")) "portraits/$it" else "icons/heart" } else icon(entry)

    private fun book(x: Int, top: Int, pw: Int, h: Int) {
        val pages = world.data.goals.pages
        val bw = (pw - 20) / 3
        pages.forEachIndexed { i, p ->
            val bx = x + 10 + (i % 3) * bw
            val by = top + (i / 3) * 20
            val (have, all) = world.pageProgress(p)
            val style = when { i == bookPage -> "gold"; world.pageClaimable(p) -> "green"; p.id in world.state.pagesClaimed -> "grey"; else -> "wood" }
            if (ui.button("page-${p.id}", bx, by, bw - 4, 18, "${p.name.uppercase().take(7)} $have/$all", style = style)) bookPage = i
        }
        val p = pages.getOrNull(bookPage) ?: return
        val entries = world.pageEntries(p)
        val gy = top + 44
        val per = (pw - 20) / 22
        entries.forEachIndexed { i, e ->
            val sx = x + 10 + (i % per) * 22
            val sy = gy + (i / per) * 22
            val found = world.entryFound(e)
            if (ui.slot("entry-$e", sx, sy, if (found) entryIcon(e) else null, -1, false, 20) && found) {
                hud.toast(if (e.startsWith("villager:")) world.data.villagers.firstOrNull { "villager:${it.id}" == e }?.name ?: e else world.data.name(e))
            }
            if (!found) g.text("?", sx + 10, sy + 6, Gfx.GREY, align = 0, outline = 0, shadow = false)
        }
        val by = top + h - 40
        g.text("PAGE REWARD: ${p.bonusText.uppercase()}", x + pw / 2, by, Gfx.INK, align = 0, outline = 0, shadow = false)
        g.text("+${Ui.compact(p.coins)} COINS", x + pw / 2, by + 10, 0xFF3E8948.toInt(), align = 0, outline = 0, shadow = false)
        when {
            p.id in world.state.pagesClaimed -> g.text("CLAIMED", x + pw / 2, by + 24, Gfx.GREY, align = 0, outline = 0, shadow = false)
            world.pageClaimable(p) -> if (ui.button("claim-page", x + pw / 2 - 30, by + 20, 60, 16, "CLAIM", style = "gold")) world.claimPage(p.id)
            else -> {}
        }
    }

    /** Today's three chores with the streak, then this week's truck contract. */
    private fun daily(x: Int, top: Int, pw: Int) {
        val today = game.today()
        val green = 0xFF3E8948.toInt()
        var y = top
        val streak = world.choreStreakNow(today)
        g.text("TODAY'S CHORES", x + 10, y, Gfx.INK, outline = 0, shadow = false)
        g.text(if (streak > 0) "STREAK: $streak DAY${if (streak > 1) "S" else ""}" else "NO STREAK YET", x + pw - 10, y, 0xFF68386C.toInt(), align = 1, outline = 0, shadow = false)
        y += 12
        val chores = world.state.chores
        if (world.level < CHORES_LEVEL || chores.isEmpty()) {
            g.text("CHORES START AT LEVEL $CHORES_LEVEL", x + pw / 2, y + 8, Gfx.GREY, align = 0, outline = 0, shadow = false)
            y += 26
        } else {
            val (coins, _) = world.choreReward()
            val bw = pw - 124
            chores.forEachIndexed { i, c ->
                val done = world.choreDone(c)
                g.slice9(if (c.claimed) "ui/slot_sel" else "ui/slot", x + 8, y, x + pw - 8, y + 27, 4)
                g.sprite(goalIcon(c.icon), (x + 20).toFloat(), (y + 13).toFloat())
                g.text(c.text.uppercase(), x + 32, y + 4, Gfx.INK, outline = 0, shadow = false)
                val have = world.choreProgress(c)
                ui.bar(x + 32, y + 16, bw, have.toFloat() / c.n, if (done) "gold" else "green", 6)
                g.text("$have/${c.n}", x + 36 + bw, y + 15, Gfx.INK, outline = 0, shadow = false)
                when {
                    c.claimed -> g.sprite("icons/check", (x + pw - 22).toFloat(), (y + 13).toFloat())
                    done -> if (ui.button("chore-$i", x + pw - 58, y + 6, 46, 16, "CLAIM", style = "gold")) world.claimChore(i)
                    else -> {
                        g.text("+$coins", x + pw - 30, y + 15, green, align = 1, outline = 0, shadow = false)
                        pinToggle("chore:${world.state.choreDay}:$i", x + pw - 18, y + 10)
                    }
                }
                y += 30
            }
            val note = if (chores.all { it.claimed }) "ALL DONE! NEW CHORES TOMORROW"
            else "ALL 3: +${world.streakBonus(streak + 1)} COINS (STREAK DAY ${minOf(streak + 1, 7)})"
            g.text(note, x + pw / 2, y, green, align = 0, outline = 0, shadow = false)
            y += 14
        }
        g.rect(x + 8, y, x + pw - 8, y + 1, 0xFFC28569.toInt())
        y += 6
        g.text("TRUCK CONTRACT", x + 10, y, Gfx.INK, outline = 0, shadow = false)
        val c = world.state.contract
        if (c == null) {
            g.text("HIRE A FARMHAND TO TAKE CONTRACTS", x + pw / 2, y + 18, Gfx.GREY, align = 0, outline = 0, shadow = false)
            return
        }
        val done = world.contractDone(c)
        val left = contractDaysLeft(today)
        g.text(if (c.claimed) "NEXT ONE MONDAY" else "$left DAY${if (left > 1) "S" else ""} LEFT", x + pw - (if (c.claimed || done) 10 else 28), y, 0xFF68386C.toInt(), align = 1, outline = 0, shadow = false)
        if (!c.claimed && !done) pinToggle("contract:${c.week}", x + pw - 16, y + 3)
        g.text("SHIP BY TRUCK THIS WEEK:", x + 10, y + 11, Gfx.GREY, outline = 0, shadow = false)
        y += 22
        val bw = pw - 110
        for ((crop, n) in c.crops) {
            val have = world.contractShipped(c, crop)
            g.sprite(icon(crop), (x + 20).toFloat(), (y + 6).toFloat())
            g.text(world.data.name(crop).uppercase(), x + 30, y + 2, Gfx.INK, outline = 0, shadow = false)
            ui.bar(x + 92, y + 3, bw - 60, have.toFloat() / n, if (have >= n) "gold" else "green", 6)
            g.text("${Ui.compact(have.toInt())}/${Ui.compact(n)}", x + 96 + bw - 60, y + 2, Gfx.INK, outline = 0, shadow = false)
            y += 14
        }
        val (coins, xp, items) = world.contractReward(c)
        val extra = items.entries.joinToString { (k, v) -> "$v ${world.data.name(k).uppercase()}" }
        g.text("PAYS ${Ui.compact(coins)} COINS, $xp XP, $extra", x + pw / 2, y + 2, green, align = 0, outline = 0, shadow = false)
        y += 14
        when {
            c.claimed -> g.text("CLAIMED. WELL DONE!", x + pw / 2, y + 4, Gfx.GREY, align = 0, outline = 0, shadow = false)
            done -> if (ui.button("contract-claim", x + pw / 2 - 30, y, 60, 16, "CLAIM", style = "gold")) world.claimContract()
            else -> g.text("SET FIELDS TO TRUCK, THEN SEND IT", x + pw / 2, y + 4, Gfx.GREY, align = 0, outline = 0, shadow = false)
        }
    }

    private fun eventsTab(x: Int, top: Int, pw: Int) {
        var y = top
        val f = world.festival()
        g.sprite("icons/festival", (x + 18).toFloat(), (y + 8).toFloat())
        if (f != null) {
            g.text(f.name.uppercase() + " TODAY!", x + 30, y + 2, Gfx.INK, outline = 0, shadow = false)
            g.text(f.goal.uppercase(), x + 30, y + 13, Gfx.GREY, outline = 0, shadow = false)
            val have = world.festivalProgress()
            ui.bar(x + 30, y + 24, pw - 100, have.toFloat() / f.target, "gold", 6)
            g.text("$have/${f.target}", x + pw - 66, y + 23, Gfx.INK, outline = 0, shadow = false)
            y += 34
            if (world.festivalItems().isNotEmpty() && have < f.target) {
                if (ui.button("fest-hand", x + 30, y, 70, 16, "HAND IN", style = "green")) world.festivalHandIn()
            }
            when {
                world.festivalClaimable() -> if (ui.button("fest-claim", x + pw - 80, y, 70, 16, "CLAIM", style = "gold")) world.claimFestival()
                have >= f.target -> g.text("WON!", x + pw - 45, y + 4, 0xFF3E8948.toInt(), align = 0, outline = 0, shadow = false)
                else -> {}
            }
            y += 24
        } else {
            val next = FESTIVALS.first { it.season == if (world.time.dayOfSeason < FESTIVAL_DAYS.first) world.time.season else Season.entries[(world.time.season.ordinal + 1) % 4] }
            g.text("NEXT FESTIVAL: ${next.name.uppercase()}", x + 30, y + 2, Gfx.INK, outline = 0, shadow = false)
            g.text("${next.season.label.uppercase()} ${FESTIVAL_DAYS.first}-${FESTIVAL_DAYS.last}: ${next.goal.uppercase()}".take(36), x + 30, y + 13, Gfx.GREY, outline = 0, shadow = false)
            y += 30
        }
        g.rect(x + 8, y, x + pw - 8, y + 1, 0xFFC28569.toInt())
        y += 6
        g.sprite("icons/merchant", (x + 18).toFloat(), (y + 8).toFloat())
        g.text("TRAVELING MERCHANT", x + 30, y + 2, Gfx.INK, outline = 0, shadow = false)
        val note = when {
            world.level < MERCHANT_LEVEL -> "VISITS FROM LEVEL $MERCHANT_LEVEL"
            world.merchantHere() -> "HERE TODAY! HIS CART IS BY THE MARKET"
            else -> "NEXT VISIT IN ${6 - world.time.dayIndex % 7} DAYS"
        }
        g.text(note, x + 30, y + 13, Gfx.GREY, outline = 0, shadow = false)
        y += 30
        g.rect(x + 8, y, x + pw - 8, y + 1, 0xFFC28569.toInt())
        y += 6
        g.sprite("icons/gift", (x + 18).toFloat(), (y + 8).toFloat())
        g.text("DAILY GIFT STREAK: ${world.state.giftStreak} DAYS", x + 30, y + 2, Gfx.INK, outline = 0, shadow = false)
        g.text("COME BACK EVERY DAY. DAY 7 IS BIGGER!", x + 30, y + 13, Gfx.GREY, outline = 0, shadow = false)
    }

    /** The daily login gift popup. */
    fun gift(): Boolean {
        val today = game.today()
        val day = world.giftStreakDay(today)
        val (coins, xp, items) = world.giftFor(day)
        val pw = 220
        val ph = 118
        val w = game.engine.w
        val x = (w - pw) / 2
        val y = game.engine.h / 2 - ph / 2
        ui.modal(x, y, pw, ph)
        ui.panel(x, y, pw, ph)
        g.sprite("icons_big/gift", (w / 2).toFloat(), (y + 20).toFloat())
        g.text("DAILY GIFT - DAY $day", w / 2, y + 36, Gfx.GOLD, align = 0)
        for (d in 1..7) {
            val bx = x + 12 + (d - 1) * 28
            g.slice9(if (d == day) "ui/slot_sel" else "ui/slot", bx, y + 50, bx + 24, y + 70, 4)
            g.text("$d", bx + 12, y + 56, if (d < day) Gfx.GREY else Gfx.WHITE, align = 0)
            if (d < day) g.sprite("icons/check", bx + 18f, y + 54f)
        }
        val reward = "+$coins COINS  +$xp XP" + items.entries.joinToString("") { (k, v) -> "  +$v ${world.data.name(k).uppercase()}" }
        g.text(reward.take(36), w / 2, y + 76, Gfx.INK, align = 0, outline = 0, shadow = false)
        if (ui.button("gift-claim", w / 2 - 30, y + ph - 24, 60, 16, "CLAIM", style = "gold")) { world.claimGift(today); return false }
        return true
    }

    // ------------------------------------------------------------------ decor shop
    /** Decorations to buy and place; beauty and its order bonus at the top. */
    fun decor(): Boolean {
        val rowsN = (DECOR.size + 1) / 2
        val (x, y, pw, ph) = frame("DECORATE", 44 + rowsN * 40).toList()
        if (closeButton(x, y, pw)) return false
        val b = world.beauty
        g.text("BEAUTY $b: +${(world.decorBonus() * 100).toInt()}% ORDER COINS", x + 10, y + 22, Gfx.INK, outline = 0, shadow = false)
        val cw = (pw - 20) / 2
        DECOR.values.forEachIndexed { i, d ->
            val cx = x + 10 + (i % 2) * cw
            val cy = y + 34 + (i / 2) * 40
            g.slice9("ui/slot", cx, cy, cx + 32, cy + 32, 4)
            g.sprite("decor/${d.id}", (cx + 16).toFloat(), (cy + 28).toFloat())
            g.text(fit(d.name.uppercase(), cw - 40), cx + 36, cy + 2, Gfx.INK, outline = 0, shadow = false)
            g.sprite("icons/coin", (cx + 40).toFloat(), (cy + 17).toFloat())
            g.text("${d.price}  +${d.beauty}", cx + 47, cy + 13, Gfx.INK, outline = 0, shadow = false)
            val why = world.decorBlocker(d.id)
            val locked = world.level < d.unlockLevel
            if (locked) g.text("LEVEL ${d.unlockLevel}", cx + 36, cy + 24, Gfx.GREY, outline = 0, shadow = false)
            else if (ui.button("decor-$i", cx + cw - 48, cy + 21, 40, 13, "BUY", style = if (why == null) "green" else "grey")) {
                if (why != null) hud.toast(why) else { onDecor?.invoke(d.id); return false }
            }
        }
        return true
    }

    // ------------------------------------------------------------------ pet
    private var petPick: PetKind? = null
    private val petName = StringBuilder()

    /** Two strays turned up: keep one, name it (a suggested name is filled in), then ADOPT. */
    fun pet(): Boolean {
        if (!world.petOffered) return false
        val w = game.engine.w
        val pw = w - 16
        val ph = 300
        val x = 8
        val y = (game.engine.h - ph) / 2
        ui.modal(x, y, pw, ph)
        ui.panel(x, y, pw, ph)
        g.text("TWO STRAYS TURNED UP!", w / 2, y + 8, Gfx.GOLD, align = 0)
        g.text("KEEP ONE. IT FOLLOWS YOU", w / 2, y + 22, Gfx.INK, align = 0, outline = 0, shadow = false)
        g.text("AND BRINGS A GIFT EACH DAY.", w / 2, y + 32, Gfx.INK, align = 0, outline = 0, shadow = false)
        PetKind.entries.forEachIndexed { i, kind ->
            val cx = w / 2 + if (i == 0) -50 else 50
            val cy = y + 76
            g.slice9(if (petPick == kind) "ui/slot_sel" else "ui/slot", cx - 36, cy - 30, cx + 36, cy + 30, 4)
            g.spriteScaled("pets/${kind.id}_sit", cx.toFloat(), (cy + 16).toFloat(), 3f, 3f)
            g.text(kind.name, cx, cy + 34, Gfx.INK, align = 0, outline = 0, shadow = false)
            if (ui.clickable("pet-$i", cx - 36, cy - 30, 72, 60)) {
                val other = PetKind.entries.first { it != kind }.defaultName
                if (petName.isEmpty() || petName.toString() == other) { petName.setLength(0); petName.append(kind.defaultName) }
                petPick = kind
            }
        }
        // Name field and key grid (as in the story's name entry).
        val ny = y + 124
        ui.pill(w / 2 - 70, ny, 140, 16)
        g.text(if (petName.isEmpty()) "PICK ONE, THEN NAME IT" else "$petName", w / 2, ny + 4, if (petName.isEmpty()) Gfx.GREY else Gfx.WHITE, align = 0)
        val kw = 22
        StoryPanel.KEYS.forEachIndexed { r, row ->
            val rowW = row.length * (kw + 2) + if (r == 2) 2 * (kw + 2) else 0
            var kx = (w - rowW) / 2
            val ky = ny + 24 + r * 22
            for (ch in row) {
                if (ui.button("pkey-$ch", kx, ky, kw, 20, ch.toString(), style = "wood") && petName.length < PET_NAME_MAX) petName.append(ch)
                kx += kw + 2
            }
            if (r == 2 && ui.button("pkey-del", kx, ky, kw * 2 + 2, 20, "DEL", style = "red") && petName.isNotEmpty()) petName.setLength(petName.length - 1)
        }
        val ok = petPick != null && petName.isNotBlank()
        if (ui.button("pet-later", x + 12, y + ph - 28, 70, 18, "LATER", style = "grey")) return false
        if (ui.button("pet-adopt", x + pw - 112, y + ph - 28, 100, 18, "ADOPT", style = if (ok) "gold" else "grey") && ok) {
            world.adoptPet(petPick!!, petName.toString())
            petPick = null; petName.setLength(0)
            return false
        }
        return true
    }

    /** The traveling merchant's six offers for today. */
    fun merchant(): Boolean {
        val stock = world.merchantStock()
        if (stock.isEmpty()) { hud.toast("The merchant has moved on"); return false }
        val (x, y, pw, ph) = frame("TRAVELING MERCHANT", 34 + 3 * 40).toList()
        if (closeButton(x, y, pw)) return false
        val cw = (pw - 20) / 2
        stock.forEachIndexed { i, o ->
            val cx = x + 10 + (i % 2) * cw
            val cy = y + 24 + (i / 2) * 40
            ui.slot("offer-$i", cx, cy, icon(o.item, big = true), o.qty, false, 32)
            g.text(fit(world.data.name(o.item).uppercase(), cw - 40), cx + 36, cy + 2, Gfx.INK, outline = 0, shadow = false)
            g.sprite("icons/coin", (cx + 40).toFloat(), (cy + 17).toFloat())
            g.text(Ui.compact(world.merchantPrice(o)), cx + 47, cy + 13, Gfx.INK, outline = 0, shadow = false)
            if (world.merchantBought(i)) g.text("SOLD", cx + cw - 30, cy + 24, Gfx.GREY, align = 0, outline = 0, shadow = false)
            else if (ui.button("buy-$i", cx + cw - 48, cy + 20, 40, 14, "BUY", style = if (world.state.coins >= world.merchantPrice(o)) "green" else "grey")) world.buyFromMerchant(i)
        }
        return true
    }

    // ------------------------------------------------------------------ market cart
    private var cartPicking = false

    /** Market Cart rules: what to sell, at which price, and how many to keep. */
    fun cart(): Boolean {
        val rules = world.state.cartRules
        val pickable = world.state.inventory.keys.map { baseId(it) }.distinct().filter { world.data.item(it).price > 0 && rules.none { r -> r.item == it } }.sorted()
        val per = (game.engine.w - 36) / 26
        val want = if (cartPicking) 80 + ((pickable.size + per - 1) / per) * 26 else 84 + rules.size * 34
        val (x, y, pw, ph) = frame("MARKET CART", minOf(maxPanelH, want)).toList()
        if (closeButton(x, y, pw)) { cartPicking = false; return false }
        g.text("SELLS UP TO $CART_DAILY_CAP A DAY AT DAWN, TOP RULE FIRST", x + pw / 2, y + 22, Gfx.GREY, align = 0, outline = 0, shadow = false)
        var ry = y + 34
        if (cartPicking) {
            g.text("PICK AN ITEM TO SELL", x + 10, ry, Gfx.INK, outline = 0, shadow = false)
            pickable.forEachIndexed { i, id ->
                if (ui.slot("pick-$id", x + 10 + (i % per) * 26, ry + 12 + (i / per) * 26, icon(id), world.count(id, anyQuality = true), false, 24)) {
                    world.addCartRule(id); cartPicking = false
                }
            }
            if (ui.button("pick-cancel", x + pw / 2 - 30, y + ph - 24, 60, 16, "BACK", style = "red")) cartPicking = false
            return true
        }
        var remove = -1
        rules.forEachIndexed { i, r ->
            g.slice9("ui/slot", x + 8, ry, x + pw - 8, ry + 30, 4)
            g.sprite(icon(r.item), (x + 20).toFloat(), (ry + 15).toFloat())
            g.text(fit(world.data.name(r.item).uppercase(), pw - 186), x + 32, ry + 4, Gfx.INK, outline = 0, shadow = false)
            g.text("${world.percentOfBase(r.item)}% TODAY", x + 32, ry + 16, Gfx.GREY, outline = 0, shadow = false)
            if (ui.button("cond-$i", x + pw - 150, ry + 7, 44, 16, CartRule.CONDITIONS[r.cond], style = "wood")) r.cond = (r.cond + 1) % CartRule.CONDITIONS.size
            if (ui.button("keep-$i-m", x + pw - 102, ry + 7, 14, 16, "-", style = "wood")) r.keep = maxOf(0, r.keep - 5)
            g.text("KEEP ${r.keep}", x + pw - 66, ry + 11, Gfx.INK, align = 0, outline = 0, shadow = false)
            if (ui.button("keep-$i-p", x + pw - 44, ry + 7, 14, 16, "+", style = "wood")) r.keep += 5
            if (ui.button("del-$i", x + pw - 26, ry + 7, 14, 16, null, "ui/x", "red")) remove = i
            ry += 34
        }
        if (remove >= 0) rules.removeAt(remove)
        if (rules.size < CART_MAX_RULES && ui.button("cart-add", x + pw / 2 - 40, ry + 2, 80, 16, "+ ADD RULE", style = "green")) cartPicking = true
        val log = world.state.cartLog.firstOrNull()
        g.text(if (log != null) "LAST SALE: ${log.units} ITEMS FOR ${log.coins} COINS" else "NO SALES YET", x + pw / 2, y + ph - 16, Gfx.INK, align = 0, outline = 0, shadow = false)
        return true
    }

    // ------------------------------------------------------------------ field blocks
    /** A field block's settings: the crop its farmhand replants and where the harvest goes. */
    fun field(block: Int): Boolean {
        val f = world.state.fields.getOrNull(block) ?: return false
        val crops = world.data.crops.values.filter { it.unlockLevel <= world.level }.sortedBy { it.unlockLevel }
        val per = 6
        val rows = (crops.size + per - 1) / per
        val (x, y, pw, ph) = frame("FIELD ${FieldLayout.name(block)}", 128 + rows * 28, pw = 220).toList()
        if (closeButton(x, y, pw)) return false
        val plots = FieldLayout.plotsOf(block)
        val ripe = plots.count { world.isReady(world.plot(it)) }
        val growing = plots.count { world.plot(it).crop != null } - ripe
        g.text("$growing GROWING, $ripe RIPE, CRATE ${f.crate.values.sum()}/$CRATE_CAP", x + 10, y + 22, Gfx.INK, outline = 0, shadow = false)
        g.text("CROP FOR THIS FIELD", x + 10, y + 36, Gfx.GREY, outline = 0, shadow = false)
        crops.forEachIndexed { i, c ->
            val sx = x + 12 + (i % per) * 33
            val sy = y + 48 + (i / per) * 28
            if (ui.slot("fcrop-${c.id}", sx, sy, icon(c.seedItem), -1, f.crop == c.id, 24)) f.crop = if (f.crop == c.id) null else c.id
        }
        val ty = y + 52 + rows * 28
        g.text("HARVEST GOES TO", x + 10, ty, Gfx.GREY, outline = 0, shadow = false)
        if (ui.button("fdest-truck", x + 10, ty + 12, 96, 16, "TRUCK (SELL)", style = if (f.toTruck) "gold" else "wood")) f.toTruck = true
        if (ui.button("fdest-silo", x + pw - 106, ty + 12, 96, 16, "SILO (KEEP)", style = if (!f.toTruck) "gold" else "wood")) f.toTruck = false
        g.text("FARMHAND", x + 10, ty + 40, Gfx.GREY, outline = 0, shadow = false)
        val hand = world.workerOn(block)
        val free = world.state.workers.values.filter { it.field < 0 }
        val label = hand?.name?.uppercase() ?: if (world.state.workers.isEmpty()) "HIRE AT BUNKHOUSE" else "NOBODY"
        if (ui.button("fhand", x + pw - 126, ty + 36, 116, 16, label, style = if (hand != null) "gold" else "wood", enabled = world.state.workers.isNotEmpty())) {
            // Cycle: the current farmhand, then each free one, then nobody.
            val next = if (hand == null) free.firstOrNull() else free.firstOrNull { it.id > hand.id } ?: free.firstOrNull()
            hand?.let { world.assign(it.id, -1) }
            if (next != null && next !== hand) world.assign(next.id, block)
        }
        return true
    }

    // ------------------------------------------------------------------ the valley
    /** Restore the valley: the next tier's bundles and coins, donated a bit at a time, then REBUILD. */
    fun village(): Boolean {
        val (x, y, pw, ph) = frame("RESTORE THE VALLEY", maxPanelH).toList()
        if (closeButton(x, y, pw)) return false
        val tier = world.villageTier
        val next = world.nextTier
        g.text("NOW: ${VILLAGE_TIERS[tier].uppercase()}", x + 10, y + 22, Gfx.INK, outline = 0, shadow = false)
        if (next == null) {
            g.text("THE TOWN IS FINISHED. THANK YOU!", x + pw / 2, y + ph / 2, 0xFF3E8948.toInt(), align = 0, outline = 0, shadow = false)
            return true
        }
        g.text("NEXT: ${next.name.uppercase()}" + if (world.level < next.level) " (LEVEL ${next.level})" else "", x + 10, y + 33, 0xFF68386C.toInt(), outline = 0, shadow = false)
        val (have, need) = world.tierProgress()
        ui.bar(x + 10, y + 45, pw - 20, have.toFloat() / need, "gold", 8)
        var ry = y + 60
        for (b in next.bundles) {
            val done = world.bundleDone(b)
            val rowH = 40
            g.slice9(if (done) "ui/slot_sel" else "ui/slot", x + 8, ry, x + pw - 8, ry + rowH - 4, 4)
            g.text(b.name.uppercase() + if (done) "  DONE" else "", x + 14, ry + 4, if (done) 0xFF3E8948.toInt() else Gfx.INK, outline = 0, shadow = false)
            var ix = x + 14
            for ((k, n) in b.items) {
                val got = minOf(world.donated(b.id, k), n)
                g.sprite(icon(k), (ix + 6).toFloat(), (ry + 24).toFloat())
                val label = "${Ui.compact(got)}/${Ui.compact(n)}"
                g.text(label, ix + 14, ry + 20, if (got >= n) 0xFF3E8948.toInt() else Gfx.INK, outline = 0, shadow = false)
                ix += 18 + g.textWidth(label)
            }
            if (!done) pinToggle("bundle:${b.id}", x + pw - 20, ry + 8)
            val can = !done && b.items.any { (k, n) -> world.donated(b.id, k) < n && world.count(k, anyQuality = true) > 0 }
            if (!done && ui.button("give-${b.id}", x + pw - 58, ry + 16, 46, 16, "GIVE", style = if (can) "green" else "grey")) {
                if (world.donate(b.id) > 0) game.sfx.play("coin") else game.sfx.play("error")
            }
            ry += rowH
        }
        // Coins.
        val coins = minOf(world.coinsDonated(), next.coins)
        g.slice9(if (coins >= next.coins) "ui/slot_sel" else "ui/slot", x + 8, ry, x + pw - 8, ry + 28, 4)
        g.sprite("icons/coin", (x + 20).toFloat(), (ry + 14).toFloat())
        g.text("${Ui.compact(coins)}/${Ui.compact(next.coins)} COINS", x + 30, ry + 10, if (coins >= next.coins) 0xFF3E8948.toInt() else Gfx.INK, outline = 0, shadow = false)
        if (coins < next.coins && ui.button("give-coins", x + pw - 58, ry + 6, 46, 16, "GIVE", style = if (world.state.coins > 0) "gold" else "grey")) {
            if (world.donateCoins() > 0) game.sfx.play("coin")
        }
        ry += 34
        g.text("BRINGS:", x + 10, ry, Gfx.GREY, outline = 0, shadow = false)
        val lines = next.rewards.flatMap { hud.wrap(("- " + it).uppercase(), pw - 24) }
        lines.forEachIndexed { i, l -> g.text(l, x + 12, ry + 11 + i * 10, Gfx.INK, outline = 0, shadow = false) }
        val why = world.rebuildBlocker()
        if (ui.button("rebuild", x + pw / 2 - 50, y + ph - 26, 100, 18, "REBUILD!", style = if (why == null) "gold" else "grey")) {
            if (why != null) hud.toast(why) else if (world.rebuild()) return false
        }
        return true
    }

    /** The Seed shop: packs of ten seeds, 20% under the planting price. */
    fun seeds(): Boolean {
        val crops = world.data.crops.values.filter { world.cropUnlocked(it.id) }.sortedBy { it.unlockLevel }
        val rowH = 26
        val (x, y, pw, ph) = frame("SEED SHOP", minOf(maxPanelH, 40 + crops.size * rowH)).toList()
        if (closeButton(x, y, pw)) return false
        g.text("PACKS OF $SEED_PACK, 20% OFF", x + 10, y + 22, Gfx.GREY, outline = 0, shadow = false)
        val top = y + 34
        val off = ui.scroll("seed-list", x + 6, top, pw - 12, ph - 40, crops.size * rowH)
        val c = g.canvas
        c.save(); c.clipRect(x + 6, top, x + pw - 6, top + ph - 40)
        crops.forEachIndexed { i, crop ->
            val ry = top + i * rowH - off
            g.sprite(icon(crop.seedItem), (x + 18).toFloat(), (ry + 11).toFloat())
            g.text("${world.data.name(crop.id).uppercase()}  x${world.count(crop.seedItem)}", x + 30, ry + 2, Gfx.INK, outline = 0, shadow = false)
            g.sprite("icons/coin", (x + 34).toFloat(), (ry + 17).toFloat())
            g.text("${world.seedPackPrice(crop.id)}", x + 41, ry + 13, Gfx.INK, outline = 0, shadow = false)
            if (ui.button("seedpack-${crop.id}", x + pw - 64, ry + 3, 52, 16, "BUY $SEED_PACK", style = if (world.state.coins >= world.seedPackPrice(crop.id)) "green" else "grey")) world.buySeedPack(crop.id)
        }
        c.restore()
        return true
    }

    // ------------------------------------------------------------------ skills
    /** The skill tree: a tab per branch, four skills each. Respec only at the Workbench ([atBench]). */
    fun skills(atBench: Boolean): Boolean {
        val (x, y, pw, ph) = frame("SKILLS", maxPanelH).toList()
        if (closeButton(x, y, pw)) return false
        val pts = world.skillPoints
        g.text("$pts POINT${if (pts == 1) "" else "S"} TO SPEND", x + 10, y + 22, if (pts > 0) 0xFF3E8948.toInt() else Gfx.INK, outline = 0, shadow = false)
        if (atBench) {
            val cost = world.respecCost()
            if (ui.button("respec", x + pw - 92, y + 19, 82, 14, "RESET ${Ui.compact(cost)}", style = "red",
                    enabled = world.state.skills.isNotEmpty() && world.state.coins >= cost)) world.respec()
        } else g.text("RESET AT THE WORKBENCH", x + pw - 10, y + 22, Gfx.GREY, align = 1, outline = 0, shadow = false)
        skillTab = ui.tabs("skill-tabs", x + 8, y + 36, pw - 16, BRANCHES.map { it.short }, skillTab)
        val br = BRANCHES[skillTab]
        g.sprite("icons_big/skill_branch_${br.id}", (x + 24).toFloat(), (y + 72).toFloat())
        g.text(br.name.uppercase(), x + 44, y + 64, Gfx.INK, outline = 0, shadow = false)
        g.text("${br.skills.sumOf { world.rank(it.id) }} POINTS IN THIS BRANCH", x + 44, y + 75, Gfx.GREY, outline = 0, shadow = false)
        val rowH = 50
        br.skills.forEachIndexed { i, s ->
            val ry = y + 92 + i * rowH
            val rank = world.rank(s.id)
            val why = world.skillBlocker(s.id)
            val locked = rank == 0 && why != null && why.startsWith("Learn")
            g.slice9("ui/slot", x + 8, ry, x + pw - 8, ry + rowH - 4, 4)
            g.sprite("icons_big/skill_${s.id}", (x + 26).toFloat(), (ry + 20).toFloat(), if (locked) 90 else 255)
            for (k in 0 until s.ranks) {
                val px = x + 18 + k * 7
                g.rect(px, ry + 37, px + 5, ry + 42, Gfx.INK)
                g.rect(px + 1, ry + 38, px + 4, ry + 41, if (k < rank) Gfx.GOLD else 0xFF8B9BB4.toInt())
            }
            g.text(s.name.uppercase(), x + 46, ry + 4, if (locked) Gfx.GREY else Gfx.INK, outline = 0, shadow = false)
            val lines = hud.wrap((if (locked) why!! else s.desc + " a rank").uppercase(), pw - 120)
            lines.take(2).forEachIndexed { li, l -> g.text(l, x + 46, ry + 16 + li * 10, Gfx.GREY, outline = 0, shadow = false) }
            val label = if (rank >= s.ranks) "MAX" else "LEARN"
            if (ui.button("learn-${s.id}", x + pw - 64, ry + 14, 52, 16, label, style = if (why == null) "green" else "grey")) {
                if (why == null) world.learn(s.id) else hud.toast(why)
            }
        }
        return true
    }

    // ------------------------------------------------------------------ farmhands
    /** The bunkhouse: hire farmhands, train them (faster, hungrier) and give each a field. */
    fun workers(): Boolean {
        val rowH = 44
        val (x, y, pw, ph) = frame("FARMHANDS", 54 + FARMHANDS.size * rowH).toList()
        if (closeButton(x, y, pw)) return false
        val t = world.state.truck
        val truck = if (t.away) world.truckSecondsLeft().toInt().let { "TRUCK IN TOWN, BACK IN %d:%02d".format(it / 60, it % 60) }
            else "TRUCK ${world.truckLoad()}/${world.truckCap}" + if (t.lastTrip > 0) "  LAST TRIP ${t.lastTrip} COINS" else ""
        g.text(truck, x + 10, y + 22, Gfx.INK, outline = 0, shadow = false)
        FARMHANDS.forEachIndexed { i, d ->
            val ry = y + 34 + i * rowH
            g.slice9("ui/slot", x + 8, ry, x + pw - 8, ry + rowH - 4, 4)
            val ws = world.state.workers[d.id]
            val locked = ws == null && world.level < d.unlockLevel
            val portrait = "portraits/fh_${d.id}"
            if (game.assets.has(portrait)) g.sprite(portrait, (x + 27).toFloat(), (ry + 20).toFloat(), if (locked) 90 else 255)
            val tx = x + 48
            val bx = x + pw - 72
            if (ws == null) {
                g.text(d.name.uppercase(), tx, ry + 4, if (locked) Gfx.GREY else Gfx.INK, outline = 0, shadow = false)
                if (locked) g.text("LOOKING FOR WORK AT L${d.unlockLevel}", tx, ry + 16, Gfx.GREY, outline = 0, shadow = false)
                else {
                    g.text("WANTS ${d.hireCost} COINS", tx, ry + 16, Gfx.GREY, outline = 0, shadow = false)
                    if (ui.button("hire-${d.id}", bx, ry + 11, 62, 16, "HIRE", style = "green", enabled = world.state.coins >= d.hireCost)) world.hire(d.id)
                }
                return@forEachIndexed
            }
            g.text("${ws.name.uppercase()}  LV ${ws.level}", tx, ry + 4, Gfx.INK, outline = 0, shadow = false)
            if (ws.hungry) g.text("HUNGRY - NEEDS SILO CROPS", tx, ry + 15, Gfx.RED, outline = 0, shadow = false)
            else g.text("${workerPlotsPerMinute(ws.level)} PLOTS/MIN, EATS ${workerAppetite(ws.level)}/DAY", tx, ry + 15, Gfx.GREY, outline = 0, shadow = false)
            g.text(if (ws.field >= 0) "WORKS FIELD ${FieldLayout.name(ws.field)}" else "RESTING", tx, ry + 26, Gfx.INK, outline = 0, shadow = false)
            if (ui.button("wfield-${d.id}", bx, ry + 2, 62, 16, if (ws.field >= 0) "FIELD ${FieldLayout.name(ws.field)}" else "NO FIELD", style = "wood")) {
                val options = listOf(-1) + (0 until FieldLayout.BLOCKS).filter { b -> world.blockOwned(b) && world.workerOn(b).let { it == null || it === ws } }
                world.assign(d.id, options[(options.indexOf(ws.field) + 1) % options.size])
            }
            val max = ws.level >= WORKER_MAX_LEVEL
            val cost = workerLevelCost(ws.level)
            if (ui.button("wlevel-${d.id}", bx, ry + 20, 62, 16, if (max) "MAX" else Ui.compact(cost), if (max) null else "icons/level", "gold",
                    enabled = !max && world.state.coins >= cost)) world.levelUpWorker(d.id)
        }
        return true
    }
}
