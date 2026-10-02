package com.smartnotes.core

import java.util.concurrent.TimeUnit

data class ClipEntry(val id: Long, val text: String, val copiedAt: Long, val saved: Boolean = false)

/** Short-lived inbox of copied text. Unsaved clips expire; saved ones become notes. */
object ClipboardInbox {

    val DEFAULT_TTL_MS = TimeUnit.HOURS.toMillis(24)
    const val MAX_CLIP_CHARS = 10_000

    /** Returns false for blanks, oversized text, and repeats of the latest clip. */
    fun shouldCapture(text: String, latest: ClipEntry?): Boolean {
        val t = text.trim()
        return t.isNotEmpty() && t.length <= MAX_CLIP_CHARS && t != latest?.text?.trim()
    }

    fun expired(clips: List<ClipEntry>, now: Long, ttlMs: Long = DEFAULT_TTL_MS): List<ClipEntry> =
        clips.filter { !it.saved && now - it.copiedAt > ttlMs }

    fun titleFor(clip: ClipEntry): String {
        val firstLine = clip.text.trim().lineSequence().first()
        return if (firstLine.length <= 48) firstLine else firstLine.take(45).trimEnd() + "…"
    }
}
