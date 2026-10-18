package com.ditherfarm.ui

import android.graphics.Bitmap
import com.ditherfarm.Game
import com.ditherfarm.engine.Camera
import com.ditherfarm.engine.Fx
import com.ditherfarm.engine.Gfx
import com.ditherfarm.engine.GroundPainter
import com.ditherfarm.engine.Iso
import com.ditherfarm.engine.Lighting
import com.ditherfarm.engine.Screen
import com.ditherfarm.engine.Terrain
import com.ditherfarm.engine.Weather

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
        live.sortBy { it.depth }
        for (e in live) e.block()
        n = 0
    }
}

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
    private var groundKey = ""
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

    /** Terrain for map cell (c, r); drives the painted ground. */
    abstract fun terrain(c: Int, r: Int): Terrain

    /** 0 spring .. 3 winter, used for ground colours. */
    abstract fun seasonIndex(): Int

    /** Clock hour for lighting (return 12 for always-lit places). */
    abstract fun hour(): Float

    /** Add standing world objects (plots, crops, trees, rocks, machines, props). */
    abstract fun collect(list: DrawList)

    /** Draw on the ground before objects (selection cursors, shadows, water glints). */
    open fun drawGroundLayer(g: Gfx) {}

    /** Light sources in world pixels (lanterns, furnaces, mine lamps). Add new Light objects each call. */
    open fun worldLights(out: MutableList<Lighting.Light>) {}

    abstract fun drawHud(g: Gfx)

    open fun onTileTap(c: Int, r: Int) {}
    open fun onTileLongPress(c: Int, r: Int) {}
    open fun onTilePaint(c: Int, r: Int) {}
    open fun onPaintEnd() {}

    /** Forces the ground bitmap to repaint (e.g. map edited). Season changes repaint automatically. */
    fun invalidateGround() { groundKey = "" }

    override fun enter() {
        cam.setBounds(Iso.bounds(cols, rows))
        syncView()
    }

    private fun syncView() = cam.setView(game.engine.w, game.engine.h, hudTop + game.engine.safeTop, hudBottom + game.engine.safeBottom)

    fun centerOnTile(c: Int, r: Int) = cam.centerOn(Iso.x(c, r).toFloat(), Iso.centerY(c, r).toFloat())

    override fun update(dt: Float) {
        time += dt
        syncView()
        cam.update(dt)
        fx.update(dt)
        weather.update(dt, game.engine.w, game.engine.h)
    }

    override fun draw(g: Gfx) {
        val c = g.canvas
        val key = "${seasonIndex()}"
        if (ground == null || key != groundKey) {
            ground?.recycle()
            ground = painter.paint(cols, rows, seasonIndex()) { cc, rr -> terrain(cc, rr) }
            groundKey = key
        }
        val shake = fx.shakeOffset(time)
        c.save()
        c.translate((-cam.snapX() + shake).toFloat(), -cam.snapY().toFloat())
        c.scale(cam.zoom.toFloat(), cam.zoom.toFloat())
        c.drawBitmap(ground!!, -painter.originX.toFloat(), 0f, g.paint)
        drawGroundLayer(g)
        collect(drawList)
        drawList.flush()
        fx.drawWorld(g)
        c.restore()

        lights.clear()
        worldLights(lights)
        for (l in lights) {
            l.x = (l.x - cam.x) * cam.zoom
            l.y = (l.y - cam.y) * cam.zoom
            l.radius *= cam.zoom
        }
        lighting.apply(c, game.engine.w, game.engine.h, lighting.ambient(hour()), lights, weather.flash)
        weather.draw(g, game.engine.w, game.engine.h)
        fx.drawText(g, { (it - cam.x) * cam.zoom }, { (it - cam.y) * cam.zoom })

        ui.beginFrame()
        drawHud(g)
        ui.endFrame()
    }

    /** World pixel → buffer pixel, e.g. to anchor a HUD bubble over a tile. */
    fun toBufferX(wx: Float) = (wx - cam.x) * cam.zoom
    fun toBufferY(wy: Float) = (wy - cam.y) * cam.zoom

    private fun tileAt(x: Float, y: Float): Boolean {
        Iso.pick(cam.toWorldX(x), cam.toWorldY(y), pick)
        return pick[0] in 0 until cols && pick[1] in 0 until rows
    }

    // --- input routing: HUD first, then the world ---------------------------------
    override fun hudDown(x: Float, y: Float): Boolean {
        if (!ui.wants(x, y)) return false
        ui.pointerDown(x, y)
        return true
    }
    override fun hudMove(x: Float, y: Float) = ui.pointerMove(x, y)
    override fun hudUp(x: Float, y: Float) = ui.pointerUp(x, y)

    override fun tap(x: Float, y: Float) { if (tileAt(x, y)) onTileTap(pick[0], pick[1]) }
    override fun longPress(x: Float, y: Float) {
        lastPaintC = Int.MIN_VALUE
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
