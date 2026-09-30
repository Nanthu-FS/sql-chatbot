package com.spendlens.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.spendlens.app.data.toLocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDateTime
import java.util.UUID

/** Copies screenshots into app storage so they survive the original being deleted. */
class ImageStore(private val context: Context) {

    val imagesDir: File get() = File(context.filesDir, "screenshots").apply { mkdirs() }
    private val stagingDir: File get() = File(context.cacheDir, "staging").apply { mkdirs() }

    class Loaded(val bitmap: Bitmap, val staged: File)

    data class Meta(val displayName: String?, val takenAt: LocalDateTime?)

    suspend fun load(uri: Uri): Loaded = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_DIMENSION) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Unable to decode $uri")
        val staged = File(stagingDir, "${UUID.randomUUID()}.jpg")
        FileOutputStream(staged).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        Loaded(bitmap, staged)
    }

    /** Moves a staged screenshot into permanent storage. */
    suspend fun persist(staged: File): File? = withContext(Dispatchers.IO) {
        if (!staged.exists()) return@withContext null
        val target = File(imagesDir, staged.name)
        if (staged.renameTo(target)) target else runCatching { staged.copyTo(target, overwrite = true).also { staged.delete() } }.getOrNull()
    }

    suspend fun discard(paths: Collection<String>) = withContext(Dispatchers.IO) {
        paths.forEach { File(it).delete() }
    }

    fun clearStaging() {
        stagingDir.listFiles()?.forEach { it.delete() }
    }

    fun meta(uri: Uri): Meta = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            fun long(column: String): Long? = c.getColumnIndex(column).takeIf { it >= 0 && !c.isNull(it) }?.let { c.getLong(it) }
            fun string(column: String): String? = c.getColumnIndex(column).takeIf { it >= 0 }?.let { c.getString(it) }
            val taken = long(MediaStore.Images.ImageColumns.DATE_TAKEN)?.takeIf { it > 0 }
                ?: long(DocumentsContract.Document.COLUMN_LAST_MODIFIED)?.takeIf { it > 0 }
                ?: long(MediaStore.MediaColumns.DATE_ADDED)?.takeIf { it > 0 }?.times(1000)
            Meta(string(OpenableColumns.DISPLAY_NAME), taken?.toLocalDateTime())
        }
    }.getOrNull() ?: Meta(null, null)

    private companion object {
        const val MAX_DIMENSION = 3600
    }
}
