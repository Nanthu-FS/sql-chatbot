package com.ditherfarm.engine

import android.graphics.Bitmap
import kotlin.math.floor

/** Deterministic value noise and fractal noise, used for organic ground detail. */
object Noise {
    fun hash(x: Int, y: Int, seed: Int): Int {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        return h xor (h ushr 16)
    }

    /** 0..1 */
    fun rand(x: Int, y: Int, seed: Int): Float = (hash(x, y, seed) and 0xFFFFFF) / 16777215f

    fun value(x: Float, y: Float, seed: Int): Float {
        val x0 = floor(x).toInt(); val y0 = floor(y).toInt()
        val fx = x - x0; val fy = y - y0
        val sx = fx * fx * (3 - 2 * fx); val sy = fy * fy * (3 - 2 * fy)
        val a = rand(x0, y0, seed); val b = rand(x0 + 1, y0, seed)
        val c = rand(x0, y0 + 1, seed); val d = rand(x0 + 1, y0 + 1, seed)
        return (a + (b - a) * sx) + ((c + (d - c) * sx) - (a + (b - a) * sx)) * sy
    }

    fun fbm(x: Float, y: Float, seed: Int, octaves: Int = 3): Float {
        var sum = 0f; var amp = 0.5f; var f = 1f; var norm = 0f
        repeat(octaves) { i ->
            sum += value(x * f, y * f, seed + i * 31) * amp
            norm += amp; amp *= 0.5f; f *= 2f
        }
        return sum / norm
    }
}

enum class Terrain { NONE, GRASS, DIRT, PATH, SAND, WATER, FOREST, STONE, CAVE, GRAVEL, COBBLE, PAVED }

/**
 * Stepped rock terrain painted with the ground: [level] per tile (0 = ground, each level
 * [LEVEL_PX] high) and the [rock] ramp (dark to light) for the cliff faces. Tops keep the tile's
 * own terrain colours, so a raised forest floor reads as a ledge of the same ground.
 */
class Raised(val level: (Int, Int) -> Int, val rock: IntArray, val accents: IntArray = IntArray(0)) {
    companion object { const val LEVEL_PX = 12 }
}

/**
 * Paints a whole isometric map into one bitmap, pixel by pixel: dithered colour
 * patches from world-space noise, wavy borders between terrains, scattered
 * detail motifs, shore foam and earth cliffs along the front edges so the map
 * reads as a floating diorama. Repaint only when the map or season changes.
 */
class GroundPainter(private val seed: Int = 7) {
    /** Bitmap pixel (0,0) is world point (-originX, -originY); originY makes room for raised ground. */
    var originX = 0
        private set
    var originY = 0
        private set

    private val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    /**
     * [raised] adds stepped cliffs; [fade] (world x, y of the ground point) → 0..1 dithers the
     * ground toward black, e.g. round a cave; [palette] overrides terrain ramps (mine bands).
     */
    fun paint(
        cols: Int, rows: Int, season: Int, cliff: Int = 14, raised: Raised? = null,
        fade: ((Float, Float) -> Float)? = null, palette: Map<Terrain, IntArray> = emptyMap(),
        terrainAt: (Int, Int) -> Terrain,
    ): Bitmap {
        originX = rows * Iso.HW
        val w = (cols + rows) * Iso.HW
        val mapH = (cols + rows) * Iso.HH
        val h = mapH + cliff
        val px = IntArray(w * h)
        val ter = arrayOfNulls<Terrain>(w * h)
        val pick = IntArray(2)
        val ramps = Ramps(season, palette)

        // Pass 1: terrain per pixel with a noise-warped lookup for wavy borders.
        for (y in 0 until mapH) for (x in 0 until w) {
            val wx = (x - originX).toFloat() + 0.5f
            val wy = y + 0.5f
            Iso.pick(wx, wy, pick)
            if (pick[0] !in 0 until cols || pick[1] !in 0 until rows) continue
            val jx = (Noise.value(wx * 0.11f, wy * 0.22f, seed + 5) - 0.5f) * 9f
            val jy = (Noise.value(wx * 0.11f + 40f, wy * 0.22f, seed + 6) - 0.5f) * 4.5f
            Iso.pick(wx + jx, wy + jy, pick)
            val c = pick[0].coerceIn(0, cols - 1)
            val r = pick[1].coerceIn(0, rows - 1)
            ter[y * w + x] = terrainAt(c, r)
        }

        // Pass 2: dithered colour ramps from low-frequency noise.
        for (y in 0 until mapH) for (x in 0 until w) {
            val t = ter[y * w + x] ?: continue
            val ramp = ramps.of(t)
            val wx = (x - originX).toFloat()
            if (t == Terrain.COBBLE || t == Terrain.PAVED) { px[y * w + x] = laid(t, wx, y.toFloat(), ramp); continue }
            val n = Noise.fbm(wx * 0.045f, y * 0.09f, seed + t.ordinal * 17) * 0.85f + Noise.rand(x, y, seed) * 0.15f
            val f = n * (ramp.size - 1) * 1.25f - 0.12f
            val i = floor(f).toInt().coerceIn(0, ramp.size - 1)
            val frac = f - floor(f)
            val up = bayer[(y and 3) * 4 + (x and 3)] < (frac * 16).toInt()
            px[y * w + x] = ramp[(if (up) i + 1 else i).coerceAtMost(ramp.size - 1)]
        }

        // Pass 3: borders. Darken land at terrain changes; foam on water edges.
        val out = px.copyOf()
        for (y in 1 until mapH - 1) for (x in 1 until w - 1) {
            val t = ter[y * w + x] ?: continue
            val up = ter[(y - 1) * w + x]
            val left = ter[y * w + x - 1]
            val right = ter[y * w + x + 1]
            val down = ter[(y + 1) * w + x]
            val changed = listOf(up, left, right, down).any { it != null && it != t }
            if (!changed) continue
            out[y * w + x] = when {
                t == Terrain.WATER -> if ((x + y) % 3 != 0) WHITE else CYAN
                t == Terrain.GRASS && (up != null && up != t) -> ramps.of(t)[0]
                t == Terrain.PATH || t == Terrain.DIRT || t == Terrain.SAND -> ramps.of(t)[0]
                else -> ramps.of(t)[0]
            }
        }

        // Pass 4: detail motifs on a jittered grid.
        for (gy in 0 until mapH step 5) for (gx in 0 until w step 7) {
            val ox = gx + (Noise.rand(gx, gy, seed + 9) * 6).toInt()
            val oy = gy + (Noise.rand(gx, gy, seed + 10) * 4).toInt()
            if (ox !in 2 until w - 3 || oy !in 2 until mapH - 2) continue
            val t = ter[oy * w + ox] ?: continue
            val roll = Noise.rand(ox, oy, seed + 11)
            val meadow = Noise.fbm((ox - originX) * 0.03f, oy * 0.06f, seed + 12)
            val motif = ramps.motif(t, roll, meadow) ?: continue
            if (motif.all { (dx, dy, _) -> ter.getOrNull((oy + dy) * w + ox + dx) == t }) {
                for ((dx, dy, color) in motif) out[(oy + dy) * w + ox + dx] = color
            }
        }

        // Pass 5: earth cliffs below the two front edges of the map.
        val bottom = IntArray(w) { -1 }
        for (x in 0 until w) for (y in mapH - 1 downTo 0) if (ter[y * w + x] != null) { bottom[x] = y; break }
        val cornerX = originX + Iso.x(cols - 1, rows - 1)
        for (x in 0 until w) {
            val b = bottom[x]
            if (b < 0) continue
            for (k in 1..cliff) {
                val y = b + k
                if (y >= h) break
                val leftFace = x < cornerX
                val stripe = (k + (if (leftFace) x / 2 else -x / 2)) % 6
                val base = if (leftFace) CLIFF_L else CLIFF_R
                var c = when {
                    k <= 2 -> if (leftFace) GRASS_LIP else GRASS_LIP_D
                    stripe == 0 -> if (leftFace) CLIFF_LINE_L else CLIFF_LINE_R
                    bayer[(y and 3) * 4 + (x and 3)] < k / 2 -> if (leftFace) CLIFF_L_D else CLIFF_R_D
                    else -> base
                }
                if (k == cliff || (k > 3 && Noise.rand(x, y, seed + 13) > 0.97f)) c = INK
                out[y * w + x] = c
            }
        }
        // Raised ground goes on a taller canvas: originY rows above world y = 0.
        var maxLevel = 0
        if (raised != null) for (r in 0 until rows) for (c in 0 until cols) maxLevel = maxOf(maxLevel, raised.level(c, r))
        originY = if (maxLevel > 0) maxLevel * Raised.LEVEL_PX + WOB + 2 else 0
        val h2 = h + originY
        var img = out
        if (originY > 0) {
            img = IntArray(w * h2)
            System.arraycopy(out, 0, img, originY * w, out.size)
        }
        // Ground point under each pixel, for the fade (raised pixels note their own).
        val groundY = if (fade != null) FloatArray(w * h2) { Float.NaN } else null
        if (raised != null && maxLevel > 0) paintRaised(img, w, h2, cols, rows, raised, ramps, terrainAt, groundY)
        if (fade != null) {
            for (y in 0 until h2) for (x in 0 until w) {
                val i = y * w + x
                if (img[i] == 0) continue
                val gy = groundY!![i].let { if (it.isNaN()) (y - originY).toFloat() else it }
                val d = fade(x - originX + 0.5f, gy + 0.5f)
                if (d > 0f && bayer[(y and 3) * 4 + (x and 3)] < (d * 17f).toInt()) img[i] = INK
            }
        }
        // Ink outline around the whole island.
        val fin = img.copyOf()
        for (y in 0 until h2) for (x in 0 until w) {
            if (img[y * w + x] != 0) continue
            val n = (x > 0 && img[y * w + x - 1] != 0) || (x < w - 1 && img[y * w + x + 1] != 0) ||
                (y > 0 && img[(y - 1) * w + x] != 0) || (y < h2 - 1 && img[(y + 1) * w + x] != 0)
            if (n) fin[y * w + x] = INK
        }
        return Bitmap.createBitmap(fin, w, h2, Bitmap.Config.ARGB_8888)
    }

    /** Wavy cliff tops: extra height (0..WOB px) that follows world x, the same for every tile. */
    private fun wob(wx: Int) = (Noise.value(wx * 0.09f, 3.3f, seed + 21) * WOB).toInt()

    /**
     * Paints each raised tile as a prism, back to front: rock faces with strata and joints that
     * follow world coordinates (so neighbours join without seams), a cap of the tile's terrain,
     * a lit rim and a grass lip where the ground in front is lower, an ink line where the
     * ground behind is lower, and a contact shadow at the foot.
     */
    private fun paintRaised(
        img: IntArray, w: Int, h: Int, cols: Int, rows: Int, raised: Raised, ramps: Ramps,
        terrainAt: (Int, Int) -> Terrain, groundY: FloatArray?,
    ) {
        fun lvl(c: Int, r: Int) = if (c in 0 until cols && r in 0 until rows) raised.level(c, r) else 0
        val rock = raised.rock
        val tiles = ArrayList<Int>()
        for (r in 0 until rows) for (c in 0 until cols) if (raised.level(c, r) > 0) tiles += r * cols + c
        tiles.sortWith(compareBy({ it % cols + it / cols }, { it % cols }))
        for (t in tiles) {
            val c = t % cols
            val r = t / cols
            val lv = raised.level(c, r)
            val terrain = terrainAt(c, r)
            val cap = ramps.of(terrain)
            val grassy = terrain == Terrain.GRASS || terrain == Terrain.FOREST
            val tx = Iso.x(c, r)
            val ty = Iso.y(c, r)
            val lowL = lvl(c - 1, r) < lv; val lowR = lvl(c, r - 1) < lv
            val frontL = lvl(c, r + 1) < lv; val frontR = lvl(c + 1, r) < lv
            for (dx in -Iso.HW until Iso.HW) {
                val wx = tx + dx
                val bx = wx + originX
                if (bx !in 0 until w) continue
                val left = dx < 0
                val adx = if (left) -dx - 0.5f else dx + 0.5f
                val topG = ty + adx / 2f
                val botG = ty + Iso.TH - adx / 2f
                val hgt = lv * Raised.LEVEL_PX + wob(wx)
                val lip = if (grassy) 1 + (Noise.value(wx * 0.35f, 7.1f, seed + 22) * 3f).toInt() else 0
                val y0 = kotlin.math.ceil(topG - hgt - 0.5f).toInt()
                val y1 = kotlin.math.floor(botG - 0.5f).toInt()
                for (wy in y0..y1) {
                    val by = wy + originY
                    if (by !in 0 until h) continue
                    val yy = wy + 0.5f
                    val col: Int
                    val gy: Float
                    if (yy < botG - hgt) {
                        // Cap: the tile's own ground, lit rim on open front edges, ink on open back edges.
                        gy = yy + hgt
                        val front = if (left) frontL else frontR
                        val back = if (left) lowL else lowR
                        col = when {
                            back && yy < topG - hgt + 1f -> INK
                            front && yy > botG - hgt - 1.2f -> cap.last()
                            else -> {
                                val n = Noise.fbm(wx * 0.045f, gy * 0.09f, seed + terrain.ordinal * 17) * 0.85f + Noise.rand(bx, by, seed) * 0.15f
                                val f = n * (cap.size - 1) * 1.25f - 0.12f
                                val i = floor(f).toInt().coerceIn(0, cap.size - 1)
                                val up = bayer[(by and 3) * 4 + (bx and 3)] < ((f - floor(f)) * 16).toInt()
                                cap[(if (up) i + 1 else i).coerceAtMost(cap.size - 1)]
                            }
                        }
                    } else {
                        // Face: z up from the foot; strata bands broken by joints.
                        gy = botG - 0.5f
                        val z = botG - yy
                        val fromTop = hgt - z
                        if (fromTop < lip) {
                            col = if (fromTop < 1f) cap[1.coerceAtMost(cap.size - 1)] else cap[0]
                        } else {
                            // Rock chunks: jittered cells in (world x, z), each lit from its top-left,
                            // with dark crevices where two cells meet.
                            val fx = wx / CHUNK_W
                            val fz = z / CHUNK_H
                            val gx = floor(fx).toInt()
                            val gz = floor(fz).toInt()
                            var d1 = 9f; var d2 = 9f; var cx = 0; var cz = 0; var sx = 0f; var sz = 0f
                            for (oz in -1..1) for (ox in -1..1) {
                                val kx = gx + ox; val kz = gz + oz
                                val px0 = kx + 0.15f + Noise.rand(kx, kz, seed + 24) * 0.7f
                                val pz0 = kz + 0.15f + Noise.rand(kx, kz, seed + 25) * 0.7f
                                val ddx = fx - px0; val ddz = (fz - pz0) * 1.2f
                                val d = ddx * ddx + ddz * ddz
                                if (d < d1) { d2 = d1; d1 = d; cx = kx; cz = kz; sx = ddx; sz = ddz } else if (d < d2) d2 = d
                            }
                            val edge = kotlin.math.sqrt(d2) - kotlin.math.sqrt(d1)
                            var v = (if (left) 2.9f else 1.6f) + 0.5f * (z / hgt) - (if (z < 3f) 0.6f else 0f)
                            v += (Noise.rand(cx, cz, seed + 26) - 0.5f) * 1.0f
                            v += (sz * 1.6f - sx * 0.8f).coerceIn(-0.9f, 0.9f)
                            if (dx == -1 || dx == 0) v += if (left) 0.6f else 0.3f
                            if (edge < 0.11f) v = if (left) 0.7f else 0.2f else if (edge < 0.2f) v -= 0.6f
                            if (grassy && fromTop < lip + 4 && Noise.rand(bx, by, seed + 27) > 0.7f) {
                                col = cap[0]
                                img[by * w + bx] = col
                                groundY?.set(by * w + bx, gy)
                                continue
                            }
                            v += (Noise.rand(bx, by, seed + 26) - 0.5f) * 0.5f
                            if (raised.accents.isNotEmpty() && Noise.rand(bx, by, seed + 28) > 0.992f) {
                                // Ore glints, crystals or embers in the rock.
                                col = raised.accents[(Noise.rand(bx, by, seed + 29) * raised.accents.size).toInt().coerceAtMost(raised.accents.size - 1)]
                                img[by * w + bx] = col
                                groundY?.set(by * w + bx, gy)
                                continue
                            }
                            val vi = v.coerceIn(0f, rock.size - 1f)
                            val lo = floor(vi).toInt()
                            val up = bayer[(by and 3) * 4 + (bx and 3)] < ((vi - lo) * 16).toInt()
                            col = rock[(if (up) lo + 1 else lo).coerceAtMost(rock.size - 1)]
                        }
                    }
                    img[by * w + bx] = col
                    groundY?.set(by * w + bx, gy)
                }
                // Contact shadow on the ground just in front of the foot.
                val fy = kotlin.math.floor(botG - 0.5f).toInt() + 1 + originY
                if ((if (left) frontL else frontR) && fy in 0 until h && img[fy * w + bx] != 0) img[fy * w + bx] = darken(img[fy * w + bx])
            }
        }
    }

    private fun darken(c: Int): Int {
        val r = (c shr 16 and 0xFF) * 3 / 5; val g = (c shr 8 and 0xFF) * 3 / 5; val b = (c and 0xFF) * 3 / 5
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Endesga-32 colour ramps and detail motifs per terrain and season (0 spring .. 3 winter). */
    private class Ramps(val season: Int, val palette: Map<Terrain, IntArray>) {
        fun of(t: Terrain): IntArray = palette[t] ?: when (t) {
            Terrain.GRASS -> when (season) {
                1 -> intArrayOf(0xFF3E8948.toInt(), 0xFF63C74D.toInt(), 0xFF63C74D.toInt(), 0xFFFEE761.toInt())
                2 -> intArrayOf(0xFF733E39.toInt(), 0xFF3E8948.toInt(), 0xFFB86F50.toInt(), 0xFFD77643.toInt())
                3 -> intArrayOf(0xFF8B9BB4.toInt(), 0xFFC0CBDC.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt())
                else -> intArrayOf(0xFF265C42.toInt(), 0xFF3E8948.toInt(), 0xFF63C74D.toInt(), 0xFF63C74D.toInt())
            }
            Terrain.FOREST -> when (season) {
                2 -> intArrayOf(0xFF3E2731.toInt(), 0xFF733E39.toInt(), 0xFF265C42.toInt(), 0xFFBE4A2F.toInt())
                3 -> intArrayOf(0xFF5A6988.toInt(), 0xFF8B9BB4.toInt(), 0xFFC0CBDC.toInt(), 0xFFFFFFFF.toInt())
                else -> intArrayOf(0xFF193C3E.toInt(), 0xFF265C42.toInt(), 0xFF3E8948.toInt(), 0xFF3E8948.toInt())
            }
            Terrain.DIRT -> if (season == 3) intArrayOf(0xFF733E39.toInt(), 0xFF8B9BB4.toInt(), 0xFFC0CBDC.toInt(), 0xFFFFFFFF.toInt())
            else intArrayOf(0xFF3E2731.toInt(), 0xFF733E39.toInt(), 0xFFB86F50.toInt())
            Terrain.PATH -> if (season == 3) intArrayOf(0xFF8B9BB4.toInt(), 0xFFC28569.toInt(), 0xFFE4A672.toInt())
            else intArrayOf(0xFFB86F50.toInt(), 0xFFC28569.toInt(), 0xFFE4A672.toInt(), 0xFFEAD4AA.toInt())
            Terrain.SAND -> intArrayOf(0xFFC28569.toInt(), 0xFFE4A672.toInt(), 0xFFEAD4AA.toInt())
            Terrain.WATER -> intArrayOf(0xFF124E89.toInt(), 0xFF0099DB.toInt(), 0xFF0099DB.toInt(), 0xFF2CE8F5.toInt())
            Terrain.STONE -> intArrayOf(0xFF3A4466.toInt(), 0xFF5A6988.toInt(), 0xFF8B9BB4.toInt(), 0xFFC0CBDC.toInt())
            Terrain.CAVE -> intArrayOf(0xFF181425.toInt(), 0xFF262B44.toInt(), 0xFF3A4466.toInt(), 0xFF5A6988.toInt())
            Terrain.GRAVEL -> intArrayOf(0xFF5A6988.toInt(), 0xFF8B9BB4.toInt(), 0xFF8B9BB4.toInt(), 0xFFC0CBDC.toInt())
            Terrain.COBBLE -> intArrayOf(0xFF3A4466.toInt(), 0xFF5A6988.toInt(), 0xFF8B9BB4.toInt(), 0xFFC0CBDC.toInt())
            Terrain.PAVED -> intArrayOf(0xFF5A6988.toInt(), 0xFF8B9BB4.toInt(), 0xFFC0CBDC.toInt())
            Terrain.NONE -> intArrayOf(0)
        }

        /** (dx, dy, colour) pixels of a motif, or null for none. */
        fun motif(t: Terrain, roll: Float, meadow: Float): List<Triple<Int, Int, Int>>? {
            val dark = of(t)[0]
            return when (t) {
                Terrain.GRASS -> when {
                    season == 3 -> if (roll < 0.25f) tuft(0xFF3E8948.toInt(), 0xFF265C42.toInt()) else if (roll < 0.35f) pebble(0xFF5A6988.toInt(), 0xFF8B9BB4.toInt()) else null
                    meadow > 0.58f && roll < 0.45f -> flower(petal(roll), 0xFFFEE761.toInt())
                    roll < 0.55f -> tuft(dark, if (season == 2) 0xFFBE4A2F.toInt() else 0xFF265C42.toInt())
                    roll < 0.70f -> blades(0xFF63C74D.toInt(), 0xFFFEE761.toInt())
                    roll < 0.78f -> clover(0xFF63C74D.toInt())
                    roll < 0.82f -> pebble(0xFF8B9BB4.toInt(), 0xFFC0CBDC.toInt())
                    season == 2 && roll < 0.95f -> leaf(0xFFD77643.toInt(), 0xFFFEAE34.toInt())
                    else -> null
                }
                Terrain.FOREST -> when {
                    roll < 0.30f -> leaf(0xFFBE4A2F.toInt(), 0xFFD77643.toInt())
                    roll < 0.55f -> tuft(dark, 0xFF193C3E.toInt())
                    roll < 0.62f -> mushroom()
                    roll < 0.70f -> pebble(0xFF5A6988.toInt(), 0xFF8B9BB4.toInt())
                    else -> null
                }
                Terrain.GRAVEL -> if (roll < 0.6f) pebble(dark, of(t).last()) else null
                Terrain.COBBLE, Terrain.PAVED -> null
                Terrain.PATH, Terrain.DIRT, Terrain.SAND -> when {
                    roll < 0.35f -> pebble(dark, of(t).last())
                    roll < 0.5f -> listOf(Triple(0, 0, dark), Triple(1, 0, dark))
                    else -> null
                }
                Terrain.STONE, Terrain.CAVE -> when {
                    roll < 0.25f -> crack(0xFF181425.toInt())
                    roll < 0.45f -> pebble(of(t)[0], of(t).last())
                    roll < 0.5f -> listOf(Triple(0, 0, 0xFF68386C.toInt()))
                    else -> null
                }
                Terrain.WATER -> if (roll < 0.15f) listOf(Triple(0, 0, 0xFF2CE8F5.toInt()), Triple(1, 0, 0xFF2CE8F5.toInt())) else null
                Terrain.NONE -> null
            }
        }

        private fun petal(roll: Float) = when ((roll * 100).toInt() % 4) {
            0 -> 0xFFFFFFFF.toInt(); 1 -> 0xFFF6757A.toInt(); 2 -> 0xFFB55088.toInt(); else -> 0xFF2CE8F5.toInt()
        }
        private fun tuft(a: Int, b: Int) = listOf(Triple(0, 0, a), Triple(2, 0, a), Triple(1, -1, a), Triple(1, 0, b))
        private fun blades(a: Int, b: Int) = listOf(Triple(0, 0, a), Triple(0, -1, a), Triple(1, -2, b))
        private fun clover(a: Int) = listOf(Triple(0, 0, a), Triple(1, 0, a), Triple(0, -1, a), Triple(1, 1, 0xFF265C42.toInt()))
        private fun flower(p: Int, c: Int) = listOf(Triple(0, -1, p), Triple(-1, 0, p), Triple(1, 0, p), Triple(0, 1, p), Triple(0, 0, c), Triple(0, 2, 0xFF265C42.toInt()))
        private fun pebble(a: Int, b: Int) = listOf(Triple(0, 0, a), Triple(1, 0, a), Triple(0, -1, b))
        private fun leaf(a: Int, b: Int) = listOf(Triple(0, 0, a), Triple(1, 0, b))
        private fun crack(a: Int) = listOf(Triple(0, 0, a), Triple(1, 1, a), Triple(2, 1, a), Triple(3, 2, a))
        private fun mushroom() = listOf(Triple(0, 0, 0xFFE43B44.toInt()), Triple(1, 0, 0xFFE43B44.toInt()), Triple(0, 1, 0xFFEAD4AA.toInt()))
    }

    /**
     * Laid surfaces follow the tile grid instead of noise: cobbles three to a tile edge with dark
     * joints and a lit corner, paving slabs two to a tile edge with thin seams.
     */
    private fun laid(t: Terrain, wx: Float, wy: Float, ramp: IntArray): Int {
        val c = (wy / Iso.HH + wx / Iso.HW) / 2f
        val r = (wy / Iso.HH - wx / Iso.HW) / 2f
        return if (t == Terrain.COBBLE) {
            val row = floor(r * 3f).toInt()
            val fa = (c * 3f + if (row % 2 == 0) 0f else 0.5f).let { it - floor(it) }
            val fb = r * 3f - floor(r * 3f)
            val stone = floor(c * 3f + if (row % 2 == 0) 0f else 0.5f).toInt() * 31 + row * 17
            when {
                fa < 0.14f || fb < 0.16f -> ramp[0]
                fa > 0.7f && fb > 0.62f -> ramp[3]
                else -> if (Noise.rand(stone, row, seed) > 0.45f) ramp[2] else ramp[1]
            }
        } else {
            val fa = c * 2f - floor(c * 2f)
            val fb = r * 2f - floor(r * 2f)
            if (fa < 0.06f || fb < 0.06f) ramp[0] else if (Noise.rand(wx.toInt(), wy.toInt(), seed) > 0.85f) ramp[2] else ramp[1]
        }
    }

    companion object {
        /** Cliff-top wobble (px) and the size of the rock chunks on cliff faces (px). */
        const val WOB = 3
        const val CHUNK_W = 9f
        const val CHUNK_H = 7f
        const val INK = 0xFF181425.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val CYAN = 0xFF2CE8F5.toInt()
        const val GRASS_LIP = 0xFF3E8948.toInt()
        const val GRASS_LIP_D = 0xFF265C42.toInt()
        const val CLIFF_L = 0xFFB86F50.toInt()
        const val CLIFF_L_D = 0xFF733E39.toInt()
        const val CLIFF_R = 0xFF733E39.toInt()
        const val CLIFF_R_D = 0xFF3E2731.toInt()
        const val CLIFF_LINE_L = 0xFF733E39.toInt()
        const val CLIFF_LINE_R = 0xFF3E2731.toInt()
    }
}
