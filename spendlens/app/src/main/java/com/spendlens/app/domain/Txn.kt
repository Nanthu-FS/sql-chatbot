package com.spendlens.app.domain

import java.time.LocalDateTime

/** A saved payment, as the UI and analytics see it. */
data class Txn(
    val id: Long,
    val amountMinor: Long,
    val merchant: String,
    val category: Category,
    val dateTime: LocalDateTime,
    val paymentApp: String? = null,
    val reference: String? = null,
    val note: String? = null,
    val imagePath: String? = null,
    val rawText: String? = null,
)
