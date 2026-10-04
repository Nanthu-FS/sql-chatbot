package com.urbanlens.core.net

import com.urbanlens.core.geo.LatLng
import com.urbanlens.core.routing.RouteGeometry
import com.urbanlens.core.routing.TravelMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.io.IOException

class RoutingException(message: String) : IOException(message)

/** OSRM routing on the FOSSGIS servers (car, bike and foot profiles). Free, no key. */
object Osrm {
    const val BASE = "https://routing.openstreetmap.de"

    fun url(mode: TravelMode, waypoints: List<LatLng>, alternatives: Int = 0): String {
        require(waypoints.size >= 2)
        val coords = waypoints.joinToString(";") { "${coord(it.lng)},${coord(it.lat)}" }
        val alt = if (alternatives > 0) alternatives.toString() else "false"
        return "$BASE/${mode.osrmProfile}/route/v1/driving/$coords" +
            "?overview=full&geometries=geojson&steps=false&alternatives=$alt"
    }

    fun parse(json: String): List<RouteGeometry> {
        val root = Json.parseToJsonElement(json) as? JsonObject ?: throw RoutingException("Unexpected routing response")
        val code = root.string("code")
        if (code != "Ok") throw RoutingException(root.string("message") ?: "Routing failed ($code)")
        return root.arr("routes").orEmpty().filterIsInstance<JsonObject>().mapNotNull { route ->
            val coordinates = route.obj("geometry")?.arr("coordinates") ?: return@mapNotNull null
            val path = coordinates.mapNotNull { pair ->
                val values = (pair as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull }
                if (values == null || values.size < 2) null else LatLng(values[1], values[0])
            }
            if (path.size < 2) return@mapNotNull null
            RouteGeometry(
                path = path,
                distanceMeters = route.double("distance") ?: 0.0,
                durationSeconds = route.double("duration") ?: 0.0,
            )
        }
    }
}
