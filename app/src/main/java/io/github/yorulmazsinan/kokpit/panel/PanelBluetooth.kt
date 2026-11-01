package io.github.yorulmazsinan.kokpit.panel

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet

/** At most this many phones on the panel's Bluetooth service at once (we have two). */
private const val MAX_LINKS = 3

/**
 * The panel's second door, for Kokpit Kumanda: a *secure* RFCOMM service, so only phones
 * already paired with the unit can connect and the link is encrypted. It listens only while
 * the panel is open and carries exactly the same requests as the Wi-Fi panel — the QR's
 * token, the PIN, the session and the time limits are all checked by [PhonePanel], not here.
 */
@SuppressLint("MissingPermission")
class PanelBluetooth(context: Context) {
    private val adapter: BluetoothAdapter? = runCatching {
        context.applicationContext.getSystemService(BluetoothManager::class.java)?.adapter
    }.getOrNull()

    /** Why Bluetooth isn't offered this time, for the unit's screen (null when it is). */
    @Volatile var problem: String? = null
        private set

    class Server internal constructor(private val socket: BluetoothServerSocket, val name: String?) {
        private val links = CopyOnWriteArraySet<BluetoothSocket>()

        /** Accepts phones until [close]; each link runs [onLink] on its own thread. */
        fun serve(onLink: (InputStream, OutputStream) -> Unit) {
            Thread {
                while (true) {
                    val s = runCatching { socket.accept() }.getOrNull() ?: break
                    if (links.size >= MAX_LINKS) { runCatching { s.close() }; continue }
                    links += s
                    Thread {
                        try {
                            onLink(BufferedInputStream(s.inputStream), s.outputStream)
                        } catch (_: Exception) {
                        } finally {
                            links -= s
                            runCatching { s.close() }
                        }
                    }.apply { isDaemon = true; name = "kokpit-panel-bt-link" }.start()
                }
            }.apply { isDaemon = true; name = "kokpit-panel-bt" }.start()
        }

        /** Stops listening and drops every connected phone. */
        fun close() {
            runCatching { socket.close() }
            links.forEach { runCatching { it.close() } }
            links.clear()
        }
    }

    /** Starts listening, or returns null (see [problem]) when the unit can't offer Bluetooth. */
    fun listen(): Server? {
        val a = adapter ?: return fail("Ünitede Android Bluetooth'u yok")
        val enabled = runCatching { a.isEnabled }.getOrDefault(false)
        if (!enabled) return fail("Ünitenin Bluetooth'u kapalı")
        return runCatching {
            val socket = a.listenUsingRfcommWithServiceRecord("Kokpit paneli", UUID.fromString(PANEL_BT_UUID))
            Server(socket, runCatching { a.name }.getOrNull())
        }.onSuccess { problem = null }
            .onFailure { problem = "Bluetooth paneli açılamadı (${it.message ?: it.javaClass.simpleName})" }
            .getOrNull()
    }

    private fun fail(reason: String): Server? {
        problem = reason
        return null
    }
}
