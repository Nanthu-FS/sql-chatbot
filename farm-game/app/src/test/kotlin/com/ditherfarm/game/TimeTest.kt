package com.ditherfarm.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeTest {
    @Test fun twentyRealMinutesIsOneDay() {
        assertEquals(1440.0, Time.realSecondsToGameMinutes(1200.0), 1e-9)
        assertEquals(50.0, Time.gameMinutesToRealSeconds(60.0), 1e-9)
    }

    @Test fun newGameStartsSpringDayOneAtSix() {
        val t = GameTime.START
        assertEquals(Season.SPRING, t.season)
        assertEquals(1, t.dayOfSeason)
        assertEquals(1, t.year)
        assertEquals("06:00", t.clockText())
    }

    @Test fun seasonsRollEveryFourteenDays() {
        assertEquals(Season.SUMMER, GameTime.of(1, Season.SPRING, 14, 23.9).plusHours(0.2).season)
        val winterEnd = GameTime.of(1, Season.WINTER, 14, 23.0)
        val nextYear = winterEnd.plusHours(2.0)
        assertEquals(Season.SPRING, nextYear.season)
        assertEquals(2, nextYear.year)
        assertEquals(1, nextYear.dayOfSeason)
    }

    @Test fun nextHourIsStrictlyLater() {
        val t = GameTime.of(1, Season.SPRING, 3, 6.0)
        assertEquals(GameTime.of(1, Season.SPRING, 4, 6.0).minutes, t.next(6.0).minutes, 1e-9)
        assertEquals(GameTime.of(1, Season.SPRING, 3, 18.0).minutes, t.next(18.0).minutes, 1e-9)
    }

    @Test fun rngIsDeterministicPerDay() {
        val a = Rng.forDay(42, 7, 1).nextDouble()
        val b = Rng.forDay(42, 7, 1).nextDouble()
        val c = Rng.forDay(42, 8, 1).nextDouble()
        assertEquals(a, b, 0.0)
        assertTrue(a != c)
        val r = Rng(1)
        repeat(1000) { val v = r.nextInt(10); assertTrue(v in 0 until 10) }
    }
}
