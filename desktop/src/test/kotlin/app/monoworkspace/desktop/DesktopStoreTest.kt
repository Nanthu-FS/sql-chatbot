package app.monoworkspace.desktop

import app.monoworkspace.AppContainer
import app.monoworkspace.data.db.MonoDatabase
import app.monoworkspace.data.repo.SearchFilters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

class DesktopStoreTest {
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("mono-test").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun seedPersistsAndReloads() = runBlocking {
        val c = AppContainer(dir)
        val tour = c.seeder.seedIfNeeded()
        assertNotNull(tour)
        val pages = c.database.pages().count()
        val blocks = c.database.blocks().count()
        assertTrue(pages > 0 && blocks > 0)
        c.database.save()
        c.appScope.cancel()

        val scope = CoroutineScope(SupervisorJob())
        val reloaded = MonoDatabase(dir, scope)
        assertEquals(pages, reloaded.pages().count())
        assertEquals(blocks, reloaded.blocks().count())
        scope.cancel()
    }

    @Test
    fun searchFindsPrefixesAfterRebuild() = runBlocking {
        val c = AppContainer(dir)
        val page = c.pages.createPage(null, "Quarterly planning")
        c.search.rebuild()
        val hits = c.search.search("quarter", SearchFilters())
        assertTrue(hits.any { it.pageId == page.id })
        c.appScope.cancel()
    }

    @Test
    fun backupRoundTripsIntoAFreshWorkspace() = runBlocking {
        val a = AppContainer(dir)
        a.seeder.seedIfNeeded()
        val tracker = a.templates.builtins().first()
        a.templates.instantiate(tracker, null)
        val out = ByteArrayOutputStream()
        a.export.exportBackup(out)
        val pages = a.database.pages().count()
        val values = a.database.values().all().size
        a.appScope.cancel()

        val otherDir = Files.createTempDirectory("mono-test-b").toFile()
        try {
            val b = AppContainer(otherDir)
            b.export.restoreBackup(ByteArrayInputStream(out.toByteArray()), b.cacheDir)
            assertEquals(pages, b.database.pages().count())
            assertEquals(values, b.database.values().all().size)
            b.appScope.cancel()
        } finally {
            otherDir.deleteRecursively()
        }
    }

    @Test
    fun deletingAPageCascades() = runBlocking {
        val c = AppContainer(dir)
        val page = c.pages.createPage(null, "Doomed")
        val child = c.pages.createPage(page.id, "Child")
        c.pages.trash(page.id)
        c.pages.deleteForever(page.id)
        assertEquals(null, c.pages.get(page.id))
        assertEquals(null, c.pages.get(child.id))
        assertTrue(c.database.blocks().byPage(child.id).isEmpty())
        c.appScope.cancel()
    }
}
