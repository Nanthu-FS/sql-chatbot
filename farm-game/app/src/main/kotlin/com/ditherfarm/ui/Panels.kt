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
import com.ditherfarm.game.ORCHARD_COST
import com.ditherfarm.game.ORCHARD_LEVEL
import com.ditherfarm.game.ORCHARD_SPOTS
import com.ditherfarm.game.FRUIT_TREES
import com.ditherfarm.game.orchard
import com.ditherfarm.game.orchardBlocker
import com.ditherfarm.game.buyOrchard
import com.ditherfarm.game.treeBlocker
import com.ditherfarm.game.plantOrchardTree
import com.ditherfarm.game.treeMature
import com.ditherfarm.game.treeStatus
import com.ditherfarm.game.pickFruit
import com.ditherfarm.game.digUpTree
import com.ditherfarm.game.AnimalKind
import com.ditherfarm.game.MAX_HEARTS
import com.ditherfarm.game.TROUGH_MAX
import com.ditherfarm.game.RANCH_HAND_COST
import com.ditherfarm.game.RANCH_HAND_LEVEL
import com.ditherfarm.game.ranch
import com.ditherfarm.game.dailyFeed
import com.ditherfarm.game.troughBlocker
import com.ditherfarm.game.fillTrough
import com.ditherfarm.game.animalCount
import com.ditherfarm.game.animalBlocker
import com.ditherfarm.game.buyAnimal
import com.ditherfarm.game.ranchHandBlocker
import com.ditherfarm.game.hireRanchHand
import com.ditherfarm.game.ANIMAL_LEVEL
import com.ditherfarm.game.RELICS
import com.ditherfarm.game.RELIC_REWARD
import com.ditherfarm.game.GOLEM_RELIC
import com.ditherfarm.game.relicsFound
import com.ditherfarm.game.relicRewardBlocker
import com.ditherfarm.game.claimRelicReward
import com.ditherfarm.game.hasRod
import com.ditherfarm.game.collectProduct
import com.ditherfarm.game.petAnimal
import com.ditherfarm.game.pettedToday
import com.ditherfarm.game.villager
import com.ditherfarm.game.FRIEND_HEARTS
import com.ditherfarm.game.nextHeart
import com.ditherfarm.game.canCourt
import com.ditherfarm.game.isDating
import com.ditherfarm.game.isSpouse
import com.ditherfarm.game.maxHearts
import com.ditherfarm.game.romanceLabel
import com.ditherfarm.game.pendingDate
import com.ditherfarm.game.giveBouquet
import com.ditherfarm.game.propose
import com.ditherfarm.game.bouquetBlocker
import com.ditherfarm.game.proposeBlocker
import com.ditherfarm.game.bouquetScene
import com.ditherfarm.game.dateScene
import com.ditherfarm.game.proposalScene
import com.ditherfarm.game.romance
import com.ditherfarm.game.COURT_HEARTS
import com.ditherfarm.game.farmhouseBlocker
import com.ditherfarm.game.buildFarmhouse
import com.ditherfarm.game.FARMHOUSE_COST
import com.ditherfarm.game.FARMHOUSE_LEVEL
import com.ditherfarm.game.spousePerk
import com.ditherfarm.game.kidStage
import com.ditherfarm.game.kidAgeLabel
import com.ditherfarm.game.SPOUSE_HEARTS
import com.ditherfarm.game.DIVORCE_COST
import com.ditherfarm.game.WEDDING_DAYS
import com.ditherfarm.game.talkedToday
import com.ditherfarm.game.giftedToday
import com.ditherfarm.game.talk
import com.ditherfarm.game.giftable
import com.ditherfarm.game.giftReaction
import com.ditherfarm.game.GiftReaction
import com.ditherfarm.game.giveGift
import com.ditherfarm.game.pendingHeartEvent
import com.ditherfarm.game.heartScene
import com.ditherfarm.game.keepsakesToPlace
import com.ditherfarm.game.act2
import com.ditherfarm.game.contest
import com.ditherfarm.game.contestScore
import com.ditherfarm.game.contestDaysLeft
import com.ditherfarm.game.CHAPTER_GOALS
import com.ditherfarm.game.ACT2_CHAPTERS
import com.ditherfarm.game.ACT2_DONE
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
import com.ditherfarm.game.BEST_FARM_BEAUTY
import com.ditherfarm.game.claimBeautyMilestone
import com.ditherfarm.game.nextBeautyMilestone
import com.ditherfarm.game.adoptPet
import com.ditherfarm.game.petOffered
import com.ditherfarm.game.PET_ACCESSORIES
import com.ditherfarm.game.PET_LEVELS
import com.ditherfarm.game.accessoryBlocker
import com.ditherfarm.game.petLevel
import com.ditherfarm.game.petLoveToNext
import com.ditherfarm.game.pet
import com.ditherfarm.game.wearAccessory
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
    /** The list of Grandpa's letters (set by Chrome). */
    var onLetters: (() -> Unit)? = null
    /** Called with a decoration id when BUY is tapped in the decor shop, and to open the shop. */
    var onDecor: ((String) -> Unit)? = null
    var onDecorShop: (() -> Unit)? = null
    /** Called when the Workbench's SKILLS button is tapped. */
    var onSkills: (() -> Unit)? = null
    private var skillTab = 0
    private var selected: String? = null
    private var confirmNewGame = 0

    private var trashArmed = -1
    private var digArmed = false
    fun reset() { selected = null; confirmNewGame = 0; trashArmed = -1; digArmed = false }

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
        if (keys.isEmpty()) g.text("EMPTY", x + pw / 2, gridY + 20, Gfx.MUTED, align = 0, outline = 0, shadow = false)
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
            g.text("${world.percentOfBase(baseId(key))}%", x + 74 + g.textWidth("$unit"), y + 18, Gfx.MUTED, outline = 0, shadow = false)
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
        g.text("PRICES CHANGE DAILY", x + 10, y + 20, Gfx.MUTED, outline = 0, shadow = false)
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
        if (keys.isEmpty()) g.text("NOTHING TO SELL", x + pw / 2, listY + 20, Gfx.MUTED, align = 0, outline = 0, shadow = false)
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
                g.text(if (left > 0) "NEXT ORDER IN %d:%02d".format(left / 60, left % 60) else "WAITING FOR A VILLAGER", x + pw / 2, cy + cardH / 2 - 4, Gfx.MUTED, align = 0, outline = 0, shadow = false)
                return@forEachIndexed
            }
            val v = world.data.villagers.firstOrNull { it.id == o.villager }
            val portrait = "portraits/${o.villager}"
            if (game.assets.has(portrait)) g.spriteScaled(portrait, (x + 28).toFloat(), (cy + cardH / 2).toFloat(), 0.76f, 0.76f)
            val tx = x + 48
            g.text((v?.name ?: o.villager).uppercase(), tx, cy + 5, Gfx.INK, outline = 0, shadow = false)
            val hearts = world.hearts(o.villager)
            if (hearts > 0) {
                val hx = tx + 6 + g.textWidth((v?.name ?: "").uppercase())
                miniHeart(hx, cy + 6, true)
                g.text("$hearts", hx + 7, cy + 5, Gfx.RED, outline = 0, shadow = false)
            }
            o.lines.forEachIndexed { li, line ->
                val lx = tx + li * 50
                val have = world.count(line.item, anyQuality = true)
                g.sprite(icon(line.item), (lx + 7).toFloat(), (cy + 26).toFloat())
                g.text("$have/${line.qty}", lx + 16, cy + 22, if (have >= line.qty) 0xFF3E8948.toInt() else 0xFFA22633.toInt(), outline = 0, shadow = false)
            }
            g.sprite("icons/coin", (tx + 2).toFloat(), (cy + 42).toFloat())
            g.text("${o.coins}", tx + 10, cy + 38, Gfx.INK, outline = 0, shadow = false)
            g.sprite("icons/xp", (tx + 20 + g.textWidth("${o.coins}")).toFloat(), (cy + 42).toFloat())
            g.text("${o.xp} XP", tx + 28 + g.textWidth("${o.coins}"), cy + 38, Gfx.INK, outline = 0, shadow = false)
            val can = world.canDeliver(i)
            if (ui.button("deliver-$i", x + pw - 76, cy + 30, 50, 16, "SEND", style = if (can) "green" else "grey")) {
                if (world.deliver(i)) { game.sfx.play("sell"); game.haptics.heavy() } else game.sfx.play("error")
            }
            // Two taps swap an order: the first asks, the second does it (round 5 playtest).
            if (ui.button("trash-$i", x + pw - 24, cy + 30, 14, 16, null, "ui/x", "red")) {
                if (trashArmed == i) { world.trashOrder(i); trashArmed = -1 } else { trashArmed = i; hud.toast("Tap X again to swap this order for a new one") }
            }
        }
        return true
    }

    // -------------------------------------------------------------- settings
    fun settings(): Boolean {
        val (x, y, pw, ph) = frame("MENU", 296).toList()
        if (closeButton(x, y, pw)) return false
        // First, so it's found (a playtest only found it under the settings, round 7).
        if (ui.button("decorate", x + 10, y + 24, pw - 20, 18, "DECORATE YOUR FARM", "icons/heart", "gold")) { onDecorShop?.invoke(); return true }
        val s = world.state
        fun row(i: Int, icon: String, label: String, on: Boolean): Boolean {
            val ry = y + 50 + i * 20
            g.sprite(icon, (x + 18).toFloat(), (ry + 6).toFloat())
            g.text(label, x + 30, ry + 2, Gfx.INK, outline = 0, shadow = false)
            return ui.toggle("t-$label", x + pw - 34, ry + 1, on)
        }
        if (row(0, "icons/sound_on", "SOUND", s.sfx)) { s.sfx = !s.sfx; game.applySettings() }
        if (row(1, "icons/vibrate", "VIBRATION", s.haptics)) { s.haptics = !s.haptics; game.applySettings() }
        if (row(2, "icons/bell", "NOTIFICATIONS", s.notifications)) s.notifications = !s.notifications
        if (row(3, "icons/sound_on", "MUSIC", s.music)) { s.music = !s.music; game.applySettings() }
        if (row(4, "icons/weather_cloudy", "AMBIENCE", s.ambience)) { s.ambience = !s.ambience; game.applySettings() }
        val by = y + 158
        if (ui.button("export", x + 10, by, (pw - 26) / 2, 18, "EXPORT", "icons/export", "blue")) game.host.requestExport()
        if (ui.button("import", x + 16 + (pw - 26) / 2, by, (pw - 26) / 2, 18, "IMPORT", "icons/import", "blue")) game.host.requestImport()
        val label = when (confirmNewGame) { 0 -> "NEW FARM"; 1 -> "SURE? TAP AGAIN"; else -> "REALLY? LAST TAP" }
        if (ui.button("newgame", x + 10, by + 24, pw - 20, 18, label, style = "red")) {
            confirmNewGame++
            if (confirmNewGame >= 3) { game.newGame(); confirmNewGame = 0; hud.toast("A fresh start!"); return false }
        }
        if (ui.button("story", x + 10, by + 48, pw - 20, 18, "STORY: WATCH THE OPENING", "icons/book", "wood")) { onStory?.invoke(); return true }
        if (ui.button("letters", x + 10, by + 72, pw - 20, 18, "GRANDPA'S LETTERS", null, "wood")) { onLetters?.invoke(); return true }
        g.text("REBUILD THE VILLAGE 0.2", x + pw / 2, y + ph - 30, Gfx.MUTED, align = 0, outline = 0, shadow = false)
        g.text("FONT SILKSCREEN (OFL) - ENDESGA 32", x + pw / 2, y + ph - 19, Gfx.MUTED, align = 0, outline = 0, shadow = false)
        return true
    }

    // -------------------------------------------------------------- level up
    fun levelUp(level: Int, coins: Int): Boolean {
        val unlocks = world.data.crops.values.filter { it.unlockLevel == level }.map { it.id }
        // Round 6: the orchard and each fruit tree announce themselves.
        val news = when (level) {
            ORCHARD_LEVEL -> "NEW: THE ORCHARD!"
            com.ditherfarm.game.FISHING_LEVEL -> "NEW: FISHING ROD AT THE BENCH"
            ANIMAL_LEVEL -> "NEW: FARM ANIMALS!"
            RANCH_HAND_LEVEL -> "NEW: RANCH HAND"
            else -> FRUIT_TREES.values.firstOrNull { it.unlockLevel == level }?.let { "NEW: ${it.name.uppercase()}" }
                ?: AnimalKind.entries.firstOrNull { it.unlockLevel == level }?.let { "NEW: ${it.label.uppercase()}S" }
        }
        val ph = 102 + (if (unlocks.isEmpty()) 0 else 24) + (if (news == null) 0 else 12)
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
        news?.let { g.text(it, w / 2, y + ph - 36, 0xFF3E8948.toInt(), align = 0, outline = 0, shadow = false) }
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
        g.text(time, x + w - 62, y + 4, Gfx.MUTED, outline = 0, shadow = false)
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
            g.text("IDLE", qx, y + 7, Gfx.MUTED, outline = 0, shadow = false)
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
                    g.text("${world.data.tools.names[world.toolTier(tool)].uppercase()} ${tool.label.uppercase()} - MAX", x + 32, ry + 12, Gfx.INK, outline = 0, shadow = false)
                } else if (next.building != "workbench") {
                    // Iron and gold tools are forged at the village Blacksmith.
                    g.slice9("ui/slot", x + 8, ry, x + pw - 12, ry + 34, 4)
                    g.sprite(toolIcon(tool.name.lowercase()), (x + 20).toFloat(), (ry + 12).toFloat())
                    g.text("NEXT ${tool.label.uppercase()}: AT THE BLACKSMITH", x + 32, ry + 6, Gfx.INK, outline = 0, shadow = false)
                    g.text(if (world.villageTier >= 1) "IN THE VILLAGE" else "OPENS WITH THE STONE VILLAGE", x + 32, ry + 18, Gfx.MUTED, outline = 0, shadow = false)
                } else {
                    recipeRow(next, x + 8, ry, pw - 20, "UPGRADE")
                    g.text(world.data.tools.names[world.toolTier(tool)].uppercase(), x + 8 + pw - 20 - 104, ry + 4, Gfx.MUTED, outline = 0, shadow = false)
                }
            }
            // The fishing rod (round 6): made once, here.
            val ry = top + Tool.entries.size * 36
            val rod = world.data.recipes["tool_rod_1"]
            if (world.hasRod || rod == null) {
                g.slice9("ui/slot", x + 8, ry, x + pw - 12, ry + 34, 4)
                g.sprite("icons/tool_rod", (x + 20).toFloat(), (ry + 12).toFloat())
                g.text("FISHING ROD - TAP THE RIVER TO FISH", x + 32, ry + 12, Gfx.INK, outline = 0, shadow = false)
            } else recipeRow(rod, x + 8, ry, pw - 20, "MAKE")
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
        val next = (stops.last() + 5).takeIf { it <= com.ditherfarm.game.MINE_BOTTOM }
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
            next != null -> world.gateBlocker(next)?.uppercase() ?: "REACH B$next BY LADDER FOR A STOP"
            else -> "YOU REACHED THE BOTTOM!"
        }
        g.text(note.take(34), x + pw / 2, y + ph - 16, Gfx.INK, align = 0, outline = 0, shadow = false)
        // The relic page, once the deep mine has given up its first relic.
        if (world.relicsFound > 0 && ui.button("relics", x + pw - 74, y + 4, 50, 14, "RELICS", style = if (world.relicRewardBlocker() == null) "gold" else "wood")) onRelics?.invoke()
        return true
    }

    /** Opens the relic page (set by Chrome). */
    var onRelics: (() -> Unit)? = null

    // ------------------------------------------------------------------ relics
    /** The eight deep-mine relics: found ones shown, missing ones as "?"; the full set pays once. */
    fun relics(): Boolean {
        val (x, y, pw, ph) = frame("RELICS ${world.relicsFound}/${RELICS.size}", 236).toList()
        if (closeButton(x, y, pw)) return false
        g.text("FROM RELIC ROCKS AND TREASURE", x + 10, y + 22, Gfx.MUTED, outline = 0, shadow = false)
        g.text("ROOMS BELOW FLOOR 40", x + 10, y + 32, Gfx.MUTED, outline = 0, shadow = false)
        val cw = (pw - 20) / 4
        RELICS.entries.forEachIndexed { i, (id, name) ->
            val cx = x + 10 + (i % 4) * cw
            val cy = y + 46 + (i / 4) * 66
            val found = id in world.state.relics
            g.slice9("ui/slot", cx + 2, cy, cx + cw - 2, cy + 44, 4)
            if (found) g.spriteScaled("relics/$id", (cx + cw / 2).toFloat(), (cy + 22).toFloat(), 2f, 2f)
            else g.text("?", cx + cw / 2, cy + 18, Gfx.GREY, align = 0)
            val words = (if (found) name else if (id == GOLEM_RELIC) "THE GOLEM" else "???").uppercase().split(" ")
            words.take(2).forEachIndexed { k, w -> g.text(w, cx + cw / 2, cy + 47 + k * 9, if (found) Gfx.INK else Gfx.MUTED, align = 0, outline = 0, shadow = false) }
        }
        val why = world.relicRewardBlocker()
        val label = if (world.state.relicsClaimed) "SET COMPLETE!" else "ALL 8: ${RELIC_REWARD["coins"]} COINS + PRISMATIC"
        if (ui.button("relic-claim", x + 10, y + ph - 28, pw - 20, 18, label, style = if (why == null) "gold" else "grey") && why == null) world.claimRelicReward()
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
        g.text(if (hut) "FELLS $trees" else "CLEARS STUMPS, PLANTS SEEDS", x + 10, y + 48, Gfx.MUTED, outline = 0, shadow = false)
        g.text(if (hut) "NEVER TOUCHES ANCIENT OR HIVE TREES" else "USES TREE SEEDS FROM THE BARN", x + 10, y + 60, Gfx.MUTED, outline = 0, shadow = false)
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
        // Story Act 2: the Vexmoor Cup opens from here once it has begun.
        if (world.act2.chapter >= 1 && ui.button("cup", x + pw - 76, y + 4, 50, 14, "CUP", "icons/trophy", style = "gold")) { onContest?.invoke(); return true }
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
            g.text(a.desc.uppercase().take(30), x + 30, ry + 13, Gfx.MUTED, outline = 0, shadow = false)
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
                // A coin icon, not a bare "C" (playtest round 7); the row has little room.
                val reward = if (t.coins > 0) Ui.compact(t.coins) else if (t.xp > 0) "${t.xp}XP" else "GIFT"
                g.text(reward, x + pw - 12, ry + 22, 0xFF3E8948.toInt(), align = 1, outline = 0, shadow = false)
                if (t.coins > 0) g.sprite("icons/coin", (x + pw - 22 - g.textWidth(reward)).toFloat(), (ry + 26).toFloat())
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
            val face = found && e.startsWith("villager:")
            if (ui.slot("entry-$e", sx, sy, if (found && !face) entryIcon(e) else null, -1, false, 20) && found) {
                hud.toast(if (e.startsWith("villager:")) world.data.villagers.firstOrNull { "villager:${it.id}" == e }?.name ?: e else world.data.name(e))
            }
            if (face) g.spriteScaled(entryIcon(e), sx + 10f, sy + 10f, 0.38f, 0.38f)
            if (!found) g.text("?", sx + 10, sy + 6, Gfx.MUTED, align = 0, outline = 0, shadow = false)
        }
        val by = top + h - 40
        g.text("PAGE REWARD: ${p.bonusText.uppercase()}", x + pw / 2, by, Gfx.INK, align = 0, outline = 0, shadow = false)
        g.text("+${Ui.compact(p.coins)} COINS", x + pw / 2, by + 10, 0xFF3E8948.toInt(), align = 0, outline = 0, shadow = false)
        when {
            p.id in world.state.pagesClaimed -> g.text("CLAIMED", x + pw / 2, by + 24, Gfx.MUTED, align = 0, outline = 0, shadow = false)
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
            g.text("CHORES START AT LEVEL $CHORES_LEVEL", x + pw / 2, y + 8, Gfx.MUTED, align = 0, outline = 0, shadow = false)
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
            g.text("HIRE A FARMHAND TO TAKE CONTRACTS", x + pw / 2, y + 18, Gfx.MUTED, align = 0, outline = 0, shadow = false)
            return
        }
        val done = world.contractDone(c)
        val left = contractDaysLeft(today)
        g.text(if (c.claimed) "NEXT ONE MONDAY" else "$left DAY${if (left > 1) "S" else ""} LEFT", x + pw - (if (c.claimed || done) 10 else 28), y, 0xFF68386C.toInt(), align = 1, outline = 0, shadow = false)
        if (!c.claimed && !done) pinToggle("contract:${c.week}", x + pw - 16, y + 3)
        g.text("SHIP BY TRUCK THIS WEEK:", x + 10, y + 11, Gfx.MUTED, outline = 0, shadow = false)
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
            c.claimed -> g.text("CLAIMED. WELL DONE!", x + pw / 2, y + 4, Gfx.MUTED, align = 0, outline = 0, shadow = false)
            done -> if (ui.button("contract-claim", x + pw / 2 - 30, y, 60, 16, "CLAIM", style = "gold")) world.claimContract()
            else -> g.text("SET FIELDS TO TRUCK, THEN SEND IT", x + pw / 2, y + 4, Gfx.MUTED, align = 0, outline = 0, shadow = false)
        }
    }

    private fun eventsTab(x: Int, top: Int, pw: Int) {
        var y = top
        val f = world.festival()
        g.sprite("icons/festival", (x + 18).toFloat(), (y + 8).toFloat())
        if (f != null) {
            g.text(f.name.uppercase() + " TODAY!", x + 30, y + 2, Gfx.INK, outline = 0, shadow = false)
            g.text(f.goal.uppercase(), x + 30, y + 13, Gfx.MUTED, outline = 0, shadow = false)
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
            g.text("${next.season.label.uppercase()} ${FESTIVAL_DAYS.first}-${FESTIVAL_DAYS.last}: ${next.goal.uppercase()}".take(36), x + 30, y + 13, Gfx.MUTED, outline = 0, shadow = false)
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
        g.text(note, x + 30, y + 13, Gfx.MUTED, outline = 0, shadow = false)
        y += 30
        g.rect(x + 8, y, x + pw - 8, y + 1, 0xFFC28569.toInt())
        y += 6
        g.sprite("icons/gift", (x + 18).toFloat(), (y + 8).toFloat())
        g.text("DAILY GIFT STREAK: ${world.state.giftStreak} DAYS", x + 30, y + 2, Gfx.INK, outline = 0, shadow = false)
        g.text("COME BACK EVERY DAY. DAY 7 IS BIGGER!", x + 30, y + 13, Gfx.MUTED, outline = 0, shadow = false)
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
    private var decorPage = 0

    /** Decorations to buy and place, ten to a page; beauty, its order bonus and milestones on top. */
    fun decor(): Boolean {
        // Keepsakes (round 6) show up after the shop's own pieces once a best friend gives one.
        val all = DECOR.values.filter { it.keepsake == null } + world.keepsakesToPlace()
        val pages = (all.size + 9) / 10
        decorPage = decorPage.coerceIn(0, pages - 1)
        val (x, y, pw, ph) = frame("DECORATE", 64 + 5 * 40 + 22).toList()
        if (closeButton(x, y, pw)) return false
        val b = world.beauty
        val best = if (b >= BEST_FARM_BEAUTY) "  BEST FARM!" else ""
        g.text("BEAUTY $b: +${(world.decorBonus() * 100).toInt()}% ORDER COINS$best", x + 10, y + 22, Gfx.INK, outline = 0, shadow = false)
        world.nextBeautyMilestone()?.let { (need, coins) ->
            if (b >= need) {
                if (ui.button("beauty-claim", x + 10, y + 34, pw - 20, 16, "BEAUTY $need REACHED: CLAIM $coins COINS", style = "gold")) world.claimBeautyMilestone()
            } else g.text("NEXT: $need BEAUTY PAYS $coins COINS" + if (need == BEST_FARM_BEAUTY) " + BEST FARM" else "", x + 10, y + 38, Gfx.MUTED, outline = 0, shadow = false)
        }
        val cw = (pw - 20) / 2
        all.drop(decorPage * 10).take(10).forEachIndexed { i, d ->
            val cx = x + 10 + (i % 2) * cw
            val cy = y + 54 + (i / 2) * 40
            g.slice9("ui/slot", cx, cy, cx + 32, cy + 32, 4)
            // Big pieces (pond, arch, fountain) shrink to fit the card.
            val fitScale = game.assets["decor/${d.id}"].let { 30f / maxOf(it.w, it.h) }
            if (fitScale < 1f) g.spriteScaled("decor/${d.id}", (cx + 16).toFloat(), (cy + 29).toFloat(), fitScale, fitScale)
            else g.sprite("decor/${d.id}", (cx + 16).toFloat(), (cy + if (d.flat) 18 else 28).toFloat())
            g.text(fit(d.name.uppercase(), cw - 40), cx + 36, cy + 2, Gfx.INK, outline = 0, shadow = false)
            if (d.keepsake != null) g.text("GIFT  +${d.beauty}", cx + 36, cy + 13, Gfx.RED, outline = 0, shadow = false)
            else {
                g.sprite("icons/coin", (cx + 40).toFloat(), (cy + 17).toFloat())
                g.text("${d.price}  +${d.beauty}", cx + 49, cy + 13, Gfx.INK, outline = 0, shadow = false)
            }
            val why = world.decorBlocker(d.id)
            when {
                world.level < d.unlockLevel -> g.text("LEVEL ${d.unlockLevel}", cx + 36, cy + 24, Gfx.MUTED, outline = 0, shadow = false)
                d.season != null && world.time.season != d.season -> g.text("IN ${d.season.name}", cx + 36, cy + 24, Gfx.MUTED, outline = 0, shadow = false)
                ui.button("decor-${d.id}", cx + cw - 48, cy + 21, 40, 13, if (d.keepsake != null) "PLACE" else "BUY", style = if (why == null) "green" else "grey") ->
                    if (why != null) hud.toast(why) else { onDecor?.invoke(d.id); return false }
            }
        }
        if (pages > 1) {
            val by = y + ph - 20
            if (ui.button("decor-prev", x + 10, by, 40, 14, "<", style = if (decorPage > 0) "wood" else "grey") && decorPage > 0) decorPage--
            g.text("PAGE ${decorPage + 1}/$pages", x + pw / 2, by + 3, Gfx.INK, align = 0, outline = 0, shadow = false)
            if (ui.button("decor-next", x + pw - 50, by, 40, 14, ">", style = if (decorPage < pages - 1) "wood" else "grey") && decorPage < pages - 1) decorPage++
        }
        return true
    }

    // ------------------------------------------------------------------ pet page
    /** The pet's page: level and love, what its gifts hold, and accessories to buy and wear. */
    fun petInfo(): Boolean {
        val kind = world.pet.kind ?: return false
        val p = world.pet
        val (x, y, pw, ph) = frame(p.name, 104 + PET_ACCESSORIES.size * 24 + 18).toList()
        if (closeButton(x, y, pw)) return false
        g.slice9("ui/slot", x + 10, y + 22, x + 76, y + 86, 4)
        game.pet.drawPet(g, kind, (x + 43).toFloat(), (y + 80).toFloat(), false, scale = 3f, pose = "sit")
        val lv = world.petLevel
        g.text("LEVEL $lv ${kind.name}", x + 84, y + 24, Gfx.INK, outline = 0, shadow = false)
        world.petLoveToNext()?.let { (have, need) ->
            val from = PET_LEVELS[lv - 1]
            ui.bar(x + 84, y + 36, pw - 98, (have - from).toFloat() / (need - from), "red")
            g.text("LOVE $have/$need", x + 84, y + 46, Gfx.MUTED, outline = 0, shadow = false)
        } ?: g.text("LOVE: BEST FRIENDS!", x + 84, y + 40, Gfx.RED, outline = 0, shadow = false)
        g.text("GIFTS: UP TO ${3 + (lv - 1) / 2} FINDS", x + 84, y + 60, Gfx.INK, outline = 0, shadow = false)
        g.text("TAP: GIFT OR FETCH", x + 84, y + 72, Gfx.MUTED, outline = 0, shadow = false)
        PET_ACCESSORIES.values.forEachIndexed { i, a ->
            val ry = y + 96 + i * 24
            g.slice9("ui/slot", x + 10, ry, x + 30, ry + 20, 4)
            g.spriteScaled("pets/acc_${a.id}", (x + 20).toFloat(), (ry + 14).toFloat(), 2f, 2f)
            g.text(a.name.uppercase(), x + 36, ry + 3, Gfx.INK, outline = 0, shadow = false)
            val owned = a.id in p.accessories
            val wearing = p.accessory == a.id
            g.text(when { wearing -> "WEARING"; owned -> "OWNED"; lv < a.petLevel -> "PET LEVEL ${a.petLevel}"; else -> "${a.price} COINS" }, x + 36, ry + 12, Gfx.MUTED, outline = 0, shadow = false)
            val label = when { wearing -> "OFF"; owned -> "WEAR"; lv < a.petLevel -> "LV ${a.petLevel}"; else -> "BUY" }
            val ok = wearing || world.accessoryBlocker(a.id) == null
            if (ui.button("acc-${a.id}", x + pw - 58, ry + 3, 48, 14, label, style = if (ok) "green" else "grey")) {
                if (wearing) world.wearAccessory(null) else world.accessoryBlocker(a.id)?.let { hud.toast(it) } ?: world.wearAccessory(a.id)
            }
        }
        return true
    }

    // ------------------------------------------------------------------ pasture
    /** The pasture (round 6): the trough, buying animals, each animal's hearts and the Ranch hand. */
    fun pasture(): Boolean {
        val r = world.ranch
        val (x, y, pw, ph) = frame("PASTURE", 292).toList()
        if (closeButton(x, y, pw)) return false
        // Trough.
        g.slice9("ui/slot", x + 10, y + 22, x + 34, y + 46, 4)
        g.sprite("deco/trough", (x + 22).toFloat(), (y + 40).toFloat())
        g.text("TROUGH ${r.feed}/$TROUGH_MAX", x + 40, y + 24, Gfx.INK, outline = 0, shadow = false)
        val eat = world.dailyFeed()
        g.text(if (eat == 0) "WHEAT 1 FEED, CORN 2" else "THEY EAT $eat A DAY", x + 40, y + 35, if (eat > r.feed) Gfx.RED else Gfx.MUTED, outline = 0, shadow = false)
        listOf("wheat" to 10, "corn" to 5).forEachIndexed { i, (crop, n) ->
            val bx = x + pw - 104 + i * 50
            val why = world.troughBlocker(crop)
            if (ui.button("feed-$crop", bx, y + 26, 46, 16, "+$n", "items/$crop", style = if (why == null) "green" else "grey")) {
                if (why != null) hud.toast(why) else world.fillTrough(crop, n)
            }
        }
        // Buying.
        AnimalKind.entries.forEachIndexed { i, k ->
            val ry = y + 54 + i * 30
            g.slice9("ui/slot", x + 10, ry, x + 36, ry + 26, 4)
            g.sprite("animals/${k.id}_0", (x + 23).toFloat(), (ry + 21).toFloat())
            g.text("${k.label.uppercase()}  ${world.animalCount(k)}/${k.max}", x + 42, ry + 3, Gfx.INK, outline = 0, shadow = false)
            g.sprite("items/${k.product}", (x + 47).toFloat(), (ry + 18).toFloat())
            g.text(if (k.everyDays == 1) "EVERY DAY, EATS ${k.eats}" else "EVERY ${k.everyDays} DAYS, EATS ${k.eats}", x + 55, ry + 14, Gfx.MUTED, outline = 0, shadow = false)
            val why = world.animalBlocker(k)
            val label = if (world.level < k.unlockLevel) "L${k.unlockLevel}" else "${k.price}"
            if (ui.button("buy-${k.id}", x + pw - 66, ry + 5, 56, 16, label, if (world.level < k.unlockLevel) null else "icons/coin", style = if (why == null) "green" else "grey")) {
                if (why != null) hud.toast(why) else world.buyAnimal(k)
            }
        }
        // Your animals: hearts, and a ready product.
        val gy = y + 148
        g.text(if (r.animals.isEmpty()) "NO ANIMALS YET" else "YOUR ANIMALS (TAP ONE TO PET IT)", x + 10, gy, Gfx.MUTED, outline = 0, shadow = false)
        val cw = (pw - 20) / 4
        r.animals.forEachIndexed { i, a ->
            val cx = x + 10 + (i % 4) * cw
            val cy = gy + 12 + (i / 4) * 36
            g.slice9("ui/slot", cx, cy, cx + cw - 4, cy + 32, 4)
            val art = if (a.kind == AnimalKind.SHEEP && !a.product) "animals/sheep_shorn_0" else "animals/${a.kind.id}_0"
            g.sprite(art, (cx + 14).toFloat(), (cy + 22).toFloat())
            if (a.product) g.sprite("items/${a.kind.product}", (cx + cw - 14).toFloat(), (cy + 12).toFloat())
            for (h in 0 until MAX_HEARTS) miniHeart(cx + 4 + h * 6, cy + 25, h < a.hearts)
            // A tap on the card collects its product, or pets it.
            if (ui.clickable("animal-$i", cx, cy, cw - 4, 32)) {
                if (a.product) world.collectProduct(i) else if (!world.pettedToday(a)) world.petAnimal(i) else hud.toast("Already petted today")
            }
        }
        // Ranch hand.
        val hy = y + ph - 46
        g.text("RANCH HAND: FILLS THE TROUGH", x + 10, hy, Gfx.INK, outline = 0, shadow = false)
        g.text("AND COLLECTS EACH MORNING", x + 10, hy + 10, Gfx.INK, outline = 0, shadow = false)
        g.text(if (r.ranchHand) "HIRED" else RANCH_HAND_COST.entries.joinToString(" ") { (k, v) -> if (k == "coins") "$v COINS" else "$v ${world.data.name(k).uppercase()}" }, x + 10, hy + 22, Gfx.MUTED, outline = 0, shadow = false)
        if (!r.ranchHand) {
            val why = world.ranchHandBlocker()
            if (ui.button("ranch-hand", x + pw - 66, hy + 2, 56, 18, if (world.level < RANCH_HAND_LEVEL) "L$RANCH_HAND_LEVEL" else "HIRE", style = if (why == null) "gold" else "grey")) {
                if (why != null) hud.toast(why) else world.hireRanchHand()
            }
        }
        return true
    }

    // ------------------------------------------------------------------ the Vexmoor Cup
    /** Opens the Vexmoor Cup page (set by Chrome). */
    var onContest: (() -> Unit)? = null

    /** Story Act 2 (round 6): the chapter's two goals and this week's contest against Vexmoor. */
    fun contest(): Boolean {
        val a = world.act2
        val (x, y, pw, ph) = frame("VEXMOOR CUP", 214).toList()
        if (closeButton(x, y, pw)) return false
        if (a.chapter >= ACT2_DONE) {
            g.sprite("decor/festival_statue", (x + pw / 2).toFloat(), (y + 110).toFloat())
            g.text("THE VALLEY IS SAVED!", x + pw / 2, y + 120, Gfx.GOLD, align = 0)
            g.text("CUP WEEKS WON: ${a.wins}", x + pw / 2, y + 136, Gfx.INK, align = 0, outline = 0, shadow = false)
            return true
        }
        g.text("CHAPTER ${a.chapter} OF $ACT2_CHAPTERS", x + 10, y + 22, Gfx.MUTED, outline = 0, shadow = false)
        // The two things this chapter needs.
        fun check(row: Int, done: Boolean, text: String) {
            val ry = y + 36 + row * 14
            g.rect(x + 10, ry + 1, x + 17, ry + 8, if (done) 0xFF3E8948.toInt() else 0xFFC0CBDC.toInt())
            g.text(text.uppercase(), x + 22, ry, if (done) 0xFF3E8948.toInt() else Gfx.INK, outline = 0, shadow = false)
        }
        check(0, a.wonThisChapter, "Win a Cup week")
        CHAPTER_GOALS[a.chapter]?.takeIf { a.chapter > 1 }?.let { goal ->
            val (have, need) = goal.progress(world)
            check(1, have >= need, "${goal.text} ($have/$need)")
        }
        // This week's contest: your score against Vexmoor's.
        val c = world.contest ?: return true
        val cy = y + 74
        g.slice9("ui/slot", x + 8, cy, x + pw - 8, cy + 94, 4)
        g.sprite("icons/trophy", (x + 22).toFloat(), (cy + 14).toFloat())
        g.text(c.name.uppercase(), x + 34, cy + 6, Gfx.GOLD)
        g.text(c.desc.uppercase(), x + 34, cy + 18, Gfx.INK, outline = 0, shadow = false)
        val score = world.contestScore().toInt()
        g.text("YOU", x + 16, cy + 36, Gfx.INK, outline = 0, shadow = false)
        ui.bar(x + 60, cy + 36, pw - 120, (score.toFloat() / a.target).coerceIn(0f, 1f), "green")
        g.text("$score", x + pw - 52, cy + 36, Gfx.INK, outline = 0, shadow = false)
        g.text("VEXMOOR", x + 16, cy + 52, Gfx.INK, outline = 0, shadow = false)
        ui.bar(x + 60, cy + 52, pw - 120, 1f, "red")
        g.text("${a.target}", x + pw - 52, cy + 52, Gfx.INK, outline = 0, shadow = false)
        val left = world.contestDaysLeft()
        g.text(if (a.weekWon) "WON THIS WEEK! NEXT CONTEST SOON" else "$left DAY${if (left == 1) "" else "S"} LEFT", x + pw / 2, cy + 72, if (a.weekWon) 0xFF3E8948.toInt() else Gfx.MUTED, align = 0, outline = 0, shadow = false)
        g.text("WEEKS WON: ${a.wins}", x + 10, y + ph - 18, Gfx.MUTED, outline = 0, shadow = false)
        return true
    }

    // ------------------------------------------------------------------ villagers
    /** What the villager just said (a chat or a thank-you), shown in their panel. */
    var villagerSays = ""
    private var giftMode = false
    /** The gift picked in the villager panel, given with GIVE (playtest round 7: tiles have no names). */
    private var giftPick: String? = null

    /** Plays a heart event (set by Chrome). */
    var onHeartEvent: ((String) -> Unit)? = null
    /** Asks before filing for divorce (set by Chrome). */
    var onDivorce: ((String) -> Unit)? = null

    /** A villager (round 6): hearts, a chat, a gift a day, and their heart events. */
    fun villager(id: String): Boolean {
        val v = world.villager(id) ?: return false
        val (x, y, pw, ph) = frame(v.name.uppercase(), if (giftMode) 280 else 196).toList()
        if (closeButton(x, y, pw)) { giftMode = false; giftPick = null; villagerSays = ""; return false }
        g.slice9("ui/slot", x + 10, y + 22, x + 58, y + 70, 4)
        g.spriteScaled("portraits/$id", (x + 34).toFloat(), (y + 46).toFloat(), 0.92f, 0.92f)
        g.text(v.role.uppercase(), x + 66, y + 24, Gfx.MUTED, outline = 0, shadow = false)
        world.romanceLabel(id)?.let { g.text(it, x + pw - 12, y + 24, Gfx.RED, align = 1, outline = 0, shadow = false) }
        val h = world.hearts(id)
        // Up to 14 hearts for a spouse (round 7), drawn a little closer together.
        val top = maxOf(FRIEND_HEARTS, world.maxHearts(id))
        val step = if (top > FRIEND_HEARTS) 6 else 7
        for (k in 0 until top) miniHeart(x + 66 + k * step, y + 36, k < h)
        world.nextHeart(id)?.let { (have, need) -> g.text("$have/$need", x + 66, y + 45, Gfx.MUTED, outline = 0, shadow = false) }
            ?: g.text(when {
                world.isSpouse(id) -> "SOULMATES!"
                world.canCourt(id) && h >= COURT_HEARTS && h < FRIEND_HEARTS -> "A BOUQUET OPENS MORE HEARTS"
                else -> "BEST FRIENDS!"
            }, x + 66, y + 45, Gfx.RED, outline = 0, shadow = false)
        g.text("LOVES", x + 66, y + 57, Gfx.INK, outline = 0, shadow = false)
        v.favorites.take(5).forEachIndexed { k, item -> g.sprite(icon(item), (x + 106 + k * 16).toFloat(), (y + 61).toFloat()) }
        // What they said last.
        val say = villagerSays.ifEmpty { if (world.talkedToday(id)) "" else "..." }
        hud.wrap("\"$say\"".uppercase(), pw - 24).take(3).forEachIndexed { k, line -> g.text(line, x + 12, y + 78 + k * 10, Gfx.INK, outline = 0, shadow = false) }
        val by = y + 112
        world.pendingHeartEvent(id)?.let { n ->
            if (ui.button("heart-event", x + 10, by, pw - 20, 18, "$n HEARTS: ${v.name.uppercase()} WANTS TO TALK", style = "gold")) {
                giftMode = false; villagerSays = ""; onHeartEvent?.invoke(heartScene(id, n)); return true
            }
        }
        val talkY = by + 24
        if (ui.button("talk", x + 10, talkY, pw / 2 - 14, 18, if (world.talkedToday(id)) "TALKED" else "TALK", style = if (world.talkedToday(id)) "grey" else "green") && !world.talkedToday(id)) world.talk(id)
        if (ui.button("gift", x + pw / 2 + 4, talkY, pw / 2 - 14, 18, if (world.giftedToday(id)) "GIFT GIVEN" else if (giftMode) "CANCEL" else "GIVE GIFT", style = if (world.giftedToday(id)) "grey" else "wood") && !world.giftedToday(id)) { giftMode = !giftMode; giftPick = null }
        // Romance (round 7): a bouquet, the date, the proposal; the wedding day; divorce at the bank.
        val ry = talkY + 22
        val r = world.romance
        val (label, blocker, act) = when {
            world.pendingDate(id) -> Triple("GO ON A DATE", null) { onHeartEvent?.invoke(dateScene(id)) }
            world.canCourt(id) && !world.isDating(id) && !world.isSpouse(id) && r.engaged != id && h >= COURT_HEARTS ->
                Triple("GIVE A BOUQUET", world.bouquetBlocker(id)) { if (world.giveBouquet(id)) onHeartEvent?.invoke(bouquetScene(id)) }
            world.isDating(id) && r.engaged == null && r.spouse == null && h >= FRIEND_HEARTS ->
                Triple("PROPOSE", world.proposeBlocker(id)) { if (world.propose(id)) onHeartEvent?.invoke(proposalScene(id)) }
            world.isSpouse(id) -> Triple("DIVORCE (BANK, ${DIVORCE_COST / 1000}K)", null) { onDivorce?.invoke(id) }
            else -> Triple(null, null, null)
        }
        when {
            r.engaged == id -> {
                val days = (r.weddingDay - world.time.dayIndex).coerceAtLeast(0)
                g.text(if (days == 0L) "THE WEDDING IS TODAY!" else "THE WEDDING IS IN $days DAY${if (days == 1L) "" else "S"}", x + pw / 2, ry + 4, Gfx.RED, align = 0, outline = 0, shadow = false)
            }
            label != null && act != null -> {
                val divorce = world.isSpouse(id)
                val style = if (divorce) "red" else if (blocker == null) "gold" else "wood"
                if (ui.button("romance", x + 10, ry, pw - 20, 18, label, style = style)) {
                    if (blocker != null) hud.toast(blocker) else { giftMode = false; villagerSays = ""; act(); if (!divorce) return true }
                }
            }
            else -> g.text("ORDERS FROM ${v.name.uppercase()} ADD HEARTS TOO", x + pw / 2, ry + 2, Gfx.MUTED, align = 0, outline = 0, shadow = false)
        }
        if (giftMode && !world.giftedToday(id)) {
            // Everything in storage that can be given; loved gifts are marked with a heart.
            val keys = world.state.inventory.keys.filter { world.giftable(it) && world.count(it) > 0 }.sortedByDescending { world.giftReaction(id, it).ordinal == 0 }
            // The picked gift's name and the button that gives it.
            val pick = giftPick?.takeIf { it in keys }
            val ny = talkY + 42
            if (pick != null) {
                g.text(fit(world.data.name(baseId(pick)).uppercase(), pw - 96), x + 12, ny + 5, Gfx.INK, outline = 0, shadow = false)
                if (ui.button("give", x + pw - 74, ny, 64, 16, "GIVE")) { world.giveGift(id, pick); giftMode = false; giftPick = null }
            } else if (keys.isNotEmpty()) g.text("PICK A GIFT", x + 12, ny + 5, Gfx.MUTED, outline = 0, shadow = false)
            val gy = ny + 20
            val cols = (pw - 20) / 26
            val rows = (ph - (gy - y) - 8) / 26
            keys.take(cols * rows).forEachIndexed { k, key ->
                val cx = x + 10 + (k % cols) * 26; val cy = gy + (k / cols) * 26
                g.slice9(if (key == pick) "ui/slot_sel" else "ui/slot", cx, cy, cx + 24, cy + 24, 4)
                g.sprite(icon(baseId(key)), (cx + 12).toFloat(), (cy + 11).toFloat())
                com.ditherfarm.game.Quality.of(key).icon?.let { g.sprite(it, (cx + 5).toFloat(), (cy + 19).toFloat()) }
                if (world.giftReaction(id, key) == GiftReaction.LOVED) miniHeart(cx + 17, cy + 2, true)
                g.text("${world.count(key)}", cx + 22, cy + 15, Gfx.WHITE, align = 1)
                if (ui.clickable("gift-$key", cx, cy, 24, 24)) giftPick = key
            }
            if (keys.isEmpty()) g.text("NOTHING TO GIVE", x + pw / 2, gy + 8, Gfx.MUTED, align = 0, outline = 0, shadow = false)
        }
        return true
    }

    /**
     * The farmer's home (round 7): Grandpa's old cabin, with what it takes to build the family
     * farmhouse (needed to propose); once built, the family page: the spouse, their morning, their
     * trade bonus, and the kid.
     */
    fun farmhouse(): Boolean {
        val r = world.romance
        if (r.farmhouse < 1) {
            val (x, y, pw, ph) = frame("GRANDPA'S FARMHOUSE", 196).toList()
            if (closeButton(x, y, pw)) return false
            g.slice9("ui/slot", x + 10, y + 24, x + 84, y + 98, 4)
            g.sprite("buildings/farmhouse_old", (x + 47).toFloat(), (y + 80).toFloat())
            listOf("THE OLD LOG CABIN, ONE", "ROOM AND A LEAKY ROOF.", "BUILD A FAMILY FARMHOUSE", "TO MAKE ROOM FOR TWO.").forEachIndexed { i, line ->
                g.text(line, x + 92, y + 28 + i * 12, Gfx.INK, outline = 0, shadow = false)
            }
            g.sprite("buildings/farmhouse_big", (x + pw - 40).toFloat(), (y + 150).toFloat())
            var cy = y + 106
            for ((k, v) in FARMHOUSE_COST) {
                g.sprite(if (k == "coins") "icons/coin" else "items/$k", (x + 19).toFloat(), (cy + 5).toFloat())
                val have = if (k == "coins") world.state.coins >= v else world.count(k) >= v
                val got = if (k == "coins") "" else " (${world.count(k)})"
                g.text(if (k == "coins") "$v COINS" else "$v ${world.data.name(k).uppercase()}$got", x + 30, cy + 1, if (have) Gfx.INK else Gfx.RED, outline = 0, shadow = false)
                cy += 13
            }
            val why = world.farmhouseBlocker()
            if (ui.button("farmhouse-build", x + pw / 2 - 50, y + ph - 28, 100, 20, if (world.level < FARMHOUSE_LEVEL) "LEVEL $FARMHOUSE_LEVEL" else "BUILD", style = if (why == null) "gold" else "grey")) {
                if (why != null) hud.toast(why) else { world.buildFarmhouse(); return false }
            }
            return true
        }
        val (x, y, pw, ph) = frame("HOME", 200).toList()
        if (closeButton(x, y, pw)) return false
        g.slice9("ui/slot", x + 10, y + 24, x + 84, y + 98, 4)
        g.sprite("buildings/farmhouse_big", (x + 47).toFloat(), (y + 86).toFloat())
        val spouse = r.spouse?.let { world.villager(it) }
        if (spouse == null) {
            val lines = if (r.engaged != null) listOf("ALL READY FOR TWO.", "THE WEDDING IS COMING!") else listOf("ROOM FOR TWO, AND A", "PORCH FOR THE EVENINGS.", "COURT SOMEONE WITH A", "BOUQUET FROM THE BENCH.")
            lines.forEachIndexed { i, line -> g.text(line, x + 92, y + 28 + i * 12, Gfx.INK, outline = 0, shadow = false) }
        } else {
            g.spriteScaled("portraits/${spouse.id}", (x + pw - 30).toFloat(), (y + 46).toFloat(), 0.6f, 0.6f)
            g.text("MARRIED TO", x + 92, y + 26, Gfx.MUTED, outline = 0, shadow = false)
            g.text(spouse.name.uppercase(), x + 92, y + 37, Gfx.RED, outline = 0, shadow = false)
            world.spousePerk()?.let { p -> hud.wrap(p.text.uppercase(), pw - 104).take(3).forEachIndexed { i, l -> g.text(l, x + 92, y + 62 + i * 10, Gfx.INK, outline = 0, shadow = false) } }
            r.lastGift?.let { gift ->
                g.text("THIS MORNING:", x + 12, y + 106, Gfx.MUTED, outline = 0, shadow = false)
                g.sprite(icon(gift), (x + 20).toFloat(), (y + 124).toFloat())
                g.text("BROUGHT YOU ${world.data.name(gift).uppercase()}", x + 30, y + 120, Gfx.INK, outline = 0, shadow = false)
                r.lastChore?.let { g.text(it.uppercase(), x + 30, y + 132, Gfx.INK, outline = 0, shadow = false) }
            }
        }
        r.kidName?.let { kid ->
            val ky = y + ph - 46
            g.slice9("ui/slot", x + 10, ky, x + 40, ky + 30, 4)
            val stage = world.kidStage()
            val art = "people/kid_${listOf("baby", "toddler", "child")[stage]}_${if (r.kidFemale) "g" else "b"}_idle"
            g.sprite(art, (x + 25).toFloat(), (ky + 27).toFloat())
            g.text(kid.uppercase(), x + 48, ky + 6, Gfx.INK, outline = 0, shadow = false)
            g.text(world.kidAgeLabel() + if (stage < 2) " - GROWS WITH THE SEASONS" else "", x + 48, ky + 17, Gfx.MUTED, outline = 0, shadow = false)
        } ?: if (r.kidDue >= 0) g.text("A LITTLE ONE IS ON THE WAY!", x + pw / 2, y + ph - 30, Gfx.RED, align = 0, outline = 0, shadow = false) else Unit
        return true
    }

    /** A 5x4 heart: red when [full], an outline when empty. */
    fun miniHeart(x: Int, y: Int, full: Boolean) {
        val c = if (full) Gfx.RED else 0xFFC0CBDC.toInt()
        val rows = if (full) listOf("XX.XX", "XXXXX", ".XXX.", "..X..") else listOf("XX.XX", "X.X.X", ".X.X.", "..X..")
        rows.forEachIndexed { r, row -> row.forEachIndexed { k, ch -> if (ch == 'X') g.rect(x + k, y + r, x + k + 1, y + r + 1, c) } }
    }

    // ------------------------------------------------------------------ orchard
    /** The orchard (round 6): buy it, choose a sapling for an empty [spot], or see the tree there. */
    fun orchard(spot: Int): Boolean {
        val o = world.orchard
        if (!o.open) {
            val (x, y, pw, ph) = frame("ORCHARD", 168).toList()
            if (closeButton(x, y, pw)) return false
            g.slice9("ui/slot", x + 10, y + 24, x + 84, y + 98, 4)
            g.sprite("trees/orchard_cherry_fruit", (x + 34).toFloat(), (y + 92).toFloat())
            g.sprite("trees/orchard_orange_fruit", (x + 60).toFloat(), (y + 96).toFloat())
            listOf("A FENCED ORCHARD WITH", "ROOM FOR $ORCHARD_SPOTS FRUIT TREES.", "TREES FRUIT EVERY 2-3", "DAYS IN THEIR SEASONS.").forEachIndexed { i, line ->
                g.text(line, x + 92, y + 28 + i * 12, Gfx.INK, outline = 0, shadow = false)
            }
            var cx = x + 12
            for ((k, v) in ORCHARD_COST) {
                g.sprite(if (k == "coins") "icons/coin" else "items/$k", (cx + 7).toFloat(), (y + 118).toFloat())
                val have = if (k == "coins") world.state.coins >= v else world.count(k) >= v
                g.text(if (k == "coins") "$v" else "$v ${world.data.name(k).uppercase()}", cx + 16, y + 114, if (have) Gfx.INK else Gfx.RED, outline = 0, shadow = false)
                cx += 90
            }
            val why = world.orchardBlocker()
            if (ui.button("orchard-buy", x + pw / 2 - 50, y + ph - 30, 100, 20, if (world.level < ORCHARD_LEVEL) "LEVEL $ORCHARD_LEVEL" else "BUY", style = if (why == null) "gold" else "grey")) {
                if (why != null) hud.toast(why) else { world.buyOrchard(); return false }
            }
            return true
        }
        val t = o.trees.getOrNull(spot)
        if (t == null) {
            val trees = FRUIT_TREES.values.toList()
            val (x, y, pw, ph) = frame("PLANT A TREE", 30 + trees.size * 40).toList()
            if (closeButton(x, y, pw)) return false
            trees.forEachIndexed { i, d ->
                val ry = y + 26 + i * 40
                g.slice9("ui/slot", x + 10, ry, x + 46, ry + 36, 4)
                g.sprite("trees/orchard_${d.id}_sapling", (x + 28).toFloat(), (ry + 32).toFloat())
                g.sprite("items/${d.fruit}", (x + 40).toFloat(), (ry + 28).toFloat())
                g.text(d.name.uppercase(), x + 52, ry + 2, Gfx.INK, outline = 0, shadow = false)
                g.text(d.seasons.sortedBy { it.ordinal }.joinToString(" ") { it.label.uppercase() }, x + 52, ry + 13, Gfx.MUTED, outline = 0, shadow = false)
                g.text("${d.yield} FRUIT EVERY ${d.everyDays} DAYS", x + 52, ry + 24, Gfx.MUTED, outline = 0, shadow = false)
                val why = world.treeBlocker(spot, d.id)
                val label = if (world.level < d.unlockLevel) "L${d.unlockLevel}" else "${d.price}"
                if (ui.button("tree-${d.id}", x + pw - 66, ry + 9, 56, 18, label, if (world.level < d.unlockLevel) null else "icons/coin", style = if (why == null) "green" else "grey")) {
                    if (why != null) hud.toast(why) else { world.plantOrchardTree(spot, d.id); return false }
                }
            }
            return true
        }
        val d = t.def
        val (x, y, pw, ph) = frame(d.name.uppercase(), 150).toList()
        if (closeButton(x, y, pw)) return false
        g.slice9("ui/slot", x + 10, y + 24, x + 70, y + 98, 4)
        val art = when { !world.treeMature(t) -> "trees/orchard_${d.id}_sapling"; t.fruit > 0 -> "trees/orchard_${d.id}_fruit"; else -> "trees/orchard_${d.id}" }
        g.sprite(art, (x + 40).toFloat(), (y + 94).toFloat())
        g.text(world.treeStatus(t).uppercase(), x + 78, y + 28, if (t.fruit > 0) Gfx.GREEN else Gfx.INK, outline = 0, shadow = false)
        g.text("FRUITS IN", x + 78, y + 44, Gfx.MUTED, outline = 0, shadow = false)
        g.text(d.seasons.sortedBy { it.ordinal }.joinToString(" ") { it.label.uppercase() }, x + 78, y + 54, Gfx.INK, outline = 0, shadow = false)
        g.sprite("items/${d.fruit}", (x + 85).toFloat(), (y + 75).toFloat())
        g.text("x${d.yield} EVERY ${d.everyDays} DAYS", x + 96, y + 71, Gfx.INK, outline = 0, shadow = false)
        if (t.fruit > 0 && ui.button("tree-pick", x + 12, y + ph - 30, 100, 20, "PICK", style = "green")) { world.pickFruit(spot); return false }
        if (ui.button("tree-dig", x + pw - 112, y + ph - 30, 100, 20, if (digArmed) "SURE?" else "DIG UP", style = "red")) {
            if (digArmed) { world.digUpTree(spot); digArmed = false; return false }
            digArmed = true; hud.toast("Tap again to dig the tree up. It is not refunded.")
        }
        return true
    }

    // ------------------------------------------------------------------ pet
    private var petPick: PetKind? = null
    private val petName = StringBuilder()
    /** The name field holds the suggested name: the first key typed replaces it. */
    private var petNameSuggested = false

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
                if (petName.isEmpty() || petName.toString() == other) { petName.setLength(0); petName.append(kind.defaultName); petNameSuggested = true }
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
                if (ui.button("pkey-$ch", kx, ky, kw, 20, ch.toString(), style = "wood")) {
                    if (petNameSuggested) { petName.setLength(0); petNameSuggested = false }
                    if (petName.length < PET_NAME_MAX) petName.append(ch)
                }
                kx += kw + 2
            }
            if (r == 2 && ui.button("pkey-del", kx, ky, kw * 2 + 2, 20, "DEL", style = "red") && petName.isNotEmpty()) { petName.setLength(petName.length - 1); petNameSuggested = false }
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
            if (world.merchantBought(i)) g.text("SOLD", cx + cw - 30, cy + 24, Gfx.MUTED, align = 0, outline = 0, shadow = false)
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
        g.text("SELLS UP TO $CART_DAILY_CAP A DAY AT DAWN, TOP RULE FIRST", x + pw / 2, y + 22, Gfx.MUTED, align = 0, outline = 0, shadow = false)
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
            g.text("${world.percentOfBase(r.item)}% TODAY", x + 32, ry + 16, Gfx.MUTED, outline = 0, shadow = false)
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
        g.text("CROP FOR THIS FIELD", x + 10, y + 36, Gfx.MUTED, outline = 0, shadow = false)
        crops.forEachIndexed { i, c ->
            val sx = x + 12 + (i % per) * 33
            val sy = y + 48 + (i / per) * 28
            if (ui.slot("fcrop-${c.id}", sx, sy, icon(c.seedItem), -1, f.crop == c.id, 24)) f.crop = if (f.crop == c.id) null else c.id
        }
        val ty = y + 52 + rows * 28
        g.text("HARVEST GOES TO", x + 10, ty, Gfx.MUTED, outline = 0, shadow = false)
        if (ui.button("fdest-truck", x + 10, ty + 12, 96, 16, "TRUCK (SELL)", style = if (f.toTruck) "gold" else "wood")) f.toTruck = true
        if (ui.button("fdest-silo", x + pw - 106, ty + 12, 96, 16, "SILO (KEEP)", style = if (!f.toTruck) "gold" else "wood")) f.toTruck = false
        g.text("FARMHAND", x + 10, ty + 40, Gfx.MUTED, outline = 0, shadow = false)
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
        g.text("BRINGS:", x + 10, ry, Gfx.MUTED, outline = 0, shadow = false)
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
        g.text("PACKS OF $SEED_PACK, 20% OFF", x + 10, y + 22, Gfx.MUTED, outline = 0, shadow = false)
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
        } else g.text("RESET AT THE WORKBENCH", x + pw - 10, y + 22, Gfx.MUTED, align = 1, outline = 0, shadow = false)
        skillTab = ui.tabs("skill-tabs", x + 8, y + 36, pw - 16, BRANCHES.map { it.short }, skillTab)
        val br = BRANCHES[skillTab]
        g.sprite("icons_big/skill_branch_${br.id}", (x + 24).toFloat(), (y + 72).toFloat())
        g.text(br.name.uppercase(), x + 44, y + 64, Gfx.INK, outline = 0, shadow = false)
        g.text("${br.skills.sumOf { world.rank(it.id) }} POINTS IN THIS BRANCH", x + 44, y + 75, Gfx.MUTED, outline = 0, shadow = false)
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
            lines.take(2).forEachIndexed { li, l -> g.text(l, x + 46, ry + 16 + li * 10, Gfx.MUTED, outline = 0, shadow = false) }
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
            if (game.assets.has(portrait)) g.spriteScaled(portrait, (x + 28).toFloat(), (ry + 20).toFloat(), 0.74f, 0.74f, if (locked) 90 else 255)
            val tx = x + 48
            val bx = x + pw - 72
            if (ws == null) {
                g.text(d.name.uppercase(), tx, ry + 4, if (locked) Gfx.GREY else Gfx.INK, outline = 0, shadow = false)
                if (locked) g.text("LOOKING FOR WORK AT L${d.unlockLevel}", tx, ry + 16, Gfx.MUTED, outline = 0, shadow = false)
                else {
                    g.text("WANTS ${d.hireCost} COINS", tx, ry + 16, Gfx.MUTED, outline = 0, shadow = false)
                    if (ui.button("hire-${d.id}", bx, ry + 11, 62, 16, "HIRE", style = "green", enabled = world.state.coins >= d.hireCost)) world.hire(d.id)
                }
                return@forEachIndexed
            }
            g.text("${ws.name.uppercase()}  LV ${ws.level}", tx, ry + 4, Gfx.INK, outline = 0, shadow = false)
            if (ws.hungry) g.text("HUNGRY - NEEDS SILO CROPS", tx, ry + 15, Gfx.RED, outline = 0, shadow = false)
            else g.text("${workerPlotsPerMinute(ws.level)} PLOTS/MIN, EATS ${workerAppetite(ws.level)}/DAY", tx, ry + 15, Gfx.MUTED, outline = 0, shadow = false)
            g.text(if (ws.field >= 0) "WORKS FIELD ${FieldLayout.name(ws.field)}" else "IDLE - PICK A FIELD", tx, ry + 26, Gfx.INK, outline = 0, shadow = false)
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
