package com.ditherfarm.ui

import com.ditherfarm.Game
import com.ditherfarm.engine.Gfx
import com.ditherfarm.game.pendingAct2Scene
import com.ditherfarm.game.playerName
import com.ditherfarm.game.villager
import com.ditherfarm.game.WorldEvent
import com.ditherfarm.game.claimableCount
import com.ditherfarm.game.festival
import com.ditherfarm.game.pet
import com.ditherfarm.game.pointer
import com.ditherfarm.game.quarryOpen
import com.ditherfarm.game.petOffered
import com.ditherfarm.game.festivalClaimable
import com.ditherfarm.game.giftReady
import com.ditherfarm.game.merchantHere
import com.ditherfarm.game.skillPoints
import com.ditherfarm.game.FieldLayout
import com.ditherfarm.game.blockPrice
import com.ditherfarm.game.buyBlock
import com.ditherfarm.game.chipGoal
import com.ditherfarm.game.claimAchievement
import com.ditherfarm.game.claimChore
import com.ditherfarm.game.claimContract
import com.ditherfarm.game.claimQuest
import com.ditherfarm.game.dailyClaimable
import com.ditherfarm.game.donate
import com.ditherfarm.game.quest
import com.ditherfarm.game.pendingScene
import com.ditherfarm.game.rollDaily
import com.ditherfarm.game.sceneById
import com.ditherfarm.game.togglePin

/**
 * Everything around the map that every screen shares: the HUD, the nav bar, the modal
 * panels (storage, market, orders, settings, machines, build, workbench), level-up
 * popups, confirm dialogs and reactions to common simulation events.
 */
class Chrome(private val game: Game, private val here: Nav) {
    val hud = Hud(game)
    val panels = Panels(game, hud)
    /** The bank-loan story's scenes (StoryPanel). */
    val story = StoryPanel(game, hud)
    private val ui get() = game.ui
    private val g get() = game.gfx
    private val world get() = game.world

    enum class Panel { NONE, BARN, MARKET, ORDERS, SETTINGS, LEVEL_UP, MACHINE, BUILD, WORKBENCH, CONFIRM, GEODES, ELEVATOR, SHED, GOALS, GIFT, MERCHANT, CART, FIELD, WORKERS, SKILLS, VILLAGE, SEEDS, STORY, PET, DECOR, PET_INFO, ORCHARD, PASTURE, RELICS, VILLAGER, CONTEST }

    var panel = Panel.NONE
        private set
    private var target: String? = null
    private val levelUps = ArrayDeque<Pair<Int, Int>>()
    private var confirmTitle = ""
    private var confirmBody = ""
    private var confirmYes = ""
    private var onConfirm: (() -> Unit)? = null
    private var confirmPin: String? = null

    val isOpen get() = panel != Panel.NONE

    /**
     * A popup that must be answered first (a confirm, a level-up, the gift, a scene, the welcome-back
     * summary): nothing under it takes input. Under an ordinary panel the HUD and the nav bar stay
     * live, so they switch panels or screens.
     */
    private val blocking get() = game.awaySummary != null ||
        panel == Panel.CONFIRM || panel == Panel.LEVEL_UP || panel == Panel.GIFT || panel == Panel.STORY

    /** Anything open: a screen's own action buttons (the Mine's ROPE, QUARRY) only draw under it. */
    private val covered get() = panel != Panel.NONE || game.awaySummary != null

    init {
        panels.onPlace = { item -> panel = Panel.NONE; game.startPlacing(item) }
        panels.onSkills = { open(Panel.SKILLS, "bench") }
        panels.onDecorShop = { open(Panel.DECOR) }
        panels.onRelics = { open(Panel.RELICS) }
        panels.onContest = { open(Panel.CONTEST) }
        panels.onHeartEvent = { id -> com.ditherfarm.game.sceneById(id)?.let { story.start(it); open(Panel.STORY) } }
        // Decorations go on the farm, so buying one elsewhere takes the player there.
        panels.onDecor = { id ->
            panel = Panel.NONE
            if (here != Nav.FARM) game.goTo(Nav.FARM)
            game.farm.startDecor(id)
        }
        // Scenes play on the farm, so a replay asked for in the Forest or Mine goes there first.
        panels.onStory = {
            if (here == Nav.FARM) replayOpening()
            else { panel = Panel.NONE; game.goTo(Nav.FARM); game.farm.chrome.replayOpening() }
        }
    }

    /** Takes the player to what the notebook points at: the right screen, centred on the target. */
    private fun showPointer(p: com.ditherfarm.game.Pointer) {
        when {
            p == com.ditherfarm.game.Pointer.MARKET -> open(Panel.MARKET)
            p == com.ditherfarm.game.Pointer.ORDERS -> open(Panel.ORDERS)
            p.onFarm -> { if (here != Nav.FARM) { panel = Panel.NONE; game.goTo(Nav.FARM) }; game.farm.focusPointer() }
            p.inForest && here == Nav.FOREST -> game.forest.focusPointer()
            p.inForest && world.level >= Nav.FOREST.unlockLevel -> { panel = Panel.NONE; game.goTo(Nav.FOREST) }
            p == com.ditherfarm.game.Pointer.MINE && here != Nav.MINE && world.quarryOpen -> { panel = Panel.NONE; game.goTo(Nav.MINE) }
            p == com.ditherfarm.game.Pointer.GOALS -> open(Panel.GOALS)
        }
    }

    /** Plays the opening again, asking for a look and a name. */
    fun replayOpening() {
        sceneById("opening")?.let { story.start(it, replay = true); open(Panel.STORY) }
    }

    /** Closes whatever is open and stops a scene mid-way: a new farm starts from a clean screen. */
    fun reset() {
        story.stop()
        panel = Panel.NONE
        target = null
        levelUps.clear()
        giftShown = false
    }

    fun open(p: Panel, target: String? = null) {
        panels.reset()
        panel = p
        this.target = target
        game.sfx.play("open")
        if (p == Panel.MARKET) hud.tip("market", "Prices change every day. A green arrow means today is a good day to sell.")
        if (p == Panel.GOALS) world.state.bump("goals_opened")
        if (p == Panel.ORDERS) hud.tip("orders", "Send what an order asks for to earn coins and XP. Tap X twice to swap an order you don't want.")
        if (p == Panel.VILLAGE) hud.tip("village", "Give goods and coins a bit at a time. When every bundle is done, rebuild the whole village.")
        if (p == Panel.SKILLS) hud.tip("skills", "Every level gives a skill point. Skills in a branch unlock in order.")
        if (p == Panel.WORKERS) hud.tip("workers", "Farmhands work one field each: they harvest into its crate, replant its crop and water. They eat crops from the Silo each morning.")
        if (p == Panel.WORKBENCH) hud.tip("workbench", "Crafts keep running while the app is closed. Tap the bench to collect.")
    }

    /** A yes/later dialog; [pin] adds a pin toggle for that goal key (fields). */
    fun confirm(title: String, body: String, yes: String, pin: String? = null, action: () -> Unit) {
        confirmTitle = title; confirmBody = body; confirmYes = yes; onConfirm = action; confirmPin = pin
        open(Panel.CONFIRM)
    }

    /** Offers field [b] for sale, with a pin so its price can sit on the goal chip. */
    fun offerBlock(b: Int) {
        val name = FieldLayout.name(b)
        if (world.level < FieldLayout.UNLOCK[b]) { hud.toast("Field $name unlocks at level ${FieldLayout.UNLOCK[b]}"); return }
        confirm("FIELD $name", "Buy this field of ${FieldLayout.PER_BLOCK} plots for ${world.blockPrice(b)} coins?", "BUY", pin = "field:$b") {
            if (world.buyBlock(b)) { game.sfx.play("buy"); game.haptics.heavy() }
        }
    }

    /** Back button: closes the open panel; false if nothing was open. */
    fun close(): Boolean {
        if (panel == Panel.NONE || panel == Panel.LEVEL_UP || panel == Panel.STORY) return panel == Panel.STORY
        panel = Panel.NONE
        return true
    }

    fun update(dt: Float) {
        hud.update(dt)
        story.update(dt)
        world.rollDaily(game.today())
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

    /** HUD top bar plus the goals button; call before drawing screen-specific overlays. [questBelow] moves the notebook chip down past a screen's own chip. */
    fun drawTop(questBelow: Int = 0) {
        // A story scene owns the whole screen: no HUD (and nothing under it to tap) until it ends.
        if (panel == Panel.STORY) return
        // Under a popup the HUD, the screen's own buttons and the nav bar are only drawn; drawBottom
        // turns input back on for the popup itself.
        ui.inert = blocking
        hud.drawTop()
        val w = game.engine.w
        val y = game.engine.safeTop + 18
        val festival = world.festival() != null
        val x = 190
        ui.pill(x, y, w - x - 2, 14)
        val goalsClick = ui.clickable("hud-goals", x, y, w - x - 2, 14)
        g.sprite("icons/trophy", x + 9f, y + 7f)
        val n = world.claimableCount() + world.dailyClaimable() + if (world.festivalClaimable()) 1 else 0
        if (n > 0) {
            g.sprite("ui/badge", x + 17f, y + 3f)
            g.text(n.coerceAtMost(9).toString(), x + 17, y - 1, Gfx.WHITE, align = 0, shadow = false)
        }
        if (festival) g.sprite("icons/festival", x + 30f, y + 7f)
        if (world.merchantHere()) g.sprite("icons/merchant", x + if (festival) 46f else 30f, y + 7f)
        if (goalsClick) open(Panel.GOALS)
        // The notebook's Goals step: the trophy chip blinks gold until tapped.
        if (world.pointer == com.ditherfarm.game.Pointer.GOALS && (game.engine.time * 3).toInt() % 2 == 0) {
            val gw = w - x - 2
            g.rect(x - 1, y - 1, x + gw + 1, y, Gfx.GOLD); g.rect(x - 1, y + 14, x + gw + 1, y + 15, Gfx.GOLD)
            g.rect(x - 1, y, x, y + 14, Gfx.GOLD); g.rect(x + gw, y, x + gw + 1, y + 14, Gfx.GOLD)
        }
        // The level badge opens the skill tree; a badge counts unspent points.
        val ly = game.engine.safeTop + 2
        val pts = world.skillPoints
        if (pts > 0) {
            g.sprite("ui/badge", 90f, ly + 2f)
            g.text(pts.coerceAtMost(9).toString(), 90, ly - 2, Gfx.WHITE, align = 0, shadow = false)
        }
        if (ui.clickable("hud-level", 2, ly, 92, 14)) open(Panel.SKILLS)
        drawGoal(questBelow)
        // The screen's own buttons, drawn next, are only drawn while anything is open.
        ui.inert = covered
    }

    /**
     * The goal chip under the status bar: the pinned goal, else Grandpa's notebook, else the
     * village. Tapping finishes a ready goal or opens where it is worked on.
     */
    private fun drawGoal(below: Int) {
        val (key, line) = world.chipGoal() ?: return
        val w = game.engine.w
        val y = game.engine.safeTop + 34 + below
        val pinned = key == world.state.pinned
        val done = line.ready
        val text = if (done) readyText(key) else line.text.uppercase()
        val count = when {
            done || line.need <= 1 -> ""
            line.percent -> " ${(100 * line.have / line.need).toInt()}%"
            else -> " ${Ui.compact(line.have.toInt())}/${Ui.compact(line.need.toInt())}"
        }
        val extra = if (pinned) 12 else 0
        val tw = minOf(w - 8, g.textWidth(text + count) + 26 + extra)
        ui.pill(2, y, tw, 14)
        g.sprite(panels.goalIcon(line.icon), 10f, y + 7f)
        g.text((text + count).let { if (g.textWidth(it) > tw - 24 - extra) it.take(((tw - 24 - extra) / 5.5).toInt()) else it }, 18, y + 3, if (done) Gfx.GOLD else Gfx.WHITE)
        if (pinned) g.sprite("icons/pin", (tw - 6).toFloat(), y + 7f)
        if (done) g.sprite("ui/badge", (tw - 1 - extra).toFloat(), y + 2f)
        if (ui.clickable("hud-goal", 2, y, tw, 14)) tapGoal(key, line)
    }

    private fun readyText(key: String) = when (key.substringBefore(':')) {
        "bundle" -> "READY! TAP TO GIVE"
        "build" -> "READY TO BUILD!"
        "field" -> "READY TO BUY!"
        "village" -> "TAP TO REBUILD THE VILLAGE"
        else -> "DONE! TAP TO CLAIM"
    }

    private fun tapGoal(key: String, line: com.ditherfarm.game.GoalLine) {
        val rest = key.substringAfter(':', "")
        when (key.substringBefore(':')) {
            "quest" -> if (line.ready) world.claimQuest() else world.quest?.let { hud.toast(it.text); showPointer(it.pointer) }
            "village" -> open(Panel.VILLAGE)
            "bundle" -> if (line.ready) world.donate(rest) else open(Panel.VILLAGE)
            "ach" -> if (line.ready) world.claimAchievement(rest.substringBefore(':')) else { panels.goalsTab = Panels.TAB_GOALS; open(Panel.GOALS) }
            "build" -> open(Panel.BUILD, rest)
            "field" -> if (line.ready) offerBlock(rest.toInt()) else hud.toast(line.text)
            "chore" -> if (line.ready) world.claimChore(rest.substringAfter(':').toInt()) else { panels.goalsTab = Panels.TAB_DAILY; open(Panel.GOALS) }
            "contract" -> if (line.ready) world.claimContract() else { panels.goalsTab = Panels.TAB_DAILY; open(Panel.GOALS) }
        }
    }

    /** Nav bar, panels and overlays. [tipLift] raises tips above screen trays. */
    fun drawBottom(tipLift: Int = 0) {
        if (panel == Panel.STORY) { ui.inert = false; drawPanel(); return }
        ui.inert = blocking
        val badges = mapOf(Nav.ORDERS to world.state.orders.indices.count { world.canDeliver(it) })
        when (val nav = hud.drawBottom(here, badges)) {
            Nav.BARN -> open(Panel.BARN)
            Nav.ORDERS -> open(Panel.ORDERS)
            Nav.MARKET -> open(Panel.MARKET)
            Nav.MENU -> open(Panel.SETTINGS)
            // Leaving the screen closes its panel, so none is left open behind.
            Nav.FARM, Nav.FOREST, Nav.MINE -> if (nav != here) { panel = Panel.NONE; game.goTo(nav) } else game.recenter()
            null -> {}
        }
        ui.inert = false
        drawPanel()
        // Toasts and tips wait while a story scene plays.
        if (panel != Panel.STORY) hud.drawOverlays(tipVisible = panel == Panel.NONE && game.awaySummary == null, tipLift = tipLift)
    }

    private fun drawPanel() {
        val keep = when (panel) {
            Panel.NONE -> {
                // Story scenes play on the farm (the officer visits the village).
                val scene = if (here == Nav.FARM) world.pendingScene() ?: world.pendingAct2Scene() else null
                if (scene != null) { story.start(scene); open(Panel.STORY) }
                else if (levelUps.isNotEmpty()) panel = Panel.LEVEL_UP
                else if (!giftShown && world.giftReady(game.today())) { giftShown = true; open(Panel.GIFT) }
                else if (here == Nav.FARM && world.petOffered && !world.pet.offered) { world.pet.offered = true; open(Panel.PET) }
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
            Panel.SKILLS -> panels.skills(atBench = target == "bench")
            Panel.VILLAGE -> panels.village()
            Panel.SEEDS -> panels.seeds()
            Panel.STORY -> story.draw()
            Panel.PET -> panels.pet()
            Panel.DECOR -> panels.decor()
            Panel.PET_INFO -> panels.petInfo()
            Panel.ORCHARD -> panels.orchard(target?.toIntOrNull() ?: -1)
            Panel.PASTURE -> panels.pasture()
            Panel.RELICS -> panels.relics()
            Panel.VILLAGER -> panels.villager(target ?: return)
            Panel.CONTEST -> panels.contest()
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
        confirmPin?.let { panels.pinToggle(it, x + pw - 14, y + 11) }
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
            is WorldEvent.Donated -> { game.haptics.tick(); hud.toast(if (e.bundle == "coins") "${e.units} coins for the village. Thank you!" else "${e.units} given to the village. Thank you!") }
            is WorldEvent.VillageRebuilt -> {
                game.sfx.play("levelup"); game.haptics.heavy()
                hud.toast("The village is rebuilt: ${com.ditherfarm.game.VILLAGE_TIERS[e.tier]}!")
            }
            is WorldEvent.FeatDone -> {
                game.sfx.play("unlock"); game.haptics.heavy()
                val k = e.key
                hud.toast(when {
                    k.startsWith("bp_") -> "Blueprint found: ${world.data.buildings[k.removePrefix("bp_")]?.name}!"
                    k == "quarry" -> "The quarry is open! Find it in the Mine."
                    k.startsWith("zone_") -> "A new part of the forest can be opened"
                    else -> world.data.recipes[k]?.outputs?.keys?.firstOrNull()?.let { "New tool recipe at the Workbench" } ?: "Something new is unlocked"
                })
            }
            is WorldEvent.QuestDone -> { game.sfx.play("coin"); game.haptics.heavy(); hud.toast("Notebook: done!") }
            is WorldEvent.ChoresNew -> { game.sfx.play("order"); hud.toast("New daily chores! Tap the trophy"); hud.tip("chores", "Three chores a day. Finish all three on more days in a row for a bigger streak bonus. One missed day a week is forgiven.") }
            is WorldEvent.ChoreDone -> { game.sfx.play("unlock"); world.state.chores.getOrNull(e.chore)?.let { hud.toast("Chore done: ${it.text}") } }
            is WorldEvent.ChoreClaimed, is WorldEvent.ContractClaimed -> { game.sfx.play("coin"); game.haptics.tick() }
            is WorldEvent.ChoresFinished -> { game.sfx.play("levelup"); game.haptics.heavy(); hud.toast("All chores done! ${e.streak}-day streak: +${e.coins} coins") }
            is WorldEvent.ContractNew -> hud.toast("A new truck contract is in. Tap the trophy")
            is WorldEvent.PetAdopted -> {
                game.sfx.play("levelup"); game.haptics.heavy(); hud.toast("Say hello to ${e.name}! Tap them for today's gift")
                hud.tip("pet", "Tap your pet for its gift or a game of fetch; hold it for its page and accessories.")
            }
            is WorldEvent.PetGift -> { game.sfx.play("coin"); game.haptics.tick() }
            is WorldEvent.OrchardOpened -> {
                game.sfx.play("levelup"); game.haptics.heavy(); hud.toast("The orchard is yours! Tap a spot to plant a tree")
                hud.tip("orchard", "Fruit trees grow for a day or two, then fruit every few days in their seasons. Tap a tree with fruit to pick it.")
            }
            is WorldEvent.AnimalBought -> {
                game.sfx.play("levelup"); game.haptics.heavy()
                hud.toast("A new ${e.kind.label.lowercase()} joins the pasture!")
                hud.tip("animals", "Animals eat from the trough each morning: keep it filled with wheat or corn. Fed animals make eggs, milk or wool. Tap one to collect and pet it.")
            }
            is WorldEvent.TroughFilled -> { game.sfx.play("collect"); game.haptics.tick() }
            is WorldEvent.AnimalsFed -> if (e.hungry > 0) hud.toast(if (e.hungry == 1) "An animal went hungry. Fill the trough!" else "${e.hungry} animals went hungry. Fill the trough!")
            is WorldEvent.RanchHandHired -> { game.sfx.play("levelup"); game.haptics.heavy(); hud.toast("The ranch hand starts tomorrow morning") }
            is WorldEvent.HeartUp -> {
                game.sfx.play("unlock"); game.haptics.tick()
                val name = world.villager(e.villager)?.name ?: e.villager
                hud.toast(if (e.hearts in com.ditherfarm.game.HEART_EVENTS) "$name: ${e.hearts} hearts! They want to talk to you" else "$name: ${e.hearts} hearts!")
            }
            is WorldEvent.ContestStarted -> {
                game.sfx.play("order")
                hud.toast("Vexmoor Cup: ${com.ditherfarm.game.CONTESTS[e.contest]?.name}! Beat ${e.target} this week. Tap the trophy")
            }
            is WorldEvent.ContestWon -> { game.sfx.play("levelup"); game.haptics.heavy(); hud.toast("You won the ${com.ditherfarm.game.CONTESTS[e.contest]?.name}! +${e.coins} coins") }
            is WorldEvent.ContestLost -> { game.sfx.play("error"); hud.toast("Vexmoor took the ${com.ditherfarm.game.CONTESTS[e.contest]?.name}. Next week!") }
            is WorldEvent.Act2Complete -> { game.sfx.play("levelup"); game.haptics.heavy() }
            is WorldEvent.KeepsakeGiven -> {
                game.sfx.play("levelup"); game.haptics.heavy()
                hud.toast("Best friends with ${world.villager(e.villager)?.name}! They gave you ${com.ditherfarm.game.DECOR[e.decor]?.name}. Place it from Decorate")
            }
            is WorldEvent.Talked -> { game.sfx.play("click"); panels.villagerSays = e.line }
            is WorldEvent.GiftGiven -> {
                game.sfx.play(if (e.reaction == com.ditherfarm.game.GiftReaction.LOVED) "levelup" else "coin"); game.haptics.tick()
                val name = world.villager(e.villager)?.name ?: e.villager
                panels.villagerSays = when (e.reaction) {
                    com.ditherfarm.game.GiftReaction.LOVED -> "Oh! I love this! Thank you, ${world.playerName}!"
                    com.ditherfarm.game.GiftReaction.LIKED -> "How thoughtful. Thank you!"
                    com.ditherfarm.game.GiftReaction.PLAIN -> "Thanks, that's kind of you."
                    com.ditherfarm.game.GiftReaction.POOR -> "Um... thanks? I think?"
                }
                hud.toast("$name ${e.reaction.label}! +${e.points}")
            }
            is WorldEvent.RelicFound -> {
                game.sfx.play("levelup"); game.haptics.heavy()
                hud.toast("Relic found: ${com.ditherfarm.game.RELICS[e.id]}! (${e.count}/${com.ditherfarm.game.RELICS.size}) See them at the elevator")
            }
            is WorldEvent.TreasureOpened -> { game.sfx.play("gem"); game.haptics.heavy(); hud.toast("Treasure! +${e.coins} coins and gems") }
            is WorldEvent.RelicsComplete -> { game.sfx.play("levelup"); game.haptics.heavy(); hud.toast("Every relic found! The collection pays out") }
            is WorldEvent.OrchardFruit -> hud.toast(if (e.trees == 1) "A tree in the orchard has fruit" else "${e.trees} trees in the orchard have fruit")
            is WorldEvent.PetLevelUp -> { game.sfx.play("levelup"); game.haptics.heavy(); hud.toast("${e.name} is now level ${e.level}! Bigger gifts") }
            is WorldEvent.BeautyMilestone -> { game.sfx.play("levelup"); game.haptics.heavy(); hud.toast(if (e.beauty >= com.ditherfarm.game.BEST_FARM_BEAUTY) "Best Farm in the Valley! +${e.coins} coins" else "Beauty ${e.beauty}! +${e.coins} coins") }
            is WorldEvent.LoanApproved -> { game.sfx.play("levelup"); game.haptics.heavy(); hud.toast("Loan approved: +${e.coins} coins!") }
            is WorldEvent.ContractDone -> { game.sfx.play("unlock"); game.haptics.heavy(); hud.toast("Truck contract complete! Claim it in Goals") }
            is WorldEvent.SkillLearned -> {
                game.sfx.play("unlock"); game.haptics.tick()
                com.ditherfarm.game.SKILLS[e.skill]?.let { hud.toast("${it.name} rank ${e.rank}") }
            }
            is WorldEvent.Respecced -> { game.sfx.play("coin"); hud.toast("Skills reset: points are back (-${e.coins} coins)") }
            is WorldEvent.TonicUsed -> {
                game.sfx.play(if (e.item == "rain_totem") "water" else "craft"); game.haptics.heavy()
                hud.toast(when (e.item) {
                    "growth_tonic" -> "Every crop grew two hours"
                    "energy_brew" -> "+${com.ditherfarm.game.TONIC_ENERGY} energy"
                    "rain_totem" -> "A shower waters every crop"
                    else -> "Lucky for ${(com.ditherfarm.game.LUCK_SECONDS / 60).toInt()} minutes: better crops"
                })
            }
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
