package com.ditherfarm.engine

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import org.json.JSONObject

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
class Assets(private val am: AssetManager) {
    private val sprites = HashMap<String, Sprite>()
    lateinit var font: Typeface
        private set
    private val missing = HashSet<String>()

    fun load() {
        font = runCatching { Typeface.createFromAsset(am, "fonts/Silkscreen-Regular.ttf") }.getOrDefault(Typeface.MONOSPACE)
        val pivots = runCatching {
            JSONObject(am.open("art/manifest.json").bufferedReader().use { it.readText() })
        }.getOrNull()
        val opts = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        walk("art") { path ->
            val bmp = am.open(path).use { BitmapFactory.decodeStream(it, null, opts) } ?: return@walk
            val name = path.removePrefix("art/").removeSuffix(".png")
            val p = pivots?.optJSONObject(name)
            val ax = p?.optInt("ax", bmp.width / 2) ?: (bmp.width / 2)
            val ay = p?.optInt("ay", bmp.height) ?: bmp.height
            sprites[name] = Sprite(name, bmp, ax, ay)
        }
    }

    private fun walk(dir: String, onPng: (String) -> Unit) {
        val list = am.list(dir) ?: return
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
