package com.pulse.tracker.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * Keeps all of Pulse's data in one JSON file in the app's private storage.
 * Reads happen once at start-up; writes are debounced and atomic (temp file + rename).
 */
@OptIn(FlowPreview::class)
class Store(context: Context, scope: CoroutineScope) {

    private val file = File(context.filesDir, "pulse.json")
    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    init {
        _data.drop(1).debounce(300).onEach { save(it) }.launchIn(CoroutineScope(scope.coroutineContext + Dispatchers.IO))
    }

    fun update(transform: (AppData) -> AppData) = _data.update(transform)

    private fun load(): AppData = try {
        if (file.exists()) decode(JSONObject(file.readText())) else AppData()
    } catch (e: Exception) {
        // A corrupt file should never brick the app; keep a copy and start fresh.
        runCatching { file.copyTo(File(file.parentFile, "pulse.corrupt.json"), overwrite = true) }
        AppData()
    }

    private fun save(data: AppData) {
        runCatching {
            val tmp = File(file.parentFile, "pulse.json.tmp")
            tmp.writeText(encode(data).toString())
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    companion object {
        fun encode(d: AppData): JSONObject = JSONObject().apply {
            put("v", 1)
            put("themeId", d.themeId)
            put("name", d.name)
            put("addBurnedBack", d.addBurnedBack)
            put("followSystemDark", d.followSystemDark)
            put("goals", JSONObject().apply {
                put("kcal", d.goals.kcal); put("protein", d.goals.protein); put("carbs", d.goals.carbs)
                put("fat", d.goals.fat); put("waterMl", d.goals.waterMl); put("steps", d.goals.steps)
                put("sleepMinutes", d.goals.sleepMinutes); put("mindfulMinutes", d.goals.mindfulMinutes)
                put("fastingHours", d.goals.fastingHours)
            })
            put("foods", JSONArray().apply {
                d.foods.forEach { e ->
                    put(JSONObject().apply {
                        put("id", e.id); put("date", e.date.toString()); put("meal", e.meal.name)
                        put("foodId", e.foodId); put("name", e.name); put("grams", e.grams)
                        put("kcal", e.kcal); put("protein", e.protein); put("carbs", e.carbs); put("fat", e.fat)
                        put("loggedAt", e.loggedAt)
                    })
                }
            })
            put("customFoods", JSONArray().apply {
                d.customFoods.forEach { f ->
                    put(JSONObject().apply {
                        put("id", f.id); put("name", f.name); put("kcal", f.kcal); put("protein", f.protein)
                        put("carbs", f.carbs); put("fat", f.fat); put("servingName", f.servingName)
                        put("servingGrams", f.servingGrams); f.brand?.let { put("brand", it) }
                    })
                }
            })
            put("water", JSONArray().apply {
                d.water.forEach { w ->
                    put(JSONObject().apply {
                        put("id", w.id); put("date", w.date.toString()); put("ml", w.ml)
                        put("kind", w.kind); put("loggedAt", w.loggedAt)
                    })
                }
            })
            put("weights", JSONArray().apply {
                d.weights.forEach { w -> put(JSONObject().apply { put("date", w.date.toString()); put("kg", w.kg) }) }
            })
            put("metrics", JSONObject().apply {
                d.metrics.forEach { (date, m) ->
                    put(date.toString(), JSONObject().apply {
                        put("steps", m.steps); put("sleepMinutes", m.sleepMinutes); put("mindfulMinutes", m.mindfulMinutes)
                    })
                }
            })
            put("supplements", JSONArray().apply {
                d.supplements.forEach { s ->
                    put(JSONObject().apply { put("id", s.id); put("name", s.name); put("timing", s.timing) })
                }
            })
            put("supplementChecks", JSONObject().apply {
                d.supplementChecks.forEach { (date, ids) -> put(date.toString(), JSONArray(ids.toList())) }
            })
            put("fasting", JSONObject().apply {
                d.fasting.startedAt?.let { put("startedAt", it) }
                d.fasting.lastCompletedHours?.let { put("lastCompletedHours", it) }
            })
        }

        fun decode(o: JSONObject): AppData {
            val defaults = AppData()
            val g = o.optJSONObject("goals")
            val goals = if (g == null) Goals() else Goals(
                kcal = g.optInt("kcal", 2400), protein = g.optInt("protein", 150), carbs = g.optInt("carbs", 260),
                fat = g.optInt("fat", 80), waterMl = g.optInt("waterMl", 3000), steps = g.optInt("steps", 10000),
                sleepMinutes = g.optInt("sleepMinutes", 480), mindfulMinutes = g.optInt("mindfulMinutes", 15),
                fastingHours = g.optInt("fastingHours", 16),
            )
            val foods = o.optJSONArray("foods").objects().mapNotNull { e ->
                runCatching {
                    FoodEntry(
                        id = e.getLong("id"), date = LocalDate.parse(e.getString("date")),
                        meal = Meal.valueOf(e.getString("meal")), foodId = e.optString("foodId"),
                        name = e.getString("name"), grams = e.optDouble("grams", 100.0),
                        kcal = e.optDouble("kcal", 0.0), protein = e.optDouble("protein", 0.0),
                        carbs = e.optDouble("carbs", 0.0), fat = e.optDouble("fat", 0.0),
                        loggedAt = e.optLong("loggedAt", 0L),
                    )
                }.getOrNull()
            }
            val customFoods = o.optJSONArray("customFoods").objects().mapNotNull { f ->
                runCatching {
                    Food(
                        id = f.getString("id"), name = f.getString("name"), kcal = f.optDouble("kcal", 0.0),
                        protein = f.optDouble("protein", 0.0), carbs = f.optDouble("carbs", 0.0), fat = f.optDouble("fat", 0.0),
                        servingName = f.optString("servingName", "100 g"), servingGrams = f.optDouble("servingGrams", 100.0),
                        brand = if (f.has("brand")) f.getString("brand") else null,
                    )
                }.getOrNull()
            }
            val water = o.optJSONArray("water").objects().mapNotNull { w ->
                runCatching {
                    WaterEntry(
                        id = w.getLong("id"), date = LocalDate.parse(w.getString("date")), ml = w.getInt("ml"),
                        kind = w.optString("kind", "Glass"), loggedAt = w.optLong("loggedAt", 0L),
                    )
                }.getOrNull()
            }
            val weights = o.optJSONArray("weights").objects().mapNotNull { w ->
                runCatching { WeightEntry(LocalDate.parse(w.getString("date")), w.getDouble("kg")) }.getOrNull()
            }
            val metrics = buildMap<LocalDate, DayMetrics> {
                o.optJSONObject("metrics")?.let { m ->
                    m.keys().forEach { k ->
                        runCatching {
                            val v = m.getJSONObject(k)
                            put(LocalDate.parse(k), DayMetrics(v.optInt("steps"), v.optInt("sleepMinutes"), v.optInt("mindfulMinutes")))
                        }
                    }
                }
            }
            val supplements = o.optJSONArray("supplements")?.let { arr ->
                arr.objects().mapNotNull { s ->
                    runCatching { Supplement(s.getString("id"), s.getString("name"), s.optString("timing", "Anytime")) }.getOrNull()
                }
            } ?: DefaultSupplements
            val checks = buildMap<LocalDate, Set<String>> {
                o.optJSONObject("supplementChecks")?.let { c ->
                    c.keys().forEach { k ->
                        runCatching {
                            val arr = c.getJSONArray(k)
                            put(LocalDate.parse(k), (0 until arr.length()).map { arr.getString(it) }.toSet())
                        }
                    }
                }
            }
            val fj = o.optJSONObject("fasting")
            val fasting = Fasting(
                startedAt = fj?.takeIf { it.has("startedAt") }?.getLong("startedAt"),
                lastCompletedHours = fj?.takeIf { it.has("lastCompletedHours") }?.getDouble("lastCompletedHours"),
            )
            return AppData(
                themeId = o.optString("themeId", defaults.themeId),
                name = o.optString("name", ""),
                goals = goals,
                addBurnedBack = o.optBoolean("addBurnedBack", true),
                followSystemDark = o.optBoolean("followSystemDark", false),
                foods = foods, customFoods = customFoods, water = water, weights = weights,
                metrics = metrics, supplements = supplements, supplementChecks = checks, fasting = fasting,
            )
        }

        private fun JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    }
}
