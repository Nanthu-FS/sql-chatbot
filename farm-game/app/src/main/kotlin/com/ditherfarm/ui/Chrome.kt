package com.ditherfarm.ui

import com.ditherfarm.Game
import com.ditherfarm.engine.Gfx
import com.ditherfarm.game.WorldEvent
import com.ditherfarm.game.claimableCount
import com.ditherfarm.game.festival
import com.ditherfarm.game.festivalClaimable
import com.ditherfarm.game.giftReady
import com.ditherfarm.game.merchantHere

/**
 * Everything around the map that every screen shares: the HUD, the nav bar, the modal
 * panels (storage, market, orders, settings, machines, build, workbench), level-up
 * popups, confirm dialogs and reactions to common simulation events.
 */
class Chrome(private val game: Game, private val here: Nav) {
    val hud = Hud(game)
    val panels = Panels(game, hud)
    private val ui get() = game.ui
    private val g get() = game.gfx
    private val world get() = game.world

    enum class Panel { NONE, BARN, MARKET, ORDERS, SETTINGS, LEVEL_UP, MACHINE, BUILD, WORKBENCH, CONFIRM, GEODES, ELEVATOR, SHED, GOALS, GIFT, MERCHANT, CART, FIELD, WORKERS }

    var panel = Panel.NONE
        private set
    private var target: String? = null
    private val levelUps = ArrayDeque<Pair<Int, Int>>()
    private var confirmTitle = ""
    private var confirmBody = ""
    private var confirmYes = ""
    private var onConfirm: (() -> Unit)? = null

    val isOpen get() = panel != Panel.NONE

    init { panels.onPlace = { item -> panel = Panel.NONE; game.startPlacing(item) } }

    fun open(p: Panel, target: String? = null) {
        panels.reset()
        panel = p
        this.target = target
        game.sfx.play("open")
        if (p == Panel.MARKET) hud.tip("market", "Prices change every day. A green arrow means today is a good day to sell.")
        if (p == Panel.WORKERS) hud.tip("workers", "Farmhands work one field each: they harvest into its crate, replant its crop and water. They eat crops from the Silo each morning.")
        if (p == Panel.WORKBENCH) hud.tip("workbench", "Crafts keep running while the app is closed. Tap the bench to collect.")
    }

    fun confirm(title: String, body: String, yes: String, action: () -> Unit) {
        confirmTitle = title; confirmBody = body; confirmYes = yes; onConfirm = action
        open(Panel.CONFIRM)
    }

    /** Back button: closes the open panel; false if nothing was open. */
    fun close(): Boolean {
        if (panel == Panel.NONE || panel == Panel.LEVEL_UP) return false
        panel = Panel.NONE
        return true
    }

    fun update(dt: Float) {
        hud.update(dt)
        // Situational tips (each shows once).
        val s = world.state
        when {
            s.energy < 20 -> hud.tip("energy", "Low energy! Tap the energy bar to eat, or come back later.")
            s.forecast.stormy -> hud.tip("storm", "Storm tomorrow! Lightning rods catch strikes and scarecrows block the wind.")
            world.used(com.ditherfarm.game.Storage.BARN) >= world.capacity(com.ditherfarm.game.Storage.BARN) * 0.9 ->
                hud.tip("barn-full", "Barn almost full! Sell goods or upgrade it from the Barn panel.")
        }
    }

    private var giftShown = false

    /** HUD top bar plus the goals button; call before drawing screen-specific overlays. */
    fun drawTop() {
        hud.drawTop()
        val w = game.engine.w
        val y = game.engine.safeTop + 18
        val festival = world.festival() != null
        val x = 190
        ui.pill(x, y, w - x - 2, 14)
        val goalsClick = ui.clickable("hud-goals", x, y, w - x - 2, 14)
        g.sprite("icons/trophy", x + 9f, y + 7f)
        val n = world.claimableCount() + if (world.festivalClaimable()) 1 else 0
        if (n > 0) {
            g.sprite("ui/badge", x + 17f, y + 3f)
            g.text(n.coerceAtMost(9).toString(), x + 17, y - 1, Gfx.WHITE, align = 0, shadow = false)
        }
        if (festival) g.sprite("icons/festival", x + 30f, y + 7f)
        if (world.merchantHere()) g.sprite("icons/merchant", x + if (festival) 46f else 30f, y + 7f)
        if (goalsClick) open(Panel.GOALS)
    }

    /** Nav bar, panels and overlays. [tipLift] raises tips above screen trays. */
    fun drawBottom(tipLift: Int = 0) {
        val badges = mapOf(Nav.ORDERS to world.state.orders.indices.count { world.canDeliver(it) })
        when (val nav = hud.drawBottom(here, badges)) {
            Nav.BARN -> open(Panel.BARN)
            Nav.ORDERS -> open(Panel.ORDERS)
            Nav.MARKET -> open(Panel.MARKET)
            Nav.MENU -> open(Panel.SETTINGS)
            Nav.FARM, Nav.FOREST, Nav.MINE -> if (nav != here) game.goTo(nav) else game.recenter()
            null -> {}
        }
        drawPanel()
        hud.drawOverlays(tipVisible = panel == Panel.NONE, tipLift = tipLift)
    }

    private fun drawPanel() {
        val keep = when (panel) {
            Panel.NONE -> {
                if (levelUps.isNotEmpty()) panel = Panel.LEVEL_UP
                else if (!giftShown && world.giftReady(game.today())) { giftShown = true; open(Panel.GIFT) }
                true
            }
            Panel.BARN -> panels.barn()
            Panel.MARKET -> panels.market()
            Panel.ORDERS -> panels.orders()
            Panel.SETTINGS -> panels.settings()
            Panel.MACHINE -> panels.machine(target ?: return)
            Panel.BUILD -> panels.build(target ?: return)
            Panel.WORKBENCH -> panels.workbench()
            Panel.CONFIRM -> drawConfirm()
            Panel.GEODES -> panels.geodes()
            Panel.ELEVATOR -> panels.elevator()
            Panel.SHED -> panels.shed(target ?: return)
            Panel.GOALS -> panels.goals()
            Panel.GIFT -> panels.gift()
            Panel.MERCHANT -> panels.merchant()
            Panel.CART -> panels.cart()
            Panel.FIELD -> panels.field(target?.toIntOrNull() ?: return)
            Panel.WORKERS -> panels.workers()
            Panel.LEVEL_UP -> levelUps.firstOrNull()?.let { (l, c) -> panels.levelUp(l, c) } ?: false
        }
        if (!keep) {
            if (panel == Panel.LEVEL_UP) levelUps.removeFirstOrNull()
            panel = Panel.NONE
            game.sfx.play("close")
        }
    }

    private fun drawConfirm(): Boolean {
        val g = game.gfx
        val w = game.engine.w
        val lines = hud.wrap(confirmBody, 170)
        val pw = 190
        val ph = 46 + lines.size * 10
        val x = (w - pw) / 2
        val y = game.engine.h / 2 - ph / 2
        val outside = ui.modal(x, y, pw, ph)
        ui.panel(x, y, pw, ph)
        g.text(confirmTitle, w / 2, y + 9, Gfx.GOLD, align = 0)
        lines.forEachIndexed { i, l -> g.text(l, w / 2, y + 21 + i * 10, Gfx.INK, align = 0, outline = 0, shadow = false) }
        if (ui.button("confirm-yes", x + 12, y + ph - 22, 76, 16, confirmYes, style = "green")) {
            onConfirm?.invoke(); return false
        }
        return !(ui.button("confirm-no", x + pw - 88, y + ph - 22, 76, 16, "LATER", style = "red") || outside)
    }

    /** Reactions shared by every screen. Returns true if handled. */
    fun onEvent(e: WorldEvent): Boolean {
        when (e) {
            is WorldEvent.LevelUp -> { levelUps.addLast(e.level to e.coins); game.sfx.play("levelup"); game.haptics.heavy() }
            is WorldEvent.OrderArrived -> {
                game.sfx.play("order")
                world.state.orders[e.slot].order?.let { o -> hud.toast("New order from ${world.data.villagers.firstOrNull { it.id == o.villager }?.name ?: o.villager}") }
            }
            is WorldEvent.OrderDelivered -> {
                hud.toast("+${e.coins} coins  +${e.xp} XP")
                hud.tip("orders", "Orders pay coins, XP and friendship. More villagers visit as you level up.")
            }
            is WorldEvent.Discovered -> hud.toast("New item: ${world.data.name(e.item)}!")
            is WorldEvent.JobDone -> game.sfx.play("machine_done")
            is WorldEvent.ToolUpgraded -> { game.sfx.play("unlock"); hud.toast("New ${world.data.tools.names[e.tier]} ${e.tool}!") }
            is WorldEvent.Built -> {
                game.sfx.play("craft"); game.haptics.heavy(); hud.toast("${world.data.buildings[e.building]?.name} built!")
                if (e.building == "sawmill") hud.tip("sawmill", "Turn Wood into Planks here. Planks build almost everything.")
            }
            is WorldEvent.AchievementReady -> { game.sfx.play("unlock"); hud.toast("Goal reached: ${world.data.goals.achievements.firstOrNull { it.id == e.id }?.name}!") }
            is WorldEvent.AchievementClaimed, is WorldEvent.PageClaimed, is WorldEvent.GiftClaimed, is WorldEvent.FestivalWon -> { game.sfx.play("coin"); game.haptics.heavy() }
            is WorldEvent.FestivalStarted -> { game.sfx.play("order"); hud.toast("The ${e.name} has begun!") }
            is WorldEvent.Bought -> { game.sfx.play("buy"); game.haptics.tick() }
            is WorldEvent.CartSold -> hud.toast("Market Cart sold ${e.units} for ${e.coins} coins")
            is WorldEvent.WorkerHired -> { game.sfx.play("unlock"); game.haptics.heavy(); hud.toast("${world.state.workers[e.worker]?.name} joined your farm!") }
            is WorldEvent.WorkerLevelUp -> { game.sfx.play("levelup"); hud.toast("${world.state.workers[e.worker]?.name} reached level ${e.level}") }
            is WorldEvent.WorkerHungry -> hud.toast("${world.state.workers[e.worker]?.name} is hungry: put crops in the Silo")
            is WorldEvent.TruckReturned -> { game.sfx.play("sell"); game.haptics.heavy(); hud.toast("The truck is back: +${e.coins} coins") }
            is WorldEvent.Collected -> { game.sfx.play("pickup"); game.haptics.tick(); hud.tip("collect", "Machines keep working while you are away. Tap the bubble to collect.") }
            is WorldEvent.ZoneUnlocked -> { game.sfx.play("unlock"); hud.toast("A new part of the forest is open!") }
            is WorldEvent.DayStarted -> if (e.day > 0) hud.toast("Day ${world.time.dayOfSeason} - ${e.weather.name.lowercase().replaceFirstChar { it.uppercase() }}")
            is WorldEvent.SeasonChanged -> hud.toast("${e.season.label} begins!" + if (e.withered > 0) " ${e.withered} crops withered" else "")
            is WorldEvent.Blocked -> {
                game.sfx.play("error")
                if (e.reason.startsWith("Needs") && e.reason.endsWith("axe")) hud.tip("axe", "Harder trees need a better axe. Upgrade tools at the Workbench on your farm.")
                if (e.plot < 0) hud.toast(e.reason) else return false
            }
            else -> return false
        }
        return true
    }
}
