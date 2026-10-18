package com.ditherfarm.game

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pacing simulation: a casual player checks in once an hour, eight times a day. Each
 * session they collect, harvest, replant the best crop, water, deliver what they can,
 * sell spare crops, buy land, chop trees and mine the quarry with spare energy, and
 * build machines when they can afford them. Prints the level reached per day.
 */
class PacingTest {
    private val data = WorldTest.loadData()

    private fun World.bestCrop(): String? = data.crops.values
        .filter { cropUnlocked(it.id) && time.season in it.seasons && it.growMinutes <= 3 * Time.MINUTES_PER_DAY }
        // Value per check-in: a crop shorter than the hour between sessions still takes an hour.
        .maxByOrNull { (it.price * it.yield - it.seedPrice + 5.0 * it.xp) / maxOf(Time.gameMinutesToRealSeconds(it.growMinutes) / 60, 60.0) }?.id

    private fun World.session() {
        state.buildings.keys.toList().forEach { if (trayCount(it) > 0) collect(it) }
        for (p in state.plots) {
            if (!p.owned) continue
            if (p.needsClearing) clearPlot(p.index)
            if (isReady(p)) harvest(p.index)
        }
        val crop = bestCrop()
        for (p in state.plots) {
            if (!p.owned) continue
            if (p.empty && crop != null && state.coins > seedPrice(crop)) plant(p.index, crop)
            water(p.index)
        }
        for (i in state.orders.indices) if (canDeliver(i)) deliver(i)
        // Keep 10 of each crop (any quality) for orders; sell the rest, and junk.
        for (id in state.inventory.keys.map { baseId(it) }.distinct()) {
            if (data.crops.containsKey(id)) {
                var spare = count(id, anyQuality = true) - 10
                for (q in Quality.entries) { if (spare <= 0) break; val n = minOf(spare, count(q.key(id))); if (n > 0) { sell(q.key(id), n); spare -= n } }
            }
            if (id == "plant_scraps" || id == "withered_crop") sell(id, count(id))
        }
        for (st in listOf(Storage.SILO, Storage.BARN)) {
            if (used(st) * 10 >= capacity(st) * 8) upgradeCost(st)?.let { t -> if (t.unlockLevel <= level && canAfford(t.cost)) upgrade(st) }
        }
        // Barn overflow: sell surplus materials above 30.
        if (used(Storage.BARN) >= capacity(Storage.BARN)) for (k in state.inventory.keys.toList()) {
            if (data.item(baseId(k)).storage == Storage.BARN && count(k) > 30) sell(k, count(k) - 30)
        }
        for (b in 0 until FieldLayout.BLOCKS) if (blockBlocker(b) == null && state.coins > FieldLayout.PRICE[b] * 2) buyBlock(b)
        // Spend energy on the forest and the quarry.
        if (level >= 2) for (sp in forestSpots) {
            if (state.energy < 30) break
            if (sp.kind == SpotKind.TREE && chopBlocker(sp.index) == null) repeat(4) { chop(sp.index) }
        }
        if (level >= 3) for (i in 0 until 20) {
            if (state.energy < 20) break
            val c = quarryNode(i) ?: continue
            if (c.node != null && mineBlocker(c.node) == null) repeat(3) { mineQuarry(i) }
        }
        // Tools and machines when affordable.
        for (t in listOf(Tool.AXE, Tool.PICKAXE)) nextToolRecipe(t)?.let { r -> if (recipeBlocker(r) == null) enqueue(r.id) }
        for (b in data.buildings.values) if (!isBuilt(b.id) && buildBlocker(b.id) == null && state.coins > (b.cost["coins"] ?: 0) * 2) build(b.id)
        if (isBuilt("sawmill")) recipesFor("sawmill").firstOrNull { recipeBlocker(it) == null }?.let { enqueue(it.id) }
        events.clear()
    }

    @Test fun casualPlayerPacing() {
        val w = World.newGame(data, 5)
        val report = ArrayList<String>()
        for (day in 1..21) {
            repeat(8) {
                w.session()
                w.advance(3600.0)
            }
            w.advance(16 * 3600.0 - 8 * 3600.0 + 8 * 3600.0) // overnight (capped catch-up)
            if (day in listOf(1, 2, 3, 5, 7, 10, 14, 21)) report += "day $day: L${w.level} coins=${w.state.coins} plots=${w.ownedPlots} built=${data.buildings.keys.count { w.isBuilt(it) }}"
        }
        println("PACING:\n" + report.joinToString("\n"))
        assertTrue("too slow: ${report.last()}", w.level >= 14)
    }

    @Test fun playedSaveRoundTripsExactly() {
        val w = World.newGame(data, 9)
        repeat(3 * 8) { w.session(); w.advance(3600.0) }
        w.state.fixtures[FieldLayout.vertex(0, 2, 2)] = "scarecrow"
        w.state.cartRules += CartRule("wheat", 2, 15)
        val first = w.state.toJson().toString()
        val again = GameState.fromJson(org.json.JSONObject(first)).toJson().toString()
        assertTrue("save changed on reload", first == again)
        // And the reloaded world keeps running.
        val back = World(data, GameState.fromJson(org.json.JSONObject(first)))
        back.advance(3600.0)
        assertTrue(back.level >= w.level)
    }
}
