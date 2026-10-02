package com.smartnotes.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    suspend fun all(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observe(id: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE LOWER(title) = LOWER(:title) LIMIT 1")
    suspend fun byTitle(title: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE pinnedToWidget = 1 LIMIT 1")
    suspend fun widgetNote(): NoteEntity?

    @Query("UPDATE notes SET pinnedToWidget = (id = :id)")
    suspend fun pinToWidget(id: Long)

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)
}

@Dao
interface VersionDao {
    @Query("SELECT * FROM note_versions WHERE noteId = :noteId ORDER BY savedAt ASC")
    fun observe(noteId: Long): Flow<List<NoteVersionEntity>>

    @Query("SELECT * FROM note_versions WHERE noteId = :noteId ORDER BY savedAt DESC LIMIT 1")
    suspend fun latest(noteId: Long): NoteVersionEntity?

    @Insert
    suspend fun insert(version: NoteVersionEntity)

    @Query("DELETE FROM note_versions WHERE noteId = :noteId")
    suspend fun deleteFor(noteId: Long)
}

@Dao
interface ClipDao {
    @Query("SELECT * FROM clips ORDER BY copiedAt DESC")
    fun observeAll(): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips ORDER BY copiedAt DESC LIMIT 1")
    suspend fun latest(): ClipEntity?

    @Insert
    suspend fun insert(clip: ClipEntity): Long

    @Query("DELETE FROM clips WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM clips WHERE copiedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface PlaceDao {
    @Query("SELECT * FROM place_reminders")
    suspend fun all(): List<PlaceReminderEntity>

    @Query("SELECT * FROM place_reminders WHERE noteId = :noteId")
    fun observeFor(noteId: Long): Flow<List<PlaceReminderEntity>>

    @Query("SELECT * FROM place_reminders WHERE id = :id")
    suspend fun get(id: Long): PlaceReminderEntity?

    @Insert
    suspend fun insert(reminder: PlaceReminderEntity): Long

    @Query("DELETE FROM place_reminders WHERE id = :id")
    suspend fun delete(id: Long)
}
