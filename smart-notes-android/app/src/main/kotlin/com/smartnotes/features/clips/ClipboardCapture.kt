package com.smartnotes.features.clips

import android.content.ClipboardManager
import android.content.Context
import com.smartnotes.app

/**
 * Android 10+ only lets the foreground app read the clipboard, so we capture when the app gains focus.
 * The text-selection "Save to Smart Notes" action (ProcessTextActivity) covers copying from other apps.
 */
object ClipboardCapture {
    suspend fun captureNow(context: Context): Boolean {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return false
        val clip = cm.primaryClip ?: return false
        if (clip.itemCount == 0) return false
        val text = clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
        return context.app.repo.captureClip(text)
    }
}
