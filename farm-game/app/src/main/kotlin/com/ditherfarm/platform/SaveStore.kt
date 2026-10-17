package com.ditherfarm.platform

import android.content.ContentResolver
import android.net.Uri
import java.io.File

/**
 * Crash-safe JSON save files. A write goes to a temp file first and is then renamed
 * over the real save, and the previous good save is kept as a backup.
 */
class SaveStore(private val dir: File) {
    private val main = File(dir, "save.json")
    private val tmp = File(dir, "save.json.tmp")
    private val backup = File(dir, "save.bak.json")

    @Synchronized
    fun write(json: String) {
        dir.mkdirs()
        tmp.writeText(json)
        if (main.exists() && main.length() > 0) main.copyTo(backup, overwrite = true)
        if (!tmp.renameTo(main)) {
            main.delete()
            if (!tmp.renameTo(main)) error("could not replace save file")
        }
    }

    /** Candidate save texts, newest first; the caller keeps the first that parses. */
    fun candidates(): List<String> = listOf(main, backup, tmp)
        .filter { it.exists() && it.length() > 0 }
        .mapNotNull { runCatching { it.readText() }.getOrNull() }

    fun export(cr: ContentResolver, uri: Uri, json: String) {
        cr.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) } ?: error("cannot open $uri")
    }

    fun import(cr: ContentResolver, uri: Uri): String =
        cr.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("cannot open $uri")
}
