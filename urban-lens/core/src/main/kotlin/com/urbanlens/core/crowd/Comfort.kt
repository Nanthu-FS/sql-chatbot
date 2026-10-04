package com.urbanlens.core.crowd

import kotlin.math.min
import kotlin.math.roundToInt

/** "How pleasant is it there right now?" 0..100, higher is calmer and cleaner. */
object Comfort {
    fun score(crowdLevel: Int, aqi: Int?, constructionNearby: Int): Int {
        val calm = 100 - crowdLevel.coerceIn(0, 100)
        val air = if (aqi == null) 60 else 100 - (aqi - 25).coerceIn(0, 150) * 100 / 150
        val penalty = min(constructionNearby, 3) * 10
        return (calm * 0.5 + air * 0.5 - penalty).roundToInt().coerceIn(0, 100)
    }
}
