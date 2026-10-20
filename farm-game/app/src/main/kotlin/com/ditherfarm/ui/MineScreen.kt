package com.ditherfarm.ui

import com.ditherfarm.Game
import com.ditherfarm.WorldListener
import com.ditherfarm.engine.Gfx
import com.ditherfarm.engine.Iso
import com.ditherfarm.engine.Lighting
import com.ditherfarm.engine.Noise
import com.ditherfarm.engine.Terrain
import com.ditherfarm.game.MineLayout
import com.ditherfarm.game.BRACE_TAPS
import com.ditherfarm.game.Season
import com.ditherfarm.game.WorldEvent
import com.ditherfarm.game.brace
import com.ditherfarm.game.placeBeam
import com.ditherfarm.game.rumbleSecondsLeft
import com.ditherfarm.game.useRopeLadder
import com.ditherfarm.game.buildingsOn
import com.ditherfarm.game.collect
import com.ditherfarm.game.descend
import com.ditherfarm.game.elevatorStops
import com.ditherfarm.game.enterFloor
import com.ditherfarm.game.entryCell
import com.ditherfarm.game.isBuilt
import com.ditherfarm.game.jobProgress
import com.ditherfarm.game.lit
import com.ditherfarm.game.mine
import com.ditherfarm.game.mineBlocker
import com.ditherfarm.game.mineFloor
import com.ditherfarm.game.mineQuarry
import com.ditherfarm.game.pickTier
import com.ditherfarm.game.placeTorch
import com.ditherfarm.game.quarryNode
import com.ditherfarm.game.quarryUpgrade
import com.ditherfarm.game.repairBlocker
import com.ditherfarm.game.repairMine
import com.ditherfarm.game.trayCount
import com.ditherfarm.game.upgradeQuarry
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max
import kotlin.math.sin

/**
 * The mine. On the surface: the quarry (rocks that regrow), the old Mine Mouth and two
 * machine slots. Underground: one rock floor at a time. Break rocks for ore, find the
 * ladder down (hollow knocks give it away), light dark floors with torches and ride the
 * elevator back from every fifth floor.
 */
class MineScreen(game: Game) : IsoMapScreen(game, groundSeed = 71), WorldListener {
    private val world get() = game.world
    private val m get() = world.mine
    private val under get() = m.floor > 0
    override val cols get() = if (under) m.grid else MineLayout.SURFACE
    override val rows get() = cols
    val chrome = Chrome(game, Nav.MINE)
    override fun petToast(msg: String) = chrome.hud.toast(msg)
    override fun openPetInfo() = chrome.open(Chrome.Panel.PET_INFO)
    private val hud get() = chrome.hud
    override val hudTop get() = hud.topHeight
    override val hudBottom get() = hud.bottomHeight

    private val quarryAt = MineLayout.quarryTiles.withIndex().associate { (i, t) -> t.first * 1000 + t.second to i }
    /** Machines on the surface slots, in unlock order (the building list is fixed game data). */
    private val machines by lazy { world.buildingsOn("mine") }
    private val shake = HashMap<Int, Float>()
    private var holdCell = -1
    private var holdTimer = 0f
    private var layoutFloor = -1

    override fun terrain(c: Int, r: Int): Terrain = if (under) {
        if (c in 1..cols - 2 && r in 1..rows - 2) Terrain.STONE else Terrain.CAVE
    } else when {
        isPit(c, r) -> Terrain.STONE
        max(abs(c - MineLayout.mouth.first), abs(r - MineLayout.mouth.second)) <= 1 -> Terrain.STONE
        c == r && (c in 10..11 || c >= 16) -> Terrain.PATH
        surfaceLevel(c, r) == 0 && cliffDepth(c, r) <= 6f -> Terrain.GRAVEL
        else -> Terrain.GRASS
    }

    // --- cliffs and cave walls ----------------------------------------------------
    /** Painted cave round a floor: its back walls rise there, its front fades to black. */
    override val margin get() = if (under) 6 else 0
    override val edgeCliff get() = if (under) 0 else 14
    override val voidColor get() = if (under) Gfx.INK else null
    override val fades get() = under

    private val band get() = world.data.mine.band(max(1, m.floor)).band - 1

    override val rock get() = if (under) BAND_ROCK[band] else QUARRY_ROCK
    override val rockAccents get() = if (under) BAND_ACCENTS[band] else IntArray(0)
    override fun palette(): Map<Terrain, IntArray> = if (under) mapOf(Terrain.STONE to BAND_FLOOR[band], Terrain.CAVE to BAND_CAVE[band]) else emptyMap()

    /** Distance from the back edges, the cliff pushed forward behind the Mine Mouth (centre line). */
    private fun cliffDepth(c: Int, r: Int) = min(c, r) - max(0f, 5f - abs(c - r) / 2f)

    /** Stepped cliffs along the surface's back edges, four levels at the top. */
    private fun surfaceLevel(c: Int, r: Int): Int {
        val e = cliffDepth(c, r)
        val bump = if (Noise.rand(c, r, 421) < 0.3f) 1 else 0
        return when {
            e <= 1f -> 4
            e <= 2f -> min(4, 3 + bump)
            e <= 3f -> 2 + bump
            e <= 4f -> 1 + bump
            else -> 0
        }
    }

    override fun level(c: Int, r: Int): Int {
        if (!under) return surfaceLevel(c, r)
        val n = m.grid
        return when {
            (c <= 0 && r < n) || (r <= 0 && c < n) -> if (c < 0 || r < 0) 5 else 4
            else -> 0
        }
    }

    /** Darkness round the floor: none on the walls and the rim, black three tiles out. */
    override fun fade(wx: Float, wy: Float): Float {
        val c = (wy / Iso.HH + wx / Iso.HW) / 2f
        val r = (wy / Iso.HH - wx / Iso.HW) / 2f
        val n = m.grid.toFloat()
        val out = maxOf(-c, -r, c - n, r - n, 0f)
        return ((out - 1f) / 3.5f).coerceIn(0f, 1f)
    }

    /** A higher tile just in front would wrongly draw under this tile's props. */
    private fun shadowed(c: Int, r: Int): Boolean {
        val l = level(c, r)
        return listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1, 1 to 2, 2 to 2).any { (dc, dr) -> level(c + dc, r + dr) > l }
    }

    private val shade = android.graphics.LightingColorFilter(0x8C8C9E, 0)

    /** Quarry tiles plus the gaps between them, so the pit reads as one dug-out area. */
    private fun isPit(c: Int, r: Int): Boolean {
        if (quarryAt.containsKey(c * 1000 + r)) return true
        val near = listOf(c - 1 to r, c + 1 to r, c to r - 1, c to r + 1).count { (x, y) -> quarryAt.containsKey(x * 1000 + y) }
        return near >= 2
    }

    override fun seasonIndex() = if (under) 0 else world.time.season.ordinal
    override fun gradeHour(): Float? = if (under) null else world.time.hour

    override fun hour() = when {
        !under -> world.time.hour
        m.dark -> 23f
        else -> 12f
    }

    override fun enter() {
        layoutFloor = m.floor
        invalidateGround()
        super.enter()
        setupCamera()
        recenter()
        if (under) hud.tip("mine-floor", "Break rocks to find the ladder down. A hollow knock means it is close.")
        else hud.tip("mine", "Tap a rock to mine it (2 energy). Rocks in the quarry grow back.")
    }

    /** Called when the floor changes while the screen is open. */
    private fun relayout() {
        if (layoutFloor == m.floor) return
        layoutFloor = m.floor
        game.engine.dissolve()
        holdCell = -1
        shake.clear()
        invalidateGround()
        cam.setBounds(mapBounds())
        setupCamera()
        recenter()
        if (under && m.dark) hud.tip("mine-dark", "It is dark down here. Tap empty ground to place a torch.")
    }

    /** The surface keeps the view on painted ground like the farm; a floor sits in its dark cave. */
    private fun setupCamera() {
        cam.maxZoom = 3
        if (under) { cam.minZoom = 1; cam.clearDiamond() }
        else {
            val n = MineLayout.SURFACE
            cam.minZoom = 2
            cam.setDiamond(0f, n * Iso.TH / 2f, (n - 2) * Iso.TW / 2f, (n - 2) * Iso.TH / 2f)
        }
    }

    fun recenter() = if (under) centerOnTile(m.grid / 2, m.grid / 2) else centerOnTile(13, 13)

    override fun update(dt: Float) {
        super.update(dt)
        chrome.update(dt)
        if (layoutFloor != m.floor) relayout()
        val it = shake.entries.iterator()
        while (it.hasNext()) { val e = it.next(); e.setValue(e.value - dt); if (e.value <= 0) it.remove() }
        if (under && m.rumble >= 0) fx.shake(1.2f)
        if (holdCell >= 0) {
            holdTimer -= dt
            if (holdTimer <= 0f) {
                holdTimer = 0.33f
                if (!swing(holdCell)) holdCell = -1
            }
        }
    }

    private fun swing(i: Int): Boolean = if (under) {
        m.cells.getOrNull(i)?.node != null && world.mineFloor(i)
    } else world.mineQuarry(i)

    // --- drawing -------------------------------------------------------------
    private fun pick(sprite: String, fallback: String) = if (game.assets.has(sprite)) sprite else fallback

    private fun rockSprite(node: String): String = when (node) {
        "stone" -> pick("rocks/rock", "deco/rock")
        "boulder" -> pick("rocks/boulder", "deco/rock_big")
        "glow_mushroom" -> pick("rocks/cave_mushroom_cluster", "deco/mushrooms")
        "amethyst_node", "emerald_node", "ruby_node", "diamond_node" -> pick("rocks/gem_node_" + node.removeSuffix("_node"), "deco/rock_big")
        else -> pick("rocks/$node", "deco/rock")
    }

    /** The Mine Mouth with its grass cap in the season's colours. */
    private fun mouthSprite(): String {
        val v = when (world.time.season) { Season.SUMMER -> "rocks/mine_mouth_summer"; Season.FALL -> "rocks/mine_mouth_fall"; Season.WINTER -> "rocks/mine_mouth_snow"; else -> "rocks/mine_mouth" }
        return pick(v, pick("rocks/mine_mouth", "deco/rock_big"))
    }

    /** One of a node's shape variants (base, _2, _3), fixed per cell and floor. */
    private fun variant(sprite: String, i: Int): String {
        val k = Math.floorMod(i * 7 + m.floor * 3, 3)
        if (k == 0) return sprite
        return "${sprite}_${k + 1}".takeIf { game.assets.has(it) } ?: sprite
    }

    private fun tileOf(i: Int): Pair<Int, Int> = if (under) (i % m.grid) to (i / m.grid) else MineLayout.quarryTiles[i]

    private fun center(i: Int): Pair<Float, Float> {
        val (c, r) = tileOf(i)
        return Iso.x(c, r).toFloat() to Iso.centerY(c, r).toFloat()
    }

    override fun drawGroundLayer(g: Gfx) {
        if (!under) return
        val n = m.grid
        val e = entryCell(n)
        g.sprite("tiles/shadow", Iso.x(e % n, e / n).toFloat(), Iso.y(e % n, e / n).toFloat(), 160)
        if (m.ladderFound && m.ladder >= 0) {
            val c = m.ladder % n; val r = m.ladder / n
            if (game.assets.has("rocks/ladder_hole")) g.sprite("rocks/ladder_hole", Iso.x(c, r).toFloat(), Iso.centerY(c, r).toFloat())
            else g.sprite("tiles/shadow", Iso.x(c, r).toFloat(), Iso.y(c, r).toFloat())
        }
    }

    override fun collect(list: DrawList) {
        if (under) collectFloor(list) else collectSurface(list)
    }

    private fun drawRock(i: Int, node: String, damage: Int, x: Float, y: Float, hidden: Boolean) {
        val g = game.gfx
        val sx = shake[i]?.let { if (((it * 60).toInt() % 2) == 0) 1f else -1f } ?: 0f
        if (hidden && damage == 0) {
            g.sprite(rockArt(i, node, damage, hidden), x + sx, y, 110)
            g.text("?", x.toInt(), (y - 16).toInt(), Gfx.WHITE, align = 0)
            return
        }
        val art = rockArt(i, node, damage, hidden)
        g.sprite(art, x + sx, y)
        val hp = world.data.mine.nodes[node]?.hp ?: return
        if (damage > 0) {
            val crack = if (damage * 2 >= hp) "rocks/crack_2" else "rocks/crack_1"
            if (game.assets.has(crack)) g.sprite(crack, x + sx, y)
            drawHpBar(g, x, y - spriteTop(art) - 2, hp - damage, hp)
        }
    }

    /** The art of rock [node] in cell [i]: an unknown grey rock while it is hidden in the dark. */
    private fun rockArt(i: Int, node: String, damage: Int, hidden: Boolean): String =
        if (hidden && damage == 0) pick("rocks/rock", "deco/rock") else variant(rockSprite(node), i)

    /** A surface prop: its sprite, where it stands, and whether it is in the dark front wall (shaded). */
    private class SurfaceProp(val sprite: String, val x: Float, val y: Float, val front: Boolean)

    /**
     * The surface's scenery. Terrain, cliffs and the machine slots are fixed on the surface, so the
     * props are worked out once (in the same order as before) and each frame only culls and draws them.
     */
    private val surfaceProps: List<SurfaceProp> by lazy {
        val n = MineLayout.SURFACE
        val out = ArrayList<SurfaceProp>()
        for (r in 0 until n) for (c in 0 until n) {
            if (MineLayout.slots.contains(c to r) || (c to r) == MineLayout.cart || shadowed(c, r)) continue
            val t = terrain(c, r)
            if (t != Terrain.GRASS && t != Terrain.GRAVEL) continue
            val lv = level(c, r)
            val front = c >= SURFACE_WALL || r >= SURFACE_WALL
            val roll = Noise.rand(c, r, 411)
            val sprite = when {
                lv > 0 -> if (roll < 0.18f) "deco/tallgrass_a" else if (roll < 0.26f) "deco/bush" else if (roll < 0.3f) "trees/pine" else null
                front -> if (roll < 0.62f) "trees/pine" else if (roll < 0.74f) "deco/bush" else if (roll < 0.8f) "deco/rock_big" else null
                t == Terrain.GRAVEL -> if (roll < 0.22f) "deco/rock" else null
                roll < 0.14f -> "deco/tallgrass_a"
                roll < 0.2f -> "deco/rock"
                roll < 0.23f -> "deco/flowers_white"
                else -> null
            } ?: continue
            val x = Iso.x(c, r) + (Noise.rand(c, r, 412) - 0.5f) * 12f
            val y = Iso.centerY(c, r) + (Noise.rand(c, r, 413) - 0.5f) * 5f - lv * com.ditherfarm.engine.Raised.LEVEL_PX
            out += SurfaceProp(sprite, x, y, front)
        }
        out
    }

    private fun collectSurface(list: DrawList) {
        val g = game.gfx
        val q = world.data.mine.quarry.getOrNull(m.quarryLevel - 1)
        val spots = if (world.level >= (world.data.mine.quarry.firstOrNull()?.unlockLevel ?: 3)) q?.spots ?: 0 else 0
        for (i in 0 until spots) {
            val (x, y) = center(i)
            val cell = world.quarryNode(i) ?: continue
            if (!inView(x, y, ROCK_PAD)) continue
            cell.node?.let { node -> tileOf(i).let { (c, r) -> hitSprite(c, r, rockArt(i, node, cell.damage, hidden = false), x, y) } }
            list.add(y) {
                val node = cell.node
                if (node == null) { if (game.assets.has("rocks/rubble")) g.sprite("rocks/rubble", x, y, 150) }
                else drawRock(i, node, cell.damage, x, y, hidden = false)
            }
        }
        // The Mine Mouth.
        val (mc, mr) = MineLayout.mouth
        val mx = Iso.x(mc, mr).toFloat()
        val my = Iso.centerY(mc, mr).toFloat()
        list.add(my + 8) {
            g.sprite(mouthSprite(), mx, my)
            if (!m.repaired) {
                if (world.level >= world.data.mine.repairLevel) g.sprite("icons_big/build", mx, my - 58 + sin(time * 3f) * 1.5f, 220)
                else g.sprite("icons_big/lock", mx, my - 56, 200)
            } else g.sprite("icons/arrow_down", mx, my - 58 + sin(time * 3f) * 2f)
        }
        // Props: scrub on the ledges, pines and boulders in the dark wall in front, grass between.
        val (kc, kr) = MineLayout.cart
        if (game.assets.has("rocks/mine_cart")) list.add(Iso.centerY(kc, kr).toFloat()) { g.sprite("rocks/mine_cart", Iso.x(kc, kr).toFloat(), Iso.centerY(kc, kr).toFloat()) }
        val winter = world.time.season == Season.WINTER
        for (p in surfaceProps) {
            if (!inView(p.x, p.y, 70f)) continue
            val s = if (p.sprite == "trees/pine" && winter && game.assets.has("trees/pine_snow")) "trees/pine_snow" else p.sprite
            if (p.front) list.add(p.y) { g.spriteFiltered(s, p.x, p.y, shade) } else list.add(p.y) { g.sprite(s, p.x, p.y) }
        }
        // Machine slots.
        MineLayout.slots.forEachIndexed { k, (c, r) ->
            val def = machines.getOrNull(k) ?: return@forEachIndexed
            val x = Iso.x(c, r).toFloat()
            val y = Iso.centerY(c, r).toFloat()
            list.add(y) {
                if (world.isBuilt(def.id)) {
                    chrome.panels.drawBuilding(def.id, x, y, world.time.season == Season.WINTER)
                    if (world.trayCount(def.id) > 0) g.sprite("icons/ready", x + 10, y - spriteTop("buildings/${def.id}") - 2 + sin(time * 5f) * 2f)
                    else if (world.jobProgress(def.id) > 0f) g.sprite("fx/sparkle", x + 10, y - spriteTop("buildings/${def.id}") + 2, (120 + sin(time * 3f) * 80).toInt())
                } else if (def.unlockLevel <= world.level) {
                    g.sprite("icons_big/build", x, y - 10 + sin(time * 3f) * 1.5f, 200)
                }
            }
        }
    }

    private fun collectFloor(list: DrawList) {
        val g = game.gfx
        val n = m.grid
        for (i in 0 until n * n) {
            val c = i % n; val r = i / n
            val x = Iso.x(c, r).toFloat()
            val y = Iso.centerY(c, r).toFloat()
            // Cells off-screen add nothing, so skip their closures; the rock state is only read here.
            if (!inView(x, y, ROCK_PAD)) continue
            if (c == 0 || r == 0 || c == n - 1 || r == n - 1) {
                // The back walls are painted; loose rubble lies along the front rim.
                if (c > 0 && r > 0 && Noise.rand(c, r, m.floor) < 0.35f && game.assets.has("rocks/rubble")) list.add(y) { g.sprite("rocks/rubble", x, y) }
                continue
            }
            val cell = m.cells[i]
            val node = cell.node
            if (node != null) {
                hitSprite(c, r, rockArt(i, node, cell.damage, hidden = !world.lit(i)), x, y)
                list.add(y) { drawRock(i, node, cell.damage, x, y, hidden = !world.lit(i)) }
            }
            if (i in m.torches) list.add(y + 0.1f) { g.sprite(pick("rocks/torch_wall", "deco/lantern"), x, y) }
            if (i in m.markers) list.add(y + 0.15f) { drawMarker(i, x, y) }
        }
        val e = entryCell(n)
        val ex = Iso.x(e % n, e / n).toFloat()
        val ey = Iso.centerY(e % n, e / n).toFloat()
        list.add(ey) {
            g.sprite(pick("rocks/support_beam_prop", "deco/fence_l"), ex, ey)
            g.sprite("icons/arrow_up", ex, ey - 22 + sin(time * 3f) * 1.5f)
        }
        if (m.ladderFound && m.ladder >= 0) {
            val (lx, ly) = center(m.ladder)
            list.add(ly + 0.2f) { g.sprite("icons/arrow_down", lx, ly - 16 + sin(time * 4f) * 2f) }
        }
    }

    /** A cracked ceiling beam; flashes with a tap counter while it rumbles. */
    private fun drawMarker(i: Int, x: Float, y: Float) {
        val g = game.gfx
        val rumbling = i == m.rumble
        val sx = if (rumbling && ((time * 30).toInt() % 2 == 0)) 1f else 0f
        g.sprite(pick("rocks/support_beam_prop", "deco/fence_l"), x + sx, y)
        if (game.assets.has("rocks/crack_2")) g.sprite("rocks/crack_2", x + sx, y - 10)
        if (rumbling) {
            g.sprite("icons/warning", x, y - 34 + sin(time * 20f) * 1.5f)
            g.text("TAP x${BRACE_TAPS - m.braceTaps}", x.toInt(), (y - 48).toInt(), Gfx.GOLD, align = 0)
        } else g.sprite("icons/warning", x + 8, y - 28, (sin(time * 3f) * 60 + 160).toInt())
    }

    override fun worldLights(out: MutableList<Lighting.Light>) {
        if (!under) {
            if (m.repaired) {
                val (mc, mr) = MineLayout.mouth
                out += Lighting.Light(Iso.x(mc, mr).toFloat(), Iso.centerY(mc, mr).toFloat() - 10, 28)
            }
            MineLayout.slots.forEachIndexed { k, (c, r) ->
                if (machines.getOrNull(k)?.let { world.isBuilt(it.id) } == true) out += Lighting.Light(Iso.x(c, r).toFloat(), Iso.centerY(c, r).toFloat() - 6, 24)
            }
            return
        }
        for (t in m.torches) { val (x, y) = center(t); out += Lighting.Light(x, y - 8, 52) }
        if (m.dark && world.count("lantern") > 0) for (o in m.opened) { val (x, y) = center(o); out += Lighting.Light(x, y - 4, 30, warm = false) }
        val (ex, ey) = center(entryCell(m.grid))
        out += Lighting.Light(ex, ey - 6, 26)
    }

    override fun drawHud(g: Gfx) {
        chrome.drawTop(questBelow = 16)
        val y = game.engine.safeTop + hud.topHeight + 2
        ui.pill(2, y, 74, 14)
        g.sprite("icons/tool_pickaxe", 10f, y + 7f)
        g.text(world.data.tools.names[world.pickTier()].uppercase() + " PICK", 18, y + 3, Gfx.WHITE)
        if (under) {
            ui.pill(80, y, 40, 14)
            g.text("B${m.floor}", 100, y + 3, if (m.dark) Gfx.GOLD else Gfx.WHITE, align = 0)
            ui.pill(124, y, 40, 14)
            g.sprite("items/torch", 132f, y + 7f)
            g.text("${world.count("torch")}", 140, y + 3, Gfx.WHITE)
            val ropes = world.count("rope_ladder")
            if (ropes > 0 && ui.button("rope", 168, y, 56, 14, "ROPE x$ropes", style = "wood")) {
                chrome.confirm("ROPE LADDER", "Climb down to floor ${m.floor + 1} right away?", "DOWN") { world.useRopeLadder() }
            }
        } else {
            val next = world.quarryUpgrade()
            val label = "QUARRY ${m.quarryLevel}"
            if (next != null && next.unlockLevel <= world.level) {
                if (ui.button("quarry-up", 80, y, 78, 14, "$label +", style = "wood")) offerQuarry()
            } else {
                ui.pill(80, y, 60, 14)
                g.text(label, 110, y + 3, Gfx.WHITE, align = 0)
            }
        }
        chrome.drawBottom()
    }

    private fun costText(cost: Map<String, Int>) = cost.entries.joinToString(", ") { (k, v) -> if (k == "coins") "${v}c" else "$v ${world.data.name(k)}" }

    private fun offerQuarry() {
        val next = world.quarryUpgrade() ?: return
        chrome.confirm("QUARRY LEVEL ${next.level}", "${next.spots} rock spots that grow back faster, for ${costText(next.cost)}?", "DIG") { world.upgradeQuarry() }
    }

    // --- input ------------------------------------------------------------------
    override fun onTileTap(c: Int, r: Int) {
        if (under) tapFloor(r * m.grid + c) else tapSurface(c, r)
    }

    private fun tapSurface(c: Int, r: Int) {
        val (mc, mr) = MineLayout.mouth
        if (max(abs(c - mc), abs(r - mr)) <= 1) { tapMouth(); return }
        val k = MineLayout.slots.indexOf(c to r)
        if (k >= 0) {
            val def = machines.getOrNull(k) ?: return
            when {
                world.isBuilt(def.id) -> when {
                    world.trayCount(def.id) > 0 -> world.collect(def.id)
                    def.id == "geode_anvil" -> chrome.open(Chrome.Panel.GEODES)
                    else -> chrome.open(Chrome.Panel.MACHINE, def.id)
                }
                def.unlockLevel <= world.level -> chrome.open(Chrome.Panel.BUILD, def.id)
                else -> hud.toast("${def.name} unlocks at level ${def.unlockLevel}")
            }
            return
        }
        val i = quarryAt[c * 1000 + r] ?: return
        val cell = world.quarryNode(i) ?: return
        if (cell.node == null) hud.toast("This rock is still growing back") else world.mineQuarry(i)
    }

    private fun tapMouth() {
        when {
            !m.repaired -> {
                val blocker = world.repairBlocker()
                if (world.level < world.data.mine.repairLevel) { hud.toast(blocker ?: ""); return }
                chrome.confirm("THE OLD MINE", "Fix the ladders and beams for ${costText(world.data.mine.repairCost)}?", "REPAIR") { world.repairMine() }
            }
            world.elevatorStops().size > 1 -> chrome.open(Chrome.Panel.ELEVATOR)
            else -> world.enterFloor(1)
        }
    }

    private fun tapFloor(i: Int) {
        val cell = m.cells.getOrNull(i) ?: return
        if (i in m.markers) {
            when {
                i == m.rumble -> { if (world.brace(i)) { game.sfx.play("chop"); game.haptics.click() } }
                world.count("support_beam") > 0 -> chrome.confirm("CRACKED BEAM", "Prop it up with a Support Beam so it can't cave in?", "PROP") { world.placeBeam(i) }
                else -> hud.toast("A cracked beam. Careful mining near it! A Support Beam makes it safe.")
            }
            return
        }
        when {
            i == entryCell(m.grid) -> chrome.open(Chrome.Panel.ELEVATOR)
            m.ladderFound && i == m.ladder -> world.descend()
            cell.node != null -> world.mineFloor(i)
            m.dark && i !in m.torches -> if (world.count("torch") > 0) world.placeTorch(i) else hud.toast("Make torches at the Workbench")
        }
    }

    override fun onTileLongPress(c: Int, r: Int) {
        val i = if (under) r * m.grid + c else quarryAt[c * 1000 + r] ?: return
        val node = if (under) m.cells.getOrNull(i)?.node else world.quarryNode(i)?.node
        if (node != null && node != "glow_mushroom" && world.mineBlocker(node) == null) {
            holdCell = i; holdTimer = 0f
            game.haptics.click()
        }
    }

    override fun onTilePaint(c: Int, r: Int) {
        if (holdCell < 0) return
        val i = if (under) r * m.grid + c else quarryAt[c * 1000 + r] ?: return
        val node = if (under) m.cells.getOrNull(i)?.node else world.quarryNode(i)?.node
        if (node != null) holdCell = i
    }

    override fun onPaintEnd() { holdCell = -1 }

    fun closePanel(): Boolean = chrome.close()

    // --- simulation events --------------------------------------------------------
    override fun onWorldEvent(e: WorldEvent) {
        if (chrome.onEvent(e)) return
        when (e) {
            is WorldEvent.Mined -> {
                if (e.quarry == under) return
                val (x, y) = center(e.cell)
                game.sfx.play("mine"); game.haptics.tick()
                shake[e.cell] = 0.12f
                fx.burst("fx/chip_stone", x, y - 8, if (e.crit) 7 else 4, speed = 55f)
                if (e.crit) { fx.floatText("CRIT!", x, y - 28, Gfx.GOLD); fx.shake(1.5f) }
            }
            is WorldEvent.RockBroken -> {
                if (e.quarry == under) return
                val (x, y) = center(e.cell)
                val gem = e.drops.keys.any { it in GEMS }
                game.sfx.play(when { e.node == "glow_mushroom" -> "pickup"; gem -> "gem"; e.drops.keys.any { it.endsWith("_ore") || it == "coal" } -> "ore"; else -> "rock_break" })
                if (e.node != "glow_mushroom") { fx.burst("fx/dust", x, y, 6, speed = 30f, gravity = 20f); fx.burst("fx/chip_stone", x, y - 6, 6, speed = 50f) }
                if (gem) { fx.burst("fx/sparkle", x, y - 12, 8, speed = 40f, gravity = 0f, life = 0.9f); game.haptics.heavy() }
                e.drops.entries.forEachIndexed { k, (item, n) -> fx.floatText("+$n", x - 8 + k * 6f, y - 24 - k * 9f, Gfx.WHITE, icon = "items/$item") }
            }
            is WorldEvent.HollowKnock -> {
                val (x, y) = center(e.cell)
                game.sfx.play("drip")
                fx.floatText("knock", x, y - 30, 0xFFC0CBDC.toInt())
                hud.tip("knock", "That rock sounded hollow. The ladder is next to it, or under it!")
            }
            is WorldEvent.Rumble -> {
                game.sfx.play("thunder"); game.haptics.heavy()
                hud.toast("The ceiling rumbles! Tap the beam ${BRACE_TAPS} times!")
            }
            is WorldEvent.Braced -> {
                val (x, y) = center(e.cell)
                game.sfx.play("unlock"); fx.floatText("SAFE!", x, y - 40, Gfx.GREEN)
                fx.burst("fx/dust", x, y - 20, 5, speed = 20f, gravity = 40f)
            }
            is WorldEvent.CaveIn -> {
                val (x, y) = center(e.cell)
                game.sfx.play("rock_break"); game.haptics.heavy(); fx.shake(4f)
                fx.burst("fx/dust", x, y - 10, 16, speed = 60f, gravity = 30f, life = 1f)
                fx.burst("fx/chip_stone", x, y - 20, 12, speed = 70f)
                hud.toast("Cave-in! Rubble everywhere. -5 energy")
            }
            is WorldEvent.LadderFound -> {
                game.sfx.play("unlock"); game.haptics.heavy()
                hud.toast("Ladder found! Tap it to go down")
            }
            is WorldEvent.FloorEntered -> relayout()
            is WorldEvent.StopUnlocked -> { game.sfx.play("unlock"); hud.toast("Elevator stop B${e.floor} unlocked!") }
            is WorldEvent.GateOpened -> { game.sfx.play("gem"); hud.toast("Gate chest: ${costText(e.chest)}") }
            is WorldEvent.GeodeCracked -> {
                val k = e.out.keys.firstOrNull() ?: return
                game.sfx.play(if (k in GEMS || k == "prismatic_shard" || k.startsWith("mineral_")) "gem" else "ore")
                if (k.startsWith("mineral_") || k == "prismatic_shard") game.haptics.heavy()
            }
            is WorldEvent.Blocked -> { game.sfx.play("error"); hud.toast(e.reason) }
            is WorldEvent.SeasonChanged -> if (!under) invalidateGround()
            else -> {}
        }
    }


    companion object {
        private val GEMS = setOf("quartz", "amethyst", "emerald", "ruby", "diamond")

        /** Front rows of the surface that are pine wall. */
        const val SURFACE_WALL = 21
        /** World px of margin for rocks and ceiling beams (the tallest rock art rises 60 px over its base). */
        private const val ROCK_PAD = 64f

        private fun ramp(vararg c: Long) = IntArray(c.size) { c[it].toInt() }

        /** Sandstone quarry cliffs. */
        val QUARRY_ROCK = ramp(0xFF181425, 0xFF3E2731, 0xFF733E39, 0xFFB86F50, 0xFFC28569, 0xFFE4A672)

        /** Per mine band (floors 1-10, 11-20, 21-30, 31-40): wall faces, floor, the cave beyond and glints. */
        val BAND_ROCK = listOf(
            ramp(0xFF181425, 0xFF3E2731, 0xFF733E39, 0xFFB86F50, 0xFFC28569, 0xFFE4A672),
            ramp(0xFF181425, 0xFF262B44, 0xFF3A4466, 0xFF5A6988, 0xFF8B9BB4, 0xFFC0CBDC),
            ramp(0xFF181425, 0xFF262B44, 0xFF124E89, 0xFF3A4466, 0xFF5A6988, 0xFF8B9BB4),
            ramp(0xFF181425, 0xFF3E2731, 0xFF68386C, 0xFFA22633, 0xFFBE4A2F, 0xFFD77643),
        )
        val BAND_FLOOR = listOf(
            ramp(0xFF3E2731, 0xFF733E39, 0xFF733E39, 0xFFB86F50),
            ramp(0xFF3A4466, 0xFF5A6988, 0xFF8B9BB4, 0xFFC0CBDC),
            ramp(0xFF262B44, 0xFF3A4466, 0xFF124E89, 0xFF5A6988),
            ramp(0xFF181425, 0xFF3E2731, 0xFF68386C, 0xFF733E39),
        )
        val BAND_CAVE = listOf(
            ramp(0xFF181425, 0xFF3E2731, 0xFF3E2731, 0xFF733E39),
            ramp(0xFF181425, 0xFF262B44, 0xFF3A4466, 0xFF5A6988),
            ramp(0xFF181425, 0xFF262B44, 0xFF262B44, 0xFF124E89),
            ramp(0xFF181425, 0xFF181425, 0xFF3E2731, 0xFF68386C),
        )
        val BAND_ACCENTS = listOf(
            ramp(0xFFF77622, 0xFFE4A672, 0xFFB86F50),
            ramp(0xFFFEAE34, 0xFFC0CBDC, 0xFFFFFFFF),
            ramp(0xFF2CE8F5, 0xFF0099DB, 0xFFFFFFFF),
            ramp(0xFFF77622, 0xFFFEAE34, 0xFFE43B44),
        )
    }
}
