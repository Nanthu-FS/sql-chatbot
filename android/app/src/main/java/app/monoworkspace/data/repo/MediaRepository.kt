package app.monoworkspace.data.repo

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import android.webkit.MimeTypeMap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import app.monoworkspace.core.Ids
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class ImportedMedia(
    val name: String,
    val displayName: String,
    val size: Long,
    val mimeType: String?,
    val width: Int? = null,
    val height: Int? = null,
)

class MediaTooLargeException : IOException("Files over 20 MB cannot be added")

/**
 * Media lives in app-private storage under filesDir/media. Picked files are
 * always copied in; nothing references external storage after import.
 */
class MediaRepository(private val context: Context) {
    val dir: File = File(context.filesDir, "media").apply { mkdirs() }
    private val resolver: ContentResolver get() = context.contentResolver
    private val cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun file(name: String): File = File(dir, name)

    fun uriFor(name: String): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".files", file(name))

    private fun queryNameAndSize(uri: Uri): Pair<String, Long> {
        var name = "file"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
            }
        }
        return name to size
    }

    suspend fun importImage(uri: Uri): ImportedMedia = withContext(Dispatchers.IO) {
        val (displayName, size) = queryNameAndSize(uri)
        if (size > MAX_BYTES) throw MediaTooLargeException()
        // Copy to a temp file first so we can read it twice (bounds, EXIF, pixels).
        val tmp = File(context.cacheDir, "import-" + Ids.new())
        try {
            copyLimited(uri, tmp)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tmp.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not an image")
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
            val decoded = BitmapFactory.decodeFile(tmp.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: throw IOException("Could not decode image")
            val rotation = runCatching {
                when (ExifInterface(tmp.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }.getOrDefault(0f)
            val longEdge = maxOf(decoded.width, decoded.height)
            val scale = if (longEdge > MAX_EDGE) MAX_EDGE.toFloat() / longEdge else 1f
            val matrix = Matrix().apply {
                if (scale != 1f) postScale(scale, scale)
                if (rotation != 0f) postRotate(rotation)
            }
            val bitmap = if (scale != 1f || rotation != 0f) {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            } else decoded
            val png = bitmap.hasAlpha()
            val name = Ids.new() + if (png) ".png" else ".jpg"
            FileOutputStream(file(name)).use { out ->
                bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 90, out)
            }
            ImportedMedia(name, displayName, file(name).length(), if (png) "image/png" else "image/jpeg", bitmap.width, bitmap.height)
        } finally {
            tmp.delete()
        }
    }

    suspend fun importFile(uri: Uri): ImportedMedia = withContext(Dispatchers.IO) {
        val (displayName, size) = queryNameAndSize(uri)
        if (size > MAX_BYTES) throw MediaTooLargeException()
        val mime = resolver.getType(uri)
        val ext = displayName.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length <= 8 }
            ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mime ?: "")
        val name = Ids.new() + (ext?.let { ".$it" } ?: "")
        val target = file(name)
        try {
            copyLimited(uri, target)
        } catch (e: IOException) {
            target.delete()
            throw e
        }
        ImportedMedia(name, displayName, target.length(), mime)
    }

    private fun copyLimited(uri: Uri, target: File) {
        val input = resolver.openInputStream(uri) ?: throw IOException("Cannot open file")
        input.use { ins ->
            FileOutputStream(target).use { out ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BYTES) {
                        out.close()
                        target.delete()
                        throw MediaTooLargeException()
                    }
                    out.write(buf, 0, n)
                }
            }
        }
    }

    suspend fun loadImage(name: String, maxEdge: Int = 1600): ImageBitmap? {
        val key = "$name@$maxEdge"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val f = file(name)
            if (!f.exists()) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
            val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@withContext null
            bmp.asImageBitmap().also { cache.put(key, it) }
        }
    }

    fun delete(names: Collection<String>) {
        names.forEach { n ->
            if (n.isNotBlank() && !n.contains('/')) file(n).delete()
        }
        cache.evictAll()
    }

    fun list(): List<File> = dir.listFiles()?.filter { it.isFile }.orEmpty()

    fun totalBytes(): Long = list().sumOf { it.length() }

    fun clear() {
        list().forEach { it.delete() }
        cache.evictAll()
    }

    companion object {
        const val MAX_BYTES = 20L * 1024 * 1024
        const val MAX_EDGE = 2048
    }
}
