package com.spendlens.app.data

import com.spendlens.app.domain.Category
import com.spendlens.app.domain.Goal
import com.spendlens.app.domain.SamplePayment
import com.spendlens.app.domain.Txn
import com.spendlens.app.domain.TxnSource
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class TransactionRepository(private val dao: TransactionDao, private val goalDao: GoalDao) {

    val transactions: Flow<List<Txn>> = dao.observeAll().map { list -> list.map { it.toTxn() } }

    fun observe(id: Long): Flow<Txn?> = dao.observe(id).map { it?.toTxn() }

    suspend fun get(id: Long): TransactionEntity? = dao.get(id)

    suspend fun all(): List<Txn> = dao.getAll().map { it.toTxn() }

    suspend fun save(entity: TransactionEntity): Long = dao.insert(entity)

    suspend fun saveAll(entities: List<TransactionEntity>) = dao.insertAll(entities)

    suspend fun update(entity: TransactionEntity) = dao.update(entity)

    /** Removes the row but keeps the screenshot so "Undo" can restore it. */
    suspend fun delete(entity: TransactionEntity) = dao.delete(entity)

    suspend fun deleteImage(path: String?) = withContext(Dispatchers.IO) {
        if (path != null) File(path).delete()
    }

    suspend fun deleteEverything(imagesDir: File) {
        dao.deleteAll()
        withContext(Dispatchers.IO) { imagesDir.listFiles()?.forEach { it.delete() } }
    }

    suspend fun importedSourceUris(): Set<String> = dao.importedSourceUris().toSet()

    val sampleCount: Flow<Int> = dao.observeCountBySource(TxnSource.SAMPLE.key)

    suspend fun addSamples(samples: List<SamplePayment>) {
        dao.insertAll(
            samples.mapIndexed { i, s ->
                TransactionEntity(
                    amountMinor = s.amountMinor,
                    merchant = s.merchant,
                    category = s.category.key,
                    timestamp = s.dateTime.toEpochMillis(),
                    paymentApp = s.app,
                    reference = "SAMPLE${i.toString().padStart(4, '0')}",
                    source = TxnSource.SAMPLE.key,
                )
            },
        )
    }

    suspend fun removeSamples(): Int = dao.deleteBySource(TxnSource.SAMPLE.key)

    suspend fun spentBetween(from: LocalDateTime, to: LocalDateTime): Long = dao.sumBetween(from.toEpochMillis(), to.toEpochMillis())

    // ---- goals

    val goals: Flow<List<Goal>> = goalDao.observeAll().map { list -> list.map { it.toGoal() } }

    suspend fun saveGoal(goal: Goal) {
        goalDao.upsert(
            GoalEntity(
                id = goal.id,
                name = goal.name,
                targetMinor = goal.targetMinor,
                savedMinor = goal.savedMinor,
                deadline = goal.deadline?.toEpochDay(),
            ),
        )
    }

    suspend fun addToGoal(id: Long, amountMinor: Long) = goalDao.addToSaved(id, amountMinor)

    suspend fun deleteGoal(id: Long) = goalDao.delete(id)

    /** Same UPI reference, or same amount within a few minutes → most likely the same payment. */
    suspend fun isDuplicate(amountMinor: Long, time: LocalDateTime, reference: String?): Boolean {
        if (reference != null && dao.countByReference(reference) > 0) return true
        val millis = time.toEpochMillis()
        val window = 3 * 60 * 1000L
        return dao.countSimilar(amountMinor, millis - window, millis + window) > 0
    }
}

fun LocalDateTime.toEpochMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun Long.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())

fun TransactionEntity.toTxn(): Txn = Txn(
    id = id,
    amountMinor = amountMinor,
    merchant = merchant,
    category = Category.fromKey(category),
    dateTime = timestamp.toLocalDateTime(),
    paymentApp = paymentApp,
    reference = reference,
    note = note,
    imagePath = imagePath,
    rawText = rawText,
    source = TxnSource.fromKey(source),
)

fun GoalEntity.toGoal(): Goal = Goal(id, name, targetMinor, savedMinor, deadline?.let { LocalDate.ofEpochDay(it) })
