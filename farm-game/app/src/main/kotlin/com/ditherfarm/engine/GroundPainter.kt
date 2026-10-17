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

enum class Terrain { NONE, GRASS, DIRT, PATH, SAND, WATER, FOREST, STONE, CAVE }

/**
 * Paints a whole isometric map into one bitmap, pixel by pixel: dithered colour
 * patches from world-space noise, wavy borders between terrains, scattered
 * detail motifs, shore foam and earth cliffs along the front edges so the map
 * reads as a floating diorama. Repaint only when the map or season changes.
 */
class GroundPainter(private val seed: Int = 7) {
    /** Bitmap pixel (0,0) is world point (-originX, 0). */
    var originX = 0
        private set

    private val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    fun paint(cols: Int, rows: Int, season: Int, cliff: Int = 14, terrainAt: (Int, Int) -> Terrain): Bitmap {
        originX = rows * Iso.HW
        val w = (cols + rows) * Iso.HW
        val mapH = (cols + rows) * Iso.HH
        val h = mapH + cliff
        val px = IntArray(w * h)
        val ter = arrayOfNulls<Terrain>(w * h)
        val pick = IntArray(2)
        val ramps = Ramps(season)

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
        // Ink outline around the whole island.
        val fin = out.copyOf()
        for (y in 0 until h) for (x in 0 until w) {
            if (out[y * w + x] != 0) continue
            val n = (x > 0 && out[y * w + x - 1] != 0) || (x < w - 1 && out[y * w + x + 1] != 0) ||
                (y > 0 && out[(y - 1) * w + x] != 0) || (y < h - 1 && out[(y + 1) * w + x] != 0)
            if (n) fin[y * w + x] = INK
        }
        return Bitmap.createBitmap(fin, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Endesga-32 colour ramps and detail motifs per terrain and season (0 spring .. 3 winter). */
    private class Ramps(val season: Int) {
        fun of(t: Terrain): IntArray = when (t) {
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
            Terrain.DIRT -> intArrayOf(0xFF3E2731.toInt(), 0xFF733E39.toInt(), 0xFFB86F50.toInt())
            Terrain.PATH -> if (season == 3) intArrayOf(0xFF8B9BB4.toInt(), 0xFFC28569.toInt(), 0xFFE4A672.toInt())
            else intArrayOf(0xFFB86F50.toInt(), 0xFFC28569.toInt(), 0xFFE4A672.toInt(), 0xFFEAD4AA.toInt())
            Terrain.SAND -> intArrayOf(0xFFC28569.toInt(), 0xFFE4A672.toInt(), 0xFFEAD4AA.toInt())
            Terrain.WATER -> intArrayOf(0xFF124E89.toInt(), 0xFF0099DB.toInt(), 0xFF0099DB.toInt(), 0xFF2CE8F5.toInt())
            Terrain.STONE -> intArrayOf(0xFF3A4466.toInt(), 0xFF5A6988.toInt(), 0xFF8B9BB4.toInt(), 0xFFC0CBDC.toInt())
            Terrain.CAVE -> intArrayOf(0xFF181425.toInt(), 0xFF262B44.toInt(), 0xFF3A4466.toInt(), 0xFF5A6988.toInt())
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

    companion object {
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
