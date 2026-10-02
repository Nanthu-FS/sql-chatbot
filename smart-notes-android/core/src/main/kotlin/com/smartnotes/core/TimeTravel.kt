package com.smartnotes.core

data class Version(val text: String, val savedAt: Long)

enum class LineChange { SAME, ADDED, REMOVED }

data class DiffLine(val text: String, val change: LineChange)

/** Scrub through a note's edit history like a video. */
object TimeTravel {

    /** Maps a slider position in 0..1 to a version index. */
    fun indexAt(position: Float, versionCount: Int): Int {
        if (versionCount <= 0) return -1
        return (position.coerceIn(0f, 1f) * (versionCount - 1) + 0.5f).toInt()
    }

    /** Line diff (LCS) between two versions, used to highlight what changed at each frame. */
    fun diff(old: String, new: String): List<DiffLine> {
        val a = old.lines(); val b = new.lines()
        val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
        val out = ArrayList<DiffLine>()
        var i = 0; var j = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> { out += DiffLine(a[i], LineChange.SAME); i++; j++ }
                lcs[i + 1][j] >= lcs[i][j + 1] -> out += DiffLine(a[i++], LineChange.REMOVED)
                else -> out += DiffLine(b[j++], LineChange.ADDED)
            }
        }
        while (i < a.size) out += DiffLine(a[i++], LineChange.REMOVED)
        while (j < b.size) out += DiffLine(b[j++], LineChange.ADDED)
        return out
    }

    /** Whether a new snapshot is worth storing (avoids one version per keystroke). */
    fun shouldSnapshot(last: Version?, text: String, now: Long, minIntervalMs: Long = 30_000, minCharDelta: Int = 20): Boolean {
        if (last == null) return text.isNotBlank()
        if (last.text == text) return false
        return now - last.savedAt >= minIntervalMs || kotlin.math.abs(text.length - last.text.length) >= minCharDelta
    }
}
