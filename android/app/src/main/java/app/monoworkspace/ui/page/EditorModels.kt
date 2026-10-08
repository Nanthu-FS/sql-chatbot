package app.monoworkspace.ui.page

import androidx.compose.ui.graphics.vector.ImageVector
import app.monoworkspace.data.repo.Backlinks
import app.monoworkspace.data.repo.DatabaseSnapshot
import app.monoworkspace.engine.CellValue
import app.monoworkspace.engine.FlatBlock
import app.monoworkspace.model.Block
import app.monoworkspace.model.BlockType
import app.monoworkspace.model.Page
import app.monoworkspace.model.PropertyDef
import app.monoworkspace.model.PropertyValue
import app.monoworkspace.model.ViewType
import app.monoworkspace.ui.theme.MonoIcons

data class FocusTarget(val blockId: String, val caret: Int, val nonce: Long)

data class SlashState(val blockId: String, val query: String, val selected: Int = 0)

data class EmbedRow(val id: String, val title: String, val meta: String)

data class EmbedSummary(
    val pageId: String,
    val title: String,
    val icon: String?,
    val viewName: String,
    val viewType: ViewType,
    val rows: List<EmbedRow>,
    val total: Int,
)

/** Database row context shown above the blocks of a row page. */
data class RowState(
    val snapshot: DatabaseSnapshot,
    val properties: List<PropertyDef>,
    val cells: Map<String, CellValue>,
    val values: Map<String, PropertyValue>,
)

data class EditorState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val page: Page? = null,
    val title: String = "",
    val breadcrumb: List<Page> = emptyList(),
    val flat: List<FlatBlock> = emptyList(),
    val blocks: Map<String, Block> = emptyMap(),
    val childMap: Map<String?, List<Block>> = emptyMap(),
    val versions: Map<String, Int> = emptyMap(),
    val focus: FocusTarget? = null,
    val focusedBlockId: String? = null,
    val activeBlockId: String? = null,
    val focusTitleNonce: Long = 0,
    val slash: SlashState? = null,
    val selection: Set<String> = emptySet(),
    val selectionMode: Boolean = false,
    val pages: Map<String, Page> = emptyMap(),
    val embeds: Map<String, EmbedSummary> = emptyMap(),
    val subpages: List<Page> = emptyList(),
    val backlinks: Backlinks = Backlinks(),
    val row: RowState? = null,
    val highlightBlockId: String? = null,
    val textSelection: Pair<Int, Int> = 0 to 0,
)

sealed interface EditorEvent {
    data class Navigate(val pageId: String) : EditorEvent
    data class PickImage(val blockId: String) : EditorEvent
    data class PickFile(val blockId: String) : EditorEvent
    data class EditLink(val blockId: String) : EditorEvent
    data class Message(val text: String) : EditorEvent
    data class ScrollTo(val blockId: String) : EditorEvent
    data class OpenMention(val blockId: String) : EditorEvent
    data object Closed : EditorEvent
}

enum class SlashGroup(val label: String) { Basic("Basic blocks"), Media("Media"), Advanced("Advanced"), Inline("Inline") }

enum class SlashAction(
    val label: String,
    val description: String,
    val keywords: String,
    val group: SlashGroup,
    val blockType: BlockType? = null,
) {
    TEXT("Text", "Plain paragraph", "paragraph plain", SlashGroup.Basic, BlockType.TEXT),
    H1("Heading 1", "Big section heading", "h1 title #", SlashGroup.Basic, BlockType.H1),
    H2("Heading 2", "Medium section heading", "h2 subtitle ##", SlashGroup.Basic, BlockType.H2),
    H3("Heading 3", "Small section heading", "h3 ###", SlashGroup.Basic, BlockType.H3),
    BULLET("Bulleted list", "Simple list", "bullet unordered ul -", SlashGroup.Basic, BlockType.BULLET),
    NUMBERED("Numbered list", "List with numbers", "number ordered ol 1.", SlashGroup.Basic, BlockType.NUMBERED),
    TODO("To-do list", "Track tasks with a checkbox", "todo task checkbox check []", SlashGroup.Basic, BlockType.TODO),
    TOGGLE("Toggle list", "Hide content inside", "toggle collapse fold", SlashGroup.Basic, BlockType.TOGGLE),
    QUOTE("Quote", "Capture a quotation", "quote blockquote >", SlashGroup.Basic, BlockType.QUOTE),
    CALLOUT("Callout", "Make writing stand out", "callout note info box", SlashGroup.Basic, BlockType.CALLOUT),
    DIVIDER("Divider", "Separate sections", "divider rule line hr ---", SlashGroup.Basic, BlockType.DIVIDER),
    PAGE("Page", "Embed a sub-page", "page subpage child", SlashGroup.Basic, BlockType.CHILD_PAGE),
    IMAGE("Image", "Pick from your device", "image photo picture media", SlashGroup.Media, BlockType.IMAGE),
    FILE("File", "Attach any file", "file attachment pdf upload", SlashGroup.Media, BlockType.FILE),
    LINK("Link preview", "A bookmark you describe", "link bookmark url web", SlashGroup.Media, BlockType.LINK_PREVIEW),
    CODE("Code", "Capture a snippet", "code snippet programming ```", SlashGroup.Advanced, BlockType.CODE),
    TABLE("Table", "Simple grid of cells", "table grid cells", SlashGroup.Advanced, BlockType.TABLE),
    DATABASE("Database", "Inline table database", "database table inline collection", SlashGroup.Advanced, BlockType.CHILD_DATABASE),
    COLUMNS2("2 columns", "Side-by-side layout", "columns layout two", SlashGroup.Advanced, BlockType.COLUMNS),
    COLUMNS3("3 columns", "Three-column layout", "columns layout three", SlashGroup.Advanced, BlockType.COLUMNS),
    TOC("Table of contents", "Outline of headings", "toc contents outline", SlashGroup.Advanced, BlockType.TOC),
    DATE("Date", "Mention today", "date today mention @", SlashGroup.Inline),
    MENTION("Mention a page", "Link to another page", "mention link page @", SlashGroup.Inline);

    val icon: ImageVector
        get() = when (this) {
            TEXT -> MonoIcons.Text
            H1, H2, H3 -> MonoIcons.Heading
            BULLET, NUMBERED -> MonoIcons.ListIcon
            TODO -> MonoIcons.Todo
            TOGGLE -> MonoIcons.Toggle
            QUOTE -> MonoIcons.Quote
            CALLOUT -> MonoIcons.Callout
            DIVIDER -> MonoIcons.Divider
            PAGE -> MonoIcons.Page
            IMAGE -> MonoIcons.Image
            FILE -> MonoIcons.File
            LINK -> MonoIcons.Link
            CODE -> MonoIcons.Code
            TABLE -> MonoIcons.Table
            DATABASE -> MonoIcons.Database
            COLUMNS2, COLUMNS3 -> MonoIcons.Columns
            TOC -> MonoIcons.Toc
            DATE -> MonoIcons.Calendar
            MENTION -> MonoIcons.At
        }

    companion object {
        fun filter(query: String): List<SlashAction> {
            val q = query.trim().lowercase()
            if (q.isEmpty()) return entries
            return entries.filter { it.label.lowercase().contains(q) || it.keywords.split(' ').any { k -> k.startsWith(q) } }
                .sortedBy { if (it.label.lowercase().startsWith(q)) 0 else 1 }
        }
    }
}

fun BlockType.icon(): ImageVector = when (this) {
    BlockType.TEXT -> MonoIcons.Text
    BlockType.H1, BlockType.H2, BlockType.H3 -> MonoIcons.Heading
    BlockType.BULLET, BlockType.NUMBERED -> MonoIcons.ListIcon
    BlockType.TODO -> MonoIcons.Todo
    BlockType.TOGGLE -> MonoIcons.Toggle
    BlockType.QUOTE -> MonoIcons.Quote
    BlockType.CALLOUT -> MonoIcons.Callout
    BlockType.DIVIDER -> MonoIcons.Divider
    BlockType.CODE -> MonoIcons.Code
    BlockType.IMAGE -> MonoIcons.Image
    BlockType.FILE -> MonoIcons.File
    BlockType.LINK_PREVIEW -> MonoIcons.Link
    BlockType.TABLE -> MonoIcons.Table
    BlockType.COLUMNS, BlockType.COLUMN -> MonoIcons.Columns
    BlockType.CHILD_PAGE -> MonoIcons.Page
    BlockType.CHILD_DATABASE -> MonoIcons.Database
    BlockType.TOC -> MonoIcons.Toc
}
