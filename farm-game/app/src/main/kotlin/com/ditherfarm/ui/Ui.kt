package com.ditherfarm.ui

import android.graphics.Rect
import com.ditherfarm.engine.Gfx
import com.ditherfarm.engine.Haptics
import com.ditherfarm.engine.Sfx
import kotlin.math.abs

/**
 * Immediate-mode pixel UI. Screens call widget functions every frame while drawing;
 * a widget returns true on the frame it is clicked. Hit areas from the previous
 * frame decide whether a touch belongs to the HUD or to the world underneath.
 */
class Ui(val g: Gfx, private val sfx: Sfx, private val haptics: Haptics) {
    private val hits = ArrayList<Rect>()
    private val prevHits = ArrayList<Rect>()
    private var modalDepthPrev = 0
    private var modalDepth = 0

    // Pointer state, fed by the screen's hud* callbacks.
    private var down = false
    private var px = 0f
    private var py = 0f
    private var startX = 0f
    private var startY = 0f
    private var released = false
    private var activeId: Any? = null
    private var dragged = false

    // Scroll regions keyed by id.
    private val scroll = HashMap<Any, Float>()
    private var scrollId: Any? = null
    private var scrollGrab = 0f

    /**
     * While true, widgets still draw but take no input: they sit under an open panel, a scene or a
     * popup, which are drawn later in the frame. A tap there falls through to the panel's modal
     * (so it counts as a tap outside it) instead of pressing the hidden-behind button.
     */
    var inert = false

    fun beginFrame() {
        prevHits.clear(); prevHits.addAll(hits); hits.clear()
        modalDepthPrev = modalDepth; modalDepth = 0
        inert = false
    }

    fun endFrame() {
        if (released) { released = false; activeId = null; scrollId = null; dragged = false }
    }

    /** True if the touch at (x, y) should go to the UI (a widget or an open modal). */
    fun wants(x: Float, y: Float): Boolean = modalDepthPrev > 0 || prevHits.any { it.contains(x.toInt(), y.toInt()) }

    fun pointerDown(x: Float, y: Float) { down = true; px = x; py = y; startX = x; startY = y; released = false; dragged = false }
    fun pointerMove(x: Float, y: Float) {
        if (abs(x - startX) + abs(y - startY) > 4f) dragged = true
        px = x; py = y
    }
    fun pointerUp(x: Float, y: Float) { down = false; px = x; py = y; released = true }

    private fun inside(x: Int, y: Int, w: Int, h: Int) = px >= x && px < x + w && py >= y && py < y + h
    private fun startedInside(x: Int, y: Int, w: Int, h: Int) = startX >= x && startX < x + w && startY >= y && startY < y + h

    /** Generic clickable area. Returns true on release inside after pressing inside. */
    fun clickable(id: Any, x: Int, y: Int, w: Int, h: Int): Boolean {
        if (inert) return false
        hits.add(Rect(x, y, x + w, y + h))
        if (down && activeId == null && startedInside(x, y, w, h) && scrollId == null) activeId = id
        if (released && activeId == id && !dragged && inside(x, y, w, h)) {
            sfx.play("click", 0.5f)
            haptics.tick()
            return true
        }
        return false
    }

    fun pressed(id: Any) = down && activeId == id && !dragged

    /** Blocks the world below while drawn; tap outside [x,y,w,h] returns true (dismiss). */
    fun modal(x: Int, y: Int, w: Int, h: Int, dim: Boolean = true): Boolean {
        modalDepth++
        if (dim) g.rect(0, 0, g.canvas.width, g.canvas.height, 0x99181425.toInt())
        return released && activeId == null && !dragged && !startedInside(x, y, w, h) && !inside(x, y, w, h)
    }

    fun panel(x: Int, y: Int, w: Int, h: Int, dark: Boolean = false) {
        if (dark) g.slice9("ui/panel_dark", x, y, x + w, y + h, 3) else g.slice9("ui/panel", x, y, x + w, y + h, 6)
    }

    fun pill(x: Int, y: Int, w: Int, h: Int = 12) = g.slice9("ui/pill", x, y, x + w, y + h, 5)

    /** Pixel button. [style]: green, gold, red, blue, wood, grey. */
    fun button(id: Any, x: Int, y: Int, w: Int, h: Int, label: String? = null, icon: String? = null, style: String = "green", enabled: Boolean = true): Boolean {
        val click = clickable(id, x, y, w, h) && enabled
        val isDown = pressed(id) && enabled
        val st = if (enabled) style else "grey"
        g.slice9(if (isDown) "ui/btn_${st}_down" else "ui/btn_$st", x, y, x + w, y + h, 5)
        val dy = if (isDown) 1 else -1
        val cy = y + h / 2 + dy
        val textW = label?.let { g.textWidth(it) } ?: 0
        val iconW = if (icon != null) 15 else 0
        val total = textW + iconW + if (icon != null && label != null) 2 else 0
        var cx = x + (w - total) / 2
        if (icon != null) { g.sprite(icon, (cx + 7).toFloat(), cy.toFloat()); cx += iconW + 2 }
        if (label != null) g.text(label, cx, cy - 4, if (enabled) Gfx.WHITE else Gfx.GREY)
        return click
    }

    /** Small round icon button (no frame); used for HUD shortcuts. */
    fun iconButton(id: Any, icon: String, cx: Int, cy: Int, size: Int = 28, badge: Int = 0): Boolean {
        val x = cx - size / 2
        val y = cy - size / 2
        val click = clickable(id, x, y, size, size)
        val s = if (pressed(id)) 0.9f else 1f
        g.spriteScaled(icon, cx.toFloat(), cy.toFloat(), s, s)
        if (badge > 0) {
            g.sprite("ui/badge", (x + size - 5).toFloat(), (y + 5).toFloat())
            if (badge > 1) g.text(badge.coerceAtMost(9).toString(), x + size - 5, y + 1, Gfx.WHITE, align = 0, shadow = false)
        }
        return click
    }

    fun slot(id: Any, x: Int, y: Int, icon: String?, count: Int = -1, selected: Boolean = false, size: Int = 20): Boolean {
        val click = clickable(id, x, y, size, size)
        g.slice9(if (selected) "ui/slot_sel" else "ui/slot", x, y, x + size, y + size, 4)
        if (icon != null) {
            val k = if (pressed(id)) 0.85f else 1f
            g.spriteScaled(icon, (x + size / 2).toFloat(), (y + size / 2).toFloat(), k, k)
        }
        if (count >= 0) g.text(compact(count), x + size - 1, y + size - 8, Gfx.WHITE, align = 1)
        return click
    }

    /** Progress bar; [color] is green, blue, gold, red or purple. */
    fun bar(x: Int, y: Int, w: Int, frac: Float, color: String = "green", h: Int = 7) {
        g.slice9("ui/bar_bg", x, y, x + w, y + h, 3)
        val fw = ((w - 2) * frac.coerceIn(0f, 1f)).toInt()
        if (fw > 0) g.slice9("ui/bar_$color", x + 1, y + 1, x + 1 + fw, y + h - 1, 1)
    }

    fun toggle(id: Any, x: Int, y: Int, on: Boolean): Boolean {
        val click = clickable(id, x, y, 20, 10)
        g.sprite(if (on) "ui/toggle_on" else "ui/toggle_off", x.toFloat(), y.toFloat())
        return click
    }

    fun tabs(id: Any, x: Int, y: Int, w: Int, labels: List<String>, selected: Int): Int {
        val tw = w / labels.size
        var result = selected
        labels.forEachIndexed { i, label ->
            val tx = x + i * tw
            if (clickable(id to i, tx, y, tw, 14)) result = i
            g.slice9(if (i == selected) "ui/tab_on" else "ui/tab", tx, y, tx + tw, y + 14, 5)
            g.text(label, tx + tw / 2, y + 3, if (i == selected) Gfx.INK else Gfx.WHITE, align = 0, outline = if (i == selected) 0 else Gfx.INK, shadow = false)
        }
        return result
    }

    /**
     * Vertical scroll region. Returns the current offset (px); draw content shifted by it
     * and clip with canvas.clipRect. Dragging inside scrolls instead of clicking.
     */
    fun scroll(id: Any, x: Int, y: Int, w: Int, h: Int, contentH: Int): Int {
        hits.add(Rect(x, y, x + w, y + h))
        val max = (contentH - h).coerceAtLeast(0).toFloat()
        var off = scroll[id] ?: 0f
        if (down && dragged && startedInside(x, y, w, h) && (scrollId == null || scrollId == id)) {
            if (scrollId == null) { scrollId = id; scrollGrab = off + py; activeId = null }
            off = scrollGrab - py
        }
        off = off.coerceIn(0f, max)
        scroll[id] = off
        if (max > 0f) {
            val th = (h * h / contentH.toFloat()).toInt().coerceAtLeast(8)
            val ty = y + ((h - th) * (off / max)).toInt()
            g.rect(x + w - 3, y, x + w - 1, y + h, 0x55181425)
            g.rect(x + w - 3, ty, x + w - 1, ty + th, 0xCCFFFFFF.toInt())
        }
        return off.toInt()
    }

    fun resetScroll(id: Any) { scroll.remove(id) }

    companion object {
        /** 1234 → "1234", 12345 → "12K", 1234567 → "1.2M" (fits small slots). */
        fun compact(n: Int): String = when {
            n < 10_000 -> n.toString()
            n < 1_000_000 -> "${n / 1000}K"
            else -> "${n / 1_000_000}.${(n / 100_000) % 10}M"
        }
    }
}
