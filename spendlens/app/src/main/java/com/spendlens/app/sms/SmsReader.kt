package com.spendlens.app.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.spendlens.app.data.toLocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/** Reads the SMS inbox (only with READ_SMS granted). Nothing leaves the phone. */
class SmsReader(private val context: Context) {

    data class Message(val id: Long, val address: String, val body: String, val date: LocalDateTime)

    suspend fun inbox(days: Int, limit: Int = 3000): List<Message> = withContext(Dispatchers.IO) {
        if (!hasReadPermission(context)) return@withContext emptyList()
        val since = System.currentTimeMillis() - days * 86_400_000L
        val out = mutableListOf<Message>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} DESC",
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(Telephony.Sms._ID)
            val address = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val body = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val date = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext() && out.size < limit) {
                out += Message(c.getLong(id), c.getString(address).orEmpty(), c.getString(body).orEmpty(), c.getLong(date).toLocalDateTime())
            }
        }
        out
    }

    companion object {
        fun hasReadPermission(context: Context) =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

        fun hasReceivePermission(context: Context) =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    }
}
