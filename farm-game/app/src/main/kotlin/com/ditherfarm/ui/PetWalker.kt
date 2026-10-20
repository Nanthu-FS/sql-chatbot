package com.ditherfarm.ui

import com.ditherfarm.Game
import com.ditherfarm.engine.Gfx
import com.ditherfarm.engine.Iso
import com.ditherfarm.game.PetKind
import com.ditherfarm.game.collectPetGift
import com.ditherfarm.game.hasPet
import com.ditherfarm.game.pet
import com.ditherfarm.game.petFetched
import com.ditherfarm.game.petOffered
import com.ditherfarm.game.petPetted
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The pet on screen (rounds 5 and 6). It trots after the view on the farm, forest and mine (placed
 * afresh beside the view when the player changes screen, so it comes along), sits when it catches
 * up and pads about now and then. A sparkle shows when it has found a gift; a tap collects it.
 * With no gift a tap throws a stick: the pet fetches it, and on the way back a meter swings over its
 * head; a tap while the marker is in the green is a great catch. A long press opens its page. It
 * wears its accessory. Before adoption the two strays sit in front of the well on the farm.
 */
class PetWalker(private val game: Game) {
    private enum class Mode { FOLLOW, FETCH_OUT, FETCH_BACK }

    private val world get() = game.world
    private var screen: IsoMapScreen? = null
    private var x = 0f
    private var y = 0f
    private var tx = 0f
    private var ty = 0f
    private var moving = false
    private var faceLeft = false
    private var faceBack = false
    private var anim = 0f
    private var idle = 0f
    private var hearts = 0f
    private var time = 0f
    private var mode = Mode.FOLLOW
    private var homeX = 0f
    private var homeY = 0f
    private var stickX = 0f
    private var stickY = 0f
    private var stickFly = 0f
    private var meter = 0f

    fun update(s: IsoMapScreen, dt: Float) {
        time += dt
        hearts = (hearts - dt).coerceAtLeast(0f)
        if (!world.hasPet) return
        val cx = s.cam.x + s.cam.viewWidth * 0.5f + 30f
        val cy = s.cam.y + s.cam.viewHeight * 0.6f
        if (s !== screen) { screen = s; x = cx - 50f; y = cy + 16f; tx = cx; ty = cy; mode = Mode.FOLLOW }
        when (mode) {
            Mode.FOLLOW -> {
                // Follow the view; once there, wander a little every few seconds.
                if (hypot(cx - tx, cy - ty) > 28f) { tx = cx; ty = cy; idle = 0f }
                idle += dt
                if (!moving && idle > 5f) { idle = 0f; tx = cx + Random.nextInt(-24, 25); ty = cy + Random.nextInt(-10, 11) }
            }
            Mode.FETCH_OUT -> {
                stickFly = (stickFly + dt / 0.6f).coerceAtMost(1f)
                tx = stickX; ty = stickY
                if (!moving && stickFly >= 1f) { mode = Mode.FETCH_BACK; tx = homeX; ty = homeY; meter = 0f }
            }
            Mode.FETCH_BACK -> {
                meter += dt * 0.9f
                tx = homeX; ty = homeY
                if (!moving) { mode = Mode.FOLLOW; s.petToast("${world.pet.name} drops the stick at your feet") }
            }
        }
        val dx = tx - x
        val dy = ty - y
        val d = hypot(dx, dy)
        moving = d > 1.5f
        if (moving) {
            // Back from fetch at a trot, so the catch meter has time to swing (playtest round 6).
            val speed = when { mode == Mode.FETCH_OUT -> 70f; mode == Mode.FETCH_BACK -> 36f; d > 60f -> 120f; else -> 40f }
            val step = min(d, speed * dt)
            x += dx / d * step; y += dy / d * step
            if (abs(dx) > 0.5f) faceLeft = dx < 0
            faceBack = dy < 0 && abs(dy) * 2 > abs(dx)
            anim += dt
        }
    }

    private val pose get() = when { !moving -> "sit"; faceBack -> "ne"; else -> "se" }

    private fun sprite(kind: PetKind, pose: String) = "pets/${kind.id}_" + when (pose) {
        "sit" -> "sit"
        else -> "${pose}_${(anim * 8).toInt() and 1}"
    }

    /** Head top and neck of each pose, from the pivot, facing right (pets.py). */
    private fun anchor(pose: String, neck: Boolean): Pair<Int, Int> = when (pose) {
        "sit" -> if (neck) 0 to -5 else 2 to -9
        "ne" -> if (neck) 1 to -5 else 3 to -8
        else -> if (neck) 2 to -4 else 3 to -8
    }

    /**
     * The pet and what it wears at (px, py), mirrored when [left] (only at scale 1); the pet page
     * draws it sitting at 3x with [pose] "sit".
     */
    fun drawPet(g: Gfx, kind: PetKind, px: Float, py: Float, left: Boolean, scale: Float = 1f, pose: String = this.pose, acc: String? = world.pet.accessory) {
        val name = sprite(kind, pose)
        if (scale == 1f) g.sprite(name, px, py, flipX = left) else g.spriteScaled(name, px, py, scale, scale)
        acc ?: return
        val neck = acc == "bandana"
        val (ax, ay) = anchor(pose, neck)
        val ox = (if (left && scale == 1f) -ax else ax) * scale
        val oy = (ay + if (neck) 0 else 1) * scale
        val art = "pets/acc_$acc"
        if (scale == 1f) g.sprite(art, px + ox, py + oy, flipX = left) else g.spriteScaled(art, px + ox, py + oy, scale, scale)
    }

    fun collect(list: DrawList, s: IsoMapScreen) {
        val g = game.gfx
        if (world.petOffered && s is FarmScreen) {
            val (sx, sy) = straysAt()
            list.add(sy) {
                g.sprite("pets/dog_sit", sx - 8f, sy)
                g.sprite("pets/cat_sit", sx + 8f, sy, flipX = true)
                g.sprite("fx/heart", sx, sy - 16f + sin(time * 4f) * 1.5f)
            }
            // Until one is adopted an arrow marks them (they wait off the starting view).
            list.add(Float.MAX_VALUE) { g.sprite("icons/arrow_down", sx, sy - 26f + sin(time * 6f) * 2f) }
            return
        }
        val kind = world.pet.kind ?: return
        if (s !== screen) return
        if (mode == Mode.FETCH_OUT) {
            // The stick: thrown in an arc, then lying where it landed until the pet gets there.
            val t = stickFly
            val sx = homeX + (stickX - homeX) * t
            val sy = homeY + (stickY - homeY) * t - sin(t * Math.PI.toFloat()) * 24f
            list.add(stickY) { g.sprite("pets/stick", sx, sy) }
        }
        // Drawn over the world: the pet follows the view and must never be lost behind a building.
        list.add(y + ON_TOP) {
            drawPet(g, kind, x, y, faceLeft)
            if (mode == Mode.FETCH_BACK) {
                g.sprite("pets/stick", x + if (faceLeft) -5f else 5f, y - 5f, flipX = faceLeft)
                drawMeter(g, x, y - 20f)
            } else if (world.pet.gift.isNotEmpty()) g.sprite("fx/sparkle", x + 2f, y - 15f + sin(time * 5f) * 1.5f)
            if (hearts > 0f) g.sprite("fx/heart", x + 2f, y - 14f - (1.2f - hearts) * 10f, (255 * hearts.coerceAtMost(1f)).toInt())
        }
    }

    /** Where the marker sweeps (0..1, back and forth at an even speed), and whether it is in the green. */
    private val marker get() = (meter % 2f).let { if (it < 1f) it else 2f - it }
    private val inGreen get() = marker in GREEN_FROM..GREEN_TO

    private fun drawMeter(g: Gfx, cx: Float, cy: Float) {
        val w = 22
        val l = cx.toInt() - w / 2
        val t = cy.toInt()
        g.rect(l - 1, t - 1, l + w + 1, t + 4, Gfx.INK)
        g.rect(l, t, l + w, t + 3, 0xFF8B9BB4.toInt())
        g.rect(l + (w * GREEN_FROM).toInt(), t, l + (w * GREEN_TO).toInt(), t + 3, 0xFF63C74D.toInt())
        val mx = l + (w * marker).toInt()
        g.rect(mx, t - 2, mx + 1, t + 5, Gfx.WHITE)
    }

    /** Where the strays wait for the player: in the open yard in front of the well. */
    private fun straysAt(): Pair<Float, Float> {
        val (c, r) = FarmMap.WELL
        return Iso.x(c, r) - 14f to Iso.centerY(c, r) + 22f
    }

    private fun onPet(wx: Float, wy: Float, reach: Float = 9f) = abs(wx - x) <= reach && wy in y - 14f..y + 3f

    /**
     * A tap at world (wx, wy): on the strays opens the adoption panel ([adopt]); on the pet takes its
     * gift, or throws a stick, or (on the way back) tries the catch. Returns the toast to show, ""
     * if handled silently, or null if the tap missed.
     */
    fun tap(s: IsoMapScreen, wx: Float, wy: Float, adopt: () -> Unit): String? {
        if (world.petOffered && s is FarmScreen) {
            val (sx, sy) = straysAt()
            if (abs(wx - sx) <= 18f && wy in sy - 16f..sy + 3f) { adopt(); return "" }
            return null
        }
        if (!world.hasPet || s !== screen) return null
        val p = world.pet
        if (mode == Mode.FETCH_BACK) {
            if (!onPet(wx, wy, 16f)) return null
            val great = inGreen
            mode = Mode.FOLLOW; tx = x; ty = y
            hearts = 1.2f
            if (!great) return "Not quite! ${p.name} wants to go again"
            game.sfx.play("coin")
            val bonus = world.petFetched(true)
            return "Great catch!" + (bonus["coins"]?.let { " +$it coins" } ?: "")
        }
        if (mode == Mode.FETCH_OUT || !onPet(wx, wy)) return null
        hearts = 1.2f
        game.sfx.play("collect")
        world.petPetted()
        if (p.gift.isNotEmpty()) {
            val got = world.collectPetGift() ?: return ""
            return "${p.name} found " + got.entries.joinToString(", ") { (k, n) -> if (k == "coins") "$n coins" else "$n ${world.data.name(k)}" }
        }
        // Nothing found today: play fetch. The stick lands a little way off, inside the view.
        homeX = x; homeY = y
        stickX = x + (if (Random.nextBoolean()) 1 else -1) * Random.nextInt(36, 60)
        stickY = y + Random.nextInt(-14, 15)
        stickFly = 0f
        mode = Mode.FETCH_OUT
        return "${p.name} ${p.kind!!.happy}. Fetch!"
    }

    /** A long press on the pet opens its page; true if it was on the pet. */
    fun longPress(s: IsoMapScreen, wx: Float, wy: Float): Boolean =
        world.hasPet && s === screen && onPet(wx, wy)

    private companion object {
        const val GREEN_FROM = 0.33f
        const val GREEN_TO = 0.67f
        const val ON_TOP = 100_000f
    }
}
