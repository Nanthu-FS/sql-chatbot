package com.urbanlens.core.crowd

import com.urbanlens.core.geo.LatLng

/**
 * Kinds of places that draw crowds, with a typical weekday hourly busyness curve (0..1, index = hour)
 * and how much busier they get at weekends. Curves are hand-tuned approximations, not measurements.
 */
enum class PlaceCategory(
    val label: String,
    /** Relative number of people at peak, compared with other categories. */
    val peopleScale: Double,
    /** How far (meters) the crowd spills out around the place. */
    val spreadMeters: Double,
    val weekendFactor: Double,
    private val weekday: DoubleArray,
) {
    MALL(
        "Mall", 10.0, 220.0, 1.35,
        doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.05, 0.2, 0.35, 0.5, 0.55, 0.5, 0.5, 0.55, 0.7, 0.85, 0.95, 0.9, 0.7, 0.35, 0.05),
    ),
    MARKET(
        "Market", 9.0, 200.0, 1.2,
        doubleArrayOf(0.05, 0.0, 0.0, 0.0, 0.1, 0.3, 0.6, 0.8, 0.85, 0.75, 0.6, 0.5, 0.45, 0.4, 0.35, 0.4, 0.55, 0.75, 0.9, 0.85, 0.6, 0.35, 0.15, 0.05),
    ),
    TRANSIT_STATION(
        "Station", 10.0, 180.0, 0.7,
        doubleArrayOf(0.05, 0.02, 0.02, 0.02, 0.1, 0.25, 0.5, 0.8, 1.0, 0.9, 0.6, 0.45, 0.45, 0.45, 0.45, 0.5, 0.65, 0.9, 1.0, 0.85, 0.6, 0.4, 0.25, 0.1),
    ),
    BUS_STATION(
        "Bus terminus", 7.0, 150.0, 0.8,
        doubleArrayOf(0.1, 0.05, 0.05, 0.05, 0.15, 0.3, 0.55, 0.8, 0.95, 0.85, 0.6, 0.5, 0.5, 0.5, 0.5, 0.55, 0.7, 0.9, 0.95, 0.8, 0.6, 0.45, 0.3, 0.15),
    ),
    CAFE(
        "Café", 2.0, 80.0, 1.2,
        doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.05, 0.2, 0.45, 0.6, 0.55, 0.45, 0.45, 0.5, 0.45, 0.4, 0.45, 0.6, 0.75, 0.8, 0.7, 0.5, 0.3, 0.1, 0.02),
    ),
    RESTAURANT(
        "Restaurant", 3.0, 90.0, 1.3,
        doubleArrayOf(0.05, 0.0, 0.0, 0.0, 0.0, 0.0, 0.05, 0.15, 0.25, 0.2, 0.2, 0.4, 0.85, 0.9, 0.6, 0.3, 0.25, 0.35, 0.55, 0.85, 1.0, 0.85, 0.5, 0.15),
    ),
    FAST_FOOD(
        "Fast food", 2.5, 80.0, 1.2,
        doubleArrayOf(0.05, 0.02, 0.0, 0.0, 0.0, 0.0, 0.05, 0.15, 0.25, 0.25, 0.3, 0.5, 0.85, 0.8, 0.55, 0.4, 0.45, 0.6, 0.75, 0.85, 0.8, 0.6, 0.35, 0.15),
    ),
    CINEMA(
        "Cinema", 5.0, 120.0, 1.5,
        doubleArrayOf(0.1, 0.05, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.05, 0.2, 0.35, 0.45, 0.5, 0.55, 0.55, 0.5, 0.6, 0.8, 0.95, 0.9, 0.75, 0.6, 0.3),
    ),
    WORSHIP(
        "Place of worship", 4.0, 120.0, 1.3,
        doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.05, 0.35, 0.7, 0.65, 0.45, 0.35, 0.3, 0.25, 0.3, 0.2, 0.15, 0.2, 0.3, 0.55, 0.75, 0.6, 0.35, 0.15, 0.05, 0.0),
    ),
    HOSPITAL(
        "Hospital", 5.0, 150.0, 0.8,
        doubleArrayOf(0.25, 0.2, 0.2, 0.2, 0.2, 0.25, 0.35, 0.5, 0.75, 0.9, 0.95, 0.9, 0.8, 0.7, 0.65, 0.6, 0.6, 0.65, 0.7, 0.65, 0.55, 0.45, 0.35, 0.3),
    ),
    EDUCATION(
        "School / college", 6.0, 160.0, 0.2,
        doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.05, 0.35, 0.85, 0.9, 0.85, 0.8, 0.75, 0.75, 0.7, 0.6, 0.45, 0.25, 0.15, 0.1, 0.05, 0.0, 0.0, 0.0),
    ),
    PARK(
        "Park", 4.0, 160.0, 1.4,
        doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.05, 0.4, 0.75, 0.6, 0.35, 0.2, 0.15, 0.1, 0.1, 0.1, 0.1, 0.15, 0.35, 0.7, 0.85, 0.6, 0.3, 0.1, 0.03, 0.0),
    ),
    BEACH(
        "Beach", 12.0, 350.0, 1.6,
        doubleArrayOf(0.05, 0.0, 0.0, 0.0, 0.05, 0.35, 0.55, 0.4, 0.25, 0.15, 0.1, 0.1, 0.1, 0.1, 0.15, 0.3, 0.6, 0.9, 1.0, 0.85, 0.55, 0.3, 0.15, 0.08),
    ),
    ATTRACTION(
        "Attraction", 5.0, 150.0, 1.4,
        doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.05, 0.15, 0.3, 0.5, 0.7, 0.8, 0.8, 0.75, 0.75, 0.8, 0.85, 0.75, 0.55, 0.35, 0.15, 0.05, 0.0, 0.0),
    ),
    STADIUM(
        "Stadium", 6.0, 250.0, 1.5,
        doubleArrayOf(0.02, 0.02, 0.0, 0.0, 0.0, 0.05, 0.15, 0.15, 0.1, 0.08, 0.08, 0.08, 0.08, 0.08, 0.08, 0.1, 0.15, 0.25, 0.3, 0.3, 0.25, 0.15, 0.05, 0.02),
    );

    /** Typical busyness (0..1) at [hour] on a weekday. */
    fun weekdayBusyness(hour: Int): Double = weekday[hour.mod(24)]

    companion object {
        /** Maps OpenStreetMap tags to a category, or null for places we don't model. */
        fun fromOsmTags(tags: Map<String, String>): PlaceCategory? {
            val amenity = tags["amenity"]
            val shop = tags["shop"]
            return when {
                shop == "mall" || shop == "department_store" -> MALL
                amenity == "marketplace" -> MARKET
                tags["railway"] == "station" -> TRANSIT_STATION
                amenity == "bus_station" -> BUS_STATION
                amenity == "cafe" -> CAFE
                amenity == "restaurant" -> RESTAURANT
                amenity == "fast_food" -> FAST_FOOD
                amenity == "cinema" -> CINEMA
                amenity == "place_of_worship" -> WORSHIP
                amenity == "hospital" -> HOSPITAL
                amenity == "university" || amenity == "college" || amenity == "school" -> EDUCATION
                tags["leisure"] == "park" -> PARK
                tags["leisure"] == "stadium" -> STADIUM
                tags["natural"] == "beach" -> BEACH
                tags["tourism"] == "attraction" || tags["tourism"] == "museum" -> ATTRACTION
                else -> null
            }
        }
    }
}

data class Place(
    val id: String,
    val name: String?,
    val category: PlaceCategory,
    val location: LatLng,
) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: category.label
}

enum class CrowdLevel(val label: String, val color: String) {
    QUIET("Quiet", "#2BA389"),
    MODERATE("Moderate", "#E3C04B"),
    BUSY("Busy", "#F08A24"),
    PACKED("Packed", "#E5484D");

    companion object {
        /** [level] is 0..100. */
        fun of(level: Int): CrowdLevel = when {
            level < 25 -> QUIET
            level < 50 -> MODERATE
            level < 75 -> BUSY
            else -> PACKED
        }
    }
}
