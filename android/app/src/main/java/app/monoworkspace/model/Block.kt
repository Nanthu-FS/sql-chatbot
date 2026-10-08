package app.monoworkspace.model

import kotlinx.serialization.Serializable

@Serializable
enum class BlockType(val label: String, val isText: Boolean) {
    TEXT("Text", true),
    H1("Heading 1", true),
    H2("Heading 2", true),
    H3("Heading 3", true),
    BULLET("Bulleted list", true),
    NUMBERED("Numbered list", true),
    TODO("To-do", true),
    TOGGLE("Toggle", true),
    QUOTE("Quote", true),
    CALLOUT("Callout", true),
    DIVIDER("Divider", false),
    CODE("Code", true),
    IMAGE("Image", false),
    FILE("File", false),
    LINK_PREVIEW("Link preview", false),
    TABLE("Table", false),
    COLUMNS("Columns", false),
    COLUMN("Column", false),
    CHILD_PAGE("Page", false),
    CHILD_DATABASE("Database", false),
    TOC("Table of contents", false);

    val isHeading: Boolean get() = this == H1 || this == H2 || this == H3
    val isListLike: Boolean get() = this == BULLET || this == NUMBERED || this == TODO
    /** Types that "Turn into" can convert between without losing content. */
    val canTurnInto: Boolean get() = isText && this != CODE || this == CODE

    companion object {
        val turnIntoTargets = listOf(TEXT, H1, H2, H3, BULLET, NUMBERED, TODO, TOGGLE, QUOTE, CALLOUT, CODE)
    }
}

@Serializable
data class TableColumn(val name: String, val width: Int = 140)

@Serializable
data class TableData(
    val columns: List<TableColumn> = listOf(TableColumn("Column 1"), TableColumn("Column 2")),
    val rows: List<List<String>> = listOf(listOf("", ""), listOf("", "")),
) {
    fun normalized(): TableData {
        val cols = columns.take(MAX_COLUMNS).ifEmpty { listOf(TableColumn("Column 1")) }
        val r = rows.take(MAX_ROWS).map { row -> List(cols.size) { i -> row.getOrElse(i) { "" } } }
        return TableData(cols, r)
    }

    companion object {
        const val MAX_COLUMNS = 20
        const val MAX_ROWS = 200
    }
}

@Serializable
data class BlockProps(
    val checked: Boolean = false,
    val collapsed: Boolean = false,
    val glyph: String? = null,
    val language: String? = null,
    /** File name inside filesDir/media. */
    val mediaPath: String? = null,
    val widthFraction: Float = 1f,
    val caption: List<Span> = emptyList(),
    val fileName: String? = null,
    val fileSize: Long? = null,
    val mimeType: String? = null,
    val url: String? = null,
    val title: String? = null,
    val description: String? = null,
    val table: TableData? = null,
    val pageId: String? = null,
    val databaseId: String? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
)

data class Block(
    val id: String,
    val pageId: String,
    val parentBlockId: String?,
    val orderKey: String,
    val type: BlockType,
    val content: List<Span>,
    val props: BlockProps,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val text: String get() = RichText.plain(content)
}

object CalloutGlyphs {
    val all = listOf("◆", "●", "▲", "■", "○", "□", "△", "◇")
}

object CodeLanguages {
    val all = listOf(
        "plain", "kotlin", "java", "javascript", "typescript", "python", "swift", "go", "rust",
        "c", "cpp", "csharp", "sql", "json", "yaml", "xml", "html", "css", "bash", "markdown",
    )
}
