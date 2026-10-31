package io.github.yorulmazsinan.kokpit.headunit

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import io.github.yorulmazsinan.kokpit.calls.VendorCallLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Connection to the head unit's own services in `com.ts.MainUI` (FF/TS MT8227L firmware).
 * On other units nothing binds and every feature falls back to standard Android.
 */
class HeadUnit(private val context: Context, private val scope: CoroutineScope) {
    @Volatile var common: HeadUnitCommon? = null; private set
    @Volatile var radio: HeadUnitRadio? = null; private set
    @Volatile var phone: HeadUnitPhone? = null; private set
    @Volatile var car: HeadUnitCar? = null; private set

    private val _frequency = MutableStateFlow<Int?>(null)
    val frequency: StateFlow<Int?> = _frequency.asStateFlow()
    private val _station = MutableStateFlow<RdsStation?>(null)
    /** RDS name with the frequency it was read on, so a stale name never sticks to a new station. */
    val station: StateFlow<RdsStation?> = _station.asStateFlow()
    @Volatile private var radioDetails: HeadUnitRadioDetails? = null
    private val rds = RdsTracker()
    private val _btDevice = MutableStateFlow<String?>(null)
    val btDevice: StateFlow<String?> = _btDevice.asStateFlow()
    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    fun start() {
        bind("android.intent.action.MAIN_UI") { common = HeadUnitCommon(VendorChannel(it, VendorDescriptors.COMMON)) }
        bind("com.ts.tsspeechlib.radio.TsRadioService") { radio = HeadUnitRadio(VendorChannel(it, VendorDescriptors.RADIO)) }
        bind("com.ts.tsspeechlib.bt.TsBtService") { phone = HeadUnitPhone(VendorChannel(it, VendorDescriptors.BT)) }
        bind("com.ts.tsspeechlib.car.TsCarService") { car = HeadUnitCar(VendorChannel(it, VendorDescriptors.CAR)) }
    }

    /** Reads frequency and Bluetooth device; call periodically while a screen needs them. */
    fun refresh() {
        scope.launch(Dispatchers.IO) {
            val tuned = common?.frequencyTenths()
            tuned?.let { _frequency.value = it }
            _station.value = tuned?.let { f -> rds.update(f, radioCommon()?.stationName())?.let { RdsStation(f, it) } }
            _btDevice.value = phone?.deviceName()?.takeIf { it.isNotBlank() }
        }
    }

    fun radioCommon(): HeadUnitRadioDetails? = radioDetails ?: common?.radioDetails()?.also { radioDetails = it }

    /** Snapshot of everything the vendor services report, for Diagnostics. */
    fun probe(): List<String> {
        val c = common
        val k = car
        return listOf(
            "services common=${c != null} radio=${radio != null} bt=${phone != null} car=${k != null}",
            "frequency=${c?.frequencyTenths()} rds=${radioCommon()?.stationName()} mode=${c?.workMode()} btConnected=${c?.bluetoothConnected()} btDevice=${phone?.deviceName()}",
            "speed=${c?.speed()} temp=${c?.temperature()} brake=${c?.brakeState()} reverse=${c?.reverseState()} night=${c?.nightMode()}",
            "callLog uri=${VendorCallLog.uri} rows=${VendorCallLog.query(context).size} error=${VendorCallLog.lastError} download=${VendorCallLog.lastDownload}",
            "can: km=${k?.totalKilometres()} fuel=${k?.fuelLeft()} speed=${k?.speed()} left=${k?.leftSignal()} right=${k?.rightSignal()} hazard=${k?.hazard()}",
        )
    }

    private fun bind(action: String, onConnected: (IBinder) -> Unit) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (service == null) return
                onConnected(service)
                _available.value = true
                refresh()
            }

            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        runCatching {
            context.bindService(Intent(action).setPackage(VENDOR_PACKAGE), connection, Context.BIND_AUTO_CREATE)
        }
    }

    companion object {
        const val VENDOR_PACKAGE = "com.ts.MainUI"
    }
}

data class RdsStation(val frequency: Int, val name: String)
