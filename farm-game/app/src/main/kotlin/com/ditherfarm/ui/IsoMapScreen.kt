package com.ditherfarm.ui

import android.graphics.Bitmap
import com.ditherfarm.Game
import com.ditherfarm.engine.Camera
import com.ditherfarm.engine.Fx
import com.ditherfarm.engine.Gfx
import com.ditherfarm.engine.GroundPainter
import com.ditherfarm.engine.Iso
import com.ditherfarm.engine.Lighting
import com.ditherfarm.engine.Raised
import com.ditherfarm.engine.Screen
import com.ditherfarm.engine.Sprite
import com.ditherfarm.engine.Terrain
import com.ditherfarm.engine.Weather
import kotlin.math.floor

/** World-space draw calls sorted back-to-front by ground depth (iso y of the object's base). */
class DrawList {
    private class Entry(var depth: Float = 0f, var block: () -> Unit = {})
    private val pool = ArrayList<Entry>()
    private var n = 0

    fun add(depth: Float, block: () -> Unit) {
        if (n == pool.size) pool.add(Entry())
        pool[n].depth = depth; pool[n].block = block; n++
    }

    fun flush() {
        val live = pool.subList(0, n)
        live.sortWith(BY_DEPTH)
        for (e in live) e.block()
        n = 0
    }

    private companion object {
        /** Same order as sortBy (a stable sort), but the depths compare as primitives instead of boxed floats. */
        val BY_DEPTH = Comparator<Entry> { a, b -> a.depth.compareTo(b.depth) }
    }
}

/** Alpha from which a sprite pixel counts as solid for taps (cast shadows are fainter). */
private const val SOLID_ALPHA = 200

/**
 * Base for the Farm, Forest and Mine screens: a painted isometric island with a
 * camera (pan, fling, whole-step pinch zoom), depth-sorted objects, world FX,
 * day/night lighting, weather and an immediate-mode HUD on top.
 *
 * Subclasses describe the map ([terrain]), add standing objects in [collect],
 * draw the HUD in [drawHud] and react to [onTileTap] / [onTilePaint].
 */
abstract class IsoMapScreen(protected val game: Game, private val groundSeed: Int) : Screen() {
    abstract val cols: Int
    abstract val rows: Int

    val cam = Camera()
    val fx = Fx()
    val weather = Weather()
    protected val ui get() = game.ui
    private val lighting = Lighting()
    private val painter = GroundPainter(groundSeed)
    private var ground: Bitmap? = null
    /** Season the ground was last painted for; -1 forces a repaint. */
    private var groundKey = -1
    private val drawList = DrawList()
    private val lights = ArrayList<Lighting.Light>()
    private val pick = IntArray(2)
    private var lastPaintC = Int.MIN_VALUE
    private var lastPaintR = Int.MIN_VALUE
    protected var time = 0f
        private set

    /** Heights (buffer px) of HUD bars that the camera should not hide the map under. */
    open val hudTop = 30
    open val hudBottom = 44

    /** Terrain for map cell (c, r); drives the painted ground. Called for -[margin] .. cols + [margin] - 1. */
    abstract fun terrain(c: Int, r: Int): Terrain

    /** Painted-only tiles round the map (cave walls round a mine floor); taps there are ignored. */
    open val margin = 0

    /** Cliff faces for raised ground; null for a flat map. */
    open val rock: IntArray? = null

    /** Sparse bright pixels in the cliff faces (ore, crystals). */
    open val rockAccents = IntArray(0)

    /** Height level of tile (c, r) when [rock] is set (0 = ground). */
    open fun level(c: Int, r: Int) = 0

    /** True to darken the ground with [fade]. */
    open val fades = false

    /** 0..1 darkness for the ground at world point (wx, wy). */
    open fun fade(wx: Float, wy: Float) = 0f

    /** Terrain ramps that replace the defaults (e.g. rock colours per mine band). */
    open fun palette(): Map<Terrain, IntArray> = emptyMap()

    /** Earth cliff under the map's front edges, in px. */
    open val edgeCliff = 14

    /** Fills the screen behind the map (black round a cave); null leaves the default. */
    open val voidColor: Int? = null

    /** 0 spring .. 3 winter, used for ground colours. */
    abstract fun seasonIndex(): Int

    /** Clock hour for lighting (return 12 for always-lit places). */
    abstract fun hour(): Float

    /** Clock hour for the colour grade, or null for none (underground). */
    open fun gradeHour(): Float? = hour()

    /** Add standing world objects (plots, crops, trees, rocks, machines, props). */
    abstract fun collect(list: DrawList)

    /** Draw on the ground before objects (selection cursors, shadows, water glints). */
    open fun drawGroundLayer(g: Gfx) {}

    /** Light sources in world pixels (lanterns, furnaces, mine lamps). Add new Light objects each call. */
    open fun worldLights(out: MutableList<Lighting.Light>) {}

    /** Glowing parts drawn over the night tint in world space; [night] 0..1 is how dark it is. */
    open fun drawEmissive(g: Gfx, night: Float) {}

    abstract fun drawHud(g: Gfx)

    /** World-pixel tap before tile picking (e.g. corners between plots); return true if handled. */
    open fun onWorldTap(wx: Float, wy: Float): Boolean = false
    /** A toast from the pet (its gift, a wag); the adoption panel when the strays are tapped. */
    open fun petToast(msg: String) {}
    open fun openPetPanel() {}
    open fun openPetInfo() {}
    open fun onTileTap(c: Int, r: Int) {}
    open fun onTileLongPress(c: Int, r: Int) {}
    open fun onTilePaint(c: Int, r: Int) {}
    open fun onPaintEnd() {}

    /** Forces the ground bitmap to repaint (e.g. map edited). Season changes repaint automatically. */
    fun invalidateGround() { groundKey = -1 }

    override fun enter() {
        cam.setBounds(mapBounds())
        syncView()
    }

    /** World bounds of the painted map, margin included. */
    protected fun mapBounds(): IntArray {
        val m = margin
        val b = Iso.bounds(cols + 2 * m, rows + 2 * m)
        b[1] -= m * Iso.TH; b[3] -= m * Iso.TH
        return b
    }

    private fun syncView() = cam.setView(game.engine.w, game.engine.h, hudTop + game.engine.safeTop, hudBottom + game.engine.safeBottom)

    fun centerOnTile(c: Int, r: Int) = cam.centerOn(Iso.x(c, r).toFloat(), Iso.centerY(c, r).toFloat())

    override fun update(dt: Float) {
        time += dt
        syncView()
        cam.update(dt)
        game.pet.update(this, dt)
        fx.update(dt)
        weather.update(dt, game.engine.w, game.engine.h)
    }

    override fun draw(g: Gfx) {
        val c = g.canvas
        val key = seasonIndex()
        val m = margin
        if (ground == null || key != groundKey) {
            ground?.recycle()
            ground = painter.paint(
                cols + 2 * m, rows + 2 * m, key, edgeCliff,
                raised = rock?.let { ramp -> Raised({ cc, rr -> level(cc - m, rr - m) }, ramp, rockAccents) },
                fade = if (fades) { wx, wy -> fade(wx, wy - m * Iso.TH) } else null,
                palette = palette(),
            ) { cc, rr -> terrain(cc - m, rr - m) }
            groundKey = key
        }
        voidColor?.let { c.drawColor(it) }
        val shake = fx.shakeOffset(time)
        c.save()
        c.translate((-cam.snapX() + shake).toFloat(), -cam.snapY().toFloat())
        c.scale(cam.zoom.toFloat(), cam.zoom.toFloat())
        g.inWorld = true
        c.drawBitmap(ground!!, -painter.originX.toFloat(), -(painter.originY + m * Iso.TH).toFloat(), g.paint)
        drawGroundLayer(g)
        hitCount = 0
        collect(drawList)
        game.pet.collect(drawList, this)
        drawList.flush()
        fx.drawWorld(g)
        g.inWorld = false
        c.restore()

        lights.clear()
        worldLights(lights)
        for (l in lights) {
            l.x = (l.x - cam.x) * cam.zoom
            l.y = (l.y - cam.y) * cam.zoom
            l.radius *= cam.zoom
        }
        gradeHour()?.let { lighting.grade(c, game.engine.w, game.engine.h, it) }
        val tint = lighting.ambient(hour())
        lighting.apply(c, game.engine.w, game.engine.h, tint, lights, weather.flash)
        // Things that glow (lit windows, lamp glass) go over the night tint.
        val night = ((lighting.darkness(tint) - 0.25f) / 0.4f).coerceIn(0f, 1f)
        if (night > 0f) {
            c.save()
            c.translate((-cam.snapX() + shake).toFloat(), -cam.snapY().toFloat())
            c.scale(cam.zoom.toFloat(), cam.zoom.toFloat())
            g.inWorld = true
            drawEmissive(g, night)
            g.inWorld = false
            c.restore()
        }
        weather.draw(g, game.engine.w, game.engine.h)
        fx.drawText(g, { (it - cam.x) * cam.zoom }, { (it - cam.y) * cam.zoom })

        ui.beginFrame()
        drawHud(g)
        ui.endFrame()
    }

    /** True if a world point is on screen (with [pad] world px of margin for tall sprites). */
    fun inView(wx: Float, wy: Float, pad: Float = 40f): Boolean =
        wx >= cam.x - pad && wx <= cam.x + cam.viewWidth + pad && wy >= cam.y - pad && wy <= cam.y + cam.viewHeight + pad * 2

    /** Height of [sprite] above its pivot, for bubbles over it; [default] if the art is missing. */
    fun spriteTop(sprite: String, default: Int = 30): Float =
        if (game.assets.has(sprite)) game.assets[sprite].ay.toFloat() else default.toFloat()

    /** World pixel → buffer pixel, e.g. to anchor a HUD bubble over a tile. */
    fun toBufferX(wx: Float) = (wx - cam.x) * cam.zoom
    fun toBufferY(wy: Float) = (wy - cam.y) * cam.zoom

    /** A sprite drawn this frame that taps on its solid pixels should reach, and the tile it stands on. */
    private class Hit {
        var c = 0
        var r = 0
        var sprite: Sprite? = null
        var x = 0f
        var y = 0f
        var depth = 0f
    }

    private val hits = ArrayList<Hit>()
    private var hitCount = 0

    /**
     * Marks [sprite], drawn this frame with its pivot at world (x, y), as standing on tile (c, r): a
     * tap, hold or slide on its solid pixels picks that tile, even where tall art (a tree's crown, a
     * ripe crop) rises over the tiles behind it. The frontmost sprite wins. Call it from [collect].
     */
    protected fun hitSprite(c: Int, r: Int, sprite: String, x: Float, y: Float, depth: Float = y) {
        if (!game.assets.has(sprite)) return
        if (hitCount == hits.size) hits += Hit()
        hits[hitCount++].also { it.c = c; it.r = r; it.sprite = game.assets[sprite]; it.x = x; it.y = y; it.depth = depth }
    }

    private fun solidAt(h: Hit, wx: Float, wy: Float): Boolean {
        val s = h.sprite ?: return false
        val lx = floor(wx).toInt() - (Math.round(h.x) - s.ax)
        val ly = floor(wy).toInt() - (Math.round(h.y) - s.ay)
        if (lx !in 0 until s.w || ly !in 0 until s.h) return false
        // Cast shadows are see-through (alpha about 90-150) and do not count.
        return (s.bmp.getPixel(lx, ly) ushr 24) >= SOLID_ALPHA
    }

    /** The tile under buffer point (x, y) into [pick]: the frontmost registered sprite there, else the ground tile. */
    private fun tileAt(x: Float, y: Float): Boolean {
        val wx = cam.toWorldX(x)
        val wy = cam.toWorldY(y)
        var best: Hit? = null
        for (k in 0 until hitCount) {
            val h = hits[k]
            if ((best == null || h.depth >= best.depth) && solidAt(h, wx, wy)) best = h
        }
        if (best != null) { pick[0] = best.c; pick[1] = best.r; return true }
        Iso.pick(wx, wy, pick)
        return pick[0] in 0 until cols && pick[1] in 0 until rows
    }

    /**
     * A health bar over something being chopped or mined: [left] of [total], centred on [x] with its
     * bottom at world [y] (just above the art). Dark frame, green fill turning gold then red as it drops.
     */
    protected fun drawHpBar(g: Gfx, x: Float, y: Float, left: Int, total: Int) {
        if (total <= 0) return
        val w = 22
        val l = x.toInt() - w / 2
        val t = y.toInt() - 5
        g.rect(l - 1, t - 1, l + w + 1, t + 5, Gfx.INK)
        g.rect(l, t, l + w, t + 4, 0xFF3A4466.toInt())
        val f = left.coerceIn(0, total).toFloat() / total
        val fill = when { f > 0.5f -> 0xFF63C74D.toInt(); f > 0.25f -> 0xFFFEAE34.toInt(); else -> 0xFFE43B44.toInt() }
        g.rect(l, t, l + Math.round(w * f), t + 4, fill)
        g.rect(l, t, l + Math.round(w * f), t + 1, 0x55FFFFFF)
    }

    // --- input routing: HUD first, then the world ---------------------------------
    override fun hudDown(x: Float, y: Float): Boolean {
        if (!ui.wants(x, y)) return false
        ui.pointerDown(x, y)
        return true
    }
    override fun hudMove(x: Float, y: Float) = ui.pointerMove(x, y)
    override fun hudUp(x: Float, y: Float) = ui.pointerUp(x, y)

    override fun tap(x: Float, y: Float) {
        game.pet.tap(this, cam.toWorldX(x), cam.toWorldY(y)) { openPetPanel() }?.let { if (it.isNotEmpty()) petToast(it); return }
        if (onWorldTap(cam.toWorldX(x), cam.toWorldY(y))) return
        if (tileAt(x, y)) onTileTap(pick[0], pick[1])
    }
    override fun longPress(x: Float, y: Float) {
        lastPaintC = Int.MIN_VALUE
        if (game.pet.longPress(this, cam.toWorldX(x), cam.toWorldY(y))) { openPetInfo(); return }
        if (tileAt(x, y)) onTileLongPress(pick[0], pick[1])
    }
    override fun paint(x: Float, y: Float) {
        if (!tileAt(x, y)) return
        if (pick[0] == lastPaintC && pick[1] == lastPaintR) return
        lastPaintC = pick[0]; lastPaintR = pick[1]
        onTilePaint(pick[0], pick[1])
    }
    override fun paintEnd() { lastPaintC = Int.MIN_VALUE; onPaintEnd() }
    override fun panStart() = cam.stop()
    override fun pan(dx: Float, dy: Float) = cam.panBy(dx, dy)
    override fun fling(vx: Float, vy: Float) = cam.fling(vx, vy)
    override fun pinch(steps: Int, fx: Float, fy: Float) { cam.zoomAt(steps, fx, fy) }
}
