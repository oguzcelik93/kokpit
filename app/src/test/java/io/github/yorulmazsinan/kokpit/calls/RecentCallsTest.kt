package io.github.yorulmazsinan.kokpit.calls

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentCallsTest {
    private fun c(name: String?, number: String, t: Long, type: CallType = CallType.INCOMING) = CallEntry(name, number, type, t)

    @Test
    fun newestFirstLimitedToThree() {
        val calls = listOf(c("A", "1", 10), c("B", "2", 40), c("C", "3", 30), c("D", "4", 20))
        assertEquals(listOf("B", "C", "D"), recentCalls(calls).map { it.name })
    }

    @Test
    fun sameNumberShownOnceWithLatestCall() {
        val calls = listOf(c("Ece", "0555", 10), c("Ece", "0 555", 50, CallType.MISSED), c("Ali", "0532", 30))
        val result = recentCalls(calls)
        assertEquals(listOf("Ece", "Ali"), result.map { it.name })
        assertEquals(CallType.MISSED, result.first().type)
    }

    @Test
    fun displayNameFallsBackToNumber() {
        assertEquals("0532 111 22 33", c(null, "0532 111 22 33", 1).displayName)
    }
}
