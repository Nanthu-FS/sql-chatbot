package com.ditherfarm.engine

import android.content.Context
import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.random.Random

/** Short chiptune effects loaded from the WAV files in assets/sfx. */
class Sfx(am: AssetManager?) {
    var enabled = true
    private val pool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        .build()
    /** Sound name → its takes (name, name_2, name_3 ...). */
    private val takes = HashMap<String, MutableList<Int>>()
    private val lastTake = HashMap<String, Int>()
    private val lastPlayed = HashMap<String, Long>()
    private val rnd = Random(3)

    init {
        if (am != null) {
            am.list("sfx")?.filter { it.endsWith(".wav") }?.sorted()?.forEach { f ->
                val name = f.removeSuffix(".wav").replace(Regex("_[2-9]$"), "")
                runCatching { takes.getOrPut(name) { ArrayList() } += pool.load(am.openFd("sfx/$f"), 1) }
            }
        }
    }

    /**
     * Plays [name]: a random take (never the same one twice in a row) with a little pitch
     * and volume wobble, so frequent sounds don't grate. Rapid repeats are throttled.
     */
    fun play(name: String, volume: Float = 0.8f, pitchJitter: Float = 0.08f) {
        if (!enabled) return
        val list = takes[name] ?: return
        val now = System.nanoTime() / 1_000_000
        if (now - (lastPlayed[name] ?: 0L) < 45) return
        lastPlayed[name] = now
        var pick = rnd.nextInt(list.size)
        if (list.size > 1 && pick == lastTake[name]) pick = (pick + 1 + rnd.nextInt(list.size - 1)) % list.size
        lastTake[name] = pick
        val rate = 1f + (rnd.nextFloat() * 2f - 1f) * pitchJitter
        val vol = (volume * (0.88f + rnd.nextFloat() * 0.24f)).coerceIn(0f, 1f)
        pool.play(list[pick], vol, vol, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    fun release() = pool.release()
}

/**
 * Looping background music and ambience from assets/music and assets/ambience (OGG).
 * Three channels (music, ambience bed, weather layer) each crossfade to a new loop when
 * [set] changes it. Safe to use without assets (tests): everything becomes a no-op.
 */
class Soundscape(private val am: AssetManager?) {
    var musicOn = true
    var ambienceOn = true
    private val music = Channel("music", 0.38f) { musicOn }
    private val bed = Channel("ambience", 0.45f) { ambienceOn }
    private val weather = Channel("ambience", 0.55f) { ambienceOn }
    private var paused = false

    /** Chooses what should be playing; null silences a channel. Cheap to call every frame. */
    fun set(track: String?, ambience: String?, weatherLoop: String?) {
        music.target(track); bed.target(ambience); weather.target(weatherLoop)
    }

    fun update(dt: Float) {
        if (paused) return
        music.update(dt); bed.update(dt); weather.update(dt)
    }

    fun pause() { paused = true; listOf(music, bed, weather).forEach { it.pause() } }
    fun resume() { paused = false; listOf(music, bed, weather).forEach { it.resume() } }
    fun release() = listOf(music, bed, weather).forEach { it.release() }

    private inner class Channel(val folder: String, val maxVolume: Float, val on: () -> Boolean) {
        private var name: String? = null
        private var player: MediaPlayer? = null
        private var vol = 0f
        /** The previous loop, fading out while the new one fades in. */
        private var old: MediaPlayer? = null
        private var oldVol = 0f

        fun target(next: String?) {
            val want = if (on()) next else null
            if (want == name) return
            old?.release()
            old = player; oldVol = vol
            name = want
            player = want?.let { open(it) }
            vol = 0f
        }

        private fun open(n: String): MediaPlayer? {
            val assets = am ?: return null
            return runCatching {
                val fd = assets.openFd("$folder/$n.ogg")
                MediaPlayer().apply {
                    setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    fd.close()
                    isLooping = true
                    setVolume(0f, 0f)
                    prepare()
                    if (!paused) start()
                }
            }.getOrNull()
        }

        fun update(dt: Float) {
            val fade = dt / 1.6f
            player?.let { vol = minOf(maxVolume, vol + fade * maxVolume); it.setVolume(vol, vol) }
            old?.let {
                oldVol -= fade * maxVolume
                if (oldVol <= 0f) { it.release(); old = null } else it.setVolume(oldVol, oldVol)
            }
        }

        fun pause() { runCatching { player?.pause() }; old?.release(); old = null }
        fun resume() { runCatching { player?.start() } }
        fun release() { player?.release(); old?.release(); player = null; old = null; name = null }
    }
}

/** Tactile feedback with a user toggle. */
class Haptics(ctx: Context?) {
    var enabled = true
    private val vib: Vibrator? = if (ctx == null) null else runCatching {
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
