package app.monoworkspace

import app.monoworkspace.core.FractionalIndex
import app.monoworkspace.engine.BlockShortcuts
import app.monoworkspace.engine.BlockTree
import app.monoworkspace.engine.Csv
import app.monoworkspace.engine.InlineShortcuts
import app.monoworkspace.engine.PropertyConversion
import app.monoworkspace.model.Block
import app.monoworkspace.model.BlockProps
import app.monoworkspace.model.BlockType
import app.monoworkspace.model.Mark
import app.monoworkspace.model.PropertyDef
import app.monoworkspace.model.PropertyType
import app.monoworkspace.model.PropertyValue
import app.monoworkspace.model.RichText
import app.monoworkspace.model.Span
import app.monoworkspace.model.SpanData
import app.monoworkspace.model.SpanKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTextTest {

    @Test
    fun typingInheritsMarks() {
        val spans = listOf(Span("ab", setOf(Mark.BOLD)), Span("cd"))
        val out = RichText.applyEdit(spans, "abXcd")
        assertEquals(listOf(Span("abX", setOf(Mark.BOLD)), Span("cd")), out)
    }

    @Test
    fun deletingAcrossSpans() {
        val spans = listOf(Span("hello", setOf(Mark.ITALIC)), Span(" world"))
        assertEquals(listOf(Span("hel", setOf(Mark.ITALIC)), Span("rld")), RichText.applyEdit(spans, "helrld"))
    }

    @Test
    fun editingInsideMentionDowngradesIt() {
        val spans = listOf(Span("@Oct 8", kind = SpanKind.DATE_MENTION, data = SpanData(start = 1L)), Span(" x"))
        val out = RichText.applyEdit(spans, "@Oct 9 x")
        assertTrue(out.all { it.kind == SpanKind.TEXT })
        assertEquals("@Oct 9 x", RichText.plain(out))
    }

    @Test
    fun toggleMarkOnRange() {
        val spans = RichText.of("hello world")
        val bold = RichText.toggleMark(spans, 0, 5, Mark.BOLD)
        assertEquals(listOf(Span("hello", setOf(Mark.BOLD)), Span(" world")), bold)
        assertEquals(spans, RichText.toggleMark(bold, 0, 5, Mark.BOLD))
    }

    @Test
    fun splitAndConcat() {
        val spans = listOf(Span("ab", setOf(Mark.BOLD)), Span("cd"))
        val (l, r) = RichText.split(spans, 1)
        assertEquals(listOf(Span("a", setOf(Mark.BOLD))), l)
        assertEquals(listOf(Span("b", setOf(Mark.BOLD)), Span("cd")), r)
        assertEquals(spans, RichText.concat(l, r))
    }

    @Test
    fun inlineShortcuts() {
        val bold = InlineShortcuts.apply(RichText.of("make **this**"), 13)
        assertNotNull(bold)
        assertEquals(listOf(Span("make "), Span("this", setOf(Mark.BOLD))), bold!!.spans)
        assertEquals(9, bold.caret)
        val code = InlineShortcuts.apply(RichText.of("run `x`"), 7)!!
        assertEquals(listOf(Span("run "), Span("x", setOf(Mark.CODE))), code.spans)
        val link = InlineShortcuts.apply(RichText.of("see [docs](https://a.b)"), 23)!!
        assertEquals("see docs", RichText.plain(link.spans))
        assertEquals("https://a.b", link.spans.last().data?.url)
        assertNull(InlineShortcuts.apply(RichText.of("2 * 3"), 5))
    }

    @Test
    fun blockShortcuts() {
        assertEquals(BlockType.H2, BlockShortcuts.match("## ", 3)?.type)
        assertEquals(BlockType.TODO, BlockShortcuts.match("[] ", 3)?.type)
        assertEquals(BlockType.DIVIDER, BlockShortcuts.match("---", 3)?.type)
        assertEquals(BlockType.CODE, BlockShortcuts.match("```", 3)?.type)
        assertNull(BlockShortcuts.match("## ", 1))
    }

    @Test
    fun csvRoundTrip() {
        val rows = listOf(listOf("Name", "Note"), listOf("A, B", "say \"hi\"\nnow"), listOf("", "x"))
        assertEquals(rows, Csv.parse(Csv.write(rows)))
    }

    @Test
    fun textToSelectCreatesOptions() {
        val prop = PropertyDef("p", "Tag", PropertyType.TEXT)
        val values = mapOf("r1" to PropertyValue.Text("red"), "r2" to PropertyValue.Text("blue"), "r3" to PropertyValue.Text("red"))
        val r = PropertyConversion.convert(prop, PropertyType.SELECT, values)
        assertEquals(listOf("red", "blue"), r.property.options.map { it.name })
        assertEquals(r.values["r1"], r.values["r3"])
        assertEquals(0, r.cleared)
        val num = PropertyConversion.convert(prop, PropertyType.NUMBER, values)
        assertEquals(3, num.cleared)
    }

    @Test
    fun blockReorderKeepsSubtree() {
        val keys = FractionalIndex.nBetween(null, null, 5)
        fun b(id: String, key: String, parent: String? = null) =
            Block(id, "p", parent, key, BlockType.TEXT, RichText.of(id), BlockProps(), 0, 0)
        val blocks = listOf(b("a", keys[0]), b("b", keys[1]), b("b1", keys[0], "b"), b("c", keys[2]))
        val move = BlockTree.moveBefore(blocks, "c", blocks.first { it.id == "a" })!!
        val moved = blocks.map { if (it.id == "c") it.copy(parentBlockId = move.first, orderKey = move.second) else it }
        assertEquals(listOf("c", "a", "b", "b1"), BlockTree.flatten(moved).map { it.block.id })
        // Cannot drop a block inside its own subtree.
        assertNull(BlockTree.moveBefore(blocks, "b", blocks.first { it.id == "b1" }))
    }
}
