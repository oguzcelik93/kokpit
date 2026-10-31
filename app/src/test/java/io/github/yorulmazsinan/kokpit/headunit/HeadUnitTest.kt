package io.github.yorulmazsinan.kokpit.headunit

import android.os.Binder
import android.os.Parcel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Stands in for the vendor service: checks the interface token and records each call. */
private class FakeVendor(private val descriptor: String, private val reply: (Int, Parcel) -> Unit = { _, _ -> }) : Binder() {
    val calls = mutableListOf<Pair<Int, List<Any?>>>()
    var readArgs: (Parcel) -> List<Any?> = { emptyList() }

    override fun onTransact(code: Int, data: Parcel, out: Parcel?, flags: Int): Boolean {
        data.enforceInterface(descriptor)
        calls += code to readArgs(data)
        out?.writeNoException()
        out?.let { reply(code, it) }
        return true
    }
}

@RunWith(RobolectricTestRunner::class)
class HeadUnitTest {
    @Test
    fun tuningSendsBandFmAndHundredthsOfAMegahertz() {
        val radio = FakeVendor(VendorDescriptors.RADIO).apply { readArgs = { listOf(it.readInt(), it.readInt()) } }
        HeadUnitRadio(VendorChannel(radio, VendorDescriptors.RADIO)).tune(984)
        assertEquals(listOf(16 to listOf(0, 9840)), radio.calls)
    }

    @Test
    fun seekAndStepUseTheirOwnCodes() {
        val radio = FakeVendor(VendorDescriptors.RADIO)
        val r = HeadUnitRadio(VendorChannel(radio, VendorDescriptors.RADIO))
        r.seekUp(); r.seekDown(); r.open()
        assertEquals(listOf(12, 13, 4), radio.calls.map { it.first })
    }

    @Test
    fun dialSendsTheNumberToTheBluetoothService() {
        val bt = FakeVendor(VendorDescriptors.BT).apply { readArgs = { listOf(it.readString()) } }
        HeadUnitPhone(VendorChannel(bt, VendorDescriptors.BT)).dial("05321112233")
        assertEquals(listOf(1 to listOf<Any?>("05321112233")), bt.calls)
    }

    @Test
    fun readsTheCurrentFrequencyFromTheCommonService() {
        val common = FakeVendor(VendorDescriptors.COMMON) { code, out -> if (code == 34) out.writeString("98.40") }
        assertEquals(984, HeadUnitCommon(VendorChannel(common, VendorDescriptors.COMMON)).frequencyTenths())
    }

    @Test
    fun frequencyParsing() {
        assertEquals(1043, parseFmTenths("104.30"))
        assertEquals(875, parseFmTenths(" 87.5 "))
        assertNull(parseFmTenths("1080"))
        assertNull(parseFmTenths(null))
    }

    @Test
    fun aDeadServiceGivesNullInsteadOfCrashing() {
        val dead = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean = throw android.os.RemoteException("gone")
        }
        assertNull(HeadUnitCommon(VendorChannel(dead, VendorDescriptors.COMMON)).frequencyTenths())
    }
}
