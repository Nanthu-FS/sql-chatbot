package com.ditherfarm.game

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Story Act 2: the Vexmoor Cup, six chapters and the Valley Festival. */
class Act2Test {
    private val data = WorldTest.loadData()

    private fun afterAct1() = World.newGame(data, 41).apply {
        knowEverything(); gainXp(data.level(20).totalXp - state.xp)
        state.storySeen.addAll(SCENES.map { it.id })
        events.clear()
    }

    /** Pushes this week's contest score past Vexmoor's. */
    private fun World.winWeek() {
        val c = contest!!
        val bump = when (c.id) {
            "harvest" -> "harvested"; "gold" -> "harvest_gold"; "orders" -> "orders"; "timber" -> "fells"
            "eggs" -> "animal_products"; "fruit" -> "fruit_picked"; "fish" -> "fish_caught"; "mine" -> "rocks_broken"
            else -> "made_cherry_jam"
        }
        state.bump(bump, act2.target.toLong())
        advance(1.0)
    }

    private fun World.play(id: String) { assertEquals(id, pendingAct2Scene()?.id); finishScene(id) }

    @Test fun itBeginsAfterTheLoan() {
        val w = World.newGame(data, 41)
        assertNull(w.pendingAct2Scene())
        val v = afterAct1()
        v.play("act2_1")
        assertEquals(1, v.act2.chapter)
        assertTrue(v.contest != null)
        assertTrue(v.events.any { it is WorldEvent.ContestStarted })
    }

    @Test fun beatingVexmoorWinsTheWeek() {
        val w = afterAct1()
        w.play("act2_1")
        val coins = w.state.coins
        w.winWeek()
        assertTrue(w.act2.weekWon)
        assertTrue(w.state.coins > coins)
        assertEquals(1, w.act2.wins)
        w.play("act2_2")
        assertEquals(2, w.act2.chapter)
        assertFalse(w.act2.wonThisChapter)
    }

    @Test fun anUnwonWeekIsLostAndANewContestBegins() {
        val w = afterAct1()
        w.play("act2_1")
        val first = w.act2.contest
        repeat(Time.DAYS_PER_SEASON / 2) { w.advance(Time.REAL_SECONDS_PER_DAY) }
        assertTrue(w.events.any { it is WorldEvent.ContestLost })
        assertTrue(w.act2.contest != first)
        assertFalse(w.act2.weekWon)
    }

    @Test fun chaptersNeedTheirGoals() {
        val w = afterAct1()
        w.play("act2_1"); w.winWeek(); w.play("act2_2")
        w.act2.weekWon = false; w.winWeek()
        assertNull("5 hearts needed first", w.pendingAct2Scene())
        w.befriend("mara", 1000)
        w.play("act2_3")
        assertEquals(3, w.act2.chapter)
    }

    @Test fun theFestivalEndsTheAct() {
        val w = afterAct1()
        w.act2.chapter = ACT2_CHAPTERS; w.act2.week = 0; w.act2.contest = "orders"; w.act2.target = 1; w.act2.base = w.state.stat("orders")
        w.act2.wonThisChapter = true
        repeat(30) { w.state.decor[decorKey(20 + it, 6)] = "flowers_red" }
        val coins = w.state.coins
        w.play("act2_finale")
        assertEquals(ACT2_DONE, w.act2.chapter)
        assertEquals(coins + ACT2_REWARD, w.state.coins)
        assertNull(w.pendingAct2Scene())
    }

    @Test fun theCupIsSaved() {
        val w = afterAct1()
        w.play("act2_1"); w.winWeek()
        val back = GameState.fromJson(JSONObject(w.state.toJson().toString())).act2
        assertEquals(w.act2.chapter, back.chapter)
        assertEquals(w.act2.contest, back.contest)
        assertEquals(w.act2.wins, back.wins)
        assertTrue(back.weekWon)
    }
}
