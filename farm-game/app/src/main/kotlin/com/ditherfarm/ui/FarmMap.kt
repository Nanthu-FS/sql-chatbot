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
    /** Village house plots in the order they fill: four huts to start, two more with each tier. */
    val HOUSES = COTTAGES + listOf(9 to 21, 14 to 34, 9 to 36, 15 to 28, 7 to 24, 7 to 29)
    fun houseCount(tier: Int) = 4 + 2 * tier
    /** Style of house k (a, b or c): the village mixes the three styles of its tier. */
    fun houseStyle(k: Int) = "abc"[(k * 2 + k / 3) % 3]
    // Shops the village tiers bring.
    val SHOP_SEED = 16 to 35
    val SHOP_SMITH = 8 to 33
    val SHOP_CARPENTER = 16 to 38
    val SHOP_CAFE = 9 to 18
    val SIGN_FOREST = 13 to 37
    val SIGN_MINE = 37 to 10

    /** Greenhouse 4x3 block (top corner). */
    val GREENHOUSE = 5 to 15

    /** Fixed slots for farm machines, assigned in unlock order. */
    val SLOTS = listOf(9 to 8, 12 to 7, 15 to 7, 17 to 9, 19 to 8, 21 to 9, 7 to 11, 6 to 13, 10 to 6, 13 to 5, 16 to 5, 18 to 6)

    /** Fenced pasture kept for animals. */
    val PASTURE_C = 32..37
    val PASTURE_R = 13..16
    /** Its sign by the mine road, so the empty fence reads as a pasture (playtest round 7). */
    val PASTURE_SIGN = 31 to 13

    /** Orchard by the village, between Fields C and E (round 6): fenced, two rows of four trees. */
    val ORCHARD_C = 17..21
    val ORCHARD_R = 29..37
    val ORCHARD_TREES = listOf(18 to 30, 20 to 30, 18 to 32, 20 to 32, 18 to 34, 20 to 34, 18 to 36, 20 to 36)
    /** Its sign, just outside the back corner. */
    val ORCHARD_SIGN = 16 to 28
    fun inOrchard(c: Int, r: Int) = c in ORCHARD_C && r in ORCHARD_R

    /** The valley's statue after the festival (Story Act 2), beside the village road. */
    val STATUE = 13 to 22

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
    /** The roads the village rebuilds (dirt, gravel, cobbles, paved); the farmyard stays packed dirt. */
    fun isStreet(c: Int, r: Int) = !isWall(c, r) && (isMainRoad(c, r) || isMineRoad(c, r) || isForestRoad(c, r))
    fun streetTerrain(tier: Int) = when (tier) { 0 -> Terrain.PATH; 1 -> Terrain.GRAVEL; 2 -> Terrain.COBBLE; else -> Terrain.PAVED }

    /** Packed dirt around the farmstead buildings. */
    private fun farmyard(c: Int, r: Int) = c in 10..16 && r in 10..14 && (c + r) % 7 != 0

    fun blockAt(c: Int, r: Int): Int = BLOCKS.indexOfFirst { (bc, br) -> c in bc until bc + FieldLayout.SIDE && r in br until br + FieldLayout.SIDE }

    fun isGreenhouse(c: Int, r: Int) = c in GREENHOUSE.first until GREENHOUSE.first + FieldLayout.GH_COLS && r in GREENHOUSE.second until GREENHOUSE.second + FieldLayout.GH_ROWS

    fun inPasture(c: Int, r: Int) = c in PASTURE_C && r in PASTURE_R

    fun terrain(c: Int, r: Int, tier: Int = 0, yard: (Int, Int) -> Boolean): Terrain = when {
        isWall(c, r) -> Terrain.FOREST
        isRiver(c, r) -> Terrain.WATER
        isStreet(c, r) -> streetTerrain(tier)
        isRoad(c, r) -> Terrain.PATH
        isBank(c, r) -> Terrain.SAND
        yard(c, r) -> Terrain.DIRT
        else -> Terrain.GRASS
    }
}
