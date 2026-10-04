package com.smartnotes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartnotes.SmartNotesApp
import com.smartnotes.core.AutoLinker
import com.smartnotes.core.ResurfaceContext
import com.smartnotes.core.Resurfaced
import com.smartnotes.core.Resurfacer
import com.smartnotes.core.ScoredNote
import com.smartnotes.core.TextIndex
import com.smartnotes.data.NoteEntity
import com.smartnotes.data.NoteSource
import com.smartnotes.features.CalendarEvent
import com.smartnotes.features.CalendarSource
import com.smartnotes.features.clips.ClipboardCapture
import com.smartnotes.features.widget.ChecklistWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SmartNotesApp
    val repo = app.repo
    val prefs = app.prefs

    val skin = prefs.skin
    val notes: StateFlow<List<NoteEntity>> = repo.notes.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val clips = repo.clips.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    private val _currentMeeting = MutableStateFlow<CalendarEvent?>(null)
    val currentMeeting: StateFlow<CalendarEvent?> = _currentMeeting

    /** The single best old note to bring back right now. */
    val resurfaced: StateFlow<Resurfaced?> = combine(notes, events) { list, ev ->
        Resurfacer.suggest(
            list.map { it.toDoc() },
            ResurfaceContext(now = System.currentTimeMillis(), upcomingEvents = ev.map { it.title }),
            limit = 1,
        ).firstOrNull()
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun clearMessage() { _message.value = null }
    fun notify(msg: String) { _message.value = msg }

    fun refreshContext() = viewModelScope.launch(Dispatchers.IO) {
        events.value = CalendarSource.upcoming(app)
        _currentMeeting.value = CalendarSource.current(app)
        repo.pruneClips()
    }

    fun captureClipboard() = viewModelScope.launch { ClipboardCapture.captureNow(app) }

    // ---------- Notes ----------

    fun createNote(title: String = "", body: String = "", source: String = NoteSource.TEXT, onCreated: (Long) -> Unit) =
        viewModelScope.launch { onCreated(repo.create(title, body, source)) }

    fun save(note: NoteEntity) = viewModelScope.launch {
        repo.save(note)
        if (note.pinnedToWidget) ChecklistWidget.refresh(app)
    }

    fun saveLayout(note: NoteEntity) = viewModelScope.launch { repo.saveLayout(note) }

    fun delete(note: NoteEntity, onDone: () -> Unit) = viewModelScope.launch {
        repo.delete(note)
        onDone()
    }

    fun pinToWidget(note: NoteEntity) = viewModelScope.launch {
        repo.pinToWidget(note.id)
        ChecklistWidget.refresh(app)
        _message.value = "Pinned. Add the \"Smart Notes checklist\" widget to your home screen."
    }

    fun openByTitle(title: String, onFound: (Long) -> Unit) = viewModelScope.launch {
        repo.byTitle(title)?.let { onFound(it.id) } ?: run { _message.value = "No note called \"$title\"" }
    }

    fun linkSuggestions(note: NoteEntity, body: String): List<ScoredNote> =
        AutoLinker.suggest(note.toDoc().copy(body = body), notes.value.filter { it.title.isNotBlank() }.map { it.toDoc() })

    /**
     * Runs whenever the home screen appears (so no editor is open): deletes notes that were
     * never written in and gives untitled notes a title from their first line of text.
     */
    fun tidyNotes() = viewModelScope.launch {
        val startedAt = System.currentTimeMillis()
        delay(500) // let the closing editor's final save land first
        for (note in repo.all()) {
            // Skip titled notes, and any note created after we started (e.g. "+" tapped just now).
            if (note.title.isNotBlank() || note.createdAt >= startedAt) continue
            val text = note.body.lineSequence().map(::plainText).filter { it.isNotEmpty() }.toList()
            val hasContent = note.body.lineSequence().any { line ->
                val l = line.trim()
                l.startsWith("{{") || l.startsWith("```") || plainText(line).isNotEmpty()
            }
            when {
                !hasContent -> repo.delete(note)
                text.isNotEmpty() -> repo.saveLayout(note.copy(title = text.first().take(48)))
            }
        }
    }

    /** A line with markdown and block syntax removed: "- [ ] Call Sam" -> "Call Sam". */
    private fun plainText(line: String): String {
        val l = line.trim()
        if (l.startsWith("{{") || l.startsWith("```")) return ""
        return l.replace(Regex("^(#+|[-*]\\s*\\[[ xX]\\]|[-*])"), "").trim()
    }

    fun search(text: String): List<NoteEntity> {
        val byId = notes.value.associateBy { it.id }
        return TextIndex(notes.value.map { it.toDoc() }).search(text, limit = 8).mapNotNull { byId[it.note.id] }
    }

    // ---------- Clipboard inbox ----------

    fun saveClip(clip: com.smartnotes.data.ClipEntity, onCreated: (Long) -> Unit) =
        viewModelScope.launch { onCreated(repo.saveClip(clip)) }

    fun discardClip(id: Long) = viewModelScope.launch { repo.discardClip(id) }

    // ---------- Capture ----------

    /** Saves a dictated note. The title is the first few words; todos spoken as "todo ..." or "to-do ..." become checkboxes. */
    fun createFromVoice(transcript: String, onCreated: (Long) -> Unit) {
        val raw = transcript.trim()
        if (raw.isEmpty()) return
        viewModelScope.launch {
            val title = raw.split(Regex("\\s+")).take(6).joinToString(" ")
            val body = raw.split(Regex("(?i)\\b(?:todo|to-do)\\b[:,]?\\s*"))
                .let { parts -> (listOf(parts.first().trim()) + parts.drop(1).map { "- [ ] " + it.trim() }).filter { it.isNotBlank() && it != "- [ ]" } }
                .joinToString("\n")
            onCreated(repo.create(title, body, NoteSource.VOICE))
        }
    }

    fun saveMeeting(title: String, transcript: String, onCreated: (Long) -> Unit) {
        val raw = transcript.trim()
        viewModelScope.launch {
            val body = if (raw.isEmpty()) MEETING_TEMPLATE else "$MEETING_TEMPLATE\n\n## Transcript\n$raw"
            onCreated(repo.create(title, body, NoteSource.MEETING))
        }
    }

    companion object {
        val MEETING_TEMPLATE = """
            ## Agenda
            
            ## Notes
            
            ## Decisions
            
            ## Action items
            - [ ] 
        """.trimIndent()
    }
}
