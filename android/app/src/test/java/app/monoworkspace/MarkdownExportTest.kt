package app.monoworkspace

import app.monoworkspace.core.FractionalIndex
import app.monoworkspace.engine.MarkdownExporter
import app.monoworkspace.model.Block
import app.monoworkspace.model.BlockProps
import app.monoworkspace.model.BlockType
import app.monoworkspace.model.Mark
import app.monoworkspace.model.RichText
import app.monoworkspace.model.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownExportTest {

    private val keys = FractionalIndex.nBetween(null, null, 20)
    private var n = 0

    private fun block(
        type: BlockType,
        text: String = "",
        props: BlockProps = BlockProps(),
        parent: String? = null,
        content: List<Span>? = null,
        id: String = "b${n}",
    ): Block {
        val b = Block(id, "p", parent, keys[n], type, content ?: RichText.of(text), props, 0, 0)
        n++
        return b
    }

    @Test
    fun exportsCommonBlocks() {
        val blocks = listOf(
            block(BlockType.H1, "Agenda"),
            block(BlockType.TEXT, content = listOf(Span("Plain "), Span("bold", setOf(Mark.BOLD)), Span(" and "), Span("code", setOf(Mark.CODE)))),
            block(BlockType.BULLET, "one"),
            block(BlockType.BULLET, "two"),
            block(BlockType.NUMBERED, "first"),
            block(BlockType.NUMBERED, "second"),
            block(BlockType.TODO, "open"),
            block(BlockType.TODO, "closed", BlockProps(checked = true)),
            block(BlockType.QUOTE, "Quoted"),
            block(BlockType.DIVIDER),
            block(BlockType.CODE, "val x = 1", BlockProps(language = "kotlin")),
            block(BlockType.IMAGE, props = BlockProps(mediaPath = "abc.jpg", caption = RichText.of("Board"))),
        )
        val r = MarkdownExporter().export("Notes", blocks)
        val md = r.markdown
        assertTrue(md, md.startsWith("# Notes\n"))
        assertTrue(md, md.contains("# Agenda\n"))
        assertTrue(md, md.contains("Plain **bold** and `code`"))
        assertTrue(md, md.contains("- one\n- two\n"))
        assertTrue(md, md.contains("1. first\n2. second\n"))
        assertTrue(md, md.contains("- [ ] open\n- [x] closed\n"))
        assertTrue(md, md.contains("> Quoted"))
        assertTrue(md, md.contains("---\n"))
        assertTrue(md, md.contains("```kotlin\nval x = 1\n```"))
        assertTrue(md, md.contains("![Board](media/abc.jpg)"))
        assertEquals(listOf("abc.jpg"), r.media)
    }

    @Test
    fun nestedListsAreIndented() {
        val parent = block(BlockType.BULLET, "parent", id = "parent")
        val child = block(BlockType.BULLET, "child", parent = "parent")
        val md = MarkdownExporter().export("T", listOf(parent, child)).markdown
        assertTrue(md, md.contains("- parent\n    - child\n"))
    }

    @Test
    fun escapesMarkdownCharacters() {
        val md = MarkdownExporter().export("T", listOf(block(BlockType.TEXT, "a*b_c"))).markdown
        assertTrue(md, md.contains("a\\*b\\_c"))
    }

    @Test
    fun linksAndMentions() {
        val spans = listOf(
            Span("site", setOf(Mark.LINK), data = app.monoworkspace.model.SpanData(url = "https://example.com")),
            Span(" "),
            Span("@Page", kind = app.monoworkspace.model.SpanKind.PAGE_MENTION, data = app.monoworkspace.model.SpanData(pageId = "p2")),
        )
        val md = MarkdownExporter(pageTitle = { if (it == "p2") "Other" else "?" }).export("T", listOf(block(BlockType.TEXT, content = spans))).markdown
        assertTrue(md, md.contains("[site](https://example.com) [Other](p2.md)"))
    }
}
