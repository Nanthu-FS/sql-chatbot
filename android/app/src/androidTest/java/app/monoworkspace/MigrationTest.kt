package app.monoworkspace

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.monoworkspace.data.db.MonoDatabase
import app.monoworkspace.data.db.Migrations
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val testDb = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MonoDatabase::class.java)

    /** A v1 database created from the exported schema opens with every declared migration. */
    @Test
    fun createV1AndOpenWithMigrations() = runTest {
        helper.createDatabase(testDb, 1).apply {
            execSQL(
                "INSERT INTO pages (id, parentId, databaseId, isDatabase, title, icon, cover, orderKey, isFavorite, favoriteOrder, isTrashed, trashRoot, trashedAt, createdAt, editedAt) " +
                    "VALUES ('p1', NULL, NULL, 0, 'Hello', NULL, NULL, 'V', 0, NULL, 0, 0, NULL, 1, 1)",
            )
            close()
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, MonoDatabase::class.java, testDb)
            .addMigrations(*Migrations.ALL)
            .build()
        try {
            assertEquals("Hello", db.pages().get("p1")?.title)
            assertEquals(1, db.pages().count())
        } finally {
            db.close()
        }
    }
}
