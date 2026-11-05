package com.pulse.tracker.data

import java.time.LocalDate
import java.time.LocalTime

enum class Meal(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snack");

    companion object {
        fun forNow(time: LocalTime = LocalTime.now()): Meal = when (time.hour) {
            in 4..10 -> BREAKFAST
            in 11..15 -> LUNCH
            in 17..21 -> DINNER
            else -> SNACK
        }
    }
}

/** A food in the catalogue. Nutrition values are per 100 g (or 100 ml). */
data class Food(
    val id: String,
    val name: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val servingName: String = "100 g",
    val servingGrams: Double = 100.0,
    val brand: String? = null,
) {
    fun kcalFor(grams: Double) = kcal * grams / 100.0
    fun proteinFor(grams: Double) = protein * grams / 100.0
    fun carbsFor(grams: Double) = carbs * grams / 100.0
    fun fatFor(grams: Double) = fat * grams / 100.0
}

data class FoodEntry(
    val id: Long,
    val date: LocalDate,
    val meal: Meal,
    val foodId: String,
    val name: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val loggedAt: Long,
)

enum class DrinkKind(val label: String, val ml: Int) {
    CUP("Cup", 150),
    GLASS("Glass", 250),
    BOTTLE("Bottle", 500),
}

data class WaterEntry(
    val id: Long,
    val date: LocalDate,
    val ml: Int,
    val kind: String,
    val loggedAt: Long,
)

data class WeightEntry(val date: LocalDate, val kg: Double)

data class DayMetrics(
    val steps: Int = 0,
    val sleepMinutes: Int = 0,
    val mindfulMinutes: Int = 0,
)

data class Supplement(val id: String, val name: String, val timing: String)

data class Goals(
    val kcal: Int = 2400,
    val protein: Int = 150,
    val carbs: Int = 260,
    val fat: Int = 80,
    val waterMl: Int = 3000,
    val steps: Int = 10000,
    val sleepMinutes: Int = 480,
    val mindfulMinutes: Int = 15,
    val fastingHours: Int = 16,
)

data class Fasting(val startedAt: Long? = null, val lastCompletedHours: Double? = null)

data class AppData(
    val themeId: String = "VOLT",
    val name: String = "",
    val goals: Goals = Goals(),
    val addBurnedBack: Boolean = true,
    val followSystemDark: Boolean = false,
    /** Steps come from Health Connect instead of manual entry. */
    val stepsFromHealthConnect: Boolean = false,
    /** When steps were last read from Health Connect (epoch ms, 0 = never). */
    val stepsSyncedAt: Long = 0L,
    val foods: List<FoodEntry> = emptyList(),
    val customFoods: List<Food> = emptyList(),
    val water: List<WaterEntry> = emptyList(),
    val weights: List<WeightEntry> = emptyList(),
    val metrics: Map<LocalDate, DayMetrics> = emptyMap(),
    val supplements: List<Supplement> = DefaultSupplements,
    val supplementChecks: Map<LocalDate, Set<String>> = emptyMap(),
    val fasting: Fasting = Fasting(),
)

val DefaultSupplements = listOf(
    Supplement("d3", "Vitamin D3", "Morning"),
    Supplement("omega", "Omega-3", "With lunch"),
    Supplement("creatine", "Creatine · 5 g", "Anytime"),
    Supplement("mag", "Magnesium", "Tonight"),
)

/** Totals for one day, derived from [AppData]. */
data class DaySummary(
    val date: LocalDate,
    val eaten: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val burned: Int,
    val waterMl: Int,
    val metrics: DayMetrics,
    val entries: List<FoodEntry>,
    val drinks: List<WaterEntry>,
) {
    val hasFood get() = entries.isNotEmpty()
}

/** Rough active-energy estimate from steps (about 0.04 kcal per step). */
fun burnedFromSteps(steps: Int): Int = (steps * 0.04).toInt()
