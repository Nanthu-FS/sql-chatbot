package com.spendlens.app

import android.content.Context
import android.net.Uri
import com.spendlens.app.data.AppDatabase
import com.spendlens.app.data.SettingsRepository
import com.spendlens.app.data.TransactionRepository
import com.spendlens.app.ocr.ImageStore
import com.spendlens.app.ocr.ImportManager
import com.spendlens.app.ocr.OcrEngine
import com.spendlens.app.ocr.ScreenshotFinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/** Hand-rolled dependency graph; small enough not to need a DI framework. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database = AppDatabase.create(context)
    val repository = TransactionRepository(database.transactionDao())
    val settings = SettingsRepository(context)
    val images = ImageStore(context).also { it.clearStaging() }
    val importManager = ImportManager(context, repository, images, OcrEngine(), ScreenshotFinder(context), appScope)

    /** Images shared to the app from the gallery or a payment app, waiting to be imported. */
    val sharedImages = MutableStateFlow<List<Uri>>(emptyList())
}
