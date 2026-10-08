package app.monoworkspace.model

data class Page(
    val id: String,
    val parentId: String?,
    val databaseId: String?,
    val isDatabase: Boolean,
    val title: String,
    val icon: String?,
    val cover: String?,
    val orderKey: String,
    val isFavorite: Boolean,
    val favoriteOrder: String?,
    val isTrashed: Boolean,
    val trashRoot: Boolean,
    val trashedAt: Long?,
    val createdAt: Long,
    val editedAt: Long,
) {
    val isRow: Boolean get() = databaseId != null
    val displayTitle: String get() = title.ifBlank { "Untitled" }
}

/** Covers are generative mono patterns or an image from the device. */
object Covers {
    const val IMAGE_PREFIX = "image:"
    const val PATTERN_PREFIX = "pattern:"
    val patterns = listOf("solid", "stripes", "grid", "dots", "diagonal", "checker", "rules", "halftone")

    fun pattern(name: String) = PATTERN_PREFIX + name
    fun image(path: String) = IMAGE_PREFIX + path
}

/** Page icons: geometric glyphs offered in the picker; emoji are typed in. */
object PageGlyphs {
    val all = listOf("◆", "●", "▲", "■", "○", "□", "△", "◇", "◐", "◑", "◒", "◓", "✦", "✕", "＋", "★", "☐", "▦", "▤", "▥", "◎", "◉", "⬡", "⬢")
}
