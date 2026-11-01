package io.github.yorulmazsinan.kokpit.connect

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.github.yorulmazsinan.kokpit.time.TrueTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalTime

/** A head unit is the *car side* of Bluetooth: it plays phones' music (A2DP sink) and their calls (HFP client). */
const val PROFILE_A2DP_SINK = 11
const val PROFILE_HEADSET_CLIENT = 16

data class PairedPhone(val address: String, val name: String, val connected: Boolean, val lastConnectedMs: Long)

/** Most recently used first; never-connected phones keep their pairing order at the end. */
fun connectionOrder(phones: List<PairedPhone>): List<PairedPhone> = phones.sortedByDescending { it.lastConnectedMs }

/**
 * The unit keeps paging the last phone and ignores the others. At ignition-on, when no phone
 * is connected after a few seconds, Kokpit tries the paired phones in turn (most recent
 * first); the driver can also pick one by hand. Uses Android's own Bluetooth stack through
 * the car-side profiles; on firmwares whose Bluetooth lives in a separate vendor module this
 * finds no paired phones and does nothing.
 */
@SuppressLint("MissingPermission")
class BtAutoConnect(private val context: Context, private val scope: CoroutineScope) {
    private val adapter: BluetoothAdapter? = runCatching { BluetoothAdapter.getDefaultAdapter() }.getOrNull()
    private val proxies = mutableMapOf<Int, BluetoothProfile>()
    private val prefs = context.getSharedPreferences("bt_phones", Context.MODE_PRIVATE)
    private val busy = Mutex()

    private val aclReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            @Suppress("DEPRECATION")
            val device: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) ?: return
            prefs.edit().putLong(device.address, TrueTime.nowMs()).apply()
            log("${nameOf(device)} bağlandı")
        }
    }

    val available: Boolean get() = adapter != null && hasPermission()

    fun start() {
        val a = adapter ?: return
        if (!hasPermission()) return
        listOf(PROFILE_A2DP_SINK, PROFILE_HEADSET_CLIENT).forEach { profile ->
            runCatching {
                a.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(p: Int, proxy: BluetoothProfile) { proxies[p] = proxy }
                    override fun onServiceDisconnected(p: Int) { proxies.remove(p) }
                }, profile)
            }
        }
        ContextCompat.registerReceiver(context, aclReceiver, IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED), ContextCompat.RECEIVER_EXPORTED)
        scope.launch {
            WakeEvents.flow.collect { if (it is WakeEvent.Wake) scope.launch { delay(WAKE_GRACE_MS); ensureConnected() } }
        }
    }

    fun phones(): List<PairedPhone> {
        val a = adapter ?: return emptyList()
        if (!hasPermission()) return emptyList()
        val connected = connectedAddresses()
        return runCatching { a.bondedDevices.orEmpty() }.getOrDefault(emptySet())
            .filter { it.bluetoothClass?.majorDeviceClass?.let { c -> c == BluetoothClass.Device.Major.PHONE } ?: true }
            .map { PairedPhone(it.address, nameOf(it), it.address in connected, prefs.getLong(it.address, 0L)) }
            .let(::connectionOrder)
    }

    /** No phone connected? Try the paired ones, most recent first. */
    suspend fun ensureConnected() {
        val a = adapter ?: return
        if (!a.isEnabled || connectedAddresses().isNotEmpty()) return
        busy.withLock {
            for (phone in phones().take(MAX_AUTO_TRIES)) {
                if (connectedAddresses().isNotEmpty()) return
                log("${phone.name} deneniyor")
                if (connectTo(phone.address)) return
            }
            log("hiçbir telefona bağlanılamadı")
        }
    }

    /** Hand pick: let go of the current phone and connect this one. */
    suspend fun switchTo(address: String): Boolean = busy.withLock {
        connectedAddresses().filter { it != address }.forEach { other -> call("disconnect", other) }
        delay(1_500)
        connectTo(address)
    }

    private suspend fun connectTo(address: String): Boolean {
        val ok = call("connect", address)
        if (!ok) {
            log("bağlanma komutu reddedildi (ünite izin vermiyor)")
            return false
        }
        repeat(CONNECT_WAIT_STEPS) {
            delay(1_000)
            if (address in connectedAddresses()) {
                log("${nameOf(address)}: bağlandı")
                return true
            }
        }
        log("${nameOf(address)}: yanıt yok")
        return false
    }

    /** Calls the car-side profile's connect/disconnect (not public API; Android 9 allows it for apps). */
    private suspend fun call(method: String, address: String): Boolean = withContext(Dispatchers.Main) {
        val device = adapter?.getRemoteDevice(address) ?: return@withContext false
        var any = false
        proxies.values.forEach { proxy ->
            val result = runCatching { proxy.javaClass.getMethod(method, BluetoothDevice::class.java).invoke(proxy, device) as? Boolean }.getOrNull()
            if (result == true) any = true
        }
        any
    }

    private fun connectedAddresses(): Set<String> =
        proxies.values.flatMap { runCatching { it.connectedDevices }.getOrDefault(emptyList()) }.mapTo(mutableSetOf()) { it.address }

    private fun nameOf(device: BluetoothDevice): String = runCatching { device.name }.getOrNull() ?: device.address
    private fun nameOf(address: String): String = adapter?.getRemoteDevice(address)?.let(::nameOf) ?: address

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun log(line: String) = PowerEventLog.add(context, "${LocalTime.now().withNano(0)} BT: $line")

    private companion object {
        const val WAKE_GRACE_MS = 10_000L
        const val MAX_AUTO_TRIES = 3
        const val CONNECT_WAIT_STEPS = 12
    }
}
