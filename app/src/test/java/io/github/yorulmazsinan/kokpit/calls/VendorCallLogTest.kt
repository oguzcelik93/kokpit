package io.github.yorulmazsinan.kokpit.calls

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VendorCallLogTest {
    @Test
    fun readsAutochipsRows() {
        val call = VendorCallLog.vendorCallEntry("Ece", "0555 111 22 33", "missed", "20261028T183005", ZoneOffset.UTC)!!
        assertEquals(CallType.MISSED, call.type)
        assertEquals("Ece", call.displayName)
        assertEquals(LocalDateTime.of(2026, 10, 28, 18, 30, 5).toInstant(ZoneOffset.UTC).toEpochMilli(), call.timeMs)
    }

    @Test
    fun mapsTypesAndTolerantTimes() {
        assertEquals(CallType.OUTGOING, VendorCallLog.vendorCallEntry(null, "1", "outgoing", null)!!.type)
        assertEquals(CallType.INCOMING, VendorCallLog.vendorCallEntry(null, "1", "incoming", "garbage")!!.type)
        assertEquals(0L, VendorCallLog.vendorCallEntry(null, "1", "incoming", "garbage")!!.timeMs)
        assertNull(VendorCallLog.vendorCallEntry("Ece", " ", "incoming", null))
    }
}
