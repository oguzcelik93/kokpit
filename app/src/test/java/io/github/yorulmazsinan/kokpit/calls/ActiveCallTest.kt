package io.github.yorulmazsinan.kokpit.calls

import io.github.yorulmazsinan.kokpit.home.cards.elapsedText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveCallTest {
    @Test
    fun mapsHandsFreeStates() {
        assertEquals(CallPhase.ACTIVE, callPhaseFor(0))
        assertEquals(CallPhase.HELD, callPhaseFor(1))
        assertEquals(CallPhase.DIALING, callPhaseFor(3))
        assertEquals(CallPhase.INCOMING, callPhaseFor(4))
        assertNull(callPhaseFor(-1))
        assertNull("terminated", callPhaseFor(7))
        assertNull(callPhaseFor(null))
    }

    @Test
    fun comparesNumbersWhateverTheFormat() {
        assertEquals(true, sameNumber("0555 111 22 33", "+90 555 111 22 33"))
        assertEquals(false, sameNumber("0555 111 22 33", "0555 111 22 34"))
        assertEquals(false, sameNumber("112", "112"))
        assertEquals(false, sameNumber(null, "0555"))
    }

    @Test
    fun formatsElapsedTime() {
        assertEquals("0:05", elapsedText(5_400))
        assertEquals("12:03", elapsedText(723_000))
        assertEquals("1:02:05", elapsedText(3_725_000))
    }
}
