package com.ditherfarm.ui

import com.ditherfarm.Game
import com.ditherfarm.WorldListener
import com.ditherfarm.engine.Gfx
import com.ditherfarm.engine.Iso
import com.ditherfarm.engine.Lighting
import com.ditherfarm.engine.Noise
import com.ditherfarm.engine.Terrain
import android.graphics.Paint
import android.graphics.Path
import com.ditherfarm.game.FieldLayout
import com.ditherfarm.game.Fixture
import com.ditherfarm.game.PlotState
import com.ditherfarm.game.Quality
import com.ditherfarm.game.Season
import com.ditherfarm.game.Weather
import com.ditherfarm.game.WorldEvent
import com.ditherfarm.game.blockOwned
import com.ditherfarm.game.build
import com.ditherfarm.game.buyBlock
import com.ditherfarm.game.buildingsOn
import com.ditherfarm.game.collect
import com.ditherfarm.game.fixtureAt
import com.ditherfarm.game.greenhouseOpen
import com.ditherfarm.game.merchantHere
import com.ditherfarm.game.pickUpFixture
import com.ditherfarm.game.placeBlocker
import com.ditherfarm.game.placeFixture
import com.ditherfarm.game.isBuilt
import com.ditherfarm.game.jobProgress
import com.ditherfarm.game.trayCount
import com.ditherfarm.game.FARMHANDS
import com.ditherfarm.game.TRUCK_CAP
import com.ditherfarm.game.sendTruck
import com.ditherfarm.game.truckLoad
import com.ditherfarm.game.truckSecondsLeft
import com.ditherfarm.game.truckValue
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import com.ditherfarm.engine.Weather as Sky

/**
 * The farm: a 44x44 world (see [FarmMap]) with eight fenced field blocks along the main
 * road, the farmstead, river, village and a forest wall. Tap a plot to plant, water,
 * harvest or clear it; hold then slide to repeat the same action across plots. Buildings
 * and the nav bar open the storage, market and order panels.
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
    /** Tile of each plot index: the field (unlocking from its centre outward), then the greenhouse. */
    private val plotTiles: List<Pair<Int, Int>> = (0 until FieldLayout.FIELD).map { i ->
        val (bc, br) = FarmMap.BLOCKS[FieldLayout.blockOf(i)]
        val (c, r) = FieldLayout.cell(i)
        bc + c to br + r
    } + (0 until FieldLayout.GREENHOUSE).map { k -> GH_C0 + k % FieldLayout.GH_COLS to GH_R0 + k / FieldLayout.GH_COLS }
    private val plotAt = HashMap<Long, Int>().apply { plotTiles.forEachIndexed { i, (c, r) -> put(key(c, r), i) } }

    private enum class Building(val c: Int, val r: Int, val sprite: String, val label: String) {
        BARN(FarmMap.BARN.first, FarmMap.BARN.second, "icons_big/nav_farm", "BARN"),
        SILO(FarmMap.SILO.first, FarmMap.SILO.second, "icons_big/silo", "SILO"),
        MARKET(FarmMap.MARKET.first, FarmMap.MARKET.second, "icons_big/market", "MARKET"),
        ORDERS(FarmMap.ORDERS.first, FarmMap.ORDERS.second, "icons_big/orders", "ORDERS"),
        WELL(FarmMap.WELL.first, FarmMap.WELL.second, "icons_big/water", "WELL"),
        WORKBENCH(FarmMap.WORKBENCH.first, FarmMap.WORKBENCH.second, "icons_big/craft", "WORKBENCH"),
    }
    private val buildingAt = Building.entries.associateBy { key(it.c, it.r) }

    /** Fixed slots for farm machines, assigned in unlock order. */
    private val slotTiles = FarmMap.SLOTS
    private val machines get() = world.buildingsOn("farm").filter { !it.prebuilt && it.id != "greenhouse" }
    private fun machineAt(c: Int, r: Int) = machines.withIndex().firstOrNull { (i, _) -> slotTiles.getOrNull(i) == (c to r) }?.value

    /** A scattered decoration; [tree] props swap to their fall and snow art by season. */
    private class Prop(val sprite: String, val x: Float, val y: Float, val sway: Boolean, val tree: Boolean = false)
    private val props = ArrayList<Prop>()
    private val lanterns = FarmMap.LANTERNS
    private val waterTiles = ArrayList<Pair<Int, Int>>()
    private val bridge: Pair<Float, Float>? by lazy {
        val tiles = (0 until cols).flatMap { c -> (0 until rows).filter { r -> FarmMap.isBridge(c, r) }.map { r -> c to r } }
        if (tiles.isEmpty()) null else tiles.map { (c, r) -> Iso.x(c, r).toFloat() }.average().toFloat() to tiles.map { (c, r) -> Iso.centerY(c, r).toFloat() }.average().toFloat()
    }

    // --- interaction state -----------------------------------------------------
    private var trayOpen = false
    private var seed: String? = null
    private var paintAction: Action? = null
    private var infoPlot = -1
    private var infoTime = 0f
    private var flashPlot = -1
    private var flashTime = 0f
    /** Fixture item being placed, and the corner picked by the first tap (the second tap confirms). */
    private var placing: String? = null
    private var placeVertex = -1

    private enum class Action { PLANT, WATER, HARVEST, CLEAR }

    /** Farmhands, crates, harvester and truck. */
    private val crew = FarmCrew(game, this)
    /** Plot of the farmhand job just announced: its result events stay quiet (the crew shows them). */
    private var workerPlot = -1

    override fun terrain(c: Int, r: Int): Terrain = FarmMap.terrain(c, r) { cc, rr -> isYard(cc, rr) }

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
        cam.minZoom = 2
        cam.maxZoom = 3
        // Keep the whole view on painted ground: inside the map diamond, one tile in from its edge.
        val n = FarmMap.SIZE
        cam.setDiamond(0f, n * Iso.TH / 2f, (n - 2) * Iso.TW / 2f, (n - 2) * Iso.TH / 2f)
        recenter()
        weather.onThunder = { game.sfx.play("thunder") }
        scatterProps()
        hud.tip("harvest", "Hold a ripe crop, then slide across the others to harvest them all.")
    }

    private val treeKinds = listOf("pine", "oak", "birch", "maple", "pine", "oak")

    private fun scatterProps() {
        props.clear()
        waterTiles.clear()
        val reserved = HashSet<Long>()
        listOf(FarmMap.BUNKHOUSE, FarmMap.TRUCK, FarmMap.MERCHANT, FarmMap.SIGN_FOREST, FarmMap.SIGN_MINE, FarmCrew.HARVESTER_PARK).forEach { reserved += key(it.first, it.second) }
        for (b in 0 until FieldLayout.BLOCKS) FarmCrew.crateTile(b).let { reserved += key(it.first, it.second) }
        for (dc in 0..1) for (dr in 0..1) reserved += key(FarmMap.TRUCK.first + dc, FarmMap.TRUCK.second + dr)
        FarmMap.COTTAGES.forEach { reserved += key(it.first, it.second) }
        for (r in 0 until rows) for (c in 0 until cols) {
            if (FarmMap.isRiver(c, r)) waterTiles += c to r
            if (key(c, r) in buildingAt || (c to r) in lanterns || (c to r) in slotTiles || key(c, r) in reserved) continue
            if (plotAt[key(c, r)]?.let { world.plot(it).owned } == true || FarmMap.isRoad(c, r) || FarmMap.inPasture(c, r)) continue
            if (FarmMap.isWall(c, r)) {
                // Dense forest wall: a tree on most tiles, bushes between.
                val roll = Noise.rand(c, r, 71)
                val x = Iso.x(c, r) + (Noise.rand(c, r, 72) - 0.5f) * 16f
                val y = Iso.centerY(c, r) + (Noise.rand(c, r, 73) - 0.5f) * 6f
                if (roll < 0.8f) props += Prop("trees/" + treeKinds[(Noise.rand(c, r, 74) * treeKinds.size).toInt().coerceIn(0, treeKinds.size - 1)], x, y, false, tree = true)
                else props += Prop("deco/bush", x, y, false)
                continue
            }
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
        // Fences along the back edges of every field block.
        for ((bc, br) in FarmMap.BLOCKS) {
            for (c in bc - 1 until bc + FieldLayout.SIDE) props += Prop("deco/fence_r", Iso.x(c, br - 1).toFloat(), Iso.y(c, br - 1).toFloat(), false)
            for (r in br until br + FieldLayout.SIDE) props += Prop("deco/fence_l", Iso.x(bc - 1, r).toFloat(), Iso.y(bc - 1, r).toFloat(), false)
        }
        // Pasture: fenced, with hay and a trough, waiting for animals.
        for (c in FarmMap.PASTURE_C) {
            props += Prop("deco/fence_r", Iso.x(c, FarmMap.PASTURE_R.first).toFloat(), Iso.y(c, FarmMap.PASTURE_R.first).toFloat(), false)
            props += Prop("deco/fence_r", Iso.x(c, FarmMap.PASTURE_R.last + 1).toFloat(), Iso.y(c, FarmMap.PASTURE_R.last + 1).toFloat(), false)
        }
        for (r in FarmMap.PASTURE_R) {
            props += Prop("deco/fence_l", Iso.x(FarmMap.PASTURE_C.first, r).toFloat(), Iso.y(FarmMap.PASTURE_C.first, r).toFloat(), false)
            props += Prop("deco/fence_l", Iso.x(FarmMap.PASTURE_C.last + 1, r).toFloat(), Iso.y(FarmMap.PASTURE_C.last + 1, r).toFloat(), false)
        }
        fun at(t: Pair<Int, Int>, sprite: String, fallback: String) =
            Prop(if (game.assets.has(sprite)) sprite else fallback, Iso.x(t.first, t.second).toFloat(), Iso.centerY(t.first, t.second).toFloat(), false)
        props += at(34 to 13, "deco/hay_bale", "deco/bush")
        props += at(36 to 15, "deco/trough", "deco/log")
        props += at(FarmMap.SIGN_FOREST, "deco/sign_forest", "trees/signpost")
        props += at(FarmMap.SIGN_MINE, "deco/sign_mine", "trees/signpost")
        FarmMap.COTTAGES.forEachIndexed { k, t -> props += at(t, "buildings/cottage_" + "abc"[k % 3], "buildings/well") }
        props += at(FarmMap.BUNKHOUSE, "buildings/cottage_c", "buildings/well")
    }

    override fun update(dt: Float) {
        super.update(dt)
        chrome.update(dt)
        crew.update(dt)
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
        for ((c, r) in waterTiles) {
            if (!inView(Iso.x(c, r).toFloat(), Iso.y(c, r).toFloat())) continue
            val phase = (time * 2f + c * 0.7f + r * 1.3f) % 3f
            if (phase < 0.6f) g.rect(Iso.x(c, r) - 3 + (phase * 8).toInt(), Iso.centerY(c, r), Iso.x(c, r) + (phase * 8).toInt(), Iso.centerY(c, r) + 1, Gfx.WHITE)
        }
        plotTiles.forEachIndexed { i, (c, r) ->
            val x = Iso.x(c, r).toFloat(); val y = Iso.y(c, r).toFloat()
            if (!inView(x, y)) return@forEachIndexed
            val p = world.plot(i)
            if (p.owned) {
                // Soil is ground: drawn before anything stands on it, so farmhands never sink into it.
                g.sprite("tiles/shadow", x + 2f, y + 3f)
                g.sprite(if (p.watered && p.crop != null) "tiles/soil_wet" else "tiles/soil_dry", x, y)
                if (i == flashPlot && flashTime > 0) g.sprite("tiles/cursor_ok", x, y)
            } else if (FieldLayout.isGreenhouse(i)) {
                if (world.level >= GREENHOUSE_LEVEL) g.sprite("tiles/cursor", x, y, (sin(time * 3f) * 30 + 70).toInt())
            } else if (world.level >= FieldLayout.UNLOCK[FieldLayout.blockOf(i)]) {
                // A field for sale you can afford to unlock: pulse its outline.
                g.sprite("tiles/cursor", x, y, (sin(time * 3f + FieldLayout.blockOf(i)) * 30 + 80).toInt())
            }
        }
        drawPlacement(g)
    }

    private fun drawPlacement(g: Gfx) {
        val item = placing ?: return
        val fixture = Fixture.of(item) ?: return
        val pulse = (sin(time * 5f) * 50 + 205).toInt()
        if (placeVertex >= 0) {
            for (i in 0 until FieldLayout.FIELD) if (FieldLayout.covers(placeVertex, fixture.radius, i)) {
                val (c, r) = plotTiles[i]
                g.sprite("tiles/cursor_ok", Iso.x(c, r).toFloat(), Iso.y(c, r).toFloat(), pulse)
            }
        }
        for (b in 0 until FieldLayout.BLOCKS) {
            if (!world.blockOwned(b)) continue
            for (vr in 0 until FieldLayout.VSIDE) for (vc in 0 until FieldLayout.VSIDE) {
                val v = FieldLayout.vertex(b, vc, vr)
                if (world.state.fixtures.containsKey(v)) continue
                val (x, y) = vertexPos(v)
                g.rect(x.toInt() - 1, y.toInt() - 1, x.toInt() + 1, y.toInt() + 1, Gfx.WHITE)
            }
        }
    }

    private fun fixtureSprite(item: String): String = when {
        game.assets.has("deco/$item") -> "deco/$item"
        game.assets.has("items/$item") -> "items/$item"
        else -> "icons/sprinkler"
    }

    private fun vertexPos(v: Int): Pair<Float, Float> {
        val (bc, br) = FarmMap.BLOCKS[FieldLayout.vertexBlock(v)]
        val (vc, vr) = FieldLayout.vertexCell(v)
        return Iso.x(bc + vc, br + vr).toFloat() to Iso.y(bc + vc, br + vr).toFloat()
    }

    // --- greenhouse ----------------------------------------------------------------
    private val glassFill = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL; color = 0x3C2CE8F5 }
    private val glassRoof = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL; color = 0x50C0CBDC }
    private val frame = Paint().apply { isAntiAlias = false; style = Paint.Style.STROKE; strokeWidth = 1f; color = 0xFFE8EEF4.toInt() }
    private val post = Paint().apply { isAntiAlias = false; style = Paint.Style.STROKE; strokeWidth = 2f; color = 0xFF5A6988.toInt() }
    private val path = Path()

    private fun ghCorner(c: Int, r: Int) = floatArrayOf(Iso.x(c, r).toFloat(), Iso.y(c, r).toFloat())

    private fun quad(paint: Paint, vararg p: FloatArray, lift: FloatArray) {
        path.reset()
        p.forEachIndexed { k, v -> val y = v[1] - lift[k]; if (k == 0) path.moveTo(v[0], y) else path.lineTo(v[0], y) }
        path.close()
        game.gfx.canvas.drawPath(path, paint)
    }

    /** Glass walls and a gabled roof over the greenhouse plots; [front] picks the half drawn in front of the crops. */
    private fun drawGreenhouse(front: Boolean) {
        val c0 = GH_C0; val r0 = GH_R0; val c1 = GH_C0 + FieldLayout.GH_COLS; val r1 = GH_R0 + FieldLayout.GH_ROWS
        val t = ghCorner(c0, r0); val rr = ghCorner(c1, r0); val b = ghCorner(c1, r1); val l = ghCorner(c0, r1)
        val h = GH_WALL; val ridge = GH_WALL + 10f
        val midBack = floatArrayOf((t[0] + l[0]) / 2, (t[1] + l[1]) / 2)
        val midFront = floatArrayOf((rr[0] + b[0]) / 2, (rr[1] + b[1]) / 2)
        val c = game.gfx.canvas
        fun post(p: FloatArray) = c.drawLine(p[0], p[1], p[0], p[1] - h, this.post)
        fun mullions(a: FloatArray, z: FloatArray, n: Int) {
            for (k in 1 until n) {
                val x = a[0] + (z[0] - a[0]) * k / n; val y = a[1] + (z[1] - a[1]) * k / n
                c.drawLine(x, y, x, y - h, frame)
            }
        }
        if (!front) {
            quad(glassFill, l, t, t, l, lift = floatArrayOf(0f, 0f, h, h))
            quad(glassFill, t, rr, rr, t, lift = floatArrayOf(0f, 0f, h, h))
            quad(glassFill, l, t, midBack, lift = floatArrayOf(h, h, ridge))
            quad(glassRoof, t, rr, midFront, midBack, lift = floatArrayOf(h, h, ridge, ridge))
            mullions(l, t, FieldLayout.GH_ROWS); mullions(t, rr, FieldLayout.GH_COLS)
            post(l); post(t); post(rr)
        } else {
            quad(glassFill, l, b, b, l, lift = floatArrayOf(0f, 0f, h, h))
            quad(glassFill, b, rr, rr, b, lift = floatArrayOf(0f, 0f, h, h))
            quad(glassFill, rr, b, midFront, lift = floatArrayOf(h, h, ridge))
            quad(glassRoof, l, b, midFront, midBack, lift = floatArrayOf(h, h, ridge, ridge))
            mullions(l, b, FieldLayout.GH_COLS); mullions(b, rr, FieldLayout.GH_ROWS)
            post(b); post(l); post(rr)
            c.drawLine(l[0], l[1] - h, b[0], b[1] - h, frame)
            c.drawLine(b[0], b[1] - h, rr[0], rr[1] - h, frame)
            c.drawLine(midBack[0], midBack[1] - ridge, midFront[0], midFront[1] - ridge, frame)
            c.drawLine(l[0], l[1] - h, midBack[0], midBack[1] - ridge, frame)
            c.drawLine(b[0], b[1] - h, midFront[0], midFront[1] - ridge, frame)
            c.drawLine(rr[0], rr[1] - h, midFront[0], midFront[1] - ridge, frame)
        }
    }

    override fun collect(list: DrawList) {
        val winter = world.time.season == Season.WINTER
        if (world.greenhouseOpen) {
            list.add(Iso.y(GH_C0, GH_R0).toFloat()) { drawGreenhouse(front = false) }
            list.add(Iso.y(GH_C0 + FieldLayout.GH_COLS, GH_R0 + FieldLayout.GH_ROWS) + 0.5f) { drawGreenhouse(front = true) }
        } else if (world.level >= GREENHOUSE_LEVEL) {
            val cx = (Iso.x(GH_C0, GH_R0) + Iso.x(GH_C0 + FieldLayout.GH_COLS, GH_R0 + FieldLayout.GH_ROWS)) / 2f
            val cy = (Iso.y(GH_C0, GH_R0) + Iso.y(GH_C0 + FieldLayout.GH_COLS, GH_R0 + FieldLayout.GH_ROWS)) / 2f
            list.add(cy) { game.gfx.sprite("icons_big/build", cx, cy - 10 + sin(time * 3f) * 1.5f, 220) }
        }
        if (world.merchantHere()) {
            val (mc, mr) = MERCHANT_TILE
            val x = Iso.x(mc, mr).toFloat(); val y = Iso.centerY(mc, mr).toFloat()
            list.add(y) {
                if (game.assets.has("buildings/merchant_cart")) game.gfx.sprite("buildings/merchant_cart", x, y)
                else { game.gfx.sprite("tiles/shadow", x, y - 6); game.gfx.sprite("icons_big/merchant", x, y - 12) }
                game.gfx.sprite("icons/coin", x + 10, y - spriteTop("buildings/merchant_cart") - 2 + sin(time * 4f) * 2f)
            }
        }
        crew.collect(list)
        // The bunkhouse calls when a farmhand is looking for work.
        if (FARMHANDS.any { it.unlockLevel <= world.level && it.id !in world.state.workers }) {
            val (bc, br) = FarmMap.BUNKHOUSE
            val x = Iso.x(bc, br).toFloat(); val y = Iso.centerY(bc, br).toFloat()
            if (inView(x, y)) list.add(y + 1f) { game.gfx.sprite("icons/ready", x + 8, y - 40 + sin(time * 6f) * 1.5f) }
        }
        for ((v, item) in world.state.fixtures) {
            val (x, y) = vertexPos(v)
            list.add(y + 0.4f) { game.gfx.sprite(fixtureSprite(item), x, y) }
        }
        placing?.let { item ->
            if (placeVertex >= 0) {
                val (x, y) = vertexPos(placeVertex)
                // The ghost draws over everything so tall crops never hide it.
                list.add(Float.MAX_VALUE) { game.gfx.sprite(fixtureSprite(item), x, y, (sin(time * 6f) * 50 + 200).toInt()) }
            }
        }
        val fall = world.time.season == Season.FALL
        for (p in props) {
            if (!inView(p.x, p.y, 64f)) continue
            if (winter && (p.sprite.startsWith("deco/flowers") || p.sprite == "deco/mushrooms")) continue
            if (!p.tree && isYardWorld(p.x, p.y)) continue
            var sprite = if (p.sway && sin(time * 2.2f + p.x * 0.15f) > 0.3f) "deco/tallgrass_b" else p.sprite
            if (winter && game.assets.has("${sprite}_snow")) sprite = "${sprite}_snow"
            else if (p.tree && fall && game.assets.has("${sprite}_fall")) sprite = "${sprite}_fall"
            list.add(p.y + p.x * 0.001f) { game.gfx.sprite(sprite, p.x, p.y) }
        }
        bridge?.let { (x, y) -> if (inView(x, y)) list.add(y - 6f) { game.gfx.sprite(if (game.assets.has("deco/bridge")) "deco/bridge" else "deco/log", x, y) } }
        // Field signs: a letter on owned fields, a price on the ones for sale.
        for (b in 0 until FieldLayout.BLOCKS) {
            val (bc, br) = FarmMap.BLOCKS[b]
            val x = Iso.x(bc - 1, br - 1).toFloat(); val y = Iso.centerY(bc - 1, br - 1).toFloat()
            if (!inView(x, y)) continue
            list.add(y) {
                val g = game.gfx
                g.sprite(if (game.assets.has("deco/field_sign")) "deco/field_sign" else "trees/signpost", x, y)
                if (world.blockOwned(b)) g.text(FieldLayout.name(b), x.toInt(), (y - 15).toInt(), Gfx.WHITE, align = 0)
                else {
                    val locked = world.level < FieldLayout.UNLOCK[b]
                    g.text(if (locked) "L${FieldLayout.UNLOCK[b]}" else Ui.compact(FieldLayout.PRICE[b]), x.toInt(), (y - 26).toInt(), if (locked) Gfx.GREY else Gfx.GOLD, align = 0)
                }
            }
        }
        for (b in Building.entries) {
            val x = Iso.x(b.c, b.r).toFloat()
            val y = Iso.centerY(b.c, b.r).toFloat()
            if (!inView(x, y, 64f)) continue
            val art = "buildings/" + b.name.lowercase()
            list.add(y) {
                val bob = if (b == Building.ORDERS && world.state.orders.indices.any { world.canDeliver(it) }) sin(time * 6f) * 1.5f else 0f
                if (game.assets.has(art)) {
                    game.gfx.sprite(if (winter && game.assets.has("${art}_snow")) "${art}_snow" else art, x, y)
                    if (bob != 0f) game.gfx.sprite("icons/ready", x + 10, y - spriteTop(art) - 4 + bob)
                } else {
                    game.gfx.sprite("tiles/shadow", x, y - 6)
                    game.gfx.sprite(b.sprite, x, y - 12 + bob)
                }
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
                    chrome.panels.drawBuilding(def.id, x, y, world.time.season == Season.WINTER)
                    val ready = world.trayCount(def.id)
                    val top = spriteTop("buildings/${def.id}")
                    if (ready > 0) g.sprite("icons/ready", x + 10, y - top - 2 + sin(time * 5f) * 2f)
                    else if (world.jobProgress(def.id) > 0f) g.sprite("fx/sparkle", x + 10, y - top + 2, (120 + sin(time * 3f) * 80).toInt())
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
            if (!inView(x, top)) return@forEachIndexed
            if (p.crop != null) list.add(cy) { drawPlot(i, p, x, cy) }
        }
    }

    private val pickTmp = IntArray(2)
    /** True if a prop at world (x, y) now sits on owned farmland (bought after it was scattered). */
    private fun isYardWorld(x: Float, y: Float): Boolean {
        Iso.pick(x, y, pickTmp)
        val i = plotAt[key(pickTmp[0], pickTmp[1])] ?: return false
        return world.plot(i).owned
    }

    /** The crop on plot [i] and its status marks; sorted by its base like everything standing. */
    private fun drawPlot(i: Int, p: PlotState, x: Float, cy: Float) {
        val g = game.gfx
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
        if (placing != null && !chrome.isOpen) drawPlaceBanner(g)
        chrome.drawBottom(tipLift = if (trayOpen) 2 * 34 + 22 else if (placing != null) 34 else 0)
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
            val inSeason = !world.outOfSeason(def.id) || FieldLayout.isGreenhouse(pendingPlant)
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
                g.text(if (owned > 0) "x$owned" else "${world.seedPrice(def.id)}c", sx + size - 2, sy + size - 9, if (owned > 0) Gfx.WHITE else Gfx.GOLD, align = 1)
            }
        }
    }

    private fun drawPlaceBanner(g: Gfx) {
        val item = placing ?: return
        val w = game.engine.w
        val y = game.engine.h - game.engine.safeBottom - hud.bottomHeight - 34
        ui.panel(2, y, w - 4, 30, dark = true)
        g.sprite(fixtureSprite(item), 16f, y + 22f)
        g.text("PLACE ${world.data.name(item).uppercase()}  x${world.count(item)}", 30, y + 5, Gfx.GOLD)
        g.text(if (placeVertex < 0) "TAP A CORNER BETWEEN PLOTS" else "TAP AGAIN TO PLACE IT THERE", 30, y + 17, Gfx.WHITE)
        if (ui.button("place-cancel", w - 22, y + 4, 16, 12, null, "ui/x", "red")) { placing = null; placeVertex = -1 }
    }

    /** Enters placement mode for a fixture item (sprinkler, scarecrow, lightning rod). */
    fun startPlacing(item: String) {
        placing = item
        placeVertex = -1
        trayOpen = false
        recenter()
        hud.tip("fixtures", "Fixtures stand on the corners between plots. The glow shows which plots they cover.")
    }

    /** Nearest field corner to a world point, or -1 if none is within [reach] pixels. */
    private fun vertexAt(wx: Float, wy: Float, reach: Float): Int {
        val fc = (wx / 16f + wy / 8f) / 2f
        val fr = (wy / 8f - wx / 16f) / 2f
        val tc = fc.roundToInt(); val tr = fr.roundToInt()
        val b = FarmMap.BLOCKS.indexOfFirst { (bc, br) -> tc - bc in 0 until FieldLayout.VSIDE && tr - br in 0 until FieldLayout.VSIDE }
        if (b < 0 || !world.blockOwned(b)) return -1
        val (bc, br) = FarmMap.BLOCKS[b]
        val v = FieldLayout.vertex(b, tc - bc, tr - br)
        val (x, y) = vertexPos(v)
        return if (abs(x - wx) / 2 + abs(y - wy) <= reach) v else -1
    }

    override fun onWorldTap(wx: Float, wy: Float): Boolean {
        val item = placing
        if (item != null) {
            val v = vertexAt(wx, wy, 8f)
            when {
                v < 0 -> { hud.toast("Tap a corner between plots"); game.sfx.play("error") }
                v == placeVertex -> {
                    if (world.placeFixture(v, item)) {
                        placeVertex = -1
                        if (world.count(item) <= 0) placing = null
                    }
                }
                else -> {
                    world.placeBlocker(v, item)?.let { hud.toast(it); game.sfx.play("error") } ?: run { placeVertex = v; game.sfx.play("click", 0.5f) }
                }
            }
            return true
        }
        val v = vertexAt(wx, wy, 4f)
        val f = if (v >= 0) world.fixtureAt(v) else null
        if (f != null) {
            chrome.confirm("PICK UP?", "Put the ${world.data.name(f.item)} back in the Barn?", "PICK UP") { world.pickUpFixture(v) }
            return true
        }
        return false
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
        if ((c to r) == MERCHANT_TILE && world.merchantHere()) { chrome.open(Chrome.Panel.MERCHANT); return }
        if ((c to r) == FarmMap.BUNKHOUSE) { chrome.open(Chrome.Panel.WORKERS); return }
        if (FarmCrew.isTruck(c, r)) { tapTruck(); return }
        machineAt(c, r)?.let { def ->
            when {
                world.isBuilt(def.id) -> when {
                    world.trayCount(def.id) > 0 -> world.collect(def.id)
                    def.id == "market_cart" -> chrome.open(Chrome.Panel.CART)
                    else -> chrome.open(Chrome.Panel.MACHINE, def.id)
                }
                def.unlockLevel <= world.level -> chrome.open(Chrome.Panel.BUILD, def.id)
            }
            return
        }
        signAt(c, r).takeIf { it >= 0 }?.let { b ->
            if (world.blockOwned(b)) chrome.open(Chrome.Panel.FIELD, b.toString()) else offerBlock(b)
            return
        }
        val i = plotAt[key(c, r)] ?: run { trayOpen = false; return }
        val p = world.plot(i)
        if (!p.owned) {
            if (FieldLayout.isGreenhouse(i)) {
                if (world.level >= GREENHOUSE_LEVEL) chrome.open(Chrome.Panel.BUILD, "greenhouse") else hud.toast("The Greenhouse unlocks at level $GREENHOUSE_LEVEL")
                return
            }
            offerBlock(FieldLayout.blockOf(i))
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

    private fun tapTruck() {
        val t = world.state.truck
        val load = world.truckLoad()
        when {
            t.away -> world.truckSecondsLeft().toInt().let { hud.toast("The truck is in town: back in %d:%02d".format(it / 60, it % 60)) }
            load == 0 -> hud.toast("The truck is empty. Farmhands fill it from fields set to Truck.")
            else -> chrome.confirm("SEND TRUCK?", "Sell $load crops in town for about ${world.truckValue()} coins?" + if (load >= TRUCK_CAP) " It is full." else "", "SEND") {
                world.sendTruck()
            }
        }
    }

    private fun offerBlock(b: Int) {
        val name = FieldLayout.name(b)
        if (world.level < FieldLayout.UNLOCK[b]) { hud.toast("Field $name unlocks at level ${FieldLayout.UNLOCK[b]}"); return }
        chrome.confirm("FIELD $name", "Buy this field of ${FieldLayout.PER_BLOCK} plots for ${FieldLayout.PRICE[b]} coins?", "BUY") {
            if (world.buyBlock(b)) { game.sfx.play("buy"); game.haptics.heavy() }
        }
    }

    /** Field sign tile of block b (just outside its top corner). */
    private fun signAt(c: Int, r: Int): Int = FarmMap.BLOCKS.indexOfFirst { (bc, br) -> c == bc - 1 && r == br - 1 }

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

    fun recenter() = centerOnTile(16, 20)

    /** Map tile of plot [i]. */
    fun plotTile(i: Int) = plotTiles[i]

    /** Opens a panel by name; used by screenshot tests. */
    fun showPanel(name: String) {
        when (name) {
            "barn" -> chrome.open(Chrome.Panel.BARN); "market" -> chrome.open(Chrome.Panel.MARKET); "orders" -> chrome.open(Chrome.Panel.ORDERS)
            "settings" -> chrome.open(Chrome.Panel.SETTINGS); "workbench" -> chrome.open(Chrome.Panel.WORKBENCH)
            "tray" -> { trayOpen = true; seed = "carrot" }
            "place" -> startPlacing("copper_sprinkler")
            "workers" -> chrome.open(Chrome.Panel.WORKERS)
            "skills" -> chrome.open(Chrome.Panel.SKILLS, "bench")
            "tonics" -> { chrome.open(Chrome.Panel.WORKBENCH); chrome.panels.benchTab = 2 }
            "goals" -> chrome.open(Chrome.Panel.GOALS); "gift" -> chrome.open(Chrome.Panel.GIFT); "merchant" -> chrome.open(Chrome.Panel.MERCHANT); "cart" -> chrome.open(Chrome.Panel.CART)
            else -> if (name.startsWith("machine:")) chrome.open(Chrome.Panel.MACHINE, name.removePrefix("machine:"))
            else if (name.startsWith("build:")) chrome.open(Chrome.Panel.BUILD, name.removePrefix("build:"))
        }
    }

    /** Closes the open panel or tray; false if nothing was open (back leaves the app). */
    fun closePanel(): Boolean {
        if (chrome.close()) return true
        if (placing != null) { placing = null; placeVertex = -1; return true }
        if (trayOpen) { trayOpen = false; return true }
        return false
    }

    // --- simulation events -------------------------------------------------------
    private fun plotCenter(i: Int): Pair<Float, Float> {
        val (c, r) = plotTiles[i]
        return Iso.x(c, r).toFloat() to Iso.centerY(c, r).toFloat()
    }

    override fun onWorldEvent(e: WorldEvent) {
        if (e is WorldEvent.Built && e.building == "greenhouse") invalidateGround()
        if (chrome.onEvent(e)) return
        // A farmhand's own harvest, planting, watering or clearing: the crew shows it when they get there.
        val quiet = when (e) {
            is WorldEvent.Harvested -> e.plot == workerPlot
            is WorldEvent.Planted -> e.plot == workerPlot
            is WorldEvent.Watered -> e.plot == workerPlot
            is WorldEvent.Cleared -> e.plot == workerPlot
            else -> false
        }
        if (quiet) { workerPlot = -1; return }
        when (e) {
            is WorldEvent.WorkerActed -> { workerPlot = e.plot; crew.onActed(e.worker, e.plot, e.kind) }
            is WorldEvent.HarvesterRan -> crew.onHarvesterRan()
            is WorldEvent.TruckSent -> {
                game.sfx.play("sell"); game.haptics.heavy()
                hud.toast("The truck left with ${e.units} crops")
                val (tc, tr) = FarmMap.TRUCK
                fx.floatText("+${e.coins}", Iso.x(tc, tr).toFloat() + 8, Iso.centerY(tc, tr) - 40f, Gfx.GOLD, icon = "icons/coin")
            }
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
            is WorldEvent.BlockBought -> {
                val plots = FieldLayout.plotsOf(e.block)
                val (x, y) = plotCenter(plots.first + FieldLayout.PER_BLOCK / 2 + 1)
                fx.burst("fx/coin", x, y - 6, 12, speed = 70f); fx.floatText("-${e.price}", x, y - 24, Gfx.GOLD, icon = "icons/coin")
                fx.shake(1.5f)
                invalidateGround()
                scatterProps()
                hud.tip("land", "Tap a field's sign to choose its crop and where the harvest goes.")
            }
            is WorldEvent.XpGained -> {}
            is WorldEvent.Blocked -> { val (x, y) = plotCenter(e.plot); fx.floatText(e.reason.uppercase(), x, y - 24, Gfx.RED) }
            is WorldEvent.SeasonChanged -> invalidateGround()
            is WorldEvent.Storm -> {
                fx.shake(3f)
                e.scorched.forEach { val (x, y) = plotCenter(it); fx.burst("fx/zap", x, y - 10, 4, speed = 40f) }
            }
            is WorldEvent.FixturePlaced -> {
                val (x, y) = vertexPos(e.vertex)
                game.sfx.play("craft"); game.haptics.heavy()
                fx.burst("fx/dust", x, y, 6, speed = 30f, gravity = 30f)
                if (Fixture.of(e.item)?.kind == com.ditherfarm.game.FixtureKind.WATER) fx.burst("fx/drop", x, y - 6, 8, speed = 45f, gravity = 120f, life = 0.6f)
            }
            is WorldEvent.FixtureRemoved -> { game.sfx.play("pickup"); val (x, y) = vertexPos(e.vertex); fx.burst("fx/dust", x, y, 4, speed = 25f) }
            is WorldEvent.Sold -> {}
            is WorldEvent.Ate -> {}
            else -> {}
        }
    }

    private fun flash(i: Int) { flashPlot = i; flashTime = 0.25f }

    companion object {
        const val MAP = FarmMap.SIZE
        /** Greenhouse plots sit in a 4x3 block west of the farmstead. */
        val GH_C0 = FarmMap.GREENHOUSE.first
        val GH_R0 = FarmMap.GREENHOUSE.second
        const val GH_WALL = 22f
        const val GREENHOUSE_LEVEL = 12
        val MERCHANT_TILE = FarmMap.MERCHANT
        private fun key(c: Int, r: Int) = c.toLong() shl 32 or (r.toLong() and 0xFFFFFFFF)
    }
}
