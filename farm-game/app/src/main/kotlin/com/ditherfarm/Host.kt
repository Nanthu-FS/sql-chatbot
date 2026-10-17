package com.ditherfarm

import com.ditherfarm.engine.AssetSource
import java.io.File

/**
 * What the game needs from the outside world. MainActivity implements it on a
 * phone; JVM screenshot tests implement it with files and no-ops.
 */
interface Host {
    val assetSource: AssetSource
    val saveDir: File
    /** android.content.Context for haptics, or null in tests. */
    val context: android.content.Context?
    val density: Float
    fun scheduleReadyNotification(atEpochMs: Long, title: String, text: String) {}
    fun cancelReadyNotifications() {}
    fun requestExport() {}
    fun requestImport() {}
    /** Writes/reads a user-picked document (save export/import). */
    fun writeDocument(uri: android.net.Uri, text: String) {}
    fun readDocument(uri: android.net.Uri): String? = null
    fun nowMs(): Long = System.currentTimeMillis()
}
