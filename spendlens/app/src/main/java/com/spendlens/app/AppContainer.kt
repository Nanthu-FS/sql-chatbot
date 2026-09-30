package com.spendlens.app

import android.content.Context
import android.net.Uri
import com.spendlens.app.data.AppDatabase
import com.spendlens.app.data.SettingsRepository
import com.spendlens.app.data.TransactionRepository
import com.spendlens.app.lock.AppLock
import com.spendlens.app.notify.Notifier
import com.spendlens.app.notify.Surfaces
import com.spendlens.app.ocr.ImageStore
import com.spendlens.app.ocr.ImportManager
import com.spendlens.app.ocr.OcrEngine
import com.spendlens.app.ocr.ScreenshotFinder
import com.spendlens.app.sms.SmsAutoImporter
import com.spendlens.app.sms.SmsReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Hand-rolled dependency graph; small enough not to need a DI framework. */
class AppContainer(context: Context, databaseName: String = AppDatabase.NAME) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database = AppDatabase.create(context, databaseName)
    val repository = TransactionRepository(database.transactionDao(), database.goalDao())
    val settings = SettingsRepository(context)
    val images = ImageStore(context).also { it.clearStaging() }
    val smsReader = SmsReader(context)
    val importManager = ImportManager(context, repository, images, OcrEngine(), ScreenshotFinder(context), smsReader, appScope)
    val notifier = Notifier(context)
    val surfaces = Surfaces(context, repository, settings, notifier)
    val smsAutoImporter = SmsAutoImporter(repository, settings, notifier)
    val lock = AppLock(settings, appScope)

    /** Images shared to the app from the gallery or a payment app, waiting to be imported. */
    val sharedImages = MutableStateFlow<List<Uri>>(emptyList())

    /** A payment to open (from a notification tap), or the scan sheet to show. */
    val pendingOpen = MutableStateFlow<Long?>(null)
    val pendingScan = MutableStateFlow(false)

    init {
        // Widget + lock-screen summary follow the data; wait for edits to settle before redrawing.
        appScope.launch {
            combine(repository.transactions, settings.settings) { txns, prefs -> txns.size to prefs }
                .collectLatest {
                    delay(1_200)
                    runCatching { surfaces.refresh() }
                }
        }
    }
}
