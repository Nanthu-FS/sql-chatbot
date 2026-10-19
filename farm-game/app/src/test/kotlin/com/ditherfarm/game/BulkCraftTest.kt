package com.ditherfarm.game

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bulk crafting: one queue slot makes N batches back to back, each taking the usual time. */
class BulkCraftTest {
    private val data = WorldTest.loadData()
    private fun fresh() = World.newGame(data, 99).apply { knowEverything() }
    private fun World.realMinutes(m: Double) = advance(m * 60)
    private val plank get() = data.recipes.getValue("wb_plank")
    private val woodPer get() = plank.inputs.getValue("wood")

    @Test fun tenPlanksInOneSlotTakeTenTimesAsLong() {
        val w = fresh()
        w.add("wood", 40)
        val wood = w.count("wood")
        assertTrue(w.enqueue("wb_plank", 10))
        assertEquals("all the wood is taken up front", wood - 10 * woodPer, w.count("wood"))
        val job = w.building("workbench").jobs.single()
        assertEquals(10, job.count)
        assertEquals(w.jobMinutes(plank) * 10, job.end - job.start, 1e-6)
        assertFalse("it fills the one slot", w.enqueue("wb_plank"))
        w.realMinutes(plank.minutes * 9.5)
        assertEquals("not done before ten batches' time", 0, w.trayCount("workbench"))
        w.realMinutes(plank.minutes)
        assertEquals(10 * plank.outputs.getValue("plank"), w.trayCount("workbench"))
    }

    @Test fun bulkNeedsEverythingForAllBatches() {
        val w = fresh()
        val wood = 5 * woodPer - 1                                  // one short of five batches
        w.state.inventory["wood"] = wood
        assertNotNull(w.recipeBlocker(plank, 5))
        assertFalse(w.enqueue("wb_plank", 5))
        assertEquals("nothing is taken when it is refused", wood, w.count("wood"))
        assertEquals(4, w.maxBatches(plank))
        assertTrue(w.enqueue("wb_plank", w.maxBatches(plank)))
        assertEquals(woodPer - 1, w.count("wood"))
    }

    @Test fun cancelRefundsEveryBatch() {
        val w = fresh()
        w.add("wood", 40)
        val wood = w.count("wood")
        assertTrue(w.enqueue("wb_plank", 10))
        assertTrue(w.cancelLast("workbench"))
        assertEquals(wood, w.count("wood"))
    }

    @Test fun bulkJobsSurviveASave() {
        val w = fresh()
        w.add("wood", 40)
        assertTrue(w.enqueue("wb_plank", 7))
        val back = GameState.fromJson(JSONObject(w.state.toJson().toString()))
        assertEquals(7, back.buildings.getValue("workbench").jobs.single().count)
        // Old saves have no count: one batch.
        assertEquals(1, Job.fromJson(JSONObject().put("r", "wb_plank").put("s", 0.0).put("e", 1.0)).count)
    }

    @Test fun toolUpgradesAreOneAtATime() {
        val w = fresh()
        val axe = data.recipes.getValue("tool_axe_1")
        assertFalse(w.canBulk(axe))
        w.state.coins += 10_000
        axe.inputs.forEach { (k, v) -> w.add(k, v * 5) }
        assertTrue(w.enqueue(axe.id, 5))
        assertEquals(1, w.building("workbench").jobs.single().count)
    }
}
