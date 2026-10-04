package com.urbanlens.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val lat: Double,
    val lng: Double,
    val note: String,
    val photoPath: String?,
    val createdAt: Long,
    val confirmations: Int,
    val lastConfirmedAt: Long,
)

@Entity(tableName = "saved_places")
data class SavedPlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lat: Double,
    val lng: Double,
    val alerting: Boolean = false,
    val lastNotifiedAt: Long = 0,
    val lastAqi: Int? = null,
    val lastCheckedAt: Long = 0,
)

@Dao
interface ReportDao {
    @Query("SELECT * FROM reports ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ReportEntity>>

    @Query("SELECT * FROM reports")
    suspend fun getAll(): List<ReportEntity>

    @Query("SELECT * FROM reports WHERE id = :id")
    suspend fun get(id: Long): ReportEntity?

    @Insert
    suspend fun insert(report: ReportEntity): Long

    @Update
    suspend fun update(report: ReportEntity)

    @Query("DELETE FROM reports WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SavedPlaceDao {
    @Query("SELECT * FROM saved_places ORDER BY id")
    fun observeAll(): Flow<List<SavedPlaceEntity>>

    @Query("SELECT * FROM saved_places ORDER BY id")
    suspend fun getAll(): List<SavedPlaceEntity>

    @Insert
    suspend fun insert(place: SavedPlaceEntity): Long

    @Update
    suspend fun update(place: SavedPlaceEntity)

    @Query("DELETE FROM saved_places WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [ReportEntity::class, SavedPlaceEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reports(): ReportDao
    abstract fun savedPlaces(): SavedPlaceDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "urban-lens.db").build()
    }
}
