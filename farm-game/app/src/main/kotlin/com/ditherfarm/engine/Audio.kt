package com.ditherfarm.engine

import android.content.Context
import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.random.Random

/** Short chiptune effects loaded from the WAV files in assets/sfx. */
class Sfx(am: AssetManager) {
    var enabled = true
    private val pool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    private val ids = HashMap<String, Int>()
    private val lastPlayed = HashMap<String, Long>()
    private val rnd = Random(3)

    init {
        am.list("sfx")?.filter { it.endsWith(".wav") }?.forEach { f ->
            runCatching { ids[f.removeSuffix(".wav")] = pool.load(am.openFd("sfx/$f"), 1) }
        }
    }

    /** Plays [name] with a little pitch wobble; rapid repeats of one sound are throttled. */
    fun play(name: String, volume: Float = 0.8f, pitchJitter: Float = 0.06f) {
        if (!enabled) return
        val id = ids[name] ?: return
        val now = System.nanoTime() / 1_000_000
        if (now - (lastPlayed[name] ?: 0L) < 45) return
        lastPlayed[name] = now
        val rate = 1f + (rnd.nextFloat() * 2f - 1f) * pitchJitter
        pool.play(id, volume, volume, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    fun release() = pool.release()
}

/** Tactile feedback with a user toggle. */
class Haptics(ctx: Context) {
    var enabled = true
    private val vib: Vibrator? = runCatching {
        (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            ?: @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
    }.getOrNull()

    fun tick() = fire(VibrationEffect.EFFECT_TICK)
    fun click() = fire(VibrationEffect.EFFECT_CLICK)
    fun heavy() = fire(VibrationEffect.EFFECT_HEAVY_CLICK)

    private fun fire(effect: Int) {
        if (!enabled) return
        val v = vib ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(VibrationEffect.createPredefined(effect)) }
    }
}
