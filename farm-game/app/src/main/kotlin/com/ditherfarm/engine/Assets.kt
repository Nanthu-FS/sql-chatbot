package com.ditherfarm.engine

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import org.json.JSONObject

/** Where sprites, fonts and sounds come from: the APK's assets, or plain files in JVM tests. */
interface AssetSource {
    fun list(dir: String): Array<String>?
    fun open(path: String): java.io.InputStream
    fun typeface(path: String): Typeface
    /** Android AssetManager for SoundPool, or null when audio is unavailable. */
    val assetManager: AssetManager? get() = null
}

class ApkAssets(override val assetManager: AssetManager) : AssetSource {
    override fun list(dir: String) = assetManager.list(dir)
    override fun open(path: String): java.io.InputStream = assetManager.open(path)
    override fun typeface(path: String): Typeface = Typeface.createFromAsset(assetManager, path)
}

class FileAssets(private val root: java.io.File) : AssetSource {
    override fun list(dir: String): Array<String>? = java.io.File(root, dir).list()
    override fun open(path: String): java.io.InputStream = java.io.File(root, path).inputStream()
    override fun typeface(path: String): Typeface = Typeface.createFromFile(java.io.File(root, path))
}

/** A loaded sprite and its pivot (the pixel placed on the draw position). */
class Sprite(val name: String, val bmp: Bitmap, val ax: Int, val ay: Int) {
    val w get() = bmp.width
    val h get() = bmp.height
}

/**
 * Loads every PNG under assets/art into memory. Pivots come from art/manifest.json,
 * written by Tools/ArtGen; sprites without an entry pivot at their bottom center.
 * Missing sprites resolve to a magenta placeholder so gaps are obvious, never crashes.
 */
class Assets(private val src: AssetSource) {
    private val sprites = HashMap<String, Sprite>()
    lateinit var font: Typeface
        private set
    private val missing = HashSet<String>()

    fun load() {
        font = runCatching { src.typeface("fonts/Silkscreen-Regular.ttf") }.getOrDefault(Typeface.MONOSPACE)
        val pivots = runCatching {
            JSONObject(src.open("art/manifest.json").bufferedReader().use { it.readText() })
        }.getOrNull()
        val opts = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        walk("art") { path ->
            val bmp = src.open(path).use { BitmapFactory.decodeStream(it, null, opts) } ?: return@walk
            val name = path.removePrefix("art/").removeSuffix(".png")
            val p = pivots?.optJSONObject(name)
            val ax = p?.optInt("ax", bmp.width / 2) ?: (bmp.width / 2)
            val ay = p?.optInt("ay", bmp.height) ?: bmp.height
            sprites[name] = Sprite(name, bmp, ax, ay)
        }
    }

    private fun walk(dir: String, onPng: (String) -> Unit) {
        val list = src.list(dir) ?: return
        for (entry in list) {
            val path = "$dir/$entry"
            if (entry.endsWith(".png")) onPng(path) else if (!entry.contains('.')) walk(path, onPng)
        }
    }

    fun has(name: String) = sprites.containsKey(name)

    operator fun get(name: String): Sprite = sprites[name] ?: placeholder(name)

    private fun placeholder(name: String): Sprite {
        if (missing.add(name)) android.util.Log.w("Assets", "missing sprite: $name")
        return sprites.getOrPut("__missing") {
            val b = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            val p = Paint()
            for (y in 0 until 16 step 4) for (x in 0 until 16 step 4) {
                p.color = if ((x + y) / 4 % 2 == 0) 0xFFFF00FF.toInt() else 0xFF000000.toInt()
                c.drawRect(Rect(x, y, x + 4, y + 4), p)
            }
            Sprite("__missing", b, 8, 16)
        }
    }
}
