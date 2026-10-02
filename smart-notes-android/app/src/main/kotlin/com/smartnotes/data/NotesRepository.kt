package com.smartnotes.data

import com.smartnotes.core.ClipboardInbox
import com.smartnotes.core.TimeTravel
import com.smartnotes.core.Version
import kotlinx.coroutines.flow.Flow

class NotesRepository(private val db: AppDatabase) {

    val notes: Flow<List<NoteEntity>> = db.notes().observeAll()
    val clips: Flow<List<ClipEntity>> = db.clips().observeAll()

    fun note(id: Long): Flow<NoteEntity?> = db.notes().observe(id)
    fun versions(noteId: Long): Flow<List<NoteVersionEntity>> = db.versions().observe(noteId)
    fun reminders(noteId: Long): Flow<List<PlaceReminderEntity>> = db.places().observeFor(noteId)

    suspend fun all(): List<NoteEntity> = db.notes().all()
    suspend fun get(id: Long): NoteEntity? = db.notes().get(id)
    suspend fun byTitle(title: String): NoteEntity? = db.notes().byTitle(title)
    suspend fun widgetNote(): NoteEntity? = db.notes().widgetNote()
    suspend fun pinToWidget(id: Long) = db.notes().pinToWidget(id)

    suspend fun create(title: String, body: String, source: String = NoteSource.TEXT): Long {
        val now = System.currentTimeMillis()
        val id = db.notes().insert(NoteEntity(title = title, body = body, source = source, createdAt = now, updatedAt = now))
        if (body.isNotBlank()) db.versions().insert(NoteVersionEntity(noteId = id, title = title, body = body, savedAt = now))
        return id
    }

    /** Saves edits and stores a time-travel snapshot when enough has changed. */
    suspend fun save(note: NoteEntity) {
        val now = System.currentTimeMillis()
        db.notes().update(note.copy(updatedAt = now))
        val last = db.versions().latest(note.id)
        val text = "${note.title}\n${note.body}"
        val lastVersion = last?.let { Version("${it.title}\n${it.body}", it.savedAt) }
        if (TimeTravel.shouldSnapshot(lastVersion, text, now)) {
            db.versions().insert(NoteVersionEntity(noteId = note.id, title = note.title, body = note.body, savedAt = now))
        }
    }

    /** Layout-only change: no new version, no reordering by updatedAt. */
    suspend fun saveLayout(note: NoteEntity) = db.notes().update(note)

    suspend fun delete(note: NoteEntity) {
        db.versions().deleteFor(note.id)
        db.notes().delete(note)
    }

    // Clipboard inbox

    suspend fun captureClip(text: String): Boolean {
        val latest = db.clips().latest()?.let { com.smartnotes.core.ClipEntry(it.id, it.text, it.copiedAt) }
        if (!ClipboardInbox.shouldCapture(text, latest)) return false
        db.clips().insert(ClipEntity(text = text.trim(), copiedAt = System.currentTimeMillis()))
        return true
    }

    suspend fun saveClip(clip: ClipEntity): Long {
        val title = ClipboardInbox.titleFor(com.smartnotes.core.ClipEntry(clip.id, clip.text, clip.copiedAt))
        val id = create(title, clip.text, NoteSource.CLIP)
        db.clips().delete(clip.id)
        return id
    }

    suspend fun discardClip(id: Long) = db.clips().delete(id)

    suspend fun pruneClips() =
        db.clips().deleteOlderThan(System.currentTimeMillis() - ClipboardInbox.DEFAULT_TTL_MS)

    // Place reminders

    suspend fun allReminders() = db.places().all()
    suspend fun reminder(id: Long) = db.places().get(id)
    suspend fun addReminder(r: PlaceReminderEntity): Long = db.places().insert(r)
    suspend fun deleteReminder(id: Long) = db.places().delete(id)
}
