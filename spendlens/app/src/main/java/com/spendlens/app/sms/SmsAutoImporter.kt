package com.spendlens.app.sms

import com.spendlens.app.data.SettingsRepository
import com.spendlens.app.data.TransactionEntity
import com.spendlens.app.data.TransactionRepository
import com.spendlens.app.data.toEpochMillis
import com.spendlens.app.domain.CategoryClassifier
import com.spendlens.app.domain.SmsParser
import com.spendlens.app.domain.TxnSource
import com.spendlens.app.notify.Notifier
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

class SmsAutoImporter(
    private val repository: TransactionRepository,
    private val settings: SettingsRepository,
    private val notifier: Notifier,
) {
    /** Returns true if the message became a saved payment. */
    suspend fun handle(sender: String, body: String, receivedAt: LocalDateTime): Boolean {
        val prefs = settings.settings.first()
        if (!prefs.smsAutoImport) return false
        val parsed = SmsParser.parse(sender, body, receivedAt) ?: return false
        if (repository.isDuplicate(parsed.amountMinor, parsed.dateTime, parsed.reference)) return false
        val id = repository.save(
            TransactionEntity(
                amountMinor = parsed.amountMinor,
                merchant = parsed.merchant ?: parsed.bank ?: "Bank debit",
                category = CategoryClassifier.classify(parsed.merchant, parsed.body).key,
                timestamp = parsed.dateTime.toEpochMillis(),
                paymentApp = parsed.via,
                reference = parsed.reference,
                sourceUri = "sms:auto:${receivedAt.toEpochMillis()}",
                rawText = parsed.body,
                source = TxnSource.SMS.key,
            ),
        )
        notifier.paymentAdded(id, prefs.currency.format(parsed.amountMinor), parsed.merchant ?: "a payment", parsed.via)
        return true
    }
}
