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
import com.ditherfarm.game.quest
import com.ditherfarm.game.act2
import com.ditherfarm.game.ranch
import com.ditherfarm.game.collectProduct
import com.ditherfarm.game.petAnimal
import com.ditherfarm.game.pettedToday
import com.ditherfarm.game.ORCHARD_COST
import com.ditherfarm.game.ORCHARD_LEVEL
import com.ditherfarm.game.orchard
import com.ditherfarm.game.treeMature
import com.ditherfarm.game.pickFruit
import com.ditherfarm.game.FieldLayout
import com.ditherfarm.game.DECOR
import com.ditherfarm.game.Fixture
import com.ditherfarm.game.PlotState
import com.ditherfarm.game.Quality
import com.ditherfarm.game.Season
import com.ditherfarm.game.decorKey
import com.ditherfarm.game.giantCenter
import com.ditherfarm.game.pickUpDecor
import com.ditherfarm.game.placeDecor
import com.ditherfarm.game.Weather
import com.ditherfarm.game.WorldEvent
import com.ditherfarm.game.blockOwned
import com.ditherfarm.game.build
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
import com.ditherfarm.game.pointer
import com.ditherfarm.game.villageTier
import com.ditherfarm.game.FARMHANDS
import com.ditherfarm.game.truckCap
import com.ditherfarm.game.blockPrice
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
    override fun petToast(msg: String) = chrome.hud.toast(msg)
    override fun openPetInfo() = chrome.open(Chrome.Panel.PET_INFO)
    override fun openPetPanel() = chrome.open(Chrome.Panel.PET)
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
    /** Farm machines in slot order (the building list is fixed game data, so it is read once). */
    private val machines by lazy { world.buildingsOn("farm").filter { !it.prebuilt && it.id != "greenhouse" } }
    private fun machineAt(c: Int, r: Int) = machines.withIndex().firstOrNull { (i, _) -> slotTiles.getOrNull(i) == (c to r) }?.value

    /** A scattered decoration; [tree] props swap to their fall and snow art by season. */
    private class Prop(val sprite: String, val x: Float, val y: Float, val sway: Boolean, val tree: Boolean = false, val tile: Long = Long.MIN_VALUE)
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
    /** Decoration being placed (round 5) and the tile picked for it (-1 until the first tap). */
    private var decorPlacing: String? = null
    private var decorTile = -1L
    /** A refused decoration tap flashes red on its tile for a moment. */
    private var decorBad = -1L
    private var decorBadTime = 0f
    /** Tiles kept clear of scattered props and decorations (the truck, signs, houses...). */
    private val reservedTiles = HashSet<Long>()
    private var placeVertex = -1

    private enum class Action { PLANT, WATER, HARVEST, CLEAR }

    /** Farmhands, crates, harvester and truck. */
    private val crew = FarmCrew(game, this)
    /** The pasture's animals. */
    private val herd = Herd(game)
    /** Villagers walking the village. */
    private val village = VillageWalk(game)
    /** Fishing on the river. */
    private val fishing = Fishing(game) { hud.toast(it) }
    /** Plot of the farmhand job just announced: its result events stay quiet (the crew shows them). */
    private var workerPlot = -1

    /** Forest painted past the map edge, so the view can reach the pasture and the orchard by the edge. */
    override val margin = EDGE

    override fun terrain(c: Int, r: Int): Terrain = FarmMap.terrain(c, r, world.villageTier) { cc, rr -> isYard(cc, rr) }

    /** Village houses standing now (sprite per plot) and the shops the tiers have brought. */
    private fun houses(): List<Pair<Pair<Int, Int>, String>> {
        val t = world.villageTier
        return FarmMap.HOUSES.take(FarmMap.houseCount(t)).mapIndexed { k, tile -> tile to "buildings/house_${t}_${FarmMap.houseStyle(k)}" }
    }

    private fun shops(): List<Pair<Pair<Int, Int>, String>> = listOfNotNull(
        (FarmMap.SHOP_SEED to "buildings/shop_seed").takeIf { world.villageTier >= 1 },
        (FarmMap.SHOP_SMITH to "buildings/shop_smith").takeIf { world.villageTier >= 1 },
        (FarmMap.SHOP_CARPENTER to "buildings/shop_carpenter").takeIf { world.villageTier >= 2 },
        (FarmMap.SHOP_CAFE to "buildings/shop_cafe").takeIf { world.villageTier >= 3 },
    )

    /** Dirt under owned plots and one tile around them; the rest of the field stays meadow. */
    private fun isYard(c: Int, r: Int): Boolean {
        if (world.orchard.open && (c to r) in FarmMap.ORCHARD_TREES) return true
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
        cam.setDiamond(0f, n * Iso.TH / 2f, (n - 2 + 2 * EDGE) * Iso.TW / 2f, (n - 2 + 2 * EDGE) * Iso.TH / 2f)
        recenter()
        weather.onThunder = { game.sfx.play("thunder") }
        scatterProps()
        // The first tip fits the field: harvesting when something is ripe, else planting.
        if (world.state.plots.any { it.owned && world.isReady(it) }) tipHarvest() else tipPlant()
        if (world.level >= 3) hud.tip("villagers", "Villagers stroll the village road west of your fields in the daytime. Tap one to chat or give a gift.")
    }

    private fun tipHarvest() = hud.tip("harvest", "Tap a ripe crop to harvest it. Or hold one and slide across the others to harvest them all.")
    private fun tipPlant() = hud.tip("plant", "Tap an empty plot to plant seeds. Watered crops grow faster.")

    private val treeKinds = listOf("pine", "oak", "birch", "maple", "pine", "oak")

    private fun scatterProps() {
        props.clear()
        waterTiles.clear()
        val reserved = reservedTiles.also { it.clear() }
        listOf(FarmMap.BUNKHOUSE, FarmMap.TRUCK, FarmMap.MERCHANT, FarmMap.SIGN_FOREST, FarmMap.SIGN_MINE, FarmMap.ORCHARD_SIGN, FarmMap.PASTURE_SIGN, FarmMap.STATUE, FarmCrew.HARVESTER_PARK).forEach { reserved += key(it.first, it.second) }
        for (b in 0 until FieldLayout.BLOCKS) FarmCrew.crateTile(b).let { reserved += key(it.first, it.second) }
        for (dc in 0..1) for (dr in 0..1) reserved += key(FarmMap.TRUCK.first + dc, FarmMap.TRUCK.second + dr)
        (FarmMap.HOUSES + listOf(FarmMap.SHOP_SEED, FarmMap.SHOP_SMITH, FarmMap.SHOP_CARPENTER, FarmMap.SHOP_CAFE)).forEach { reserved += key(it.first, it.second) }
        for (r in -EDGE until rows + EDGE) for (c in -EDGE until cols + EDGE) {
            if (FarmMap.isRiver(c, r) && !FarmMap.isWall(c, r)) waterTiles += c to r
            if (key(c, r) in buildingAt || (c to r) in lanterns || (c to r) in slotTiles || key(c, r) in reserved) continue
            if (plotAt[key(c, r)]?.let { world.plot(it).owned } == true || FarmMap.isRoad(c, r) || FarmMap.inPasture(c, r) || FarmMap.inOrchard(c, r)) continue
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
            props += Prop(sprite, x, y, sprite == "deco/tallgrass_a", tile = decorKey(c, r))
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
        // Orchard: fenced like the pasture (the trees are drawn with the crops).
        for (c in FarmMap.ORCHARD_C) {
            props += Prop("deco/fence_r", Iso.x(c, FarmMap.ORCHARD_R.first).toFloat(), Iso.y(c, FarmMap.ORCHARD_R.first).toFloat(), false)
            props += Prop("deco/fence_r", Iso.x(c, FarmMap.ORCHARD_R.last + 1).toFloat(), Iso.y(c, FarmMap.ORCHARD_R.last + 1).toFloat(), false)
        }
        for (r in FarmMap.ORCHARD_R) {
            props += Prop("deco/fence_l", Iso.x(FarmMap.ORCHARD_C.first, r).toFloat(), Iso.y(FarmMap.ORCHARD_C.first, r).toFloat(), false)
            props += Prop("deco/fence_l", Iso.x(FarmMap.ORCHARD_C.last + 1, r).toFloat(), Iso.y(FarmMap.ORCHARD_C.last + 1, r).toFloat(), false)
        }
        fun at(t: Pair<Int, Int>, sprite: String, fallback: String) =
            Prop(if (game.assets.has(sprite)) sprite else fallback, Iso.x(t.first, t.second).toFloat(), Iso.centerY(t.first, t.second).toFloat(), false)
        props += at(33 to 13, "deco/hay_bale", "deco/bush")
        props += at(34 to 16, "deco/trough", "deco/log")
        props += at(FarmMap.SIGN_FOREST, "deco/sign_forest", "trees/signpost")
        props += at(FarmMap.SIGN_MINE, "deco/sign_mine", "trees/signpost")
        for ((t, sprite) in houses() + shops()) props += at(t, sprite, "buildings/cottage_a")
        props += at(FarmMap.BUNKHOUSE, "buildings/cottage_c", "buildings/well")
    }

    override fun update(dt: Float) {
        super.update(dt)
        chrome.update(dt)
        crew.update(dt)
        herd.update(dt)
        village.update(dt)
        fishing.update(dt)
        if (infoTime > 0) infoTime -= dt
        if (decorBadTime > 0) decorBadTime -= dt
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
        if (decorPlacing != null) drawDecorSpots(g)
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
        drawPointer(list)
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
        for ((k, id) in world.state.decor) {
            val c = (k / 1000).toInt(); val r = (k % 1000).toInt()
            val x = Iso.x(c, r).toFloat(); val y = Iso.centerY(c, r).toFloat()
            // Paths lie flat: under anything standing on or around them.
            if (inView(x, y)) list.add(if (DECOR[id]?.flat == true) y - Iso.TH * 2 else y) { game.gfx.sprite("decor/$id", x, y) }
        }
        decorPlacing?.let { id ->
            if (decorTile >= 0) {
                val c = (decorTile / 1000).toInt(); val r = (decorTile % 1000).toInt()
                val x = Iso.x(c, r).toFloat(); val y = Iso.centerY(c, r).toFloat()
                list.add(Float.MAX_VALUE) {
                    game.gfx.sprite("tiles/cursor_ok", x, Iso.y(c, r).toFloat(), (sin(time * 5f) * 50 + 205).toInt())
                    game.gfx.sprite("decor/$id", x, y, (sin(time * 6f) * 50 + 200).toInt())
                }
            }
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
            if (p.tile in world.state.decor) continue
            var sprite = if (p.sway && sin(time * 2.2f + p.x * 0.15f) > 0.3f) "deco/tallgrass_b" else p.sprite
            if (winter && game.assets.has("${sprite}_snow")) sprite = "${sprite}_snow"
            else if (p.tree && fall && game.assets.has("${sprite}_fall")) sprite = "${sprite}_fall"
            list.add(p.y + p.x * 0.001f) { game.gfx.sprite(sprite, p.x, p.y) }
            // Taps on the bunkhouse's walls and roof reach it, not just its base tile.
            if (p.sprite == "buildings/cottage_c") hitSprite(FarmMap.BUNKHOUSE.first, FarmMap.BUNKHOUSE.second, p.sprite, p.x, p.y)
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
                // Written on the board: the field's letter, or its level or price while it is not yours.
                if (world.blockOwned(b)) g.text(FieldLayout.name(b), x.toInt(), (y - 15).toInt(), Gfx.WHITE, align = 0)
                else {
                    val locked = world.level < FieldLayout.UNLOCK[b]
                    g.text(if (locked) "L${FieldLayout.UNLOCK[b]}" else Ui.compact(world.blockPrice(b)), x.toInt(), (y - 15).toInt(), if (locked) 0xFFC0CBDC.toInt() else Gfx.GOLD, align = 0)
                }
            }
        }
        // The festival statue (Story Act 2 finale).
        if (world.act2.chapter >= com.ditherfarm.game.ACT2_DONE) {
            val (c, r) = FarmMap.STATUE
            val x = Iso.x(c, r).toFloat(); val y = Iso.centerY(c, r).toFloat()
            if (inView(x, y)) { hitSprite(c, r, "decor/festival_statue", x, y); list.add(y) { game.gfx.sprite("decor/festival_statue", x, y) } }
        }
        drawOrchard(list)
        drawPastureSign(list)
        herd.collect(list, this)
        village.collect(list, this)
        fishing.collect(list)
        for (b in Building.entries) {
            val x = Iso.x(b.c, b.r).toFloat()
            val y = Iso.centerY(b.c, b.r).toFloat()
            if (!inView(x, y, 64f)) continue
            val art = "buildings/" + b.name.lowercase()
            hitSprite(b.c, b.r, art, x, y)
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
            if (built) hitSprite(c, r, "buildings/${def.id}", x, y)
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
            // A giant crop is drawn once, on its middle plot, sorted by its front plot.
            if (p.giant >= 0) {
                if (i != giantCenter(p.giant)) return@forEachIndexed
                val sprite = "crops/giant_${p.crop}"
                if (!inView(x, cy, 70f)) return@forEachIndexed
                hitSprite(c, r, sprite, x, cy)
                list.add(cy + Iso.HH) {
                    game.gfx.sprite(sprite, x, cy)
                    game.gfx.sprite("fx/sparkle", x + 20, cy - 40 + sin(time * 3f) * 2f)
                }
                return@forEachIndexed
            }
            if (!inView(x, top)) return@forEachIndexed
            val crop = cropSprite(p) ?: return@forEachIndexed
            hitSprite(c, r, crop, x, cy)
            // Tapping a crop's drop or sparkle counts as tapping the crop.
            when {
                world.isReady(p) -> hitSprite(c, r, "fx/sparkle", x + 7, cy - 20, cy + 0.5f)
                !p.needsClearing && !p.watered -> hitSprite(c, r, "fx/drop", x + 8, cy - 12, cy + 0.5f)
            }
            list.add(cy) { drawPlot(i, p, x, cy) }
        }
    }

    /** While a decoration is being placed: every free tile in view is marked green (a refused tap flashes red). */
    private fun drawDecorSpots(g: Gfx) {
        // Full strength: the dawn and dusk tint would wash out anything fainter. White, as green
        // vanished on the grass (playtest round 7).
        val a = (sin(time * 3f) * 25 + 230).toInt()
        for (r in 0 until rows) for (c in 0 until cols) {
            val x = Iso.x(c, r).toFloat(); val y = Iso.y(c, r).toFloat()
            if (!inView(x, y, 8f)) continue
            if (decorKey(c, r) == decorBad && decorBadTime > 0) g.sprite("tiles/cursor_bad", x, y)
            else if (decorFree(c, r)) g.sprite("tiles/cursor", x, y, a)
        }
    }

    /** Fishing hooks for screenshot tests. */
    fun fishAt(c: Int, r: Int) = fishing.start(c, r)
    val fishingState get() = fishing.state
    fun fishingTap() = fishing.tap()

    private fun camCenter() = cam.x + cam.viewWidth / 2f to cam.y + cam.viewHeight / 2f

    /** A tap on an animal: collect its product (and pet it), else pet it, else open the pasture. */
    private fun tapAnimal(i: Int) {
        val a = world.ranch.animals.getOrNull(i) ?: return
        when {
            a.product -> world.collectProduct(i)
            !world.pettedToday(a) -> world.petAnimal(i)
            else -> chrome.open(Chrome.Panel.PASTURE)
        }
    }

    /** The pasture's sign: named until the first animal arrives, then an egg; the level while locked. */
    private fun drawPastureSign(list: DrawList) {
        val (sc, sr) = FarmMap.PASTURE_SIGN
        val sx = Iso.x(sc, sr).toFloat(); val sy = Iso.centerY(sc, sr).toFloat()
        if (!inView(sx, sy)) return
        hitSprite(sc, sr, "deco/field_sign", sx, sy)
        val empty = world.ranch.animals.isEmpty()
        list.add(sy) {
            val g = game.gfx
            g.sprite("deco/field_sign", sx, sy)
            if (empty) g.text("PASTURE", sx.toInt(), (sy - 34).toInt(), Gfx.WHITE, align = 0)
            if (world.level < com.ditherfarm.game.ANIMAL_LEVEL) g.text("L${com.ditherfarm.game.ANIMAL_LEVEL}", sx.toInt(), (sy - 15).toInt(), 0xFFC0CBDC.toInt(), align = 0)
            else g.sprite("items/egg", sx, sy - 13)
        }
    }

    /** The orchard's sign (its level or price until it is bought) and its trees. */
    private fun drawOrchard(list: DrawList) {
        val o = world.orchard
        val (sc, sr) = FarmMap.ORCHARD_SIGN
        val sx = Iso.x(sc, sr).toFloat(); val sy = Iso.centerY(sc, sr).toFloat()
        if (inView(sx, sy)) {
            hitSprite(sc, sr, "deco/field_sign", sx, sy)
            list.add(sy) {
                val g = game.gfx
                g.sprite("deco/field_sign", sx, sy)
                // Named, so it isn't mistaken for a field's sign (playtest round 6).
                if (!o.open) g.text("ORCHARD", sx.toInt(), (sy - 34).toInt(), Gfx.WHITE, align = 0)
                when {
                    o.open -> g.sprite("items/apple", sx, sy - 13)
                    world.level < ORCHARD_LEVEL -> g.text("L$ORCHARD_LEVEL", sx.toInt(), (sy - 15).toInt(), 0xFFC0CBDC.toInt(), align = 0)
                    else -> g.text(Ui.compact(ORCHARD_COST["coins"] ?: 0), sx.toInt(), (sy - 15).toInt(), Gfx.GOLD, align = 0)
                }
            }
        }
        if (!o.open) return
        FarmMap.ORCHARD_TREES.forEachIndexed { i, (c, r) ->
            val x = Iso.x(c, r).toFloat(); val y = Iso.centerY(c, r).toFloat()
            if (!inView(x, y, 60f)) return@forEachIndexed
            val t = o.trees[i]
            if (t == null) {
                list.add(y) { game.gfx.sprite("icons/plus", x, y - 4 + sin(time * 3f + i) * 1.5f, 170) }
                return@forEachIndexed
            }
            val art = when {
                !world.treeMature(t) -> "trees/orchard_${t.kind}_sapling"
                t.fruit > 0 -> "trees/orchard_${t.kind}_fruit"
                else -> "trees/orchard_${t.kind}"
            }
            hitSprite(c, r, art, x, y)
            list.add(y) {
                game.gfx.sprite(art, x, y)
                if (t.fruit > 0 && (time * 2 + i * 0.37f) % 3f < 1.6f) game.gfx.sprite("fx/sparkle", x + 10, y - 40 + sin(time * 5f + i) * 1.5f)
            }
        }
    }

    /** Grandpa's notebook points at what its quest needs on the farm. */
    private fun drawPointer(list: DrawList) {
        val (tile, art) = pointerTarget() ?: return
        val x = Iso.x(tile.first, tile.second).toFloat(); val y = Iso.centerY(tile.first, tile.second).toFloat()
        val h = (art?.let { spriteTop(it) } ?: 6f) + 10f
        if (inView(x, y, 60f)) list.add(Float.MAX_VALUE) { game.gfx.sprite("icons/arrow_down", x, y - h + sin(time * 6f) * 2f) }
    }

    /** Centres the view on what the notebook points at (tapping the quest chip). */
    fun focusPointer() { pointerTarget()?.let { (t, _) -> centerOnTile(t.first, t.second) } }

    /** The farm tile the notebook's pointer marks, and the art standing there (for the arrow's height). */
    private fun pointerTarget(): Pair<Pair<Int, Int>, String?>? {
        return when (world.pointer) {
            com.ditherfarm.game.Pointer.FIELD -> fieldTarget()?.let { plotTiles[it] to cropSprite(world.plot(it)) }
                ?: ((FarmMap.BLOCKS[0].first + 1 to FarmMap.BLOCKS[0].second + 1) to null)
            com.ditherfarm.game.Pointer.WORKBENCH -> FarmMap.WORKBENCH to "buildings/workbench"
            com.ditherfarm.game.Pointer.WINDMILL -> (slotTiles.getOrNull(machines.indexOfFirst { it.id == "windmill" }) ?: return null) to "buildings/windmill"
            com.ditherfarm.game.Pointer.FIELD_B -> (FarmMap.BLOCKS[1].first - 1 to FarmMap.BLOCKS[1].second - 1) to "deco/field_sign"
            com.ditherfarm.game.Pointer.BUNKHOUSE -> FarmMap.BUNKHOUSE to "buildings/cottage_c"
            com.ditherfarm.game.Pointer.TRUCK -> FarmMap.TRUCK to "vehicles/truck_se_0"
            com.ditherfarm.game.Pointer.VILLAGE -> FarmMap.HOUSES[0] to "buildings/house_${world.villageTier}_a"
            else -> null
        }
    }

    /** The plot a field step needs: an empty one to plant, a dry crop to water, a ripe one to harvest. */
    private fun fieldTarget(): Int? {
        val plots = world.state.plots.filter { it.owned && it.giant < 0 }
        val p = when (world.quest?.id) {
            "plant" -> plots.firstOrNull { it.crop == null && !it.needsClearing }
            "water" -> plots.firstOrNull { it.crop != null && !it.needsClearing && !it.watered && !world.isReady(it) }
            "harvest" -> plots.firstOrNull { it.crop != null && !it.needsClearing && world.isReady(it) }
                ?: plots.firstOrNull { it.crop != null && !it.needsClearing }
            else -> null
        }
        return p?.index
    }

    private val pickTmp = IntArray(2)
    /** True if a prop at world (x, y) now sits on owned farmland (bought after it was scattered). */
    private fun isYardWorld(x: Float, y: Float): Boolean {
        Iso.pick(x, y, pickTmp)
        val i = plotAt[key(pickTmp[0], pickTmp[1])] ?: return false
        return world.plot(i).owned
    }

    /** The crop art standing on plot [p], or null when nothing grows there. */
    private fun cropSprite(p: PlotState): String? {
        val crop = p.crop ?: return null
        return if (p.needsClearing) "crops/${crop}_dead" else "crops/${crop}_${world.stage(p)}"
    }

    /** The crop on plot [i] and its status marks; sorted by its base like everything standing. */
    private fun drawPlot(i: Int, p: PlotState, x: Float, cy: Float) {
        val g = game.gfx
        val crop = p.crop
        if (crop != null) {
            val sprite = cropSprite(p)!!
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
        // A small halo round each lamp head; no pool painted on the ground.
        for ((c, r) in lanterns) out += Lighting.Light(Iso.x(c, r).toFloat(), Iso.centerY(c, r).toFloat() - LAMP_HEAD, 9, round = true)
    }

    override fun drawEmissive(g: Gfx, night: Float) {
        val a = (night * 255).toInt()
        for ((c, r) in lanterns) {
            val x = Iso.x(c, r).toFloat(); val y = Iso.centerY(c, r).toFloat()
            if (inView(x, y)) g.sprite("deco/lantern_lit", x, y, a)
        }
        // Lit windows in the village and the bunkhouse.
        val houses = houses() + shops() + (FarmMap.BUNKHOUSE to "buildings/cottage_c")
        for ((t, sprite) in houses) {
            val x = Iso.x(t.first, t.second).toFloat(); val y = Iso.centerY(t.first, t.second).toFloat()
            if (inView(x, y, 48f) && game.assets.has("${sprite}_lit")) g.sprite("${sprite}_lit", x, y, a)
        }
    }

    override fun drawHud(g: Gfx) {
        if (infoTime > 0 && infoPlot >= 0) drawInfo(g)
        chrome.drawTop()
        if (trayOpen && !chrome.isOpen) drawSeedTray(g)
        if (placing != null && !chrome.isOpen) drawPlaceBanner(g)
        if (decorPlacing != null && !chrome.isOpen) drawDecorBanner(g)
        if (fishing.active && !chrome.isOpen) fishing.drawUi(g, ui, game.engine.h - game.engine.safeBottom - hud.bottomHeight)
        chrome.drawBottom(tipLift = if (trayOpen) 2 * 34 + 22 else if (placing != null || decorPlacing != null || fishing.active) 34 else 0)
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

    private fun drawDecorBanner(g: Gfx) {
        val id = decorPlacing ?: return
        val d = DECOR[id] ?: return
        val w = game.engine.w
        val y = game.engine.h - game.engine.safeBottom - hud.bottomHeight - 34
        ui.panel(2, y, w - 4, 30, dark = true)
        g.sprite("decor/$id", 16f, y + 26f)
        g.text("PLACE ${d.name.uppercase()}  ${d.price} COINS", 32, y + 5, Gfx.GOLD)
        g.text(if (decorTile < 0) "TAP A GLOWING TILE ON THE FARM" else "TAP AGAIN TO PLACE IT THERE", 32, y + 17, Gfx.WHITE)
        if (ui.button("decor-cancel", w - 22, y + 4, 16, 12, null, "ui/x", "red")) { decorPlacing = null; decorTile = -1 }
    }

    /** Enters placement mode for a decoration bought in the decor shop. */
    fun startDecor(id: String) {
        decorPlacing = id
        decorTile = -1
        placing = null
        trayOpen = false
        hud.tip("decor", "Decorations go on free grass. Each adds beauty, and beauty adds a little to order coins.")
    }

    /** Free grass for a decoration: not a field, road, river, pasture, building, sign or lamp. */
    fun decorFree(c: Int, r: Int): Boolean {
        if (c !in 0 until cols || r !in 0 until rows) return false
        val k = key(c, r)
        if (terrain(c, r) != Terrain.GRASS || FarmMap.isRoad(c, r) || FarmMap.isRiver(c, r) || FarmMap.inPasture(c, r) || FarmMap.inOrchard(c, r) || FarmMap.isWall(c, r)) return false
        if (k in buildingAt || k in reservedTiles || plotAt.containsKey(k) || (c to r) in lanterns || (c to r) in slotTiles) return false
        return !world.state.decor.containsKey(decorKey(c, r))
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
        if (fishing.active) { fishing.tap(); return true }
        if (decorPlacing == null && placing == null) {
            herd.at(wx, wy).takeIf { it >= 0 }?.let { i -> tapAnimal(i); return true }
            village.at(wx, wy)?.let { id -> chrome.panels.villagerSays = ""; chrome.open(Chrome.Panel.VILLAGER, id); return true }
        }
        decorPlacing?.let { id ->
            val t = IntArray(2).also { Iso.pick(wx, wy, it) }
            val k = decorKey(t[0], t[1])
            when {
                !decorFree(t[0], t[1]) -> { hud.toast("Pick a glowing patch of grass"); game.sfx.play("error"); decorBad = k; decorBadTime = 0.6f }
                k == decorTile -> if (world.placeDecor(t[0], t[1], id)) {
                    decorPlacing = null; decorTile = -1; game.sfx.play("craft")
                    // Back to the shop for the next piece (playtest round 6).
                    chrome.open(Chrome.Panel.DECOR)
                }
                else -> { decorTile = k; game.sfx.play("click", 0.5f) }
            }
            return true
        }
        run {
            val t = IntArray(2).also { Iso.pick(wx, wy, it) }
            val id = world.state.decor[decorKey(t[0], t[1])] ?: return@run
            val d = DECOR[id] ?: return@run
            chrome.confirm("PICK UP?", "Pick up the ${d.name} for ${d.price / 2} coins back?", "PICK UP") { world.pickUpDecor(t[0], t[1]) }
            return true
        }
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
        if ((c to r) in FarmMap.HOUSES.take(FarmMap.houseCount(world.villageTier))) { chrome.open(Chrome.Panel.VILLAGE); return }
        when (c to r) {
            FarmMap.SHOP_SEED -> if (world.villageTier >= 1) { chrome.open(Chrome.Panel.SEEDS); return }
            FarmMap.SHOP_SMITH -> if (world.villageTier >= 1) { chrome.open(Chrome.Panel.MACHINE, "blacksmith"); return }
            FarmMap.SHOP_CARPENTER -> if (world.villageTier >= 2) { hud.toast("Willa's workshop: buildings and fields cost 15% less"); return }
            FarmMap.SHOP_CAFE -> if (world.villageTier >= 3) { hud.toast("Coco's cafe: dishes sell for 25% more at the Market"); return }
        }
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
        if ((c to r) == FarmMap.STATUE && world.act2.chapter >= com.ditherfarm.game.ACT2_DONE) { hud.toast("The valley's statue: Vexmoor never got the valley"); return }
        if ((c to r) == FarmMap.ORCHARD_SIGN) { chrome.open(Chrome.Panel.ORCHARD); return }
        if ((c to r) == FarmMap.PASTURE_SIGN) { chrome.open(Chrome.Panel.PASTURE); return }
        FarmMap.ORCHARD_TREES.indexOf(c to r).takeIf { it >= 0 }?.let { i ->
            val t = world.orchard.trees[i]
            if (t != null && t.fruit > 0) world.pickFruit(i) else chrome.open(Chrome.Panel.ORCHARD, i.toString())
            return
        }
        if (FarmMap.inOrchard(c, r)) { chrome.open(Chrome.Panel.ORCHARD); return }
        if (FarmMap.inPasture(c, r)) { chrome.open(Chrome.Panel.PASTURE); return }
        if (FarmMap.isRiver(c, r) && !FarmMap.isWall(c, r) && !FarmMap.isBridge(c, r)) { fishing.start(c, r); return }
        signAt(c, r).takeIf { it >= 0 }?.let { b ->
            if (world.blockOwned(b)) chrome.open(Chrome.Panel.FIELD, b.toString()) else chrome.offerBlock(b)
            return
        }
        val i = plotAt[key(c, r)] ?: run { trayOpen = false; return }
        val p = world.plot(i)
        if (!p.owned) {
            if (FieldLayout.isGreenhouse(i)) {
                if (world.level >= GREENHOUSE_LEVEL) chrome.open(Chrome.Panel.BUILD, "greenhouse") else hud.toast("The Greenhouse unlocks at level $GREENHOUSE_LEVEL")
                return
            }
            chrome.offerBlock(FieldLayout.blockOf(i))
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
            else -> chrome.confirm("SEND TRUCK?", "Sell $load crops in town for about ${world.truckValue()} coins?" + if (load >= world.truckCap) " It is full." else "", "SEND") {
                world.sendTruck()
            }
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
            "village" -> chrome.open(Chrome.Panel.VILLAGE)
            "tonics" -> { chrome.open(Chrome.Panel.WORKBENCH); chrome.panels.benchTab = 2 }
            "goals" -> chrome.open(Chrome.Panel.GOALS); "gift" -> chrome.open(Chrome.Panel.GIFT); "merchant" -> chrome.open(Chrome.Panel.MERCHANT); "cart" -> chrome.open(Chrome.Panel.CART); "pet" -> chrome.open(Chrome.Panel.PET); "decor" -> chrome.open(Chrome.Panel.DECOR); "pet_info" -> chrome.open(Chrome.Panel.PET_INFO)
            "orchard" -> chrome.open(Chrome.Panel.ORCHARD)
            "pasture" -> chrome.open(Chrome.Panel.PASTURE)
            "villager" -> chrome.open(Chrome.Panel.VILLAGER, "mara")
            else -> if (name.startsWith("orchard:")) chrome.open(Chrome.Panel.ORCHARD, name.removePrefix("orchard:"))
            else if (name.startsWith("machine:")) chrome.open(Chrome.Panel.MACHINE, name.removePrefix("machine:"))
            else if (name.startsWith("build:")) chrome.open(Chrome.Panel.BUILD, name.removePrefix("build:"))
        }
    }

    /** Drops placing and the seed tray (a new or imported farm starts from a clean screen). */
    fun clearModes() {
        placing = null
        placeVertex = -1
        decorPlacing = null
        decorTile = -1
        trayOpen = false
        infoPlot = -1
    }

    /** Closes the open panel or tray; false if nothing was open (back leaves the app). */
    fun closePanel(): Boolean {
        if (chrome.close()) return true
        if (placing != null) { placing = null; placeVertex = -1; return true }
        if (decorPlacing != null) { decorPlacing = null; decorTile = -1; return true }
        if (fishing.active) { fishing.cancel(); return true }
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
        if (e is WorldEvent.VillageRebuilt) {
            // Every house and road changes at once, in a cloud of dust.
            invalidateGround()
            scatterProps()
            fx.shake(3f)
            for ((t, _) in houses() + shops()) fx.burst("fx/dust", Iso.x(t.first, t.second).toFloat(), Iso.centerY(t.first, t.second) - 10f, 10, speed = 50f, gravity = 20f, life = 0.9f)
            centerOnTile(11, 27)
        }
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
                if (world.state.plots.any { it.owned && world.isReady(it) }) tipHarvest() else tipPlant()
            }
            is WorldEvent.GiantGrew -> {
                game.sfx.play("levelup"); game.haptics.heavy()
                hud.toast("A giant ${world.data.name(e.crop).lowercase()} grew overnight in Field ${FieldLayout.name(FieldLayout.blockOf(e.anchor))}!")
                hud.tip("giant", "Giant crops grow from a ripe 3x3 patch of pumpkins, melons or cabbages. Tap it to harvest a big gold haul.")
            }
            is WorldEvent.GiantHarvested -> {
                val (x, y) = plotCenter(giantCenter(e.anchor))
                game.sfx.play("levelup"); game.haptics.heavy()
                fx.burst("items/${e.crop}", x, y - 16, 10, speed = 80f)
                fx.floatText("+${e.qty}", x, y - 40, Gfx.GOLD, icon = "items/${e.crop}")
                fx.rise(Quality.GOLD.icon!!, x + 12, y - 30)
                hud.toast("Giant ${world.data.name(e.crop).lowercase()}: ${e.qty} gold ${world.data.name(e.crop).lowercase()}!")
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
            is WorldEvent.Act2Complete -> {
                // Anything set out where the statue goes comes back at full price.
                val (sc, sr) = FarmMap.STATUE
                world.state.decor.remove(decorKey(sc, sr))?.let { world.state.coins += DECOR[it]?.price ?: 0 }
                val x = Iso.x(sc, sr).toFloat(); val y = Iso.centerY(sc, sr).toFloat()
                fx.burst("fx/sparkle", x, y - 20, 16, speed = 60f, gravity = 0f, life = 1.2f)
                centerOnTile(sc, sr)
            }
            is WorldEvent.OrchardOpened -> {
                // Decorations standing where the orchard goes come back to the player at full price.
                for (c in FarmMap.ORCHARD_C) for (r in FarmMap.ORCHARD_R) world.state.decor.remove(decorKey(c, r))?.let { world.state.coins += DECOR[it]?.price ?: 0 }
                invalidateGround()
                val (c, r) = FarmMap.ORCHARD_TREES[5]
                fx.burst("fx/coin", Iso.x(c, r).toFloat(), Iso.centerY(c, r) - 6f, 12, speed = 70f); fx.shake(1.5f)
            }
            is WorldEvent.OrchardPlanted -> {
                val (c, r) = FarmMap.ORCHARD_TREES[e.spot]
                game.sfx.play("plant"); fx.burst("fx/dust", Iso.x(c, r).toFloat(), Iso.centerY(c, r).toFloat(), 6, speed = 30f, gravity = 30f, life = 0.5f)
            }
            is WorldEvent.TreeDugUp -> {
                val (c, r) = FarmMap.ORCHARD_TREES[e.spot]
                game.sfx.play("pickup"); fx.burst("fx/leaf", Iso.x(c, r).toFloat(), Iso.centerY(c, r) - 10f, 8, speed = 50f)
            }
            is WorldEvent.ProductCollected -> if (!e.byHand) herd.head(e.index)?.let { (x, y) ->
                game.sfx.play("harvest"); game.haptics.tick()
                fx.floatText("+1", x, y - 12, Gfx.WHITE, icon = "items/${e.item}")
                e.quality.icon?.let { fx.rise(it, x + 8, y - 6) }
            }
            is WorldEvent.AnimalPetted -> herd.head(e.index)?.let { (x, y) -> game.sfx.play("collect"); fx.rise("fx/heart", x, y) }
            is WorldEvent.FishCaught -> {
                game.sfx.play("levelup"); game.haptics.heavy()
                hud.toast("Caught a ${world.data.name(e.fish).lowercase()}! +${e.xp} XP")
                val (cx, cy) = camCenter()
                fx.floatText("+1", cx, cy - 20, Gfx.WHITE, icon = "items/${e.fish}")
                hud.tip("fishing", "Fish sell at the Market and fill orders. Each season has its own fish; catfish bite best at night and in the rain.")
            }
            is WorldEvent.FruitPicked -> {
                val (c, r) = FarmMap.ORCHARD_TREES[e.spot]
                val x = Iso.x(c, r).toFloat(); val y = Iso.centerY(c, r).toFloat()
                game.sfx.play("harvest"); game.haptics.tick()
                fx.burst("items/${e.item}", x, y - 24, 5, speed = 60f)
                fx.floatText("+${e.qty}", x, y - 44, Gfx.WHITE, icon = "items/${e.item}")
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
        /** Tiles of forest painted (and planted) beyond the map on every side. */
        const val EDGE = 4
        /** Greenhouse plots sit in a 4x3 block west of the farmstead. */
        val GH_C0 = FarmMap.GREENHOUSE.first
        val GH_R0 = FarmMap.GREENHOUSE.second
        const val GH_WALL = 22f
        const val GREENHOUSE_LEVEL = 12
        /** Height of a lantern's glass above its foot. */
        const val LAMP_HEAD = 11f
        val MERCHANT_TILE = FarmMap.MERCHANT
        private fun key(c: Int, r: Int) = c.toLong() shl 32 or (r.toLong() and 0xFFFFFFFF)
    }
}
