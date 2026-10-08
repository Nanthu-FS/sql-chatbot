package app.monoworkspace.engine

import app.monoworkspace.model.Block
import app.monoworkspace.model.BlockType
import app.monoworkspace.model.Mark
import app.monoworkspace.model.Span
import app.monoworkspace.model.SpanKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Converts a page's blocks to CommonMark-flavoured Markdown. */
class MarkdownExporter(
    private val pageTitle: (String) -> String = { "Untitled" },
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    data class Result(val markdown: String, val media: List<String>)

    private val dateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy")
    private val dateTimeFmt = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")

    fun export(title: String, blocks: List<Block>): Result {
        val media = ArrayList<String>()
        val map = BlockTree.childrenMap(blocks)
        val out = StringBuilder()
        out.append("# ").append(escapeLine(title.ifBlank { "Untitled" })).append("\n\n")
        renderChildren(null, map, 0, out, media, blocks)
        return Result(out.toString().trimEnd() + "\n", media)
    }

    private fun renderChildren(
        parentId: String?,
        map: Map<String?, List<Block>>,
        indent: Int,
        out: StringBuilder,
        media: MutableList<String>,
        all: List<Block>,
    ) {
        var number = 0
        val kids = map[parentId].orEmpty()
        for ((i, b) in kids.withIndex()) {
            number = if (b.type == BlockType.NUMBERED) number + 1 else 0
            val pad = "    ".repeat(indent)
            val text = inline(b.content)
            val next = kids.getOrNull(i + 1)
            val tight = b.type.isListLike && next != null && next.type.isListLike
            when (b.type) {
                BlockType.TEXT -> out.append(pad).append(text).append("\n")
                BlockType.H1 -> out.append(pad).append("# ").append(text).append("\n")
                BlockType.H2 -> out.append(pad).append("## ").append(text).append("\n")
                BlockType.H3 -> out.append(pad).append("### ").append(text).append("\n")
                BlockType.BULLET -> out.append(pad).append("- ").append(text).append("\n")
                BlockType.NUMBERED -> out.append(pad).append(number).append(". ").append(text).append("\n")
                BlockType.TODO -> out.append(pad).append(if (b.props.checked) "- [x] " else "- [ ] ").append(text).append("\n")
                BlockType.TOGGLE -> out.append(pad).append("- ").append(text).append("\n")
                BlockType.QUOTE -> out.append(pad).append(text.lines().joinToString("\n") { "$pad> $it" }.removePrefix(pad)).append("\n")
                BlockType.CALLOUT -> out.append(pad).append("> ").append(b.props.glyph ?: "◆").append(" ").append(text).append("\n")
                BlockType.DIVIDER -> out.append(pad).append("---\n")
                BlockType.CODE -> {
                    val lang = b.props.language?.takeIf { it != "plain" }.orEmpty()
                    val fence = if (b.text.contains("```")) "````" else "```"
                    out.append(pad).append(fence).append(lang).append("\n")
                    b.text.lines().forEach { out.append(pad).append(it).append("\n") }
                    out.append(pad).append(fence).append("\n")
                }
                BlockType.IMAGE -> {
                    val path = b.props.mediaPath
                    if (path != null) {
                        media.add(path)
                        out.append(pad).append("![").append(escapeInline(plain(b.props.caption))).append("](media/").append(path).append(")\n")
                    }
                }
                BlockType.FILE -> {
                    val path = b.props.mediaPath
                    if (path != null) {
                        media.add(path)
                        out.append(pad).append("[").append(escapeInline(b.props.fileName ?: path)).append("](media/").append(path).append(")\n")
                    }
                }
                BlockType.LINK_PREVIEW -> {
                    val url = b.props.url.orEmpty()
                    val title = b.props.title?.takeIf { it.isNotBlank() } ?: url
                    out.append(pad).append("[").append(escapeInline(title)).append("](").append(url).append(")")
                    b.props.description?.takeIf { it.isNotBlank() }?.let { out.append(" — ").append(escapeInline(it)) }
                    out.append("\n")
                }
                BlockType.TABLE -> {
                    val t = (b.props.table ?: app.monoworkspace.model.TableData()).normalized()
                    out.append(pad).append("| ").append(t.columns.joinToString(" | ") { cell(it.name) }).append(" |\n")
                    out.append(pad).append("|").append(t.columns.joinToString("|") { " --- " }).append("|\n")
                    t.rows.forEach { r -> out.append(pad).append("| ").append(r.joinToString(" | ") { cell(it) }).append(" |\n") }
                }
                BlockType.COLUMNS -> {
                    // Columns flatten into sequential sections.
                    renderChildren(b.id, map, indent, out, media, all)
                    continue
                }
                BlockType.COLUMN -> {
                    renderChildren(b.id, map, indent, out, media, all)
                    continue
                }
                BlockType.CHILD_PAGE -> {
                    val id = b.props.pageId
                    out.append(pad).append("[").append(escapeInline(id?.let(pageTitle) ?: "Untitled")).append("](")
                        .append(id ?: "").append(".md)\n")
                }
                BlockType.CHILD_DATABASE -> {
                    val id = b.props.databaseId
                    out.append(pad).append("[").append(escapeInline(id?.let(pageTitle) ?: "Database")).append("](")
                        .append(id ?: "").append(".csv)\n")
                }
                BlockType.TOC -> {
                    BlockTree.headings(all).forEach { h ->
                        val level = when (h.type) { BlockType.H1 -> 0; BlockType.H2 -> 1; else -> 2 }
                        out.append(pad).append("  ".repeat(level)).append("- ").append(inline(h.content)).append("\n")
                    }
                }
            }
            if (b.type != BlockType.COLUMNS && b.type != BlockType.COLUMN) {
                renderChildren(b.id, map, indent + 1, out, media, all)
            }
            if (!tight) out.append("\n")
        }
    }

    private fun cell(s: String) = s.replace("|", "\\|").replace("\n", " ")

    private fun plain(spans: List<Span>) = spans.joinToString("") { it.text }

    fun inline(spans: List<Span>): String = buildString {
        for (s in spans) {
            if (s.text.isEmpty()) continue
            when (s.kind) {
                SpanKind.DATE_MENTION -> {
                    val start = s.data?.start
                    if (start != null) {
                        val f = if (s.data?.includeTime == true) dateTimeFmt else dateFmt
                        append("@").append(Instant.ofEpochMilli(start).atZone(zone).format(f))
                    } else append(escapeInline(s.text))
                    continue
                }
                SpanKind.PAGE_MENTION -> {
                    val id = s.data?.pageId
                    append("[").append(escapeInline(id?.let(pageTitle) ?: s.text)).append("](").append(id ?: "").append(".md)")
                    continue
                }
                SpanKind.TEXT -> Unit
            }
            if (Mark.CODE in s.marks) {
                val tick = if (s.text.contains('`')) "``" else "`"
                var t = "$tick${s.text}$tick"
                if (Mark.LINK in s.marks && s.data?.url != null) t = "[$t](${s.data.url})"
                append(t)
                continue
            }
            // Keep surrounding whitespace outside the markers so they stay valid.
            val lead = s.text.takeWhile { it.isWhitespace() }
            val trail = s.text.takeLastWhile { it.isWhitespace() }
            val core = s.text.trim()
            if (core.isEmpty()) {
                append(s.text)
                continue
            }
            var t = escapeInline(core)
            if (Mark.BOLD in s.marks) t = "**$t**"
            if (Mark.ITALIC in s.marks) t = "*$t*"
            if (Mark.STRIKE in s.marks) t = "~~$t~~"
            if (Mark.UNDERLINE in s.marks) t = "<u>$t</u>"
            if (Mark.LINK in s.marks && s.data?.url != null) t = "[$t](${s.data.url})"
            append(lead).append(t).append(trail)
        }
    }

    private fun escapeInline(s: String): String = s.replace(Regex("([\\\\`*_\\[\\]~<>])"), "\\\\$1")

    private fun escapeLine(s: String): String = escapeInline(s)

    companion object {
        /** Markdown index for a database export: a table linking to the CSV. */
        fun databaseIndex(title: String, csvName: String, headers: List<String>, rows: List<List<String>>): String = buildString {
            append("# ").append(title.ifBlank { "Untitled" }).append("\n\n")
            append("Data: [").append(csvName).append("](").append(csvName).append(")\n\n")
            if (headers.isNotEmpty()) {
                append("| ").append(headers.joinToString(" | ") { it.replace("|", "\\|") }).append(" |\n")
                append("|").append(headers.joinToString("|") { " --- " }).append("|\n")
                rows.forEach { r -> append("| ").append(r.joinToString(" | ") { it.replace("|", "\\|").replace("\n", " ") }).append(" |\n") }
            }
        }
    }
}
