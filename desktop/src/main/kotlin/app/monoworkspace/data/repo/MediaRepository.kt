package app.monoworkspace.data.repo

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import app.monoworkspace.core.Ids
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.net.URLConnection
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

data class ImportedMedia(
    val name: String,
    val displayName: String,
    val size: Long,
    val mimeType: String?,
    val width: Int? = null,
    val height: Int? = null,
)

class MediaTooLargeException : IOException("Files over 20 MB cannot be added")

/** Media is copied into the workspace folder; nothing points outside it after import. */
class MediaRepository(root: File) {
    val dir: File = File(root, "media").apply { mkdirs() }
    private val cache = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        var bytes = 0L
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
            if (bytes <= CACHE_BYTES || eldest == null) return false
            bytes -= eldest.value.width.toLong() * eldest.value.height * 4
            return true
        }
    }

    fun file(name: String): File = File(dir, name)

    suspend fun importImage(source: File): ImportedMedia = withContext(Dispatchers.IO) {
        if (source.length() > MAX_BYTES) throw MediaTooLargeException()
        val decoded = ImageIO.read(source) ?: throw IOException("Not an image")
        val rotation = if (source.extension.lowercase() in setOf("jpg", "jpeg")) exifRotation(source) else 0
        val longEdge = maxOf(decoded.width, decoded.height)
        val scale = if (longEdge > MAX_EDGE) MAX_EDGE.toDouble() / longEdge else 1.0
        val png = decoded.colorModel.hasAlpha()
        val image = transform(decoded, scale, rotation, png)
        val name = Ids.new() + if (png) ".png" else ".jpg"
        val target = file(name)
        if (png) {
            ImageIO.write(image, "png", target)
        } else {
            val writer = ImageIO.getImageWritersByFormatName("jpg").next()
            val param = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = 0.9f
            }
            ImageIO.createImageOutputStream(target).use { out ->
                writer.output = out
                writer.write(null, IIOImage(image, null, null), param)
            }
            writer.dispose()
        }
        ImportedMedia(name, source.name, target.length(), if (png) "image/png" else "image/jpeg", image.width, image.height)
    }

    private fun transform(src: BufferedImage, scale: Double, rotation: Int, alpha: Boolean): BufferedImage {
        if (scale == 1.0 && rotation == 0 && (alpha || src.type == BufferedImage.TYPE_INT_RGB)) return src
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        val swap = rotation == 90 || rotation == 270
        val out = BufferedImage(if (swap) h else w, if (swap) w else h, if (alpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        val t = AffineTransform()
        when (rotation) {
            90 -> { t.translate(h.toDouble(), 0.0); t.rotate(Math.PI / 2) }
            180 -> { t.translate(w.toDouble(), h.toDouble()); t.rotate(Math.PI) }
            270 -> { t.translate(0.0, w.toDouble()); t.rotate(-Math.PI / 2) }
        }
        t.scale(scale, scale)
        g.drawImage(src, t, null)
        g.dispose()
        return out
    }

    /** Reads the EXIF orientation tag from a JPEG's APP1 segment. */
    private fun exifRotation(f: File): Int = runCatching {
        DataInputStream(f.inputStream().buffered()).use { ins ->
            if (ins.readUnsignedShort() != 0xFFD8) return@use 0
            while (true) {
                val marker = ins.readUnsignedShort()
                if (marker and 0xFF00 != 0xFF00 || marker == 0xFFDA) return@use 0
                val len = ins.readUnsignedShort() - 2
                if (marker != 0xFFE1) { ins.skipNBytes(len.toLong()); continue }
                val seg = ByteArray(len).also { ins.readFully(it) }
                if (String(seg, 0, 4) != "Exif") return@use 0
                val base = 6
                val little = seg[base] == 'I'.code.toByte()
                fun u16(o: Int) = if (little) (seg[o].toInt() and 0xFF) or ((seg[o + 1].toInt() and 0xFF) shl 8)
                else ((seg[o].toInt() and 0xFF) shl 8) or (seg[o + 1].toInt() and 0xFF)
                fun u32(o: Int) = if (little) u16(o) or (u16(o + 2) shl 16) else (u16(o) shl 16) or u16(o + 2)
                val ifd = base + u32(base + 4)
                val count = u16(ifd)
                for (i in 0 until count) {
                    val e = ifd + 2 + i * 12
                    if (u16(e) == 0x0112) {
                        return@use when (u16(e + 8)) { 3 -> 180; 6 -> 90; 8 -> 270; else -> 0 }
                    }
                }
                return@use 0
            }
            @Suppress("UNREACHABLE_CODE") 0
        }
    }.getOrDefault(0)

    suspend fun importFile(source: File): ImportedMedia = withContext(Dispatchers.IO) {
        if (source.length() > MAX_BYTES) throw MediaTooLargeException()
        val ext = source.extension.takeIf { it.isNotEmpty() && it.length <= 8 }
        val name = Ids.new() + (ext?.let { ".$it" } ?: "")
        val target = file(name)
        try {
            source.copyTo(target)
        } catch (e: IOException) {
            target.delete()
            throw e
        }
        ImportedMedia(name, source.name, target.length(), URLConnection.guessContentTypeFromName(source.name))
    }

    suspend fun loadImage(name: String, maxEdge: Int = 1600): ImageBitmap? {
        val key = "$name@$maxEdge"
        synchronized(cache) { cache[key] }?.let { return it }
        return withContext(Dispatchers.IO) {
            val f = file(name)
            if (!f.exists()) return@withContext null
            val img = runCatching { ImageIO.read(f) }.getOrNull() ?: return@withContext null
            val longEdge = maxOf(img.width, img.height)
            val scaled = if (longEdge > maxEdge) transform(img, maxEdge.toDouble() / longEdge, 0, img.colorModel.hasAlpha()) else img
            scaled.toComposeImageBitmap().also { bmp ->
                synchronized(cache) {
                    cache.bytes += bmp.width.toLong() * bmp.height * 4
                    cache[key] = bmp
                }
            }
        }
    }

    fun delete(names: Collection<String>) {
        names.forEach { n ->
            if (n.isNotBlank() && !n.contains('/') && !n.contains('\\')) file(n).delete()
        }
        evict()
    }

    fun list(): List<File> = dir.listFiles()?.filter { it.isFile }.orEmpty()

    fun totalBytes(): Long = list().sumOf { it.length() }

    fun clear() {
        list().forEach { it.delete() }
        evict()
    }

    private fun evict() = synchronized(cache) {
        cache.clear()
        cache.bytes = 0
    }

    companion object {
        const val MAX_BYTES = 20L * 1024 * 1024
        const val MAX_EDGE = 2048
        private const val CACHE_BYTES = 96L * 1024 * 1024
    }
}
