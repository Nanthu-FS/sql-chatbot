package com.ditherfarm.ui

import com.ditherfarm.Game
import com.ditherfarm.engine.Gfx
import com.ditherfarm.engine.Iso
import com.ditherfarm.game.kidStage
import com.ditherfarm.game.romance
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The family on the farm (round 7, Parts 4 and 5). The spouse keeps shop hours in the village
 * (VillageWalk has them from 9 to 5) and spends mornings and evenings at home, strolling round the
 * farmhouse. The kid: a baby on a quilt by the door, then a toddler and a child who run after the
 * pet (or potter about the house when there's no pet). Tapping the spouse opens their panel; tapping
 * the kid gets a giggle and a few hearts.
 */
class FamilyWalk(private val game: Game) {
    private class Walker {
        var x = 0f; var y = 0f
        var tx = 0f; var ty = 0f
        var wait = 0f; var anim = 0f
        var moving = false; var left = false; var back = false
        var placed = false
    }

    private val world get() = game.world
    private val spouse = Walker()
    private val kid = Walker()
    private var time = 0f
    private var giggle = 0f

    /** Off shift: before nine and after five (and never at night, when everyone's indoors). */
    fun spouseHome() = world.romance.spouse != null && world.time.hour.let { it in 6.5f..9f || it in 17f..21f }

    private fun night() = world.time.hour.let { it < 6.5f || it >= 21f }

    private fun home(): Pair<Float, Float> {
        val (c, r) = FarmMap.FARMHOUSE
        return Iso.x(c + 1, r + 1).toFloat() to Iso.centerY(c + 1, r + 1).toFloat()
    }

    private fun wander(w: Walker, dt: Float, speed: Float, radius: Float, cx: Float, cy: Float) {
        if (!w.placed) { w.x = cx; w.y = cy; w.tx = cx; w.ty = cy; w.placed = true }
        val d = hypot(w.tx - w.x, w.ty - w.y)
        w.moving = d > 1f
        if (!w.moving) {
            w.wait -= dt
            if (w.wait <= 0f) {
                w.wait = 2f + Random.nextFloat() * 4f
                w.tx = cx + (Random.nextFloat() - 0.5f) * radius * 2f
                w.ty = cy + (Random.nextFloat() - 0.3f) * radius
            }
            return
        }
        step(w, dt, speed, d)
    }

    private fun step(w: Walker, dt: Float, speed: Float, d: Float) {
        val s = min(d, speed * dt)
        val dx = (w.tx - w.x) / d; val dy = (w.ty - w.y) / d
        w.x += dx * s; w.y += dy * s
        w.anim += dt
        w.moving = true
        if (abs(dx) > 0.05f) w.left = dx < 0
        w.back = dy < -0.4f
    }

    fun update(s: IsoMapScreen, dt: Float) {
        time += dt
        giggle = (giggle - dt).coerceAtLeast(0f)
        val (hx, hy) = home()
        if (spouseHome()) wander(spouse, dt, 22f, 26f, hx + 14f, hy + 8f) else spouse.placed = false
        when (world.kidStage()) {
            -1, 0 -> kid.placed = false
            else -> {
                val pet = game.pet.position(s)
                val speed = if (world.kidStage() == 1) 20f else 30f
                if (pet != null && hypot(pet.first - hx, pet.second - hy) < 260f) {
                    // Chase the pet, stopping just short of it.
                    if (!kid.placed) { kid.x = hx + 30f; kid.y = hy; kid.placed = true }
                    kid.tx = pet.first - 14f; kid.ty = pet.second + 2f
                    val d = hypot(kid.tx - kid.x, kid.ty - kid.y)
                    if (d > 4f) step(kid, dt, speed, d) else kid.moving = false
                } else wander(kid, dt, speed, 24f, hx + 36f, hy + 2f)
            }
        }
    }

    fun collect(list: DrawList, s: IsoMapScreen) {
        if (night()) return
        val g = game.gfx
        val (hx, hy) = home()
        val r = world.romance
        if (spouseHome()) r.spouse?.let { id ->
            val w = spouse
            if (s.inView(w.x, w.y)) list.add(w.y) {
                val pose = if (w.moving) "walk${(w.anim * 6).toInt() and 1}" else "idle"
                g.sprite("people/v_${id}_${if (w.back) "ne" else "se"}_$pose", w.x, w.y, flipX = w.left)
                if (time % 6f < 1.2f) g.sprite("fx/heart", w.x + 6f, w.y - 32f - (time % 6f) * 6f, ((1f - (time % 6f) / 1.2f) * 255).toInt())
            }
        }
        val stage = world.kidStage()
        if (stage < 0) return
        val gs = if (r.kidFemale) "g" else "b"
        if (stage == 0) {
            val x = hx - 16f; val y = hy + 12f
            if (s.inView(x, y)) list.add(y) { g.sprite("people/kid_baby_${gs}_idle", x, y + if (giggle > 0f) -abs(sin(giggle * 12f)) * 2f else 0f) }
            return
        }
        val w = kid
        val name = if (stage == 1) "toddler" else "child"
        if (s.inView(w.x, w.y)) list.add(w.y) {
            val pose = if (w.moving) "walk${(w.anim * (if (stage == 1) 7 else 8)).toInt() and 1}" else "idle"
            val hop = if (giggle > 0f) -abs(sin(giggle * 12f)) * 3f else if (!w.moving && stage == 2) -abs(sin(time * 5f)) * 1.5f else 0f
            g.sprite("people/kid_${name}_${gs}_$pose", w.x, w.y + hop, flipX = w.left)
        }
    }

    /** "spouse", "kid" or null for a tap at world point (wx, wy). */
    fun at(wx: Float, wy: Float): String? {
        if (night()) return null
        if (spouseHome() && abs(wx - spouse.x) <= 11f && wy in spouse.y - 28f..spouse.y + 6f) return "spouse"
        val stage = world.kidStage()
        if (stage < 0) return null
        val (hx, hy) = home()
        val (kx, ky) = if (stage == 0) (hx - 16f) to (hy + 12f) else kid.x to kid.y
        if (abs(wx - kx) <= 10f && wy in ky - 22f..ky + 6f) { giggle = 1f; return "kid" }
        return null
    }
}
