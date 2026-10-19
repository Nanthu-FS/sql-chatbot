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
import com.ditherfarm.game.Pointer
import com.ditherfarm.game.pointer
import com.ditherfarm.game.toolTier
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
import com.ditherfarm.game.smokeHive
import com.ditherfarm.game.forestSpots
import com.ditherfarm.game.isBuilt
import com.ditherfarm.game.jobProgress
import com.ditherfarm.game.plantTree
import com.ditherfarm.game.spot
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
    /** Machines on the forest slots, in unlock order (the building list is fixed game data). */
    private val machines by lazy { world.buildingsOn("forest") }
    private val slotSpots by lazy { world.forestSpots.filter { it.kind == SpotKind.SLOT } }

    /** Per-spot shake timer after a hit. */
    private val shake = HashMap<Int, Float>()
    private var holdSpot = -1
    private var holdTimer = 0f
    /** Scenery; [dark] marks the forest wall, drawn shaded so it reads as background. */
    private class Prop(val sprite: String, val x: Float, val y: Float, val dark: Boolean)
    private var props = ArrayList<Prop>()
    private val shade = android.graphics.LightingColorFilter(0x8C8C9E, 0)

    override fun terrain(c: Int, r: Int): Terrain = when {
        ForestLayout.isTrail(c, r) -> Terrain.PATH
        ForestLayout.zoneOf(c, r) != null -> Terrain.GRASS
        else -> Terrain.FOREST
    }

    override val rock = intArrayOf(0xFF181425.toInt(), 0xFF262B44.toInt(), 0xFF3A4466.toInt(), 0xFF5A6988.toInt(), 0xFF8B9BB4.toInt(), 0xFFC0CBDC.toInt())
    /**
     * Rock cliffs: rising along the two back edges, and ledges in the wall behind the clearings
     * (never just in front of one, where they would cover the spots).
     */
    override fun level(c: Int, r: Int): Int {
        if (!ForestLayout.isWall(c, r)) return 0
        val d = minOf(c, r)
        val bump = if (Noise.rand(c, r, 331) < 0.3f) 1 else 0
        when {
            d <= 1 -> return 3
            d == 2 -> return 2 + bump
            d == 3 -> return 1 + bump
        }
        if (nearFront(c, r)) return 0
        val n = Noise.fbm(c * 0.21f, r * 0.21f, 332)
        return if (n > 0.66f) 2 else if (n > 0.58f) 1 else 0
    }

    /** A higher tile just in front would wrongly draw under this tile's props. */
    private fun shadowed(c: Int, r: Int): Boolean {
        val l = level(c, r)
        return listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1, 1 to 2, 2 to 2).any { (dc, dr) -> level(c + dc, r + dr) > l }
    }

    override fun seasonIndex() = world.time.season.ordinal
    override fun hour() = world.time.hour

    override fun enter() {
        super.enter()
        cam.minZoom = 2
        cam.maxZoom = 3
        // Keep the whole view on painted ground, like the farm.
        val n = ForestLayout.SIZE
        cam.setDiamond(0f, n * Iso.TH / 2f, (n - 2) * Iso.TW / 2f, (n - 2) * Iso.TH / 2f)
        recenter()
        weather.onThunder = { game.sfx.play("thunder") }
        if (props.isEmpty()) scatter()
        hud.tip("forest", "Tap a tree to chop it. Each swing uses 2 energy. Hold to keep swinging.")
        if (world.state.forest.hive >= 0) hud.tip("hive", "A wild beehive! Tap its tree to smoke the bees out for honey.")
    }

    /** The Edge Grove, just up the trail from the entrance. */
    fun recenter() = centerOnTile(33, 33)

    private val wallTrees = listOf("pine", "oak", "birch", "pine", "oak", "pine")

    /** Wall tile just in front of a clearing: only low shrubs, so trees never hide the spots behind. */
    private fun nearFront(c: Int, r: Int) = (1..3).any { k -> (0..k).any { j -> !ForestLayout.isWall(c - j, r - (k - j)) } }

    private fun scatter() {
        for (r in 0 until rows) for (c in 0 until cols) {
            if (spotAt.containsKey(c * 1000 + r) || ForestLayout.isTrail(c, r)) continue
            if (ForestLayout.isWall(c, r)) {
                // Forest wall: trees (on the cliff tops too), shrubs near the clearings, a few boulders.
                if (shadowed(c, r)) continue
                val lift = level(c, r) * com.ditherfarm.engine.Raised.LEVEL_PX
                val roll = Noise.rand(c, r, 311)
                val x = Iso.x(c, r) + (Noise.rand(c, r, 312) - 0.5f) * 14f
                val y = Iso.centerY(c, r) + (Noise.rand(c, r, 313) - 0.5f) * 5f - lift
                val treeChance = if (lift > 0) 0.55f else 0.78f
                val sprite = when {
                    nearFront(c, r) -> if (roll < 0.22f) "deco/bush" else if (roll < 0.28f) "deco/rock_big" else null
                    roll < treeChance -> "trees/" + wallTrees[(Noise.rand(c, r, 314) * wallTrees.size).toInt().coerceIn(0, wallTrees.size - 1)]
                    roll < 0.84f -> "deco/bush"
                    roll < 0.87f -> "deco/rock_big"
                    else -> null
                } ?: continue
                props += Prop(sprite, x, y, dark = true)
                continue
            }
            val roll = Noise.rand(c, r, 301)
            val sprite = when {
                roll < 0.18f -> "deco/tallgrass_a"
                roll < 0.24f -> "deco/bush"
                roll < 0.27f -> "deco/rock"
                roll < 0.29f -> "deco/log"
                else -> null
            } ?: continue
            props += Prop(sprite, Iso.x(c, r) + (Noise.rand(c, r, 302) - 0.5f) * 12f, Iso.centerY(c, r) + (Noise.rand(c, r, 303) - 0.5f) * 5f, dark = false)
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
        // Dim locked clearings (and the trail up to them) with a dark dither.
        for (r in 0 until rows) for (c in 0 until cols) {
            val z = ForestLayout.zoneOf(c, r) ?: continue
            val x = Iso.x(c, r).toFloat(); val y = Iso.y(c, r).toFloat()
            if (!inView(x, y, TILE_PAD)) continue
            if (!world.zoneOpen(z)) g.sprite("tiles/shadow", x, y, 200)
        }
        for (b in world.state.forest.branches) {
            val c = b / 1000; val r = b % 1000
            g.sprite(if (game.assets.has("trees/branch_pile")) "trees/branch_pile" else "deco/log", Iso.x(c, r).toFloat(), Iso.centerY(c, r).toFloat())
        }
    }

    override fun collect(list: DrawList) {
        val winter = world.time.season == Season.WINTER
        for (p in props) {
            val x = p.x; val y = p.y
            if (!inView(x, y, 70f)) continue
            var s = if (p.sprite.startsWith("trees/")) seasonal(p.sprite) else p.sprite
            if (winter && game.assets.has("${s}_snow")) s = "${s}_snow"
            if (p.sprite == "deco/tallgrass_a" && sin(time * 2.2f + x * 0.15f) > 0.3f) s = if (winter) "deco/tallgrass_b_snow" else "deco/tallgrass_b"
            if (p.dark) list.add(y + x * 0.001f) { game.gfx.spriteFiltered(s, x, y, shade) } else list.add(y + x * 0.001f) { game.gfx.sprite(s, x, y) }
        }
        val g = game.gfx
        for (sp in world.forestSpots) {
            val x = Iso.x(sp.c, sp.r).toFloat()
            val y = Iso.centerY(sp.c, sp.r).toFloat()
            // Off-screen spots add nothing: their closures and draws would all land outside the buffer.
            if (!inView(x, y, SPOT_PAD)) continue
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
                        if (sp.index == world.state.forest.hive) {
                            g.sprite(pick("trees/beehive", "icons/gift"), x + 6, y - 22)
                            g.sprite("ui/bubble_tail", x, y - 66 + sin(time * 4f) * 2f)
                            g.sprite("items/honey", x, y - 74 + sin(time * 4f) * 2f)
                            if ((time * 3).toInt() % 2 == 0) g.rect((x + 10 + sin(time * 7f) * 5).toInt(), (y - 30 + sin(time * 5f) * 3).toInt(), (x + 11 + sin(time * 7f) * 5).toInt(), (y - 29 + sin(time * 5f) * 3).toInt(), Gfx.GOLD)
                        }
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
                    SpotKind.ROCK -> g.sprite(if (st.stock > 0) "deco/rock_big" else "deco/rock", x, y, if (st.stock > 0) 255 else 140)
                    SpotKind.SLOT -> drawSlot(sp.index, x, y)
                }
            }
        }
        // Grandpa's notebook points at a tree, a loose rock or the Sawmill's spot.
        val target = when (world.pointer) {
            Pointer.TREE -> world.forestSpots.firstOrNull { sp ->
                sp.kind == SpotKind.TREE && world.zoneOpen(sp.zone) && world.treeStage(sp.index) == TreeStage.MATURE &&
                    world.spot(sp.index).tree?.let { (world.data.trees[it]?.minAxe ?: 0) <= world.toolTier(Tool.AXE) } == true
            }
            Pointer.ROCK -> world.forestSpots.firstOrNull { it.kind == SpotKind.ROCK && world.zoneOpen(it.zone) && world.spot(it.index).stock > 0 }
            Pointer.SAWMILL -> slotSpots.getOrNull(machines.indexOfFirst { it.id == "sawmill" })
            else -> null
        }
        target?.let { sp ->
            val x = Iso.x(sp.c, sp.r).toFloat(); val y = Iso.centerY(sp.c, sp.r).toFloat()
            val h = if (sp.kind == SpotKind.TREE) 56f else 24f
            list.add(Float.MAX_VALUE) { g.sprite("icons/arrow_down", x, y - h + sin(time * 6f) * 2f) }
        }
    }

    private fun pick(sprite: String, fallback: String) = if (game.assets.has(sprite)) sprite else fallback

    private fun slotMachine(spotIndex: Int) = slotSpots.indexOfFirst { it.index == spotIndex }.let { machines.getOrNull(it) }

    private fun drawSlot(spotIndex: Int, x: Float, y: Float) {
        val def = slotMachine(spotIndex) ?: return
        val g = game.gfx
        if (world.isBuilt(def.id)) {
            chrome.panels.drawBuilding(def.id, x, y, world.time.season == Season.WINTER)
            if (world.trayCount(def.id) > 0) g.sprite("icons/ready", x + 10, y - spriteTop("buildings/${def.id}") - 2 + sin(time * 5f) * 2f)
            else if (world.jobProgress(def.id) > 0f) g.sprite("fx/sparkle", x + 10, y - spriteTop("buildings/${def.id}") + 2, (120 + sin(time * 3f) * 80).toInt())
        } else if (def.unlockLevel <= world.level) {
            g.sprite("icons_big/build", x, y - 10 + sin(time * 3f) * 1.5f, 200)
        }
    }

    override fun worldLights(out: MutableList<Lighting.Light>) {
        for (sp in slotSpots) if (slotMachine(sp.index)?.let { world.isBuilt(it.id) } == true) out += Lighting.Light(Iso.x(sp.c, sp.r).toFloat(), Iso.centerY(sp.c, sp.r).toFloat() - 6, 24)
    }

    override fun drawHud(g: Gfx) {
        chrome.drawTop(questBelow = 16)
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
                when {
                    sp.index == world.state.forest.hive -> chrome.confirm("WILD BEEHIVE", "Smoke the bees out for honey? Uses a torch, or 4 energy without one.", "SMOKE") { world.smokeHive() }
                    stage == TreeStage.EMPTY -> plantHere(sp.index)
                    else -> world.chop(sp.index)
                }
            }
            SpotKind.BUSH, SpotKind.APPLE, SpotKind.MUSHROOM -> if (!world.forage(sp.index)) hud.toast("Nothing to pick yet")
            SpotKind.ROCK -> if (!world.forage(sp.index)) hud.toast("No loose stones left here today")
            SpotKind.SLOT -> {
                val def = slotMachine(sp.index) ?: return
                when {
                    world.isBuilt(def.id) -> when {
                        world.trayCount(def.id) > 0 -> world.collect(def.id)
                        def.id == "logging_hut" || def.id == "tree_nursery" -> chrome.open(Chrome.Panel.SHED, def.id)
                        else -> chrome.open(Chrome.Panel.MACHINE, def.id)
                    }
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
            SpotKind.BUSH, SpotKind.APPLE, SpotKind.MUSHROOM, SpotKind.ROCK -> world.forage(sp.index)
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
            is WorldEvent.HiveSmoked -> {
                val (x, y) = center(e.spot)
                game.sfx.play("pickup"); fx.burst("fx/dust", x + 6, y - 22, 8, speed = 20f, gravity = -20f, life = 1f)
                fx.floatText("+3", x, y - 40, Gfx.GOLD, icon = "items/honey")
                if (e.seed) hud.toast("The bees left a Wildflower Seed!")
            }
            is WorldEvent.Stung -> { game.sfx.play("error"); game.haptics.heavy(); fx.shake(2f); hud.toast("Ouch! Bees! -10 energy. Smoke hives first.") }
            is WorldEvent.BirdNest -> { game.sfx.play("gem"); hud.toast("Bird nest! +${e.qty} ${if (e.item == "coins") "coins" else world.data.name(e.item)}") }
            is WorldEvent.Blocked -> { game.sfx.play("error"); hud.toast(e.reason) }
            is WorldEvent.ZoneUnlocked -> invalidateGround()
            is WorldEvent.SeasonChanged -> invalidateGround()
            else -> {}
        }
    }

    companion object {
        /** World px of margin around a view for tile shadows (one tile is 32 x 16). */
        private const val TILE_PAD = 48f
        /** World px of margin for spots: the beehive's bubble and honey rise about 84 px over its tree. */
        private const val SPOT_PAD = 96f
    }
}
