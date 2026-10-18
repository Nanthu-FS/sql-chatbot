package com.ditherfarm.ui

import com.ditherfarm.Game
import com.ditherfarm.WorldListener
import com.ditherfarm.engine.Gfx
import com.ditherfarm.engine.Iso
import com.ditherfarm.engine.Lighting
import com.ditherfarm.engine.Noise
import com.ditherfarm.engine.Terrain
import com.ditherfarm.game.PlotState
import com.ditherfarm.game.Quality
import com.ditherfarm.game.Season
import com.ditherfarm.game.Weather
import com.ditherfarm.game.WorldEvent
import com.ditherfarm.game.build
import com.ditherfarm.game.buildingsOn
import com.ditherfarm.game.collect
import com.ditherfarm.game.isBuilt
import com.ditherfarm.game.jobProgress
import com.ditherfarm.game.trayCount
import kotlin.math.abs
import kotlin.math.sin
import com.ditherfarm.engine.Weather as Sky

/**
 * The farm: a painted island with the plot field, fixed buildings and decorations.
 * Tap a plot to plant, water, harvest or clear it; hold then slide to repeat the same
 * action across plots. Buildings and the nav bar open the storage, market and order panels.
 */
class FarmScreen(game: Game) : IsoMapScreen(game, groundSeed = 31), WorldListener {
    override val cols = MAP
    override val rows = MAP
    val chrome = Chrome(game, Nav.FARM)
    private val hud get() = chrome.hud
    override val hudTop get() = hud.topHeight
    override val hudBottom get() = hud.bottomHeight

    private val world get() = game.world

    // --- layout ---------------------------------------------------------------
    /** Tile of each plot index; plots unlock from the centre of the field outward. */
    private val plotTiles: List<Pair<Int, Int>> = run {
        val cells = ArrayList<Pair<Int, Int>>()
        for (r in FIELD_R0 until FIELD_R0 + FIELD_ROWS) for (c in FIELD_C0 until FIELD_C0 + FIELD_COLS) cells += c to r
        val cc = FIELD_C0 + (FIELD_COLS - 1) / 2f
        val cr = FIELD_R0 + (FIELD_ROWS - 1) / 2f
        cells.sortedWith(compareBy({ (c, r) -> abs(c - cc) * 0.9f + abs(r - cr) }, { it.second }, { it.first }))
    }
    private val plotAt = HashMap<Long, Int>().apply { plotTiles.forEachIndexed { i, (c, r) -> put(key(c, r), i) } }

    private enum class Building(val c: Int, val r: Int, val sprite: String, val label: String) {
        BARN(3, 6, "icons_big/nav_farm", "BARN"),
        SILO(3, 9, "icons_big/silo", "SILO"),
        MARKET(16, 6, "icons_big/market", "MARKET"),
        ORDERS(16, 9, "icons_big/orders", "ORDERS"),
        WELL(9, 3, "icons_big/water", "WELL"),
        WORKBENCH(4, 13, "icons_big/craft", "WORKBENCH"),
    }
    private val buildingAt = Building.entries.associateBy { key(it.c, it.r) }

    /** Fixed slots for farm machines, assigned in unlock order. */
    private val slotTiles = listOf(6 to 14, 8 to 15, 10 to 15, 12 to 15, 14 to 14, 17 to 11, 2 to 12, 12 to 3, 15 to 2, 6 to 3, 18 to 4, 2 to 3)
    private val machines get() = world.buildingsOn("farm").filter { !it.prebuilt }
    private fun machineAt(c: Int, r: Int) = machines.withIndex().firstOrNull { (i, _) -> slotTiles.getOrNull(i) == (c to r) }?.value

    private class Prop(val sprite: String, val x: Float, val y: Float, val sway: Boolean)
    private val props = ArrayList<Prop>()
    private val lanterns = listOf(FIELD_C0 - 1 to FIELD_R0 - 1, FIELD_C0 + FIELD_COLS to FIELD_R0 + FIELD_ROWS, 4 to 7, 16 to 7)

    // --- interaction state -----------------------------------------------------
    private var trayOpen = false
    private var seed: String? = null
    private var paintAction: Action? = null
    private var infoPlot = -1
    private var infoTime = 0f
    private var flashPlot = -1
    private var flashTime = 0f

    private enum class Action { PLANT, WATER, HARVEST, CLEAR }

    override fun terrain(c: Int, r: Int): Terrain {
        val dx = c - 16; val dy = r - 16
        return when {
            dx * dx + dy * dy <= 4 -> Terrain.WATER
            dx * dx + dy * dy <= 8 -> Terrain.SAND
            c + r < 5 || c > 18 || r > 18 -> Terrain.FOREST
            isYard(c, r) -> Terrain.DIRT
            r == 13 && c in 2..17 -> Terrain.PATH
            (c == 4 || c == 15) && r in 5..13 -> Terrain.PATH
            c == 9 && r in 3..4 -> Terrain.PATH
            else -> Terrain.GRASS
        }
    }

    /** Dirt under owned plots and one tile around them; the rest of the field stays meadow. */
    private fun isYard(c: Int, r: Int): Boolean {
        for (dr in -1..1) for (dc in -1..1) {
            val i = plotAt[key(c + dc, r + dr)] ?: continue
            if (world.plot(i).owned) return true
        }
        return false
    }

    override fun seasonIndex() = world.time.season.ordinal
    override fun hour() = world.time.hour

    override fun enter() {
        super.enter()
        cam.maxZoom = 3
        centerOnTile(FIELD_C0 + FIELD_COLS / 2, FIELD_R0 + FIELD_ROWS / 2)
        weather.onThunder = { game.sfx.play("thunder") }
        scatterProps()
        hud.tip("harvest", "Hold a ripe crop, then slide across the others to harvest them all.")
    }

    private fun scatterProps() {
        props.clear()
        for (r in 0 until rows) for (c in 0 until cols) {
            if (key(c, r) in buildingAt || (c to r) in lanterns || (c to r) in slotTiles) continue
            if (plotAt.containsKey(key(c, r)) && world.plot(plotAt[key(c, r)]!!).owned) continue
            val t = terrain(c, r)
            val roll = Noise.rand(c, r, 91)
            val x = Iso.x(c, r) + (Noise.rand(c, r, 92) - 0.5f) * 14f
            val y = Iso.centerY(c, r) + (Noise.rand(c, r, 93) - 0.5f) * 6f
            val sprite = when (t) {
                Terrain.GRASS -> when {
                    roll < 0.12f -> "deco/tallgrass_a"
                    roll < 0.18f -> "deco/flowers_" + listOf("red", "white", "pink", "blue", "purple")[(roll * 1000).toInt() % 5]
                    roll < 0.20f -> "deco/bush"
                    roll < 0.215f -> "deco/rock"
                    else -> null
                }
                Terrain.FOREST -> when {
                    roll < 0.35f -> "deco/bush"
                    roll < 0.45f -> "deco/bush_berry"
                    roll < 0.55f -> "deco/mushrooms"
                    roll < 0.62f -> "deco/stump"
                    roll < 0.66f -> "deco/log"
                    else -> "deco/tallgrass_a"
                }
                Terrain.SAND -> if (roll < 0.3f) "deco/reeds" else null
                else -> null
            } ?: continue
            props += Prop(sprite, x, y, sprite == "deco/tallgrass_a")
        }
        for ((c, r) in lanterns) props += Prop("deco/lantern", Iso.x(c, r).toFloat(), Iso.centerY(c, r).toFloat(), false)
        // Fence along the back edges of the field yard.
        for (c in FIELD_C0 - 1..FIELD_C0 + FIELD_COLS) props += Prop("deco/fence_r", Iso.x(c, FIELD_R0 - 1).toFloat(), Iso.y(c, FIELD_R0 - 1).toFloat(), false)
        for (r in FIELD_R0 - 1..FIELD_R0 + FIELD_ROWS) props += Prop("deco/fence_l", Iso.x(FIELD_C0 - 1, r).toFloat(), Iso.y(FIELD_C0 - 1, r).toFloat(), false)
    }

    override fun update(dt: Float) {
        super.update(dt)
        chrome.update(dt)
        if (infoTime > 0) infoTime -= dt
        if (flashTime > 0) flashTime -= dt
        weather.kind = when (world.state.weather) {
            Weather.SUNNY -> if (world.time.season == Season.FALL) Sky.Kind.LEAVES else Sky.Kind.CLEAR
            Weather.RAIN -> Sky.Kind.RAIN
            Weather.STORM -> Sky.Kind.STORM
            Weather.SNOW, Weather.BLIZZARD -> Sky.Kind.SNOW
        }
    }

    // --- drawing ---------------------------------------------------------------
    override fun drawGroundLayer(g: Gfx) {
        for (r in 0 until rows) for (c in 0 until cols) {
            if (terrain(c, r) == Terrain.WATER) {
                val phase = (time * 2f + c * 0.7f + r * 1.3f) % 3f
                if (phase < 0.6f) g.rect(Iso.x(c, r) - 3 + (phase * 8).toInt(), Iso.centerY(c, r), Iso.x(c, r) + (phase * 8).toInt(), Iso.centerY(c, r) + 1, Gfx.WHITE)
            }
        }
        plotTiles.forEachIndexed { i, (c, r) ->
            val p = world.plot(i)
            if (p.owned) g.sprite("tiles/shadow", Iso.x(c, r) + 2f, Iso.y(c, r) + 3f)
            else if (i == world.ownedPlots && world.nextPlotPrice() != null) {
                val pulse = (sin(time * 4f) * 40 + 120).toInt()
                g.sprite("tiles/cursor", Iso.x(c, r).toFloat(), Iso.y(c, r).toFloat(), pulse)
            }
        }
    }

    override fun collect(list: DrawList) {
        val winter = world.time.season == Season.WINTER
        for (p in props) {
            if (winter && (p.sprite.startsWith("deco/flowers") || p.sprite == "deco/mushrooms")) continue
            if (isYardWorld(p.x, p.y)) continue
            var sprite = if (p.sway && sin(time * 2.2f + p.x * 0.15f) > 0.3f) "deco/tallgrass_b" else p.sprite
            if (winter && game.assets.has("${sprite}_snow")) sprite = "${sprite}_snow"
            list.add(p.y + p.x * 0.001f) { game.gfx.sprite(sprite, p.x, p.y) }
        }
        for (b in Building.entries) {
            val x = Iso.x(b.c, b.r).toFloat()
            val y = Iso.centerY(b.c, b.r).toFloat()
            list.add(y) {
                game.gfx.sprite("tiles/shadow", x, y - 6)
                val bob = if (b == Building.ORDERS && world.state.orders.indices.any { world.canDeliver(it) }) sin(time * 6f) * 1.5f else 0f
                game.gfx.sprite(b.sprite, x, y - 12 + bob)
            }
        }
        machines.forEachIndexed { i, def ->
            val (c, r) = slotTiles.getOrNull(i) ?: return@forEachIndexed
            val x = Iso.x(c, r).toFloat()
            val y = Iso.centerY(c, r).toFloat()
            val built = world.isBuilt(def.id)
            if (!built && def.unlockLevel > world.level) return@forEachIndexed
            list.add(y) {
                val g = game.gfx
                if (built) {
                    g.sprite("tiles/shadow", x, y - 6)
                    g.sprite(chrome.panels.buildingSprite(def.id), x, y - 12)
                    val ready = world.trayCount(def.id)
                    if (ready > 0) g.sprite("icons/ready", x + 10, y - 30 + sin(time * 5f) * 2f)
                    else if (world.jobProgress(def.id) > 0f) g.sprite("fx/sparkle", x + 10, y - 26, (120 + sin(time * 3f) * 80).toInt())
                } else {
                    g.sprite("tiles/cursor", Iso.x(c, r).toFloat(), Iso.y(c, r).toFloat(), (sin(time * 4f) * 50 + 140).toInt())
                    g.sprite("icons_big/build", x, y - 10 + sin(time * 3f) * 1.5f, 200)
                }
            }
        }
        plotTiles.forEachIndexed { i, (c, r) ->
            val p = world.plot(i)
            if (!p.owned) return@forEachIndexed
            val x = Iso.x(c, r).toFloat()
            val top = Iso.y(c, r).toFloat()
            val cy = Iso.centerY(c, r).toFloat()
            list.add(top + 0.5f) { drawPlot(i, p, x, top, cy) }
        }
    }

    private val pickTmp = IntArray(2)
    /** True if a prop at world (x, y) now sits on owned farmland (bought after it was scattered). */
    private fun isYardWorld(x: Float, y: Float): Boolean {
        Iso.pick(x, y, pickTmp)
        val i = plotAt[key(pickTmp[0], pickTmp[1])] ?: return false
        return world.plot(i).owned
    }

    private fun drawPlot(i: Int, p: PlotState, x: Float, top: Float, cy: Float) {
        val g = game.gfx
        g.sprite(if (p.watered && p.crop != null) "tiles/soil_wet" else "tiles/soil_dry", x, top)
        if (i == flashPlot && flashTime > 0) g.sprite("tiles/cursor_ok", x, top)
        val crop = p.crop
        if (crop != null) {
            val sprite = if (p.needsClearing) "crops/${crop}_dead" else "crops/${crop}_${world.stage(p)}"
            val pop = if (i == flashPlot && flashTime > 0) 1f + flashTime * 0.4f else 1f
            g.spriteScaled(sprite, x, cy, pop, 2f - pop)
            val bob = sin(time * 5f + i) * 1.5f
            when {
                world.isReady(p) -> if ((time * 2 + i * 0.37f) % 3f < 1.6f) g.sprite("fx/sparkle", x + 7, cy - 20 + bob)
                !p.needsClearing && !p.watered -> g.sprite("fx/drop", x + 8, cy - 12 + bob)
            }
            if (p.fertSilver > 0 && !world.isReady(p)) g.sprite("fx/star", x - 8, cy - 4)
        }
    }

    override fun worldLights(out: MutableList<Lighting.Light>) {
        for ((c, r) in lanterns) out += Lighting.Light(Iso.x(c, r).toFloat(), Iso.centerY(c, r).toFloat() - 8, 28)
    }

    override fun drawHud(g: Gfx) {
        if (infoTime > 0 && infoPlot >= 0) drawInfo(g)
        chrome.drawTop()
        if (trayOpen && !chrome.isOpen) drawSeedTray(g)
        chrome.drawBottom(tipLift = if (trayOpen) 2 * 34 + 22 else 0)
    }

    private fun drawSeedTray(g: Gfx) {
        val w = game.engine.w
        val size = 32
        val perRow = 6
        val crops = world.data.crops.values.sortedBy { it.unlockLevel }
        val trayH = 2 * (size + 2) + 20
        val y = game.engine.h - game.engine.safeBottom - hud.bottomHeight - trayH - 2
        val x0 = (w - perRow * (size + 2)) / 2
        ui.panel(2, y, w - 4, trayH, dark = true)
        g.text(seed?.let { "PLANT ${world.data.name(it).uppercase()}" } ?: "PICK A SEED", 10, y + 5, Gfx.GOLD)
        if (ui.button("tray-close", w - 22, y + 3, 16, 12, null, "ui/x", "red")) { trayOpen = false; return }
        crops.forEachIndexed { i, def ->
            val sx = x0 + (i % perRow) * (size + 2)
            val sy = y + 16 + (i / perRow) * (size + 2)
            val locked = def.unlockLevel > world.level
            val inSeason = !world.outOfSeason(def.id)
            if (ui.slot("seed-${def.id}", sx, sy, if (locked) null else "items_big/seed_${def.id}", -1, seed == def.id, size)) {
                when {
                    locked -> hud.toast("Unlocks at level ${def.unlockLevel}")
                    !inSeason -> hud.toast("${world.data.name(def.id)} grows in ${def.seasons.joinToString { it.label }}")
                    else -> { seed = def.id; plantPending() }
                }
            }
            if (locked) {
                g.sprite("icons/lock", (sx + size / 2).toFloat(), (sy + size / 2).toFloat())
                g.text("L${def.unlockLevel}", sx + size / 2, sy + size - 9, Gfx.WHITE, align = 0)
            } else {
                if (!inSeason) g.sprite("icons/cross", (sx + size - 6).toFloat(), (sy + 6).toFloat())
                val owned = world.count(def.seedItem)
                g.text(if (owned > 0) "x$owned" else "${def.seedPrice}c", sx + size - 2, sy + size - 9, if (owned > 0) Gfx.WHITE else Gfx.GOLD, align = 1)
            }
        }
    }

    private var pendingPlant = -1
    private fun plantPending() {
        val i = pendingPlant
        val s = seed ?: return
        if (i >= 0 && world.plot(i).empty && world.plot(i).owned) {
            if (world.plant(i, s)) pendingPlant = -1
        }
    }

    private fun drawInfo(g: Gfx) {
        val p = world.plot(infoPlot)
        val crop = p.crop ?: return
        val (c, r) = plotTiles[infoPlot]
        val bx = toBufferX(Iso.x(c, r).toFloat()).toInt()
        val by = toBufferY(Iso.y(c, r).toFloat()).toInt() - 30
        val secs = world.secondsToReady(p).toInt()
        val text = "${world.data.name(crop).uppercase()} %d:%02d".format(secs / 60, secs % 60)
        val tw = g.textWidth(text) + 12
        g.slice9("ui/bubble", bx - tw / 2, by, bx + tw / 2, by + 14, 5)
        g.sprite("ui/bubble_tail", bx.toFloat(), (by + 13).toFloat())
        g.text(text, bx, by + 3, Gfx.INK, align = 0, outline = 0, shadow = false)
    }

    // --- input -------------------------------------------------------------------
    private fun actionFor(i: Int): Action? {
        val p = world.plot(i)
        return when {
            !p.owned -> null
            p.needsClearing -> Action.CLEAR
            world.isReady(p) -> Action.HARVEST
            p.crop != null && !p.watered -> Action.WATER
            p.empty -> Action.PLANT
            else -> null
        }
    }

    private fun perform(i: Int, a: Action): Boolean = when (a) {
        Action.HARVEST -> world.harvest(i)
        Action.WATER -> world.water(i)
        Action.CLEAR -> world.clearPlot(i)
        Action.PLANT -> seed?.let { world.plant(i, it) } ?: false
    }

    override fun onTileTap(c: Int, r: Int) {
        buildingAt[key(c, r)]?.let { b ->
            when (b) {
                Building.BARN, Building.SILO -> chrome.open(Chrome.Panel.BARN)
                Building.MARKET -> chrome.open(Chrome.Panel.MARKET)
                Building.ORDERS -> chrome.open(Chrome.Panel.ORDERS)
                Building.WELL -> hud.toast("The well keeps your can full")
                Building.WORKBENCH -> if (world.trayCount("workbench") > 0) world.collect("workbench") else chrome.open(Chrome.Panel.WORKBENCH)
            }
            return
        }
        machineAt(c, r)?.let { def ->
            when {
                world.isBuilt(def.id) -> if (world.trayCount(def.id) > 0) world.collect(def.id) else chrome.open(Chrome.Panel.MACHINE, def.id)
                def.unlockLevel <= world.level -> chrome.open(Chrome.Panel.BUILD, def.id)
            }
            return
        }
        val i = plotAt[key(c, r)] ?: run { trayOpen = false; return }
        val p = world.plot(i)
        if (!p.owned) {
            if (i == world.ownedPlots) {
                val price = world.nextPlotPrice()
                if (price == null) hud.toast("Reach level ${world.level + 1} to buy more land")
                else chrome.confirm("BUY THIS LAND?", "Plot ${i + 1} costs $price coins.", "BUY") {
                    if (world.buyPlot(i)) { game.sfx.play("buy"); game.haptics.heavy() }
                }
            }
            return
        }
        when (val a = actionFor(i)) {
            Action.PLANT -> {
                if (trayOpen && seed != null) perform(i, a) else { pendingPlant = i; trayOpen = true; game.sfx.play("open") }
            }
            null -> { infoPlot = i; infoTime = 2.2f; game.sfx.play("click", 0.4f) }
            else -> perform(i, a)
        }
    }

    override fun onTileLongPress(c: Int, r: Int) {
        val i = plotAt[key(c, r)] ?: return
        paintAction = actionFor(i)?.takeIf { it != Action.PLANT || seed != null }
        if (paintAction != null) game.haptics.click()
    }

    override fun onTilePaint(c: Int, r: Int) {
        val a = paintAction ?: return
        val i = plotAt[key(c, r)] ?: return
        if (actionFor(i) == a) perform(i, a)
    }

    override fun onPaintEnd() { paintAction = null }

    fun toast(text: String) = hud.toast(text)

    fun recenter() = centerOnTile(FIELD_C0 + FIELD_COLS / 2, FIELD_R0 + FIELD_ROWS / 2)

    /** Map tile of plot [i]. */
    fun plotTile(i: Int) = plotTiles[i]

    /** Opens a panel by name; used by screenshot tests. */
    fun showPanel(name: String) {
        when (name) {
            "barn" -> chrome.open(Chrome.Panel.BARN); "market" -> chrome.open(Chrome.Panel.MARKET); "orders" -> chrome.open(Chrome.Panel.ORDERS)
            "settings" -> chrome.open(Chrome.Panel.SETTINGS); "workbench" -> chrome.open(Chrome.Panel.WORKBENCH)
            "tray" -> { trayOpen = true; seed = "carrot" }
            else -> if (name.startsWith("machine:")) chrome.open(Chrome.Panel.MACHINE, name.removePrefix("machine:"))
            else if (name.startsWith("build:")) chrome.open(Chrome.Panel.BUILD, name.removePrefix("build:"))
        }
    }

    /** Closes the open panel or tray; false if nothing was open (back leaves the app). */
    fun closePanel(): Boolean {
        if (chrome.close()) return true
        if (trayOpen) { trayOpen = false; return true }
        return false
    }

    // --- simulation events -------------------------------------------------------
    private fun plotCenter(i: Int): Pair<Float, Float> {
        val (c, r) = plotTiles[i]
        return Iso.x(c, r).toFloat() to Iso.centerY(c, r).toFloat()
    }

    override fun onWorldEvent(e: WorldEvent) {
        if (chrome.onEvent(e)) return
        when (e) {
            is WorldEvent.Harvested -> {
                val (x, y) = plotCenter(e.plot)
                game.sfx.play("harvest"); game.haptics.tick()
                fx.burst("items/${e.crop}", x, y - 8, 3, speed = 50f)
                fx.floatText("+${e.qty}", x, y - 26, Gfx.WHITE, icon = "items/${e.crop}")
                if (e.quality != Quality.NORMAL) fx.rise(e.quality.icon!!, x + 10, y - 20)
                flash(e.plot)
                hud.tip("plant", "Tap an empty plot to plant seeds. Watered crops grow faster.")
            }
            is WorldEvent.Planted -> {
                val (x, y) = plotCenter(e.plot)
                game.sfx.play("plant"); fx.burst("fx/dust", x, y, 4, speed = 25f, gravity = 30f, life = 0.4f)
                flash(e.plot)
                if (!world.plot(e.plot).watered) hud.tip("water", "Dry crops grow slowly. Tap a crop with a drop to water it.")
            }
            is WorldEvent.Watered -> {
                val (x, y) = plotCenter(e.plot)
                game.sfx.play("water"); fx.burst("fx/drop", x, y - 10, 6, speed = 35f, gravity = 120f, life = 0.5f)
            }
            is WorldEvent.Cleared -> {
                val (x, y) = plotCenter(e.plot)
                game.sfx.play("pickup"); fx.burst("fx/leaf", x, y - 6, 5, speed = 40f)
            }
            is WorldEvent.Ripened -> { val (x, y) = plotCenter(e.plot); fx.rise("fx/sparkle", x, y - 14) }
            is WorldEvent.PlotBought -> {
                val (x, y) = plotCenter(e.plot)
                fx.burst("fx/coin", x, y - 6, 8, speed = 60f); fx.floatText("-${e.price}", x, y - 24, Gfx.GOLD, icon = "icons/coin")
                invalidateGround()
                hud.tip("land", "Every new plot costs a bit more than the last.")
            }
            is WorldEvent.XpGained -> {}
            is WorldEvent.Blocked -> { val (x, y) = plotCenter(e.plot); fx.floatText(e.reason.uppercase(), x, y - 24, Gfx.RED) }
            is WorldEvent.SeasonChanged -> invalidateGround()
            is WorldEvent.Storm -> {
                fx.shake(3f)
                e.scorched.forEach { val (x, y) = plotCenter(it); fx.burst("fx/zap", x, y - 10, 4, speed = 40f) }
            }
            is WorldEvent.Sold -> {}
            is WorldEvent.Ate -> {}
            else -> {}
        }
    }

    private fun flash(i: Int) { flashPlot = i; flashTime = 0.25f }

    companion object {
        const val MAP = 20
        const val FIELD_C0 = 6
        const val FIELD_R0 = 6
        const val FIELD_COLS = 8
        const val FIELD_ROWS = 6
        private fun key(c: Int, r: Int) = c.toLong() shl 32 or (r.toLong() and 0xFFFFFFFF)
    }
}
