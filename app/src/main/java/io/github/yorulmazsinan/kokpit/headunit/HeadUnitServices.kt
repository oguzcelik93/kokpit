package io.github.yorulmazsinan.kokpit.headunit

/** Radio control via `ITsSpeechRadio` (transaction codes read from the vendor stub). */
class HeadUnitRadio(private val channel: VendorChannel) {
    fun open() = channel.send(4)
    fun close() = channel.send(5)
    fun fm() = channel.send(6)
    fun seekUp() = channel.send(12)
    fun seekDown() = channel.send(13)
    fun nextStation() = channel.send(2)
    fun previousStation() = channel.send(1)

    /** [tenths] is MHz × 10 (984 = 98.4); the tuner takes MHz × 100 on band 0 (FM1). */
    fun tune(tenths: Int) = channel.send(16) {
        writeInt(0)
        writeInt(tenths * 10)
    }
}

/** Calls and Bluetooth music via `ITsSpeechBt`. */
class HeadUnitPhone(private val channel: VendorChannel) {
    fun dial(number: String) = channel.send(1) { writeString(number) }
    fun answer() = channel.send(2)
    fun hangUp() = channel.send(3)
    fun connectState(): Int? = channel.call(4) { it.readInt() }
    fun deviceName(): String? = channel.call(6) { it.readString() }
    fun openMusic() = channel.send(15)
    fun play() = channel.send(8)
    fun pause() = channel.send(9)
    fun previous() = channel.send(10)
    fun next() = channel.send(11)
    fun musicState(): Int? = channel.call(7) { it.readInt() }

    /** The current call's `BluetoothHeadsetClientCall` state, -1 when there is none. */
    fun callState(): Int? = channel.call(5) { it.readInt() }
}

/** Status and car data via `ITsCommon` (the MainUI service). */
class HeadUnitCommon(private val channel: VendorChannel) {
    fun frequencyTenths(): Int? = parseFmTenths(channel.call(34) { it.readString() })
    fun bluetoothConnected(): Boolean? = channel.call(12) { it.readInt() != 0 }
    fun speed(): Float? = channel.call(23) { it.readFloat() }
    fun temperature(): String? = channel.call(24) { it.readString() }
    fun brakeState(): Int? = channel.call(9) { it.readInt() }
    fun reverseState(): Int? = channel.call(8) { it.readInt() }
    fun nightMode(): Boolean? = channel.call(14) { it.readInt() != 0 }
    fun workMode(): Int? = channel.call(10) { it.readInt() }
    fun mute() = channel.send(2)
    fun volumeUp() = channel.send(3)
    fun volumeDown() = channel.send(4)
    fun isMuted(): Boolean? = channel.call(13) { it.readInt() != 0 }
    fun openEqualizer() = channel.send(7)
    fun showVolume() = channel.send(21)
    fun screenOff() = channel.send(22)

    /** `getSpecialBinder("Radio")`: the tuner's own `ITsRadioCommon`, which carries RDS. */
    fun radioDetails(): HeadUnitRadioDetails? =
        channel.call(55, { writeString("Radio") }) { it.readStrongBinder() }
            ?.let { HeadUnitRadioDetails(VendorChannel(it, VendorDescriptors.RADIO_COMMON)) }
}

/** RDS data via `ITsRadioCommon` (MainUI's `RadioServiceBinder`). */
class HeadUnitRadioDetails(private val channel: VendorChannel) {
    /** The station's RDS programme service name, e.g. "POWER FM"; null when nothing is received. */
    fun stationName(): String? = cleanPsName(channel.call(30) { it.readString() })

    /** `Evc_evol_workmode_set`: the audio source switch the vendor screens make on resume. */
    fun setWorkMode(mode: Int): Boolean = channel.send(29) { writeInt(mode) }
}

/** The tuner hands back a fixed 15-char buffer: NUL-padded, sometimes space-padded. */
fun cleanPsName(raw: String?): String? =
    raw?.replace('\u0000', ' ')?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }

/** CAN-bus data via `ITsSpeechCar`; only filled when a CAN decoder is fitted and configured. */
class HeadUnitCar(private val channel: VendorChannel) {
    fun totalKilometres(): Int? = channel.call(17) { it.readInt() }
    fun fuelLeft(): Int? = channel.call(18) { it.readInt() }
    fun leftSignal(): Int? = channel.call(19) { it.readInt() }
    fun rightSignal(): Int? = channel.call(20) { it.readInt() }
    fun hazard(): Int? = channel.call(21) { it.readInt() }
    fun speed(): Int? = channel.call(22) { it.readInt() }
}
