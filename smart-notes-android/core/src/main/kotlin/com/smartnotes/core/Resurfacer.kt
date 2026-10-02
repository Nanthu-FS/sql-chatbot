package com.smartnotes.core

import java.util.concurrent.TimeUnit

/** What the user is doing right now; any field may be empty. */
data class ResurfaceContext(
    val now: Long,
    val currentText: String = "",
    val upcomingEvents: List<String> = emptyList(),
    val placeName: String = "",
)

data class Resurfaced(val note: NoteDoc, val score: Double, val reason: String)

/**
 * Brings back old notes that matter again. A note qualifies if it hasn't been touched for a while
 * and is similar to what you're writing, an upcoming calendar event, or where you are.
 */
object Resurfacer {

    private val MIN_AGE_MS = TimeUnit.DAYS.toMillis(3)

    fun suggest(notes: List<NoteDoc>, ctx: ResurfaceContext, limit: Int = 3, minScore: Double = 0.15): List<Resurfaced> {
        val old = notes.filter { ctx.now - it.updatedAt >= MIN_AGE_MS }
        if (old.isEmpty()) return emptyList()
        val index = TextIndex(old)

        val signals = buildList {
            if (ctx.currentText.isNotBlank()) add(ctx.currentText to "Related to what you're writing")
            ctx.upcomingEvents.filter { it.isNotBlank() }.forEach { add(it to "For your event \"$it\"") }
            if (ctx.placeName.isNotBlank()) add(ctx.placeName to "You're near ${ctx.placeName}")
        }

        val best = HashMap<Long, Resurfaced>()
        for ((text, reason) in signals) {
            for (hit in index.search(text, limit = limit, minScore = minScore)) {
                val ageDays = TimeUnit.MILLISECONDS.toDays(ctx.now - hit.note.updatedAt)
                // Slight boost for older notes: those are the ones you've forgotten.
                val score = hit.score * (1.0 + minOf(ageDays, 90L) / 300.0)
                if (score > (best[hit.note.id]?.score ?: 0.0)) best[hit.note.id] = Resurfaced(hit.note, score, reason)
            }
        }
        return best.values.sortedByDescending { it.score }.take(limit)
    }
}
