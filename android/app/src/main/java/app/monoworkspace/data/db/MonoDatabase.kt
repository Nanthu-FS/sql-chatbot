package app.monoworkspace.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        PageEntity::class,
        BlockEntity::class,
        DatabaseEntity::class,
        ViewEntity::class,
        PropertyValueEntity::class,
        VersionEntity::class,
        TemplateEntity::class,
        RecentEntity::class,
        SearchFts::class,
    ],
    version = MonoDatabase.VERSION,
    exportSchema = true,
)
abstract class MonoDatabase : RoomDatabase() {
    abstract fun pages(): PageDao
    abstract fun blocks(): BlockDao
    abstract fun databases(): DatabaseDao
    abstract fun views(): ViewDao
    abstract fun values(): ValueDao
    abstract fun versions(): VersionDao
    abstract fun templates(): TemplateDao
    abstract fun recents(): RecentDao
    abstract fun search(): SearchDao

    companion object {
        const val NAME = "mono_workspace.db"
        const val VERSION = 1

        fun build(context: Context): MonoDatabase =
            Room.databaseBuilder(context, MonoDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                .build()
    }
}

/**
 * Schema migrations. Version 1 is the first schema, so the list is empty;
 * add `Migration(n, n + 1)` objects here as the schema evolves.
 */
object Migrations {
    val ALL: Array<Migration> = arrayOf()
}
