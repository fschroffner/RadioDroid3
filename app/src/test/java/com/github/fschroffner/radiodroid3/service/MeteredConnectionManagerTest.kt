package com.github.fschroffner.radiodroid3.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MeteredConnectionManagerTest {

    @Test
    fun testCooldownConstant() {
        assertEquals(20_000L, MeteredConnectionManager.METERED_CONNECTION_WARNING_COOLDOWN)
    }

    @Test
    fun testCooldownLifecycleWithMockTime() {
        var currentTime = 1_000_000L
        val tracker = MeteredConnectionCooldownTracker(timeProvider = { currentTime })

        // Initially inactive
        assertFalse(tracker.isCooldownActive())

        // Pause recorded at t = 1,000,000
        tracker.recordMeteredPause()
        assertEquals(1_000_000L, tracker.lastMeteredConnectionWarningTime)

        // 5 seconds later -> cooldown active
        currentTime += 5_000L
        assertTrue(tracker.isCooldownActive())

        // 19.999 seconds later -> cooldown still active
        currentTime = 1_000_000L + 19_999L
        assertTrue(tracker.isCooldownActive())

        // 20.001 seconds later -> cooldown expired
        currentTime = 1_000_000L + 20_001L
        assertFalse(tracker.isCooldownActive())

        // Reset clears cooldown immediately
        currentTime = 2_000_000L
        tracker.recordMeteredPause()
        assertTrue(tracker.isCooldownActive())
        tracker.resetCooldown()
        assertFalse(tracker.isCooldownActive())
        assertEquals(0L, tracker.lastMeteredConnectionWarningTime)
    }
}
