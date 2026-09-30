package com.spendlens.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Builds a database exactly as version 1 of the app left it, then opens it with the current
 * schema to prove the upgrade keeps every payment.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun v1DatabaseUpgradesWithoutLosingPayments() = runBlocking {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amountMinor` INTEGER NOT NULL, " +
                    "`merchant` TEXT NOT NULL, `category` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `paymentApp` TEXT, `reference` TEXT, " +
                    "`note` TEXT, `imagePath` TEXT, `sourceUri` TEXT, `rawText` TEXT, `createdAt` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_timestamp` ON `transactions` (`timestamp`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_sourceUri` ON `transactions` (`sourceUri`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_reference` ON `transactions` (`reference`)")
            db.execSQL(
                "INSERT INTO transactions (amountMinor, merchant, category, timestamp, paymentApp, reference, imagePath, sourceUri, rawText, createdAt) " +
                    "VALUES (45000, 'Swiggy', 'food', 1759240000000, 'Google Pay', '426512345678', '/data/shot.jpg', 'content://x/1', 'Paid to SWIGGY', 1759240000000)",
            )
            db.execSQL(
                "INSERT INTO transactions (amountMinor, merchant, category, timestamp, createdAt) VALUES (12000, 'Chai', 'food', 1759250000000, 1759250000000)",
            )
            db.version = 1
        }

        val database = AppDatabase.create(context, name)
        val repo = TransactionRepository(database.transactionDao(), database.goalDao())
        val txns = repo.transactions.first()
        assertEquals(2, txns.size)
        val swiggy = txns.first { it.merchant == "Swiggy" }
        assertEquals(45000L, swiggy.amountMinor)
        assertEquals("426512345678", swiggy.reference)
        assertEquals("screenshot", swiggy.source.key)
        assertEquals("manual", txns.first { it.merchant == "Chai" }.source.key)

        repo.saveGoal(com.spendlens.app.domain.Goal(0, "Trip", 10_000_00, 0, null))
        repo.addToGoal(repo.goals.first().single().id, 2_500_00)
        assertEquals(2_500_00L, repo.goals.first().single().savedMinor)
        database.close()
    }
}
