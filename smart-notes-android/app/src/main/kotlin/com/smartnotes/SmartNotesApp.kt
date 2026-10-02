package com.smartnotes

import android.app.Application
import com.smartnotes.data.AppDatabase
import com.smartnotes.data.NotesRepository
import com.smartnotes.data.Prefs
import com.smartnotes.features.Notifications

class SmartNotesApp : Application() {

    lateinit var repo: NotesRepository
        private set
    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        repo = NotesRepository(AppDatabase.build(this))
        prefs = Prefs(this)
        Notifications.createChannels(this)
    }
}

val android.content.Context.app: SmartNotesApp get() = applicationContext as SmartNotesApp
