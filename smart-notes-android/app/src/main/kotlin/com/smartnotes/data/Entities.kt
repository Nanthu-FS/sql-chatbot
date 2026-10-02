package com.smartnotes.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.smartnotes.core.NoteDoc

object NoteSource {
    const val TEXT = "text"
    const val VOICE = "voice"
    const val PHOTO = "photo"
    const val MEETING = "meeting"
    const val CLIP = "clip"
}

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val source: String = NoteSource.TEXT,
    val createdAt: Long,
    val updatedAt: Long,
    /** Board (canvas) layout: JSON map of block index -> [x, y] in dp. */
    val canvasJson: String = "",
    val pinnedToWidget: Boolean = false,
) {
    fun toDoc() = NoteDoc(id, title, body, updatedAt)
}

@Entity(tableName = "note_versions", indices = [Index("noteId")])
data class NoteVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val title: String,
    val body: String,
    val savedAt: Long,
)

@Entity(tableName = "clips")
data class ClipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val copiedAt: Long,
)

@Entity(tableName = "place_reminders", indices = [Index("noteId")])
data class PlaceReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val placeName: String,
    val lat: Double,
    val lng: Double,
    val radiusMeters: Float = 150f,
)
