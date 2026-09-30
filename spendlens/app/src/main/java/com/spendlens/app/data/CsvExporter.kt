package com.spendlens.app.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.spendlens.app.domain.CurrencyOption
import com.spendlens.app.domain.Money
import com.spendlens.app.domain.Txn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

object CsvExporter {

    suspend fun export(context: Context, txns: List<Txn>, currency: CurrencyOption): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "spendlens-${LocalDate.now()}.csv")
        file.bufferedWriter().use { out ->
            out.write("date,time,amount,currency,payee,category,paid_via,reference,note\n")
            txns.sortedBy { it.dateTime }.forEach { t ->
                val row = listOf(
                    t.dateTime.toLocalDate().toString(),
                    t.dateTime.toLocalTime().withNano(0).toString(),
                    Money.toInput(t.amountMinor),
                    currency.code,
                    t.merchant,
                    t.category.label,
                    t.paymentApp.orEmpty(),
                    t.reference.orEmpty(),
                    t.note.orEmpty(),
                )
                out.write(row.joinToString(",") { escape(it) })
                out.write("\n")
            }
        }
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}
