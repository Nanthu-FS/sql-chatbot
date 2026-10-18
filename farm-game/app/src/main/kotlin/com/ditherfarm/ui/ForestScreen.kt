package com.ditherfarm.ui

import com.ditherfarm.Game
import com.ditherfarm.WorldListener
import com.ditherfarm.engine.Gfx
import com.ditherfarm.engine.Iso
import com.ditherfarm.engine.Lighting
import com.ditherfarm.engine.Noise
import com.ditherfarm.engine.Terrain
import com.ditherfarm.game.ForestLayout
import com.ditherfarm.game.Season
import com.ditherfarm.game.SpotKind
import com.ditherfarm.game.Tool
import com.ditherfarm.game.TreeStage
import com.ditherfarm.game.Weather
import com.ditherfarm.game.WorldEvent
import com.ditherfarm.game.buildingsOn
import com.ditherfarm.game.chop
import com.ditherfarm.game.chopBlocker
import com.ditherfarm.game.collect
import com.ditherfarm.game.collectBranch
import com.ditherfarm.game.forage
import com.ditherfarm.game.forestSpots
import com.ditherfarm.game.isBuilt
import com.ditherfarm.game.jobProgress
import com.ditherfarm.game.plantTree
import com.ditherfarm.game.spot
import com.ditherfarm.game.toolTier
import com.ditherfarm.game.trayCount
import com.ditherfarm.game.treeHp
import com.ditherfarm.game.treeStage
import com.ditherfarm.game.unlockZone
import com.ditherfarm.game.zoneBlocker
import com.ditherfarm.game.zoneOpen
import kotlin.math.sin
import com.ditherfarm.engine.Weather as Sky

/**
 * The forest: five zones of trees climbing from the entrance (bottom) to the Ancient
 * Heart (top). Tap a tree to swing the axe (2 energy), hold to keep swinging, tap
 * stumps, bushes, apple trees, mushrooms and branches to collect, and build the
 * Sawmill and friends on the forest slots.
 */
class ForestScreen(game: Game) : IsoMapScreen(game, groundSeed = 53), WorldListener {
    override val cols = ForestLayout.SIZE
    override val rows = ForestLayout.SIZE
    val chrome = Chrome(game, Nav.FOREST)
    private val hud get() = chrome.hud
    override val hudTop get() = hud.topHeight
    override val hudBottom get() = hud.bottomHeight
    private val world get() = game.world

    private val spotAt by lazy { world.forestSpots.associateBy { it.c * 1000 + it.r } }
    private val machines get() = world.buildingsOn("forest")
    private val slotSpots by lazy { world.forestSpots.filter { it.kind == SpotKind.SLOT } }

    /** Per-spot shake timer after a hit. */
    private val shake = HashMap<Int, Float>()
    private var holdSpot = -1
    private var holdTimer = 0f
    private var props = ArrayList<Triple<String, Float, Float>>()

    override fun terrain(c: Int, r: Int): Terrain = when {
        ForestLayout.isTrail(c, r) -> Terrain.PATH
        c + r >= 38 -> Terrain.GRASS
        else -> Terrain.FOREST
    }

    override fun seasonIndex() = world.time.season.ordinal
    override fun hour() = world.time.hour

    override fun enter() {
        super.enter()
        cam.maxZoom = 3
        recenter()
        weather.onThunder = { game.sfx.play("thunder") }
        if (props.isEmpty()) scatter()
        hud.tip("forest", "Tap a tree to chop it. Each swing uses 2 energy. Hold to keep swinging.")
    }

    fun recenter() = centerOnTile(ForestLayout.SIZE - 5, ForestLayout.SIZE - 5)

    private fun scatter() {
        for (r in 0 until rows) for (c in 0 until cols) {
            if (spotAt.containsKey(c * 1000 + r) || ForestLayout.isTrail(c, r)) continue
            val roll = Noise.rand(c, r, 301)
            val sprite = when {
                roll < 0.18f -> "deco/tallgrass_a"
                roll < 0.24f -> "deco/bush"
                roll < 0.27f -> "deco/rock"
                roll < 0.29f -> "deco/log"
                else -> null
            } ?: continue
            props += Triple(sprite, Iso.x(c, r) + (Noise.rand(c, r, 302) - 0.5f) * 12f, Iso.centerY(c, r) + (Noise.rand(c, r, 303) - 0.5f) * 5f)
        }
    }

    override fun update(dt: Float) {
        super.update(dt)
        chrome.update(dt)
        val it = shake.entries.iterator()
        while (it.hasNext()) { val e = it.next(); e.setValue(e.value - dt); if (e.value <= 0) it.remove() }
        weather.kind = when (world.state.weather) {
            Weather.SUNNY -> if (world.time.season == Season.FALL) Sky.Kind.LEAVES else Sky.Kind.CLEAR
            Weather.RAIN -> Sky.Kind.RAIN
            Weather.STORM -> Sky.Kind.STORM
            Weather.SNOW, Weather.BLIZZARD -> Sky.Kind.SNOW
        }
        // Holding on a tree keeps swinging at 3 per second.
        if (holdSpot >= 0) {
            holdTimer -= dt
            if (holdTimer <= 0f) {
                holdTimer = 0.33f
                if (!world.chop(holdSpot)) holdSpot = -1
            }
        }
    }

    // --- drawing -------------------------------------------------------------
    private fun seasonal(base: String): String {
        val season = world.time.season
        val v = when (season) { Season.FALL -> "${base}_fall"; Season.WINTER -> "${base}_snow"; else -> base }
        return if (game.assets.has(v)) v else base
    }

    private fun treeSprite(tree: String, stage: TreeStage): String = when (stage) {
        TreeStage.SAPLING -> "trees/${tree}_sapling"
        TreeStage.YOUNG -> "trees/${tree}_young"
        TreeStage.MATURE -> seasonal("trees/$tree")
        TreeStage.STUMP -> "trees/${tree}_stump"
        TreeStage.EMPTY -> ""
    }.let { if (it.isEmpty() || game.assets.has(it)) it else "deco/bush" }

    override fun drawGroundLayer(g: Gfx) {
        // Dim locked zones with a dark dither.
        for (r in 0 until rows) for (c in 0 until cols) {
            if (!world.zoneOpen(ForestLayout.zoneOf(c, r))) g.sprite("tiles/shadow", Iso.x(c, r).toFloat(), Iso.y(c, r).toFloat(), 200)
        }
        for (b in world.state.forest.branches) {
            val c = b / 1000; val r = b % 1000
            g.sprite(if (game.assets.has("trees/branch_pile")) "trees/branch_pile" else "deco/log", Iso.x(c, r).toFloat(), Iso.centerY(c, r).toFloat())
        }
    }

    override fun collect(list: DrawList) {
        val winter = world.time.season == Season.WINTER
        for ((sprite, x, y) in props) {
            var s = sprite
            if (winter && game.assets.has("${s}_snow")) s = "${s}_snow"
            if (sprite == "deco/tallgrass_a" && sin(time * 2.2f + x * 0.15f) > 0.3f) s = if (winter) "deco/tallgrass_b_snow" else "deco/tallgrass_b"
            list.add(y + x * 0.001f) { game.gfx.sprite(s, x, y) }
        }
        val g = game.gfx
        for (sp in world.forestSpots) {
            val x = Iso.x(sp.c, sp.r).toFloat()
            val y = Iso.centerY(sp.c, sp.r).toFloat()
            val open = world.zoneOpen(sp.zone)
            list.add(y) {
                if (!open) {
                    if (sp.kind != SpotKind.SLOT) g.sprite(if (game.assets.has("trees/brambles")) "trees/brambles" else "deco/bush_berry", x, y)
                    return@add
                }
                val st = world.spot(sp.index)
                when (sp.kind) {
                    SpotKind.TREE -> {
                        val stage = world.treeStage(sp.index)
                        val tree = st.tree ?: return@add
                        if (stage == TreeStage.EMPTY) return@add
                        val sx = shake[sp.index]?.let { if (((it * 60).toInt() % 2) == 0) 1f else -1f } ?: 0f
                        g.sprite(treeSprite(tree, stage), x + sx, y)
                        if (st.damage > 0) {
                            val hp = world.treeHp(sp.index)
                            val w = 16
                            g.rect((x - w / 2).toInt(), (y + 3).toInt(), (x + w / 2).toInt(), (y + 5).toInt(), 0xFF181425.toInt())
                            g.rect((x - w / 2).toInt(), (y + 3).toInt(), (x - w / 2 + w * (hp - st.damage) / hp).toInt(), (y + 5).toInt(), 0xFF63C74D.toInt())
                        }
                    }
                    SpotKind.BUSH -> g.sprite(if (st.stock > 0) pick("trees/berry_bush", "deco/bush_berry") else pick("trees/berry_bush_empty", "deco/bush"), x, y)
                    SpotKind.APPLE -> g.sprite(if (st.stock > 0) pick("trees/apple_tree", "deco/bush_berry") else pick("trees/apple_tree_empty", "deco/bush"), x, y)
                    SpotKind.MUSHROOM -> if (st.stock > 0) g.sprite(pick("trees/mushroom_spot", "deco/mushrooms"), x, y)
                    SpotKind.SLOT -> drawSlot(sp.index, x, y)
                }
            }
        }
    }

    private fun pick(sprite: String, fallback: String) = if (game.assets.has(sprite)) sprite else fallback

    private fun slotMachine(spotIndex: Int) = slotSpots.indexOfFirst { it.index == spotIndex }.let { machines.getOrNull(it) }

    private fun drawSlot(spotIndex: Int, x: Float, y: Float) {
        val def = slotMachine(spotIndex) ?: return
        val g = game.gfx
        if (world.isBuilt(def.id)) {
            g.sprite("tiles/shadow", x, y - 6)
            g.sprite(chrome.panels.buildingSprite(def.id), x, y - 12)
            if (world.trayCount(def.id) > 0) g.sprite("icons/ready", x + 10, y - 30 + sin(time * 5f) * 2f)
            else if (world.jobProgress(def.id) > 0f) g.sprite("fx/sparkle", x + 10, y - 26, (120 + sin(time * 3f) * 80).toInt())
        } else if (def.unlockLevel <= world.level) {
            g.sprite("icons_big/build", x, y - 10 + sin(time * 3f) * 1.5f, 200)
        }
    }

    override fun worldLights(out: MutableList<Lighting.Light>) {
        for (sp in slotSpots) if (slotMachine(sp.index)?.let { world.isBuilt(it.id) } == true) out += Lighting.Light(Iso.x(sp.c, sp.r).toFloat(), Iso.centerY(sp.c, sp.r).toFloat() - 6, 24)
    }

    override fun drawHud(g: Gfx) {
        chrome.drawTop()
        // Axe tier chip under the HUD.
        val y = game.engine.safeTop + hud.topHeight + 2
        ui.pill(2, y, 70, 14)
        g.sprite("icons/tool_axe", 10f, y + 7f)
        g.text(world.data.tools.names[world.toolTier(Tool.AXE)].uppercase() + " AXE", 18, y + 3, Gfx.WHITE)
        chrome.drawBottom()
    }

    // --- input ------------------------------------------------------------------
    override fun onTileTap(c: Int, r: Int) {
        if (world.state.forest.branches.contains(c * 1000 + r)) { world.collectBranch(c, r); return }
        val sp = spotAt[c * 1000 + r] ?: return
        if (!world.zoneOpen(sp.zone)) { offerZone(sp.zone); return }
        when (sp.kind) {
            SpotKind.TREE -> {
                val stage = world.treeStage(sp.index)
                if (stage == TreeStage.EMPTY) plantHere(sp.index) else world.chop(sp.index)
            }
            SpotKind.BUSH, SpotKind.APPLE, SpotKind.MUSHROOM -> if (!world.forage(sp.index)) hud.toast("Nothing to pick yet")
            SpotKind.SLOT -> {
                val def = slotMachine(sp.index) ?: return
                when {
                    world.isBuilt(def.id) -> if (world.trayCount(def.id) > 0) world.collect(def.id) else chrome.open(Chrome.Panel.MACHINE, def.id)
                    def.unlockLevel <= world.level -> chrome.open(Chrome.Panel.BUILD, def.id)
                    else -> hud.toast("${def.name} unlocks at level ${def.unlockLevel}")
                }
            }
        }
    }

    private fun plantHere(i: Int) {
        val native = world.forestSpots[i].native ?: return
        val seed = world.data.trees.getValue(native).seed
        val any = world.data.trees.values.map { it.seed }.firstOrNull { world.count(it) > 0 && (it == "golden_acorn") == (native == "ancient_oak") }
        val use = if (world.count(seed) > 0) seed else any
        if (use == null) { hud.toast("Plant a ${world.data.name(seed)} here"); return }
        world.plantTree(i, use)
    }

    private fun offerZone(zone: String) {
        val z = world.data.zones.first { it.id == zone }
        val blocker = world.zoneBlocker(zone)
        if (blocker != null && z.unlockLevel > world.level) { hud.toast("${z.name}: level ${z.unlockLevel}"); return }
        val cost = z.cost.entries.joinToString(", ") { (k, v) -> if (k == "coins") "${v}c" else "$v ${world.data.name(k)}" }
        chrome.confirm(z.name.uppercase(), "Clear the brambles for $cost?", "CLEAR") { world.unlockZone(zone) }
    }

    override fun onTileLongPress(c: Int, r: Int) {
        val sp = spotAt[c * 1000 + r] ?: return
        if (sp.kind == SpotKind.TREE && world.zoneOpen(sp.zone) && world.chopBlocker(sp.index) == null) {
            holdSpot = sp.index; holdTimer = 0f
            game.haptics.click()
        }
    }

    override fun onTilePaint(c: Int, r: Int) {
        if (world.state.forest.branches.contains(c * 1000 + r)) { world.collectBranch(c, r); return }
        val sp = spotAt[c * 1000 + r] ?: return
        if (!world.zoneOpen(sp.zone)) return
        when (sp.kind) {
            SpotKind.TREE -> if (holdSpot >= 0 && world.treeStage(sp.index) != TreeStage.EMPTY) holdSpot = sp.index
            SpotKind.BUSH, SpotKind.APPLE, SpotKind.MUSHROOM -> world.forage(sp.index)
            SpotKind.SLOT -> {}
        }
    }

    override fun onPaintEnd() { holdSpot = -1 }

    fun closePanel(): Boolean = chrome.close()

    // --- simulation events --------------------------------------------------------
    private fun center(i: Int): Pair<Float, Float> {
        val sp = world.forestSpots[i]
        return Iso.x(sp.c, sp.r).toFloat() to Iso.centerY(sp.c, sp.r).toFloat()
    }

    override fun onWorldEvent(e: WorldEvent) {
        if (chrome.onEvent(e)) return
        when (e) {
            is WorldEvent.Chopped -> {
                val (x, y) = center(e.spot)
                game.sfx.play("chop"); game.haptics.tick()
                shake[e.spot] = 0.12f
                fx.burst("fx/chip_wood", x, y - 14, if (e.crit) 7 else 4, speed = 55f)
                if (e.crit) { fx.floatText("CRIT!", x, y - 34, Gfx.GOLD); fx.shake(1.5f) }
            }
            is WorldEvent.Felled -> {
                val (x, y) = center(e.spot)
                game.sfx.play("tree_fall"); game.haptics.heavy(); fx.shake(2.5f)
                fx.burst("fx/leaf", x, y - 30, 12, speed = 60f, gravity = 40f, life = 1f)
                fx.burst("fx/dust", x, y, 6, speed = 30f, gravity = 20f)
                e.drops.entries.forEachIndexed { k, (item, n) -> fx.floatText("+$n", x - 8 + k * 6f, y - 30 - k * 9f, Gfx.WHITE, icon = "items/$item") }
                hud.tip("stump", "Stumps need a Stone axe. Upgrade tools at the Workbench on your farm.")
            }
            is WorldEvent.StumpCleared -> { val (x, y) = center(e.spot); game.sfx.play("rock_break"); fx.burst("fx/chip_wood", x, y - 4, 6, speed = 40f) }
            is WorldEvent.Foraged -> {
                game.sfx.play("pickup"); game.haptics.tick()
                if (e.spot >= 0) { val (x, y) = center(e.spot); fx.floatText("+${e.qty}", x, y - 20, Gfx.WHITE, icon = "items/${e.item}") }
            }
            is WorldEvent.TreePlanted -> { val (x, y) = center(e.spot); game.sfx.play("plant"); fx.burst("fx/dust", x, y, 4, speed = 25f, gravity = 30f) }
            is WorldEvent.BirdNest -> { game.sfx.play("gem"); hud.toast("Bird nest! +${e.qty} ${if (e.item == "coins") "coins" else world.data.name(e.item)}") }
            is WorldEvent.Blocked -> { game.sfx.play("error"); hud.toast(e.reason) }
            is WorldEvent.ZoneUnlocked -> invalidateGround()
            is WorldEvent.SeasonChanged -> invalidateGround()
            else -> {}
        }
    }
}
