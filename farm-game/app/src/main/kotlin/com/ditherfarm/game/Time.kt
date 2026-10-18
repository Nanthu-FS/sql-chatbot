package com.ditherfarm.game

/**
 * Game time. One in-game day lasts 20 real minutes, so one game minute is 0.8333 real
 * seconds and one game hour is 50 real seconds. A season is 14 days and a year is 4 seasons.
 * Everything that grows or produces is timed in game minutes so offline catch-up is just
 * "advance the clock by the real time that passed".
 */
object Time {
    const val REAL_SECONDS_PER_DAY = 1200.0
    const val MINUTES_PER_DAY = 1440
    const val DAYS_PER_SEASON = 14
    const val SEASONS_PER_YEAR = 4
    const val DAYS_PER_YEAR = DAYS_PER_SEASON * SEASONS_PER_YEAR

    /** Game minutes that pass per real second. */
    const val GAME_MINUTES_PER_REAL_SECOND = MINUTES_PER_DAY / REAL_SECONDS_PER_DAY

    /** A new game starts on day 1 of spring at 6:00. */
    const val START_MINUTE = 6.0 * 60

    fun realSecondsToGameMinutes(s: Double) = s * GAME_MINUTES_PER_REAL_SECOND
    fun gameMinutesToRealSeconds(m: Double) = m / GAME_MINUTES_PER_REAL_SECOND
}

enum class Season(val label: String) {
    SPRING("Spring"), SUMMER("Summer"), FALL("Fall"), WINTER("Winter");

    val next: Season get() = entries[(ordinal + 1) % entries.size]
}

/** A point in game time, measured in game minutes since the start of year 1. */
@JvmInline
value class GameTime(val minutes: Double) : Comparable<GameTime> {
    /** 0-based day counter since the start of the game. */
    val dayIndex: Int get() = (minutes / Time.MINUTES_PER_DAY).toInt()
    /** 1..14 */
    val dayOfSeason: Int get() = dayIndex % Time.DAYS_PER_SEASON + 1
    val season: Season get() = Season.entries[(dayIndex / Time.DAYS_PER_SEASON) % Time.SEASONS_PER_YEAR]
    /** 1-based year. */
    val year: Int get() = dayIndex / Time.DAYS_PER_YEAR + 1
    val minuteOfDay: Double get() = minutes - dayIndex.toDouble() * Time.MINUTES_PER_DAY
    /** Fractional hour of day, 0 until 24. */
    val hour: Float get() = (minuteOfDay / 60.0).toFloat()

    fun plusMinutes(m: Double) = GameTime(minutes + m)
    fun plusHours(h: Double) = GameTime(minutes + h * 60)
    fun startOfDay() = GameTime(dayIndex.toDouble() * Time.MINUTES_PER_DAY)
    /** The next time the clock reads [hourOfDay] (strictly after this). */
    fun next(hourOfDay: Double): GameTime {
        val today = startOfDay().plusHours(hourOfDay)
        return if (today.minutes > minutes) today else today.plusMinutes(Time.MINUTES_PER_DAY.toDouble())
    }

    /** "06:40" */
    fun clockText(): String {
        val m = minuteOfDay.toInt()
        return "%02d:%02d".format(m / 60, m % 60)
    }

    override fun compareTo(other: GameTime) = minutes.compareTo(other.minutes)

    companion object {
        val START = GameTime(Time.START_MINUTE)
        fun of(year: Int, season: Season, day: Int, hour: Double = 6.0) = GameTime(
            ((year - 1) * Time.DAYS_PER_YEAR + season.ordinal * Time.DAYS_PER_SEASON + (day - 1)).toDouble() * Time.MINUTES_PER_DAY + hour * 60,
        )
    }
}

/** Small deterministic RNG (SplitMix64) so daily rolls are reproducible from a seed. */
class Rng(seed: Long) {
    private var state = seed

    fun nextLong(): Long {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    /** 0 until 1 */
    fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53))
    fun nextInt(bound: Int): Int = ((nextLong() ushr 1) % bound).toInt()
    fun range(lo: Double, hi: Double) = lo + (hi - lo) * nextDouble()
    fun chance(p: Double) = nextDouble() < p

    companion object {
        /** Seed for a (world seed, day, purpose) triple, so each day's rolls are stable. */
        fun forDay(worldSeed: Long, day: Int, salt: Int): Rng = Rng(worldSeed * 31 + day * 1_000_003L + salt * 7919L)
    }
}
