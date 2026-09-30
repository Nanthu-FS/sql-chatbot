package com.spendlens.app.domain

import java.math.BigDecimal
import java.time.LocalDateTime

enum class PayMode(val label: String) { UPI("UPI"), CARD("Card"), NETBANKING("Net banking"), ATM("ATM"), OTHER("Bank") }

data class SmsPayment(
    val amountMinor: Long,
    val merchant: String?,
    val bank: String?,
    val account: String?,
    val mode: PayMode,
    val reference: String?,
    val dateTime: LocalDateTime,
    val body: String,
) {
    /** "HDFC Bank · UPI" */
    val via: String get() = listOfNotNull(bank, mode.label.takeIf { mode != PayMode.OTHER }).joinToString(" · ").ifBlank { "Bank SMS" }
}

/**
 * Reads Indian bank / card debit alerts. Returns null for anything that isn't money leaving an
 * account: credits, OTPs, reminders, mandates, failed or reversed transactions, promotions.
 */
object SmsParser {

    private val DLT_SENDER = Regex("""^[A-Z]{2}-[A-Z0-9]{3,8}(-[A-Z])?$""")

    private val BANKS = linkedMapOf(
        "HDFC" to "HDFC Bank", "ICICI" to "ICICI Bank", "SBI" to "SBI", "AXIS" to "Axis Bank", "KOTAK" to "Kotak",
        "IDFC" to "IDFC First", "YESB" to "Yes Bank", "PNB" to "PNB", "BOB" to "Bank of Baroda", "CANB" to "Canara Bank",
        "INDB" to "Indian Bank", "UNION" to "Union Bank", "FEDB" to "Federal Bank", "AUBANK" to "AU Bank",
        "PAYTM" to "Paytm Bank", "AIRBNK" to "Airtel Bank", "SCB" to "Standard Chartered", "HSBC" to "HSBC",
        "CITI" to "Citi", "AMEX" to "Amex", "RBL" to "RBL Bank", "INDUS" to "IndusInd", "CBSSBI" to "SBI", "JUPITER" to "Jupiter",
        "FIBANK" to "Fi", "ONECRD" to "OneCard", "SLICE" to "slice",
    )

    private val IGNORE = listOf(
        "otp", "one time password", "one-time password", "verification code", "will be debited", "to be debited",
        "is due", "due on", "due date", "min amt due", "minimum amount due", "requested money", "collect request",
        "payment request", "mandate", "autopay set", "e-mandate", "scheduled", "declined", "failed", "unsuccessful",
        "reversed", "reversal", "refund", "has been cancelled", "is blocked", "insufficient",
    )
    private val DEBIT = Regex("""\b(debited|spent|sent|withdrawn|paid|purchase|purchased|txn of|transaction of|trf to|transferred)\b""", RegexOption.IGNORE_CASE)
    private val ACCOUNTISH = Regex("""\b(a/?c|acct|account|card|upi|vpa|ref|rrn|utr|imps|neft|atm)\b""", RegexOption.IGNORE_CASE)
    private val AMOUNT = Regex("""(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val AMOUNT_AFTER_BY = Regex("""\bdebited\s+(?:by|for|with)\s+(?:rs\.?|inr|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    private val BALANCE_CONTEXT = Regex("""(bal|balance|avl|available|limit|outstanding)[^0-9]{0,18}$""", RegexOption.IGNORE_CASE)
    private val ACCOUNT = Regex("""(?:a/?c|acct|account|card)\s*(?:no\.?)?\s*(?:ending\s*(?:with|in)?\s*)?[x*]*\s*(\d{3,6})\b""", RegexOption.IGNORE_CASE)
    private val REFERENCE = Regex("""(?:upi\s*ref(?:erence)?(?:\s*no\.?)?|ref(?:erence)?\s*(?:no\.?|number|#|id)?|refno|utr(?:\s*no\.?)?|rrn|txn\s*id|upi)\s*[:.#-]?\s*(\d{6,}|[A-Za-z0-9]{10,})""", RegexOption.IGNORE_CASE)

    private val UPI_PATH_REF = Regex("""UPI/P2[AM]/(\d{6,})""", RegexOption.IGNORE_CASE)

    /** True for DLT-style sender IDs such as "VM-HDFCBK" or "JD-ICICIT-S". */
    fun looksLikeBankSender(sender: String): Boolean = DLT_SENDER.matches(sender.trim().uppercase())

    private val MERCHANT_PATTERNS = listOf(
        // "UPI/P2M/426512345678/Chai Point"  or  "Info: UPI/P2A/.../RAHUL KUMAR"
        Regex("""\bUPI/P2[AM]/\d+/([A-Za-z][A-Za-z0-9 .&'-]{1,40})""", RegexOption.IGNORE_CASE),
        // "; AIRTEL credited" (ICICI)
        Regex(""";\s*([A-Za-z][A-Za-z0-9 .&'-]{1,40}?)\s+credited""", RegexOption.IGNORE_CASE),
        // "trf to Rahul Kumar Refno" (SBI)
        Regex("""\btrf to\s+([A-Za-z][A-Za-z0-9 .&'-]{1,40}?)(?=\s+ref|\s+on\b|[.,]|$)""", RegexOption.IGNORE_CASE),
        // "to VPA swiggy@icici" / "to netflix@ybl"
        Regex("""\bto\s+(?:vpa\s+)?([a-z0-9._-]+@[a-z]+)""", RegexOption.IGNORE_CASE),
        // "At BLINKIT On ..." (cards)
        Regex("""\bat\s+([A-Za-z][A-Za-z0-9 .&'*-]{1,40}?)(?=\s+on\b|\s+via\b|\s+ref|\s+txn|\s+avl|[.,;]|\s*$)""", RegexOption.IGNORE_CASE),
        // "To SWIGGY" on its own line / "to Rahul on 30-09"
        Regex("""\bto\s+([A-Za-z][A-Za-z0-9 .&'-]{1,40}?)(?=\s+on\b|\s+ref|\s+upi|[.,;\n]|$)""", RegexOption.IGNORE_CASE),
    )
    private val NOT_MERCHANT = Regex("""^(a/?c|acct|account|your|you|the|beneficiary|block|dispute|atm\s+\w+|call|sms|report)\b""", RegexOption.IGNORE_CASE)

    fun parse(sender: String, body: String, receivedAt: LocalDateTime): SmsPayment? {
        val text = body.replace(' ', ' ')
        val lower = text.lowercase()
        if (IGNORE.any { it in lower }) return null
        if (!DEBIT.containsMatchIn(text)) return null
        val strongDebit = Regex("""\b(debited|spent|withdrawn|sent|trf to)\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)
        // Pure credit alerts ("... credited to your A/c") without any debit wording.
        if ("credited" in lower && !strongDebit && "paid" !in lower) return null
        // Must mention an account, card or UPI reference — filters out promotions that say "paid".
        if (!ACCOUNTISH.containsMatchIn(text)) return null

        val amount = findAmount(text) ?: return null
        val mode = when {
            "atm" in lower || "withdrawn" in lower -> PayMode.ATM
            "upi" in lower || "vpa" in lower || Regex("@[a-z]+").containsMatchIn(lower) -> PayMode.UPI
            "card" in lower -> PayMode.CARD
            Regex("""\b(neft|imps|rtgs|netbanking|net banking)\b""").containsMatchIn(lower) -> PayMode.NETBANKING
            else -> PayMode.OTHER
        }
        val merchant = if (mode == PayMode.ATM) "Cash withdrawal" else findMerchant(text)
        val date = PaymentParser.findDateTime(listOf(OcrLine(text)), receivedAt)
        val when_ = when {
            date == null -> receivedAt
            date.second -> date.first
            date.first.toLocalDate() == receivedAt.toLocalDate() -> receivedAt
            else -> date.first
        }
        return SmsPayment(
            amountMinor = amount,
            merchant = merchant,
            bank = bankOf(sender, text),
            account = ACCOUNT.find(text)?.groupValues?.get(1)?.let { "XX" + it.takeLast(4) },
            mode = mode,
            reference = UPI_PATH_REF.find(text)?.groupValues?.get(1)
                ?: REFERENCE.findAll(text).map { it.groupValues[1] }.firstOrNull { it.any(Char::isDigit) && it.length >= 6 },
            dateTime = when_,
            body = text,
        )
    }

    private fun findAmount(text: String): Long? {
        AMOUNT_AFTER_BY.find(text)?.let { m -> toMinor(m.groupValues[1])?.let { return it } }
        for (m in AMOUNT.findAll(text)) {
            val before = text.substring(0, m.range.first)
            if (BALANCE_CONTEXT.containsMatchIn(before.takeLast(24))) continue
            toMinor(m.groupValues[1])?.takeIf { it > 0 }?.let { return it }
        }
        return null
    }

    private fun toMinor(number: String): Long? = runCatching {
        BigDecimal(number.replace(",", "").trimEnd('.')).movePointRight(2).toLong()
    }.getOrNull()

    private fun findMerchant(text: String): String? {
        for (pattern in MERCHANT_PATTERNS) {
            for (m in pattern.findAll(text)) {
                val raw = m.groupValues[1].trim().trimEnd('.', ',', ';')
                if (raw.isBlank() || NOT_MERCHANT.containsMatchIn(raw)) continue
                val name = if ('@' in raw) handleName(raw) else clean(raw)
                if (name != null) return name
            }
        }
        return null
    }

    private fun handleName(handle: String): String? {
        val token = handle.substringBefore('@').split('.', '_', '-').flatMap { it.split(Regex("\\d+")) }
            .firstOrNull { it.length >= 3 && it.all(Char::isLetter) && it.lowercase() !in setOf("upi", "pay", "paytmqr", "gpay", "bharatpe", "merchant", "qr") }
        return token?.let { titleCase(it) }
    }

    private fun clean(raw: String): String? {
        val name = raw.replace(Regex("""\s{2,}"""), " ").trim()
        if (name.length < 2 || name.count(Char::isLetter) < 2) return null
        if (Regex("""\d{5,}""").containsMatchIn(name)) return null
        return if (name == name.uppercase()) titleCase(name) else name
    }

    private fun titleCase(s: String) = s.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase() } }

    private fun bankOf(sender: String, text: String): String? {
        val code = sender.uppercase().substringAfter('-').substringBefore('-')
        BANKS.entries.firstOrNull { code.contains(it.key) }?.let { return it.value }
        val lower = text.lowercase()
        return BANKS.values.firstOrNull { it.lowercase() in lower }
    }
}
