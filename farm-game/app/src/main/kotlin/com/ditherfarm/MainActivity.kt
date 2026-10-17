package com.ditherfarm

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import com.ditherfarm.engine.ApkAssets
import com.ditherfarm.engine.AssetSource
import com.ditherfarm.engine.GameView
import com.ditherfarm.platform.Notifier

class MainActivity : Activity(), Host {
    private lateinit var game: Game
    private lateinit var view: GameView

    override val assetSource: AssetSource by lazy { ApkAssets(assets) }
    override val context: android.content.Context get() = this
    override val density: Float get() = resources.displayMetrics.density
    override val saveDir: java.io.File get() = filesDir

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        Notifier.ensureChannel(this)
        game = Game(this)
        view = GameView(this, game.engine) { _, _ -> }
        setContentView(view)
        hideSystemBars()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFY)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        window.insetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onResume() {
        super.onResume()
        view.resetClock()
        game.onResume()
    }

    override fun onPause() {
        game.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        game.onDestroy()
        super.onDestroy()
    }

    override fun scheduleReadyNotification(atEpochMs: Long, title: String, text: String) =
        Notifier.schedule(this, atEpochMs, title, text)

    override fun cancelReadyNotifications() = Notifier.cancel(this)

    override fun writeDocument(uri: android.net.Uri, text: String) {
        contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
    }

    override fun readDocument(uri: android.net.Uri): String? =
        contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }

    override fun requestExport() {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/json")
            .putExtra(Intent.EXTRA_TITLE, "dither-farm-save.json")
        @Suppress("DEPRECATION")
        startActivityForResult(i, REQ_EXPORT)
    }

    override fun requestImport() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
        @Suppress("DEPRECATION")
        startActivityForResult(i, REQ_IMPORT)
    }

    @Deprecated("Platform API without AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            REQ_EXPORT -> game.exportTo(uri)
            REQ_IMPORT -> game.importFrom(uri)
        }
    }

    @Deprecated("Back is handled by the game's own panels")
    override fun onBackPressed() {
        if (!game.onBack()) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    companion object {
        private const val REQ_NOTIFY = 1
        private const val REQ_EXPORT = 2
        private const val REQ_IMPORT = 3
    }
}
