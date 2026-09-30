package com.spendlens.app.lock

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AppLockTest {

    @Test
    fun pinIsStoredOnlyAsASaltedHash() {
        val stored = AppLock.hash("2580")
        assertFalse(stored.contains("2580"))
        assertTrue(AppLock.matches("2580", stored))
        assertFalse(AppLock.matches("2581", stored))
        assertFalse(AppLock.matches("2580", null))
        assertFalse(AppLock.matches("2580", "garbage"))
        // Same PIN, different salt → different stored value.
        assertNotEquals(stored, AppLock.hash("2580"))
    }
}
