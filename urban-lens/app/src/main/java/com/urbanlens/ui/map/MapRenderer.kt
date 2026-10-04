package com.urbanlens.ui.map

import com.urbanlens.core.air.AqiCategory
import com.urbanlens.core.construction.ConstructionSite
import com.urbanlens.core.crowd.CrowdLevel
import com.urbanlens.core.crowd.Place
import com.urbanlens.core.geo.LatLng
import com.urbanlens.core.reports.CommunityReport
import com.urbanlens.core.routing.RouteOption
import com.urbanlens.ui.AirCell
import com.urbanlens.ui.CrowdCell
import com.urbanlens.ui.MainViewModel
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.toColor
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.backgroundColor
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Geometry
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/** Owns our sources and layers on top of the base map style. */
class MapRenderer(private val style: Style) {

    fun install() {
        warmUpBaseStyle()

        source(AIR)
        style.addLayer(FillLayer(AIR_FILL, AIR).withProperties(fillColor(toColor(get("color"))), fillOpacity(0.26f)))

        source(CROWD)
        style.addLayer(
            FillLayer(CROWD_FILL, CROWD).withProperties(
                fillColor(toColor(get("color"))),
                fillOpacity(interpolate(linear(), get("level"), stop(0, 0.06f), stop(50, 0.34f), stop(100, 0.6f))),
            ),
        )
        style.addLayer(
            LineLayer(CROWD_LINE, CROWD).withProperties(
                lineColor(toColor(get("color"))),
                lineWidth(0.6f),
                lineOpacity(0.35f),
            ),
        )

        source(SITES)
        style.addLayer(
            FillLayer(SITES_FILL, SITES)
                .withFilter(eq(get("geom"), "area"))
                .withProperties(fillColor(ORANGE), fillOpacity(0.2f)),
        )
        style.addLayer(
            LineLayer(SITES_LINE, SITES).withProperties(
                lineColor(ORANGE),
                lineWidth(interpolate(linear(), zoom(), stop(11, 1.2f), stop(16, 4f))),
                lineDasharray(arrayOf(1.6f, 1f)),
                lineOpacity(0.95f),
            ),
        )
        source(SITE_PINS)
        style.addLayer(
            CircleLayer(SITE_PINS_CIRCLE, SITE_PINS).withProperties(
                circleColor(ORANGE),
                circleRadius(interpolate(linear(), zoom(), stop(11, 3f), stop(16, 7f))),
                circleStrokeColor(INK),
                circleStrokeWidth(2f),
            ),
        )

        source(PLACES)
        style.addLayer(
            CircleLayer(PLACES_CIRCLE, PLACES).withProperties(
                circleColor(toColor(get("color"))),
                circleRadius(interpolate(linear(), zoom(), stop(12, 2.5f), stop(16, 6.5f))),
                circleStrokeColor(INK),
                circleStrokeWidth(1.2f),
                circleOpacity(0.95f),
            ).also { it.minZoom = 12.5f },
        )

        source(REPORTS)
        style.addLayer(
            CircleLayer(REPORTS_CIRCLE, REPORTS).withProperties(
                circleColor(toColor(get("color"))),
                circleRadius(8f),
                circleStrokeColor(PAPER),
                circleStrokeWidth(2.2f),
            ),
        )

        source(ROUTES)
        style.addLayer(
            LineLayer(ROUTES_ALT, ROUTES).withFilter(eq(get("selected"), false)).withProperties(
                lineColor("#8C8178"),
                lineWidth(4.5f),
                lineOpacity(0.8f),
                lineCap("round"),
                lineJoin("round"),
            ),
        )
        style.addLayer(
            LineLayer(ROUTES_CASING, ROUTES).withFilter(eq(get("selected"), true)).withProperties(
                lineColor(INK),
                lineWidth(9f),
                lineCap("round"),
                lineJoin("round"),
            ),
        )
        style.addLayer(
            LineLayer(ROUTES_MAIN, ROUTES).withFilter(eq(get("selected"), true)).withProperties(
                lineColor(ORANGE),
                lineWidth(5.5f),
                lineCap("round"),
                lineJoin("round"),
            ),
        )
        source(ROUTE_ENDS)
        style.addLayer(
            CircleLayer(ROUTE_ENDS_CIRCLE, ROUTE_ENDS).withProperties(
                circleColor(toColor(get("color"))),
                circleRadius(7f),
                circleStrokeColor(INK),
                circleStrokeWidth(2.5f),
            ),
        )

        source(USER)
        style.addLayer(
            CircleLayer(USER_HALO, USER).withProperties(circleColor(ORANGE), circleOpacity(0.18f), circleRadius(18f)),
        )
        style.addLayer(
            CircleLayer(USER_DOT, USER).withProperties(
                circleColor(ORANGE),
                circleRadius(6.5f),
                circleStrokeColor(PAPER),
                circleStrokeWidth(2.5f),
            ),
        )

        source(SELECTION)
        style.addLayer(
            CircleLayer(SELECTION_RING, SELECTION).withProperties(
                circleColor(ORANGE),
                circleOpacity(0f),
                circleRadius(15f),
                circleStrokeColor(PAPER),
                circleStrokeWidth(2.5f),
            ),
        )
    }

    fun showAir(cells: List<AirCell>, visible: Boolean) {
        setVisible(visible, AIR_FILL)
        set(AIR, cells.map { cell ->
            feature(polygon(cell.corners)) { addStringProperty("color", AqiCategory.of(cell.aqi).color) }
        })
    }

    fun showCrowd(cells: List<CrowdCell>, visible: Boolean) {
        setVisible(visible, CROWD_FILL, CROWD_LINE)
        set(CROWD, cells.map { cell ->
            feature(polygon(cell.corners)) {
                addStringProperty("color", CrowdLevel.of(cell.level).color)
                addNumberProperty("level", cell.level)
            }
        })
    }

    fun showSites(sites: List<ConstructionSite>, visible: Boolean) {
        setVisible(visible, SITES_FILL, SITES_LINE, SITE_PINS_CIRCLE)
        set(SITES, sites.map { site ->
            val points = site.shape.points
            val geometry: Geometry = when {
                site.shape.isArea && points.size >= 3 -> polygon(points)
                points.size >= 2 -> line(points)
                else -> point(points.first())
            }
            feature(geometry) {
                addStringProperty("kind", MainViewModel.KIND_SITE)
                addStringProperty("id", site.id)
                addStringProperty("geom", if (site.shape.isArea) "area" else "line")
            }
        })
        set(SITE_PINS, sites.map { site ->
            feature(point(site.shape.center)) {
                addStringProperty("kind", MainViewModel.KIND_SITE)
                addStringProperty("id", site.id)
            }
        })
    }

    /** [crowdColors] maps place id to its crowd color. */
    fun showPlaces(places: List<Place>, crowdColors: Map<String, String>, visible: Boolean) {
        setVisible(visible, PLACES_CIRCLE)
        set(PLACES, places.map { place ->
            feature(point(place.location)) {
                addStringProperty("kind", MainViewModel.KIND_PLACE)
                addStringProperty("id", place.id)
                addStringProperty("color", crowdColors[place.id] ?: TEAL)
            }
        })
    }

    fun showReports(reports: List<CommunityReport>, visible: Boolean) {
        setVisible(visible, REPORTS_CIRCLE)
        set(REPORTS, reports.map { report ->
            feature(point(report.location)) {
                addStringProperty("kind", MainViewModel.KIND_REPORT)
                addStringProperty("id", report.id.toString())
                addStringProperty("color", report.type.color)
            }
        })
    }

    fun showRoutes(options: List<RouteOption>, selectedId: Int?, origin: LatLng?, destination: LatLng?) {
        val ordered = options.sortedBy { it.id == selectedId }
        set(ROUTES, ordered.map { option ->
            feature(line(option.path)) {
                addStringProperty("kind", MainViewModel.KIND_ROUTE)
                addStringProperty("id", option.id.toString())
                addBooleanProperty("selected", option.id == selectedId)
            }
        })
        set(ROUTE_ENDS, listOfNotNull(
            origin?.let { feature(point(it)) { addStringProperty("color", PAPER) } },
            destination?.let { feature(point(it)) { addStringProperty("color", ORANGE) } },
        ))
    }

    fun showUser(location: LatLng?) = set(USER, listOfNotNull(location?.let { feature(point(it)) {} }))

    fun showSelection(location: LatLng?) = set(SELECTION, listOfNotNull(location?.let { feature(point(it)) {} }))

    /** Nudges the dark base map toward the warm palette of the app. */
    private fun warmUpBaseStyle() {
        for (layer in style.layers) {
            val id = layer.id.lowercase()
            when {
                layer is BackgroundLayer -> layer.setProperties(backgroundColor("#1B1815"))
                layer is FillLayer && "water" in id -> layer.setProperties(fillColor("#100E0C"))
                layer is FillLayer && "building" in id -> layer.setProperties(fillColor("#2A2521"))
                layer is FillLayer && ("landcover" in id || "landuse" in id || "park" in id) ->
                    layer.setProperties(fillColor("#211D19"))
            }
        }
    }

    private fun source(id: String) {
        if (style.getSource(id) == null) style.addSource(GeoJsonSource(id))
    }

    private fun set(sourceId: String, features: List<Feature>) {
        style.getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    private fun setVisible(visible: Boolean, vararg layerIds: String) {
        for (id in layerIds) {
            style.getLayer(id)?.setProperties(visibility(if (visible) "visible" else "none"))
        }
    }

    private inline fun feature(geometry: Geometry, props: Feature.() -> Unit): Feature =
        Feature.fromGeometry(geometry).apply(props)

    private fun point(p: LatLng): Point = Point.fromLngLat(p.lng, p.lat)

    private fun line(points: List<LatLng>): LineString = LineString.fromLngLats(points.map(::point))

    private fun polygon(points: List<LatLng>): Polygon {
        val ring = points.map(::point).toMutableList()
        if (points.first() != points.last()) ring += point(points.first())
        return Polygon.fromLngLats(listOf(ring))
    }

    companion object {
        /** CARTO "Dark Matter" vector style; © OpenStreetMap contributors, © CARTO. */
        const val STYLE_URL = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"

        const val ORANGE = "#F07F2B"
        const val TEAL = "#2BA389"
        const val INK = "#1B1815"
        const val PAPER = "#F3EDE6"

        private const val AIR = "ul-air"
        private const val CROWD = "ul-crowd"
        private const val SITES = "ul-sites"
        private const val SITE_PINS = "ul-site-pins"
        private const val PLACES = "ul-places"
        private const val REPORTS = "ul-reports"
        private const val ROUTES = "ul-routes"
        private const val ROUTE_ENDS = "ul-route-ends"
        private const val USER = "ul-user"
        private const val SELECTION = "ul-selection"

        private const val AIR_FILL = "ul-air-fill"
        private const val CROWD_FILL = "ul-crowd-fill"
        private const val CROWD_LINE = "ul-crowd-line"
        private const val SITES_FILL = "ul-sites-fill"
        private const val SITES_LINE = "ul-sites-line"
        private const val SITE_PINS_CIRCLE = "ul-site-pins-circle"
        private const val PLACES_CIRCLE = "ul-places-circle"
        private const val REPORTS_CIRCLE = "ul-reports-circle"
        private const val ROUTES_ALT = "ul-routes-alt"
        private const val ROUTES_CASING = "ul-routes-casing"
        private const val ROUTES_MAIN = "ul-routes-main"
        private const val ROUTE_ENDS_CIRCLE = "ul-route-ends-circle"
        private const val USER_HALO = "ul-user-halo"
        private const val USER_DOT = "ul-user-dot"
        private const val SELECTION_RING = "ul-selection-ring"

        /** Layers a tap can hit, most important first. */
        val TAPPABLE_LAYERS = arrayOf(
            REPORTS_CIRCLE, PLACES_CIRCLE, SITE_PINS_CIRCLE, SITES_LINE, SITES_FILL, ROUTES_MAIN, ROUTES_ALT,
        )
    }
}
