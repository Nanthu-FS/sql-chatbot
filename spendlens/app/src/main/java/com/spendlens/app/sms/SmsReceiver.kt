package com.spendlens.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.spendlens.app.SpendLensApplication
import com.spendlens.app.data.toLocalDateTime
import kotlinx.coroutines.launch

/** Adds bank debit SMS as they arrive, when "Auto-add bank SMS" is on. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)?.filterNotNull().orEmpty()
        if (parts.isEmpty()) return
        val sender = parts.first().displayOriginatingAddress.orEmpty()
        val body = parts.joinToString("") { it.displayMessageBody.orEmpty() }
        val at = parts.first().timestampMillis.toLocalDateTime()
        val container = (context.applicationContext as SpendLensApplication).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.smsAutoImporter.handle(sender, body, at)
            } finally {
                pending.finish()
            }
        }
    }
}
