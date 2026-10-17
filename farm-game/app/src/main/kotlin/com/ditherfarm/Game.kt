package com.ditherfarm

import android.net.Uri
import com.ditherfarm.engine.Assets
import com.ditherfarm.engine.Engine
import com.ditherfarm.engine.Gfx
import com.ditherfarm.engine.GestureRouter
import com.ditherfarm.engine.Haptics
import com.ditherfarm.engine.Sfx
import com.ditherfarm.platform.SaveStore
import com.ditherfarm.ui.GridDemoScreen

/** Wires services to the engine. Game rules plug in here once designed. */
class Game(val host: Host) {
    val assets = Assets(host.assetSource).also { it.load() }
    val gfx = Gfx(assets)
    val router = GestureRouter(slopPx = 10f * host.density)
    val engine = Engine(gfx, router)
    val sfx = Sfx(host.assetSource.assetManager)
    val haptics = Haptics(host.context)
    val store = SaveStore(host.saveDir)

    init {
        engine.start(GridDemoScreen(this))
    }

    fun onResume() {}
    fun onPause() {}
    fun onDestroy() = sfx.release()
    fun onBack(): Boolean = false

    fun exportTo(uri: Uri) {}
    fun importFrom(uri: Uri) {}
}
