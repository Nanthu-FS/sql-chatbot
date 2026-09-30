package com.spendlens.app.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** One line of recognised text with its bounding box in image pixels (all zero when unknown). */
data class OcrLine(val text: String, val top: Int = 0, val bottom: Int = 0, val left: Int = 0, val right: Int = 0) {
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val centerY: Int get() = (top + bottom) / 2
}

enum class PaymentStatus { SUCCESS, PENDING, FAILED, UNKNOWN }

data class ParsedPayment(
    val amountMinor: Long?,
    val currencyCode: String?,
    val merchant: String?,
    val dateTime: LocalDateTime?,
    val hasTime: Boolean,
    val paymentApp: String?,
    val reference: String?,
    val status: PaymentStatus,
    val isIncoming: Boolean,
    /** 0..1 — how much this looks like a payment receipt at all. */
    val confidence: Float,
    val rawText: String,
)

/**
 * Turns OCR output of a payment screenshot (UPI apps, wallets, card/bank receipts) into a payment.
 * Pure Kotlin so it can be unit-tested on the JVM.
 */
object PaymentParser {

    fun parse(input: List<OcrLine>, now: LocalDateTime = LocalDateTime.now()): ParsedPayment {
        val lines = orderLines(input).map { it.copy(text = it.text.trim()) }.filter { it.text.isNotEmpty() }
        val rawText = lines.joinToString("\n") { it.text }
        val lower = rawText.lowercase()

        val amount = findAmount(lines)
        val merchant = findMerchant(lines, amount?.lineIndex)
        val date = findDateTime(lines, now)
        val app = detectApp(lower)
        val reference = findReference(lines)
        val status = detectStatus(lower)
        val incoming = detectIncoming(lower)

        var confidence = 0f
        if (amount != null) confidence += if (amount.explicitCurrency) 0.4f else 0.15f
        if (app != null) confidence += 0.2f
        confidence += (PAYMENT_WORDS.count { it in lower } * 0.1f).coerceAtMost(0.3f)
        if (reference != null) confidence += 0.15f
        if (merchant != null) confidence += 0.1f
        if (date != null) confidence += 0.05f

        return ParsedPayment(
            amountMinor = amount?.minor,
            currencyCode = amount?.currency,
            merchant = merchant,
            dateTime = date?.first,
            hasTime = date?.second ?: false,
            paymentApp = app,
            reference = reference,
            status = status,
            isIncoming = incoming,
            confidence = confidence.coerceIn(0f, 1f),
            rawText = rawText,
        )
    }

    // ---------------------------------------------------------------- line ordering

    /** Reading order: rows top-to-bottom, and left-to-right inside a row. */
    fun orderLines(lines: List<OcrLine>): List<OcrLine> {
        if (lines.all { it.top == 0 && it.bottom == 0 }) return lines
        val sorted = lines.sortedBy { it.centerY }
        val rows = mutableListOf<MutableList<OcrLine>>()
        for (line in sorted) {
            val row = rows.lastOrNull()
            val anchor = row?.first()
            val tolerance = maxOf(anchor?.height ?: 0, line.height) / 2
            if (anchor != null && kotlin.math.abs(line.centerY - anchor.centerY) <= tolerance) row.add(line)
            else rows.add(mutableListOf(line))
        }
        return rows.flatMap { row -> row.sortedBy { it.left } }
    }

    // ---------------------------------------------------------------- amount

    private data class AmountCandidate(
        val minor: Long,
        val digits: String,
        val currency: String?,
        val explicitCurrency: Boolean,
        val lineIndex: Int,
        val score: Float,
    )

    private data class FoundAmount(val minor: Long, val currency: String?, val explicitCurrency: Boolean, val lineIndex: Int)

    private const val NUMBER = """(\d{1,3}(?:,\d{2,3})+(?:\.\d{1,2})?|\d{1,8}(?:\.\d{1,2})?)"""
    private val CURRENCY_PREFIX = Regex("""(₹|rs\.?|inr|\$|usd|us\$|€|eur|£|gbp|aed|sgd|s\$|¥|jpy)\s*$NUMBER(?![\d,])""", RegexOption.IGNORE_CASE)
    private val CURRENCY_SUFFIX = Regex("""(?<![\d.,])$NUMBER\s*(rs|inr|usd|eur|gbp|aed|sgd|jpy)\b""", RegexOption.IGNORE_CASE)

    /** A line that is only a number, maybe with a garbled rupee sign in front (₹ often reads as %, Z, *, ?, F, t, R, e). */
    private val BARE_AMOUNT = Regex("""^([%zZ*?fFtRe₹{]\s?)?[-–]?\s*$NUMBER\s*(/-)?$""")

    private fun words(vararg w: String) = Regex(w.joinToString("|", "(?<![a-z])(?:", ")") { Regex.escape(it) })

    private val POSITIVE_CONTEXT = words(
        "paid", "amount", "total", "debited", "sent", "payment", "spent", "charged", "pay", "to pay",
        "grand total", "net amount", "bill amount", "order total",
    )
    private val NEGATIVE_CONTEXT = words(
        "balance", "cashback", "cash back", "reward", "saved", "discount", "offer", "limit", "available",
        "coins", "points", "won", "scratch", "refund", "tip", "fee", "gst", "tax", "subtotal", "sub total",
        "mrp", "coupon", "save",
    )
    private val ID_CONTEXT = Regex("""(?<![a-z])(?:id|ref|utr|no\.|no:|number|a/c|acc|account|phone|mobile|upi|order|invoice|pin|otp)(?![a-z])|xx|\+91""")

    private fun findAmount(lines: List<OcrLine>): FoundAmount? {
        val maxHeight = lines.maxOfOrNull { it.height } ?: 0
        val candidates = mutableListOf<AmountCandidate>()

        lines.forEachIndexed { index, line ->
            val text = line.text
            val lower = text.lowercase()
            val previous = lines.getOrNull(index - 1)?.text?.lowercase().orEmpty()
            val sizeScore = if (maxHeight > 0) 45f * line.height / maxHeight else 0f
            var context = 0f
            val positiveHere = POSITIVE_CONTEXT.containsMatchIn(lower)
            if (positiveHere) context += 15f
            if (POSITIVE_CONTEXT.containsMatchIn(previous)) context += 12f
            if (Regex("(?<![a-z])(grand )?total").containsMatchIn(lower)) context += 15f
            if (NEGATIVE_CONTEXT.containsMatchIn(lower)) context -= 35f
            if (NEGATIVE_CONTEXT.containsMatchIn(previous) && !positiveHere) context -= 20f

            fun add(numberText: String, currency: String?, explicit: Boolean, bonus: Float) {
                val minor = toMinor(numberText) ?: return
                if (minor <= 0 || minor > 10_000_000_000L) return
                val digits = numberText.substringBefore('.').replace(",", "")
                candidates += AmountCandidate(minor, digits, currency, explicit, index, sizeScore + context + bonus)
            }

            CURRENCY_PREFIX.findAll(text).forEach { m ->
                val wholeLine = m.value.length >= text.replace(Regex("[\\s/-]+$"), "").length - 1
                add(m.groupValues[2], currencyCode(m.groupValues[1]), true, 40f + if (wholeLine) 15f else 0f)
            }
            CURRENCY_SUFFIX.findAll(text).forEach { m ->
                add(m.groupValues[1], currencyCode(m.groupValues[2]), true, 35f)
            }
            BARE_AMOUNT.matchEntire(text.trim())?.let { m ->
                val garbledSign = m.groupValues[1].isNotBlank()
                val idLike = ID_CONTEXT.containsMatchIn(previous)
                val number = m.groupValues[2]
                // Long plain digit runs without separators are IDs, phone numbers, PINs…
                val plainDigits = number.replace(",", "").substringBefore('.')
                if (!garbledSign && idLike) return@let
                if (!garbledSign && plainDigits.length >= 7 && ',' !in number) return@let
                val currency = if (m.groupValues[1].trim() == "₹") "INR" else null
                add(number, currency, currency != null, 15f + if (garbledSign) 20f else 0f)
            }
        }
        if (candidates.isEmpty()) return null

        var best = candidates.maxWith(compareBy<AmountCandidate> { it.score }.thenBy { -it.lineIndex })

        // "₹500" in huge type is often read as "2500" or "7500": if the same number minus its first digit
        // shows up elsewhere in the receipt, trust that one.
        if (!best.explicitCurrency && best.digits.length >= 2) {
            val stripped = best.digits.drop(1).trimStart('0')
            val better = candidates.firstOrNull { it !== best && it.digits == stripped && it.minor % 100 == best.minor % 100 }
            if (better != null) best = better.copy(score = best.score)
        }
        return FoundAmount(best.minor, best.currency, best.explicitCurrency, best.lineIndex)
    }

    private fun toMinor(number: String): Long? = runCatching {
        BigDecimal(number.replace(",", "")).movePointRight(2).longValueExact()
    }.getOrNull()

    private fun currencyCode(symbol: String): String = when (symbol.lowercase().trimEnd('.')) {
        "₹", "rs", "inr" -> "INR"
        "$", "usd", "us$" -> "USD"
        "€", "eur" -> "EUR"
        "£", "gbp" -> "GBP"
        "aed" -> "AED"
        "sgd", "s$" -> "SGD"
        "¥", "jpy" -> "JPY"
        else -> "INR"
    }

    // ---------------------------------------------------------------- merchant

    private val INLINE_PAYEE = listOf(
        Regex("""^(?:paid|payment|paying|sent|transferred|money sent|pay)\s+(?:successfully\s+)?to\s*[:\-]?\s*(.+)$""", RegexOption.IGNORE_CASE),
        Regex("""^(?:paid|sent)\s+(?:successfully\s+)?(?:to\s+)?(.+?)\s+(?:₹|rs\.?|\$|€|£)\s*\d.*$""", RegexOption.IGNORE_CASE),
        Regex("""^(?:paid|sent)\s+(?:₹|rs\.?|\$|€|£)\s*[\d,.]+\s+to\s+(.+)$""", RegexOption.IGNORE_CASE),
        Regex("""^you\s+paid\s+(.+?)(?:\s+(?:₹|rs\.?|\$|€|£)\s*\d.*)?$""", RegexOption.IGNORE_CASE),
        Regex("""^(?:to|payee|merchant|beneficiary|recipient|paid at|merchant name|payee name)\s*[:\-]\s*(.+)$""", RegexOption.IGNORE_CASE),
        Regex("""^to\s+(.+)$""", RegexOption.IGNORE_CASE),
    )
    private val PAYEE_LABEL = Regex(
        """^(?:paid\s+(?:successfully\s+)?to|payment\s+to|sent\s+to|to|payee|payee name|merchant|merchant name|beneficiary|recipient|paid at|paying)\s*:?$""",
        RegexOption.IGNORE_CASE,
    )
    private val NOT_A_NAME = listOf(
        "payment", "successful", "success", "completed", "transaction", "google pay", "gpay", "phonepe",
        "paytm", "bhim", "upi", "bank", "debited", "credited", "share", "done", "view", "details", "split",
        "receipt", "amount", "total", "balance", "rewards", "cashback", "powered by", "help", "support",
        "from:", "your account", "a/c", "status", "pending", "failed", "processing", "download", "check",
        "wallet", "card ending", "xxxx", "ref", "utr", "date", "time", "you paid", "paid to", "sent to",
    )

    private fun findMerchant(lines: List<OcrLine>, amountLine: Int?): String? {
        for ((index, line) in lines.withIndex()) {
            val text = line.text
            if (PAYEE_LABEL.matches(text)) {
                val next = lines.drop(index + 1).take(2).map { it.text }.firstOrNull { cleanName(it) != null }
                if (next != null) return cleanName(next)
            }
            for (pattern in INLINE_PAYEE) {
                val match = pattern.matchEntire(text) ?: continue
                val name = cleanName(match.groupValues[1])
                if (name != null) return name
            }
        }
        // Fall back to the nearest "name-looking" line around the big amount.
        if (amountLine != null) {
            val order = listOf(-1, -2, 1, 2, -3, 3)
            for (offset in order) {
                val candidate = lines.getOrNull(amountLine + offset)?.text ?: continue
                cleanName(candidate)?.let { return it }
            }
        }
        // Last resort: a UPI handle like "swiggy.merchant@icici".
        val handle = Regex("""\b([a-zA-Z][a-zA-Z._-]{2,})@[a-zA-Z]{2,}\b""").find(lines.joinToString(" ") { it.text })
        return handle?.groupValues?.get(1)
            ?.split('.', '_', '-')?.firstOrNull { it.length >= 3 && it.any(Char::isLetter) }
            ?.let { titleCase(it) }
    }

    private fun cleanName(raw: String): String? {
        var name = raw
            .replace(Regex("""\(?\s*upi\s*id\s*:?.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b[\w.\-]+@[\w.\-]+\b"""), "")
            .replace(Regex("""(banking\s+name|verified|\bupi\b).*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""(₹|rs\.?|\$|€|£)\s*\d[\d,.]*.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^[^\p{L}\d]+|[^\p{L}\d.)]+$"""), "")
            .trim()
        if (name.length < 2 || name.length > 48) return null
        val letters = name.count { it.isLetter() }
        if (letters < 2 || letters < name.length * 0.5) return null
        val lower = name.lowercase()
        if (NOT_A_NAME.any { lower == it || lower.startsWith("$it ") || lower.contains(it) && it.length > 6 }) return null
        if (Regex("""^(mon|tue|wed|thu|fri|sat|sun|today|yesterday|jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\b""", RegexOption.IGNORE_CASE).containsMatchIn(name) && name.any(Char::isDigit)) return null
        if (name == name.uppercase() && name.any(Char::isLetter)) name = titleCase(name)
        return name
    }

    private fun titleCase(text: String): String = text.lowercase().split(' ').joinToString(" ") { word ->
        word.replaceFirstChar { it.titlecase() }
    }

    // ---------------------------------------------------------------- date & time

    private val MONTH_NAMES = listOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december",
    )
    private const val MONTH_NAME = """([a-z]{3,9})\.?"""

    /** "sep", "sept", "september" → 9; "decathlon" → null. */
    private fun monthOf(word: String): Int? {
        val w = word.lowercase()
        val index = MONTH_NAMES.indexOfFirst { it.startsWith(w) }
        return if (index >= 0) index + 1 else null
    }
    private val DAY_MONTH_YEAR = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?[\s\-/.,]*$MONTH_NAME(?:[\s\-/.,']*(\d{4}|\d{2}(?![\d:.])))?""", RegexOption.IGNORE_CASE)
    private val MONTH_DAY_YEAR = Regex("""\b$MONTH_NAME\s+(\d{1,2})(?:st|nd|rd|th)?(?![\d:])(?:,?\s*(\d{4}))?""", RegexOption.IGNORE_CASE)
    private val NUMERIC_DATE = Regex("""\b(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4}|\d{2})\b""")
    private val ISO_DATE = Regex("""\b(20\d{2})[/\-.](\d{1,2})[/\-.](\d{1,2})\b""")
    private val RELATIVE_DATE = Regex("""\b(today|yesterday)\b""", RegexOption.IGNORE_CASE)
    private val TIME_12H = Regex("""\b(\d{1,2})[:.](\d{2})(?::(\d{2}))?\s*([ap])\.?\s*m\b\.?""", RegexOption.IGNORE_CASE)
    private val TIME_24H = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)(?::([0-5]\d))?\b""")

    /** Returns the payment date-time and whether a time of day was found. */
    fun findDateTime(lines: List<OcrLine>, now: LocalDateTime): Pair<LocalDateTime, Boolean>? {
        val text = lines.joinToString("\n") { it.text }
        val dates = mutableListOf<Pair<Int, LocalDate>>()

        DAY_MONTH_YEAR.findAll(text).forEach { m ->
            val day = m.groupValues[1].toInt()
            val month = monthOf(m.groupValues[2]) ?: return@forEach
            date(day, month, m.groupValues[3], now)?.let { dates += m.range.first to it }
        }
        MONTH_DAY_YEAR.findAll(text).forEach { m ->
            val month = monthOf(m.groupValues[1]) ?: return@forEach
            date(m.groupValues[2].toInt(), month, m.groupValues[3], now)?.let { dates += m.range.first to it }
        }
        ISO_DATE.findAll(text).forEach { m ->
            date(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1], now)?.let { dates += m.range.first to it }
        }
        NUMERIC_DATE.findAll(text).forEach { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            // Day-first unless that's impossible.
            val (day, month) = if (b > 12 && a <= 12) b to a else a to b
            date(day, month, m.groupValues[3], now)?.let { dates += m.range.first to it }
        }
        RELATIVE_DATE.findAll(text).forEach { m ->
            val d = if (m.value.equals("today", true)) now.toLocalDate() else now.toLocalDate().minusDays(1)
            dates += m.range.first to d
        }

        val times = mutableListOf<Pair<Int, LocalTime>>()
        TIME_12H.findAll(text).forEach { m ->
            var hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].toInt()
            if (hour !in 1..12 || minute > 59) return@forEach
            val pm = m.groupValues[4].equals("p", true)
            if (pm && hour < 12) hour += 12
            if (!pm && hour == 12) hour = 0
            times += m.range.first to LocalTime.of(hour, minute, m.groupValues[3].toIntOrNull()?.coerceIn(0, 59) ?: 0)
        }
        if (times.isEmpty()) {
            TIME_24H.findAll(text).forEach { m ->
                times += m.range.first to LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toIntOrNull() ?: 0)
            }
        }

        val chosen = dates.minByOrNull { it.first } ?: return null
        val time = times.minByOrNull { kotlin.math.abs(it.first - chosen.first) }?.second
        return LocalDateTime.of(chosen.second, time ?: LocalTime.NOON) to (time != null)
    }

    private fun date(day: Int, month: Int, yearText: String, now: LocalDateTime): LocalDate? {
        if (month !in 1..12 || day !in 1..31) return null
        val today = now.toLocalDate()
        val year = when {
            yearText.length == 4 -> yearText.toInt()
            yearText.length == 2 -> 2000 + yearText.toInt()
            else -> today.year
        }
        val parsed = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
        val resolved = if (yearText.isEmpty() && parsed.isAfter(today.plusDays(1))) parsed.minusYears(1) else parsed
        if (resolved.isAfter(today.plusDays(1)) || resolved.isBefore(today.minusYears(15))) return null
        return resolved
    }

    // ---------------------------------------------------------------- app, reference, status

    private val APPS = listOf(
        "Google Pay" to listOf("google pay", "gpay", "g pay", "google transaction id"),
        "PhonePe" to listOf("phonepe", "phone pe"),
        "Paytm" to listOf("paytm"),
        "BHIM" to listOf("bhim"),
        "Amazon Pay" to listOf("amazon pay"),
        "CRED" to listOf(" cred ", "cred pay", "\ncred"),
        "MobiKwik" to listOf("mobikwik"),
        "WhatsApp Pay" to listOf("whatsapp"),
        "PayPal" to listOf("paypal"),
        "Venmo" to listOf("venmo"),
        "Cash App" to listOf("cash app", "cashapp"),
        "Zelle" to listOf("zelle"),
        "Apple Pay" to listOf("apple pay"),
        "Revolut" to listOf("revolut"),
    )

    fun detectApp(lowerText: String): String? {
        val padded = " $lowerText "
        APPS.firstOrNull { (_, keys) -> keys.any { it in padded } }?.let { return it.first }
        return if (Regex("""\bupi\b""").containsMatchIn(lowerText)) "UPI" else null
    }

    private val REFERENCE_LABEL = Regex(
        """(upi\s*(?:transaction|txn|ref(?:erence)?)\s*(?:id|no\.?|number)?|upi\s*ref\s*no\.?|utr(?:\s*(?:no\.?|number))?|transaction\s*(?:id|no\.?|number)|txn\s*(?:id|no\.?)|ref(?:erence)?\s*(?:no\.?|number|id)|order\s*id|google\s*transaction\s*id)""",
        RegexOption.IGNORE_CASE,
    )
    private val REFERENCE_VALUE = Regex("""[A-Za-z0-9]{8,}""")

    private fun findReference(lines: List<OcrLine>): String? {
        lines.forEachIndexed { index, line ->
            val label = REFERENCE_LABEL.find(line.text) ?: return@forEachIndexed
            val rest = line.text.substring(label.range.last + 1)
            REFERENCE_VALUE.find(rest)?.takeIf { it.value.any(Char::isDigit) }?.let { return it.value }
            lines.getOrNull(index + 1)?.text?.replace(" ", "")?.let { next ->
                REFERENCE_VALUE.matchEntire(next)?.takeIf { it.value.any(Char::isDigit) }?.let { return it.value }
            }
        }
        return null
    }

    private fun detectStatus(lower: String): PaymentStatus = when {
        listOf("failed", "declined", "unsuccessful", "rejected", "could not be completed").any { it in lower } -> PaymentStatus.FAILED
        listOf("pending", "processing", "in progress", "awaiting").any { it in lower } -> PaymentStatus.PENDING
        listOf("successful", "success", "completed", "paid", "sent", "approved", "done").any { it in lower } -> PaymentStatus.SUCCESS
        else -> PaymentStatus.UNKNOWN
    }

    private fun detectIncoming(lower: String): Boolean {
        val incoming = listOf("received from", "you received", "money received", "credited to your", "received successfully", "payment received")
        val outgoing = listOf("paid to", "sent to", "debited from", "you paid", "paid successfully", "payment to", "paying ")
        return incoming.any { it in lower } && outgoing.none { it in lower }
    }

    private val PAYMENT_WORDS = listOf(
        "paid", "payment", "transaction", "upi", "debited", "successful", "completed", "total", "receipt",
        "utr", "sent", "bank",
    )

    // ---------------------------------------------------------------- screenshot file names

    private val FILE_DATE = Regex("""(20\d{2})[-_]?(\d{2})[-_]?(\d{2})[-_ ]?(\d{2})[-_:.]?(\d{2})[-_:.]?(\d{2})?""")

    /** "Screenshot_20260930-194512_GPay.jpg" → 2026-09-30T19:45:12. */
    fun dateFromFileName(name: String?): LocalDateTime? {
        val m = FILE_DATE.find(name ?: return null) ?: return null
        return runCatching {
            LocalDateTime.of(
                m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(),
                m.groupValues[4].toInt(), m.groupValues[5].toInt(), m.groupValues[6].toIntOrNull() ?: 0,
            )
        }.getOrNull()
    }

    /** Samsung/MIUI put the foreground app in the file name. */
    fun appFromFileName(name: String?): String? {
        val lower = name?.lowercase() ?: return null
        return when {
            "gpay" in lower || "google pay" in lower || "nbu.paisa" in lower -> "Google Pay"
            "phonepe" in lower -> "PhonePe"
            "paytm" in lower || "one97" in lower -> "Paytm"
            "bhim" in lower || "npci" in lower -> "BHIM"
            "amazon" in lower -> "Amazon Pay"
            "dreamplug" in lower || "_cred" in lower -> "CRED"
            else -> null
        }
    }
}
