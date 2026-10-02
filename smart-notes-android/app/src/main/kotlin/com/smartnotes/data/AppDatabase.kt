package com.smartnotes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [NoteEntity::class, NoteVersionEntity::class, ClipEntity::class, PlaceReminderEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notes(): NoteDao
    abstract fun versions(): VersionDao
    abstract fun clips(): ClipDao
    abstract fun places(): PlaceDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "smart-notes.db").build()
    }
}
