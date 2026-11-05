package com.pulse.tracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pulse.tracker.data.AppData
import com.pulse.tracker.data.DayMetrics
import com.pulse.tracker.data.DaySummary
import com.pulse.tracker.data.Food
import com.pulse.tracker.data.FoodCatalog
import com.pulse.tracker.data.FoodEntry
import com.pulse.tracker.data.Goals
import com.pulse.tracker.data.HealthStatus
import com.pulse.tracker.data.HealthSteps
import com.pulse.tracker.data.Meal
import com.pulse.tracker.data.OpenFoodFacts
import com.pulse.tracker.data.Store
import com.pulse.tracker.data.Supplement
import com.pulse.tracker.data.WaterEntry
import com.pulse.tracker.data.WeightEntry
import com.pulse.tracker.data.burnedFromSteps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

class PulseViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app, viewModelScope)
    val data: StateFlow<AppData> = store.data

    private val _date = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _date.asStateFlow()

    private val ids = AtomicLong(System.currentTimeMillis() * 1000)
    private fun nextId() = ids.incrementAndGet()

    // ------------------------------------------------------------ health connect

    private val health = HealthSteps(app)
    val healthPermissions: Set<String> get() = health.permissions

    private val _health = MutableStateFlow(HealthStatus.UNKNOWN)
    val healthStatus: StateFlow<HealthStatus> = _health.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()
    private val syncLock = Mutex()

    init {
        refreshHealth()
    }

    fun refreshHealth() {
        viewModelScope.launch { _health.value = runCatching { health.status() }.getOrDefault(HealthStatus.UNSUPPORTED) }
    }

    /** Called with whatever the person granted on Health Connect's permission screen. */
    fun onHealthPermissionsResult(granted: Set<String>) {
        if (granted.containsAll(health.permissions)) {
            _health.value = HealthStatus.CONNECTED
            store.update { it.copy(stepsFromHealthConnect = true) }
            syncSteps(force = true)
        } else {
            refreshHealth()
        }
    }

    fun disconnectHealth() = store.update { it.copy(stepsFromHealthConnect = false) }

    fun openHealthInstall() = health.openInstall()
    fun openHealthSettings() = health.openSettings()

    /**
     * Pulls the last five weeks of daily step totals so Today, Habits and the Insights
     * averages all stay current. Skipped unless the person turned Health Connect on.
     */
    fun syncSteps(force: Boolean = false) {
        if (!data.value.stepsFromHealthConnect) return
        viewModelScope.launch {
            if (syncLock.isLocked) return@launch
            syncLock.withLock {
                if (!force && System.currentTimeMillis() - data.value.stepsSyncedAt < 20_000) return@withLock
                _syncing.value = true
                try {
                    val status = runCatching { health.status() }.getOrDefault(HealthStatus.UNSUPPORTED)
                    _health.value = status
                    if (status != HealthStatus.CONNECTED) return@withLock
                    val today = LocalDate.now()
                    val steps = runCatching { health.dailySteps(today.minusDays(34), today) }.getOrNull() ?: return@withLock
                    store.update { d ->
                        val merged = d.metrics.toMutableMap()
                        steps.forEach { (day, count) -> merged[day] = (merged[day] ?: DayMetrics()).copy(steps = count) }
                        d.copy(metrics = merged, stepsSyncedAt = System.currentTimeMillis())
                    }
                } finally {
                    _syncing.value = false
                }
            }
        }
    }

    fun selectDate(date: LocalDate) {
        _date.value = if (date.isAfter(LocalDate.now())) LocalDate.now() else date
    }

    // ------------------------------------------------------------ settings

    fun setTheme(id: String) = store.update { it.copy(themeId = id) }
    fun setName(name: String) = store.update { it.copy(name = name.take(30)) }
    fun setGoals(goals: Goals) = store.update { it.copy(goals = goals) }
    fun setAddBurnedBack(on: Boolean) = store.update { it.copy(addBurnedBack = on) }

    // ------------------------------------------------------------ food

    fun logFood(food: Food, grams: Double, meal: Meal, date: LocalDate = _date.value) = store.update { d ->
        val entry = FoodEntry(
            id = nextId(), date = date, meal = meal, foodId = food.id, name = food.name, grams = grams,
            kcal = food.kcalFor(grams), protein = food.proteinFor(grams), carbs = food.carbsFor(grams),
            fat = food.fatFor(grams), loggedAt = System.currentTimeMillis(),
        )
        val custom = if (FoodCatalog.find(food.id) == null && d.customFoods.none { it.id == food.id }) {
            listOf(food) + d.customFoods
        } else d.customFoods
        d.copy(foods = d.foods + entry, customFoods = custom.take(200))
    }

    fun deleteFood(id: Long) = store.update { d -> d.copy(foods = d.foods.filterNot { it.id == id }) }

    fun findFood(id: String, d: AppData): Food? = FoodCatalog.find(id) ?: d.customFoods.firstOrNull { it.id == id }

    /** Distinct foods logged most recently, newest first. */
    fun recentFoods(d: AppData, limit: Int = 8): List<Pair<Food, Double>> =
        d.foods.sortedByDescending { it.loggedAt }
            .distinctBy { it.foodId }
            .mapNotNull { e -> findFood(e.foodId, d)?.let { it to e.grams } }
            .take(limit)

    suspend fun lookupBarcode(code: String): OpenFoodFacts.Result = OpenFoodFacts.lookup(code)

    // ------------------------------------------------------------ water

    fun addWater(ml: Int, kind: String, date: LocalDate = _date.value) = store.update { d ->
        d.copy(water = d.water + WaterEntry(nextId(), date, ml, kind, System.currentTimeMillis()))
    }

    fun undoWater(date: LocalDate = _date.value) = store.update { d ->
        val last = d.water.filter { it.date == date }.maxByOrNull { it.loggedAt } ?: return@update d
        d.copy(water = d.water.filterNot { it.id == last.id })
    }

    fun deleteWater(id: Long) = store.update { d -> d.copy(water = d.water.filterNot { it.id == id }) }

    // ------------------------------------------------------------ essentials

    private fun updateMetrics(date: LocalDate, f: (DayMetrics) -> DayMetrics) = store.update { d ->
        d.copy(metrics = d.metrics + (date to f(d.metrics[date] ?: DayMetrics())))
    }

    fun setSteps(steps: Int, date: LocalDate = _date.value) = updateMetrics(date) { it.copy(steps = steps.coerceIn(0, 200_000)) }
    fun setSleep(minutes: Int, date: LocalDate = _date.value) = updateMetrics(date) { it.copy(sleepMinutes = minutes.coerceIn(0, 24 * 60)) }
    fun addMindful(minutes: Int, date: LocalDate = _date.value) =
        updateMetrics(date) { it.copy(mindfulMinutes = (it.mindfulMinutes + minutes).coerceIn(0, 600)) }

    fun logWeight(kg: Double, date: LocalDate = _date.value) = store.update { d ->
        d.copy(weights = (d.weights.filterNot { it.date == date } + WeightEntry(date, kg)).sortedBy { it.date })
    }

    fun toggleSupplement(id: String, date: LocalDate = _date.value) = store.update { d ->
        val current = d.supplementChecks[date] ?: emptySet()
        val next = if (id in current) current - id else current + id
        d.copy(supplementChecks = d.supplementChecks + (date to next))
    }

    fun addSupplement(name: String, timing: String) = store.update { d ->
        d.copy(supplements = d.supplements + Supplement("s${nextId()}", name.trim(), timing.trim().ifBlank { "Anytime" }))
    }

    fun removeSupplement(id: String) = store.update { d -> d.copy(supplements = d.supplements.filterNot { it.id == id }) }

    fun startFast() = store.update { d -> d.copy(fasting = d.fasting.copy(startedAt = System.currentTimeMillis())) }

    fun endFast() = store.update { d ->
        val started = d.fasting.startedAt ?: return@update d
        val hours = (System.currentTimeMillis() - started) / 3_600_000.0
        d.copy(fasting = d.fasting.copy(startedAt = null, lastCompletedHours = hours))
    }

    fun setFastingHours(h: Int) = store.update { d -> d.copy(goals = d.goals.copy(fastingHours = h)) }
}

// ---------------------------------------------------------------- derived data

fun summarize(d: AppData, date: LocalDate): DaySummary {
    val entries = d.foods.filter { it.date == date }
    val drinks = d.water.filter { it.date == date }.sortedByDescending { it.loggedAt }
    val m = d.metrics[date] ?: DayMetrics()
    return DaySummary(
        date = date,
        eaten = entries.sumOf { it.kcal },
        protein = entries.sumOf { it.protein },
        carbs = entries.sumOf { it.carbs },
        fat = entries.sumOf { it.fat },
        burned = burnedFromSteps(m.steps),
        waterMl = drinks.sumOf { it.ml },
        metrics = m,
        entries = entries.sortedBy { it.loggedAt },
        drinks = drinks,
    )
}

fun kcalLeft(d: AppData, s: DaySummary): Int =
    (d.goals.kcal - s.eaten + if (d.addBurnedBack) s.burned else 0).toInt()

/** Consecutive days (ending today, or yesterday if today is empty) that satisfy [ok]. */
fun streak(today: LocalDate, ok: (LocalDate) -> Boolean): Int {
    var day = if (ok(today)) today else today.minusDays(1)
    var n = 0
    while (ok(day) && n < 3650) {
        n++
        day = day.minusDays(1)
    }
    return n
}

fun bestStreak(days: List<LocalDate>, ok: (LocalDate) -> Boolean): Int {
    var best = 0
    var run = 0
    days.sorted().forEach { if (ok(it)) { run++; best = maxOf(best, run) } else run = 0 }
    return best
}
