package com.pulse.tracker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Looks up a scanned barcode on Open Food Facts (free, open database). */
object OpenFoodFacts {

    sealed interface Result {
        data class Found(val food: Food) : Result
        data object NotFound : Result
        data class Error(val message: String) : Result
    }

    suspend fun lookup(barcode: String): Result = withContext(Dispatchers.IO) {
        val code = URLEncoder.encode(barcode.trim(), "UTF-8")
        val url = URL("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=product_name,brands,nutriments,serving_quantity,serving_size")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "Pulse-Android/1.0")
        }
        try {
            if (conn.responseCode == 404) return@withContext Result.NotFound
            if (conn.responseCode !in 200..299) return@withContext Result.Error("Lookup failed (${conn.responseCode})")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            if (root.optInt("status", 0) != 1) return@withContext Result.NotFound
            val p = root.getJSONObject("product")
            val n = p.optJSONObject("nutriments") ?: JSONObject()
            val name = p.optString("product_name").ifBlank { "Scanned item $barcode" }
            val kcal = n.optDouble("energy-kcal_100g", Double.NaN).let {
                if (it.isNaN()) n.optDouble("energy_100g", 0.0) / 4.184 else it
            }
            val serving = p.optDouble("serving_quantity", Double.NaN)
            val food = Food(
                id = "off_$barcode",
                name = name,
                brand = p.optString("brands").split(',').firstOrNull()?.trim()?.ifBlank { null },
                kcal = kcal,
                protein = n.optDouble("proteins_100g", 0.0),
                carbs = n.optDouble("carbohydrates_100g", 0.0),
                fat = n.optDouble("fat_100g", 0.0),
                servingName = if (!serving.isNaN() && serving > 0) p.optString("serving_size").ifBlank { "1 serving" } else "100 g",
                servingGrams = if (!serving.isNaN() && serving > 0) serving else 100.0,
            )
            Result.Found(food)
        } catch (e: Exception) {
            Result.Error("No connection — try again or add it manually")
        } finally {
            conn.disconnect()
        }
    }
}
