package com.ditherfarm.ui

import com.ditherfarm.engine.Terrain
import com.ditherfarm.game.FieldLayout
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Layout of the 44x44 farm world in tile coordinates (c, r). On screen u = c - r runs left
 * to right and v = c + r top to bottom, so the main road (c == r) runs straight down the
 * middle with the field blocks on both sides, the farmstead at the top, the river down the
 * right, the village along the Forest road on the left and a dense forest wall all round.
 */
object FarmMap {
    const val SIZE = 44
    /** Tiles from the map edge that are forest wall. */
    const val WALL = 4

    /** Top corner (c, r) of each 4x4 field block, A..H: left and right of the road, top to bottom. */
    val BLOCKS: List<Pair<Int, Int>> = listOf(14 to 20, 20 to 14, 19 to 25, 25 to 19, 24 to 30, 30 to 24, 29 to 35, 35 to 29)

    // Farmstead.
    val BARN = 11 to 11
    val SILO = 9 to 12
    val WELL = 13 to 9
    val WORKBENCH = 14 to 12
    val MARKET = 16 to 11
    val BUNKHOUSE = 10 to 15
    val TRUCK = 15 to 15
    val MERCHANT = 17 to 13

    // Village along the Forest road.
    val ORDERS = 14 to 27
    val COTTAGES = listOf(14 to 24, 9 to 26, 14 to 31, 9 to 31)
    val SIGN_FOREST = 13 to 37
    val SIGN_MINE = 37 to 10

    /** Greenhouse 4x3 block (top corner). */
    val GREENHOUSE = 5 to 15

    /** Fixed slots for farm machines, assigned in unlock order. */
    val SLOTS = listOf(9 to 8, 12 to 7, 15 to 7, 17 to 9, 19 to 8, 21 to 9, 7 to 11, 6 to 13, 10 to 6, 13 to 5, 16 to 5, 18 to 6)

    /** Fenced pasture kept for animals. */
    val PASTURE_C = 32..37
    val PASTURE_R = 12..16

    /** Lanterns light the farmstead, the village and the main road's edge (never on the road: vehicles drive it). */
    val LANTERNS = listOf(12 to 14, 14 to 17, 13 to 19, 23 to 21, 28 to 26, 33 to 31, 19 to 12, 13 to 29)

    fun u(c: Int, r: Int) = c - r
    fun v(c: Int, r: Int) = c + r

    fun isWall(c: Int, r: Int) = c < WALL || r < WALL || c >= SIZE - WALL || r >= SIZE - WALL

    /** River: two tiles wide, meandering around u = 14 from the top-right edge to the bottom. */
    private fun riverU(v: Int) = 14 + (1.3 * sin(v * 0.21)).roundToInt()
    fun isRiver(c: Int, r: Int): Boolean { val du = u(c, r) - riverU(v(c, r)); return du == 0 || du == 1 }
    fun isBank(c: Int, r: Int) = !isRiver(c, r) && (isRiver(c + 1, r) || isRiver(c - 1, r) || isRiver(c, r + 1) || isRiver(c, r - 1))

    /** Main road (c == r and c == r + 1) from the farmstead down off the map, for the truck. */
    fun isMainRoad(c: Int, r: Int) = (c == r || c == r + 1) && c >= ROAD_START
    const val ROAD_START = 13
    /** Road to the Mine, east, crossing the river on a bridge. */
    fun isMineRoad(c: Int, r: Int) = (r == 11 || r == 12) && c >= 18
    /** Road to the Forest, west, past the village. */
    fun isForestRoad(c: Int, r: Int) = (c == 11 || c == 12) && r >= 16
    fun isBridge(c: Int, r: Int) = isMineRoad(c, r) && isRiver(c, r)
    fun isRoad(c: Int, r: Int) = !isWall(c, r) && (isMainRoad(c, r) || isMineRoad(c, r) || isForestRoad(c, r) || farmyard(c, r))

    /** Packed dirt around the farmstead buildings. */
    private fun farmyard(c: Int, r: Int) = c in 10..16 && r in 10..14 && (c + r) % 7 != 0

    fun blockAt(c: Int, r: Int): Int = BLOCKS.indexOfFirst { (bc, br) -> c in bc until bc + FieldLayout.SIDE && r in br until br + FieldLayout.SIDE }

    fun isGreenhouse(c: Int, r: Int) = c in GREENHOUSE.first until GREENHOUSE.first + FieldLayout.GH_COLS && r in GREENHOUSE.second until GREENHOUSE.second + FieldLayout.GH_ROWS

    fun inPasture(c: Int, r: Int) = c in PASTURE_C && r in PASTURE_R

    fun terrain(c: Int, r: Int, yard: (Int, Int) -> Boolean): Terrain = when {
        isWall(c, r) -> Terrain.FOREST
        isRiver(c, r) -> Terrain.WATER
        isRoad(c, r) -> Terrain.PATH
        isBank(c, r) -> Terrain.SAND
        yard(c, r) -> Terrain.DIRT
        else -> Terrain.GRASS
    }
}
