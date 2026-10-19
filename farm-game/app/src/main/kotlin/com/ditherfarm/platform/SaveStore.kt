package com.ditherfarm.platform

import com.ditherfarm.game.GameState
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/**
 * Crash-safe JSON save files.
 *
 * A save is written to a temp file and forced onto the disk, then renamed over the real
 * save, so a reader only ever finds a complete old save or a complete new one. Before the
 * main file is replaced it is copied to the backup, but only when it holds a whole save:
 * a damaged main file never overwrites the last good backup. A main file that cannot be
 * read is kept as a copy, and so is a save written by a newer [version] of the game (the
 * first one seen), because this build may not understand all of it.
 */
class SaveStore(private val dir: File, private val version: Int = GameState.VERSION) {
    private val main = File(dir, "save.json")
    private val tmp = File(dir, "save.json.tmp")
    private val backup = File(dir, "save.bak.json")

    /** Replaces the save with [json]. Throws when the new save could not be written. */
    @Synchronized
    fun write(json: String) {
        dir.mkdirs()
        flush(tmp, json.toByteArray(Charsets.UTF_8))
        val old = wholeSave(main)
        if (old != null) {
            if (old.version > version) keepCopy("save.v${old.version}.json", main)
            writeAtomic(backup, old.bytes)
        } else if (main.length() > 0) {
            keepCopy("save.corrupt.json", main)
        }
        commit(tmp, main)
    }

    /**
     * Save texts that are not empty, newest first: a whole temp file left by a write that
     * crashed before its rename, then the main file, then the backup. The caller keeps the
     * first one that loads, so a half-written temp file or a damaged main file is skipped.
     */
    fun candidates(): List<String> = listOf(tmp, main, backup)
        .filter { it.isFile && it.length() > 0 }
        .mapNotNull { runCatching { it.readText() }.getOrNull() }

    /** The bytes of [file] and its save version, when it holds one whole JSON object; null otherwise. */
    private fun wholeSave(file: File): WholeSave? {
        if (!file.isFile || file.length() == 0L) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        val obj = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull() ?: return null
        return WholeSave(bytes, obj.optInt("version", 1))
    }

    /** Copies [from] to [name] in the save folder, unless a copy with that name is already there. */
    private fun keepCopy(name: String, from: File) {
        val copy = File(dir, name)
        if (!copy.exists()) writeAtomic(copy, from.readBytes())
    }

    /** Writes [bytes] to a temp file beside [target] and renames it into place. */
    private fun writeAtomic(target: File, bytes: ByteArray) {
        val part = File(target.path + ".tmp")
        flush(part, bytes)
        commit(part, target)
    }

    /** Writes [bytes] to [part] and forces them onto the disk before returning. */
    private fun flush(part: File, bytes: ByteArray) {
        FileOutputStream(part).use { out ->
            out.write(bytes)
            out.fd.sync()
        }
    }

    /**
     * Renames [part] over [target], which is atomic on Linux and Android. The delete and
     * retry is only for platforms that refuse to rename over an existing file; if it fails
     * the whole new save is still in [part], where [candidates] finds it.
     */
    private fun commit(part: File, target: File) {
        if (!part.renameTo(target)) {
            target.delete()
            if (!part.renameTo(target)) error("could not replace ${target.name}")
        }
        syncDir()
    }

    /** Forces the directory entries (the renames) onto the disk; best effort, not every platform allows it. */
    private fun syncDir() {
        runCatching { FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { it.force(true) } }
    }

    /** A save file's bytes and the save version it declares. */
    private class WholeSave(val bytes: ByteArray, val version: Int)
}
