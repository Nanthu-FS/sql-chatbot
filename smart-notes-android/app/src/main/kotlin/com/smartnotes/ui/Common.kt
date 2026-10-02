package com.smartnotes.ui

import android.text.format.DateUtils
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.smartnotes.core.AutoLinker
import com.smartnotes.data.NoteEntity
import com.smartnotes.data.NoteSource

fun relativeTime(ms: Long): String =
    DateUtils.getRelativeTimeSpanString(ms, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()

fun sourceGlyph(source: String) = when (source) {
    NoteSource.VOICE -> "♪ voice"
    NoteSource.PHOTO -> "▣ photo"
    NoteSource.MEETING -> "◎ meeting"
    NoteSource.CLIP -> "✂ clip"
    else -> ""
}

fun NoteEntity.displayTitle() = title.ifBlank { "Untitled" }

fun NoteEntity.preview(): String =
    body.lineSequence().map { it.trim().removePrefix("#").removePrefix("#").trim() }
        .firstOrNull { it.isNotEmpty() && !it.startsWith("```") && !it.startsWith("{{") }.orEmpty()

/** "↔2 · ♪ voice" style summary. */
fun NoteEntity.meta(): String = listOfNotNull(
    AutoLinker.linkedTitles(body).size.takeIf { it > 0 }?.let { "↔$it" },
    sourceGlyph(source).ifEmpty { null },
).joinToString(" · ")

private val LINKS = Regex("\\[\\[([^\\]]+)]]|\\[([^\\]\\[]+)]")

/** Makes [[Wiki links]] and [Citations] tappable; [onOpen] receives the note title. */
fun linkify(text: String, accent: Color, onOpen: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in LINKS.findAll(text)) {
        append(text.substring(last, m.range.first))
        val title = (m.groups[1] ?: m.groups[2])!!.value.trim()
        val style = TextLinkStyles(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline))
        withLink(LinkAnnotation.Clickable(tag = title, styles = style) { onOpen(title) }) { append(title) }
        last = m.range.last + 1
    }
    append(text.substring(last))
}
