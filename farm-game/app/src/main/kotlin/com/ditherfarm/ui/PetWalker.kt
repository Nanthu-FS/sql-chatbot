package com.ditherfarm.ui

import com.ditherfarm.Game
import com.ditherfarm.engine.Iso
import com.ditherfarm.game.PetKind
import com.ditherfarm.game.collectPetGift
import com.ditherfarm.game.hasPet
import com.ditherfarm.game.pet
import com.ditherfarm.game.petOffered
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The pet on screen (round 5). It trots after the view on the farm, forest and mine (placed afresh
 * beside the view when the player changes screen, so it comes along), sits when it catches up and
 * pads about now and then. A gift bubble shows when it has found something; a tap collects it, or
 * gets a happy wag (hearts). Before adoption, the two strays sit by the well on the farm.
 */
class PetWalker(private val game: Game) {
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

    fun update(s: IsoMapScreen, dt: Float) {
        time += dt
        hearts = (hearts - dt).coerceAtLeast(0f)
        if (!world.hasPet) return
        val cx = s.cam.x + s.cam.viewWidth * 0.5f + 30f
        val cy = s.cam.y + s.cam.viewHeight * 0.6f
        if (s !== screen) { screen = s; x = cx - 50f; y = cy + 16f; tx = cx; ty = cy }
        // Follow the view; once there, wander a little every few seconds.
        if (hypot(cx - tx, cy - ty) > 28f) { tx = cx; ty = cy; idle = 0f }
        idle += dt
        if (!moving && idle > 5f) { idle = 0f; tx = cx + Random.nextInt(-24, 25); ty = cy + Random.nextInt(-10, 11) }
        val dx = tx - x
        val dy = ty - y
        val d = hypot(dx, dy)
        moving = d > 1.5f
        if (moving) {
            val step = min(d, (if (d > 60f) 120f else 40f) * dt)
            x += dx / d * step; y += dy / d * step
            if (abs(dx) > 0.5f) faceLeft = dx < 0
            faceBack = dy < 0 && abs(dy) * 2 > abs(dx)
            anim += dt
        }
    }

    private fun sprite(kind: PetKind) = "pets/${kind.id}_" + when {
        !moving -> "sit"
        faceBack -> "ne_${(anim * 8).toInt() and 1}"
        else -> "se_${(anim * 8).toInt() and 1}"
    }

    fun collect(list: DrawList, s: IsoMapScreen) {
        val g = game.gfx
        if (world.petOffered && s is FarmScreen) {
            val (sx, sy) = straysAt()
            list.add(sy) {
                g.sprite("pets/dog_sit", sx - 8f, sy)
                g.sprite("pets/cat_sit", sx + 8f, sy, flipX = true)
                g.sprite("icons/heart", sx, sy - 18f + sin(time * 4f) * 1.5f)
            }
            return
        }
        val kind = world.pet.kind ?: return
        if (s !== screen) return
        list.add(y) {
            g.sprite(sprite(kind), x, y, flipX = faceLeft)
            // Found something: the sparkle that marks a ripe crop, bobbing over its head.
            if (world.pet.gift.isNotEmpty()) g.sprite("fx/sparkle", x + 2f, y - 15f + sin(time * 5f) * 1.5f)
            if (hearts > 0f) g.sprite("fx/heart", x + 2f, y - 14f - (1.2f - hearts) * 10f, (255 * hearts.coerceAtMost(1f)).toInt())
        }
    }

    /** Where the strays wait for the player: in the yard beside the well. */
    private fun straysAt(): Pair<Float, Float> {
        val (c, r) = FarmMap.WELL
        return Iso.x(c, r) + 26f to Iso.centerY(c, r) + 16f
    }

    /**
     * A tap at world (wx, wy): on the strays opens the adoption panel ([adopt]); on the pet takes its
     * gift or gets a wag. Returns the toast to show, "" if handled silently, or null if the tap missed.
     */
    fun tap(s: IsoMapScreen, wx: Float, wy: Float, adopt: () -> Unit): String? {
        if (world.petOffered && s is FarmScreen) {
            val (sx, sy) = straysAt()
            if (abs(wx - sx) <= 18f && wy in sy - 16f..sy + 3f) { adopt(); return "" }
            return null
        }
        if (!world.hasPet || s !== screen) return null
        if (abs(wx - x) > 9f || wy !in y - 14f..y + 3f) return null
        val p = world.pet
        hearts = 1.2f
        game.sfx.play("collect")
        val got = world.collectPetGift() ?: return if (p.gift.isNotEmpty()) "" else "${p.name} ${p.kind!!.happy}"
        return "${p.name} found " + got.entries.joinToString(", ") { (k, n) -> if (k == "coins") "$n coins" else "$n ${world.data.name(k)}" }
    }
}
