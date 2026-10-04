package com.urbanlens.core.net

import com.urbanlens.core.construction.ConstructionKind
import com.urbanlens.core.construction.ConstructionSite
import com.urbanlens.core.crowd.Place
import com.urbanlens.core.crowd.PlaceCategory
import com.urbanlens.core.geo.BoundingBox
import com.urbanlens.core.geo.LatLng
import com.urbanlens.core.geo.Shape
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException

/** Overpass answered, but the query itself failed on the server (timeout, out of memory...). */
class OverpassException(message: String) : IOException(message)

/** Construction sites and crowd-drawing places inside an area, from OpenStreetMap. */
data class AreaData(val sites: List<ConstructionSite>, val places: List<Place>)

/** OpenStreetMap data via the Overpass API. Free, no key; be gentle with request volume. */
object Overpass {
    const val MAIN = "https://overpass-api.de/api/interpreter"
    const val MIRROR = "https://overpass.private.coffee/api/interpreter"

    /** Every server we know of; see [ATTEMPTS] for the order they're tried in. */
    val ENDPOINTS = listOf(MAIN, MIRROR)

    /**
     * Retry order. The main server is fast and its failures are usually brief rate limits, so it
     * gets a second chance before the mirror (which can be slow, see LiveApiTest).
     */
    val ATTEMPTS = listOf(MAIN, MAIN, MIRROR)

    /**
     * Big crowd magnets (malls, stations, beaches...) and small everyday spots (cafés, schools...)
     * get separate output limits so a dense city can't crowd the important places out.
     */
    fun query(box: BoundingBox, maxSites: Int = 250, maxMajorPlaces: Int = 500, maxMinorPlaces: Int = 500): String {
        val b = "${coord(box.south)},${coord(box.west)},${coord(box.north)},${coord(box.east)}"
        return """
            [out:json][timeout:25];
            (
              way["highway"="construction"]($b);
              way["railway"="construction"]($b);
              way["building"="construction"]($b);
              way["landuse"="construction"]($b);
            )->.c;
            .c out geom $maxSites;
            (
              nwr["shop"~"^(mall|department_store)$"]($b);
              nwr["amenity"~"^(marketplace|bus_station|cinema|place_of_worship|hospital|university|college)$"]($b);
              nwr["railway"="station"]($b);
              nwr["leisure"~"^(park|stadium)$"]($b);
              nwr["natural"="beach"]($b);
              nwr["tourism"~"^(attraction|museum)$"]($b);
            )->.major;
            .major out center $maxMajorPlaces;
            (
              nwr["amenity"~"^(cafe|restaurant|fast_food|school)$"]($b);
            )->.minor;
            .minor out center $maxMinorPlaces;
        """.trimIndent()
    }

    fun parse(json: String): AreaData {
        val root = Json.parseToJsonElement(json) as? JsonObject ?: throw OverpassException("Unexpected Overpass response")
        // Server-side failures still come back as HTTP 200, with partial data and a remark.
        root.string("remark")?.takeIf { it.contains("error", ignoreCase = true) }?.let { throw OverpassException(it) }
        val sites = mutableListOf<ConstructionSite>()
        val places = mutableListOf<Place>()
        val seenPlaces = HashSet<String>()
        for (element in root.arr("elements")?.filterIsInstance<JsonObject>().orEmpty()) {
            val type = element.string("type") ?: continue
            val id = element["id"]?.let { (it as? JsonPrimitive)?.content } ?: continue
            val tags = element.obj("tags")?.mapValues { (_, v) -> (v as? JsonPrimitive)?.content.orEmpty() }.orEmpty()
            val osmId = "osm:$type/$id"

            val kind = ConstructionKind.fromOsmTags(tags)
            if (kind != null) {
                val shape = shapeOf(element) ?: continue
                sites += ConstructionSite(
                    id = osmId,
                    kind = kind,
                    name = tags["name"] ?: tags["name:en"],
                    shape = shape,
                    startDate = tags["start_date"],
                    expectedEnd = tags["opening_date"],
                    operator = tags["operator"],
                    note = tags["description"] ?: tags["note"],
                )
                continue
            }

            val category = PlaceCategory.fromOsmTags(tags) ?: continue
            val location = pointOf(element) ?: continue
            if (!seenPlaces.add(osmId)) continue
            places += Place(
                id = osmId,
                name = tags["name:en"] ?: tags["name"],
                category = category,
                location = location,
            )
        }
        return AreaData(sites, places)
    }

    private fun shapeOf(element: JsonObject): Shape? {
        val geometry = element.arr("geometry")
            ?.filterIsInstance<JsonObject>()
            ?.mapNotNull { p -> LatLng(p.double("lat") ?: return@mapNotNull null, p.double("lon") ?: return@mapNotNull null) }
        if (!geometry.isNullOrEmpty()) {
            val closed = geometry.size >= 4 && geometry.first() == geometry.last()
            return Shape(geometry, isArea = closed)
        }
        return pointOf(element)?.let { Shape.point(it) }
    }

    private fun pointOf(element: JsonObject): LatLng? {
        val lat = element.double("lat")
        val lon = element.double("lon")
        if (lat != null && lon != null) return LatLng(lat, lon)
        val center = element.obj("center") ?: return null
        return LatLng(center.double("lat") ?: return null, center.double("lon") ?: return null)
    }
}
