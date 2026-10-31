package io.github.yorulmazsinan.kokpit.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetryPolicyTest {
    private val policy = RetryPolicy()

    @Test
    fun retriesEvery20sWithinWindow() {
        assertEquals(20_000L, policy.nextAttemptDelay(startedElapsedMs = 1_000, nowElapsedMs = 1_000))
        assertEquals(20_000L, policy.nextAttemptDelay(startedElapsedMs = 1_000, nowElapsedMs = 61_000))
        assertEquals(10_000L, policy.nextAttemptDelay(startedElapsedMs = 0, nowElapsedMs = 170_000))
    }

    @Test
    fun givesUpAfterThreeMinutes() {
        assertNull(policy.nextAttemptDelay(startedElapsedMs = 0, nowElapsedMs = 180_000))
        assertNull(policy.nextAttemptDelay(startedElapsedMs = 0, nowElapsedMs = 500_000))
    }
}
