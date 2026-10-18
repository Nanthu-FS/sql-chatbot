package com.ditherfarm

import android.net.Uri
import com.ditherfarm.engine.Assets
import com.ditherfarm.engine.Engine
import com.ditherfarm.engine.Gfx
import com.ditherfarm.engine.GestureRouter
import com.ditherfarm.engine.Haptics
import com.ditherfarm.engine.Sfx
import com.ditherfarm.game.GameData
import com.ditherfarm.game.GameState
import com.ditherfarm.game.World
import com.ditherfarm.game.WorldEvent
import com.ditherfarm.platform.SaveStore
import com.ditherfarm.ui.FarmScreen
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

    init {
        applySettings()
        engine.onTick = { dt -> tick(dt) }
        engine.start(farm.screen)
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
        return World.newGame(data, host.nowMs())
    }

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
        awaySummary = if (parts.isEmpty()) null else parts.joinToString(" - ")
    }

    private fun tick(dt: Float) {
        if (paused) return
        world.advance(dt.toDouble())
        if (world.events.isNotEmpty()) {
            val listener = engine.screen as? WorldListener
            val batch = ArrayList(world.events)
            world.events.clear()
            if (listener != null) batch.forEach { listener.onWorldEvent(it) }
        }
        sinceSave += dt
        if (sinceSave > AUTOSAVE_SECONDS) save()
    }

    fun applySettings() {
        sfx.enabled = world.state.sfx
        haptics.enabled = world.state.haptics
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
    }

    fun onPause() {
        save()
        paused = true
        if (world.state.notifications) {
            world.secondsUntilNextCrop()?.let { s ->
                if (s > 30) host.scheduleReadyNotification(host.nowMs() + (s * 1000).toLong(), "Dither Farm", "Your crops are ready to harvest!")
            }
        }
    }

    fun onDestroy() {
        save()
        sfx.release()
    }

    /** Back button: closes the open panel first. */
    fun onBack(): Boolean = farm.closePanel()

    fun exportTo(uri: Uri) {
        save()
        runCatching { host.writeDocument(uri, world.state.toJson().toString(2)) }
            .onSuccess { farm.toast("Save exported") }.onFailure { farm.toast("Export failed") }
    }

    fun importFrom(uri: Uri) {
        val text = runCatching { host.readDocument(uri) }.getOrNull()
        val state = text?.let { runCatching { GameState.fromJson(JSONObject(it)) }.getOrNull() }
        if (state == null || state.plots.isEmpty()) { farm.toast("Not a Dither Farm save"); return }
        world = World(data, state)
        applySettings()
        save()
        farm.toast("Save imported")
    }

    /** Starts over with a fresh farm (settings menu, confirmed twice). */
    fun newGame() {
        world = World.newGame(data, host.nowMs())
        applySettings()
        save()
    }

    companion object {
        const val AUTOSAVE_SECONDS = 30f
    }
}
