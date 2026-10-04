package com.urbanlens.core.construction

import com.urbanlens.core.air.PollutionHotspot
import com.urbanlens.core.geo.Shape

enum class ConstructionKind(
    val label: String,
    val impacts: List<String>,
    /** Relative dust output, used for the local air-quality bump. */
    val dustFactor: Double,
) {
    ROAD("Road works", listOf("Lane closures", "Traffic diversions", "Noise"), 0.6),
    BUILDING("Building construction", listOf("Noise", "Dust", "Heavy vehicles"), 1.0),
    RAIL("Metro / rail works", listOf("Road narrowing", "Heavy machinery", "Noise & dust"), 1.2),
    SITE("Construction site", listOf("Noise", "Dust"), 0.9);

    companion object {
        /** Maps OpenStreetMap tags to a construction kind, or null if the element isn't under construction. */
        fun fromOsmTags(tags: Map<String, String>): ConstructionKind? {
            val future = tags["construction"]
            return when {
                tags["railway"] == "construction" -> RAIL
                tags["highway"] == "construction" -> if (future in RAIL_VALUES) RAIL else ROAD
                tags["building"] == "construction" -> BUILDING
                tags["landuse"] == "construction" -> when (future) {
                    in RAIL_VALUES -> RAIL
                    "building", "residential", "commercial", "apartments" -> BUILDING
                    else -> SITE
                }
                else -> null
            }
        }

        private val RAIL_VALUES = setOf("rail", "subway", "light_rail", "monorail", "tram", "station")
    }
}

enum class DataSource(val label: String) {
    OPENSTREETMAP("OpenStreetMap"),
    COMMUNITY("Community report"),
}

data class ConstructionSite(
    val id: String,
    val kind: ConstructionKind,
    val name: String?,
    val shape: Shape,
    val startDate: String? = null,
    val expectedEnd: String? = null,
    val operator: String? = null,
    val note: String? = null,
    val source: DataSource = DataSource.OPENSTREETMAP,
) {
    val title: String get() = name?.takeIf { it.isNotBlank() } ?: kind.label

    fun toHotspot(): PollutionHotspot = PollutionHotspot.constructionDust(kind.label, shape, kind.dustFactor)
}
