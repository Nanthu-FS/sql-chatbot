package app.monoworkspace.engine

import app.monoworkspace.model.BlockType
import app.monoworkspace.model.Mark
import app.monoworkspace.model.RichText
import app.monoworkspace.model.Span

/** Markdown-style shortcuts typed at the start of a text block. */
object BlockShortcuts {
    data class Match(val type: BlockType, val consumed: Int, val checked: Boolean = false)

    private val prefixes: List<Pair<String, BlockType>> = listOf(
        "### " to BlockType.H3,
        "## " to BlockType.H2,
        "# " to BlockType.H1,
        "- " to BlockType.BULLET,
        "* " to BlockType.BULLET,
        "1. " to BlockType.NUMBERED,
        "[] " to BlockType.TODO,
        "[ ] " to BlockType.TODO,
        "[x] " to BlockType.TODO,
        "> " to BlockType.QUOTE,
    )

    /**
     * Checks the text of a plain paragraph after an edit. Only fires when the
     * caret sits right after the shortcut, i.e. the user just typed it.
     */
    fun match(text: String, caret: Int): Match? {
        for ((prefix, type) in prefixes) {
            if (text.startsWith(prefix) && caret == prefix.length) {
                return Match(type, prefix.length, checked = prefix == "[x] ")
            }
        }
        if (text == "---" && caret == 3) return Match(BlockType.DIVIDER, 3)
        if (text == "```" && caret == 3) return Match(BlockType.CODE, 3)
        // Equations are out of scope: "$$" becomes a code block.
        if (text == "$$" && caret == 2) return Match(BlockType.CODE, 2)
        return null
    }
}

/** Inline markdown applied when the closing marker is typed. */
object InlineShortcuts {
    data class Result(val spans: List<Span>, val caret: Int)

    private data class Rule(val regex: Regex, val mark: Mark, val markerLen: Int)

    // Order matters: ** before *, ~~ before ~.
    private val rules = listOf(
        Rule(Regex("\\*\\*([^*\\n]+?)\\*\\*$"), Mark.BOLD, 2),
        Rule(Regex("(?<![*\\w])\\*([^*\\n]+?)\\*$"), Mark.ITALIC, 1),
        Rule(Regex("~~([^~\\n]+?)~~$"), Mark.STRIKE, 2),
        Rule(Regex("`([^`\\n]+?)`$"), Mark.CODE, 1),
    )
    private val linkRule = Regex("\\[([^\\]\\n]+)]\\(([^)\\s]+)\\)$")

    /** Looks at the text before [caret]; returns transformed spans when a shortcut closed. */
    fun apply(spans: List<Span>, caret: Int): Result? {
        val text = RichText.plain(spans)
        if (caret <= 0 || caret > text.length) return null
        val before = text.substring(0, caret)

        linkRule.find(before)?.let { m ->
            val start = m.range.first
            val label = m.groupValues[1]
            val url = m.groupValues[2]
            val replaced = RichText.replace(spans, start, caret, RichText.of(label))
            val linked = RichText.setMark(replaced, start, start + label.length, Mark.LINK, url)
            return Result(linked, start + label.length)
        }

        for (rule in rules) {
            val m = rule.regex.find(before) ?: continue
            val start = m.range.first
            val inner = m.groupValues[1]
            if (inner.isBlank()) continue
            // Remove the closing marker, then the opening one.
            var out = RichText.replace(spans, caret - rule.markerLen, caret, emptyList())
            out = RichText.replace(out, start, start + rule.markerLen, emptyList())
            out = RichText.setMark(out, start, start + inner.length, rule.mark)
            return Result(out, start + inner.length)
        }
        return null
    }
}
