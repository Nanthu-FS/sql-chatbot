package com.spendlens.app.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class SharedTextTest {

    private val now = LocalDateTime.of(2026, 10, 1, 12, 0)

    @Test
    fun bankMessageSharedAsText() {
        val draft = draftFromText("Sent Rs.450.00\nFrom HDFC Bank A/C *1234\nTo SWIGGY\nOn 30/09/26\nRef 426512345678", now)
        assertEquals(45_000L, draft.amountMinor)
        assertTrue(draft.merchant, draft.merchant.contains("swiggy", ignoreCase = true))
        assertTrue(draft.sourceUri.startsWith("text:"))
        assertEquals(DraftState.READY, draft.state)
    }

    @Test
    fun upiReceiptSharedAsText() {
        val draft = draftFromText("Payment successful\n₹1,250\nPaid to Blue Tokai Coffee\nUPI transaction ID 426598765432\n30 Sep 2026, 9:41 am", now)
        assertEquals(1_25_000L, draft.amountMinor)
        assertTrue(DraftFlag.NO_AMOUNT !in draft.flags)
    }

    @Test
    fun textWithoutAnAmountAsksForOne() {
        val draft = draftFromText("see you at lunch", now)
        assertTrue(DraftFlag.NO_AMOUNT in draft.flags)
        assertEquals(now, draft.dateTime)
    }

    @Test
    fun sameTextGivesSameSource() {
        assertEquals(draftFromText("Paid ₹99", now).sourceUri, draftFromText("Paid ₹99", now).sourceUri)
    }
}
