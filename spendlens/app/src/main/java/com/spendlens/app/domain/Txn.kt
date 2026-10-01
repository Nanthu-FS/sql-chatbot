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
    val source: TxnSource = TxnSource.SCREENSHOT,
)

/** Where a payment came from. Stored as [key] in the database. */
enum class TxnSource(val key: String, val label: String) {
    SCREENSHOT("screenshot", "Screenshot"),
    MANUAL("manual", "Added by hand"),
    SMS("sms", "Bank SMS"),
    SHARED("shared", "Shared text"),
    SAMPLE("sample", "Sample data");

    companion object {
        fun fromKey(key: String?): TxnSource = entries.firstOrNull { it.key == key } ?: SCREENSHOT
    }
}
