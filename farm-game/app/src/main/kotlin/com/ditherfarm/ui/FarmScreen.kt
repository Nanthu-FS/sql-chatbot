package com.ditherfarm.ui

import com.ditherfarm.Game
import com.ditherfarm.WorldListener
import com.ditherfarm.game.WorldEvent

/**
 * Placeholder while the real Farm screen is being built: shows the demo island and
 * the shared HUD, and forwards toasts. Replaced by the playable farm next.
 */
class FarmScreen(game: Game) : WorldListener {
    val screen = GridDemoScreen(game)
    private val hud = Hud(game)

    fun toast(text: String) = hud.toast(text)
    fun closePanel(): Boolean = false

    override fun onWorldEvent(e: WorldEvent) {}
}
