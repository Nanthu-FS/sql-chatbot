package app.monoworkspace

import app.monoworkspace.data.db.MonoDatabase
import app.monoworkspace.data.repo.PageRepository
import app.monoworkspace.data.repo.SettingsRepository
import app.monoworkspace.data.repo.TemplateRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Creates the Getting Started tour on first launch. */
class Seeder(
    private val settings: SettingsRepository,
    private val pages: PageRepository,
    private val templates: TemplateRepository,
    private val db: MonoDatabase,
) {
    private val lock = Mutex()

    /** Returns the id of the tour page when it was created now. */
    suspend fun seedIfNeeded(): String? = lock.withLock {
        if (settings.current().seeded) return@withLock null
        if (db.pages().count() > 0) {
            settings.setSeeded(true)
            return@withLock null
        }
        val payload = templates.readAsset("seed/getting_started.json") ?: run {
            settings.setSeeded(true)
            return@withLock null
        }
        val page = templates.instantiate(payload, null)
        pages.setFavorite(page.id, true)
        settings.setSeeded(true)
        page.id
    }
}
