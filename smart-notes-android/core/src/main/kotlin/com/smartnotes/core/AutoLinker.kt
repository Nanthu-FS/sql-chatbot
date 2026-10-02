package com.smartnotes.core

/** Suggests related notes while the user types, like Obsidian backlinks but automatic. */
object AutoLinker {

    private val WIKI_LINK = Regex("\\[\\[([^\\]]+)]]")

    fun suggest(
        current: NoteDoc,
        allNotes: List<NoteDoc>,
        limit: Int = 3,
        minScore: Double = 0.12,
    ): List<ScoredNote> {
        if (current.body.length < 20) return emptyList()
        val alreadyLinked = linkedTitles(current.body)
        return TextIndex(allNotes)
            .search(current.fullText, limit = limit + alreadyLinked.size, excludeId = current.id, minScore = minScore)
            .filterNot { it.note.title.lowercase() in alreadyLinked }
            .take(limit)
    }

    fun linkedTitles(body: String): Set<String> =
        WIKI_LINK.findAll(body).map { it.groupValues[1].trim().lowercase() }.toSet()

    fun insertLink(body: String, title: String): String =
        if (body.endsWith("\n") || body.isEmpty()) "$body[[$title]]" else "$body [[$title]]"
}
