package app.monoworkspace.model

import kotlinx.serialization.Serializable

/** Portable page tree used by built-in templates, user templates and seeding. */
@Serializable
data class TemplatePayload(
    val name: String,
    val description: String = "",
    val glyph: String = "◆",
    val page: TemplatePage,
)

@Serializable
data class TemplatePage(
    val title: String,
    val icon: String? = null,
    val cover: String? = null,
    val blocks: List<TemplateBlock> = emptyList(),
    /** When set, this page is a database. */
    val database: TemplateDatabase? = null,
)

@Serializable
data class TemplateBlock(
    val type: BlockType,
    /** Shorthand for a single plain span. */
    val text: String = "",
    val content: List<Span>? = null,
    val props: BlockProps = BlockProps(),
    val children: List<TemplateBlock> = emptyList(),
    /** For CHILD_PAGE and CHILD_DATABASE blocks: the page to create. */
    val page: TemplatePage? = null,
) {
    fun spans(): List<Span> = content ?: RichText.of(text)
}

@Serializable
data class TemplateDatabase(
    val properties: List<PropertyDef>,
    val views: List<TemplateView> = listOf(TemplateView("Table", ViewType.TABLE)),
    val rows: List<TemplateRow> = emptyList(),
)

@Serializable
data class TemplateView(val name: String, val type: ViewType, val config: ViewConfig = ViewConfig())

@Serializable
data class TemplateRow(
    val title: String,
    val icon: String? = null,
    val values: Map<String, PropertyValue> = emptyMap(),
    val blocks: List<TemplateBlock> = emptyList(),
)
