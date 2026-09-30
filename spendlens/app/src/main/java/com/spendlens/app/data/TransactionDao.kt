package com.spendlens.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observe(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAll(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<TransactionEntity>): List<Long>

    @Update
    suspend fun update(entity: TransactionEntity)

    @Delete
    suspend fun delete(entity: TransactionEntity)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT sourceUri FROM transactions WHERE sourceUri IS NOT NULL")
    suspend fun importedSourceUris(): List<String>

    @Query("SELECT COUNT(*) FROM transactions WHERE reference = :reference")
    suspend fun countByReference(reference: String): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE amountMinor = :amountMinor AND timestamp BETWEEN :from AND :to")
    suspend fun countSimilar(amountMinor: Long, from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE source = :source")
    fun observeCountBySource(source: String): Flow<Int>

    @Query("DELETE FROM transactions WHERE source = :source")
    suspend fun deleteBySource(source: String): Int

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE timestamp >= :from AND timestamp < :to")
    suspend fun sumBetween(from: Long, to: Long): Long
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY createdAt")
    fun observeAll(): Flow<List<GoalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: GoalEntity): Long

    @Query("UPDATE goals SET savedMinor = MAX(0, savedMinor + :delta) WHERE id = :id")
    suspend fun addToSaved(id: Long, delta: Long)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun delete(id: Long)
}
