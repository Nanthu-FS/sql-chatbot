package com.ditherfarm

import android.net.Uri
import com.ditherfarm.engine.Assets
import com.ditherfarm.engine.Engine
import com.ditherfarm.engine.GameView
import com.ditherfarm.engine.Gfx
import com.ditherfarm.engine.GestureRouter
import com.ditherfarm.engine.Haptics
import com.ditherfarm.engine.Sfx
import com.ditherfarm.platform.SaveStore
import com.ditherfarm.ui.GridDemoScreen

/** Wires platform services to the engine. Game rules plug in here once designed. */
class Game(val activity: MainActivity) {
    val assets = Assets(activity.assets).also { it.load() }
    val gfx = Gfx(assets)
    val router = GestureRouter(slopPx = 10f * activity.resources.displayMetrics.density)
    val engine = Engine(gfx, router)
    val sfx = Sfx(activity.assets)
    val haptics = Haptics(activity)
    val store = SaveStore(activity.filesDir)
    val view = GameView(activity, engine) { _, _ -> }

    init {
        engine.start(GridDemoScreen(this))
    }

    fun onResume() = view.resetClock()
    fun onPause() {}
    fun onDestroy() = sfx.release()
    fun onBack(): Boolean = false

    fun exportTo(uri: Uri) {}
    fun importFrom(uri: Uri) {}
}
