package com.ditherfarm

import android.net.Uri
import com.ditherfarm.engine.Assets
import com.ditherfarm.engine.Engine
import com.ditherfarm.engine.Gfx
import com.ditherfarm.engine.GestureRouter
import com.ditherfarm.engine.Haptics
import com.ditherfarm.engine.Sfx
import com.ditherfarm.engine.Soundscape
import com.ditherfarm.game.Weather
import com.ditherfarm.game.GameData
import com.ditherfarm.game.GameState
import com.ditherfarm.game.World
import com.ditherfarm.game.WorldEvent
import com.ditherfarm.game.truckLoad
import com.ditherfarm.platform.SaveStore
import com.ditherfarm.ui.FarmScreen
import com.ditherfarm.ui.ForestScreen
import com.ditherfarm.ui.MineScreen
import com.ditherfarm.ui.Nav
import com.ditherfarm.ui.Ui
import org.json.JSONObject

/** Screens that want to react to simulation events (sounds, particles, popups). */
interface WorldListener {
    fun onWorldEvent(e: WorldEvent)
}

/**
 * Wires services, the simulation and the screens together: loads or creates the save,
 * advances the world every frame, autosaves, catches up after the app was closed and
 * schedules "ready" notifications when it goes to the background.
 */
class Game(val host: Host) {
    val assets = Assets(host.assetSource).also { it.load() }
    val gfx = Gfx(assets)
    val router = GestureRouter(slopPx = 10f * host.density)
    val engine = Engine(gfx, router)
    val sfx = Sfx(host.assetSource.assetManager)
    val haptics = Haptics(host.context)
    val soundscape = Soundscape(host.assetSource.assetManager)
    val store = SaveStore(host.saveDir)
    val ui = Ui(gfx, sfx, haptics)
    val data = GameData.load { host.assetSource.open("data/$it").bufferedReader().use { r -> r.readText() } }

    /** Text shown once after offline catch-up ("While you were away"). */
    var awaySummary: String? = null
    var world: World = loadOrCreate()
        private set
    private var sinceSave = 0f
    private var paused = false

    val farm = FarmScreen(this)
    val forest by lazy { ForestScreen(this) }
    val mine by lazy { MineScreen(this) }

    init {
        applySettings()
        engine.onTick = { dt -> tick(dt) }
        engine.start(farm)
    }

    private fun loadOrCreate(): World {
        for (text in store.candidates()) {
            val state = runCatching { GameState.fromJson(JSONObject(text)) }.getOrNull() ?: continue
            if (state.plots.isEmpty()) continue
            val w = World(data, state)
            val away = (host.nowMs() - state.lastSavedMs) / 1000.0
            if (state.lastSavedMs > 0 && away > 5) catchUpSummary(w, away)
            return w
        }
        return fresh()
    }

    /** A new farm; the first login gift comes tomorrow. */
    private fun fresh() = World.newGame(data, host.nowMs()).also { it.state.giftDay = today() }

    /** The device's calendar day (epoch days, local time zone) for the login gift. */
    fun today(): Long = java.time.Instant.ofEpochMilli(host.nowMs()).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()

    private fun catchUpSummary(w: World, seconds: Double) {
        val coinsBefore = w.state.coins
        val readyBefore = w.state.plots.count { w.isReady(it) }
        w.catchUp(seconds)
        val ready = w.state.plots.count { w.isReady(it) } - readyBefore
        val withered = w.events.filterIsInstance<WorldEvent.SeasonChanged>().sumOf { it.withered }
        val storms = w.events.count { it is WorldEvent.Storm }
        w.events.removeAll { it is WorldEvent.Ripened || it is WorldEvent.DayStarted }
        val parts = ArrayList<String>()
        if (ready > 0) parts += "$ready crops ready"
        if (withered > 0) parts += "$withered withered"
        if (storms > 0) parts += "$storms storm${if (storms > 1) "s" else ""}"
        if (w.state.coins != coinsBefore) parts += "${w.state.coins - coinsBefore} coins"
        val batches = w.events.count { it is WorldEvent.JobDone }
        if (batches > 0) parts += "$batches machine batches done"
        w.events.filterIsInstance<WorldEvent.CartSold>().takeIf { it.isNotEmpty() }?.let { s -> parts += "Market Cart sold ${s.sumOf { it.units }}" }
        val worked = w.events.count { it is WorldEvent.WorkerActed }
        if (worked > 0) parts += "farmhands did $worked jobs"
        if (w.state.truck.load.isNotEmpty()) parts += "the truck has ${w.truckLoad()} crops to sell"
        val hut = w.state.buildings["logging_hut"]?.tray?.values?.sum() ?: 0
        if (hut > 0) parts += "Logging Hut has $hut items"
        awaySummary = if (parts.isEmpty()) null else parts.joinToString(" - ")
    }

    private fun tick(dt: Float) {
        if (paused) return
        world.advance(dt.toDouble())
        updateSoundscape(dt)
        if (world.events.isNotEmpty()) {
            val listener = engine.screen as? WorldListener
            val batch = ArrayList(world.events)
            world.events.clear()
            if (listener != null) batch.forEach { listener.onWorldEvent(it) }
        }
        sinceSave += dt
        if (sinceSave > AUTOSAVE_SECONDS) save()
    }

    /** Music and ambience follow the screen, the hour, the season and the weather. */
    private fun updateSoundscape(dt: Float) {
        val t = world.time
        val night = t.hour >= 20f || t.hour < 5f
        val underground = engine.screen === mine && world.state.mine.floor > 0
        val track = when {
            underground -> "mine"
            night -> "night"
            engine.screen === forest -> "forest"
            else -> "farm_" + t.season.name.lowercase()
        }
        val bed = when {
            underground -> "mine"
            night -> "night"
            engine.screen === forest -> "forest"
            else -> "day"
        }
        val weather = if (underground) null else when (world.state.weather) {
            Weather.RAIN -> "rain"
            Weather.STORM -> "storm"
            Weather.SNOW, Weather.BLIZZARD -> "snow"
            Weather.SUNNY -> null
        }
        soundscape.set(track, bed, weather)
        soundscape.update(dt)
    }

    fun applySettings() {
        sfx.enabled = world.state.sfx
        haptics.enabled = world.state.haptics
        soundscape.musicOn = world.state.music
        soundscape.ambienceOn = world.state.ambience
    }

    fun save() {
        sinceSave = 0f
        world.state.lastSavedMs = host.nowMs()
        runCatching { store.write(world.state.toJson().toString()) }
    }

    fun onResume() {
        host.cancelReadyNotifications()
        val away = (host.nowMs() - world.state.lastSavedMs) / 1000.0
        if (paused && world.state.lastSavedMs > 0 && away > 5) catchUpSummary(world, away)
        paused = false
        soundscape.resume()
    }

    fun onPause() {
        save()
        paused = true
        soundscape.pause()
        if (world.state.notifications) {
            world.secondsUntilNextCrop()?.let { s ->
                if (s > 30) host.scheduleReadyNotification(host.nowMs() + (s * 1000).toLong(), "Dither Farm", "Your crops are ready to harvest!")
            }
        }
    }

    fun onDestroy() {
        save()
        sfx.release()
        soundscape.release()
    }

    /** Back button: closes the open panel first, then returns to the farm. */
    fun onBack(): Boolean = when (val s = engine.screen) {
        is FarmScreen -> s.closePanel()
        is ForestScreen -> s.closePanel() || run { goTo(Nav.FARM); true }
        is MineScreen -> s.closePanel() || run { goTo(Nav.FARM); true }
        else -> false
    }

    /** Starts placing a fixture item on the farm field. */
    fun startPlacing(item: String) {
        if (engine.screen !== farm) goTo(Nav.FARM)
        farm.startPlacing(item)
    }

    fun goTo(nav: Nav) {
        val to = when (nav) {
            Nav.FARM -> farm
            Nav.FOREST -> forest
            Nav.MINE -> mine
            else -> return
        }
        sfx.play("swap")
        engine.go(to)
    }

    fun recenter() = when (val s = engine.screen) {
        is FarmScreen -> s.recenter()
        is ForestScreen -> s.recenter()
        is MineScreen -> s.recenter()
        else -> Unit
    }

    fun toast(text: String) = when (val s = engine.screen) {
        is ForestScreen -> s.chrome.hud.toast(text)
        is MineScreen -> s.chrome.hud.toast(text)
        else -> farm.toast(text)
    }

    fun exportTo(uri: Uri) {
        save()
        runCatching { host.writeDocument(uri, world.state.toJson().toString(2)) }
            .onSuccess { toast("Save exported") }.onFailure { toast("Export failed") }
    }

    fun importFrom(uri: Uri) {
        val text = runCatching { host.readDocument(uri) }.getOrNull()
        val state = text?.let { runCatching { GameState.fromJson(JSONObject(it)) }.getOrNull() }
        if (state == null || state.plots.isEmpty()) { toast("Not a Dither Farm save"); return }
        world = World(data, state)
        applySettings()
        save()
        toast("Save imported")
    }

    /** Starts over with a fresh farm (settings menu, confirmed twice). */
    fun newGame() {
        world = fresh()
        applySettings()
        save()
    }

    companion object {
        const val AUTOSAVE_SECONDS = 30f
    }
}
