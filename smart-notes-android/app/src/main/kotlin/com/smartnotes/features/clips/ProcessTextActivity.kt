package com.smartnotes.features.clips

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.smartnotes.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Appears as "Save to Smart Notes" when you select text in any app. Adds it to the clipboard inbox. */
class ProcessTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        if (text.isNotBlank()) {
            val repo = app.repo
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { repo.captureClip(text) }
            Toast.makeText(this, "Saved to clipboard inbox", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
