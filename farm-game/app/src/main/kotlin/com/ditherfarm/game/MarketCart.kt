package com.ditherfarm.game

import org.json.JSONArray
import org.json.JSONObject

/** One auto-sell rule: sell [item] when its price meets [cond], keeping at least [keep]. */
class CartRule(val item: String, var cond: Int = 1, var keep: Int = 10) {
    fun toJson() = JSONObject().put("item", item).put("cond", cond).put("keep", keep)

    companion object {
        /** Price conditions: any price, at or above base, +10%, +20%. */
        val CONDITIONS = listOf("ANY", "BASE+", "+10%", "+20%")
        val MIN_PERCENT = intArrayOf(0, 100, 110, 120)
        fun fromJson(o: JSONObject) = CartRule(o.getString("item"), o.optInt("cond", 1), o.optInt("keep", 10))
    }
}

/** A day of Market Cart sales for the log. */
class CartDay(val day: Int, val units: Int, val coins: Int) {
    fun toJson() = JSONObject().put("day", day).put("units", units).put("coins", coins)
}

const val CART_DAILY_CAP = 15
const val CART_MAX_RULES = 6

fun cartRulesToJson(rules: List<CartRule>) = JSONArray().apply { rules.forEach { put(it.toJson()) } }

fun World.addCartRule(item: String): Boolean {
    if (state.cartRules.size >= CART_MAX_RULES || state.cartRules.any { it.item == item }) return false
    state.cartRules += CartRule(item)
    return true
}

/** Sells by the rules in priority order, once per day at dawn, up to the daily cap. */
internal fun World.runMarketCart() {
    if (!isBuilt("market_cart") || state.cartRules.isEmpty()) return
    var left = CART_DAILY_CAP
    var units = 0
    var coins = 0
    for (r in state.cartRules) {
        if (left <= 0) break
        if (percentOfBase(r.item) < CartRule.MIN_PERCENT[r.cond]) continue
        // Sell lowest quality first so stars are kept.
        for (q in Quality.entries) {
            val key = q.key(r.item)
            val spare = minOf(left, count(r.item, anyQuality = true) - r.keep, count(key))
            if (spare <= 0) continue
            val got = sell(key, spare)
            if (got > 0) { units += spare; coins += got; left -= spare }
        }
    }
    if (units > 0) {
        state.cartLog.add(0, CartDay(time.dayIndex, units, coins))
        while (state.cartLog.size > 7) state.cartLog.removeAt(state.cartLog.size - 1)
        events += WorldEvent.CartSold(units, coins)
    }
}
