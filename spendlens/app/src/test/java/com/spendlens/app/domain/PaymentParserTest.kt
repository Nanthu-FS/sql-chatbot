package com.spendlens.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PaymentParserTest {

    private val now = LocalDateTime.of(2026, 9, 30, 21, 0)

    /** Builds lines stacked top to bottom; `size` is the text height in px. */
    private fun screen(vararg rows: Pair<String, Int>): List<OcrLine> {
        var y = 100
        return rows.map { (text, size) ->
            OcrLine(text, top = y, bottom = y + size, left = 80, right = 600).also { y += size + 30 }
        }
    }

    @Test
    fun googlePayReceipt() {
        val p = PaymentParser.parse(
            screen(
                "To SWIGGY" to 40, "swiggy@icici" to 28, "₹450" to 110, "Completed" to 36,
                "30 Sep 2026, 7:45 pm" to 30, "UPI transaction ID" to 28, "426512345678" to 28,
                "To: SWIGGY" to 28, "Google transaction ID" to 28, "CICAgOiabcdEF12" to 28,
            ),
            now,
        )
        assertEquals(45000L, p.amountMinor)
        assertEquals("Swiggy", p.merchant)
        assertEquals(LocalDateTime.of(2026, 9, 30, 19, 45), p.dateTime)
        assertEquals("Google Pay", p.paymentApp)
        assertEquals("426512345678", p.reference)
        assertEquals(PaymentStatus.SUCCESS, p.status)
        assertTrue(p.confidence >= 0.8f)
        assertEquals(Category.FOOD, CategoryClassifier.classify(p.merchant, p.rawText))
    }

    @Test
    fun phonePeReceipt() {
        val p = PaymentParser.parse(
            screen(
                "Transaction Successful" to 40, "07:45 pm on 28 Sep 2026" to 28, "Paid to" to 28,
                "Blinkit" to 36, "₹ 1,249" to 90, "Transaction ID" to 28, "T2409281945123456789" to 28,
                "Debited from" to 28, "XXXXXX1234" to 28, "₹1,249" to 28, "UTR: 426512345678" to 28,
                "Powered by PhonePe" to 24,
            ),
            now,
        )
        assertEquals(124900L, p.amountMinor)
        assertEquals("Blinkit", p.merchant)
        assertEquals(LocalDateTime.of(2026, 9, 28, 19, 45), p.dateTime)
        assertEquals("PhonePe", p.paymentApp)
        assertEquals("T2409281945123456789", p.reference)
        assertEquals(Category.GROCERIES, CategoryClassifier.classify(p.merchant, p.rawText))
    }

    @Test
    fun paytmWithGarbledRupeeSign() {
        val p = PaymentParser.parse(
            screen(
                "Paid Successfully to" to 32, "RAHUL KUMAR" to 36, "2500" to 110, "30 Sep, 07:45 PM" to 28,
                "Paid ₹500 from HDFC Bank" to 26, "UPI Ref No: 426512345678" to 26, "paytm" to 30,
            ),
            now,
        )
        assertEquals(50000L, p.amountMinor)
        assertEquals("Rahul Kumar", p.merchant)
        assertEquals(LocalDateTime.of(2026, 9, 30, 19, 45), p.dateTime)
        assertEquals("Paytm", p.paymentApp)
        assertEquals("426512345678", p.reference)
        assertEquals(Category.TRANSFERS, CategoryClassifier.classify(p.merchant, p.rawText))
    }

    @Test
    fun usdVenmoPayment() {
        val p = PaymentParser.parse(
            screen("Venmo" to 30, "You paid Jane Doe" to 34, "$20.00" to 80, "Sep 30, 2026" to 26, "Dinner 🍕" to 26),
            now,
        )
        assertEquals(2000L, p.amountMinor)
        assertEquals("USD", p.currencyCode)
        assertEquals("Jane Doe", p.merchant)
        assertEquals("Venmo", p.paymentApp)
        assertEquals(LocalDateTime.of(2026, 9, 30, 12, 0), p.dateTime)
        assertEquals(false, p.hasTime)
    }

    @Test
    fun ignoresBalanceAndCashback() {
        val p = PaymentParser.parse(
            screen(
                "Paid to Zomato" to 34, "₹320" to 100, "Available balance ₹12,450" to 26,
                "You won ₹25 cashback" to 26, "1 Oct 2025, 1:05 pm" to 26,
            ),
            now,
        )
        assertEquals(32000L, p.amountMinor)
        assertEquals("Zomato", p.merchant)
        assertEquals(LocalDateTime.of(2025, 10, 1, 13, 5), p.dateTime)
    }

    @Test
    fun paidAmountToMerchantSentence() {
        val p = PaymentParser.parse(screen("Paid ₹1,500 to Uber India" to 30, "Today, 9:10 AM" to 26), now)
        assertEquals(150000L, p.amountMinor)
        assertEquals("Uber India", p.merchant)
        assertEquals(LocalDateTime.of(2026, 9, 30, 9, 10), p.dateTime)
        assertEquals(Category.TRANSPORT, CategoryClassifier.classify(p.merchant, p.rawText))
    }

    @Test
    fun receiptTotalWins() {
        val p = PaymentParser.parse(
            listOf(
                OcrLine("Blue Tokai Coffee Roasters"), OcrLine("Cappuccino 250.00"), OcrLine("Croissant 180.00"),
                OcrLine("Subtotal 430.00"), OcrLine("GST 21.50"), OcrLine("Total ₹451.50"), OcrLine("12/09/2026 10:32"),
            ),
            now,
        )
        assertEquals(45150L, p.amountMinor)
        assertEquals(LocalDateTime.of(2026, 9, 12, 10, 32), p.dateTime)
    }

    @Test
    fun chatScreenshotIsNotAPayment() {
        val p = PaymentParser.parse(screen("Priya" to 30, "Are we meeting at 5?" to 26, "Sure, see you" to 26, "9:41" to 20), now)
        assertNull(p.amountMinor)
        assertTrue(p.confidence < 0.5f)
    }

    @Test
    fun failedAndIncomingPayments() {
        val failed = PaymentParser.parse(screen("Payment Failed" to 34, "₹300" to 90, "Paid to Airtel" to 26), now)
        assertEquals(PaymentStatus.FAILED, failed.status)
        val incoming = PaymentParser.parse(screen("Received from Priya Sharma" to 30, "₹1,000" to 90), now)
        assertTrue(incoming.isIncoming)
        assertEquals(100000L, incoming.amountMinor)
    }

    @Test
    fun ordersLinesIntoRows() {
        val lines = listOf(
            OcrLine("426512345678", top = 400, bottom = 430, left = 600, right = 900),
            OcrLine("Paid to", top = 100, bottom = 130, left = 80, right = 300),
            OcrLine("UPI transaction ID", top = 402, bottom = 428, left = 80, right = 400),
        )
        assertEquals(listOf("Paid to", "UPI transaction ID", "426512345678"), PaymentParser.orderLines(lines).map { it.text })
    }

    @Test
    fun fileNameHints() {
        assertEquals(LocalDateTime.of(2026, 9, 30, 19, 45, 12), PaymentParser.dateFromFileName("Screenshot_20260930-194512_GPay.jpg"))
        assertEquals(
            LocalDateTime.of(2026, 9, 30, 19, 45, 12),
            PaymentParser.dateFromFileName("Screenshot_2026-09-30-19-45-12-345_com.phonepe.app.jpg"),
        )
        assertEquals("Google Pay", PaymentParser.appFromFileName("Screenshot_20260930-194512_GPay.jpg"))
        assertEquals("PhonePe", PaymentParser.appFromFileName("Screenshot_2026-09-30-19-45-12-345_com.phonepe.app.jpg"))
        assertNull(PaymentParser.dateFromFileName("IMG-WA0001.jpg"))
    }
}
