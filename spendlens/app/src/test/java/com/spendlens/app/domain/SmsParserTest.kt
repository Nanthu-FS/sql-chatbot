package com.spendlens.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class SmsParserTest {

    private val received = LocalDateTime.of(2026, 9, 30, 20, 15)

    private fun parse(sender: String, body: String) = SmsParser.parse(sender, body, received)

    @Test
    fun hdfcUpiSent() {
        val p = parse("VM-HDFCBK", "Sent Rs.450.00\nFrom HDFC Bank A/C *1234\nTo SWIGGY\nOn 30/09/26\nRef 426512345678\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808")!!
        assertEquals(45000L, p.amountMinor)
        assertEquals("Swiggy", p.merchant)
        assertEquals("HDFC Bank", p.bank)
        assertEquals("XX1234", p.account)
        assertEquals(PayMode.UPI, p.mode)
        assertEquals("426512345678", p.reference)
        assertEquals(received, p.dateTime) // same day, no time in SMS → arrival time
    }

    @Test
    fun hdfcCardSpend() {
        val p = parse("AD-HDFCBK", "Spent Rs.1249 On HDFC Bank Card 5678 At BLINKIT On 2026-09-30:19:45:12.Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 5678 to 7308080808")!!
        assertEquals(124900L, p.amountMinor)
        assertEquals("Blinkit", p.merchant)
        assertEquals(PayMode.CARD, p.mode)
        assertEquals(LocalDateTime.of(2026, 9, 30, 19, 45, 12), p.dateTime)
    }

    @Test
    fun iciciDebit() {
        val p = parse("JD-ICICIT-S", "ICICI Bank Acct XX123 debited for Rs 599.00 on 29-Sep-26; AIRTEL credited. UPI:426512345678. Call 18002662 for dispute. SMS BLOCK 123 to 9215676766.")!!
        assertEquals(59900L, p.amountMinor)
        assertEquals("Airtel", p.merchant)
        assertEquals("ICICI Bank", p.bank)
        assertEquals("426512345678", p.reference)
        assertEquals(LocalDateTime.of(2026, 9, 29, 12, 0), p.dateTime)
    }

    @Test
    fun sbiTransfer() {
        val p = parse("CP-SBIUPI", "Dear UPI user A/C X1234 debited by 250.0 on date 30Sep26 trf to Rahul Kumar Refno 426512345678. If not u? call 1800111109. -SBI")!!
        assertEquals(25000L, p.amountMinor)
        assertEquals("Rahul Kumar", p.merchant)
        assertEquals("SBI", p.bank)
        assertEquals("426512345678", p.reference)
    }

    @Test
    fun axisUpiPath() {
        val p = parse("AX-AXISBK", "INR 320.00 debited\nA/c no. XX1234\n30-09-26, 13:05:22\nUPI/P2M/426512345678/Chai Point\nNot you? SMS BLOCKUPI Cust ID to 919951860002\nAxis Bank")!!
        assertEquals(32000L, p.amountMinor)
        assertEquals("Chai Point", p.merchant)
        assertEquals("426512345678", p.reference)
        assertEquals(LocalDateTime.of(2026, 9, 30, 13, 5, 22), p.dateTime)
    }

    @Test
    fun kotakToVpa() {
        val p = parse("VM-KOTAKB", "Sent Rs.99.00 from Kotak Bank AC X1234 to netflix@ybl on 30-09-26.UPI Ref 426512345678. Not you, https://kotak.com/KBANKT/Fraud")!!
        assertEquals(9900L, p.amountMinor)
        assertEquals("Netflix", p.merchant)
        assertEquals("Kotak", p.bank)
        assertEquals("XX1234", p.account)
    }

    @Test
    fun atmWithdrawalIgnoresBalance() {
        val p = parse("VM-HDFCBK", "Rs.2000.00 withdrawn at ATM S1AB1234 on 30-09-26 from A/c XX1234. Avl Bal Rs.10,345.67")!!
        assertEquals(200000L, p.amountMinor)
        assertEquals(PayMode.ATM, p.mode)
        assertEquals("Cash withdrawal", p.merchant)
    }

    @Test
    fun rejectsNonDebits() {
        assertNull(parse("VM-HDFCBK", "Rs.5000.00 credited to HDFC Bank A/c XX1234 on 30-09-26 by a/c linked to VPA priya@okhdfc (UPI Ref No 426512345678)."))
        assertNull(parse("VM-HDFCBK", "123456 is the OTP for your transaction of Rs.2,499.00 at AMAZON on HDFC Bank card ending 5678. Valid for 10 mins."))
        assertNull(parse("VM-HDFCBK", "Your A/c XX1234 will be debited with Rs.649.00 on 05-10-2026 towards NACH mandate for NETFLIX."))
        assertNull(parse("VM-HDFCBK", "Available balance in A/c XX1234 is Rs 12,345.67 as on 30-09-26."))
        assertNull(parse("VM-HDFCBK", "Get 10% cashback on payments above Rs.500 via HDFC Bank card. T&C apply"))
        assertNull(parse("VM-HDFCBK", "Your transaction of Rs.500 at XYZ STORE using card XX5678 has been declined due to insufficient balance."))
        assertNull(parse("+919876543210", "Hey I paid 500 for the tickets"))
    }

    @Test
    fun senderShape() {
        assertEquals(true, SmsParser.looksLikeBankSender("VM-HDFCBK"))
        assertEquals(true, SmsParser.looksLikeBankSender("JD-ICICIT-S"))
        assertEquals(false, SmsParser.looksLikeBankSender("+919876543210"))
    }
}
