package com.ditherfarm.engine

import kotlin.math.floor

/**
 * Isometric grid math. Tiles are 32x16 diamonds; world coordinates are art pixels.
 * Tile (c, r) has its top corner at (x(c, r), y(c, r)) and its center HH pixels below that.
 */
object Iso {
    const val TW = 32
    const val TH = 16
    const val HW = TW / 2
    const val HH = TH / 2

    fun x(c: Int, r: Int): Int = (c - r) * HW
    fun y(c: Int, r: Int): Int = (c + r) * HH
    fun centerY(c: Int, r: Int): Int = y(c, r) + HH

    /** Writes the tile containing world point (wx, wy) into out[0]=col, out[1]=row. */
    fun pick(wx: Float, wy: Float, out: IntArray) {
        val a = wx / HW
        val b = wy / HH
        out[0] = floor((b + a) / 2f).toInt()
        out[1] = floor((b - a) / 2f).toInt()
    }

    /** World-space bounding box of a cols x rows map: left, top, right, bottom. */
    fun bounds(cols: Int, rows: Int): IntArray =
        intArrayOf(x(0, rows - 1) - HW, 0, x(cols - 1, 0) + HW, y(cols - 1, rows - 1) + TH)
}
