package app.monoworkspace

import android.content.Context
import app.monoworkspace.data.db.MonoDatabase
import app.monoworkspace.data.repo.BlockRepository
import app.monoworkspace.data.repo.DatabaseRepository
import app.monoworkspace.data.repo.ExportRepository
import app.monoworkspace.data.repo.MediaRepository
import app.monoworkspace.data.repo.PageRepository
import app.monoworkspace.data.repo.SearchRepository
import app.monoworkspace.data.repo.SettingsRepository
import app.monoworkspace.data.repo.TemplateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency graph, one per process. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: MonoDatabase = MonoDatabase.build(appContext)
    val settings = SettingsRepository(appContext)
    val media = MediaRepository(appContext)
    val search = SearchRepository(database)
    val pages = PageRepository(database, search, media)
    val blocks = BlockRepository(database, search)
    val databases = DatabaseRepository(database, search, pages)
    val templates = TemplateRepository(appContext, database, pages, blocks, databases)
    val export = ExportRepository(database, pages, blocks, databases, search, media, settings)
    val seeder = Seeder(settings, pages, templates, database)
}
