package app.monoworkspace

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
import java.io.File

/** Manual dependency graph, one per process. Data lives in %APPDATA%\MonoWorkspace on Windows. */
class AppContainer(val dataDir: File = defaultDataDir()) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val cacheDir: File = File(dataDir, "cache").apply { mkdirs() }

    val database = MonoDatabase(dataDir, appScope)
    val settings = SettingsRepository(dataDir)
    val media = MediaRepository(dataDir)
    val search = SearchRepository(database)
    val pages = PageRepository(database, search, media)
    val blocks = BlockRepository(database, search)
    val databases = DatabaseRepository(database, search, pages)
    val templates = TemplateRepository(database, pages, blocks, databases)
    val export = ExportRepository(database, pages, blocks, databases, search, media, settings)
    val seeder = Seeder(settings, pages, templates, database)

    companion object {
        fun defaultDataDir(): File {
            System.getProperty("mono.dataDir")?.let { return File(it) }
            val appData = System.getenv("APPDATA")
            val base = when {
                !appData.isNullOrBlank() -> File(appData)
                System.getProperty("os.name").lowercase().contains("mac") -> File(System.getProperty("user.home"), "Library/Application Support")
                else -> File(System.getenv("XDG_DATA_HOME") ?: (System.getProperty("user.home") + "/.local/share"))
            }
            return File(base, "MonoWorkspace").apply { mkdirs() }
        }
    }
}
