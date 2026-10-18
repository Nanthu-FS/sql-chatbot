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
    private val hud get() = chrome.hud
    override val hudTop get() = hud.topHeight
    override val hudBottom get() = hud.bottomHeight

    private val quarryAt = MineLayout.quarryTiles.withIndex().associate { (i, t) -> t.first * 1000 + t.second to i }
    private val machines get() = world.buildingsOn("mine")
    private val shake = HashMap<Int, Float>()
    private var holdCell = -1
    private var holdTimer = 0f
    private var layoutFloor = -1

    override fun terrain(c: Int, r: Int): Terrain = if (under) {
        if (c == 0 || r == 0 || c == cols - 1 || r == rows - 1) Terrain.CAVE else Terrain.STONE
    } else when {
        isPit(c, r) -> Terrain.STONE
        max(abs(c - MineLayout.mouth.first), abs(r - MineLayout.mouth.second)) <= 1 -> Terrain.STONE
        c == r && (c in 4..5 || c >= 10) -> Terrain.PATH
        else -> Terrain.GRASS
    }

    /** Quarry tiles plus the gaps between them, so the pit reads as one dug-out area. */
    private fun isPit(c: Int, r: Int): Boolean {
        if (quarryAt.containsKey(c * 1000 + r)) return true
        val near = listOf(c - 1 to r, c + 1 to r, c to r - 1, c to r + 1).count { (x, y) -> quarryAt.containsKey(x * 1000 + y) }
        return near >= 2
    }

    override fun seasonIndex() = if (under) 0 else world.time.season.ordinal
    override fun hour() = when {
        !under -> world.time.hour
        m.dark -> 23f
        else -> 12f
    }

    override fun enter() {
        layoutFloor = m.floor
        invalidateGround()
        super.enter()
        cam.maxZoom = 3
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
        cam.setBounds(Iso.bounds(cols, rows))
        recenter()
        if (under && m.dark) hud.tip("mine-dark", "It is dark down here. Tap empty ground to place a torch.")
    }

    fun recenter() = if (under) centerOnTile(m.grid / 2, m.grid / 2) else centerOnTile(7, 7)

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
            g.sprite(pick("rocks/rock", "deco/rock"), x + sx, y, 110)
            g.text("?", x.toInt(), (y - 16).toInt(), Gfx.WHITE, align = 0)
            return
        }
        g.sprite(rockSprite(node), x + sx, y)
        val hp = world.data.mine.nodes[node]?.hp ?: return
        if (damage > 0) {
            val crack = if (damage * 2 >= hp) "rocks/crack_2" else "rocks/crack_1"
            if (game.assets.has(crack)) g.sprite(crack, x + sx, y)
            val w = 16
            g.rect((x - w / 2).toInt(), (y + 3).toInt(), (x + w / 2).toInt(), (y + 5).toInt(), Gfx.INK)
            g.rect((x - w / 2).toInt(), (y + 3).toInt(), (x - w / 2 + w * (hp - damage) / hp).toInt(), (y + 5).toInt(), 0xFFFEAE34.toInt())
        }
    }

    private fun collectSurface(list: DrawList) {
        val g = game.gfx
        val q = world.data.mine.quarry.getOrNull(m.quarryLevel - 1)
        val spots = if (world.level >= (world.data.mine.quarry.firstOrNull()?.unlockLevel ?: 3)) q?.spots ?: 0 else 0
        for (i in 0 until spots) {
            val (x, y) = center(i)
            val cell = world.quarryNode(i) ?: continue
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
            g.sprite(pick("rocks/mine_mouth", "deco/rock_big"), mx, my + 8)
            if (!m.repaired) {
                if (world.level >= world.data.mine.repairLevel) g.sprite("icons_big/build", mx, my - 30 + sin(time * 3f) * 1.5f, 220)
                else g.sprite("icons_big/lock", mx, my - 28, 200)
            } else g.sprite("icons/arrow_down", mx, my - 30 + sin(time * 3f) * 2f)
        }
        // Props.
        if (game.assets.has("rocks/mine_cart")) list.add(Iso.centerY(2, 5).toFloat()) { g.sprite("rocks/mine_cart", Iso.x(2, 5).toFloat(), Iso.centerY(2, 5).toFloat()) }
        for (r in 0 until rows) for (c in 0 until cols) {
            if (terrain(c, r) != Terrain.GRASS || MineLayout.slots.contains(c to r)) continue
            val roll = Noise.rand(c, r, 411)
            val sprite = when {
                roll < 0.14f -> "deco/tallgrass_a"
                roll < 0.2f -> "deco/rock"
                roll < 0.23f -> "deco/flowers_white"
                else -> null
            } ?: continue
            val x = Iso.x(c, r) + (Noise.rand(c, r, 412) - 0.5f) * 12f
            val y = Iso.centerY(c, r) + (Noise.rand(c, r, 413) - 0.5f) * 5f
            list.add(y) { g.sprite(sprite, x, y) }
        }
        // Machine slots.
        MineLayout.slots.forEachIndexed { k, (c, r) ->
            val def = machines.getOrNull(k) ?: return@forEachIndexed
            val x = Iso.x(c, r).toFloat()
            val y = Iso.centerY(c, r).toFloat()
            list.add(y) {
                if (world.isBuilt(def.id)) {
                    g.sprite("tiles/shadow", x, y - 6)
                    g.sprite(chrome.panels.buildingSprite(def.id), x, y - 12)
                    if (world.trayCount(def.id) > 0) g.sprite("icons/ready", x + 10, y - 30 + sin(time * 5f) * 2f)
                    else if (world.jobProgress(def.id) > 0f) g.sprite("fx/sparkle", x + 10, y - 26, (120 + sin(time * 3f) * 80).toInt())
                } else if (def.unlockLevel <= world.level) {
                    g.sprite("icons_big/build", x, y - 10 + sin(time * 3f) * 1.5f, 200)
                }
            }
        }
    }

    private fun collectFloor(list: DrawList) {
        val g = game.gfx
        val n = m.grid
        val wall = pick("rocks/rock_big", "deco/rock_big")
        for (i in 0 until n * n) {
            val c = i % n; val r = i / n
            val x = Iso.x(c, r).toFloat()
            val y = Iso.centerY(c, r).toFloat()
            if (c == 0 || r == 0 || c == n - 1 || r == n - 1) {
                // Cave walls along the back edges only, so they never hide the floor.
                if (c == 0 || r == 0) list.add(y) { g.sprite(wall, x, y, 255) }
                continue
            }
            val cell = m.cells[i]
            val node = cell.node
            if (node != null) list.add(y) { drawRock(i, node, cell.damage, x, y, hidden = !world.lit(i)) }
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
        chrome.drawTop()
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

    private companion object {
        val GEMS = setOf("quartz", "amethyst", "emerald", "ruby", "diamond")
    }
}
