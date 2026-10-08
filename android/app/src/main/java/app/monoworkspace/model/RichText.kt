package app.monoworkspace.model

import kotlinx.serialization.Serializable

@Serializable
enum class Mark { BOLD, ITALIC, UNDERLINE, STRIKE, CODE, LINK }

@Serializable
enum class SpanKind { TEXT, DATE_MENTION, PAGE_MENTION }

@Serializable
data class SpanData(
    val url: String? = null,
    /** Date mention start, epoch millis. A value of 0 in a template means "now". */
    val start: Long? = null,
    val end: Long? = null,
    val includeTime: Boolean = false,
    val pageId: String? = null,
)

/** One run of inline text sharing the same marks and data. */
@Serializable
data class Span(
    val text: String,
    val marks: Set<Mark> = emptySet(),
    val kind: SpanKind = SpanKind.TEXT,
    val data: SpanData? = null,
)

/** Per-character attributes; spans are run-length encodings of these. */
data class CharAttr(
    val marks: Set<Mark> = emptySet(),
    val kind: SpanKind = SpanKind.TEXT,
    val data: SpanData? = null,
) {
    companion object {
        val Plain = CharAttr()
    }
}

/**
 * Pure operations on inline rich text. All offsets are UTF-16 indices into the
 * plain text, matching what text fields report.
 */
object RichText {

    fun plain(spans: List<Span>): String = buildString { spans.forEach { append(it.text) } }

    fun of(text: String): List<Span> = if (text.isEmpty()) emptyList() else listOf(Span(text))

    fun length(spans: List<Span>): Int = spans.sumOf { it.text.length }

    fun explode(spans: List<Span>): MutableList<CharAttr> {
        val out = ArrayList<CharAttr>(length(spans))
        for (s in spans) {
            val attr = CharAttr(s.marks, s.kind, s.data)
            repeat(s.text.length) { out.add(attr) }
        }
        return out
    }

    fun implode(text: String, attrs: List<CharAttr>): List<Span> {
        require(text.length == attrs.size) { "text and attrs differ in length" }
        if (text.isEmpty()) return emptyList()
        val out = ArrayList<Span>()
        var runStart = 0
        for (i in 1..text.length) {
            if (i == text.length || attrs[i] != attrs[runStart]) {
                val a = attrs[runStart]
                out.add(Span(text.substring(runStart, i), a.marks, a.kind, a.data))
                runStart = i
            }
        }
        return out
    }

    fun normalize(spans: List<Span>): List<Span> = implode(plain(spans), explode(spans))

    /**
     * Applies a text edit described by the old and new plain strings. The changed
     * region is found by common prefix/suffix. Inserted characters inherit the
     * attributes of the character before the insertion point (never a mention's).
     * A mention touched by the edit is downgraded to plain text.
     */
    fun applyEdit(spans: List<Span>, newText: String): List<Span> {
        val oldText = plain(spans)
        if (oldText == newText) return spans
        val attrs = explode(spans)
        var prefix = 0
        val maxPrefix = minOf(oldText.length, newText.length)
        while (prefix < maxPrefix && oldText[prefix] == newText[prefix]) prefix++
        var suffix = 0
        while (
            suffix < oldText.length - prefix &&
            suffix < newText.length - prefix &&
            oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
        ) suffix++
        val removeEnd = oldText.length - suffix
        val inserted = newText.substring(prefix, newText.length - suffix)

        // Downgrade mentions the edit touches.
        val touchStart = prefix
        val touchEnd = removeEnd
        downgradeMentions(attrs, touchStart, touchEnd, insertion = removeEnd == prefix)

        val inherit = when {
            prefix > 0 -> attrs[prefix - 1]
            removeEnd < attrs.size -> attrs[removeEnd]
            else -> CharAttr.Plain
        }.let { if (it.kind != SpanKind.TEXT) CharAttr.Plain else it }
        // Links do not extend past their end when typing after them.
        val insertAttr = if (prefix > 0 && Mark.LINK in inherit.marks && prefix == linkRunEnd(attrs, prefix - 1)) {
            CharAttr(inherit.marks - Mark.LINK, SpanKind.TEXT, null)
        } else inherit

        val newAttrs = ArrayList<CharAttr>(newText.length)
        newAttrs.addAll(attrs.subList(0, prefix))
        repeat(inserted.length) { newAttrs.add(insertAttr) }
        newAttrs.addAll(attrs.subList(removeEnd, attrs.size))
        return implode(newText, newAttrs)
    }

    private fun linkRunEnd(attrs: List<CharAttr>, index: Int): Int {
        var i = index
        val a = attrs[index]
        while (i < attrs.size && attrs[i] == a) i++
        return i
    }

    private fun downgradeMentions(attrs: MutableList<CharAttr>, start: Int, end: Int, insertion: Boolean) {
        if (attrs.isEmpty()) return
        val probe = buildList {
            for (i in start until end) add(i)
            if (insertion) {
                // An insertion strictly inside a mention breaks it.
                if (start > 0 && start < attrs.size && attrs[start - 1] == attrs[start] && attrs[start].kind != SpanKind.TEXT) add(start)
            }
        }
        for (i in probe) {
            if (i !in attrs.indices) continue
            val a = attrs[i]
            if (a.kind == SpanKind.TEXT) continue
            var s = i
            while (s > 0 && attrs[s - 1] == a) s--
            var e = i
            while (e < attrs.size && attrs[e] == a) e++
            for (k in s until e) attrs[k] = CharAttr(a.marks, SpanKind.TEXT, null)
        }
    }

    /** True if every character in [start, end) carries [mark]. */
    fun hasMark(spans: List<Span>, start: Int, end: Int, mark: Mark): Boolean {
        if (start >= end) return false
        val attrs = explode(spans)
        return (start until end.coerceAtMost(attrs.size)).all { mark in attrs[it].marks }
    }

    fun marksAt(spans: List<Span>, offset: Int): Set<Mark> {
        val attrs = explode(spans)
        if (attrs.isEmpty()) return emptySet()
        val i = (offset - 1).coerceIn(0, attrs.size - 1)
        return attrs[i].marks
    }

    fun toggleMark(spans: List<Span>, start: Int, end: Int, mark: Mark, url: String? = null): List<Span> {
        if (start >= end) return spans
        val text = plain(spans)
        val attrs = explode(spans)
        val s = start.coerceIn(0, text.length)
        val e = end.coerceIn(0, text.length)
        val remove = (s until e).all { mark in attrs[it].marks } && !(mark == Mark.LINK && url != null)
        for (i in s until e) {
            val a = attrs[i]
            attrs[i] = if (remove) {
                a.copy(marks = a.marks - mark, data = if (mark == Mark.LINK) a.data?.copy(url = null)?.takeIf { it != SpanData() } else a.data)
            } else {
                val data = if (mark == Mark.LINK) (a.data ?: SpanData()).copy(url = url) else a.data
                a.copy(marks = a.marks + mark, data = data)
            }
        }
        return implode(text, attrs)
    }

    fun setMark(spans: List<Span>, start: Int, end: Int, mark: Mark, url: String? = null): List<Span> {
        val text = plain(spans)
        val attrs = explode(spans)
        for (i in start.coerceAtLeast(0) until end.coerceAtMost(text.length)) {
            val a = attrs[i]
            val data = if (mark == Mark.LINK) (a.data ?: SpanData()).copy(url = url) else a.data
            attrs[i] = a.copy(marks = a.marks + mark, data = data)
        }
        return implode(text, attrs)
    }

    fun slice(spans: List<Span>, start: Int, end: Int): List<Span> {
        val text = plain(spans)
        val s = start.coerceIn(0, text.length)
        val e = end.coerceIn(s, text.length)
        return implode(text.substring(s, e), explode(spans).subList(s, e))
    }

    fun split(spans: List<Span>, offset: Int): Pair<List<Span>, List<Span>> {
        val len = length(spans)
        return slice(spans, 0, offset) to slice(spans, offset, len)
    }

    fun concat(a: List<Span>, b: List<Span>): List<Span> = normalize(a + b)

    fun insert(spans: List<Span>, offset: Int, inserted: List<Span>): List<Span> {
        val (left, right) = split(spans, offset)
        return normalize(left + inserted + right)
    }

    /** Replaces [start, end) with [replacement] spans. */
    fun replace(spans: List<Span>, start: Int, end: Int, replacement: List<Span>): List<Span> {
        val len = length(spans)
        return normalize(slice(spans, 0, start) + replacement + slice(spans, end, len))
    }

    /** Collects the ids of pages mentioned in the text. */
    fun mentionedPages(spans: List<Span>): List<String> =
        spans.filter { it.kind == SpanKind.PAGE_MENTION }.mapNotNull { it.data?.pageId }
}
