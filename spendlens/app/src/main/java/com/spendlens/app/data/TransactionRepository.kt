package com.spendlens.app.data

import com.spendlens.app.domain.Category
import com.spendlens.app.domain.Txn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class TransactionRepository(private val dao: TransactionDao) {

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
)
