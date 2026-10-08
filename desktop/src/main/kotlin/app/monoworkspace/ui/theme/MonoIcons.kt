package app.monoworkspace.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Thin-line geometric icon set: 1.5 stroke on a 24 grid. Icons are drawn in
 * black and tinted by Icon(), so they work in black or secondary grey.
 */
object MonoIcons {
    private const val CIRCLE = "a"

    private fun icon(name: String, strokes: List<String>, fills: List<String> = emptyList()): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            strokes.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Square,
                    strokeLineJoin = StrokeJoin.Miter,
                )
            }
            fills.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
        }.build()

    private fun s(name: String, vararg paths: String) = icon(name, paths.toList())

    /** Circle path helper: centre and radius. */
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}${CIRCLE}$r $r 0 1 0 ${2 * r} 0${CIRCLE}$r $r 0 1 0 ${-2 * r} 0"

    val Plus by lazy { s("plus", "M12 5v14M5 12h14") }
    val Command by lazy { s("command", "M13 3L5 13.5h6.5L10.5 21 19 10.5h-6.5L13 3z") }
    val Grip by lazy { icon("grip", emptyList(), listOf("M8 5h2.2v2.2H8zM13.8 5H16v2.2h-2.2zM8 10.9h2.2v2.2H8zM13.8 10.9H16v2.2h-2.2zM8 16.8h2.2V19H8zM13.8 16.8H16V19h-2.2z")) }
    val Search by lazy { s("search", circle(11f, 11f, 6f), "M15.5 15.5L20 20") }
    val Home by lazy { s("home", "M4 11l8-7 8 7v9h-5v-6H9v6H4z") }
    val Star by lazy { s("star", "M12 4l2.4 5 5.6.8-4 3.9.9 5.5-4.9-2.6-4.9 2.6.9-5.5-4-3.9 5.6-.8z") }
    val StarFilled by lazy { icon("star_filled", listOf("M12 4l2.4 5 5.6.8-4 3.9.9 5.5-4.9-2.6-4.9 2.6.9-5.5-4-3.9 5.6-.8z"), listOf("M12 4l2.4 5 5.6.8-4 3.9.9 5.5-4.9-2.6-4.9 2.6.9-5.5-4-3.9 5.6-.8z")) }
    val Trash by lazy { s("trash", "M5 7h14M10 7V4h4v3M7 7l1 13h8l1-13M10 11v5M14 11v5") }
    val Settings by lazy { s("settings", circle(12f, 12f, 3f), "M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M5.6 18.4l2.1-2.1M16.3 7.7l2.1-2.1") }
    val Menu by lazy { s("menu", "M4 7h16M4 12h16M4 17h16") }
    val More by lazy { icon("more", emptyList(), listOf("M5 11h2.2v2.2H5zM10.9 11h2.2v2.2h-2.2zM16.8 11H19v2.2h-2.2z")) }
    val ChevronRight by lazy { s("chevron_right", "M9 6l6 6-6 6") }
    val ChevronLeft by lazy { s("chevron_left", "M15 6l-6 6 6 6") }
    val ChevronDown by lazy { s("chevron_down", "M6 9l6 6 6-6") }
    val ChevronUp by lazy { s("chevron_up", "M6 15l6-6 6 6") }
    val Check by lazy { s("check", "M5 12l5 5 9-10") }
    val X by lazy { s("x", "M6 6l12 12M18 6L6 18") }
    val Calendar by lazy { s("calendar", "M4 5h16v15H4zM4 10h16M8 3v4M16 3v4") }
    val Table by lazy { s("table", "M4 5h16v14H4zM4 10h16M10 5v14") }
    val Board by lazy { s("board", "M4 5h4v14H4zM10 5h4v9h-4zM16 5h4v12h-4z") }
    val ListIcon by lazy { s("list", "M9 7h11M9 12h11M9 17h11M4 7h1M4 12h1M4 17h1") }
    val Gallery by lazy { s("gallery", "M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z") }
    val Timeline by lazy { s("timeline", "M4 7h9M8 12h12M6 17h8") }
    val Link by lazy { s("link", "M10 14l4-4M9 8l2-2a3.5 3.5 0 0 1 5 5l-2 2M15 16l-2 2a3.5 3.5 0 0 1-5-5l2-2") }
    val Lock by lazy { s("lock", "M6 11h12v9H6zM9 11V8a3 3 0 0 1 6 0v3") }
    val Export by lazy { s("export", "M12 4v11M8 8l4-4 4 4M5 14v6h14v-6") }
    val Import by lazy { s("import", "M12 15V4M8 11l4 4 4-4M5 14v6h14v-6") }
    val Copy by lazy { s("copy", "M8 8h12v12H8zM16 8V4H4v12h4") }
    val History by lazy { s("history", "M4.5 9a8 8 0 1 1-.5 3M4 4v5h5M12 8v4l3 2") }
    val ArrowUp by lazy { s("arrow_up", "M12 19V5M6 11l6-6 6 6") }
    val ArrowDown by lazy { s("arrow_down", "M12 5v14M6 13l6 6 6-6") }
    val Back by lazy { s("back", "M19 12H5M11 6l-6 6 6 6") }
    val Page by lazy { s("page", "M6 3h8l4 4v14H6zM14 3v4h4") }
    val Filter by lazy { s("filter", "M4 6h16M7 12h10M10 18h4") }
    val Sort by lazy { s("sort", "M8 5v14M5 16l3 3 3-3M16 19V5M13 8l3-3 3 3") }
    val Group by lazy { s("group", "M4 5h16M4 10h16M7 14h13M7 19h13M4 14v5") }
    val Sliders by lazy { s("sliders", "M4 7h10M18 7h2M4 17h2M10 17h10M14 5h4v4h-4zM6 15h4v4H6z") }
    val Indent by lazy { s("indent", "M10 6h10M10 12h10M10 18h10M4 9l3 3-3 3") }
    val Outdent by lazy { s("outdent", "M10 6h10M10 12h10M10 18h10M7 9l-3 3 3 3") }
    val Image by lazy { s("image", "M4 5h16v14H4zM4 16l5-5 4 4 3-3 4 4", circle(15f, 9f, 1.2f)) }
    val File by lazy { s("file", "M6 3h8l4 4v14H6zM14 3v4h4M9 13h6M9 17h6") }
    val Code by lazy { s("code", "M9 7l-5 5 5 5M15 7l5 5-5 5") }
    val Quote by lazy { s("quote", "M6 5v14M10 8h8M10 12h8M10 16h5") }
    val Text by lazy { s("text", "M5 6h14M12 6v13") }
    val Heading by lazy { s("heading", "M6 5v14M18 5v14M6 12h12") }
    val Todo by lazy { s("todo", "M5 5h14v14H5zM8.5 12l2.5 2.5 4.5-5") }
    val Toggle by lazy { s("toggle", "M8 6l6 6-6 6M16 6v12") }
    val Callout by lazy { s("callout", "M4 5h16v14H4zM8 10h8M8 14h5") }
    val Divider by lazy { s("divider", "M4 12h16") }
    val Columns by lazy { s("columns", "M4 5h16v14H4zM12 5v14") }
    val Toc by lazy { s("toc", "M5 7h14M9 12h10M9 17h10") }
    val Sidebar by lazy { s("sidebar", "M4 5h16v14H4zM9 5v14") }
    val Template by lazy { s("template", "M4 4h16v6H4zM4 14h7v6H4zM15 14h5v6h-5z") }
    val Restore by lazy { s("restore", "M4.5 9a8 8 0 1 1-.5 3M4 4v5h5") }
    val Clock by lazy { s("clock", circle(12f, 12f, 8f), "M12 8v4l3 2") }
    val Hash by lazy { s("hash", "M10 4L8 20M16 4l-2 16M5 9h15M4 15h15") }
    val At by lazy { s("at", circle(12f, 12f, 3.5f), "M15.5 12v1.5a2.5 2.5 0 0 0 5 0V12a8.5 8.5 0 1 0-3.3 6.7") }
    val Relation by lazy { s("relation", "M7 17L17 7M9 7h8v8") }
    val Sigma by lazy { s("sigma", "M17 5H7l5 7-5 7h10") }
    val Formula by lazy { s("formula", "M15 4h-2.5a2 2 0 0 0-2 2v14M7 10h7") }
    val Status by lazy { s("status", circle(12f, 12f, 8f), "M12 4v16") }
    val Select by lazy { s("select", circle(12f, 12f, 8f), "M9 11l3 3 3-3") }
    val Tag by lazy { s("tag", "M4 4h7l9 9-7 7-9-9z", circle(8.5f, 8.5f, 1f)) }
    val Mail by lazy { s("mail", "M4 6h16v12H4zM4 6l8 7 8-7") }
    val Phone by lazy { s("phone", "M7 3h10v18H7zM11 18h2") }
    val Person by lazy { s("person", circle(12f, 8.5f, 3.5f), "M5 20a7 7 0 0 1 14 0") }
    val Swap by lazy { s("swap", "M7 7h13M17 4l3 3-3 3M17 17H4M7 14l-3 3 3 3") }
    val Pen by lazy { s("pen", "M4 20l1-5L16 4l4 4L9 19zM14 6l4 4") }
    val Eye by lazy { s("eye", "M3 12s3.5-6 9-6 9 6 9 6-3.5 6-9 6-9-6-9-6z", circle(12f, 12f, 2.5f)) }
    val EyeOff by lazy { s("eye_off", "M3 12s3.5-6 9-6 9 6 9 6-3.5 6-9 6-9-6-9-6zM4 4l16 16") }
    val Database by lazy { s("database", "M4 5h16v14H4zM4 10h16M4 14.5h16M10 5v14") }
    val Cover by lazy { s("cover", "M4 5h16v8H4zM4 17h16M4 20h10") }
    val Glyph by lazy { s("glyph", "M12 4l8 8-8 8-8-8z") }
    val Undo by lazy { s("undo", "M9 14l-5-5 5-5M4 9h10a6 6 0 0 1 0 12h-3") }
    val Storage by lazy { s("storage", "M4 5h16v5H4zM4 14h16v5H4zM7 7.5h1M7 16.5h1") }
    val Info by lazy { s("info", circle(12f, 12f, 8f), "M12 11v6M12 7.5v.5") }
}
