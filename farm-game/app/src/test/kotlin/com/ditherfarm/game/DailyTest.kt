package com.ditherfarm.game

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyTest {
    private val data = WorldTest.loadData()
    private fun fresh(level: Int = 6) = World.newGame(data, 77).apply {
        knowEverything()
        gainXp(data.level(level).totalXp - state.xp)
        events.clear()
    }

    /** A Monday, so days MON..MON+6 share a week. */
    private val mon = 20_000L.let { it - Math.floorMod(it + 3, 7L) }

    private fun World.doAll() {
        for (c in state.chores) state.bump(c.stat, c.n)
        state.chores.indices.forEach { claimChore(it) }
    }

    @Test fun threeChoresADayFromLevelThree() {
        val low = fresh(2)
        low.rollDaily(mon)
        assertTrue(low.state.chores.isEmpty())
        val w = fresh()
        w.rollDaily(mon)
        assertEquals(3, w.state.chores.size)
        assertTrue(w.events.contains(WorldEvent.ChoresNew))
        val first = w.state.chores.map { it.text }
        w.rollDaily(mon)
        assertEquals("same day keeps its chores", first, w.state.chores.map { it.text })
        w.rollDaily(mon + 1)
        assertEquals(3, w.state.chores.size)
    }

    @Test fun choresCountFromTheirStartAndPay() {
        val w = fresh()
        w.state.stats["harvested"] = 500
        w.rollDaily(mon)
        val c = w.state.chores[0]
        assertEquals(0L, w.choreProgress(c))
        assertFalse(w.claimChore(0))
        w.state.bump(c.stat, c.n)
        w.checkDaily()
        assertTrue(w.events.any { it is WorldEvent.ChoreDone })
        val coins = w.state.coins
        assertTrue(w.claimChore(0))
        assertEquals(coins + w.choreReward().first, w.state.coins)
        assertFalse("only once", w.claimChore(0))
    }

    @Test fun streakForgivesOneMissedDayAWeek() {
        val w = fresh()
        w.rollDaily(mon); w.doAll()
        assertEquals(1, w.state.choreStreak)
        assertTrue(w.events.any { it is WorldEvent.ChoresFinished })
        w.rollDaily(mon + 1); w.doAll()
        assertEquals(2, w.state.choreStreak)
        // Miss Wednesday: forgiven.
        assertEquals(2, w.choreStreakNow(mon + 3))
        w.rollDaily(mon + 3); w.doAll()
        assertEquals(3, w.state.choreStreak)
        // Miss Friday too: the second miss this week resets it.
        assertEquals(0, w.choreStreakNow(mon + 5))
        w.rollDaily(mon + 5); w.doAll()
        assertEquals(1, w.state.choreStreak)
    }

    @Test fun streakBonusGrowsToDaySeven() {
        val w = fresh()
        assertTrue(w.streakBonus(2) > w.streakBonus(1))
        assertEquals(w.streakBonus(7), w.streakBonus(12))
        val tonics = w.count("growth_tonic")
        for (d in 0 until 7) { w.rollDaily(mon + d); w.doAll() }
        assertEquals(7, w.state.choreStreak)
        assertEquals("a tonic on day seven", tonics + 1, w.count("growth_tonic"))
    }

    @Test fun finishedChoresArePaidAtMidnight() {
        val w = fresh()
        w.rollDaily(mon)
        for (c in w.state.chores) w.state.bump(c.stat, c.n)
        w.rollDaily(mon + 1)
        assertEquals(1, w.state.choreStreak)
        assertEquals(mon, w.state.choreDoneDay)
    }

    @Test fun craftChoresUseBuiltMachinesOnly() {
        val w = fresh(3)
        w.state.buildings.values.forEach { if (!data.buildings.getValue(it.id).prebuilt) it.built = false }
        for (d in 0 until 20) {
            w.rollDaily(mon + d)
            val made = w.state.chores[1].stat.removePrefix("made_")
            if (made != w.state.chores[1].stat) {
                assertTrue("$made comes from a built machine", data.recipes.values.any { r -> r.outputs.containsKey(made) && w.isBuilt(r.building) })
            }
        }
    }

    @Test fun weeklyContractShipsByTruck() {
        val w = fresh()
        w.rollDaily(mon)
        assertNull("no contract before a farmhand", w.state.contract)
        w.state.bump("truck_trips")
        w.rollDaily(mon)
        val c = w.state.contract!!
        assertEquals(3, c.crops.size)
        assertTrue(c.crops.keys.all { w.cropUnlocked(it) })
        assertEquals(7, contractDaysLeft(mon))
        assertEquals(1, contractDaysLeft(mon + 6))
        // Ship it all.
        for ((k, n) in c.crops) w.state.truck.load[k] = n
        assertTrue(w.sendTruck())
        assertTrue(w.contractDone(c))
        w.checkDaily()
        assertTrue(w.events.contains(WorldEvent.ContractDone))
        val coins = w.state.coins
        assertTrue(w.claimContract())
        assertTrue(w.state.coins > coins)
        assertTrue(w.count("gold_bar") + w.count("lucky_charm") > 0)
        w.rollDaily(mon + 6)
        assertTrue("same week", w.state.contract!!.claimed)
        w.rollDaily(mon + 7)
        assertFalse("a new one on Monday", w.state.contract!!.claimed)
    }

    @Test fun pinnedGoalWinsTheChipUntilItIsDone() {
        val w = fresh()
        w.state.coins = 0
        w.state.inventory.clear()
        assertEquals("quest", w.chipGoal()!!.first)
        val b = w.nextTier!!.bundles[0]
        w.togglePin("bundle:${b.id}")
        assertEquals("bundle:${b.id}", w.chipGoal()!!.first)
        assertFalse(w.chipGoal()!!.second.ready)
        for ((k, n) in b.items) w.add(k, n)
        assertTrue(w.chipGoal()!!.second.ready)
        w.donate(b.id)
        w.checkDaily()
        assertEquals("unpinned once done", "", w.state.pinned)
        assertEquals("quest", w.chipGoal()!!.first)
        // Notebook finished: the village shows.
        w.state.questIndex = QUESTS.size
        assertEquals("village", w.chipGoal()!!.first)
    }

    @Test fun everyPinnableKindHasALine() {
        val w = fresh()
        w.rollDaily(mon)
        w.state.bump("truck_trips")
        w.rollDaily(mon)
        val a = data.goals.achievements.first { w.claimedTiers(it.id) < it.tiers.size }
        val notBuilt = data.buildings.values.first { !it.prebuilt && !w.isBuilt(it.id) }
        val field = (1 until FieldLayout.BLOCKS).first { !w.blockOwned(it) }
        for (key in listOf("ach:${a.id}:${w.claimedTiers(a.id)}", "build:${notBuilt.id}", "field:$field", "chore:$mon:0", "contract:${weekOf(mon)}")) {
            assertNotNull(key, w.goalLine(key))
        }
        assertNull("yesterday's chore", w.goalLine("chore:${mon - 1}:0"))
        assertNull(w.goalLine("field:0"))
        w.state.coins = 1_000_000
        assertTrue(w.goalLine("field:1")!!.ready)
        w.buyBlock(1)
        assertNull("bought", w.goalLine("field:1"))
    }

    @Test fun dailyStateSurvivesSaving() {
        val w = fresh()
        w.state.bump("truck_trips")
        w.rollDaily(mon)
        w.state.bump(w.state.chores[0].stat, w.state.chores[0].n)
        w.claimChore(0)
        w.togglePin("contract:${weekOf(mon)}")
        val back = GameState.fromJson(JSONObject(w.state.toJson().toString()))
        assertEquals(mon, back.choreDay)
        assertEquals(w.state.chores.map { it.text }, back.chores.map { it.text })
        assertTrue(back.chores[0].claimed)
        assertEquals(w.state.contract!!.crops, back.contract!!.crops)
        assertEquals("contract:${weekOf(mon)}", back.pinned)
    }
}
