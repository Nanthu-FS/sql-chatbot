package com.smartnotes.core

/** Platform-free view of a note, used by all core algorithms. */
data class NoteDoc(
    val id: Long,
    val title: String,
    val body: String,
    val updatedAt: Long = 0L,
) {
    val fullText: String get() = "$title\n$body"
}
