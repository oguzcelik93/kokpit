package io.github.yorulmazsinan.kokpit.headunit

import android.os.IBinder
import android.os.Parcel

/** AIDL interface names exposed by the FF/TS head unit's `com.ts.MainUI`. */
object VendorDescriptors {
    const val COMMON = "com.ts.main.common.ITsCommon"
    const val RADIO_COMMON = "com.ts.main.common.ITsRadioCommon"
    const val RADIO = "com.ts.tsspeechlib.radio.ITsSpeechRadio"
    const val BT = "com.ts.tsspeechlib.bt.ITsSpeechBt"
    const val CAR = "com.ts.tsspeechlib.car.ITsSpeechCar"
}

/**
 * Raw AIDL calls into the vendor services (no stub classes needed): token, arguments,
 * transaction code, then the usual exception header. Any failure yields null.
 */
class VendorChannel(private val binder: IBinder, private val descriptor: String) {
    fun <T> call(code: Int, write: Parcel.() -> Unit = {}, read: (Parcel) -> T): T? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(descriptor)
            data.write()
            binder.transact(code, data, reply, 0)
            reply.readException()
            read(reply)
        } catch (e: Exception) {
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun send(code: Int, write: Parcel.() -> Unit = {}): Boolean = call(code, write) { true } ?: false
}

/** "98.40" → 984 (tenths of MHz); null for AM or unreadable values. */
fun parseFmTenths(value: String?): Int? {
    val text = value?.trim() ?: return null
    if ('.' !in text) return null
    val mhz = text.toDoubleOrNull() ?: return null
    return Math.round(mhz * 10).toInt().takeIf { it in 640..1080 }
}
