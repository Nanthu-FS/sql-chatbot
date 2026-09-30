package com.spendlens.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TransactionEntity::class, GoalEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun goalDao(): GoalDao

    companion object {
        const val NAME = "spendlens.db"

        /** v1 → v2: remember where each payment came from, and add savings goals. Keeps every row. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'screenshot'")
                db.execSQL("UPDATE `transactions` SET `source` = 'manual' WHERE `imagePath` IS NULL")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`targetMinor` INTEGER NOT NULL, `savedMinor` INTEGER NOT NULL, `deadline` INTEGER, `createdAt` INTEGER NOT NULL)",
                )
            }
        }

        fun create(context: Context, name: String = NAME): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
