package com.spendlens.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [Index("timestamp"), Index("sourceUri"), Index("reference")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    val merchant: String,
    val category: String,
    /** Epoch millis, local wall-clock time converted with the device zone. */
    val timestamp: Long,
    val paymentApp: String? = null,
    val reference: String? = null,
    val note: String? = null,
    val imagePath: String? = null,
    val sourceUri: String? = null,
    val rawText: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
